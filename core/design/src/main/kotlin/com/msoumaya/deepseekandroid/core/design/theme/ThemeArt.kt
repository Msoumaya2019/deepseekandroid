package com.msoumaya.deepseekandroid.core.design.theme

import androidx.annotation.DrawableRes
import com.msoumaya.deepseekandroid.core.design.R
import com.msoumaya.deepseekandroid.core.model.AppTheme

// ---------------------------------------------------------------------------
// Illustrations de thème
// ---------------------------------------------------------------------------
// Portage de `themeArt` (`src/ui/Premium.tsx`) et de `readingArt`
// (`src/ui/DesignSystem.tsx`).
//
// Le dépôt d'origine associait cinq PNG aux cinq thèmes :
//
//   white -> white.png     classic -> emerald.png   feminine -> rose.png
//   lilac -> lilac.png     night   -> night.png
//
// Le nom du fichier vert est « emerald », pas « classic » : la correspondance est conservée
// pour que l'on retrouve la source sans hésiter.
//
// Les six images sont des photographies sans transparence, recompressées en JPEG de qualité 85
// par `tools/import-theme-art.py` : 12 Mo deviennent 1,3 Mo, soit 89 % de moins, **sans
// redimensionnement**. Elles sont placées dans `drawable-nodpi` : à cette densité, Android ne
// les rééchantillonne pas, l'affichage reste celui prévu.
// ---------------------------------------------------------------------------

/** Fond d'illustration d'un thème, pour la bande d'en-tête des écrans. */
@DrawableRes
fun themeArt(theme: AppTheme): Int = when (theme) {
    AppTheme.WHITE -> R.drawable.theme_white
    AppTheme.CLASSIC -> R.drawable.theme_classic
    AppTheme.FEMININE -> R.drawable.theme_feminine
    AppTheme.LILAC -> R.drawable.theme_lilac
    AppTheme.NIGHT -> R.drawable.theme_night
}

/**
 * Opacités du fond de thème.
 *
 * Le dépôt d'origine superposait le fond à 0,55 sur la bande d'accueil, et — sur les bandes
 * compactes — à `theme==='white'?0.68:0.28`. Un fond trop opaque rendrait le titre illisible :
 * ces trois valeurs sont reprises telles quelles.
 */
const val ThemeArtHeroOpacity = 0.55f

/** Opacité d'une bande d'en-tête compacte sur le thème **blanc**. */
const val ThemeArtHeroCompactOpacity = 0.68f

/**
 * Opacité d'une bande d'en-tête compacte sur un thème **coloré**.
 *
 * La règle de la source est asymétrique, et c'est la palette colorée qui l'impose : sur ces
 * thèmes, un fond à 0,68 passerait devant le titre au lieu de l'accompagner.
 */
const val ThemeArtHeroColoredOpacity = 0.28f

/**
 * Opacité retenue pour la bande d'en-tête compacte d'un thème donné.
 *
 * Elle est nommée parce qu'un appelant qui écrirait [ThemeArtHeroCompactOpacity] en dur se
 * tromperait sur **quatre thèmes sur cinq** — et le défaut ne se verrait que sur eux.
 */
fun themeArtHeroAlpha(theme: AppTheme): Float =
    if (theme == AppTheme.WHITE) ThemeArtHeroCompactOpacity else ThemeArtHeroColoredOpacity

/** Illustration « continuer ma lecture ». */
@DrawableRes
val ReadingArt: Int = R.drawable.reading_art
