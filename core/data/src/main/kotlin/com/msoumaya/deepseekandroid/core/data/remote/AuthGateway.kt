package com.msoumaya.deepseekandroid.core.data.remote

import com.msoumaya.deepseekandroid.core.domain.AuthOutcome
import com.msoumaya.deepseekandroid.core.domain.AuthStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Ce que l'application attend d'un service d'authentification.
 *
 * L'interface existe pour que la logique de connexion soit éprouvable sans réseau, et pour
 * qu'un autre fournisseur puisse la remplacer sans toucher aux écrans.
 */
interface AuthGateway {

    /** État de la session, émis à chaque changement. */
    val status: Flow<AuthStatus>

    /** Attend la fin de la relecture de la session enregistrée. */
    suspend fun awaitReady()

    suspend fun signIn(email: String, password: String): AuthOutcome

    suspend fun signUp(email: String, password: String): AuthOutcome

    suspend fun signOut()

    suspend fun currentUserId(): String?

    /**
     * Renvoie le courriel de confirmation d'inscription.
     *
     * Nécessaire parce que le projet partagé exige une confirmation d'adresse : le lien peut
     * expirer ou ne jamais arriver, et sans ce renvoi l'utilisateur n'a aucun moyen de
     * terminer son inscription.
     */
    suspend fun resendSignUpConfirmation(email: String): AuthOutcome

    /** Envoie un lien permettant de créer ou de changer le mot de passe. */
    suspend fun requestPasswordReset(email: String): AuthOutcome
}

/**
 * Implémentation Supabase du projet partagé.
 *
 * Les erreurs sont traduites ici, et nulle part ailleurs : les écrans ne doivent jamais
 * connaître `AuthRestException`. La distinction qui compte pour l'utilisateur est entre
 * **« vos identifiants sont refusés »** et **« votre téléphone est hors ligne »** — les
 * confondre ferait croire à une faute de mot de passe alors qu'il n'y a pas de réseau.
 */
class SupabaseAuthGateway(private val client: SupabaseClient) : AuthGateway {

    private val auth get() = client.auth

    override val status: Flow<AuthStatus> = auth.sessionStatus.map { it.toAuthStatus() }

    override suspend fun awaitReady() {
        runCatching { auth.awaitInitialization() }
    }

    override suspend fun signIn(email: String, password: String): AuthOutcome = attempt(forSignIn = true) {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signUp(email: String, password: String): AuthOutcome = attempt(forSignIn = false) {
        auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signOut() {
        // Une déconnexion qui échoue faute de réseau doit quand même effacer la session
        // locale : sinon l'utilisateur reste connecté contre son gré.
        runCatching { auth.signOut() }
    }

    override suspend fun currentUserId(): String? = auth.currentUserOrNull()?.id

    /**
     * Renvoi du courriel de confirmation.
     *
     * `redirectUrl` reste nul : la redirection vers l'application dépend d'un lien profond
     * enregistré et vérifié, qui n'existe pas encore. Le lien envoyé ouvre donc la page du
     * projet Supabase. C'est une limite connue, consignée dans `ANDROID_MIGRATION.md`.
     */
    override suspend fun resendSignUpConfirmation(email: String): AuthOutcome = attempt(forSignIn = false) {
        auth.resendEmail(OtpType.Email.SIGNUP, email)
    }

    /**
     * Lien de création ou de changement de mot de passe.
     *
     * Le serveur répond « succès » même pour une adresse inconnue, et c'est délibéré de sa
     * part : répondre « cette adresse n'existe pas » permettrait d'énumérer les comptes. Le
     * message affiché doit donc rester neutre.
     */
    override suspend fun requestPasswordReset(email: String): AuthOutcome = attempt(forSignIn = false) {
        auth.resetPasswordForEmail(email)
    }

    private suspend fun attempt(forSignIn: Boolean, block: suspend () -> Unit): AuthOutcome = try {
        block()
        AuthOutcome.Success
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: AuthRestException) {
        classify(error, forSignIn)
    } catch (error: RestException) {
        // Une erreur REST qui n'est pas une erreur d'authentification ne porte pas de code :
        // seule la description du serveur est exploitable.
        classifyText(error, forSignIn)
    } catch (error: HttpRequestException) {
        AuthOutcome.Offline
    } catch (error: IOException) {
        // Le moteur HTTP remonte ses pannes réseau en `IOException` : c'est le signal
        // « réessayez plus tard », pas « vos identifiants sont faux ».
        AuthOutcome.Offline
    } catch (error: Exception) {
        AuthOutcome.Unexpected(error.message ?: error::class.simpleName.orEmpty())
    }

    private fun classify(error: AuthRestException, forSignIn: Boolean): AuthOutcome =
        when (error.errorCode) {
            AuthErrorCode.EmailNotConfirmed, AuthErrorCode.PhoneNotConfirmed -> AuthOutcome.EmailNotConfirmed
            AuthErrorCode.EmailExists, AuthErrorCode.UserAlreadyExists, AuthErrorCode.Conflict ->
                AuthOutcome.EmailAlreadyUsed
            AuthErrorCode.UserBanned -> AuthOutcome.AccountDisabled
            AuthErrorCode.SignupDisabled, AuthErrorCode.EmailProviderDisabled, AuthErrorCode.PhoneProviderDisabled ->
                AuthOutcome.SignUpDisabled
            AuthErrorCode.WeakPassword -> AuthOutcome.WeakPassword
            AuthErrorCode.UserNotFound -> AuthOutcome.InvalidCredentials
            else -> classifyText(error, forSignIn)
        }

    /**
     * Repli sur le texte du serveur.
     *
     * GoTrue ne donne pas de code exploitable pour un mot de passe faux : il répond
     * « Invalid login credentials » avec un code générique. Le texte est stable et lisible,
     * c'est donc lui qui tranche — après avoir épuisé les codes.
     */
    private fun classifyText(error: RestException, forSignIn: Boolean): AuthOutcome {
        val text = "${error.error} ${error.description.orEmpty()}".lowercase()
        return when {
            "invalid login credentials" in text || "invalid_grant" in text -> AuthOutcome.InvalidCredentials
            "already registered" in text || "already exists" in text -> AuthOutcome.EmailAlreadyUsed
            "not confirmed" in text -> AuthOutcome.EmailNotConfirmed
            "password should be" in text || "password is too short" in text -> AuthOutcome.WeakPassword
            "banned" in text -> AuthOutcome.AccountDisabled
            "signups not allowed" in text || "signup disabled" in text -> AuthOutcome.SignUpDisabled
            // Un refus sans texte reconnu sur un formulaire de connexion est un refus
            // d'identifiants : c'est la seule cause que ce formulaire puisse produire.
            forSignIn && error.statusCode in 400..401 -> AuthOutcome.InvalidCredentials
            else -> AuthOutcome.Unexpected(error.description ?: error.error)
        }
    }

    private fun SessionStatus.toAuthStatus(): AuthStatus = when (this) {
        is SessionStatus.Authenticated ->
            session.user?.id?.let { AuthStatus.signedIn(it) } ?: AuthStatus.SignedOut
        SessionStatus.Initializing -> AuthStatus.Initializing
        // `NotAuthenticated` est une classe de données (elle porte `isSignOut`), pas un
        // objet : il faut la tester par `is`.
        is SessionStatus.NotAuthenticated -> AuthStatus.SignedOut
        // L'utilisateur n'est pas déconnecté : seul le rafraîchissement a échoué. Sa
        // progression locale reste affichée, et la synchronisation attendra le réseau.
        is SessionStatus.RefreshFailure -> AuthStatus.RefreshFailure
    }
}

/**
 * Passerelle inerte, utilisée quand aucun projet Supabase n'est configuré.
 *
 * Elle n'est pas une erreur : l'application doit rester entièrement utilisable hors ligne,
 * avec la totalité du Coran et de la progression locale. Sans cette passerelle, il faudrait
 * tester la configuration à chaque appel d'écran.
 */
object UnavailableAuthGateway : AuthGateway {

    override val status: Flow<AuthStatus> = flowOf(AuthStatus.SignedOut)

    override suspend fun awaitReady() = Unit

    override suspend fun signIn(email: String, password: String): AuthOutcome = AuthOutcome.NotConfigured

    override suspend fun signUp(email: String, password: String): AuthOutcome = AuthOutcome.NotConfigured

    override suspend fun signOut() = Unit

    override suspend fun currentUserId(): String? = null

    override suspend fun resendSignUpConfirmation(email: String): AuthOutcome = AuthOutcome.NotConfigured

    override suspend fun requestPasswordReset(email: String): AuthOutcome = AuthOutcome.NotConfigured
}
