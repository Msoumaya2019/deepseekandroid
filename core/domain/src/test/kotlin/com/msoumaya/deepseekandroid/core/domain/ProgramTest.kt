package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.*
import com.msoumaya.deepseekandroid.core.model.Goal
import com.msoumaya.deepseekandroid.core.model.GoalPreset
import com.msoumaya.deepseekandroid.core.model.Mastery
import com.msoumaya.deepseekandroid.core.model.Pace
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.SessionStatus
import com.msoumaya.deepseekandroid.core.model.StudyMode
import com.msoumaya.deepseekandroid.core.model.StudyProgress
import com.msoumaya.deepseekandroid.core.model.StudyStatus
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Programme d'apprentissage.
 *
 * Le test central de cette classe est la règle des dates : une séance prévue mardi et faite
 * lundi garde sa date prévue et reçoit une date de validation distincte. Cette règle est
 * structurante pour l'objectif hebdomadaire et pour les consolidations J+1/J+3/J+7.
 */
class ProgramTest {

    @Before
    fun setUp() = QuranFixture.install()

    // --- Maîtrise -----------------------------------------------------------

    @Test
    fun `markKnowledge date la memorisation seulement apres l'inscription`() {
        val before = Program.defaultState().copy(onboardingDone = false)
        val duringOnboarding = Program.markKnowledge(before, Range(1, 3), Mastery.PERFECT)
        assertTrue(duringOnboarding.effectiveMemorizedAt.isEmpty(), "pas de date avant la fin de l'inscription")

        val after = QuranFixture.onboardedState()
        val marked = Program.markKnowledge(after, Range(1, 3), Mastery.PERFECT)
        assertEquals(Dates.todayLocal(), marked.effectiveMemorizedAt["1"])
        assertEquals(Dates.todayLocal(), marked.effectiveMemorizedAt["3"])
        assertTrue(Program.isKnown(marked, 1))
        assertTrue(Program.isKnown(marked, 2))
        assertTrue(Program.isRangeKnown(marked, Range(1, 3)))
    }

    @Test
    fun `repasser en apprentissage efface la date et l'echeance sans toucher aux revisions`() {
        var state = QuranFixture.onboardedState()
        state = Program.markKnowledge(state, Range(1, 3), Mastery.PERFECT)
        state = state.copy(reviewDue = mapOf("1" to "2026-10-11"))
        val reverted = Program.markKnowledge(state, Range(1, 1), Mastery.LEARNING)

        assertNull(reverted.effectiveMemorizedAt["1"], "la date d'apprentissage doit être effacée")
        assertNull(reverted.reviewDue?.get("1"), "l'échéance de révision doit être effacée")
        // Les autres versets de la plage initiale sont intacts.
        assertEquals(Dates.todayLocal(), reverted.effectiveMemorizedAt["2"])
    }

    @Test
    fun `toggleKnownRange bascule puis restaure`() {
        val state = QuranFixture.onboardedState()
        val known = Program.toggleKnownRange(state, Range(10, 12))
        assertTrue(Program.isRangeKnown(known, Range(10, 12)))
        val back = Program.toggleKnownRange(known, Range(10, 12))
        assertTrue(!Program.isRangeKnown(back, Range(10, 12)))
    }

    @Test
    fun `partialKnownRanges decrit les passages incomplets`() {
        var state = QuranFixture.onboardedState()
        state = Program.markKnowledge(state, Range(1, 7), Mastery.PERFECT)
        // La sourate 1 est complète : elle ne doit pas apparaître comme partielle.
        assertTrue(Program.partialKnownRanges(state).isEmpty())

        // La sourate 2 va de 8 à 293 : n'en connaître que 20 à 22 est un passage partiel.
        state = Program.markKnowledge(state, Range(20, 22), Mastery.PERFECT)
        assertEquals(listOf(Range(20, 22)), Program.partialKnownRanges(state))
    }

    // --- Découpage ----------------------------------------------------------

    @Test
    fun `nextChunk respecte le rythme en versets`() {
        assertEquals(listOf(1, 2, 3), Program.nextChunk((1..10).toList(), Pace.VERSE3))
        assertEquals(listOf(1, 2, 3, 4, 5), Program.nextChunk((1..10).toList(), Pace.VERSE5))
        assertEquals(emptyList(), Program.nextChunk(emptyList(), Pace.VERSE3))
    }

    @Test
    fun `nextChunk en page entiere s'arrete a la fin de la page`() {
        // Page 1 = versets 1 à 7.
        assertEquals((1..7).toList(), Program.nextChunk((1..30).toList(), Pace.PAGE))
    }

    @Test
    fun `nextChunk en deux pages ne franchit pas la troisieme`() {
        val chunk = Program.nextChunk((1..40).toList(), Pace.PAGE2)
        val pages = chunk.map { Quran.pageOf(it) }.distinct()
        assertEquals(listOf(1, 2), pages)
        assertEquals(12, chunk.size, "page 1 = 7 versets, page 2 = 5 versets")
    }

    @Test
    fun `nextChunk en demi-page s'arrete a la moitie du volume`() {
        val chunk = Program.nextChunk((1..30).toList(), Pace.HALF_PAGE)
        val full = Quran.volume(Quran.pageRange(1))
        assertTrue(chunk.size in 1..6, "une demi-page ne peut pas contenir les 7 versets entiers")
        assertTrue(Quran.volume(chunk) * 2 >= full, "la demi-page doit atteindre la moitié du volume")
    }

    @Test
    fun `nextChunk en rub' s'arrete a la frontiere de la division`() {
        val quarter = Quran.quarters.first()
        val chunk = Program.nextChunk((quarter.start..(quarter.end + 20)).toList(), Pace.QUARTER)
        assertEquals(quarter.start, chunk.first())
        assertEquals(quarter.end, chunk.last())
    }

    @Test
    fun `splitContiguous ne franchit jamais une sourate`() {
        // 7 est le dernier verset de la sourate 1, 8 le premier de la sourate 2.
        assertEquals(
            listOf(Range(5, 7), Range(8, 10)),
            Program.splitContiguous(listOf(5, 6, 7, 8, 9, 10)),
        )
        assertEquals(listOf(Range(1, 3)), Program.splitContiguous(listOf(1, 2, 3)))
    }

    // --- Génération ---------------------------------------------------------

    @Test
    fun `generateProgram planifie a partir de la date demandee, sur les seuls jours choisis`() {
        val state = QuranFixture.onboardedState()
        val generated = Program.generateProgram(state, from = "2026-10-05")

        assertTrue(generated.sessions.isNotEmpty())
        val dates = generated.sessions.map { it.date }.distinct()
        assertEquals(
            listOf("2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09", "2026-10-12"),
            dates.take(6),
            "lundi à vendredi, puis lundi suivant : samedi et dimanche sont hors jours d'apprentissage",
        )
        // Aucune séance ne porte sur un jour hors des jours choisis.
        assertTrue(generated.sessions.none { Dates.dayOf(it.date) !in state.learningDays })
        // Les séances sont triées par date.
        assertEquals(generated.sessions.sortedBy { it.date }, generated.sessions)
        // `scheduledDate` est renseigné dès la création.
        assertTrue(generated.sessions.all { it.scheduledDate == it.date })
    }

    @Test
    fun `generateProgram suit le sens de parcours de l'objectif`() {
        // L'état par défaut ne fixe aucun sens : les versets sont pris dans l'ordre du Coran,
        // donc depuis le début du juz’ ‘Amma (5673).
        val ascending = Program.generateProgram(QuranFixture.onboardedState(), from = "2026-10-05")
        assertEquals(5673, ascending.sessions.first().start)
        assertEquals(5675, ascending.sessions.first().end)

        // `goalFromPreset` fixe `fromNas` : on commence par la fin du Coran.
        // An-Nâs (114) compte 6 versets : 6231 à 6236, découpés par trois.
        val fromNas = Program.generateProgram(
            QuranFixture.onboardedState().copy(goal = Program.goalFromPreset(GoalPreset.AMMA)),
            from = "2026-10-05",
        )
        val first = fromNas.sessions.first()
        assertEquals(6231, first.start)
        assertEquals(6233, first.end)
        assertEquals(Pace.VERSE3, first.unit)
    }

    @Test
    fun `generateProgram ne replanifie jamais les versets deja maitrises`() {
        var state = QuranFixture.onboardedState()
        state = Program.markKnowledge(state, Range(6231, 6236), Mastery.PERFECT)
        val generated = Program.generateProgram(state, from = "2026-10-05")
        assertTrue(
            generated.sessions.none { it.start in 6231..6236 || it.end in 6231..6236 },
            "les versets connus ne doivent pas être replanifiés",
        )
    }

    @Test
    fun `generateProgram conserve les seances passees et reporte les seances manquees`() {
        val state = QuranFixture.onboardedState().copy(
            sessions = listOf(
                QuranFixture.session("past", "2026-09-28", 1, 3, SessionStatus.TODO),
                QuranFixture.session("done", "2026-09-29", 4, 6, SessionStatus.DONE),
            ),
        )
        val generated = Program.generateProgram(state, from = "2026-10-05")
        val past = generated.sessions.first { it.id == "past" }
        assertEquals(SessionStatus.POSTPONED, past.status, "une séance manquée reste due, mais ne bloque pas le planning")
        assertEquals("2026-09-28", past.date, "la date d'origine n'est jamais déplacée")
        assertEquals(SessionStatus.DONE, generated.sessions.first { it.id == "done" }.status)
    }

    // --- Validation et dates ------------------------------------------------

    @Test
    fun `une seance faite en avance garde sa date prevue et recoit sa date de validation`() {
        val state = QuranFixture.onboardedState().copy(
            goal = Goal(label = "test", ranges = listOf(Range(1, 3))),
            sessions = listOf(QuranFixture.session("s1", "2026-10-06", 1, 3, scheduledDate = "2026-10-06")),
        )
        val done = Program.completeSession(state, "s1", memorized = true, from = "2026-10-05")
        val session = done.sessions.first { it.id == "s1" }

        assertEquals(SessionStatus.DONE, session.status)
        assertEquals("2026-10-06", session.scheduledDate, "la date prévue n'est jamais déplacée")
        assertEquals("2026-10-06", session.dueDate)
        assertEquals("2026-10-05", session.completedDate, "la date réelle de validation est conservée")
        assertNotNull(session.completedAt)
        assertEquals("2026-10-05", done.effectiveMemorizedAt["1"])
        assertEquals("2026-10-05", done.effectiveMemorizedAt["3"])
    }

    @Test
    fun `completeSession cree une revision a J plus un`() {
        val state = QuranFixture.onboardedState().copy(
            goal = Goal(label = "test", ranges = listOf(Range(1, 3))),
            sessions = listOf(QuranFixture.session("s1", "2026-10-06", 1, 3)),
        )
        val done = Program.completeSession(state, "s1", memorized = true, from = "2026-10-05")
        val revision = done.revisions.single { it.id == "r-1-3" }
        assertEquals("2026-10-06", revision.due)
        assertEquals(1, revision.interval)
    }

    @Test
    fun `completeSession non memorisee reporte la seance`() {
        val state = QuranFixture.onboardedState().copy(
            sessions = listOf(QuranFixture.session("s1", "2026-10-06", 1, 3)),
        )
        val result = Program.completeSession(state, "s1", memorized = false, from = "2026-10-05")
        assertEquals(SessionStatus.POSTPONED, result.sessions.first { it.id == "s1" }.status)
    }

    @Test
    fun `postponeSession laisse une seance partielle reprenable`() {
        val partial = StudyProgress(
            id = "s1",
            mode = StudyMode.LEARNING,
            start = 1,
            end = 3,
            through = 2,
            page = 1,
            source = "coranTest",
            updatedAt = "2026-10-05T10:00:00Z",
            status = StudyStatus.PARTIAL,
        )
        val state = QuranFixture.onboardedState().copy(
            sessions = listOf(QuranFixture.session("s1", "2026-10-06", 1, 3)),
            studyProgress = mapOf("learning:s1" to partial),
        )
        val result = Program.postponeSession(state, "s1", from = "2026-10-05")
        assertEquals(SessionStatus.TODO, result.sessions.first().status, "une séance partielle est reprise, pas reportée")
    }

    // --- Progression --------------------------------------------------------

    @Test
    fun `progress mesure l'objectif en volume, sans double compte`() {
        var state = QuranFixture.onboardedState().copy(
            goal = Goal(label = "test", ranges = listOf(Range(1, 7))),
        )
        val target = Quran.volume((1..7).toList())
        val empty = Program.progress(state)
        assertEquals(0.0, empty.goal)
        assertEquals(target, empty.goalTotal, "le total est un volume de lettres, pas un nombre de versets")
        assertEquals(0, empty.goalKnown)

        state = Program.markKnowledge(state, Range(1, 7), Mastery.PERFECT)
        val full = Program.progress(state)
        assertEquals(1.0, full.goal)
        assertEquals(target, full.goalKnown)
    }

    @Test
    fun `resetAllProgress repart d'un etat vierge`() {
        var state = QuranFixture.onboardedState()
        state = Program.markKnowledge(state, Range(1, 7), Mastery.PERFECT)
        val reset = Program.resetAllProgress(state)
        assertTrue(reset.knowledge.isEmpty())
        assertTrue(reset.sessions.isEmpty())
        assertTrue(reset.revisions.isEmpty())
    }

    @Test
    fun `isValidPersistedState refuse un etat sans identifiant ni schema`() {
        assertTrue(!Program.isValidPersistedState(null))
        val state = QuranFixture.onboardedState()
        assertTrue(Program.isValidPersistedState(state))
        assertTrue(!Program.isValidPersistedState(state.copy(schema = -1)))
    }
}
