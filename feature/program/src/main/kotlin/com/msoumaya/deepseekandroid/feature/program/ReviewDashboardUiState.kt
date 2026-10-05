package com.msoumaya.deepseekandroid.feature.program

import androidx.compose.runtime.Immutable
import com.msoumaya.deepseekandroid.core.domain.ReviewText
import com.msoumaya.deepseekandroid.core.domain.StudySession

// ---------------------------------------------------------------------------
// État affichable du tableau de bord des révisions
// ---------------------------------------------------------------------------
// Portage de `ReviewDashboard.tsx` et de `RevisionBottomActionBar.tsx`.
//
// **Pourquoi ce fichier vit dans `feature:program`.** Le tableau de bord est un satellite du
// programme : c'est sa carte de révision qui l'ouvre, et il referme sur l'onglet « Progrès ».
// Surtout, il partage avec le programme la **carte de reprise** (`StudyResumeCard`) et sa ligne
// (`ResumeLine`) : une reprise d'apprentissage et une reprise de révision sont la même carte avec
// deux jeux de mots. La déplacer dans `feature:progress` aurait demandé soit de dupliquer cette
// carte de 270 lignes, soit d'ouvrir une dépendance entre deux fonctionnalités — deux façons de
// payer plus cher que le gain d'un nom de paquet. `feature:progress` reste l'écran « Progrès »
// (les statistiques), qui n'est pas celui-ci.
//
// **Tout ce qui se lit est déjà résolu.** Comme pour le programme, les libellés, les dates, les
// comptes et les pourcentages sont des chaînes : le rendu ne calcule rien, il pose. C'est ce qui
// rend le plan de révision éprouvable sans appareil — « Jour 3 / 7 », « 43 % réellement révisés »
// et « À rattraper » se vérifient en quelques millisecondes.
//
// **Ce qui reste dans le composable.** Trois choses, et trois seulement : le dépliage des
// consolidations, celui des versets prioritaires, et l'ouverture du sélecteur de cycle. Ce sont
// des états d'interface : ils ne changent rien à ce que l'application croit appris.
// ---------------------------------------------------------------------------

/**
 * Ce que le tableau de bord affiche.
 *
 * Les cartes `day`, `cycle` et `tracking` sont **toujours** présentes une fois chargé, y compris
 * quand le plan est vide : le client d'origine les affiche avec leurs zéros et leurs absences
 * plutôt que de les retirer, et une carte qui disparaît ferait croire à un écran cassé. Elles
 * sont donc non nulles, et leurs champs portent les libellés de repli.
 */
@Immutable
data class ReviewDashboardUiState(
    /** Vrai tant que le premier état n'a pas été lu. */
    val loading: Boolean = true,

    /**
     * Message d'échec, ou `null`.
     *
     * Renseigné quand le référentiel coranique n'a pas pu être chargé : `Quran.verseAt` lève, donc
     * aucune référence n'est calculable. Un écran vide laisserait croire à un plan inexistant
     * plutôt qu'à une panne.
     */
    val failure: String? = null,

    /**
     * Révisions interrompues à reprendre.
     *
     * Le complément exact des reprises du programme : ici `mode == REVISION`, là-bas
     * `mode == LEARNING`. Un même enregistrement ne peut pas être les deux.
     */
    val resumes: List<ResumeLine> = emptyList(),

    /** Les trois colonnes du résumé : cycle du jour, consolidation, à retravailler. */
    val summary: List<SummaryColumn> = emptyList(),

    /**
     * Le compte en tête de la carte « À retravailler » : « 3 versets à retravailler aujourd'hui ».
     *
     * Il est calculé par le renderer, et non recomposé par l'écran à partir de [summary] : le mot
     * est une phrase, pas une concaténation, et l'écrire dans le composable la rendrait
     * inéprouvable alors que c'est elle qui annonce le travail du jour.
     *
     * C'est le compte des versets **dus aujourd'hui**, et non celui de la liste : la carte montre
     * tous les versets marqués difficiles, mais n'annonce que le travail du jour.
     */
    val priorityHeadline: String = "",

    /** Carte « Ma révision du jour ». */
    val day: DayCard = DayCard(),

    /** Carte « Mon cycle de révision ». */
    val cycle: CycleCard = CycleCard(),

    /**
     * Carte « Nouveaux versets à consolider », **au complet**.
     *
     * La liste n'est pas tronquée ici : le client d'origine coupe à l'affichage
     * (`slice(0, 5)`), et c'est l'écran qui replie. Tronquer dans le calcul perdrait les lignes
     * que le bouton « Voir toutes les consolidations » doit justement révéler.
     */
    val consolidations: List<ConsolidationLine> = emptyList(),

    /** Vrai quand la carte des consolidations en cache au-delà des cinq premières. */
    val consolidationOverflow: Boolean = false,

    /**
     * Carte « À retravailler », **au complet**.
     *
     * Même règle que [consolidations] : le repli est une décision d'affichage, pas de calcul.
     */
    val priorities: List<PriorityLine> = emptyList(),

    /** Vrai quand la carte des priorités en cache au-delà des cinq premières. */
    val priorityOverflow: Boolean = false,

    /** Carte « Mon suivi ». */
    val tracking: TrackingCard = TrackingCard(),
)

/**
 * Une colonne du résumé.
 *
 * @param kind la colonne, qui porte son libellé et décide de l'icône. Un entier de position ne
 *   suffirait pas : réordonner le résumé afficherait alors les icônes du mauvais côté.
 * @param quantity le volume, déjà mis en forme — « 1 Hizb », « 5 versets », « 0 verset ».
 * @param reference la référence du premier verset, ou « Rien à revoir ».
 */
@Immutable
data class SummaryColumn(
    val kind: ReviewText.SummaryKind,
    val quantity: String,
    val reference: String,
)

/**
 * La carte « Ma révision du jour ».
 *
 * @param hasSession vrai si la séance du jour porte au moins une tâche. C'est ce qui **active**
 *   le bouton, et ce qui choisit le pied de carte.
 * @param start la tâche que le bouton doit ouvrir, ou `null` quand il n'y a rien à ouvrir. Nulle
 *   et non vide : un bouton désactivé qui porterait quand même une requête pourrait être activé
 *   par erreur, et le lecteur servirait alors une tâche que le plan n'a pas prévue.
 */
@Immutable
data class DayCard(
    val hasSession: Boolean = false,
    val footer: String = ReviewText.DAY_FOOTER_EMPTY,
    val start: StudySession.Request? = null,
)

/**
 * La carte « Mon cycle de révision ».
 *
 * @param mode le mode du réglage — `"quantity"` ou autre —, pour que le sélecteur sache lequel
 *   des deux afficher.
 * @param quantity la quantité quotidienne retenue, ou `null`. Sert à marquer le bouton choisi.
 * @param lengthDays la durée retenue, ou la durée par défaut quand aucun cycle n'existe. Sert à
 *   la fois au compteur et au bouton choisi.
 * @param ratio la part du corpus déjà revue, entre 0 et 1, pour la barre de progression.
 */
@Immutable
data class CycleCard(
    val mode: String? = null,
    val quantity: String? = null,
    val lengthDays: Int = DEFAULT_CYCLE_DAYS,
    val modeLabel: String = "",
    val dayCounter: String = "",
    val percent: String = "",
    val ratio: Float = 0f,
    val corpusSummary: String = "",
    val stats: List<StatLine> = emptyList(),
    val rhythm: String = "",
    val footnote: String = ReviewText.CYCLE_FOOTNOTE,
)

/** Une des trois statistiques du cycle : une valeur et son mot. */
@Immutable
data class StatLine(val value: String, val label: String)

/**
 * Une ligne de consolidation.
 *
 * @param label le libellé lu par les lecteurs d'écran — le bouton n'a pas de texte visible, et
 *   sans lui TalkBack annoncerait un bouton sans nom.
 * @param study la tâche que l'appui ouvre : une consolidation, sous l'identité
 *   `consolidation-<début>-<fin>`, comme le client d'origine la construit.
 */
@Immutable
data class ConsolidationLine(
    val id: String,
    val reference: String,
    val detail: String,
    val label: String,
    val steps: List<StepLine>,
    val study: StudySession.Request,
)

/**
 * Une étape de consolidation : J+1, J+3, J+7.
 *
 * @param completed vrai si l'étape est validée. Décide de l'icône **et** de la couleur du trait.
 * @param dueOrPast vrai si l'échéance est atteinte ou dépassée. Le client d'origine colorait
 *   l'icône de la même façon pour les deux cas, et c'est la même condition qui décide du trait :
 *   une étape faite est traitée comme une étape due, ce qui est voulu — les deux sont « en
 *   règle », l'une parce qu'elle est faite, l'autre parce qu'elle est à faire maintenant.
 */
@Immutable
data class StepLine(
    val offset: Int,
    val label: String,
    val status: String,
    val date: String,
    val completed: Boolean,
    val dueOrPast: Boolean,
)

/**
 * Une ligne de verset prioritaire.
 *
 * @param study la tâche que l'appui ouvre. Sa catégorie est `priority` : elle ne décide donc pas
 *   d'une consolidation, contrairement à une ligne de consolidation.
 */
@Immutable
data class PriorityLine(
    val id: String,
    val reference: String,
    val detail: String,
    val study: StudySession.Request,
)

/**
 * La carte « Mon suivi ».
 *
 * Le résumé du corpus y est le **même mot** que sur la carte du cycle : le client d'origine
 * l'écrit deux fois, à l'identique. Il n'est écrit qu'une fois ici, et les deux cartes le
 * reçoivent.
 */
@Immutable
data class TrackingCard(
    val corpusSummary: String = "",
    val revisions: String = "",
)

/**
 * Durée de cycle retenue quand aucun cycle n'existe encore.
 *
 * C'est le `cycle?.lengthDays ?? 7` du client d'origine, écrit une fois. La valeur est ici, et non
 * dans [ReviewText], parce que c'est un **défaut de donnée** et non un mot : le libellé qui
 * l'affiche, lui, reste dans `ReviewText`.
 */
internal const val DEFAULT_CYCLE_DAYS: Int = 7
