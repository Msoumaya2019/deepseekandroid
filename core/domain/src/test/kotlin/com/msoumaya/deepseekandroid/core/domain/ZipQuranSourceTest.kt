package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.MushafSource
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Éprouve le passage des coordonnées du paquet « Coran 1441 » au type que le lecteur sait
 * dessiner.
 *
 * C'est une conversion d'espace, et c'est là que se logent les fautes invisibles : un `x` lu
 * comme un `y` donne des surlignages plausibles mais faux, sur une page qui, elle, s'affiche
 * correctement. Les contrôles portent donc sur les **bornes** et sur la **cohérence avec le
 * reste du référentiel**, pas sur quelques valeurs choisies.
 *
 * ## Pourquoi cette classe installe le référentiel
 *
 * `ZipQuranSource.pageRange` passe par `Quran.verseId` pour transformer une coordonnée en
 * identifiant de verset, et `Quran` est un objet **global** dont le contenu vient de
 * `Quran.initialize`. Sans cet appel, `Quran` reste vide, tous les `verseId` valent `null`, et
 * `pageRange(300)` échoue en annonçant « page sans coordonnées de verset » — alors que la page
 * en a vingt et une, et que le défaut est ailleurs.
 *
 * Ce n'était pas une hypothèse : sans `install()`, cette classe passait ou échouait **selon
 * l'ordre d'exécution des classes de test**, parce qu'une autre classe avait installé le
 * référentiel avant elle. Un test qui dépend d'un voisin pour être vert ne prouve rien de
 * stable.
 */
class ZipQuranSourceTest {

    @Before
    fun setUp() = QuranFixture.install()

    private val pages = listOf(1, 2, 50, 300, 604)

    @Test
    fun `chaque ligne du referentiel devient un rectangle`() {
        for (page in pages) {
            assertEquals(
                ZipQuranSource.pageData(page).rows.size,
                ZipQuranSource.verseRows(page).size,
                "page $page",
            )
        }
    }

    @Test
    fun `les rectangles tiennent dans la page, sur les pages echantillonnees`() {
        for (page in pages) {
            val (width, height) = with(ZipQuranSource.pageData(page)) { width to height }
            for (row in ZipQuranSource.verseRows(page)) {
                assertTrue(row.left >= 0, "page $page : bord gauche négatif")
                assertTrue(row.right <= width, "page $page : bord droit hors page (${row.right} > $width)")
                assertTrue(row.top >= 0, "page $page : bord haut négatif")
                assertTrue(row.bottom <= height, "page $page : bord bas hors page (${row.bottom} > $height)")
                assertTrue(row.left <= row.right && row.top <= row.bottom, "page $page : rectangle retourné")
            }
        }
    }

    @Test
    fun `les versets d'une page sont ceux de la plage de la page`() {
        // `pageRange` lit le même référentiel par un autre chemin. Si la conversion inversait
        // deux colonnes, les identifiants trouvés ici ne seraient plus les mêmes.
        for (page in pages) {
            val parLesRectangles = ZipQuranSource.verseRows(page).mapNotNull { Quran.verseId(it.surah, it.ayah) }
            val parLaPlage = ZipQuranSource.pageData(page).rows.mapNotNull { it.verseId }
            assertEquals(parLaPlage, parLesRectangles, "page $page")
            assertEquals(ZipQuranSource.pageRange(page).start, parLesRectangles.min(), "page $page")
            assertEquals(ZipQuranSource.pageRange(page).end, parLesRectangles.max(), "page $page")
        }
    }

    @Test
    fun `les lignes portent un indice de zero a quatorze`() {
        // L'indice est celui de la bande dans l'image, et il désigne le fichier à lire
        // (`zipLineFileName(page, indice + 1)`). Un indice à quinze demanderait un seizième
        // fichier qui n'existe pas.
        for (page in pages) {
            for (row in ZipQuranSource.verseRows(page)) {
                assertTrue(row.line in 0 until ZIP_LINES_PER_PAGE, "page $page : ligne ${row.line} hors des quinze bandes")
            }
        }
    }

    @Test
    fun `le dernier indice de ligne n'est pas le nombre de lignes`() {
        // Trois nombres différents sur la même page, et les confondre se paie :
        //
        //   * 15 — le nombre de **bandes** de l'image, constant (`MushafPageShape.lines`) ;
        //   * 14 — l'index de la dernière bande d'une page complète, le référentiel étant
        //          0-based ;
        //   *  7 — le nombre de lignes portant réellement un verset sur la page 1.
        //
        // Mesuré sur le référentiel livré : deux pages seulement ont un maximum de 11 — la 1 et
        // la 2 — et 602 l'ont à 14. Dessiner quatorze bandes sur une page qui en compte quinze
        // ferait disparaître la dernière ligne du moushaf, sans que rien ne le signale.
        assertEquals(ZIP_LINES_PER_PAGE - 1, ZipQuranSource.lastLineIndex(50))

        val lignesDeLaPage1 = ZipQuranSource.verseRows(1).map { it.line }.distinct().sorted()
        assertEquals(7, lignesDeLaPage1.size, "lignes distinctes de la page 1")
        assertEquals(11, lignesDeLaPage1.max(), "dernier indice de la page 1")
        assertEquals(11, ZipQuranSource.lastLineIndex(1))
        // Le nombre de bandes, lui, ne dépend pas de la page.
        assertEquals(ZIP_LINES_PER_PAGE, MushafPageShape.lines(MushafSource.CORAN_1441))
    }

    @Test
    fun `une page sans rectangle n'a pas de dernier indice`() {
        // `null` et non `0` : zéro est un indice valide — c'est la première bande — et le
        // confondre avec « aucune ligne » ferait lire une bande là où il n'y a rien.
        for (page in listOf(0, -1, 605, 9999)) {
            assertNull(ZipQuranSource.lastLineIndex(page), "page $page")
        }
    }

    @Test
    fun `une page inconnue ne rend aucun rectangle`() {
        for (page in listOf(0, -1, 605, 9999)) {
            assertEquals(emptyList(), ZipQuranSource.verseRows(page), "page $page")
        }
    }
}
