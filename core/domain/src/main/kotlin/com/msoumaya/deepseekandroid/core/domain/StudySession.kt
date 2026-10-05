package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.ReviewCategory
import com.msoumaya.deepseekandroid.core.model.ReviewGrade
import com.msoumaya.deepseekandroid.core.model.StudyMode
import com.msoumaya.deepseekandroid.core.model.StudyProgress
import com.msoumaya.deepseekandroid.core.model.StudyStatus
import com.msoumaya.deepseekandroid.core.model.effectiveReviewConsolidations
import com.msoumaya.deepseekandroid.core.model.effectiveStudyProgress

/**
 * La séance que le lecteur sert.
 *
 * ## Ce que ce fichier remplace
 *
 * Le client d'origine ouvre son lecteur avec un objet anonyme — `type Reader` d'`App.tsx:66` —
 * qui porte à la fois la plage demandée et l'identité de la tâche : `sessionId` pour une séance
 * d'apprentissage, `reviewTask` ou `revisionId` pour une révision, `consolidation` pour une
 * étape de consolidation. Le lecteur en déduit ensuite, en dix lignes mêlées à son rendu
 * (`App.tsx:472-483`), s'il est « focalisé », quel identifiant porte la progression, quelle
 * plage il faut compter, et à quel verset on s'est arrêté.
 *
 * Ce fichier est le portage de ces dix lignes, et rien d'autre. Elles ont été sorties du rendu
 * parce qu'elles **décident** — elles disent quel verset est validé, donc quel verset sera
 * écrit comme su, donc ce que le programme croira appris. Une décision de cette portée ne
 * s'éprouve pas dans une fonction `@Composable`.
 *
 * ## La distinction qui gouverne tout le fichier
 *
 * Trois plages cohabitent, et les confondre est l'erreur que ce fichier existe pour empêcher :
 *
 *  - la plage **demandée** — ce que l'appelant a passé (`request.range`) ;
 *  - la plage **ouverte** — ce que le lecteur affiche ([opening]) : une tâche interrompue
 *    reprend à son reste, pas à son début ;
 *  - la plage **prévue** — ce que le bandeau compte ([plannedRange]) : la séance entière, ou la
 *    tâche telle qu'elle a été enregistrée.
 *
 * La troisième n'est pas la première. Pour une séance d'apprentissage, la plage prévue est
 * celle de la **séance présente dans l'état** : c'est elle qui a été planifiée, et une
 * régénération du programme a pu déplacer les bornes depuis que la requête a été construite.
 * Pour une révision, c'est celle du **compte rendu enregistré** : une reprise doit se compter
 * contre la plage qui a été persistée, sans quoi un `through` d'une autre plage serait refusé
 * par [StudyProgressCalculator.validateStudyProgress] — qui exige des bornes identiques — et la
 * validation serait silencieusement perdue.
 *
 * ## Ce qui n'est pas ici
 *
 * Le calcul des pages et des mesures reste dans [StudyProgressCalculator] : ce fichier ne fait
 * que choisir la plage sur laquelle l'appeler. Le rendu du bandeau reste dans `feature:reader` :
 * ce fichier ne rend que le **texte déjà résolu** ([banner]), ce qui le rend éprouvable sans
 * appareil.
 */
object StudySession {

    /**
     * Les trois étapes de consolidation, dans l'ordre du client d'origine (`App.tsx:474`).
     *
     * L'ordre compte : c'est le **premier** offset non validé qui est proposé. Une étape faite
     * en retard n'empêche donc pas les suivantes d'être proposées à leur tour, et une étape
     * sautée ne l'est jamais définitivement.
     */
    val CONSOLIDATION_OFFSETS: List<Int> = listOf(1, 3, 7)

    /** L'étape de consolidation proposée quand les trois sont faites, comme l'original. */
    const val LAST_CONSOLIDATION_OFFSET = 7

    /**
     * Ce que le lecteur doit servir.
     *
     * Les quatre identifiants ne sont pas exclusifs dans le type, et c'est fidèle à
     * l'original : une consolidation arrive **avec** sa `reviewTask`, et une révision
     * historique avec son `revisionId`. Ce qui les ordonne, c'est [targetId].
     */
    data class Request(
        val range: Range,
        val sessionId: String? = null,
        val revisionId: String? = null,
        val reviewTask: Review.ReviewTask? = null,
        val consolidation: Boolean = false,
    ) {
        /** Une séance d'apprentissage : c'est la présence d'un `sessionId` qui le dit. */
        val learning: Boolean get() = sessionId != null

        /** Une révision, sous l'une de ses trois formes. */
        val reviewing: Boolean get() = reviewTask != null || revisionId != null || consolidation

        /**
         * Vrai quand le lecteur sert une tâche.
         *
         * C'est la condition qui décide si le bandeau s'affiche et si une validation est
         * possible. Une lecture libre n'est pas une séance : elle ne se valide pas, et
         * [validate] refuse alors d'écrire.
         */
        val focused: Boolean get() = learning || reviewing

        /**
         * Le mode écrit dans la progression.
         *
         * Défini même pour une lecture libre — l'original fait de même, et la valeur n'est
         * jamais écrite dans ce cas puisque [targetId] est alors `null`.
         */
        val mode: StudyMode get() = if (learning) StudyMode.LEARNING else StudyMode.REVISION

        /**
         * L'identifiant qui porte la progression, ou `null` pour une lecture libre.
         *
         * L'ordre est celui de l'original (`App.tsx:478`) et il n'est pas interchangeable :
         * une consolidation arrive avec une `reviewTask` dont l'identifiant est
         * `consolidation-<début>-<fin>`, donc c'est bien la tâche qui porte la progression, et
         * jamais la séance d'apprentissage qui a produit ces versets.
         */
        val targetId: String? get() = sessionId ?: reviewTask?.id ?: revisionId
    }

    /** Une séance d'apprentissage. */
    fun forSession(session: com.msoumaya.deepseekandroid.core.model.Session): Request =
        Request(range = session.range, sessionId = session.id)

    /** Une tâche de révision. `consolidation` distingue l'étape des trois jours du cycle. */
    fun forTask(task: Review.ReviewTask, consolidation: Boolean = false): Request =
        Request(range = task.range, reviewTask = task, consolidation = consolidation)

    /** La progression enregistrée pour cette requête, ou `null` s'il n'y en a pas. */
    fun record(state: AppState, request: Request): StudyProgress? {
        val id = request.targetId ?: return null
        return state.effectiveStudyProgress[StudyProgressCalculator.studyKey(request.mode, id)]
    }

    /**
     * La plage que le bandeau compte, et sur laquelle une validation porte.
     *
     * Voir la note de tête : la séance de l'état pour l'apprentissage, le compte rendu
     * enregistré pour la révision, et la plage demandée seulement en dernier recours — quand la
     * séance a disparu du programme, ou qu'aucune progression n'existe encore.
     */
    fun plannedRange(state: AppState, request: Request): Range = if (request.learning) {
        state.sessions.firstOrNull { it.id == request.sessionId }?.range ?: request.range
    } else {
        record(state, request)?.range ?: request.range
    }

    /**
     * Le dernier verset validé.
     *
     * Quand rien n'a été validé, c'est le verset qui **précède** la plage — et non son premier
     * verset. La différence n'est pas cosmétique : `validateStudyProgress` calcule le début de
     * la validation par `max(range.start, through + 1)`, donc annoncer `range.start` ferait
     * commencer la seconde validation au verset suivant le premier, et le premier verset ne
     * serait jamais compté comme appris.
     */
    fun through(state: AppState, request: Request): Int {
        val range = plannedRange(state, request)
        return record(state, request)?.through ?: (range.start - 1)
    }

    /**
     * La requête **réellement ouverte** par le lecteur.
     *
     * Une tâche interrompue reprend à son reste : c'est le portage de la première ligne
     * d'`openReader` (`App.tsx:211`), et c'est ce qui fait qu'une séance reprise ne redemande
     * pas de relire ce qui est déjà validé.
     *
     * Deux conditions, et non une : il faut un compte rendu **et** qu'il soit **partiel**. Un
     * compte rendu terminé n'a pas de reste — `remainingStudyRange` rendrait `null` — et une
     * tâche terminée rouverte se rouvre donc à sa plage entière, comme l'original.
     */
    fun opening(state: AppState, request: Request): Request {
        val record = record(state, request) ?: return request
        if (record.status != StudyStatus.PARTIAL) return request
        val reste = StudyProgressCalculator.remainingStudyRange(record.range, record) ?: return request
        return request.copy(range = reste)
    }

    /**
     * La prochaine étape de consolidation à valider, ou `null` si les trois sont faites.
     *
     * Lue sur le **premier verset de la plage demandée**, comme l'original : la consolidation
     * est enregistrée par verset d'apprentissage, et la tâche qui la propose commence à ce
     * verset.
     */
    fun consolidationOffset(state: AppState, request: Request): Int? =
        CONSOLIDATION_OFFSETS.firstOrNull { offset ->
            state.effectiveReviewConsolidations[request.range.start.toString()]
                ?.completed
                ?.containsKey(offset) != true
        }

    /**
     * Ce que le bandeau affiche, **déjà résolu**.
     *
     * Le rendu ne calcule rien : il pose ces chaînes. C'est ce qui permet d'éprouver les
     * libellés — « Consolidation · J+3 », « 12 / 30 versets », « Pages 120 → 125 » — sans
     * appareil, alors qu'ils décident de ce que la personne croit valider.
     */
    data class Banner(
        val title: String,
        /** Le libellé d'accessibilité du bouton : ce que l'action annonce, et non ce qu'elle montre. */
        val actionLabel: String,
        /** La ligne de progression, ou `null` pour une consolidation — l'original la masque. */
        val progress: String?,
        val pageLabel: String,
        val pageText: String,
        val ratio: Double,
    )

    fun banner(state: AppState, request: Request, source: String): Banner {
        val metrics = StudyProgressCalculator.studyMetrics(
            plannedRange(state, request),
            through(state, request),
            source,
        )
        val offset = if (request.consolidation) {
            consolidationOffset(state, request) ?: LAST_CONSOLIDATION_OFFSET
        } else {
            null
        }
        val title = when {
            offset != null -> "Consolidation · J+$offset"
            request.learning -> "Apprentissage du jour"
            else -> "Révision du jour"
        }
        val actionLabel = if (offset != null) {
            "Valider la consolidation J+$offset"
        } else {
            "Terminer " + if (request.learning) "mon apprentissage" else "ma révision"
        }
        val pages = metrics.first != metrics.last
        return Banner(
            title = title,
            actionLabel = actionLabel,
            // Masquée pour une consolidation, comme l'original : une étape de consolidation
            // porte sur une poignée de versets, et un compteur « 0 / 3 » y ressemblerait à une
            // séance qu'on n'a pas commencée.
            progress = if (offset != null) null else "${metrics.done} / ${metrics.total} ${metrics.unit}",
            pageLabel = if (pages) "Pages" else "Page",
            pageText = if (pages) "${metrics.first} → ${metrics.last}" else "${metrics.first}",
            ratio = metrics.ratio,
        )
    }

    /**
     * Valide la séance jusqu'à [through] et rend l'état écrit.
     *
     * **Deux refus, et ils sont le cœur de la règle.** Une lecture libre ne valide rien : sans
     * cible, il n'y a pas de progression à écrire, et écrire sous un identifiant inventé
     * créerait une tâche que personne n'a demandée. La seconde porte sur le mode : une requête
     * sans tâche n'est pas une séance, quelle que soit la plage.
     *
     * Le reste est délégué à [StudyProgressCalculator.validateStudyProgress], qui porte les
     * garde-fous de fond — bornes, continuité, séance présente et non terminée. Ce fichier ne
     * les duplique pas : deux copies d'une même règle finissent par diverger, et c'est celle
     * qu'on ne relit pas qui reste.
     */
    fun validate(
        state: AppState,
        request: Request,
        through: Int,
        source: String,
        grade: ReviewGrade = ReviewGrade.PERFECT,
        at: String = Dates.todayLocal(),
    ): AppState {
        val id = request.targetId ?: return state
        if (!request.focused) return state
        return StudyProgressCalculator.validateStudyProgress(
            state = state,
            mode = request.mode,
            id = id,
            range = plannedRange(state, request),
            through = through,
            source = source,
            // La catégorie suit la tâche, puis le compte rendu, puis le défaut du cycle. La
            // lire sur la tâche d'abord est ce qui distingue une consolidation d'une révision
            // ordinaire dans l'historique.
            category = request.reviewTask?.category
                ?: record(state, request)?.category
                ?: ReviewCategory.HABITUAL,
            grade = grade,
            at = at,
        )
    }

    /**
     * Les calculs purs de la feuille de validation.
     *
     * Ils sont ici, et non dans le rendu, pour la même raison que le reste : ce sont eux qui
     * décident **quel verset** sera écrit comme le dernier appris. Un défaut d'un cran dans
     * `nextStart` ferait valider un verset de trop, ou en oublier un, et le programme
     * apprendrait ensuite des versets que personne n'a lus.
     */
    object Completion {

        /** Le premier verset qui reste à valider. */
        fun nextStart(range: Range, through: Int): Int = maxOf(range.start, through + 1)

        /**
         * Le verset proposé par défaut : la fin de la page affichée.
         *
         * La proposition suit ce que la personne a **sous les yeux** — elle vient de finir une
         * page — sans jamais sortir de ce qui reste à faire : d'où le plancher à `nextStart`.
         */
        fun initialEndpoint(range: Range, through: Int, currentPage: Int, source: String): Int {
            val next = nextStart(range, through)
            val finDePage = StudyProgressCalculator.studyPageRange(currentPage, source).end
            return maxOf(next, minOf(range.end, finDePage))
        }

        /** Le verset retenu : la fin de la plage quand on déclare avoir tout fait. */
        fun selected(range: Range, endpoint: Int, all: Boolean): Int = if (all) range.end else endpoint

        /**
         * La phrase qui résume ce qui va être validé.
         *
         * Deux formes, comme l'original : une quand la plage se compte en pages, une quand elle
         * se compte en versets. La mention « page N en cours » signale qu'une page est
         * **entamée** sans être finie — sans elle, le compte des pages apprises aurait l'air
         * faux, puisqu'il compte les pages terminées.
         */
        fun summary(
            range: Range,
            through: Int,
            selected: Int,
            source: String,
            learning: Boolean,
        ): String {
            val metrics = StudyProgressCalculator.studyMetrics(range, through, source)
            val completed = StudyProgressCalculator.studyMetrics(range, selected, source)
            val lastCompleted = metrics.first + completed.done - 1
            if (!metrics.pages || completed.done <= 0) {
                return Quran.reference(Range(range.start, selected)) + " " +
                    if (learning) "appris" else "révisés"
            }
            val pluriel = if (completed.done > 1) "s" else ""
            val jusqua = if (completed.done > 1) " à $lastCompleted" else ""
            val page = StudyProgressCalculator.studyPage(selected, source)
            val enCours = if (page > lastCompleted) " · page $page en cours" else ""
            return "Page$pluriel ${metrics.first}$jusqua " +
                (if (learning) "apprise" else "révisée") + pluriel + enCours
        }

        /**
         * Le libellé du bouton de validation.
         *
         * Il dit **ce qui sera écrit**, et non « Valider » : valider tout et valider jusqu'à un
         * verset ne produisent pas la même progression, et le bouton est le dernier endroit où
         * la personne peut le voir avant que ce soit fait.
         */
        fun validateLabel(
            range: Range,
            selected: Int,
            all: Boolean,
            learning: Boolean,
            unitIsPage: Boolean,
            page: Int,
            ayah: Int,
        ): String = when {
            all || selected == range.end ->
                if (learning) "Valider tout l'apprentissage" else "Valider toute la révision"

            unitIsPage -> "Valider jusqu'à la page $page"

            else -> "Valider jusqu'au verset $ayah"
        }
    }
}
