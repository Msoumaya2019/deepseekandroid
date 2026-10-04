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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.msoumaya.deepseekandroid.core.audio.AudioSessionController
import com.msoumaya.deepseekandroid.core.audio.ExoAudioOutput
import com.msoumaya.deepseekandroid.core.design.component.AppCard
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.Audio
import com.msoumaya.deepseekandroid.core.domain.AudioSession
import com.msoumaya.deepseekandroid.core.domain.MushafSourceNavigation
import com.msoumaya.deepseekandroid.core.domain.PageNavigation
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.ReaderData
import com.msoumaya.deepseekandroid.core.domain.ReaderGesture
import com.msoumaya.deepseekandroid.core.domain.ReaderLayout
import com.msoumaya.deepseekandroid.core.domain.ReaderZoomGeometry
import com.msoumaya.deepseekandroid.core.model.MushafPageSource
import com.msoumaya.deepseekandroid.core.model.MushafSource
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
 * - **L'écoute de la page**, avec le mini-lecteur décrit dans `MiniPlayer`. La coquille et le
 *   mini-lecteur sont **dans le flux** : ils prennent leur hauteur au lieu de recouvrir la
 *   page, donc le dernier verset reste lisible sans rien faire disparaître.
 * - **Aucun réseau à l'ouverture.** Les 604 pages sont dans l'application : le lecteur s'ouvre
 *   en avion. Seule l'écoute demande une connexion, puisque les versets sont des fichiers
 *   distants — et c'est elle, et elle seule, qui le dit quand elle échoue.
 *
 * ## Ce qui viendra, et où
 *
 * Les réglages d'écoute (récitateur, nombre d'écoutes, silence entre deux écoutes, vitesse)
 * sont portés par `AudioSession` et déjà appliqués par le contrôleur ; il manque l'écran qui
 * les modifie. La coquille d'étude et le mode signet appartiennent à la phase C.
 *
 * @param initialPage page ouverte au lancement. Bornée au moushaf.
 * @param source la source coranique affichée. Elle décide du **découpage** des pages : deux
 *   sources ne placent pas les mêmes versets sur la page 300. Elle ne décide pas des images,
 *   qui viennent de [pages].
 * @param pages où trouver les images. Le lecteur ne le sait pas : il reçoit des chemins déjà
 *   résolus. C'est ce qui lui permet de n'avoir ni stockage ni réseau.
 * @param onClose ferme le lecteur. L'écran ne connaît pas la navigation : c'est l'appelant
 *   qui décide où l'on retourne.
 * @param onPageChanged rapporte la page affichée. L'appelant en a besoin pour changer de
 *   présentation **sans faire perdre sa page** à la personne : c'est la page courante qui est
 *   vérifiée avant d'adopter une autre source.
 * @param onOpenSourcePicker ouvre le choix de présentation. `null` quand l'appelant n'en
 *   propose pas — le bouton est alors absent plutôt que présent et sans effet.
 */
@Composable
fun ReaderScreen(
    modifier: Modifier = Modifier,
    initialPage: Int = 1,
    source: MushafSource = MushafSource.MEDINA,
    pages: MushafPageSource = EmbeddedMushafPages,
    onClose: () -> Unit = {},
    onPageChanged: (Int) -> Unit = {},
    onOpenSourcePicker: (() -> Unit)? = null,
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

    // Le lecteur audio vit aussi longtemps que l'écran : c'est la portée qui décide, et
    // `DisposableEffect` libère le lecteur natif quand on quitte. Sans service d'avant-plan,
    // l'écoute s'arrête en quittant le lecteur — c'est honnête, et ce sera l'affaire des
    // notifications que de la poursuivre.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audio = remember(context) {
        AudioSessionController(ExoAudioOutput(context.applicationContext), scope)
    }
    DisposableEffect(audio) {
        onDispose { audio.release() }
    }
    val audioState by audio.state.collectAsState()

    // Les réglages d'écoute. Ce sont les valeurs du client d'origine ; l'écran qui les modifie
    // viendra avec le reste de la phase B.
    val settings = remember { AudioSession() }
    val reciterName = remember { Audio.defaultReciter.name }
    val countLabel = remember(settings) { countLabelOf(settings) }

    val page by pageState
    val zoom = zoomState.value
    val chromeVisible = chromeState.value
    val selectedVerse = verseState.value

    // La page est rapportée à l'appelant à chaque changement, et non à chaque recomposition :
    // la clé de l'effet est la page elle-même.
    LaunchedEffect(page) { onPageChanged(page) }

    // `null` quand la page n'a pas de plage connue : l'action est alors absente plutôt que
    // présente et sans effet. La plage est celle du **découpage de la source affichée** : lire
    // une page du paquet avec la table du moushaf de Médine ferait commencer l'écoute au
    // mauvais verset.
    val listenAction: (() -> Unit)? = remember(page, settings, source) {
        runCatching { MushafSourceNavigation.pageRange(source, page) }.getOrNull()?.let { range ->
            { audio.start(range, settings) }
        }
    }

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

            // Les images de la page, telles que la source les fournit. `remember` sur la page
            // et sur la source : relire le référentiel à chaque recomposition ferait sauter
            // une image sur deux pendant un balayage.
            val mushafPage = remember(page, pages) { pages.page(page) }

            // Le gestionnaire de gestes est installé **une seule fois** — c'est la raison
            // d'être des objets d'état déclarés plus haut. Il doit donc relire la page à
            // chaque appui long : capturer `mushafPage` directement figerait les rectangles
            // sur la page du premier rendu, et après un balayage l'appui long désignerait un
            // verset de la page précédente — avec une fiche qui paraîtrait juste.
            val currentPage = rememberUpdatedState(mushafPage)

            val fitted = ReaderLayout.fitMushafPage(
                availableWidth = availableWidth.toDouble(),
                availableHeight = availableHeight.toDouble(),
                // Le rapport de la **source affichée** : la page embarquée et celle du paquet
                // n'ont pas le même. Utiliser l'un pour l'autre déformerait la page.
                sourceWidth = mushafPage.sourceWidth,
                sourceHeight = mushafPage.sourceHeight,
            )
            val pageWidth = fitted.width.toFloat()
            val pageHeight = fitted.height.toFloat()

            PreloadMushafPages(
                page = page,
                source = pages,
                widthPx = (pageWidth - ReaderLayout.FRAME).roundToInt(),
                heightPx = (pageHeight - ReaderLayout.FRAME).roundToInt(),
            )

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
                                val shown = currentPage.value
                                verseState.value = ReaderData.verseAtImagePoint(
                                    rows = shown.rows,
                                    x = (unzoomedX - (availableWidth - pageWidth) / 2f).toDouble(),
                                    y = (unzoomedY - (availableHeight - pageHeight) / 2f).toDouble(),
                                    width = pageWidth.toDouble(),
                                    height = pageHeight.toDouble(),
                                    // Les rectangles sont exprimés dans l'espace de la source :
                                    // les rapporter à celui de l'autre source désignerait un
                                    // verset voisin, sur une page pourtant correcte.
                                    sourceWidth = shown.sourceWidth,
                                    sourceHeight = shown.sourceHeight,
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
                    page = mushafPage,
                    zoom = zoom,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                    selectedVerse = selectedVerse,
                    bookmarkIds = emptySet(),
                    difficultIds = emptySet(),
                )
            }
        }

        // Le mini-lecteur prend sa hauteur, comme la coquille : la page reste entière, et le
        // dernier verset ne passe jamais dessous.
        if (audioState.isOpen) {
            MiniPlayer(
                state = audioState,
                reciterName = reciterName,
                countLabel = countLabel,
                onToggle = { audio.toggle() },
                onStop = { audio.close() },
            )
        }

        selectedVerse?.let { verseId ->
            VerseCard(verseId = verseId)
        }

        if (chromeVisible) {
            ReaderChrome(
                page = page,
                totalPages = totalPages,
                surahName = surahNameFor(source, page),
                zoomed = zoom.scale > ReaderGesture.ZOOMED_THRESHOLD,
                onPage = { target ->
                    pageState.intValue = target.coerceIn(1, totalPages)
                    zoomState.value = ReaderZoom()
                    verseState.value = null
                },
                onClose = onClose,
                onResetZoom = { zoomState.value = ReaderZoom() },
                onOpenSourcePicker = onOpenSourcePicker,
                onListen = listenAction,
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

/**
 * Nom de la sourate au début d'une page : c'est ce qu'on cherche en tournant une page.
 *
 * La page est lue dans le découpage de la source affichée : la première page du paquet
 * « Coran 1441 » ne porte pas le même verset que la première page du moushaf de Médine.
 */
private fun surahNameFor(source: MushafSource, page: Int): String = runCatching {
    Quran.surahAt(MushafSourceNavigation.pageRange(source, page).start).name
}.getOrElse { "Le Coran" }
