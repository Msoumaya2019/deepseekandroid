package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.Range
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Éprouve le panneau de traduction : quels versets il énumère, et ce que chaque ligne porte.
 *
 * Trois règles y sont tenues, et ce sont celles qui se voient à l'usage :
 *
 *  1. **une plage hors du corpus ne fait pas tomber l'écran.** Le client d'origine lève sur un
 *     verset inexistant, et c'est le panneau entier qui disparaît ; ici la plage est ramenée aux
 *     versets qui existent ;
 *  2. **chaque ligne porte la traduction de son propre verset.** `ReaderData` range ses lignes par
 *     identifiant global, en supposant le même ordre que le corpus : un décalage d'un seul rang
 *     afficherait la traduction du voisin, et cela se lirait sans qu'on s'en aperçoive. C'est cet
 *     accord-là qui est mesuré, sur les 6 236 versets ;
 *  3. **le titre du panneau est celui de la ligne qui l'ouvre.** Deux endroits portent le même
 *     texte sans pouvoir se lire l'un l'autre.
 */
class TranslationPanelTest {

    @BeforeTest
    fun setUp() {
        // Installé explicitement, et non hérité d'une autre classe : une suite qui ne passe que
        // parce qu'un test précédent a chargé le référentiel mesure l'ordre d'exécution.
        QuranFixture.install()
    }

    // ------------------------------------------------------------------ la plage

    @Test
    fun `les versets d'une plage sont enumeres dans l'ordre`() {
        assertEquals(listOf(10, 11, 12, 13), TranslationPanel.verses(null, Range(10, 13)))
    }

    @Test
    fun `une plage de seance prime sur la page`() {
        // C'est la règle `focused ? reader.range : sourcePageRange` de l'original. La séance
        // n'existe pas encore ici, mais la règle est éprouvée : le jour où la phase C la
        // branchera, elle n'aura pas à être réinventée.
        assertEquals(
            listOf(100, 101, 102),
            TranslationPanel.verses(session = Range(100, 102), page = Range(10, 13)),
        )
    }

    @Test
    fun `une plage qui deborde du corpus est ramenee aux versets qui existent`() {
        val total = Quran.verses.size
        assertEquals(
            listOf(total - 2, total - 1, total),
            TranslationPanel.verses(null, Range(total - 2, total + 4)),
        )
    }

    @Test
    fun `une plage entierement hors du corpus ne montre rien`() {
        assertTrue(TranslationPanel.verses(null, Range(7000, 7010)).isEmpty())
        assertTrue(TranslationPanel.verses(null, Range(0, 0)).isEmpty())
    }

    @Test
    fun `une plage a l'envers ne montre rien`() {
        assertTrue(TranslationPanel.verses(null, Range(13, 10)).isEmpty())
    }

    // ------------------------------------------------------------------ les lignes

    @Test
    fun `les lignes suivent l'ordre des versets`() {
        val ids = TranslationPanel.rows(null, Range(3, 6)).map { it.verseId }
        assertEquals(listOf(3, 4, 5, 6), ids)
    }

    @Test
    fun `un verset est nomme comme dans le client d'origine`() {
        // La référence d'un seul verset est celle d'une plage : « Al Fâtiha 1–1 » et non
        // « Al Fâtiha 1 ». La répétition est celle de l'original — c'est un texte qu'on lit.
        assertEquals("Al Fâtiha 1–1", TranslationPanel.rows(null, Range(1, 1)).single().reference)
        // 255 est le 248e verset d'Al Baqarah : la référence suit le corpus, pas la page.
        assertEquals("Al Baqarah 248–248", TranslationPanel.rows(null, Range(255, 255)).single().reference)
        assertEquals("An Nâs 6–6", TranslationPanel.rows(null, Range(6236, 6236)).single().reference)
    }

    @Test
    fun `la ligne garde le renvoi de note, et pas la note`() {
        // Le texte de la traduction porte « [1] » à l'endroit de l'appel de note. Le panneau
        // l'affiche tel quel et ne déroule pas la note : une ligne n'a pas de champ pour elle.
        // Nettoyer ce renvoi changerait un texte que la personne lit.
        val line = TranslationPanel.rows(null, Range(1, 1)).single()

        assertTrue(line.translation.startsWith("Au nom d’Allah"), line.translation)
        assertTrue(line.translation.endsWith("[1]."), line.translation)
    }

    @Test
    fun `chaque verset du corpus a sa traduction, au bon endroit`() {
        val total = Quran.verses.size
        assertEquals(6236, total, "le corpus doit etre complet pour que cet accord ait un sens")

        for (id in 1..total) {
            val verse = Quran.verseAt(id)
            val french = ReaderData.frenchVerse(id)

            assertNotNull(french, "verset $id : aucune traduction")
            // L'accord entre les deux tables : `verses.json` et la traduction sont rangees dans le
            // meme ordre. C'est ce qui rend `frenchVerse(id)` juste, et donc le panneau aussi.
            assertEquals(verse.surah, french.surah, "verset $id : mauvaise sourate")
            assertEquals(verse.ayah, french.ayah, "verset $id : mauvais verset")
            assertTrue(french.translation.isNotBlank(), "verset $id : traduction vide")
        }
    }

    // ------------------------------------------------------------------ le titre

    @Test
    fun `le titre du panneau est celui de la ligne qui l'ouvre`() {
        val row = ReaderOptionsText.ALL.single { it.action == ReaderOptionsText.Action.TRANSLATION }
        assertEquals(row.title, TranslationPanel.TITLE)
    }
}
