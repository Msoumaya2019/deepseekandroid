package com.msoumaya.deepseekandroid.feature.social

import com.msoumaya.deepseekandroid.core.data.repository.SocialState
import com.msoumaya.deepseekandroid.core.domain.Social
import com.msoumaya.deepseekandroid.core.domain.SocialText
import com.msoumaya.deepseekandroid.core.model.ConversationSummary
import com.msoumaya.deepseekandroid.core.model.FriendBrief
import com.msoumaya.deepseekandroid.core.model.FriendGroup
import com.msoumaya.deepseekandroid.core.model.FriendLink
import com.msoumaya.deepseekandroid.core.model.FriendLinkStatus
import com.msoumaya.deepseekandroid.core.model.FriendProfile
import com.msoumaya.deepseekandroid.core.model.SocialSuspension
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Règles de l'écran « Amis »
// ---------------------------------------------------------------------------
// Le rendu est une fonction **pure** de `(état du dépôt, saisies, instant)`. Il est donc éprouvé
// ici sans coroutine, sans réseau, sans appareil et sans horloge : l'instant est fixé, et les
// seules valeurs qui dépendent du fuseau sont recalculées par le JDK lui-même plutôt que
// recopiées à la main — un test qui écrit « 2 mars 13:00 » en dur échouerait sur une machine
// réglée autrement, et personne ne saurait si c'est le code ou le fuseau qui a bougé.
//
// Quatre familles portent le plus, parce que ce sont celles qui peuvent **mentir** :
//
//   1. **Le garde-fou du profil.** `Social.otherId` retombe sur le demandeur quand on ne reconnaît
//      ni l'un ni l'autre participant : avec un identifiant vide, **chaque** lien rendrait son
//      demandeur, et la liste afficherait des gens au hasard. Le test construit deux liens dont
//      les demandeurs diffèrent, retire le profil, et exige une liste **vide**.
//
//   2. **Les deux clés.** Les aperçus sont indexés par **lien** et les présences par **personne**.
//      Les confondre n'affiche ni erreur ni ami : seulement des conversations sans aperçu et des
//      présences inconnues. Le test pose une présence sous la clé du **lien** et exige que l'ami
//      ne soit **pas** en ligne.
//
//   3. **Ce que le filtre et la recherche font au compte.** Le nombre du titre est le nombre
//      d'amis acceptés : il ne bouge ni quand on filtre, ni quand on cherche, ni quand on
//      déplie. Un titre qui suivrait la liste visible annoncerait « Mes amis (0) » à quelqu'un
//      qui cherche un nom qu'il a mal orthographié.
//
//   4. **Ce que l'écran annonce sans le savoir.** Un code fait d'espaces activait le bouton dans
//      l'original, parce que `!code.trim()` était évalué à l'envers ; un nom de cercle d'un seul
//      caractère passait aussi. Les deux sont mesurés ici sur des saisies **pièges**.
// ---------------------------------------------------------------------------

class SocialRendererTest {

    private val moi = "moi"

    /** Un mardi, à midi. Fixé : aucune règle de cet écran ne doit dépendre du jour où on le joue. */
    private val maintenant = "2026-03-10T12:00:00Z"

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    private fun profil(code: String = "ABC123") = FriendProfile(
        id = moi,
        displayName = "Moi",
        inviteCode = code,
    )

    /**
     * Un lien d'amitié.
     *
     * @param autre identifiant de l'autre participant, et clé de sa présence.
     * @param nom nom affiché de l'autre ; `null` simule un lien que le serveur rend sans profil.
     * @param recu `true` fait de moi le **destinataire** — donc d'une demande en attente une
     *   invitation **reçue** —, `false` le demandeur.
     * @param bloquePar celui qui a posé le blocage.
     */
    private fun lien(
        id: String,
        statut: FriendLinkStatus = FriendLinkStatus.ACCEPTED,
        autre: String = "autre-$id",
        nom: String? = "Ami $id",
        cree: String = "2026-03-01T10:00:00Z",
        bloquePar: String? = null,
        recu: Boolean = true,
    ) = FriendLink(
        id = id,
        requesterId = if (recu) autre else moi,
        recipientId = if (recu) moi else autre,
        status = statut,
        createdAt = cree,
        blockedBy = bloquePar,
        other = nom?.let { FriendBrief(id = autre, displayName = it) },
    )

    private fun etat(
        profile: FriendProfile? = profil(),
        links: List<FriendLink> = emptyList(),
        groups: List<FriendGroup> = emptyList(),
        suspension: SocialSuspension? = null,
        summaries: Map<String, ConversationSummary> = emptyMap(),
        online: Map<String, Boolean> = emptyMap(),
        loading: Boolean = false,
        signedIn: Boolean = true,
        busy: Boolean = false,
        failure: String? = null,
        notice: String? = null,
    ) = SocialState(
        signedIn = signedIn,
        loading = loading,
        busy = busy,
        notice = notice,
        failure = failure,
        profile = profile,
        links = links,
        groups = groups,
        suspension = suspension,
        summaries = summaries,
        online = online,
    )

    private fun rendu(
        state: SocialState,
        query: String = "",
        filter: Social.Filter = Social.Filter.ALL,
        allFriends: Boolean = false,
        optionsOpen: Boolean = false,
        code: String = "",
        groupName: String = "",
    ) = SocialRenderer.render(
        state = state,
        inputs = SocialInputs(
            query = query,
            filter = filter,
            allFriends = allFriends,
            optionsOpen = optionsOpen,
            code = code,
            groupName = groupName,
        ),
        nowIso = maintenant,
    )

    /**
     * L'instant d'un message, écrit comme l'écran doit l'écrire.
     *
     * Recalculé par le JDK, avec le même motif et la même locale que [SocialText.dayStamp] : le
     * test tient donc le motif et la locale, sans tenir le fuseau de la machine qui le joue.
     */
    private fun tampon(iso: String): String =
        DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.FRANCE)
            .format(Instant.parse(iso).atZone(ZoneId.systemDefault()))

    // -----------------------------------------------------------------------
    // Ce que le rendu recopie
    // -----------------------------------------------------------------------

    @Test
    fun `le rendu recopie ce que le depot annonce`() {
        val rendu = rendu(
            etat(
                profile = null,
                loading = true,
                signedIn = false,
                busy = true,
                failure = "reseau injoignable",
                notice = "Enregistré.",
            ),
        )

        assertTrue(rendu.loading)
        assertFalse(rendu.signedIn)
        assertTrue(rendu.busy)
        assertEquals("reseau injoignable", rendu.failure)
        assertEquals("Enregistré.", rendu.notice)
    }

    @Test
    fun `les etiquettes du filtre sont celles du domaine, dans l'ordre`() {
        assertEquals(Social.Filter.entries.map { it.label }, rendu(etat()).filterLabels)
        assertEquals(listOf("Tous", "En ligne", "Demandes"), rendu(etat()).filterLabels)
    }

    // -----------------------------------------------------------------------
    // Le garde-fou du profil
    // -----------------------------------------------------------------------

    @Test
    fun `sans profil, aucune liste d'amis n'est calculee`() {
        // Deux liens dont les demandeurs different : sans le garde-fou, le repli de
        // `Social.otherId` rendrait le demandeur de chacun, et la liste afficherait deux
        // personnes qui n'ont rien a voir avec mes amis.
        val liens = listOf(
            lien("l1", autre = "amina"),
            lien("l2", autre = "youssef"),
        )

        val rendu = rendu(etat(profile = null, links = liens))

        assertTrue(rendu.friends.isEmpty(), "sans profil, la liste doit rester vide")
        assertEquals(0, rendu.friendCount)
    }

    @Test
    fun `sans profil, les invitations et les blocages restent vides`() {
        val liens = listOf(
            lien("recue", statut = FriendLinkStatus.PENDING),
            lien("envoyee", statut = FriendLinkStatus.PENDING, recu = false),
            lien("bloquee", statut = FriendLinkStatus.BLOCKED, bloquePar = moi),
        )

        val rendu = rendu(etat(profile = null, links = liens))

        assertTrue(rendu.invitations.isEmpty())
        assertTrue(rendu.sent.isEmpty())
        assertTrue(rendu.blocked.isEmpty())
    }

    @Test
    fun `sans profil, il n'y a pas de code d'invitation a montrer`() {
        assertNull(rendu(etat(profile = null)).inviteCode)
    }

    @Test
    fun `avec un profil, la liste est calculee et le code est la`() {
        val rendu = rendu(etat(links = listOf(lien("l1"))))

        assertEquals(1, rendu.friends.size)
        assertEquals("ABC123", rendu.inviteCode)
    }

    // -----------------------------------------------------------------------
    // Amis, invitations, blocages : trois listes distinctes
    // -----------------------------------------------------------------------

    @Test
    fun `un lien accepte est un ami, un lien en attente n'en est pas un`() {
        val rendu = rendu(
            etat(
                links = listOf(
                    lien("accepte"),
                    lien("en-attente", statut = FriendLinkStatus.PENDING),
                ),
            ),
        )

        assertEquals(1, rendu.friends.size)
        assertEquals("accepte", rendu.friends.single().id)
        assertEquals(1, rendu.friendCount)
    }

    @Test
    fun `une invitation recue porte le nom de l'autre`() {
        val rendu = rendu(
            etat(links = listOf(lien("l1", statut = FriendLinkStatus.PENDING, nom = "Amina"))),
        )

        assertEquals(listOf(InvitationRow("l1", "Invitation de Amina")), rendu.invitations)
        assertTrue(rendu.sent.isEmpty())
    }

    @Test
    fun `une invitation recue sans profil porte le repli de phrase`() {
        val rendu = rendu(etat(links = listOf(lien("l1", statut = FriendLinkStatus.PENDING, nom = null))))

        assertEquals("Invitation de un membre", rendu.invitations.single().label)
    }

    @Test
    fun `une invitation envoyee est listee a part, et non parmi les recues`() {
        val rendu = rendu(
            etat(links = listOf(lien("l1", statut = FriendLinkStatus.PENDING, nom = "Amina", recu = false))),
        )

        assertTrue(rendu.invitations.isEmpty())
        assertEquals("Invitation envoyée à Amina", rendu.sent.single().label)
        assertEquals("l1", rendu.sent.single().linkId)
    }

    @Test
    fun `un blocage que j'ai pose est listable et designe le compte, pas le lien`() {
        val rendu = rendu(
            etat(links = listOf(lien("l1", statut = FriendLinkStatus.BLOCKED, autre = "youssef", bloquePar = moi))),
        )

        val bloque = rendu.blocked.single()
        assertEquals("youssef", bloque.otherId, "debloquer attend l'identifiant du compte")
        assertEquals("Ami l1 bloqué", bloque.label)
    }

    @Test
    fun `un blocage que l'autre a pose ne m'est pas propose`() {
        // C'est l'autre qui a la main : proposer « Debloquer » ici serait un geste sans effet.
        val rendu = rendu(
            etat(links = listOf(lien("l1", statut = FriendLinkStatus.BLOCKED, bloquePar = "youssef"))),
        )

        assertTrue(rendu.blocked.isEmpty())
    }

    @Test
    fun `un ami accepte n'est ni une invitation ni un blocage`() {
        val rendu = rendu(etat(links = listOf(lien("l1"))))

        assertTrue(rendu.invitations.isEmpty())
        assertTrue(rendu.sent.isEmpty())
        assertTrue(rendu.blocked.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Le medaillon : nom, presence, apercu
    // -----------------------------------------------------------------------

    @Test
    fun `un ami sans profil porte le nom de repli`() {
        val rendu = rendu(etat(links = listOf(lien("l1", nom = null))))

        assertEquals(SocialText.FRIEND, rendu.friends.single().name)
    }

    @Test
    fun `un ami dont la presence est connue et vraie dit En ligne`() {
        val rendu = rendu(
            etat(
                links = listOf(lien("l1", autre = "amina")),
                online = mapOf("amina" to true),
            ),
        )

        val ami = rendu.friends.single()
        assertTrue(ami.online)
        assertEquals(SocialText.ONLINE, ami.subtitle)
    }

    @Test
    fun `une presence fausse ne dit pas En ligne`() {
        val rendu = rendu(
            etat(
                links = listOf(lien("l1", autre = "amina")),
                online = mapOf("amina" to false),
            ),
        )

        assertFalse(rendu.friends.single().online)
        assertEquals(SocialText.START_CHAT, rendu.friends.single().subtitle)
    }

    @Test
    fun `la presence se lit sur la personne, jamais sur le lien`() {
        // La faute est silencieuse : une presence rangee sous la cle du **lien** ferait passer
        // tous les amis pour hors ligne, et rien a l'ecran ne le dirait.
        val rendu = rendu(
            etat(
                links = listOf(lien("l1", autre = "amina")),
                online = mapOf("l1" to true),
            ),
        )

        assertFalse(rendu.friends.single().online)
    }

    @Test
    fun `un ami hors ligne dit la date de son dernier message`() {
        val instant = "2026-03-02T09:30:00Z"
        val rendu = rendu(
            etat(
                links = listOf(lien("l1")),
                summaries = mapOf("l1" to ConversationSummary(body = "Salut", createdAt = instant)),
            ),
        )

        assertEquals(tampon(instant), rendu.friends.single().subtitle)
    }

    @Test
    fun `la date du message passe avant l'invitation a commencer`() {
        val rendu = rendu(
            etat(
                links = listOf(lien("l1")),
                summaries = mapOf("l1" to ConversationSummary(body = "Salut", createdAt = "2026-03-02T09:30:00Z")),
            ),
        )

        assertNotNull(SocialText.dayStamp("2026-03-02T09:30:00Z"))
        assertTrue(rendu.friends.single().subtitle != SocialText.START_CHAT)
    }

    @Test
    fun `un ami sans message est invite a commencer une discussion`() {
        val rendu = rendu(etat(links = listOf(lien("l1"))))

        assertEquals(SocialText.START_CHAT, rendu.friends.single().subtitle)
    }

    @Test
    fun `un instant illisible ne produit pas de texte d'erreur`() {
        // L'original ecrit « Invalid Date ». Ici l'instant illisible est traite comme **absent**,
        // et l'ami retombe sur l'invitation a commencer — qui est vraie : aucun message lisible
        // n'a ouvert la conversation.
        val rendu = rendu(
            etat(
                links = listOf(lien("l1")),
                summaries = mapOf("l1" to ConversationSummary(body = "Salut", createdAt = "pas une date")),
            ),
        )

        assertEquals(SocialText.START_CHAT, rendu.friends.single().subtitle)
    }

    @Test
    fun `l'apercu porte le dernier message et le nombre de non-lus`() {
        val rendu = rendu(
            etat(
                links = listOf(lien("l1")),
                summaries = mapOf(
                    "l1" to ConversationSummary(
                        body = "Tu as fini ta page ?",
                        createdAt = "2026-03-02T09:30:00Z",
                        unread = 3,
                    ),
                ),
            ),
        )

        val ami = rendu.friends.single()
        assertEquals("Tu as fini ta page ?", ami.summary)
        assertEquals(3, ami.unread)
    }

    @Test
    fun `un ami sans apercu n'annonce aucun non-lu`() {
        val ami = rendu(etat(links = listOf(lien("l1")))).friends.single()

        assertEquals("", ami.summary)
        assertEquals(0, ami.unread)
    }

    @Test
    fun `la ligne publie l'identifiant de l'autre, celui que les gestes attendent`() {
        // Bloquer attend l'identifiant du **compte** ; retirer attend celui du **lien**. La ligne
        // porte les deux, et les confondre ferait bloquer le mauvais compte.
        val ami = rendu(etat(links = listOf(lien("l1", autre = "amina")))).friends.single()

        assertEquals("l1", ami.id)
        assertEquals("amina", ami.otherId)
    }

    // -----------------------------------------------------------------------
    // Filtre, recherche, depliage
    // -----------------------------------------------------------------------

    @Test
    fun `le compte du titre ne depend ni du filtre, ni de la recherche`() {
        val liens = listOf(lien("l1", autre = "amina"), lien("l2", autre = "youssef"))

        val filtre = rendu(etat(links = liens), filter = Social.Filter.ONLINE)
        val cherche = rendu(etat(links = liens), query = "zzz")

        assertTrue(filtre.friends.isEmpty())
        assertTrue(cherche.friends.isEmpty())
        assertEquals(2, filtre.friendCount)
        assertEquals(2, cherche.friendCount)
    }

    @Test
    fun `le filtre Demandes vide la liste au lieu de la remplir`() {
        val liens = listOf(lien("l1"), lien("demande", statut = FriendLinkStatus.PENDING))

        val rendu = rendu(etat(links = liens), filter = Social.Filter.REQUESTS)

        assertTrue(rendu.friends.isEmpty(), "les demandes vivent sous la liste, pas dedans")
        assertEquals(1, rendu.friendCount)
    }

    @Test
    fun `le filtre En ligne ecarte une presence inconnue`() {
        val liens = listOf(lien("l1", autre = "amina"), lien("l2", autre = "youssef"))
        val rendu = rendu(
            etat(links = liens, online = mapOf("amina" to true)),
            filter = Social.Filter.ONLINE,
        )

        assertEquals(1, rendu.friends.size)
        assertEquals("amina", rendu.friends.single().otherId)
    }

    @Test
    fun `la recherche ignore la casse`() {
        val liens = listOf(lien("l1", nom = "Amina"), lien("l2", nom = "Youssef"))

        assertEquals(1, rendu(etat(links = liens), query = "AMI").friends.size)
        assertEquals(1, rendu(etat(links = liens), query = "youss").friends.size)
    }

    @Test
    fun `la liste est bornee a cinq amis, puis deployee`() {
        val liens = (1..6).map { lien("l$it") }

        assertEquals(5, rendu(etat(links = liens)).friends.size)
        assertEquals(6, rendu(etat(links = liens), allFriends = true).friends.size)
    }

    @Test
    fun `le depliage ne change pas le compte du titre`() {
        val liens = (1..6).map { lien("l$it") }

        assertEquals(6, rendu(etat(links = liens), allFriends = true).friendCount)
        assertEquals(6, rendu(etat(links = liens)).friendCount)
    }

    @Test
    fun `les amis sont tries par dernier message, le plus recent d'abord`() {
        val liens = listOf(
            lien("ancien", cree = "2026-03-01T10:00:00Z"),
            lien("recent", cree = "2026-03-05T10:00:00Z"),
        )

        // Sans conversation, l'ordre suit la date du lien.
        assertEquals(listOf("recent", "ancien"), rendu(etat(links = liens)).friends.map { it.id })

        // Avec un message, c'est la date du **message** qui commande : la conversation vivante
        // remonte, meme si l'amitie est plus ancienne.
        val avecApercu = rendu(
            etat(
                links = liens,
                summaries = mapOf(
                    "ancien" to ConversationSummary(body = "Coucou", createdAt = "2026-03-08T10:00:00Z"),
                ),
            ),
        )
        assertEquals(listOf("ancien", "recent"), avecApercu.friends.map { it.id })
    }

    @Test
    fun `les saisies sont recopiees telles quelles`() {
        val rendu = rendu(
            etat(),
            query = "ami",
            filter = Social.Filter.ONLINE,
            allFriends = true,
            optionsOpen = true,
            code = "XYZ",
            groupName = "Cercle",
        )

        assertEquals("ami", rendu.query)
        assertEquals(SocialText.FILTER_ONLINE, rendu.filterLabel)
        assertTrue(rendu.allFriends)
        assertTrue(rendu.optionsOpen)
        assertEquals("XYZ", rendu.code)
        assertEquals("Cercle", rendu.groupName)
    }

    // -----------------------------------------------------------------------
    // Ce que l'ecran autorise a envoyer
    // -----------------------------------------------------------------------

    @Test
    fun `l'invitation part avec un code non vide`() {
        assertTrue(rendu(etat(), code = "X").canSendInvitation)
        assertTrue(rendu(etat(), code = "  X  ").canSendInvitation, "un code entoure d'espaces reste un code")
    }

    @Test
    fun `l'invitation ne part pas avec un code vide ou fait d'espaces`() {
        assertFalse(rendu(etat(), code = "").canSendInvitation)
        assertFalse(rendu(etat(), code = "   ").canSendInvitation)
    }

    @Test
    fun `l'invitation ne part pas deux fois`() {
        assertFalse(rendu(etat(busy = true), code = "X").canSendInvitation)
    }

    @Test
    fun `le cercle exige deux caracteres utiles`() {
        assertTrue(rendu(etat(), groupName = "Ab").canCreateGroup)
        assertFalse(rendu(etat(), groupName = "").canCreateGroup)
        assertFalse(rendu(etat(), groupName = " A ").canCreateGroup, "un caractere utile ne suffit pas")
    }

    @Test
    fun `le cercle ne se cree pas deux fois`() {
        assertFalse(rendu(etat(busy = true), groupName = "Ab").canCreateGroup)
    }

    // -----------------------------------------------------------------------
    // Les cercles
    // -----------------------------------------------------------------------

    @Test
    fun `un cercle marque comme contact administrateur est signale`() {
        val rendu = rendu(
            etat(
                groups = listOf(
                    FriendGroup(id = "g1", name = "Cercle du soir", ownerId = moi, createdAt = maintenant),
                    FriendGroup(
                        id = "g2",
                        name = "Contact · Moi",
                        ownerId = moi,
                        createdAt = maintenant,
                        contactUserId = moi,
                    ),
                ),
            ),
        )

        assertEquals(2, rendu.circles.size)
        assertFalse(rendu.circles.first { it.id == "g1" }.isAdminContact)
        assertTrue(rendu.circles.first { it.id == "g2" }.isAdminContact)
    }

    @Test
    fun `sans cercle, la liste des cercles est vide`() {
        assertTrue(rendu(etat()).circles.isEmpty())
    }

    // -----------------------------------------------------------------------
    // La suspension
    // -----------------------------------------------------------------------

    private fun suspension(jusqu: String?) = SocialSuspension(
        userId = moi,
        reason = "Propos signalés",
        createdAt = "2026-03-01T10:00:00Z",
        suspendedUntil = jusqu,
    )

    @Test
    fun `une suspension sans terme est active pour toujours`() {
        val rendu = rendu(etat(suspension = suspension(null)))

        assertEquals(SocialText.suspended("Propos signalés"), rendu.suspension)
    }

    @Test
    fun `une suspension encore a courir est affichee`() {
        val rendu = rendu(etat(suspension = suspension("2026-03-11T12:00:00Z")))

        assertNotNull(rendu.suspension)
    }

    @Test
    fun `une suspension echue n'est plus affichee`() {
        // La lire comme active bloquerait quelqu'un dont la sanction est finie ; la lire comme
        // expiree quand elle n'a pas de terme rouvrirait la messagerie a qui vient d'en etre
        // ecarte. Les deux cas sont donc mesures separement.
        val rendu = rendu(etat(suspension = suspension("2026-03-09T12:00:00Z")))

        assertNull(rendu.suspension)
    }

    @Test
    fun `sans suspension, il n'y a pas de bandeau`() {
        assertNull(rendu(etat(suspension = null)).suspension)
    }
}
