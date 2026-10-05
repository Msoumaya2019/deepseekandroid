package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.TestLineType
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Éprouve le chargement des 604 pages de la composition « Coran avec règles de Tajwid ».
 *
 * ## Ce que ce fichier a attrapé, et qui n'était pas visible à la lecture
 *
 * La première version du modèle déclarait `fontSize` comme un **entier**. C'est ce que laisse
 * croire la page 1 — taille 70, ronde — et c'est faux : mesuré sur les 604 pages, **602**
 * portent une valeur fractionnaire (de 55,29 à 70), deux seulement tombent juste. Le chargeur
 * aurait donc refusé 602 pages sur 604 en annonçant « champ absent ». Un contrôle qui n'aurait
 * regardé que la première page aurait été vert.
 *
 * C'est pourquoi le recensement ci-dessous parcourt **les 604 pages** : il ne vérifie pas
 * qu'une page se charge, il vérifie que le corpus se charge.
 */
class TestPageLoaderTest {

    @Before
    fun setUp() = QuranFixture.install()

    // --- une page, dans le détail ---

    @Test
    fun `la premiere page est Al-Fatiha, avec son bandeau et sans basmala`() {
        val page = TestPageLoader.load(1)
        assertEquals(1, page.page)
        assertEquals(1, page.surah)
        assertEquals(1, page.juz)
        assertEquals(70.0, page.fontSize)
        assertEquals(8, page.lines.size)

        val premiere = page.lines.first()
        assertEquals(TestLineType.SURAH_NAME, premiere.type)
        assertEquals(1, premiere.surah)
        assertTrue(premiere.centered)
        assertTrue(premiere.words.isEmpty())

        // La basmala d'Al-Fatiha **est** son premier verset : elle n'est pas une ligne à part,
        // et la chercher comme telle ferait disparaître un verset numéroté.
        assertTrue(page.lines.none { it.type == TestLineType.BASMALLAH })
    }

    @Test
    fun `la deuxieme page porte une basmala, et le premier mot d'Al-Baqara`() {
        val page = TestPageLoader.load(2)
        assertEquals(2, page.surah)
        assertEquals(TestLineType.BASMALLAH, page.lines[1].type)
        assertTrue(page.lines[1].words.isEmpty())

        val premier = page.lines[2].words.first()
        assertEquals(37, premier.id)
        assertEquals(2, premier.surah)
        assertEquals(1, premier.ayah)
        assertEquals(1, premier.word)
        assertEquals("2:1", premier.verseKey)
    }

    @Test
    fun `une taille de police fractionnaire est lue telle quelle`() {
        // Le contrôle de non-régression : c'est cette page qui a montré que `fontSize` n'était
        // pas un entier.
        assertEquals(56.93794974923073, TestPageLoader.load(3).fontSize)
    }

    @Test
    fun `un mot porte ses glyphes et son texte lisible, qui different`() {
        val premier = TestPageLoader.load(1).lines[1].words.first()
        assertEquals(1, premier.id)
        assertEquals("1:1", premier.verseKey)
        assertTrue(premier.glyphs.isNotEmpty(), "les glyphes dessines")
        assertTrue(premier.arabic.isNotEmpty(), "le texte lisible")
        assertTrue(premier.glyphs != premier.arabic, "les deux ne sont pas interchangeables")
    }

    // --- le corpus entier ---

    @Test
    fun `les 604 pages se chargent, et leurs lignes se recensent`() {
        var lignes = 0
        var bandeaux = 0
        var basmalas = 0
        var versets = 0
        var motsMax = 0
        var tailleMax = 0.0
        for (page in 1..TestPageIndex.PAGES) {
            val chargee = TestPageLoader.load(page)
            assertEquals(page, chargee.page, "la page chargee porte son numero")
            assertTrue(chargee.fontSize > 0.0, "la page $page a une taille")
            tailleMax = maxOf(tailleMax, chargee.fontSize)
            for (ligne in chargee.lines) {
                lignes += 1
                when (ligne.type) {
                    TestLineType.SURAH_NAME -> bandeaux += 1
                    TestLineType.BASMALLAH -> basmalas += 1
                    TestLineType.AYAH -> versets += 1
                }
                motsMax = maxOf(motsMax, ligne.words.size)
            }
        }
        assertEquals(9046, lignes, "les lignes des 604 pages")
        assertEquals(114, bandeaux, "un bandeau par sourate")
        assertEquals(112, basmalas, "une basmala par sourate, sauf Al-Fatiha et At-Tawba")
        assertEquals(8820, versets)
        assertEquals(16, motsMax, "la ligne la plus chargee")
        assertEquals(70.0, tailleMax)
    }

    @Test
    fun `chaque bandeau de sourate porte sa sourate, et une seule fois par sourate`() {
        val sourates = mutableListOf<Int>()
        for (page in 1..TestPageIndex.PAGES) {
            for (ligne in TestPageLoader.load(page).lines) {
                if (ligne.type == TestLineType.SURAH_NAME) {
                    sourates += requireNotNull(ligne.surah) { "un bandeau sans sourate" }
                }
            }
        }
        assertEquals((1..114).toList(), sourates.sorted(), "les 114 sourates, une fois chacune")
    }

    // --- les refus ---

    @Test
    fun `une page hors bornes est refusee`() {
        for (page in listOf(0, -1, 605)) {
            assertFailsWith<IllegalArgumentException>("page $page") { TestPageLoader.load(page) }
        }
    }

    @Test
    fun `un type de ligne inconnu est refuse en le nommant`() {
        val document = """{"page":1,"surah":1,"juz":1,"fontSize":70,"lines":[{"line":1,"type":"ornement","centered":false,"surah":null,"words":[]}]}"""
        val erreur = assertFailsWith<IllegalStateException> { TestPageLoader.parse(document) }
        assertTrue(erreur.message.orEmpty().contains("ornement"), "le message nomme le type recu")
    }

    @Test
    fun `un document sans taille de police est refuse`() {
        val document = """{"page":1,"surah":1,"juz":1,"lines":[]}"""
        assertFailsWith<IllegalStateException> { TestPageLoader.parse(document) }
    }

    @Test
    fun `un document ecrit a la main se lit`() {
        val document = """{"page":9,"surah":2,"juz":1,"fontSize":61.5,"lines":[
            {"line":1,"type":"ayah","centered":false,"surah":null,"words":[[42,2,3,1,"\uFCA0","x"]]},
            {"line":2,"type":"surah_name","centered":true,"surah":2,"words":[]}
        ]}"""
        val page = TestPageLoader.parse(document)
        assertEquals(9, page.page)
        assertEquals(61.5, page.fontSize)
        assertEquals(2, page.lines.size)
        assertEquals(42, page.lines[0].words[0].id)
        assertNull(page.lines[0].surah, "une ligne de mots ne porte pas de sourate propre")
        assertEquals(2, page.lines[1].surah)
    }
}
