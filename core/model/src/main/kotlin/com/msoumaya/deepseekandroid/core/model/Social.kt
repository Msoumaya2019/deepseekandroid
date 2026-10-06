package com.msoumaya.deepseekandroid.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modèle social — amis, cercles, messages.
 *
 * Porté depuis les types de `src/services/social.ts`. Ces types décrivent des **lignes de
 * tables Supabase** et des résultats de fonctions RPC, partagées avec le client React Native :
 * les noms de champs y sont donc ceux des colonnes, et les valeurs d'énumération sont les
 * **littéraux** que l'original écrit.
 *
 * **Ce que ce fichier ne fait pas.** Il ne contient aucune règle : le filtrage, le tri et les
 * décisions vivent dans `core:domain/Social.kt`, où ils s'éprouvent sans réseau. Ici il n'y a
 * que la forme des données.
 *
 * **Sur `@SerialName`.** Il n'est posé que sur les **valeurs d'énumération**, comme dans
 * `Enums.kt` : ce sont elles que l'original écrit dans ses colonnes, et une valeur renommée
 * ferait tomber la lecture d'une ligne existante. Les noms de **champs** restent en
 * camelCase, et c'est la couche de données qui fait la correspondance avec les colonnes
 * (`display_name`, `invite_code`, …) au moment où elle lit — un `@SerialName` par champ
 * dupliquerait cette correspondance en deux endroits qui ne peuvent pas se vérifier l'un
 * l'autre.
 */

/** État d'un lien d'amitié. */
@Serializable
enum class FriendLinkStatus {
    @SerialName("pending")
    PENDING,

    @SerialName("accepted")
    ACCEPTED,

    @SerialName("blocked")
    BLOCKED,
}

/** Rôle d'un membre dans un cercle. */
@Serializable
enum class GroupRole {
    @SerialName("owner")
    OWNER,

    @SerialName("moderator")
    MODERATOR,

    @SerialName("member")
    MEMBER,
}

/** Nature d'un message. */
@Serializable
enum class ChatMessageKind {
    @SerialName("text")
    TEXT,

    @SerialName("encouragement")
    ENCOURAGEMENT,

    @SerialName("progress")
    PROGRESS,

    @SerialName("recitation")
    RECITATION,
}

/** État d'un signalement, du point de vue de la modération. */
@Serializable
enum class ReportStatus {
    @SerialName("open")
    OPEN,

    @SerialName("reviewed")
    REVIEWED,
}

/**
 * Profil social.
 *
 * `shareOnline`, `shareLocation` et `shareProgress` sont les **trois** réglages de partage :
 * ils décident de ce qu'un ami voit dans [FriendOverview]. Un profil qui ne les porte pas est
 * traité comme « ne partage rien », jamais comme « partage tout ».
 */
@Serializable
data class FriendProfile(
    val id: String,
    val displayName: String,
    val inviteCode: String,
    val shareOnline: Boolean = false,
    val shareLocation: Boolean = false,
    val shareProgress: Boolean = false,
    val avatarPath: String? = null,
)

/**
 * Profil réduit joint à un lien d'amitié.
 *
 * C'est le `Pick<FriendProfile, …>` de l'original : quatre champs et pas un de plus. Le
 * modèle le déclare à part au lieu d'un [FriendProfile] complet, parce qu'un lien lu depuis
 * la table ne porte **que** ces quatre-là — un type complet laisserait croire que
 * `shareProgress` est disponible alors qu'il vaut toujours `false` par défaut, donc
 * « ne partage rien », ce qui est faux.
 */
@Serializable
data class FriendBrief(
    val id: String,
    val displayName: String,
    val avatarPath: String? = null,
    val shareOnline: Boolean = false,
)

/** Lien d'amitié entre deux comptes. */
@Serializable
data class FriendLink(
    val id: String,
    val requesterId: String,
    val recipientId: String,
    val status: FriendLinkStatus,
    val createdAt: String,
    val blockedBy: String? = null,
    val other: FriendBrief? = null,
)

/**
 * Ce qu'un ami accepte de montrer.
 *
 * `isOnline` n'est renseigné que si l'ami partage sa présence ; `goalLabel` et les deux
 * compteurs ne le sont que s'il partage sa progression. Un champ absent n'est pas un zéro :
 * c'est une **absence de consentement**, et l'écran doit la distinguer.
 */
@Serializable
data class FriendOverview(
    val id: String,
    val displayName: String,
    val goalLabel: String? = null,
    val weeklyVerses: Int = 0,
    val weeklySessions: Int = 0,
    val goalPercent: Int = 0,
    val quranPercent: Int = 0,
    val currentStart: Int? = null,
    val currentEnd: Int? = null,
    val isOnline: Boolean = false,
    val updatedAt: String? = null,
)

/** Cercle privé. `contactUserId` non nul désigne le cercle « contact administrateur ». */
@Serializable
data class FriendGroup(
    val id: String,
    val name: String,
    val ownerId: String,
    val createdAt: String,
    val contactUserId: String? = null,
)

/** Membre d'un cercle. `acceptedAt` nul veut dire « invitation en attente ». */
@Serializable
data class GroupMember(
    val groupId: String,
    val userId: String,
    val role: GroupRole,
    val acceptedAt: String? = null,
    val invitedBy: String? = null,
    val profile: FriendProfile? = null,
)

/** Enregistrement vocal joint à un message. */
@Serializable
data class RecitationAttachment(
    val id: String,
    val startVerseId: Int,
    val endVerseId: Int,
    val durationMs: Long,
    val storagePath: String,
)

/**
 * Message d'une conversation.
 *
 * Un message appartient soit à un lien (`linkId`), soit à un cercle (`groupId`) : jamais aux
 * deux, jamais à aucun. `deletedAt` non nul marque une suppression **logique**, et le corps
 * du message reste en base — c'est ce qui permet à la modération de le lire encore.
 */
@Serializable
data class ChatMessage(
    val id: String,
    val senderId: String,
    val kind: ChatMessageKind,
    val body: String,
    val createdAt: String,
    val linkId: String? = null,
    val groupId: String? = null,
    val recitationId: String? = null,
    val recitation: RecitationAttachment? = null,
    val deletedAt: String? = null,
)

/** Signalement d'un message. `excerpt` est l'extrait figé au moment du signalement. */
@Serializable
data class MessageReport(
    val id: String,
    val messageId: String,
    val reason: String,
    val reporterId: String,
    val excerpt: String,
    val status: ReportStatus,
    val createdAt: String,
)

/** Suspension de la messagerie. `suspendedUntil` nul veut dire « sans terme ». */
@Serializable
data class SocialSuspension(
    val userId: String,
    val reason: String,
    val createdAt: String,
    val suspendedUntil: String? = null,
)

/** Objectif de séances fixé à deux pour une semaine donnée. */
@Serializable
data class SharedGoal(
    val id: String,
    val linkId: String,
    val weekStart: String,
    val targetSessions: Int,
    val proposedBy: String,
    val acceptedAt: String? = null,
)

/** Rendez-vous de révision proposé à deux. */
@Serializable
data class ReviewAppointment(
    val id: String,
    val linkId: String,
    val startsAt: String,
    val proposedBy: String,
    val acceptedAt: String? = null,
)

/**
 * Résumé d'une conversation, pour la liste d'amis.
 *
 * `unread` compte les messages non lus ; `createdAt` est l'instant du **dernier** message, et
 * c'est lui qui trie la liste — jamais la date du lien, qui ne bouge plus après l'acceptation.
 */
@Serializable
data class ConversationSummary(
    val body: String,
    val createdAt: String,
    val unread: Int = 0,
)

/** Instantané des amis, mis en cache localement par utilisateur. */
@Serializable
data class FriendsSnapshot(
    val userId: String,
    val profile: FriendProfile,
    val links: List<FriendLink> = emptyList(),
    val groups: List<FriendGroup> = emptyList(),
    val suspension: SocialSuspension? = null,
)
