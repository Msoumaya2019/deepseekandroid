package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.remote.ChatRoom
import com.msoumaya.deepseekandroid.core.data.remote.SocialInbox
import com.msoumaya.deepseekandroid.core.domain.Social
import com.msoumaya.deepseekandroid.core.domain.SocialText
import com.msoumaya.deepseekandroid.core.model.ChatMessage
import com.msoumaya.deepseekandroid.core.model.ChatMessageKind
import com.msoumaya.deepseekandroid.core.model.ConversationSummary
import com.msoumaya.deepseekandroid.core.model.FriendBrief
import com.msoumaya.deepseekandroid.core.model.FriendGroup
import com.msoumaya.deepseekandroid.core.model.FriendLink
import com.msoumaya.deepseekandroid.core.model.FriendLinkStatus
import com.msoumaya.deepseekandroid.core.model.FriendOverview
import com.msoumaya.deepseekandroid.core.model.GroupMember
import com.msoumaya.deepseekandroid.core.model.GroupRole
import com.msoumaya.deepseekandroid.core.model.ReviewAppointment
import com.msoumaya.deepseekandroid.core.model.SharedGoal
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
 * Éprouve le dépôt de la **conversation** : quelle page il lit, dans quel ordre, ce qu'il fait
 * d'un curseur, et ce qu'un échec partiel laisse à l'écran.
 *
 * ## Ce qui est mesuré ici, et pourquoi
 *
 * Aucune règle de ce fichier ne lève d'exception quand elle est fausse — elles mentent toutes :
 *
 *  - ouvrir une pièce en gardant les messages de la précédente affiche les propos d'un ami
 *    **sous le nom d'un autre** ;
 *  - remonter l'historique sans trier par instant met la **fin** de la conversation au début ;
 *  - demander la page ancienne à partir du mauvais message rend une page qui **recouvre** la
 *    précédente, ou qui la saute ;
 *  - refermer le bouton « Charger les messages précédents » après une page pleine, ou l'ouvrir
 *    après une page incomplète, annonce un historique qui n'existe pas — ou cache celui qui
 *    existe ;
 *  - laisser un aperçu illisible effacer les messages fait disparaître une conversation qu'on
 *    est en train de lire.
 *
 * ## La doublure
 *
 * [FakeSocialSource] et [FakeOwners] viennent de `SocialTestDoubles.kt` : les mêmes que
 * `SocialRepositoryTest`. Les refus y sont découpés par **catégorie** — la lecture des messages,
 * une page plus ancienne, les compléments de la pièce, l'aperçu de l'ami —, et c'est ce découpage
 * qui rend mesurable qu'un échec de l'aperçu et un échec des messages n'ont pas le même effet.
 *
 * ## Pourquoi [settle] et non `advanceUntilIdle`
 *
 * `openRoom` lance sa lecture dans la portée du dépôt, donc dans le `backgroundScope` du test.
 * `advanceUntilIdle()` **n'exécute pas** ce travail-là : la note complète et sa mesure sont dans
 * `SocialRepositoryTest`.
 */
class ConversationRepositoryTest {

    private val moi = "moi-0000"
    private val ami = "ami-0000"

    // ------------------------------------------------------------------
    // Ouverture
    // ------------------------------------------------------------------

    @Test
    fun `ouvrir un lien lit sa page, marque comme lu et lit la position de l'autre`() = runTest {
        val source = FakeSocialSource().apply {
            links = listOf(lien("l1"))
            messagePage = page(2, debut = 10, prefixe = "m")
            readAt = "2026-03-10T12:00:00Z"
        }
        val repository = repository(source)
        settle()

        repository.openRoom(ChatRoom(linkId = "l1"))
        settle()

        val room = assertNotNull(repository.state.value.room)
        assertEquals(listOf("m0", "m1"), room.messages.map { it.id })
        assertEquals("2026-03-10T12:00:00Z", room.otherReadAt)
        assertEquals(1, source.readMarks, "une conversation ouverte est une conversation lue")
        assertEquals(listOf<String?>(null), source.cursors, "la premiere page n'a pas de curseur")
        assertEquals(listOf("link:l1"), source.rooms)
    }

    @Test
    fun `ouvrir un cercle lit ses membres au lieu de marquer comme lu`() = runTest {
        val source = FakeSocialSource().apply {
            groups = listOf(groupe("g1"))
            memberList = listOf(membre("moi-0000", GroupRole.OWNER))
        }
        val repository = repository(source)
        settle()

        repository.openRoom(ChatRoom(groupId = "g1"))
        settle()

        val room = assertNotNull(repository.state.value.room)
        assertEquals(1, room.members.size)
        assertEquals(0, source.readMarks, "on ne marque pas un cercle comme lu : il n'a pas de position")
        assertEquals(listOf("group:g1"), source.rooms)
    }

    @Test
    fun `le cercle de l'administration ne demande ni apercu ni objectifs`() = runTest {
        val source = FakeSocialSource().apply {
            groups = listOf(groupe("g-admin", contact = "admin-0000"))
            memberList = listOf(membre("moi-0000", GroupRole.MEMBER))
            goalList = listOf(SharedGoal("o1", "l1", "2026-03-09", 5, "lui-0000"))
            friendOverview = FriendOverview(id = "admin-0000", displayName = "Admin")
        }
        val repository = repository(source)
        settle()

        repository.openRoom(ChatRoom(groupId = "g-admin"))
        settle()

        val room = assertNotNull(repository.state.value.room)
        assertEquals(1, room.members.size, "les membres, eux, se lisent")
        assertEquals(0, source.overviewCalls, "on ne montre pas le profil de la moderation")
        assertTrue(room.goals.isEmpty(), "on ne fixe pas d'objectif avec la moderation")
        assertTrue(room.appointments.isEmpty())
    }

    @Test
    fun `ouvrir une autre piece ne garde pas les messages de la precedente`() = runTest {
        val source = FakeSocialSource().apply {
            links = listOf(lien("l1"), lien("l2", autre = "autre-0000"))
            messagePage = listOf(message("avant", "2026-03-10T10:00:00Z"))
        }
        val repository = repository(source)
        settle()

        repository.openRoom(ChatRoom(linkId = "l1"))
        settle()
        assertEquals(1, repository.state.value.room?.messages?.size)

        // La seconde lecture echoue : sans remise a zero, les messages du premier ami resteraient
        // a l'ecran **sous le nom du second**.
        source.messagePage = emptyList()
        source.failRoom = IllegalStateException("coupure")
        repository.openRoom(ChatRoom(linkId = "l2"))
        settle()

        val room = assertNotNull(repository.state.value.room)
        assertTrue(room.messages.isEmpty(), "les messages de l'ami precedent ne survivent pas")
        assertEquals("coupure", room.failure)
    }

    @Test
    fun `fermer la piece oublie ses messages`() = runTest {
        val source = FakeSocialSource().apply {
            links = listOf(lien("l1"))
            messagePage = listOf(message("m0", "2026-03-10T10:00:00Z"))
        }
        val repository = repository(source)
        settle()

        repository.openRoom(ChatRoom(linkId = "l1"))
        settle()
        repository.closeRoom()

        assertNull(repository.state.value.room)
    }

    @Test
    fun `sans source, ouvrir une piece ne fait rien et ne se plaint pas`() = runTest {
        val repository = SocialRepository(null, FakeOwners(moi), backgroundScope)
        settle()

        repository.openRoom(ChatRoom(linkId = "l1"))
        settle()

        assertNull(
            repository.state.value.room,
            "sans reseau, ouvrir laisserait un chargement sans fin a l'ecran",
        )
        assertNull(repository.state.value.notice, "il n'y a pas de reseau a atteindre : ce n'est pas une erreur")
    }

    // ------------------------------------------------------------------
    // Échecs de la pièce
    // ------------------------------------------------------------------

    @Test
    fun `un echec de la lecture des messages est une panne, pas une conversation vide`() = runTest {
        val source = FakeSocialSource().apply {
            links = listOf(lien("l1"))
            failRoom = IllegalStateException("reseau injoignable")
        }
        val repository = repository(source)
        settle()

        repository.openRoom(ChatRoom(linkId = "l1"))
        settle()

        val room = assertNotNull(repository.state.value.room)
        assertEquals("reseau injoignable", room.failure)
        assertFalse(room.loading, "un chargement sans fin se lirait comme une lecture qui n'aboutit pas")
        assertTrue(room.messages.isEmpty())
    }

    @Test
    fun `un echec de relecture garde les messages et se signale`() = runTest {
        val source = FakeSocialSource().apply {
            links = listOf(lien("l1"))
            messagePage = listOf(message("m0", "2026-03-10T10:00:00Z"))
        }
        val repository = repository(source)
        settle()
        repository.openRoom(ChatRoom(linkId = "l1"))
        settle()

        source.failRoom = IllegalStateException("coupure")
        repository.refreshRoom()

        val room = assertNotNull(repository.state.value.room)
        assertEquals(1, room.messages.size, "un echec de relecture ne rend pas faux ce qu'on a lu")
        assertNull(room.failure, "la panne ne doit pas remplacer une conversation deja affichee")
        assertEquals("coupure", repository.state.value.notice)
    }

    @Test
    fun `un complement illisible n'efface pas les messages`() = runTest {
        val source = FakeSocialSource().apply {
            links = listOf(lien("l1"))
            messagePage = listOf(message("m0", "2026-03-10T10:00:00Z"))
            goalList = listOf(SharedGoal("o1", "l1", "2026-03-09", 5, "lui-0000"))
        }
        val repository = repository(source)
        settle()
        repository.openRoom(ChatRoom(linkId = "l1"))
        settle()
        assertEquals(1, repository.state.value.room?.goals?.size)

        source.failRoomExtras = IllegalStateException("objectifs indisponibles")
        repository.refreshRoom()

        val room = assertNotNull(repository.state.value.room)
        assertEquals(1, room.messages.size, "les complements ne sont pas la conversation")
        assertNull(room.failure)
        assertEquals("objectifs indisponibles", repository.state.value.notice)
    }

    @Test
    fun `un apercu illisible n'empeche pas les objectifs d'arriver`() = runTest {
        val source = FakeSocialSource().apply {
            links = listOf(lien("l1"))
            failOverview = IllegalStateException("apercu indisponible")
            goalList = listOf(SharedGoal("o1", "l1", "2026-03-09", 5, "lui-0000"))
        }
        val repository = repository(source)
        settle()

        repository.openRoom(ChatRoom(linkId = "l1"))
        settle()

        val room = assertNotNull(repository.state.value.room)
        assertNull(room.overview, "l'apercu a echoue, et rien ne l'invente")
        assertEquals(
            1,
            room.goals.size,
            "chaque complement est isole : le premier qui echoue n'empeche pas les suivants",
        )
    }

    // ------------------------------------------------------------------
    // Historique
    // ------------------------------------------------------------------

    @Test
    fun `la page ancienne se demande a partir du plus ancien message affiche`() = runTest {
        val source = FakeSocialSource().apply {
            links = listOf(lien("l1"))
            messagePage = page(50, debut = 10, prefixe = "r")
            olderPage = page(3, debut = 0, prefixe = "o")
        }
        val repository = repository(source)
        settle()
        repository.openRoom(ChatRoom(linkId = "l1"))
        settle()
        assertTrue(assertNotNull(repository.state.value.room).history.hasOlder)

        repository.loadOlder()

        val room = assertNotNull(repository.state.value.room)
        assertEquals(
            listOf<String?>(null, "2026-03-10T10:10:00Z"),
            source.cursors,
            "le curseur est l'instant du message le plus ancien affiche",
        )
        assertEquals(53, room.messages.size, "les deux pages se cumulent")
        assertEquals(
            "o0",
            room.messages.first().id,
            "la page ancienne se peint **avant** la recente",
        )
        assertEquals("r49", room.messages.last().id)
    }

    @Test
    fun `une page ancienne incomplete referme le bouton pour de bon`() = runTest {
        val source = FakeSocialSource().apply {
            links = listOf(lien("l1"))
            messagePage = page(50, debut = 10, prefixe = "r")
            olderPage = page(3, debut = 0, prefixe = "o")
        }
        val repository = repository(source)
        settle()
        repository.openRoom(ChatRoom(linkId = "l1"))
        settle()

        repository.loadOlder()

        val room = assertNotNull(repository.state.value.room)
        assertFalse(room.history.hasOlder, "une page incomplete est le debut de la conversation")
        assertTrue(room.history.exhausted)

        // Et un rafraichissement qui rend une page **pleine** ne rouvre pas la porte : c'est ce que
        // `exhausted` protege, et sans lui le bouton clignoterait a chaque nouveau message.
        source.messagePage = page(50, debut = 0, prefixe = "s")
        repository.refreshRoom()

        assertFalse(
            assertNotNull(repository.state.value.room).history.hasOlder,
            "un debut connu ne se rouvre pas",
        )
    }

    @Test
    fun `remonter sans message affiche ne part pas`() = runTest {
        val source = FakeSocialSource().apply { links = listOf(lien("l1")) }
        val repository = repository(source)
        settle()
        repository.openRoom(ChatRoom(linkId = "l1"))
        settle()

        repository.loadOlder()

        assertEquals(0, source.olderCalls, "sans message, il n'y a pas de curseur a envoyer")
    }

    @Test
    fun `un echec de la page ancienne se signale sans perdre ce qui est affiche`() = runTest {
        val source = FakeSocialSource().apply {
            links = listOf(lien("l1"))
            messagePage = page(50, debut = 10, prefixe = "r")
        }
        val repository = repository(source)
        settle()
        repository.openRoom(ChatRoom(linkId = "l1"))
        settle()

        source.failOlder = IllegalStateException("historique indisponible")
        repository.loadOlder()

        val room = assertNotNull(repository.state.value.room)
        assertEquals(50, room.messages.size)
        assertFalse(room.loadingOlder, "le bouton doit redevenir actif, sinon l'ecran reste bloque")
        assertEquals("historique indisponible", repository.state.value.notice)
    }

    // ------------------------------------------------------------------
    // Gestes
    // ------------------------------------------------------------------

    @Test
    fun `un message envoye ne publie pas de confirmation`() = runTest {
        val source = FakeSocialSource().apply { links = listOf(lien("l1")) }
        val repository = repository(source)
        settle()
        repository.openRoom(ChatRoom(linkId = "l1"))
        settle()

        assertTrue(repository.sendMessage("salam"))

        assertEquals(
            null,
            repository.state.value.notice,
            "un message qu'on vient d'envoyer se voit : un bandeau par-dessus serait du bruit",
        )
        assertTrue("sendMessage" in source.actions)
    }

    @Test
    fun `un geste de conversation relit la piece`() = runTest {
        val source = FakeSocialSource().apply { links = listOf(lien("l1")) }
        val repository = repository(source)
        settle()
        repository.openRoom(ChatRoom(linkId = "l1"))
        settle()
        assertEquals(0, assertNotNull(repository.state.value.room).messages.size)

        source.messagePage = listOf(message("m0", "2026-03-10T10:00:00Z"))
        repository.sendMessage("salam")

        assertEquals(1, assertNotNull(repository.state.value.room).messages.size)
    }

    @Test
    fun `marquer comme lu remet le compteur de la liste a zero`() = runTest {
        val source = FakeSocialSource().apply {
            links = listOf(lien("l1"))
            inbox = SocialInbox(
                summaries = mapOf("l1" to ConversationSummary("Salut", "2026-01-02T10:00:00Z", 3)),
                statuses = mapOf(ami to true),
            )
        }
        val repository = repository(source)
        settle()
        assertEquals(3, repository.state.value.summaries.getValue("l1").unread)

        repository.openRoom(ChatRoom(linkId = "l1"))
        settle()

        assertEquals(
            0,
            repository.state.value.summaries.getValue("l1").unread,
            "ouvrir une conversation l'a lue : le compteur ne doit pas continuer de l'annoncer",
        )
    }

    @Test
    fun `chaque geste de conversation part par sa propre fonction`() = runTest {
        val source = FakeSocialSource().apply { links = listOf(lien("l1")) }
        val repository = repository(source)
        settle()
        repository.openRoom(ChatRoom(linkId = "l1"))
        settle()

        repository.deleteMessage("m1")
        repository.reportMessage("m2", "motif")
        repository.hideMessage("m3")
        repository.inviteToGroup("g1", ami)
        repository.acceptGroupInvite("g1")
        repository.declineGroupInvite("g1")
        repository.setModerator("g1", ami, true)
        repository.removeGroupMember("g1", ami)
        repository.proposeSharedGoal("l1", "2026-03-09", 5)
        repository.acceptSharedGoal("o1")
        repository.proposeAppointment("l1", "2026-04-01T16:00:00Z")
        repository.acceptAppointment("a1")
        repository.cancelAppointment("a2")

        assertEquals(
            listOf(
                "deleteMessage",
                "reportMessage",
                "hideMessageForMe",
                "inviteGroupMember",
                "acceptGroupInvite",
                "declineGroupInvite",
                "setGroupModerator",
                "removeGroupMember",
                "proposeSharedGoal",
                "acceptSharedGoal",
                "proposeAppointment",
                "acceptAppointment",
                "cancelAppointment",
            ),
            source.actions,
            "deux gestes branches sur la meme fonction feraient l'un des deux sans effet",
        )
    }

    @Test
    fun `un geste refuse se signale et laisse la conversation en place`() = runTest {
        val source = FakeSocialSource().apply {
            links = listOf(lien("l1"))
            messagePage = listOf(message("m0", "2026-03-10T10:00:00Z"))
        }
        val repository = repository(source)
        settle()
        repository.openRoom(ChatRoom(linkId = "l1"))
        settle()

        source.failAction = IllegalStateException("Signalement deja enregistre")
        assertFalse(repository.reportMessage("m0", "motif"))

        val room = assertNotNull(repository.state.value.room)
        assertEquals("Signalement deja enregistre", repository.state.value.notice)
        assertFalse(repository.state.value.busy)
        assertEquals(1, room.messages.size, "un geste refuse ne change pas ce qui est a l'ecran")
        assertNull(room.failure)
    }

    @Test
    fun `supprimer le cercle relit la liste d'amis et referme la piece`() = runTest {
        val source = FakeSocialSource().apply { groups = listOf(groupe("g1")) }
        val repository = repository(source)
        settle()
        repository.openRoom(ChatRoom(groupId = "g1"))
        settle()

        assertTrue(repository.deleteGroup("g1"))

        assertNull(
            repository.state.value.room,
            "le cercle n'existe plus : le laisser ouvert ferait ecrire dans une piece disparue",
        )
        assertEquals(SocialText.SAVED, repository.state.value.notice)
        assertTrue("deleteGroup" in source.actions)
        assertTrue(source.calls >= 2, "la liste d'amis est relue : le cercle en disparait")
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    private fun TestScope.repository(source: FakeSocialSource) =
        SocialRepository(source, FakeOwners(moi), backgroundScope)

    /** Le remède de `SocialRepositoryTest`, pour la même raison : le travail vit dans `backgroundScope`. */
    private fun TestScope.settle(millis: Long = 1_000) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private fun lien(id: String, autre: String = ami) = FriendLink(
        id = id,
        requesterId = moi,
        recipientId = autre,
        status = FriendLinkStatus.ACCEPTED,
        createdAt = "2026-01-01T10:00:00Z",
        other = FriendBrief(id = autre, displayName = "Ami $autre"),
    )

    private fun groupe(id: String, contact: String? = null) = FriendGroup(
        id = id,
        name = "Cercle $id",
        ownerId = moi,
        createdAt = "2026-01-01T10:00:00Z",
        contactUserId = contact,
    )

    private fun membre(id: String, role: GroupRole) = GroupMember(
        groupId = "g1",
        userId = id,
        role = role,
        acceptedAt = "2026-01-01T10:00:00Z",
    )

    private fun message(id: String, cree: String) = ChatMessage(
        id = id,
        senderId = ami,
        kind = ChatMessageKind.TEXT,
        body = "corps $id",
        createdAt = cree,
        linkId = "l1",
    )

    /**
     * Une page de `taille` messages, minutes `debut` à `debut + taille - 1` d'une même heure.
     *
     * Les instants sont **distincts et croissants**, ce qui est la seule propriété dont le tri
     * dépend — et c'est aussi ce que le serveur garantit : la requête suivante demande ce qui est
     * **strictement** antérieur au curseur, donc deux pages ne peuvent pas partager un instant.
     */
    private fun page(taille: Int, debut: Int, prefixe: String) = (0 until taille).map { i ->
        message("$prefixe$i", "2026-03-10T10:%02d:00Z".format(debut + i))
    }

    @Test
    fun `la constante de page du domaine est celle que la requete demande`() {
        // Le nombre sert deux fois — la requete le demande, et la regle « en reste-t-il ? » le
        // relit —, et ce test fige la valeur que les deux lisent. Le controle de forme qui
        // verifie que la source ne le **recopie** pas vit dans `SocialSourceShapeTest`.
        assertEquals(50, Social.MESSAGE_PAGE)
    }

    @Test
    fun `un lien dont l'autre a lu mon message porte un accuse, pas l'inverse`() = runTest {
        val source = FakeSocialSource().apply {
            links = listOf(lien("l1"))
            messagePage = listOf(message("m0", "2026-03-10T10:00:00Z"))
            readAt = "2026-03-10T09:00:00Z"
        }
        val repository = repository(source)
        settle()
        repository.openRoom(ChatRoom(linkId = "l1"))
        settle()

        val room = assertNotNull(repository.state.value.room)
        // L'autre a lu **avant** mon message : il ne l'a donc pas lu. Un accusé faux annoncerait a
        // l'auteur qu'on l'a lu alors que non, et c'est la seule faute qui compte ici.
        assertEquals(
            SocialText.SENT,
            Social.readState(room.messages.single(), room.otherReadAt),
        )
    }

    @Test
    fun `les objectifs et les rendez-vous se lisent dans un lien, jamais dans un cercle`() = runTest {
        val source = FakeSocialSource().apply {
            links = listOf(lien("l1"))
            groups = listOf(groupe("g1"))
            goalList = listOf(SharedGoal("o1", "l1", "2026-03-09", 5, "lui-0000"))
            appointmentList = listOf(
                ReviewAppointment("a1", "l1", "2026-04-01T16:00:00Z", "lui-0000"),
            )
        }
        val repository = repository(source)
        settle()

        repository.openRoom(ChatRoom(linkId = "l1"))
        settle()

        val lienOuvert = assertNotNull(repository.state.value.room)
        assertEquals(1, lienOuvert.goals.size)
        assertEquals(1, lienOuvert.appointments.size)

        // Dans un cercle, il n'y a pas d'« autre » avec qui fixer un objectif : les deux listes
        // restent vides, et rien ne les demande.
        repository.openRoom(ChatRoom(groupId = "g1"))
        settle()

        val cercleOuvert = assertNotNull(repository.state.value.room)
        assertTrue(cercleOuvert.goals.isEmpty())
        assertTrue(cercleOuvert.appointments.isEmpty())
    }
}
