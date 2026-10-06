package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.remote.OwnerStore
import com.msoumaya.deepseekandroid.core.data.remote.SocialInbox
import com.msoumaya.deepseekandroid.core.data.remote.SocialSource
import com.msoumaya.deepseekandroid.core.domain.SocialText
import com.msoumaya.deepseekandroid.core.model.ConversationSummary
import com.msoumaya.deepseekandroid.core.model.FriendBrief
import com.msoumaya.deepseekandroid.core.model.FriendGroup
import com.msoumaya.deepseekandroid.core.model.FriendLink
import com.msoumaya.deepseekandroid.core.model.FriendLinkStatus
import com.msoumaya.deepseekandroid.core.model.FriendProfile
import com.msoumaya.deepseekandroid.core.model.SocialSuspension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Éprouve le dépôt « Amis » : ce qu'il lit, dans quel ordre, et ce qu'un échec partiel fait à
 * ce qui est déjà à l'écran.
 *
 * ## Ce qui est mesuré ici, et pourquoi c'est ici
 *
 * Le dépôt social ne contient que des règles d'**ordonnancement et de survie** : ne pas
 * interroger le réseau sans compte, ne pas effacer une liste d'amis parce que les cercles ont
 * échoué, ne pas garder les amis du compte précédent, ne pas demander la boîte de réception
 * sans ami accepté. Aucune ne lève d'exception quand elle est fausse : la première affiche une
 * erreur réseau à quelqu'un qui n'est pas connecté, la deuxième fait disparaître des amis, la
 * troisième montre les conversations d'un autre compte, et la quatrième coûte un aller-retour
 * à chaque ouverture. Aucune de ces quatre fautes ne casse quoi que ce soit — elles mentent,
 * et c'est pour cela qu'elles sont figées.
 *
 * ## La doublure
 *
 * [FakeSocialSource] est en mémoire, compte ses appels, et sait refuser **séparément** les
 * lectures principales, les compléments, la boîte de réception et les gestes. C'est cette
 * séparation qui rend mesurable ce que le client d'origine ne distinguait pas : un échec des
 * cercles et un échec de la liste d'amis n'ont pas les mêmes conséquences.
 *
 * ## Pourquoi [settle] et non `advanceUntilIdle`
 *
 * Le chargement part d'un collecteur lancé à la construction, donc dans le `backgroundScope` du
 * test. `advanceUntilIdle()` **n'exécute pas** ce travail-là, et le fait est déjà mesuré dans ce
 * dépôt — voir `AudioSessionControllerTest`, qui porte la même note et le même remède. Ici la
 * conséquence serait pire qu'ailleurs : dix tests sur dix-huit lisaient l'état **initial** du
 * dépôt, et quatre d'entre eux passaient quand même — ceux qui demandent de constater une
 * absence. Un contrôle qui ne dit plus ce qu'il annonce est pire qu'aucun contrôle.
 */
class SocialRepositoryTest {

    private val moi = "moi-0000"
    private val ami = "ami-0000"

    // ------------------------------------------------------------------
    // Sans compte
    // ------------------------------------------------------------------

    @Test
    fun `sans compte ouvert, la source n'est jamais interrogee`() = runTest {
        val source = FakeSocialSource()
        val repository = SocialRepository(source, FakeOwners(null), backgroundScope)
        settle()

        assertEquals(0, source.calls, "aucune lecture ne doit partir sans compte ouvert")
        assertEquals(0, source.inboxCalls, "aucune boite de reception ne doit partir sans compte")
        assertTrue(source.actions.isEmpty(), "aucun geste ne doit partir sans compte ouvert")
    }

    @Test
    fun `sans compte, l'ecran n'est ni en chargement ni en panne`() = runTest {
        val repository = SocialRepository(FakeSocialSource(), FakeOwners(null), backgroundScope)
        settle()

        val state = repository.state.value
        assertFalse(state.loading, "une attente sans fin se lirait comme un chargement qui n'aboutit pas")
        assertFalse(state.signedIn)
        assertNull(state.failure, "l'absence de compte n'est pas une panne")
        assertNull(state.profile)
    }

    // ------------------------------------------------------------------
    // Chargement
    // ------------------------------------------------------------------

    @Test
    fun `un compte ouvert charge le profil, les liens et la boite de reception`() = runTest {
        val source = FakeSocialSource().apply { links = listOf(lien("l1", FriendLinkStatus.ACCEPTED)) }
        val repository = SocialRepository(source, FakeOwners(moi), backgroundScope)
        settle()

        val state = repository.state.value
        assertTrue(state.signedIn)
        assertFalse(state.loading)
        assertNotNull(state.profile)
        assertEquals(1, state.links.size)
        assertEquals(1, source.inboxCalls)
    }

    @Test
    fun `un echec de la premiere lecture est une panne, pas une liste vide`() = runTest {
        val source = FakeSocialSource().apply {
            failEssentials = IllegalStateException("reseau injoignable")
        }
        val repository = SocialRepository(source, FakeOwners(moi), backgroundScope)
        settle()

        val state = repository.state.value
        assertEquals("reseau injoignable", state.failure)
        assertFalse(state.loading)
        assertNull(state.profile)
        assertTrue(
            state.links.isEmpty() && state.failure != null,
            "une liste vide sans panne se lirait « tu n'as pas d'amis » — une affirmation fausse",
        )
    }

    @Test
    fun `un message d'erreur absent donne le repli generique`() = runTest {
        val source = FakeSocialSource().apply { failEssentials = RuntimeException() }
        val repository = SocialRepository(source, FakeOwners(moi), backgroundScope)
        settle()

        assertEquals(SocialText.GENERIC_ERROR, repository.state.value.failure)
    }

    // ------------------------------------------------------------------
    // Échecs partiels
    // ------------------------------------------------------------------

    @Test
    fun `un echec des cercles ne retire pas les amis deja charges`() = runTest {
        val source = FakeSocialSource().apply {
            links = listOf(lien("l1", FriendLinkStatus.ACCEPTED))
            failExtras = IllegalStateException("cercles indisponibles")
        }
        val repository = SocialRepository(source, FakeOwners(moi), backgroundScope)
        settle()

        val state = repository.state.value
        assertEquals(1, state.links.size, "les cercles ne sont pas la liste d'amis")
        assertNotNull(state.profile)
        assertNull(state.failure, "un complement illisible n'est pas une panne de l'ecran")
        assertEquals("cercles indisponibles", state.notice)
    }

    @Test
    fun `un echec de la boite de reception ne retire pas les amis deja charges`() = runTest {
        val source = FakeSocialSource().apply {
            links = listOf(lien("l1", FriendLinkStatus.ACCEPTED))
            failInbox = IllegalStateException("apercus indisponibles")
        }
        val repository = SocialRepository(source, FakeOwners(moi), backgroundScope)
        settle()

        val state = repository.state.value
        assertEquals(1, state.links.size)
        assertNull(state.failure)
        assertEquals("apercus indisponibles", state.notice)
        assertTrue(state.summaries.isEmpty())
    }

    @Test
    fun `un echec de relecture garde la liste et se signale`() = runTest {
        val source = FakeSocialSource().apply { links = listOf(lien("l1", FriendLinkStatus.ACCEPTED)) }
        val repository = SocialRepository(source, FakeOwners(moi), backgroundScope)
        settle()

        source.failEssentials = IllegalStateException("coupure")
        assertFalse(repository.refresh(), "la relecture a echoue")

        val state = repository.state.value
        assertEquals(1, state.links.size, "un echec de relecture ne rend pas faux ce qu'on a lu")
        assertNull(state.failure, "la panne ne doit pas remplacer une liste deja affichee")
        assertEquals("coupure", state.notice)
    }

    // ------------------------------------------------------------------
    // Boîte de réception
    // ------------------------------------------------------------------

    @Test
    fun `la boite de reception n'est pas demandee sans ami accepte`() = runTest {
        val source = FakeSocialSource().apply {
            links = listOf(lien("l1", FriendLinkStatus.PENDING), lien("l2", FriendLinkStatus.BLOCKED))
        }
        val repository = SocialRepository(source, FakeOwners(moi), backgroundScope)
        settle()

        assertEquals(0, source.inboxCalls, "une fonction qui rendrait un tableau vide, au prix d'un aller-retour")
    }

    @Test
    fun `la boite de reception est demandee des qu'un ami est accepte`() = runTest {
        val source = FakeSocialSource().apply {
            links = listOf(lien("l1", FriendLinkStatus.PENDING), lien("l2", FriendLinkStatus.ACCEPTED))
        }
        val repository = SocialRepository(source, FakeOwners(moi), backgroundScope)
        settle()

        assertEquals(1, source.inboxCalls)
    }

    @Test
    fun `les apercus et les presences ne sont pas confondus`() = runTest {
        val source = FakeSocialSource().apply {
            links = listOf(lien("l1", FriendLinkStatus.ACCEPTED))
            inbox = SocialInbox(
                summaries = mapOf("l1" to ConversationSummary("Salut", "2026-01-02T10:00:00Z", 2)),
                statuses = mapOf(ami to true),
            )
        }
        val repository = SocialRepository(source, FakeOwners(moi), backgroundScope)
        settle()

        val state = repository.state.value
        assertEquals(setOf("l1"), state.summaries.keys, "les apercus sont indexes par lien")
        assertEquals(setOf(ami), state.online.keys, "les presences sont indexees par personne")
        assertEquals(2, state.summaries.getValue("l1").unread)
        assertTrue(state.online.getValue(ami))
    }

    // ------------------------------------------------------------------
    // Gestes
    // ------------------------------------------------------------------

    @Test
    fun `un geste occupe l'ecran pendant tout son appel`() = runTest {
        val source = FakeSocialSource()
        val repository = SocialRepository(source, FakeOwners(moi), backgroundScope)
        settle()

        var pendant = false
        source.onAction = { pendant = repository.state.value.busy }

        assertTrue(repository.requestFriend("CODE1234"))
        assertTrue(pendant, "sans `busy` pendant l'appel, un double appui enverrait deux invitations")
        assertFalse(repository.state.value.busy, "`busy` doit retomber, sinon l'ecran reste bloque")
    }

    @Test
    fun `un geste reussi confirme puis relit`() = runTest {
        val source = FakeSocialSource()
        val repository = SocialRepository(source, FakeOwners(moi), backgroundScope)
        settle()

        // Le serveur « repond » en ajoutant le lien : c'est la relecture qui doit l'apporter.
        source.onAction = { source.links = listOf(lien("l9", FriendLinkStatus.PENDING)) }

        assertTrue(repository.requestFriend("CODE1234"))
        assertEquals(SocialText.INVITATION_SENT, repository.state.value.notice)
        assertEquals(listOf("l9"), repository.state.value.links.map { it.id })
        assertEquals(listOf("requestFriend"), source.actions)
    }

    @Test
    fun `un geste refuse se signale et laisse l'etat en place`() = runTest {
        val source = FakeSocialSource().apply { links = listOf(lien("l1", FriendLinkStatus.ACCEPTED)) }
        val repository = SocialRepository(source, FakeOwners(moi), backgroundScope)
        settle()

        source.failAction = IllegalStateException("Invitation ou relation deja existante")
        assertFalse(repository.requestFriend("CODE1234"))

        val state = repository.state.value
        assertEquals("Invitation ou relation deja existante", state.notice)
        assertFalse(state.busy)
        assertEquals(1, state.links.size, "un geste refuse ne change pas ce qui est a l'ecran")
        assertNull(state.failure)
    }

    @Test
    fun `chaque geste de la liste part par sa propre fonction`() = runTest {
        val source = FakeSocialSource()
        val repository = SocialRepository(source, FakeOwners(moi), backgroundScope)
        settle()

        repository.acceptInvitation("l1")
        repository.declineInvitation("l2")
        repository.removeFriend("l3")
        repository.block(ami)
        repository.unblock(ami)
        repository.createGroup("Les amis")

        assertEquals(
            listOf(
                "acceptFriend",
                "declineFriend",
                "removeFriend",
                "blockFriend",
                "unblockFriend",
                "createGroup",
            ),
            source.actions,
            "deux gestes branches sur la meme fonction feraient l'un des deux sans effet",
        )
    }

    @Test
    fun `sans source, un geste ne fait rien et ne se plaint pas`() = runTest {
        val repository = SocialRepository(null, FakeOwners(moi), backgroundScope)
        settle()

        assertFalse(repository.requestFriend("CODE1234"))
        assertFalse(repository.state.value.busy)
        assertNull(repository.state.value.notice, "il n'y a pas de reseau a atteindre : ce n'est pas une erreur")
    }

    @Test
    fun `ouvrir le contact administrateur ne confirme pas, il ouvre`() = runTest {
        val source = FakeSocialSource()
        val repository = SocialRepository(source, FakeOwners(moi), backgroundScope)
        settle()

        assertEquals("g-contact", repository.openAdminContact())
        assertEquals(listOf("openAdminContact"), source.actions)
        assertNull(repository.state.value.notice, "un « Enregistre. » annoncerait une reussite que personne ne voit")
    }

    @Test
    fun `ouvrir le contact administrateur qui echoue se signale et rend null`() = runTest {
        val source = FakeSocialSource().apply { failAction = IllegalStateException("Administration indisponible") }
        val repository = SocialRepository(source, FakeOwners(moi), backgroundScope)
        settle()

        assertNull(repository.openAdminContact())
        assertEquals("Administration indisponible", repository.state.value.notice)
        assertFalse(repository.state.value.busy)
    }

    // ------------------------------------------------------------------
    // Comptes
    // ------------------------------------------------------------------

    @Test
    fun `la deconnexion efface les amis du compte precedent`() = runTest {
        val source = FakeSocialSource().apply {
            links = listOf(lien("l1", FriendLinkStatus.ACCEPTED))
            inbox = SocialInbox(
                summaries = mapOf("l1" to ConversationSummary("Salut", "2026-01-02T10:00:00Z", 1)),
                statuses = mapOf(ami to true),
            )
        }
        val owners = FakeOwners(moi)
        val repository = SocialRepository(source, owners, backgroundScope)
        settle()
        assertNotNull(repository.state.value.profile)

        owners.setOwner(null)
        settle()

        val state = repository.state.value
        assertNull(state.profile, "les amis du compte precedent ne doivent pas survivre a la deconnexion")
        assertTrue(state.links.isEmpty())
        assertTrue(state.summaries.isEmpty())
        assertTrue(state.online.isEmpty())
        assertFalse(state.signedIn)
    }

    @Test
    fun `le changement de compte ne garde rien du precedent`() = runTest {
        val source = FakeSocialSource().apply { links = listOf(lien("l1", FriendLinkStatus.ACCEPTED)) }
        val owners = FakeOwners(moi)
        val repository = SocialRepository(source, owners, backgroundScope)
        settle()

        source.links = listOf(lien("l2", FriendLinkStatus.ACCEPTED))
        owners.setOwner("autre-1111")
        settle()

        assertEquals(listOf("l2"), repository.state.value.links.map { it.id })
        assertNull(repository.state.value.summaries["l1"])
    }

    @Test
    fun `une relecture remplace les liens au lieu de les accumuler`() = runTest {
        val source = FakeSocialSource().apply { links = listOf(lien("l1", FriendLinkStatus.ACCEPTED)) }
        val repository = SocialRepository(source, FakeOwners(moi), backgroundScope)
        settle()

        source.links = listOf(lien("l2", FriendLinkStatus.ACCEPTED))
        repository.refresh()

        assertEquals(listOf("l2"), repository.state.value.links.map { it.id })
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    /**
     * Fait avancer l'horloge jusqu'à ce que le chargement ait fini de réagir.
     *
     * Le remède est celui d'`AudioSessionControllerTest`, et pour la même raison : le travail
     * lancé dans le `backgroundScope` n'est pas exécuté par `advanceUntilIdle()`.
     */
    private fun TestScope.settle(millis: Long = 1_000) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private fun lien(
        id: String,
        status: FriendLinkStatus,
        autre: String = ami,
    ) = FriendLink(
        id = id,
        requesterId = moi,
        recipientId = autre,
        status = status,
        createdAt = "2026-01-01T10:00:00Z",
        other = FriendBrief(id = autre, displayName = "Ami $autre"),
    )

    private class FakeOwners(owner: String?) : OwnerStore {
        private val flow = MutableStateFlow(owner)
        override val ownerId: Flow<String?> = flow
        override suspend fun currentOwner(): String? = flow.value
        override suspend fun setOwner(userId: String?) {
            flow.value = userId
        }
    }

    /**
     * Doublure en mémoire, qui compte ses appels et refuse **par catégorie**.
     *
     * Le découpage des refus n'est pas un confort de test : c'est la seule façon de mesurer
     * qu'un échec des cercles et un échec de la liste d'amis n'ont pas le même effet. Une
     * doublure qui refuserait tout d'un bloc ne distinguerait pas les deux règles.
     */
    private class FakeSocialSource : SocialSource {

        var profile = FriendProfile(id = "moi-0000", displayName = "Moi", inviteCode = "CODE1234")
        var links: List<FriendLink> = emptyList()
        var groups: List<FriendGroup> = emptyList()
        var suspension: SocialSuspension? = null
        var inbox = SocialInbox()

        var failEssentials: Throwable? = null
        var failExtras: Throwable? = null
        var failInbox: Throwable? = null
        var failAction: Throwable? = null

        /** Appelé **pendant** un geste, donc pendant que le dépôt est occupé. */
        var onAction: (() -> Unit)? = null

        var calls = 0
        var inboxCalls = 0
        val actions = mutableListOf<String>()

        private fun <T> refuser(error: Throwable?): T = throw error!!

        override suspend fun ensureProfile(): FriendProfile {
            calls++
            failEssentials?.let { refuser<Nothing>(it) }
            return profile
        }

        override suspend fun links(userId: String): List<FriendLink> {
            calls++
            failEssentials?.let { refuser<Nothing>(it) }
            return links
        }

        override suspend fun groups(): List<FriendGroup> {
            calls++
            failExtras?.let { refuser<Nothing>(it) }
            return groups
        }

        override suspend fun suspension(userId: String): SocialSuspension? {
            calls++
            failExtras?.let { refuser<Nothing>(it) }
            return suspension
        }

        override suspend fun inbox(links: List<FriendLink>): SocialInbox {
            inboxCalls++
            failInbox?.let { refuser<Nothing>(it) }
            return inbox
        }

        override suspend fun requestFriend(code: String) = geste("requestFriend")

        override suspend fun acceptFriend(linkId: String) = geste("acceptFriend")

        override suspend fun declineFriend(linkId: String) = geste("declineFriend")

        override suspend fun removeFriend(linkId: String) = geste("removeFriend")

        override suspend fun blockFriend(otherId: String) = geste("blockFriend")

        override suspend fun unblockFriend(otherId: String) = geste("unblockFriend")

        override suspend fun createGroup(name: String): String {
            geste("createGroup")
            return "g-1"
        }

        override suspend fun openAdminContact(): String {
            geste("openAdminContact")
            return "g-contact"
        }

        private fun geste(nom: String) {
            actions += nom
            onAction?.invoke()
            failAction?.let { refuser<Nothing>(it) }
        }
    }
}
