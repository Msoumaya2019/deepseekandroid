package com.msoumaya.deepseekandroid.feature.program

import com.msoumaya.deepseekandroid.core.domain.Program
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.Review
import com.msoumaya.deepseekandroid.core.domain.ReviewText
import com.msoumaya.deepseekandroid.core.domain.StudySession
import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.ReviewCategory
import com.msoumaya.deepseekandroid.core.model.ReviewCycle
import com.msoumaya.deepseekandroid.core.model.StudyMode
import com.msoumaya.deepseekandroid.core.model.StudyStatus
import com.msoumaya.deepseekandroid.core.model.effectiveReviewHistory
import com.msoumaya.deepseekandroid.core.model.effectiveStudyProgress

// ---------------------------------------------------------------------------
// Calcul du tableau de bord des révisions
// ---------------------------------------------------------------------------
// Portage de `ReviewDashboard.tsx`. Le calcul est séparé du `ViewModel` à dessein : c'est une
// fonction pure de `(état, jour)`, donc éprouvable sans coroutine, sans horloge et sans appareil.
//
// **Le plan vient du domaine, et rien n'est recalculé ici.** `Review.reviewPlan` porte déjà
// l'ordre de la séance, le découpage des plages, les trois mécanismes et les trois comptes de
// division. Ce fichier ne fait que le **mettre en mots** : il appelle `ReviewText` pour chaque
// libellé et `Quran.reference` pour chaque plage. Une règle recalculée ici finirait par diverger
// de celle qui décide de la séance — et c'est la seconde qui écrit la progression.
//
// **Ce que ce fichier décide, et qui ne se voit pas.** Trois points portent à conséquence :
//
//   1. **Deux comptes différents pour les versets difficiles.** Le résumé compte `plan.priority`,
//      c'est-à-dire les versets **dus aujourd'hui** ; la carte « À retravailler » liste
//      `plan.rework`, c'est-à-dire **tous** les versets marqués difficiles. Les confondre ferait
//      annoncer « 3 à retravailler » devant une liste de quarante, ou l'inverse.
//
//   2. **Le pourcentage est un rapport de poids, et non de versets.** Un verset long pèse plus
//      qu'un verset court ; c'est ce qui rend « 43 % réellement révisés » comparable à l'effort
//      fourni, et non au nombre de lignes cochées. Le diviseur est borné à un millionième pour
//      qu'un corpus vide rende `0` et non une division par zéro.
//
//   3. **Une ligne de consolidation porte une identité construite.** Son identifiant est
//      `consolidation-<début>-<fin>` et sa catégorie est `recent`, exactement comme le client
//      d'origine la fabrique avant d'ouvrir le lecteur. C'est cette identité qui décide de
//      l'étape validée : la lire sur la ligne de la liste, et non sur une plage, est ce qui fait
//      qu'une consolidation se valide au bon offset.
//
// **Le référentiel coranique doit être chargé avant d'appeler [render].** Tout passe par `Quran`,
// qui rend un référentiel vide tant qu'il n'est pas initialisé. C'est au `ViewModel` de ne pas
// appeler avant que l'état ne soit prêt.
// ---------------------------------------------------------------------------

internal object ReviewDashboardRenderer {

    /**
     * Combien de lignes une carte dépliable montre avant de se replier.
     *
     * Le client d'origine coupe à cinq, sur les deux cartes, et propose alors un bouton
     * « Voir toutes… ». Ce n'est pas une pagination : c'est un repli, et les lignes cachées sont
     * déjà calculées. Le seuil est **strictement supérieur** — une liste de cinq lignes exactement
     * n'affiche pas de bouton, puisqu'il n'y aurait rien à déplier.
     */
    private const val COLLAPSED_LIMIT = 5

    /**
     * Calcule l'état affichable du tableau de bord.
     *
     * @param state état applicatif persisté.
     * @param at jour courant, au format `AAAA-MM-JJ`.
     */
    fun render(state: AppState, at: String): ReviewDashboardUiState {
        val plan = Review.reviewPlan(state, at)
        val cycle = plan.cycle
        val settings = state.reviewSettings
        val lengthDays = cycle?.lengthDays ?: DEFAULT_CYCLE_DAYS
        val memorized = Program.memorizedIds(state).size
        val ratio = completedRatio(cycle)
        val done = cycle?.completed?.toSet() ?: emptySet()

        val corpusSummary =
            ReviewText.corpusSummary(memorized, plan.completeJuz, plan.completeRub, plan.completeNisf)

        // La séance du jour est la **première** du plan : `reviewPlan` les ordonne déjà — reprises
        // partielles, consolidations dues, versets difficiles, puis la part habituelle du cycle.
        // Trier ou choisir ici défairait cet ordre, qui est la règle du domaine.
        val session = plan.session.firstOrNull()

        // Les trois colonnes sont construites **une fois** : la carte « À retravailler » réutilise
        // celle des priorités pour annoncer son compte. Le recalculer écrirait deux fois la même
        // quantité, et deux calculs finissent par diverger.
        val prioritaires = column(ReviewText.SummaryKind.PRIORITY, plan.priority)
        val summary = listOf(
            column(ReviewText.SummaryKind.CYCLE, plan.habitual),
            column(ReviewText.SummaryKind.CONSOLIDATION, plan.recent),
            prioritaires,
        )

        return ReviewDashboardUiState(
            loading = false,
            resumes = state.effectiveStudyProgress.values
                // Le complément exact du filtre du programme : ici la révision, là-bas
                // l'apprentissage. Un enregistrement ne peut pas être les deux.
                .filter { it.mode == StudyMode.REVISION && it.status == StudyStatus.PARTIAL }
                .mapNotNull { ProgramRenderer.resume(it) },
            summary = summary,
            priorityHeadline = ReviewText.priorityHeadline(prioritaires.quantity),
            day = DayCard(
                hasSession = session != null,
                footer = ReviewText.dayFooter(session != null),
                // Une consolidation se distingue d'une révision ordinaire par sa **catégorie** :
                // c'est elle qui décide si le lecteur propose l'étape des trois jours. La lire sur
                // la tâche, et non sur la position dans la séance, est ce qui la fait survivre au
                // regroupement des plages. C'est `forTask` qui l'applique, en une seule copie.
                start = session?.let { StudySession.forTask(it) },
            ),
            cycle = CycleCard(
                mode = settings?.mode,
                quantity = settings?.dailyQuantity,
                lengthDays = lengthDays,
                modeLabel = ReviewText.cycleModeLabel(settings?.mode, settings?.dailyQuantity, lengthDays),
                dayCounter = ReviewText.dayCounter(plan.cycleDay, lengthDays),
                percent = ReviewText.percentReviewed(ratio),
                ratio = ratio.toFloat(),
                corpusSummary = corpusSummary,
                stats = listOf(
                    // « à revoir » : tout le corpus du cycle, y compris ce qui est déjà fait — le
                    // client d'origine ne retire pas les versets revus de ce premier compte.
                    stat(cycle?.corpus ?: emptyList(), ReviewText.CYCLE_STAT_LABELS[0]),
                    stat(cycle?.completed ?: emptyList(), ReviewText.CYCLE_STAT_LABELS[1]),
                    // « restants » : le corpus **moins** ce qui est fait. Le filtre porte sur
                    // l'ensemble des versets faits, et non sur leur nombre : deux cycles peuvent
                    // avoir le même compte sans porter les mêmes versets.
                    stat(cycle?.corpus?.filter { it !in done } ?: emptyList(), ReviewText.CYCLE_STAT_LABELS[2]),
                ),
                rhythm = ReviewText.rhythmLine(
                    hasCorpus = cycle?.corpus?.isNotEmpty() == true,
                    rhythm = cycle?.let { Review.reviewRhythm(it) } ?: "",
                ),
            ),
            consolidations = plan.consolidations.map { consolidation(it, at) },
            consolidationOverflow = plan.consolidations.size > COLLAPSED_LIMIT,
            priorities = plan.rework.map { priority(it) },
            priorityOverflow = plan.rework.size > COLLAPSED_LIMIT,
            tracking = TrackingCard(
                // Le **même** mot que sur la carte du cycle : le client d'origine l'écrit deux
                // fois, à l'identique, et deux copies finiraient par diverger.
                corpusSummary = corpusSummary,
                revisions = ReviewText.revisionCount(state.effectiveReviewHistory.size),
            ),
        )
    }

    /** Une colonne du résumé : son volume, et la référence de sa première tâche. */
    private fun column(kind: ReviewText.SummaryKind, tasks: List<Review.ReviewTask>): SummaryColumn =
        SummaryColumn(
            kind = kind,
            quantity = Review.reviewQuantity(tasks.map { it.range }),
            reference = ReviewText.summaryReference(
                count = tasks.size,
                first = tasks.firstOrNull()?.let { Quran.reference(it.range) } ?: "",
            ),
        )

    /** Une statistique du cycle. */
    private fun stat(ids: List<Int>, label: String): StatLine =
        StatLine(value = Review.reviewQuantity(ids), label = label)

    /**
     * Une ligne de consolidation, avec ses trois étapes.
     *
     * L'identité de la tâche est construite **ici**, comme le client d'origine la construit au
     * moment de l'appui. Elle n'est pas décorative : c'est sous cette clé que la consolidation est
     * enregistrée, et sous aucune autre.
     */
    private fun consolidation(row: Review.ConsolidationRow, at: String): ConsolidationLine {
        val task = Review.ReviewTask(
            start = row.start,
            end = row.end,
            id = "consolidation-${row.start}-${row.end}",
            category = ReviewCategory.RECENT,
            label = "Consolidation",
        )
        // La plage est lue sur la **tâche** et non sur la ligne : `ConsolidationRow` porte deux
        // bornes, `ReviewTask` porte la plage, et n'en construire qu'une seule garantit que la
        // référence affichée et la plage servie sont la même.
        val reference = Quran.reference(task.range)
        return ConsolidationLine(
            id = task.id,
            reference = reference,
            detail = ReviewText.consolidationDetail(Review.reviewQuantity(listOf(task.range)), row.learnedAt),
            label = ReviewText.consolidateLabel(reference),
            steps = row.steps.map { step ->
                StepLine(
                    offset = step.offset,
                    label = ReviewText.stepOffset(step.offset),
                    status = ReviewText.stepStatus(step.completed, step.due, at),
                    date = ReviewText.stepDate(step.completed, step.due),
                    completed = step.completed != null,
                    // Une étape validée est traitée comme une étape due : les deux sont « en
                    // règle », et le client d'origine les colore de la même façon.
                    dueOrPast = step.due <= at,
                )
            },
            // La tâche est construite juste au-dessus avec `category = RECENT`, donc `forTask` en
            // déduit l'étape des trois jours : la règle n'est pas recopiée ici.
            study = StudySession.forTask(task),
        )
    }

    /**
     * Une ligne de verset prioritaire.
     *
     * Sa catégorie reste `priority` : elle n'ouvre donc **pas** l'étape de consolidation, qui ne
     * concerne que les versets récemment appris. La déduction est celle de `forTask`, la même que
     * partout ailleurs.
     */
    private fun priority(task: Review.ReviewTask): PriorityLine =
        PriorityLine(
            id = task.id,
            reference = Quran.reference(task.range),
            detail = ReviewText.priorityDetail(Review.reviewQuantity(listOf(task.range))),
            study = StudySession.forTask(task),
        )

    /**
     * La part du corpus du cycle déjà revue, entre 0 et 1.
     *
     * Le rapport est un rapport de **poids**, comme le client d'origine : `Review.reviewWeight`
     * mesure un verset en lettres arabes, donc un verset long pèse plus qu'un verset court. Un
     * rapport de nombres donnerait « 50 % » à mi-parcours d'un cycle dont la moitié du texte
     * reste à faire.
     *
     * Le diviseur est borné à un millionième — le `Math.max(.000001, …)` d'origine — pour qu'un
     * corpus vide rende `0` au lieu de `NaN`. Un `NaN` remonterait jusqu'à la barre de
     * progression et jusqu'au pourcentage, où il s'afficherait « NaN % ».
     */
    private fun completedRatio(cycle: ReviewCycle?): Double {
        if (cycle == null) return 0.0
        val corpus = cycle.corpus.sumOf { Review.reviewWeight(it) }
        val done = cycle.completed.sumOf { Review.reviewWeight(it) }
        return done / maxOf(MIN_WEIGHT, corpus)
    }

    /** Bornage du diviseur du rapport : le `Math.max(.000001, …)` du client d'origine. */
    private const val MIN_WEIGHT = 0.000001
}
