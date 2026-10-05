package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.ReaderZoom
import com.msoumaya.deepseekandroid.core.model.VerseBoundsRow
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Ce que le doigt désigne sur la page.
 *
 * ## Pourquoi cette règle est éprouvée ici, et pas sur l'appareil
 *
 * La conversion d'une position d'écran en verset est de l'arithmétique pure : elle ne touche ni
 * Compose ni le disque. La jouer sur un appareil demanderait un hôte Compose pour ne rien
 * mesurer de plus.
 *
 * ## Ce que les cas séparent
 *
 * Chaque cas isole **une** des deux corrections, et il est construit pour **échouer** si elle
 * disparaissait — un cas qui resterait vert sans le dé-zoom ne prouverait rien. Les deux
 * frontières utiles sont donc choisies pour tomber juste de part et d'autre d'un changement de
 * verset : c'est là, et seulement là, qu'une correction oubliée se voit.
 *
 * La géométrie est la même pour tous les cas, et elle est volontairement lisible : la source
 * fait 1 000 × 1 000, la page est dessinée en 500 × 500, donc un point de la source vaut deux
 * fois son abscisse à l'écran. Le verset 1 occupe la moitié gauche haute, le verset 2 la moitié
 * droite haute, le verset 3 un petit coin en haut à gauche, et le bas de la page est **vide** —
 * ce qui donne un point qui ne désigne aucun verset sans sortir de la page.
 */
class ReaderTouchTest {

    @BeforeTest
    fun setUp() {
        QuranFixture.install()
    }

    /** La page source fait 1 000 × 1 000 ; elle est dessinée en 500 × 500. */
    private val page = 500.0

    private val rows = listOf(
        VerseBoundsRow(surah = 1, ayah = 1, line = 1, x1 = 0, x2 = 499, y1 = 0, y2 = 499),
        VerseBoundsRow(surah = 1, ayah = 2, line = 2, x1 = 500, x2 = 999, y1 = 0, y2 = 499),
        // Un petit rectangle **dans** le premier : il sert à montrer que le plus précis gagne.
        VerseBoundsRow(surah = 1, ayah = 3, line = 3, x1 = 0, x2 = 49, y1 = 0, y2 = 49),
    )

    private fun verset(
        x: Double,
        y: Double,
        zoom: ReaderZoom = ReaderZoom(),
        availableWidth: Double = page,
        availableHeight: Double = page,
        pageWidth: Double = page,
        pageHeight: Double = page,
    ): Int? = ReaderTouch.verseAt(
        x = x,
        y = y,
        zoom = zoom,
        availableWidth = availableWidth,
        availableHeight = availableHeight,
        pageWidth = pageWidth,
        pageHeight = pageHeight,
        rows = rows,
        sourceWidth = 1000,
        sourceHeight = 1000,
    )

    // -----------------------------------------------------------------------
    // Sans transformation : le point désigne le verset sous lui
    // -----------------------------------------------------------------------

    @Test
    fun `un point de la moitie gauche designe le premier verset`() {
        assertEquals(Quran.verseId(1, 1), verset(x = 100.0, y = 100.0))
    }

    @Test
    fun `un point de la moitie droite designe le second verset`() {
        // 400 à l'écran vaut 800 dans la source : c'est bien le second verset, et non le
        // premier — la conversion d'échelle est donc faite.
        assertEquals(Quran.verseId(1, 2), verset(x = 400.0, y = 100.0))
    }

    @Test
    fun `le plus petit rectangle contenant le point gagne`() {
        // 20 à l'écran vaut 40 dans la source : ce point est dans le premier verset **et** dans
        // le troisième, qui s'arrête à 49. C'est le plus précis qui doit répondre — et non le
        // premier trouvé dans la liste, qui serait le premier verset.
        assertEquals(Quran.verseId(1, 3), verset(x = 20.0, y = 20.0))
    }

    // -----------------------------------------------------------------------
    // Les deux corrections
    // -----------------------------------------------------------------------

    @Test
    fun `le zoom est defait avant de chercher le verset`() {
        // La page est agrandie deux fois : un point à 400 à l'écran désigne ce qui est à 200
        // sur la page dessinée, donc le **premier** verset. Sans le dé-zoom, il désignerait le
        // second — c'est le cas qui sépare les deux comportements.
        val zoom = ReaderZoom(scale = 2f, x = 0f, y = 0f)
        assertEquals(Quran.verseId(1, 1), verset(x = 400.0, y = 100.0, zoom = zoom))
    }

    @Test
    fun `un point qui n'existe qu'apres le de-zoom est trouve`() {
        // 800 à l'écran est hors de la page dessinée (500 de large). C'est le dé-zoom qui le
        // ramène à 400, donc dans la page : sans lui, ce point ne désignerait rien du tout.
        val zoom = ReaderZoom(scale = 2f, x = 0f, y = 0f)
        assertEquals(Quran.verseId(1, 2), verset(x = 800.0, y = 100.0, zoom = zoom))
    }

    @Test
    fun `la translation du zoom est retiree avant de chercher le verset`() {
        // La page a été tirée de 100 vers la gauche. Un point à 150 à l'écran désigne donc ce
        // qui est à 250 sur la page dessinée — la frontière exacte entre les deux versets, du
        // côté droit. Sans le retrait de la translation, il désignerait le premier.
        val zoom = ReaderZoom(scale = 1f, x = -100f, y = 0f)
        assertEquals(Quran.verseId(1, 2), verset(x = 150.0, y = 100.0, zoom = zoom))
    }

    @Test
    fun `le centrage est retire avant de chercher le verset`() {
        // La page est centrée dans un espace plus large qu'elle : l'écart est de 100 de chaque
        // côté. Un point à 300 à l'écran désigne donc ce qui est à 200 sur la page, soit le
        // premier verset. Sans le dé-centrage, il désignerait le second.
        assertEquals(
            Quran.verseId(1, 1),
            verset(x = 300.0, y = 100.0, availableWidth = 700.0),
        )
    }

    // -----------------------------------------------------------------------
    // Ce qui ne désigne rien
    // -----------------------------------------------------------------------

    @Test
    fun `un point hors de la page ne designe aucun verset`() {
        assertNull(verset(x = 600.0, y = 100.0))
    }

    @Test
    fun `un point de la zone vide ne designe aucun verset`() {
        // Le bas de la page ne porte aucun rectangle : le doigt y est, mais ne désigne rien.
        assertNull(verset(x = 100.0, y = 400.0))
    }

    @Test
    fun `une echelle nulle ne designe aucun verset`() {
        // Cas documenté dans le KDoc de `ReaderTouch` : il n'y a **pas** de garde sur l'échelle,
        // et c'est mesuré ici. Une échelle nulle donne l'infini, que les bornes de
        // `verseAtImagePoint` refusent. Ce cas existe pour que l'affirmation du KDoc soit
        // vérifiable, et non pour couvrir une branche qui n'existe pas.
        val zoom = ReaderZoom(scale = 0f, x = 0f, y = 0f)
        assertNull(verset(x = 100.0, y = 100.0, zoom = zoom))
    }
}
