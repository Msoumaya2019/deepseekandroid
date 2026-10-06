package com.msoumaya.deepseekandroid.feature.progress

import com.msoumaya.deepseekandroid.core.domain.Program
import com.msoumaya.deepseekandroid.core.domain.ProgressText
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.QuranDataLoader
import com.msoumaya.deepseekandroid.core.domain.QuizText
import com.msoumaya.deepseekandroid.core.model.ChallengeAnswer
import com.msoumaya.deepseekandroid.core.model.ChallengeStatus
import com.msoumaya.deepseekandroid.core.model.DailyResponse
import com.msoumaya.deepseekandroid.core.model.QuizAnswer
import com.msoumaya.deepseekandroid.core.model.QuizChallenge
import com.msoumaya.deepseekandroid.core.model.QuizQuestion
import com.msoumaya.deepseekandroid.core.model.QuizSnapshot
import org.junit.BeforeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// ---------------------------------------------------------------------------
// Le bloc « Quiz » de l'écran « Progrès »
// ---------------------------------------------------------------------------
// Le calcul est une fonction pure de `(état, période, jour, instantané, compte)`. Il est éprouvé
// ici sans coroutine, sans horloge et sans appareil, sur le **vrai** référentiel —
// `ProgressRenderer.render` balaie les 604 pages du moushaf.
//
// ## Ce que ce fichier surveille, et que le comptage du domaine ne peut pas voir
//
// Les règles de comptage elles-mêmes — quelles réponses sont « confirmées », quels défis sont
// « terminés » — vivent dans `Quiz.statistics` et s'éprouvent dans `QuizTest`. Ce qui est mesuré
// ici, c'est le **passage** : que l'instantané arrive au comptage, que l'identifiant du compte
// arrive du bon côté, et que les trois lignes soient mises en forme par `QuizText` et non
// recopiées.
//
// ## Le compte nul
//
// L'original écrit `quizStatistics(useQuiz(userId), userId ?? '')` : sans compte, l'identifiant est
// la chaîne vide, et aucun défi n'est gagné — ce qui est exact, puisque personne n'a joué. Le
// renderer reproduit ce repli, et le test le mesure.
// ---------------------------------------------------------------------------

class ProgressQuizStatsTest {

    private val me = "moi"
    private val other = "autre"

    /** Une question quelconque. */
    private fun question(id: String) = QuizQuestion(
        id = id,
        category = "Coran",
        question = "Combien de sourates compte le Coran ?",
        answers = listOf(QuizAnswer("a", "114"), QuizAnswer("b", "112")),
    )

    /**
     * Une réponse au jour donné.
     *
     * `pending` et `isCorrect` sont laissés à leur défaut : la réponse n'est donc **pas**
     * confirmée, et `Quiz.statistics` doit l'ignorer. Les tests qui veulent la compter passent par
     * [confirmee].
     */
    private fun reponse(day: String, correct: Boolean? = null, pending: Boolean? = null) =
        DailyResponse(
            questionId = "q-$day",
            day = day,
            selectedAnswerId = "a",
            answeredAt = "${day}T09:00:00.000Z",
            question = question("q-$day"),
            isCorrect = correct,
            pending = pending,
        )

    private fun confirmee(day: String, correct: Boolean) =
        reponse(day = day, correct = correct, pending = false)

    /**
     * Un défi terminé où [own] bonnes réponses sont les miennes et [theirs] celles de l'adversaire.
     */
    private fun defi(own: Int, theirs: Int, status: ChallengeStatus = ChallengeStatus.COMPLETED) =
        QuizChallenge(
            id = "c1",
            creatorId = me,
            opponentId = other,
            creatorName = "Moi",
            opponentName = "Autre",
            questionCount = 10,
            createdAt = "2026-03-01T09:00:00.000Z",
            expiresAt = "2026-03-03T09:00:00.000Z",
            status = status,
            questions = List(own + theirs) { question("cq$it") },
            answers = buildList {
                repeat(own) { add(ChallengeAnswer(userId = me, questionId = "cq$it", answeredAt = "2026-03-02T09:00:00.000Z", isCorrect = true)) }
                repeat(theirs) { add(ChallengeAnswer(userId = other, questionId = "co$it", answeredAt = "2026-03-02T10:00:00.000Z", isCorrect = true)) }
            },
        )

    private fun resume(snapshot: QuizSnapshot?, userId: String? = me) =
        ProgressRenderer.render(
            state = Program.defaultState(),
            period = ProgressText.Period.WEEK,
            at = "2026-03-10",
            quiz = snapshot,
            userId = userId,
        ).quiz

    @Test
    fun `sans instantane lu le bloc n'existe pas`() {
        // « 0 bonne réponse / 0 » se lirait « tu n'as jamais joué », ce qui est une affirmation sur
        // la personne — et elle serait fausse pendant la seconde qui suit le démarrage. Le bloc
        // disparaît donc au lieu de mentir.
        assertNull(
            resume(null),
            "Sans instantané lu, le bloc de quiz ne doit pas exister : des zéros affirmeraient " +
                "que la personne n'a jamais joué.",
        )
    }

    @Test
    fun `un instantane vide affiche trois lignes a zero`() {
        // Le pendant du test précédent : **lu, et vide** n'est pas la même chose que **pas lu**.
        // Ici, « 0 / 0 » est exact.
        val summary = assertNotNull(
            resume(QuizSnapshot(day = "2026-03-10")),
            "Un instantané lu, même vide, doit produire un bloc : c'est la distinction entre " +
                "« pas encore lu » et « lu, et rien à compter ».",
        )

        assertEquals(QuizText.STATS_TITLE, summary.title)
        assertEquals(QuizText.statsDaily(0, 0), summary.daily)
        assertEquals(QuizText.statsRate(0), summary.rate)
        assertEquals(QuizText.statsChallenges(0, 0, 0), summary.challenges)
    }

    @Test
    fun `seules les reponses confirmees entrent dans le score`() {
        // La règle du domaine, mesurée **à travers le renderer** : une réponse en attente de
        // synchronisation ne doit pas gonfler le score affiché. Sans ce test, un renderer qui
        // compterait lui-même les réponses passerait les autres cas et échouerait ici.
        val snapshot = QuizSnapshot(
            day = "2026-03-10",
            responses = listOf(
                confirmee("2026-03-10", correct = true),
                confirmee("2026-03-09", correct = false),
                reponse("2026-03-08", correct = true, pending = true),
                reponse("2026-03-07"),
            ),
        )

        val summary = assertNotNull(resume(snapshot))

        // Deux confirmées, dont une bonne : 1 / 2, soit 50 %.
        assertEquals(QuizText.statsDaily(1, 2), summary.daily)
        assertEquals(QuizText.statsRate(50), summary.rate)
    }

    @Test
    fun `un defi termine gagne compte une victoire`() {
        val snapshot = QuizSnapshot(day = "2026-03-10", challenges = listOf(defi(own = 3, theirs = 1)))

        val summary = assertNotNull(resume(snapshot))

        assertEquals(QuizText.statsChallenges(1, 1, 0), summary.challenges)
    }

    @Test
    fun `un defi termine a egalite compte une egalite, pas une victoire`() {
        val snapshot = QuizSnapshot(day = "2026-03-10", challenges = listOf(defi(own = 2, theirs = 2)))

        val summary = assertNotNull(resume(snapshot))

        assertEquals(QuizText.statsChallenges(1, 0, 1), summary.challenges)
    }

    @Test
    fun `un defi non termine n'entre pas dans les defis joues`() {
        // Un défi en cours n'a pas de vainqueur : le compter annoncerait une victoire qui n'existe
        // pas encore, et sur un défi où l'adversaire mène, il annoncerait même une défaite.
        val snapshot = QuizSnapshot(
            day = "2026-03-10",
            challenges = listOf(
                defi(own = 3, theirs = 0, status = ChallengeStatus.PENDING),
                defi(own = 0, theirs = 3, status = ChallengeStatus.EXPIRED),
            ),
        )

        val summary = assertNotNull(resume(snapshot))

        assertEquals(QuizText.statsChallenges(0, 0, 0), summary.challenges)
    }

    @Test
    fun `le compte decide de quel cote se lit le defi`() {
        // Le même défi, lu par l'un puis par l'autre : 3 bonnes réponses contre 1. Du côté de
        // « moi » c'est une victoire, du côté de « autre » c'est une défaite — et l'original écrit
        // `own = answers.filter(a => a.userId === userId).length`. Un renderer qui passerait un
        // identifiant vide inverserait les deux lectures sans que rien ne le dise.
        val snapshot = QuizSnapshot(day = "2026-03-10", challenges = listOf(defi(own = 3, theirs = 1)))

        assertEquals(QuizText.statsChallenges(1, 1, 0), assertNotNull(resume(snapshot, me)).challenges)
        assertEquals(QuizText.statsChallenges(1, 0, 0), assertNotNull(resume(snapshot, other)).challenges)
    }

    @Test
    fun `sans compte aucun defi n'est gagne`() {
        // Le repli de l'original : `userId ?? ''`. La chaîne vide ne correspond à aucun joueur,
        // donc `own` vaut toujours zéro. Le défi est bien compté comme **joué** — il est terminé —
        // mais il n'est ni gagné ni à égalité.
        val snapshot = QuizSnapshot(day = "2026-03-10", challenges = listOf(defi(own = 3, theirs = 1)))

        val summary = assertNotNull(resume(snapshot, userId = null))

        assertEquals(QuizText.statsChallenges(1, 0, 0), summary.challenges)
    }

    @Test
    fun `le bloc ne depend pas de la periode choisie`() {
        // C'est un cumul depuis le début, et non une fenêtre : le poser sous le sélecteur
        // « Jour / Semaine / Mois » laisserait croire qu'il change avec lui. Le contrôle compare
        // les trois périodes **sur le même instantané**, et exige le même résultat.
        val snapshot = QuizSnapshot(
            day = "2026-03-10",
            responses = listOf(confirmee("2026-03-10", correct = true)),
            challenges = listOf(defi(own = 2, theirs = 1)),
        )

        val lignes = ProgressText.Period.entries.map { periode ->
            ProgressRenderer.render(
                state = Program.defaultState(),
                period = periode,
                at = "2026-03-10",
                quiz = snapshot,
                userId = me,
            ).quiz?.daily
        }

        assertEquals(
            1,
            lignes.distinct().size,
            "Le bloc de quiz change avec la période ($lignes) : c'est un cumul depuis le début, " +
                "et il ne doit pas suivre le sélecteur.",
        )
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun chargerLeReferentiel() {
            Quran.initialize(QuranDataLoader.loadFromClasspath())
        }
    }
}
