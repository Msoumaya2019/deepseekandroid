package com.msoumaya.deepseekandroid.core.design

import androidx.compose.ui.graphics.Color
import com.msoumaya.deepseekandroid.core.design.theme.Accents
import com.msoumaya.deepseekandroid.core.design.theme.AppPalettes
import com.msoumaya.deepseekandroid.core.design.theme.AppRadius
import com.msoumaya.deepseekandroid.core.design.theme.AppSpacing
import com.msoumaya.deepseekandroid.core.design.theme.AppTypeScale
import com.msoumaya.deepseekandroid.core.design.theme.CardShadow
import com.msoumaya.deepseekandroid.core.design.theme.appThemeOptions
import com.msoumaya.deepseekandroid.core.design.theme.resolveColors
import com.msoumaya.deepseekandroid.core.design.theme.resolveFonts
import com.msoumaya.deepseekandroid.core.model.AccentName
import com.msoumaya.deepseekandroid.core.model.AppTheme
import com.msoumaya.deepseekandroid.core.model.UiFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Règles du thème, vérifiées sur la JVM.
 *
 * Ces tests ne relisent pas les valeurs de couleur une par une : c'est le rôle de
 * `tools/verifier-jetons-design.py`, qui les compare au dépôt React Native. Ce qui est testé
 * ici, ce sont les **règles** — celles qu'une modification distraite casse sans qu'aucun
 * contrôle de valeur ne s'en aperçoive :
 *
 *  - un accent explicite écrase la palette, un accent absent ne l'écrase que pour le blanc ;
 *  - `surfaceSecondary` suit une règle différente des autres fonds ;
 *  - les cinq palettes restent distinctes ;
 *  - les polices suivent le réglage utilisateur.
 */
class DesignRulesTest {

    private val allThemes = AppTheme.entries.toList()

    // --- Accents -------------------------------------------------------------

    @Test
    fun `un accent explicite ecrase la palette pour les cinq themes`() {
        allThemes.forEach { theme ->
            val colors = resolveColors(theme, AccentName.GOLD)
            assertEquals(
                "la couleur principale doit venir de l'accent, theme $theme",
                Accents.Gold.primary,
                colors.green,
            )
            assertEquals("green2 suit green", colors.green, colors.green2)
            assertEquals("les fonds teintes viennent de l'accent", Accents.Gold.soft, colors.selected)
            assertEquals(Accents.Gold.soft, colors.surahBadge)
            assertEquals(Accents.Gold.soft, colors.progress)
        }
    }

    @Test
    fun `sans accent explicite seul le theme blanc recoit l'accent par defaut`() {
        // Le theme blanc est le seul dont la palette porte deja la couleur prune : l'accent par
        // defaut s'y applique.
        assertEquals(Accents.Prune.primary, resolveColors(AppTheme.WHITE, null).green)

        // Les themes colores gardent leur propre palette : c'est la dissymetrie du depot
        // d'origine, et c'est elle qui empeche les cinq themes de se ressembler.
        listOf(AppTheme.CLASSIC, AppTheme.FEMININE, AppTheme.LILAC, AppTheme.NIGHT).forEach { theme ->
            val palette = AppPalettes.of(theme)
            val colors = resolveColors(theme, null)
            assertEquals("theme $theme : la palette doit rester intacte", palette.green, colors.green)
            assertEquals("theme $theme : fonds teintes intacts", palette.selected, colors.selected)
        }
    }

    @Test
    fun `l'accent par defaut depend du theme`() {
        assertEquals(Accents.Prune, Accents.resolve(AppTheme.WHITE, null))
        assertEquals(Accents.Green, Accents.resolve(AppTheme.CLASSIC, null))
        assertEquals(Accents.Rose, Accents.resolve(AppTheme.FEMININE, null))
        assertEquals(Accents.Prune, Accents.resolve(AppTheme.LILAC, null))
        assertEquals(Accents.Prune, Accents.resolve(AppTheme.NIGHT, null))
    }

    @Test
    fun `un accent explicite prime sur l'accent par defaut du theme`() {
        assertEquals(Accents.Gold, Accents.resolve(AppTheme.CLASSIC, AccentName.GOLD))
        assertEquals(Accents.Rose, Accents.resolve(AppTheme.WHITE, AccentName.ROSE))
    }

    @Test
    fun `les quatre accents sont proposes avec leur libelle accentue`() {
        assertEquals(4, Accents.all.size)
        assertEquals(listOf("Prune", "Rose", "Vert", "Doré"), Accents.all.map { it.label })
    }

    // --- Fond secondaire -----------------------------------------------------

    @Test
    fun `surfaceSecondary suit une regle propre`() {
        // Le blanc recoit une teinte fixe, les autres themes prennent leur `soft`.
        assertEquals(Color(0xFFF8F6F4), resolveColors(AppTheme.WHITE, null).surfaceSecondary)
        assertEquals(AppPalettes.Classic.soft, resolveColors(AppTheme.CLASSIC, null).surfaceSecondary)
        assertEquals(AppPalettes.Night.soft, resolveColors(AppTheme.NIGHT, null).surfaceSecondary)
    }

    @Test
    fun `les couleurs hors palette sont communes a tous les themes`() {
        val reference = AppPalettes.White
        allThemes.forEach { theme ->
            val c = AppPalettes.of(theme)
            assertEquals("theme $theme : quizLavender", reference.quizLavender, c.quizLavender)
            assertEquals("theme $theme : quizPurple", reference.quizPurple, c.quizPurple)
            assertEquals("theme $theme : review", reference.review, c.review)
            assertEquals("theme $theme : reviewSoft", reference.reviewSoft, c.reviewSoft)
            assertEquals("theme $theme : mutedLight", reference.mutedLight, c.mutedLight)
        }
    }

    // --- Les cinq palettes restent distinctes --------------------------------

    @Test
    fun `les cinq palettes ont des couleurs principales differentes`() {
        val principales = allThemes.map { AppPalettes.of(it).green }
        assertEquals(5, principales.size)
        assertEquals("deux themes partagent la meme couleur principale", 5, principales.toSet().size)
    }

    @Test
    fun `les cinq palettes ont des fonds differents`() {
        val fonds = allThemes.map { AppPalettes.of(it).paper }
        assertEquals("deux themes partagent le meme fond", 5, fonds.toSet().size)
    }

    @Test
    fun `chaque palette definit les dix-huit cles avec une couleur pleinement opaque`() {
        allThemes.forEach { theme ->
            val c = AppPalettes.of(theme)
            listOf(
                c.green, c.green2, c.cream, c.paper, c.beige, c.gold, c.text, c.muted, c.line,
                c.red, c.soft, c.softBorder, c.selected, c.onDark, c.onDarkSoft, c.track,
                c.progress, c.surahBadge,
            ).forEachIndexed { index, couleur ->
                assertEquals(
                    "theme $theme, cle n°$index : la couleur doit etre opaque",
                    1f,
                    couleur.alpha,
                    0.0001f,
                )
            }
        }
    }

    // --- Jetons numeriques ---------------------------------------------------

    @Test
    fun `l'echelle typographique reprend les valeurs de tokens ts`() {
        val s = AppTypeScale.Default
        assertEquals(24f, s.header.value, 0.001f)
        assertEquals(30f, s.screen.value, 0.001f)
        assertEquals(21f, s.section.value, 0.001f)
        assertEquals(17f, s.card.value, 0.001f)
        assertEquals(14f, s.body.value, 0.001f)
        assertEquals(12f, s.secondary.value, 0.001f)
        assertEquals(11f, s.metadata.value, 0.001f)
        assertEquals(22f, s.arabic.value, 0.001f)
    }

    @Test
    fun `les espacements reprennent les valeurs de tokens ts`() {
        val s = AppSpacing.Default
        assertEquals(4f, s.xs.value, 0.001f)
        assertEquals(8f, s.sm.value, 0.001f)
        assertEquals(12f, s.md.value, 0.001f)
        assertEquals(16f, s.lg.value, 0.001f)
        assertEquals(20f, s.xl.value, 0.001f)
        assertEquals(24f, s.xxl.value, 0.001f)
        assertEquals(32f, s.section.value, 0.001f)
    }

    @Test
    fun `les rayons reprennent les valeurs du depot d'origine`() {
        val r = AppRadius.Default
        assertEquals(14f, r.small.value, 0.001f)
        assertEquals(20f, r.card.value, 0.001f)
        assertEquals(28f, r.pill.value, 0.001f)
        assertEquals(30f, r.sheet.value, 0.001f)
        // Le rayon du bouton etait en dur dans le composant Button, pas dans tokens.ts.
        assertEquals(15f, r.button.value, 0.001f)
    }

    @Test
    fun `l'ombre de carte reprend les valeurs du depot d'origine`() {
        val s = CardShadow.Default
        assertEquals(Color(0xFF241C2B), s.color)
        assertEquals(0.045f, s.alpha, 0.0001f)
        assertEquals(10f, s.radius.value, 0.001f)
        assertEquals(3f, s.offsetY.value, 0.001f)
        assertEquals(1f, s.elevation.value, 0.001f)
    }

    // --- Polices -------------------------------------------------------------

    @Test
    fun `la police d'interface ne passe a Cormorant qu'en mode classique`() {
        assertNull(
            "en mode elegant l'interface garde la police du systeme",
            resolveFonts(UiFont.ELEGANT).interfaceFamily,
        )
        assertNull(resolveFonts(UiFont.SYSTEM).interfaceFamily)
        assertNotNull(
            "en mode classique l'interface passe a Cormorant",
            resolveFonts(UiFont.CLASSIC).interfaceFamily,
        )
    }

    @Test
    fun `les titres passent au systeme uniquement en mode system`() {
        assertNotNull(resolveFonts(UiFont.ELEGANT).titleFamily)
        assertNotNull(resolveFonts(UiFont.CLASSIC).titleFamily)
        assertNull(resolveFonts(UiFont.SYSTEM).titleFamily)
    }

    @Test
    fun `le texte coranique garde toujours Amiri`() {
        val familles = UiFont.entries.map { resolveFonts(it).arabicFamily }
        assertEquals(3, familles.size)
        assertEquals("Amiri doit etre la meme famille dans les trois modes", 1, familles.toSet().size)
    }

    // --- Ecran Apparence -----------------------------------------------------

    @Test
    fun `l'ecran Apparence propose les cinq themes du depot d'origine`() {
        assertEquals(5, appThemeOptions.size)
        assertEquals(
            listOf("Thème blanc", "Thème vert", "Thème rose", "Lilas & Perle", "Bleu Nuit & Or"),
            appThemeOptions.map { it.name },
        )
        assertEquals(
            listOf(
                AppTheme.WHITE, AppTheme.CLASSIC, AppTheme.FEMININE, AppTheme.LILAC, AppTheme.NIGHT,
            ),
            appThemeOptions.map { it.theme },
        )
    }

    @Test
    fun `chaque theme est presente avec quatre pastilles distinctes`() {
        appThemeOptions.forEach { option ->
            assertEquals("${option.name} : quatre pastilles attendues", 4, option.swatches.size)
            assertEquals(
                "${option.name} : deux pastilles identiques",
                4,
                option.swatches.toSet().size,
            )
            assertNotEquals("${option.name} : libelle vide", "", option.name)
            assertNotEquals("${option.name} : description vide", "", option.description)
            assertTrue(
                "${option.name} : la premiere pastille doit etre la couleur principale",
                option.swatches.first() == AppPalettes.of(option.theme).green,
            )
        }
    }
}
