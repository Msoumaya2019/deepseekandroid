package com.msoumaya.deepseekandroid.feature.progress

import androidx.compose.runtime.Immutable
import com.msoumaya.deepseekandroid.core.domain.ProgressText

// ---------------------------------------------------------------------------
// État affichable de l'écran « Progrès »
// ---------------------------------------------------------------------------
// Portage de `ProgressScreen` (`src/ui/MainScreens.tsx:46`).
//
// Les valeurs sont calculées **une fois**, dans le `ViewModel`, et non dans les composables :
// `Program.progress`, `Program.stats`, `Activity.activity` et le comptage des pages mémorisées
// parcourent les séances, les connaissances, les validations et les 604 pages du moushaf. Les
// appeler depuis le corps d'un composable les rejouerait à chaque recomposition — c'est-à-dire à
// chaque animation et à chaque changement de thème.
//
// `@Immutable` est une promesse tenue : toutes les propriétés sont `val` et de type stable.
//
// **Tout ce qui se lit est déjà résolu.** Le pourcentage de l'anneau, « 3 jours d'affilée » et
// les titres qui suivent la période sont des chaînes, pas des nombres à mettre en forme. C'est ce
// qui rend les règles éprouvables sans appareil : « Versets appris ce mois » se vérifie en
// quelques millisecondes, alors qu'un libellé écrit dans un composable demanderait un test
// d'interface.
//
// **La seule chose qui reste dans le composable est l'affichage du graphique.** Le client
// d'origine le garde dans un `useState` ; ici, c'est un état d'interface — un pli, comme le
// détail d'une reprise au programme — et non une décision. Le graphique est donc **toujours
// calculé** et l'écran décide de le peindre ou non.
// ---------------------------------------------------------------------------

/** Ce que l'écran « Progrès » affiche. */
@Immutable
data class ProgressUiState(
    /** Vrai tant que le premier état n'a pas été lu depuis le disque. */
    val loading: Boolean = true,

    /**
     * Message d'échec, ou `null`.
     *
     * Renseigné quand le référentiel coranique n'a pas pu être chargé. Aucune règle du domaine
     * n'est alors utilisable — `Quran.verseAt` lève —, et un écran vide laisserait croire à une
     * progression nulle plutôt qu'à une panne. C'est le pire des deux messages possibles : la
     * personne lirait « 0 verset mémorisé » sur un travail qu'elle a fait.
     */
    val failure: String? = null,

    /** Période retenue par le sélecteur, publiée avec le reste de l'état. */
    val period: ProgressText.Period = ProgressText.Period.WEEK,

    /** Carte de l'anneau : le pourcentage du Coran mémorisé. Nulle tant que rien n'est lisible. */
    val ring: Ring? = null,

    /** Carte « Versets appris … », dont le titre suit la période. */
    val learned: Stat? = null,

    /** Carte « Régularité » : la série de jours consécutifs. */
    val regularity: Stat? = null,

    /** Carte « Pages mémorisées ». */
    val memorizedPages: Stat? = null,

    /** Carte « Révisions faites ». */
    val revisions: Stat? = null,

    /** Graphique de la période. Toujours calculé ; l'écran décide de le peindre. */
    val graph: Graph? = null,

    /** Carte « Mon objectif ». */
    val goal: GoalLine? = null,

    /**
     * Le bloc « Quiz » du bas de page, ou `null` tant que l'instantané n'a pas été lu.
     *
     * **Il est affiché même à zéro**, et c'est la différence avec [goal] : la carte d'objectif
     * n'existe que si un objectif est fixé, alors que le bloc de quiz compte des réponses dont
     * l'absence est une information. « 0 bonne réponse / 0 » se lit « tu n'as pas encore joué »,
     * ce qui est exact ; le taire ferait croire à une fonctionnalité absente.
     */
    val quiz: QuizSummary? = null,

    /** Les quatre compteurs du bas, dans l'ordre du client d'origine. */
    val counters: List<Counter> = emptyList(),
)

/**
 * Carte de l'anneau.
 *
 * @param ratio valeur de l'anneau, entre 0 et 1.
 * @param percent pourcentage déjà mis en forme — « 42 % ».
 * @param knownVerses versets mémorisés, numérateur du compteur.
 * @param totalVerses nombre de versets du Coran, dénominateur. Il vaut `Quran.verses.size` —
 *   **6 236** —, et non `Quran.totalVolume` : ce dernier est la somme des poids en lettres
 *   arabes, l'unité dans laquelle les pourcentages sont mesurés, et il vaut **320 543** sur le
 *   référentiel livré. Les confondre afficherait « / 320543 » sous un compteur de versets, ce qui
 *   ne se voit qu'à l'écran.
 */
@Immutable
data class Ring(
    val ratio: Float,
    val percent: String,
    val knownVerses: Int,
    val totalVerses: Int,
)

/**
 * Carte de statistique : un titre et une valeur déjà mis en forme.
 *
 * @param title libellé, qui peut suivre la période — « Versets appris ce mois ».
 * @param value valeur affichée, chaîne et non nombre : « 12 jours d'affilée » n'est pas un
 *   entier, et une carte de statistique affiche les deux sans les distinguer.
 */
@Immutable
data class Stat(val title: String, val value: String)

/**
 * Graphique en barres.
 *
 * @param title titre de la carte, qui ne suit pas le nom de la période — voir
 *   [ProgressText.graphTitle].
 * @param bars les barres, de la plus ancienne à la plus récente.
 */
@Immutable
data class Graph(val title: String, val bars: List<Bar>)

/**
 * Une barre du graphique.
 *
 * @param label ce qui est écrit sous la barre : « lun » pour un jour, « S1 » pour une semaine du
 *   mois.
 * @param value nombre de versets appris sur la fenêtre, écrit au-dessus de la barre.
 * @param ratio hauteur relative, entre 0 et 1, rapportée à la plus haute barre. La hauteur
 *   minimale visible est une décision d'écran : une barre vide garde un trait, sinon elle
 *   disparaîtrait et la période semblerait absente du graphique.
 */
@Immutable
data class Bar(val label: String, val value: Int, val ratio: Float)

/**
 * Carte « Mon objectif ».
 *
 * @param label l'objectif en cours, tel que la personne l'a choisi.
 * @param ratio avancement vers l'objectif, entre 0 et 1.
 * @param percent pourcentage déjà mis en forme.
 */
@Immutable
data class GoalLine(val label: String, val ratio: Float, val percent: String)

/**
 * Les quatre compteurs du bas.
 *
 * L'énumération existe pour que l'**icône** reste une décision d'écran : la correspondance vers
 * Material Icons vit dans le composable, comme celle des onglets de la barre basse. Le libellé,
 * lui, vit ici parce qu'il est du texte lu.
 */
enum class CounterKind(val label: String) {
    JUZ(ProgressText.JUZ_DONE),
    ACTIVE_DAYS(ProgressText.ACTIVE_DAYS),
    PAGES_READ(ProgressText.PAGES_READ),
    VERSES(ProgressText.VERSES_MEMORIZED),
}

/**
 * Un compteur du bas.
 *
 * @param kind lequel des quatre, donc aussi son libellé et son icône.
 * @param value la valeur, toujours entière — un nombre de Juz', de jours, de pages ou de versets.
 */
@Immutable
data class Counter(val kind: CounterKind, val value: Int)

/**
 * Le bloc « Quiz » de l'écran « Progrès ».
 *
 * **Trois lignes, toutes pré-résolues.** Le comptage vit dans `Quiz.statistics`, dans le domaine,
 * où il est éprouvé ; la mise en forme vit dans `QuizText` ; le renderer ne fait que les relier.
 * L'écran, lui, ne pose que trois `AppLabel` — c'est ce qui rend « 7 bonnes réponses / 9 »
 * vérifiable sans appareil.
 *
 * Les trois lignes sont **distinctes** et non un seul texte à sauts : l'original leur donne trois
 * tailles et deux couleurs différentes, et le titre du bloc est une `Heading` de 19. Les replier
 * en une chaîne ferait perdre cette hiérarchie, qui est ce qui rend le bloc lisible.
 *
 * @param title titre du bloc.
 * @param daily bonnes réponses et total, mis en forme.
 * @param rate taux de réussite, mis en forme.
 * @param challenges défis joués, victoires et égalités, mis en forme.
 */
@Immutable
data class QuizSummary(
    val title: String,
    val daily: String,
    val rate: String,
    val challenges: String,
)
