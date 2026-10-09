package com.msoumaya.deepseekandroid.feature.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Notifications
// L'alias n'est pas une coquetterie : `androidx.compose.foundation.Image` affiche une image, et
// `Icons.Outlined.Image` en est une. Sans alias, l'import d'une propriété d'extension écraserait
// l'autre, et l'un des deux usages ne compilerait pas.
import androidx.compose.material.icons.outlined.Image as ImageIcon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msoumaya.deepseekandroid.core.data.LocalAppContainer
import com.msoumaya.deepseekandroid.core.data.repository.ProblemReportAttachment
import com.msoumaya.deepseekandroid.core.data.repository.ProblemReportRepository
import com.msoumaya.deepseekandroid.core.design.component.AppButton
import com.msoumaya.deepseekandroid.core.design.component.AppCard
import com.msoumaya.deepseekandroid.core.design.component.AppField
import com.msoumaya.deepseekandroid.core.design.component.AppHeading
import com.msoumaya.deepseekandroid.core.design.component.AppInlineIcon
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.ProblemReportText
import com.msoumaya.deepseekandroid.core.model.ProblemReportType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Écran de signalement de problème
// ---------------------------------------------------------------------------
// Portage de `ProblemReportCard` et `ProblemReportSheet` (`src/ui/ProblemReport.tsx`).
//
// ## Ce que ce fichier fait, et ce qu'il ne fait pas
//
// Il ne **décide** rien : les mots viennent de `ProblemReportText`, les bornes du domaine, le
// choix des libellés de `ProblemReportRenderer`, et l'état d'envoi du dépôt. Il ne fait que poser.
//
// ## Trois différences avec le client d'origine, et pourquoi
//
//   1. **La feuille est un `Dialog`**, comme celles du lecteur, et non un `Modal` de React Native.
//      Le résultat est le même — un panneau par-dessus l'écran, refermable —, mais la géométrie
//      est celle du portage : rayon 30 du thème, poignée, ombre. L'original pose 28, une poignée
//      de 42 × 5 et un fond `#0006` ; ces valeurs-là sont conservées ici parce qu'elles
//      appartiennent à cet écran, et non au thème.
//
//   2. **Le fond de la feuille est un appui qui referme**, et il ne referme pas pendant un envoi.
//      C'est `if(!busy)onClose()` de l'original, écrit aux deux endroits où il l'écrit — le fond
//      et le bouton de fermeture.
//
//   3. **Les cinq natures s'enroulent** (`FlowRow`), au lieu d'être cinq lignes empilées. C'est le
//      `flexWrap: wrap` de l'original : sur un téléphone étroit, les cinq pastilles tiennent sur
//      deux lignes au lieu de repousser le champ de description hors de l'écran.
//
// ## La capture : trois gestes, trois endroits
//
// Choisir, c'est le sélecteur du système — `ProblemReportScreenshotPicker`, injecté. Copier, c'est
// le magasin, au moment où le signalement est inscrit. Mesurer, c'est le domaine. L'écran ne fait
// que les enchaîner, et c'est pour cela qu'il n'a aucune règle à lui.
// ---------------------------------------------------------------------------

/**
 * La carte « Un problème avec l'application ? », et la feuille qu'elle ouvre.
 *
 * La carte **porte son propre état d'ouverture**, comme l'original (`const [open,setOpen]
 * =useState(false)`), et la feuille n'est composée que lorsqu'elle est ouverte. Ce n'est pas un
 * détail : les `remember` de la feuille renaissent donc vides à chaque ouverture — le texte, la
 * nature et la capture du geste précédent ne sont pas retrouvés. L'état du **dépôt**, lui, ne
 * renaît pas, et c'est [ProblemReportRepository.reset] qui l'efface à l'ouverture.
 *
 * @param repository le dépôt des signalements. Par défaut, celui du conteneur applicatif.
 */
@Composable
fun ProblemReportCard(
    modifier: Modifier = Modifier,
    repository: ProblemReportRepository = LocalAppContainer.current.problemReports,
) {
    val ui = ProblemReportRenderer.render()
    val colors = AppTheme.colors

    var open by remember { mutableStateOf(false) }
    val picker = rememberProblemReportPicker()

    AppCard(
        modifier = modifier.padding(top = CARD_TOP_MARGIN),
        spaced = false,
        onClick = { open = true },
        padding = CARD_PADDING,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(colors.reviewSoft),
                contentAlignment = Alignment.Center,
            ) {
                AppInlineIcon(
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    tint = colors.green,
                    size = 26.dp,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                AppHeading(text = ui.cardTitle, size = 17.sp)
                AppLabel(
                    text = ui.cardSubtitle,
                    modifier = Modifier.padding(top = 5.dp),
                    color = colors.muted,
                    fontSize = 12.sp,
                    selectable = false,
                )
            }

            AppInlineIcon(icon = Icons.Outlined.ChevronRight, tint = colors.green, size = 19.dp)
        }
    }

    if (open) {
        ProblemReportSheet(
            repository = repository,
            picker = picker,
            onClose = { open = false },
        )
    }
}

/**
 * La feuille de signalement.
 *
 * @param repository le dépôt, qui porte l'état d'envoi et l'issue.
 * @param picker le sélecteur de capture, injecté — voir `ProblemReportScreenshotPicker`.
 * @param onClose la fermeture. Elle **n'est pas appelée pendant un envoi** : la feuille elle-même
 *   refuse de se fermer tant que le travail n'est pas fini, parce qu'une feuille refermée en plein
 *   envoi laisserait la personne sans réponse sur un geste qu'elle a fait.
 */
@Composable
private fun ProblemReportSheet(
    repository: ProblemReportRepository,
    picker: ProblemReportScreenshotPicker,
    onClose: () -> Unit,
) {
    val colors = AppTheme.colors
    val state by repository.state.collectAsStateWithLifecycle()

    var type by remember { mutableStateOf(ProblemReportType.all.first()) }
    var description by remember { mutableStateOf("") }
    var attachment by remember { mutableStateOf<ProblemReportAttachment?>(null) }
    var attachNotice by remember { mutableStateOf<String?>(null) }

    val ui = ProblemReportRenderer.render(
        description = description,
        hasAttachment = attachment != null,
        busy = state.busy,
        done = state.done,
        notice = state.notice,
        attachNotice = attachNotice,
    )

    // Ce que le dépôt a gardé du geste précédent est effacé à l'ouverture. Sans cela, une feuille
    // rouverte montrerait la confirmation du signalement d'avant, et personne n'écrirait le
    // suivant.
    LaunchedEffect(Unit) { repository.reset() }

    val scope = rememberCoroutineScope()

    // Trois chemins referment la feuille — le fond, la poignée, le retour système —, et **aucun**
    // ne referme pendant un envoi : une feuille refermée en plein envoi laisserait la personne sans
    // réponse sur un geste qu'elle a fait. La garde est écrite **une fois**, et les trois la
    // reçoivent ; recopiée, l'une des trois finirait par l'oublier.
    val fermer: () -> Unit = { if (!ui.busy) onClose() }

    // Ce que l'original écrit au début de chacun de ses deux gestes (`setNotice('')`) : le message
    // du geste précédent disparaît quand un nouveau commence. Les deux sources sont effacées
    // ensemble, sinon un refus de capture resterait affiché sous un envoi qui a réussi.
    fun nouveauGeste() {
        attachNotice = null
        repository.reset()
    }

    fun attach() {
        if (ui.busy) return
        nouveauGeste()
        scope.launch {
            try {
                val choisie = picker.pick()
                if (choisie != null) attachment = choisie
            } catch (annule: CancellationException) {
                // La feuille a quitté la composition pendant le choix : il n'y a plus personne à
                // qui annoncer quoi que ce soit, et écrire un message après coup serait un
                // mensonge silencieux.
                throw annule
            } catch (refus: Exception) {
                // Le refus porte déjà sa phrase — voir `ProblemReportPickException`. Le repli
                // couvre le cas d'une panne qui n'en porte pas, comme l'original.
                attachNotice = refus.message ?: ProblemReportText.ATTACH_FAILED
            }
        }
    }

    fun send() {
        if (!ui.canSend || ui.done) return
        nouveauGeste()
        scope.launch { repository.send(type, description, attachment) }
    }

    Dialog(
        onDismissRequest = fermer,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val ecran = LocalConfiguration.current.screenHeightDp.dp
        val insets = WindowInsets.safeDrawing.asPaddingValues()

        Box(modifier = Modifier.fillMaxSize()) {
            // Le fond : il assombrit, et il referme. Il ne referme pas pendant un envoi.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Backdrop)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = fermer,
                    ),
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // La hauteur maximale est celle de l'original : l'écran, moins la barre d'état,
                    // moins 12. Sans elle, une feuille longue pousserait le bouton d'envoi hors de
                    // l'écran — donc hors de portée.
                    .heightIn(max = ecran - insets.calculateTopPadding() - SHEET_TOP_MARGIN)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                    ),
                shape = RoundedCornerShape(
                    topStart = SHEET_RADIUS,
                    topEnd = SHEET_RADIUS,
                ),
                color = colors.paper,
                shadowElevation = SHEET_ELEVATION,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = SHEET_PADDING_TOP),
                ) {
                    SheetHandle(onDismiss = fermer, enabled = !ui.busy)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp)
                            .padding(bottom = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                AppHeading(text = ui.sheetTitle, size = 23.sp)
                                AppLabel(
                                    text = ui.sheetSubtitle,
                                    modifier = Modifier.padding(top = 5.dp),
                                    color = colors.muted,
                                    fontSize = 13.sp,
                                    selectable = false,
                                )
                            }
                            SheetCloseButton(
                                onClose = onClose,
                                description = ui.closeLabel,
                                enabled = !ui.busy,
                            )
                        }

                        if (ui.done) {
                            // L'écran de confirmation : la phrase de l'issue, et rien d'autre.
                            AppCard {
                                AppLabel(
                                    text = ui.notice.orEmpty(),
                                    selectable = false,
                                )
                            }
                            AppButton(
                                text = ui.closeLabel,
                                onClick = onClose,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            AppLabel(
                                text = ui.typeLabel,
                                modifier = Modifier.padding(bottom = 9.dp),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                selectable = false,
                            )
                            NaturePills(
                                natures = ui.natures,
                                selected = type,
                                enabled = !ui.busy,
                                onSelect = { type = it },
                            )

                            AppLabel(
                                text = ui.descriptionLabel,
                                modifier = Modifier.padding(bottom = 9.dp),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                selectable = false,
                            )
                            AppField(
                                value = description,
                                onValueChange = { description = it },
                                placeholder = ui.descriptionPlaceholder,
                                enabled = !ui.busy,
                                multiline = true,
                                maxLength = ui.descriptionMax,
                            )
                            AppLabel(
                                text = ui.counter,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                color = colors.muted,
                                fontSize = 11.sp,
                                textAlign = TextAlign.End,
                                selectable = false,
                            )

                            AppCard(
                                modifier = Modifier.fillMaxWidth(),
                                spaced = false,
                                onClick = if (ui.busy) null else ({ attach() }),
                                padding = ATTACH_PADDING,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    // La capture est lue **une fois** dans une valeur locale : un
                                    // `var` délégué à `remember` ne se laisse pas smart-caster, et
                                    // un `!!` ferait tomber l'écran si la garde changeait un jour
                                    // de forme.
                                    val piece = attachment
                                    if (piece != null) {
                                        CaptureThumbnail(
                                            path = piece.path,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(10.dp)),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(15.dp))
                                                .background(colors.surfaceSecondary),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            AppInlineIcon(
                                                icon = Icons.Outlined.ImageIcon,
                                                tint = colors.green,
                                                size = 25.dp,
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        AppHeading(text = ui.attachTitle, size = 16.sp)
                                        AppLabel(
                                            text = ui.attachSubtitle,
                                            modifier = Modifier.padding(top = 3.dp),
                                            color = colors.muted,
                                            fontSize = 12.sp,
                                            selectable = false,
                                        )
                                    }

                                    AppInlineIcon(
                                        icon = Icons.Outlined.ChevronRight,
                                        tint = colors.muted,
                                        size = 18.dp,
                                    )
                                }
                            }

                            if (ui.hasAttachment) {
                                AppButton(
                                    text = ui.removeLabel,
                                    onClick = { attachment = null },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !ui.busy,
                                    secondary = true,
                                    small = true,
                                )
                            }

                            if (ui.notice != null) {
                                AppLabel(
                                    text = ui.notice,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                    color = colors.red,
                                    fontSize = 12.sp,
                                    selectable = false,
                                )
                            }

                            AppButton(
                                text = ui.sendLabel,
                                onClick = { send() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = ui.canSend,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Les cinq natures, en pastilles qui s'enroulent.
 *
 * Chacune porte son icône, comme l'original : la forme du mot ne suffit pas à distinguer « Audio »
 * de « Notification » d'un coup d'œil, et c'est le seul repère non textuel du groupe.
 *
 * `selectable` est employé plutôt que `clickable` : il expose l'état **coché** à l'accessibilité,
 * ce que fait l'`accessibilityState={{selected:…}}` de l'original. Un `clickable` aurait rendu
 * cinq boutons dont aucun ne se dit sélectionné.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NaturePills(
    natures: List<ProblemReportType>,
    selected: ProblemReportType,
    enabled: Boolean,
    onSelect: (ProblemReportType) -> Unit,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(AppTheme.radius.small)

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (nature in natures) {
            val cochee = nature == selected
            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = 46.dp)
                    .clip(shape)
                    .background(if (cochee) colors.reviewSoft else colors.paper)
                    .border(1.dp, if (cochee) colors.green else colors.line, shape)
                    .selectable(
                        selected = cochee,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(nature) },
                    )
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                AppInlineIcon(
                    icon = natureIcon(nature),
                    tint = if (cochee) colors.green else colors.muted,
                    size = 20.dp,
                )
                AppLabel(
                    text = nature.wire,
                    color = if (cochee) colors.green else colors.text,
                    fontSize = 13.sp,
                    selectable = false,
                )
            }
        }
    }
}

/**
 * L'icône d'une nature.
 *
 * Le `when` est **exhaustif**, et c'est ce qui le rend utile : ajouter une sixième valeur à
 * l'énumération fera échouer la compilation ici, au lieu d'afficher une pastille sans icône — ou,
 * pire, de réutiliser celle d'une autre nature.
 *
 * Les noms sont ceux de `src/ui/ProblemReport.tsx` (`bug-outline`, `monitor`, `volume-high`,
 * `bell-outline`, `dots-horizontal`), et l'ordre de l'énumération est celui de l'écran.
 */
private fun natureIcon(nature: ProblemReportType): ImageVector = when (nature) {
    ProblemReportType.BUG -> Icons.Outlined.BugReport
    ProblemReportType.AFFICHAGE -> Icons.Outlined.Monitor
    ProblemReportType.AUDIO -> Icons.AutoMirrored.Outlined.VolumeUp
    ProblemReportType.NOTIFICATION -> Icons.Outlined.Notifications
    ProblemReportType.AUTRE -> Icons.Outlined.MoreHoriz
}

/**
 * La miniature de la capture choisie.
 *
 * **Elle est réduite à la lecture**, et pas seulement affichée petite : une capture d'écran
 * moderne pèse plusieurs milliers de pixels de côté, et la décoder en pleine résolution pour la
 * peindre dans un carré de 44 points consommerait plusieurs mégaoctets de mémoire vive — sur un
 * écran dont c'est précisément la ressource rare. `inSampleSize` divise donc la résolution avant
 * le décodage.
 *
 * Tant que la miniature n'est pas prête — ou si le fichier a disparu —, l'icône de la ligne
 * s'affiche : la ligne ne doit jamais être vide, parce qu'un carré blanc se lirait comme une
 * capture absente.
 */
@Composable
private fun CaptureThumbnail(path: String, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val miniature by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { reduire(path) }
    }

    val peinte = miniature
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (peinte != null) {
            Image(
                bitmap = peinte,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            AppInlineIcon(icon = Icons.Outlined.ImageIcon, tint = colors.green, size = 25.dp)
        }
    }
}

/** Décode la miniature de [path], ou `null` si le fichier n'est pas une image lisible. */
private fun reduire(path: String): ImageBitmap? {
    val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bornes)
    if (bornes.outWidth <= 0 || bornes.outHeight <= 0) return null

    var echantillon = 1
    while (
        bornes.outWidth / (echantillon * 2) >= THUMBNAIL_MAX ||
        bornes.outHeight / (echantillon * 2) >= THUMBNAIL_MAX
    ) {
        echantillon *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = echantillon }
    return BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}

/**
 * La poignée de la feuille : elle se tire vers le bas pour refermer.
 *
 * Une poignée dessinée mais inerte serait un mensonge — elle invite à un geste qui ne ferait rien.
 * Le seuil est celui du portage : un glissement de plus de [DISMISS_DRAG] referme.
 *
 * La conversion en pixels est nécessaire : `dragAmount` est exprimé en pixels physiques, alors que
 * le seuil est en points indépendants de la densité. Comparer l'un à l'autre sans convertir
 * rendrait la fermeture deux à trois fois plus sensible sur un écran dense.
 *
 * @param enabled faux pendant un envoi. Une feuille refermée en plein envoi laisserait la personne
 *   sans réponse sur un geste qu'elle a fait — la même garde que le fond et le retour système.
 */
@Composable
private fun SheetHandle(onDismiss: () -> Unit, enabled: Boolean) {
    val colors = AppTheme.colors
    val seuil = with(LocalDensity.current) { DISMISS_DRAG.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .pointerInput(seuil, enabled) {
                if (!enabled) return@pointerInput
                var glisse = 0f
                detectVerticalDragGestures(
                    onDragStart = { glisse = 0f },
                    onDragEnd = { if (glisse > seuil) onDismiss() },
                    onDragCancel = { glisse = 0f },
                ) { _, quantite -> glisse += quantite }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = HANDLE_WIDTH, height = HANDLE_HEIGHT)
                .clip(RoundedCornerShape(HANDLE_RADIUS))
                .background(colors.muted),
        )
    }
}

/**
 * Le bouton de fermeture de la feuille.
 *
 * @param enabled faux pendant un envoi. Le dessin suit celui des feuilles du lecteur : la teinte
 *   passe au gris doux, et le geste ne part pas. Un bouton grisé qui répondrait quand même serait
 *   pire que pas de bouton.
 */
@Composable
private fun SheetCloseButton(onClose: () -> Unit, description: String, enabled: Boolean) {
    val colors = AppTheme.colors

    Box(
        modifier = Modifier
            .size(CLOSE_SIZE)
            .clip(RoundedCornerShape(CLOSE_SIZE / 2))
            .background(colors.soft)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = description,
            tint = if (enabled) colors.green else colors.mutedLight,
        )
    }
}

/** Le fond de la feuille : noir à 40 %, comme le `#0006` de l'original. */
private val Backdrop = androidx.compose.ui.graphics.Color(0x66000000)

/** Marge au-dessus de la carte, dans l'accueil. L'original pose 8. */
private val CARD_TOP_MARGIN = 8.dp

/** Marge interne de la carte. L'original pose 16. */
private val CARD_PADDING = 16.dp

/** Marge interne de la ligne de capture. L'original pose 13. */
private val ATTACH_PADDING = 13.dp

/** Rayon du haut du panneau. L'original pose 28. */
private val SHEET_RADIUS = 28.dp

/** Marge du haut du panneau, sous la barre d'état. L'original pose 12. */
private val SHEET_TOP_MARGIN = 12.dp

/** Marge du haut, avant la poignée. L'original pose 10. */
private val SHEET_PADDING_TOP = 10.dp

/** L'ombre d'une feuille. L'original pose une élévation de 12. */
private val SHEET_ELEVATION = 12.dp

private val HANDLE_WIDTH = 42.dp
private val HANDLE_HEIGHT = 5.dp
private val HANDLE_RADIUS = 3.dp

/** Le glissement qui referme la feuille, en points. C'est le seuil du portage. */
private val DISMISS_DRAG = 45.dp

private val CLOSE_SIZE = 44.dp

/** Le côté de la miniature décodée. Au-delà, elle serait peinte dans 44 points de toute façon. */
private const val THUMBNAIL_MAX = 128
