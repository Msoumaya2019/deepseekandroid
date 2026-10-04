package com.msoumaya.deepseekandroid.core.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msoumaya.deepseekandroid.core.model.AccentName
import com.msoumaya.deepseekandroid.core.model.AppTheme

// ---------------------------------------------------------------------------
// Jetons de design
// ---------------------------------------------------------------------------
// Portage fidèle de `src/theme/tokens.ts` de l'application React Native. Les valeurs
// numériques sont celles du dépôt d'origine : elles ne sont pas « arrondies à l'Android »,
// sinon la nouvelle application ne ressemblerait plus à l'ancienne.
//
// Chaque famille est un `data class` immuable avec un `Default` plutôt qu'un objet global :
// un test peut ainsi vérifier une valeur sans dépendre d'un état global mutable, ce que
// `applyTheme()` faisait côté React Native.
// ---------------------------------------------------------------------------

/** Espacements. `section` sépare deux grandes sections d'un écran. */
@Immutable
data class AppSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val section: Dp = 32.dp,
) {
    companion object {
        val Default = AppSpacing()
    }
}

/**
 * Rayons d'arrondi. `sheet` habille les feuilles modales du bas.
 *
 * `button` ne figurait pas dans `tokens.ts` : le composant `Button` du dépôt d'origine portait
 * la valeur 15 en dur. Elle est remontée ici pour qu'un thème puisse la changer sans toucher
 * au composant.
 */
@Immutable
data class AppRadius(
    val small: Dp = 14.dp,
    val card: Dp = 20.dp,
    val pill: Dp = 28.dp,
    val sheet: Dp = 30.dp,
    val button: Dp = 15.dp,
) {
    companion object {
        val Default = AppRadius()
    }
}

/**
 * Échelle typographique.
 *
 * Côté React Native ces valeurs étaient des tailles de police posées au cas par cas. Elles
 * sont conservées telles quelles : `screen` pour un titre d'écran, `header` pour l'en-tête
 * d'une carte forte, `section` pour un intertitre, `card` pour un titre de carte, `body` pour
 * le texte courant, `secondary` pour une précision, `metadata` pour une mention discrète,
 * `arabic` pour le texte coranique.
 */
@Immutable
data class AppTypeScale(
    val header: TextUnit = 24.sp,
    val screen: TextUnit = 30.sp,
    val section: TextUnit = 21.sp,
    val card: TextUnit = 17.sp,
    val body: TextUnit = 14.sp,
    val secondary: TextUnit = 12.sp,
    val metadata: TextUnit = 11.sp,
    val arabic: TextUnit = 22.sp,
) {
    companion object {
        val Default = AppTypeScale()
    }
}

/**
 * Ombre portée d'une carte.
 *
 * React Native exprimait cette ombre en `shadowColor/shadowOpacity/shadowRadius/offset` plus
 * un `elevation` Android de 1. Compose n'expose pas d'opacité d'ombre séparée : on rend la
 * même intention avec une élévation faible et une teinte d'ombre reprise du texte, ce qui
 * donne un liseré à peine visible plutôt qu'un gris neutre.
 */
@Immutable
data class CardShadow(
    val color: Color = Color(0xFF241C2B),
    val alpha: Float = 0.045f,
    val radius: Dp = 10.dp,
    val offsetY: Dp = 3.dp,
    val elevation: Dp = 1.dp,
) {
    companion object {
        val Default = CardShadow()
    }
}

// ---------------------------------------------------------------------------
// Accents
// ---------------------------------------------------------------------------

/**
 * Couleur d'accent appliquée par-dessus une palette.
 *
 * `swatch` sert à la pastille de choix dans l'écran Apparence, `primary` remplace la couleur
 * principale du thème, `soft` remplace les fonds teintés.
 */
@Immutable
data class Accent(
    val name: AccentName,
    val label: String,
    val swatch: Color,
    val primary: Color,
    val soft: Color,
)

/** Les quatre accents proposés, dans l'ordre de l'écran Apparence. */
object Accents {

    val Prune = Accent(
        name = AccentName.PRUNE,
        label = "Prune",
        swatch = Color(0xFF7B285C),
        primary = Color(0xFF7B285C),
        soft = Color(0xFFF5EDF2),
    )

    val Rose = Accent(
        name = AccentName.ROSE,
        label = "Rose",
        swatch = Color(0xFFD9899A),
        primary = Color(0xFFA95069),
        soft = Color(0xFFFCF0F3),
    )

    val Green = Accent(
        name = AccentName.GREEN,
        label = "Vert",
        swatch = Color(0xFF6E8B68),
        primary = Color(0xFF54734E),
        soft = Color(0xFFEDF5EA),
    )

    val Gold = Accent(
        name = AccentName.GOLD,
        label = "Doré",
        swatch = Color(0xFFC89A52),
        primary = Color(0xFF916825),
        soft = Color(0xFFFBF4E8),
    )

    /** Accent par défaut de chaque thème, quand l'utilisateur n'en a choisi aucun. */
    val byDefault: Map<AppTheme, Accent> = mapOf(
        AppTheme.WHITE to Prune,
        AppTheme.CLASSIC to Green,
        AppTheme.FEMININE to Rose,
        AppTheme.LILAC to Prune,
        AppTheme.NIGHT to Prune,
    )

    val all: List<Accent> = listOf(Prune, Rose, Green, Gold)

    fun of(name: AccentName): Accent = when (name) {
        AccentName.PRUNE -> Prune
        AccentName.ROSE -> Rose
        AccentName.GREEN -> Green
        AccentName.GOLD -> Gold
    }

    /** Accent retenu pour un thème donné, en tenant compte du choix explicite éventuel. */
    fun resolve(theme: AppTheme, accent: AccentName?): Accent =
        accent?.let(::of) ?: byDefault.getValue(theme)
}
