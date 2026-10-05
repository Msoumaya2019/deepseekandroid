package com.msoumaya.deepseekandroid.feature.home

import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.Program
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.QuranDataLoader
import com.msoumaya.deepseekandroid.core.model.LastRead
import com.msoumaya.deepseekandroid.core.model.MushafSource
import com.msoumaya.deepseekandroid.core.model.Pace
import com.msoumaya.deepseekandroid.core.model.PersonalProfile
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.Session
import com.msoumaya.deepseekandroid.core.model.SessionStatus
import org.junit.BeforeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Règles de l'accueil
// ---------------------------------------------------------------------------
// Le calcul de l'accueil est une fonction pure de `(état, jour)`. Il est donc éprouvé ici sans
// coroutine, sans horloge et sans appareil.
//
// Le jour est **fixé** (« 2026-03-10 ») et non lu depuis l'horloge : un test qui dépend du jour
// où on le lance passe le lundi et échoue le dimanche. Les jours relatifs sont dérivés de cette
// constante avec `Dates.addDays`, jamais recalculés à la main.
//
// Les deux tests qui comptent le plus portent sur `scheduledDate`. Le cahier des charges est
// explicite : une séance prévue mardi et faite lundi garde `scheduledDate = mardi` et
// `completedAt = lundi`, et la séance suivante ne doit pas prendre la date du jour. Une
// confusion entre `date` et `scheduledDate` ferait disparaître la séance du jour, ou ferait
// apparaître celle d'hier — les deux sens de l'erreur sont donc couverts.
// ---------------------------------------------------------------------------

class HomeRendererTest {

    private val today = "2026-03-10"
    private val yesterday = Dates.addDays(today, -1)

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
        // `Program.stats` exige un `completedAt` non nul pour compter une séance terminée :
        // une séance `done` sans instant de validation est ignorée, ce qui est le comportement
        // voulu (une séance marquée terminée sans date n'a pas eu lieu).
        completedAt = completedDate?.let { "${it}T20:00:00.000Z" },
        completedDate = completedDate,
    )

    // -----------------------------------------------------------------------
    // Point de reprise
    // -----------------------------------------------------------------------

    @Test
    fun `sans derniere lecture le point de reprise est le debut de l'objectif`() {
        val ui = HomeRenderer.render(Program.defaultState(), today)

        val attendu = Program.defaultState().goal.ranges.first().start
        assertEquals(attendu, ui.resume?.verseId)
        assertEquals(Quran.surahAt(attendu).name, ui.resume?.surahName)
        assertEquals(Quran.pageOf(attendu), ui.resume?.page)
    }

    @Test
    fun `la page annoncee suit le decoupage de la source affichee`() {
        // Le point de reprise est ici un verset dont la page **diffère** entre les deux
        // découpages : le verset 746 (5:77), page 121 dans le moushaf de Médine et page 120 dans
        // la composition. Le contrôle porte donc sur les deux sources, et non sur une seule —
        // une règle qui annoncerait toujours la page de Médine tomberait sur la première
        // assertion, et une règle qui n'annoncerait que la composition sur la seconde. Le test
        // voisin, lui, prend le début de l'objectif par défaut, dont la page est la même des deux
        // côtés : il passait avant comme après, et ne prouvait rien sur cette règle.
        val base = Program.defaultState().let {
            it.copy(goal = it.goal.copy(ranges = listOf(Range(746, 760))))
        }

        assertEquals(746, HomeRenderer.render(base, today).resume?.verseId)
        assertEquals(120, HomeRenderer.render(base, today).resume?.page)
        assertEquals(
            121,
            HomeRenderer.render(
                base.copy(reader = base.reader?.copy(mushaf = MushafSource.MEDINA)),
                today,
            ).resume?.page,
        )
    }

    @Test
    fun `la derniere lecture prime sur la seance du jour et sur l'objectif`() {
        val state = Program.defaultState().copy(
            lastRead = LastRead(page = 42, verseId = 100, readAt = "${today}T09:00:00.000Z"),
            sessions = listOf(session("a", 200, 205, SessionStatus.TODO, today, scheduledDate = today)),
        )

        val ui = HomeRenderer.render(state, today)

        assertEquals(100, ui.resume?.verseId)
        // La page enregistrée est reprise telle quelle : elle ne se recalcule pas depuis le
        // verset, sinon une lecture faite dans une autre source reviendrait à la mauvaise page.
        assertEquals(42, ui.resume?.page)
    }

    @Test
    fun `une seance planifiee aujourd'hui mais datee d'hier sert de point de reprise`() {
        val state = Program.defaultState().copy(
            sessions = listOf(
                session("a", 200, 205, SessionStatus.TODO, date = yesterday, scheduledDate = today),
            ),
        )

        val ui = HomeRenderer.render(state, today)

        assertEquals(200, ui.resume?.verseId)
        assertEquals(200, ui.learning.verseId)
    }

    @Test
    fun `une seance datee d'aujourd'hui mais planifiee hier ne compte pas`() {
        val state = Program.defaultState().copy(
            sessions = listOf(
                session("a", 200, 205, SessionStatus.TODO, date = today, scheduledDate = yesterday),
            ),
        )

        val ui = HomeRenderer.render(state, today)

        // Repli sur l'objectif : la séance d'hier n'est pas la séance du jour.
        assertEquals(Program.defaultState().goal.ranges.first().start, ui.resume?.verseId)
        assertNull(ui.learning.verseId)
    }

    @Test
    fun `une seance deja validee ne remonte pas comme apprentissage du jour`() {
        val state = Program.defaultState().copy(
            sessions = listOf(
                session(
                    id = "a",
                    start = 200,
                    end = 205,
                    status = SessionStatus.DONE,
                    date = today,
                    scheduledDate = today,
                    completedDate = today,
                ),
            ),
        )

        val ui = HomeRenderer.render(state, today)

        assertEquals(DailyTask.EMPTY_LEARNING, ui.learning)
    }

    // -----------------------------------------------------------------------
    // Cartes vides
    // -----------------------------------------------------------------------

    @Test
    fun `les cartes vides portent les libelles du depot d'origine`() {
        val ui = HomeRenderer.render(Program.defaultState(), today)

        assertEquals("Aucune séance prévue", ui.learning.passage)
        assertEquals("Programme à jour", ui.learning.details)
        assertEquals("Révisions à jour", ui.revision.passage)
        assertEquals("Aucun passage dû", ui.revision.details)
    }

    @Test
    fun `le comptage des versets s'accorde au pluriel`() {
        assertEquals("1 verset", HomeRenderer.countText(Range(10, 10)))
        assertEquals("2 versets", HomeRenderer.countText(Range(10, 11)))
        assertEquals("5 versets", HomeRenderer.countText(Range(10, 14)))
    }

    @Test
    fun `la carte d'apprentissage porte la reference et le nombre de versets`() {
        val state = Program.defaultState().copy(
            sessions = listOf(session("a", 6000, 6004, SessionStatus.TODO, today, scheduledDate = today)),
        )

        val ui = HomeRenderer.render(state, today)

        assertEquals(Quran.reference(Range(6000, 6004)), ui.learning.passage)
        assertEquals("5 versets", ui.learning.details)
    }

    // -----------------------------------------------------------------------
    // Série et bandeaux
    // -----------------------------------------------------------------------

    @Test
    fun `la serie compte les jours consecutifs`() {
        val state = Program.defaultState().copy(
            sessions = (0..2).map { offset ->
                val jour = Dates.addDays(today, -offset)
                session("s$offset", 100 + offset * 10, 104 + offset * 10, SessionStatus.DONE, jour, jour, jour)
            },
        )

        assertEquals(3, HomeRenderer.render(state, today).week.streak)
    }

    @Test
    fun `la serie s'arrete au premier jour manque`() {
        val state = Program.defaultState().copy(
            sessions = listOf(
                session("a", 100, 104, SessionStatus.DONE, today, today, today),
                // Le jour d'hier manque : la série s'arrête à un.
                session(
                    id = "b",
                    start = 110,
                    end = 114,
                    status = SessionStatus.DONE,
                    date = Dates.addDays(today, -2),
                    scheduledDate = Dates.addDays(today, -2),
                    completedDate = Dates.addDays(today, -2),
                ),
            ),
        )

        assertEquals(1, HomeRenderer.render(state, today).week.streak)
    }

    @Test
    fun `une journee en cours sans activite ne rompt pas la serie`() {
        val state = Program.defaultState().copy(
            sessions = listOf(
                session("a", 100, 104, SessionStatus.DONE, yesterday, yesterday, yesterday),
            ),
        )

        // Rien aujourd'hui, quelque chose hier : la journée n'est pas finie, la série tient.
        assertEquals(1, HomeRenderer.render(state, today).week.streak)
    }

    @Test
    fun `une journee manquee rompt la serie`() {
        val avantHier = Dates.addDays(today, -2)
        val state = Program.defaultState().copy(
            sessions = listOf(session("a", 100, 104, SessionStatus.DONE, avantHier, avantHier, avantHier)),
        )

        // Ni aujourd'hui ni hier : la série est retombée à zéro.
        assertEquals(0, HomeRenderer.render(state, today).week.streak)
    }

    @Test
    fun `les sept pastilles se terminent sur aujourd'hui`() {
        val state = Program.defaultState().copy(
            sessions = listOf(session("a", 100, 104, SessionStatus.DONE, today, today, today)),
        )

        val week = HomeRenderer.render(state, today).week

        assertEquals(WeekSummary.DAYS, week.activeDays.size)
        assertTrue(week.activeDays.last(), "La dernière pastille doit être aujourd'hui")
        assertFalse(
            week.activeDays.dropLast(1).any { it },
            "Aucun autre jour ne doit être actif",
        )
    }

    @Test
    fun `les sept barres ne retiennent que la semaine en cours`() {
        val ancien = Dates.addDays(today, -20)
        val state = Program.defaultState().copy(
            sessions = listOf(
                session("recente", 100, 104, SessionStatus.DONE, today, today, today),
                session("ancienne", 110, 119, SessionStatus.DONE, ancien, ancien, ancien),
            ),
        )

        val week = HomeRenderer.render(state, today).week

        assertEquals(WeekSummary.DAYS, week.daily.size)
        // La séance d'il y a vingt jours est hors de la semaine : elle ne compte nulle part.
        assertEquals(5, week.verses)
        assertEquals(5, week.daily.sum())
    }

    // -----------------------------------------------------------------------
    // Objectif de la semaine
    // -----------------------------------------------------------------------

    @Test
    fun `l'objectif de la semaine est nul sans seance planifiee`() {
        val week = HomeRenderer.render(Program.defaultState(), today).week

        assertEquals(0, week.goalPercent)
        assertEquals(0f, week.goalRatio)
    }

    @Test
    fun `l'objectif de la semaine atteint cent pour cent quand la seule seance est faite`() {
        val state = Program.defaultState().copy(
            sessions = listOf(session("a", 100, 104, SessionStatus.DONE, today, today, today)),
        )

        assertEquals(100, HomeRenderer.render(state, today).week.goalPercent)
    }

    @Test
    fun `l'objectif de la semaine compte les seances faites sur les seances planifiees`() {
        val state = Program.defaultState().copy(
            sessions = listOf(
                session("faite", 100, 104, SessionStatus.DONE, today, today, today),
                session("a_faire", 110, 114, SessionStatus.TODO, today, today),
            ),
        )

        assertEquals(50, HomeRenderer.render(state, today).week.goalPercent)
    }

    // -----------------------------------------------------------------------
    // Divers
    // -----------------------------------------------------------------------

    @Test
    fun `le prenom du profil remonte a l'accueil`() {
        val state = Program.defaultState().copy(
            profile = PersonalProfile(sex = "Homme", firstName = "Moussa"),
        )

        assertEquals("Moussa", HomeRenderer.render(state, today).firstName)
    }

    @Test
    fun `sans profil l'accueil n'a pas de prenom`() {
        assertNull(HomeRenderer.render(Program.defaultState(), today).firstName)
    }

    @Test
    fun `l'accueil n'est plus en chargement une fois calcule`() {
        val ui = HomeRenderer.render(Program.defaultState(), today)

        assertFalse(ui.loading)
        assertNull(ui.failure)
    }

    @Test
    fun `le message d'echec nomme la cause`() {
        assertEquals(
            "Le référentiel coranique n'a pas pu être chargé : boum",
            HomeViewModel.failureMessage(IllegalStateException("boum")),
        )
    }

    @Test
    fun `le message d'echec survit a une cause sans message`() {
        val message = HomeViewModel.failureMessage(RuntimeException())

        assertTrue(
            message.startsWith("Le référentiel coranique n'a pas pu être chargé : "),
            "Le message doit annoncer l'échec : $message",
        )
        assertFalse(
            message.endsWith(": "),
            "Le message ne doit pas se terminer sur un deux-points orphelin : $message",
        )
    }

    companion object {

        /**
         * Charge le référentiel coranique une fois pour toute la classe.
         *
         * Les fichiers JSON du dossier `quran` sont des ressources du module `core:domain` :
         * elles sont sur le chemin de classes des tests, exactement comme elles sont à la racine
         * de l'APK à l'exécution. Un test qui les lirait depuis un chemin de fichier ne
         * prouverait pas que le chargement fonctionne sur l'appareil.
         *
         * Le dossier s'écrit ici sans barre oblique suivie d'une étoile : en Kotlin les
         * commentaires bloc s'imbriquent, donc une telle séquence à l'intérieur de ce commentaire
         * en ouvrirait un second et laisserait le premier ouvert.
         */
        @BeforeClass
        @JvmStatic
        fun chargerLeReferentiel() {
            Quran.initialize(QuranDataLoader.loadFromClasspath())
        }
    }
}
