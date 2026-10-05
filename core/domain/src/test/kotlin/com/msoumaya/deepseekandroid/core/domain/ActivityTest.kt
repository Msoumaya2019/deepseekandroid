package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.SessionStatus
import com.msoumaya.deepseekandroid.core.model.StudyMode
import com.msoumaya.deepseekandroid.core.model.StudyProgress
import com.msoumaya.deepseekandroid.core.model.StudyStatus
import com.msoumaya.deepseekandroid.core.model.StudyValidation
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Jours actifs et série en cours.
 *
 * Deux décisions de cette règle se voient à l'écran et ne se devinent pas :
 *
 *  - la série part d'**hier** quand aujourd'hui est vide, sans quoi l'écran afficherait « 0 jour
 *    d'affilée » tous les matins ;
 *  - une séance de révision n'est pas un jour actif, parce qu'elle n'a pas de `status = done`.
 */
class ActivityTest {

    @Before
    fun setUp() = QuranFixture.install()

    private fun stateWith(sessions: List<com.msoumaya.deepseekandroid.core.model.Session>) =
        QuranFixture.onboardedState().copy(sessions = sessions)

    private fun learningRecord(id: String, vararg dates: String): StudyProgress = StudyProgress(
        id = id,
        mode = StudyMode.LEARNING,
        start = 1,
        end = 6,
        through = 6,
        page = 1,
        source = "coranTest",
        updatedAt = "2026-10-05T10:00:00Z",
        status = StudyStatus.COMPLETED,
        validations = dates.map { StudyValidation(start = 1, end = 6, date = it) },
    )

    private fun revisionRecord(id: String, vararg dates: String): StudyProgress = StudyProgress(
        id = id,
        mode = StudyMode.REVISION,
        start = 1,
        end = 6,
        through = 6,
        page = 1,
        source = "coranTest",
        updatedAt = "2026-10-05T10:00:00Z",
        status = StudyStatus.COMPLETED,
        validations = dates.map { StudyValidation(start = 1, end = 6, date = it) },
    )

    @Test
    fun `un etat vierge n'a ni jour actif ni serie`() {
        val a = Activity.activity(QuranFixture.onboardedState(), at = "2026-10-05")
        assertEquals(0, a.dates.size)
        assertEquals(0, a.streak)
    }

    @Test
    fun `une seance terminee aujourd'hui ouvre une serie de un`() {
        val state = stateWith(
            listOf(
                QuranFixture.session("s", "2026-10-05", 1, 3, SessionStatus.DONE)
                    .copy(completedDate = "2026-10-05"),
            ),
        )
        val a = Activity.activity(state, at = "2026-10-05")
        assertEquals(setOf("2026-10-05"), a.dates)
        assertEquals(1, a.streak)
    }

    @Test
    fun `la serie part d'hier quand aujourd'hui est vide`() {
        // C'est le repli décisif : sans lui, la personne qui a travaillé hier lirait « 0 ».
        val state = stateWith(
            listOf(
                QuranFixture.session("s", "2026-10-04", 1, 3, SessionStatus.DONE)
                    .copy(completedDate = "2026-10-04"),
            ),
        )
        val a = Activity.activity(state, at = "2026-10-05")
        assertEquals(1, a.streak)
    }

    @Test
    fun `deux jours vides d'affilee donnent une serie nulle`() {
        // Le repli ne va pas plus loin qu'hier : avant-hier seul ne suffit pas.
        val state = stateWith(
            listOf(
                QuranFixture.session("s", "2026-10-03", 1, 3, SessionStatus.DONE)
                    .copy(completedDate = "2026-10-03"),
            ),
        )
        val a = Activity.activity(state, at = "2026-10-05")
        assertEquals(1, a.dates.size, "la date reste un jour actif")
        assertEquals(0, a.streak, "mais elle n'ouvre aucune série")
    }

    @Test
    fun `trois jours consecutifs donnent une serie de trois`() {
        val state = stateWith(
            listOf("2026-10-03", "2026-10-04", "2026-10-05").map { day ->
                QuranFixture.session("s-$day", day, 1, 3, SessionStatus.DONE)
                    .copy(completedDate = day)
            },
        )
        assertEquals(3, Activity.activity(state, at = "2026-10-05").streak)
    }

    @Test
    fun `un trou interrompt la serie sans effacer les jours actifs`() {
        val state = stateWith(
            listOf(
                QuranFixture.session("a", "2026-10-05", 1, 3, SessionStatus.DONE)
                    .copy(completedDate = "2026-10-05"),
                QuranFixture.session("b", "2026-10-03", 4, 6, SessionStatus.DONE)
                    .copy(completedDate = "2026-10-03"),
            ),
        )
        val a = Activity.activity(state, at = "2026-10-05")
        assertEquals(2, a.dates.size, "les deux jours restent comptés")
        assertEquals(1, a.streak, "la série s'arrête au trou du 4 octobre")
    }

    @Test
    fun `une seance terminee est datee par sa date d'achevement`() {
        val state = stateWith(
            listOf(
                QuranFixture.session("s", "2026-10-05", 1, 3, SessionStatus.DONE)
                    .copy(completedAt = "2026-10-04T21:30:00Z"),
            ),
        )
        // La séance était prévue le 5 mais achevée le 4 : c'est le 4 qui compte.
        assertEquals(setOf("2026-10-04"), Activity.activity(state, at = "2026-10-05").dates)
    }

    @Test
    fun `la date d'achevement explicite prime sur l'horodatage`() {
        val state = stateWith(
            listOf(
                QuranFixture.session("s", "2026-10-05", 1, 3, SessionStatus.DONE)
                    .copy(completedDate = "2026-10-02", completedAt = "2026-10-04T21:30:00Z"),
            ),
        )
        assertEquals(setOf("2026-10-02"), Activity.activity(state, at = "2026-10-05").dates)
    }

    @Test
    fun `a defaut d'achevement c'est la date prevue qui compte`() {
        val state = stateWith(
            listOf(QuranFixture.session("s", "2026-10-05", 1, 3, SessionStatus.DONE)),
        )
        assertEquals(setOf("2026-10-05"), Activity.activity(state, at = "2026-10-05").dates)
    }

    @Test
    fun `une seance non terminee ne compte pas`() {
        val state = stateWith(
            listOf(
                QuranFixture.session("todo", "2026-10-05", 1, 3, SessionStatus.TODO),
                QuranFixture.session("postponed", "2026-10-05", 4, 6, SessionStatus.POSTPONED),
            ),
        )
        val a = Activity.activity(state, at = "2026-10-05")
        assertEquals(0, a.dates.size)
        assertEquals(0, a.streak)
    }

    @Test
    fun `deux seances le meme jour ne font qu'un jour actif`() {
        val state = stateWith(
            listOf(
                QuranFixture.session("a", "2026-10-05", 1, 3, SessionStatus.DONE)
                    .copy(completedDate = "2026-10-05"),
                QuranFixture.session("b", "2026-10-05", 4, 6, SessionStatus.DONE)
                    .copy(completedDate = "2026-10-05"),
            ),
        )
        assertEquals(1, Activity.activity(state, at = "2026-10-05").dates.size)
    }

    @Test
    fun `une validation d'apprentissage compte comme un jour actif`() {
        val state = QuranFixture.onboardedState().copy(
            studyProgress = mapOf("learning:a" to learningRecord("a", "2026-10-05", "2026-10-04")),
        )
        val a = Activity.activity(state, at = "2026-10-05")
        assertEquals(setOf("2026-10-05", "2026-10-04"), a.dates)
        assertEquals(2, a.streak)
    }

    @Test
    fun `une validation de revision ne compte pas`() {
        // Le filtre porte sur le mode, et non sur la seule présence d'une validation : c'est
        // exactement ce que fait le client d'origine.
        val state = QuranFixture.onboardedState().copy(
            studyProgress = mapOf("revision:a" to revisionRecord("a", "2026-10-05")),
        )
        val a = Activity.activity(state, at = "2026-10-05")
        assertTrue(a.dates.isEmpty(), "une révision n'ouvre pas de jour actif")
        assertEquals(0, a.streak)
    }

    @Test
    fun `les seances et les validations se cumulent`() {
        val state = QuranFixture.onboardedState().copy(
            sessions = listOf(
                QuranFixture.session("s", "2026-10-04", 1, 3, SessionStatus.DONE)
                    .copy(completedDate = "2026-10-04"),
            ),
            studyProgress = mapOf("learning:a" to learningRecord("a", "2026-10-05")),
        )
        val a = Activity.activity(state, at = "2026-10-05")
        assertEquals(setOf("2026-10-05", "2026-10-04"), a.dates)
        assertEquals(2, a.streak)
    }
}
