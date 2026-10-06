package com.msoumaya.deepseekandroid.core.data.remote

import com.msoumaya.deepseekandroid.core.domain.Dates
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
import com.msoumaya.deepseekandroid.core.model.FriendProfile
import com.msoumaya.deepseekandroid.core.model.GroupMember
import com.msoumaya.deepseekandroid.core.model.GroupRole
import com.msoumaya.deepseekandroid.core.model.RecitationAttachment
import com.msoumaya.deepseekandroid.core.model.ReviewAppointment
import com.msoumaya.deepseekandroid.core.model.SharedGoal
import com.msoumaya.deepseekandroid.core.model.SocialSuspension
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ---------------------------------------------------------------------------
// Implémentation Supabase de l'espace « Amis »
// ---------------------------------------------------------------------------
// Portage de `src/services/social.ts`, requête par requête. C'est la seule partie du dépôt
// social qui touche au réseau, et c'est délibéré : les décisions vivent dans
// `core:domain/Social.kt`, l'ordonnancement dans `SocialRepository`, et il ne reste ici que
// « quelle table, quelles colonnes, quel filtre ».
//
// **Les requêtes sont recopiées telles quelles, colonnes comprises.** Là où l'original écrit
// `select('*')`, ce fichier ne demande pas de colonnes — ce qui fait qu'une colonne ajoutée par
// une migration ultérieure n'a rien à changer ici. La tolérance vient du sérialiseur, configuré
// avec `AppJson` (`ignoreUnknownKeys`), et non de la requête.
//
// **Les noms de colonnes sont écrits à la main, en `snake_case`.** Le client Postgrest est
// configuré sans conversion de propriété : `eq("user_id", …)` interroge la colonne `user_id`,
// et les `@SerialName` des lignes ci-dessous font la correspondance inverse. Une conversion
// automatique ferait correspondre `shareOnline` à `share_online` sans que rien ne le dise, et
// la première colonne irrégulière casserait en silence.
// ---------------------------------------------------------------------------

/** Tables et fonctions du projet partagé, nommées une fois. */
private const val TABLE_PROFILES = "friend_profiles"
private const val TABLE_LINKS = "friend_links"
private const val TABLE_GROUPS = "friend_groups"
private const val TABLE_SUSPENSIONS = "social_suspensions"
private const val TABLE_MESSAGES = "friend_messages"
private const val TABLE_HIDDEN = "friend_message_hidden"
private const val TABLE_READS = "friend_message_reads"
private const val TABLE_RECITATIONS = "recitations"
private const val TABLE_MEMBERS = "friend_group_members"
private const val TABLE_SHARED_GOALS = "friend_shared_goals"
private const val TABLE_APPOINTMENTS = "friend_review_appointments"

private const val RPC_ENSURE_PROFILE = "ensure_social_profile"
private const val RPC_INBOX = "friend_inbox"
private const val RPC_REQUEST_FRIEND = "request_friend"
private const val RPC_ACCEPT_FRIEND = "accept_friend"
private const val RPC_DECLINE_FRIEND = "decline_friend"
private const val RPC_REMOVE_FRIEND = "remove_friend"
private const val RPC_BLOCK_FRIEND = "block_friend"
private const val RPC_UNBLOCK_FRIEND = "unblock_friend"
private const val RPC_CREATE_GROUP = "create_friend_group"
private const val RPC_OPEN_ADMIN_CONTACT = "open_admin_contact"
private const val RPC_OVERVIEW = "friend_overview"
private const val RPC_DELETE_MESSAGE = "delete_friend_message"
private const val RPC_REPORT_MESSAGE = "report_friend_message"
private const val RPC_INVITE_MEMBER = "invite_group_member"
private const val RPC_ACCEPT_GROUP = "accept_group_invite"
private const val RPC_DECLINE_GROUP = "decline_group_invite"
private const val RPC_SET_MODERATOR = "set_group_moderator"
private const val RPC_REMOVE_MEMBER = "remove_group_member"
private const val RPC_DELETE_GROUP = "delete_friend_group"
private const val RPC_ACCEPT_GOAL = "accept_shared_goal"
private const val RPC_ACCEPT_APPOINTMENT = "accept_review_appointment"
private const val RPC_CANCEL_APPOINTMENT = "cancel_review_appointment"

/**
 * Taille d'une page de messages, **prise au domaine**.
 *
 * Elle sert deux fois — la requête la demande au serveur, et la règle « reste-t-il des messages
 * plus anciens ? » la relit pour savoir si la page reçue est pleine —, donc elle a **une seule**
 * source : [Social.MESSAGE_PAGE]. La recopier ici ferait diverger la requête et la décision, et
 * la divergence serait muette : soit un bouton qui disparaît, soit un chargement qui ne rend
 * rien.
 *
 * Les bornes des listes secondaires, elles, restent ci-dessous : ce sont des choix de lecture —
 * combien d'objectifs, combien de rendez-vous —, et aucune règle n'en dépend.
 */
private val MESSAGE_PAGE = Social.MESSAGE_PAGE.toLong()

/** Bornes des listes secondaires de la conversation, reprises de l'original. */
private const val GOAL_PAGE = 8L
private const val APPOINTMENT_PAGE = 20L

/**
 * Implémentation réelle, adossée au projet Supabase partagé avec le client React Native.
 *
 * @param client client déjà construit. Il n'est **jamais** construit ici : hors configuration,
 *   le conteneur ne crée pas de source du tout, et aucune de ces requêtes ne peut donc partir
 *   sans projet — plutôt qu'échouer une par une sur une adresse vide.
 * @param nowIso horloge injectable, pour la seule valeur que ce fichier fabrique lui-même : le
 *   repli de date d'un aperçu de conversation que le serveur rend sans date.
 */
class SupabaseSocialSource(
    private val client: SupabaseClient,
    private val nowIso: () -> String = { Dates.nowIso() },
) : SocialSource {

    override suspend fun ensureProfile(): FriendProfile =
        client.postgrest.rpc(RPC_ENSURE_PROFILE).decodeSingle<ProfileRow>().toModel()

    /**
     * Les liens, puis les profils des autres en **une seconde requête**.
     *
     * Le découpage en deux n'est pas une optimisation : PostgREST ne sait pas joindre la
     * relation « l'autre » d'un lien, qui dépend de quel côté du lien se trouve l'appelant.
     * La seconde requête est donc filtrée sur les identifiants rassemblés, et un lien dont le
     * profil manque garde `other = null` — la liste affiche alors le repli « Ami » plutôt que
     * de faire disparaître la conversation.
     */
    override suspend fun links(userId: String): List<FriendLink> {
        val rows = client.postgrest.from(TABLE_LINKS)
            .select(
                columns = Columns.list(
                    "id",
                    "requester_id",
                    "recipient_id",
                    "status",
                    "blocked_by",
                    "created_at",
                ),
            ) {
                order("created_at", Order.DESCENDING)
            }
            .decodeList<LinkRow>()

        val others = rows.map { if (it.requesterId == userId) it.recipientId else it.requesterId }
        if (others.isEmpty()) return rows.map { it.toModel(null) }

        val profiles = client.postgrest.from(TABLE_PROFILES)
            .select(columns = Columns.list("id", "display_name", "avatar_path", "share_online")) {
                filter { isIn("id", others) }
            }
            .decodeList<BriefRow>()
            .associateBy { it.id }

        return rows.map { row ->
            val other = if (row.requesterId == userId) row.recipientId else row.requesterId
            row.toModel(profiles[other]?.toModel())
        }
    }

    override suspend fun groups(): List<FriendGroup> =
        client.postgrest.from(TABLE_GROUPS)
            .select { order("created_at", Order.DESCENDING) }
            .decodeList<GroupRow>()
            .map { it.toModel() }

    override suspend fun suspension(userId: String): SocialSuspension? =
        client.postgrest.from(TABLE_SUSPENSIONS)
            .select(columns = Columns.list("user_id", "reason", "suspended_until", "created_at")) {
                filter { eq("user_id", userId) }
            }
            .decodeSingleOrNull<SuspensionRow>()
            ?.toModel()

    /**
     * L'aperçu des conversations, en **un seul** appel de fonction.
     *
     * La fonction serveur rend, par lien accepté, le dernier message, son instant, le nombre de
     * non-lus, l'autre identifiant et sa présence. Le client d'origine faisait auparavant un
     * comptage et un appel de présence **par ami** ; `friend-inbox.sql` a remplacé ces N appels
     * par un seul, et c'est cette version-là qui est portée.
     *
     * **Ce qui décide de la présence d'un résumé est recopié à l'identique** : une ligne entre
     * dans [SocialInbox.summaries] si elle porte une date **ou** des non-lus. Une ligne sans
     * date et sans non-lu est un lien accepté dont la conversation n'a jamais commencé — la
     * liste doit alors dire « Commencer une discussion », ce qu'un résumé vide empêcherait.
     *
     * **Le repli de date n'est pas décoratif** : une conversation dont le serveur rend des
     * non-lus sans date — un message arrivé entre deux lectures — doit remonter en tête de
     * liste, et c'est la date qui trie. Sans elle, elle tomberait en queue, c'est-à-dire à
     * l'endroit exact où on ne la verrait pas.
     */
    override suspend fun inbox(links: List<FriendLink>): SocialInbox {
        val rows = client.postgrest.rpc(RPC_INBOX).decodeList<InboxRow>()
        val summaries = mutableMapOf<String, ConversationSummary>()
        val statuses = mutableMapOf<String, Boolean>()

        for (row in rows) {
            statuses[row.otherId] = row.isOnline
            if (row.createdAt != null || row.unreadCount > 0) {
                summaries[row.linkId] = ConversationSummary(
                    body = row.body ?: SocialText.NEW_MESSAGE,
                    createdAt = row.createdAt ?: nowIso(),
                    unread = row.unreadCount,
                )
            }
        }
        return SocialInbox(summaries = summaries, statuses = statuses)
    }

    override suspend fun requestFriend(code: String) {
        client.postgrest.rpc(RPC_REQUEST_FRIEND, buildJsonObject { put("p_code", code) })
    }

    override suspend fun acceptFriend(linkId: String) {
        client.postgrest.rpc(RPC_ACCEPT_FRIEND, buildJsonObject { put("p_link", linkId) })
    }

    override suspend fun declineFriend(linkId: String) {
        client.postgrest.rpc(RPC_DECLINE_FRIEND, buildJsonObject { put("p_link", linkId) })
    }

    override suspend fun removeFriend(linkId: String) {
        client.postgrest.rpc(RPC_REMOVE_FRIEND, buildJsonObject { put("p_link", linkId) })
    }

    override suspend fun blockFriend(otherId: String) {
        client.postgrest.rpc(RPC_BLOCK_FRIEND, buildJsonObject { put("p_other", otherId) })
    }

    override suspend fun unblockFriend(otherId: String) {
        client.postgrest.rpc(RPC_UNBLOCK_FRIEND, buildJsonObject { put("p_other", otherId) })
    }

    override suspend fun createGroup(name: String): String =
        client.postgrest.rpc(RPC_CREATE_GROUP, buildJsonObject { put("p_name", name) })
            .decodeAs<String>()

    /**
     * Ouvre le cercle « contact administrateur » et rend son identifiant.
     *
     * **Forme de la réponse, et pourquoi elle est lue ainsi.** La fonction déclare
     * `returns uuid` : PostgREST rend donc la valeur **seule**, et non un tableau d'une ligne.
     * C'est ce que dit le client d'origine, qui l'écrit `as string` là où il écrit
     * `as FriendOverview[]` pour une fonction qui rend une table. `decodeAs` lit le corps
     * entier ; `decodeSingle` attendrait un tableau et échouerait.
     */
    override suspend fun openAdminContact(): String =
        client.postgrest.rpc(RPC_OPEN_ADMIN_CONTACT).decodeAs<String>()

    // ------------------------------------------------------------------ conversation

    /**
     * L'aperçu d'un ami, en **un** appel de fonction.
     *
     * La fonction déclare `returns setof friend_overview`, donc la réponse est un **tableau** :
     * le client d'origine écrit `as FriendOverview[]` puis prend la première ligne, et **lève**
     * quand il n'y en a aucune. Ici l'absence rend `null`, parce que « progression privée » est
     * un état normal et non une panne — voir `SocialSource.overview`.
     */
    override suspend fun overview(otherId: String): FriendOverview? =
        client.postgrest.rpc(RPC_OVERVIEW, buildJsonObject { put("p_other", otherId) })
            .decodeList<OverviewRow>()
            .firstOrNull()
            ?.toModel()

    /**
     * Une page de messages, du plus ancien au plus récent.
     *
     * **Trois requêtes au plus, dans cet ordre** : les messages, puis les masquages, puis les
     * récitations jointes. Les masquages sont lus **avant** la jointure parce qu'ils décident de
     * ce qui reste : joindre la récitation d'un message que je viens de masquer serait un appel
     * pour rien.
     *
     * **Le renversement n'est pas cosmétique.** La requête demande les **derniers** messages
     * (`order desc` borné à une page), et l'écran les veut dans l'ordre du temps. Sans le
     * `.reversed()`, la première page afficherait les messages les plus **anciens** de la
     * conversation — c'est-à-dire l'inverse de ce qu'on vient lire.
     */
    override suspend fun messages(room: ChatRoom, before: String?): List<ChatMessage> {
        val linkId = room.linkId
        val groupId = room.groupId
        val rows = client.postgrest.from(TABLE_MESSAGES)
            .select {
                order("created_at", Order.DESCENDING)
                limit(MESSAGE_PAGE)
                filter {
                    if (linkId != null) eq("link_id", linkId) else eq("group_id", groupId!!)
                    if (before != null) lt("created_at", before)
                }
            }
            .decodeList<MessageRow>()
            .reversed()

        if (rows.isEmpty()) return emptyList()

        val hidden = client.postgrest.from(TABLE_HIDDEN)
            .select(columns = Columns.list("message_id")) {
                filter { isIn("message_id", rows.map { it.id }) }
            }
            .decodeList<HiddenRow>()
            .mapTo(mutableSetOf()) { it.messageId }

        val visible = rows.filterNot { it.id in hidden }
        val recitationIds = visible
            .filter { it.kind == ChatMessageKind.RECITATION && it.recitationId != null }
            .mapNotNull { it.recitationId }
        if (recitationIds.isEmpty()) return visible.map { it.toModel(null) }

        val attachments = client.postgrest.from(TABLE_RECITATIONS)
            .select(
                columns = Columns.list(
                    "id",
                    "start_verse_id",
                    "end_verse_id",
                    "duration_ms",
                    "storage_path",
                ),
            ) {
                filter { isIn("id", recitationIds) }
            }
            .decodeList<RecitationRow>()
            .associateBy { it.id }

        return visible.map { it.toModel(attachments[it.recitationId]) }
    }

    /**
     * Écrit un message.
     *
     * **L'insertion est directe, et non une fonction serveur** — comme dans l'original : la
     * table porte une politique RLS qui n'accepte une ligne que si `sender_id` est le compte du
     * jeton. Le corps est **rogné** avant l'envoi, et la règle n'est pas décorative : un message
     * fait d'espaces s'afficherait comme une bulle vide, sans que rien ne dise pourquoi.
     */
    override suspend fun sendMessage(room: ChatRoom, body: String, kind: ChatMessageKind) {
        client.postgrest.from(TABLE_MESSAGES).insert(
            MessageInsert(
                linkId = room.linkId,
                groupId = room.groupId,
                senderId = requireUserId(),
                body = body.trim(),
                kind = kind,
            ),
        )
    }

    override suspend fun deleteMessage(messageId: String) {
        client.postgrest.rpc(RPC_DELETE_MESSAGE, buildJsonObject { put("p_message", messageId) })
    }

    override suspend fun reportMessage(messageId: String, reason: String) {
        client.postgrest.rpc(
            RPC_REPORT_MESSAGE,
            buildJsonObject {
                put("p_message", messageId)
                put("p_reason", reason)
            },
        )
    }

    override suspend fun markConversationRead(linkId: String) {
        val userId = currentUserId() ?: return
        client.postgrest.from(TABLE_READS).upsert(ReadUpsert(linkId, userId, nowIso()))
    }

    override suspend fun otherReadAt(linkId: String, otherId: String): String? =
        client.postgrest.from(TABLE_READS)
            .select(columns = Columns.list("last_read_at")) {
                filter {
                    eq("link_id", linkId)
                    eq("user_id", otherId)
                }
            }
            .decodeSingleOrNull<ReadRow>()
            ?.lastReadAt

    override suspend fun hideMessageForMe(messageId: String) {
        val userId = currentUserId() ?: return
        client.postgrest.from(TABLE_HIDDEN).upsert(HiddenUpsert(messageId, userId))
    }

    /**
     * Les membres d'un cercle, puis leurs profils en **une seconde requête**.
     *
     * Même raison que pour les liens : PostgREST ne joint pas `friend_profiles` sur une clé
     * étrangère sans que la relation soit déclarée dans le schéma. Un membre dont le profil
     * manque garde `profile = null`, et l'écran affiche « Membre » plutôt que de le faire
     * disparaître — le retirer de la liste ferait croire à un cercle plus petit qu'il n'est.
     */
    override suspend fun members(groupId: String): List<GroupMember> {
        val rows = client.postgrest.from(TABLE_MEMBERS)
            .select { filter { eq("group_id", groupId) } }
            .decodeList<MemberRow>()
        if (rows.isEmpty()) return emptyList()

        val profiles = client.postgrest.from(TABLE_PROFILES)
            .select { filter { isIn("id", rows.map { it.userId }) } }
            .decodeList<ProfileRow>()
            .associateBy { it.id }

        return rows.map { it.toModel(profiles[it.userId]?.toModel()) }
    }

    override suspend fun inviteGroupMember(groupId: String, friendId: String) {
        client.postgrest.rpc(
            RPC_INVITE_MEMBER,
            buildJsonObject {
                put("p_group", groupId)
                put("p_friend", friendId)
            },
        )
    }

    override suspend fun acceptGroupInvite(groupId: String) {
        client.postgrest.rpc(RPC_ACCEPT_GROUP, buildJsonObject { put("p_group", groupId) })
    }

    override suspend fun declineGroupInvite(groupId: String) {
        client.postgrest.rpc(RPC_DECLINE_GROUP, buildJsonObject { put("p_group", groupId) })
    }

    override suspend fun setGroupModerator(groupId: String, memberId: String, enabled: Boolean) {
        client.postgrest.rpc(
            RPC_SET_MODERATOR,
            buildJsonObject {
                put("p_group", groupId)
                put("p_member", memberId)
                put("p_enabled", enabled)
            },
        )
    }

    override suspend fun removeGroupMember(groupId: String, memberId: String) {
        client.postgrest.rpc(
            RPC_REMOVE_MEMBER,
            buildJsonObject {
                put("p_group", groupId)
                put("p_member", memberId)
            },
        )
    }

    override suspend fun deleteGroup(groupId: String) {
        client.postgrest.rpc(RPC_DELETE_GROUP, buildJsonObject { put("p_group", groupId) })
    }

    override suspend fun sharedGoals(linkId: String): List<SharedGoal> =
        client.postgrest.from(TABLE_SHARED_GOALS)
            .select {
                filter { eq("link_id", linkId) }
                order("week_start", Order.DESCENDING)
                limit(GOAL_PAGE)
            }
            .decodeList<GoalRow>()
            .map { it.toModel() }

    override suspend fun proposeSharedGoal(linkId: String, weekStart: String, targetSessions: Int) {
        client.postgrest.from(TABLE_SHARED_GOALS).insert(
            GoalInsert(
                linkId = linkId,
                weekStart = weekStart,
                targetSessions = targetSessions,
                proposedBy = requireUserId(),
            ),
        )
    }

    override suspend fun acceptSharedGoal(goalId: String) {
        client.postgrest.rpc(RPC_ACCEPT_GOAL, buildJsonObject { put("p_goal", goalId) })
    }

    /**
     * Les rendez-vous **à venir** d'une conversation.
     *
     * Le filtre `gte starts_at now()` est celui de l'original, et il compte : sans lui, un
     * rendez-vous passé resterait proposé à l'acceptation, et l'écran inviterait à confirmer
     * une rencontre qui a déjà eu lieu.
     */
    override suspend fun appointments(linkId: String): List<ReviewAppointment> =
        client.postgrest.from(TABLE_APPOINTMENTS)
            .select {
                filter {
                    eq("link_id", linkId)
                    gte("starts_at", nowIso())
                }
                order("starts_at", Order.ASCENDING)
                limit(APPOINTMENT_PAGE)
            }
            .decodeList<AppointmentRow>()
            .map { it.toModel() }

    override suspend fun proposeAppointment(linkId: String, startsAt: String) {
        client.postgrest.from(TABLE_APPOINTMENTS).insert(
            AppointmentInsert(
                linkId = linkId,
                startsAt = startsAt,
                proposedBy = requireUserId(),
            ),
        )
    }

    override suspend fun acceptAppointment(appointmentId: String) {
        client.postgrest.rpc(
            RPC_ACCEPT_APPOINTMENT,
            buildJsonObject { put("p_appointment", appointmentId) },
        )
    }

    override suspend fun cancelAppointment(appointmentId: String) {
        client.postgrest.rpc(
            RPC_CANCEL_APPOINTMENT,
            buildJsonObject { put("p_appointment", appointmentId) },
        )
    }

    /**
     * Le compte courant, ou `null` hors session.
     *
     * L'identifiant n'est **pas** un paramètre des gestes qui l'emploient : la politique RLS de
     * la table le compare à `auth.uid()`, et le lire ici plutôt que de le recevoir garantit
     * qu'on écrit toujours pour le compte réellement connecté. Un identifiant venu de l'appelant
     * pourrait désigner quelqu'un d'autre, et le serveur refuserait alors une écriture qu'on
     * aurait crue valide.
     */
    private suspend fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    /** Le compte courant, ou une erreur lisible. Pour les gestes qui n'ont pas de sens sans lui. */
    private suspend fun requireUserId(): String =
        currentUserId() ?: throw IllegalStateException(SocialText.CONNECTION_NEEDED)
}

// ---------------------------------------------------------------------------
// Lignes
// ---------------------------------------------------------------------------
// Une ligne par table lue, avec la correspondance `snake_case` → `camelCase` écrite
// explicitement. Les champs facultatifs portent leur valeur par défaut : une colonne absente
// — un projet où `friend-avatars.sql` ou `admin-contact.sql` n'a pas encore été appliqué —
// donne `null`, jamais une erreur de décodage.
// ---------------------------------------------------------------------------

@Serializable
private data class ProfileRow(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("invite_code") val inviteCode: String,
    @SerialName("share_online") val shareOnline: Boolean = false,
    @SerialName("share_location") val shareLocation: Boolean = false,
    @SerialName("share_progress") val shareProgress: Boolean = false,
    @SerialName("avatar_path") val avatarPath: String? = null,
) {
    fun toModel() = FriendProfile(
        id = id,
        displayName = displayName,
        inviteCode = inviteCode,
        shareOnline = shareOnline,
        shareLocation = shareLocation,
        shareProgress = shareProgress,
        avatarPath = avatarPath,
    )
}

/** Profil réduit, tel que la seconde requête de [SupabaseSocialSource.links] le rend. */
@Serializable
private data class BriefRow(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_path") val avatarPath: String? = null,
    @SerialName("share_online") val shareOnline: Boolean = false,
) {
    fun toModel() = FriendBrief(
        id = id,
        displayName = displayName,
        avatarPath = avatarPath,
        shareOnline = shareOnline,
    )
}

@Serializable
private data class LinkRow(
    val id: String,
    @SerialName("requester_id") val requesterId: String,
    @SerialName("recipient_id") val recipientId: String,
    val status: FriendLinkStatus,
    @SerialName("created_at") val createdAt: String,
    @SerialName("blocked_by") val blockedBy: String? = null,
) {
    fun toModel(other: FriendBrief?) = FriendLink(
        id = id,
        requesterId = requesterId,
        recipientId = recipientId,
        status = status,
        createdAt = createdAt,
        blockedBy = blockedBy,
        other = other,
    )
}

@Serializable
private data class GroupRow(
    val id: String,
    val name: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("contact_user_id") val contactUserId: String? = null,
) {
    fun toModel() = FriendGroup(
        id = id,
        name = name,
        ownerId = ownerId,
        createdAt = createdAt,
        contactUserId = contactUserId,
    )
}

@Serializable
private data class SuspensionRow(
    @SerialName("user_id") val userId: String,
    val reason: String,
    @SerialName("suspended_until") val suspendedUntil: String? = null,
    @SerialName("created_at") val createdAt: String,
) {
    fun toModel() = SocialSuspension(
        userId = userId,
        reason = reason,
        createdAt = createdAt,
        suspendedUntil = suspendedUntil,
    )
}

/**
 * Une ligne de `friend_inbox()`.
 *
 * Les quatre champs nullables le sont parce que la fonction les rend nullables : `body` et
 * `created_at` sont nuls quand le lien n'a **aucun** message, et `body` porte déjà
 * « Message supprimé » quand le dernier a été supprimé. Le repli de ce fichier n'est donc
 * atteignable que par un serveur plus ancien, et il est conservé pour ne pas inventer un
 * troisième comportement.
 */
@Serializable
private data class InboxRow(
    @SerialName("link_id") val linkId: String,
    val body: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("unread_count") val unreadCount: Int = 0,
    @SerialName("other_id") val otherId: String,
    @SerialName("is_online") val isOnline: Boolean = false,
)

/**
 * Une ligne de `friend_overview()`.
 *
 * Les champs facultatifs portent leur défaut parce qu'ils sont **conditionnels au consentement**
 * de l'autre : `goal_label` et les compteurs ne sortent que s'il partage sa progression. Un
 * défaut à zéro n'est pas une mesure, c'est une absence — et c'est à l'écran de les distinguer,
 * ce qu'il fait en regardant `goal_label`.
 */
@Serializable
private data class OverviewRow(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("goal_label") val goalLabel: String? = null,
    @SerialName("weekly_verses") val weeklyVerses: Int = 0,
    @SerialName("weekly_sessions") val weeklySessions: Int = 0,
    @SerialName("goal_percent") val goalPercent: Int = 0,
    @SerialName("quran_percent") val quranPercent: Int = 0,
    @SerialName("current_start") val currentStart: Int? = null,
    @SerialName("current_end") val currentEnd: Int? = null,
    @SerialName("is_online") val isOnline: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    fun toModel() = FriendOverview(
        id = id,
        displayName = displayName,
        goalLabel = goalLabel,
        weeklyVerses = weeklyVerses,
        weeklySessions = weeklySessions,
        goalPercent = goalPercent,
        quranPercent = quranPercent,
        currentStart = currentStart,
        currentEnd = currentEnd,
        isOnline = isOnline,
        updatedAt = updatedAt,
    )
}

@Serializable
private data class MessageRow(
    val id: String,
    @SerialName("link_id") val linkId: String? = null,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("sender_id") val senderId: String,
    val kind: ChatMessageKind,
    val body: String,
    @SerialName("recitation_id") val recitationId: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    fun toModel(attachment: RecitationRow?) = ChatMessage(
        id = id,
        senderId = senderId,
        kind = kind,
        body = body,
        createdAt = createdAt,
        linkId = linkId,
        groupId = groupId,
        recitationId = recitationId,
        recitation = attachment?.toModel(),
        deletedAt = deletedAt,
    )
}

@Serializable
private data class HiddenRow(
    @SerialName("message_id") val messageId: String,
)

@Serializable
private data class ReadRow(
    @SerialName("last_read_at") val lastReadAt: String,
)

@Serializable
private data class RecitationRow(
    val id: String,
    @SerialName("start_verse_id") val startVerseId: Int,
    @SerialName("end_verse_id") val endVerseId: Int,
    @SerialName("duration_ms") val durationMs: Long,
    @SerialName("storage_path") val storagePath: String,
) {
    fun toModel() = RecitationAttachment(
        id = id,
        startVerseId = startVerseId,
        endVerseId = endVerseId,
        durationMs = durationMs,
        storagePath = storagePath,
    )
}

@Serializable
private data class MemberRow(
    @SerialName("group_id") val groupId: String,
    @SerialName("user_id") val userId: String,
    val role: GroupRole,
    @SerialName("accepted_at") val acceptedAt: String? = null,
    @SerialName("invited_by") val invitedBy: String? = null,
) {
    fun toModel(profile: FriendProfile?) = GroupMember(
        groupId = groupId,
        userId = userId,
        role = role,
        acceptedAt = acceptedAt,
        invitedBy = invitedBy,
        profile = profile,
    )
}

@Serializable
private data class GoalRow(
    val id: String,
    @SerialName("link_id") val linkId: String,
    @SerialName("week_start") val weekStart: String,
    @SerialName("target_sessions") val targetSessions: Int,
    @SerialName("proposed_by") val proposedBy: String,
    @SerialName("accepted_at") val acceptedAt: String? = null,
) {
    fun toModel() = SharedGoal(
        id = id,
        linkId = linkId,
        weekStart = weekStart,
        targetSessions = targetSessions,
        proposedBy = proposedBy,
        acceptedAt = acceptedAt,
    )
}

@Serializable
private data class AppointmentRow(
    val id: String,
    @SerialName("link_id") val linkId: String,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("proposed_by") val proposedBy: String,
    @SerialName("accepted_at") val acceptedAt: String? = null,
) {
    fun toModel() = ReviewAppointment(
        id = id,
        linkId = linkId,
        startsAt = startsAt,
        proposedBy = proposedBy,
        acceptedAt = acceptedAt,
    )
}

// ---------------------------------------------------------------------------
// Lignes écrites
// ---------------------------------------------------------------------------
// Trois insertions directes et deux `upsert`, pour les seules tables que le client écrit sans
// passer par une fonction serveur. `AppJson` porte `explicitNulls = false` : un champ nul est
// **omis** du corps envoyé plutôt qu'envoyé à `null`. C'est ce qu'il faut ici — un message de
// cercle ne porte pas de `link_id`, et lui en donner un, même nul, serait écrire une colonne
// que la politique RLS lit.
// ---------------------------------------------------------------------------

@Serializable
private data class MessageInsert(
    @SerialName("link_id") val linkId: String? = null,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("sender_id") val senderId: String,
    val body: String,
    val kind: ChatMessageKind,
)

@Serializable
private data class HiddenUpsert(
    @SerialName("message_id") val messageId: String,
    @SerialName("user_id") val userId: String,
)

@Serializable
private data class ReadUpsert(
    @SerialName("link_id") val linkId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("last_read_at") val lastReadAt: String,
)

@Serializable
private data class GoalInsert(
    @SerialName("link_id") val linkId: String,
    @SerialName("week_start") val weekStart: String,
    @SerialName("target_sessions") val targetSessions: Int,
    @SerialName("proposed_by") val proposedBy: String,
)

@Serializable
private data class AppointmentInsert(
    @SerialName("link_id") val linkId: String,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("proposed_by") val proposedBy: String,
)
