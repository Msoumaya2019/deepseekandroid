package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.SessionStatus
import com.msoumaya.deepseekandroid.core.model.StudyMode
import com.msoumaya.deepseekandroid.core.model.StudyProgress
import com.msoumaya.deepseekandroid.core.model.StudyStatus
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Objectif hebdomadaire et programme à venir.
 *
 * La semaine va du lundi 00:00 au dimanche 23:59 en calendrier local. À la nouvelle semaine,
 * l'affichage repart de zéro — sans qu'aucun historique ne soit supprimé.
 */
class WeeklyProgressTest {

    @Before
    fun setUp() = QuranFixture.install()

    @Test
    fun `la semaine va du lundi au dimanche`() {
        // 7 octobre 2026 est un mercredi.
        val week = WeeklyProgress.weeklyProgress(QuranFixture.onboardedState(), at = "2026-10-07")
        assertEquals("2026-10-05", week.weekStart, "le lundi de la semaine")
        assertEquals("2026-10-11", week.weekEnd, "le dimanche de la semaine")
    }

    @Test
    fun `un dimanche appartient a la semaine qui commence le lundi precedent`() {
        val week = WeeklyProgress.weeklyProgress(QuranFixture.onboardedState(), at = "2026-10-04")
        assertEquals("2026-09-28", week.weekStart)
        assertEquals("2026-10-04", week.weekEnd)
    }

    @Test
    fun `weeklyProgress ne compte que les seances de la semaine`() {
        val state = QuranFixture.onboardedState().copy(
            sessions = listOf(
                QuranFixture.session("in1", "2026-10-05", 1, 3, SessionStatus.DONE),
                QuranFixture.session("in2", "2026-10-07", 4, 6, SessionStatus.TODO),
                QuranFixture.session("before", "2026-10-04", 7, 9, SessionStatus.DONE),
                QuranFixture.session("after", "2026-10-12", 10, 12, SessionStatus.TODO),
            ),
        )
        val week = WeeklyProgress.weeklyProgress(state, at = "2026-10-07")
        assertEquals(2, week.total)
        assertEquals(1, week.done)
        assertEquals(0.5, week.ratio)
    }

    @Test
    fun `la nouvelle semaine repart de zero sans effacer l'historique`() {
        val state = QuranFixture.onboardedState().copy(
            sessions = listOf(QuranFixture.session("in1", "2026-10-05", 1, 3, SessionStatus.DONE)),
        )
        val nextWeek = WeeklyProgress.weeklyProgress(state, at = "2026-10-12")
        assertEquals(0, nextWeek.total)
        assertEquals(0.0, nextWeek.ratio)
        assertEquals(1, state.sessions.size, "la séance de la semaine précédente reste en base")
    }

    @Test
    fun `weeklyProgress honore la date planifiee, pas la date de creation`() {
        // Séance créée pour le mardi, mais planifiée le mercredi : c'est la date planifiée
        // qui décide de la semaine.
        val state = QuranFixture.onboardedState().copy(
            sessions = listOf(
                QuranFixture.session("s", "2026-10-06", 1, 3, SessionStatus.TODO, scheduledDate = "2026-10-07"),
            ),
        )
        val week = WeeklyProgress.weeklyProgress(state, at = "2026-10-07")
        assertEquals(1, week.total)
    }

    @Test
    fun `upcomingSessions s'arrete a dix jours et ne supprime rien`() {
        val sessions = (0..20).map { offset ->
            QuranFixture.session("s$offset", Dates.addDays("2026-10-05", offset), 1, 3)
        }
        val state = QuranFixture.onboardedState().copy(sessions = sessions)
        val upcoming = WeeklyProgress.upcomingSessions(state, at = "2026-10-05")

        assertEquals("2026-10-05", WeeklyProgress.scheduledDate(upcoming.first()))
        assertEquals("2026-10-15", WeeklyProgress.scheduledDate(upcoming.last()))
        assertTrue(upcoming.size < sessions.size, "les séances au-delà de dix jours ne sont pas affichées")
        assertEquals(sessions.size, state.sessions.size, "aucune donnée plus lointaine n'est supprimée")
    }

    @Test
    fun `upcomingSessions ignore les seances deja faites ou reportees`() {
        val state = QuranFixture.onboardedState().copy(
            sessions = listOf(
                QuranFixture.session("done", "2026-10-06", 1, 3, SessionStatus.DONE),
                QuranFixture.session("postponed", "2026-10-07", 4, 6, SessionStatus.POSTPONED),
                QuranFixture.session("todo", "2026-10-08", 7, 9, SessionStatus.TODO),
            ),
        )
        val upcoming = WeeklyProgress.upcomingSessions(state, at = "2026-10-05")
        assertEquals(listOf("todo"), upcoming.map { it.id })
    }

    @Test
    fun `sessionStatus distingue fait, reporte, partiel et en attente`() {
        val partial = StudyProgress(
            id = "s",
            mode = StudyMode.LEARNING,
            start = 1,
            end = 6,
            through = 3,
            page = 1,
            source = "coranTest",
            updatedAt = "2026-10-05T10:00:00Z",
            status = StudyStatus.PARTIAL,
        )
        val base = QuranFixture.onboardedState()
        assertEquals(
            "completed",
            WeeklyProgress.sessionStatus(base, QuranFixture.session("a", "2026-10-05", 1, 3, SessionStatus.DONE)),
        )
        assertEquals(
            "skipped",
            WeeklyProgress.sessionStatus(base, QuranFixture.session("b", "2026-10-05", 1, 3, SessionStatus.POSTPONED)),
        )
        assertEquals(
            "pending",
            WeeklyProgress.sessionStatus(base, QuranFixture.session("c", "2026-10-05", 1, 3, SessionStatus.TODO)),
        )
        val withPartial = base.copy(studyProgress = mapOf("learning:c" to partial))
        assertEquals(
            "partiallyCompleted",
            WeeklyProgress.sessionStatus(withPartial, QuranFixture.session("c", "2026-10-05", 1, 6)),
        )
    }
}
