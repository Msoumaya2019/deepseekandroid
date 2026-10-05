package com.msoumaya.deepseekandroid.feature.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Éprouve le nommage des polices de la composition.
 *
 * ## Ce qui est éprouvé ici, et ce qui ne peut pas l'être
 *
 * Les fichiers eux-mêmes ne sont pas lisibles depuis un test JVM : ils vivent dans les actifs de
 * l'**application**, et c'est `tools/import-coran-test-assets.mjs` qui vérifie leur nombre et
 * leur provenance à l'import — il refuse de terminer si un compte ne tombe pas juste.
 *
 * Ce qui reste à prouver ici est le **nommage**, et il n'est pas décoratif : un numéro complété
 * sur trois chiffres (`001.woff2`, comme les images du moushaf de Médine) ne trouverait aucun
 * fichier, et la page s'afficherait en carrés vides — sans erreur, puisque l'actif manquant est
 * signalé à la lecture et non à la compilation.
 */
class TestPageFontsTest {

    @Test
    fun `le fichier d'une page porte son numero sans complement`() {
        // La convention des polices n'est **pas** celle des images : `1.woff2`, pas `001.woff2`.
        assertEquals("1.woff2", TestPageFonts.pageFile(1))
        assertEquals("9.woff2", TestPageFonts.pageFile(9))
        assertEquals("604.woff2", TestPageFonts.pageFile(604))
    }

    @Test
    fun `aucune page ne partage le fichier d'une autre`() {
        val fichiers = (1..604).map { TestPageFonts.pageFile(it) }
        assertEquals(604, fichiers.toSet().size, "deux pages se partagent une police")
    }

    @Test
    fun `une page recoit trois polices distinctes`() {
        // La composition, le titre et la basmala. Un même fichier donné deux fois ferait
        // afficher la basmala dans la police de composition — un défaut visible, mais qui
        // accuserait la basmala.
        val fichiers = TestPageFonts.filesOf(7)
        assertEquals(3, fichiers.size)
        assertEquals(3, fichiers.toSet().size)
        assertEquals("7.woff2", fichiers[0])
    }

    @Test
    fun `le titre et la basmala sont communs a toutes les pages`() {
        // Seule la police de composition est par page : les deux autres sont partagées, et
        // l'ordre des trois est celui que le document attend.
        assertEquals(TestPageFonts.filesOf(1).drop(1), TestPageFonts.filesOf(604).drop(1))
    }

    @Test
    fun `la police de composition d'une page n'est celle d'aucune autre`() {
        assertNotEquals(TestPageFonts.pageFile(7), TestPageFonts.pageFile(8))
        assertNotEquals(TestPageFonts.pageFile(1), TestPageFonts.TITLE_FILE)
        assertNotEquals(TestPageFonts.pageFile(1), TestPageFonts.BASMALA_FILE)
        assertNotEquals(TestPageFonts.TITLE_FILE, TestPageFonts.BASMALA_FILE)
    }
}
