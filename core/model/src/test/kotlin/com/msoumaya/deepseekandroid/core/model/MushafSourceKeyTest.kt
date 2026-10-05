package com.msoumaya.deepseekandroid.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Les clés persistées des sources du moushaf.
 *
 * `MushafSource.persistedKey` est **dérivée** du `@SerialName` de chaque entrée, et non recopiée
 * dans une seconde table : une table recopiée finirait par diverger du format réellement écrit,
 * et la divergence serait muette — la carte `sourcePages` d'un signet rendrait `null`, et le
 * signet s'ouvrirait simplement à la mauvaise page. Ces tests fixent donc les huit valeurs **en
 * clair**, ce qui est le seul endroit où elles sont écrites deux fois : c'est voulu, pour qu'un
 * renommage de `@SerialName` casse ici au lieu de casser silencieusement chez l'utilisateur.
 *
 * ## Pourquoi ces chaînes ne sont pas un détail interne
 *
 * Ce sont elles que le client React Native écrit dans `VerseBookmark.sourcePages`, et que ce
 * client relit pour ouvrir un signet à la bonne page. Les changer rompt la compatibilité avec
 * l'état **déjà synchronisé** de l'utilisateur — un état qui n'appartient pas au dépôt, donc
 * qu'aucune migration ne peut rattraper. Ce test est un garde-fou de format.
 */
class MushafSourceKeyTest {

    @Test
    fun `les huit cles persistees sont celles du format deja ecrit`() {
        val attendu = mapOf(
            MushafSource.MEDINA to "traditional",
            MushafSource.SIMPLIFIED to "tajweed",
            MushafSource.TAJWEED_PAGES to "tajweedPages",
            MushafSource.CORAN_TEST to "coranTest",
            MushafSource.CORAN_1441 to "coran_1441",
            MushafSource.LEGACY_TAWJEED_TEST_2 to "tawjeed_test_2",
            MushafSource.LEGACY_TAJWEED_TEST_2 to "tajweed_test_2",
            MushafSource.LEGACY_MEDINE_TEST to "medine_test",
        )
        assertEquals(attendu, MushafSource.entries.associateWith { it.persistedKey })
    }

    @Test
    fun `deux sources ne partagent jamais la meme cle`() {
        // `sourcePages` est une carte indexée par cette clé. Deux sources qui la partageraient
        // écriraient leur page l'une sur l'autre, et un signet s'ouvrirait sur la page de
        // l'autre découpage — sans erreur, sans message.
        val cles = MushafSource.entries.map { it.persistedKey }
        assertEquals(cles.size, cles.toSet().size, "clés en double : $cles")
    }

    @Test
    fun `une valeur heritee garde une cle distincte de celle vers laquelle elle migre`() {
        // Une valeur héritée est convertie en CORAN_1441 par la migration d'état, mais un état
        // ancien peut encore la porter. Si les deux clés coïncidaient, une page notée sous
        // l'ancienne source serait lue comme si elle appartenait au découpage 1441.
        val heritees = MushafSource.entries.filter { it.isLegacy }
        assertEquals(3, heritees.size, "les trois valeurs héritées doivent rester déclarées")
        for (heritee in heritees) {
            assertNotEquals(
                MushafSource.CORAN_1441.persistedKey,
                heritee.persistedKey,
                "$heritee ne doit pas se confondre avec CORAN_1441",
            )
        }
    }
}
