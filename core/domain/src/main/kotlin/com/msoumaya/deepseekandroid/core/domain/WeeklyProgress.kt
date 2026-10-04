package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.Session
import com.msoumaya.deepseekandroid.core.model.StudyStatus

/**
 * Programme à venir et objectif hebdomadaire.
 *
 * Porté depuis `src/core/weeklyProgress.ts`.
 *
 * Deux règles de comportement :
 *  - [upcomingSessions] n'affiche que les 10 prochains jours, **sans jamais supprimer** la
 *    planification au-delà : les séances plus lointaines restent en base ;
 *  - [weeklyProgress] travaille en calendrier local, du lundi 00:00 au dimanche 23:59,
 *    changement d'heure inclus. À la nouvelle semaine l'affichage repart de zéro, sans
 *    toucher à l'historique.
 */
object WeeklyProgress {

    fun scheduledDate(session: Session): String = session.scheduledDate ?: session.date

    /** Séances à venir dans la fenêtre [at, at + 10 jours]. */
    fun upcomingSessions(state: AppState, at: String = Dates.todayLocal()): List<Session> {
        val limit = Dates.addDays(at, 10)
        return state.sessions.filter {
            it.status == com.msoumaya.deepseekandroid.core.model.SessionStatus.TODO &&
                scheduledDate(it) >= at && scheduledDate(it) <= limit
        }
    }

    data class WeekProgress(
        val weekStart: String,
        val weekEnd: String,
        val total: Int,
        val done: Int,
        val ratio: Double,
    )

    fun weeklyProgress(state: AppState, at: String = Dates.todayLocal()): WeekProgress {
        val start = Dates.addDays(at, -((Dates.dayOf(at) + 6) % 7))
        val end = Dates.addDays(start, 6)
        val sessions = state.sessions.filter {
            val d = scheduledDate(it)
            d >= start && d <= end
        }
        val done = sessions.count { it.status == com.msoumaya.deepseekandroid.core.model.SessionStatus.DONE }
        return WeekProgress(
            weekStart = start,
            weekEnd = end,
            total = sessions.size,
            done = done,
            ratio = if (sessions.isEmpty()) 0.0 else done.toDouble() / sessions.size,
        )
    }

    /** Statut d'affichage d'une séance. */
    fun sessionStatus(state: AppState, session: Session): String = when {
        session.status == com.msoumaya.deepseekandroid.core.model.SessionStatus.DONE -> "completed"
        session.status == com.msoumaya.deepseekandroid.core.model.SessionStatus.POSTPONED -> "skipped"
        state.studyProgress?.get("learning:${session.id}")?.status == StudyStatus.PARTIAL -> "partiallyCompleted"
        else -> "pending"
    }
}
