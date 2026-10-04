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
 * Opacité du fond de thème.
 *
 * Le dépôt d'origine superposait le fond à 0,55 sur la bande d'accueil, et à 0,68 sur la bande
 * de l'écran Coran. Un fond trop opaque rendrait le titre illisible : ces valeurs sont reprises
 * telles quelles.
 */
const val ThemeArtHeroOpacity = 0.55f
const val ThemeArtHeroCompactOpacity = 0.68f

/** Illustration « continuer ma lecture ». */
@DrawableRes
val ReadingArt: Int = R.drawable.reading_art
