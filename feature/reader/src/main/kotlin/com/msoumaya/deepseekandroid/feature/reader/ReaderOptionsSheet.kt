package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.ReaderOptionsText

/**
 * La feuille « Plus d'options » du lecteur.
 *
 * Portée depuis `src/ui/ReaderMoreSheet.tsx`. Elle est le **carrefour** du lecteur : changer de
 * sourate, afficher la traduction, régler l'écoute, choisir la présentation. Le bouton « ⋯ » de
 * la coquille l'ouvre.
 *
 * ## Une destination qui n'existe pas n'apparaît pas
 *
 * Chaque destination est un paramètre **facultatif**. Ce qui n'est pas branché n'est pas
 * affiché, et non grisé : une ligne grisée laisse croire que l'écran existe mais qu'il est
 * momentanément indisponible. La décision appartient à [ReaderOptionsText.visible], éprouvée
 * dans `core:domain` — ici, on ne fait que lui passer les destinations reçues.
 *
 * ## Écart assumé avec l'original : le voile
 *
 * Le client d'origine dessine son propre voile (`rgba(30,24,47,0.12)`) et pose la feuille
 * au-dessus de sa barre d'actions flottante. Ici, la feuille est rendue dans une fenêtre de
 * dialogue : le voile vient de la plateforme, et la feuille s'ancre au bas de l'écran. La
 * raison est que **notre coquille est dans le flux** — elle prend sa hauteur au lieu de
 * recouvrir la page — donc « au-dessus de la barre » n'a plus de sens. Ancrer en bas est
 * l'équivalent Android, et le voile de la plateforme évite d'en empiler deux.
 *
 * @param onClose referme la feuille. Le client d'origine referme aussi en la tirant vers le
 *   bas : la poignée le fait ici, avec le même seuil de 45 points.
 * @param onSurah ouvre le sélecteur de sourate. `null` quand il n'y a nulle part où aller.
 * @param onTranslation affiche la traduction française. `null` tant que l'écran n'existe pas.
 * @param onAudio ouvre les réglages d'écoute. `null` quand l'appelant n'en propose pas.
 * @param onDisplay ouvre le choix de présentation. `null` quand l'appelant n'en propose pas.
 */
@Composable
internal fun ReaderOptionsSheet(
    onClose: () -> Unit,
    onSurah: (() -> Unit)? = null,
    onTranslation: (() -> Unit)? = null,
    onAudio: (() -> Unit)? = null,
    onDisplay: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors

    // Les destinations réellement branchées, et elles seules.
    val destinations: Map<ReaderOptionsText.Action, () -> Unit> = buildMap {
        onSurah?.let { put(ReaderOptionsText.Action.SURAH, it) }
        onTranslation?.let { put(ReaderOptionsText.Action.TRANSLATION, it) }
        onAudio?.let { put(ReaderOptionsText.Action.AUDIO, it) }
        onDisplay?.let { put(ReaderOptionsText.Action.DISPLAY, it) }
    }

    // Rien à proposer : la feuille ne s'ouvre pas du tout. Se réduire à un titre et à une
    // poignée serait une impasse — et c'est à l'appelant de ne pas proposer le bouton « ⋯ »
    // dans ce cas, ce que le lecteur fait déjà.
    if (!ReaderOptionsText.isUseful(destinations.keys)) return

    Dialog(
        onDismissRequest = onClose,
        // Sans cela, la feuille se limite à la largeur d'un téléphone en paysage et flotte au
        // milieu de l'écran.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // La zone au-dessus de la feuille : le toucher referme. Volontairement **sans
            // couleur** — la fenêtre de dialogue pose déjà son voile, et en ajouter un second
            // donnerait un fond deux fois plus sombre que celui de l'original.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    ),
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // Le bas de la feuille doit rester au-dessus de la barre de gestes : c'est
                    // le seul bord qui compte, la feuille étant ancrée en bas.
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                    ),
                shape = RoundedCornerShape(
                    topStart = AppTheme.radius.sheet,
                    topEnd = AppTheme.radius.sheet,
                ),
                color = colors.paper,
                shadowElevation = SHEET_ELEVATION,
            ) {
                // Le défilement n'est utile qu'en paysage, où quatre lignes ne tiennent pas :
                // la feuille est alors bornée par l'écran et défile, au lieu d'être coupée.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(SHEET_PADDING),
                ) {
                    SheetHandle(onDismiss = onClose)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppLabel(
                            text = ReaderOptionsText.TITLE,
                            modifier = Modifier.weight(1f),
                            // 21 et non 20 : le client d'origine pose cette taille à la main et
                            // `tokens.ts` n'offre pas de jeton à 20. `section` est le plus proche,
                            // et l'écart est d'un point.
                            fontSize = AppTheme.typeScale.section,
                            fontWeight = FontWeight.ExtraBold,
                            selectable = false,
                        )

                        SheetCloseButton(
                            onClose = onClose,
                            description = ReaderOptionsText.CLOSE,
                        )
                    }

                    for (row in ReaderOptionsText.visible(destinations.keys)) {
                        OptionsRow(row = row, onClick = destinations.getValue(row.action))
                    }
                }
            }
        }
    }
}

/**
 * Une ligne : une pastille teintée, un titre, une précision, et un chevron.
 *
 * L'icône ne porte pas de description : le titre de la ligne dit déjà ce que fait le bouton, et
 * l'annoncer deux fois ferait répéter TalkBack.
 */
@Composable
private fun OptionsRow(row: ReaderOptionsText.Row, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val accent = rowAccent(row.action)
    val shape = RoundedCornerShape(ROW_RADIUS)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = ROW_GAP)
            .alpha(if (pressed) PRESSED_ALPHA else 1f)
            .clip(shape)
            .background(accent.background)
            .border(1.dp, colors.line, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = ROW_MIN_HEIGHT)
            .padding(ROW_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(BADGE_SIZE)
                .clip(RoundedCornerShape(BADGE_RADIUS))
                .background(accent.badge),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = accent.icon,
                contentDescription = null,
                tint = accent.foreground,
                modifier = Modifier.size(ICON_SIZE),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            AppLabel(
                text = row.title,
                fontSize = AppTheme.typeScale.body,
                fontWeight = FontWeight.Bold,
                selectable = false,
            )
            AppLabel(
                text = row.subtitle,
                modifier = Modifier.padding(top = 3.dp),
                fontSize = AppTheme.typeScale.metadata,
                color = colors.muted,
                selectable = false,
            )
        }

        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = colors.green2,
            modifier = Modifier.size(CHEVRON_SIZE),
        )
    }
}

/** Les quatre teintes d'une ligne : icône, fond, pastille, et la couleur de l'icône. */
private data class RowAccent(
    val icon: ImageVector,
    val background: Color,
    val badge: Color,
    val foreground: Color,
)

/**
 * Les teintes des lignes, relevées dans `src/ui/ReaderMoreSheet.tsx`.
 *
 * Elles ne viennent **pas** de `tokens.ts` : le client d'origine les pose à la main sur ces
 * quatre lignes et elles ne servent nulle part ailleurs. Les remonter dans le thème les ferait
 * passer pour réutilisables alors qu'elles ne le sont pas.
 */
private fun rowAccent(action: ReaderOptionsText.Action): RowAccent = when (action) {
    ReaderOptionsText.Action.SURAH -> RowAccent(
        icon = Icons.AutoMirrored.Outlined.MenuBook,
        background = Color(0xFFF2EEFB),
        badge = Color(0xFFE4DAFF),
        foreground = Color(0xFF7356AD),
    )

    ReaderOptionsText.Action.TRANSLATION -> RowAccent(
        icon = Icons.Outlined.Public,
        background = Color(0xFFEDF4FC),
        badge = Color(0xFFD7E7FF),
        foreground = Color(0xFF427FBC),
    )

    ReaderOptionsText.Action.AUDIO -> RowAccent(
        icon = Icons.Outlined.Headphones,
        background = Color(0xFFFFF0F4),
        badge = Color(0xFFFFDFE8),
        foreground = Color(0xFFBD5B7B),
    )

    ReaderOptionsText.Action.DISPLAY -> RowAccent(
        icon = Icons.Outlined.Settings,
        background = Color(0xFFEEF8F5),
        badge = Color(0xFFD7EEE6),
        foreground = Color(0xFF478F7C),
    )
}

/** Une ligne fait au moins 66 points de haut, comme dans l'original. */
private val ROW_MIN_HEIGHT = 66.dp
private val ROW_RADIUS = 18.dp
private val ROW_GAP = 8.dp
private val ROW_PADDING = 10.dp

private val BADGE_SIZE = 40.dp
private val BADGE_RADIUS = 13.dp
private val ICON_SIZE = 23.dp
private val CHEVRON_SIZE = 20.dp
