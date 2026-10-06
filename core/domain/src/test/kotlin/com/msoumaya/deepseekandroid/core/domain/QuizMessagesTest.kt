package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.ChallengeStatus
import com.msoumaya.deepseekandroid.core.model.DailyResponse
import com.msoumaya.deepseekandroid.core.model.QuizAnswer
import com.msoumaya.deepseekandroid.core.model.QuizChallenge
import com.msoumaya.deepseekandroid.core.model.QuizQuestion
import com.msoumaya.deepseekandroid.core.model.QuizSnapshot
import com.msoumaya.deepseekandroid.core.model.ThemedQuiz
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Les deux règles du Quiz qui décident de **ce que l'écran dit quand quelque chose ne va pas**.
 *
 * Elles sont séparées des règles du jeu, qui vivent dans [QuizTest], parce qu'elles ne portent
 * pas sur le quiz lui-même : elles portent sur le message et sur le partage entre « rien à
 * montrer » et « quelque chose à montrer ». Les deux fautes qu'elles évitent ne cassent rien —
 * elles mentent :
 *
 *  - **confondre le code `PGRST202` avec un message ordinaire** afficherait à quelqu'un qui
 *    exploite le projet un texte de PostgREST au lieu de « le service Quiz doit être activé sur
 *    le serveur », donc ferait chercher une panne de réseau là où il manque une migration ;
 *  - **prendre un instantané vide pour une panne, ou l'inverse**, afficherait « Aucune question
 *    publiée aujourd'hui » alors que la lecture a échoué — une affirmation fausse sur le
 *    contenu du serveur, faite au moment précis où l'on n'en sait rien.
 *
 * [Quiz.isEmpty] n'existe que pour ce partage : elle ne se pose jamais toute seule, et c'est
 * pourquoi les cas éprouvés ici sont ceux d'un instantané **vide** ou **pas vide**, jamais d'un
 * instantané « presque vide ».
 */
class QuizMessagesTest {

    private val day = "2026-10-04"

    private fun question(id: String = "q1") = QuizQuestion(
        id = id,
        category = "Coran",
        question = "Quelle est la première sourate ?",
        answers = listOf(QuizAnswer("a", "Al Fâtiha"), QuizAnswer("b", "Al Baqarah")),
        publicationDate = day,
        correctAnswerId = "a",
    )

    private fun challenge() = QuizChallenge(
        id = "c1",
        creatorId = "me",
        opponentId = "friend",
        creatorName = "Moi",
        opponentName = "Ami",
        questionCount = 5,
        createdAt = "2026-10-04T10:00:00Z",
        expiresAt = "2026-10-11T00:00:00Z",
        status = ChallengeStatus.PENDING,
        questions = List(5) { question("q$it") },
    )

    // ------------------------------------------------------------------
    // Le message d'erreur
    // ------------------------------------------------------------------

    @Test
    fun `un code de fonction absente prime sur le texte du serveur`() {
        // Le texte du serveur est long et parle de schéma : c'est le code qui doit trancher.
        assertEquals(
            QuizText.SERVICE_MISSING,
            Quiz.errorText(
                message = "Could not find the function public.quiz_snapshot(p_day) in the schema cache",
                code = "PGRST202",
            ),
        )
    }

    @Test
    fun `un refus ordinaire affiche le texte du serveur`() {
        assertEquals("Connecte-toi pour participer.", Quiz.errorText(message = "Connecte-toi pour participer."))
    }

    @Test
    fun `un code voisin ne declenche pas le message du schema`() {
        // Un code qui ressemble n'est pas le code attendu : le repli doit rester sur le texte.
        assertEquals("Refus", Quiz.errorText(message = "Refus", code = "PGRST203"))
    }

    @Test
    fun `une panne sans texte lisible retombe sur le message generique`() {
        assertEquals(QuizText.GENERIC_ERROR, Quiz.errorText())
        assertEquals(QuizText.GENERIC_ERROR, Quiz.errorText(message = "   "))
        assertEquals(QuizText.GENERIC_ERROR, Quiz.errorText(message = null, code = "PGRST205"))
    }

    @Test
    fun `le repli du Quiz differe de celui de l'espace social`() {
        // Deux écrans, deux causes probables : le Quiz passe tout par le réseau.
        assertFalse(QuizText.GENERIC_ERROR == SocialText.GENERIC_ERROR)
    }

    // ------------------------------------------------------------------
    // L'instantané vide
    // ------------------------------------------------------------------

    @Test
    fun `un instantane neuf est vide`() {
        assertTrue(Quiz.isEmpty(Quiz.emptySnapshot(day)))
    }

    @Test
    fun `une question du jour suffit a remplir l'ecran`() {
        assertFalse(Quiz.isEmpty(QuizSnapshot(day = day, daily = question())))
    }

    @Test
    fun `une reponse enregistree suffit a remplir l'ecran`() {
        // C'est le cas d'une réponse faite hors ligne : elle est à l'écran alors que la question
        // du jour n'a pas encore été relue.
        assertFalse(
            Quiz.isEmpty(
                QuizSnapshot(
                    day = day,
                    responses = listOf(
                        DailyResponse("q1", day, "a", "2026-10-04T08:00:00Z", question(), pending = true),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `un defi suffit a remplir l'ecran`() {
        assertFalse(Quiz.isEmpty(QuizSnapshot(day = day, challenges = listOf(challenge()))))
    }

    @Test
    fun `des quiz thematiques seuls ne remplissent pas l'ecran`() {
        // Ils ne servent qu'à l'écran de création d'un défi : leur présence ne rend pas vraie
        // l'absence de question du jour, et ne doit donc pas faire taire une panne.
        assertTrue(
            Quiz.isEmpty(QuizSnapshot(day = day, quizSets = listOf(ThemedQuiz("s1", "Titre", "Coran")))),
        )
    }
}
