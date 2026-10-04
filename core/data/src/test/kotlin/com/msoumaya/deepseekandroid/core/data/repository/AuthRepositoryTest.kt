package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.remote.AuthGateway
import com.msoumaya.deepseekandroid.core.data.remote.OwnerStore
import com.msoumaya.deepseekandroid.core.domain.AuthOutcome
import com.msoumaya.deepseekandroid.core.domain.AuthStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Éprouve le dépôt d'authentification.
 *
 * Deux règles y sont fixées, et ce sont les deux qui pourraient casser en silence :
 *
 *  1. **le propriétaire ne change que sur une session réelle.** Un mot de passe refusé ne doit
 *     pas faire basculer l'application sur le fichier d'état d'un autre compte ;
 *  2. **la connexion ne juge pas la longueur du mot de passe.** Le serveur partagé est seul
 *     juge, sinon l'application Android refuserait un compte que l'application React Native
 *     accepte encore.
 */
class AuthRepositoryTest {

    private val user = "11111111-2222-3333-4444-555555555555"

    // ------------------------------------------------------------------
    // Longueur du mot de passe
    // ------------------------------------------------------------------

    @Test
    fun `une connexion avec un mot de passe court atteint le serveur`() = runTest {
        val gateway = FakeGateway()
        val owners = FakeOwners()

        val outcome = AuthRepository(gateway, owners).signIn("a@b.fr", "1234")

        // Le point : la tentative ne doit pas être refusée localement. Un compte créé sous une
        // politique plus permissive doit pouvoir se connecter, comme il le fait encore dans
        // l'application d'origine.
        assertEquals(listOf("signIn:a@b.fr:1234"), gateway.calls)
        assertEquals(AuthOutcome.Success, outcome)
    }

    @Test
    fun `une inscription avec un mot de passe court est refusee sans appel reseau`() = runTest {
        val gateway = FakeGateway()

        val outcome = AuthRepository(gateway, FakeOwners()).signUp("a@b.fr", "1234")

        assertEquals(AuthOutcome.ShortPassword, outcome)
        assertEquals(emptyList(), gateway.calls, "un refus local ne doit pas coûter un aller-retour")
    }

    @Test
    fun `une connexion sans mot de passe est refusee sans appel reseau`() = runTest {
        val gateway = FakeGateway()

        val outcome = AuthRepository(gateway, FakeOwners()).signIn("a@b.fr", "")

        assertEquals(AuthOutcome.ShortPassword, outcome)
        assertEquals(emptyList(), gateway.calls)
    }

    @Test
    fun `une adresse mal formee est refusee avant tout appel`() = runTest {
        val gateway = FakeGateway()
        val auth = AuthRepository(gateway, FakeOwners())

        assertEquals(AuthOutcome.InvalidEmail, auth.signIn("pas-une-adresse", "motdepasse"))
        assertEquals(AuthOutcome.InvalidEmail, auth.signUp("pas-une-adresse", "motdepasse"))
        assertEquals(AuthOutcome.InvalidEmail, auth.resendSignUpConfirmation("pas-une-adresse"))
        assertEquals(AuthOutcome.InvalidEmail, auth.requestPasswordReset("pas-une-adresse"))
        assertEquals(emptyList(), gateway.calls)
    }

    @Test
    fun `le renvoi de confirmation normalise l'adresse`() = runTest {
        // Le serveur compare des chaînes exactes : sans normalisation, une majuscule de trop
        // ferait répondre « compte inconnu » alors que le compte existe.
        val gateway = FakeGateway()

        assertEquals(AuthOutcome.Success, AuthRepository(gateway, FakeOwners()).resendSignUpConfirmation(" A@B.FR "))

        assertEquals(listOf("resend:a@b.fr"), gateway.calls)
    }

    @Test
    fun `la demande de lien de mot de passe normalise l'adresse`() = runTest {
        val gateway = FakeGateway()

        assertEquals(AuthOutcome.Success, AuthRepository(gateway, FakeOwners()).requestPasswordReset("A@B.fr"))

        assertEquals(listOf("reset:a@b.fr"), gateway.calls)
    }

    // ------------------------------------------------------------------
    // Propriétaire
    // ------------------------------------------------------------------

    @Test
    fun `une connexion reussie retient le proprietaire`() = runTest {
        val gateway = FakeGateway().apply { userId = user }
        val owners = FakeOwners()

        assertEquals(AuthOutcome.Success, AuthRepository(gateway, owners).signIn("a@b.fr", "motdepasse"))

        assertEquals(user, owners.currentOwner())
    }

    @Test
    fun `une connexion refusee n'ecrit aucun proprietaire`() = runTest {
        // Le garde-fou : sans cette règle, une tentative ratée ferait basculer l'application
        // sur le fichier d'état d'un autre compte.
        val gateway = FakeGateway().apply {
            userId = user
            nextSignIn = AuthOutcome.InvalidCredentials
        }
        val owners = FakeOwners()

        assertEquals(
            AuthOutcome.InvalidCredentials,
            AuthRepository(gateway, owners).signIn("a@b.fr", "mauvais"),
        )

        assertNull(owners.currentOwner())
    }

    @Test
    fun `une inscription acceptee sans session demande une confirmation`() = runTest {
        // Le projet Supabase exige une confirmation par courriel : l'inscription réussit mais
        // aucune session ne s'ouvre. L'annoncer, plutôt qu'un succès suivi d'un écran figé.
        val gateway = FakeGateway().apply { userId = null }
        val owners = FakeOwners()

        val outcome = AuthRepository(gateway, owners).signUp("a@b.fr", "motdepasse")

        assertEquals(AuthOutcome.EmailConfirmationRequired, outcome)
        assertNull(owners.currentOwner())
    }

    @Test
    fun `la deconnexion efface le proprietaire`() = runTest {
        val gateway = FakeGateway().apply { userId = user }
        val owners = FakeOwners(initial = user)

        AuthRepository(gateway, owners).signOut()

        assertNull(owners.currentOwner())
    }

    @Test
    fun `un echec de rafraichissement ne deconnecte pas`() = runTest {
        // L'utilisateur n'est pas déconnecté : seul son jeton a expiré. Sa progression locale
        // doit rester à l'écran, donc le propriétaire ne bouge pas.
        val gateway = FakeGateway().apply { userId = user }
        val owners = FakeOwners(initial = user)
        val auth = AuthRepository(gateway, owners)

        gateway.statusFlow.value = AuthStatus.RefreshFailure
        assertEquals(AuthStatus.RefreshFailure, auth.status.first())

        assertEquals(user, owners.currentOwner())
    }

    @Test
    fun `une session absente n'efface pas le proprietaire`() = runTest {
        // Le point le plus facile à casser sans s'en apercevoir. « Pas de session » est
        // produit aussi bien par une déconnexion que par un jeton expiré, un téléphone hors
        // ligne ou un projet injoignable. Traiter ce signal comme une déconnexion retirerait
        // à l'utilisateur sa progression locale exactement quand il n'a pas de réseau — le
        // moment où elle est le seul contenu disponible.
        val gateway = FakeGateway().apply { userId = user }
        val owners = FakeOwners(initial = user)
        val auth = AuthRepository(gateway, owners)

        gateway.statusFlow.value = AuthStatus.SignedOut
        assertEquals(AuthStatus.SignedOut, auth.status.first())

        assertEquals(user, owners.currentOwner(), "une session absente ne doit pas déconnecter")
    }

    @Test
    fun `la reprise du proprietaire n'efface rien en l'absence de session`() = runTest {
        val gateway = FakeGateway().apply { userId = null }
        val owners = FakeOwners(initial = user)

        AuthRepository(gateway, owners).refreshOwner()

        assertEquals(user, owners.currentOwner())
    }

    @Test
    fun `la reprise du proprietaire adopte une session ouverte`() = runTest {
        val gateway = FakeGateway().apply { userId = user }
        val owners = FakeOwners()

        AuthRepository(gateway, owners).refreshOwner()

        assertEquals(user, owners.currentOwner())
    }

    @Test
    fun `une session ouverte met le proprietaire a jour`() = runTest {
        val gateway = FakeGateway()
        val owners = FakeOwners()
        val auth = AuthRepository(gateway, owners)

        gateway.statusFlow.value = AuthStatus.signedIn(user)
        assertEquals(AuthStatus.signedIn(user), auth.status.first())

        assertEquals(user, owners.currentOwner())
    }

    // ------------------------------------------------------------------
    // Doublures
    // ------------------------------------------------------------------

    private class FakeGateway : AuthGateway {
        val calls = mutableListOf<String>()
        val statusFlow = MutableStateFlow<AuthStatus>(AuthStatus.SignedOut)
        var nextSignIn: AuthOutcome = AuthOutcome.Success
        var nextSignUp: AuthOutcome = AuthOutcome.Success
        var nextResend: AuthOutcome = AuthOutcome.Success
        var nextReset: AuthOutcome = AuthOutcome.Success
        var userId: String? = null

        override val status: Flow<AuthStatus> = statusFlow

        override suspend fun awaitReady() = Unit

        override suspend fun signIn(email: String, password: String): AuthOutcome {
            calls += "signIn:$email:$password"
            return nextSignIn
        }

        override suspend fun signUp(email: String, password: String): AuthOutcome {
            calls += "signUp:$email:$password"
            return nextSignUp
        }

        override suspend fun signOut() {
            calls += "signOut"
            userId = null
        }

        override suspend fun currentUserId(): String? = userId

        override suspend fun resendSignUpConfirmation(email: String): AuthOutcome {
            calls += "resend:$email"
            return nextResend
        }

        override suspend fun requestPasswordReset(email: String): AuthOutcome {
            calls += "reset:$email"
            return nextReset
        }
    }

    private class FakeOwners(initial: String? = null) : OwnerStore {
        private val flow = MutableStateFlow(initial)
        override val ownerId: Flow<String?> = flow
        override suspend fun currentOwner(): String? = flow.value
        override suspend fun setOwner(userId: String?) {
            flow.value = userId
        }
    }
}
