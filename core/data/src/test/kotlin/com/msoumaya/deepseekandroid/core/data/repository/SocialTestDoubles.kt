package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.remote.ChatRoom
import com.msoumaya.deepseekandroid.core.data.remote.OwnerStore
import com.msoumaya.deepseekandroid.core.data.remote.SocialInbox
import com.msoumaya.deepseekandroid.core.data.remote.SocialSource
import com.msoumaya.deepseekandroid.core.model.ChatMessage
import com.msoumaya.deepseekandroid.core.model.ChatMessageKind
import com.msoumaya.deepseekandroid.core.model.FriendGroup
import com.msoumaya.deepseekandroid.core.model.FriendLink
import com.msoumaya.deepseekandroid.core.model.FriendOverview
import com.msoumaya.deepseekandroid.core.model.FriendProfile
import com.msoumaya.deepseekandroid.core.model.GroupMember
import com.msoumaya.deepseekandroid.core.model.ReviewAppointment
import com.msoumaya.deepseekandroid.core.model.SharedGoal
import com.msoumaya.deepseekandroid.core.model.SocialSuspension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

// ---------------------------------------------------------------------------
// Doublures des tests du dépôt social
// ---------------------------------------------------------------------------
// Écrites **une fois**, et partagées par `SocialRepositoryTest` et `ConversationRepositoryTest`.
// Elles vivaient d'abord dans le premier, en classes imbriquées privées, et le second devait
// alors en porter une copie : deux doublures de trente-deux méthodes, dont l'une aurait
// divergé de l'autre au premier ajout à l'interface — et le symptôme aurait été un test voisin
// qui échoue sans rien expliquer.
//
// **Ce qu'elles refusent, et pourquoi c'est découpé.** [FakeSocialSource] sait refuser
// **séparément** les lectures principales, les compléments de la liste d'amis, la boîte de
// réception, les gestes, la lecture des messages, une page plus ancienne, les compléments de la
// conversation et l'aperçu de l'ami. Ce découpage n'est pas un confort : c'est la seule façon de
// mesurer qu'un échec des cercles, un échec de la liste d'amis et un échec d'un aperçu n'ont pas
// les mêmes conséquences. Une doublure qui refuserait tout d'un bloc ne distinguerait rien.
// ---------------------------------------------------------------------------

/** Propriétaire courant, en mémoire et observable. C'est lui qui déclenche le chargement. */
internal class FakeOwners(owner: String?) : OwnerStore {
    private val flow = MutableStateFlow(owner)
    override val ownerId: Flow<String?> = flow
    override suspend fun currentOwner(): String? = flow.value
    override suspend fun setOwner(userId: String?) {
        flow.value = userId
    }
}

/** Doublure en mémoire, qui compte ses appels et refuse par catégorie. */
internal class FakeSocialSource : SocialSource {

    // ------------------------------------------------------------------ liste d'amis

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

    // ------------------------------------------------------------------ conversation

    var friendOverview: FriendOverview? = null
    var messagePage: List<ChatMessage> = emptyList()
    var olderPage: List<ChatMessage> = emptyList()
    var memberList: List<GroupMember> = emptyList()
    var goalList: List<SharedGoal> = emptyList()
    var appointmentList: List<ReviewAppointment> = emptyList()
    var readAt: String? = null

    /** Refus de la lecture **des messages**. */
    var failRoom: Throwable? = null

    /** Refus d'une page **plus ancienne**. Séparé : remonter et rafraîchir n'ont pas le même effet. */
    var failOlder: Throwable? = null

    /** Refus des compléments : membres, objectifs, rendez-vous, position de lecture de l'autre. */
    var failRoomExtras: Throwable? = null

    /** Refus de l'aperçu de l'ami, **seul** : c'est ainsi qu'on mesure que les autres survivent. */
    var failOverview: Throwable? = null

    /** Curseurs reçus par `messages`, dans l'ordre : `null` pour la page la plus récente. */
    val cursors = mutableListOf<String?>()

    /** Pièces ouvertes, dans l'ordre : « link:<id> » ou « group:<id> ». */
    val rooms = mutableListOf<String>()

    var roomCalls = 0
    var olderCalls = 0
    var readMarks = 0
    var memberCalls = 0
    var overviewCalls = 0

    override suspend fun messages(room: ChatRoom, before: String?): List<ChatMessage> {
        rooms += if (room.linkId != null) "link:${room.linkId}" else "group:${room.groupId}"
        cursors += before
        return if (before == null) {
            roomCalls++
            failRoom?.let { refuser<Nothing>(it) }
            messagePage
        } else {
            olderCalls++
            failOlder?.let { refuser<Nothing>(it) }
            olderPage
        }
    }

    override suspend fun overview(otherId: String): FriendOverview? {
        overviewCalls++
        failOverview?.let { refuser<Nothing>(it) }
        return friendOverview
    }

    override suspend fun markConversationRead(linkId: String) {
        readMarks++
        failRoomExtras?.let { refuser<Nothing>(it) }
    }

    override suspend fun otherReadAt(linkId: String, otherId: String): String? {
        failRoomExtras?.let { refuser<Nothing>(it) }
        return readAt
    }

    override suspend fun members(groupId: String): List<GroupMember> {
        memberCalls++
        failRoomExtras?.let { refuser<Nothing>(it) }
        return memberList
    }

    override suspend fun sharedGoals(linkId: String): List<SharedGoal> {
        failRoomExtras?.let { refuser<Nothing>(it) }
        return goalList
    }

    override suspend fun appointments(linkId: String): List<ReviewAppointment> {
        failRoomExtras?.let { refuser<Nothing>(it) }
        return appointmentList
    }

    override suspend fun sendMessage(room: ChatRoom, body: String, kind: ChatMessageKind) =
        geste("sendMessage")

    override suspend fun deleteMessage(messageId: String) = geste("deleteMessage")

    override suspend fun reportMessage(messageId: String, reason: String) =
        geste("reportMessage")

    override suspend fun hideMessageForMe(messageId: String) = geste("hideMessageForMe")

    override suspend fun inviteGroupMember(groupId: String, friendId: String) =
        geste("inviteGroupMember")

    override suspend fun acceptGroupInvite(groupId: String) = geste("acceptGroupInvite")

    override suspend fun declineGroupInvite(groupId: String) = geste("declineGroupInvite")

    override suspend fun setGroupModerator(
        groupId: String,
        memberId: String,
        enabled: Boolean,
    ) = geste("setGroupModerator")

    override suspend fun removeGroupMember(groupId: String, memberId: String) =
        geste("removeGroupMember")

    override suspend fun deleteGroup(groupId: String) = geste("deleteGroup")

    override suspend fun proposeSharedGoal(
        linkId: String,
        weekStart: String,
        targetSessions: Int,
    ) = geste("proposeSharedGoal")

    override suspend fun acceptSharedGoal(goalId: String) = geste("acceptSharedGoal")

    override suspend fun proposeAppointment(linkId: String, startsAt: String) =
        geste("proposeAppointment")

    override suspend fun acceptAppointment(appointmentId: String) = geste("acceptAppointment")

    override suspend fun cancelAppointment(appointmentId: String) =
        geste("cancelAppointment")
}
