package com.msoumaya.deepseekandroid.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme

// ---------------------------------------------------------------------------
// Sélecteur segmenté
// ---------------------------------------------------------------------------
// Portage de `SegmentedControl` (`src/ui/DesignSystem.tsx`).
//
// Le dépôt d'origine dessinait ce contrôle avec des `Pressable` et deux `accessibilityRole` :
// `tablist` sur le conteneur, `tab` sur chaque segment, plus `accessibilityState.selected`.
// C'est repris ici avec `selectableGroup` et `Role.Tab`, sans quoi TalkBack annoncerait trois
// boutons ordinaires là où l'écran présente un choix exclusif — et la position du choix
// (« 2 sur 3 ») serait perdue.
//
// Les valeurs sont celles du composant d'origine : rayon 16 pour le cadre, 13 pour le segment,
// remplissage 3, hauteur minimale 44, et un fond `surfaceSecondary` qui se distingue du fond
// crème de l'écran. La marge verticale de 8 px est **portée par le composant** dans l'original,
// comme celle de `Card` ; elle est conservée ici pour que les trois écrans qui l'utilisent
// gardent le même espacement sans avoir à le répéter.
// ---------------------------------------------------------------------------

/** Rayon du cadre. */
private val FRAME_RADIUS = 16.dp

/** Rayon du segment sélectionné, plus petit que le cadre : c'est ce qui crée l'encoche. */
private val SEGMENT_RADIUS = 13.dp

/** Remplissage du cadre, qui laisse voir sa couleur de fond autour du segment. */
private val FRAME_PADDING = 3.dp

/**
 * Sélecteur à choix exclusif, présenté comme une rangée de segments.
 *
 * @param options libellés, dans l'ordre d'affichage. Aucun n'est vide.
 * @param value option retenue. Une valeur absente de [options] laisse tous les segments
 *   éteints, ce qui est un état affichable — le composant ne choisit pas à la place de l'écran.
 * @param onSelect appelé avec l'option touchée.
 */
@Composable
fun AppSegmentedControl(
    options: List<String>,
    value: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val frame = RoundedCornerShape(FRAME_RADIUS)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(frame)
            .background(colors.surfaceSecondary)
            .border(1.dp, colors.line, frame)
            .padding(FRAME_PADDING)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val selected = option == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(RoundedCornerShape(SEGMENT_RADIUS))
                    .background(if (selected) colors.green else Color.Transparent)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onSelect(option) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        color = if (selected) Color.White else colors.muted,
                        fontSize = 14.sp,
                        fontFamily = AppTheme.fonts.titleFamily,
                    ),
                )
            }
        }
    }
}
