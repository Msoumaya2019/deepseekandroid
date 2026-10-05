package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.AppTheme
import com.msoumaya.deepseekandroid.core.model.Division
import com.msoumaya.deepseekandroid.core.model.Goal
import com.msoumaya.deepseekandroid.core.model.GoalPreset
import com.msoumaya.deepseekandroid.core.model.LearningDirection
import com.msoumaya.deepseekandroid.core.model.LegacyReviewGrade
import com.msoumaya.deepseekandroid.core.model.Mastery
import com.msoumaya.deepseekandroid.core.model.MushafSource
import com.msoumaya.deepseekandroid.core.model.NotificationPreferences
import com.msoumaya.deepseekandroid.core.model.Pace
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.ReaderPreferences
import com.msoumaya.deepseekandroid.core.model.ReviewSettings
import com.msoumaya.deepseekandroid.core.model.Revision
import com.msoumaya.deepseekandroid.core.model.Session
import com.msoumaya.deepseekandroid.core.model.SessionStatus
import com.msoumaya.deepseekandroid.core.model.effectiveDifficultyHistory
import com.msoumaya.deepseekandroid.core.model.effectiveDifficultyMarkers
import com.msoumaya.deepseekandroid.core.model.effectiveMemorizedAt
import com.msoumaya.deepseekandroid.core.model.effectiveReadPages
import com.msoumaya.deepseekandroid.core.model.effectiveReviewHistory
import com.msoumaya.deepseekandroid.core.model.effectiveStudyProgress
import com.msoumaya.deepseekandroid.core.model.effectiveUiFont
import com.msoumaya.deepseekandroid.core.model.effectiveAccent

/**
 * Programme d'apprentissage, maîtrise des versets, réconciliation d'état.
 *
 * Porté depuis `src/core/program.ts`. Toutes les règles métier du client d'origine sont
 * reproduites à l'identique, y compris celles qui ne se voient pas à la lecture :
 *
 *  - **`scheduledDate` ne bouge jamais.** Une séance prévue mardi et validée lundi garde
 *    `scheduledDate = mardi` et reçoit `completedDate = lundi`. La séance suivante ne prend
 *    pas la date du jour pour autant.
 *  - **Les passages connus ne sont jamais reprogrammés.** Un verset mémorisé, ou couvert par
 *    une séance faite ou partielle, est exclu du reste à planifier.
 *  - **Les recalculs conservent l'historique.** `generateProgram` ne remplace que le futur :
 *    les séances faites, reportées ou partielles sont conservées telles quelles.
 */
object Program {

    // -----------------------------------------------------------------------
    // Objectifs
    // -----------------------------------------------------------------------

    fun goalFromPreset(
        preset: GoalPreset,
        direction: LearningDirection = LearningDirection.FROM_NAS,
    ): Goal {
        val ranges: List<Range> = when (preset) {
            // Al-Fîl (105) → An-Nâs (114).
            GoalPreset.LAST_TEN -> listOf(Range(Quran.surahs[104].start, Quran.surahs[113].end))
            // Hizb Sabbih (hizb 60).
            GoalPreset.SABBIH -> listOf(Quran.hizbs[59].range)
            // Juz’ ‘Amma (juz 30).
            GoalPreset.AMMA -> listOf(Quran.juzs[29].range)
            // Yâ Sîn (36) → fin.
            GoalPreset.TO_YASIN -> listOf(Range(Quran.surahs[35].start, Quran.surahs[113].end))
            // Juz 16 → fin.
            GoalPreset.HALF -> listOf(Range(Quran.juzs[15].start, Quran.juzs[29].end))
            GoalPreset.ALL -> listOf(Range(1, 6236))
        }
        return Goal(label = Texts.goalPresetLabels.getValue(preset), ranges = ranges, direction = direction)
    }

    fun goalIsAlreadyKnown(state: AppState, preset: GoalPreset): Boolean =
        goalFromPreset(preset).ranges.all { isRangeKnown(state, it) }

    // -----------------------------------------------------------------------
    // État par défaut et migrations
    // -----------------------------------------------------------------------

    /** État initial, valeurs identiques à `defaultState()` du client d'origine. */
    fun defaultState(): AppState = AppState(
        schema = AppState.SCHEMA,
        onboardingDone = false,
        updatedAt = AppState.EPOCH,
        knowledge = emptyMap(),
        goal = Goal(label = "Juz’ ‘Amma", ranges = listOf(Range(5673, 6236))),
        pace = Pace.VERSE3,
        learningDays = listOf(1, 2, 3, 4, 5),
        sessions = emptyList(),
        revisions = emptyList(),
        memorizedAt = emptyMap(),
        reviewSettings = ReviewSettings(enabled = true, cycleDays = 7),
        reviewHistory = emptyList(),
        reviewDue = emptyMap(),
        difficultyMarkers = emptyMap(),
        difficultyHistory = emptyList(),
        reviewCycle = null,
        reviewConsolidations = emptyMap(),
        reviewPriorityDue = emptyMap(),
        reviewCycleHistory = emptyList(),
        consolidationHistory = emptyList(),
        studyProgress = emptyMap(),
        theme = AppTheme.WHITE,
        notifications = NotificationPreferences(messages = true, learning = false),
        reader = ReaderPreferences(mushaf = MushafSource.CORAN_TEST, followAudio = true),
    )

    /**
     * Migration idempotente des préférences de lecture.
     *
     *  - les identifiants de source hérités (`tawjeed_test_2`, `tajweed_test_2`, `medine_test`)
     *    deviennent `coran_1441` ;
     *  - toute séance sans `scheduledDate` reçoit `date`, pour que le calcul des dates
     *    planifiées ne dépende jamais d'un repli implicite ;
     *  - l'ancien « Moushaf Tajweed » par images devient `coranTest`, la source recommandée.
     *
     * La clé de source `tajweed` (Lecture simplifiée) est conservée telle quelle : c'est un
     * réglage utilisateur, pas un identifiant technique.
     */
    fun migrateReaderState(state: AppState): AppState {
        var s = state
        val mushaf = s.reader?.mushaf
        if (mushaf != null && mushaf.isLegacy) {
            s = s.copy(reader = s.reader!!.copy(mushaf = MushafSource.CORAN_1441))
        }
        if (s.sessions.any { it.scheduledDate == null }) {
            s = s.copy(sessions = s.sessions.map { if (it.scheduledDate != null) it else it.copy(scheduledDate = it.date) })
        }
        val current = s.reader?.mushaf
        if (current != null && current != MushafSource.TAJWEED_PAGES) return s
        return adoptComposedSource(s)
    }

    /**
     * L'état après **adoption de la source composée** — le moushaf de Tajwid.
     *
     * Le client d'origine écrit ce même objet à **trois** endroits, sous trois formes qui ne
     * diffèrent que par leur précondition : la migration (quand la source est l'ancien
     * « Moushaf Tajweed » par images), la fermeture du lecteur, et la carte « Coran avec règles
     * de Tajwid » de l'écran Coran. Le corps, lui, est identique dans les trois :
     *
     * ```
     * {...state.reader, mushaf:'coranTest', followAudio: state.reader?.followAudio !== false}
     * ```
     *
     * Il est donc nommé **une fois**, ici : trois copies d'une même écriture finiraient par
     * décrire la même chose de trois façons, et c'est la copie qu'on ne relit pas qui resterait.
     * Chaque appelant garde sa précondition, qui est sa décision propre.
     *
     * `followAudio` est **conservé**, et le `!= false` n'est pas une coquetterie : il rend `true`
     * sur un état qui n'a pas encore de préférences de lecture. Le remplacer par `== true`
     * changerait le comportement d'un état vierge — l'audio cesserait d'y suivre la lecture.
     *
     * @param testPage la page à mémoriser pour cette source, ou `null` pour **la laisser telle
     *   quelle**. Les deux appelants en ont besoin différemment : fermer le lecteur passe la page
     *   affichée, parce que c'est le geste qui mémorise où l'on s'est arrêté ; la carte de
     *   l'écran Coran n'en passe aucune, parce qu'elle ne fait qu'**ouvrir** — inventer une page
     *   à cet instant écraserait la position mémorisée par celle qu'on s'apprête à lire.
     */
    fun adoptComposedSource(state: AppState, testPage: Int? = null): AppState = state.copy(
        reader = (state.reader ?: ReaderPreferences()).copy(
            mushaf = MushafSource.CORAN_TEST,
            followAudio = state.reader?.followAudio != false,
            testPage = testPage ?: state.reader?.testPage,
        ),
    )

    /** Marque l'état comme modifié maintenant, après migration. */
    fun touch(state: AppState): AppState = migrateReaderState(state).copy(updatedAt = Dates.nowIso())

    // -----------------------------------------------------------------------
    // Maîtrise
    // -----------------------------------------------------------------------

    private fun masteryOf(state: AppState, id: Int): Mastery? = state.knowledge[id.toString()]

    fun isKnown(state: AppState, id: Int): Boolean = masteryOf(state, id)?.isKnown == true

    /**
     * Applique un niveau de maîtrise à une plage.
     *
     * Deux effets de bord volontaires :
     *  - passer un verset en « connu » lui attribue une date de mémorisation, mais seulement
     *    si l'inscription est terminée et qu'il n'en avait pas : c'est cette date qui ancre
     *    les consolidations J+1/J+3/J+7 ;
     *  - repasser un verset en « en cours d'apprentissage » efface cette date et l'échéance
     *    de révision, sans toucher aux compteurs de révision déjà effectuées.
     */
    fun markKnowledge(state: AppState, range: Range, mastery: Mastery): AppState {
        val knowledge = state.knowledge.toMutableMap()
        val memorizedAt = state.effectiveMemorizedAt.toMutableMap()
        val reviewDue = (state.reviewDue ?: emptyMap()).toMutableMap()
        for (id in range.start..range.end) {
            val key = id.toString()
            val current = knowledge[key]
            if (mastery != Mastery.LEARNING && state.onboardingDone &&
                current != Mastery.PERFECT && current != Mastery.REVIEW && memorizedAt[key] == null
            ) {
                memorizedAt[key] = Dates.todayLocal()
            }
            knowledge[key] = mastery
            if (mastery == Mastery.LEARNING) {
                memorizedAt.remove(key)
                reviewDue.remove(key)
            }
        }
        return touch(
            state.copy(
                knowledge = knowledge,
                memorizedAt = memorizedAt,
                reviewDue = reviewDue,
            ),
        )
    }

    fun isRangeKnown(state: AppState, range: Range): Boolean {
        for (id in range.start..range.end) if (!isKnown(state, id)) return false
        return true
    }

    fun toggleKnownRange(state: AppState, range: Range): AppState =
        markKnowledge(state, range, if (isRangeKnown(state, range)) Mastery.LEARNING else Mastery.PERFECT)

    /** Passages connus contigus, dans une même sourate, hors sourates entières. */
    fun partialKnownRanges(state: AppState): List<Range> {
        val ids = memorizedIds(state).sorted()
        val ranges = ArrayList<Range>()
        for (id in ids) {
            val previous = ranges.lastOrNull()
            if (previous != null && id == previous.end + 1 &&
                Quran.surahAt(id).number == Quran.surahAt(previous.start).number
            ) {
                ranges[ranges.size - 1] = previous.copy(end = id)
            } else {
                ranges.add(Range(id, id))
            }
        }
        return ranges.filter { range ->
            val surah = Quran.surahAt(range.start)
            range.start != surah.start || range.end != surah.end
        }
    }

    // -----------------------------------------------------------------------
    // Ensembles de versets
    // -----------------------------------------------------------------------

    fun goalIds(state: AppState): List<Int> = Quran.expand(state.goal.ranges)

    /**
     * Ordre de parcours de l'objectif.
     *
     * En mode `fromNas`, les sourates sont parcourues de la dernière à la première, en
     * gardant les versets de chaque sourate dans leur ordre canonique. C'est le parcours
     * choisi par défaut : on commence par les sourates les plus courtes et les plus connues.
     */
    fun learningOrderIds(state: AppState): List<Int> {
        val ids = goalIds(state)
        return if (state.goal.direction == LearningDirection.FROM_NAS) {
            ids.sortedWith(compareByDescending<Int> { Quran.surahAt(it).number }.thenBy { it })
        } else {
            ids
        }
    }

    fun memorizedIds(state: AppState): List<Int> =
        state.knowledge.entries
            .filter { it.value.isKnown }
            .mapNotNull { it.key.toIntOrNull() }

    data class Progress(val quran: Double, val goal: Double, val goalKnown: Int, val goalTotal: Int)

    fun progress(state: AppState): Progress {
        val known = memorizedIds(state)
        val knownSet = known.toSet()
        val target = goalIds(state)
        val goalTotal = Quran.volume(target)
        val goalKnown = Quran.volume(target.filter { it in knownSet })
        return Progress(
            quran = if (Quran.totalVolume != 0) Quran.volume(known).toDouble() / Quran.totalVolume else 0.0,
            goal = if (goalTotal != 0) goalKnown.toDouble() / goalTotal else 0.0,
            goalKnown = goalKnown,
            goalTotal = goalTotal,
        )
    }

    /**
     * Un objectif est valide s'il contient au moins un hizb entier, ou à défaut un volume
     * équivalent au soixantième du Coran. Cela empêche de valider un objectif d'un seul verset.
     */
    fun validGoal(ranges: List<Range>): Boolean {
        val ids = Quran.expand(ranges)
        val selected = ids.toSet()
        if (Quran.hizbs.any { Quran.full(selected, it.range) }) return true
        return Quran.volume(ids) >= Quran.totalVolume / 60
    }

    // -----------------------------------------------------------------------
    // Découpage des séances
    // -----------------------------------------------------------------------

    private fun takePrefix(ids: List<Int>, includes: (Int) -> Boolean): List<Int> {
        val out = ArrayList<Int>()
        for (id in ids) {
            if (!includes(id)) break
            out.add(id)
        }
        return out
    }

    /**
     * Découpe le prochain groupe de versets selon le rythme.
     *
     * [takePrefix] s'arrête au premier verset hors critère : un trou n'est jamais sauté, sans
     * quoi la séance pourrait mémoriser un verset isolé loin du reste.
     */
    internal fun nextChunk(remaining: List<Int>, pace: Pace): List<Int> {
        if (remaining.isEmpty()) return emptyList()
        pace.verseCount?.let { return remaining.take(it) }
        val first = remaining[0]
        if (pace == Pace.PAGE2) {
            val selectedPages = mutableSetOf<Int>()
            val out = ArrayList<Int>()
            for (id in remaining) {
                val page = Quran.pageOf(id)
                if (page !in selectedPages && selectedPages.size == 2) break
                selectedPages.add(page)
                out.add(id)
            }
            return out
        }
        if (pace == Pace.HALF_PAGE || pace == Pace.PAGE) {
            val pr = Quran.pageRange(Quran.pageOf(first))
            val within = takePrefix(remaining) { it in pr.start..pr.end }
            if (pace == Pace.PAGE) return within
            val target = Quran.volume(pr) / 2
            var sum = 0
            val out = ArrayList<Int>()
            for (id in within) {
                out.add(id)
                sum += Quran.weights[id - 1]
                if (sum >= target) break
            }
            return out
        }
        if (pace == Pace.TOUMOUN && Toumoun.verifiedToumouns == null) {
            throw IllegalStateException("Les limites Hafs des toumoun ne sont pas vérifiées.")
        }
        val divisions: List<Division> = when (pace) {
            Pace.TOUMOUN -> Toumoun.verifiedToumouns!!.mapIndexed { i, r -> Division(i + 1, r.start, r.end) }
            Pace.QUARTER -> Quran.quarters
            Pace.HALF_HIZB -> Quran.halves
            else -> Quran.hizbs
        }
        val boundary = Quran.divisionContaining(divisions, first)
            ?: throw IllegalStateException("Aucune division ne contient le verset $first.")
        return takePrefix(remaining) { it in boundary.start..boundary.end }
    }

    /** Découpe une suite de versets en plages contiguës, sans franchir une sourate. */
    internal fun splitContiguous(ids: List<Int>): List<Range> {
        val result = ArrayList<Range>()
        for (id in ids) {
            val last = result.lastOrNull()
            if (last != null && id == last.end + 1 &&
                Quran.surahAt(id).number == Quran.surahAt(last.start).number
            ) {
                result[result.size - 1] = last.copy(end = id)
            } else {
                result.add(Range(id, id))
            }
        }
        return result
    }

    // -----------------------------------------------------------------------
    // Génération du programme
    // -----------------------------------------------------------------------

    /**
     * Recalcule le programme à partir de [from], en conservant tout l'historique.
     *
     * Sont conservées telles quelles : les séances faites, les séances partielles (qui
     * restent à reprendre) et les séances reportées. Sont supprimées : les séances `todo`
     * non partielles à venir, qui sont régénérées. Une séance `todo` non partielle devenue
     * passée est marquée `postponed` — elle reste due, mais elle ne bloque pas le planning.
     */
    fun generateProgram(state: AppState, from: String = Dates.todayLocal(), days: Int = 20000): AppState {
        val partial: (Session) -> Boolean = { s ->
            state.studyProgress?.get("learning:${s.id}")?.status == com.msoumaya.deepseekandroid.core.model.StudyStatus.PARTIAL
        }
        val old = state.sessions
            .filter { partial(it) || it.status != SessionStatus.TODO || it.date < from }
            .map { if (it.status == SessionStatus.TODO && !partial(it)) it.copy(status = SessionStatus.POSTPONED) else it }

        val scheduled = HashSet<Int>()
        for (s in old) {
            if (s.status == SessionStatus.DONE) for (id in s.start..s.end) scheduled.add(id)
            if (partial(s)) for (id in s.start..s.end) scheduled.add(id)
        }
        val known = memorizedIds(state).toHashSet()

        var remaining = learningOrderIds(state).filter { it !in known && it !in scheduled }
        val sessions = ArrayList<Session>()
        var serial = 0
        var offset = 0
        while (offset < days && remaining.isNotEmpty()) {
            val date = Dates.addDays(from, offset)
            offset += 1
            if (Dates.dayOf(date) !in state.learningDays) continue
            val chunk = nextChunk(remaining, state.pace)
            remaining = remaining.drop(chunk.size)
            for (range in splitContiguous(chunk)) {
                sessions.add(
                    Session(
                        id = "$date-${range.start}-${serial++}",
                        date = date,
                        scheduledDate = date,
                        start = range.start,
                        end = range.end,
                        unit = state.pace,
                        status = SessionStatus.TODO,
                    ),
                )
            }
        }
        return touch(state.copy(sessions = (old + sessions).sortedBy { it.date }))
    }

    /** Crée une révision initiale pour chaque passage connu qui n'est pas déjà couvert. */
    fun seedInitialRevisions(state: AppState, from: String = Dates.todayLocal()): AppState {
        val covered = HashSet<Int>()
        for (r in state.revisions) for (id in r.start..r.end) covered.add(id)
        val known = memorizedIds(state).filter { it !in covered }.sorted()
        val revisions = state.revisions + splitContiguous(known).map { r ->
            Revision(
                id = "r-initial-${r.start}-${r.end}",
                start = r.start,
                end = r.end,
                due = Dates.addDays(from, 1),
                interval = 1,
                streak = 0,
                completedCount = 0,
            )
        }
        return touch(state.copy(revisions = revisions))
    }

    /**
     * Report d'une séance.
     *
     * Le report ne change **que le statut**, jamais la date prévue ni les séances voisines.
     * Une séance partiellement apprise reste `todo` : elle est reprise, pas reportée.
     */
    fun postponeSession(state: AppState, id: String, from: String = Dates.todayLocal()): AppState {
        val isPartial = state.studyProgress?.get("learning:$id")?.status ==
            com.msoumaya.deepseekandroid.core.model.StudyStatus.PARTIAL
        return touch(
            state.copy(
                sessions = state.sessions.map {
                    if (it.id == id) it.copy(status = if (isPartial) SessionStatus.TODO else SessionStatus.POSTPONED) else it
                },
            ),
        )
    }

    /**
     * Validation d'une séance.
     *
     * [from] est le **jour de validation**, qui peut être antérieur ou postérieur à la date
     * prévue. Il alimente `memorizedAt` et `completedDate` ; la date prévue reste intacte.
     */
    fun completeSession(state: AppState, id: String, memorized: Boolean, from: String = Dates.todayLocal()): AppState {
        val session = state.sessions.firstOrNull { it.id == id } ?: return state
        if (!memorized) return postponeSession(state, id, from)

        val memorizedAt = state.effectiveMemorizedAt.toMutableMap()
        for (verse in session.start..session.end) {
            val key = verse.toString()
            val current = state.knowledge[key]
            if (current != Mastery.PERFECT && current != Mastery.REVIEW && memorizedAt[key] == null) {
                memorizedAt[key] = from
            }
        }
        val updated = markKnowledge(state.copy(memorizedAt = memorizedAt), session.range, Mastery.PERFECT)

        val revisions = updated.revisions.toMutableList()
        val revisionId = "r-${session.start}-${session.end}"
        if (revisions.none { it.id == revisionId }) {
            revisions.add(
                Revision(
                    id = revisionId,
                    start = session.start,
                    end = session.end,
                    due = Dates.addDays(from, 1),
                    interval = 1,
                    streak = 0,
                    completedCount = 0,
                ),
            )
        }
        val sessions = updated.sessions.map {
            if (it.id == id) {
                it.copy(status = SessionStatus.DONE, completedAt = Dates.nowIso(), completedDate = from)
            } else {
                it
            }
        }
        return extendLearningProgram(touch(updated.copy(sessions = sessions, revisions = revisions)))
    }

    /**
     * Prolonge un programme épuisé ou hérité **sans reconstruire aucune date existante**.
     *
     * Si l'objectif contient encore des versets ni connus ni couverts par une séance, la suite
     * est planifiée à partir du lendemain de la dernière date prévue. Les versets déjà couverts
     * sont marqués connus pour ne jamais être replanifiés.
     */
    fun extendLearningProgram(state: AppState): AppState {
        val covered = HashSet<Int>()
        for (s in state.sessions) {
            if (s.status == SessionStatus.TODO || s.status == SessionStatus.DONE) {
                for (id in s.start..s.end) covered.add(id)
            }
        }
        val needsExtension = learningOrderIds(state).any { id ->
            id !in covered && state.knowledge[id.toString()] != Mastery.PERFECT &&
                state.knowledge[id.toString()] != Mastery.REVIEW
        }
        if (!needsExtension) return state

        val knowledge = state.knowledge.toMutableMap()
        for (id in covered) knowledge[id.toString()] = Mastery.PERFECT

        val latest = state.sessions.map { it.scheduledDate ?: it.date }.maxOrNull()
        val start = if (latest != null) Dates.addDays(latest, 1) else Dates.todayLocal()
        val extension = generateProgram(state.copy(knowledge = knowledge, sessions = emptyList()), start)
        return touch(state.copy(sessions = state.sessions + extension.sessions))
    }

    /**
     * Notation d'une révision du modèle historique.
     *
     * Les intervalles suivent une progression douce : `perfect` double l'intervalle sans
     * dépasser 90 jours, `hesitant` revient à 3 jours, `errors` et `relearn` à 1 jour.
     */
    fun gradeRevision(
        state: AppState,
        id: String,
        grade: LegacyReviewGrade,
        from: String = Dates.todayLocal(),
    ): AppState {
        val target = state.revisions.firstOrNull { it.id == id } ?: return state
        val revisions = state.revisions.map { r ->
            if (r.id != id) {
                r
            } else {
                val interval = when (grade) {
                    LegacyReviewGrade.PERFECT -> minOf(90, maxOf(3, r.interval * 2))
                    LegacyReviewGrade.HESITANT -> 3
                    LegacyReviewGrade.ERRORS -> 1
                    LegacyReviewGrade.RELEARN -> 1
                }
                r.copy(
                    interval = interval,
                    streak = if (grade == LegacyReviewGrade.PERFECT) r.streak + 1 else 0,
                    due = Dates.addDays(from, interval),
                    lastGrade = grade,
                    completedCount = r.completedCount + 1,
                )
            }
        }
        var updated = state.copy(revisions = revisions)
        updated = when (grade) {
            LegacyReviewGrade.RELEARN -> markKnowledge(updated, target.range, Mastery.LEARNING)
            LegacyReviewGrade.PERFECT -> markKnowledge(updated, target.range, Mastery.PERFECT)
            else -> markKnowledge(updated, target.range, Mastery.REVIEW)
        }
        return touch(updated)
    }

    // -----------------------------------------------------------------------
    // Statistiques
    // -----------------------------------------------------------------------

    /** Nombre de hizb entièrement mémorisés. */
    fun completedHizbs(state: AppState): Int {
        val known = memorizedIds(state).toHashSet()
        return Quran.hizbs.count { Quran.full(known, it.range) }
    }

    data class Stats(
        val today: Int,
        val week: Int,
        val month: Int,
        val days: Int,
        val revisions: Int,
        val hizbs: Int,
        val weeklySessions: Int,
    )

    /**
     * Statistiques de progression.
     *
     * Le comptage est pondéré par le nombre de versets, mais chaque verset n'est compté
     * qu'une fois : une séance suivie par une progression fine est comptée par ses
     * validations, pas par la séance entière, sinon le total doublerait.
     */
    fun stats(state: AppState, at: String = Dates.todayLocal()): Stats {
        val tracked = state.effectiveStudyProgress.values.filter {
            it.mode == com.msoumaya.deepseekandroid.core.model.StudyMode.LEARNING
        }
        val done = state.sessions.filter { s ->
            s.status == SessionStatus.DONE && s.completedAt != null && tracked.none { it.id == s.id }
        }
        val weekStart = Dates.addDays(at, -((Dates.dayOf(at) + 6) % 7))
        val monthStart = at.substring(0, 7) + "-01"
        val dateOf: (Session) -> String = { it.completedDate ?: it.completedAt!!.substring(0, 10) }
        val validations = tracked.flatMap { it.validations }

        fun count(start: String): Int =
            done.filter { dateOf(it) in start..at }.sumOf { it.end - it.start + 1 } +
                validations.filter { it.date in start..at }.sumOf { it.end - it.start + 1 }

        return Stats(
            today = count(at),
            week = count(weekStart),
            month = count(monthStart),
            days = (done.map(dateOf) + validations.map { it.date }).toSet().size,
            revisions = state.revisions.sumOf { it.completedCount },
            hizbs = completedHizbs(state),
            weeklySessions = done.count { dateOf(it) >= weekStart } +
                tracked.count { r ->
                    r.status == com.msoumaya.deepseekandroid.core.model.StudyStatus.COMPLETED &&
                        r.validations.lastOrNull()?.date?.let { it in weekStart..at } == true
                },
        )
    }

    // -----------------------------------------------------------------------
    // Réconciliation d'état entre appareils
    // -----------------------------------------------------------------------

    data class ReconcileResult(val state: AppState, val shouldPush: Boolean)

    /**
     * Fusionne l'état local et l'état distant.
     *
     * Règles, dans l'ordre :
     *  1. les métadonnées locales absentes du serveur sont récupérées (apparence, pages lues,
     *     progression fine, cycle de révision) : un client plus ancien ne doit pas faire
     *     disparaître le travail d'un client plus récent ;
     *  2. si le distant n'est pas plus récent, le local gagne — sauf si le distant a terminé
     *     l'inscription et pas le local, auquel cas c'est le distant ;
     *  3. sinon le distant gagne, complété par les valeurs locales des champs qu'il ignore.
     *
     * `updatedAt` est toujours strictement supérieur aux deux états, ce qui garantit qu'une
     * fusion est bien propagée au tour suivant.
     */
    fun reconcileState(localInput: AppState, remoteInput: AppState?): ReconcileResult {
        val local = migrateReaderState(localInput)
        if (remoteInput == null) return ReconcileResult(local, true)
        var remote = migrateReaderState(remoteInput)

        val bookmarks = Bookmarks.mergeBookmarks(local.bookmarks, remote.bookmarks)

        val appearanceMetadataRecovered =
            (remote.uiFont == null && local.uiFont != null) ||
                (remote.accent == null && local.accent != null) ||
                local.effectiveReadPages.any { it !in remote.effectiveReadPages }

        val studyMetadataRecovered =
            remote.studyProgress == null && local.effectiveStudyProgress.isNotEmpty()

        remote = remote.copy(
            uiFont = remote.uiFont ?: local.uiFont,
            accent = remote.accent ?: local.accent,
            readPages = (remote.effectiveReadPages + local.effectiveReadPages).distinct().sorted(),
            reviewCycleHistory = remote.reviewCycleHistory ?: local.reviewCycleHistory,
            consolidationHistory = remote.consolidationHistory ?: local.consolidationHistory,
            studyProgress = remote.studyProgress ?: local.studyProgress ?: emptyMap(),
        )

        val reviewMetadataRecovered =
            (remote.reviewCycle == null && local.reviewCycle != null) ||
                (remote.reviewConsolidations == null && (local.reviewConsolidations ?: emptyMap()).isNotEmpty()) ||
                (remote.reviewPriorityDue == null && (local.reviewPriorityDue ?: emptyMap()).isNotEmpty())

        remote = remote.copy(
            reviewModelStartedAt = remote.reviewModelStartedAt ?: local.reviewModelStartedAt,
            reviewCycle = remote.reviewCycle ?: local.reviewCycle,
            reviewConsolidations = remote.reviewConsolidations ?: local.reviewConsolidations,
            reviewPriorityDue = remote.reviewPriorityDue ?: local.reviewPriorityDue,
        )

        val audioPreferences = remote.audioPreferences ?: local.audioPreferences

        if (remote.updatedAt <= local.updatedAt && !(remote.onboardingDone && !local.onboardingDone)) {
            return ReconcileResult(local.copy(bookmarks = bookmarks), true)
        }

        val profile = remote.profile ?: local.profile
        val theme = remote.theme ?: local.theme
        val notifications = remote.notifications ?: local.notifications
        val reader = remote.reader?.let { r ->
            val localTestPage = local.reader?.testPage
            if (r.testPage == null && localTestPage != null) r.copy(testPage = localTestPage) else r
        } ?: local.reader
        val lastRead = remote.lastRead ?: local.lastRead
        val memorizedAt = remote.memorizedAt ?: local.memorizedAt
        val reviewSettings = remote.reviewSettings ?: local.reviewSettings
        val reviewHistory = remote.reviewHistory ?: local.reviewHistory
        val reviewDue = remote.reviewDue ?: local.reviewDue
        val difficultyMarkers = remote.difficultyMarkers ?: local.difficultyMarkers
        val difficultyHistory = remote.difficultyHistory ?: local.difficultyHistory

        val nothingRecovered = !appearanceMetadataRecovered && !studyMetadataRecovered &&
            !reviewMetadataRecovered &&
            audioPreferences == remote.audioPreferences &&
            bookmarks == remote.bookmarks &&
            profile == remote.profile &&
            theme == remote.theme &&
            notifications == remote.notifications &&
            reader == remote.reader &&
            lastRead == remote.lastRead &&
            memorizedAt == remote.memorizedAt &&
            reviewSettings == remote.reviewSettings &&
            reviewHistory == remote.reviewHistory &&
            reviewDue == remote.reviewDue &&
            difficultyMarkers == remote.difficultyMarkers &&
            difficultyHistory == remote.difficultyHistory

        if (nothingRecovered) return ReconcileResult(remote, false)

        val updatedAt = Dates.iso(
            maxOf(
                System.currentTimeMillis(),
                Dates.parseIsoMillis(remote.updatedAt) + 1,
                Dates.parseIsoMillis(local.updatedAt) + 1,
            ),
        )
        return ReconcileResult(
            remote.copy(
                bookmarks = bookmarks,
                audioPreferences = audioPreferences,
                profile = profile,
                theme = theme,
                notifications = notifications,
                reader = reader,
                lastRead = lastRead,
                memorizedAt = memorizedAt,
                reviewSettings = reviewSettings,
                reviewHistory = reviewHistory,
                reviewDue = reviewDue,
                difficultyMarkers = difficultyMarkers,
                difficultyHistory = difficultyHistory,
                updatedAt = updatedAt,
            ),
            true,
        )
    }

    /**
     * Sélectionne l'état d'un compte donné.
     *
     * Le cache local n'est utilisé que s'il appartient au compte demandé : un utilisateur qui
     * change de compte ne doit jamais voir le programme du précédent. Un état distant
     * appartenant à un autre compte est ignoré pour la même raison.
     */
    fun accountState(userId: String, cached: AppState?, remote: AppState?): ReconcileResult {
        val local = if (cached?.userId == userId) cached else defaultState()
        val safeRemote = when {
            remote?.userId != null && remote.userId != userId -> null
            remote != null -> migrateReaderState(remote)
            else -> null
        }
        if (cached?.userId != userId && safeRemote != null) {
            return ReconcileResult(
                safeRemote.copy(userId = userId),
                safeRemote.userId != userId || safeRemote != remote,
            )
        }
        val result = reconcileState(local, safeRemote)
        return ReconcileResult(
            result.state.copy(userId = userId),
            result.shouldPush || result.state.userId != userId || safeRemote != remote,
        )
    }

    /**
     * Remise à zéro de la progression.
     *
     * Sont conservés : l'identité du compte, les signets, le récitant, le profil, l'apparence,
     * les préférences de notification, les réglages de lecture et de révision. Sont effacés :
     * connaissances, programme, historique de révisions, consolidations, progression fine.
     */
    fun resetAllProgress(previous: AppState? = null): AppState {
        val now = System.currentTimeMillis()
        val previousTime = previous?.let { Dates.parseIsoMillis(it.updatedAt) } ?: 0L
        return defaultState().copy(
            userId = previous?.userId,
            bookmarks = previous?.bookmarks,
            audioPreferences = previous?.audioPreferences,
            profile = previous?.profile,
            theme = previous?.theme ?: AppTheme.WHITE,
            accent = previous?.accent,
            uiFont = previous?.uiFont,
            notifications = previous?.notifications ?: NotificationPreferences(messages = true, learning = true),
            reader = previous?.reader ?: ReaderPreferences(),
            reviewSettings = previous?.reviewSettings ?: ReviewSettings(enabled = true, cycleDays = 7),
            updatedAt = Dates.iso(maxOf(now, previousTime + 1)),
        )
    }

    // -----------------------------------------------------------------------
    // Aide au diagnostic
    // -----------------------------------------------------------------------

    /** Vérifie qu'un état distant est exploitable, comme `loadState` côté client d'origine. */
    fun isValidPersistedState(state: AppState?): Boolean =
        state != null && state.schema == AppState.SCHEMA
}
