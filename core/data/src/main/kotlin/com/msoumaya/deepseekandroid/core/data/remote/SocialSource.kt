package com.msoumaya.deepseekandroid.core.data.remote

import com.msoumaya.deepseekandroid.core.model.ConversationSummary
import com.msoumaya.deepseekandroid.core.model.FriendGroup
import com.msoumaya.deepseekandroid.core.model.FriendLink
import com.msoumaya.deepseekandroid.core.model.FriendProfile
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
// **Ce qui n'est pas ici.** La messagerie d'une conversation (envoyer, supprimer, signaler,
// marquer lu, objectifs partagés, rendez-vous, membres de cercle) arrive avec l'écran de
// conversation. Ce fichier ne porte que ce que la **liste d'amis** demande : le profil, les
// liens, les cercles, la suspension, l'aperçu des conversations, et les six gestes de la liste.
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
}
