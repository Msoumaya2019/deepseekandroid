package com.msoumaya.deepseekandroid.core.domain

/**
 * État de la session, indépendant de la bibliothèque d'authentification.
 *
 * Le domaine ne connaît pas Supabase : ces types décrivent ce que l'interface doit montrer,
 * ce qui permet d'éprouver la logique de connexion sans réseau.
 */
enum class AuthPhase {
    /** La session est en cours de relecture depuis le disque. */
    INITIALIZING,

    /** Aucune session : l'application s'utilise en local. */
    SIGNED_OUT,

    /** Une session est ouverte. */
    SIGNED_IN,

    /**
     * La session existe mais le jeton n'a pas pu être rafraîchi.
     *
     * C'est un état distinct de [SIGNED_OUT] : l'utilisateur n'est pas déconnecté, et sa
     * progression locale est intacte. On ne le renvoie pas vers l'écran de connexion sans
     * raison — il retrouve ses données, et seule la synchronisation attend.
     */
    REFRESH_FAILURE,
}

data class AuthStatus(
    val phase: AuthPhase,
    val userId: String? = null,
) {
    val isSignedIn: Boolean get() = phase == AuthPhase.SIGNED_IN && userId != null

    companion object {
        val Initializing = AuthStatus(AuthPhase.INITIALIZING)
        val SignedOut = AuthStatus(AuthPhase.SIGNED_OUT)
        val RefreshFailure = AuthStatus(AuthPhase.REFRESH_FAILURE)

        fun signedIn(userId: String) = AuthStatus(AuthPhase.SIGNED_IN, userId)
    }
}

/**
 * Résultat d'une tentative de connexion ou d'inscription.
 *
 * Les cas sont séparés parce qu'ils appellent des messages différents : « mot de passe
 * incorrect » n'est pas « pas de réseau », et confondre les deux fait croire à l'utilisateur
 * que son mot de passe est faux alors que son téléphone est simplement hors ligne.
 */
sealed interface AuthOutcome {
    data object Success : AuthOutcome

    /** Identifiants refusés, ou compte inexistant. */
    data object InvalidCredentials : AuthOutcome

    /** Le compte existe mais l'adresse n'a pas été confirmée. */
    data object EmailNotConfirmed : AuthOutcome

    /** L'adresse est déjà utilisée par un autre compte. */
    data object EmailAlreadyUsed : AuthOutcome

    /** Le compte est suspendu côté serveur. */
    data object AccountDisabled : AuthOutcome

    /** Les inscriptions sont fermées sur ce projet. */
    data object SignUpDisabled : AuthOutcome

    /** Mot de passe refusé par la politique du serveur. */
    data object WeakPassword : AuthOutcome

    /** Réseau indisponible : la tentative peut être refaite telle quelle. */
    data object Offline : AuthOutcome

    /**
     * L'inscription a réussi mais aucune session n'a été ouverte : le projet exige une
     * confirmation de l'adresse par courriel. Ce n'est pas un échec, et l'écran doit le dire
     * — sinon l'utilisateur croit que son inscription n'a pas fonctionné.
     */
    data object EmailConfirmationRequired : AuthOutcome

    /**
     * Aucun projet Supabase n'est configuré dans cette compilation.
     *
     * L'application reste pleinement utilisable hors ligne ; seule la synchronisation est
     * indisponible. Ce cas est distinct de [Offline] : réessayer n'y changerait rien.
     */
    data object NotConfigured : AuthOutcome

    /** Adresse mal formée. Refusée avant tout appel réseau. */
    data object InvalidEmail : AuthOutcome

    /** Mot de passe trop court. Refusé avant tout appel réseau. */
    data object ShortPassword : AuthOutcome

    data class Unexpected(val message: String) : AuthOutcome
}

/**
 * Contrôles faits **avant** l'appel réseau.
 *
 * Le serveur reste l'autorité — la longueur minimale réelle est décidée par le projet
 * Supabase. Mais refuser localement une adresse manifestement mal formée évite un
 * aller-retour, et surtout évite de faire croire à une panne réseau là où la saisie est en
 * cause. Le seuil de six caractères correspond au minimum par défaut de Supabase ; un mot de
 * passe plus court est refusé par le serveur de toute façon.
 */
object AuthInput {

    /**
     * Longueur minimale exigée à l'**inscription**.
     *
     * Publique parce que le formulaire doit l'afficher et s'y conformer : c'est le même
     * chiffre qui écrit « au moins 6 caractères » sous le champ et qui active le bouton. Deux
     * constantes séparées finiraient par diverger, et le bouton deviendrait actif pour un mot
     * de passe que la règle refuse.
     */
    const val MIN_PASSWORD = 6

    fun emailProblem(email: String): AuthOutcome? {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return AuthOutcome.InvalidEmail
        val at = trimmed.indexOf('@')
        if (at <= 0 || at == trimmed.length - 1) return AuthOutcome.InvalidEmail
        val domain = trimmed.substring(at + 1)
        if (!domain.contains('.') || domain.startsWith('.') || domain.endsWith('.')) {
            return AuthOutcome.InvalidEmail
        }
        if (trimmed.any { it.isWhitespace() }) return AuthOutcome.InvalidEmail
        return null
    }

    /** Contrôle d'**inscription** : la longueur minimale est exigée. */
    fun passwordProblem(password: String): AuthOutcome? =
        if (password.length < MIN_PASSWORD) AuthOutcome.ShortPassword else null

    /**
     * Contrôle de **connexion** : seul le vide est refusé.
     *
     * La connexion ne vérifie pas la longueur minimale, et c'est délibéré. Le serveur reste
     * seul juge de ce qu'un compte existant accepte ; refuser localement un mot de passe court
     * bloquerait un compte dont le mot de passe a été créé sous une politique plus permissive
     * — c'est-à-dire un compte qui fonctionne encore dans l'application React Native. Comme
     * les deux applications partagent la même base, une règle locale plus stricte ici
     * fermerait une porte restée ouverte là-bas.
     *
     * Un mot de passe vide, lui, ne peut rien ouvrir : le refuser évite un aller-retour réseau
     * pour rien.
     */
    fun emptyPasswordProblem(password: String): AuthOutcome? =
        if (password.isEmpty()) AuthOutcome.ShortPassword else null

    /** Normalise l'adresse avant envoi : le serveur compare des chaînes exactes. */
    fun normalizeEmail(email: String): String = email.trim().lowercase()
}
