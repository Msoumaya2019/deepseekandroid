package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.MarginRegion
import com.msoumaya.deepseekandroid.core.model.ReaderZoom
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Géométrie du lecteur.
 *
 * Aucune marge fixe n'est appliquée : la page est ajustée aux dimensions **réellement
 * disponibles** après prise en compte des barres système, des découpes d'écran et du
 * mini-player. La page ne doit jamais être déformée.
 */
class ReaderTest {

    @Before
    fun setUp() = QuranFixture.install()

    @Test
    fun `la page occupe l'espace disponible sans se deformer`() {
        val available = ReaderLayout.fitMushafPage(1080.0, 2000.0)
        // Le rapport largeur/hauteur est conservé (le cadre de 4 px est retiré puis rajouté).
        val sourceRatio = ReaderLayout.PAGE_WIDTH.toDouble() / ReaderLayout.PAGE_HEIGHT
        val fittedRatio = (available.width - ReaderLayout.FRAME) / (available.height - ReaderLayout.FRAME)
        assertEquals(sourceRatio, fittedRatio, 1e-9)

        // La page tient dans l'espace disponible…
        assertTrue(available.width <= 1080.0 + 1e-9)
        assertTrue(available.height <= 2000.0 + 1e-9)
        // …et l'une des deux dimensions sature l'espace : pas de marge arbitraire.
        assertTrue(
            kotlin.math.abs(available.width - 1080.0) < 1e-6 ||
                kotlin.math.abs(available.height - 2000.0) < 1e-6,
        )
    }

    @Test
    fun `une fenetre haute est limitee par la largeur`() {
        val available = ReaderLayout.fitMushafPage(1080.0, 5000.0)
        assertEquals(1080.0, available.width, 1e-6)
        assertTrue(available.height < 5000.0)
    }

    @Test
    fun `une fenetre large est limitee par la hauteur`() {
        val available = ReaderLayout.fitMushafPage(4000.0, 1000.0)
        assertEquals(1000.0, available.height, 1e-6)
        assertTrue(available.width < 4000.0)
    }

    @Test
    fun `un espace trop petit rend une page nulle plutot qu'une page negative`() {
        assertEquals(ReaderLayout.Size(0.0, 0.0), ReaderLayout.fitMushafPage(0.0, 1000.0))
        assertEquals(ReaderLayout.Size(0.0, 0.0), ReaderLayout.fitMushafPage(1000.0, 2.0))
        assertEquals(ReaderLayout.Size(0.0, 0.0), ReaderLayout.fitMushafPage(-10.0, -10.0))
    }

    @Test
    fun `le zoom est borne entre un et trois`() {
        assertEquals(1f, ReaderZoomGeometry.constrain(0.2f, 0f, 0f, 1000f, 2000f).scale)
        assertEquals(3f, ReaderZoomGeometry.constrain(9f, 0f, 0f, 1000f, 2000f).scale)
        assertEquals(2f, ReaderZoomGeometry.constrain(2f, 0f, 0f, 1000f, 2000f).scale)
    }

    @Test
    fun `le zoom ne laisse jamais sortir la page de l'ecran`() {
        val zoomed = ReaderZoomGeometry.constrain(2f, 500f, 500f, 1000f, 2000f)
        assertEquals(0f, zoomed.x, "à l'échelle 2, le décalage horizontal est borné à 0")
        assertEquals(0f, zoomed.y)
        // Un décalage au-delà des bornes est ramené exactement à la borne.
        val panned = ReaderZoomGeometry.constrain(2f, -1200f, -3000f, 1000f, 2000f)
        assertEquals(-1000f, panned.x)
        assertEquals(-2000f, panned.y)
    }

    @Test
    fun `un glissement franc change de page`() {
        assertEquals(199, PageNavigation.pageAfterSwipe(200, -200f, 5f))
        assertEquals(201, PageNavigation.pageAfterSwipe(200, 200f, 5f))
    }

    @Test
    fun `un glissement trop court ou trop vertical ne change pas de page`() {
        assertEquals(200, PageNavigation.pageAfterSwipe(200, -30f, 0f), "sous le seuil de 60 px")
        assertEquals(200, PageNavigation.pageAfterSwipe(200, 100f, 100f), "trop vertical")
        assertEquals(200, PageNavigation.pageAfterSwipe(200, 100f, 70f), "rapport inférieur à 1,5")
    }

    @Test
    fun `le glissement ne sort jamais des bornes du moushaf`() {
        assertEquals(1, PageNavigation.pageAfterSwipe(1, -200f, 0f))
        assertEquals(2, PageNavigation.pageAfterSwipe(1, 200f, 0f))
        assertEquals(604, PageNavigation.pageAfterSwipe(604, 200f, 0f))
        assertEquals(603, PageNavigation.pageAfterSwipe(604, -200f, 0f))
    }

    @Test
    fun `la numerotation orientale couvre les identifiants du Coran`() {
        assertEquals("١", AyahMarker.easternArabicNumber(1))
        assertEquals("٩", AyahMarker.easternArabicNumber(9))
        assertEquals("١٠", AyahMarker.easternArabicNumber(10))
        assertEquals("١١٤", AyahMarker.easternArabicNumber(114))
        assertEquals("٦٢٣٦", AyahMarker.easternArabicNumber(6236))
    }

    @Test
    fun `la taille du marqueur diminue au-dela de deux chiffres`() {
        assertEquals(19, AyahMarker.fontSize(9))
        assertEquals(19, AyahMarker.fontSize(99))
        assertEquals(16, AyahMarker.fontSize(100))
        assertEquals(16, AyahMarker.fontSize(286))
    }

    @Test
    fun `les marqueurs de marge retiennent la premiere ancre de chaque verset`() {
        val regions = listOf(
            // Le verset 5 s'étale sur deux lignes : seule la première ancre compte.
            MarginRegion(id = 5, ayah = 5, x = 0f, y = 0.6f, width = 0.1f, height = 0.05f, line = 3),
            MarginRegion(id = 5, ayah = 5, x = 0f, y = 0.3f, width = 0.1f, height = 0.05f, line = 2),
            MarginRegion(id = 6, ayah = 6, x = 0f, y = 0.3f, width = 0.1f, height = 0.05f, line = 2),
            // Hors plage : ignoré.
            MarginRegion(id = 9, ayah = 9, x = 0f, y = 0.1f, width = 0.1f, height = 0.05f, line = 1),
        )
        val groups = MarginAnnotations.marginAnnotations(regions, start = 5, end = 6, through = 5)
        assertEquals(1, groups.size, "les versets 5 et 6 partagent la même ligne")
        val items = groups.first().items
        assertEquals(listOf(5, 6), items.map { it.id })
        assertTrue(items.first { it.id == 5 }.done, "le verset 5 est validé jusqu'à `through`")
        assertTrue(!items.first { it.id == 6 }.done)
    }

    @Test
    fun `les marqueurs de marge sont ordonnes de haut en bas`() {
        val regions = listOf(
            MarginRegion(id = 10, ayah = 10, x = 0f, y = 0.8f, width = 0.1f, height = 0.05f, line = 5),
            MarginRegion(id = 11, ayah = 11, x = 0f, y = 0.2f, width = 0.1f, height = 0.05f, line = 2),
        )
        val groups = MarginAnnotations.marginAnnotations(regions, start = 10, end = 11)
        assertEquals(listOf(11, 10), groups.flatMap { it.items }.map { it.id })
    }

    @Test
    fun `le zoom conserve le point vise sous le doigt`() {
        val current = ReaderZoom(scale = 1f, x = 0f, y = 0f)
        val zoomed = ReaderZoomGeometry.zoomAt(current, 2f, 300f, 400f, 1000f, 2000f)
        assertEquals(2f, zoomed.scale)
        // Le point visé reste sous le doigt : (ancre - décalage) / échelle est invariant.
        assertEquals(300.0, ((300f - zoomed.x) / zoomed.scale).toDouble(), 1e-3)
        assertEquals(400.0, ((400f - zoomed.y) / zoomed.scale).toDouble(), 1e-3)
    }
}
