package com.msoumaya.deepseekandroid.feature.social

import com.msoumaya.deepseekandroid.core.data.remote.ChatRoom
import com.msoumaya.deepseekandroid.core.data.repository.RoomState
import com.msoumaya.deepseekandroid.core.data.repository.SocialState
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.QuranDataLoader
import com.msoumaya.deepseekandroid.core.domain.Social
import com.msoumaya.deepseekandroid.core.domain.SocialText
import com.msoumaya.deepseekandroid.core.model.ChatMessage
import com.msoumaya.deepseekandroid.core.model.ChatMessageKind
import com.msoumaya.deepseekandroid.core.model.FriendBrief
import com.msoumaya.deepseekandroid.core.model.FriendGroup
import com.msoumaya.deepseekandroid.core.model.FriendLink
import com.msoumaya.deepseekandroid.core.model.FriendLinkStatus
import com.msoumaya.deepseekandroid.core.model.FriendOverview
import com.msoumaya.deepseekandroid.core.model.FriendProfile
import com.msoumaya.deepseekandroid.core.model.GroupMember
import com.msoumaya.deepseekandroid.core.model.GroupRole
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.RecitationAttachment
import com.msoumaya.deepseekandroid.core.model.ReviewAppointment
import com.msoumaya.deepseekandroid.core.model.SharedGoal
import com.msoumaya.deepseekandroid.core.model.SocialSuspension
import org.junit.BeforeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Règles de l'écran « Conversation »
// ---------------------------------------------------------------------------
// Le rendu est une fonction **pure** de `(état du dépôt, saisies, instant)`. Il est donc éprouvé
// ici sans coroutine, sans réseau, sans appareil et sans horloge : l'instant est fixé, et les
// seules valeurs qui dépendent du fuseau ou du référentiel sont **recalculées** par le JDK ou par
// `Quran` plutôt que recopiées à la main. Un test qui écrirait « 10:04 » en dur échouerait sur une
// machine réglée autrement, et personne ne saurait si c'est le code ou le fuseau qui a bougé.
//
// Cinq familles portent le plus, parce que ce sont celles qui peuvent **mentir** :
//
//   1. **Le côté d'un message.** Il décide à la fois de la marge et de la couleur. Un message
//      attribué au mauvais auteur ne casse rien : il se lit simplement comme si quelqu'un d'autre
//      l'avait écrit. Le test construit les deux côtés, et vérifie aussi le cas où le profil
//      manque — sans lui, **tous** les messages basculeraient du même côté.
//
//   2. **L'accusé de lecture.** « Lu » annonce à l'auteur qu'on l'a lu. Il n'a donc le droit
//      d'apparaître que sur **mes** messages, dans un **tête-à-tête**, et seulement si la position
//      de lecture de l'autre est **postérieure**. Les trois conditions sont mesurées séparément,
//      parce qu'oublier l'une des trois produit la même affirmation fausse.
//
//   3. **Les blocs d'entraide.** Le cercle de l'administration réunit des inconnus : y afficher
//      l'objectif, la progression ou le passage en cours d'un ami serait une fuite. Le test
//      l'ouvre de force et exige que **rien** ne sorte.
//
//   4. **Les droits sur un membre.** Nommer un modérateur, retirer quelqu'un, supprimer le
//      cercle : trois gestes, trois droits distincts. Le test pose un propriétaire, un modérateur
//      et une invitation en attente, et vérifie chaque ligne **séparément** — un droit trop large
//      ne se voit qu'à l'usage, et il est alors trop tard.
//
//   5. **Le référentiel coranique.** Une plage venue du serveur peut sortir du corpus, et
//      `Quran.reference` **lève** alors. Le test le fait exprès : l'écran doit survivre à une
//      ligne corrompue, pas planter à distance.
// ---------------------------------------------------------------------------

class ConversationRendererTest {

    private val moi = "moi"
    private val ami = "ami"
    private val lienId = "lien-1"
    private val cercleId = "cercle-1"

    /** Un mardi, à midi. Fixé : aucune règle de cet écran ne dépend du jour où on le joue. */
    private val maintenant = "2026-03-10T12:00:00Z"

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    private fun monProfil() = FriendProfile(id = moi, displayName = "Moi", inviteCode = "MOI123")

    private fun lienAmitie(
        nom: String = "Amina",
        avatar: String? = null,
        autre: String = ami,
    ) = FriendLink(
        id = lienId,
        requesterId = moi,
        recipientId = autre,
        status = FriendLinkStatus.ACCEPTED,
        createdAt = "2026-01-01T00:00:00Z",
        other = FriendBrief(id = autre, displayName = nom, avatarPath = avatar),
    )

    private fun cercle(
        nom: String = "Les trois",
        contact: String? = null,
    ) = FriendGroup(
        id = cercleId,
        name = nom,
        ownerId = moi,
        createdAt = "2026-01-01T00:00:00Z",
        contactUserId = contact,
    )

    private fun membre(
        userId: String,
        role: GroupRole = GroupRole.MEMBER,
        nom: String? = null,
        accepte: Boolean = true,
    ) = GroupMember(
        groupId = cercleId,
        userId = userId,
        role = role,
        acceptedAt = if (accepte) "2026-01-02T00:00:00Z" else null,
        profile = nom?.let { FriendProfile(id = userId, displayName = it, inviteCode = "CODE-$userId") },
    )

    private fun message(
        id: String,
        de: String,
        corps: String = "Salam",
        at: String = "2026-03-10T10:04:00Z",
        kind: ChatMessageKind = ChatMessageKind.TEXT,
        recitation: RecitationAttachment? = null,
        supprime: Boolean = false,
        dansUnCercle: Boolean = false,
    ) = ChatMessage(
        id = id,
        senderId = de,
        kind = kind,
        body = corps,
        createdAt = at,
        linkId = if (dansUnCercle) null else lienId,
        groupId = if (dansUnCercle) cercleId else null,
        recitation = recitation,
        deletedAt = if (supprime) "2026-03-10T11:00:00Z" else null,
    )

    private fun apercu(
        online: Boolean = true,
        objectifLabel: String? = null,
        pourcent: Int = 0,
        versets: Int = 0,
        seances: Int = 0,
        debut: Int? = null,
        fin: Int? = null,
    ) = FriendOverview(
        id = ami,
        displayName = "Amina",
        goalLabel = objectifLabel,
        weeklyVerses = versets,
        weeklySessions = seances,
        goalPercent = pourcent,
        currentStart = debut,
        currentEnd = fin,
        isOnline = online,
    )

    private fun objectifPartage(
        id: String,
        proposePar: String,
        accepte: Boolean = false,
        semaine: String = "2026-03-09",
        seances: Int = 3,
    ) = SharedGoal(
        id = id,
        linkId = lienId,
        weekStart = semaine,
        targetSessions = seances,
        proposedBy = proposePar,
        acceptedAt = if (accepte) "2026-03-01T00:00:00Z" else null,
    )

    private fun rendezVous(
        id: String,
        proposePar: String,
        accepte: Boolean = false,
        at: String = "2026-04-01T18:00:00Z",
    ) = ReviewAppointment(
        id = id,
        linkId = lienId,
        startsAt = at,
        proposedBy = proposePar,
        acceptedAt = if (accepte) "2026-03-01T00:00:00Z" else null,
    )

    private fun piece(
        open: ChatRoom = ChatRoom(linkId = lienId),
        messages: List<ChatMessage> = emptyList(),
        history: Social.History = Social.History(),
        loadingOlder: Boolean = false,
        otherReadAt: String? = null,
        apercu: FriendOverview? = null,
        membres: List<GroupMember> = emptyList(),
        objectifs: List<SharedGoal> = emptyList(),
        rendezVous: List<ReviewAppointment> = emptyList(),
        loading: Boolean = false,
        failure: String? = null,
    ) = RoomState(
        room = open,
        loading = loading,
        failure = failure,
        messages = messages,
        history = history,
        loadingOlder = loadingOlder,
        otherReadAt = otherReadAt,
        overview = apercu,
        members = membres,
        goals = objectifs,
        appointments = rendezVous,
    )

    private fun pieceDeCercle(
        messages: List<ChatMessage> = emptyList(),
        membres: List<GroupMember> = emptyList(),
    ) = piece(open = ChatRoom(groupId = cercleId), messages = messages, membres = membres)

    private fun rendre(
        ouverte: RoomState? = piece(),
        saisies: ConversationInputs = ConversationInputs(),
        profil: FriendProfile? = monProfil(),
        liens: List<FriendLink> = listOf(lienAmitie()),
        cercles: List<FriendGroup> = emptyList(),
        suspension: SocialSuspension? = null,
        avis: String? = null,
        busy: Boolean = false,
    ): ConversationUiState = ConversationRenderer.render(
        SocialState(
            signedIn = true,
            loading = false,
            busy = busy,
            notice = avis,
            profile = profil,
            links = liens,
            groups = cercles,
            suspension = suspension,
            room = ouverte,
        ),
        saisies,
        maintenant,
    )

    private fun suspension(terme: String?) = SocialSuspension(
        userId = moi,
        reason = "Spam",
        createdAt = "2026-03-01T00:00:00Z",
        suspendedUntil = terme,
    )

    // -----------------------------------------------------------------------
    // Ouverture et en-tête
    // -----------------------------------------------------------------------

    @Test
    fun `sans piece ouverte, l'etat est ferme`() {
        val ui = rendre(ouverte = null)
        assertFalse(ui.open)
        assertTrue(ui.messages.isEmpty())
    }

    /**
     * Sans identifiant de compte, on ne sait pas quel message est « le mien ». Afficher la pièce
     * quand même montrerait tous les messages du même côté, et donnerait à chacun les droits de
     * tous : l'écran doit attendre, pas mentir.
     */
    @Test
    fun `sans profil, une piece ouverte ne s'affiche pas`() {
        val ui = rendre(ouverte = piece(messages = listOf(message("m1", ami))), profil = null)
        assertFalse(ui.open)
        assertTrue(ui.messages.isEmpty())
    }

    @Test
    fun `le titre d'un lien est le nom de l'ami`() {
        val ui = rendre(liens = listOf(lienAmitie(nom = "Amina")))
        assertEquals("Amina", ui.title)
    }

    @Test
    fun `un lien sans profil d'ami porte le repli Ami`() {
        val orphelin = FriendLink(
            id = lienId,
            requesterId = moi,
            recipientId = ami,
            status = FriendLinkStatus.ACCEPTED,
            createdAt = "2026-01-01T00:00:00Z",
        )
        assertEquals(SocialText.FRIEND, rendre(liens = listOf(orphelin)).title)
    }

    @Test
    fun `le titre d'un cercle se relit dans la liste`() {
        val ui = rendre(ouverte = pieceDeCercle(), cercles = listOf(cercle(nom = "Les trois")))
        assertEquals("Les trois", ui.title)
    }

    @Test
    fun `un cercle disparu de la liste porte quand meme un titre`() {
        val ui = rendre(ouverte = pieceDeCercle(), cercles = emptyList())
        assertEquals(SocialText.CIRCLE, ui.title)
    }

    /**
     * L'original écrasait le nom du serveur : il affichait « Administration », et le serveur, lui,
     * nomme ce cercle « Contact · <nom> ». Lire la liste sans écraser ferait diverger les deux
     * clients sur le même écran.
     */
    @Test
    fun `le cercle de l'administration porte son nom d'ecran, pas celui du serveur`() {
        val ui = rendre(
            ouverte = pieceDeCercle(),
            cercles = listOf(cercle(nom = "Contact · Administration", contact = "admin-1")),
        )
        assertEquals(SocialText.ADMIN_CIRCLE_NAME, ui.title)
    }

    @Test
    fun `l'etat sous le nom n'existe que dans un tete-a-tete`() {
        assertEquals(SocialText.OFFLINE, rendre(ouverte = piece()).statusLabel)
        assertNull(rendre(ouverte = pieceDeCercle(), cercles = listOf(cercle())).statusLabel)
    }

    @Test
    fun `la presence de l'ami se lit dans son apercu`() {
        val ui = rendre(ouverte = piece(apercu = apercu(online = true)))
        assertEquals(SocialText.ONLINE, ui.statusLabel)
    }

    /**
     * Un aperçu **absent** — l'ami ne partage pas sa présence, ou la lecture a échoué — se lit
     * « Hors ligne », et non « on ne sait pas ». C'est ce que fait l'original, et le corriger ici
     * ferait diverger les deux clients sur la même donnée.
     */
    @Test
    fun `un apercu absent se lit hors ligne, comme dans l'original`() {
        assertEquals(SocialText.OFFLINE, rendre(ouverte = piece(apercu = null)).statusLabel)
    }

    @Test
    fun `on ne signale que dans un tete-a-tete`() {
        assertTrue(rendre(ouverte = piece()).canReport)
        assertFalse(rendre(ouverte = pieceDeCercle(), cercles = listOf(cercle())).canReport)
    }

    /**
     * L'avis du dépôt est **publié par la conversation**, et pas seulement par la liste d'amis.
     *
     * La conversation est l'écran qui produit la plupart des avis — « Étape partagée avec cet
     * ami. », « Aucun message reçu à signaler dans cette conversation. », « Entre une date et une
     * heure futures… » —, et la liste n'est plus à l'écran quand ils paraissent. Un avis qui ne
     * transiterait pas jusqu'ici serait écrit pour personne : le geste semblerait n'avoir rien
     * fait, ce qui est exactement ce qu'un avis évite.
     */
    @Test
    fun `l'avis du depot parvient a la conversation`() {
        assertEquals(SocialText.STEP_SHARED, rendre(avis = SocialText.STEP_SHARED).notice)
        assertNull(rendre().notice)
    }

    // -----------------------------------------------------------------------
    // Profil et entraide
    // -----------------------------------------------------------------------

    @Test
    fun `le bloc d'entraide ne se deplie que si on le demande`() {
        val ferme = rendre(saisies = ConversationInputs(toolsOpen = false))
        assertTrue(ferme.showTools)
        assertFalse(ferme.toolsOpen)
        assertNull(ferme.overview)
        assertNull(ferme.goals)

        val ouvert = rendre(saisies = ConversationInputs(toolsOpen = true))
        assertTrue(ouvert.toolsOpen)
        assertNotNull(ouvert.goals)
    }

    /**
     * Le cercle de l'administration réunit des administrateurs qui ne sont pas amis. On y ouvre
     * le bloc de force : rien ne doit sortir.
     */
    @Test
    fun `le bloc d'entraide est cache dans le cercle de l'administration`() {
        val ui = rendre(
            ouverte = pieceDeCercle(),
            cercles = listOf(cercle(contact = "admin-1")),
            saisies = ConversationInputs(toolsOpen = true),
        )
        assertFalse(ui.showTools)
        assertFalse(ui.toolsOpen)
        assertNull(ui.overview)
        assertNull(ui.goals)
        assertNull(ui.appointments)
        assertNull(ui.members)
    }

    @Test
    fun `les objectifs et les rendez-vous n'existent que dans un tete-a-tete`() {
        val lien = rendre(saisies = ConversationInputs(toolsOpen = true))
        assertNotNull(lien.goals)
        assertNotNull(lien.appointments)
        assertNull(lien.members)

        val cercleUi = rendre(
            ouverte = pieceDeCercle(),
            cercles = listOf(cercle()),
            saisies = ConversationInputs(toolsOpen = true),
        )
        assertNull(cercleUi.goals)
        assertNull(cercleUi.appointments)
        assertNotNull(cercleUi.members)
    }

    @Test
    fun `l'apercu d'un ami ne dit jamais deux choses a la fois`() {
        val avec = rendre(
            ouverte = piece(apercu = apercu(objectifLabel = "Mémorisation", pourcent = 40, versets = 12, seances = 3)),
            saisies = ConversationInputs(toolsOpen = true),
        )
        val partage = assertNotNull(avec.overview)
        assertNotNull(partage.goalLine)
        assertNotNull(partage.weekLine)
        assertNull(partage.privateNote)

        val sans = rendre(ouverte = piece(apercu = apercu()), saisies = ConversationInputs(toolsOpen = true))
        val prive = assertNotNull(sans.overview)
        assertNull(prive.goalLine)
        assertNull(prive.weekLine)
        assertEquals(SocialText.PRIVATE_PROGRESS, prive.privateNote)
    }

    @Test
    fun `le passage actuel de l'ami est nomme par le referentiel`() {
        val ui = rendre(
            ouverte = piece(apercu = apercu(debut = 6000, fin = 6004)),
            saisies = ConversationInputs(toolsOpen = true),
        )
        assertEquals(
            SocialText.currentPassage(Quran.reference(Range(6000, 6004))),
            ui.overview?.passageLine,
        )
    }

    @Test
    fun `un passage hors du referentiel disparait au lieu de faire tomber l'ecran`() {
        val ui = rendre(
            ouverte = piece(apercu = apercu(debut = 999_999, fin = 999_999)),
            saisies = ConversationInputs(toolsOpen = true),
        )
        assertNull(ui.overview?.passageLine)
    }

    // -----------------------------------------------------------------------
    // Messages
    // -----------------------------------------------------------------------

    @Test
    fun `le cote d'un message suit son auteur, pas son rang`() {
        val ui = rendre(ouverte = piece(messages = listOf(message("m1", moi), message("m2", ami))))
        assertTrue(ui.messages[0].mine)
        assertFalse(ui.messages[1].mine)
    }

    @Test
    fun `l'auteur d'un message est nomme par le membre, puis par le lien, puis par le repli`() {
        val parLeMembre = rendre(
            ouverte = pieceDeCercle(
                messages = listOf(message("m1", "inconnu", dansUnCercle = true)),
                membres = listOf(membre("inconnu", nom = "Youssef")),
            ),
            cercles = listOf(cercle()),
            liens = emptyList(),
        )
        assertTrue(parLeMembre.messages[0].stamp.startsWith("Youssef"))

        val parLeLien = rendre(
            ouverte = piece(messages = listOf(message("m1", ami))),
            liens = listOf(lienAmitie(nom = "Amina")),
        )
        assertTrue(parLeLien.messages[0].stamp.startsWith("Amina"))

        val parLeRepli = rendre(
            ouverte = pieceDeCercle(messages = listOf(message("m1", "inconnu", dansUnCercle = true))),
            cercles = listOf(cercle()),
            liens = emptyList(),
        )
        // Le repli porte l'heure comme les autres : on compare le **début** de l'en-tête, pas
        // l'en-tête entier — l'instant est lisible ici, et il s'écrit dans le fuseau de la
        // machine, qu'un test n'a pas à connaître.
        assertTrue(parLeRepli.messages[0].stamp.startsWith(SocialText.MEMBER))
    }

    @Test
    fun `l'en-tete d'un message porte l'heure quand elle est lisible`() {
        val iso = "2026-03-10T10:04:00Z"
        val ui = rendre(ouverte = piece(messages = listOf(message("m1", moi, at = iso))))
        assertEquals(SocialText.messageStamp(SocialText.ME, SocialText.clock(iso)), ui.messages[0].stamp)
    }

    /**
     * L'original écrit « Untel · Invalid Date » sur un instant illisible. Ici l'heure disparaît
     * **avec son séparateur** : laisser « Untel · » en suspens afficherait une ponctuation qui
     * n'annonce rien.
     */
    @Test
    fun `un instant illisible laisse le nom seul, sans separateur orphelin`() {
        val ui = rendre(ouverte = piece(messages = listOf(message("m1", moi, at = "pas une date"))))
        assertEquals(SocialText.ME, ui.messages[0].stamp)
    }

    @Test
    fun `l'accuse de lecture ne se pose que sur mes messages, et seulement en tete-a-tete`() {
        val lien = rendre(
            ouverte = piece(
                messages = listOf(message("m1", moi), message("m2", ami)),
                otherReadAt = "2026-03-10T23:00:00Z",
            ),
        )
        assertEquals(SocialText.READ, lien.messages[0].receipt)
        assertNull(lien.messages[1].receipt)

        val cercleUi = rendre(
            ouverte = pieceDeCercle(
                messages = listOf(message("m1", moi, dansUnCercle = true)),
                membres = listOf(membre(moi)),
            ),
            cercles = listOf(cercle()),
        )
        assertNull(cercleUi.messages[0].receipt)
    }

    @Test
    fun `un message non lu se dit Envoye, jamais Lu`() {
        val avant = rendre(
            ouverte = piece(
                messages = listOf(message("m1", moi, at = "2026-03-10T10:00:00Z")),
                otherReadAt = "2026-03-10T09:00:00Z",
            ),
        )
        assertEquals(SocialText.SENT, avant.messages[0].receipt)

        val jamaisLu = rendre(ouverte = piece(messages = listOf(message("m1", moi)), otherReadAt = null))
        assertEquals(SocialText.SENT, jamaisLu.messages[0].receipt)
    }

    @Test
    fun `dans un tete-a-tete, je ne supprime que mes messages`() {
        val ui = rendre(ouverte = piece(messages = listOf(message("m1", moi), message("m2", ami))))
        assertTrue(ui.messages[0].canDelete)
        assertFalse(ui.messages[1].canDelete)
    }

    @Test
    fun `un moderateur de cercle supprime le message d'un autre, un membre non`() {
        val moderateur = rendre(
            ouverte = pieceDeCercle(
                messages = listOf(message("m1", ami, dansUnCercle = true)),
                membres = listOf(membre(moi, role = GroupRole.MODERATOR)),
            ),
            cercles = listOf(cercle()),
        )
        assertTrue(moderateur.messages[0].canDelete)

        val simple = rendre(
            ouverte = pieceDeCercle(
                messages = listOf(message("m1", ami, dansUnCercle = true)),
                membres = listOf(membre(moi)),
            ),
            cercles = listOf(cercle()),
        )
        assertFalse(simple.messages[0].canDelete)
    }

    @Test
    fun `un message supprime ne se supprime plus`() {
        val ui = rendre(ouverte = piece(messages = listOf(message("m1", moi, supprime = true))))
        assertFalse(ui.messages[0].canDelete)
    }

    @Test
    fun `la panne des messages se distingue d'une conversation vide`() {
        val ui = rendre(ouverte = piece(failure = "Réseau indisponible"))
        assertTrue(ui.open)
        assertEquals("Réseau indisponible", ui.failure)
    }

    // -----------------------------------------------------------------------
    // Historique et suspension
    // -----------------------------------------------------------------------

    /**
     * Le bouton d'historique a **trois** états, et pas deux : absent, prêt, en cours. Deux champs
     * séparés — un booléen et un libellé — auraient permis un libellé de chargement sans
     * chargement en cours.
     */
    @Test
    fun `le bouton d'historique a trois etats, et pas deux`() {
        val rien = rendre(ouverte = piece(history = Social.History(hasOlder = false)))
        assertNull(rien.olderLabel)
        assertFalse(rien.canLoadOlder)

        val pret = rendre(ouverte = piece(history = Social.History(hasOlder = true)))
        assertEquals(SocialText.LOAD_OLDER, pret.olderLabel)
        assertTrue(pret.canLoadOlder)

        val enCours = rendre(
            ouverte = piece(history = Social.History(hasOlder = true), loadingOlder = true),
        )
        assertEquals(SocialText.LOADING_OLDER, enCours.olderLabel)
        assertFalse(enCours.canLoadOlder)
    }

    @Test
    fun `la suspension s'affiche tant qu'elle court, y compris sans terme`() {
        val active = rendre(suspension = suspension("2026-04-01T00:00:00Z"))
        assertEquals(SocialText.suspended("Spam"), active.suspension)

        val sansTerme = rendre(suspension = suspension(null))
        assertEquals(SocialText.suspended("Spam"), sansTerme.suspension)

        val echue = rendre(suspension = suspension("2026-03-01T00:00:00Z"))
        assertNull(echue.suspension)
    }

    // -----------------------------------------------------------------------
    // Compositeur
    // -----------------------------------------------------------------------

    @Test
    fun `un brouillon fait d'espaces ne part pas`() {
        assertFalse(rendre(saisies = ConversationInputs(draft = "   ")).canSend)
        assertFalse(rendre(saisies = ConversationInputs(draft = "")).canSend)
        assertTrue(rendre(saisies = ConversationInputs(draft = "Salam")).canSend)
    }

    @Test
    fun `un geste en cours ou une suspension ferme le compositeur`() {
        assertFalse(
            rendre(saisies = ConversationInputs(draft = "Salam"), busy = true).canSend,
        )
        assertFalse(
            rendre(
                saisies = ConversationInputs(draft = "Salam"),
                suspension = suspension("2026-04-01T00:00:00Z"),
            ).canSend,
        )
    }

    @Test
    fun `le partage d'etape est reserve au tete-a-tete`() {
        assertTrue(rendre().canShareProgress)
        assertFalse(rendre(ouverte = pieceDeCercle(), cercles = listOf(cercle())).canShareProgress)
        assertFalse(rendre(busy = true).canShareProgress)
    }

    // -----------------------------------------------------------------------
    // Membres d'un cercle
    // -----------------------------------------------------------------------

    @Test
    fun `le compte des membres ne compte que les adhesions acceptees`() {
        val ui = rendre(
            ouverte = pieceDeCercle(membres = listOf(membre(moi), membre("x", accepte = false, nom = "X"))),
            cercles = listOf(cercle()),
            saisies = ConversationInputs(toolsOpen = true),
        )
        assertEquals(SocialText.membersCount(1), ui.members?.title)
    }

    @Test
    fun `la ligne d'un membre en attente le dit`() {
        val ui = rendre(
            ouverte = pieceDeCercle(membres = listOf(membre("x", nom = "X", accepte = false))),
            cercles = listOf(cercle()),
            saisies = ConversationInputs(toolsOpen = true),
        )
        val ligne = ui.members!!.rows.single()
        assertEquals(
            SocialText.memberLine("X", SocialText.role(GroupRole.MEMBER), pending = true),
            ligne.label,
        )
    }

    @Test
    fun `mon invitation en attente se rejoint, celle d'un autre non`() {
        val mienne = rendre(
            ouverte = pieceDeCercle(membres = listOf(membre(moi, accepte = false, nom = "Moi"))),
            cercles = listOf(cercle()),
            saisies = ConversationInputs(toolsOpen = true),
        )
        val ligne = mienne.members!!.rows.single()
        assertTrue(ligne.canJoin)
        assertTrue(ligne.canDecline)

        val autre = rendre(
            ouverte = pieceDeCercle(membres = listOf(membre("x", accepte = false, nom = "X"))),
            cercles = listOf(cercle()),
            saisies = ConversationInputs(toolsOpen = true),
        )
        assertFalse(autre.members!!.rows.single().canJoin)
    }

    /**
     * Le libellé de modération dépend du rôle **actuel** du membre : c'est pourquoi il est résolu
     * ligne par ligne, et pourquoi un membre qui n'y a pas droit porte `null` — ce qui distingue
     * « pas de bouton » de « bouton sans libellé ».
     */
    @Test
    fun `le proprietaire seul nomme les moderateurs, et jamais lui-meme`() {
        val ui = rendre(
            ouverte = pieceDeCercle(
                membres = listOf(
                    membre(moi, role = GroupRole.OWNER, nom = "Moi"),
                    membre("x", role = GroupRole.MEMBER, nom = "X"),
                    membre("y", role = GroupRole.MODERATOR, nom = "Y"),
                ),
            ),
            cercles = listOf(cercle()),
            saisies = ConversationInputs(toolsOpen = true),
        )
        val carte = assertNotNull(ui.members)
        val lignes = carte.rows.associateBy { it.userId }

        assertEquals(SocialText.NAME_MODERATOR, lignes.getValue("x").moderatorLabel)
        assertEquals(SocialText.UNNAME_MODERATOR, lignes.getValue("y").moderatorLabel)
        assertNull(lignes.getValue(moi).moderatorLabel)

        assertTrue(carte.canDeleteGroup)
    }

    @Test
    fun `un moderateur nomme les moderateurs des autres, mais ne supprime pas le cercle`() {
        val ui = rendre(
            ouverte = pieceDeCercle(
                membres = listOf(
                    membre(moi, role = GroupRole.MODERATOR, nom = "Moi"),
                    membre("x", role = GroupRole.MEMBER, nom = "X"),
                ),
            ),
            cercles = listOf(cercle()),
            saisies = ConversationInputs(toolsOpen = true),
        )
        val carte = assertNotNull(ui.members)
        val lignes = carte.rows.associateBy { it.userId }
        assertNull(lignes.getValue("x").moderatorLabel)
        assertTrue(lignes.getValue("x").canRemove)
        assertFalse(carte.canDeleteGroup)
    }

    /**
     * Le **sens** du geste de modération, et pas seulement son libellé.
     *
     * L'écran reçoit un libellé et un booléen ; c'est le booléen qui dit si l'appui **donne** la
     * modération ou la **retire**. Le déduire du libellé marcherait jusqu'au jour où l'un des deux
     * mots change — et ce jour-là, le bouton ferait le contraire de ce qu'il annonce sans que rien
     * ne le signale. Les deux valeurs sont donc mesurées **ensemble**, sur les deux rôles.
     */
    @Test
    fun `le sens du geste de moderation suit le role actuel du membre`() {
        val ui = rendre(
            ouverte = pieceDeCercle(
                membres = listOf(
                    membre(moi, role = GroupRole.OWNER, nom = "Moi"),
                    membre("mod", role = GroupRole.MODERATOR, nom = "Mod"),
                    membre("x", role = GroupRole.MEMBER, nom = "X"),
                ),
            ),
            cercles = listOf(cercle()),
            saisies = ConversationInputs(toolsOpen = true),
        )
        val lignes = assertNotNull(ui.members).rows.associateBy { it.userId }

        assertEquals(SocialText.UNNAME_MODERATOR, lignes.getValue("mod").moderatorLabel)
        assertFalse(lignes.getValue("mod").grantsModerator)

        assertEquals(SocialText.NAME_MODERATOR, lignes.getValue("x").moderatorLabel)
        assertTrue(lignes.getValue("x").grantsModerator)
    }

    @Test
    fun `un simple membre ne peut ni inviter, ni retirer, ni supprimer`() {
        val ui = rendre(
            ouverte = pieceDeCercle(
                membres = listOf(membre(moi, role = GroupRole.MEMBER, nom = "Moi"), membre("x", nom = "X")),
            ),
            cercles = listOf(cercle()),
            saisies = ConversationInputs(toolsOpen = true),
        )
        val carte = assertNotNull(ui.members)
        assertTrue(carte.invites.isEmpty())
        assertFalse(carte.rows.associateBy { it.userId }.getValue("x").canRemove)
        assertFalse(carte.canDeleteGroup)
    }

    @Test
    fun `le proprietaire invite chacun de ses amis acceptes`() {
        val ui = rendre(
            ouverte = pieceDeCercle(membres = listOf(membre(moi, role = GroupRole.OWNER, nom = "Moi"))),
            cercles = listOf(cercle()),
            liens = listOf(lienAmitie(nom = "Amina")),
            saisies = ConversationInputs(toolsOpen = true),
        )
        val invitation = ui.members!!.invites.single()
        assertEquals(ami, invitation.userId)
        assertEquals(SocialText.inviteMember("Amina"), invitation.label)
    }

    // -----------------------------------------------------------------------
    // Objectifs et rendez-vous
    // -----------------------------------------------------------------------

    @Test
    fun `un objectif ne s'accepte ni deux fois ni le sien`() {
        val ui = rendre(
            ouverte = piece(
                objectifs = listOf(
                    objectifPartage("g1", proposePar = ami),
                    objectifPartage("g2", proposePar = moi),
                    objectifPartage("g3", proposePar = ami, accepte = true),
                ),
            ),
            saisies = ConversationInputs(toolsOpen = true),
        )
        val lignes = ui.goals!!.rows.associateBy { it.id }

        assertTrue(lignes.getValue("g1").canAccept)
        assertEquals(SocialText.GOAL_PENDING, lignes.getValue("g1").state)

        assertFalse(lignes.getValue("g2").canAccept)
        assertFalse(lignes.getValue("g3").canAccept)
        assertEquals(SocialText.GOAL_ACCEPTED, lignes.getValue("g3").state)
    }

    @Test
    fun `un objectif se propose sur un entier de 1 a 14, et pas pendant un geste`() {
        assertTrue(rendre(saisies = ConversationInputs(toolsOpen = true, goalTarget = "3")).goals!!.canPropose)
        assertFalse(rendre(saisies = ConversationInputs(toolsOpen = true, goalTarget = "15")).goals!!.canPropose)
        assertFalse(rendre(saisies = ConversationInputs(toolsOpen = true, goalTarget = "0")).goals!!.canPropose)
        assertFalse(
            rendre(saisies = ConversationInputs(toolsOpen = true, goalTarget = "3"), busy = true)
                .goals!!.canPropose,
        )
    }

    @Test
    fun `la semaine d'un objectif est ecrite telle que le serveur la stocke`() {
        val ui = rendre(
            ouverte = piece(objectifs = listOf(objectifPartage("g1", proposePar = ami, semaine = "2026-03-09", seances = 4))),
            saisies = ConversationInputs(toolsOpen = true),
        )
        assertEquals(SocialText.weekGoal("2026-03-09", 4), ui.goals!!.rows.single().label)
    }

    @Test
    fun `un rendez-vous se dit confirme ou en attente, et s'accepte selon qui l'a propose`() {
        val ui = rendre(
            ouverte = piece(
                rendezVous = listOf(
                    rendezVous("a1", proposePar = ami),
                    rendezVous("a2", proposePar = ami, accepte = true),
                    rendezVous("a3", proposePar = moi),
                ),
            ),
            saisies = ConversationInputs(toolsOpen = true),
        )
        val lignes = ui.appointments!!.rows.associateBy { it.id }

        assertTrue(lignes.getValue("a1").canAccept)
        assertEquals(SocialText.APPOINTMENT_PENDING, lignes.getValue("a1").state)

        assertFalse(lignes.getValue("a2").canAccept)
        assertEquals(SocialText.APPOINTMENT_CONFIRMED, lignes.getValue("a2").state)

        assertFalse(lignes.getValue("a3").canAccept)
    }

    @Test
    fun `un rendez-vous illisible montre l'instant brut, pas un message d'erreur`() {
        val ui = rendre(
            ouverte = piece(rendezVous = listOf(rendezVous("a1", proposePar = ami, at = "pas une date"))),
            saisies = ConversationInputs(toolsOpen = true),
        )
        assertEquals("pas une date", ui.appointments!!.rows.single().label)
    }

    // -----------------------------------------------------------------------
    // Signalement
    // -----------------------------------------------------------------------

    @Test
    fun `le formulaire de signalement suit la longueur du motif, et se ferme sans cible`() {
        assertNull(rendre().report)

        val pret = rendre(saisies = ConversationInputs(reportTarget = "m1", reportReason = "abc"))
        assertEquals("m1", pret.report?.targetId)
        assertTrue(pret.report?.canSend == true)

        val tropCourt = rendre(saisies = ConversationInputs(reportTarget = "m1", reportReason = "ab"))
        assertFalse(tropCourt.report?.canSend == true)

        val espace = rendre(saisies = ConversationInputs(reportTarget = "m1", reportReason = "   "))
        assertFalse(espace.report?.canSend == true)
    }

    /**
     * **Écart assumé.** L'original n'ajoute pas `busy` ici, et un second appui pendant l'envoi
     * dépose deux signalements. Le reste de l'écran porte la garde partout ailleurs ; la
     * reproduire ici aurait recopié une négligence que le serveur paie en données dupliquées.
     */
    @Test
    fun `un signalement ne part pas deux fois`() {
        val ui = rendre(
            saisies = ConversationInputs(reportTarget = "m1", reportReason = "abc"),
            busy = true,
        )
        assertFalse(ui.report?.canSend == true)
    }

    // -----------------------------------------------------------------------
    // Récitation partagée
    // -----------------------------------------------------------------------
    // Le bloc affiche ce que le message **porte** — le passage et sa durée —, sans bouton
    // d'écoute : l'URL signée d'un fichier déposé dans un espace privé n'existe pas encore, et un
    // bouton qui ne joue rien est pire qu'un bouton absent. Ces cas mesurent donc la
    // **nomination** du passage, et le repli quand elle est impossible.

    @Test
    fun `une recitation partagee porte sa reference et sa duree`() {
        val piece = piece(
            messages = listOf(
                message(
                    "m1",
                    ami,
                    kind = ChatMessageKind.RECITATION,
                    recitation = RecitationAttachment(
                        id = "r1",
                        startVerseId = 1,
                        endVerseId = 7,
                        durationMs = 95_000,
                        storagePath = "chemin/r1.m4a",
                    ),
                ),
            ),
        )
        val bloc = rendre(ouverte = piece).messages[0].recitation
        assertNotNull(bloc)
        assertEquals(Quran.reference(Range(1, 7)), bloc.reference)
        assertEquals(SocialText.durationLabel(95_000), bloc.durationLabel)
    }

    @Test
    fun `un message de texte n'a pas de bloc de recitation`() {
        assertNull(rendre(ouverte = piece(messages = listOf(message("m1", ami)))).messages[0].recitation)
    }

    @Test
    fun `une recitation sans enregistrement se dit indisponible, sans duree`() {
        val ui = rendre(
            ouverte = piece(
                messages = listOf(message("m1", ami, kind = ChatMessageKind.RECITATION, recitation = null)),
            ),
        )
        val bloc = ui.messages[0].recitation
        assertNotNull(bloc)
        assertEquals(SocialText.RECORDING_MISSING, bloc.reference)
        assertNull(bloc.durationLabel)
    }

    /**
     * La plage vient du serveur. `Quran.reference` lève hors du corpus : l'écran doit survivre à
     * une ligne corrompue, pas planter à distance.
     */
    @Test
    fun `une recitation hors du referentiel ne fait pas tomber l'ecran`() {
        val ui = rendre(
            ouverte = piece(
                messages = listOf(
                    message(
                        "m1",
                        ami,
                        kind = ChatMessageKind.RECITATION,
                        recitation = RecitationAttachment("r1", 999_999, 999_999, 1_000, "chemin/r1.m4a"),
                    ),
                ),
            ),
        )
        val bloc = ui.messages[0].recitation
        assertNotNull(bloc)
        assertEquals(SocialText.RECORDING_MISSING, bloc.reference)
        assertNull(bloc.durationLabel)
    }

    companion object {

        /**
         * Charge le référentiel coranique une fois pour toute la classe.
         *
         * Les cas qui nomment une plage ne peuvent pas être joués avant : `Quran.reference` lève
         * sur un référentiel vide. Le test le charge donc lui-même, exactement comme
         * l'application le fait au démarrage.
         */
        @BeforeClass
        @JvmStatic
        fun chargerLeReferentiel() {
            Quran.initialize(QuranDataLoader.loadFromClasspath())
        }
    }
}
