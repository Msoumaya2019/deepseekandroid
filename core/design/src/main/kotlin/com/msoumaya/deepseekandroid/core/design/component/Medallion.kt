package com.msoumaya.deepseekandroid.core.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msoumaya.deepseekandroid.core.design.R
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme

// ---------------------------------------------------------------------------
// Médaillon de numéro de sourate
// ---------------------------------------------------------------------------
// Portage de `QuranNumberMedallion` (`src/ui/DesignSystem.tsx`).
//
// Un ornement doré de 44 x 44 sur lequel se pose un numéro. Le dépôt d'origine superposait
// une `Image` en `position:'absolute'` et un `Text` centré par le conteneur ; c'est repris
// ici par une `Box` centrée, ce qui donne le même résultat sans positionnement absolu.
//
// Le nom ne porte **pas** le préfixe `App` que portent `AppCard`, `AppLabel` ou `AppButton`.
// Ce préfixe existe pour distinguer un composant du portage de son homonyme Material, ou d'un
// mot trop générique pour dire quelque chose seul : `Card`, `Label`, `Button`, `Field`. Ici le
// nom est déjà sans ambiguïté et n'entre en collision avec rien — comme `ProgressTrack` et
// `ProgressRing`, qui l'ont eux aussi gardé sans préfixe.
//
// L'illustration est importée par `tools/import-medallion.py`, qui produit une image par
// densité. La source en fournissait une seule de 1254 x 1254 pour un affichage de 44 px, soit
// 6 Mo de bitmap décodé par appareil ; les cinq fichiers réunis pèsent 22 Ko.
// ---------------------------------------------------------------------------

/**
 * Côté du médaillon, en pixels indépendants de la densité.
 *
 * Valeur du dépôt d'origine : le `View` qui porte l'ornement fait 44 x 44 et l'`Image` le
 * remplit exactement. C'est aussi la seule taille à laquelle le médaillon soit affiché — il
 * n'a qu'un appelant, la ligne d'une sourate.
 */
private val MEDALLION_SIZE = 44.dp

/**
 * Corps du numéro.
 *
 * La source pose 20, une valeur écrite au cas par cas. L'échelle typographique du portage n'a
 * pas de jeton à 20 — le plus proche est `section`, à 21 — et cette valeur n'est pas une
 * taille de texte courante : elle est propre à l'ornement, qui doit tenir dans son vide
 * intérieur. Elle reste donc ici, à côté de la taille qui la contraint.
 */
private val NUMBER_SIZE = 20.sp

/**
 * Numéro de sourate posé sur un ornement doré.
 *
 * L'ornement est décoratif : il n'est pas annoncé à l'accessibilité, seul le numéro l'est. Une
 * ligne de liste qui s'annonce déjà par « Ouvrir … » n'a pas à faire répéter son rang.
 *
 * @param number rang affiché. La source ne le formate pas : elle écrit le nombre tel quel.
 */
@Composable
fun QuranNumberMedallion(number: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(MEDALLION_SIZE),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.medallion),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
        )
        Text(
            text = number.toString(),
            style = TextStyle(
                color = AppTheme.colors.text,
                fontSize = NUMBER_SIZE,
                fontFamily = AppTheme.fonts.titleFamily,
            ),
        )
    }
}
