package com.msoumaya.deepseekandroid.feature.program

import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.Program
import com.msoumaya.deepseekandroid.core.domain.ProgramText
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.Review
import com.msoumaya.deepseekandroid.core.domain.StudyProgressCalculator
import com.msoumaya.deepseekandroid.core.domain.StudySession
import com.msoumaya.deepseekandroid.core.domain.Texts
import com.msoumaya.deepseekandroid.core.domain.WeeklyProgress
import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.MushafSource
import com.msoumaya.deepseekandroid.core.model.ReviewCategory
import com.msoumaya.deepseekandroid.core.model.Session
import com.msoumaya.deepseekandroid.core.model.SessionStatus
import com.msoumaya.deepseekandroid.core.model.StudyMode
import com.msoumaya.deepseekandroid.core.model.StudyProgress
import com.msoumaya.deepseekandroid.core.model.StudyStatus
import com.msoumaya.deepseekandroid.core.model.effectiveStudyProgress

// ---------------------------------------------------------------------------
// Calcul du programme
// ---------------------------------------------------------------------------
// Portage de `ProgramScreen` et de `StudyResumeCard` (`src/ui/MainScreens.tsx:35`,
// `src/ui/StudySession.tsx:29`).
//
// Le calcul est séparé du `ViewModel` à dessein : c'est une fonction pure de
// `(état, jour, période)`, donc éprouvable sans coroutine, sans horloge et sans appareil.
//
// **Ce que ce fichier décide, et qui ne se voit pas.** Trois règles portent à conséquence :
//
//   1. **Quelle séance est « celle du jour ».** Le client d'origine prend la séance prévue
//      aujourd'hui si elle existe, et sinon **la première à venir** — il n'annonce donc jamais
//      « aucune séance » tant qu'une séance est planifiée. Le repli n'est pas symétrique : une
//      séance **en retard** n'est pas « à venir », elle est proposée au rattrapage. Confondre
//      les deux ferait disparaître le rattrapage, ou ferait passer une séance oubliée pour la
//      séance du jour.
//
//   2. **Ce que compte la période « Jour ».** Sa journée de référence est celle de la première
//      séance à venir, et non celle du jour : sans séance aujourd'hui, l'onglet « Jour » montre
//      donc la prochaine journée chargée. C'est le comportement de l'original, et il est utile —
//      un onglet « Jour » vide n'apprendrait rien.
//
//   3. **Ce que la carte de révision ouvre.** La tâche du plan est transmise au lecteur **avec
//      sa catégorie**, parce que c'est elle qui décide de la consolidation : une révision
//      `recent` ouvre l'étape des trois jours, une révision ordinaire non. Sans la tâche, la
//      carte ouvrirait une lecture libre — et la révision faite ne serait enregistrée nulle
//      part, donc le lendemain l'application redemanderait le même passage.
//
// **Les helpers de reprise portent les deux modes du composant d'origine.** Le filtre de
// l'appelant ne laisse passer que l'apprentissage aujourd'hui, et [resume] **refuse** le reste
// plutôt que de le mal servir ; les mots de la révision sont néanmoins conservés, parce qu'ils
// sont la contrepartie exacte de `StudyResumeCard` et que `ProgramText.rowStatus` les épingle.
// Les retirer obligerait à les réécrire au portage du tableau de bord des révisions — et c'est
// le genre de réécriture qui perd un mot.
//
// **Le référentiel coranique doit être chargé avant d'appeler [render].** Tout passe par
// `Quran`, qui rend un référentiel vide tant qu'il n'est pas initialisé. C'est au `ViewModel`
// de ne pas appeler avant que l'état ne soit prêt.
// ---------------------------------------------------------------------------

internal object ProgramRenderer {

    /** Combien de séances en retard sont proposées au rattrapage. */
    private const val CATCH_UP_LIMIT = 10

    /**
     * Combien de séances passées entrent dans l'historique.
     *
     * Le client d'origine prend les vingt **dernières** de la liste, puis les renverse. C'est un
     * **renversement**, et non un tri : la fenêtre est celle des vingt dernières de l'état, et
     * l'ordre affiché est l'inverse de celui de l'état. Un état rangé dans l'ordre du programme
     * donne donc la plus récente en tête — mais c'est l'état qui le donne, pas ce calcul. Trier
     * ici ferait diverger les deux clients pour un état dont l'ordre ne serait pas le même.
     */
    private const val HISTORY_LIMIT = 20

    /**
     * Calcule l'état affichable du programme.
     *
     * @param state état applicatif persisté.
     * @param at jour courant, au format `AAAA-MM-JJ`.
     * @param period période d'affichage de la liste « À venir ».
     */
    fun render(state: AppState, at: String, period: ProgramPeriod): ProgramUiState {
        val future = WeeklyProgress.upcomingSessions(state, at)
        val plan = Review.reviewPlan(state, at)
        val week = WeeklyProgress.weeklyProgress(state, at)
        val progress = Program.progress(state)
        val source = (state.reader?.mushaf ?: MushafSource.CORAN_TEST).persistedKey
        val reviewsOn = Review.reviewsEnabled(state)

        val learning = state.sessions.firstOrNull {
            it.status == SessionStatus.TODO && WeeklyProgress.scheduledDate(it) == at
        } ?: future.firstOrNull()

        val review = plan.session.firstOrNull()

        return ProgramUiState(
            loading = false,
            period = period,
            today = Today(
                dateLabel = ProgramText.longDate(at),
                learning = Task(
                    passage = learning?.let { Quran.reference(it.range) } ?: "Programme terminé",
                    details = learning?.let { "${ProgramText.verseCount(it.range)} • Page ${page(it.start, source)}" }
                        ?: "Aucune séance",
                    // Le couple du bas **résume** : il donne le compte sans la page, et ses replis
                    // disent autre chose que ceux de la carte — « Objectif atteint » là où la
                    // carte dit « Aucune séance ». Voir `Task`.
                    compactPassage = learning?.let { Quran.reference(it.range) } ?: "Objectif atteint",
                    compactDetails = learning?.let { ProgramText.verseCount(it.range) }
                        ?: "Modifier mon objectif",
                    study = learning?.let { StudySession.forSession(it) },
                    verseId = learning?.start,
                ),
                revision = if (!reviewsOn) {
                    // La carte n'est pas affichée du tout quand les révisions sont éteintes :
                    // c'est une absence voulue, et non une carte vide. Voir `Today.revision`.
                    null
                } else {
                    Task(
                        passage = review?.let { Quran.reference(it.range) } ?: "Révisions à jour",
                        details = review?.let { "${ProgramText.verseCount(it.range)} • Page ${page(it.start, source)}" }
                            ?: "Aucun passage dû",
                        compactPassage = review?.let { Quran.reference(it.range) } ?: "À jour",
                        compactDetails = review?.let { ProgramText.verseCount(it.range) }
                            ?: "Voir mes révisions",
                        study = review?.let {
                            StudySession.forTask(it, consolidation = it.category == ReviewCategory.RECENT)
                        },
                        verseId = review?.start,
                    )
                },
                nextSession = learning
                    ?.takeIf { WeeklyProgress.scheduledDate(it) != at }
                    ?.let { "Prochaine séance prévue : ${ProgramText.mediumDate(WeeklyProgress.scheduledDate(it))}" },
            ),
            goal = GoalLine(
                label = state.goal.label,
                // `getValue` et non `[]` : la table couvre les douze rythmes, et un rythme
                // ajouté au modèle sans son libellé doit **lever** ici plutôt que d'afficher un
                // « null / jour ». Un test épingle la totalité de la table.
                pace = "${Texts.paceLabels.getValue(state.pace)} / jour",
                ratio = progress.goal.toFloat(),
            ),
            resumes = state.effectiveStudyProgress.values
                .filter { it.mode == StudyMode.LEARNING && it.status == StudyStatus.PARTIAL }
                .filter { record ->
                    // Seule une séance **encore à faire** est reprise : une séance reportée ou
                    // terminée n'a plus de reste à proposer, et sa carte serait un bouton mort.
                    state.sessions.any { it.id == record.id && it.status == SessionStatus.TODO }
                }
                .mapNotNull { resume(it) },
            week = WeekLine(ratio = week.ratio.toFloat()),
            catchUp = state.sessions
                .filter { it.status == SessionStatus.TODO && WeeklyProgress.scheduledDate(it) < at }
                .take(CATCH_UP_LIMIT)
                .map {
                    CatchUpLine(
                        id = it.id,
                        label = "${WeeklyProgress.scheduledDate(it)} · ${Quran.reference(it.range)}",
                        // La séance **entière**, et non son reste : un rattrapage est une séance
                        // qu'on n'a pas faite, pas une séance qu'on a commencée.
                        study = StudySession.forSession(it),
                    )
                },
            upcoming = upcoming(future, at, period, source),
            history = state.sessions
                .filter { it.status != SessionStatus.TODO }
                .takeLast(HISTORY_LIMIT)
                .asReversed()
                .map {
                    HistoryLine(
                        id = it.id,
                        label = "${WeeklyProgress.scheduledDate(it)} · " +
                            if (it.status == SessionStatus.DONE) "Terminé" else "Reporté",
                        passage = Quran.reference(it.range),
                    )
                },
        )
    }

    /**
     * Les séances à venir, filtrées par la période.
     *
     * « Jour » prend pour référence la date de la **première séance à venir**, et non celle du
     * jour : voir la note de tête. « Semaine » va jusqu'à sept jours plus loin, bornes
     * comprises. « Mois » ne filtre rien — c'est la fenêtre de dix jours de
     * [WeeklyProgress.upcomingSessions] qui borne, et la nommer « Mois » est un écart assumé du
     * client d'origine.
     */
    private fun upcoming(
        future: List<Session>,
        at: String,
        period: ProgramPeriod,
        source: String,
    ): List<UpcomingLine> {
        val list = when (period) {
            ProgramPeriod.DAY -> {
                val reference = future.firstOrNull()?.let { WeeklyProgress.scheduledDate(it) } ?: at
                future.filter { WeeklyProgress.scheduledDate(it) == reference }
            }

            ProgramPeriod.WEEK -> {
                val limit = Dates.addDays(at, 7)
                future.filter { WeeklyProgress.scheduledDate(it) <= limit }
            }

            ProgramPeriod.MONTH -> future
        }
        val tomorrow = Dates.addDays(at, 1)

        return list.map { session ->
            val date = WeeklyProgress.scheduledDate(session)
            UpcomingLine(
                id = session.id,
                weekday = ProgramText.shortWeekday(date),
                day = Dates.parse(date).dayOfMonth,
                passage = Quran.reference(session.range),
                details = "${ProgramText.verseCount(session.range)} • Page ${page(session.start, source)}",
                whenLabel = when (date) {
                    at -> "Aujourd'hui"
                    tomorrow -> "Demain"
                    else -> ProgramText.dayMonth(date)
                },
                arabic = Quran.surahAt(session.start).arabic,
                study = StudySession.forSession(session),
            )
        }
    }

    /**
     * Une reprise, ou `null` quand il n'y a rien à reprendre — ou rien à servir.
     *
     * Deux refus, et les deux sont voulus. `remainingStudyRange` rend `null` quand tout est
     * validé : une reprise sans reste proposerait de relire ce qui est déjà appris, sous un titre
     * qui annoncerait un travail déjà fait. Et une reprise de **révision** est refusée, parce que
     * son identité de tâche n'est transportée par aucune route — voir le corps.
     */
    private fun resume(record: StudyProgress): ResumeLine? {
        // Seule une reprise **d'apprentissage** est servie ici, et le refus est explicite plutôt
        // que silencieux. Une reprise de révision se valide sous l'identité de sa tâche de
        // révision, que ce client ne transporte encore par aucune route : lui prêter l'identifiant
        // de la progression ferait écrire la validation sous une clé que personne ne relira, et le
        // lendemain l'application redemanderait le même passage sans que rien ne le dise. C'est
        // ici que la variante se branchera, avec le portage du tableau de bord des révisions.
        if (record.mode != StudyMode.LEARNING) return null

        val range = record.range
        val remaining = StudyProgressCalculator.remainingStudyRange(range, record) ?: return null
        val metrics = StudyProgressCalculator.studyMetrics(range, record.through, record.source)
        val learning = record.mode == StudyMode.LEARNING

        // La localisation du reste suit l'unité de la tâche : une reprise qui couvre une seule
        // page restante annonce « Page 12 », pas « Pages 12 à 12 ».
        val where = if (metrics.pages) {
            val firstPage = StudyProgressCalculator.studyPage(remaining.start, record.source)
            if (firstPage == metrics.last) "Page ${metrics.last}" else "Pages $firstPage à ${metrics.last}"
        } else {
            Quran.reference(remaining)
        }

        // Le reste se compte dans l'unité de la tâche, comme le titre ci-dessus.
        val restants = if (metrics.pages) {
            "${metrics.remaining} page${plural(metrics.remaining)} " +
                if (metrics.remaining == 1) "restante" else "restantes"
        } else {
            "${metrics.remaining} verset${plural(metrics.remaining)} " +
                if (metrics.remaining == 1) "restant" else "restants"
        }

        val progress = "${metrics.done} / ${metrics.total} ${metrics.unit} " +
            when {
                learning && metrics.pages -> "apprises"
                learning -> "appris"
                metrics.pages -> "faites"
                else -> "faits"
            } +
            " · ${Math.round(metrics.ratio * 100f)} %"

        return ResumeLine(
            id = record.id,
            title = if (learning) "Apprentissage à continuer" else "Révision à continuer",
            where = where,
            remaining = restants,
            progress = progress,
            ratio = metrics.ratio.toFloat(),
            done = validatedSoFar(metrics, record, learning),
            // La plage est celle du **reste**, et l'identité est celle de la séance : c'est ce
            // couple qui fait qu'une reprise continue la progression au lieu d'en recommencer
            // une. Le filtre de l'appelant et le refus ci-dessus garantissent que `record` est
            // bien une reprise d'apprentissage, donc que `record.id` est un identifiant de séance.
            study = StudySession.Request(range = remaining, sessionId = record.id),
            total = "${metrics.total} ${metrics.unit} au total · Juz ${juzOf(record.start)}",
            rows = rows(record, metrics, learning),
        )
    }

    /**
     * La ligne de validation déjà effectuée.
     *
     * Deux formes, comme l'original : une quand la tâche se compte en pages — « Pages 12 à 13
     * apprises » —, une quand elle se compte en versets. La seconde **nomme la plage validée**,
     * pas la plage entière : c'est ce qui a été appris, et l'annoncer autrement ferait croire
     * que la tâche est finie.
     */
    private fun validatedSoFar(
        metrics: StudyProgressCalculator.Metrics,
        record: StudyProgress,
        learning: Boolean,
    ): String {
        val prefix = if (metrics.pages && metrics.done > 0) {
            val pages = if (metrics.done > 1) "s" else ""
            val jusqua = if (metrics.done > 1) " à ${metrics.first + metrics.done - 1}" else ""
            val participe = if (learning) "apprise" else "révisée"
            "Page$pages ${metrics.first}$jusqua $participe$pages"
        } else {
            val participe = if (learning) "appris" else "révisés"
            "${Quran.reference(record.range.copy(end = record.through))} $participe"
        }
        return "✓ $prefix"
    }

    /**
     * Les lignes du détail : une par page quand la tâche en couvre plusieurs, une par verset
     * sinon.
     *
     * Les bornes d'une ligne de page sont **rognées sur la tâche** : une page partagée avec un
     * voisin ne compte que la portion qui appartient à cette reprise, sans quoi une ligne
     * entamée pourrait se croire terminée par un verset qu'elle ne porte pas.
     */
    private fun rows(
        record: StudyProgress,
        metrics: StudyProgressCalculator.Metrics,
        learning: Boolean,
    ): List<ResumeRow> {
        if (!metrics.pages) {
            // Le nom de la sourate n'est répété que si la plage en traverse plusieurs : sur une
            // seule sourate, le préfixe serait identique sur chaque ligne et n'apprendrait rien.
            val acrossSurahs = Quran.verseAt(record.start).surah != Quran.verseAt(record.end).surah
            return (record.start..record.end).map { id ->
                val verse = Quran.verseAt(id)
                val prefix = if (acrossSurahs) "${Quran.surahs[verse.surah - 1].name} · " else ""
                row(id = id, start = id, end = id, label = "$prefix" + "Verset ${verse.ayah}", record = record, learning = learning)
            }
        }
        return metrics.pageList.map { page ->
            val pageRange = StudyProgressCalculator.studyPageRange(page, record.source)
            row(
                id = page,
                start = maxOf(record.start, pageRange.start),
                end = minOf(record.end, pageRange.end),
                label = "Page $page",
                record = record,
                learning = learning,
            )
        }
    }

    /**
     * Une ligne du détail.
     *
     * Trois états, et l'ordre compte : une ligne est faite quand son **dernier** verset est
     * validé, entamée quand son **premier** l'est, et à faire sinon. Une ligne de page rognée
     * peut donc être faite alors que la page voisine ne l'est pas — c'est la tâche qui décide,
     * pas la page.
     */
    private fun row(
        id: Int,
        start: Int,
        end: Int,
        label: String,
        record: StudyProgress,
        learning: Boolean,
    ): ResumeRow {
        val done = end <= record.through
        val partial = !done && start <= record.through
        return ResumeRow(
            id = id,
            label = label,
            state = when {
                done -> RowState.DONE
                partial -> RowState.PARTIAL
                else -> RowState.TODO
            },
            status = ProgramText.rowStatus(done = done, partial = partial, learning = learning),
        )
    }

    /** Numéro du juz’ qui porte un verset, à partir de 1. */
    private fun juzOf(verseId: Int): Int =
        Quran.juzs.indexOfFirst { verseId >= it.start && verseId <= it.end } + 1

    /** Page d'un verset dans le découpage de la source affichée. */
    private fun page(verseId: Int, source: String): Int =
        StudyProgressCalculator.studyPage(verseId, source)

    /** Marque du pluriel, vide au singulier. */
    private fun plural(count: Int): String = if (count == 1) "" else "s"
}
