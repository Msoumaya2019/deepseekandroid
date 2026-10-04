package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.QuranData
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.Session
import com.msoumaya.deepseekandroid.core.model.SessionStatus
import com.msoumaya.deepseekandroid.core.model.Pace

/**
 * Référentiel coranique réel, partagé par toute la suite de tests.
 *
 * Les tests mesurent la vraie logique sur les vraies données (6 236 versets) plutôt que sur
 * une maquette : c'est la seule façon de détecter une erreur d'indexation ou une borne
 * fausse, qu'une maquette de trois versets laisserait passer.
 */
object QuranFixture {

    val reference: QuranData by lazy { QuranDataLoader.loadFromClasspath() }

    /** Installe le référentiel dans [Quran]. Idempotent, le chargement est fait une seule fois. */
    fun install() {
        Quran.initialize(reference)
    }

    /** État minimal terminé, prêt à recevoir un programme. */
    fun onboardedState(): AppState = Program.defaultState().copy(onboardingDone = true)

    fun session(
        id: String,
        date: String,
        start: Int,
        end: Int,
        status: SessionStatus = SessionStatus.TODO,
        scheduledDate: String? = date,
        pace: Pace = Pace.VERSE3,
    ): Session = Session(
        id = id,
        date = date,
        start = start,
        end = end,
        unit = pace,
        status = status,
        scheduledDate = scheduledDate,
    )

    fun range(start: Int, end: Int): Range = Range(start, end)
}
