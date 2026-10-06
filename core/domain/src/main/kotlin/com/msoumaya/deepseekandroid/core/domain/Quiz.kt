package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.ChallengeStatus
import com.msoumaya.deepseekandroid.core.model.DailyResponse
import com.msoumaya.deepseekandroid.core.model.QuizChallenge
import com.msoumaya.deepseekandroid.core.model.QuizQuestion
import com.msoumaya.deepseekandroid.core.model.QuizSnapshot
import com.msoumaya.deepseekandroid.core.model.QuizStats
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Règles du quiz : question du jour et défis entre amis.
 *
 * Porté depuis `src/core/quiz.ts`. Les questions elles-mêmes vivent côté serveur ; ce fichier
 * ne porte que les décisions prises sur le client (une participation par jour, statut d'un
 * défi, statistiques, fusion d'un instantané local avec celui du serveur).
 */
object Quiz {

    private val DAY_KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    val categories: List<String> get() = Texts.quizCategories

    fun emptySnapshot(day: String): QuizSnapshot = QuizSnapshot(day = day)

    /** Jour courant dans le fuseau indiqué, au format `AAAA-MM-JJ`. */
    fun quizDay(zone: ZoneId = Dates.zone()): String =
        LocalDate.now(zone).format(DAY_KEY)

    /**
     * Statut lisible d'un défi du point de vue d'un joueur.
     *
     * Le statut serveur prime ; l'expiration est ensuite déduite de `expiresAt`, ce qui rend
     * l'affichage correct même si le serveur n'a pas encore basculé le statut.
     */
    fun challengeStatus(challenge: QuizChallenge, userId: String, now: Long = System.currentTimeMillis()): String {
        if (challenge.status == ChallengeStatus.COMPLETED) return "Terminé"
        if (challenge.status == ChallengeStatus.EXPIRED ||
            Dates.parseIsoMillis(challenge.expiresAt) <= now
        ) {
            return "Expiré"
        }
        val answered = challenge.answers.count { it.userId == userId }
        return if (answered == challenge.questionCount) "En attente de l’ami" else "À toi de jouer"
    }

    /**
     * Statistiques agrégées.
     *
     * Seules les réponses **confirmées** comptent (`pending` faux et `isCorrect` connu) :
     * une réponse en attente de synchronisation ne doit pas gonfler le score affiché.
     */
    fun statistics(snapshot: QuizSnapshot, userId: String): QuizStats {
        val confirmed = snapshot.responses.filter { it.pending != true && it.isCorrect != null }
        val correct = confirmed.count { it.isCorrect == true }
        val finished = snapshot.challenges.filter { it.status == ChallengeStatus.COMPLETED }
        var wins = 0
        var ties = 0
        for (challenge in finished) {
            val own = challenge.answers.count { it.userId == userId && it.isCorrect == true }
            val other = challenge.answers.count { it.userId != userId && it.isCorrect == true }
            if (own > other) wins++
            if (own == other) ties++
        }
        return QuizStats(
            correct = correct,
            total = confirmed.size,
            rate = if (confirmed.isEmpty()) 0 else Math.round(correct.toDouble() / confirmed.size * 100).toInt(),
            played = finished.size,
            wins = wins,
            ties = ties,
        )
    }

    /**
     * Fusionne l'instantané serveur et l'instantané local.
     *
     * Une réponse locale **en attente** est conservée si le serveur ne connaît pas déjà ce
     * jour : c'est ce qui évite de perdre une participation faite hors ligne, sans jamais
     * écraser une réponse confirmée par le serveur.
     */
    fun mergeSnapshot(remote: QuizSnapshot, local: QuizSnapshot): QuizSnapshot {
        val byDay = LinkedHashMap<String, DailyResponse>()
        for (r in remote.responses) byDay[r.day] = r
        for (r in local.responses) {
            if (r.pending == true && !byDay.containsKey(r.day)) byDay[r.day] = r
        }
        val merged = byDay.values.sortedByDescending { it.day }
        return remote.copy(responses = merged)
    }

    /**
     * Enregistre la réponse du jour.
     *
     * Une seule participation par jour : si une réponse existe déjà pour ce jour, l'état est
     * rendu inchangé. La réponse est marquée `pending` jusqu'à confirmation du serveur.
     */
    fun recordDailyAnswer(
        snapshot: QuizSnapshot,
        question: QuizQuestion,
        answerId: String,
        day: String,
        answeredAt: String,
    ): QuizSnapshot {
        if (snapshot.responses.any { it.day == day }) return snapshot
        if (question.answers.none { it.id == answerId } || question.publicationDate != day) {
            error("Réponse ou date invalide.")
        }
        val response = DailyResponse(
            questionId = question.id,
            day = day,
            selectedAnswerId = answerId,
            answeredAt = answeredAt,
            question = question,
            pending = true,
        )
        return snapshot.copy(responses = listOf(response) + snapshot.responses)
    }

    /**
     * Vrai si l'instantané ne porte rien de montrable : ni question du jour, ni réponse, ni défi.
     *
     * **La question ne se pose qu'à cause d'un échec, jamais toute seule.** Un instantané vide
     * après une lecture **réussie** est une information : « aucune question n'est publiée
     * aujourd'hui », ce que l'écran sait déjà dire. Le même instantané vide après une lecture
     * **ratée** ne dit rien du tout — l'écran ignore si la question manque ou s'il n'a pas pu la
     * lire, et afficher « Aucune question publiée » serait alors une affirmation fausse, faite
     * au pire moment. C'est ce partage que le dépôt fait, et c'est la seule raison d'être de
     * cette fonction.
     *
     * Les quiz thématiques ne comptent pas : ils ne servent qu'à l'écran de création d'un défi,
     * où leur absence n'annonce rien de faux.
     */
    fun isEmpty(snapshot: QuizSnapshot): Boolean =
        snapshot.daily == null && snapshot.responses.isEmpty() && snapshot.challenges.isEmpty()

    /** Code PostgREST d'une fonction absente du schéma : la migration n'est pas déployée. */
    private const val MISSING_FUNCTION_CODE = "PGRST202"

    /**
     * Message lisible d'une erreur du service Quiz.
     *
     * La **règle d'ordre** est celle de [Social.errorText] — un message explicite d'abord, et le
     * repli seulement s'il n'y en a pas —, mais le repli **diffère** : voir
     * [QuizText.GENERIC_ERROR]. Un message fait d'espaces ne compte pas, sans quoi l'écran
     * afficherait un cadre vide là où il y a une erreur à comprendre.
     *
     * **Le code prime sur le message, et c'est l'ordre de l'original.** `quizRpc` de
     * `services/quiz.ts` teste `error.code === 'PGRST202'` **avant** de retomber sur le texte du
     * serveur. Ce code ne décrit pas la panne d'un appel : il dit que la fonction n'existe pas
     * dans le schéma — donc que la migration du Quiz n'est pas déployée. C'est un diagnostic
     * d'installation, et le texte du serveur le noie sous des détails de PostgREST.
     *
     * @param message texte du serveur, ou `null`.
     * @param code code PostgREST, ou `null` quand l'erreur n'en porte pas.
     */
    fun errorText(message: String? = null, code: String? = null): String = when {
        code == MISSING_FUNCTION_CODE -> QuizText.SERVICE_MISSING
        !message.isNullOrBlank() -> message
        else -> QuizText.GENERIC_ERROR
    }
}
