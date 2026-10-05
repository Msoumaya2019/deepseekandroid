package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.Range
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Éprouve le découpage de la source `coranTest` sur l'index **livré**, pas sur une maquette.
 *
 * Les valeurs attendues ici ne sont pas des estimations : elles ont été mesurées sur
 * `verse-index.json` (604 pages, 6 236 versets). C'est la seule façon de voir un trou dans les
 * plages ou une page manquante — une maquette de trois versets laisserait passer les deux.
 *
 * ## Ce que ces contrôles séparent, et pourquoi
 *
 * Deux choses différentes s'éprouvent ici, et les confondre rendrait l'un des deux faux :
 *
 * - **les données** : 604 pages contiguës qui couvrent 1..6236, et le fait qu'aucun verset ne
 *   soit à cheval sur deux pages ;
 * - **la règle** : ce qu'on fait d'un verset qui, lui, serait à cheval. Aucune donnée livrée ne
 *   traverse cette branche, donc elle s'éprouve sur une liste écrite à la main.
 *
 * Un contrôle qui n'est jamais pris pour les vraies données ne prouve rien ; l'écrire sur une
 * entrée fabriquée le prouve, et le fait mesuré est écrit juste à côté pour qu'on ne croie pas
 * que la branche est couverte par accident.
 */
class TestPageIndexTest {

    @Before
    fun setUp() = QuranFixture.install()

    // --- ce que l'index livré contient ---

    @Test
    fun `l'index couvre les 604 pages de la composition`() {
        assertEquals(604, TestPageIndex.knownPageCount())
    }

    @Test
    fun `l'index couvre les 6236 versets du Mushaf`() {
        assertEquals(6236, TestPageIndex.knownVerseCount())
    }

    @Test
    fun `la premiere page porte Al-Fatiha entiere`() {
        assertEquals(Range(1, 7), TestPageIndex.pageRange(1))
    }

    @Test
    fun `la deuxieme page commence au huitieme verset`() {
        assertEquals(Range(8, 12), TestPageIndex.pageRange(2))
    }

    @Test
    fun `la derniere page finit au dernier verset`() {
        assertEquals(Range(6222, 6236), TestPageIndex.pageRange(604))
    }

    @Test
    fun `les 604 plages se suivent sans trou et couvrent tout le Mushaf`() {
        // Un trou ferait disparaître des versets de la navigation sans qu'aucun écran ne le
        // signale ; un recouvrement ferait lire deux fois le même verset.
        for (page in 1 until TestPageIndex.PAGES) {
            val courante = TestPageIndex.pageRange(page)
            val suivante = TestPageIndex.pageRange(page + 1)
            assertEquals(courante.end + 1, suivante.start, "entre les pages $page et ${page + 1}")
        }
        assertEquals(1, TestPageIndex.pageRange(1).start)
        assertEquals(6236, TestPageIndex.pageRange(604).end)
    }

    // --- le refus, et non une plage vide ---

    @Test
    fun `une page hors bornes est refusee au lieu de rendre une plage vide`() {
        for (page in listOf(0, -1, 605, 1000)) {
            assertFailsWith<IllegalArgumentException>("page $page") { TestPageIndex.pageRange(page) }
        }
    }

    @Test
    fun `validPage borne la composition`() {
        assertFalse(TestPageIndex.validPage(0))
        assertTrue(TestPageIndex.validPage(1))
        assertTrue(TestPageIndex.validPage(604))
        assertFalse(TestPageIndex.validPage(605))
    }

    @Test
    fun `les pages voisines sont bornees aux deux extremites`() {
        assertEquals(listOf(1, 2), TestPageIndex.adjacentPages(1))
        assertEquals(listOf(9, 10, 11), TestPageIndex.adjacentPages(10))
        assertEquals(listOf(603, 604), TestPageIndex.adjacentPages(604))
    }

    // --- la page d'un verset ---

    @Test
    fun `le premier verset est sur la premiere page`() {
        assertEquals(1, TestPageIndex.versePage(1))
    }

    @Test
    fun `le verset 2-66 est sur la page 10`() {
        assertEquals(10, TestPageIndex.versePage(73))
    }

    @Test
    fun `le dernier verset est sur la derniere page`() {
        assertEquals(604, TestPageIndex.versePage(6236))
    }

    @Test
    fun `les pages d'un verset se lisent`() {
        assertEquals(listOf(1), TestPageIndex.pagesOf(1))
        assertEquals(listOf(10), TestPageIndex.pagesOf(73))
    }

    @Test
    fun `un verset hors du Mushaf est refuse`() {
        for (id in listOf(0, -1, 6237)) {
            assertFailsWith<IllegalArgumentException>("verset $id") { TestPageIndex.versePage(id) }
        }
    }

    // --- la règle seule, sur une entrée fabriquée ---

    @Test
    fun `la page courante est conservee quand elle porte le verset`() {
        assertEquals(11, TestPageIndex.pageFor(listOf(10, 11), 11))
        assertEquals(10, TestPageIndex.pageFor(listOf(10, 11), 12))
        assertEquals(10, TestPageIndex.pageFor(listOf(10, 11), null))
        assertEquals(10, TestPageIndex.pageFor(listOf(10), 10))
    }

    @Test
    fun `une liste de pages vide est refusee`() {
        assertFailsWith<IllegalArgumentException> { TestPageIndex.pageFor(emptyList(), 3) }
    }

    @Test
    fun `aucun verset du Mushaf ne vit sur deux pages`() {
        // Le fait mesuré, écrit à côté de la règle : sur ces données, la conservation de la
        // page courante n'est jamais déclenchée. Le dire évite de croire qu'un contrôle la
        // traverse alors qu'il ne la touche pas.
        val aCheval = (1..6236).count { TestPageIndex.pagesOf(it).size > 1 }
        assertEquals(0, aCheval)
    }

    // --- l'écart avec le moushaf de Médine, qui justifie que les deux tables existent ---

    @Test
    fun `le decoupage de coranTest differe de celui de Medine pour 56 versets`() {
        val differents = (1..6236).count { Quran.pageOf(it) != TestPageIndex.versePage(it) }
        assertEquals(56, differents)
    }

    @Test
    fun `les deux decoupages couvrent le meme corpus`() {
        assertEquals(604, TestPageIndex.knownPageCount())
        assertEquals(Quran.pageRange(1).start, TestPageIndex.pageRange(1).start)
        assertEquals(Quran.pageRange(604).end, TestPageIndex.pageRange(604).end)
    }
}
