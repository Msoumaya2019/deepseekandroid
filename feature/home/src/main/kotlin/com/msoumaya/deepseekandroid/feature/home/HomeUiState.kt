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
     * Le carrousel des contenus du jour.
     *
     * `null` décrit la même chose que pour [quiz] : « l'accueil n'a pas encore calculé son
     * contenu », c'est-à-dire les deux états d'attente et de panne du référentiel coranique, où
     * l'écran n'affiche de toute façon pas ce bloc. Une fois calculé, il est **toujours** présent :
     * une base où rien n'est publié est un état **affiché** ([DailyCarousel.Empty]), pas une
     * absence de bloc — et c'est toute la différence entre « il n'y a rien » et « je n'ai pas pu
     * lire ».
     */
    val daily: DailyCarousel? = null,

    /**
     * Message d'échec, ou `null`.
     *
     * Renseigné quand le référentiel coranique n'a pas pu être chargé : dans ce cas aucune
     * règle du domaine n'est utilisable, et un écran vide laisserait croire à un programme
     * inexistant plutôt qu'à une panne.
     */
    val failure: String? = null,
)

/**
 * Le carrousel des contenus du jour.
 *
 * **Deux formes, et non une liste plus un message facultatif.** Les deux états n'ont ni le même
 * contenu ni le même titre : avec des vignettes, le titre suit celle qui est affichée ; sans
 * vignette, il est celui du repli et le bloc porte une phrase d'état. Un `List` vide accompagné
 * d'un `String?` laisserait ces deux invariants à la charge de l'appelant — et un `orEmpty()` posé
 * dans un composable est exactement l'endroit où une phrase d'état se perd sans que rien ne le
 * dise. La forme est celle de `QuranState`, pour la même raison.
 */
@Immutable
sealed interface DailyCarousel {

    /**
     * Le carrousel a des vignettes : au plus un rappel, puis au plus une invocation.
     *
     * Il n'y a **pas** de titre global : chaque [DailySlide] porte le sien, et l'en-tête suit la
     * vignette affichée. C'est le comportement de l'original, dont l'en-tête relit
     * `entries[index]?.type` à chaque défilement.
     */
    @Immutable
    data class Slides(val slides: List<DailySlide>) : DailyCarousel

    /**
     * Le carrousel n'a rien à montrer, et [message] dit pourquoi.
     *
     * [title] est le titre de repli — « Rappel du jour » —, celui que l'original affiche dans son
     * `else`, et qui coiffe donc aussi bien un rappel absent qu'un carrousel vide. La place du
     * bloc reste annoncée au lieu de disparaître.
     */
    @Immutable
    data class Empty(val title: String, val message: String) : DailyCarousel
}

/**
 * Une vignette du carrousel, **entièrement résolue en texte**.
 *
 * L'écran ne choisit aucun libellé et ne compose aucune ligne de source : c'est [HomeRenderer] qui
 * décide, à partir du contenu et de son type. Un choix fait dans le composable serait rejoué à
 * chaque recomposition — c'est-à-dire à chaque défilement du carrousel — et ne serait éprouvable
 * qu'avec un hôte Compose.
 *
 * [source] est la ligne de provenance **déjà jointe** (`source · référence`), parce que les deux
 * champs de l'original sont filtrés puis joints, et que la règle du filtre — une référence absente
 * ne laisse pas de séparateur orphelin — se mesure sans appareil.
 *
 * [label] est le libellé du lecteur d'écran. L'original le pose sur la carte entière
 * (`accessibilityLabel`), ce qui remplace les cinq textes par cette seule phrase : sans la fusion,
 * TalkBack annoncerait le titre, l'arabe, la phonétique, le français et la source d'affilée pour
 * une vignette qui s'ouvre d'un seul geste.
 */
@Immutable
data class DailySlide(
    val id: String,
    /** Titre de la vignette — « Rappel du jour » ou « Invocation du jour ». */
    val title: String,
    /** Texte arabe, ou `null` si le contenu n'en porte pas. */
    val arabic: String?,
    /** Transcription phonétique, ou `null`. */
    val phonetic: String?,
    /** Traduction française. Jamais nulle : la base l'exige non vide. */
    val french: String,
    /** Ligne de provenance, déjà jointe : `source`, ou `source · référence`. */
    val source: String,
    /** Libellé du lecteur d'écran. */
    val label: String,
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
