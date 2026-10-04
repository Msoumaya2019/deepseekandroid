package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.MushafSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Éprouve la forme d'une page selon la source.
 *
 * L'enjeu est mesurable : centrer la page du paquet avec le rapport de la page embarquée la
 * déformerait de 35 % en hauteur. Sur un moushaf, c'est illisible — et invisible dans un test
 * qui se contenterait de vérifier que « la page s'affiche ».
 */
class MushafPageShapeTest {

    private val toutes = MushafSource.entries.toList()

    @Test
    fun `la source 1441 se dessine en quinze bandes et les autres en une`() {
        for (source in toutes) {
            val attendu = if (source == MushafSource.CORAN_1441) ZIP_LINES_PER_PAGE else 1
            assertEquals(attendu, MushafPageShape.lines(source), "source $source")
        }
        // Sans ce témoin, une source ajoutée à l'énumération passerait inaperçue : la boucle
        // ci-dessus ne dit pas qu'elle les a toutes vues.
        assertEquals(8, toutes.size, "l'énumération des sources a changé : relire cette règle")
        assertEquals(15, MushafPageShape.lines(MushafSource.CORAN_1441))
    }

    @Test
    fun `la page du paquet a le rapport du paquet, sur les 604 pages`() {
        val formes = (1..QuranSourceTransition.TOTAL_PAGES)
            .map { MushafPageShape.of(MushafSource.CORAN_1441, it) }
            .distinct()

        assertEquals(
            listOf(ReaderLayout.Size(1440.0, 2320.0)),
            formes,
            "toutes les pages du paquet partagent un seul rapport, et ce rapport n'est pas celui de la page embarquée",
        )
    }

    @Test
    fun `la page du paquet n'a pas le rapport de la page embarquee`() {
        // C'est la raison d'être de cette règle. Si les deux rapports devenaient égaux, la
        // distinction n'aurait plus lieu d'être et ce test le dirait.
        val paquet = MushafPageShape.of(MushafSource.CORAN_1441, 12)
        assertNotEquals(MushafPageShape.EMBEDDED, paquet)
        assertEquals(1920.0, MushafPageShape.EMBEDDED.width)
        assertEquals(3106.0, MushafPageShape.EMBEDDED.height)
    }

    @Test
    fun `toutes les autres sources gardent le rapport de la page embarquee`() {
        for (source in toutes) {
            if (source == MushafSource.CORAN_1441) continue
            for (page in listOf(1, 12, 604)) {
                assertEquals(MushafPageShape.EMBEDDED, MushafPageShape.of(source, page), "$source page $page")
            }
        }
    }

    @Test
    fun `une page hors bornes ne prend pas le rapport du paquet`() {
        // Ce test tient la **propriété**, pas le garde-fou qui la produit. Mesuré : la garde de
        // bornes et le seuil `> 1.0` se recouvrent exactement sur le référentiel livré, donc
        // retirer l'un ou l'autre laisse ce test vert. C'est dit ici pour que personne ne croie
        // couvrir la garde en lisant ce test — et pour que le jour où le référentiel changera,
        // on sache que ce test est le premier à devoir être relu.
        for (page in listOf(0, -1, 605, 9999)) {
            val forme = MushafPageShape.of(MushafSource.CORAN_1441, page)
            assertEquals(MushafPageShape.EMBEDDED, forme, "page $page")
            assertTrue(forme.width > 1.0 && forme.height > 1.0, "page $page : un rapport d'un pixel n'est pas une page")
        }
    }

    @Test
    fun `les deux pages extremes du paquet ont bien un rapport connu`() {
        for (page in listOf(1, QuranSourceTransition.TOTAL_PAGES)) {
            val forme = MushafPageShape.of(MushafSource.CORAN_1441, page)
            assertEquals(1440.0, forme.width, "page $page")
            assertEquals(2320.0, forme.height, "page $page")
        }
    }

    @Test
    fun `le rapport du paquet vient des donnees et non d'une constante`() {
        // Le référentiel porte les dimensions page par page. La règle doit les lire : si elle
        // les remplaçait par des constantes, ce test resterait vert sur ce référentiel (une
        // seule dimension pour 604 pages) mais le garde-fou de bornes, lui, ne le resterait
        // pas — c'est lui qui distingue une lecture d'une écriture en dur.
        val data = ZipQuranSource.pageData(7)
        assertEquals(data.width, MushafPageShape.of(MushafSource.CORAN_1441, 7).width)
        assertEquals(data.height, MushafPageShape.of(MushafSource.CORAN_1441, 7).height)
    }
}
