package com.msoumaya.deepseekandroid.feature.reader

import com.msoumaya.deepseekandroid.core.domain.PageNavigation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Le nommage des pages, et les bornes du geste.
 *
 * Une page dont le nom est faux ne lève **aucune erreur** : Coil ne trouve pas le fichier,
 * n'affiche rien, et l'utilisateur voit une page blanche. C'est la panne la plus coûteuse du
 * lecteur parce qu'elle est la plus silencieuse — d'où ces quelques contrôles.
 */
class MushafAssetsTest {

    @Test
    fun `le nom de fichier est sur trois chiffres`() {
        // « page1.png » n'existe pas : le fichier s'appelle « page001.png ».
        assertEquals("page001.png", MushafAssets.fileName(1))
        assertEquals("page010.png", MushafAssets.fileName(10))
        assertEquals("page100.png", MushafAssets.fileName(100))
    }

    @Test
    fun `le nom de fichier ne tronque pas les pages a trois chiffres`() {
        assertEquals("page604.png", MushafAssets.fileName(604))
    }

    @Test
    fun `les 604 pages donnent 604 noms distincts`() {
        val noms = (1..604).map { MushafAssets.fileName(it) }
        assertEquals(604, noms.toSet().size, "deux pages partagent un nom de fichier")
    }

    @Test
    fun `tous les noms ont la meme longueur`() {
        // Une longueur variable signalerait un remplissage oublié quelque part.
        val longueurs = (1..604).map { MushafAssets.fileName(it).length }.toSet()
        assertEquals(setOf(11), longueurs, "« pageXXX.png » fait 11 caractères")
    }

    @Test
    fun `l'uri designe les ressources embarquees`() {
        // `android_asset` est ce qui rend le lecteur indépendant du réseau : c'est la
        // condition du fonctionnement hors ligne, et elle se lit dans le préfixe.
        assertTrue(MushafAssets.uri(1).startsWith("file:///android_asset/${MushafAssets.DIRECTORY}/"))
        assertTrue(MushafAssets.uri(1).endsWith(MushafAssets.fileName(1)))
    }

    @Test
    fun `la duree d'appui long est celle du client d'origine`() {
        // 450 ms : en dessous, on sélectionne un verset en voulant tourner une page.
        assertEquals(450L, ReaderGestures.LONG_PRESS_MILLIS)
    }

    @Test
    fun `la tolerance d'appui est plus petite que le seuil de glissement`() {
        // Invariant : si la tolérance d'appui atteignait le seuil de glissement, un même
        // geste serait à la fois un appui et un changement de page — et le lecteur
        // tournerait une page chaque fois qu'on touche l'écran pour faire apparaître la
        // coquille.
        assertTrue(
            ReaderGestures.TAP_SLOP < PageNavigation.MIN_DX,
            "tolérance ${ReaderGestures.TAP_SLOP} contre seuil ${PageNavigation.MIN_DX}",
        )
    }
}
