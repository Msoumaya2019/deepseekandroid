package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.remote.DailyAnswerPayload
import com.msoumaya.deepseekandroid.core.data.remote.QuizSource
import com.msoumaya.deepseekandroid.core.model.QuizSnapshot
import kotlinx.coroutines.CompletableDeferred

// ---------------------------------------------------------------------------
// Doublure des tests du dépôt Quiz
// ---------------------------------------------------------------------------
// **Ce qu'elle mesure, et pourquoi elle le mesure.** Elle retient l'**ordre** de ses appels, et
// ce n'est pas du confort : l'ordre est la seule chose que `QuizRepository` décide vraiment. La
// file doit être vidée **avant** que l'instantané ne soit lu ; inverser ces deux étapes ne casse
// rien de visible — cela fait lire un instantané antérieur à l'envoi, donc sans la réponse qu'on
// vient de faire, et le symptôme serait une réponse qui « disparaît » une fois sur deux. Une
// doublure qui se contenterait de rendre des valeurs ne pourrait pas l'apercevoir.
//
// **Chaque refus est séparé.** L'instantané, l'envoi d'une réponse du jour, la création d'un
// défi, la réponse à un défi, les notifications : un échec de l'un n'a pas les mêmes conséquences
// que l'échec de l'autre, et une doublure qui refuserait tout d'un bloc ne les distinguerait pas.
//
// **Deux crochets, et une raison chacun.** [probe] photographie l'état publié au moment précis où
// le réseau est appelé — c'est la seule façon de mesurer que le disque est publié **avant** le
// premier appel, donc que l'application affiche le quiz sans connexion. [gate] suspend les appels
// pour tenir un geste en cours et éprouver qu'un second est ignoré, ce qui ne se mesure pas avec
// une doublure qui répond instantanément.
// ---------------------------------------------------------------------------

/** Doublure en mémoire du service Quiz. */
internal class FakeQuizSource : QuizSource {

    /** L'instantané que le serveur rendra. Son jour est ajusté à celui qui est demandé. */
    var snapshotValue: QuizSnapshot = QuizSnapshot(day = "2000-01-01")

    var failSnapshot: Throwable? = null
    var failAnswerDaily: Throwable? = null
    var failCreateChallenge: Throwable? = null
    var failAnswerChallenge: Throwable? = null
    var failSetNotifications: Throwable? = null

    /** Les appels, dans leur ordre, sous une forme lisible par un test. */
    val calls = mutableListOf<String>()

    /** Appelé au début de chaque appel réseau : permet de photographier l'état publié. */
    var probe: (() -> Unit)? = null

    /** Quand elle est posée, chaque appel attend qu'elle soit franchie. */
    var gate: CompletableDeferred<Unit>? = null

    val sentAnswers = mutableListOf<DailyAnswerPayload>()
    var snapshotCalls = 0
        private set

    var challengeId: String = "defi-0001"
    val createdChallenges = mutableListOf<Triple<String, Int, String?>>()
    val sentChallengeAnswers = mutableListOf<Triple<String, String, String>>()
    val sentNotifications = mutableListOf<Pair<Boolean, String>>()

    override suspend fun snapshot(day: String): QuizSnapshot {
        calls += "instantane"
        snapshotCalls++
        probe?.invoke()
        gate?.await()
        failSnapshot?.let { throw it }
        return snapshotValue.copy(day = day)
    }

    override suspend fun answerDaily(payload: DailyAnswerPayload) {
        calls += "reponse du jour"
        probe?.invoke()
        gate?.await()
        failAnswerDaily?.let { throw it }
        sentAnswers += payload
    }

    override suspend fun createChallenge(opponentId: String, count: Int, quizSetId: String?): String {
        calls += "creation de defi"
        gate?.await()
        failCreateChallenge?.let { throw it }
        createdChallenges += Triple(opponentId, count, quizSetId)
        return challengeId
    }

    override suspend fun answerChallenge(challengeId: String, questionId: String, answerId: String) {
        calls += "reponse de defi"
        gate?.await()
        failAnswerChallenge?.let { throw it }
        sentChallengeAnswers += Triple(challengeId, questionId, answerId)
    }

    override suspend fun setNotifications(enabled: Boolean, timezone: String) {
        calls += "notifications"
        gate?.await()
        failSetNotifications?.let { throw it }
        sentNotifications += enabled to timezone
    }
}
