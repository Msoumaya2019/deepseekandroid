package com.msoumaya.deepseekandroid.feature.progress

import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.Program
import com.msoumaya.deepseekandroid.core.domain.ProgressText
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.QuranDataLoader
import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.LastRead
import com.msoumaya.deepseekandroid.core.model.Mastery
import com.msoumaya.deepseekandroid.core.model.Pace
import com.msoumaya.deepseekandroid.core.model.ReviewCategory
import com.msoumaya.deepseekandroid.core.model.ReviewEvent
import com.msoumaya.deepseekandroid.core.model.ReviewGrade
import com.msoumaya.deepseekandroid.core.model.Revision
import com.msoumaya.deepseekandroid.core.model.Session
import com.msoumaya.deepseekandroid.core.model.SessionStatus
import com.msoumaya.deepseekandroid.core.model.StudyMode
import com.msoumaya.deepseekandroid.core.model.StudyProgress
import com.msoumaya.deepseekandroid.core.model.StudyStatus
import com.msoumaya.deepseekandroid.core.model.StudyValidation
import org.junit.BeforeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Règles de l'écran « Progrès »
// ---------------------------------------------------------------------------
// Le calcul est une fonction pure de `(état, période, jour)`. Il est donc éprouvé ici sans
// coroutine, sans horloge et sans appareil, sur le **vrai** référentiel — 6 236 versets et
// 604 pages.
//
// Le jour est **fixé** (« 2026-03-10 », un mardi) et non lu depuis l'horloge. Les jours relatifs
// sont dérivés de cette constante avec `Dates.addDays`.
//
// Quatre familles de contrôles portent le plus, parce que ce sont celles qui peuvent **mentir** :
//
//   1. **Quel compteur nourrit quelle période.** `stats.today`, `stats.week` et `stats.month`
//      sont trois nombres distincts ; les permuter afficherait le bon chiffre sous le mauvais
//      mot. Le test construit donc un état où **les trois diffèrent** — sans cette précondition,
//      une permutation resterait invisible.
//
//   2. **Deux comptes de révisions qui ne s'accordent pas.** « Révisions faites » compte les
//      entrées de `reviewHistory` ; le tableau de bord des révisions affiche la somme des
//      `completedCount`. Le test pose un état où les deux nombres diffèrent, et exige celui de
//      l'écran — c'est la seule façon de distinguer les deux règles.
//
//   3. **Ce que le graphique somme.** Des versets, et non des séances : deux séances de trois
//      versets le même jour font six. Le test le mesure, et mesure aussi qu'une séance suivie par
//      sa progression fine n'est pas comptée **deux fois**.
//
//   4. **Une divergence assumée avec `Program.stats`.** Le graphique accepte une séance terminée
//      sans horodatage, le compteur non. Le test la construit et exige les deux comportements :
//      c'est le seul endroit où l'écart est visible, et le taire ferait passer un « 0 verset » sur
//      un travail réel pour une panne.
//
// Les fenêtres du graphique sont mesurées sur leurs **bornes**, pas sur leur longueur : une
// fenêtre de sept jours mal ancrée garde sept barres et se trompe de semaine.
// ---------------------------------------------------------------------------

class ProgressRendererTest {

    /** Un mardi. */
    private val today = "2026-03-10"

    private fun render(state: AppState, period: ProgressText.Period) =
        ProgressRenderer.render(state, period, today)

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    private fun seance(
        id: String,
        jour: String,
        start: Int,
        end: Int,
        terminee: Boolean = true,
        horodatage: Boolean = true,
    ) = Session(
        id = id,
        date = jour,
        start = start,
        end = end,
        unit = Pace.VERSE3,
        status = if (terminee) SessionStatus.DONE else SessionStatus.TODO,
        scheduledDate = jour,
        completedAt = if (terminee && horodatage) "${jour}T10:00:00Z" else null,
        completedDate = if (terminee && horodatage) jour else null,
    )

    /**
     * Trois séances terminées, dont les trois fenêtres de `Program.stats` diffèrent.
     *
     * Aujourd'hui : 3 versets. Cette semaine (lundi 9 mars) : 3 + 3 = 6. Ce mois : 6 + 5 = 11.
     */
    private fun etatTroisPeriodes(): AppState = Program.defaultState().copy(
        sessions = listOf(
            seance("aujourdhui", today, 1, 3),
            seance("cette-semaine", Dates.addDays(today, -1), 10, 12),
            seance("ce-mois", "2026-03-05", 20, 24),
        ),
    )

    /** Un état où la page 1 (versets 1 à 7) est entièrement connue. */
    private fun etatPageUneConnue(): AppState = Program.defaultState().copy(
        knowledge = (1..7).associate { "$it" to Mastery.PERFECT },
    )

    private fun enregistrement(id: String, vararg jours: String) = StudyProgress(
        id = id,
        mode = StudyMode.LEARNING,
        start = 1,
        end = 6,
        through = 6,
        page = 1,
        source = "coranTest",
        updatedAt = "${today}T10:00:00Z",
        status = StudyStatus.COMPLETED,
        validations = jours.map { StudyValidation(start = 1, end = 3, date = it) },
    )

    // Le référentiel est chargé **une fois pour la classe** : `Quran.initialize` est idempotent,
    // mais le charger par test relirait 6 236 versets pour rien.
    companion object {
        @BeforeClass
        @JvmStatic
        fun chargerLeReferentiel() {
            Quran.initialize(QuranDataLoader.loadFromClasspath())
        }
    }

    // -----------------------------------------------------------------------
    // Anneau
    // -----------------------------------------------------------------------

    @Test
    fun `le rendu n'est ni en chargement ni en echec`() {
        // Le chargement et l'échec appartiennent au `ViewModel` : le renderer ne s'exécute que sur
        // un référentiel prêt. Un état de chargement produit ici masquerait un écran vide.
        val state = render(Program.defaultState(), ProgressText.Period.WEEK)
        assertFalse(state.loading)
        assertNull(state.failure)
    }

    @Test
    fun `un etat vierge affiche un anneau a zero`() {
        val ring = assertNotNull(render(Program.defaultState(), ProgressText.Period.WEEK).ring)
        assertEquals(0f, ring.ratio)
        assertEquals("0 %", ring.percent)
        assertEquals(0, ring.knownVerses)
        assertEquals(6236, ring.totalVerses)
    }

    @Test
    fun `l'anneau compte les versets memorises sur le volume total`() {
        val state = render(etatPageUneConnue(), ProgressText.Period.WEEK)
        val ring = assertNotNull(state.ring)
        assertEquals(7, ring.knownVerses)

        // Le rapport est un rapport de **volume**, et non de nombre de versets : c'est la règle du
        // domaine, et le test la recalcule sur les mêmes poids plutôt que de la supposer.
        val attendu = Quran.volume((1..7).toList()).toDouble() / Quran.totalVolume
        assertEquals(attendu.toFloat(), ring.ratio)
        assertEquals(Math.round(attendu * 100).toInt(), ring.percent.removeSuffix(" %").toInt())
    }

    @Test
    fun `le denominateur du compteur est le nombre de versets, pas le volume en lettres`() {
        // Le piège est réel : `Quran.totalVolume` est la somme des poids en **lettres arabes**,
        // l'unité dans laquelle les pourcentages sont mesurés. L'employer comme dénominateur
        // afficherait « / 320543 » sous un compteur de versets — et cela ne se voit qu'à l'écran.
        assertEquals(6236, Quran.verses.size)
        assertTrue(
            Quran.totalVolume != Quran.verses.size,
            "le volume en lettres ne doit pas valoir le nombre de versets : sans cette " +
                "précondition, ce contrôle ne distinguerait plus les deux grandeurs",
        )
        // Le chiffre est **mesuré** et figé : un commentaire qui cite une valeur non vérifiée est
        // une supposition, et c'est précisément ainsi que le défaut a été écrit la première fois.
        assertEquals(320543, Quran.totalVolume, "le volume en lettres du référentiel livré")

        val ring = assertNotNull(render(Program.defaultState(), ProgressText.Period.WEEK).ring)
        assertEquals(Quran.verses.size, ring.totalVerses)
    }

    // -----------------------------------------------------------------------
    // Versets appris : la période décide
    // -----------------------------------------------------------------------

    @Test
    fun `les trois periodes donnent trois nombres distincts`() {
        // Précondition : sans elle, une permutation des trois compteurs passerait inaperçue.
        val state = etatTroisPeriodes()
        val stats = Program.stats(state, today)
        assertEquals(3, stats.today)
        assertEquals(6, stats.week)
        assertEquals(11, stats.month)
    }

    @Test
    fun `le titre et la valeur des versets appris suivent la periode`() {
        val state = etatTroisPeriodes()

        val jour = assertNotNull(render(state, ProgressText.Period.DAY).learned)
        assertEquals("Versets appris aujourd’hui", jour.title)
        assertEquals("3", jour.value)

        val semaine = assertNotNull(render(state, ProgressText.Period.WEEK).learned)
        assertEquals("Versets appris cette semaine", semaine.title)
        assertEquals("6", semaine.value)

        val mois = assertNotNull(render(state, ProgressText.Period.MONTH).learned)
        assertEquals("Versets appris ce mois", mois.title)
        assertEquals("11", mois.value)
    }

    // -----------------------------------------------------------------------
    // Régularité
    // -----------------------------------------------------------------------

    @Test
    fun `la regularite suit la serie de jours consecutifs`() {
        val deux = Program.defaultState().copy(
            sessions = listOf(
                seance("a", today, 1, 3),
                seance("b", Dates.addDays(today, -1), 4, 6),
            ),
        )
        assertEquals("2 jours d’affilée", assertNotNull(render(deux, ProgressText.Period.WEEK).regularity).value)

        val un = Program.defaultState().copy(sessions = listOf(seance("a", today, 1, 3)))
        assertEquals("1 jour d’affilée", assertNotNull(render(un, ProgressText.Period.WEEK).regularity).value)
    }

    @Test
    fun `la regularite reste a zero sans jour actif`() {
        val state = render(Program.defaultState(), ProgressText.Period.WEEK)
        assertEquals("0 jour d’affilée", assertNotNull(state.regularity).value)
    }

    @Test
    fun `la carte de regularite porte le libelle du domaine`() {
        val state = render(Program.defaultState(), ProgressText.Period.WEEK)
        assertEquals(ProgressText.REGULARITY, assertNotNull(state.regularity).title)
    }

    // -----------------------------------------------------------------------
    // Pages mémorisées et révisions
    // -----------------------------------------------------------------------

    @Test
    fun `les pages memorisees comptent les pages entierement connues`() {
        // La page 1 du moushaf de Médine est le verset 1 à 7 : connus, elle est complète.
        assertEquals("0", assertNotNull(render(Program.defaultState(), ProgressText.Period.WEEK).memorizedPages).value)
        assertEquals("1", assertNotNull(render(etatPageUneConnue(), ProgressText.Period.WEEK).memorizedPages).value)
    }

    @Test
    fun `une page incomplete ne compte pas`() {
        // Six versets sur sept : la page n'est pas mémorisée, et un décompte qui la compterait
        // annoncerait une page que la personne ne connaît pas en entier.
        val presque = Program.defaultState().copy(
            knowledge = (1..6).associate { "$it" to Mastery.PERFECT },
        )
        assertEquals("0", assertNotNull(render(presque, ProgressText.Period.WEEK).memorizedPages).value)
    }

    @Test
    fun `les revisions faites comptent l'historique, pas les repetitions`() {
        // Deux règles différentes dans le dépôt d'origine : l'écran « Progrès » compte les entrées
        // de `reviewHistory`, le tableau de bord affiche la somme des `completedCount`.
        val state = Program.defaultState().copy(
            revisions = listOf(
                Revision(
                    id = "r1",
                    start = 1,
                    end = 3,
                    due = today,
                    interval = 7,
                    streak = 1,
                    completedCount = 5,
                ),
            ),
            reviewHistory = listOf(
                ReviewEvent(
                    id = "e1",
                    date = today,
                    start = 1,
                    end = 3,
                    category = ReviewCategory.HABITUAL,
                    grade = ReviewGrade.PERFECT,
                ),
                ReviewEvent(
                    id = "e2",
                    date = Dates.addDays(today, -1),
                    start = 4,
                    end = 6,
                    category = ReviewCategory.RECENT,
                    grade = ReviewGrade.HESITANT,
                ),
            ),
        )

        // Précondition : les deux nombres diffèrent, sinon le test ne distinguerait rien.
        assertEquals(5, Program.stats(state, today).revisions)

        assertEquals("2", assertNotNull(render(state, ProgressText.Period.WEEK).revisions).value)
    }

    // -----------------------------------------------------------------------
    // Graphique
    // -----------------------------------------------------------------------

    @Test
    fun `le graphique du jour couvre les sept derniers jours`() {
        val graph = assertNotNull(render(etatTroisPeriodes(), ProgressText.Period.DAY).graph)
        assertEquals("Les 7 derniers jours", graph.title)
        assertEquals(7, graph.bars.size)

        // Les bornes : du 4 mars au 10 mars, dans cet ordre.
        assertEquals(
            listOf("mer", "jeu", "ven", "sam", "dim", "lun", "mar"),
            graph.bars.map { it.label },
        )
        // Le 5 mars porte la séance de 5 versets, le 9 celle de 3, le 10 celle de 3.
        assertEquals(listOf(0, 5, 0, 0, 0, 3, 3), graph.bars.map { it.value })
    }

    @Test
    fun `le graphique de la semaine part du lundi`() {
        val graph = assertNotNull(render(etatTroisPeriodes(), ProgressText.Period.WEEK).graph)
        assertEquals("Cette semaine", graph.title)
        assertEquals(
            listOf("lun", "mar", "mer", "jeu", "ven", "sam", "dim"),
            graph.bars.map { it.label },
        )
        // La séance du 5 mars est **avant** le lundi 9 : elle ne doit pas apparaître ici.
        assertEquals(listOf(3, 3, 0, 0, 0, 0, 0), graph.bars.map { it.value })
    }

    @Test
    fun `le graphique du mois compte cinq semaines numerotees`() {
        val graph = assertNotNull(render(etatTroisPeriodes(), ProgressText.Period.MONTH).graph)
        assertEquals("Ce mois", graph.title)
        assertEquals(listOf("S1", "S2", "S3", "S4", "S5"), graph.bars.map { it.label })
        // S1 = 1–7 mars (5 versets), S2 = 8–14 mars (3 + 3 = 6), les trois autres sont vides.
        assertEquals(listOf(5, 6, 0, 0, 0), graph.bars.map { it.value })
    }

    @Test
    fun `le graphique est toujours calcule, quelle que soit la periode`() {
        // L'affichage du graphique est un pli d'interface, tenu par l'écran : le renderer ne le
        // connaît pas, et doit donc toujours produire les barres.
        for (periode in ProgressText.Period.entries) {
            assertNotNull(render(etatTroisPeriodes(), periode).graph, "période $periode")
        }
    }

    @Test
    fun `le graphique compte des versets, pas des seances`() {
        val state = Program.defaultState().copy(
            sessions = listOf(
                seance("a", today, 1, 3),
                seance("b", today, 4, 6),
            ),
        )
        val graph = assertNotNull(render(state, ProgressText.Period.DAY).graph)
        // Deux séances de trois versets : six, et non deux.
        assertEquals(6, graph.bars.last().value)
    }

    @Test
    fun `une seance suivie par sa progression n'est pas comptee deux fois`() {
        // La séance porte les versets 1 à 6, et sa progression fine en valide 3. Compter les deux
        // annoncerait neuf versets pour un travail de trois.
        val state = Program.defaultState().copy(
            sessions = listOf(seance("suivie", today, 1, 6)),
            studyProgress = mapOf("learning:suivie" to enregistrement("suivie", today)),
        )
        val graph = assertNotNull(render(state, ProgressText.Period.DAY).graph)
        assertEquals(3, graph.bars.last().value)
        assertEquals("3", assertNotNull(render(state, ProgressText.Period.DAY).learned).value)
    }

    @Test
    fun `la barre la plus haute remplit la hauteur et une barre vide garde un trait`() {
        val graph = assertNotNull(render(etatTroisPeriodes(), ProgressText.Period.MONTH).graph)
        val parValeur = graph.bars.associateBy { it.value }

        assertEquals(1f, assertNotNull(parValeur[6]).ratio, "la plus haute barre vaut 1")
        assertEquals(0f, assertNotNull(parValeur[0]).ratio, "une barre vide vaut 0")
        // Le rapport est celui des versets, et non un rang : 5 sur 6 vaut cinq sixièmes.
        assertEquals(5f / 6f, assertNotNull(parValeur[5]).ratio)
    }

    @Test
    fun `un graphique sans aucun verset ne divise pas par zero`() {
        val graph = assertNotNull(render(Program.defaultState(), ProgressText.Period.MONTH).graph)
        assertEquals(5, graph.bars.size)
        assertTrue(graph.bars.all { it.value == 0 && it.ratio == 0f })
    }

    @Test
    fun `une seance terminee sans horodatage est comptee par le graphique, pas par les statistiques`() {
        // Divergence assumée : `Program.stats` exige un `completedAt`, le graphique se rabat sur
        // la date prévue. Les deux règles viennent du dépôt d'origine, et les taire ferait passer
        // un « 0 verset » sur un travail réel pour une panne.
        val state = Program.defaultState().copy(
            sessions = listOf(seance("sans-horodatage", today, 1, 3, horodatage = false)),
        )

        assertEquals("0", assertNotNull(render(state, ProgressText.Period.DAY).learned).value)
        assertEquals(3, assertNotNull(render(state, ProgressText.Period.DAY).graph).bars.last().value)
    }

    // -----------------------------------------------------------------------
    // Objectif et compteurs
    // -----------------------------------------------------------------------

    @Test
    fun `la carte d'objectif porte le libelle, l'avancement et le pourcentage`() {
        val state = Program.defaultState().copy(
            knowledge = (5673..6236).associate { "$it" to Mastery.PERFECT },
        )
        val goal = assertNotNull(render(state, ProgressText.Period.WEEK).goal)
        assertEquals("Juz’ ‘Amma", goal.label)
        assertEquals(1f, goal.ratio)
        assertEquals("100 %", goal.percent)
    }

    @Test
    fun `un objectif vierge affiche zero pour cent`() {
        val goal = assertNotNull(render(Program.defaultState(), ProgressText.Period.WEEK).goal)
        assertEquals(0f, goal.ratio)
        assertEquals("0 %", goal.percent)
    }

    @Test
    fun `les quatre compteurs sont dans l'ordre du client d'origine`() {
        val state = render(etatPageUneConnue(), ProgressText.Period.WEEK)
        assertEquals(
            listOf(
                CounterKind.JUZ,
                CounterKind.ACTIVE_DAYS,
                CounterKind.PAGES_READ,
                CounterKind.VERSES,
            ),
            state.counters.map { it.kind },
        )
        // Sept versets connus : un seul Juz' est-il complet ? Non, et le test le mesure.
        assertEquals(0, state.counters.first { it.kind == CounterKind.JUZ }.value)
        assertEquals(7, state.counters.first { it.kind == CounterKind.VERSES }.value)
    }

    @Test
    fun `le compteur de jours actifs compte les jours, pas les seances`() {
        val state = Program.defaultState().copy(
            sessions = listOf(
                seance("a", today, 1, 3),
                seance("b", today, 4, 6),
                seance("c", Dates.addDays(today, -1), 7, 9),
            ),
        )
        val compteur = render(state, ProgressText.Period.WEEK).counters
            .first { it.kind == CounterKind.ACTIVE_DAYS }
        assertEquals(2, compteur.value, "trois séances, mais deux jours")
    }

    @Test
    fun `un juz entierement connu est compte`() {
        // Le premier Juz' va du verset 1 à 148 : le connaître en entier le fait compter.
        val state = Program.defaultState().copy(
            knowledge = (1..148).associate { "$it" to Mastery.PERFECT },
        )
        val compteur = render(state, ProgressText.Period.WEEK).counters
            .first { it.kind == CounterKind.JUZ }
        assertEquals(1, compteur.value)
    }

    @Test
    fun `les pages lues distinguent une liste absente d'une liste vide`() {
        val vierge = Program.defaultState()
        assertEquals(0, compteur(render(vierge, ProgressText.Period.WEEK), CounterKind.PAGES_READ))

        // Une dernière lecture sans liste : un état venu d'un ancien schéma. Le client d'origine
        // affiche alors une page, et le taire afficherait zéro sur une lecture réelle.
        val sansListe = vierge.copy(lastRead = LastRead(page = 12, verseId = 100, readAt = today))
        assertEquals(1, compteur(render(sansListe, ProgressText.Period.WEEK), CounterKind.PAGES_READ))

        // Une liste écrite mais vide est un autre état : elle vaut zéro.
        val listeVide = vierge.copy(readPages = emptyList(), lastRead = LastRead(12, 100, today))
        assertEquals(0, compteur(render(listeVide, ProgressText.Period.WEEK), CounterKind.PAGES_READ))

        val trois = vierge.copy(readPages = listOf(1, 2, 3))
        assertEquals(3, compteur(render(trois, ProgressText.Period.WEEK), CounterKind.PAGES_READ))
    }

    private fun compteur(state: ProgressUiState, kind: CounterKind): Int =
        state.counters.first { it.kind == kind }.value

    @Test
    fun `les quatre compteurs sont toujours presents et libelles`() {
        val state = render(Program.defaultState(), ProgressText.Period.WEEK)
        assertEquals(4, state.counters.size)
        assertTrue(
            state.counters.all { it.kind.label.isNotBlank() },
            "chaque compteur doit porter un libellé",
        )
    }
}
