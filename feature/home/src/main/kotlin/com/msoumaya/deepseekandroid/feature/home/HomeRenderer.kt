package com.msoumaya.deepseekandroid.feature.home

import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.Program
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.Review
import com.msoumaya.deepseekandroid.core.domain.StudyProgressCalculator
import com.msoumaya.deepseekandroid.core.domain.StudySession
import com.msoumaya.deepseekandroid.core.domain.WeeklyProgress
import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.MushafSource
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.SessionStatus
import com.msoumaya.deepseekandroid.core.model.StudyMode
import com.msoumaya.deepseekandroid.core.model.effectiveStudyProgress

// ---------------------------------------------------------------------------
// Calcul de l'accueil
// ---------------------------------------------------------------------------
// Le calcul est séparé du `ViewModel` à dessein : c'est une fonction pure de `(état, jour)`, donc
// éprouvable sans coroutine, sans horloge et sans appareil. Le `ViewModel` ne fait plus que
// relier deux flux et appeler ce fichier.
//
// Cette séparation a une conséquence pratique : les règles de l'accueil — l'ordre du point de
// reprise, le calcul de la série, la période couverte par chaque bandeau — se vérifient en
// quelques millisecondes, alors qu'un calcul enfoui dans un `init` de `ViewModel` demanderait de
// piloter `Dispatchers.Main` pour être atteint.
//
// **Le référentiel coranique doit être chargé avant d'appeler [render].** Toutes les fonctions
// ci-dessous passent par `Quran`, qui rend un référentiel vide tant qu'il n'est pas initialisé.
// C'est au `ViewModel` de ne pas appeler avant que l'état ne soit `Ready`.
// ---------------------------------------------------------------------------

internal object HomeRenderer {

    /** Libellé de la carte de révision quand un passage est dû. */
    private const val REVISION_DUE = "À revoir aujourd'hui"

    /**
     * Calcule l'état affichable à partir de l'état applicatif.
     *
     * @param state état applicatif persisté.
     * @param at jour courant, au format `AAAA-MM-JJ`.
     */
    fun render(state: AppState, at: String): HomeUiState {
        val stats = Program.stats(state, at)
        val week = WeeklyProgress.weeklyProgress(state, at)
        val active = activity(state, at)
        val monday = Dates.addDays(at, -((Dates.dayOf(at) + 6) % 7))

        val todoToday = state.sessions.firstOrNull {
            WeeklyProgress.scheduledDate(it) == at && it.status == SessionStatus.TODO
        }
        val reviewTask = Review.reviewPlan(state, at).session.firstOrNull()

        val resumeId = state.lastRead?.verseId
            ?: todoToday?.start
            ?: state.goal.ranges.firstOrNull()?.start
            ?: 1
        val verse = Quran.verseAt(resumeId)
        val surah = Quran.surahs[verse.surah - 1]
        // La source **brute**, comme le client d'origine (`studyPage(id, state.reader?.mushaf
        // ?? 'coranTest')`). Replier la source ferait calculer la page annoncée avec le découpage
        // de Médine pour une source qui a le sien — et `coranTest` est le **défaut** de
        // l'application, donc le cas courant et non un cas de bord.
        val source = (state.reader?.mushaf ?: MushafSource.CORAN_TEST).persistedKey
        val page = state.lastRead?.page ?: StudyProgressCalculator.studyPage(resumeId, source)

        return HomeUiState(
            loading = false,
            firstName = state.profile?.firstName,
            resume = Resume(
                surahName = surah.name,
                surahArabic = surah.arabic,
                ayah = verse.ayah,
                page = page,
                ratio = if (surah.count > 0) (verse.ayah - 1).toFloat() / surah.count else 0f,
                verseId = resumeId,
            ),
            learning = todoToday
                ?.let {
                    DailyTask(
                        passage = Quran.reference(it.range),
                        details = countText(it.range),
                        verseId = it.start,
                        study = StudySession.forSession(it),
                    )
                }
                ?: DailyTask.EMPTY_LEARNING,
            revision = reviewTask
                ?.let { DailyTask(Quran.reference(it.range), REVISION_DUE, it.start) }
                ?: DailyTask.EMPTY_REVISION,
            week = WeekSummary(
                verses = stats.week,
                daily = (0 until WeekSummary.DAYS).map {
                    Program.stats(state, Dates.addDays(monday, it)).today
                },
                streak = active.streak,
                activeDays = (0 until WeekSummary.DAYS).map {
                    Dates.addDays(at, it - (WeekSummary.DAYS - 1)) in active.dates
                },
                goalRatio = week.ratio.toFloat(),
            ),
        )
    }

    /**
     * Jours d'activité et série en cours.
     *
     * Un jour compte dès qu'une séance y a été validée **ou** qu'une validation fine y a été
     * enregistrée. Les deux sources sont nécessaires : une séance suivie par progression fine
     * n'est pas marquée terminée avant sa dernière validation, donc ne compter que les séances
     * terminées romprait la série au milieu d'un apprentissage en cours.
     *
     * La série part d'aujourd'hui si aujourd'hui compte, sinon d'hier : une journée en cours
     * n'est pas une journée manquée, et afficher « 0 jour d'affilée » à dix heures du matin
     * serait faux pour quelqu'un dont la séance est prévue le soir.
     */
    private fun activity(state: AppState, at: String): Activity {
        val dates = buildSet {
            state.sessions
                .filter { it.status == SessionStatus.DONE }
                .forEach { add(it.completedDate ?: it.completedAt?.take(10) ?: it.date) }
            state.effectiveStudyProgress.values
                .filter { it.mode == StudyMode.LEARNING }
                .flatMap { it.validations }
                .forEach { add(it.date) }
        }

        var streak = 0
        var cursor = if (at in dates) at else Dates.addDays(at, -1)
        while (cursor in dates) {
            streak++
            cursor = Dates.addDays(cursor, -1)
        }
        return Activity(dates, streak)
    }

    private data class Activity(val dates: Set<String>, val streak: Int)

    /** Libellé du nombre de versets d'une plage, au pluriel si nécessaire. */
    internal fun countText(range: Range): String {
        val count = range.end - range.start + 1
        return "$count verset${if (range.end == range.start) "" else "s"}"
    }
}
