package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import com.msoumaya.deepseekandroid.core.domain.QuranArchive
import com.msoumaya.deepseekandroid.core.domain.ReaderLayout
import com.msoumaya.deepseekandroid.core.model.MushafPage
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
 * Le rapport appliqué est celui de **la source affichée**, porté par [page] : la page
 * embarquée est en 1920×3106, celle du paquet « Coran 1441 » en 1440×2320. Centrer l'une avec
 * le rapport de l'autre l'étirerait de plusieurs pour cent — invisible sur une capture, très
 * visible en lisant.
 *
 * La taille ajustée est **reçue** plutôt que recalculée : l'écran en a besoin pour convertir
 * les coordonnées du doigt, et deux calculs séparés de la même grandeur finissent par diverger
 * — ce qui donnerait un surlignage décalé exactement quand la page change de taille.
 *
 * ## Une image, ou quinze bandes
 *
 * Le moushaf embarqué livre une image par page. La source 1441 livre **quinze bandes** de
 * 1440×232, qui se recouvrent partiellement : elles sont réparties de façon que la première
 * touche le haut de la page et la dernière son bas. C'est la disposition du client d'origine,
 * reprise telle quelle — les bandes ne sont pas juxtaposées bout à bout, et les coller l'une
 * sous l'autre décalerait toutes les lignes suivantes.
 *
 * ## Les surlignages
 *
 * Les rectangles viennent du référentiel de la source — `bounds.json` pour le moushaf de
 * Médine, `coran_1441-bounds.json` pour le paquet — et sont ramenés à l'échelle de
 * l'affichage. Le plus petit rectangle contenant le doigt gagne (voir
 * `ReaderData.verseAtImagePoint`) : sur une page où deux versets se chevauchent visuellement,
 * c'est le plus précis qui doit répondre.
 */
@Composable
internal fun MushafPageView(
    page: MushafPage,
    zoom: ReaderZoom,
    pageWidth: Float,
    pageHeight: Float,
    selectedVerse: Int?,
    bookmarkIds: Set<Int>,
    difficultIds: Set<Int>,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors

    if (pageWidth <= 0f || pageHeight <= 0f) return

    val frame = ReaderLayout.FRAME.toFloat()
    val innerWidth = pageWidth - frame
    val innerHeight = pageHeight - frame

    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(pageWidth.dp, pageHeight.dp)
                // Le zoom s'applique à la page entière : images et surlignages restent
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
                PageImages(
                    page = page,
                    innerWidth = innerWidth,
                    innerHeight = innerHeight,
                    context = context,
                )
            }

            for (row in page.rows) {
                val verseId = Quran.verseId(row.surah, row.ayah) ?: continue
                val tint = tintFor(verseId, selectedVerse, bookmarkIds, difficultIds, colors) ?: continue
                Box(
                    modifier = Modifier
                        .offset(
                            x = (frame / 2f + row.left / page.sourceWidth.toFloat() * innerWidth).dp,
                            y = (frame / 2f + row.top / page.sourceHeight.toFloat() * innerHeight).dp,
                        )
                        .size(
                            ((row.right - row.left) / page.sourceWidth.toFloat() * innerWidth).dp,
                            ((row.bottom - row.top) / page.sourceHeight.toFloat() * innerHeight).dp,
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .background(tint),
                )
            }
        }
    }
}

/**
 * Les images d'une page, dans le cadre blanc.
 *
 * Le cas d'une seule image est celui de toutes les sources embarquées : l'image occupe la page
 * entière. Le cas de plusieurs bandes est celui du paquet « Coran 1441 ».
 */
@Composable
private fun PageImages(
    page: MushafPage,
    innerWidth: Float,
    innerHeight: Float,
    context: android.content.Context,
) {
    if (page.isSingleImage) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(page.lines.first())
                // Demandée à la taille affichée : Coil réduit au décodage, ce qui divise
                // la mémoire occupée par la page.
                .size(innerWidth.roundToInt(), innerHeight.roundToInt())
                .crossfade(false)
                .build(),
            contentDescription = "Page du moushaf",
            // `Fit` et non `FillBounds` : une page ne se déforme pas, même si la mesure et
            // l'image divergent d'un pixel.
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val lineHeight = MushafPageGeometry.bandHeight(innerWidth)
    val count = page.lines.size

    for ((index, uri) in page.lines.withIndex()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(uri)
                .size(innerWidth.roundToInt(), lineHeight.roundToInt())
                .crossfade(false)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                // La position vient de `MushafPageGeometry`, et non d'un calcul refait ici :
                // c'est ce qui permet de l'éprouver sans dessiner, et de garantir que la
                // première bande touche le haut de la page et la dernière son bas.
                .offset(y = MushafPageGeometry.bandTop(index, count, innerHeight, lineHeight).dp)
                .size(innerWidth.dp, lineHeight.dp),
        )
    }
}

/**
 * Rapport d'une bande de ligne : 1440 sur 232, mesuré sur le paquet.
 *
 * Écrit à partir des constantes du paquet et non en dur : c'est la même mesure qui décide de
 * la validité d'une image pendant l'installation. Deux valeurs séparées finiraient par
 * diverger, et l'installation accepterait alors des images que l'affichage étire.
 */
internal val LINE_ASPECT: Float =
    QuranArchive.IMAGE_HEIGHT.toFloat() / QuranArchive.IMAGE_WIDTH.toFloat()

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
