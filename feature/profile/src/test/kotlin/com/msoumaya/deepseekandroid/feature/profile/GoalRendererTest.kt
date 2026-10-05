package com.msoumaya.deepseekandroid.feature.profile

import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.GoalText
import com.msoumaya.deepseekandroid.core.domain.Program
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.QuranDataLoader
import com.msoumaya.deepseekandroid.core.model.Goal
import com.msoumaya.deepseekandroid.core.model.Mastery
import com.msoumaya.deepseekandroid.core.model.Pace
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.SessionStatus
import org.junit.BeforeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Règles de l'écran d'objectif
// ---------------------------------------------------------------------------
// Le calcul est une fonction pure de `(état, champs, jour)`. Il est donc éprouvé ici sans
// coroutine, sans Compose, sans horloge et sans appareil.
//
// Le jour est **fixé** (« 2026-03-10 », un mardi) et non lu depuis l'horloge : les jours
// d'apprentissage par défaut sont du lundi au vendredi, et un test qui prendrait le jour réel
// programmerait zéro séance le samedi — il passerait six jours sur sept.
//
// Quatre familles portent le plus, et ce sont elles qui justifient le fichier :
//
//   1. **Ce qui est annoncé est ce qui est écrit.** L'aperçu et l'enregistrement passent par le
//      même brouillon ; un contrôle relie la référence affichée à la première séance réellement
//      programmée. C'est le seul contrôle qui verrait une divergence entre les deux.
//   2. **Le pas de rythme est borné à son unité**, et **tout rythme tombe dans une unité** — sauf
//      ceux qu'aucune liste ne contient, et ce fichier fige lesquels.
//   3. **L'unité de l'objectif se relit dans le bon ordre.** Les trois listes de divisions se
//      recouvrent : essayer la sourate avant le juz’ ferait afficher « Sourate » sur un objectif
//      qui était un juz’.
//   4. **Les trois refus de date.** Le troisième — l'aller-retour — est le seul qui attrape un
//      jour inexistant qu'un analyseur permissif corrigerait en silence.
//
// Plusieurs tests posent une **précondition sur leur propre fixture** — « cette plage de sourate
// n'est ni un juz’ ni un hizb », « le douzième verset n'est pas le premier ». Sans elles, une
// fixture qui cesserait d'exercer la branche visée resterait verte pour la mauvaise raison.
// ---------------------------------------------------------------------------

class GoalRendererTest {

    /** Un mardi : les jours d'apprentissage par défaut le contiennent. */
    private val today = "2026-03-10"

    // --- Ce qui est annoncé est ce qui est écrit ----------------------------

    @Test
    fun `l'apercu annonce la seance que la sauvegarde programme`() {
        val state = Program.defaultState()
        val champs = GoalFields(
            goalUnit = GoalUnit.JUZ,
            goalIndex = 30,
            goalEdited = true,
            pace = Pace.PAGE,
            paceUnit = GoalPaceUnit.PER_PAGE,
        )

        val annonce = GoalRenderer.render(state, champs, today).preview
        val enregistre = GoalRenderer.save(state, champs, today)
        val premiere = enregistre.sessions
            .filter { it.status == SessionStatus.TODO }
            .minByOrNull { it.date }

        assertNotEquals(
            GoalText.GOAL_REACHED,
            annonce,
            "Cet objectif est neuf : l'aperçu doit annoncer une séance, pas l'objectif atteint.",
        )
        assertNotNull(premiere, "L'enregistrement doit programmer au moins une séance.")
        assertEquals(
            annonce,
            Quran.reference(Range(premiere.start, premiere.end)),
            "L'aperçu et l'enregistrement doivent désigner le même passage : sinon l'écran " +
                "annonce un passage et en écrit un autre.",
        )
    }

    @Test
    fun `l'apercu annonce l'objectif atteint quand tout est connu`() {
        val tout = Program.markKnowledge(
            Program.defaultState(),
            Range(1, Quran.verses.size),
            Mastery.PERFECT,
        )
        val derniere = Quran.surahs.last()
        val champs = GoalFields(
            goalUnit = GoalUnit.SURAH,
            goalIndex = derniere.number,
            goalEdited = true,
        )

        // Précondition : l'objectif couvre bien tout le Coran jusqu'à la fin de cette sourate —
        // c'est la règle de `goalFor`, qui part toujours du verset 1.
        assertEquals(listOf(Range(1, derniere.end)), GoalRenderer.goalFor(
            GoalChoice(derniere.number, GoalText.finishSurah(derniere.name), derniere.end),
            Program.defaultState().goal,
        ).ranges)

        assertEquals(GoalText.GOAL_REACHED, GoalRenderer.render(tout, champs, today).preview)
    }

    @Test
    fun `l'objectif part toujours du premier verset et garde son echeance`() {
        val precedent = Goal(
            label = "Juz’ ‘Amma",
            ranges = listOf(Range(5673, 6236)),
            deadline = "2026-05-01",
        )
        val objectif = GoalRenderer.goalFor(
            GoalChoice(number = 3, label = "Finir le Hizb 3", end = 300),
            precedent,
        )

        assertEquals(listOf(Range(1, 300)), objectif.ranges)
        assertEquals("Finir le Hizb 3", objectif.label)
        assertEquals(
            "2026-05-01",
            objectif.deadline,
            "L'objectif choisi ne doit pas effacer l'échéance : le brouillon la pose avant.",
        )
    }

    @Test
    fun `le libelle affiche est celui qui sera enregistre`() {
        val choices = GoalRenderer.goalChoices(GoalUnit.HIZB)
        val choix = choices[6]

        assertEquals("Finir le Hizb 7", choix.label)
        assertEquals(
            choix.label,
            GoalRenderer.goalFor(choix, Program.defaultState().goal).label,
            "Le libellé de l'option et celui de l'objectif écrit sont la même expression.",
        )
    }

    @Test
    fun `les options d'objectif nomment la division selon son unite`() {
        // Une sourate porte son **nom**, les deux autres divisions leur **numéro**. Confondre les
        // deux formes afficherait « Finir le 2 » là où l'on attend « Finir Al-Baqara ».
        val premiereSourate = GoalRenderer.goalChoices(GoalUnit.SURAH).first()
        assertEquals("Finir ${Quran.surahs.first().name}", premiereSourate.label)
        assertEquals(Quran.surahs.first().end, premiereSourate.end)

        assertEquals("Finir le Hizb 1", GoalRenderer.goalChoices(GoalUnit.HIZB).first().label)
        assertEquals("Finir le Juz’ 1", GoalRenderer.goalChoices(GoalUnit.JUZ).first().label)
    }

    @Test
    fun `les options de connaissance ne portent pas le mot Finir`() {
        // La carte des connaissances déclare ce qu'on **sait**, celle de l'objectif ce qu'on
        // **vise** : deux phrases. Les confondre afficherait « Finir le Hizb 12 » sous
        // « Dernier Hizb appris ».
        assertEquals("Hizb 1", GoalRenderer.knownDivisionChoices(GoalUnit.HIZB).first().label)
        assertEquals("Juz’ 1", GoalRenderer.knownDivisionChoices(GoalUnit.JUZ).first().label)
        assertEquals(Quran.surahs.first().name, GoalRenderer.knownSurahChoices().first().label)
    }

    @Test
    fun `le brouillon porte les quatre pieces`() {
        val state = Program.defaultState()
        val objectif = Goal(label = "Finir le Hizb 3", ranges = listOf(Range(1, 300)))
        val brouillon = GoalRenderer.draft(
            state = state,
            pace = Pace.PAGE,
            deadline = "2026-04-01",
            markKnown = Range(1, 10),
            goal = objectif,
        )

        assertEquals(Pace.PAGE, brouillon.pace)
        assertEquals("2026-04-01", brouillon.goal.deadline)
        assertEquals(objectif.label, brouillon.goal.label)
        assertEquals(objectif.ranges, brouillon.goal.ranges)
        assertTrue(Program.isKnown(brouillon, 10), "Le dixième verset doit être marqué connu.")
        assertFalse(Program.isKnown(brouillon, 11), "Le onzième ne doit pas l'être.")
    }

    @Test
    fun `un brouillon sans objectif ni connaissance ne touche a rien`() {
        val state = Program.defaultState()
        val brouillon = GoalRenderer.draft(state, state.pace, null, null, null)

        assertEquals(state.goal.label, brouillon.goal.label)
        assertEquals(state.goal.ranges, brouillon.goal.ranges)
        assertNull(brouillon.goal.deadline, "« Sans date » doit effacer l'échéance.")
        assertEquals(state.knowledge, brouillon.knowledge)
    }

    @Test
    fun `l'enregistrement ajoute les revisions initiales et le programme`() {
        val state = Program.markKnowledge(
            Program.defaultState(),
            Range(1, 5),
            Mastery.PERFECT,
        )
        val champs = GoalFields(goalUnit = GoalUnit.JUZ, goalIndex = 30, goalEdited = true)

        val enregistre = GoalRenderer.save(state, champs, today)
        val initiales = enregistre.revisions.filter { it.id.startsWith("r-initial-") }

        assertTrue(enregistre.sessions.isNotEmpty(), "L'objectif doit produire des séances.")
        assertEquals(1, initiales.size, "Les cinq versets connus sont contigus : une révision.")
        assertEquals(1, initiales.single().start)
        assertEquals(5, initiales.single().end)
        assertEquals(
            Dates.addDays(today, 1),
            initiales.single().due,
            "Une révision initiale est due le lendemain du jour d'enregistrement.",
        )
    }

    // --- Le rythme ----------------------------------------------------------

    @Test
    fun `le pas de rythme est borne a son unite`() {
        for (unite in GoalPaceUnit.entries) {
            val options = GoalRenderer.paceOptions(unite)
            assertTrue(options.size >= 2, "Une unité sans voisin ne peut pas être éprouvée.")
            assertEquals(
                options.first(),
                GoalRenderer.shiftPace(options.first(), unite, -1),
                "Reculer au premier cran ne doit pas sortir de l'unité ($unite).",
            )
            assertEquals(
                options.last(),
                GoalRenderer.shiftPace(options.last(), unite, 1),
                "Avancer au dernier cran ne doit pas sortir de l'unité ($unite).",
            )
            assertEquals(options[1], GoalRenderer.shiftPace(options[0], unite, 1))
        }
    }

    @Test
    fun `un rythme absent de la liste ne saute pas au dernier cran`() {
        // Le cas est atteignable : un rythme enregistré peut ne pas appartenir à l'unité
        // affichée. La source y voit un `indexOf` de `-1` et un premier pas qui retombe sur le
        // **premier** élément ; ici, le départ est ramené au premier cran, et le pas reste borné.
        val orphelin = Pace.TOUMOUN
        assertFalse(orphelin in GoalRenderer.paceOptions(GoalRenderer.paceUnitOf(orphelin)))

        assertEquals(
            GoalRenderer.paceOptions(GoalPaceUnit.PER_PAGE)[1],
            GoalRenderer.shiftPace(orphelin, GoalPaceUnit.PER_PAGE, 1),
        )
    }

    @Test
    fun `tout rythme tombe dans une unite, sauf ceux qu'aucune liste ne contient`() {
        val orphelins = Pace.entries.filter { pace ->
            GoalRenderer.paceOptions(GoalRenderer.paceUnitOf(pace)).none { it == pace }
        }

        // Mesuré à la source : `paceOptions` n'offre que `halfPage/page/page2`,
        // `verse1..verse5` et `quarter/halfHizb/hizb`. `toumoun` n'y figure nulle part — ni ici,
        // ni dans le client d'origine. Ce test fige cet ensemble : y ajouter un rythme serait un
        // écart à la source, et le faire disparaître signalerait une liste élargie sans le dire.
        assertEquals(listOf(Pace.TOUMOUN), orphelins)
    }

    @Test
    fun `l'unite de rythme se deduit de la liste qui contient le rythme`() {
        assertEquals(GoalPaceUnit.PER_VERSE, GoalRenderer.paceUnitOf(Pace.VERSE3))
        assertEquals(GoalPaceUnit.PER_PAGE, GoalRenderer.paceUnitOf(Pace.PAGE))
        assertEquals(GoalPaceUnit.PER_PAGE, GoalRenderer.paceUnitOf(Pace.HALF_PAGE))
        assertEquals(GoalPaceUnit.PER_RUBU, GoalRenderer.paceUnitOf(Pace.QUARTER))
    }

    @Test
    fun `les deux rythmes que la source oublie sont des rub‘`() {
        // L'écart assumé, mesuré et atteignable : la source écrit
        // `pace === 'quarter' ? 'Par rubu‘' : 'Par page'`, donc `halfHizb` et `hizb` — qui
        // appartiennent pourtant à la liste des rub‘ — s'affichent « Par page » là-bas, et un
        // appui sur « + » les remplace silencieusement par une demi-page.
        assertEquals(GoalPaceUnit.PER_RUBU, GoalRenderer.paceUnitOf(Pace.HALF_HIZB))
        assertEquals(GoalPaceUnit.PER_RUBU, GoalRenderer.paceUnitOf(Pace.HIZB))
        assertTrue(Pace.HALF_HIZB in GoalRenderer.paceOptions(GoalPaceUnit.PER_RUBU))
        assertTrue(Pace.HIZB in GoalRenderer.paceOptions(GoalPaceUnit.PER_RUBU))
    }

    @Test
    fun `l'unite de rythme ouvre sur le rythme qui la represente`() {
        assertEquals(Pace.PAGE, GoalRenderer.defaultPace(GoalPaceUnit.PER_PAGE))
        assertEquals(Pace.VERSE1, GoalRenderer.defaultPace(GoalPaceUnit.PER_VERSE))
        assertEquals(Pace.QUARTER, GoalRenderer.defaultPace(GoalPaceUnit.PER_RUBU))

        // Ce qui distingue « représenter l'unité » de « premier de la liste » : une demi-page et
        // un quart de rub‘ sont des réglages fins, et ouvrir dessus ferait croire à une réduction.
        assertNotEquals(
            GoalRenderer.paceOptions(GoalPaceUnit.PER_PAGE).first(),
            GoalRenderer.defaultPace(GoalPaceUnit.PER_PAGE),
        )
    }

    @Test
    fun `la ligne de rythme vient du referentiel`() {
        val ui = GoalRenderer.render(Program.defaultState(), GoalFields(pace = Pace.HIZB), today)

        assertEquals("1 hizb / jour", ui.paceLabel)
    }

    // --- L'objectif enregistré ----------------------------------------------

    @Test
    fun `l'unite de l'objectif se relit dans l'ordre juz puis hizb puis sourate`() {
        val juz = Quran.juzs[4]
        val hizb = Quran.hizbs[4]
        val sourate = Quran.surahs[50]

        // Préconditions sur la fixture : si deux de ces plages coïncidaient, le test ne dirait
        // rien sur l'ordre d'essai.
        assertNotEquals(juz.range, hizb.range)
        assertTrue(Quran.juzs.none { it.range == sourate.range })
        assertTrue(Quran.hizbs.none { it.range == sourate.range })

        assertEquals(GoalUnit.JUZ, GoalRenderer.unitOf(Goal(label = "x", ranges = listOf(juz.range))))
        assertEquals(GoalUnit.HIZB, GoalRenderer.unitOf(Goal(label = "x", ranges = listOf(hizb.range))))
        assertEquals(
            GoalUnit.SURAH,
            GoalRenderer.unitOf(Goal(label = "x", ranges = listOf(sourate.range))),
        )
    }

    @Test
    fun `un objectif qui ne correspond a rien retombe sur le hizb`() {
        assertEquals(
            GoalUnit.HIZB,
            GoalRenderer.unitOf(Goal(label = "x", ranges = listOf(Range(2, 3)))),
        )
        assertEquals(
            GoalUnit.HIZB,
            GoalRenderer.unitOf(Goal(label = "x", ranges = listOf(Range(1, 10), Range(20, 30)))),
            "Deux plages ne sont une division d'aucune sorte.",
        )
    }

    @Test
    fun `le numero de l'objectif se relit`() {
        assertEquals(5, GoalRenderer.goalIndex(Goal(label = "x", ranges = listOf(Quran.juzs[4].range))))
        assertEquals(7, GoalRenderer.goalIndex(Goal(label = "x", ranges = listOf(Quran.hizbs[6].range))))
    }

    @Test
    fun `le repli du numero d'objectif reste dans les bornes du referentiel`() {
        // La source écrit 60 en dur. Ici la borne est lue : le contrôle vérifie qu'elle tombe
        // bien dans le référentiel, quel qu'il soit.
        val sansPlage = GoalRenderer.goalIndex(Goal(label = "x", ranges = emptyList()))
        val debutInconnu = GoalRenderer.goalIndex(Goal(label = "x", ranges = listOf(Range(1, 2))))

        assertTrue(sansPlage in 1..Quran.hizbs.size, "Le repli doit rester un hizb réel.")
        assertEquals(1, debutInconnu, "Les premiers versets sont dans le premier hizb.")
    }

    // --- Les options --------------------------------------------------------

    @Test
    fun `les versets proposes sont ceux de la sourate choisie`() {
        assertEquals(Quran.surahs[0].count, GoalRenderer.verseChoices(1).size)
        assertEquals(Quran.surahs[1].count, GoalRenderer.verseChoices(2).size)
        assertTrue(GoalRenderer.verseChoices(0).isEmpty(), "Une sourate hors corpus n'a pas de versets.")
        assertTrue(GoalRenderer.verseChoices(Quran.surahs.size + 1).isEmpty())
    }

    @Test
    fun `une sourate ne se declare pas par une division`() {
        assertTrue(GoalRenderer.knownDivisionChoices(GoalUnit.SURAH).isEmpty())
        assertTrue(GoalRenderer.knownDivisionChoices(GoalUnit.HIZB).isNotEmpty())
        assertTrue(GoalRenderer.knownDivisionChoices(GoalUnit.JUZ).isNotEmpty())
    }

    @Test
    fun `le rendu ne propose de versets que pour une sourate`() {
        val state = Program.defaultState()

        val parSourate = GoalRenderer.render(state, GoalFields(knownUnit = GoalUnit.SURAH, surah = 2), today)
        assertEquals(Quran.surahs[1].count, parSourate.verseChoices.size)

        val parHizb = GoalRenderer.render(state, GoalFields(knownUnit = GoalUnit.HIZB), today)
        assertTrue(parHizb.verseChoices.isEmpty())
        assertTrue(parHizb.knownChoices.isNotEmpty())
    }

    @Test
    fun `la fin connue se compte depuis le debut de la sourate`() {
        val deuxieme = Quran.surahs[1]

        assertEquals(deuxieme.start + 4, GoalRenderer.knownEnd(GoalUnit.SURAH, 2, 5, 1))
        assertEquals(Quran.hizbs[2].end, GoalRenderer.knownEnd(GoalUnit.HIZB, 1, 1, 3))
        assertEquals(Quran.juzs[2].end, GoalRenderer.knownEnd(GoalUnit.JUZ, 1, 1, 3))
    }

    // --- L'amorçage ---------------------------------------------------------

    @Test
    fun `l'amorcage lit le dernier verset connu`() {
        val state = Program.markKnowledge(Program.defaultState(), Range(1, 12), Mastery.PERFECT)
        val champs = GoalRenderer.seed(state)

        assertEquals(12, Program.memorizedIds(state).maxOrNull(), "Précondition : le douzième est connu.")
        assertEquals(Quran.verses[11].surah, champs.surah)
        assertEquals(Quran.verses[11].ayah, champs.ayah)
        assertNotEquals(
            Quran.verses[0].surah to Quran.verses[0].ayah,
            champs.surah to champs.ayah,
            "Les champs ne doivent pas retomber sur le premier verset.",
        )
    }

    @Test
    fun `un etat sans connaissance ouvre sur le premier verset`() {
        val champs = GoalRenderer.seed(Program.defaultState())

        assertEquals(1, champs.surah)
        assertEquals(1, champs.ayah)
        assertTrue(champs.deadlineOn.not(), "Un état sans échéance ouvre sur « Sans date ».")
        assertEquals("", champs.deadline)
    }

    @Test
    fun `l'amorcage reprend l'echeance enregistree`() {
        val state = Program.defaultState().copy(
            goal = Goal(label = "x", ranges = listOf(Range(1, 10)), deadline = "2026-05-01"),
        )
        val champs = GoalRenderer.seed(state)

        assertTrue(champs.deadlineOn)
        assertEquals("2026-05-01", champs.deadline)
    }

    @Test
    fun `le rappel de l'objectif vient de l'etat enregistre`() {
        val state = Program.defaultState()

        assertEquals(state.goal.label, GoalRenderer.render(state, GoalFields(), today).savedGoalLabel)
    }

    @Test
    fun `la phrase d'absence se tait des qu'on declare`() {
        val vide = Program.defaultState()

        assertTrue(GoalRenderer.render(vide, GoalFields(), today).nothingKnown)
        assertFalse(GoalRenderer.render(vide, GoalFields(knownEdited = true), today).nothingKnown)

        val connu = Program.markKnowledge(vide, Range(1, 3), Mastery.PERFECT)
        assertFalse(
            GoalRenderer.render(connu, GoalFields(), today).nothingKnown,
            "La phrase décrit l'état enregistré : elle n'a rien à dire quand il y a des connus.",
        )
    }

    // --- La date ------------------------------------------------------------

    @Test
    fun `une date bien formee est acceptee`() {
        assertTrue(GoalRenderer.validDate("2026-02-03"))
        assertTrue(GoalRenderer.validDate("2026-12-31"))
    }

    @Test
    fun `une date de mauvaise forme est refusee`() {
        assertFalse(GoalRenderer.validDate("2026-2-3"), "Un analyseur permissif accepterait cette forme.")
        assertFalse(GoalRenderer.validDate("03/02/2026"))
        assertFalse(GoalRenderer.validDate("2026-02-03T12:00:00"))
        assertFalse(GoalRenderer.validDate(""))
    }

    @Test
    fun `un jour inexistant est refuse`() {
        assertFalse(GoalRenderer.validDate("2026-02-31"))
        assertFalse(GoalRenderer.validDate("2026-13-01"))
        assertFalse(GoalRenderer.validDate("2026-00-10"))
    }

    companion object {

        /**
         * Charge le référentiel coranique une fois pour toute la classe.
         *
         * Le renderer ne peut pas être appelé avant : `Quran` rend un référentiel vide, et
         * `reference` lève alors sur une plage. Le test le charge donc lui-même, exactement comme
         * l'application le fait au démarrage.
         */
        @BeforeClass
        @JvmStatic
        fun chargerLeReferentiel() {
            Quran.initialize(QuranDataLoader.loadFromClasspath())
        }
    }
}
