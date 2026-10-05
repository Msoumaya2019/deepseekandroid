package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.Consolidation
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.ReviewCategory
import com.msoumaya.deepseekandroid.core.model.StudyMode
import com.msoumaya.deepseekandroid.core.model.StudyProgress
import com.msoumaya.deepseekandroid.core.model.StudyStatus
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * La séance que le lecteur sert, éprouvée sur le **vrai** référentiel.
 *
 * Les six mille deux cent trente-six versets sont chargés, et ce n'est pas du zèle : la règle
 * la plus fragile de ce fichier est celle qui choisit **quel verset représente la fin d'une
 * page**. Une table de trois versets ne dirait rien d'une erreur d'indexation, qui produit
 * toujours un verset plausible — donc jamais signalé.
 *
 * ## Les trois plages, et les tests qui les séparent
 *
 * La confusion que ce banc existe pour empêcher est celle de la plage **demandée** et de la
 * plage **prévue**. Deux tests la mettent en scène en donnant volontairement à la requête une
 * plage plus étroite que la séance : le bandeau doit compter la séance, et la validation doit
 * porter sur elle. Si `plannedRange` retombait un jour sur `request.range`, ces deux tests
 * tomberaient — et eux seuls — parce que partout ailleurs les deux valeurs sont égales.
 *
 * ## Ce que ce banc a appris en tombant
 *
 * Trois attentes écrites ici étaient **fausses**, et la mesure les a corrigées : une plage
 * d'une seule page se compte en **versets** et non en pages (`studyMetrics` ne bascule en pages
 * que si la plage en couvre plusieurs), et la page 2 du moushaf n'est pas une page de dix
 * versets mais de cinq — Al-Baqarah 1 à 5. Les deux règles sont désormais écrites, et non
 * supposées.
 */
class StudySessionTest {

    @BeforeTest
    fun installerLeReferentiel() {
        QuranFixture.install()
    }

    // -----------------------------------------------------------------------
    // Ce que la requête désigne
    // -----------------------------------------------------------------------

    @Test
    fun `une requete de seance est une seance d'apprentissage`() {
        val requete = StudySession.forSession(QuranFixture.session("s1", DATE, 100, 110))
        assertTrue(requete.learning)
        assertTrue(requete.focused)
        assertEquals(StudyMode.LEARNING, requete.mode)
        assertEquals("s1", requete.targetId)
    }

    @Test
    fun `une tache de revision est une revision`() {
        val requete = StudySession.forTask(tache("rev-1"))
        assertTrue(!requete.learning)
        assertTrue(requete.focused)
        assertEquals(StudyMode.REVISION, requete.mode)
        assertEquals("rev-1", requete.targetId)
    }

    @Test
    fun `une consolidation est une revision`() {
        val requete = StudySession.forTask(tache("consolidation-100-110"), consolidation = true)
        assertTrue(requete.consolidation)
        assertTrue(requete.reviewing)
        assertEquals(StudyMode.REVISION, requete.mode)
    }

    @Test
    fun `une lecture libre n'est pas une seance`() {
        // Le cas qui décide de tout le reste : sans cible, il n'y a rien à valider. Le mode est
        // défini — l'original fait de même — mais aucune écriture n'aura lieu, puisque
        // `targetId` est nul et que `validate` refuse alors de continuer.
        val requete = StudySession.Request(Range(100, 110))
        assertTrue(!requete.focused)
        assertTrue(!requete.learning)
        assertTrue(!requete.reviewing)
        assertNull(requete.targetId)
        assertEquals(StudyMode.REVISION, requete.mode)
    }

    @Test
    fun `la tache prime sur la revision historique`() {
        // Une révision historique arrive avec son `revisionId` ; une tâche du plan arrive avec
        // son `reviewTask`. Quand les deux sont là, c'est la tâche qui porte la progression.
        val requete = StudySession.forTask(tache("rev-1")).copy(revisionId = "r-100-110")
        assertEquals("rev-1", requete.targetId)
    }

    @Test
    fun `la seance prime sur la tache`() {
        // Une consolidation porte une `reviewTask` **et** peut être ouverte depuis une séance.
        // L'ordre de l'original met la séance en tête, et il est conservé.
        val requete = StudySession.forSession(QuranFixture.session("s1", DATE, 100, 110))
            .copy(reviewTask = tache("rev-1"))
        assertEquals("s1", requete.targetId)
    }

    // -----------------------------------------------------------------------
    // La plage prévue
    // -----------------------------------------------------------------------

    @Test
    fun `la plage prevue d'une seance vient de l'etat, et non de la requete`() {
        val seance = QuranFixture.session("s1", DATE, 100, 110)
        val etat = QuranFixture.onboardedState().copy(sessions = listOf(seance))
        val requete = StudySession.forSession(seance).copy(range = Range(105, 110))
        assertEquals(Range(100, 110), StudySession.plannedRange(etat, requete))
    }

    @Test
    fun `la plage prevue d'une revision vient du compte rendu enregistre`() {
        val etat = QuranFixture.onboardedState()
            .copy(studyProgress = mapOf(CLE_REV to compteRendu(id = "rev-1")))
        val requete = StudySession.forTask(tache("rev-1")).copy(range = Range(105, 110))
        assertEquals(Range(100, 110), StudySession.plannedRange(etat, requete))
    }

    @Test
    fun `sans seance ni compte rendu la plage demandee sert de repli`() {
        // Le repli n'est pas décoratif : c'est lui qui rend une requête utilisable avant que le
        // programme ait été généré, et après qu'une régénération a retiré la séance.
        val etat = QuranFixture.onboardedState()
        val requete = StudySession.forSession(QuranFixture.session("s1", DATE, 100, 110))
        assertEquals(Range(100, 110), StudySession.plannedRange(etat, requete))
    }

    // -----------------------------------------------------------------------
    // Le dernier verset validé
    // -----------------------------------------------------------------------

    @Test
    fun `sans validation le dernier verset est celui qui precede la plage`() {
        // Et non le premier : `validateStudyProgress` calcule le début de la validation par
        // `max(range.start, through + 1)`. Annoncer `range.start` ferait commencer la première
        // validation au deuxième verset, et le premier ne serait jamais compté comme appris.
        val seance = QuranFixture.session("s1", DATE, 100, 110)
        val etat = QuranFixture.onboardedState().copy(sessions = listOf(seance))
        assertEquals(99, StudySession.through(etat, StudySession.forSession(seance)))
    }

    @Test
    fun `avec un compte rendu le dernier verset est celui enregistre`() {
        val seance = QuranFixture.session("s1", DATE, 100, 110)
        val etat = QuranFixture.onboardedState().copy(
            sessions = listOf(seance),
            studyProgress = mapOf(CLE_SEANCE to compteRendu(through = 105)),
        )
        assertEquals(105, StudySession.through(etat, StudySession.forSession(seance)))
    }

    @Test
    fun `une lecture libre ne trouve aucun compte rendu`() {
        val etat = QuranFixture.onboardedState()
            .copy(studyProgress = mapOf(CLE_SEANCE to compteRendu()))
        assertNull(StudySession.record(etat, StudySession.Request(Range(100, 110))))
    }

    // -----------------------------------------------------------------------
    // La plage ouverte
    // -----------------------------------------------------------------------

    @Test
    fun `une tache partielle se rouvre sur son reste`() {
        val seance = QuranFixture.session("s1", DATE, 100, 110)
        val etat = QuranFixture.onboardedState().copy(
            sessions = listOf(seance),
            studyProgress = mapOf(CLE_SEANCE to compteRendu(through = 105)),
        )
        val ouverte = StudySession.opening(etat, StudySession.forSession(seance))
        assertEquals(Range(106, 110), ouverte.range)
    }

    @Test
    fun `une tache terminee se rouvre sur sa plage entiere`() {
        // Il n'y a pas de reste à reprendre : `remainingStudyRange` rendrait `null`, et la
        // séance terminée se rouvre donc à sa plage entière, comme dans l'original.
        val seance = QuranFixture.session("s1", DATE, 100, 110)
        val etat = QuranFixture.onboardedState().copy(
            sessions = listOf(seance),
            studyProgress = mapOf(
                CLE_SEANCE to compteRendu(through = 110, status = StudyStatus.COMPLETED),
            ),
        )
        assertEquals(Range(100, 110), StudySession.opening(etat, StudySession.forSession(seance)).range)
    }

    @Test
    fun `une lecture libre s'ouvre sur la plage demandee`() {
        val etat = QuranFixture.onboardedState()
            .copy(studyProgress = mapOf(CLE_SEANCE to compteRendu(through = 105)))
        val requete = StudySession.Request(Range(100, 110))
        assertEquals(Range(100, 110), StudySession.opening(etat, requete).range)
    }

    // -----------------------------------------------------------------------
    // La consolidation
    // -----------------------------------------------------------------------

    @Test
    fun `la premiere etape non validee est proposee`() {
        val etat = QuranFixture.onboardedState().copy(
            reviewConsolidations = mapOf("100" to consolidation(faits = listOf(1))),
        )
        assertEquals(3, StudySession.consolidationOffset(etat, requeteConsolidation()))
    }

    @Test
    fun `les etapes faites avancent une a une`() {
        // La règle de l'original est le **premier** offset non validé dans l'ordre 1, 3, 7 —
        // et non « le plus grand validé plus un ». La différence se voit sur une étape faite
        // hors d'ordre : J+3 validée sans J+1 laisse J+1 proposée, ce qui est voulu, puisqu'une
        // étape manquée doit rester rattrapable.
        val apresUn = QuranFixture.onboardedState().copy(
            reviewConsolidations = mapOf("100" to consolidation(faits = listOf(1))),
        )
        assertEquals(3, StudySession.consolidationOffset(apresUn, requeteConsolidation()))

        val apresDeux = QuranFixture.onboardedState().copy(
            reviewConsolidations = mapOf("100" to consolidation(faits = listOf(1, 3))),
        )
        assertEquals(7, StudySession.consolidationOffset(apresDeux, requeteConsolidation()))

        val horsOrdre = QuranFixture.onboardedState().copy(
            reviewConsolidations = mapOf("100" to consolidation(faits = listOf(3))),
        )
        assertEquals(1, StudySession.consolidationOffset(horsOrdre, requeteConsolidation()))
    }

    @Test
    fun `les trois etapes faites ne proposent plus rien`() {
        val etat = QuranFixture.onboardedState().copy(
            reviewConsolidations = mapOf("100" to consolidation(faits = listOf(1, 3, 7))),
        )
        assertNull(StudySession.consolidationOffset(etat, requeteConsolidation()))
    }

    @Test
    fun `sans aucune consolidation le jour un est propose`() {
        val etat = QuranFixture.onboardedState()
        assertEquals(1, StudySession.consolidationOffset(etat, requeteConsolidation()))
    }

    // -----------------------------------------------------------------------
    // Le bandeau
    // -----------------------------------------------------------------------

    @Test
    fun `le bandeau d'une seance annonce l'apprentissage du jour`() {
        val seance = QuranFixture.session("s1", DATE, 100, 110)
        val etat = QuranFixture.onboardedState().copy(sessions = listOf(seance))
        val bandeau = StudySession.banner(etat, StudySession.forSession(seance), SOURCE)
        assertEquals("Apprentissage du jour", bandeau.title)
        assertEquals("Terminer mon apprentissage", bandeau.actionLabel)
    }

    @Test
    fun `le bandeau d'une revision annonce la revision du jour`() {
        val etat = QuranFixture.onboardedState()
        val bandeau = StudySession.banner(etat, StudySession.forTask(tache("rev-1")), SOURCE)
        assertEquals("Révision du jour", bandeau.title)
        assertEquals("Terminer ma révision", bandeau.actionLabel)
    }

    @Test
    fun `le bandeau d'une consolidation annonce le jour et masque le compteur`() {
        val etat = QuranFixture.onboardedState().copy(
            reviewConsolidations = mapOf("100" to consolidation(faits = listOf(1))),
        )
        val bandeau = StudySession.banner(etat, requeteConsolidation(), SOURCE)
        assertEquals("Consolidation · J+3", bandeau.title)
        assertEquals("Valider la consolidation J+3", bandeau.actionLabel)
        // Le compteur est masqué pour une consolidation, comme dans l'original : une étape
        // porte sur une poignée de versets, et « 0 / 3 » y ressemblerait à une séance qu'on
        // n'a pas commencée.
        assertNull(bandeau.progress)
    }

    @Test
    fun `les trois etapes faites annoncent la derniere`() {
        val etat = QuranFixture.onboardedState().copy(
            reviewConsolidations = mapOf("100" to consolidation(faits = listOf(1, 3, 7))),
        )
        // L'original écrit `consolidationDay ?? 7` : quand il n'y a plus d'étape à proposer, la
        // coquille annonce la dernière plutôt que rien.
        assertEquals("Consolidation · J+7", StudySession.banner(etat, requeteConsolidation(), SOURCE).title)
    }

    @Test
    fun `le bandeau compte la plage prevue et non la plage demandee`() {
        // Le test qui sépare les deux plages. La requête ne demande que la fin de la séance —
        // une reprise partielle — mais le bandeau doit annoncer la séance entière.
        val page = Quran.pageRange(2)
        val seance = QuranFixture.session("s1", DATE, page.start, page.end)
        val etat = QuranFixture.onboardedState().copy(sessions = listOf(seance))
        val requete = StudySession.forSession(seance).copy(range = Range(page.start + 2, page.end))
        val attendu = "0 / ${page.end - page.start + 1} versets"
        assertEquals(attendu, StudySession.banner(etat, requete, SOURCE).progress)
    }

    @Test
    fun `le bandeau avance au rythme du dernier verset valide`() {
        val page = Quran.pageRange(2)
        val seance = QuranFixture.session("s1", DATE, page.start, page.end)
        val etat = QuranFixture.onboardedState().copy(
            sessions = listOf(seance),
            studyProgress = mapOf(
                CLE_SEANCE to compteRendu(
                    start = page.start,
                    end = page.end,
                    through = page.start + 2,
                ),
            ),
        )
        val bandeau = StudySession.banner(etat, StudySession.forSession(seance), SOURCE)
        assertEquals("3 / ${page.end - page.start + 1} versets", bandeau.progress)
    }

    @Test
    fun `le bandeau dit Page au singulier quand la plage tient sur une page`() {
        // Attention : ce libellé suit un critère **différent** de celui du résumé de la feuille
        // de validation. Ici c'est `first == last` — les deux bornes de page — tandis que le
        // résumé bascule sur `pages`, qui est vrai seulement quand la plage couvre plusieurs
        // pages. Une plage d'une page s'annonce donc « Page 2 » dans le bandeau et se résume en
        // versets dans la feuille. C'est le comportement de l'original, et les deux règles sont
        // éprouvées séparément.
        val page = Quran.pageRange(2)
        val seance = QuranFixture.session("s1", DATE, page.start, page.end)
        val etat = QuranFixture.onboardedState().copy(sessions = listOf(seance))
        val bandeau = StudySession.banner(etat, StudySession.forSession(seance), SOURCE)
        assertEquals("Page", bandeau.pageLabel)
        assertEquals("2", bandeau.pageText)
    }

    @Test
    fun `le bandeau dit Pages au pluriel et donne les bornes`() {
        val debut = Quran.pageRange(2).start
        val fin = Quran.pageRange(4).end
        val seance = QuranFixture.session("s1", DATE, debut, fin)
        val etat = QuranFixture.onboardedState().copy(sessions = listOf(seance))
        val bandeau = StudySession.banner(etat, StudySession.forSession(seance), SOURCE)
        assertEquals("Pages", bandeau.pageLabel)
        assertEquals("2 → 4", bandeau.pageText)
    }

    // -----------------------------------------------------------------------
    // La validation
    // -----------------------------------------------------------------------

    @Test
    fun `une lecture libre ne valide rien`() {
        // La garde la plus importante du fichier. `assertSame` et non `assertEquals` : l'état
        // doit être rendu **tel quel**, sans être recopié — une copie identique passerait un
        // contrôle d'égalité tout en ayant touché à l'état, et c'est justement ce qu'on refuse.
        val etat = QuranFixture.onboardedState()
        val requete = StudySession.Request(Range(100, 110))
        assertSame(etat, StudySession.validate(etat, requete, 110, SOURCE, at = DATE))
    }

    @Test
    fun `une validation hors bornes ne change rien`() {
        val seance = QuranFixture.session("s1", DATE, 100, 110)
        val etat = QuranFixture.onboardedState().copy(sessions = listOf(seance))
        assertSame(
            etat,
            StudySession.validate(etat, StudySession.forSession(seance), 200, SOURCE, at = DATE),
        )
    }

    @Test
    fun `valider une partie ecrit un compte rendu partiel`() {
        val seance = QuranFixture.session("s1", DATE, 100, 110)
        val etat = QuranFixture.onboardedState().copy(sessions = listOf(seance))
        val apres = StudySession.validate(etat, StudySession.forSession(seance), 105, SOURCE, at = DATE)
        val record = apres.studyProgress?.get(CLE_SEANCE)
        assertEquals(105, record?.through)
        assertEquals(StudyStatus.PARTIAL, record?.status)
        assertEquals(StudyMode.LEARNING, record?.mode)
    }

    @Test
    fun `valider toute la seance la termine`() {
        val seance = QuranFixture.session("s1", DATE, 100, 110)
        val etat = QuranFixture.onboardedState().copy(sessions = listOf(seance))
        val apres = StudySession.validate(etat, StudySession.forSession(seance), 110, SOURCE, at = DATE)
        assertEquals(StudyStatus.COMPLETED, apres.studyProgress?.get(CLE_SEANCE)?.status)
        assertEquals(
            com.msoumaya.deepseekandroid.core.model.SessionStatus.DONE,
            apres.sessions.first { it.id == "s1" }.status,
        )
    }

    @Test
    fun `la validation porte sur la plage prevue et non sur la plage demandee`() {
        // Second test qui sépare les deux plages, et le plus coûteux s'il se trompait : les
        // bornes écrites dans le compte rendu seraient celles de la requête, donc une reprise
        // partielle écraserait la plage de la séance — et la progression suivante serait
        // refusée par le garde-fou de bornes, sans que rien ne le dise.
        val seance = QuranFixture.session("s1", DATE, 100, 110)
        val etat = QuranFixture.onboardedState().copy(sessions = listOf(seance))
        val requete = StudySession.forSession(seance).copy(range = Range(106, 110))
        val apres = StudySession.validate(etat, requete, 110, SOURCE, at = DATE)
        assertEquals(100, apres.studyProgress?.get(CLE_SEANCE)?.start)
        assertEquals(110, apres.studyProgress?.get(CLE_SEANCE)?.end)
    }

    @Test
    fun `la categorie d'une tache est recopiee dans le compte rendu`() {
        val etat = QuranFixture.onboardedState()
        val requete = StudySession.forTask(
            tache("rev-1").copy(category = ReviewCategory.PRIORITY),
        )
        val apres = StudySession.validate(etat, requete, 110, SOURCE, at = DATE)
        assertEquals(ReviewCategory.PRIORITY, apres.studyProgress?.get(CLE_REV)?.category)
    }

    // -----------------------------------------------------------------------
    // Les calculs de la feuille de validation
    // -----------------------------------------------------------------------

    @Test
    fun `le premier verset a valider suit le dernier valide`() {
        assertEquals(106, StudySession.Completion.nextStart(Range(100, 110), 105))
        assertEquals(100, StudySession.Completion.nextStart(Range(100, 110), 99))
    }

    @Test
    fun `le premier verset a valider ne remonte jamais avant la plage`() {
        // Un `through` antérieur à la plage — un état incohérent, ou une tâche déplacée — ne
        // doit pas faire proposer un verset qui n'appartient pas à la séance.
        assertEquals(100, StudySession.Completion.nextStart(Range(100, 110), 40))
    }

    @Test
    fun `le point de depart propose la fin de la page affichee`() {
        val page = Quran.pageOf(100)
        val plage = Quran.pageRange(page)
        val etendue = Range(plage.start, plage.end + 5)
        val propose = StudySession.Completion.initialEndpoint(
            range = etendue,
            through = etendue.start - 1,
            currentPage = page,
            source = SOURCE,
        )
        assertEquals(plage.end, propose)
    }

    @Test
    fun `le point de depart ne revient jamais sur ce qui est deja valide`() {
        // Le plancher : la page affichée est entièrement validée, donc la proposition doit
        // passer au verset suivant — sinon la feuille proposerait de revalider du déjà fait.
        val page = Quran.pageOf(100)
        val plage = Quran.pageRange(page)
        val etendue = Range(plage.start, plage.end + 5)
        val propose = StudySession.Completion.initialEndpoint(
            range = etendue,
            through = plage.end,
            currentPage = page,
            source = SOURCE,
        )
        assertEquals(plage.end + 1, propose)
    }

    @Test
    fun `le point de depart ne sort jamais de la plage`() {
        val plage = Range(100, 110)
        val propose = StudySession.Completion.initialEndpoint(
            range = plage,
            through = 99,
            currentPage = 600,
            source = SOURCE,
        )
        assertEquals(110, propose)
    }

    @Test
    fun `tout valider retient la fin de la plage`() {
        val plage = Range(100, 110)
        assertEquals(110, StudySession.Completion.selected(plage, endpoint = 105, all = true))
        assertEquals(105, StudySession.Completion.selected(plage, endpoint = 105, all = false))
    }

    @Test
    fun `le resume compte les pages terminees`() {
        val debut = Quran.pageRange(2).start
        val fin = Quran.pageRange(4).end
        val resume = StudySession.Completion.summary(
            range = Range(debut, fin),
            through = debut - 1,
            selected = Quran.pageRange(4).start,
            source = SOURCE,
            learning = true,
        )
        // Deux pages terminées — la 2 et la 3 — et la quatrième entamée : sans la mention
        // « en cours », le compte des pages apprises aurait l'air faux, puisqu'il ne compte
        // que les pages finies.
        assertEquals("Pages 2 à 3 apprises · page 4 en cours", resume)
    }

    @Test
    fun `le resume d'une plage d'une seule page se lit en versets`() {
        // Une plage qui **tient** sur une page n'est pas comptée en pages : `studyMetrics` ne
        // bascule en pages que lorsque la plage en couvre plusieurs. C'est la règle de
        // l'original, et elle a une conséquence visible — une séance d'une page s'annonce en
        // versets dans la feuille de validation.
        val plage = Quran.pageRange(2)
        val resume = StudySession.Completion.summary(
            range = plage,
            through = plage.start - 1,
            selected = plage.end,
            source = SOURCE,
            learning = true,
        )
        assertEquals(Quran.reference(plage) + " appris", resume)
    }

    @Test
    fun `le resume d'une revision se lit au meme format`() {
        val plage = Quran.pageRange(2)
        val resume = StudySession.Completion.summary(
            range = plage,
            through = plage.start - 1,
            selected = plage.end,
            source = SOURCE,
            learning = false,
        )
        assertEquals(Quran.reference(plage) + " révisés", resume)
    }

    @Test
    fun `le bouton dit ce qui sera ecrit`() {
        val plage = Range(100, 110)
        assertEquals(
            "Valider tout l'apprentissage",
            StudySession.Completion.validateLabel(plage, 110, all = true, learning = true, unitIsPage = false, page = 3, ayah = 5),
        )
        assertEquals(
            "Valider toute la révision",
            StudySession.Completion.validateLabel(plage, 110, all = false, learning = false, unitIsPage = false, page = 3, ayah = 5),
        )
        assertEquals(
            "Valider jusqu'à la page 3",
            StudySession.Completion.validateLabel(plage, 105, all = false, learning = true, unitIsPage = true, page = 3, ayah = 5),
        )
        assertEquals(
            "Valider jusqu'au verset 5",
            StudySession.Completion.validateLabel(plage, 105, all = false, learning = true, unitIsPage = false, page = 3, ayah = 5),
        )
    }

    // -----------------------------------------------------------------------
    // Outils
    // -----------------------------------------------------------------------

    private fun tache(id: String): Review.ReviewTask = Review.ReviewTask(
        start = 100,
        end = 110,
        id = id,
        category = ReviewCategory.HABITUAL,
    )

    private fun requeteConsolidation(): StudySession.Request =
        StudySession.forTask(tache("consolidation-100-110"), consolidation = true)

    private fun consolidation(faits: List<Int>): Consolidation = Consolidation(
        learnedAt = DATE,
        completed = faits.associateWith { DATE },
    )

    /**
     * Un compte rendu dont les bornes **suivent la séance** par défaut.
     *
     * C'est délibéré : `validateStudyProgress` refuse d'écrire quand les bornes du compte rendu
     * enregistré diffèrent de celles de la plage prévue. Un outil qui figerait 100..110 ferait
     * passer les tests de validation sur une séance de cette plage seulement, et les autres
     * mesureraient un refus sans le dire.
     */
    private fun compteRendu(
        id: String = "s1",
        start: Int = 100,
        end: Int = 110,
        through: Int = 105,
        status: StudyStatus = StudyStatus.PARTIAL,
    ): StudyProgress = StudyProgress(
        id = id,
        mode = if (id == "s1") StudyMode.LEARNING else StudyMode.REVISION,
        start = start,
        end = end,
        through = through,
        page = 1,
        source = SOURCE,
        updatedAt = DATE,
        status = status,
    )

    private companion object {
        const val DATE = "2026-10-05"
        const val SOURCE = "traditional"
        const val CLE_SEANCE = "learning:s1"
        const val CLE_REV = "revision:rev-1"
    }
}
