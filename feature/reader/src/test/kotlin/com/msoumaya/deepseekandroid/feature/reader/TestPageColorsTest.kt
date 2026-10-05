package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.ui.graphics.Color
import com.msoumaya.deepseekandroid.core.domain.Texts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Éprouve le format de couleur échangé avec le document immersif.
 *
 * ## Pourquoi cette forme mérite un contrôle
 *
 * Le document valide le fond par `/^#[0-9a-f]{6}$/i` et **retombe sur son propre fond** quand la
 * forme ne correspond pas. Une couleur mal écrite n'échoue donc nulle part : la page perd sa
 * teinte, sans message, et l'écart se cherche à l'œil.
 *
 * Le motif attendu est **réécrit ici** plutôt qu'emprunté au code de production : un contrôle qui
 * interroge la constante qu'il vérifie ne vérifie rien.
 */
class TestPageColorsTest {

    /** Le motif du document, écrit indépendamment de `TestPageColors`. */
    private val formeDuDocument = Regex("^#[0-9a-fA-F]{6}$")

    @Test
    fun `une couleur est ecrite sur six chiffres`() {
        assertEquals("#153F36", TestPageColors.hex(Color(0xFF153F36)))
        assertEquals("#000000", TestPageColors.hex(Color(0xFF000000)))
        assertEquals("#FFFFFF", TestPageColors.hex(Color(0xFFFFFFFF)))
    }

    @Test
    fun `l'alpha est ecarte et non replie sur le blanc`() {
        // Deux couleurs qui ne diffèrent que par l'alpha doivent s'écrire pareil : le document
        // ne connaît que six chiffres, et un fond translucide laisserait voir la WebView.
        assertEquals(
            TestPageColors.hex(Color(0xFF153F36)),
            TestPageColors.hex(Color(0x80153F36)),
        )
    }

    @Test
    fun `les couleurs du theme sont ecrites dans la forme que le document valide`() {
        // Les quatre jetons que l'écran immersif envoie réellement.
        val jetons = listOf(0xFF153F36, 0xFFEAF2EC, 0xFFB39559, 0xFFFAF7F2)
        for (jeton in jetons) {
            val texte = TestPageColors.hex(Color(jeton))
            assertTrue(formeDuDocument.matches(texte), "$texte ne suit pas la forme attendue")
        }
    }

    @Test
    fun `l'aller et le retour rendent la meme couleur`() {
        for (jeton in listOf(0xFF153F36L, 0xFFEAF2ECL, 0xFFB39559L, 0xFFFAF7F2L, 0xFF000000L, 0xFFFFFFFFL)) {
            val texte = TestPageColors.hex(Color(jeton))
            assertEquals(Color(jeton), TestPageColors.parse(texte), texte)
        }
    }

    @Test
    fun `le catalogue de papier est ecrit dans la forme attendue, et se relit`() {
        // Le lien entre les deux moitiés : ce que `Texts` fournit au lecteur doit être
        // exactement ce que le document sait lire. Un catalogue qui dériverait ferait
        // disparaître le fond choisi sans rien dire.
        assertEquals(4, Texts.quranPaperOptions.size)
        for (option in Texts.quranPaperOptions) {
            assertTrue(
                formeDuDocument.matches(option.color),
                "${option.key} : ${option.color} ne suit pas la forme attendue",
            )
            assertEquals(
                option.color.uppercase(),
                TestPageColors.hex(TestPageColors.parse(option.color)),
                option.key.name,
            )
        }
    }

    @Test
    fun `une couleur illisible est refusee au lieu d'etre devinee`() {
        for (texte in listOf("#FFF", "153F36", "#153F3", "#153F366", "#GGGGGG", "")) {
            assertFailsWith<IllegalArgumentException>("« $texte » aurait dû être refusé") {
                TestPageColors.parse(texte)
            }
        }
    }
}
