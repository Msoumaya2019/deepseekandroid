package com.msoumaya.deepseekandroid.core.design.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.msoumaya.deepseekandroid.core.model.AccentName
import com.msoumaya.deepseekandroid.core.model.AppTheme as ThemeName
import com.msoumaya.deepseekandroid.core.model.UiFont

// ---------------------------------------------------------------------------
// Thème
// ---------------------------------------------------------------------------
// `src/ui/theme.tsx` exposait un objet `colors` global, muté en place par `applyTheme()`, et un
// hook `useTheme()` qui le relisait. Compose attend l'inverse : une valeur immuable fournie par
// l'environnement, donc relue automatiquement quand elle change.
//
// L'équivalent est ici : `resolveColors()` calcule une instance, `DeepSeekTheme` la place dans
// l'environnement, et les écrans lisent `AppTheme.colors`. Rien n'est muté, donc un écran
// recomposé après un changement de thème voit forcément la nouvelle palette.
//
// La palette est aussi traduite en `ColorScheme` Material 3 afin que les composants Material
// (champ de saisie, feuille modale, barre de progression) prennent les couleurs de
// l'application sans habillage supplémentaire.
// ---------------------------------------------------------------------------

/** Toutes les valeurs de design résolues pour un thème donné. */
@Immutable
data class AppThemeValues(
    val theme: ThemeName,
    val accent: Accent,
    val colors: AppColors,
    val spacing: AppSpacing,
    val radius: AppRadius,
    val typeScale: AppTypeScale,
    val shadow: CardShadow,
    val fonts: AppFonts,
)

private val LocalAppThemeValues = compositionLocalOf<AppThemeValues> {
    error("Aucun thème : enveloppez l'écran dans DeepSeekTheme { ... }.")
}

/**
 * Accès aux valeurs du thème courant.
 *
 * Même rôle que `useTheme()` côté React Native, mais vérifié par le compilateur : lire
 * `AppTheme.colors` en dehors d'un `DeepSeekTheme` lève une erreur explicite au lieu de rendre
 * la palette blanche par silence.
 */
object AppTheme {

    val values: AppThemeValues
        @Composable @ReadOnlyComposable get() = LocalAppThemeValues.current

    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppThemeValues.current.colors

    val spacing: AppSpacing
        @Composable @ReadOnlyComposable get() = LocalAppThemeValues.current.spacing

    val radius: AppRadius
        @Composable @ReadOnlyComposable get() = LocalAppThemeValues.current.radius

    val typeScale: AppTypeScale
        @Composable @ReadOnlyComposable get() = LocalAppThemeValues.current.typeScale

    val shadow: CardShadow
        @Composable @ReadOnlyComposable get() = LocalAppThemeValues.current.shadow

    val fonts: AppFonts
        @Composable @ReadOnlyComposable get() = LocalAppThemeValues.current.fonts

    val accent: Accent
        @Composable @ReadOnlyComposable get() = LocalAppThemeValues.current.accent

    val themeName: ThemeName
        @Composable @ReadOnlyComposable get() = LocalAppThemeValues.current.theme
}

/**
 * Applique un thème à un sous-arbre.
 *
 * @param theme palette choisie (`white` par défaut, comme dans le dépôt d'origine).
 * @param accent accent explicite ; `null` laisse le thème décider (voir `resolveColors`).
 * @param uiFont famille de police de l'interface.
 */
@Composable
fun DeepSeekTheme(
    theme: ThemeName = ThemeName.WHITE,
    accent: AccentName? = null,
    uiFont: UiFont = UiFont.ELEGANT,
    content: @Composable () -> Unit,
) {
    val values = remember(theme, accent, uiFont) {
        AppThemeValues(
            theme = theme,
            accent = Accents.resolve(theme, accent),
            colors = resolveColors(theme, accent),
            spacing = AppSpacing.Default,
            radius = AppRadius.Default,
            typeScale = AppTypeScale.Default,
            shadow = CardShadow.Default,
            fonts = resolveFonts(uiFont),
        )
    }

    CompositionLocalProvider(LocalAppThemeValues provides values) {
        MaterialTheme(
            colorScheme = values.colors.toColorScheme(),
            typography = appTypography(values.typeScale, values.fonts),
            shapes = values.radius.toShapes(),
            content = content,
        )
    }
}

// ---------------------------------------------------------------------------
// Traduction vers Material 3
// ---------------------------------------------------------------------------

/**
 * Traduit la palette de l'application en `ColorScheme` Material 3.
 *
 * Tous les thèmes du dépôt d'origine sont clairs : `lightColorScheme` suffit, aucun mode sombre
 * n'est inventé. Les surfaces Material pointent vers `paper` / `soft` / `cream`, si bien qu'un
 * composant Material non habillé se fond dans l'écran au lieu de trancher.
 */
private fun AppColors.toColorScheme(): ColorScheme = lightColorScheme(
    primary = green,
    onPrimary = Color.White,
    primaryContainer = selected,
    onPrimaryContainer = green,
    inversePrimary = onDark,

    secondary = gold,
    onSecondary = Color.White,
    secondaryContainer = beige,
    onSecondaryContainer = text,

    tertiary = green2,
    onTertiary = Color.White,
    tertiaryContainer = soft,
    onTertiaryContainer = green2,

    background = cream,
    onBackground = text,

    surface = paper,
    onSurface = text,
    surfaceVariant = soft,
    onSurfaceVariant = muted,
    surfaceTint = green,

    surfaceContainerLowest = paper,
    surfaceContainerLow = cream,
    surfaceContainer = soft,
    surfaceContainerHigh = soft,
    surfaceContainerHighest = beige,

    inverseSurface = text,
    inverseOnSurface = cream,

    outline = softBorder,
    outlineVariant = line,

    error = red,
    onError = Color.White,
    errorContainer = soft,
    onErrorContainer = red,

    scrim = Color(0xCC000000),
)

private fun AppRadius.toShapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape(small),
    small = RoundedCornerShape(small),
    medium = RoundedCornerShape(card),
    large = RoundedCornerShape(card),
    extraLarge = RoundedCornerShape(sheet),
)

/**
 * Construit la typographie Material à partir de l'échelle et des polices.
 *
 * Le dépôt d'origine ne fixait aucune interligne : React Native laissait la police décider.
 * Compose, lui, attend une valeur sur chaque style Material, sinon les composants Material
 * retombent sur leur propre échelle. Deux ratios sont donc choisis côté Android et documentés
 * ici : **1,35** pour le texte courant, **1,2** pour les libellés courts. Les tailles, elles,
 * sont exactement celles de `tokens.ts`.
 */
private fun appTypography(scale: AppTypeScale, fonts: AppFonts): Typography {
    fun style(
        size: TextUnit,
        family: FontFamily?,
        weight: FontWeight = FontWeight.Normal,
        ratio: Float = 1.35f,
    ): TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size,
        lineHeight = (size.value * ratio).sp,
    )

    val text = fonts.interfaceFamily
    val title = fonts.titleFamily

    return Typography(
        displayLarge = style(scale.screen, title, FontWeight.SemiBold),
        displayMedium = style(scale.screen, title, FontWeight.SemiBold),
        displaySmall = style(scale.screen, title, FontWeight.SemiBold),

        headlineLarge = style(scale.header, title, FontWeight.SemiBold),
        headlineMedium = style(scale.header, title, FontWeight.SemiBold),
        headlineSmall = style(scale.header, title, FontWeight.SemiBold),

        titleLarge = style(scale.section, title, FontWeight.SemiBold),
        titleMedium = style(scale.card, text, FontWeight.Medium),
        titleSmall = style(scale.card, text),

        bodyLarge = style(scale.body, text),
        bodyMedium = style(scale.body, text),
        bodySmall = style(scale.secondary, text, ratio = 1.3f),

        labelLarge = style(scale.body, text, FontWeight.Medium, ratio = 1.2f),
        labelMedium = style(scale.secondary, text, ratio = 1.2f),
        labelSmall = style(scale.metadata, text, ratio = 1.2f),
    )
}
