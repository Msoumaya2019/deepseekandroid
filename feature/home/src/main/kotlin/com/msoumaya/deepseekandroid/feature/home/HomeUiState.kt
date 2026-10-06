package com.msoumaya.deepseekandroid.feature.home

import androidx.compose.runtime.Immutable
import com.msoumaya.deepseekandroid.core.domain.StudySession

// ---------------------------------------------------------------------------
// État affichable de l'accueil
// ---------------------------------------------------------------------------
// Les valeurs sont calculées **une fois**, dans le `ViewModel`, et non dans les composables :
// `Program.stats` parcourt les séances et les validations, et `WeeklyProgress.weeklyProgress`
// les reparcourt. Les appeler depuis le corps d'un composable les rejouerait à chaque
// recomposition — c'est-à-dire à chaque frappe, à chaque animation, à chaque changement de
// thème.
//
// `@Immutable` est une promesse tenue : toutes les propriétés sont `val` et de type stable
// (`String`, `Int`, `Boolean`, `Float`, `List`). Compose peut donc sauter la recomposition d'un
// composant dont l'état n'a pas changé.
// ---------------------------------------------------------------------------

/** Ce que l'écran d'accueil affiche. */
@Immutable
data class HomeUiState(
    /** Vrai tant que le premier état n'a pas été lu depuis le disque. */
    val loading: Boolean = true,

    /** Prénom du profil, ou `null` si l'utilisateur n'en a pas encore saisi. */
    val firstName: String? = null,

    /** Carte « Continuer ma lecture ». */
    val resume: Resume? = null,

    /** Carte de tâche « Apprentissage ». Jamais nulle : l'absence de séance est un état affiché. */
    val learning: DailyTask = DailyTask.EMPTY_LEARNING,

    /** Carte de tâche « Révision ». Jamais nulle, même remarque. */
    val revision: DailyTask = DailyTask.EMPTY_REVISION,

    /** Bandeau « Ma semaine ». */
    val week: WeekSummary = WeekSummary(),

    /**
     * Les deux cartes de quiz.
     *
     * **Jamais nulles une fois l'état calculé**, et c'est délibéré : les deux cartes de l'original
     * ne sont conditionnées par rien, et elles mènent au Quiz même quand aucune question n'est
     * publiée — c'est une porte, pas un compteur.
     *
     * Le `null` ne décrit donc pas « pas de quiz » : il décrit « l'accueil n'a pas encore calculé
     * son contenu », c'est-à-dire les deux états d'attente et de panne, où l'écran n'affiche de
     * toute façon pas ce bloc. Le distinguer d'un état calculé évite d'inventer des cartes pour un
     * écran qui montre autre chose.
     */
    val quiz: QuizCards? = null,

    /**
     * Message d'échec, ou `null`.
     *
     * Renseigné quand le référentiel coranique n'a pas pu être chargé : dans ce cas aucune
     * règle du domaine n'est utilisable, et un écran vide laisserait croire à un programme
     * inexistant plutôt qu'à une panne.
     */
    val failure: String? = null,
)

/** Position de reprise de lecture. */
@Immutable
data class Resume(
    val surahName: String,
    val surahArabic: String,
    val ayah: Int,
    val page: Int,
    val ratio: Float,
    val verseId: Int,
)

/**
 * Une des deux cartes de tâche du jour.
 *
 * [passage] porte la référence (« Sourate 78 • versets 1 à 5 ») et [details] la précision
 * (« 5 versets »). Les deux libellés de repli sont des valeurs, pas des `null` : le dépôt
 * d'origine les écrivait en clair dans le composable, et les remonter ici les rend vérifiables.
 *
 * [verseId] porte la **cible du toucher**. Le dépôt d'origine avait deux comportements selon
 * qu'une tâche existait ou non : ouvrir le lecteur sur la plage, ou basculer vers l'écran qui
 * permet de la programmer. Un `verseId` nul exprime le second cas sans ajouter de drapeau.
 */
@Immutable
data class DailyTask(
    val passage: String,
    val details: String,
    val verseId: Int? = null,
    /**
     * La séance que le lecteur doit servir, ou `null` quand il n'y en a pas.
     *
     * C'est **elle** qui fait d'une ouverture une séance : sans identifiant de tâche, le lecteur
     * ne sait pas quelle progression écrire, donc il ne peut rien valider — et le bandeau qui
     * l'annonce n'existe pas.
     *
     * Une **révision** n'en porte pas encore : son identité de tâche — son identifiant et sa
     * catégorie — n'est transportée par aucune route. Elle s'ouvre donc en lecture libre, comme
     * avant, et le champ reste `null` plutôt que d'être rempli d'une valeur que rien ne
     * consomme. L'écran qui la portera est le tableau de bord des révisions.
     */
    val study: StudySession.Request? = null,
) {
    companion object {
        val EMPTY_LEARNING = DailyTask("Aucune séance prévue", "Programme à jour")
        val EMPTY_REVISION = DailyTask("Révisions à jour", "Aucun passage dû")
    }
}

/**
 * Bandeau « Ma semaine ».
 *
 * [daily] porte les sept comptes de la semaine en cours, du lundi au dimanche ; [activeDays]
 * les sept derniers jours jusqu'à aujourd'hui inclus. Les deux tableaux font sept entrées et
 * ne couvrent pas la même période — c'est le dépôt d'origine qui les superposait dans la même
 * carte, et les confondre inverserait les barres.
 */
@Immutable
data class WeekSummary(
    val verses: Int = 0,
    val daily: List<Int> = List(DAYS) { 0 },
    val streak: Int = 0,
    val activeDays: List<Boolean> = List(DAYS) { false },
    val goalRatio: Float = 0f,
) {
    /** Objectif de la semaine, en pourcentage arrondi. */
    val goalPercent: Int get() = Math.round(goalRatio * 100f)

    companion object {
        const val DAYS = 7
    }
}

/**
 * Les deux cartes de quiz de l'accueil : « Quiz » et « Amis ».
 *
 * **Tout est pré-résolu en texte.** L'écran ne choisit pas entre trois sous-titres, ne compte rien
 * et ne compare aucune date : c'est [HomeRenderer] qui décide, à partir de l'instantané et du jour.
 * Un choix fait dans le composable serait rejoué à chaque recomposition, et ne serait éprouvable
 * qu'avec un hôte Compose.
 *
 * [quizAlert] porte la **pastille rouge** de l'original, et c'est la seule chose qui ne soit pas du
 * texte. La source écrit `available && !done` — deux conditions distinctes, et non la négation
 * l'une de l'autre : une question peut être publiée **et** déjà répondue, auquel cas la pastille
 * disparaît, ou non publiée **et** sans réponse, auquel cas elle n'apparaît pas non plus.
 */
@Immutable
data class QuizCards(
    /** Titre de la première carte — « Quiz ». */
    val quizTitle: String,
    /** Sous-titre : l'état du jour, dit en toutes lettres. */
    val quizSub: String,
    /** Détail : « 1/1 aujourd'hui » une fois la question répondue, sinon l'invitation. */
    val quizDetail: String,
    /** Vrai quand la pastille doit signaler une question du jour non encore répondue. */
    val quizAlert: Boolean,
    /** Titre de la seconde carte — « Amis ». */
    val friendsTitle: String,
    /** Sous-titre : « Défie tes amis ». */
    val friendsSub: String,
    /** Détail : « Quiz entre amis ». */
    val friendsDetail: String,
)
