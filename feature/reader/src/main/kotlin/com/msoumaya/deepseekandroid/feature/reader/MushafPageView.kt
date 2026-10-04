package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.msoumaya.deepseekandroid.core.design.theme.AppColors
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.ReaderData
import com.msoumaya.deepseekandroid.core.domain.ReaderLayout
import com.msoumaya.deepseekandroid.core.model.ReaderZoom
import kotlin.math.roundToInt

/**
 * Une page du moushaf, centrée et jamais déformée.
 *
 * ## Le centrage
 *
 * La taille est calculée par `ReaderLayout.fitMushafPage`, à partir des dimensions
 * **réellement disponibles** — c'est-à-dire après retrait des barres système, des découpes
 * d'écran et de la coquille du lecteur. Aucune marge fixe n'est appliquée : une marge en dur
 * paraît correcte sur l'appareil où elle a été choisie, et décale la page partout ailleurs.
 *
 * Le rapport `(largeur - cadre) / (hauteur - cadre)` reste celui de la source : la page est
 * réduite, jamais étirée. Une page de moushaf déformée est illisible — et le cadre de 4 px
 * est compté dans **les deux** dimensions, ce qui évite la dérive observée quand on enchaîne
 * les changements de page.
 *
 * ## Les surlignages
 *
 * Les rectangles viennent de `bounds.json`, exprimés dans l'espace 1920×3106 de la source, et
 * sont ramenés à l'échelle de l'affichage. Le plus petit rectangle contenant le doigt gagne
 * (voir `ReaderData.verseAtImagePoint`) : sur une page où deux versets se chevauchent
 * visuellement, c'est le plus précis qui doit répondre.
 */
@Composable
internal fun MushafPageView(
    page: Int,
    zoom: ReaderZoom,
    availableWidth: Float,
    availableHeight: Float,
    selectedVerse: Int?,
    bookmarkIds: Set<Int>,
    difficultIds: Set<Int>,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val fitted = ReaderLayout.fitMushafPage(availableWidth.toDouble(), availableHeight.toDouble())
    val pageWidth = fitted.width.toFloat()
    val pageHeight = fitted.height.toFloat()

    if (pageWidth <= 0f || pageHeight <= 0f) return

    val frame = ReaderLayout.FRAME.toFloat()
    val innerWidth = pageWidth - frame
    val innerHeight = pageHeight - frame

    val rows = remember(page) { ReaderData.pageRows(page) }
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(pageWidth.dp, pageHeight.dp)
                // Le zoom s'applique à la page entière : image et surlignages restent
                // solidaires, sinon les rectangles dériveraient dès le premier agrandissement.
                .graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.x
                    translationY = zoom.y
                    // Origine en haut à gauche, comme le client d'origine : le point (x, y)
                    // de la page se retrouve en (x·échelle + décalage). Avec l'origine au
                    // centre par défaut, un zoom ferait sortir la page de l'écran.
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            Box(
                modifier = Modifier
                    .size(innerWidth.dp, innerHeight.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(MushafAssets.uri(page))
                        // Demandée à la taille affichée : Coil réduit au décodage, ce qui
                        // divise la mémoire occupée par la page.
                        .size(innerWidth.roundToInt(), innerHeight.roundToInt())
                        .crossfade(false)
                        .build(),
                    contentDescription = "Page $page du moushaf",
                    // `Fit` et non `FillBounds` : une page ne se déforme pas, même si la
                    // mesure et l'image divergent d'un pixel.
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            for (row in rows) {
                val verseId = Quran.verseId(row.surah, row.ayah) ?: continue
                val tint = tintFor(verseId, selectedVerse, bookmarkIds, difficultIds, colors) ?: continue
                Box(
                    modifier = Modifier
                        .offset(
                            x = (frame / 2f + row.left / ReaderLayout.PAGE_WIDTH.toFloat() * innerWidth).dp,
                            y = (frame / 2f + row.top / ReaderLayout.PAGE_HEIGHT.toFloat() * innerHeight).dp,
                        )
                        .size(
                            ((row.right - row.left) / ReaderLayout.PAGE_WIDTH.toFloat() * innerWidth).dp,
                            ((row.bottom - row.top) / ReaderLayout.PAGE_HEIGHT.toFloat() * innerHeight).dp,
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .background(tint),
                )
            }
        }
    }
}

/**
 * Couleur d'un surlignage, et son ordre de priorité.
 *
 * Le verset **en cours d'écoute** passe avant tout : c'est l'information la plus fugace, donc
 * celle qui doit rester lisible. Vient ensuite le verset **difficile**, puis le signet, puis
 * la sélection. Les valeurs sont celles du client d'origine, opacités comprises.
 *
 * Les couleurs sont reçues en paramètre : `AppTheme.colors` ne se lit que depuis une
 * composition, et cette fonction doit rester appelable dans une boucle sans être `@Composable`.
 */
private fun tintFor(
    verseId: Int,
    selectedVerse: Int?,
    bookmarkIds: Set<Int>,
    difficultIds: Set<Int>,
    colors: AppColors,
): Color? = when {
    verseId == selectedVerse -> colors.selected.copy(alpha = 0.42f)
    verseId in difficultIds -> Color(0xFFE85B5B).copy(alpha = 0.18f)
    verseId in bookmarkIds -> colors.green2.copy(alpha = 0.18f)
    else -> null
}
