package com.msoumaya.deepseekandroid.core.design.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme

// ---------------------------------------------------------------------------
// Progression
// ---------------------------------------------------------------------------
// Portage de `ProgressTrack` et `ProgressRing` (`src/ui/Premium.tsx`).
//
// Les deux composants annonçaient déjà leur valeur à l'accessibilité côté React Native
// (`accessibilityRole="progressbar"`, `accessibilityValue`) : c'est repris ici avec
// `progressBarRangeInfo`, sans quoi TalkBack ne lirait plus qu'un rectangle muet.
// ---------------------------------------------------------------------------

/**
 * Barre de progression fine.
 *
 * Reproduit : hauteur 6, fond `soft`, rayon 8, remplissage `green2`. La valeur est bornée entre
 * 0 et 1 — le dépôt d'origine faisait ce bornage à l'affichage, pas au calcul, et une valeur
 * aberrante produisait une barre qui débordait de son cadre.
 */
@Composable
fun ProgressTrack(
    value: Float,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.green2,
    trackColor: Color = AppTheme.colors.soft,
) {
    val bornée = value.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(shape)
            .background(trackColor)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(bornée, 0f..1f)
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(bornée)
                .height(6.dp)
                .clip(shape)
                .background(color),
        )
    }
}

/**
 * Anneau de progression, avec un contenu au centre.
 *
 * Reproduit la géométrie du dépôt d'origine : l'anneau est tracé à 6 px du bord, avec un trait
 * de 9 px, un fond `paper` plein et une piste `soft`. Le trait a une extrémité arrondie, ce qui
 * donne l'aspect doux de l'original.
 */
@Composable
fun ProgressRing(
    value: Float,
    modifier: Modifier = Modifier,
    size: Dp = 130.dp,
    strokeWidth: Dp = 9.dp,
    color: Color = AppTheme.colors.green2,
    trackColor: Color = AppTheme.colors.soft,
    fillColor: Color = AppTheme.colors.paper,
    content: @Composable () -> Unit = {},
) {
    val bornée = value.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .size(size)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(bornée, 0f..1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val trait = strokeWidth.toPx()
            // Le rayon laisse la moitié du trait hors du cadre, comme `(size - 12) / 2` avec un
            // trait de 9 dans le composant d'origine : l'anneau touche presque le bord.
            val rayon = (this.size.minDimension - trait) / 2f
            val centre = Offset(this.size.width / 2f, this.size.height / 2f)

            drawCircle(color = fillColor, radius = rayon - trait / 2f, center = centre)
            drawCircle(
                color = trackColor,
                radius = rayon,
                center = centre,
                style = Stroke(width = trait),
            )
            if (bornée > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * bornée,
                    useCenter = false,
                    topLeft = Offset(centre.x - rayon, centre.y - rayon),
                    size = Size(rayon * 2f, rayon * 2f),
                    style = Stroke(width = trait, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}
