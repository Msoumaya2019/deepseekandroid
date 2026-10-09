package com.msoumaya.deepseekandroid.feature.home

import androidx.compose.runtime.Immutable
import com.msoumaya.deepseekandroid.core.model.ProblemReportType

// ---------------------------------------------------------------------------
// État affichable du signalement de problème
// ---------------------------------------------------------------------------
// Même séparation que pour l'accueil : **tout ce qui se décide est pré-résolu en texte**, et
// l'écran ne choisit rien. Le composable ne fait que poser ce paquet ; il ne compte pas les
// caractères, ne compare pas de longueur, et n'écrit aucune phrase.
//
// La raison est la même qu'ailleurs dans ce module : un choix fait dans le corps d'un composable
// est rejoué à chaque recomposition — c'est-à-dire à chaque frappe, puisque l'écran porte un
// champ de saisie — et n'est éprouvable qu'avec un hôte Compose, dont ce projet n'a aucun.
// ---------------------------------------------------------------------------

/**
 * Ce que la carte et la feuille de signalement affichent.
 *
 * @param natures les cinq natures, **dans l'ordre de l'écran**. Elles viennent de
 *   [ProblemReportType.all], qui est l'ordre du client d'origine, et non d'une liste écrite ici :
 *   une seconde liste finirait par en oublier une, et la sixième nature n'apparaîtrait nulle part.
 * @param initialType la nature cochée à l'ouverture. C'est la première de [natures], comme le
 *   `useState<ProblemType>('Bug')` de l'original — et non un `Bug` écrit en dur, qui resterait
 *   « Bug » si l'ordre changeait.
 * @param descriptionMax la borne du champ, **celle du domaine**. Le composable la reçoit au lieu
 *   de l'écrire : le `maxLength` du champ, le compteur [counter] et la phrase de refus
 *   `ProblemReportText.DESCRIPTION_INVALID` disent le même nombre, et trois copies divergeraient.
 * @param counter le compteur sous le champ, déjà mis en forme.
 * @param notice le message à afficher, ou `null`. **Déjà fusionné** : voir
 *   [ProblemReportRenderer.render] pour ce que la fusion décide.
 * @param done vrai quand le dernier envoi a abouti à une issue. L'écran de confirmation remplace
 *   alors le formulaire — et c'est ce qui fait qu'un second envoi est impossible sans rouvrir.
 */
@Immutable
data class ProblemReportUiState(
    /** Titre de la carte de l'accueil. Sert aussi d'étiquette au geste. */
    val cardTitle: String,

    /** Sous-titre de la carte de l'accueil. */
    val cardSubtitle: String,

    /** Titre de la feuille. */
    val sheetTitle: String,

    /** Sous-titre de la feuille. */
    val sheetSubtitle: String,

    /** Libellé du bouton de fermeture, et des boutons qui referment. */
    val closeLabel: String,

    /** Étiquette du fond de la feuille, qui la referme aussi. */
    val dismissLabel: String,

    /** Intertitre du groupe des natures. */
    val typeLabel: String,

    /** Les cinq natures, dans l'ordre de l'écran. */
    val natures: List<ProblemReportType>,

    /** La nature cochée à l'ouverture. */
    val initialType: ProblemReportType,

    /** Intertitre du champ de description. */
    val descriptionLabel: String,

    /** Invite du champ de description. */
    val descriptionPlaceholder: String,

    /** Borne du champ de description. */
    val descriptionMax: Int,

    /** Compteur de caractères, mis en forme. */
    val counter: String,

    /** Titre de la ligne de capture — « Ajouter une capture » ou « Capture ajoutée ». */
    val attachTitle: String,

    /** Sous-titre de la ligne de capture. */
    val attachSubtitle: String,

    /** Libellé du bouton qui détache la capture. */
    val removeLabel: String,

    /** Libellé du bouton d'envoi — « Envoyer à l'administrateur » ou « Envoi… ». */
    val sendLabel: String,

    /** Vrai quand une capture est jointe. */
    val hasAttachment: Boolean,

    /** Vrai quand le bouton d'envoi doit être actif. */
    val canSend: Boolean,

    /** Vrai pendant un envoi. */
    val busy: Boolean,

    /** Vrai quand l'envoi a abouti à une issue : l'écran de confirmation s'affiche. */
    val done: Boolean,

    /** Le message à afficher, ou `null`. */
    val notice: String?,
)
