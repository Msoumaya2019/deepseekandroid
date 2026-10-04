package com.msoumaya.deepseekandroid.feature.auth

import com.msoumaya.deepseekandroid.core.domain.AuthInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Éprouve les règles d'activation du formulaire de connexion.
 *
 * Ce sont les seules règles de cet écran qui puissent être fausses sans que cela se voie : un
 * bouton actif pour une saisie que la règle refuse fait cliquer pour rien, et un bouton inactif
 * pour une saisie valide bloque sans expliquer pourquoi.
 */
class AuthUiStateTest {

    private fun login(email: String = "a@b.fr", password: String = "motdepasse") =
        AuthUiState(mode = AuthMode.LOGIN, email = email, password = password)

    private fun signup(email: String = "a@b.fr", password: String = "motdepasse") =
        AuthUiState(mode = AuthMode.SIGNUP, email = email, password = password)

    // ------------------------------------------------------------------
    // Choix initial
    // ------------------------------------------------------------------

    @Test
    fun `aucun bouton de soumission avant le choix du mode`() {
        // L'écran d'accueil propose trois boutons ; le formulaire n'existe pas encore, donc
        // rien ne doit pouvoir être soumis.
        assertFalse(AuthUiState(email = "a@b.fr", password = "motdepasse").submitEnabled)
    }

    // ------------------------------------------------------------------
    // Adresse
    // ------------------------------------------------------------------

    @Test
    fun `une adresse sans arobase n'active rien`() {
        assertFalse(login(email = "jean.laposte").submitEnabled)
        assertFalse(signup(email = "jean.laposte").submitEnabled)
        assertFalse(login(email = "").emailReady)
    }

    @Test
    fun `une adresse avec arobase suffit a activer la connexion`() {
        // La règle est volontairement lâche, comme dans l'original : `a@b` n'est pas une
        // adresse valide, mais le bouton doit rester actif pour que l'appui déclenche le
        // contrôle complet, qui explique ce qui manque. Un bouton grisé n'explique rien.
        assertTrue(login(email = "a@b").submitEnabled)
    }

    // ------------------------------------------------------------------
    // Mot de passe
    // ------------------------------------------------------------------

    @Test
    fun `la connexion n'exige qu'un mot de passe non vide`() {
        // Et non la longueur minimale : le serveur partagé est seul juge de ce qu'un compte
        // existant accepte. Voir `AuthInput.emptyPasswordProblem`.
        assertTrue(login(password = "x").submitEnabled)
        assertFalse(login(password = "").submitEnabled)
    }

    @Test
    fun `l'inscription exige la longueur minimale`() {
        val court = "x".repeat(AuthInput.MIN_PASSWORD - 1)
        val juste = "x".repeat(AuthInput.MIN_PASSWORD)

        assertFalse(signup(password = court).submitEnabled)
        assertTrue(signup(password = juste).submitEnabled)
    }

    @Test
    fun `le seuil affiche et le seuil applique sont le meme`() {
        // Deux constantes séparées finiraient par diverger, et le bouton s'activerait pour un
        // mot de passe que la règle refuse.
        val hint = signup().passwordHint

        assertTrue(hint.contains(AuthInput.MIN_PASSWORD.toString()), hint)
    }

    // ------------------------------------------------------------------
    // Occupation
    // ------------------------------------------------------------------

    @Test
    fun `rien n'est soumis pendant une tentative en cours`() {
        assertFalse(login().copy(busy = true).submitEnabled)
        assertFalse(signup().copy(busy = true).submitEnabled)
        assertFalse(login().copy(busy = true).emailReady)
    }

    // ------------------------------------------------------------------
    // Libellés
    // ------------------------------------------------------------------

    @Test
    fun `les libelles suivent le mode`() {
        assertEquals("Se connecter", login().formTitle)
        assertEquals("Créer mon compte", signup().formTitle)
        assertEquals("Se connecter", login().submitLabel)
        assertEquals("Créer mon compte", signup().submitLabel)
    }

    @Test
    fun `le libelle du bouton annonce l'attente`() {
        // Sans cela, un appui sur un réseau lent laisse croire que rien ne s'est passé.
        assertEquals("Connexion…", login().copy(busy = true).submitLabel)
    }
}
