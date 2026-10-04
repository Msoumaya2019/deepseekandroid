package com.msoumaya.deepseekandroid.feature.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Éprouve la pose des bandes d'une page du paquet « Coran 1441 ».
 *
 * Le défaut visé est invisible à l'œil sur une capture d'écran : **des bandes juxtaposées au
 * lieu d'être réparties** décaleraient la page d'une ligne et demie vers le bas, alors que la
 * première ligne, elle, serait juste. C'est le genre de défaut qu'on attribue au fichier
 * téléchargé plutôt qu'au calcul — d'où ces contrôles sur les bornes, et non sur des valeurs
 * choisies.
 */
class MushafPageGeometryTest {

    private val delta = 0.01f

    @Test
    fun `le rapport d'une bande est celui du paquet`() {
        assertEquals(232f / 1440f, LINE_ASPECT, delta)
        assertEquals(232f, MushafPageGeometry.bandHeight(1440f), delta)
        assertEquals(161.11f, MushafPageGeometry.bandHeight(1000f), delta)
    }

    @Test
    fun `la premiere bande touche le haut de la page`() {
        for (count in listOf(1, 2, 15)) {
            assertEquals(0f, MushafPageGeometry.bandTop(0, count, 1000f, 161.11f), delta, "$count bandes")
        }
    }

    @Test
    fun `la derniere bande touche le bas de la page`() {
        // C'est la propriété qui définit la disposition : les bandes se répartissent entre le
        // haut et le bas, quitte à se recouvrir. Une juxtaposition laisserait un blanc en bas
        // de page et décalerait tout ce qui suit.
        val hauteur = 1611.11f
        val bande = 161.11f
        for (count in listOf(2, 3, 15)) {
            val bas = MushafPageGeometry.bandTop(count - 1, count, hauteur, bande) + bande
            assertEquals(hauteur, bas, 0.05f, "$count bandes")
        }
    }

    @Test
    fun `les quinze bandes d'une page se recouvrent`() {
        // 15 × 232 = 3480 pixels de bandes dans une page qui en mesure 2320 : le recouvrement
        // n'est pas un accident, c'est la seule façon de tout faire tenir.
        val hauteur = 2320f
        val bande = MushafPageGeometry.bandHeight(1440f)
        val pas = MushafPageGeometry.bandTop(1, 15, hauteur, bande)

        assertTrue(pas < bande, "pas $pas contre bande $bande : les bandes seraient juxtaposées")
        assertTrue(pas > 0f, "pas $pas : les bandes seraient toutes au même endroit")
    }

    @Test
    fun `une page d'une seule bande n'a rien a repartir`() {
        assertEquals(0f, MushafPageGeometry.bandTop(0, 1, 2320f, 232f), delta)
    }

    @Test
    fun `une page plus courte que sa bande ne renverse pas l'ordre`() {
        // Cas limite : si la hauteur devenait plus petite que la bande — un écran très large et
        // très bas — le pas devient négatif et les bandes se poseraient à l'envers. Le calcul
        // doit rester monotone, sinon la page se lirait de bas en haut.
        val haut0 = MushafPageGeometry.bandTop(0, 15, 100f, 200f)
        val haut1 = MushafPageGeometry.bandTop(1, 15, 100f, 200f)
        val haut14 = MushafPageGeometry.bandTop(14, 15, 100f, 200f)

        assertTrue(haut1 < haut0, "la deuxième bande doit rester sous la première")
        assertTrue(haut14 < haut1, "l'ordre doit rester décroissant jusqu'au bout")
    }
}
