package com.msoumaya.deepseekandroid.core.data.remote

import com.msoumaya.deepseekandroid.core.model.ChatMessage
import com.msoumaya.deepseekandroid.core.model.ChatMessageKind
import com.msoumaya.deepseekandroid.core.model.ConversationSummary
import com.msoumaya.deepseekandroid.core.model.FriendGroup
import com.msoumaya.deepseekandroid.core.model.FriendLink
import com.msoumaya.deepseekandroid.core.model.FriendOverview
import com.msoumaya.deepseekandroid.core.model.FriendProfile
import com.msoumaya.deepseekandroid.core.model.GroupMember
import com.msoumaya.deepseekandroid.core.model.ReviewAppointment
import com.msoumaya.deepseekandroid.core.model.SharedGoal
import com.msoumaya.deepseekandroid.core.model.SocialSuspension

// ---------------------------------------------------------------------------
// Accès aux données sociales
// ---------------------------------------------------------------------------
// Portage de la partie « lecture et gestes » de `src/services/social.ts`.
//
// **L'interface existe pour la même raison que `RemoteStateSource`.** Le dépôt qui la consomme
// contient des règles qui ne se voient pas à l'écran : ne pas appeler le réseau sans session,
// ne pas effacer une liste d'amis parce que les cercles ont échoué, ne pas garder les amis du
// compte précédent. Ces règles se mesurent en substituant une source en mémoire — sans réseau,
// sans serveur et sans appareil.
//
// **Ce qui est ici.** Deux ensembles, et ils ne se recouvrent pas. D'un côté ce que la **liste
// d'amis** demande — le profil, les liens, les cercles, la suspension, l'aperçu des
// conversations, et ses six gestes. De l'autre ce que la **conversation** demande — lire et
// écrire des messages, masquer, supprimer, signaler, marquer lu, l'aperçu d'un ami, les membres
// d'un cercle, les objectifs partagés et les rendez-vous.
//
// **Ce qui n'est toujours pas ici.** Le temps réel. Dans le client d'origine, la saisie en cours
// et l'arrivée d'un message passent par un canal Realtime ; le portage ne l'a pas encore, et
// l'écran se relit à l'ouverture et après chaque geste au lieu d'être poussé. C'est une
// différence de **fraîcheur**, pas de contenu : rien de ce qui s'écrit ici ne dépend d'elle.
// ---------------------------------------------------------------------------

/**
 * Aperçu de la boîte de réception.
 *
 * Deux tables distinctes, et deux clés distinctes — c'est le point où une erreur ne se voit pas :
 *
 *  - [summaries] est indexé par l'identifiant du **lien** (`friend_links.id`), parce que c'est
 *    ce que la fonction serveur `friend_inbox` rend : une ligne par lien accepté ;
 *  - [statuses] est indexé par l'identifiant de **l'autre personne** (`friend_links` porte deux
 *    comptes, et c'est l'autre qui est en ligne, pas le lien).
 *
 * Les confondre ne lève rien : la liste s'affiche, sans aperçu, et tous les amis paraissent
 * hors ligne. Un test de `core:domain` fige déjà l'indexation attendue par le filtre.
 */
data class SocialInbox(
    val summaries: Map<String, ConversationSummary> = emptyMap(),
    val statuses: Map<String, Boolean> = emptyMap(),
)

/**
 * La pièce d'une conversation : un **lien** ou un **cercle**, jamais les deux.
 *
 * Le client d'origine construit cet objet à la volée — `{linkId:selected.id}` ou
 * `{groupId:selected.id}` — et le passe à `listMessages` comme à `sendMessage`. Le nommer ici
 * évite que deux fonctions s'entendent sur une forme que rien ne déclare : un message dont les
 * deux champs seraient nuls n'appartient à aucune conversation, et le serveur le refuserait
 * sans que le type l'ait dit.
 *
 * Aucune garde n'est posée sur cette forme : elle est construite par l'écran à partir d'un
 * choix — un lien **ou** un cercle —, et une garde qui ne peut pas se déclencher ne mesure rien.
 */
data class ChatRoom(
    val linkId: String? = null,
    val groupId: String? = null,
)

/**
 * Lecture et gestes de l'espace « Amis ».
 *
 * Chaque méthode correspond à une fonction exportée de `services/social.ts`, et porte le même
 * nom d'usage. Aucune ne prend l'identifiant du compte courant en paramètre quand la fonction
 * serveur le lit elle-même dans le jeton (`auth.uid()`) : le passer laisserait croire que le
 * client décide pour qui il interroge, alors que c'est la politique RLS du serveur qui tranche.
 */
interface SocialSource {

    /**
     * Rend le profil social du compte courant, en le créant s'il n'existe pas.
     *
     * La création est **côté serveur** (`ensure_social_profile`) : c'est aussi ce qui produit le
     * code d'invitation, et le fabriquer ici donnerait un code que le serveur pourrait refuser.
     */
    suspend fun ensureProfile(): FriendProfile

    /**
     * Les liens d'amitié du compte [userId], du plus récent au plus ancien, profil de l'autre
     * joint quand il est lisible.
     *
     * [userId] est nécessaire ici, contrairement aux autres : la lecture des profils porte sur
     * « l'autre » de chaque lien, et c'est le client qui sait lequel des deux c'est.
     */
    suspend fun links(userId: String): List<FriendLink>

    /** Les cercles dont le compte courant est membre, du plus récent au plus ancien. */
    suspend fun groups(): List<FriendGroup>

    /** La suspension de messagerie de [userId], ou `null` s'il n'y en a pas. */
    suspend fun suspension(userId: String): SocialSuspension?

    /** Dernier message et présence pour chaque lien accepté. */
    suspend fun inbox(links: List<FriendLink>): SocialInbox

    /** Envoie une demande d'ami à partir d'un code d'invitation. */
    suspend fun requestFriend(code: String)

    suspend fun acceptFriend(linkId: String)

    suspend fun declineFriend(linkId: String)

    /** Retire une amitié acceptée. Le serveur exige que je sois l'un des deux participants. */
    suspend fun removeFriend(linkId: String)

    /**
     * Bloque le compte [otherId].
     *
     * Le serveur refuse de bloquer quelqu'un qui m'a déjà bloqué : c'est l'autre qui a la main,
     * et l'écran doit le taire plutôt que de proposer un geste sans effet.
     */
    suspend fun blockFriend(otherId: String)

    /** Débloque le compte [otherId]. Le filtre `blocked_by` est posé côté serveur. */
    suspend fun unblockFriend(otherId: String)

    /** Crée un cercle et rend son identifiant. */
    suspend fun createGroup(name: String): String

    /** Ouvre — ou retrouve — le cercle « contact administrateur », et rend son identifiant. */
    suspend fun openAdminContact(): String

    // ------------------------------------------------------------------ conversation

    /**
     * L'aperçu public de [otherId] : objectif, semaine en cours, passage actuel, présence.
     *
     * Rend `null` quand rien n'est lisible. Le client d'origine **levait** dans ce cas ; ici
     * l'absence est une réponse, parce que « progression privée » est un état normal — celui
     * d'un ami qui ne partage pas — et non une panne. Les confondre afficherait une erreur à
     * quelqu'un qui a simplement coché « non ».
     */
    suspend fun overview(otherId: String): FriendOverview?

    /**
     * Les messages de [room], du plus ancien au plus récent, bornés à **une page**.
     *
     * @param before instant du plus ancien message déjà à l'écran, ou `null` pour la première
     *   page. Le sens de la lecture est celui de l'original : on demande les **derniers**
     *   (`order desc`), puis on les remet dans l'ordre chronologique. Sans ce renversement, la
     *   première page afficherait les messages les plus **anciens** de la conversation.
     *
     * Les messages masqués pour moi sont retirés ici, et non par l'écran : c'est le même
     * filtrage qui décide de la page, et le faire ailleurs ferait compter les messages cachés
     * dans la règle « reste-t-il des messages plus anciens ».
     */
    suspend fun messages(room: ChatRoom, before: String? = null): List<ChatMessage>

    suspend fun sendMessage(
        room: ChatRoom,
        body: String,
        kind: ChatMessageKind = ChatMessageKind.TEXT,
    )

    /**
     * Partage la récitation [recitationId] dans la conversation du lien [linkId].
     *
     * **Un partage est un message**, et c'est pourquoi il vit ici plutôt qu'auprès des
     * récitations : il s'écrit dans `friend_messages`, avec `kind = 'recitation'` et la colonne
     * `recitation_id`. La conversation, elle, l'affiche comme n'importe quel message — c'est déjà
     * le cas à la lecture ([messages] va chercher la pièce jointe des messages de ce genre).
     *
     * **Jamais dans un cercle.** Le déclencheur `validate_recitation_message` exige `group_id`
     * nul : un cercle réunit des gens qui ne sont pas tous amis, et l'enregistrement de quelqu'un
     * ne s'ouvre pas à eux. La signature ne prend donc qu'un lien, et non une [ChatRoom] — le
     * type dit la règle, au lieu de la laisser à l'appelant.
     *
     * La [description] est le texte du message — l'original y met la référence du passage. Elle
     * est **rognée** par la réalisation : la colonne est bornée, et un corps trop long serait
     * refusé par le serveur au lieu d'être raccourci.
     */
    suspend fun shareRecitation(linkId: String, recitationId: String, description: String)

    suspend fun deleteMessage(messageId: String)

    suspend fun reportMessage(messageId: String, reason: String)

    /** Marque la conversation [linkId] comme lue par le compte courant. */
    suspend fun markConversationRead(linkId: String)

    /** Position de lecture de [otherId] sur [linkId], ou `null` s'il ne l'a jamais ouverte. */
    suspend fun otherReadAt(linkId: String, otherId: String): String?

    /** Masque [messageId] **pour le compte courant seulement** : l'autre le voit toujours. */
    suspend fun hideMessageForMe(messageId: String)

    /** Les membres de [groupId], profil joint quand il est lisible. */
    suspend fun members(groupId: String): List<GroupMember>

    suspend fun inviteGroupMember(groupId: String, friendId: String)

    suspend fun acceptGroupInvite(groupId: String)

    suspend fun declineGroupInvite(groupId: String)

    suspend fun setGroupModerator(groupId: String, memberId: String, enabled: Boolean)

    suspend fun removeGroupMember(groupId: String, memberId: String)

    /** Supprime le cercle **et ses messages**, définitivement. */
    suspend fun deleteGroup(groupId: String)

    /** Les objectifs partagés de [linkId], du plus récent au plus ancien, bornés. */
    suspend fun sharedGoals(linkId: String): List<SharedGoal>

    suspend fun proposeSharedGoal(linkId: String, weekStart: String, targetSessions: Int)

    suspend fun acceptSharedGoal(goalId: String)

    /** Les rendez-vous **à venir** de [linkId] : le passé n'est pas demandé. */
    suspend fun appointments(linkId: String): List<ReviewAppointment>

    suspend fun proposeAppointment(linkId: String, startsAt: String)

    suspend fun acceptAppointment(appointmentId: String)

    suspend fun cancelAppointment(appointmentId: String)
}
