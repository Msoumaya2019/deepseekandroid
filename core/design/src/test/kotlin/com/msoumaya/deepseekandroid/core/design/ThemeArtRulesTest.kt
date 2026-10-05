package com.msoumaya.deepseekandroid.core.design

import com.msoumaya.deepseekandroid.core.design.theme.ThemeArtHeroColoredOpacity
import com.msoumaya.deepseekandroid.core.design.theme.ThemeArtHeroCompactOpacity
import com.msoumaya.deepseekandroid.core.design.theme.ThemeArtHeroOpacity
import com.msoumaya.deepseekandroid.core.design.theme.themeArtHeroAlpha
import com.msoumaya.deepseekandroid.core.model.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Opacités du fond de thème, vérifiées sur la JVM.
 *
 * Le contrôle ne porte pas sur les valeurs — elles sont relevées à la source et figées par les
 * constantes — mais sur la **règle** qui les choisit. Elle est asymétrique :
 * `theme === 'white' ? 0.68 : 0.28`. Un appelant qui écrirait 0,68 en dur se tromperait donc sur
 * **quatre thèmes sur cinq**, et le défaut ne se verrait que sur eux : c'est exactement le genre
 * d'erreur qu'un test doit attraper, parce qu'aucune relecture d'écran ne la montre.
 */
class ThemeArtRulesTest {

    @Test
    fun `la bande compacte n'est opaque qu'en theme blanc`() {
        assertEquals(
            "le thème blanc est le seul à porter l'opacité forte",
            ThemeArtHeroCompactOpacity,
            themeArtHeroAlpha(AppTheme.WHITE),
            0.0001f,
        )

        listOf(AppTheme.CLASSIC, AppTheme.FEMININE, AppTheme.LILAC, AppTheme.NIGHT).forEach { theme ->
            assertEquals(
                "thème $theme : la bande compacte doit prendre l'opacité colorée",
                ThemeArtHeroColoredOpacity,
                themeArtHeroAlpha(theme),
                0.0001f,
            )
        }
    }

    @Test
    fun `chaque theme est couvert, sans exception ni doublon`() {
        // Le `when` de `themeArt` et le `if` de `themeArtHeroAlpha` énumèrent les mêmes thèmes.
        // Un sixième thème ajouté à l'énumération serait couvert par la seconde fonction — qui a
        // un `else` — mais pas par la première, qui ne compile plus. Ce test épingle le nombre
        // pour que l'ajout se voie aussi ici.
        assertEquals(5, AppTheme.entries.size)
        assertEquals(
            "un thème coloré ne doit pas recevoir l'opacité du blanc",
            AppTheme.entries.count { themeArtHeroAlpha(it) == ThemeArtHeroCompactOpacity },
            1,
        )
    }

    @Test
    fun `les trois opacites sont distinctes`() {
        // 0,55 pour l'accueil, 0,68 et 0,28 pour les bandes compactes : trois valeurs qui
        // coexistent dans la source. Les confondre changerait le contraste du titre sur au moins
        // un thème, et rien ne le dirait.
        assertNotEquals(ThemeArtHeroOpacity, ThemeArtHeroCompactOpacity, 0.0001f)
        assertNotEquals(ThemeArtHeroOpacity, ThemeArtHeroColoredOpacity, 0.0001f)
        assertNotEquals(ThemeArtHeroCompactOpacity, ThemeArtHeroColoredOpacity, 0.0001f)
    }

    @Test
    fun `la bande d'accueil garde son opacite propre`() {
        assertEquals(0.55f, ThemeArtHeroOpacity, 0.0001f)
    }
}
