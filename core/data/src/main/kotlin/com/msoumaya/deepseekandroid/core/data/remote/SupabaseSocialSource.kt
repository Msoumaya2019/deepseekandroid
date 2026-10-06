package com.msoumaya.deepseekandroid.core.data.remote

import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.SocialText
import com.msoumaya.deepseekandroid.core.model.ConversationSummary
import com.msoumaya.deepseekandroid.core.model.FriendBrief
import com.msoumaya.deepseekandroid.core.model.FriendGroup
import com.msoumaya.deepseekandroid.core.model.FriendLink
import com.msoumaya.deepseekandroid.core.model.FriendLinkStatus
import com.msoumaya.deepseekandroid.core.model.FriendProfile
import com.msoumaya.deepseekandroid.core.model.SocialSuspension
import io.github.jan.supabase.SupabaseClient
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
