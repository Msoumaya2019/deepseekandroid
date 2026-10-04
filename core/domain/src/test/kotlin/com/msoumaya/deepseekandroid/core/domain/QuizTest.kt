package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.ChallengeAnswer
import com.msoumaya.deepseekandroid.core.model.ChallengeStatus
import com.msoumaya.deepseekandroid.core.model.DailyResponse
import com.msoumaya.deepseekandroid.core.model.QuizAnswer
import com.msoumaya.deepseekandroid.core.model.QuizChallenge
import com.msoumaya.deepseekandroid.core.model.QuizQuestion
import com.msoumaya.deepseekandroid.core.model.QuizSnapshot
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Quiz : question du jour et défis entre amis.
 *
 * Deux règles à ne jamais perdre : une seule participation par question du jour, et seules
 * les réponses **confirmées** comptent dans les statistiques — une réponse encore en attente
 * de synchronisation ne doit pas gonfler le score affiché.
 */
class QuizTest {

    @Before
    fun setUp() = QuranFixture.install()

    private val day = "2026-10-04"

    private fun question(id: String = "q1", publicationDate: String? = day) = QuizQuestion(
        id = id,
        category = "Coran",
        question = "Quelle est la première sourate ?",
        answers = listOf(
            QuizAnswer("a", "Al Fâtiha"),
            QuizAnswer("b", "Al Baqarah"),
        ),
        publicationDate = publicationDate,
        explanation = "Al Fâtiha ouvre le Coran.",
        sourceTitle = "Coran",
        correctAnswerId = "a",
    )

    private fun challenge(
        status: ChallengeStatus = ChallengeStatus.PENDING,
        questionCount: Int = 5,
        expiresAt: String = "2026-10-11T00:00:00Z",
        answers: List<ChallengeAnswer> = emptyList(),
    ) = QuizChallenge(
        id = "c1",
        creatorId = "me",
        opponentId = "friend",
        creatorName = "Moi",
        opponentName = "Ami",
        questionCount = questionCount,
        createdAt = "2026-10-04T10:00:00Z",
        expiresAt = expiresAt,
        status = status,
        questions = List(questionCount) { question("q$it") },
        answers = answers,
    )

    @Test
    fun `le jour du quiz suit le fuseau demande`() {
        assertEquals(
            java.time.LocalDate.now(java.time.ZoneId.of("Europe/Paris")).toString(),
            Quiz.quizDay(java.time.ZoneId.of("Europe/Paris")),
        )
    }

    @Test
    fun `un instantane vide porte le jour demande`() {
        val snapshot = Quiz.emptySnapshot(day)
        assertEquals(day, snapshot.day)
        assertTrue(snapshot.responses.isEmpty())
        assertTrue(snapshot.challenges.isEmpty())
    }

    @Test
    fun `une seule participation par jour`() {
        val snapshot = Quiz.emptySnapshot(day)
        val once = Quiz.recordDailyAnswer(snapshot, question(), "a", day, "2026-10-04T08:00:00Z")
        assertEquals(1, once.responses.size)
        assertTrue(once.responses.first().pending == true, "la réponse reste en attente du serveur")

        val twice = Quiz.recordDailyAnswer(once, question(), "b", day, "2026-10-04T09:00:00Z")
        assertEquals(once, twice, "une seconde participation le même jour est sans effet")
        assertEquals("a", twice.responses.first().selectedAnswerId)
    }

    @Test
    fun `une reponse hors propositions ou hors du jour est refusee`() {
        assertFailsWith<IllegalStateException> {
            Quiz.recordDailyAnswer(Quiz.emptySnapshot(day), question(), "z", day, "2026-10-04T08:00:00Z")
        }
        assertFailsWith<IllegalStateException> {
            Quiz.recordDailyAnswer(Quiz.emptySnapshot(day), question(), "a", "2026-10-05", "2026-10-05T08:00:00Z")
        }
    }

    @Test
    fun `les statistiques ne comptent que les reponses confirmees`() {
        val snapshot = QuizSnapshot(
            day = day,
            responses = listOf(
                DailyResponse("q1", "2026-10-01", "a", "2026-10-01T08:00:00Z", question("q1"), isCorrect = true),
                DailyResponse("q2", "2026-10-02", "b", "2026-10-02T08:00:00Z", question("q2"), isCorrect = false),
                DailyResponse("q3", "2026-10-03", "a", "2026-10-03T08:00:00Z", question("q3"), pending = true),
            ),
        )
        val stats = Quiz.statistics(snapshot, "me")
        assertEquals(2, stats.total, "la réponse en attente est exclue")
        assertEquals(1, stats.correct)
        assertEquals(50, stats.rate)
    }

    @Test
    fun `les statistiques de defis comptent victoires et egalites`() {
        val win = challenge(
            status = ChallengeStatus.COMPLETED,
            answers = listOf(
                ChallengeAnswer("me", "q0", "a", "2026-10-05T08:00:00Z", isCorrect = true),
                ChallengeAnswer("friend", "q0", "b", "2026-10-05T09:00:00Z", isCorrect = false),
            ),
        )
        val tie = challenge(
            status = ChallengeStatus.COMPLETED,
            answers = listOf(
                ChallengeAnswer("me", "q0", "a", "2026-10-05T08:00:00Z", isCorrect = true),
                ChallengeAnswer("friend", "q0", "b", "2026-10-05T09:00:00Z", isCorrect = true),
            ),
        )
        val running = challenge(status = ChallengeStatus.PENDING)
        val snapshot = QuizSnapshot(day = day, challenges = listOf(win, tie, running))

        val stats = Quiz.statistics(snapshot, "me")
        assertEquals(2, stats.played, "seuls les défis terminés comptent")
        assertEquals(1, stats.wins)
        assertEquals(1, stats.ties)
    }

    @Test
    fun `le statut d'un defi dit a qui de jouer`() {
        val now = Dates.parseIsoMillis("2026-10-05T00:00:00Z")
        // Aucune réponse : c'est au créateur de jouer.
        assertEquals("À toi de jouer", Quiz.challengeStatus(challenge(), "me", now))
        // Le créateur a répondu toutes les questions : il attend son ami.
        val answered = challenge(
            questionCount = 5,
            answers = (0 until 5).map { ChallengeAnswer("me", "q$it", "a", "2026-10-05T08:00:00Z") },
        )
        assertEquals("En attente de l’ami", Quiz.challengeStatus(answered, "me", now))
        // Défi expiré par sa date.
        assertEquals("Expiré", Quiz.challengeStatus(challenge(expiresAt = "2026-10-04T00:00:00Z"), "me", now))
        // Statut terminal.
        assertEquals("Terminé", Quiz.challengeStatus(challenge(status = ChallengeStatus.COMPLETED), "me", now))
    }

    @Test
    fun `la fusion conserve une reponse locale en attente inconnue du serveur`() {
        val remote = QuizSnapshot(
            day = day,
            responses = listOf(
                DailyResponse("q1", "2026-10-03", "a", "2026-10-03T08:00:00Z", question("q1"), isCorrect = true),
            ),
        )
        val local = QuizSnapshot(
            day = day,
            responses = listOf(
                DailyResponse("q2", "2026-10-04", "b", "2026-10-04T08:00:00Z", question("q2"), pending = true),
            ),
        )
        val merged = Quiz.mergeSnapshot(remote, local)
        assertEquals(listOf("2026-10-04", "2026-10-03"), merged.responses.map { it.day })
        assertEquals(2, merged.responses.size)
    }

    @Test
    fun `la fusion n'ecrase jamais une reponse confirmee par le serveur`() {
        val remote = QuizSnapshot(
            day = day,
            responses = listOf(
                DailyResponse("q1", "2026-10-04", "a", "2026-10-04T08:00:00Z", question("q1"), isCorrect = true),
            ),
        )
        val local = QuizSnapshot(
            day = day,
            responses = listOf(
                DailyResponse("q1", "2026-10-04", "b", "2026-10-04T07:00:00Z", question("q1"), pending = true),
            ),
        )
        val merged = Quiz.mergeSnapshot(remote, local)
        assertEquals(1, merged.responses.size)
        assertEquals("a", merged.responses.first().selectedAnswerId, "la réponse du serveur prime")
    }
}
