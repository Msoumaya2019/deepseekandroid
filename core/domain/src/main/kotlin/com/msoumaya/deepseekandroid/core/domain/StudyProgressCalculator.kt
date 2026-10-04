package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.MushafSource
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.ReviewCategory
import com.msoumaya.deepseekandroid.core.model.ReviewGrade
import com.msoumaya.deepseekandroid.core.model.StudyMode
import com.msoumaya.deepseekandroid.core.model.StudyProgress
import com.msoumaya.deepseekandroid.core.model.StudyStatus
import com.msoumaya.deepseekandroid.core.model.StudyValidation
import com.msoumaya.deepseekandroid.core.model.effectiveMemorizedAt
import com.msoumaya.deepseekandroid.core.model.effectiveStudyProgress

/**
 * Progression fine d'une séance.
 *
 * Porté depuis `src/core/studyProgress.ts`. Cette couche existe parce qu'une séance peut être
 * interrompue au milieu : `through` mémorise le dernier verset réellement validé, ce qui
 * permet de reprendre exactement là où on s'est arrêté, sans recompter ni sauter de verset.
 *
 * Deux garde-fous importants :
 *  - **un point d'arrêt explicite est obligatoire** : une valeur invalide ou périmée laisse
 *    l'état strictement inchangé. Aucune validation implicite, aucun avancement silencieux ;
 *  - **une page partiellement faite n'est jamais comptée comme complète**.
 */
object StudyProgressCalculator {

    fun studyKey(mode: StudyMode, id: String): String = "${mode.name.lowercase()}:$id"

    /** Page d'un verset pour la source courante. */
    fun studyPage(id: Int, source: String): Int = when {
        source == "coran_1441" -> ZipQuranSource.versePage(id)
        else -> Quran.pageOf(id)
    }

    fun studyPageRange(page: Int, source: String): Range = when {
        source == "coran_1441" -> ZipQuranSource.pageRange(page)
        else -> Quran.pageRange(page)
    }

    fun studyLastPage(id: Int, source: String): Int = when {
        source == "coran_1441" -> ZipQuranSource.versePages(id).lastOrNull() ?: ZipQuranSource.versePage(id)
        else -> Quran.pageOf(id)
    }

    /** Dernier verset de [range] appartenant à [page], borné par la séance. */
    fun studyEndpointForPage(page: Int, range: Range, source: String): Int {
        var id = minOf(range.end, studyPageRange(page, source).end)
        while (id >= range.start && studyLastPage(id, source) > page) id--
        return id
    }

    data class Metrics(
        val first: Int,
        val last: Int,
        val pages: Boolean,
        val pageList: List<Int>,
        val total: Int,
        val done: Int,
        val remaining: Int,
        val ratio: Double,
        val unit: String,
        val label: String,
    )

    fun studyMetrics(range: Range, through: Int, source: String): Metrics {
        val first = studyPage(range.start, source)
        val last = studyLastPage(range.end, source)
        val pages = last > first
        val pageList = (first..last).toList()
        val total = if (pages) pageList.size else range.end - range.start + 1
        val done: Int
        val ratio: Double
        if (pages) {
            done = pageList.count { minOf(range.end, studyPageRange(it, source).end) <= through }
            ratio = pageList.sumOf { p ->
                val r = studyPageRange(p, source)
                val start = maxOf(range.start, r.start)
                val end = minOf(range.end, r.end)
                maxOf(0.0, minOf(1.0, (through - start + 1).toDouble() / (end - start + 1)))
            } / total
        } else {
            done = maxOf(0, minOf(total, through - range.start + 1))
            ratio = maxOf(0.0, minOf(1.0, (through - range.start + 1).toDouble() / (range.end - range.start + 1)))
        }
        return Metrics(
            first = first,
            last = last,
            pages = pages,
            pageList = pageList,
            total = total,
            done = done,
            remaining = total - done,
            ratio = ratio,
            unit = if (pages) "pages" else "versets",
            label = if (pages) "Pages $first à $last" else Quran.reference(range),
        )
    }

    fun studyRangeLabel(range: Range): String {
        val a = Quran.verseAt(range.start)
        val b = Quran.verseAt(range.end)
        return if (a.surah == b.surah) "${a.ayah} → ${b.ayah}" else "${a.surah}:${a.ayah} → ${b.surah}:${b.ayah}"
    }

    fun studySurahs(range: Range) = Quran.surahs.filter { it.end >= range.start && it.start <= range.end }

    fun studyVerses(range: Range, surah: Int): List<Int> {
        val s = Quran.surahs.getOrNull(surah - 1) ?: return emptyList()
        val first = maxOf(s.start, range.start)
        val last = minOf(s.end, range.end)
        return if (last < first) emptyList() else (first..last).toList()
    }

    /** Portion restante d'une tâche, ou `null` si elle est terminée. */
    fun remainingStudyRange(range: Range, record: StudyProgress?): Range? {
        val start = maxOf(range.start, (record?.through ?: (range.start - 1)) + 1)
        return if (start <= range.end) Range(start, range.end) else null
    }

    /**
     * Enregistre une progression sur une séance ou une révision.
     *
     * En apprentissage, un arrêt au milieu marque les versets parcourus comme connus et crée
     * une révision pour la portion réellement vue — jamais pour la séance entière. En
     * révision, la notation est déléguée à [Review.gradeReviewTask] pour que les règles de
     * consolidation et de difficulté restent au même endroit.
     */
    fun validateStudyProgress(
        state: AppState,
        mode: StudyMode,
        id: String,
        range: Range,
        through: Int,
        source: String,
        category: ReviewCategory = ReviewCategory.HABITUAL,
        grade: ReviewGrade = ReviewGrade.PERFECT,
        at: String = Dates.todayLocal(),
    ): AppState {
        val key = studyKey(mode, id)
        val old = state.effectiveStudyProgress[key]

        // Le point d'arrêt doit être explicite et cohérent : une entrée invalide ou périmée
        // ne fait jamais avancer la progression.
        if (through < range.start || through > range.end) return state
        if (old != null && (old.start != range.start || old.end != range.end)) return state
        val start = maxOf(range.start, (old?.through ?: (range.start - 1)) + 1)
        if (through < start) return state

        var next = state
        if (mode == StudyMode.LEARNING) {
            val session = state.sessions.firstOrNull { it.id == id } ?: return state
            if (session.start != range.start || session.end != range.end ||
                session.status == com.msoumaya.deepseekandroid.core.model.SessionStatus.DONE
            ) {
                return state
            }
            if (through == range.end) {
                next = Program.completeSession(state, id, true, at)
                if (old != null) {
                    val wholeId = "r-${range.start}-${range.end}"
                    val priorExists = state.revisions.any { it.id == wholeId }
                    val revisions = next.revisions.filter { it.id != wholeId || priorExists }.toMutableList()
                    val remainderId = "r-$start-$through"
                    if (revisions.none { it.id == remainderId }) {
                        revisions.add(
                            com.msoumaya.deepseekandroid.core.model.Revision(
                                id = remainderId, start = start, end = through,
                                due = Dates.addDays(at, 1), interval = 1, streak = 0, completedCount = 0,
                            ),
                        )
                    }
                    next = next.copy(revisions = revisions)
                }
            } else {
                val memorizedAt = state.effectiveMemorizedAt.toMutableMap()
                for (verse in start..through) {
                    val vk = verse.toString()
                    val current = state.knowledge[vk]
                    if (current != com.msoumaya.deepseekandroid.core.model.Mastery.PERFECT &&
                        current != com.msoumaya.deepseekandroid.core.model.Mastery.REVIEW &&
                        memorizedAt[vk] == null
                    ) {
                        memorizedAt[vk] = at
                    }
                }
                next = Program.markKnowledge(state.copy(memorizedAt = memorizedAt), Range(start, through), com.msoumaya.deepseekandroid.core.model.Mastery.PERFECT)
                val revisionId = "r-$start-$through"
                if (next.revisions.none { it.id == revisionId }) {
                    next = next.copy(
                        revisions = next.revisions + com.msoumaya.deepseekandroid.core.model.Revision(
                            id = revisionId, start = start, end = through,
                            due = Dates.addDays(at, 1), interval = 1, streak = 0, completedCount = 0,
                        ),
                    )
                }
            }
        } else {
            next = Review.gradeReviewTask(
                state,
                Review.ReviewTask(start = range.start, end = through, id = id, category = category),
                grade,
                at,
            )
        }

        val record = StudyProgress(
            id = id,
            mode = mode,
            start = range.start,
            end = range.end,
            through = through,
            page = studyPage(through, source),
            source = source,
            updatedAt = Dates.nowIso(),
            status = if (through == range.end) StudyStatus.COMPLETED else StudyStatus.PARTIAL,
            validations = (old?.validations ?: emptyList()) +
                StudyValidation(start = start, end = through, date = at, validatedAt = Dates.nowIso()),
            category = if (mode == StudyMode.REVISION) category else null,
        )
        return Program.touch(next.copy(studyProgress = (next.studyProgress ?: emptyMap()) + (key to record)))
    }

    /** Reprend une tâche partielle : la suite commence au verset suivant `through`. */
    fun resumeStudyTask(record: StudyProgress): Review.ReviewTask = Review.ReviewTask(
        start = record.through + 1,
        end = record.end,
        id = record.id,
        category = record.category ?: ReviewCategory.HABITUAL,
    )

    /** Source de lecture effective, utilisée pour résoudre les pages. */
    fun sourceKey(mushaf: MushafSource): String = when (mushaf) {
        MushafSource.CORAN_1441 -> "coran_1441"
        // Le moushaf Tajwid QPC n'est pas encore rendu par ce client ; la division
        // canonique en 604 pages sert de repli et la préférence est conservée telle quelle.
        MushafSource.CORAN_TEST -> "traditional"
        MushafSource.TAJWEED_PAGES -> "traditional"
        MushafSource.MEDINA -> "traditional"
        MushafSource.SIMPLIFIED -> "tajweed"
        MushafSource.LEGACY_TAWJEED_TEST_2 -> "traditional"
        MushafSource.LEGACY_TAJWEED_TEST_2 -> "traditional"
        MushafSource.LEGACY_MEDINE_TEST -> "traditional"
    }
}
