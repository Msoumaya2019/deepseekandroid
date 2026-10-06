package com.msoumaya.deepseekandroid.feature.home

import com.msoumaya.deepseekandroid.core.domain.Program
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.QuranDataLoader
import com.msoumaya.deepseekandroid.core.domain.QuizText
import com.msoumaya.deepseekandroid.core.model.DailyResponse
import com.msoumaya.deepseekandroid.core.model.QuizAnswer
import com.msoumaya.deepseekandroid.core.model.QuizQuestion
import com.msoumaya.deepseekandroid.core.model.QuizSnapshot
import org.junit.BeforeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Les deux cartes de quiz de l'accueil
// ---------------------------------------------------------------------------
// Le calcul est une fonction pure de `(état, jour, instantané)`. Il est donc éprouvé ici sans
// coroutine, sans horloge et sans appareil, sur le **vrai** référentiel — `HomeRenderer.render`
// passe par `Quran.verseAt`, qui lève tant que le référentiel n'est pas chargé.
//
// ## Les deux conditions que ce fichier existe pour distinguer
//
// L'original écrit `done = responses.some(r => r.day === quizDay())` et
// `available = data.day === quizDay() && !!data.daily`. Ce ne sont **pas** deux façons de dire la
// même chose, et les confondre produit deux défauts muets :
//
//  - un instantané **d'hier** peut porter des réponses **d'aujourd'hui** — le joueur a répondu
//    depuis un autre appareil, ou le cache a été rafraîchi partiellement. La carte doit alors dire
//    « terminée », ce que `available` seul ne dirait pas ;
//  - un instantané **du jour** peut n'avoir **aucune question publiée** — l'administrateur n'a rien
//    programmé. La carte doit alors dire « Question du jour », et non « disponible », qui
//    promettrait une question inexistante.
//
// Les deux sens de l'erreur sont donc couverts, et chacun par un cas distinct.
//
// ## Le jour est fixé, jamais lu
//
// `today` est une constante, et tous les jours relatifs en dérivent. Un test qui lit l'horloge
// passe le lundi et échoue le dimanche — et c'est exactement ce que ce module vérifie ailleurs.
// ---------------------------------------------------------------------------

class HomeQuizCardsTest {

    private val today = "2026-03-10"
    private val yesterday = "2026-03-09"

    /** Une question du jour quelconque, publiée le jour fixé. */
    private fun question(id: String) = QuizQuestion(
        id = id,
        category = "Coran",
        question = "Combien de sourates compte le Coran ?",
        answers = listOf(QuizAnswer("a", "114"), QuizAnswer("b", "112")),
        publicationDate = today,
    )

    /** Une réponse **confirmée** au jour donné. */
    private fun response(questionId: String, day: String) = DailyResponse(
        questionId = questionId,
        day = day,
        selectedAnswerId = "a",
        answeredAt = "${day}T09:00:00.000Z",
        question = question(questionId),
        isCorrect = true,
    )

    private fun cards(snapshot: QuizSnapshot?) =
        HomeRenderer.render(Program.defaultState(), today, snapshot).quiz

    @Test
    fun `sans instantane lu les cartes annoncent la question du jour`() {
        // Le tout premier affichage, et le cas d'une application sans projet Supabase configuré.
        // Les cartes sont **là** quand même : ce sont des portes vers le Quiz, et les cacher
        // rendrait l'écran inatteignable depuis l'accueil.
        val cards = assertNotNull(cards(null), "Sans instantané, les deux cartes doivent exister.")

        assertEquals(QuizText.HOME_CARD_QUIZ, cards.quizTitle)
        assertEquals(QuizText.HOME_CARD_QUIZ_PENDING, cards.quizSub)
        assertEquals(QuizText.HOME_CARD_QUIZ_DETAIL, cards.quizDetail)
        assertFalse(cards.quizAlert, "Sans instantané, aucune question ne peut être signalée.")

        assertEquals(QuizText.HOME_CARD_FRIENDS, cards.friendsTitle)
        assertEquals(QuizText.HOME_CARD_FRIENDS_SUB, cards.friendsSub)
        assertEquals(QuizText.HOME_CARD_FRIENDS_DETAIL, cards.friendsDetail)
    }

    @Test
    fun `une question publiee et non repondue est annoncee disponible, pastille allumee`() {
        val cards = assertNotNull(cards(QuizSnapshot(day = today, daily = question("q1"))))

        assertEquals(QuizText.HOME_CARD_QUIZ_AVAILABLE, cards.quizSub)
        assertEquals(QuizText.HOME_CARD_QUIZ_DETAIL, cards.quizDetail)
        assertTrue(cards.quizAlert, "Une question disponible et non répondue doit être signalée.")
    }

    @Test
    fun `une question repondue est annoncee terminee, pastille eteinte`() {
        val snapshot = QuizSnapshot(
            day = today,
            daily = question("q1"),
            responses = listOf(response("q1", today)),
        )

        val cards = assertNotNull(cards(snapshot))

        assertEquals(QuizText.HOME_CARD_QUIZ_DONE, cards.quizSub)
        assertEquals(QuizText.HOME_CARD_QUIZ_DETAIL_DONE, cards.quizDetail)
        assertFalse(cards.quizAlert, "Une question déjà répondue ne doit plus être signalée.")
    }

    @Test
    fun `un instantane d'hier n'annonce pas la question du jour disponible`() {
        // La borne basse de `available` : l'instantané porte bien une question, mais celle
        // **d'hier**. L'annoncer disponible promettrait une question que le joueur ne peut pas
        // ouvrir — et la carte mènerait à une vue du jour vide.
        val cards = assertNotNull(cards(QuizSnapshot(day = yesterday, daily = question("q1"))))

        assertEquals(QuizText.HOME_CARD_QUIZ_PENDING, cards.quizSub)
        assertFalse(cards.quizAlert, "La question d'hier ne concerne pas le jour affiché.")
    }

    @Test
    fun `une reponse du jour suffit a dire terminee, meme si l'instantane date d'hier`() {
        // L'autre borne, celle que `available` seul manquerait : la réponse est bien datée
        // d'aujourd'hui, mais l'instantané — et donc la question qu'il porte — est d'hier. La
        // carte doit dire « terminée », parce que c'est la réponse qui décide, pas l'instantané.
        val snapshot = QuizSnapshot(day = yesterday, responses = listOf(response("q1", today)))

        val cards = assertNotNull(cards(snapshot))

        assertEquals(QuizText.HOME_CARD_QUIZ_DONE, cards.quizSub)
        assertEquals(QuizText.HOME_CARD_QUIZ_DETAIL_DONE, cards.quizDetail)
        // Aucune question n'est publiée dans cet instantané : rien à signaler, même si la carte
        // dit « terminée ». Les deux conditions restent indépendantes jusqu'au bout.
        assertFalse(cards.quizAlert)
    }

    @Test
    fun `une question publiee sans reponse ne dit pas terminee`() {
        // La réciproque du cas précédent : `available` vrai, `done` faux. Sans elle, une carte qui
        // confondrait les deux annoncerait « terminée » sur toute question publiée.
        val cards = assertNotNull(cards(QuizSnapshot(day = today, daily = question("q1"))))

        assertEquals(QuizText.HOME_CARD_QUIZ_AVAILABLE, cards.quizSub)
        assertEquals(QuizText.HOME_CARD_QUIZ_DETAIL, cards.quizDetail)
    }

    @Test
    fun `le jour du Quiz est celui qu'on passe, et non celui de l'horloge`() {
        // Le contrôle porte sur la **date d'entrée**, et il est mesuré des deux côtés : un
        // instantané daté du jour fixé est reconnu, le même daté de la veille ne l'est pas. Un
        // renderer qui lirait `Quiz.quizDay()` au lieu du paramètre passerait le premier cas et
        // échouerait le second — sauf le jour où le test tourne un 10 mars.
        val duJour = assertNotNull(cards(QuizSnapshot(day = today, daily = question("q1"))))
        val deLaVeille = assertNotNull(cards(QuizSnapshot(day = yesterday, daily = question("q1"))))

        assertTrue(duJour.quizAlert)
        assertFalse(deLaVeille.quizAlert)
    }

    @Test
    fun `les deux cartes empruntent leurs libelles a QuizText`() {
        // Aucun libellé n'est écrit dans le renderer. Le vérifier par la valeur, et non par une
        // lecture de source, est ce qui rend le contrôle utile : deux constantes peuvent porter le
        // même texte, et c'est la référence qui compte — mais une comparaison de **chaînes** ne
        // peut pas les distinguer. Le contrôle porte donc sur ce qui est observable ici : les
        // libellés rendus sont exactement ceux du domaine.
        val cards = assertNotNull(cards(null))

        assertEquals(QuizText.HOME_CARD_QUIZ, cards.quizTitle)
        assertEquals(QuizText.HOME_CARD_FRIENDS, cards.friendsTitle)
        assertEquals(QuizText.HOME_CARD_FRIENDS_SUB, cards.friendsSub)
        assertEquals(QuizText.HOME_CARD_FRIENDS_DETAIL, cards.friendsDetail)
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun chargerLeReferentiel() {
            Quran.initialize(QuranDataLoader.loadFromClasspath())
        }
    }
}
