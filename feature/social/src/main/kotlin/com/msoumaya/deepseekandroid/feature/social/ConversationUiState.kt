package com.msoumaya.deepseekandroid.feature.social

import androidx.compose.runtime.Immutable

// ---------------------------------------------------------------------------
// État affichable de l'écran « Conversation »
// ---------------------------------------------------------------------------
// Portage de la conversation de `FriendsScreen` (`src/SocialScreens.tsx:168-208`).
//
// **Ce qui est résolu ici, et ce qui ne l'est pas.** Tout ce qui se **lit** est déjà écrit : le
// nom de l'auteur, l'heure, l'accusé de lecture, la référence d'une récitation, le libellé d'un
// rôle. L'écran ne met rien en forme, il dispose.
//
// Les libellés **constants** — « Envoyer », « Rejoindre », « Supprimer le cercle » — ne
// transitent pas par ici : l'écran les lit dans `SocialText`, comme il le fait déjà pour la liste
// d'amis. Ce qui transite, c'est ce qui **dépend de la donnée** : un libellé qui change d'une
// ligne à l'autre, un booléen qui décide de l'existence d'un bouton. Recopier une constante dans
// cet état n'ajouterait rien qu'une occasion de diverger.
//
// **Pourquoi un état séparé de `SocialUiState`.** Celui-ci décrit la **pièce ouverte**,
// l'autre décrit la **liste d'amis**. Les fondre obligerait chaque écran à porter les champs de
// l'autre — la liste n'a que faire d'un brouillon de message, et la conversation ne montre ni
// filtre ni recherche.
// ---------------------------------------------------------------------------

/**
 * Ce que l'écran « Conversation » affiche.
 *
 * Tous les champs ont une valeur par défaut : un état par défaut est donc un état **fermé**, et
 * c'est ce que rend le calcul quand aucune pièce n'est ouverte. L'écran n'a ainsi jamais à
 * traiter un cas « à moitié rempli ».
 */
@Immutable
data class ConversationUiState(
    /** Vrai si une pièce est ouverte. Faux : c'est la liste d'amis qui est à l'écran. */
    val open: Boolean = false,

    /** Vrai pendant la première lecture de la pièce. */
    val loading: Boolean = false,

    /**
     * Panne de la lecture des messages, quand il n'y a rien à montrer à la place.
     *
     * Un échec de **relecture**, lui, ne passe pas par ici : la pièce garde ses messages et se
     * signale par un bandeau. Une conversation vide **sans** panne se lirait « personne ne t'a
     * jamais écrit », ce qui est une affirmation fausse sur un tiers.
     */
    val failure: String? = null,

    /**
     * Dernier message d'information du dépôt, ou `null`.
     *
     * **Pourquoi il doit transiter par ici.** La conversation est l'écran qui produit la plupart
     * des avis — « Étape partagée avec cet ami. », « Aucun message reçu à signaler dans cette
     * conversation. », « Entre une date et une heure futures au format AAAA-MM-JJ HH:mm. » —, et
     * le bandeau de la liste d'amis n'est plus à l'écran quand ils paraissent. Sans ce champ, ces
     * messages seraient **écrits pour personne** : le geste semblerait n'avoir rien fait, ce qui
     * est exactement ce qu'un avis évite.
     */
    val notice: String? = null,

    // --- En-tête ---

    /** Nom affiché : celui de l'ami, ou celui du cercle. Jamais vide. */
    val title: String = "",

    /**
     * Ligne d'état sous le nom : « Écrit un message… », « En ligne », « Hors ligne ».
     *
     * `null` dans un **cercle** : l'original n'y montre pas d'état, et il n'y a pas de présence
     * à afficher pour un groupe. C'est une absence voulue, pas une valeur manquante.
     */
    val statusLabel: String? = null,

    /**
     * Vrai si « Signaler » a un sens ici.
     *
     * Réservé au **tête-à-tête** : on signale les propos d'une personne, pas ceux d'un groupe.
     * L'original place le geste dans la branche du lien, et le calcul reprend cette portée.
     */
    val canReport: Boolean = false,

    // --- Profil et entraide ---

    /** Vrai si le bloc peut être ouvert. Faux dans le cercle de l'administration. */
    val showTools: Boolean = false,

    /** Vrai si le bloc est déplié. Toujours faux quand [showTools] est faux. */
    val toolsOpen: Boolean = false,

    /** Carte d'aperçu de l'ami. `null` si l'ami ne partage rien, ou dans un cercle. */
    val overview: OverviewCard? = null,

    /** Objectifs partagés. `null` dans un cercle — la section n'existe pas. */
    val goals: GoalSection? = null,

    /** Rendez-vous de révision. `null` dans un cercle — la section n'existe pas. */
    val appointments: AppointmentSection? = null,

    /** Carte des membres. `null` dans un lien, et dans le cercle de l'administration. */
    val members: MembersCard? = null,

    // --- Discussion ---

    /**
     * Libellé du bouton d'historique, ou `null` s'il n'y a rien avant.
     *
     * Il porte les **deux** états du bouton — « Charger les messages précédents » et
     * « Chargement… » — parce que le second n'est qu'un état du premier. Deux champs séparés
     * auraient permis un état incohérent : un libellé de chargement sans chargement en cours.
     */
    val olderLabel: String? = null,

    /** Vrai si l'historique peut être demandé maintenant. Faux pendant une demande. */
    val canLoadOlder: Boolean = false,

    /** Message de suspension de la messagerie, ou `null` si elle ne l'est pas. */
    val suspension: String? = null,

    /** Les messages, du plus ancien au plus récent. */
    val messages: List<MessageRow> = emptyList(),

    /** Formulaire de signalement, ouvert ou non. */
    val report: ReportForm? = null,

    // --- Compositeur ---

    /** Texte du champ de saisie. Il **entre** dans le calcul : il décide de l'envoi. */
    val draft: String = "",

    /** Vrai si le message peut partir. */
    val canSend: Boolean = false,

    /** Vrai si l'étape peut être partagée : un tête-à-tête, et aucun geste en cours. */
    val canShareProgress: Boolean = false,
)

/**
 * Carte d'aperçu de ce que l'ami accepte de montrer.
 *
 * @param title nom et présence, sur une ligne.
 * @param goalLine objectif et pourcentage atteint. `null` si l'ami ne partage pas d'objectif.
 * @param weekLine versets et séances de la semaine. `null` avec [goalLine].
 * @param privateNote « Progression privée ». `null` dès que [goalLine] est renseignée.
 * @param passageLine passage en cours. `null` si l'ami n'en partage pas.
 *
 * **Exactement l'un des deux entre [goalLine] et [privateNote] est renseigné.** L'original écrit
 * l'un ou l'autre : dire « progression privée » à côté d'un objectif affiché serait se
 * contredire, et n'écrire ni l'un ni l'autre laisserait un blanc qu'on lirait comme un bug.
 */
@Immutable
data class OverviewCard(
    val title: String,
    val goalLine: String? = null,
    val weekLine: String? = null,
    val privateNote: String? = null,
    val passageLine: String? = null,
)

/**
 * Section « Objectif partagé ».
 *
 * @param target saisie courante, telle quelle — c'est le champ qui la porte.
 * @param canPropose vrai si la saisie est un entier de 1 à 14, et qu'aucun geste n'est en cours.
 */
@Immutable
data class GoalSection(
    val target: String = "",
    val canPropose: Boolean = false,
    val rows: List<GoalRow> = emptyList(),
)

/** Un objectif partagé. @param state « Accepté par vous deux » ou « En attente d'acceptation ». */
@Immutable
data class GoalRow(
    val id: String,
    val label: String,
    val state: String,
    /** Vrai si je peux l'accepter : ni déjà accepté, ni proposé par moi. */
    val canAccept: Boolean = false,
)

/**
 * Section « Rendez-vous de révision ».
 *
 * **Pas de `canPropose`.** L'original ne désactive jamais ce bouton : la saisie est vérifiée au
 * moment du clic, et un rendez-vous mal formé se refuse par un message, pas par un bouton gris.
 * Un bouton grisé pendant la frappe ne dirait pas *pourquoi* il l'est.
 */
@Immutable
data class AppointmentSection(
    val text: String = "",
    val rows: List<AppointmentRow> = emptyList(),
)

/** Un rendez-vous. @param state « Confirmé » ou « En attente ». */
@Immutable
data class AppointmentRow(
    val id: String,
    val label: String,
    val state: String,
    val canAccept: Boolean = false,
)

/**
 * Carte des membres d'un cercle.
 *
 * @param title compte des membres ayant accepté, sur le plafond du cercle.
 * @param invites amis acceptés que je peux encore inviter.
 * @param canDeleteGroup vrai si je suis le propriétaire : lui seul supprime le cercle.
 */
@Immutable
data class MembersCard(
    val title: String = "",
    val rows: List<MemberRow> = emptyList(),
    val invites: List<InviteRow> = emptyList(),
    val canDeleteGroup: Boolean = false,
)

/**
 * Un membre dans la carte du cercle.
 *
 * @param moderatorLabel « Nommer modérateur » ou « Retirer la modération », ou `null` si je n'ai
 *   pas le droit d'y toucher. Le libellé dépend du rôle **actuel** du membre : c'est pourquoi il
 *   est résolu ici, et non choisi par l'écran.
 */
@Immutable
data class MemberRow(
    val userId: String,
    val label: String,
    val canJoin: Boolean = false,
    val canDecline: Boolean = false,
    val moderatorLabel: String? = null,

    /**
     * Vrai si appuyer sur [moderatorLabel] doit **donner** la modération. Faux : la retirer.
     *
     * **Pourquoi ce booléen existe, alors que le libellé dit déjà tout.** L'écran a besoin du
     * **sens** du geste, et le libellé ne le porte pas de façon fiable : déduire « donner » de
     * `moderatorLabel == NAME_MODERATOR` marcherait jusqu'au jour où l'un des deux mots change.
     * La décision est donc prise là où le rôle est connu — dans le rendu —, et l'écran ne fait que
     * la transmettre.
     */
    val grantsModerator: Boolean = false,
    val canRemove: Boolean = false,
)

/** Un ami que je peux inviter dans le cercle. */
@Immutable
data class InviteRow(val userId: String, val label: String)

/**
 * Un message, prêt à disposer.
 *
 * @param mine vrai si le message est de moi. C'est ce qui décide du **côté** où il se peint et
 *   de la couleur de sa carte — les deux vont ensemble, donc un seul booléen.
 * @param stamp « Moi · 18:00 », ou « Moi » si l'instant est illisible.
 * @param recitation bloc d'écoute joint, `null` pour un message de texte.
 * @param receipt « Lu » ou « Envoyé ». `null` pour les messages des autres, et dans un cercle :
 *   on n'accuse réception d'un message que dans un tête-à-tête, et seulement des siens.
 * @param canDelete vrai si le message peut être supprimé — le mien, ou celui d'un autre quand je
 *   modère un cercle.
 */
@Immutable
data class MessageRow(
    val id: String,
    val mine: Boolean,
    val stamp: String,
    val body: String,
    val recitation: RecitationRow? = null,
    val receipt: String? = null,
    val canDelete: Boolean = false,
)

/**
 * Bloc d'une récitation partagée.
 *
 * @param reference passage enregistré, ou le repli « Enregistrement indisponible ».
 * @param durationLabel durée en `m:ss`, `null` quand il n'y a rien à écouter.
 *
 * **Pas de bouton d'écoute, et pourquoi.** L'original en porte un — « ▶ Écouter la
 * récitation » —, et le porter ici supposerait deux choses qui n'existent pas encore : une URL
 * **signée** pour un fichier déposé dans un espace privé, et un lecteur qui ne se confonde pas
 * avec la séance en cours du lecteur coranique. Un bouton qui ne joue rien est pire qu'un bouton
 * absent : le bloc affiche donc **ce que le message porte** — le passage, et sa durée —, et
 * l'écoute viendra avec le partage de récitations.
 */
@Immutable
data class RecitationRow(
    val reference: String,
    val durationLabel: String? = null,
)

/**
 * Formulaire de signalement, ouvert.
 *
 * @param targetId identifiant du message signalé — le dernier **reçu** de la conversation.
 * @param canSend vrai si le motif est assez long, et qu'aucun geste n'est en cours.
 */
@Immutable
data class ReportForm(
    val targetId: String,
    val reason: String = "",
    val canSend: Boolean = false,
)

/**
 * Les saisies de l'écran « Conversation », qui **entrent** dans le calcul.
 *
 * Tenues par le `ViewModel`, comme celles de la liste d'amis : ce ne sont pas des états
 * d'interface oubliés dans un composable, mais des **entrées** du rendu. Le brouillon de message
 * décide de l'activation du bouton d'envoi ; le garder dans le champ et l'activation ailleurs
 * ferait deux sources pour la même décision.
 */
@Immutable
internal data class ConversationInputs(
    val draft: String = "",
    val toolsOpen: Boolean = false,
    val reportTarget: String? = null,
    val reportReason: String = "",
    val goalTarget: String = "",
    val appointmentText: String = "",
)
