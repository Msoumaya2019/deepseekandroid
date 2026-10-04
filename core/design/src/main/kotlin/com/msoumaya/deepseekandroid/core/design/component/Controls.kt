package com.msoumaya.deepseekandroid.core.design.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme

// ---------------------------------------------------------------------------
// Commandes
// ---------------------------------------------------------------------------
// Portage de `Button`, `Choice` et `CheckChoice` de `src/ui/theme.tsx`.
//
// Deux choix de portage à connaître :
//
//  1. `Button` est redessiné plutôt que remplacé par un `Button` Material. Le dépôt d'origine
//     impose une hauteur minimale de 44, un rayon de 15 et une variante secondaire bordée que
//     Material n'offre pas telle quelle ; passer par un `Box` cliquable garde la géométrie
//     exacte et récupère tout de même l'ondulation et le rôle d'accessibilité.
//  2. `Choice` et `CheckChoice` utilisent en revanche les vrais `RadioButton` et `Checkbox`
//     Material. React Native dessinait des glyphes texte faute de mieux ; sur Android, les
//     composants natifs apportent l'état vocalisé par TalkBack et une zone tactile de 48 px
//     autour d'un pictogramme de 20 px — ce que le cahier des charges demande explicitement.
// ---------------------------------------------------------------------------

/**
 * Bouton principal ou secondaire.
 *
 * Géométrie reprise du dépôt d'origine : hauteur minimale 44, rayon 15, marge verticale 5,
 * remplissage 18/15 en taille normale et 14/10 en taille réduite, opacité 0,85 à l'appui et
 * 0,45 désactivé. Les tailles de texte 15 et 13 étaient en dur dans le composant d'origine et
 * ne figurent pas dans `tokens.ts` : elles sont conservées telles quelles.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    enabled: Boolean = true,
    small: Boolean = false,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(AppTheme.radius.button)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val dim = when {
        !enabled -> 0.45f
        pressed -> 0.85f
        else -> 1f
    }

    Box(
        modifier = modifier
            .padding(vertical = 5.dp)
            .defaultMinSize(minHeight = 44.dp)
            .alpha(dim)
            .clip(shape)
            .background(if (secondary) colors.soft else colors.green)
            .then(
                if (secondary) Modifier.border(1.dp, colors.softBorder, shape) else Modifier,
            )
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(
                horizontal = if (small) 14.dp else 18.dp,
                vertical = if (small) 10.dp else 15.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = TextStyle(
                color = if (secondary) colors.green else Color.White,
                fontSize = if (small) 13.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = AppTheme.fonts.interfaceFamily,
            ),
        )
    }
}

/**
 * Choix unique, présenté comme une ligne bordée.
 *
 * Remplace `Choice`. La ligne entière est cliquable, pas seulement le bouton radio.
 */
@Composable
fun AppChoice(
    label: String,
    selected: Boolean,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(AppTheme.radius.small)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(if (selected) colors.selected else colors.paper)
            .border(1.dp, if (selected) colors.green2 else colors.line, shape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onPress,
            )
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AppLabel(
                text = label,
                selectable = false,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
            if (subtitle != null) {
                AppLabel(
                    text = subtitle,
                    selectable = false,
                    fontSize = AppTheme.typeScale.secondary,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.green2,
                unselectedColor = colors.muted,
            ),
        )
    }
}

/**
 * Choix multiple.
 *
 * Remplace `CheckChoice`. Même géométrie que `AppChoice`, avec une case à cocher native.
 */
@Composable
fun AppCheckChoice(
    label: String,
    selected: Boolean,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(AppTheme.radius.small)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(if (selected) colors.selected else colors.paper)
            .border(1.dp, if (selected) colors.green else colors.line, shape)
            .toggleable(
                value = selected,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onPress() },
            )
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = null,
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = colors.green,
                checkmarkColor = Color.White,
                uncheckedColor = colors.muted,
            ),
        )
        Column(modifier = Modifier.weight(1f)) {
            AppLabel(
                text = label,
                selectable = false,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
            if (subtitle != null) {
                AppLabel(
                    text = subtitle,
                    selectable = false,
                    fontSize = AppTheme.typeScale.secondary,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}
