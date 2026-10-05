package com.msoumaya.deepseekandroid.feature.profile

import com.msoumaya.deepseekandroid.core.domain.GoalText
import com.msoumaya.deepseekandroid.core.model.Pace

// ---------------------------------------------------------------------------
// État de l'écran d'objectif
// ---------------------------------------------------------------------------
// Deux types, et non un seul : les **champs** sont ce que la personne manipule, l'**état** est ce
// que l'écran affiche. Les confondre ferait recalculer les listes à chaque frappe sans qu'on
// sache, à la lecture, ce qui est saisi et ce qui est déduit.
//
// **Les champs sont séparés de l'état persisté, et c'est nécessaire.** Le client d'origine les
// tient dans des `useState` initialisés **une fois** au montage, à partir de l'état enregistré.
// Les dériver à chaque émission de l'état écraserait la saisie en cours — et pire, la sauvegarde
// écrit un nouvel état, qui redonnerait les valeurs initiales : la personne verrait ses choix
// revenir en arrière juste après avoir appuyé sur « Enregistrer ». Le `ViewModel` les amorce donc
// une seule fois, et ne les réécrit plus.
//
// **Pourquoi les trois types d'option vivent ici, et non dans le renderer.** `GoalUnit`,
// `GoalPaceUnit` et `GoalChoice` sont ce que l'état **publie** : l'écran les reçoit et les
// affiche. Un type déclaré dans un objet `internal` ne peut pas remonter dans un état public —
// le compilateur le refuse —, et le dépôt garde ses renderers `internal` (`HomeRenderer`,
// `ProgramRenderer`, `ReviewDashboardRenderer`, `GoalRenderer` : quatre sur quatre). Les sortir
// ici est donc la seule forme qui tienne les deux bouts, et c'est déjà celle du dépôt : les types
// de `ProgramUiState` — `Today`, `Task`, `GoalLine` — sont déclarés dans le fichier de l'état.
//
// **`GoalUnit` et non `Unit`.** Le nom court masquerait `kotlin.Unit` dans tout le paquet, et le
// renderer écrit `fun unitOf(goal: Goal): GoalUnit` : avec le nom court, cette signature aurait
// l'air de rendre `kotlin.Unit`, ce qui est exactement le genre de piège qu'on ne relit pas.
// ---------------------------------------------------------------------------

/**
 * Une unité dans laquelle on désigne une division du Coran.
 *
 * @property label le mot affiché dans le sélecteur d'unité.
 */
enum class GoalUnit(val label: String) {
    /** Une sourate entière. */
    SURAH(GoalText.SURAH),

    /** Un hizb — un demi-juz’. */
    HIZB(GoalText.HIZB),

    /** Un juz’. */
    JUZ(GoalText.JUZ),
    ;

    companion object {
        /** L'unité portant ce libellé, ou `null` si aucun ne correspond. */
        fun of(label: String): GoalUnit? = entries.firstOrNull { it.label == label }
    }
}

/**
 * Une façon de compter un rythme.
 *
 * @property label le mot affiché dans le sélecteur d'unité de rythme.
 */
enum class GoalPaceUnit(val label: String) {
    /** En pages. */
    PER_PAGE(GoalText.PER_PAGE),

    /** En versets. */
    PER_VERSE(GoalText.PER_VERSE),

    /** En rub‘, nisf ou hizb. */
    PER_RUBU(GoalText.PER_RUBU),
    ;

    companion object {
        /** L'unité portant ce libellé, ou `null` si aucun ne correspond. */
        fun of(label: String): GoalPaceUnit? = entries.firstOrNull { it.label == label }
    }
}

/**
 * Une option de sélecteur.
 *
 * @param number la valeur choisie — un numéro de sourate, de hizb, de juz’ ou de verset.
 * @param label ce qui s'affiche.
 * @param end le **dernier verset** de la division, qui est ce que l'objectif retient. Vaut `0`
 *   pour une option de verset, qui ne désigne pas une division.
 */
data class GoalChoice(val number: Int, val label: String, val end: Int)

/**
 * Les champs de saisie de l'écran.
 *
 * @param knownUnit l'unité dans laquelle on déclare ce qu'on connaît.
 * @param goalUnit l'unité dans laquelle on désigne l'objectif.
 * @param surah la dernière sourate apprise, quand l'unité connue est « Sourate ».
 * @param ayah le dernier verset appris de cette sourate.
 * @param knownDivision le dernier hizb ou juz’ appris, pour les deux autres unités.
 * @param knownEdited `true` dès qu'une connaissance a été déclarée. Le client d'origine s'en sert
 *   pour taire « Aucun verset validé pour le moment. » : la phrase décrit l'état **enregistré**,
 *   et une déclaration en cours la contredirait à l'écran.
 * @param goalIndex le numéro de la division visée.
 * @param goalEdited `true` dès que l'objectif a été changé. Il fait taire le rappel « Objectif
 *   actuel : … », qui décrirait alors l'objectif qu'on est en train de quitter.
 * @param pace le rythme affiché.
 * @param paceUnit l'unité de rythme choisie — c'est elle qui décide des rythmes proposés.
 * @param deadlineOn `true` si une échéance est voulue. Distinct de [deadline] vide : « Sans date »
 *   et « une date pas encore saisie » sont deux états différents, et le second doit refuser
 *   d'enregistrer.
 * @param deadline le texte saisi, tel quel — il n'est validé qu'à l'enregistrement, comme dans le
 *   client d'origine, où la saisie libre est permise pendant qu'on tape.
 * @param error le refus à afficher, ou `null`.
 */
data class GoalFields(
    val knownUnit: GoalUnit = GoalUnit.SURAH,
    val goalUnit: GoalUnit = GoalUnit.HIZB,
    val surah: Int = 1,
    val ayah: Int = 1,
    val knownDivision: Int = 1,
    val knownEdited: Boolean = false,
    val goalIndex: Int = 1,
    val goalEdited: Boolean = false,
    val pace: Pace = Pace.VERSE3,
    val paceUnit: GoalPaceUnit = GoalPaceUnit.PER_PAGE,
    val deadlineOn: Boolean = false,
    val deadline: String = "",
    val error: String? = null,
)

/**
 * Ce que l'écran d'objectif affiche.
 *
 * @param loading `true` tant que le référentiel coranique n'est pas prêt. Aucune liste n'est alors
 *   calculable : `Quran` rend un référentiel vide, et une sourate vide à l'écran serait pire
 *   qu'un écran qui attend.
 * @param failure le message d'échec du chargement du référentiel, ou `null`.
 * @param fields les champs de saisie, tels quels.
 * @param knownChoices les options du champ de connaissance : les sourates quand l'unité est
 *   « Sourate », les hizbs ou juz’ sinon.
 * @param verseChoices les options du champ de verset, pour la sourate choisie. Vide pour les deux
 *   autres unités, qui n'ont qu'un champ.
 * @param goalChoices les options du champ d'objectif.
 * @param savedGoalLabel le libellé de l'objectif **enregistré**, pour le rappel.
 * @param nothingKnown `true` si aucun verset n'est connu et que rien n'a été déclaré : c'est le
 *   seul cas où la phrase d'absence s'affiche.
 * @param paceLabel ce que la carte du rythme annonce — « 3 versets / jour ».
 * @param preview la référence de la première séance à faire, ou « Objectif atteint ».
 */
data class GoalUiState(
    val loading: Boolean = true,
    val failure: String? = null,
    val fields: GoalFields = GoalFields(),
    val knownChoices: List<GoalChoice> = emptyList(),
    val verseChoices: List<GoalChoice> = emptyList(),
    val goalChoices: List<GoalChoice> = emptyList(),
    val savedGoalLabel: String = "",
    val nothingKnown: Boolean = false,
    val paceLabel: String = "",
    val preview: String = "",
)
