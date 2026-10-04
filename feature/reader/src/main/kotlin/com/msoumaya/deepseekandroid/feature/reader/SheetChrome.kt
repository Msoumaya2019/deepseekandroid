package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme

/**
 * Ce que toutes les feuilles du lecteur ont en commun : la poignée, la fermeture, et la
 * géométrie qui va avec.
 *
 * ## Pourquoi c'est ici, et non dans une feuille en particulier
 *
 * Les feuilles du lecteur — les options, les réglages d'écoute, et plus tard la traduction —
 * sont censées **se ressembler** : même poignée, même bouton, même rayon, même ombre, même
 * animation d'appui. Deux copies de ces valeurs divergeraient au premier ajustement, et l'écart
 * ne se verrait qu'en passant de l'une à l'autre. Elles sont donc écrites une seule fois.
 *
 * La **géométrie propre à une feuille** — la hauteur d'une ligne, la taille d'une pastille —
 * reste dans la feuille concernée : elle ne se partage pas.
 */

/**
 * La poignée : elle se tire vers le bas pour refermer.
 *
 * Une poignée dessinée mais inerte serait un mensonge — elle invite à un geste qui ne ferait
 * rien. Le seuil est celui du client d'origine : un glissement de plus de [DISMISS_DRAG] referme.
 *
 * La conversion en pixels est nécessaire : `dragAmount` est exprimé en pixels physiques, alors
 * que le seuil de l'original est en points indépendants de la densité. Comparer l'un à l'autre
 * sans convertir rendrait la fermeture deux à trois fois plus sensible sur un écran dense.
 */
@Composable
internal fun SheetHandle(onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    val threshold = with(LocalDensity.current) { DISMISS_DRAG.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .pointerInput(threshold) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (dragged > threshold) onDismiss() },
                    onDragCancel = { dragged = 0f },
                ) { _, amount -> dragged += amount }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(HANDLE_WIDTH)
                .height(HANDLE_HEIGHT)
                .clip(RoundedCornerShape(HANDLE_RADIUS))
                .background(colors.softBorder),
        )
    }
}

/**
 * Le bouton de fermeture : un rond de [CLOSE_SIZE] points, teinté du fond doux.
 *
 * La description est un **paramètre**, et non une constante : chaque feuille annonce ce qu'elle
 * ferme. Un lecteur d'écran qui entend « Fermer les options » sur l'écran des réglages d'écoute
 * ferait douter de l'endroit où l'on se trouve.
 */
@Composable
internal fun SheetCloseButton(onClose: () -> Unit, description: String) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .size(CLOSE_SIZE)
            .alpha(if (pressed) PRESSED_ALPHA else 1f)
            .clip(RoundedCornerShape(CLOSE_SIZE / 2))
            .background(colors.soft)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = description,
            tint = colors.green,
        )
    }
}

/** Le glissement qui referme une feuille, en points. C'est le seuil du client d'origine. */
internal val DISMISS_DRAG = 45.dp

/** L'ombre d'une feuille. L'original pose une élévation de 12. */
internal val SHEET_ELEVATION = 12.dp

/** Le remplissage intérieur d'une feuille. L'original pose 14. */
internal val SHEET_PADDING = 14.dp

internal val HANDLE_WIDTH = 36.dp
internal val HANDLE_HEIGHT = 4.dp
internal val HANDLE_RADIUS = 4.dp

internal val CLOSE_SIZE = 44.dp

/** L'opacité d'un élément à l'appui. C'est celle du client d'origine. */
internal const val PRESSED_ALPHA = 0.72f
