package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.Consolidation
import com.msoumaya.deepseekandroid.core.model.ConsolidationEvent
import com.msoumaya.deepseekandroid.core.model.DifficultyEvent
import com.msoumaya.deepseekandroid.core.model.DifficultyMarker
import com.msoumaya.deepseekandroid.core.model.DifficultyStamp
import com.msoumaya.deepseekandroid.core.model.Division
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.ReviewCategory
import com.msoumaya.deepseekandroid.core.model.ReviewCycle
import com.msoumaya.deepseekandroid.core.model.ReviewEvent
import com.msoumaya.deepseekandroid.core.model.ReviewGrade
import com.msoumaya.deepseekandroid.core.model.StudyMode
import com.msoumaya.deepseekandroid.core.model.StudyStatus
import com.msoumaya.deepseekandroid.core.model.effectiveDifficultyHistory
import com.msoumaya.deepseekandroid.core.model.effectiveDifficultyMarkers
import com.msoumaya.deepseekandroid.core.model.effectiveMemorizedAt
import com.msoumaya.deepseekandroid.core.model.effectiveReviewConsolidations
import com.msoumaya.deepseekandroid.core.model.effectiveReviewHistory
import com.msoumaya.deepseekandroid.core.model.effectiveReviewPriorityDue
import com.msoumaya.deepseekandroid.core.model.effectiveStudyProgress
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Révisions espacées, consolidation et difficulté.
 *
 * Porté depuis `src/core/review.ts`. Les règles qui structurent le module :
 *
 *  - **Un cycle est un instantané.** `corpus` et `days` sont figés à la création. Un verset
 *    nouvellement appris n'entre dans un cycle qu'au suivant, jamais au milieu.
 *  - **Au plus une part ordinaire par jour.** Un jour manqué allonge le cycle au lieu de
 *    reporter toutes les parts en retard sur la séance suivante.
 *  - **La consolidation est ancrée sur la date d'apprentissage.** J+1, J+3 et J+7 sont
 *    calculés depuis `memorizedAt` et ne bougent jamais, même validés en avance.
 *  - **Le rythme de difficulté ne s'efface pas tout seul.** Un succès ne retire pas le
 *    marqueur : seul un retrait explicite le fait.
 */
object Review {

    val consolidationOffsets = listOf(1, 3, 7)

    fun reviewsEnabled(state: AppState): Boolean = state.reviewSettings?.enabled != false

    fun reviewCycleDays(state: AppState): Int = state.reviewSettings?.cycleDays ?: 7

    private fun known(state: AppState, id: Int): Boolean = Program.isKnown(state, id)

    /** Volume d'un verset rapporté au volume de sa page. Aucun verset n'est jamais coupé. */
    private val pageVolumes = ConcurrentHashMap<Int, Int>()

    fun reviewWeight(id: Int): Double {
        val page = Quran.pageOf(id)
        val size = pageVolumes.getOrPut(page) { Quran.volume(Quran.pageRange(page)) }
        return Quran.volume(listOf(id)).toDouble() / maxOf(1, size)
    }

    // -----------------------------------------------------------------------
    // Répartition du corpus
    // -----------------------------------------------------------------------

    /**
     * Répartit un corpus sur [length] jours.
     *
     * Deux stratégies, dans cet ordre :
     *  1. **division exacte en unités coraniques** — si le corpus est composé de hizb, nisf
     *     ou rub‘ entiers et que leur nombre est un multiple de [length], chaque jour reçoit
     *     des unités entières (7 hizb sur 14 jours donnent 1 nisf par jour) ;
     *  2. sinon **équilibrage par volume** : les frontières sont posées au plus près des
     *     cibles de poids cumulé, puis décalées vers une vraie frontière de division si
     *     celle-ci reste à moins de 15 % de la part moyenne.
     *
     * Le résultat concaténé est toujours exactement le corpus : ni trou, ni doublon.
     */
    fun partitionReviewCorpus(corpus: List<Int>, length: Int): List<List<Int>> {
        if (length <= 0) return listOf(corpus.distinct().sorted())
        val ordered = corpus.distinct().sorted()
        if (ordered.isEmpty()) return List(length) { emptyList() }
        val set = ordered.toSet()

        // 1. Division exacte en unités coraniques entières.
        for (units in listOf(Quran.hizbs, Quran.halves, Quran.quarters)) {
            val complete = units.filter { Quran.full(set, it.range) }
            if (complete.size >= length && complete.size % length == 0 &&
                Quran.idsOf(complete.map { it.range }).size == ordered.size
            ) {
                val count = complete.size / length
                return List(length) { day ->
                    Quran.idsOf(complete.subList(day * count, (day + 1) * count).map { it.range })
                }
            }
        }

        // 2. Équilibrage par volume.
        val prefix = DoubleArray(ordered.size + 1)
        for (i in ordered.indices) prefix[i + 1] = prefix[i] + reviewWeight(ordered[i])
        val total = prefix[ordered.size]

        val boundaries = listOf(Quran.hizbs, Quran.halves, Quran.quarters)
            .map { units -> units.map { it.end }.toSet() }
        val boundaryIndexes = boundaries.map { ends ->
            ordered.flatMapIndexed { i, id -> if (id in ends) listOf(i + 1) else emptyList() }
        }

        val days = ArrayList<List<Int>>(length)
        var cursor = 0
        for (day in 1..length) {
            var end = cursor
            if (day == length) {
                end = ordered.size
            } else {
                val target = total * day / length
                while (end < ordered.size && prefix[end + 1] <= target) end++
                if (end < ordered.size && kotlin.math.abs(prefix[end + 1] - target) < kotlin.math.abs(prefix[end] - target)) end++
            }
            if (day < length) {
                for (indexes in boundaryIndexes) {
                    val target = total * day / length
                    val candidates = indexes.filter { it > cursor && kotlin.math.abs(prefix[it] - target) <= total / length * 0.15 }
                    if (candidates.isNotEmpty()) {
                        end = candidates.reduce { a, b ->
                            if (kotlin.math.abs(prefix[a] - target) < kotlin.math.abs(prefix[b] - target)) a else b
                        }
                        break
                    }
                }
            }
            days.add(ordered.subList(cursor, end.coerceAtMost(ordered.size)))
            cursor = end.coerceAtMost(ordered.size)
        }
        return days
    }

    /**
     * Répartition en quantités coraniques réelles.
     * Un juz’ entier peut se lire en rub‘ ou en nisf quand ces unités sont entièrement connues.
     */
    fun partitionDailyQuantity(corpus: List<Int>, quantity: String): List<List<Int>> {
        val units: List<Division> = when (quantity) {
            "nisf" -> Quran.halves
            "hizb" -> Quran.hizbs
            else -> Quran.juzs
        }
        val groups = units.map { r -> corpus.filter { it in r.start..r.end } }.filter { it.isNotEmpty() }
        if (quantity != "juz2") return groups
        val out = ArrayList<List<Int>>()
        for ((i, ids) in groups.withIndex()) {
            if (i % 2 == 1 && out.isNotEmpty()) out[out.size - 1] = out.last() + ids else out.add(ids)
        }
        return out
    }

    // -----------------------------------------------------------------------
    // Cycle
    // -----------------------------------------------------------------------

    private fun createCycle(state: AppState, at: String, index: Int): ReviewCycle {
        val startedAt = state.reviewModelStartedAt ?: at
        val memorizedAt = state.effectiveMemorizedAt
        val consolidations = state.effectiveReviewConsolidations
        val corpus = Program.memorizedIds(state)
            .filter { id ->
                val learned = memorizedAt[id.toString()] ?: return@filter true
                val established = learned < startedAt && Dates.age(learned, startedAt) >= 7
                established || consolidations[id.toString()]?.completed?.get(7) != null
            }
            .sorted()

        val quantityMode = state.reviewSettings?.mode == "quantity"
        val days = if (quantityMode) {
            partitionDailyQuantity(corpus, state.reviewSettings?.dailyQuantity ?: "hizb")
        } else {
            partitionReviewCorpus(corpus, reviewCycleDays(state))
        }
        return ReviewCycle(
            index = index,
            startDate = at,
            lengthDays = if (quantityMode) maxOf(1, days.size) else reviewCycleDays(state),
            corpus = corpus,
            days = days,
            completed = emptyList(),
            assignments = emptyMap(),
        )
    }

    fun setReviewsEnabled(state: AppState, enabled: Boolean, at: String = Dates.todayLocal()): AppState {
        if (reviewsEnabled(state) == enabled) return state
        val settings = (state.reviewSettings ?: com.msoumaya.deepseekandroid.core.model.ReviewSettings())
            .copy(enabled = enabled, cycleDays = reviewCycleDays(state), resumedAt = if (enabled) at else null)
        return Program.touch(state.copy(reviewSettings = settings))
    }

    fun setReviewCycle(state: AppState, cycleDays: Int, at: String = Dates.todayLocal()): AppState {
        if (reviewCycleDays(state) == cycleDays && state.reviewSettings?.mode != "quantity") return state
        val settings = (state.reviewSettings ?: com.msoumaya.deepseekandroid.core.model.ReviewSettings())
            .copy(enabled = reviewsEnabled(state), cycleDays = cycleDays, mode = "cycle")
        val next = state.copy(reviewSettings = settings)
        val previous = state.reviewCycle
        val history = if (previous != null) (state.reviewCycleHistory ?: emptyList()) + previous
            else state.reviewCycleHistory
        return Program.touch(
            next.copy(
                reviewCycleHistory = history,
                reviewCycle = createCycle(next, at, (state.reviewCycle?.index ?: 0) + 1),
            ),
        )
    }

    fun setReviewQuantity(state: AppState, quantity: String, at: String = Dates.todayLocal()): AppState {
        val settings = (state.reviewSettings ?: com.msoumaya.deepseekandroid.core.model.ReviewSettings())
            .copy(
                enabled = reviewsEnabled(state),
                cycleDays = reviewCycleDays(state),
                mode = "quantity",
                dailyQuantity = quantity,
            )
        val next = state.copy(reviewSettings = settings)
        val previous = state.reviewCycle
        val history = if (previous != null) (state.reviewCycleHistory ?: emptyList()) + previous
            else state.reviewCycleHistory
        return Program.touch(
            next.copy(
                reviewCycleHistory = history,
                reviewCycle = createCycle(next, at, (state.reviewCycle?.index ?: 0) + 1),
            ),
        )
    }

    /**
     * Bascule le marqueur de difficulté d'un verset.
     * Le marqueur du professeur n'est jamais touché par cette action.
     */
    fun toggleDifficulty(state: AppState, id: Int, at: String = Dates.todayLocal()): AppState {
        if (id < 1 || id > 6236) return state
        val markers = state.effectiveDifficultyMarkers.toMutableMap()
        val current = markers[id.toString()] ?: DifficultyMarker()
        val due = state.effectiveReviewPriorityDue.toMutableMap()
        val action: String
        val next: DifficultyMarker
        if (current.user != null) {
            action = "resolved"
            next = current.copy(user = null)
            due.remove(id.toString())
        } else {
            action = "marked"
            next = current.copy(user = DifficultyStamp(at))
            due[id.toString()] = at
        }
        if (next.user != null || next.admin != null) markers[id.toString()] = next else markers.remove(id.toString())
        return Program.touch(
            state.copy(
                difficultyMarkers = markers,
                reviewPriorityDue = due,
                difficultyHistory = state.effectiveDifficultyHistory +
                    DifficultyEvent(verseId = id, date = at, origin = "user", action = action),
            ),
        )
    }

    // -----------------------------------------------------------------------
    // Consolidation
    // -----------------------------------------------------------------------

    /**
     * Consolidation d'un verset, calculée ou récupérée.
     *
     * Quand aucune consolidation n'existe encore, elle est reconstruite à partir des
     * révisions réellement effectuées — jamais à partir de checkpoints supposés. Une étape
     * manquée reste manquée : elle n'est pas inventée comme faite.
     */
    fun consolidationFor(state: AppState, id: Int): Consolidation? {
        val learnedAt = state.effectiveMemorizedAt[id.toString()] ?: return null
        val stored = state.effectiveReviewConsolidations[id.toString()]
        if (stored != null && stored.learnedAt == learnedAt) {
            return if (stored.scheduledDates != null) stored else stored.copy(scheduledDates = anchoredDates(learnedAt))
        }
        val completed = LinkedHashMap<Int, String>()
        var previous = ""
        val events = state.effectiveReviewHistory
            .filter { it.start <= id && it.end >= id && it.date >= learnedAt }
            .sortedBy { it.date }
        for (offset in consolidationOffsets) {
            val event = events.firstOrNull { it.date >= Dates.addDays(learnedAt, offset) && it.date > previous } ?: break
            completed[offset] = event.date
            previous = event.date
        }
        return Consolidation(learnedAt = learnedAt, completed = completed, scheduledDates = anchoredDates(learnedAt))
    }

    private fun anchoredDates(learnedAt: String): Map<Int, String> = mapOf(
        1 to Dates.addDays(learnedAt, 1),
        3 to Dates.addDays(learnedAt, 3),
        7 to Dates.addDays(learnedAt, 7),
    )

    /**
     * Prépare le planning de révision du jour. Idempotent : deux appels le même jour donnent
     * le même état.
     */
    fun prepareReviewSchedule(state: AppState, at: String = Dates.todayLocal()): AppState {
        if (!reviewsEnabled(state)) return state
        var cycle = state.reviewCycle
        var changed = state.reviewModelStartedAt == null

        if (cycle == null || (state.reviewSettings?.mode != "quantity" && cycle.lengthDays != reviewCycleDays(state))) {
            cycle = createCycle(state, at, (cycle?.index ?: 0) + 1)
            changed = true
        }

        val completedSet = cycle.completed.toSet()
        val finished = cycle.corpus.all { it in completedSet || !known(state, it) }
        if (finished && at >= Dates.addDays(cycle.startDate, cycle.lengthDays)) {
            cycle = createCycle(state, at, cycle.index + 1)
            changed = true
        }

        if (!cycle.assignments.containsKey(at)) {
            val done = cycle.completed.toSet()
            // Une seule part d'origine par jour : un jour manqué allonge le cycle au lieu de
            // verser toutes les parts en retard dans la séance suivante.
            val index = cycle.days.indexOfFirst { day -> day.any { known(state, it) && it !in done } }
            cycle = cycle.copy(assignments = cycle.assignments + (at to index))
            changed = true
        }

        val consolidations = state.effectiveReviewConsolidations.toMutableMap()
        for (id in Program.memorizedIds(state)) {
            val c = consolidationFor(state, id)
            if (c != null && consolidations[id.toString()] != c) {
                consolidations[id.toString()] = c
                changed = true
            }
        }
        if (!changed) return state

        val previousCycle = state.reviewCycle
        val history = if (cycle.index != previousCycle?.index && previousCycle != null) {
            (state.reviewCycleHistory ?: emptyList()) + previousCycle
        } else {
            state.reviewCycleHistory
        }
        return Program.touch(
            state.copy(
                reviewModelStartedAt = state.reviewModelStartedAt ?: at,
                reviewCycleHistory = history,
                reviewCycle = cycle,
                reviewConsolidations = consolidations,
            ),
        )
    }

    /** Valide la première étape de consolidation non terminée d'un verset. */
    fun completeConsolidation(
        state: AppState,
        range: Range,
        at: String = Dates.todayLocal(),
        completedAt: String = Dates.nowIso(),
        targetOffset: Int? = null,
    ): AppState {
        val prepared = prepareReviewSchedule(state, at)
        val consolidations = prepared.effectiveReviewConsolidations.toMutableMap()
        val events = prepared.consolidationHistory?.toMutableList() ?: mutableListOf()
        for (id in range.start..range.end) {
            if (!known(prepared, id)) continue
            val c = consolidationFor(prepared, id) ?: continue
            val offset = consolidationOffsets.firstOrNull { c.completed[it] == null } ?: continue
            if (targetOffset != null && targetOffset != offset) continue
            consolidations[id.toString()] = c.copy(
                completed = c.completed + (offset to at),
                completedAt = (c.completedAt ?: emptyMap()) + (offset to completedAt),
            )
            events.add(
                ConsolidationEvent(
                    id = "$id-${c.learnedAt}-$offset",
                    verseId = id,
                    offset = offset,
                    learnedAt = c.learnedAt,
                    scheduledDate = c.scheduledDates?.get(offset) ?: Dates.addDays(c.learnedAt, offset),
                    completedAt = completedAt,
                ),
            )
        }
        return Program.touch(
            prepared.copy(reviewConsolidations = consolidations, consolidationHistory = events),
        )
    }

    // -----------------------------------------------------------------------
    // Plan du jour
    // -----------------------------------------------------------------------

    data class ReviewTask(
        val start: Int,
        val end: Int,
        val id: String,
        val category: ReviewCategory,
        val label: String = "Versets",
        val scheduledDate: String? = null,
    ) {
        val range: Range get() = Range(start, end)
    }

    data class ConsolidationStep(val offset: Int, val due: String, val completed: String?)

    data class ConsolidationRow(
        val start: Int,
        val end: Int,
        val learnedAt: String,
        val steps: List<ConsolidationStep>,
    )

    data class ReviewPlan(
        val recent: List<ReviewTask>,
        val habitual: List<ReviewTask>,
        val priority: List<ReviewTask>,
        val session: List<ReviewTask>,
        val rework: List<ReviewTask>,
        val consolidations: List<ConsolidationRow>,
        val completeJuz: Int,
        val completeRub: Int,
        val completeNisf: Int,
        val cycle: ReviewCycle?,
        val cycleDay: Int,
    )

    /** Regroupe des versets contigus d'une même sourate en une seule tâche. */
    internal fun grouped(ids: List<Int>, category: ReviewCategory): List<ReviewTask> {
        val tasks = ArrayList<ReviewTask>()
        for (id in ids.toSortedSet()) {
            val last = tasks.lastOrNull()
            if (last != null && last.end + 1 == id &&
                Quran.surahAt(last.start).number == Quran.surahAt(id).number
            ) {
                tasks[tasks.size - 1] = last.copy(end = id, id = "${category.name.lowercase()}-${last.start}-$id")
            } else {
                val key = category.name.lowercase()
                tasks.add(ReviewTask(id, id, "$key-$id-$id", category))
            }
        }
        return tasks
    }

    /**
     * Libellé d'une quantité de révision.
     * Une quantité incomplète n'est jamais nommée comme une unité complète.
     */
    fun reviewQuantity(input: List<Int>): String {
        val ids = input.distinct().sorted()
        if (ids.isEmpty()) return "0 verset"
        val set = ids.toSet()
        for ((units, label) in listOf(Quran.hizbs to "Hizb", Quran.halves to "Nisf", Quran.quarters to "Rubu’")) {
            val matched = units.filter { Quran.full(set, it.range) }
            if (matched.sumOf { it.end - it.start + 1 } == ids.size) return "${matched.size} $label"
        }
        val selectedPages = ids.map { Quran.pageOf(it) }.distinct()
        if (selectedPages.all { Quran.full(set, Quran.pageRange(it)) }) {
            return "${selectedPages.size} page" + if (selectedPages.size == 1) "" else "s"
        }
        val equivalent = ids.sumOf { reviewWeight(it) }
        if (equivalent >= 1.5) return "≈ ${Math.round(equivalent)} pages"
        return "${ids.size} verset" + if (ids.size == 1) "" else "s"
    }

    /**
     * Même libellé, à partir d'une liste de plages.
     *
     * Le nom JVM est distinct de la surcharge sur `List<Int>` : l'effacement de type rendrait
     * les deux signatures identiques à la compilation.
     */
    @JvmName("reviewQuantityForRanges")
    fun reviewQuantity(ranges: List<Range>): String = reviewQuantity(Quran.idsOf(ranges))

    /** Rythme du cycle, exprimé en unités coraniques quand c'est exact. */
    fun reviewRhythm(cycle: ReviewCycle): String {
        val set = cycle.corpus.toSet()
        for ((units, label) in listOf(Quran.hizbs to "Hizb", Quran.halves to "Nisf", Quran.quarters to "Rubu’")) {
            val complete = units.filter { Quran.full(set, it.range) }
            if (complete.isNotEmpty() &&
                complete.sumOf { it.end - it.start + 1 } == cycle.corpus.size &&
                complete.size % cycle.lengthDays == 0
            ) {
                return "${complete.size / cycle.lengthDays} $label / jour"
            }
        }
        val pages = cycle.corpus.sumOf { reviewWeight(it) } / cycle.lengthDays
        return if (pages >= 1) {
            "≈ ${formatFr(pages)} pages / jour"
        } else {
            "≈ ${formatFr(cycle.corpus.size.toDouble() / cycle.lengthDays)} versets / jour"
        }
    }

    private fun formatFr(value: Double): String =
        if (value == Math.floor(value)) Math.floor(value).toLong().toString()
        else String.format(Locale.FRANCE, "%.1f", value)

    /**
     * Plan de révision du jour.
     *
     * L'ordre de la séance est : reprises partielles, consolidations dues, versets difficiles,
     * puis la part habituelle du cycle. Chaque verset n'apparaît qu'une fois, mais une plage
     * qui relève de deux mécanismes est créditée aux deux.
     */
    fun reviewPlan(original: AppState, at: String = Dates.todayLocal()): ReviewPlan {
        val state = prepareReviewSchedule(original, at)
        val all = Program.memorizedIds(state).sorted()
        val set = all.toSet()
        val cycle = state.reviewCycle

        val completeJuz = Quran.juzs.count { Quran.full(set, it.range) }
        val completeRub = Quran.quarters.count { Quran.full(set, it.range) }
        val completeNisf = Quran.halves.count { Quran.full(set, it.range) }
        val cycleDay = if (cycle != null) minOf(cycle.lengthDays, maxOf(1, Dates.age(cycle.startDate, at) + 1)) else 0

        if (!reviewsEnabled(state)) {
            return ReviewPlan(
                recent = emptyList(), habitual = emptyList(), priority = emptyList(), session = emptyList(),
                rework = emptyList(), consolidations = emptyList(),
                completeJuz = completeJuz, completeRub = completeRub, completeNisf = completeNisf,
                cycle = cycle, cycleDay = cycleDay,
            )
        }

        val today = HashSet<Int>()
        for (e in state.effectiveReviewHistory) if (e.date == at) for (id in e.start..e.end) today.add(id)

        val recentIds = HashSet<Int>()
        val rows = ArrayList<ConsolidationRow>()
        val consolidations = state.effectiveReviewConsolidations
        val memorizedAt = state.effectiveMemorizedAt
        for (id in all) {
            val c = consolidations[id.toString()] ?: continue
            if (c.learnedAt != memorizedAt[id.toString()]) continue
            if (c.completed[7] != null) continue
            val steps = consolidationOffsets.map { offset ->
                ConsolidationStep(
                    offset = offset,
                    due = c.scheduledDates?.get(offset) ?: Dates.addDays(c.learnedAt, offset),
                    completed = c.completed[offset],
                )
            }
            val pending = steps.firstOrNull { it.completed == null }
            if (pending != null && pending.due <= at && id !in today) recentIds.add(id)

            val last = rows.lastOrNull()
            if (last != null && last.end + 1 == id &&
                Quran.surahAt(last.start).number == Quran.surahAt(id).number &&
                last.learnedAt == c.learnedAt && last.steps == steps
            ) {
                rows[rows.size - 1] = last.copy(end = id)
            } else {
                rows.add(ConsolidationRow(id, id, c.learnedAt, steps))
            }
        }

        val markers = state.effectiveDifficultyMarkers
        val priorityDue = state.effectiveReviewPriorityDue
        val priorityIds = all.filter { id ->
            val marker = markers[id.toString()]
            (marker?.user != null || marker?.admin != null) &&
                (priorityDue[id.toString()] ?: at) <= at && id !in today
        }

        val done = cycle?.completed?.toSet() ?: emptySet()
        val index = cycle?.assignments?.get(at) ?: -1
        val habitualIds = (cycle?.days?.getOrNull(index) ?: emptyList())
            .filter { known(state, it) && it !in done && it !in today }

        val recent = rows.flatMap { row ->
            val ids = (row.start..row.end).filter { it in recentIds }
            grouped(ids, ReviewCategory.RECENT).map { task ->
                task.copy(scheduledDate = row.steps.firstOrNull { it.completed == null }?.due)
            }
        }
        val priority = grouped(priorityIds, ReviewCategory.PRIORITY).map { task ->
            task.copy(scheduledDate = priorityDue[task.start.toString()] ?: at)
        }
        val habitual = grouped(habitualIds, ReviewCategory.HABITUAL).map { task ->
            task.copy(scheduledDate = if (cycle != null && index >= 0) Dates.addDays(cycle.startDate, index) else at)
        }

        val partialRecords = state.effectiveStudyProgress.values
            .filter { it.mode == StudyMode.REVISION && it.status == StudyStatus.PARTIAL }
        val partials = partialRecords.flatMap { r ->
            val ids = ((r.through + 1)..r.end).filter { known(state, it) }
            grouped(ids, r.category ?: ReviewCategory.HABITUAL).map { it.copy(id = r.id) }
        }

        val seen = HashSet<Int>()
        val session = ArrayList<ReviewTask>()
        for (task in partials + recent + priority + habitual) {
            val ids = (task.start..task.end).filter { seen.add(it) }
            for (t in grouped(ids, task.category)) {
                val isPartial = task in partials
                session.add(t.copy(scheduledDate = task.scheduledDate, id = if (isPartial) task.id else t.id))
            }
        }

        val rework = grouped(
            all.filter { id ->
                val m = markers[id.toString()]
                m?.user != null || m?.admin != null
            },
            ReviewCategory.PRIORITY,
        )

        return ReviewPlan(
            recent = recent, habitual = habitual, priority = priority, session = session, rework = rework,
            consolidations = rows, completeJuz = completeJuz, completeRub = completeRub,
            completeNisf = completeNisf, cycle = cycle, cycleDay = cycleDay,
        )
    }

    /**
     * Enregistre une révision effectuée.
     *
     * Idempotent : un verset déjà revu le jour [at] est ignoré, ce qui rend l'action sûre à
     * rejouer après une coupure réseau.
     */
    fun gradeReviewTask(
        original: AppState,
        task: ReviewTask,
        grade: ReviewGrade,
        at: String = Dates.todayLocal(),
    ): AppState {
        if (!reviewsEnabled(original)) return original
        val state = prepareReviewSchedule(original, at)

        val reviewed = HashSet<Int>()
        for (e in state.effectiveReviewHistory) if (e.date == at) for (id in e.start..e.end) reviewed.add(id)
        val ids = (task.start..task.end).filter { known(state, it) && it !in reviewed }
        if (ids.isEmpty()) return state

        val event = ReviewEvent(
            id = "$at-${task.category.name.lowercase()}-${task.start}-${task.end}-${System.currentTimeMillis()}",
            date = at,
            scheduledDate = task.scheduledDate ?: at,
            completedAt = Dates.nowIso(),
            start = task.start,
            end = task.end,
            category = task.category,
            grade = grade,
        )

        val markers = state.effectiveDifficultyMarkers.toMutableMap()
        val due = state.effectiveReviewPriorityDue.toMutableMap()
        val history = state.effectiveDifficultyHistory.toMutableList()
        val consolidations = state.effectiveReviewConsolidations.toMutableMap()
        val cycle = state.reviewCycle
        val completed = (cycle?.completed ?: emptyList()).toMutableSet()
        val assigned = (cycle?.days?.getOrNull(cycle.assignments[at] ?: -1) ?: emptyList()).toSet()

        for (id in ids) {
            // Le recouvrement est effectué une fois, mais crédité à chaque mécanisme exigible.
            if (id in assigned || (task.category == ReviewCategory.HABITUAL && cycle?.corpus?.contains(id) == true)) {
                completed.add(id)
            }
            val c = consolidations[id.toString()]
            if (c != null) {
                val offset = consolidationOffsets.firstOrNull { c.completed[it] == null }
                if (offset != null && Dates.addDays(c.learnedAt, offset) <= at) {
                    consolidations[id.toString()] = c.copy(
                        completed = c.completed + (offset to at),
                        completedAt = (c.completedAt ?: emptyMap()) + (offset to Dates.nowIso()),
                    )
                }
            }
            if (grade != ReviewGrade.PERFECT) {
                val marker = markers[id.toString()]
                if (marker?.user == null) {
                    markers[id.toString()] = (marker ?: DifficultyMarker()).copy(user = DifficultyStamp(at))
                    history.add(DifficultyEvent(id, at, "user", "marked"))
                }
                due[id.toString()] = Dates.addDays(at, if (grade == ReviewGrade.REWORK) 1 else 2)
            } else {
                val marker = markers[id.toString()]
                if (marker?.user != null || marker?.admin != null) {
                    due[id.toString()] = Dates.addDays(at, reviewCycleDays(state))
                } else {
                    due.remove(id.toString())
                }
            }
        }

        return Program.touch(
            state.copy(
                reviewCycle = cycle?.copy(completed = completed.sorted()),
                reviewConsolidations = consolidations,
                reviewPriorityDue = due,
                difficultyMarkers = markers,
                difficultyHistory = history,
                reviewHistory = state.effectiveReviewHistory + event,
            ),
        )
    }
}
