package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.Mastery
import com.msoumaya.deepseekandroid.core.model.MushafSource
import com.msoumaya.deepseekandroid.core.model.ReaderPreferences
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Éprouve la composition des deux fusions de compte.
 *
 * Ces tests portent sur la partie du code où une faute ne se voit pas : l'application
 * fonctionne, les écrans s'affichent, et une donnée a disparu. Chaque scénario est donc
 * écrit pour **échouer** si la composition est naïve.
 */
class AccountSyncTest {

    private val user = "11111111-2222-3333-4444-555555555555"
    private val other = "99999999-8888-7777-6666-555555555555"

    @BeforeTest
    fun setUp() {
        QuranFixture.install()
    }

    /**
     * Un état dont le lecteur porte déjà un moushaf non hérité.
     *
     * Le préciser évite que `migrateReaderState` modifie l'état en douce : sans cela, un état
     * lu au serveur ne serait jamais égal à lui-même, et le test mesurerait la migration au
     * lieu de la fusion.
     */
    private fun state(
        owner: String?,
        updatedAt: String,
        knowledge: Map<String, Mastery> = emptyMap(),
    ): AppState = QuranFixture.onboardedState().copy(
        userId = owner,
        updatedAt = updatedAt,
        knowledge = knowledge,
        reader = ReaderPreferences(mushaf = MushafSource.CORAN_1441),
    )

    @Test
    fun `un appareil neuf adopte l'etat du serveur au lieu de pousser le sien`() {
        val remote = state(user, "2026-02-01T10:00:00.000Z", mapOf("1" to Mastery.PERFECT))

        val outcome = AccountSync.merge(
            userId = user,
            cached = null,
            remote = remote,
            base = null,
            pending = false,
        )

        // Le point critique : l'état par défaut de l'appareil ne doit pas écraser le compte.
        assertEquals(mapOf("1" to Mastery.PERFECT), outcome.state.knowledge)
        assertEquals(user, outcome.state.userId)
        assertFalse(outcome.shouldPush, "un appareil neuf n'a rien à pousser")
    }

    @Test
    fun `le travail hors ligne survit a une horloge en retard`() {
        val base = state(user, "2026-02-01T10:00:00.000Z", mapOf("1" to Mastery.PERFECT, "4" to Mastery.LEARNING))
        val local = state(
            user,
            // L'horloge de l'appareil est en retard sur celle du serveur : c'est le cas d'un
            // téléphone mal réglé, pas une hypothèse d'école.
            "2026-02-01T11:00:00.000Z",
            mapOf("1" to Mastery.PERFECT, "2" to Mastery.PERFECT, "4" to Mastery.PERFECT),
        )
        val remote = state(
            user,
            "2026-03-01T09:00:00.000Z",
            mapOf("1" to Mastery.PERFECT, "3" to Mastery.LEARNING, "4" to Mastery.REVIEW),
        )

        val outcome = AccountSync.merge(user, local, remote, base, pending = true)

        assertEquals(Mastery.PERFECT, outcome.state.knowledge["2"], "le verset appris hors ligne a disparu")
        assertEquals(Mastery.PERFECT, outcome.state.knowledge["4"], "le local doit l'emporter sur un conflit")
        assertEquals(Mastery.LEARNING, outcome.state.knowledge["3"], "ce que le serveur apporte est adopté")
        assertTrue(outcome.shouldPush)

        // Témoin : la règle de compte seule, qui tranche à l'horodatage, perdrait le verset 2.
        // Sans cette assertion, le test pourrait passer alors que la fusion à trois voies ne
        // sert à rien.
        val naive = Program.accountState(user, local, remote).state
        assertNull(naive.knowledge["2"], "témoin invalide : la règle de compte seule garderait le verset 2")
        assertEquals(Mastery.REVIEW, naive.knowledge["4"], "témoin invalide : la règle de compte seule garderait le local")
    }

    @Test
    fun `le compte precedent n'est jamais melange au nouveau`() {
        val cached = state(other, "2026-02-01T10:00:00.000Z", mapOf("9" to Mastery.PERFECT))
        val remote = state(user, "2026-02-01T10:00:00.000Z", mapOf("1" to Mastery.PERFECT))

        val outcome = AccountSync.merge(user, cached, remote, base = null, pending = false)

        assertEquals(mapOf("1" to Mastery.PERFECT), outcome.state.knowledge)
        assertNull(outcome.state.knowledge["9"], "la progression d'un autre compte a fui")
        assertEquals(user, outcome.state.userId)
    }

    @Test
    fun `sans serveur joignable l'etat local est conserve et pousse`() {
        val local = state(user, "2026-02-01T10:00:00.000Z", mapOf("2" to Mastery.PERFECT))

        val outcome = AccountSync.merge(user, local, remote = null, base = null, pending = false)

        assertEquals(mapOf("2" to Mastery.PERFECT), outcome.state.knowledge)
        assertTrue(outcome.shouldPush, "sans confirmation du serveur, l'état local doit être renvoyé")
    }

    @Test
    fun `une operation en attente force le renvoi meme si le serveur parait plus recent`() {
        val base = state(user, "2026-01-01T00:00:00.000Z")
        val local = state(user, "2026-01-01T00:00:00.000Z")
        val remote = state(user, "2026-03-01T00:00:00.000Z", mapOf("1" to Mastery.PERFECT))

        val idle = AccountSync.merge(user, local, remote, base, pending = false)
        val queued = AccountSync.merge(user, local, remote, base, pending = true)

        assertFalse(idle.shouldPush, "rien à envoyer : le serveur porte déjà cet état")
        assertTrue(queued.shouldPush, "une opération en file doit partir")
        assertEquals(mapOf("1" to Mastery.PERFECT), queued.state.knowledge)
    }

    @Test
    fun `l'identifiant du compte est toujours estampille`() {
        val remote = state(null, "2026-02-01T10:00:00.000Z", mapOf("1" to Mastery.PERFECT))

        val outcome = AccountSync.merge(user, cached = null, remote = remote, base = null, pending = false)

        assertEquals(user, outcome.state.userId)
    }
}
