package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.MushafSource
import com.msoumaya.deepseekandroid.core.model.Range
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Éprouve que chaque source répond avec **son** découpage, et non avec un découpage commun.
 *
 * Le défaut que ce fichier existe pour attraper ne se voit pas à la compilation : lire la
 * composition typographique avec la table du moushaf de Médine rend des pages parfaitement
 * valides, et une écoute qui commence au mauvais verset. C'est pourquoi les attentes ci-dessous
 * ne comparent pas à des nombres écrits à la main mais **aux deux tables elles-mêmes** — et
 * pourquoi la dernière mesure le nombre de versets qui changent de page, de façon que la
 * bascule ne puisse pas devenir un `if` décoratif.
 */
class MushafSourceNavigationTest {

    @Before
    fun setUp() = QuranFixture.install()

    @Test
    fun `coranTest se lit avec le decoupage de la composition, pour les 604 pages`() {
        for (page in 1..TestPageIndex.PAGES) {
            assertEquals(
                TestPageIndex.pageRange(page),
                MushafSourceNavigation.pageRange(MushafSource.CORAN_TEST, page),
                "page $page",
            )
        }
    }

    @Test
    fun `Medine garde le decoupage du moushaf`() {
        for (page in 1..Quran.pages.size) {
            assertEquals(
                Quran.pageRange(page),
                MushafSourceNavigation.pageRange(MushafSource.MEDINA, page),
                "page $page",
            )
        }
    }

    @Test
    fun `la page d'un verset suit le decoupage de sa source`() {
        for (id in 1..Quran.verses.size) {
            assertEquals(
                TestPageIndex.versePage(id),
                MushafSourceNavigation.versePage(MushafSource.CORAN_TEST, id),
                "verset $id",
            )
        }
    }

    @Test
    fun `la page courante est conservee quand elle porte deja le verset`() {
        // C'est la règle du suivi automatique : sans elle, la lecture sauterait en arrière à
        // chaque changement de verset.
        for (id in listOf(1, 8, 3000, 6236)) {
            val page = MushafSourceNavigation.versePage(MushafSource.CORAN_TEST, id)
            assertEquals(
                page,
                MushafSourceNavigation.versePage(MushafSource.CORAN_TEST, id, current = page),
                "verset $id",
            )
        }
    }

    @Test
    fun `une page hors bornes est refusee pour coranTest`() {
        assertFailsWith<IllegalArgumentException> {
            MushafSourceNavigation.pageRange(MushafSource.CORAN_TEST, TestPageIndex.PAGES + 1)
        }
        assertFailsWith<IllegalArgumentException> {
            MushafSourceNavigation.pageRange(MushafSource.CORAN_TEST, 0)
        }
    }

    @Test
    fun `les deux decoupages ne placent pas les memes versets aux memes pages`() {
        // Le fait qui justifie que cette bascule existe. Mesuré sur les données livrées :
        // 56 versets sur 6 236. Si ce nombre tombait à zéro, la branche `coranTest` deviendrait
        // inutile — et c'est précisément ce qu'il faut savoir plutôt que de le supposer.
        val differents = (1..Quran.verses.size).count {
            MushafSourceNavigation.versePage(MushafSource.MEDINA, it) !=
                MushafSourceNavigation.versePage(MushafSource.CORAN_TEST, it)
        }
        assertEquals(56, differents)
    }

    @Test
    fun `les deux sources s'accordent au debut et divergent a la page 120`() {
        // Mesuré, et non supposé : la page 1 porte les sept versets d'Al-Fatiha dans les deux
        // découpages — les confondre au début ne se verrait donc pas. La première divergence est
        // la page 120, où la composition ajoute le verset 746 là où le moushaf s'arrête à 745.
        assertEquals(
            MushafSourceNavigation.pageRange(MushafSource.MEDINA, 1),
            MushafSourceNavigation.pageRange(MushafSource.CORAN_TEST, 1),
        )
        assertEquals(
            Range(740, 745),
            MushafSourceNavigation.pageRange(MushafSource.MEDINA, 120),
        )
        assertEquals(
            Range(740, 746),
            MushafSourceNavigation.pageRange(MushafSource.CORAN_TEST, 120),
        )
    }

    @Test
    fun `trente-six pages sur six cent quatre sont decoupees autrement`() {
        // L'ampleur de l'écart : il ne se voit pas sur la première page, mais il porte sur plus
        // d'une trentaine d'endroits du Mushaf.
        val divergentes = (1..TestPageIndex.PAGES).count {
            MushafSourceNavigation.pageRange(MushafSource.MEDINA, it) !=
                MushafSourceNavigation.pageRange(MushafSource.CORAN_TEST, it)
        }
        assertEquals(36, divergentes)
    }

    @Test
    fun `seule la source composee a son propre ecran`() {
        assertTrue(MushafSourceNavigation.isImmersive(MushafSource.CORAN_TEST))
        for (source in MushafSource.entries.filter { it != MushafSource.CORAN_TEST }) {
            assertFalse(MushafSourceNavigation.isImmersive(source), source.name)
        }
    }

    @Test
    fun `la lecture simplifiee est rendue par police sans etre composee`() {
        // Les deux questions sont voisines et les confondre enverrait la lecture simplifiée sur
        // un écran qui n'a pas ses données : elle n'a ni index de pages de composition, ni
        // police par page. Le fait est mesuré, et non commenté.
        assertFalse(MushafSourceNavigation.isImmersive(MushafSource.SIMPLIFIED))
        assertEquals(
            PageReadiness.FontRendered,
            QuranSourceReady.readiness(MushafSource.SIMPLIFIED, page = 1),
        )
    }
}
