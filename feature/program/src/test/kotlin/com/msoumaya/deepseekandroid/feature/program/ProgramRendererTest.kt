package com.msoumaya.deepseekandroid.feature.program

import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.Program
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.QuranDataLoader
import com.msoumaya.deepseekandroid.core.domain.Review
import com.msoumaya.deepseekandroid.core.domain.StudyProgressCalculator
import com.msoumaya.deepseekandroid.core.domain.Texts
import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.Mastery
import com.msoumaya.deepseekandroid.core.model.MushafSource
import com.msoumaya.deepseekandroid.core.model.Pace
import com.msoumaya.deepseekandroid.core.model.Range
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Règles de l'écran Programme
// ---------------------------------------------------------------------------
// Le calcul est une fonction pure de `(état, jour, période)`. Il est donc éprouvé ici sans
// coroutine, sans horloge et sans appareil.
//
// Le jour est **fixé** (« 2026-03-10 », un mardi) et non lu depuis l'horloge : un test qui dépend
// du jour où on le lance passe le mardi et échoue le dimanche. Les jours relatifs sont dérivés de
// cette constante avec `Dates.addDays`, jamais recalculés à la main.
//
// Trois familles de contrôles portent le plus :
//
//   1. **Quelle séance est celle du jour.** Une séance en retard n'est pas « à venir », et une
//      séance déjà faite ne remonte pas. Les deux erreurs sont symétriques, donc couvertes
//      séparément.
//   2. **Les deux formes d'une même tâche.** La carte du jour détaille, le couple du bas résume,
//      et leurs replis disent deux choses différentes. Un seul des quatre textes suffirait à
//      faire mentir l'écran.
//   3. **La ligne de validation et le détail d'une reprise.** Ce sont eux qui disent quel verset
//      a été appris : une erreur d'un cran ferait relire ou sauter un verset sans rien signaler.
//
// Deux tests posent une **précondition** sur leur propre fixture — « cette plage couvre bien
// plusieurs pages », « ce `through` valide bien une seule page ». Ce n'est pas du zèle : sans
// elles, une fixture qui cesserait d'exercer la branche visée comparerait à une chaîne qui n'a
// plus le même sens, et le test resterait vert pour la mauvaise raison.
// ---------------------------------------------------------------------------

class ProgramRendererTest {

    /** Un mardi. */
    private val today = "2026-03-10"
    private val yesterday = Dates.addDays(today, -1)
    private val tomorrow = Dates.addDays(today, 1)
    private val dansHuitJours = Dates.addDays(today, 8)

    /** La source de l'état par défaut : `coranTest`, celle du client d'origine. */
    private val source = MushafSource.CORAN_TEST.persistedKey

    /** Une plage large, qui couvre plusieurs pages dans le découpage de la composition. */
    private val large = Range(6000, 6060)

    private fun render(state: AppState, period: ProgramPeriod = ProgramPeriod.DAY): ProgramUiState =
        ProgramRenderer.render(state, today, period)

    private fun session(
        id: String,
        start: Int,
        end: Int,
        status: SessionStatus,
        date: String,
        scheduledDate: String? = null,
        completedDate: String? = null,
    ) = Session(
        id = id,
        date = date,
        start = start,
        end = end,
        unit = Pace.VERSE3,
        status = status,
        scheduledDate = scheduledDate,
        // Une séance terminée sans instant de validation n'est comptée nulle part : le compte
        // hebdomadaire l'ignorerait, et le test mesurerait autre chose que ce qu'il annonce.
        completedAt = completedDate?.let { "${it}T20:00:00.000Z" },
        completedDate = completedDate,
    )

    private fun partial(
        id: String,
        start: Int,
        end: Int,
        through: Int,
        mode: StudyMode = StudyMode.LEARNING,
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
    )

    /** Une séance du jour interrompue en [through], prête à être reprise. */
    private fun avecReprise(start: Int, end: Int, through: Int): AppState =
        Program.defaultState().copy(
            sessions = listOf(session("a", start, end, SessionStatus.TODO, today, today)),
            studyProgress = mapOf("learning:a" to partial("a", start, end, through)),
        )

    // -----------------------------------------------------------------------
    // Carte du jour : les deux formes de chaque tâche
    // -----------------------------------------------------------------------

    @Test
    fun `sans seance les deux formes d'apprentissage disent deux choses differentes`() {
        val ui = render(Program.defaultState())

        // La carte du jour annonce une absence…
        assertEquals("Programme terminé", ui.today?.learning?.passage)
        assertEquals("Aucune séance", ui.today?.learning?.details)
        // … et le couple du bas annonce un objectif atteint. Deux absences différentes ne se
        // peignent pas pareil, et c'est la source qui le dit.
        assertEquals("Objectif atteint", ui.today?.learning?.compactPassage)
        assertEquals("Modifier mon objectif", ui.today?.learning?.compactDetails)
    }

    @Test
    fun `sans revision due les deux formes de revision disent deux choses differentes`() {
        val ui = render(Program.defaultState())

        assertEquals("Révisions à jour", ui.today?.revision?.passage)
        assertEquals("Aucun passage dû", ui.today?.revision?.details)
        assertEquals("À jour", ui.today?.revision?.compactPassage)
        assertEquals("Voir mes révisions", ui.today?.revision?.compactDetails)
    }

    @Test
    fun `une carte du jour sans rien a servir ne porte ni tache ni verset`() {
        val revision = render(Program.defaultState()).today?.revision

        // Rien à ouvrir : la carte est un texte, et le geste doit retomber sur l'écran des
        // révisions. C'est exactement ce que `study == null && verseId == null` décide dans le
        // composable — un `verseId` inventé ouvrirait le lecteur sur un verset que personne n'a
        // demandé.
        assertNull(revision?.study)
        assertNull(revision?.verseId)
    }

    @Test
    fun `la carte du jour detaille le compte et la page, le couple du bas resume`() {
        val state = Program.defaultState().copy(
            sessions = listOf(session("a", 6000, 6004, SessionStatus.TODO, today, today)),
        )

        val tache = render(state).today!!.learning

        assertEquals(Quran.reference(Range(6000, 6004)), tache.passage)
        assertEquals(
            "5 versets • Page ${StudyProgressCalculator.studyPage(6000, source)}",
            tache.details,
        )
        // La forme courte donne le compte **sans** la page : c'est ce qui la distingue.
        assertEquals(Quran.reference(Range(6000, 6004)), tache.compactPassage)
        assertEquals("5 versets", tache.compactDetails)
        assertNotEquals(tache.details, tache.compactDetails)
    }

    // -----------------------------------------------------------------------
    // Quelle séance est celle du jour
    // -----------------------------------------------------------------------

    @Test
    fun `la seance du jour est celle planifiee aujourd'hui`() {
        val state = Program.defaultState().copy(
            sessions = listOf(
                session("aujourdhui", 6000, 6004, SessionStatus.TODO, today, today),
                session("demain", 6010, 6014, SessionStatus.TODO, tomorrow, tomorrow),
            ),
        )

        val ui = render(state)

        assertEquals(6000, ui.today?.learning?.verseId)
        assertEquals("aujourdhui", ui.today?.learning?.study?.sessionId)
        // La séance retenue est bien celle du jour : la ligne « prochaine séance » n'a rien à
        // annoncer.
        assertNull(ui.today?.nextSession)
    }

    @Test
    fun `sans seance aujourd'hui la carte annonce la prochaine`() {
        val state = Program.defaultState().copy(
            sessions = listOf(session("plus_tard", 6010, 6014, SessionStatus.TODO, tomorrow, tomorrow)),
        )

        val ui = render(state)

        // La séance retenue est la première à venir…
        assertEquals(6010, ui.today?.learning?.verseId)
        // … et la carte le dit, parce qu'elle n'est pas celle du jour.
        assertEquals("Prochaine séance prévue : mercredi 11 mars", ui.today?.nextSession)
    }

    @Test
    fun `une seance en retard n'est pas la seance du jour`() {
        val state = Program.defaultState().copy(
            sessions = listOf(session("retard", 6000, 6004, SessionStatus.TODO, yesterday, yesterday)),
        )

        val ui = render(state)

        // Le repli n'est pas symétrique : une séance en retard n'est pas « à venir ». Confondre
        // les deux ferait disparaître le rattrapage, ou ferait passer une séance oubliée pour
        // celle du jour.
        assertNull(ui.today?.learning?.verseId)
        assertNull(ui.today?.learning?.study)
        assertEquals("Aucune séance", ui.today?.learning?.details)
        assertEquals(1, ui.catchUp.size)
    }

    @Test
    fun `une seance deja faite ne remonte pas comme seance du jour`() {
        val state = Program.defaultState().copy(
            sessions = listOf(session("faite", 6000, 6004, SessionStatus.DONE, today, today, today)),
        )

        assertNull(render(state).today?.learning?.verseId)
    }

    @Test
    fun `la seance du jour voyage avec sa plage et son identifiant`() {
        val state = Program.defaultState().copy(
            sessions = listOf(session("a", 6000, 6004, SessionStatus.TODO, today, today)),
        )

        val requete = render(state).today?.learning?.study

        // Les deux sont nécessaires : la plage dit **quoi** ouvrir, l'identifiant dit **où**
        // écrire la progression. Sans lui, le lecteur afficherait un bandeau qui ne valide rien.
        assertNotNull(requete)
        assertEquals(Range(6000, 6004), requete.range)
        assertEquals("a", requete.sessionId)
        assertTrue(requete.learning)
    }

    // -----------------------------------------------------------------------
    // Périodes
    // -----------------------------------------------------------------------

    @Test
    fun `les trois periodes filtrent la liste a venir`() {
        val state = Program.defaultState().copy(
            sessions = listOf(
                session("a", 6000, 6004, SessionStatus.TODO, today, today),
                session("b", 6010, 6014, SessionStatus.TODO, today, today),
                session("c", 6020, 6024, SessionStatus.TODO, tomorrow, tomorrow),
                session("d", 6030, 6034, SessionStatus.TODO, dansHuitJours, dansHuitJours),
            ),
        )

        assertEquals(listOf("a", "b"), render(state, ProgramPeriod.DAY).upcoming.map { it.id })
        assertEquals(listOf("a", "b", "c"), render(state, ProgramPeriod.WEEK).upcoming.map { it.id })
        assertEquals(listOf("a", "b", "c", "d"), render(state, ProgramPeriod.MONTH).upcoming.map { it.id })
    }

    @Test
    fun `la periode Jour montre la prochaine journee chargee quand aujourd'hui est vide`() {
        val state = Program.defaultState().copy(
            sessions = listOf(
                session("c", 6020, 6024, SessionStatus.TODO, tomorrow, tomorrow),
                session("e", 6030, 6034, SessionStatus.TODO, tomorrow, tomorrow),
            ),
        )

        // La journée de référence est celle de la **première séance à venir**, et non celle du
        // jour : un onglet « Jour » vide n'apprendrait rien.
        assertEquals(listOf("c", "e"), render(state, ProgramPeriod.DAY).upcoming.map { it.id })
    }

    @Test
    fun `la periode Semaine inclut le septieme jour et exclut le huitieme`() {
        val septieme = Dates.addDays(today, 7)
        val state = Program.defaultState().copy(
            sessions = listOf(
                session("j7", 6000, 6004, SessionStatus.TODO, septieme, septieme),
                session("j8", 6010, 6014, SessionStatus.TODO, dansHuitJours, dansHuitJours),
            ),
        )

        // Borne **comprise** : `<= addDays(today, 7)`.
        assertEquals(listOf("j7"), render(state, ProgramPeriod.WEEK).upcoming.map { it.id })
    }

    @Test
    fun `la periode Mois ne filtre rien et laisse la fenetre de dix jours borner`() {
        val dixieme = Dates.addDays(today, 10)
        val onzieme = Dates.addDays(today, 11)
        val state = Program.defaultState().copy(
            sessions = listOf(
                session("j10", 6000, 6004, SessionStatus.TODO, dixieme, dixieme),
                session("j11", 6010, 6014, SessionStatus.TODO, onzieme, onzieme),
            ),
        )

        // « Mois » est un écart assumé du client d'origine : c'est `upcomingSessions` qui borne,
        // à dix jours, et non la période. Le nommer « Mois » ne l'élargit pas.
        assertEquals(listOf("j10"), render(state, ProgramPeriod.MONTH).upcoming.map { it.id })
    }

    @Test
    fun `les trois periodes portent les libelles du selecteur`() {
        assertEquals(listOf("Jour", "Semaine", "Mois"), ProgramPeriod.labels)
    }

    // -----------------------------------------------------------------------
    // Ligne à venir
    // -----------------------------------------------------------------------

    @Test
    fun `une ligne a venir porte l'initiale du jour, le quantieme et le nom arabe`() {
        val state = Program.defaultState().copy(
            sessions = listOf(session("c", 6000, 6004, SessionStatus.TODO, tomorrow, tomorrow)),
        )

        val ligne = render(state).upcoming.single()

        assertEquals("MER.", ligne.weekday)
        assertEquals(11, ligne.day)
        assertEquals(Quran.surahAt(6000).arabic, ligne.arabic)
        assertEquals(
            "5 versets • Page ${StudyProgressCalculator.studyPage(6000, source)}",
            ligne.details,
        )
    }

    @Test
    fun `le libelle du jour suit la date de la seance`() {
        val state = Program.defaultState().copy(
            sessions = listOf(
                session("aujourdhui", 6000, 6004, SessionStatus.TODO, today, today),
                session("demain", 6010, 6014, SessionStatus.TODO, tomorrow, tomorrow),
                session("plus_tard", 6020, 6024, SessionStatus.TODO, dansHuitJours, dansHuitJours),
            ),
        )

        val libelles = render(state, ProgramPeriod.MONTH).upcoming.associate { it.id to it.whenLabel }

        assertEquals("Aujourd'hui", libelles["aujourdhui"])
        assertEquals("Demain", libelles["demain"])
        assertEquals("18 mars", libelles["plus_tard"])
    }

    @Test
    fun `la ligne a venir ouvre la seance, pas le verset`() {
        val state = Program.defaultState().copy(
            sessions = listOf(session("c", 6000, 6004, SessionStatus.TODO, tomorrow, tomorrow)),
        )

        val requete = render(state).upcoming.single().study

        assertEquals(Range(6000, 6004), requete.range)
        assertEquals("c", requete.sessionId)
    }

    // -----------------------------------------------------------------------
    // Rattrapage
    // -----------------------------------------------------------------------

    @Test
    fun `le rattrapage propose les dix premieres seances en retard`() {
        val state = Program.defaultState().copy(
            sessions = (1..12).map { i ->
                val jour = Dates.addDays(today, -i)
                session("r$i", 6000 + i * 10, 6004 + i * 10, SessionStatus.TODO, jour, jour)
            },
        )

        val ui = render(state)

        assertEquals(10, ui.catchUp.size)
        assertEquals("r1", ui.catchUp.first().id)
        assertEquals(
            "$yesterday · ${Quran.reference(Range(6010, 6014))}",
            ui.catchUp.first().label,
        )
    }

    @Test
    fun `le rattrapage ouvre la seance entiere, pas son reste`() {
        val state = Program.defaultState().copy(
            sessions = listOf(session("r", 6000, 6004, SessionStatus.TODO, yesterday, yesterday)),
        )

        val requete = render(state).catchUp.single().study

        // Un rattrapage est une séance qu'on n'a **pas faite**, pas une séance commencée : sa
        // plage est celle de la séance, et non un reste qui n'existe pas.
        assertEquals(Range(6000, 6004), requete.range)
        assertEquals("r", requete.sessionId)
    }

    // -----------------------------------------------------------------------
    // Historique
    // -----------------------------------------------------------------------

    @Test
    fun `l'historique renverse l'ordre de l'etat, sur les vingt derniers`() {
        val state = Program.defaultState().copy(
            sessions = (1..25).map { i ->
                val jour = Dates.addDays(today, -i)
                session("h$i", 6000 + i, 6000 + i, SessionStatus.DONE, jour, jour, jour)
            },
        )

        val ids = render(state).history.map { it.id }

        assertEquals(20, ids.size)
        // `takeLast(20)` garde h6..h25, puis `asReversed()` met h25 en tête. C'est un
        // **renversement**, et non un tri : l'ordre vient de l'état. Le test l'épingle ainsi,
        // pour qu'un tri ajouté plus tard — qui changerait l'ordre des deux clients — se voie.
        assertEquals("h25", ids.first())
        assertEquals("h6", ids.last())
    }

    @Test
    fun `l'historique distingue une seance terminee d'une seance reportee`() {
        val avantHier = Dates.addDays(today, -2)
        val state = Program.defaultState().copy(
            sessions = listOf(
                session("reportee", 6010, 6014, SessionStatus.POSTPONED, avantHier, avantHier),
                session("faite", 6000, 6004, SessionStatus.DONE, yesterday, yesterday, yesterday),
            ),
        )

        val lignes = render(state).history.associate { it.id to it.label }

        assertEquals("$avantHier · Reporté", lignes["reportee"])
        assertEquals("$yesterday · Terminé", lignes["faite"])
    }

    @Test
    fun `une seance encore a faire n'entre pas dans l'historique`() {
        val state = Program.defaultState().copy(
            sessions = listOf(session("a_faire", 6000, 6004, SessionStatus.TODO, yesterday, yesterday)),
        )

        assertTrue(render(state).history.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Reprises
    // -----------------------------------------------------------------------

    @Test
    fun `une reprise d'apprentissage rouvre le reste de la seance`() {
        val ligne = render(avecReprise(6000, 6004, through = 6002)).resumes.single()

        assertEquals("Apprentissage à continuer", ligne.title)
        // La plage ouverte est celle du **reste** : rouvrir la séance entière ferait relire les
        // trois premiers versets, et c'est précisément ce que la carte évite.
        assertEquals(Range(6003, 6004), ligne.study.range)
        assertEquals("a", ligne.study.sessionId)
        assertTrue(ligne.study.learning)
    }

    @Test
    fun `une reprise sans reste ne propose plus rien`() {
        // Tout est validé : la carte disparaît, plutôt que de proposer de relire ce qui est déjà
        // appris sous un titre qui annoncerait un travail déjà fait.
        assertTrue(render(avecReprise(6000, 6004, through = 6004)).resumes.isEmpty())
    }

    @Test
    fun `une seance reportee n'est plus proposee a la reprise`() {
        val state = Program.defaultState().copy(
            sessions = listOf(session("a", 6000, 6004, SessionStatus.POSTPONED, yesterday, yesterday)),
            studyProgress = mapOf("learning:a" to partial("a", 6000, 6004, through = 6002)),
        )

        // La carte serait un bouton mort : la séance n'est plus au programme.
        assertTrue(render(state).resumes.isEmpty())
    }

    @Test
    fun `une reprise marquee terminee n'est pas reprise`() {
        val state = Program.defaultState().copy(
            sessions = listOf(session("a", 6000, 6004, SessionStatus.TODO, today, today)),
            studyProgress = mapOf(
                "learning:a" to partial("a", 6000, 6004, through = 6002)
                    .copy(status = StudyStatus.COMPLETED),
            ),
        )

        // Le reste existe — `through` n'est pas la fin — donc c'est bien le **statut** qui écarte
        // la ligne. Une fixture qui laisserait les deux raisons se confondre ne dirait pas
        // laquelle des deux a joué.
        assertTrue(render(state).resumes.isEmpty())
    }

    @Test
    fun `une reprise de revision n'est pas servie`() {
        val state = Program.defaultState().copy(
            sessions = listOf(session("a", 6000, 6004, SessionStatus.TODO, today, today)),
            studyProgress = mapOf(
                "revision:t1" to partial("t1", 6000, 6004, through = 6002, mode = StudyMode.REVISION),
            ),
        )

        // Une reprise de révision se valide sous l'identité de sa **tâche**, que ce client ne
        // transporte encore par aucune route. La servir sous l'identifiant de la progression
        // écrirait la validation sous une clé que personne ne relirait — et le lendemain,
        // l'application redemanderait le même passage sans que rien ne le dise.
        assertTrue(render(state).resumes.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Ligne de validation et détail d'une reprise
    // -----------------------------------------------------------------------

    @Test
    fun `la validation d'une page s'ecrit au singulier`() {
        val premiere = StudyProgressCalculator.studyPage(large.start, source)
        // `through` est le **premier** verset de la deuxième page : la première est donc
        // entièrement faite, et la deuxième entamée.
        val through = StudyProgressCalculator.studyPageRange(premiere + 1, source).start
        val metrics = StudyProgressCalculator.studyMetrics(large, through, source)

        assertTrue(metrics.pages, "la fixture doit couvrir plusieurs pages")
        assertEquals(1, metrics.done, "une seule page doit être complète")

        val ligne = render(avecReprise(large.start, large.end, through)).resumes.single()

        assertEquals("✓ Page $premiere apprise", ligne.done)
    }

    @Test
    fun `la validation de deux pages s'ecrit au pluriel et nomme les deux`() {
        val premiere = StudyProgressCalculator.studyPage(large.start, source)
        val through = StudyProgressCalculator.studyPageRange(premiere + 1, source).end
        val metrics = StudyProgressCalculator.studyMetrics(large, through, source)

        assertTrue(metrics.pages, "la fixture doit couvrir plusieurs pages")
        assertEquals(2, metrics.done, "deux pages doivent être complètes")

        val ligne = render(avecReprise(large.start, large.end, through)).resumes.single()

        assertEquals("✓ Pages $premiere à ${premiere + 1} apprises", ligne.done)
    }

    @Test
    fun `la validation d'une plage d'une seule page nomme les versets appris`() {
        val page = StudyProgressCalculator.studyPage(6000, source)
        val plagePage = StudyProgressCalculator.studyPageRange(page, source)
        val debut = maxOf(6000, plagePage.start)
        val fin = minOf(debut + 4, plagePage.end)
        val through = fin - 1
        val petite = Range(debut, fin)
        val metrics = StudyProgressCalculator.studyMetrics(petite, through, source)

        assertFalse(metrics.pages, "la fixture doit tenir sur une seule page")

        val ligne = render(avecReprise(petite.start, petite.end, through)).resumes.single()

        // La seconde forme **nomme la plage validée**, pas la plage entière : c'est ce qui a été
        // appris, et l'annoncer autrement ferait croire la tâche finie.
        assertEquals("✓ ${Quran.reference(Range(debut, through))} appris", ligne.done)
    }

    @Test
    fun `une ligne entamee n'est ni faite ni a faire`() {
        val premiere = StudyProgressCalculator.studyPage(large.start, source)
        val through = StudyProgressCalculator.studyPageRange(premiere + 1, source).start
        val metrics = StudyProgressCalculator.studyMetrics(large, through, source)

        val ligne = render(avecReprise(large.start, large.end, through)).resumes.single()

        assertEquals(metrics.pageList.size, ligne.rows.size)
        assertEquals("Page $premiere", ligne.rows.first().label)
        assertEquals(RowState.DONE, ligne.rows.first().state)
        assertEquals(RowState.PARTIAL, ligne.rows[1].state)
        assertTrue(
            ligne.rows.drop(2).all { it.state == RowState.TODO },
            "états obtenus : ${ligne.rows.map { it.state }}",
        )
        assertEquals("Appris", ligne.rows.first().status)
        assertEquals("À continuer", ligne.rows[1].status)
        assertEquals("À apprendre", ligne.rows[2].status)
    }

    @Test
    fun `le detail d'une plage d'une seule page nomme chaque verset sans repeter la sourate`() {
        val page = StudyProgressCalculator.studyPage(6000, source)
        val plagePage = StudyProgressCalculator.studyPageRange(page, source)
        val debut = maxOf(6000, plagePage.start)
        val fin = minOf(debut + 4, plagePage.end)
        val petite = Range(debut, fin)
        val metrics = StudyProgressCalculator.studyMetrics(petite, fin - 1, source)

        assertFalse(metrics.pages, "la fixture doit tenir sur une seule page")

        val ligne = render(avecReprise(petite.start, petite.end, fin - 1)).resumes.single()

        assertEquals(5, ligne.rows.size)
        assertTrue(
            ligne.rows.all { it.label.startsWith("Verset ") },
            "libellés obtenus : ${ligne.rows.map { it.label }}",
        )
        // Une seule sourate : le préfixe serait identique sur chaque ligne et n'apprendrait rien.
        assertTrue(
            ligne.rows.none { it.label.contains(" · ") },
            "le nom de la sourate ne doit pas être répété : ${ligne.rows.map { it.label }}",
        )
    }

    @Test
    fun `le nom de la sourate apparait quand la plage en traverse plusieurs`() {
        val frontiere = Quran.surahs[10].start
        val plage = Range(frontiere - 1, frontiere + 1)
        val metrics = StudyProgressCalculator.studyMetrics(plage, frontiere - 1, source)

        assertFalse(metrics.pages, "la fixture doit tenir sur une seule page")

        val ligne = render(avecReprise(plage.start, plage.end, frontiere - 1)).resumes.single()

        assertEquals(3, ligne.rows.size)
        assertEquals(
            "${Quran.surahs[9].name} · Verset ${Quran.verseAt(frontiere - 1).ayah}",
            ligne.rows[0].label,
        )
        assertEquals("${Quran.surahs[10].name} · Verset 1", ligne.rows[1].label)
    }

    // -----------------------------------------------------------------------
    // Objectif, semaine, révisions éteintes
    // -----------------------------------------------------------------------

    @Test
    fun `l'objectif porte son libelle et son rythme mis en forme`() {
        val goal = render(Program.defaultState()).goal

        assertEquals("Juz’ ‘Amma", goal?.label)
        assertEquals("3 versets / jour", goal?.pace)
    }

    @Test
    fun `les douze rythmes ont tous leur libelle`() {
        // `ProgramRenderer` lit la table avec `getValue`, qui **lève** sur une clé absente : un
        // rythme ajouté au modèle sans son libellé ferait planter l'écran au lieu d'afficher
        // « null / jour ». Ce test est ce qui rend ce choix sûr.
        assertEquals(Pace.entries.size, Texts.paceLabels.size)
        Pace.entries.forEach { pace ->
            assertTrue(Texts.paceLabels.containsKey(pace), "rythme sans libellé : $pace")
        }
    }

    @Test
    fun `l'objectif est a zero sans verset connu`() {
        assertEquals(0, render(Program.defaultState()).goal?.percent)
    }

    @Test
    fun `le bandeau de la semaine compte les seances faites sur les planifiees`() {
        val state = Program.defaultState().copy(
            sessions = listOf(
                session("faite", 6000, 6004, SessionStatus.DONE, today, today, today),
                session("a_faire", 6010, 6014, SessionStatus.TODO, today, today),
            ),
        )

        assertEquals(50, render(state).week?.percent)
    }

    @Test
    fun `le bandeau de la semaine est nul sans seance planifiee`() {
        assertEquals(0, render(Program.defaultState()).week?.percent)
    }

    @Test
    fun `la carte de revision disparait quand les revisions sont eteintes`() {
        val eteint = Program.defaultState().copy(reviewSettings = ReviewSettings(enabled = false))

        // `null` et non une carte vide : l'absence est un état, et l'afficher peindrait une carte
        // que la source n'a pas.
        assertNull(render(eteint).today?.revision)
        // L'apprentissage, lui, garde sa carte même sans séance.
        assertNotNull(render(eteint).today?.learning)
    }

    @Test
    fun `la tache de revision porte sa categorie, qui decide de la consolidation`() {
        val appris = Dates.addDays(today, -2)
        val base = Program.defaultState().copy(
            knowledge = mapOf("6000" to Mastery.PERFECT),
            memorizedAt = mapOf("6000" to appris),
        )
        val state = Review.prepareReviewSchedule(base, today)

        val revision = render(state).today?.revision

        assertNotNull(revision)
        val requete = revision.study
        assertNotNull(requete)
        // La consolidation J+1 est due : la tâche est donc de catégorie `recent`, et c'est cette
        // catégorie qui ouvre l'étape des trois jours. Sans elle, la carte ouvrirait une lecture
        // libre, et la révision faite ne serait enregistrée nulle part.
        assertTrue(requete.consolidation, "une révision `recent` ouvre l'étape des trois jours")
        assertEquals(Range(6000, 6000), requete.range)
        assertNotNull(requete.reviewTask)
    }

    // -----------------------------------------------------------------------
    // Chargement et période publiée
    // -----------------------------------------------------------------------

    @Test
    fun `le rendu n'est plus en chargement une fois calcule`() {
        val ui = render(Program.defaultState())

        assertFalse(ui.loading)
        assertNull(ui.failure)
    }

    @Test
    fun `la periode choisie est publiee avec l'etat`() {
        // L'écran ne garde pas la sélection : elle est une **entrée** du calcul, publiée avec le
        // reste. La garder dans le composable ferait deux sources de vérité — la sélection
        // affichée et la liste filtrée — qui pourraient diverger sans que rien ne le dise.
        assertEquals(ProgramPeriod.WEEK, render(Program.defaultState(), ProgramPeriod.WEEK).period)
    }

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
