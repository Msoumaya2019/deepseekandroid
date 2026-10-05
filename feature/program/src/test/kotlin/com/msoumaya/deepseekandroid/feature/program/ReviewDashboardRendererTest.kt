package com.msoumaya.deepseekandroid.feature.program

import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.Program
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.QuranDataLoader
import com.msoumaya.deepseekandroid.core.domain.Review
import com.msoumaya.deepseekandroid.core.domain.ReviewText
import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.DifficultyMarker
import com.msoumaya.deepseekandroid.core.model.DifficultyStamp
import com.msoumaya.deepseekandroid.core.model.Mastery
import com.msoumaya.deepseekandroid.core.model.MushafSource
import com.msoumaya.deepseekandroid.core.model.Pace
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.ReviewCategory
import com.msoumaya.deepseekandroid.core.model.ReviewCycle
import com.msoumaya.deepseekandroid.core.model.ReviewEvent
import com.msoumaya.deepseekandroid.core.model.ReviewGrade
import com.msoumaya.deepseekandroid.core.model.ReviewSettings
import com.msoumaya.deepseekandroid.core.model.Session
import com.msoumaya.deepseekandroid.core.model.SessionStatus
import com.msoumaya.deepseekandroid.core.model.StudyMode
import com.msoumaya.deepseekandroid.core.model.StudyProgress
import com.msoumaya.deepseekandroid.core.model.StudyStatus
import org.junit.BeforeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Règles du tableau de bord des révisions
// ---------------------------------------------------------------------------
// Le calcul est une fonction pure de `(état, jour)`. Il est donc éprouvé ici sans coroutine, sans
// horloge et sans appareil.
//
// Le jour est **fixé** (« 2026-03-10 », un mardi) et non lu depuis l'horloge. Les jours relatifs
// sont dérivés de cette constante avec `Dates.addDays`, jamais recalculés à la main.
//
// Quatre familles de contrôles portent le plus, parce que ce sont celles qui peuvent **mentir** :
//
//   1. **Quelle liste nourrit quelle colonne du résumé.** Le cycle, les consolidations et les
//      priorités sont trois listes distinctes ; permuter deux colonnes afficherait le bon compte
//      devant le mauvais mot. Le test le rend mesurable en exigeant d'abord que les trois colonnes
//      se **distinguent** — sans cette précondition, une permutation resterait invisible.
//
//   2. **Deux comptes différents pour les versets difficiles.** Le résumé compte ceux **dus
//      aujourd'hui**, la carte en liste **tous**. Le test construit un état où les deux nombres
//      diffèrent, sinon la règle ne serait pas éprouvée.
//
//   3. **L'identité d'une tâche.** Une consolidation se valide sous `consolidation-<début>-<fin>`,
//      une reprise de révision sous l'identité de sa tâche. Se tromper de clé écrirait la
//      validation là où personne ne la relirait, et le lendemain le même passage serait redemandé
//      sans que rien ne le dise.
//
//   4. **Le rapport de poids.** « 43 % réellement révisés » se calcule sur le volume des versets,
//      et non sur leur nombre : le test compare à la valeur attendue, calculée sur les mêmes poids.
//
// Trois tests posent une **précondition** sur leur propre fixture. Ce n'est pas du zèle : sans
// elles, une fixture qui cesserait d'exercer la branche visée comparerait à une valeur qui n'a plus
// le même sens, et le test resterait vert pour la mauvaise raison.
// ---------------------------------------------------------------------------

class ReviewDashboardRendererTest {

    /** Un mardi. */
    private val today = "2026-03-10"

    private val source = MushafSource.CORAN_TEST.persistedKey

    private fun render(state: AppState): ReviewDashboardUiState =
        ReviewDashboardRenderer.render(state, today)

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    /**
     * Un état dont trois versets sont mémorisés : deux anciens, un récent.
     *
     * Les deux anciens — appris il y a trente jours — entrent dans le **corpus du cycle**. Le
     * récent — appris il y a deux jours — n'y entre pas : il est encore dans ses trois
     * consolidations, et c'est précisément ce que la carte des consolidations doit montrer.
     */
    private fun etatComplet(): AppState = Program.defaultState().copy(
        knowledge = mapOf(
            "6000" to Mastery.PERFECT,
            "6005" to Mastery.PERFECT,
            "6010" to Mastery.PERFECT,
        ),
        memorizedAt = mapOf(
            "6000" to Dates.addDays(today, -30),
            "6005" to Dates.addDays(today, -30),
            "6010" to Dates.addDays(today, -2),
        ),
    )

    /** Un état dont les révisions sont éteintes : le domaine rend alors un plan entièrement vide. */
    private fun etatEteint(): AppState = etatComplet().copy(
        reviewSettings = ReviewSettings(enabled = false),
    )

    /**
     * Un cycle posé à la main, dont un verset sur deux est déjà revu.
     *
     * Le cycle est construit plutôt que dérivé : `prepareReviewSchedule` ne produit jamais un
     * cycle partiellement revu, puisqu'il n'en crée un qu'au premier jour. Le poser est le seul
     * moyen d'éprouver le pourcentage sur une valeur non nulle.
     */
    private fun avecCyclePartiel(): AppState = etatComplet().copy(
        reviewCycle = ReviewCycle(
            index = 1,
            startDate = today,
            lengthDays = 7,
            corpus = listOf(6000, 6005),
            days = listOf(listOf(6000), listOf(6005)),
            completed = listOf(6000),
            assignments = mapOf(today to 0),
        ),
    )

    private fun session(id: String, start: Int, end: Int) = Session(
        id = id,
        date = today,
        start = start,
        end = end,
        unit = Pace.VERSE3,
        status = SessionStatus.TODO,
        scheduledDate = today,
    )

    private fun progress(
        id: String,
        start: Int,
        end: Int,
        through: Int,
        mode: StudyMode,
        category: ReviewCategory? = null,
    ) = StudyProgress(
        id = id,
        mode = mode,
        start = start,
        end = end,
        through = through,
        page = 0,
        source = source,
        updatedAt = "${today}T10:00:00.000Z",
        status = StudyStatus.PARTIAL,
        category = category,
    )

    /** Marque [ids] difficiles, chacun avec son échéance de priorité. */
    private fun difficiles(state: AppState, dues: Map<Int, String>): AppState = state.copy(
        difficultyMarkers = dues.keys.associate { it.toString() to DifficultyMarker(user = DifficultyStamp(today)) },
        reviewPriorityDue = dues.mapKeys { it.key.toString() },
    )

    // -----------------------------------------------------------------------
    // Résumé — quelle liste nourrit quelle colonne
    // -----------------------------------------------------------------------

    @Test
    fun `chaque colonne du resume recoit la liste qui lui correspond`() {
        val state = difficiles(etatComplet(), mapOf(6010 to today))
        val plan = Review.reviewPlan(state, today)
        val ui = render(state)

        // Précondition : les trois colonnes portent des références **différentes**. Sans elle,
        // permuter deux colonnes ne changerait rien et le test resterait vert pour la mauvaise
        // raison. C'est la première chose qu'il vérifie, avant de comparer.
        val attendues = listOf(plan.habitual, plan.recent, plan.priority).map { taches ->
            ReviewText.summaryReference(
                count = taches.size,
                first = taches.firstOrNull()?.let { Quran.reference(it.range) } ?: "",
            )
        }
        assertEquals(3, attendues.distinct().size, "les trois colonnes doivent se distinguer : $attendues")

        assertEquals(attendues, ui.summary.map { it.reference })
        assertEquals(
            listOf(
                ReviewText.SummaryKind.CYCLE,
                ReviewText.SummaryKind.CONSOLIDATION,
                ReviewText.SummaryKind.PRIORITY,
            ),
            ui.summary.map { it.kind },
        )
    }

    @Test
    fun `un plan vide annonce son absence sur les trois colonnes`() {
        val ui = render(etatEteint())

        assertEquals(listOf("Rien à revoir", "Rien à revoir", "Rien à revoir"), ui.summary.map { it.reference })
        assertTrue(ui.consolidations.isEmpty())
        assertTrue(ui.priorities.isEmpty())
        assertFalse(ui.day.hasSession)
        assertNull(ui.day.start)
    }

    // -----------------------------------------------------------------------
    // Carte du jour
    // -----------------------------------------------------------------------

    @Test
    fun `la carte du jour porte la tache a ouvrir et la marque comme consolidation`() {
        val ui = render(difficiles(etatComplet(), mapOf(6010 to today)))

        assertTrue(ui.day.hasSession)
        val start = assertNotNull(ui.day.start)
        // La séance du plan commence par les consolidations dues : c'est leur catégorie qui décide
        // de l'étape proposée par le lecteur, et non leur position dans la liste.
        assertEquals(ReviewCategory.RECENT, start.reviewTask?.category)
        assertTrue(start.consolidation, "une tâche `recent` ouvre l'étape des trois jours")
        assertNull(start.sessionId, "une révision n'est pas une séance d'apprentissage")
    }

    @Test
    fun `le pied de la carte du jour dit pourquoi le bouton est inactif`() {
        assertTrue(render(etatEteint()).day.footer.startsWith("Ta révision du jour est terminée"))
        assertTrue(render(etatComplet()).day.footer.startsWith("La séance du jour regroupe"))
    }

    // -----------------------------------------------------------------------
    // Carte du cycle
    // -----------------------------------------------------------------------

    @Test
    fun `le compteur du cycle part du premier jour et suit la duree du reglage`() {
        assertEquals("Jour 1 / 7", render(etatComplet()).cycle.dayCounter)

        val quinze = etatComplet().copy(reviewSettings = ReviewSettings(enabled = true, cycleDays = 14))
        assertEquals("Jour 1 / 14", render(quinze).cycle.dayCounter)
    }

    @Test
    fun `le pourcentage est un rapport de poids, et non de versets`() {
        val ui = render(avecCyclePartiel())
        val attendu = Review.reviewWeight(6000) / (Review.reviewWeight(6000) + Review.reviewWeight(6005))

        assertEquals(attendu.toFloat(), ui.cycle.ratio)
        assertTrue(ui.cycle.ratio > 0f, "un verset sur deux est revu : le rapport ne peut pas être nul")
        assertEquals(ReviewText.percentReviewed(attendu), ui.cycle.percent)
    }

    @Test
    fun `un cycle neuf affiche zero pour cent`() {
        val ui = render(etatComplet())
        assertEquals(0f, ui.cycle.ratio)
        assertEquals("0 % réellement révisés", ui.cycle.percent)
    }

    @Test
    fun `les trois statistiques du cycle portent leurs mots et leurs comptes`() {
        val ui = render(avecCyclePartiel())

        assertEquals(listOf("à revoir", "révisés", "restants"), ui.cycle.stats.map { it.label })
        // Le corpus entier, puis ce qui est fait, puis ce qui reste : trois comptes différents, et
        // c'est ce qui rend la ligne lisible d'un coup d'œil.
        assertEquals("2 versets", ui.cycle.stats[0].value)
        assertEquals("1 verset", ui.cycle.stats[1].value)
        assertEquals("1 verset", ui.cycle.stats[2].value)
    }

    @Test
    fun `le rythme d'un cycle vide est une absence, pas un zero par jour`() {
        assertEquals(ReviewText.RHYTHM_EMPTY, render(etatEteint()).cycle.rhythm)

        val rythme = render(etatComplet()).cycle.rhythm
        assertTrue(rythme.isNotEmpty())
        assertTrue(rythme.contains("/ jour"), "un cycle rempli annonce un rythme : $rythme")
    }

    @Test
    fun `le resume du corpus est le meme mot sur les deux cartes`() {
        // Le client d'origine l'écrit deux fois, à l'identique. Deux copies finiraient par
        // diverger, et la personne lirait deux vérités sur le même état.
        val ui = render(etatComplet())
        assertEquals(ui.cycle.corpusSummary, ui.tracking.corpusSummary)
        assertEquals("3 versets mémorisés · 0 Juz’ · 0 Rubu’ · 0 Nisf", ui.tracking.corpusSummary)
    }

    // -----------------------------------------------------------------------
    // Carte du suivi
    // -----------------------------------------------------------------------

    @Test
    fun `le compte des revisions suit l'historique`() {
        assertEquals("0 révisions effectuées", render(etatComplet()).tracking.revisions)

        val une = etatComplet().copy(
            reviewHistory = listOf(
                ReviewEvent(
                    id = "e1",
                    date = today,
                    start = 6000,
                    end = 6000,
                    category = ReviewCategory.HABITUAL,
                    grade = ReviewGrade.PERFECT,
                ),
            ),
        )
        assertEquals("1 révision effectuée", render(une).tracking.revisions)
    }

    // -----------------------------------------------------------------------
    // Reprises
    // -----------------------------------------------------------------------

    @Test
    fun `une reprise de revision est servie sous l'identite de sa tache`() {
        val state = etatComplet().copy(
            studyProgress = mapOf(
                "revision:habitual-6000-6005" to progress(
                    id = "habitual-6000-6005",
                    start = 6000,
                    end = 6005,
                    through = 6002,
                    mode = StudyMode.REVISION,
                    category = ReviewCategory.HABITUAL,
                ),
            ),
        )

        val ligne = render(state).resumes.single()
        assertEquals("Révision à continuer", ligne.title)

        // L'identité est celle de la **tâche de révision**, et non celle de la progression : c'est
        // sous cette clé que la validation sera écrite, et sous aucune autre.
        val tache = assertNotNull(ligne.study.reviewTask)
        assertEquals("habitual-6000-6005", tache.id)
        assertEquals(ReviewCategory.HABITUAL, tache.category)
        assertNull(ligne.study.sessionId)
        assertTrue(ligne.study.reviewing)
        assertFalse(ligne.study.learning)

        // La plage servie est le **reste** : relire ce qui est déjà validé ferait recommencer la
        // progression au lieu de la continuer.
        assertEquals(6003, ligne.study.range.start)
        assertEquals(6005, ligne.study.range.end)
    }

    @Test
    fun `une reprise d'apprentissage n'est pas servie ici`() {
        // Le complément exact du filtre du programme : un même enregistrement ne peut pas être les
        // deux, et servir l'apprentissage ici le ferait apparaître sur deux écrans à la fois.
        val state = etatComplet().copy(
            sessions = listOf(session("a", 6000, 6005)),
            studyProgress = mapOf("learning:a" to progress("a", 6000, 6005, 6002, StudyMode.LEARNING)),
        )

        assertTrue(render(state).resumes.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Carte des consolidations
    // -----------------------------------------------------------------------

    @Test
    fun `la ligne de consolidation porte l'identite que le lecteur validera`() {
        val ligne = render(etatComplet()).consolidations
            .single { it.reference == Quran.reference(Range(6010, 6010)) }

        assertEquals("consolidation-6010-6010", ligne.id)
        assertEquals(ReviewCategory.RECENT, ligne.study.reviewTask?.category)
        assertTrue(ligne.study.consolidation, "une consolidation s'ouvre en mode consolidation")
        assertEquals(ReviewText.consolidateLabel(ligne.reference), ligne.label)
        assertEquals(
            ReviewText.consolidationDetail(Review.reviewQuantity(listOf(Range(6010, 6010))), Dates.addDays(today, -2)),
            ligne.detail,
        )
    }

    @Test
    fun `les trois etapes portent leurs echeances et leurs etats`() {
        val ligne = render(etatComplet()).consolidations
            .single { it.reference == Quran.reference(Range(6010, 6010)) }

        assertEquals(listOf("J+1", "J+3", "J+7"), ligne.steps.map { it.label })
        // Appris il y a deux jours : J+1 est dépassé, J+3 et J+7 sont à venir.
        assertEquals(listOf("À rattraper", "À venir", "À venir"), ligne.steps.map { it.status })
        assertEquals(listOf(true, false, false), ligne.steps.map { it.dueOrPast })
        assertEquals(listOf(false, false, false), ligne.steps.map { it.completed })
        // Appris le 8 mars : J+1 tombe le 9, J+3 le 11, J+7 le 15. Les échéances sont **ancrées sur
        // la date d'apprentissage** et ne bougent pas, même si une étape est validée en retard.
        assertEquals(listOf("9 mars", "11 mars", "15 mars"), ligne.steps.map { it.date })
    }

    @Test
    fun `une etape validee est consolidee et porte la date de sa validation`() {
        val state = Review.completeConsolidation(
            state = etatComplet(),
            range = Range(6010, 6010),
            at = today,
            completedAt = "${today}T20:00:00.000Z",
            targetOffset = 1,
        )

        val ligne = render(state).consolidations
            .single { it.reference == Quran.reference(Range(6010, 6010)) }
        val premiere = ligne.steps.first()

        assertEquals("Consolidé", premiere.status)
        assertTrue(premiere.completed)
        // La date affichée est celle de la **validation**, et non l'échéance : montrer l'échéance
        // d'une étape faite ferait croire qu'elle est en retard.
        assertEquals("10 mars", premiere.date)
    }

    @Test
    fun `les deux listes depliables se replient au dela de cinq lignes`() {
        // Cinq lignes : rien à déplier, donc pas de bouton.
        val cinq = (0 until 5).associate { i ->
            val id = 6000 + i * 10
            id.toString() to Mastery.PERFECT
        }
        val apprisCinq = (0 until 5).associate { i ->
            val id = 6000 + i * 10
            id.toString() to Dates.addDays(today, -2 - i)
        }
        val stateCinq = Program.defaultState().copy(
            knowledge = cinq,
            memorizedAt = apprisCinq,
        )
        val uiCinq = render(stateCinq)
        assertEquals(5, uiCinq.consolidations.size)
        assertFalse(uiCinq.consolidationOverflow)

        // Six lignes : le repli apparaît.
        val six = (0 until 6).associate { i ->
            val id = 6000 + i * 10
            id.toString() to Mastery.PERFECT
        }
        val apprisSix = (0 until 6).associate { i ->
            val id = 6000 + i * 10
            id.toString() to Dates.addDays(today, -2 - i)
        }
        val uiSix = render(Program.defaultState().copy(knowledge = six, memorizedAt = apprisSix))
        assertEquals(6, uiSix.consolidations.size)
        assertTrue(uiSix.consolidationOverflow)
    }

    // -----------------------------------------------------------------------
    // Carte des versets prioritaires
    // -----------------------------------------------------------------------

    @Test
    fun `le resume compte les versets dus aujourd'hui, la carte les montre tous`() {
        // Deux versets difficiles, mais un seul dû aujourd'hui : c'est la seule façon de
        // distinguer `plan.priority` de `plan.rework`, et de prouver que le résumé et la carte ne
        // lisent pas la même liste.
        val state = difficiles(etatComplet(), mapOf(6000 to today, 6010 to Dates.addDays(today, 3)))
        val plan = Review.reviewPlan(state, today)
        val ui = render(state)

        assertEquals(1, plan.priority.size, "un seul verset est dû aujourd'hui")
        assertEquals(2, plan.rework.size, "les deux versets restent marqués difficiles")

        assertEquals("1 verset", ui.summary[2].quantity)
        assertEquals(2, ui.priorities.size)
        // Le mot de la carte suit le compte du **jour**, et non celui de la liste : « 2 versets à
        // retravailler aujourd'hui » devant une liste de deux serait faux, puisqu'un seul est dû.
        assertEquals("1 verset à retravailler aujourd’hui", ui.priorityHeadline)
    }

    @Test
    fun `une ligne prioritaire n'ouvre pas la consolidation`() {
        // Une consolidation ne concerne que les versets **récemment appris**. La catégorie d'une
        // tâche prioritaire est `priority`, donc l'étape des trois jours ne doit pas s'ouvrir.
        val state = difficiles(etatComplet(), mapOf(6000 to today))
        val ligne = render(state).priorities.single()

        assertEquals(ReviewCategory.PRIORITY, ligne.study.reviewTask?.category)
        assertFalse(ligne.study.consolidation)
        assertEquals(ReviewText.priorityDetail(Review.reviewQuantity(listOf(Range(6000, 6000)))), ligne.detail)
    }

    @Test
    fun `la liste des versets prioritaires se replie au dela de cinq lignes`() {
        // `rework` est le groupe des versets difficiles **parmi les versets mémorisés** : marquer
        // un verset que personne n'a appris ne le fait apparaître nulle part. La fixture doit donc
        // mémoriser les six, sans quoi elle n'exercerait pas la règle qu'elle annonce.
        val ids = (0 until 6).map { 6000 + it * 10 }
        val state = difficiles(
            Program.defaultState().copy(
                knowledge = ids.associate { it.toString() to Mastery.PERFECT },
                memorizedAt = ids.associate { it.toString() to Dates.addDays(today, -30) },
            ),
            ids.associateWith { today },
        )
        val ui = render(state)

        assertEquals(6, ui.priorities.size)
        assertTrue(ui.priorityOverflow)
    }

    // -----------------------------------------------------------------------
    // Référentiel
    // -----------------------------------------------------------------------

    companion object {

        /**
         * Charge le référentiel coranique une fois pour toute la classe.
         *
         * Le renderer ne peut pas être appelé avant : `Quran.reference` lève sur un référentiel
         * vide, et c'est au `ViewModel` de garantir l'ordre. Le test le charge donc lui-même,
         * exactement comme l'application le fait au démarrage.
         */
        @BeforeClass
        @JvmStatic
        fun chargerLeReferentiel() {
            Quran.initialize(QuranDataLoader.loadFromClasspath())
        }
    }
}
