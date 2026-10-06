package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.ChatMessage
import com.msoumaya.deepseekandroid.core.model.ConversationSummary
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
}
