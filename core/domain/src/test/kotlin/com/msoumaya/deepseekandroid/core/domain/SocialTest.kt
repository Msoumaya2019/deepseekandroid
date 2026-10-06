package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.ChatMessage
import com.msoumaya.deepseekandroid.core.model.ChatMessageKind
import com.msoumaya.deepseekandroid.core.model.ConversationSummary
import com.msoumaya.deepseekandroid.core.model.FriendBrief
import com.msoumaya.deepseekandroid.core.model.FriendGroup
import com.msoumaya.deepseekandroid.core.model.FriendLink
import com.msoumaya.deepseekandroid.core.model.FriendLinkStatus
import com.msoumaya.deepseekandroid.core.model.GroupMember
import com.msoumaya.deepseekandroid.core.model.GroupRole
import com.msoumaya.deepseekandroid.core.model.SocialSuspension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Règles de l'espace « Amis ».
 *
 * Les cas visent ce qui peut **mentir** — une liste filtrée à l'envers, un accusé de lecture
 * prématuré, un rendez-vous accepté dans le passé —, et non ce qui peut planter. Chaque test
 * dont la règle reproduit une décision de l'original le dit dans son commentaire, et chaque
 * écart assumé est mesuré.
 */
class SocialTest {

    private val moi = "moi"

    // ------------------------------------------------------------------ fabriques

    private fun lien(
        id: String,
        statut: FriendLinkStatus = FriendLinkStatus.ACCEPTED,
        demandeur: String = moi,
        destinataire: String = "autre-$id",
        nom: String? = "Ami $id",
        cree: String = "2026-01-01T00:00:00Z",
        bloquePar: String? = null,
    ) = FriendLink(
        id = id,
        requesterId = demandeur,
        recipientId = destinataire,
        status = statut,
        createdAt = cree,
        blockedBy = bloquePar,
        other = nom?.let { FriendBrief(id = destinataire, displayName = it) },
    )

    private fun message(
        id: String,
        auteur: String,
        cree: String = "2026-03-10T10:00:00Z",
        supprime: String? = null,
        groupe: String? = null,
    ) = ChatMessage(
        id = id,
        senderId = auteur,
        kind = ChatMessageKind.TEXT,
        body = "corps",
        createdAt = cree,
        groupId = groupe,
        deletedAt = supprime,
    )

    private fun membre(
        id: String,
        role: GroupRole = GroupRole.MEMBER,
        accepte: String? = "2026-01-01T00:00:00Z",
        nom: String? = null,
    ) = GroupMember(
        groupId = "g",
        userId = id,
        role = role,
        acceptedAt = accepte,
        profile = nom?.let {
            com.msoumaya.deepseekandroid.core.model.FriendProfile(
                id = id,
                displayName = it,
                inviteCode = "X",
            )
        },
    )

    // ------------------------------------------------------------------ erreurs

    @Test
    fun `le message d'erreur suit l'ordre message puis details puis code`() {
        assertEquals(
            "explicite",
            Social.errorText(message = "explicite", details = "detaille", code = "42"),
            "le message explicite l'emporte",
        )
        assertEquals(
            "detaille",
            Social.errorText(message = null, details = "detaille", code = "42"),
            "a defaut, les details",
        )
        assertEquals(
            SocialText.serviceError("42"),
            Social.errorText(message = null, details = null, code = "42"),
            "a defaut, le code",
        )
        assertEquals(
            SocialText.GENERIC_ERROR,
            Social.errorText(),
            "sans rien, le repli generique",
        )
    }

    @Test
    fun `un message d'erreur vide ne compte pas comme un message`() {
        // Sans ce cas, l'ecran afficherait un cadre **vide** la ou il y a une erreur a lire :
        // une chaine faite d'espaces est une absence, pas un contenu.
        assertEquals(SocialText.GENERIC_ERROR, Social.errorText(message = "   "))
        assertEquals("detaille", Social.errorText(message = "  ", details = "detaille"))
    }

    // ------------------------------------------------------------------ liens

    @Test
    fun `l'autre participant depend de qui je suis dans le lien`() {
        val jeDemande = lien("a", demandeur = moi, destinataire = "lui")
        val ilDemande = lien("b", demandeur = "lui", destinataire = moi)
        assertEquals("lui", Social.otherId(jeDemande, moi))
        assertEquals("lui", Social.otherId(ilDemande, moi))
    }

    @Test
    fun `seuls les liens acceptes comptent comme des amis`() {
        val liens = listOf(
            lien("a", FriendLinkStatus.ACCEPTED),
            lien("b", FriendLinkStatus.PENDING),
            lien("c", FriendLinkStatus.BLOCKED),
            lien("d", FriendLinkStatus.ACCEPTED),
        )
        assertEquals(2, Social.acceptedCount(liens))
    }

    @Test
    fun `les trois listes d'invitations ne se recouvrent pas et couvrent tout`() {
        val liens = listOf(
            lien("recue", FriendLinkStatus.PENDING, demandeur = "lui", destinataire = moi),
            lien("envoyee", FriendLinkStatus.PENDING, demandeur = moi, destinataire = "lui"),
            lien("bloquee", FriendLinkStatus.BLOCKED, demandeur = moi, bloquePar = moi),
            lien("bloque-par-lui", FriendLinkStatus.BLOCKED, demandeur = "lui", bloquePar = "lui"),
            lien("acceptee", FriendLinkStatus.ACCEPTED),
        )
        val recues = Social.receivedInvitations(liens, moi).map { it.id }
        val envoyees = Social.sentInvitations(liens, moi).map { it.id }
        val bloquees = Social.blockedLinks(liens, moi).map { it.id }

        assertEquals(listOf("recue"), recues)
        assertEquals(listOf("envoyee"), envoyees)
        // Un compte qui m'a bloque n'est pas dans **ma** liste de blocages : c'est l'autre qui a
        // la main, et proposer « Debloquer » serait un geste sans effet.
        assertEquals(listOf("bloquee"), bloquees)
        assertEquals(3, recues.size + envoyees.size + bloquees.size)
    }

    // ------------------------------------------------------------------ liste d'amis

    @Test
    fun `la liste ne garde que les amis acceptes`() {
        val liens = listOf(
            lien("a", FriendLinkStatus.ACCEPTED, nom = "Amina"),
            lien("b", FriendLinkStatus.PENDING, nom = "Bilal"),
        )
        assertEquals(listOf("a"), Social.visibleFriends(liens, moi).map { it.id })
    }

    @Test
    fun `le filtre Demandes vide la liste au lieu de la remplir`() {
        // Dans l'original, la section des demandes vit **sous** la liste : choisir ce filtre la
        // deplie sans meler les deux. Un filtre qui garderait les amis afficherait donc les deux
        // listes en meme temps.
        val liens = listOf(lien("a", FriendLinkStatus.ACCEPTED))
        assertTrue(Social.visibleFriends(liens, moi, filter = Social.Filter.REQUESTS).isEmpty())
    }

    @Test
    fun `le filtre En ligne exclut une presence inconnue, pas seulement une presence fausse`() {
        val liens = listOf(lien("a"), lien("b"), lien("c"))
        val presence = mapOf("autre-a" to true, "autre-b" to false)
        val visibles = Social.visibleFriends(
            liens,
            moi,
            filter = Social.Filter.ONLINE,
            online = presence,
        ).map { it.id }
        // « c » n'est pas dans la table : c'est « on ne sait pas », et l'inclure ferait passer un
        // ami injoignable pour connecte.
        assertEquals(listOf("a"), visibles)
    }

    @Test
    fun `la recherche ignore la casse`() {
        val liens = listOf(lien("a", nom = "Amina"), lien("b", nom = "Bilal"))
        assertEquals(
            listOf("a"),
            Social.visibleFriends(liens, moi, query = "AMIN").map { it.id },
        )
    }

    @Test
    fun `le tri suit le dernier message, et retombe sur la date du lien`() {
        val liens = listOf(
            lien("vieille", cree = "2026-01-01T00:00:00Z"),
            lien("recente", cree = "2026-01-02T00:00:00Z"),
        )
        val resumes = mapOf(
            "vieille" to ConversationSummary("coucou", "2026-03-10T09:00:00Z"),
        )
        // « vieille » remonte : son dernier message est plus recent que la date du lien de
        // « recente ». Trier sur la date du lien figerait la liste a l'instant de l'amitie.
        assertEquals(
            listOf("vieille", "recente"),
            Social.visibleFriends(liens, moi, summaries = resumes).map { it.id },
        )
    }

    @Test
    fun `la liste est bornee a cinq entrees, et le pli la libere`() {
        val liens = (1..7).map { lien("l$it") }
        assertEquals(Social.LIST_LIMIT, Social.visibleFriends(liens, moi).size)
        assertEquals(7, Social.visibleFriends(liens, moi, all = true).size)
        assertEquals(5, Social.LIST_LIMIT)
    }

    // ------------------------------------------------------------------ cercles

    @Test
    fun `seuls les membres ayant accepte sont comptes`() {
        val membres = listOf(
            membre("a", GroupRole.OWNER),
            membre("b", GroupRole.MEMBER),
            membre("c", GroupRole.MEMBER, accepte = null),
        )
        assertEquals(2, Social.acceptedMemberCount(membres))
    }

    private fun cercle(id: String, contact: String? = null) = FriendGroup(
        id = id,
        name = "Cercle $id",
        ownerId = moi,
        createdAt = "2026-01-01T00:00:00Z",
        contactUserId = contact,
    )

    /**
     * Le cercle de l'administration se reconnaît à son **marqueur** (`contactUserId`), et non à
     * son nom : c'est le serveur qui le pose, et un nom se change. Le cas construit deux cercles
     * dont l'un seulement est marqué, pour que la règle ne puisse pas se tromper de critère.
     */
    @Test
    fun `le cercle de l'administration se reconnait a son marqueur, pas a son nom`() {
        val cercles = listOf(cercle("ordinaire"), cercle("administration", contact = "admin-1"))
        assertFalse(Social.isAdminContact(cercles, "ordinaire"))
        assertTrue(Social.isAdminContact(cercles, "administration"))
    }

    /**
     * **Le sens du repli est mesuré.** Un cercle absent de la liste — relecture partielle, cercle
     * supprimé — rend `false`. Accorder par défaut ouvrirait le partage de progression à un cercle
     * ordinaire ; refuser par défaut ne fait que cacher un bloc. Les deux fautes ne coûtent pas
     * la même chose, et c'est cette asymétrie qui fixe la décision.
     */
    @Test
    fun `un cercle inconnu n'est jamais celui de l'administration`() {
        val cercles = listOf(cercle("administration", contact = "admin-1"))
        assertFalse(Social.isAdminContact(cercles, "disparu"))
        assertFalse(Social.isAdminContact(cercles, null))
        assertFalse(Social.isAdminContact(emptyList(), "administration"))
    }

    // ------------------------------------------------------------------ auteurs

    @Test
    fun `le nom de l'auteur suit l'ordre moi puis membre puis ami puis repli`() {
        val membres = listOf(membre("m", nom = "Membre Nomme"))
        val liens = listOf(lien("l", demandeur = moi, destinataire = "a", nom = "Ami Nomme"))

        assertEquals(SocialText.ME, Social.senderName(moi, moi, membres, liens))
        assertEquals("Membre Nomme", Social.senderName("m", moi, membres, liens))
        assertEquals("Ami Nomme", Social.senderName("a", moi, membres, liens))
        assertEquals(SocialText.MEMBER, Social.senderName("inconnu", moi, membres, liens))
    }

    @Test
    fun `un membre sans profil ne masque pas le nom de l'ami`() {
        // L'ordre compte : un membre entre dans le cercle **avant** d'avoir un profil charge. Le
        // chercher d'abord ne doit pas faire tomber le repli a « Membre » alors que le lien, lui,
        // porte le nom.
        val membres = listOf(membre("a", nom = null))
        val liens = listOf(lien("l", demandeur = moi, destinataire = "a", nom = "Ami Nomme"))
        assertEquals("Ami Nomme", Social.senderName("a", moi, membres, liens))
    }

    // ------------------------------------------------------------------ suppression

    @Test
    fun `un message deja supprime ne se supprime plus`() {
        assertFalse(Social.canDeleteMessage(message("m", moi, supprime = "2026-03-10T11:00:00Z"), moi))
    }

    @Test
    fun `mon message m'appartient, celui d'un autre non`() {
        assertTrue(Social.canDeleteMessage(message("m", moi), moi))
        assertFalse(Social.canDeleteMessage(message("m", "lui"), moi))
    }

    @Test
    fun `dans un cercle un moderateur supprime le message d'un autre`() {
        val moderateur = listOf(membre(moi, GroupRole.MODERATOR), membre("lui"))
        val simple = listOf(membre(moi, GroupRole.MEMBER), membre("lui"))
        val autre = message("m", "lui", groupe = "g")

        assertTrue(Social.canDeleteMessage(autre, moi, moderateur))
        assertTrue(
            Social.canDeleteMessage(autre, moi, listOf(membre(moi, GroupRole.OWNER))),
            "le proprietaire aussi",
        )
        assertFalse(Social.canDeleteMessage(autre, moi, simple))
    }

    @Test
    fun `dans une conversation privee on ne supprime pas les propos d'un tiers`() {
        // On les signale. Un message prive de l'autre n'est jamais supprimable, meme pour un
        // moderateur : le role ne s'applique qu'aux cercles.
        val moderateur = listOf(membre(moi, GroupRole.MODERATOR))
        assertFalse(Social.canDeleteMessage(message("m", "lui", groupe = null), moi, moderateur))
    }

    // ------------------------------------------------------------------ signalement

    @Test
    fun `le message a signaler est le dernier recu, non supprime`() {
        val messages = listOf(
            message("vieux", "lui", cree = "2026-03-10T08:00:00Z"),
            message("recent", "lui", cree = "2026-03-10T09:00:00Z"),
            message("a-moi", moi, cree = "2026-03-10T09:30:00Z"),
            message("supprime", "lui", cree = "2026-03-10T10:00:00Z", supprime = "2026-03-10T10:01:00Z"),
        )
        assertEquals("recent", assertNotNull(Social.reportable(messages, moi)).id)
    }

    @Test
    fun `sans message recu il n'y a rien a signaler`() {
        val messages = listOf(message("a-moi", moi))
        assertNull(Social.reportable(messages, moi))
    }

    // ------------------------------------------------------------------ accuse de lecture

    @Test
    fun `un accuse de lecture absent ne dit jamais Lu`() {
        val m = message("m", moi, cree = "2026-03-10T10:00:00Z")
        assertEquals(SocialText.SENT, Social.readState(m, null))
    }

    @Test
    fun `la position de lecture doit etre posterieure au message`() {
        val m = message("m", moi, cree = "2026-03-10T10:00:00Z")
        assertEquals(
            SocialText.SENT,
            Social.readState(m, "2026-03-10T09:59:59Z"),
            "lue avant l'envoi : c'est Envoye",
        )
        assertEquals(
            SocialText.READ,
            Social.readState(m, "2026-03-10T10:00:00Z"),
            "a la seconde meme : c'est Lu",
        )
        assertEquals(SocialText.READ, Social.readState(m, "2026-03-10T10:05:00Z"))
    }

    @Test
    fun `le mien se distingue de celui de l'autre`() {
        assertTrue(Social.isMine(message("m", moi), moi))
        assertFalse(Social.isMine(message("m", "lui"), moi))
    }

    // ------------------------------------------------------------------ duree

    @Test
    fun `la duree d'un enregistrement est en minutes et secondes`() {
        assertEquals("0:00", Social.duration(0))
        assertEquals("0:01", Social.duration(1_000))
        assertEquals("0:59", Social.duration(59_000))
        assertEquals("1:00", Social.duration(60_000))
        // Les secondes sont completees a deux chiffres, les minutes non : « 1:05 », pas « 1:5 ».
        assertEquals("1:05", Social.duration(65_000))
        assertEquals("60:00", Social.duration(3_600_000))
    }

    // ------------------------------------------------------------------ objectif partage

    @Test
    fun `le nombre de seances va de un a quatorze`() {
        assertEquals(1, Social.targetSessions("1"))
        assertEquals(14, Social.targetSessions("14"))
        assertNull(Social.targetSessions("0"))
        assertNull(Social.targetSessions("15"))
        assertNull(Social.targetSessions("abc"))
        assertNull(Social.targetSessions(""))
    }

    @Test
    fun `une saisie entouree d'espaces reste valide, une forme non decimale non`() {
        assertEquals(7, Social.targetSessions(" 7 "))
        // **Ecart assume et mesure** : `Number.isInteger(Number("3.0"))` vaut `true` en
        // JavaScript, et `Number("0x3")` vaut 3. Ces formes ne sont pas des entiers ecrits en
        // decimal : le portage est plus etroit, et ce test fige l'ecart.
        assertNull(Social.targetSessions("3.0"))
        assertNull(Social.targetSessions("0x3"))
        assertNull(Social.targetSessions("3,0"))
    }

    @Test
    fun `la semaine de l'objectif commence le lundi`() {
        // 2026-03-10 est un mardi, 2026-03-09 le lundi de sa semaine.
        assertEquals("2026-03-09", Social.goalWeek("2026-03-10"))
        assertEquals("2026-03-09", Social.goalWeek("2026-03-09"), "un lundi est son propre debut")
        assertEquals("2026-03-09", Social.goalWeek("2026-03-15"), "un dimanche appartient a la veille")
        assertEquals("2026-03-16", Social.goalWeek("2026-03-16"), "le lundi suivant ouvre la suivante")
    }

    // ------------------------------------------------------------------ rendez-vous

    @Test
    fun `un rendez-vous futur est accepte et converti en instant`() {
        val instant = Social.appointment("2026-04-01 12:00", nowIso = "2026-03-10T10:00:00Z")
        assertNotNull(instant)
        assertTrue(instant.endsWith("Z"), "un instant ISO se termine par Z : $instant")
    }

    @Test
    fun `un rendez-vous passe est refuse`() {
        assertNull(Social.appointment("2026-01-01 12:00", nowIso = "2026-03-10T10:00:00Z"))
    }

    @Test
    fun `une date qui n'existe pas est refusee au lieu d'etre corrigee`() {
        // « 2026-02-31 » respecte le format, et un analyseur permissif la corrigerait en silence
        // au 3 mars : le rendez-vous serait alors propose a une date que personne n'a ecrite.
        assertNull(Social.appointment("2026-02-31 12:00", nowIso = "2026-01-01T00:00:00Z"))
    }

    @Test
    fun `le format du rendez-vous est ancre des deux cotes`() {
        val now = "2026-01-01T00:00:00Z"
        assertNull(Social.appointment("2026-04-01", now))
        assertNull(Social.appointment("01/04/2026 12:00", now))
        assertNull(Social.appointment("2026-4-1 12:00", now), "les chiffres sont sur deux positions")
        // L'original ne rogne pas la saisie, et son motif est ancre : l'espace finale est refusee.
        assertNull(Social.appointment("2026-04-01 12:00 ", now))
    }

    // ------------------------------------------------------------------ moderation

    @Test
    fun `une suspension sans terme est active pour toujours`() {
        val now = "2026-03-10T10:00:00Z"
        assertFalse(Social.isSuspended(null, now), "aucune suspension")
        assertTrue(
            Social.isSuspended(SocialSuspension("u", "motif", now, suspendedUntil = null), now),
            "sans terme : lire « expiree » rouvrirait la messagerie a qui vient d'en etre ecarte",
        )
        assertTrue(Social.isSuspended(SocialSuspension("u", "m", now, "2026-04-01T00:00:00Z"), now))
        assertFalse(Social.isSuspended(SocialSuspension("u", "m", now, "2026-01-01T00:00:00Z"), now))
    }

    @Test
    fun `un motif de signalement demande trois caracteres utiles`() {
        assertTrue(Social.validReportReason("abc"))
        assertTrue(Social.validReportReason("  abc  "))
        assertFalse(Social.validReportReason("ab"))
        assertFalse(Social.validReportReason("   "))
        assertEquals(3, Social.MIN_REPORT_REASON)
    }

    @Test
    fun `un nom de cercle demande deux caracteres utiles`() {
        assertTrue(Social.validGroupName("ab"))
        assertFalse(Social.validGroupName("a"))
        assertFalse(Social.validGroupName("  a  "))
        assertEquals(2, Social.MIN_GROUP_NAME)
    }

    // ------------------------------------------------------------------ filtre

    @Test
    fun `les trois filtres sont dans l'ordre du selecteur`() {
        assertEquals(
            listOf(SocialText.FILTER_ALL, SocialText.FILTER_ONLINE, SocialText.FILTER_REQUESTS),
            Social.Filter.entries.map { it.label },
        )
    }

    // ------------------------------------------------------------------ conversation

    @Test
    fun `la page de messages est la meme pour la requete et pour la decision`() {
        // Le nombre sert deux fois : la requete le demande au serveur, et la regle « reste-t-il
        // des messages plus anciens ? » le relit pour savoir si la page est **pleine**. Une
        // divergence entre les deux ne casserait rien : elle ferait dire « il n'y a rien avant »,
        // ce qui est faux et muet. Ce test fige la valeur ; un controle de forme, dans
        // `core:data`, verifie que la requete la lit bien **ici** au lieu de la recopier.
        assertEquals(50, Social.MESSAGE_PAGE)
    }

    @Test
    fun `une page pleine ouvre l'historique, une page incomplete ne l'ouvre pas`() {
        assertEquals(
            Social.History(hasOlder = true),
            Social.afterLatestPage(Social.History(), Social.MESSAGE_PAGE),
        )
        assertEquals(
            Social.History(hasOlder = false),
            Social.afterLatestPage(Social.History(), Social.MESSAGE_PAGE - 1),
            "une page incomplete est la premiere de la conversation",
        )
    }

    @Test
    fun `une page recente courte ne referme pas l'historique`() {
        // C'est la seule asymetrie entre les deux transitions, et elle compte : la page recente
        // redevient pleine des qu'un message arrive. Si elle pouvait refermer la porte, le bouton
        // « Charger les messages precedents » clignoterait a chaque rafraichissement.
        val ouvert = Social.History(hasOlder = true)
        assertEquals(ouvert, Social.afterLatestPage(ouvert, 1))
        assertEquals(ouvert, Social.afterLatestPage(ouvert, 0))
    }

    @Test
    fun `une page ancienne incomplete referme l'historique pour toujours`() {
        val ferme = Social.afterOlderPage(Social.History(hasOlder = true), Social.MESSAGE_PAGE - 1)
        assertFalse(ferme.hasOlder, "on a atteint le debut de la conversation")
        assertTrue(ferme.exhausted)

        // Et une page recente pleine ne le rouvre **pas** : c'est ce que `exhausted` protege.
        assertEquals(
            ferme,
            Social.afterLatestPage(ferme, Social.MESSAGE_PAGE),
            "un debut connu ne se rouvre pas",
        )
    }

    @Test
    fun `une page ancienne pleine laisse l'historique ouvert`() {
        val ouvert = Social.afterOlderPage(Social.History(hasOlder = false), Social.MESSAGE_PAGE)
        assertTrue(ouvert.hasOlder)
        assertFalse(ouvert.exhausted)
    }

    @Test
    fun `la fusion remplace par identifiant au lieu d'empiler`() {
        val ancien = message("a", "lui")
        val corrige = ancien.copy(deletedAt = "2026-03-10T11:00:00Z")

        val fusion = Social.mergeMessages(listOf(ancien), listOf(corrige))

        assertEquals(1, fusion.size, "le meme identifiant ne s'empile pas")
        assertEquals("2026-03-10T11:00:00Z", fusion.single().deletedAt, "la page recue remplace")
    }

    @Test
    fun `la fusion trie par instant croissant, donc une page ancienne passe devant`() {
        val recent = message("r", "lui", cree = "2026-03-10T10:00:00Z")
        val vieux = message("v", "moi", cree = "2026-03-09T10:00:00Z")

        // La page ancienne est lue **apres** la recente : sans tri, le debut de la conversation
        // se peindrait a la fin.
        val fusion = Social.mergeMessages(listOf(recent), listOf(vieux))

        assertEquals(listOf("v", "r"), fusion.map { it.id })
    }

    @Test
    fun `une page vide laisse la liste intacte`() {
        val liste = listOf(message("a", "lui"), message("b", "moi"))
        // L'identite est verifiee, pas seulement l'egalite : c'est le retour anticipe qui la
        // donne, et il evite de reconstruire et de retrier une liste inchangee a chaque relecture.
        assertTrue(
            Social.mergeMessages(liste, emptyList()) === liste,
            "une page vide ne doit rien reconstruire",
        )
    }

    @Test
    fun `le message le plus recent est le dernier`() {
        assertNull(Social.newestId(emptyList()), "une conversation vide n'a pas de dernier")
        assertEquals("b", Social.newestId(listOf(message("a", "lui"), message("b", "moi"))))
    }

    @Test
    fun `la ligne d'etat suit l'ordre activite, cercle, presence`() {
        assertEquals(
            SocialText.TYPING,
            Social.statusLine(otherTyping = true, adminContact = true, isOnline = true),
            "l'activite passe avant tout le reste : c'est ce qui se passe maintenant",
        )
        assertEquals(
            SocialText.ADMIN_CONTACT_LABEL,
            Social.statusLine(adminContact = true, isOnline = true),
        )
        assertEquals(SocialText.ONLINE, Social.statusLine(isOnline = true))
        assertEquals(SocialText.OFFLINE, Social.statusLine(isOnline = false))
    }

    @Test
    fun `un apercu absent se lit hors ligne, comme dans l'original`() {
        // C'est une imprecision **portee telle quelle** : `overview` nul veut dire « on ne sait
        // pas » — l'ami ne partage pas sa presence, ou la lecture a echoue —, et l'original en
        // ecrit « Hors ligne ». La corriger ici ferait diverger les deux clients sur la meme
        // donnee, donc elle est figee.
        assertEquals(SocialText.OFFLINE, Social.statusLine(isOnline = null))
    }

    // ------------------------------------------------------------------ droits sur un cercle

    @Test
    fun `le proprietaire seul nomme les moderateurs`() {
        val proprietaire = listOf(membre("moi", GroupRole.OWNER), membre("lui"))
        val moderateur = listOf(membre("moi", GroupRole.MODERATOR), membre("lui"))

        assertTrue(Social.canToggleModerator(membre("lui"), proprietaire, "moi"))
        assertFalse(Social.canToggleModerator(membre("lui"), moderateur, "moi"), "un moderateur ne nomme pas")
    }

    @Test
    fun `le proprietaire ne peut pas se demettre lui-meme`() {
        val moi = membre("moi", GroupRole.OWNER)
        // Sans ce refus, le proprietaire pourrait laisser le cercle sans personne pour le gerer.
        assertFalse(Social.canToggleModerator(moi, listOf(moi), "moi"))
    }

    @Test
    fun `une invitation en attente ne se nomme pas moderatrice`() {
        val enAttente = membre("lui", accepte = null)
        assertFalse(Social.canToggleModerator(enAttente, listOf(membre("moi", GroupRole.OWNER), enAttente), "moi"))
    }

    @Test
    fun `un moderateur ne peut pas retirer le proprietaire`() {
        val proprietaire = membre("lui", GroupRole.OWNER)
        val membres = listOf(membre("moi", GroupRole.MODERATOR), proprietaire)

        assertFalse(
            Social.canRemoveGroupMember(proprietaire, membres, "moi"),
            "un moderateur qui exclut le proprietaire pourrait s'emparer du cercle",
        )
        assertTrue(Social.canRemoveGroupMember(membre("tiers"), membres, "moi"))
        assertFalse(
            Social.canRemoveGroupMember(membre("tiers"), membres, "tiers"),
            "on quitte un cercle, on ne s'en exclut pas",
        )
    }

    @Test
    fun `inviter un ami demande d'etre gestionnaire`() {
        assertTrue(Social.canInviteToGroup(listOf(membre("moi", GroupRole.OWNER)), "moi"))
        assertTrue(Social.canInviteToGroup(listOf(membre("moi", GroupRole.MODERATOR)), "moi"))
        assertFalse(Social.canInviteToGroup(listOf(membre("moi")), "moi"))
    }

    @Test
    fun `supprimer le cercle est reserve au proprietaire`() {
        assertTrue(Social.canDeleteGroup(listOf(membre("moi", GroupRole.OWNER)), "moi"))
        assertFalse(Social.canDeleteGroup(listOf(membre("moi", GroupRole.MODERATOR)), "moi"))
    }

    @Test
    fun `mon invitation en attente se distingue de mon adhesion`() {
        assertTrue(Social.isPendingForMe(membre("moi", accepte = null), "moi"))
        assertFalse(Social.isPendingForMe(membre("moi"), "moi"))
        assertFalse(
            Social.isPendingForMe(membre("lui", accepte = null), "moi"),
            "l'invitation d'un autre ne m'invite pas, moi",
        )
    }

    // ------------------------------------------------------------------ composer et partage

    @Test
    fun `un message fait d'espaces ne part pas`() {
        assertFalse(
            Social.canSendMessage("   ", busy = false, suspended = false),
            "une ligne d'espaces ecrirait un message vide chez l'autre",
        )
        assertTrue(Social.canSendMessage("salam", busy = false, suspended = false))
        assertFalse(Social.canSendMessage("salam", busy = true, suspended = false))
        assertFalse(Social.canSendMessage("salam", busy = false, suspended = true))
    }

    @Test
    fun `le partage d'etape est reserve au tete-a-tete`() {
        assertTrue(Social.canShareProgress(isLink = true, adminContact = false))
        assertFalse(Social.canShareProgress(isLink = false, adminContact = false), "pas dans un cercle")
        assertFalse(
            Social.canShareProgress(isLink = true, adminContact = true),
            "on ne partage pas sa progression avec la moderation",
        )
    }

    @Test
    fun `le bloc profil et entraide est cache dans le cercle de l'administration`() {
        assertTrue(Social.showsFriendTools(adminContact = false))
        assertFalse(Social.showsFriendTools(adminContact = true))
    }

    @Test
    fun `une proposition ne s'accepte ni deux fois ni la sienne`() {
        assertTrue(Social.canAcceptProposal(null, proposedBy = "lui", me = "moi"))
        assertFalse(
            Social.canAcceptProposal("2026-01-01T00:00:00Z", proposedBy = "lui", me = "moi"),
            "deja acceptee",
        )
        assertFalse(
            Social.canAcceptProposal(null, proposedBy = "moi", me = "moi"),
            "accepter sa propre proposition ecraserait l'attente de l'autre",
        )
    }
}
