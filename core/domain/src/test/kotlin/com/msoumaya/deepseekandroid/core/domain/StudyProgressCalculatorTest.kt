package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.MushafSource
import com.msoumaya.deepseekandroid.core.model.Range
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * Éprouve que les pages d'**étude** suivent, elles aussi, le découpage de la source affichée.
 *
 * ## Le défaut que ce fichier existe pour attraper
 *
 * Les trois règles ci-dessous étaient portées sans la branche `coranTest` : la source se lisait
 * donc avec le découpage du moushaf de Médine. C'était **juste** tant que `coranTest` n'avait pas
 * de table à elle — et c'est devenu faux le jour où l'écran immersif lui en a donné une. Le
 * défaut ne se voit pas : `Quran.pageOf` rend un numéro de page parfaitement valide, pour un
 * verset qui n'y est pas.
 *
 * ## Pourquoi les attentes portent sur les deux tables
 *
 * Les nombres écrits ici — 121, 120, 740, 745, 746, 56 — sont des **mesures** relevées sur les
 * données livrées, pas des constantes choisies. Les contrôles qui portent sur les 6 236 versets
 * comparent, eux, aux deux tables elles-mêmes : une bascule qui deviendrait décorative ferait
 * tomber le premier, et un déplacement des données ferait tomber les seconds.
 *
 * Les littéraux `"coranTest"` et `"traditional"` sont **le format écrit** dans l'état
 * synchronisé par le client React Native ; `MushafSourceKeyTest` fige l'égalité entre ces mots
 * et le `@SerialName` des sources. Les renommer ici ne masquerait donc rien.
 */
class StudyProgressCalculatorTest {

    @Before
    fun setUp() = QuranFixture.install()

    // -----------------------------------------------------------------------
    // La page d'un verset
    // -----------------------------------------------------------------------

    @Test
    fun `la page d'etude suit le decoupage de la source, pour les 6 236 versets`() {
        for (id in 1..Quran.verses.size) {
            assertEquals(
                TestPageIndex.versePage(id),
                StudyProgressCalculator.studyPage(id, "coranTest"),
                "verset $id dans la composition",
            )
            assertEquals(
                Quran.pageOf(id),
                StudyProgressCalculator.studyPage(id, "traditional"),
                "verset $id dans le moushaf de Médine",
            )
        }
    }

    @Test
    fun `le verset 5 sur 77 change de page entre les deux decoupages`() {
        // Le premier des 56 versets qui changent de page, et le seul que la page 120 de la
        // composition ajoute à celle du moushaf. Mesuré sur `verse-index.json` et `pages.json`.
        val id = 746
        assertEquals(121, StudyProgressCalculator.studyPage(id, "traditional"))
        assertEquals(120, StudyProgressCalculator.studyPage(id, "coranTest"))
    }

    @Test
    fun `l'ecart entre les deux decoupages est celui de la navigation`() {
        // Le même nombre que `MushafSourceNavigationTest` mesure par la règle de navigation.
        // Les deux chemins ne peuvent donc pas diverger sans qu'un des deux tombe — et si les
        // données cessaient de diverger, c'est la branche entière qu'il faudrait relire.
        val differents = (1..Quran.verses.size).count {
            StudyProgressCalculator.studyPage(it, "coranTest") !=
                StudyProgressCalculator.studyPage(it, "traditional")
        }
        assertEquals(56, differents)
    }

    // -----------------------------------------------------------------------
    // La plage d'une page
    // -----------------------------------------------------------------------

    @Test
    fun `la plage d'une page suit le decoupage de la source, pour les 604 pages`() {
        for (page in 1..TestPageIndex.PAGES) {
            assertEquals(
                TestPageIndex.pageRange(page),
                StudyProgressCalculator.studyPageRange(page, "coranTest"),
                "page $page de la composition",
            )
            assertEquals(
                Quran.pageRange(page),
                StudyProgressCalculator.studyPageRange(page, "traditional"),
                "page $page du moushaf",
            )
        }
    }

    @Test
    fun `la page 120 ne borne pas la meme plage dans les deux decoupages`() {
        assertEquals(Range(740, 745), StudyProgressCalculator.studyPageRange(120, "traditional"))
        assertEquals(Range(740, 746), StudyProgressCalculator.studyPageRange(120, "coranTest"))
    }

    @Test
    fun `une page de composition hors bornes est refusee`() {
        // Un refus, et non une plage vide : une plage `Range(0, 0)` ferait lire le verset 0, et
        // le défaut ne se verrait qu'à l'écoute.
        assertFailsWith<IllegalArgumentException> {
            StudyProgressCalculator.studyPageRange(TestPageIndex.PAGES + 1, "coranTest")
        }
        assertFailsWith<IllegalArgumentException> {
            StudyProgressCalculator.studyPageRange(0, "coranTest")
        }
    }

    // -----------------------------------------------------------------------
    // La derniere page d'un verset
    // -----------------------------------------------------------------------

    @Test
    fun `la derniere page d'un verset suit le decoupage de la source`() {
        for (id in 1..Quran.verses.size) {
            assertEquals(
                TestPageIndex.pagesOf(id).last(),
                StudyProgressCalculator.studyLastPage(id, "coranTest"),
                "verset $id dans la composition",
            )
        }
        assertEquals(120, StudyProgressCalculator.studyLastPage(746, "coranTest"))
        assertEquals(121, StudyProgressCalculator.studyLastPage(746, "traditional"))
    }

    // -----------------------------------------------------------------------
    // Le point d'arret d'une page
    // -----------------------------------------------------------------------

    @Test
    fun `le point d'arret d'une page suit le decoupage de la source`() {
        // Les trois règles ci-dessus prises ensemble : la borne haute de la page, la dernière
        // page du verset, et la plage de la séance.
        assertEquals(
            746,
            StudyProgressCalculator.studyEndpointForPage(
                page = 120,
                range = StudyProgressCalculator.studyPageRange(120, "coranTest"),
                source = "coranTest",
            ),
        )
        assertEquals(
            745,
            StudyProgressCalculator.studyEndpointForPage(
                page = 120,
                range = StudyProgressCalculator.studyPageRange(120, "traditional"),
                source = "traditional",
            ),
        )
    }

    // -----------------------------------------------------------------------
    // La cle d'etude
    // -----------------------------------------------------------------------

    @Test
    fun `la cle d'etude de coranTest n'est plus repliee sur le moushaf`() {
        // L'hypothèse qui a expiré : tant que la source n'avait pas de table à elle, la replier
        // sur `"traditional"` était juste. Le commentaire d'alors le disait — « le moushaf Tajwid
        // QPC n'est pas encore rendu par ce client ». Il l'est.
        assertEquals("coranTest", StudyProgressCalculator.sourceKey(MushafSource.CORAN_TEST))
        assertNotEquals(
            "traditional",
            StudyProgressCalculator.sourceKey(MushafSource.CORAN_TEST),
            "replier la source ferait annoncer la page de Médine pour un verset qui n'y est pas",
        )
    }

    @Test
    fun `seules les sources non rendues et les sources heritees sont repliees`() {
        // La liste est écrite ici pour être **exhaustive** : le jour où une source cesse d'être
        // rendue, ou cesse d'être héritée, ce contrôle tombe et force à relire la règle au lieu
        // de la laisser dériver.
        val repliees = setOf(
            MushafSource.TAJWEED_PAGES,
            MushafSource.LEGACY_TAWJEED_TEST_2,
            MushafSource.LEGACY_TAJWEED_TEST_2,
            MushafSource.LEGACY_MEDINE_TEST,
        )
        for (source in MushafSource.entries) {
            val attendu = if (source in repliees) "traditional" else source.persistedKey
            assertEquals(attendu, StudyProgressCalculator.sourceKey(source), source.name)
        }
    }
}
