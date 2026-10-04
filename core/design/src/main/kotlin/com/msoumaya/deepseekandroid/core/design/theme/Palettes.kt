package com.msoumaya.deepseekandroid.core.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.msoumaya.deepseekandroid.core.model.AccentName
import com.msoumaya.deepseekandroid.core.model.AppTheme

// ---------------------------------------------------------------------------
// Palettes
// ---------------------------------------------------------------------------
// Portage fidèle de `src/ui/theme.tsx` de l'application React Native, valeurs hexadécimales
// comprises. Cinq thèmes, chacun définissant les mêmes clés.
//
// Côté React Native ces couleurs vivaient dans un objet unique muté en place par
// `applyTheme()`. Ici la palette est une valeur immuable : `resolveColors()` en calcule une
// instance, ce qui rend le résultat testable et évite qu'un écran composé avec l'ancienne
// palette se retrouve repeint après coup.
// ---------------------------------------------------------------------------

/**
 * Jeu complet de couleurs d'un écran.
 *
 * Les dix-huit premières clés viennent des palettes du dépôt d'origine. Les six dernières
 * étaient posées une fois pour toutes sur la palette blanche, donc identiques quel que soit le
 * thème ; elles restent ici pour que rien ne soit perdu.
 */
@Immutable
data class AppColors(
    val green: Color,
    val green2: Color,
    val cream: Color,
    val paper: Color,
    val beige: Color,
    val gold: Color,
    val text: Color,
    val muted: Color,
    val line: Color,
    val red: Color,
    val soft: Color,
    val softBorder: Color,
    val selected: Color,
    val onDark: Color,
    val onDarkSoft: Color,
    val track: Color,
    val progress: Color,
    val surahBadge: Color,
    // Couleurs hors palette, communes à tous les thèmes.
    val quizLavender: Color = Color(0xFFF0EEFF),
    val quizPurple: Color = Color(0xFF6563A7),
    val review: Color = Color(0xFF246B48),
    val reviewSoft: Color = Color(0xFFEBF5EF),
    val surfaceSecondary: Color = Color(0xFFF8F6F4),
    val mutedLight: Color = Color(0xFFA49DA9),
)

/** Les cinq palettes du dépôt d'origine. */
object AppPalettes {

    val White = AppColors(
        green = Color(0xFF7B285C),
        green2 = Color(0xFF7B285C),
        cream = Color(0xFFFCFBF9),
        paper = Color(0xFFFFFFFF),
        beige = Color(0xFFECE8E5),
        gold = Color(0xFFC89A52),
        text = Color(0xFF241C2B),
        muted = Color(0xFF746D7B),
        line = Color(0xFFECE8E5),
        red = Color(0xFFA85353),
        soft = Color(0xFFF8F6F4),
        softBorder = Color(0xFFECE8E5),
        selected = Color(0xFFF5EDF2),
        onDark = Color(0xFFF5EDF2),
        onDarkSoft = Color(0xFFF5EDF2),
        track = Color(0xFFE8D9E3),
        progress = Color(0xFFF5EDF2),
        surahBadge = Color(0xFFF5EDF2),
    )

    val Classic = AppColors(
        green = Color(0xFF153F36),
        green2 = Color(0xFF276454),
        cream = Color(0xFFF7F5EE),
        paper = Color(0xFFFFFDF7),
        beige = Color(0xFFE9E2D2),
        gold = Color(0xFFB39559),
        text = Color(0xFF20342E),
        muted = Color(0xFF6C7B72),
        line = Color(0xFFE4E7DF),
        red = Color(0xFF9D554D),
        soft = Color(0xFFECF1EA),
        softBorder = Color(0xFFCAD8CE),
        selected = Color(0xFFEAF2EC),
        onDark = Color(0xFFBFD6C9),
        onDarkSoft = Color(0xFFC9DDCF),
        track = Color(0xFF49756B),
        progress = Color(0xFFDBC591),
        surahBadge = Color(0xFFE8EFE8),
    )

    val Feminine = AppColors(
        green = Color(0xFF9D496B),
        green2 = Color(0xFFC76D91),
        cream = Color(0xFFFFFAFC),
        paper = Color(0xFFFFF5F8),
        beige = Color(0xFFF3DCE5),
        gold = Color(0xFFB39559),
        text = Color(0xFF3C2833),
        muted = Color(0xFF765B69),
        line = Color(0xFFEBCAD8),
        red = Color(0xFF9D554D),
        soft = Color(0xFFF3DCE5),
        softBorder = Color(0xFFDCA6BD),
        selected = Color(0xFFF8E7EE),
        onDark = Color(0xFFFBE4ED),
        onDarkSoft = Color(0xFFF5D8E4),
        track = Color(0xFFB9708D),
        progress = Color(0xFFF3DCE5),
        surahBadge = Color(0xFFF3DCE5),
    )

    val Lilac = AppColors(
        green = Color(0xFF5F548E),
        green2 = Color(0xFF897AB5),
        cream = Color(0xFFFBF9FF),
        paper = Color(0xFFFFFCFF),
        beige = Color(0xFFECE4F5),
        gold = Color(0xFFB89D65),
        text = Color(0xFF2D2943),
        muted = Color(0xFF716B86),
        line = Color(0xFFE3DCF0),
        red = Color(0xFFA45C67),
        soft = Color(0xFFF0EBF8),
        softBorder = Color(0xFFCFC5E3),
        selected = Color(0xFFECE6F6),
        onDark = Color(0xFFEDE7FB),
        onDarkSoft = Color(0xFFE0D8F2),
        track = Color(0xFF9689BB),
        progress = Color(0xFFD4C4EB),
        surahBadge = Color(0xFFEEE8F8),
    )

    val Night = AppColors(
        green = Color(0xFF132B47),
        green2 = Color(0xFF315273),
        cream = Color(0xFFF7F7F4),
        paper = Color(0xFFFFFDF8),
        beige = Color(0xFFE9E6DF),
        gold = Color(0xFFB58942),
        text = Color(0xFF1C2A3B),
        muted = Color(0xFF68727D),
        line = Color(0xFFDFE3E5),
        red = Color(0xFFA85F57),
        soft = Color(0xFFE9EEF1),
        softBorder = Color(0xFFC4D2DB),
        selected = Color(0xFFE8EFF4),
        onDark = Color(0xFFD5E1E9),
        onDarkSoft = Color(0xFFCBD7E1),
        track = Color(0xFF5C7390),
        progress = Color(0xFFD8AF68),
        surahBadge = Color(0xFFE8EDF1),
    )

    fun of(theme: AppTheme): AppColors = when (theme) {
        AppTheme.WHITE -> White
        AppTheme.CLASSIC -> Classic
        AppTheme.FEMININE -> Feminine
        AppTheme.LILAC -> Lilac
        AppTheme.NIGHT -> Night
    }
}

/**
 * Calcule le jeu de couleurs effectif d'un thème, accent compris.
 *
 * Reproduit exactement la règle du dépôt d'origine, qui n'est pas symétrique :
 *
 *  - un accent **explicite** écrase toujours `green`, `green2`, `selected`, `surahBadge` et
 *    `progress` ;
 *  - sans accent explicite, seul le thème **blanc** reçoit l'accent par défaut (prune) ;
 *  - un thème coloré (vert, rose, lilas, nuit) garde donc ses propres fonds teintés, sinon les
 *    cinq thèmes finiraient par se ressembler.
 *
 * `surfaceSecondary` est ensuite recalculé : teinte fixe pour le blanc, `soft` sinon.
 */
fun resolveColors(theme: AppTheme, accent: AccentName? = null): AppColors {
    val palette = AppPalettes.of(theme)
    val withAccent = if (accent != null || theme == AppTheme.WHITE) {
        val a = Accents.resolve(theme, accent)
        palette.copy(
            green = a.primary,
            green2 = a.primary,
            selected = a.soft,
            surahBadge = a.soft,
            progress = a.soft,
        )
    } else {
        palette
    }
    return withAccent.copy(
        surfaceSecondary = if (theme == AppTheme.WHITE) Color(0xFFF8F6F4) else withAccent.soft,
    )
}

/** Un thème tel qu'il est présenté dans l'écran Apparence. */
@Immutable
data class AppThemeOption(
    val theme: AppTheme,
    val name: String,
    val description: String,
    val swatches: List<Color>,
)

/** Les cinq choix de l'écran Apparence, dans l'ordre du dépôt d'origine. */
val appThemeOptions: List<AppThemeOption> = listOf(
    AppThemeOption(
        theme = AppTheme.WHITE,
        name = "Thème blanc",
        description = "Simple et épuré",
        swatches = listOf(
            Color(0xFF7B285C), Color(0xFFECE8E5), Color(0xFFFFFFFF), Color(0xFFC89A52),
        ),
    ),
    AppThemeOption(
        theme = AppTheme.CLASSIC,
        name = "Thème vert",
        description = "Serein et naturel",
        swatches = listOf(
            Color(0xFF153F36), Color(0xFFDCEBDD), Color(0xFFFFFDF7), Color(0xFFD4AA67),
        ),
    ),
    AppThemeOption(
        theme = AppTheme.FEMININE,
        name = "Thème rose",
        description = "Doux et moderne",
        swatches = listOf(
            Color(0xFF9D496B), Color(0xFFF3DCE5), Color(0xFFFFF5F8), Color(0xFFD18DA3),
        ),
    ),
    AppThemeOption(
        theme = AppTheme.LILAC,
        name = "Lilas & Perle",
        description = "Délicat et raffiné",
        swatches = listOf(
            Color(0xFF5F548E), Color(0xFFD9CDEA), Color(0xFFF8F3FC), Color(0xFFB89D65),
        ),
    ),
    AppThemeOption(
        theme = AppTheme.NIGHT,
        name = "Bleu Nuit & Or",
        description = "Sobre et élégant",
        swatches = listOf(
            Color(0xFF132B47), Color(0xFF5C7390), Color(0xFFF4F1EA), Color(0xFFB58942),
        ),
    ),
)
