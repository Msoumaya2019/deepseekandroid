package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Éprouve la décision d'ouverture de l'application.
 *
 * C'est une règle courte, et c'est exactement pour cela qu'elle est éprouvée : elle décide si
 * l'utilisateur voit sa progression, un formulaire de connexion, ou un écran d'attente — et
 * une inversion ne se verrait qu'au moment où quelqu'un se retrouve dehors, hors ligne, sans
 * comprendre pourquoi.
 */
class AccountAccessTest {

    private val user = "11111111-2222-3333-4444-555555555555"

    /** Un état de compte, tel que le produit `LocalStateStore` pour un compte sans fichier. */
    private fun accountState() = Program.defaultState().copy(userId = user)

    // ------------------------------------------------------------------
    // Attente
    // ------------------------------------------------------------------

    @Test
    fun `tant que l'etat local n'est pas lu l'application attend`() {
        // `null` n'est pas « pas de compte » : c'est « on ne sait pas encore ». Confondre les
        // deux ferait clignoter l'écran de connexion à chaque lancement, avant que le disque
        // n'ait répondu.
        assertEquals(AccountStage.CHECKING, AccountAccess.stage(null, hasLocalState = false))
        assertEquals(AccountStage.CHECKING, AccountAccess.stage(null, hasLocalState = true))
    }

    @Test
    fun `un compte jamais rapatrie n'ouvre pas l'application`() {
        // Le garde-fou central. Ouvrir ici afficherait un programme vide, et la première
        // séance validée dans ce programme vide serait poussée au serveur, écrasant le vrai
        // compte. L'application doit d'abord lire le serveur.
        assertEquals(
            AccountStage.CHECKING,
            AccountAccess.stage(accountState(), hasLocalState = false),
        )
    }

    // ------------------------------------------------------------------
    // Compte
    // ------------------------------------------------------------------

    @Test
    fun `un compte dont l'etat local existe ouvre l'application`() {
        assertEquals(
            AccountStage.READY,
            AccountAccess.stage(accountState(), hasLocalState = true),
        )
    }

    @Test
    fun `l'application s'ouvre sans reseau des lors que le compte est enregistre`() {
        // Le point de la règle : rien ici ne dépend d'une session ni d'une réponse du serveur.
        val offline = accountState().copy(updatedAt = AppState.EPOCH)

        assertTrue(AccountAccess.stage(offline, hasLocalState = true) == AccountStage.READY)
    }

    @Test
    fun `une progression vide mais ecrite ouvre l'application`() {
        // Le témoin est le **fichier**, pas le contenu : un utilisateur qui vient de créer son
        // compte et n'a encore rien appris doit entrer normalement.
        val vide = accountState()

        assertTrue(vide.knowledge.isEmpty())
        assertEquals(AccountStage.READY, AccountAccess.stage(vide, hasLocalState = true))
    }

    // ------------------------------------------------------------------
    // Absence de compte
    // ------------------------------------------------------------------

    @Test
    fun `un etat sans compte ouvre l'ecran de bienvenue`() {
        assertEquals(
            AccountStage.WELCOME,
            AccountAccess.stage(Program.defaultState(), hasLocalState = false),
        )
    }

    @Test
    fun `l'etat anonyme ne compte pas comme un etat de compte`() {
        // Le fichier anonyme existe dès la première ouverture, sans compte. S'il suffisait à
        // ouvrir, la coquille s'afficherait sans compte et l'écran de connexion deviendrait
        // inatteignable — donc impossible de se connecter un jour.
        assertEquals(
            AccountStage.WELCOME,
            AccountAccess.stage(Program.defaultState(), hasLocalState = true),
        )
    }

    @Test
    fun `un identifiant vide vaut absence de compte`() {
        // JavaScript confond `''` et `null` dans un test de vérité, et la règle d'origine
        // s'appuyait là-dessus. Kotlin ne le fait pas : l'équivalence est donc écrite à la
        // main, et ce test la fixe. Sans elle, un état portant une chaîne vide ouvrirait une
        // coquille anonyme, sans compte et sans moyen d'en créer un.
        assertEquals(AccountStage.WELCOME, AccountAccess.stage(AppState(userId = ""), hasLocalState = true))
        assertEquals(AccountStage.WELCOME, AccountAccess.stage(AppState(userId = "   "), hasLocalState = true))
    }

    @Test
    fun `les trois etapes sont distinctes`() {
        // Garde-fou de portage : les trois valeurs de `accountIntro` doivent rester trois
        // valeurs distinctes, et `CHECKING` ne doit pas se confondre avec `WELCOME`.
        val stages = AccountStage.entries.toSet()

        assertEquals(3, stages.size)
        assertFalse(AccountStage.CHECKING == AccountStage.WELCOME)
    }
}
