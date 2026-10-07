package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.ChatMessage
import com.msoumaya.deepseekandroid.core.model.ConversationSummary
import com.msoumaya.deepseekandroid.core.model.FriendGroup
import com.msoumaya.deepseekandroid.core.model.FriendLink
import com.msoumaya.deepseekandroid.core.model.FriendLinkStatus
import com.msoumaya.deepseekandroid.core.model.GroupMember
import com.msoumaya.deepseekandroid.core.model.GroupRole
import com.msoumaya.deepseekandroid.core.model.SocialSuspension
import java.time.Instant
import java.time.LocalDateTime

/**
 * Règles de l'espace « Amis ».
 *
 * Porté depuis `src/SocialScreens.tsx`. Tout ce qui **décide** y est rassemblé — quel ami est
 * visible, dans quel ordre, qui peut supprimer un message, ce qu'un rendez-vous doit être pour
 * être accepté —, et rien de ce qui parle au réseau. Ces règles s'éprouvent donc sans
 * coroutine, sans serveur et sans appareil.
 *
 * **Pourquoi elles vivent ici.** Une liste d'amis filtrée à l'envers n'affiche pas d'erreur :
 * elle affiche **moins de monde**, ce qui se lit comme « il n'y a personne ». Un rendez-vous
 * accepté dans le passé ne se voit qu'une fois la date passée. Un accusé de lecture faux
 * annonce à l'auteur qu'on l'a lu alors que non. Aucune de ces fautes ne casse quoi que ce
 * soit — elles mentent, et c'est pour cela qu'elles sont écrites ici et figées par des tests.
 */
object Social {

    /** Nombre d'amis affichés avant « Voir tout ». */
    const val LIST_LIMIT = 5

    /** Bornes de l'objectif de séances partagé, telles que le formulaire les refuse. */
    const val MIN_TARGET_SESSIONS = 1
    const val MAX_TARGET_SESSIONS = 14

    /** Longueur minimale d'un motif de signalement, après rognage. */
    const val MIN_REPORT_REASON = 3

    /** Longueur minimale du nom d'un cercle, après rognage. */
    const val MIN_GROUP_NAME = 2

    /**
     * Filtre de la liste d'amis.
     *
     * L'ordre est celui du sélecteur segmenté de l'original — `['Tous','En ligne','Demandes']`
     * — et il est **visible**. Le type vit ici et non dans `SocialText` : c'est un paramètre de
     * règle, et ses libellés lui viennent de [SocialText].
     */
    enum class Filter(val label: String) {
        ALL(SocialText.FILTER_ALL),
        ONLINE(SocialText.FILTER_ONLINE),
        REQUESTS(SocialText.FILTER_REQUESTS),
    }

    /** Format du champ de rendez-vous : `AAAA-MM-JJ HH:mm`, ancré des deux côtés. */
    private val APPOINTMENT = Regex("""^(\d{4}-\d{2}-\d{2}) (\d{2}:\d{2})$""")

    // ------------------------------------------------------------------ erreurs

    /**
     * Message lisible d'une erreur de service.
     *
     * L'**ordre** est la règle, et c'est elle qu'un test fige : un message explicite d'abord,
     * puis les détails, puis le code, et seulement ensuite le repli générique. Un message vide
     * ou fait d'espaces ne compte pas — il est traité comme absent, sans quoi l'écran
     * afficherait un cadre vide là où il y a une erreur à comprendre.
     */
    fun errorText(
        message: String? = null,
        details: String? = null,
        code: String? = null,
    ): String {
        if (!message.isNullOrBlank()) return message
        if (!details.isNullOrBlank()) return details
        if (!code.isNullOrBlank()) return SocialText.serviceError(code)
        return SocialText.GENERIC_ERROR
    }

    // ------------------------------------------------------------------ liens

    /**
     * Identifiant de l'**autre** participant d'un lien.
     *
     * Le repli est le demandeur : c'est ce que fait l'original, et il ne peut tomber juste que
     * si l'appelant passe bien mon identifiant. Un lien dont je ne suis ni demandeur ni
     * destinataire rend donc le demandeur, ce qui est faux — mais un tel lien n'existe pas
     * côté serveur, et inventer un troisième cas ici masquerait la faute au lieu de la laisser
     * apparaître.
     */
    fun otherId(link: FriendLink, me: String): String =
        if (link.requesterId == me) link.recipientId else link.requesterId

    /** Nombre d'amis acceptés. C'est le compte affiché dans « Mes amis (n) ». */
    fun acceptedCount(links: List<FriendLink>): Int =
        links.count { it.status == FriendLinkStatus.ACCEPTED }

    /**
     * Les amis visibles dans la liste, dans l'ordre d'affichage.
     *
     * Quatre filtres s'appliquent dans cet ordre, et chacun a une raison :
     *
     *  - le lien est **accepté** — une invitation en attente n'est pas un ami ;
     *  - le filtre « Demandes » **vide** la liste au lieu de la remplir : dans l'original, la
     *    section des demandes vit sous la liste, et choisir ce filtre la déplie sans mêler les
     *    deux ;
     *  - « En ligne » ne garde que les amis dont la **présence est connue et vraie** — une
     *    entrée absente de [online] n'est pas « hors ligne », c'est « on ne sait pas », et
     *    l'inclure ferait passer un ami injoignable pour connecté ;
     *  - la recherche porte sur le **nom affiché**, sans accent ni casse distingués.
     *
     * Le tri est **décroissant sur la date du dernier message**, et il retombe sur la date du
     * lien quand la conversation est vide : c'est le seul ordre qui fasse remonter une
     * conversation vivante, là où trier sur la date du lien figerait la liste à l'instant où
     * l'amitié a été acceptée.
     *
     * @param all `false` borne la liste à [LIST_LIMIT] entrées.
     */
    fun visibleFriends(
        links: List<FriendLink>,
        me: String,
        filter: Filter = Filter.ALL,
        query: String = "",
        online: Map<String, Boolean> = emptyMap(),
        summaries: Map<String, ConversationSummary> = emptyMap(),
        all: Boolean = false,
    ): List<FriendLink> {
        val needle = query.lowercase()
        val visibles = links
            .filter { it.status == FriendLinkStatus.ACCEPTED }
            .filter { filter != Filter.REQUESTS }
            .filter { filter != Filter.ONLINE || online[otherId(it, me)] == true }
            .filter { (it.other?.displayName ?: "").lowercase().contains(needle) }
            .sortedByDescending { summaries[it.id]?.createdAt ?: it.createdAt }
        return if (all) visibles else visibles.take(LIST_LIMIT)
    }

    /** Invitations **reçues** : en attente, et c'est moi le destinataire. */
    fun receivedInvitations(links: List<FriendLink>, me: String): List<FriendLink> =
        links.filter { it.status == FriendLinkStatus.PENDING && it.recipientId == me }

    /** Invitations **envoyées** : en attente, et c'est moi le demandeur. */
    fun sentInvitations(links: List<FriendLink>, me: String): List<FriendLink> =
        links.filter { it.status == FriendLinkStatus.PENDING && it.requesterId == me }

    /**
     * Comptes que **j'ai** bloqués.
     *
     * Le filtre porte sur `blockedBy` et non sur le statut seul : un compte qui m'a bloqué
     * n'apparaît pas dans ma liste de blocages, et je ne peux pas le débloquer — c'est l'autre
     * qui a la main, et l'écran doit le taire plutôt que de proposer un geste sans effet.
     */
    fun blockedLinks(links: List<FriendLink>, me: String): List<FriendLink> =
        links.filter { it.status == FriendLinkStatus.BLOCKED && it.blockedBy == me }

    /**
     * Les destinataires possibles d'une **récitation partagée** : les amitiés acceptées.
     *
     * La règle n'est pas une commodité d'affichage, c'est celle du **serveur**. La fonction
     * `can_play_shared_recitation` ne donne accès à l'enregistrement et à son fichier que si le
     * lien est accepté et si l'expéditeur comme le destinataire en sont les deux participants.
     * Proposer une amitié en attente, ou un compte bloqué, mènerait donc à un partage que
     * personne ne pourrait écouter — et l'écran aurait promis ce que le serveur refuse.
     *
     * **Sans plafond**, contrairement à [visibleFriends] : l'écran des amis borne sa liste à
     * [LIST_LIMIT] parce qu'on la parcourt, alors qu'un choix de destinataire se fait sur une
     * liste courte et **complète** — borner ferait disparaître un ami sans le dire. L'original
     * filtre sans borner (`links.filter(link => link.status === 'accepted')`).
     */
    fun shareRecipients(links: List<FriendLink>): List<FriendLink> =
        links.filter { it.status == FriendLinkStatus.ACCEPTED }

    // ------------------------------------------------------------------ cercles

    /** Membres ayant accepté. Le compte affiché est « n/5 ». */
    fun acceptedMemberCount(members: List<GroupMember>): Int =
        members.count { it.acceptedAt != null }

    // ------------------------------------------------------------------ messages

    /** Un message est-il de moi ? C'est ce qui décide du côté où il se peint. */
    fun isMine(message: ChatMessage, me: String): Boolean = message.senderId == me

    /**
     * Nom à afficher pour l'auteur d'un message.
     *
     * Trois sources, dans cet ordre : moi, puis le **membre du cercle** — qui porte un profil
     * complet —, puis l'**ami** — dont le lien ne porte qu'un profil réduit —, puis le repli.
     * L'ordre compte dans un cercle : un ami qui n'est pas encore membre n'a pas de profil de
     * membre, et un membre n'est pas forcément dans mes liens.
     */
    fun senderName(
        id: String,
        me: String,
        members: List<GroupMember> = emptyList(),
        links: List<FriendLink> = emptyList(),
    ): String {
        if (id == me) return SocialText.ME
        members.firstOrNull { it.userId == id }?.profile?.displayName?.let { return it }
        links.firstOrNull { otherId(it, me) == id }?.other?.displayName?.let { return it }
        return SocialText.MEMBER
    }

    /**
     * Puis-je supprimer ce message ?
     *
     * Trois refus, et un seul accord : un message **déjà supprimé** ne se supprime plus ; mon
     * propre message m'appartient ; dans un **cercle**, un propriétaire ou un modérateur peut
     * supprimer celui des autres. Un message de conversation privée qui n'est pas de moi ne se
     * supprime donc jamais — on ne touche pas aux propos d'un tiers dans un tête-à-tête, on les
     * signale.
     */
    fun canDeleteMessage(
        message: ChatMessage,
        me: String,
        members: List<GroupMember> = emptyList(),
    ): Boolean {
        if (message.deletedAt != null) return false
        if (message.senderId == me) return true
        if (message.groupId == null) return false
        return members.any {
            it.userId == me && (it.role == GroupRole.OWNER || it.role == GroupRole.MODERATOR)
        }
    }

    /**
     * Le message à signaler quand on appuie sur « Signaler » : le **dernier reçu**.
     *
     * L'original prend le dernier message reçu **non supprimé**, et le signale sans demander
     * lequel. S'il n'y en a aucun, l'écran doit le dire au lieu d'ouvrir un formulaire vide.
     */
    fun reportable(messages: List<ChatMessage>, me: String): ChatMessage? =
        messages.lastOrNull { it.senderId != me && it.deletedAt == null }

    /**
     * Accusé de lecture de **mon** message.
     *
     * « Lu » exige deux choses : que l'autre ait une position de lecture, et qu'elle soit
     * **postérieure** à mon message. La comparaison porte sur les chaînes ISO-8601, comme dans
     * l'original — c'est l'ordre lexicographique qui y est utilisé, et il coïncide avec l'ordre
     * chronologique pour ce format. Une position de lecture absente rend « Envoyé » et jamais
     * « Lu » : annoncer une lecture qui n'a pas eu lieu est la seule faute qui compte ici.
     */
    fun readState(message: ChatMessage, otherReadAt: String?): String =
        if (otherReadAt != null && message.createdAt <= otherReadAt) SocialText.READ else SocialText.SENT

    /** Durée d'un enregistrement joint, en `m:ss`. */
    fun duration(ms: Long): String =
        SocialText.duration(minutes = ms / 60_000, seconds = (ms / 1_000) % 60)

    // ------------------------------------------------------------------ objectif partagé

    /**
     * Nombre de séances proposé, ou `null` si la saisie est refusée.
     *
     * La règle est « un entier de 1 à 14 ». **Écart assumé** : l'original écrit
     * `Number.isInteger(Number(v))`, et la conversion JavaScript accepte `"3.0"`, `" 3 "` et
     * même `"0x3"` comme valant 3. Ces formes ne sont pas des entiers écrits en décimal, et
     * les accepter ferait entrer une valeur que le formulaire n'a jamais voulu laisser passer.
     * Le portage est donc **plus étroit**, et c'est dit ici plutôt que découvert un jour.
     */
    fun targetSessions(raw: String): Int? {
        val value = raw.trim().toIntOrNull() ?: return null
        return if (value in MIN_TARGET_SESSIONS..MAX_TARGET_SESSIONS) value else null
    }

    /** Lundi de la semaine en cours, au format `AAAA-MM-JJ` — la clé d'un objectif partagé. */
    fun goalWeek(at: String = Dates.todayLocal()): String = Dates.weekStart(at)

    // ------------------------------------------------------------------ rendez-vous

    /**
     * Instant d'un rendez-vous saisi, ou `null` s'il est refusé.
     *
     * Trois exigences, dans cet ordre : le format `AAAA-MM-JJ HH:mm` **ancré**, une date qui
     * existe réellement, et un instant **strictement futur**. La deuxième compte : « 2026-02-31 »
     * respecte le format, et un analyseur permissif la corrigerait en silence au 3 mars — le
     * rendez-vous serait alors proposé à une date que personne n'a écrite.
     *
     * **Écart assumé** : la saisie n'est pas rognée, comme dans l'original, dont le motif est
     * ancré des deux côtés — « 2026-03-10 18:00 » avec une espace finale est donc refusé. C'est
     * le comportement du client d'origine, et le rogner ici ferait diverger les deux.
     */
    fun appointment(raw: String, nowIso: String = Dates.nowIso()): String? {
        val match = APPOINTMENT.matchEntire(raw) ?: return null
        val (day, time) = match.destructured
        val local = runCatching { LocalDateTime.parse("${day}T$time:00") }.getOrNull() ?: return null
        val instant = local.atZone(Dates.zone()).toInstant()
        return if (instant.isAfter(Instant.parse(nowIso))) instant.toString() else null
    }

    // ------------------------------------------------------------------ modération

    /**
     * La messagerie est-elle suspendue ?
     *
     * Une suspension **sans terme** ([SocialSuspension.suspendedUntil] nul) est active pour
     * toujours : c'est le cas d'une exclusion, et le lire comme « expirée » rouvrirait la
     * messagerie à quelqu'un qu'on vient d'en écarter.
     */
    fun isSuspended(suspension: SocialSuspension?, nowIso: String = Dates.nowIso()): Boolean {
        if (suspension == null) return false
        val until = suspension.suspendedUntil ?: return true
        return Dates.parseIsoMillis(until) > Dates.parseIsoMillis(nowIso)
    }

    /** Un motif de signalement est valide à partir de [MIN_REPORT_REASON] caractères utiles. */
    fun validReportReason(raw: String): Boolean = raw.trim().length >= MIN_REPORT_REASON

    /** Un nom de cercle est valide à partir de [MIN_GROUP_NAME] caractères utiles. */
    fun validGroupName(raw: String): Boolean = raw.trim().length >= MIN_GROUP_NAME

    // ------------------------------------------------------------------ conversation

    /**
     * Taille d'une page de messages.
     *
     * **Une seule source de vérité.** Ce nombre sert deux fois : la requête le demande au
     * serveur, et la règle « reste-t-il des messages plus anciens ? » le relit pour savoir si la
     * page reçue est **pleine**. Deux constantes — l'une dans la requête, l'autre dans la
     * décision — finiraient par diverger, et la divergence serait muette : une page pleine
     * comparée à un nombre plus grand ferait dire « il n'y a rien avant », ce qui est faux et
     * n'affiche aucune erreur.
     */
    const val MESSAGE_PAGE = 50

    /** Plafond d'un cercle. C'est le dénominateur du compte « n/5 ». */
    const val MAX_GROUP_MEMBERS = 5

    /**
     * État de l'historique d'une conversation.
     *
     * [exhausted] n'est pas redondant avec [hasOlder] : l'original les tient séparément, et pour
     * une raison. Une fois qu'une page plus ancienne s'est révélée **incomplète**, on sait que la
     * conversation commence là, et une page récente pleine ne doit plus rouvrir la porte. Les
     * confondre ferait réapparaître « Charger les messages précédents » sous une conversation
     * dont on a déjà atteint le début.
     */
    data class History(val hasOlder: Boolean = false, val exhausted: Boolean = false)

    /**
     * L'historique après la lecture de la page **la plus récente**.
     *
     * La porte ne s'ouvre que si la page est **pleine** — une page incomplète est la première de
     * la conversation —, et elle ne se **referme jamais** ici : c'est la seule différence avec
     * [afterOlderPage], et elle compte. La page récente redevient pleine dès qu'un message
     * arrive ; si elle pouvait refermer la porte, le bouton clignoterait à chaque
     * rafraîchissement.
     *
     * La comparaison est `>=` là où l'original écrit `=== 50`. La requête demande
     * [MESSAGE_PAGE] lignes, donc les deux se valent — mais si le serveur en rendait une de
     * plus, `>=` dit « il y en a encore », ce qui est vrai, là où `==` dirait le contraire.
     */
    fun afterLatestPage(history: History, size: Int): History =
        if (history.exhausted) {
            history
        } else {
            history.copy(hasOlder = history.hasOlder || size >= MESSAGE_PAGE)
        }

    /**
     * L'historique après la lecture d'une page **plus ancienne**.
     *
     * Ici la porte est **posée**, et non seulement ouverte : une page incomplète prouve qu'on a
     * atteint le début de la conversation. C'est le seul endroit qui puisse refermer le bouton,
     * et c'est pour cela que [History.exhausted] existe à côté de [History.hasOlder].
     */
    fun afterOlderPage(history: History, size: Int): History =
        History(hasOlder = size >= MESSAGE_PAGE, exhausted = size < MESSAGE_PAGE)

    /**
     * Fusionne une page reçue dans les messages déjà affichés.
     *
     * Deux règles, et les deux comptent :
     *
     *  - **par identifiant**, jamais par position. Un message déjà présent est **remplacé** —
     *    c'est ainsi qu'une suppression arrive à l'écran —, et un message nouveau s'ajoute. Une
     *    fusion par position empilerait deux fois le même message à chaque rafraîchissement ;
     *  - **trié par instant croissant**. Les deux pages ne se suivent pas : une page plus
     *    ancienne est lue **après** la plus récente, et doit pourtant se peindre **avant** elle.
     *    Sans ce tri, charger l'historique ferait passer le début de la conversation à la fin.
     *
     * Le tri porte sur les chaînes ISO-8601, comme dans l'original : pour ce format, l'ordre
     * lexicographique **est** l'ordre chronologique. Il est **stable**, donc deux messages au
     * même instant gardent l'ordre où le serveur les a rendus.
     *
     * La carte est ordonnée : une clé déjà présente **garde sa place** et change de valeur,
     * exactement comme `Map.set` en JavaScript. Un tri ultérieur rendrait cet ordre invisible —
     * mais il est l'ordre de départ, et deux messages au même instant en dépendent.
     */
    fun mergeMessages(
        previous: List<ChatMessage>,
        incoming: List<ChatMessage>,
    ): List<ChatMessage> {
        if (incoming.isEmpty()) return previous
        val byId = LinkedHashMap<String, ChatMessage>(previous.size + incoming.size)
        for (message in previous) byId[message.id] = message
        for (message in incoming) byId[message.id] = message
        return byId.values.sortedBy { it.createdAt }
    }

    /**
     * Identifiant du message le plus récent, ou `null` si la conversation est vide.
     *
     * C'est ce que l'écran compare d'un rafraîchissement à l'autre pour décider s'il doit
     * descendre : sans cette comparaison, il redescendrait à chaque relecture, y compris quand
     * rien n'est arrivé — et remonter dans l'historique deviendrait impossible, chaque page
     * relue ramenant de force en bas.
     */
    fun newestId(messages: List<ChatMessage>): String? = messages.lastOrNull()?.id

    /**
     * Ligne d'état sous le nom, dans l'en-tête d'une conversation.
     *
     * L'ordre est celui de l'original : l'activité de l'autre d'abord — c'est ce qui se passe
     * **maintenant**, et cela passe avant tout le reste —, puis la nature du cercle, puis la
     * présence.
     *
     * **Une imprécision portée telle quelle.** Un aperçu **absent** — l'ami ne partage pas sa
     * présence, ou la lecture a échoué — rend « Hors ligne », et non « on ne sait pas ». C'est ce
     * que fait l'original (`overview?.is_online ? … : 'Hors ligne'`), et le corriger ici ferait
     * diverger les deux clients sur la même donnée. Le paramètre est donc **nullable** : c'est
     * l'appelant qui dit « je ne sais pas », et la règle décide quoi en écrire.
     */
    fun statusLine(
        otherTyping: Boolean = false,
        adminContact: Boolean = false,
        isOnline: Boolean? = null,
    ): String = when {
        otherTyping -> SocialText.TYPING
        adminContact -> SocialText.ADMIN_CONTACT_LABEL
        isOnline == true -> SocialText.ONLINE
        else -> SocialText.OFFLINE
    }

    /**
     * Cette pièce est-elle le cercle « contact administrateur » ?
     *
     * Ce cercle n'est pas un cercle comme les autres : le serveur le crée et y réunit tous les
     * administrateurs. Trois choses en découlent — on n'y affiche ni profil ni entraide, on n'y
     * partage pas son étape, et l'on n'y lit ni objectifs ni rendez-vous.
     *
     * **Pourquoi cette règle est ici, et non recopiée.** Elle vivait en privé dans le dépôt, qui
     * en a besoin pour choisir ses lectures ; le rendu en a besoin pour choisir ses blocs. Deux
     * copies de la même règle finiraient par diverger, et la divergence serait muette : un bloc
     * d'entraide dans le cercle de l'administration, ou une lecture refusée à tort.
     *
     * Un cercle **inconnu** n'est pas le cercle de l'administration : l'absence d'entrée dans la
     * liste rend `false`. Refuser le partage à un cercle ordinaire se verrait ; l'accorder à
     * l'administration, non — et c'est cette asymétrie qui fixe le sens du repli.
     *
     * **Le paramètre est un identifiant, et non la pièce ouverte.** La pièce est un type du
     * module de données, que le domaine ne peut pas nommer sans inverser la dépendance entre les
     * deux — `core:data` dépend de `core:domain`, jamais l'inverse. La règle n'a d'ailleurs
     * besoin que du seul champ qui la concerne.
     */
    fun isAdminContact(groups: List<FriendGroup>, groupId: String?): Boolean {
        if (groupId == null) return false
        return groups.any { it.id == groupId && it.contactUserId != null }
    }

    /** Suis-je le **propriétaire** du cercle ? C'est lui seul qui nomme les modérateurs. */
    fun isOwner(members: List<GroupMember>, me: String): Boolean =
        members.any { it.userId == me && it.role == GroupRole.OWNER }

    /**
     * Puis-je **modérer** le cercle ? Propriétaire ou modérateur : les deux ont les mêmes droits
     * d'invitation et d'exclusion.
     *
     * **Ce que cette règle ne vérifie pas.** L'original ne demande pas que le rôle soit
     * **accepté** : un modérateur dont l'invitation est encore en attente obtient donc les mêmes
     * droits. Le serveur, lui, refuse — mais le client afficherait le bouton. C'est porté tel
     * quel, et signalé ici plutôt que corrigé en silence.
     */
    fun isManager(members: List<GroupMember>, me: String): Boolean =
        members.any {
            it.userId == me && (it.role == GroupRole.OWNER || it.role == GroupRole.MODERATOR)
        }

    /**
     * Mon invitation à ce cercle est-elle **en attente** ?
     *
     * Une invitation reçue ne se distingue d'une adhésion que par `acceptedAt` : c'est ce champ
     * qui décide d'afficher « Rejoindre » plutôt que le rôle.
     */
    fun isPendingForMe(member: GroupMember, me: String): Boolean =
        member.acceptedAt == null && member.userId == me

    /** Inviter un ami déjà accepté : réservé au propriétaire et aux modérateurs. */
    fun canInviteToGroup(members: List<GroupMember>, me: String): Boolean = isManager(members, me)

    /**
     * Nommer ou démettre un modérateur : le propriétaire seul, et jamais sur lui-même.
     *
     * La seconde condition n'est pas cosmétique : sans elle, le propriétaire pourrait se démettre
     * et laisser le cercle sans personne pour le gérer.
     */
    fun canToggleModerator(member: GroupMember, members: List<GroupMember>, me: String): Boolean =
        member.userId != me && member.acceptedAt != null && isOwner(members, me)

    /**
     * Retirer un membre du cercle.
     *
     * Un modérateur peut le faire, mais **pas sur le propriétaire** : un modérateur qui pourrait
     * exclure le propriétaire pourrait s'emparer du cercle. Et jamais sur soi-même — on quitte,
     * on ne s'exclut pas.
     */
    fun canRemoveGroupMember(
        member: GroupMember,
        members: List<GroupMember>,
        me: String,
    ): Boolean = member.userId != me && member.role != GroupRole.OWNER && isManager(members, me)

    /** Supprimer le cercle, et avec lui ses messages : le propriétaire seul. */
    fun canDeleteGroup(members: List<GroupMember>, me: String): Boolean = isOwner(members, me)

    /**
     * Le message peut-il partir ?
     *
     * Trois refus : un geste déjà en cours, un texte qui n'est **que des espaces**, et une
     * messagerie suspendue. Le rognage compte : un message fait d'une espace passerait une garde
     * « non vide » et écrirait une ligne vide dans la conversation de l'autre.
     */
    fun canSendMessage(draft: String, busy: Boolean, suspended: Boolean): Boolean =
        !busy && draft.isNotBlank() && !suspended

    /**
     * Le partage d'étape est-il proposé ?
     *
     * Réservé au **tête-à-tête** : dans un cercle, l'étape partirait à plusieurs, et l'original
     * ne l'a jamais permis. Le cercle de l'administration est exclu aussi — on ne partage pas sa
     * progression avec la modération.
     */
    fun canShareProgress(isLink: Boolean, adminContact: Boolean): Boolean =
        isLink && !adminContact

    /** Le bloc « Profil et entraide » est-il proposé ? Jamais dans le cercle de l'administration. */
    fun showsFriendTools(adminContact: Boolean): Boolean = !adminContact

    /**
     * Une proposition à deux peut-elle être **acceptée** ?
     *
     * La même règle sert aux objectifs partagés et aux rendez-vous : l'original écrit deux fois
     * la même expression. Elle tient en deux refus — déjà acceptée, ou proposée par moi-même.
     * Les confondre afficherait un bouton « Accepter » sur sa propre proposition, ce qui
     * n'aurait aucun sens et écraserait l'attente de l'autre.
     */
    fun canAcceptProposal(acceptedAt: String?, proposedBy: String, me: String): Boolean =
        acceptedAt == null && proposedBy != me
}
