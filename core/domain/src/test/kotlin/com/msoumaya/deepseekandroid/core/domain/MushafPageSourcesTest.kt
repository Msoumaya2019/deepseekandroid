package com.msoumaya.deepseekandroid.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Éprouve la description d'une page pour chacune des deux formes de source.
 *
 * Ce qui est vérifié ici est ce que le rendu ne peut pas vérifier lui-même : que les images
 * sont **celles de la source**, dans le bon ordre, et que les rectangles sont exprimés dans
 * l'espace de cette source. Un `x` pris pour un `y` donne des surlignages plausibles mais faux,
 * sur une page qui, elle, s'affiche correctement — donc personne ne s'en aperçoit avant de
 * chercher un verset précis.
 */
class MushafPageSourcesTest {

    // ------------------------------------------------------------- le moushaf embarqué

    @Test
    fun `la page embarquee tient en une seule image`() {
        val pages = embeddedMushafPages { "asset:page$it" }
        val page = pages.page(12)

        assertTrue(page.isSingleImage)
        assertEquals(listOf("asset:page12"), page.lines)
        assertEquals(ReaderLayout.PAGE_WIDTH, page.sourceWidth)
        assertEquals(ReaderLayout.PAGE_HEIGHT, page.sourceHeight)
    }

    @Test
    fun `la page embarquee porte les rectangles du moushaf de Medine`() {
        // Les rectangles ne sont pas recopiés : ils viennent du référentiel. Deux copies
        // divergeraient, et le surlignage désignerait un verset voisin.
        val page = embeddedMushafPages { "asset:page$it" }.page(12)
        assertEquals(ReaderData.pageRows(12), page.rows)
        assertTrue(page.rows.isNotEmpty(), "la page 12 doit porter des versets")
    }

    @Test
    fun `une page embarquee hors bornes ne leve pas`() {
        val pages = embeddedMushafPages { "asset:page$it" }
        for (page in listOf(0, 605)) {
            assertTrue(pages.page(page).rows.isEmpty(), "page $page")
        }
    }

    // ------------------------------------------------------------- le paquet 1441

    @Test
    fun `une page du paquet se decrit avec quinze bandes, dans l'ordre`() {
        val pages = archiveMushafPages { page, line -> "paquet:$page:$line" }
        val page = pages.page(12)

        assertFalse(page.isSingleImage)
        assertEquals(ZIP_LINES_PER_PAGE, page.lines.size)
        assertEquals("paquet:12:1", page.lines.first())
        assertEquals("paquet:12:15", page.lines.last())
    }

    @Test
    fun `les bandes sont demandees dans l'ordre des lignes du moushaf`() {
        // Les fichiers du paquet vont de 1 à 15, alors que les coordonnées du référentiel
        // numérotent les lignes de 0 à 14. Inverser l'un ou l'autre donnerait une page qui se
        // lit à l'envers — visible, mais mieux vaut le dire ici.
        val demandes = mutableListOf<Pair<Int, Int>>()
        archiveMushafPages { page, line -> demandes += page to line; "x" }.page(7)

        assertEquals((1..ZIP_LINES_PER_PAGE).map { 7 to it }, demandes)
    }

    @Test
    fun `une page du paquet a le rapport du paquet`() {
        val page = archiveMushafPages { _, _ -> "x" }.page(12)
        assertEquals(1440, page.sourceWidth)
        assertEquals(2320, page.sourceHeight)
    }

    @Test
    fun `une page du paquet porte ses propres rectangles`() {
        val page = archiveMushafPages { _, _ -> "x" }.page(12)
        assertEquals(ZipQuranSource.verseRows(12), page.rows)
        assertTrue(page.rows.isNotEmpty(), "la page 12 du paquet doit porter des versets")
        for (row in page.rows) {
            assertTrue(row.right <= page.sourceWidth, "rectangle hors de l'espace de la source")
            assertTrue(row.bottom <= page.sourceHeight, "rectangle hors de l'espace de la source")
        }
    }

    @Test
    fun `une page du paquet hors bornes ne leve pas`() {
        // Le refus de lecture appartient à `QuranSourceReady`. Un fournisseur d'images qui
        // lèverait ferait tomber le lecteur au lieu de laisser l'écran dire ce qui manque.
        val pages = archiveMushafPages { _, _ -> "x" }
        for (page in listOf(0, -1, 605, 9999)) {
            val decrite = pages.page(page)
            assertTrue(decrite.rows.isEmpty(), "page $page")
            assertEquals(
                MushafPageShape.EMBEDDED.width.toInt(),
                decrite.sourceWidth,
                "page $page : le repli doit rester une page, pas un carré d'un pixel",
            )
        }
    }

    @Test
    fun `les deux sources ne decrivent pas la meme page de la meme facon`() {
        // Si elles devenaient identiques, la couture n'aurait plus lieu d'être — et ce test
        // le dirait plutôt que de laisser deux chemins de code pour rien.
        val embarquee = embeddedMushafPages { "asset:page$it" }.page(1)
        val paquet = archiveMushafPages { page, line -> "paquet:$page:$line" }.page(1)

        assertEquals(1, embarquee.lines.size)
        assertEquals(ZIP_LINES_PER_PAGE, paquet.lines.size)
        assertFalse(embarquee.sourceWidth == paquet.sourceWidth && embarquee.sourceHeight == paquet.sourceHeight)
    }
}
