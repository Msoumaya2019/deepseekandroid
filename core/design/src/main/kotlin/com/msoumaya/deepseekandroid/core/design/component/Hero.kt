package com.msoumaya.deepseekandroid.core.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.design.theme.themeArt
import com.msoumaya.deepseekandroid.core.design.theme.themeArtHeroAlpha

// ---------------------------------------------------------------------------
// Bande d'en-tête
// ---------------------------------------------------------------------------
// Portage de `IslamicHero` (`src/ui/Premium.tsx`).
//
// Trois mesures relevées à la source, qui ne se devinent pas :
//
//   1. **L'opacité du fond dépend du thème** — `theme==='white'?0.68:0.28`. Les quatre thèmes
//      colorés reçoivent donc un fond près de deux fois et demie plus discret que le blanc.
//      Poser 0,68 partout rendrait le titre illisible sur ces quatre-là, et le défaut ne se
//      verrait que sur eux. La règle vit dans `themeArtHeroAlpha`, à côté des deux constantes.
//
//   2. La bande **déborde** de la marge des autres blocs : elle est posée dans un conteneur en
//      retrait de 18 px et remonte de −18 px pour occuper toute la largeur. En Compose, cela
//      s'exprime en plaçant la bande **hors** de la colonne en retrait, ce qui évite une marge
//      négative — plus court à lire, et le résultat est le même. Même choix qu'`HomeScreen`.
//
//   3. Les marges verticales ne sont **pas** symétriques — 22 en haut, 20 en bas —, et le titre
//      porte un interligne explicite de 35 px pour 30 px de corps, soit 1,167 là où les autres
//      titres de l'application sont à 1,2. Les deux valeurs sont reprises telles quelles.
//
// **Ce qui n'est pas là.** La variante haute (130 px) et l'emplacement d'enfants n'ont aucun
// appelant : les cinq bandes du client d'origine sont toutes compactes et sans contenu. Les
// écrire quand même serait écrire du code que rien ne joue.
// ---------------------------------------------------------------------------

/** Hauteur minimale d'une bande compacte, valeur du dépôt d'origine. */
private val HERO_MIN_HEIGHT = 115.dp

/** Part de la largeur occupée par le bloc de texte (`maxWidth:'95%'` à la source). */
private const val HERO_TEXT_WIDTH = 0.95f

/**
 * Bande d'en-tête d'un écran : illustration du thème, titre, sous-titre.
 *
 * À placer **hors** d'une colonne en retrait horizontal : la bande occupe toute la largeur.
 *
 * @param title titre de l'écran, en police de titre et couleur principale du thème.
 * @param subtitle précision affichée sous le titre.
 */
@Composable
fun AppHero(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val theme = AppTheme.themeName

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HERO_MIN_HEIGHT)
            .background(colors.cream),
    ) {
        Image(
            painter = painterResource(themeArt(theme)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = themeArtHeroAlpha(theme),
            modifier = Modifier.matchParentSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth(HERO_TEXT_WIDTH)
                .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 20.dp),
        ) {
            AppHeading(
                text = title,
                size = AppTheme.typeScale.screen,
                // Interligne explicite de la source : 35 px pour un corps de 30, soit 1,167 là
                // où les autres titres de l'application sont à 1,2.
                lineHeight = 35.sp,
            )
            AppLabel(
                text = subtitle,
                selectable = false,
                fontSize = 13.sp,
                color = colors.muted,
                style = TextStyle(lineHeight = 19.sp),
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}
