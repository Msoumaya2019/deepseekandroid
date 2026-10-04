package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.msoumaya.deepseekandroid.core.design.component.AppCard
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.PageNavigation
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.ReaderData
import com.msoumaya.deepseekandroid.core.domain.ReaderGesture
import com.msoumaya.deepseekandroid.core.domain.ReaderLayout
import com.msoumaya.deepseekandroid.core.domain.ReaderZoomGeometry
import com.msoumaya.deepseekandroid.core.model.ReaderZoom
import kotlin.math.roundToInt

/** Le moushaf de Médine compte 604 pages. Sert de repli si le référentiel n'est pas chargé. */
private const val DEFAULT_TOTAL_PAGES = 604

/**
 * Le lecteur de moushaf.
 *
 * C'est la **priorité absolue** du cahier des charges, et l'écran qui décide si l'application
 * est utilisable : on y passe le plus de temps, et c'est là qu'un défaut se sent.
 *
 * ## Ce qui est en place
 *
 * - **La page est centrée dans l'espace réellement disponible**, après retrait des barres
 *   système, des découpes d'écran et de la navigation par geste. Aucune marge fixe : la
 *   géométrie vient de `ReaderLayout.fitMushafPage`, qui préserve le rapport de la source.
 * - **La page n'est jamais déformée.** Le cadre de 4 px est compté dans les deux dimensions.
 * - **Un seul gestionnaire de gestes**, décrit dans `ReaderGestures` : balayage, pincement,
 *   appui et appui long ne peuvent pas se disputer les mêmes événements.
 * - **Trois pages en mémoire au maximum** — la courante et ses voisines — via `ReaderPreload`.
 * - **Aucun réseau.** Les 604 pages sont dans l'application : le lecteur s'ouvre en avion.
 *
 * ## Ce qui viendra, et où
 *
 * L'audio verset par verset, la coquille d'étude (bandeau de séance, marqueurs de marge) et
 * le mode signet appartiennent aux phases B et C. La place est faite : ces actions
 * **apparaîtront dans la coquille quand elles existeront**, et n'y figurent pas en attendant.
 *
 * @param initialPage page ouverte au lancement. Bornée au moushaf.
 * @param onClose ferme le lecteur. L'écran ne connaît pas la navigation : c'est l'appelant
 *   qui décide où l'on retourne.
 */
@Composable
fun ReaderScreen(
    modifier: Modifier = Modifier,
    initialPage: Int = 1,
    onClose: () -> Unit = {},
) {
    val colors = AppTheme.colors
    val totalPages = remember { Quran.pages.size.takeIf { it > 0 } ?: DEFAULT_TOTAL_PAGES }

    // Des **objets d'état**, et non des valeurs : le gestionnaire de gestes est installé une
    // seule fois et relit ces états à chaque événement. Capturer les valeurs le figerait sur
    // l'état du premier rendu, et la page ne tournerait plus qu'une fois.
    val pageState = rememberSaveable { mutableIntStateOf(initialPage.coerceIn(1, totalPages)) }
    val zoomState = remember { mutableStateOf(ReaderZoom()) }
    val chromeState = rememberSaveable { mutableStateOf(true) }
    val verseState = remember { mutableStateOf<Int?>(null) }

    val page by pageState
    val zoom = zoomState.value
    val chromeVisible = chromeState.value
    val selectedVerse = verseState.value

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceSecondary)
            // Barres système, découpes d'écran et barre de gestes : la page est centrée dans
            // l'espace **sûr**, jamais dessous. Le fond, lui, va jusqu'aux bords de l'écran.
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val availableWidth = constraints.maxWidth.toFloat()
            val availableHeight = constraints.maxHeight.toFloat()

            val fitted = ReaderLayout.fitMushafPage(
                availableWidth.toDouble(),
                availableHeight.toDouble(),
            )
            val pageWidth = fitted.width.toFloat()
            val pageHeight = fitted.height.toFloat()

            PreloadMushafPages(
                page = page,
                widthPx = pageWidth.roundToInt(),
                heightPx = pageHeight.roundToInt(),
            )

            val rows = remember(page) { ReaderData.pageRows(page) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Rejoué quand la géométrie change : les coordonnées d'appui en
                    // dépendent, et une rotation doit les recalculer.
                    .pointerInput(pageWidth, pageHeight) {
                        readerGesture(
                            zoomed = { zoomState.value.scale > ReaderGesture.ZOOMED_THRESHOLD },
                            onTransform = { centroid, pan, factor ->
                                val current = zoomState.value
                                val anchored = ReaderZoomGeometry.zoomAt(
                                    current = current,
                                    scale = current.scale * factor,
                                    anchorX = centroid.x,
                                    anchorY = centroid.y,
                                    width = pageWidth,
                                    height = pageHeight,
                                )
                                zoomState.value = ReaderZoomGeometry.constrain(
                                    scale = anchored.scale,
                                    x = anchored.x + pan.x,
                                    y = anchored.y + pan.y,
                                    width = pageWidth,
                                    height = pageHeight,
                                )
                            },
                            onPan = { delta ->
                                val current = zoomState.value
                                zoomState.value = ReaderZoomGeometry.constrain(
                                    scale = current.scale,
                                    x = current.x + delta.x,
                                    y = current.y + delta.y,
                                    width = pageWidth,
                                    height = pageHeight,
                                )
                            },
                            onTap = {
                                // Une fiche de verset ouverte se ferme au premier appui :
                                // c'est ce que la fiche annonce, et un appui qui ne ferait
                                // que masquer la coquille laisserait la fiche en place.
                                if (verseState.value != null) {
                                    verseState.value = null
                                } else {
                                    chromeState.value = !chromeState.value
                                }
                            },
                            onLongPress = { position ->
                                // Le doigt est dans le repère de l'écran, le verset se cherche
                                // dans celui de la page. Il faut donc retirer le zoom **et** le
                                // centrage, sinon un appui sur une page agrandie désignerait le
                                // mauvais verset — et la fiche afficherait une autre traduction.
                                val current = zoomState.value
                                val unzoomedX = (position.x - current.x) / current.scale
                                val unzoomedY = (position.y - current.y) / current.scale
                                verseState.value = ReaderData.verseAtImagePoint(
                                    rows = rows,
                                    x = (unzoomedX - (availableWidth - pageWidth) / 2f).toDouble(),
                                    y = (unzoomedY - (availableHeight - pageHeight) / 2f).toDouble(),
                                    width = pageWidth.toDouble(),
                                    height = pageHeight.toDouble(),
                                )
                            },
                            onSwipe = { dx, dy ->
                                // Le seuil, le rapport et la borne viennent du domaine.
                                val current = pageState.intValue
                                val next = PageNavigation.pageAfterSwipe(current, dx, dy, totalPages)
                                if (next != current) {
                                    pageState.intValue = next
                                    // Changer de page remet le zoom à la page entière :
                                    // garder l'agrandissement d'une autre page n'aurait aucun
                                    // sens et donnerait l'impression d'un lecteur perdu.
                                    zoomState.value = ReaderZoom()
                                    verseState.value = null
                                }
                            },
                        )
                    },
            ) {
                MushafPageView(
                    page = page,
                    zoom = zoom,
                    availableWidth = availableWidth,
                    availableHeight = availableHeight,
                    selectedVerse = selectedVerse,
                    bookmarkIds = emptySet(),
                    difficultIds = emptySet(),
                )
            }
        }

        selectedVerse?.let { verseId ->
            VerseCard(verseId = verseId)
        }

        if (chromeVisible) {
            ReaderChrome(
                page = page,
                totalPages = totalPages,
                surahName = surahNameFor(page),
                zoomed = zoom.scale > ReaderGesture.ZOOMED_THRESHOLD,
                onPage = { target ->
                    pageState.intValue = target.coerceIn(1, totalPages)
                    zoomState.value = ReaderZoom()
                    verseState.value = null
                },
                onClose = onClose,
                onResetZoom = { zoomState.value = ReaderZoom() },
            )
        }
    }
}

/**
 * Fiche du verset touché : référence et traduction française du sens.
 *
 * Elle apparaît à l'appui long, et se ferme au prochain appui sur la page. Volontairement
 * discrète : elle ne doit pas couvrir la page qu'on est en train de lire.
 */
@Composable
private fun VerseCard(verseId: Int) {
    val colors = AppTheme.colors
    val surah = runCatching { Quran.surahAt(verseId) }.getOrNull()
    val verse = runCatching { Quran.verseAt(verseId) }.getOrNull()
    val translation = remember(verseId) { ReaderData.frenchVerse(verseId) }

    AppCard(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = listOfNotNull(surah?.name, verse?.let { "verset ${it.ayah}" }).joinToString(" · "),
                fontSize = AppTheme.typeScale.secondary,
                color = colors.gold,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = translation?.translation ?: "Traduction indisponible pour ce verset.",
                fontSize = AppTheme.typeScale.body,
                color = colors.text,
            )
            translation?.footnotes?.takeIf { it.isNotBlank() }?.let { footnote ->
                Text(
                    text = footnote,
                    fontSize = AppTheme.typeScale.metadata,
                    color = colors.muted,
                )
            }
            Text(
                text = "Toucher la page pour fermer",
                fontSize = AppTheme.typeScale.metadata,
                color = colors.muted,
            )
        }
    }
}

/** Nom de la sourate au début d'une page : c'est ce qu'on cherche en tournant une page. */
private fun surahNameFor(page: Int): String = runCatching {
    Quran.surahAt(Quran.pageRange(page).start).name
}.getOrElse { "Le Coran" }
