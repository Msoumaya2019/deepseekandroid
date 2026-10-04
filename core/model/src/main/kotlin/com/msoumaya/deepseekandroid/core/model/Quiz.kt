package com.msoumaya.deepseekandroid.core.model

import kotlinx.serialization.Serializable

/**
 * Modèle du quiz.
 *
 * Porté depuis `src/core/quiz.ts`. Les questions, les réponses du jour et les défis vivent
 * côté serveur (tables `quiz_*` et fonctions RPC) ; ce modèle ne décrit que ce que le
 * client manipule et met en cache.
 */

/** Une proposition de réponse. */
@Serializable
data class QuizAnswer(val id: String, val text: String)

/** Une question à choix multiple. */
@Serializable
data class QuizQuestion(
    val id: String,
    val category: String,
    val question: String,
    val answers: List<QuizAnswer>,
    val publicationDate: String? = null,
    val explanation: String? = null,
    val sourceTitle: String? = null,
    val sourceReference: String? = null,
    val sourceUrl: String? = null,
    val arabic: String? = null,
    val translation: String? = null,
    val surah: Int? = null,
    val ayah: Int? = null,
    /** Absent pour une question de défi tant que le joueur n'a pas répondu. */
    val correctAnswerId: String? = null,
    val isDailyQuestion: Boolean? = null,
    val availableForChallenges: Boolean? = null,
    val isActive: Boolean? = null,
)

/** Réponse à la question du jour. Une seule participation par jour et par utilisateur. */
@Serializable
data class DailyResponse(
    val questionId: String,
    val day: String,
    val selectedAnswerId: String,
    val answeredAt: String,
    val question: QuizQuestion,
    val isCorrect: Boolean? = null,
    /** `true` tant que le serveur n'a pas confirmé la réponse. */
    val pending: Boolean? = null,
)

/** Réponse d'un joueur à une question de défi. */
@Serializable
data class ChallengeAnswer(
    val userId: String,
    val questionId: String,
    val selectedAnswerId: String,
    val answeredAt: String,
    val isCorrect: Boolean? = null,
)

/** Défi asynchrone entre deux amis. Les deux joueurs reçoivent les mêmes questions. */
@Serializable
data class QuizChallenge(
    val id: String,
    val creatorId: String,
    val opponentId: String,
    val creatorName: String,
    val opponentName: String,
    /** 5 ou 10. La valeur par défaut est 10. */
    val questionCount: Int,
    val createdAt: String,
    val expiresAt: String,
    val status: ChallengeStatus,
    val questions: List<QuizQuestion>,
    val answers: List<ChallengeAnswer> = emptyList(),
    val completedAt: String? = null,
    val creatorAvatar: String? = null,
    val opponentAvatar: String? = null,
)

/** Quiz thématique préparé par l'administration : exactement 10 questions. */
@Serializable
data class ThemedQuiz(
    val id: String,
    val title: String,
    val category: String,
    val questionIds: List<String>? = null,
    val isActive: Boolean? = null,
)

/** Instantané complet du quiz, mis en cache localement par utilisateur. */
@Serializable
data class QuizSnapshot(
    val day: String,
    val daily: QuizQuestion? = null,
    val responses: List<DailyResponse> = emptyList(),
    val challenges: List<QuizChallenge> = emptyList(),
    val quizSets: List<ThemedQuiz>? = null,
    val notificationsEnabled: Boolean? = null,
)

/** Statistiques agrégées. */
@Serializable
data class QuizStats(
    val correct: Int,
    val total: Int,
    val rate: Int,
    val played: Int,
    val wins: Int,
    val ties: Int,
)
