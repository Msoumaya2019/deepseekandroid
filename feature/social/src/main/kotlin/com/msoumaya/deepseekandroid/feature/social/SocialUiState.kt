package com.msoumaya.deepseekandroid.feature.social

import androidx.compose.runtime.Immutable
import com.msoumaya.deepseekandroid.core.domain.Social

// ---------------------------------------------------------------------------
// État affichable de l'écran « Amis »
// ---------------------------------------------------------------------------
// Portage de la liste d'amis de `FriendsScreen` (`src/SocialScreens.tsx:144-167`).
//
// Les valeurs sont calculées **une fois**, dans le `ViewModel`, et non dans les composables :
// le filtrage, le tri et la recherche parcourent les liens, les résumés de conversation et la
// carte de présence. Les appeler depuis le corps d'un composable les rejouerait à chaque
// recomposition — c'est-à-dire à chaque frappe dans le champ de recherche, puisque sa valeur
// est justement l'une des entrées du calcul.
//
// `@Immutable` est une promesse tenue : toutes les propriétés sont `val` et de type stable.
//
// **Tout ce qui se lit est déjà résolu.** « En ligne », « 2 janv. 10:00 » et
// « Invitation de Untel » sont des chaînes, pas des nombres ni des dates à mettre en forme.
// C'est ce qui rend les règles éprouvables sans appareil : « un ami dont la présence est
// inconnue n'apparaît pas sous le filtre En ligne » se vérifie en quelques millisecondes, alors
// qu'un libellé écrit dans un composable demanderait un test d'interface.
// ---------------------------------------------------------------------------

/** Ce que l'écran « Amis » affiche. */
@Immutable
data class SocialUiState(
    /** Vrai tant que le premier état n'a pas été lu. */
    val loading: Boolean = true,

    /**
     * Panne de la lecture principale, ou `null`.
     *
     * Une liste vide **sans** panne se lit « tu n'as pas d'amis » : c'est une affirmation fausse
     * sur des tiers, et c'est la raison d'être de ce champ.
     */
    val failure: String? = null,

    /** Vrai si un compte est ouvert. Faux aussi quand l'application n'est pas configurée. */
    val signedIn: Boolean = false,

    /** Vrai pendant un geste. L'écran désactive alors ce qui pourrait partir deux fois. */
    val busy: Boolean = false,

    /** Dernier message d'information, ou `null`. Jamais effacé, comme à l'origine. */
    val notice: String? = null,

    /**
     * Message de suspension de la messagerie, ou `null`.
     *
     * **Écart assumé.** Le client d'origine ne l'affiche que dans la conversation. La
     * conversation n'existe pas encore ici : sans cette carte, une personne suspendue ne
     * l'apprendrait nulle part. L'information est donc remontée d'un cran, et elle redescendra
     * dans la conversation quand elle existera.
     */
    val suspension: String? = null,

    /**
     * Vrai si une conversation est ouverte : l'écran l'affiche alors **à la place** de la liste.
     *
     * **Ce n'est pas un état d'interface, c'est un fait du dépôt.** C'est `room != null`, la même
     * vérité que `ConversationUiState.open`, et il n'y en a qu'une : l'écran n'a donc pas à se
     * souvenir de ce qu'il a ouvert, il regarde ce qui est ouvert. Deux sources — un booléen ici
     * et une pièce là-bas — finiraient par diverger, et l'écran montrerait une conversation fermée
     * ou l'inverse.
     */
    val conversationOpen: Boolean = false,

    // --- Saisies, tenues par le ViewModel ---

    /** Texte du champ de recherche. Il **entre** dans le calcul, donc il est publié. */
    val query: String = "",

    /** Libellé du filtre retenu, tel que le sélecteur segmenté l'affiche. */
    val filterLabel: String = Social.Filter.ALL.label,

    /** Vrai si la liste est dépliée au-delà de [Social.LIST_LIMIT]. */
    val allFriends: Boolean = false,

    /** Vrai si le bloc « Invitations et cercles privés » est déplié. */
    val optionsOpen: Boolean = false,

    /** Texte du champ « Code d'invitation ». */
    val code: String = "",

    /** Texte du champ « Nom du cercle ». */
    val groupName: String = "",

    // --- Données ---

    /** Code d'invitation du compte, ou `null` tant que le profil n'est pas lu. */
    val inviteCode: String? = null,

    /** Les amis visibles, déjà filtrés, triés et bornés. */
    val friends: List<FriendRow> = emptyList(),

    /** Nombre d'amis acceptés — le compte du titre, indépendant du filtre. */
    val friendCount: Int = 0,

    /** Invitations reçues, à accepter ou refuser. */
    val invitations: List<InvitationRow> = emptyList(),

    /** Invitations envoyées, en attente. */
    val sent: List<InvitationRow> = emptyList(),

    /** Comptes que **j'ai** bloqués. */
    val blocked: List<BlockedRow> = emptyList(),

    /** Cercles dont je suis membre. */
    val circles: List<CircleRow> = emptyList(),

    // --- Ce qui reste à décider par l'écran ---

    /** Libellés du sélecteur segmenté, dans l'ordre. */
    val filterLabels: List<String> = Social.Filter.entries.map { it.label },

    /** Vrai si l'invitation peut partir : un code saisi, et aucun geste en cours. */
    val canSendInvitation: Boolean = false,

    /** Vrai si le cercle peut être créé : un nom valide, et aucun geste en cours. */
    val canCreateGroup: Boolean = false,
)

/**
 * Un ami dans la liste.
 *
 * @param otherId identifiant de **l'autre** participant. Il est publié parce que c'est lui qui
 *   désigne la personne dans les gestes de gestion — bloquer, débloquer —, et non l'identifiant
 *   du lien : le serveur attend l'identifiant du compte.
 * @param subtitle sous-titre déjà résolu : « En ligne », un instant, ou « Commencer une
 *   discussion ».
 * @param summary dernier message, ou chaîne vide. Une chaîne vide et un résumé absent
 *   s'affichent de la même façon — il n'y a rien à dire.
 * @param unread nombre de messages non lus. Zéro n'affiche pas de pastille.
 *
 * **Ce que cette ligne ne porte pas.** Ni « la présence est connue », ni « hors ligne » : la
 * pastille de la liste est verte quand l'ami est en ligne et grise sinon, **y compris quand on
 * ne sait pas** — c'est le comportement du client d'origine, où la carte de présence rend
 * `undefined` et où le ternaire retombe sur le gris. Distinguer les deux cas demanderait un
 * troisième état de pastille, que l'original n'a pas.
 */
@Immutable
data class FriendRow(
    val id: String,
    val otherId: String,
    val name: String,
    val online: Boolean,
    val subtitle: String,
    val summary: String,
    val unread: Int,
)

/** Une invitation, reçue ou envoyée. `linkId` est ce que le serveur attend pour agir. */
@Immutable
data class InvitationRow(val linkId: String, val label: String)

/** Un compte bloqué. `otherId` est l'identifiant du **compte**, pas du lien. */
@Immutable
data class BlockedRow(val otherId: String, val label: String)

/**
 * Un cercle privé.
 *
 * @param isAdminContact vrai pour le cercle « contact administrateur », qui n'est pas un cercle
 *   comme les autres : il est créé par le serveur et réunit tous les administrateurs.
 */
@Immutable
data class CircleRow(val id: String, val name: String, val isAdminContact: Boolean)
