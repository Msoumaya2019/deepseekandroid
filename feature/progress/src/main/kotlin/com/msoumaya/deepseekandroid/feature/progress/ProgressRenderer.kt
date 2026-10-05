package com.msoumaya.deepseekandroid.feature.progress

import com.msoumaya.deepseekandroid.core.domain.Activity
import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.Program
import com.msoumaya.deepseekandroid.core.domain.ProgramText
import com.msoumaya.deepseekandroid.core.domain.ProgressText
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.SessionStatus
import com.msoumaya.deepseekandroid.core.model.StudyMode
import com.msoumaya.deepseekandroid.core.model.effectiveReviewHistory
import com.msoumaya.deepseekandroid.core.model.effectiveStudyProgress

// ---------------------------------------------------------------------------
// Calcul de l'écran « Progrès »
// ---------------------------------------------------------------------------
// Portage de `ProgressScreen` (`src/ui/MainScreens.tsx:46`). Tout le calcul vit ici parce qu'il
// est pur : il s'éprouve sans coroutine, sans horloge et sans appareil, sur le vrai référentiel.
//
// **Ce que le renderer ne fait pas.** Il n'écrit rien, et il ne touche pas à la période : celle-ci
// lui est donnée. Le client d'origine recalcule tout le composable à chaque rendu, y compris le
// balayage des 604 pages ; ici le balayage a lieu une fois par changement d'état ou de période.
//
// **Deux nombres qui se ressemblent et ne sont pas le même.** « Révisions faites » compte les
// entrées de `reviewHistory` — c'est ce que fait l'écran d'origine —, alors que le tableau de bord
// des révisions affiche `Stats.revisions`, la somme des `completedCount`. Les deux existent dans
// le dépôt d'origine et ne s'accordent pas ; les reproduire à l'identique vaut mieux que d'en
// choisir un, parce que l'écran doit dire la même chose des deux côtés.
// ---------------------------------------------------------------------------

/**
 * Une plage travaillée, et le jour où elle l'a été.
 *
 * Sert au graphique, qui somme `end - start + 1` par fenêtre. C'est la même unité que partout
 * ailleurs — le nombre de versets —, et non un nombre de séances : deux séances de trois versets
 * le même jour font bien six.
 */
private data class Event(val start: Int, val end: Int, val date: String)

/** Calcul de l'écran « Progrès ». */
internal object ProgressRenderer {

    /**
     * Prépare l'affichage.
     *
     * @param state l'état du compte.
     * @param period la période choisie : elle décide de la fenêtre des versets appris et de la
     *   largeur du graphique.
     * @param at jour courant, injectable pour que les tests ne dépendent pas de l'horloge.
     */
    fun render(
        state: AppState,
        period: ProgressText.Period,
        at: String = Dates.todayLocal(),
    ): ProgressUiState {
        val progress = Program.progress(state)
        val stats = Program.stats(state, at)
        val known = Program.memorizedIds(state).toSet()
        val activity = Activity.activity(state, at)

        return ProgressUiState(
            loading = false,
            period = period,
            ring = Ring(
                ratio = progress.quran.toFloat(),
                percent = ProgressText.percent(Math.round(progress.quran * 100).toInt()),
                knownVerses = known.size,
                // `verses.size`, et **non** `totalVolume` : ce dernier est la somme des poids en
                // lettres arabes — l'unité des pourcentages —, et il vaut 320 543 sur le
                // référentiel livré. L'afficher donnerait « / 320543 » sous un compteur de versets.
                totalVerses = Quran.verses.size,
            ),
            learned = Stat(
                title = ProgressText.learnedTitle(period),
                value = when (period) {
                    ProgressText.Period.DAY -> stats.today
                    ProgressText.Period.WEEK -> stats.week
                    ProgressText.Period.MONTH -> stats.month
                }.toString(),
            ),
            regularity = Stat(
                title = ProgressText.REGULARITY,
                value = ProgressText.streak(activity.streak),
            ),
            memorizedPages = Stat(
                title = ProgressText.MEMORIZED_PAGES,
                value = Quran.pages.count { Quran.full(known, Quran.pageRange(it.page)) }.toString(),
            ),
            revisions = Stat(
                title = ProgressText.REVISIONS_DONE,
                value = state.effectiveReviewHistory.size.toString(),
            ),
            graph = graph(state, period, at),
            goal = GoalLine(
                label = state.goal.label,
                ratio = progress.goal.toFloat(),
                percent = ProgressText.percent(Math.round(progress.goal * 100).toInt()),
            ),
            counters = listOf(
                Counter(CounterKind.JUZ, Quran.juzs.count { Quran.full(known, it.range) }),
                Counter(CounterKind.ACTIVE_DAYS, activity.dates.size),
                Counter(CounterKind.PAGES_READ, pagesRead(state)),
                Counter(CounterKind.VERSES, known.size),
            ),
        )
    }

    /**
     * Nombre de pages lues.
     *
     * La distinction entre « jamais écrit » et « écrit, vide » est **conservée** : le client
     * d'origine affiche une page dès qu'une dernière lecture existe, même si la liste des pages
     * lues est absente — c'est le cas d'un état venu d'un ancien schéma. Passer par
     * `effectiveReadPages`, qui remplace `null` par une liste vide, afficherait donc zéro sur une
     * lecture qui a bien eu lieu.
     *
     * La liste est liée à une variable locale avant le test : `readPages` appartient à
     * `core:model`, donc à un autre module, et Kotlin ne peut pas en affiner le type à travers un
     * `when` — la propriété est publique et pourrait, en théorie, changer entre deux lectures.
     */
    private fun pagesRead(state: AppState): Int {
        val pages = state.readPages
        return when {
            pages != null -> pages.size
            state.lastRead != null -> 1
            else -> 0
        }
    }

    /**
     * Graphique de la période.
     *
     * Trois fenêtres, et une seule forme de calcul :
     *
     *  - **Jour** : sept fenêtres d'un jour, qui finissent aujourd'hui ;
     *  - **Semaine** : sept fenêtres d'un jour, du lundi au dimanche de la semaine en cours ;
     *  - **Mois** : cinq fenêtres de sept jours, à partir du 1er du mois.
     *
     * **La dernière fenêtre du mois peut dépasser la fin du mois** — du 29 au 4 du mois suivant
     * pour un mois de 31 jours. C'est le calcul du client d'origine, et il est conservé tel quel
     * plutôt que corrigé : aucune plage n'est datée après le jour courant, puisque `completedDate`
     * est toujours posé à aujourd'hui par les deux appelants de `Program.completeSession`. Le
     * débordement ne compte donc rien — mais il faut le savoir avant de le croire faux.
     */
    private fun graph(state: AppState, period: ProgressText.Period, at: String): Graph {
        val events = events(state)
        val monthly = period == ProgressText.Period.MONTH
        val count = if (monthly) 5 else 7
        val step = if (monthly) 7 else 1

        val base = when (period) {
            ProgressText.Period.MONTH -> at.substring(0, 7) + "-01"
            ProgressText.Period.DAY -> Dates.addDays(at, -6)
            ProgressText.Period.WEEK -> Dates.addDays(at, -((Dates.dayOf(at) + 6) % 7))
        }

        val windows = (0 until count).map { index ->
            val start = Dates.addDays(base, index * step)
            start to if (monthly) Dates.addDays(start, 6) else start
        }

        val values = windows.map { (start, end) ->
            events.filter { it.date >= start && it.date <= end }.sumOf { it.end - it.start + 1 }
        }

        // Le maximum vaut au moins 1 : une période sans rien appris ne doit pas diviser par zéro.
        val max = maxOf(1, values.maxOrNull() ?: 0)

        return Graph(
            title = ProgressText.graphTitle(period),
            bars = windows.mapIndexed { index, (start, _) ->
                Bar(
                    label = if (monthly) {
                        ProgressText.weekLabel(index)
                    } else {
                        ProgramText.weekdayInitials(start)
                    },
                    value = values[index],
                    ratio = values[index].toFloat() / max,
                )
            },
        )
    }

    /**
     * Les plages travaillées, avec leur date.
     *
     * Deux sources, et la seconde évite un double compte :
     *
     *  - une séance **terminée** qui n'a **pas** de suivi fin — sinon ses versets seraient comptés
     *    deux fois, une fois par la séance et une fois par ses validations ;
     *  - chaque **validation** d'une ligne d'apprentissage.
     *
     * Une séance terminée est datée par `completedDate`, à défaut par `completedAt`, à défaut par
     * sa date prévue. C'est une règle **plus large** que celle de `Program.stats`, qui exige un
     * `completedAt` : le graphique compte donc une séance que le compteur « Versets appris »
     * ignore. Les deux règles viennent du client d'origine, et elles sont reproduites telles
     * quelles.
     */
    private fun events(state: AppState): List<Event> {
        val tracked = state.effectiveStudyProgress.values.filter { it.mode == StudyMode.LEARNING }
        val trackedIds = tracked.map { it.id }.toSet()

        val done = state.sessions
            .filter { it.status == SessionStatus.DONE && it.id !in trackedIds }
            .map { Event(it.start, it.end, it.completedDate ?: it.completedAt?.take(10) ?: it.date) }

        val validations = tracked.flatMap { record ->
            record.validations.map { Event(it.start, it.end, it.date) }
        }

        return done + validations
    }
}
