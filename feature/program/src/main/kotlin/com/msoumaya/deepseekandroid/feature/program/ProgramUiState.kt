package com.msoumaya.deepseekandroid.feature.program

import androidx.compose.runtime.Immutable
import com.msoumaya.deepseekandroid.core.domain.StudySession

// ---------------------------------------------------------------------------
// État affichable du programme
// ---------------------------------------------------------------------------
// Les valeurs sont calculées **une fois**, dans le `ViewModel`, et non dans les composables :
// `Program.progress`, `Review.reviewPlan` et `WeeklyProgress.weeklyProgress` parcourent les
// séances, les connaissances et les validations. Les appeler depuis le corps d'un composable
// les rejouerait à chaque recomposition — c'est-à-dire à chaque frappe, à chaque animation, à
// chaque changement de thème.
//
// `@Immutable` est une promesse tenue : toutes les propriétés sont `val` et de type stable.
// Compose peut donc sauter la recomposition d'un composant dont l'état n'a pas changé.
//
// **Tout ce qui se lit est déjà résolu.** Les libellés de repli, les dates en toutes lettres,
// les comptes de versets et les statuts d'une ligne de détail sont des chaînes, pas des `null`
// ni des nombres à mettre en forme. C'est ce qui rend les règles du programme éprouvables sans
// appareil : « Aucune séance » ou « Prochaine séance prévue : mardi 10 mars » se vérifient en
// quelques millisecondes, alors qu'un libellé écrit dans un composable demanderait un test
// d'interface.
//
// **Ce qui n'est pas résolu, et pourquoi.** Chaque tâche à ouvrir porte un
// `StudySession.Request` — la requête que le lecteur doit servir — et non un simple numéro de
// verset. Ce n'est pas un excès de zèle : sans identifiant de tâche, le lecteur ne sait pas
// quelle progression écrire, donc il ne peut rien valider. Un écran qui n'enverrait qu'un
// verset afficherait donc des cartes qui ne mènent nulle part.
//
// La **seule** chose qui reste dans le composable est le dépliage du détail d'une reprise et
// celui de l'historique : ce sont des états d'interface, pas des décisions. Ils ne changent rien
// à ce que l'application croit appris.
// ---------------------------------------------------------------------------

/**
 * Période d'affichage de la liste « À venir ».
 *
 * Les trois libellés sont ceux du sélecteur du dépôt d'origine. Ils ne sont **pas** partagés
 * avec l'écran Progrès, qui offre les mêmes trois mots pour une autre question : là-bas, la
 * période décide de la largeur du graphique et de la fenêtre des statistiques ; ici, elle
 * filtre la liste des séances. Deux sémantiques pour trois libellés identiques : les réunir
 * ferait dépendre l'un des écrans d'un changement décidé pour l'autre.
 */
enum class ProgramPeriod(val label: String) {
    DAY("Jour"),
    WEEK("Semaine"),
    MONTH("Mois"),
    ;

    companion object {
        /** Les libellés, dans l'ordre du sélecteur. */
        val labels: List<String> = entries.map { it.label }
    }
}

/** Ce que l'écran de programme affiche. */
@Immutable
data class ProgramUiState(
    /** Vrai tant que le premier état n'a pas été lu depuis le disque. */
    val loading: Boolean = true,

    /**
     * Message d'échec, ou `null`.
     *
     * Renseigné quand le référentiel coranique n'a pas pu être chargé : dans ce cas aucune
     * règle du domaine n'est utilisable — `Quran.verseAt` lève —, et un écran vide laisserait
     * croire à un programme inexistant plutôt qu'à une panne.
     */
    val failure: String? = null,

    /**
     * Période retenue par le sélecteur « À venir ».
     *
     * Publiée avec le reste de l'état : l'écran ne la garde pas, sinon la sélection affichée et
     * la liste filtrée pourraient diverger sans que rien ne le dise.
     */
    val period: ProgramPeriod = ProgramPeriod.DAY,

    /** Carte « Aujourd'hui ». Nulle tant que rien n'est lisible. */
    val today: Today? = null,

    /** Carte « Mon objectif ». */
    val goal: GoalLine? = null,

    /** Séances interrompues à reprendre, dans l'ordre de l'état. */
    val resumes: List<ResumeLine> = emptyList(),

    /** Bandeau « Objectif de la semaine ». */
    val week: WeekLine? = null,

    /** Séances en retard, les dix premières seulement. */
    val catchUp: List<CatchUpLine> = emptyList(),

    /** Séances à venir, filtrées par la période choisie. */
    val upcoming: List<UpcomingLine> = emptyList(),

    /** Séances terminées ou reportées, les vingt dernières, de la plus récente à la plus ancienne. */
    val history: List<HistoryLine> = emptyList(),
)

/**
 * La carte « Aujourd'hui ».
 *
 * @param dateLabel le jour en toutes lettres — « mardi 10 mars 2026 ».
 * @param learning la tâche d'apprentissage. Jamais nulle : l'absence de séance est un état
 *   affiché (« Aucune séance »), pas une carte absente.
 * @param revision la tâche de révision, ou `null` quand les révisions sont **désactivées**.
 *   `null` et non une tâche vide : le client d'origine n'affiche pas la carte dans ce cas, alors
 *   qu'il affiche bien « Aucune séance » pour l'apprentissage. Deux absences différentes ne
 *   doivent pas se peindre pareil — c'est la seule carte de l'écran dont l'absence soit un état.
 * @param nextSession ligne « Prochaine séance prévue : … », ou `null` quand la séance retenue
 *   est bien celle du jour.
 */
@Immutable
data class Today(
    val dateLabel: String,
    val learning: Task,
    val revision: Task? = null,
    val nextSession: String? = null,
)

/**
 * Une des deux cartes de tâche du jour.
 *
 * **Deux formes pour la même tâche, et ce n'est pas un doublon.** Le client d'origine montre
 * chaque tâche du jour deux fois : dans la carte « Aujourd'hui », puis dans le couple
 * « Prochaine séance » / « À revoir aujourd'hui » plus bas. Les deux n'écrivent pas la même
 * chose — la carte détaille « 5 versets • Page 12 », le couple résume « 5 versets » — et leurs
 * replis diffèrent : sans séance, la carte annonce « Aucune séance » tandis que le couple annonce
 * « Objectif atteint », ce qui ne veut pas dire la même chose. Les deux formes sont donc portées
 * par la tâche, et non recalculées au jugé dans le composable — c'est ce qui permet de les
 * éprouver sans appareil.
 *
 * @param passage le passage de la carte « Aujourd'hui ».
 * @param details la précision de la carte « Aujourd'hui ».
 * @param compactPassage le passage du couple du bas.
 * @param compactDetails la précision du couple du bas.
 * @param study la tâche que le lecteur doit servir. Elle n'est pas décorative : sans identifiant
 *   de tâche, le lecteur ne sait pas quelle progression écrire, donc il ne peut rien valider — et
 *   le bandeau qui l'annonce n'existe pas. Pour l'apprentissage c'est la séance du programme ;
 *   pour une révision c'est la tâche du plan, avec sa catégorie, qui décide de la consolidation.
 * @param verseId le repli quand il n'y a pas de tâche à servir : le verset sur lequel ouvrir le
 *   lecteur en lecture libre.
 */
@Immutable
data class Task(
    val passage: String,
    val details: String,
    val compactPassage: String,
    val compactDetails: String,
    val study: StudySession.Request? = null,
    val verseId: Int? = null,
)

/** La carte « Mon objectif ». */
@Immutable
data class GoalLine(
    /** Libellé de l'objectif, tel qu'il a été choisi. */
    val label: String,
    /** Rythme affiché, déjà mis en forme : « 3 versets / jour ». */
    val pace: String,
    /** Part de l'objectif déjà connue, entre 0 et 1. */
    val ratio: Float,
) {
    /** Objectif atteint, en pourcentage arrondi. */
    val percent: Int get() = Math.round(ratio * 100f)
}

/**
 * Une séance interrompue, proposée à la reprise.
 *
 * Tout est déjà écrit : le titre, la localisation du reste, le compte restant, la ligne de
 * progression et la validation déjà effectuée. Le composable ne fait que les poser.
 *
 * @param study la tâche à rouvrir, sur le **reste** de son parcours — et non sur son début, qui
 *   ferait relire ce qui est déjà validé. Non nulle : une reprise que le lecteur ne saurait pas
 *   servir serait un bouton mort, et c'est le seul bouton de la carte. Le renderer refuse donc
 *   les reprises qu'il ne peut pas servir, au lieu d'en produire une à moitié.
 */
@Immutable
data class ResumeLine(
    val id: String,
    val title: String,
    val where: String,
    val remaining: String,
    val progress: String,
    val ratio: Float,
    val done: String,
    val study: StudySession.Request,
    /** Ligne « 6 versets au total · Juz 30 ». */
    val total: String,
    val rows: List<ResumeRow> = emptyList(),
)

/**
 * Une ligne du détail d'une reprise : une page, ou un verset.
 *
 * @param state l'état de la ligne, qui décide de l'icône. Trois états et non deux : une ligne
 *   **entamée** n'est ni faite ni à faire, et la peindre comme l'une des deux ferait mentir le
 *   décompte des pages apprises, qui compte les pages terminées.
 * @param status le mot qui accompagne l'icône, déjà résolu — « Appris », « Révisée »,
 *   « À continuer », « À apprendre », « À réviser ». Il dépend du mode, et non de l'état seul.
 */
@Immutable
data class ResumeRow(
    val id: Int,
    val label: String,
    val state: RowState,
    val status: String,
)

/** État d'une ligne du détail d'une reprise. */
enum class RowState {
    /** La ligne est entièrement faite. */
    DONE,

    /** La ligne est entamée sans être finie. */
    PARTIAL,

    /** La ligne n'est pas commencée. */
    TODO,
}

/** Le bandeau « Objectif de la semaine ». */
@Immutable
data class WeekLine(
    /** Part des séances de la semaine déjà faites, entre 0 et 1. */
    val ratio: Float,
) {
    /** Objectif de la semaine, en pourcentage arrondi. */
    val percent: Int get() = Math.round(ratio * 100f)
}

/**
 * Une séance en retard, proposée au rattrapage.
 *
 * @param label libellé du bouton, déjà mis en forme : « 2026-03-08 · Sourate 78 • versets 1 à 5 ».
 * @param study la séance à servir. Non nulle : un rattrapage qui n'ouvrirait pas sa séance ne
 *   servirait à rien, et la carte ne serait qu'un texte.
 */
@Immutable
data class CatchUpLine(
    val id: String,
    val label: String,
    val study: StudySession.Request,
)

/**
 * Une séance à venir.
 *
 * @param weekday l'initiale du jour en majuscules — « MAR. ».
 * @param day le quantième du mois.
 * @param whenLabel « Aujourd'hui », « Demain », ou « 12 mars ».
 * @param arabic le nom arabe de la sourate de tête.
 * @param study la séance à servir, pour la même raison que [CatchUpLine.study].
 */
@Immutable
data class UpcomingLine(
    val id: String,
    val weekday: String,
    val day: Int,
    val passage: String,
    val details: String,
    val whenLabel: String,
    val arabic: String,
    val study: StudySession.Request,
)

/** Une séance passée : terminée, ou reportée. */
@Immutable
data class HistoryLine(
    val id: String,
    /** « 2026-03-08 · Terminé » ou « 2026-03-08 · Reporté ». */
    val label: String,
    val passage: String,
)
