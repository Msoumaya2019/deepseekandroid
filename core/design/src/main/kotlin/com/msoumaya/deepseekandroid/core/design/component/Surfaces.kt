package com.msoumaya.deepseekandroid.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.design.theme.CardShadow

// ---------------------------------------------------------------------------
// Surfaces
// ---------------------------------------------------------------------------

/**
 * Ombre portée d'une carte.
 *
 * L'élévation seule donne une ombre grise générique ; React Native teintait la sienne avec la
 * couleur du texte. `ambientColor` et `spotColor` permettent de retrouver ce liseré chaud
 * plutôt qu'un gris neutre.
 */
fun Modifier.cardShadow(shadow: CardShadow, shape: Shape): Modifier = this.shadow(
    elevation = shadow.elevation,
    shape = shape,
    ambientColor = shadow.color,
    spotColor = shadow.color,
)

/**
 * Carte de contenu.
 *
 * Remplace `Card`. Reproduit : fond `paper`, rayon 20, marge interne 16, bordure 1 px couleur
 * `line`, ombre douce.
 *
 * Une différence assumée avec le dépôt d'origine : la marge basse de 12 px y était posée par le
 * composant lui-même. En Compose, l'espacement se gère d'habitude chez le parent
 * (`Arrangement.spacedBy`). Le garder ici par défaut (`spaced = true`) évite qu'une carte
 * oubliée colle à la suivante sur une vingtaine d'écrans portés ; `spaced = false` rend la main
 * au parent quand un écran veut maîtriser ses écarts.
 *
 * Le **fond** est facultatif et vaut `paper`, comme partout. Il sert à poser une carte
 * d'explication sur le fond doux du thème — l'usage que le dépôt d'origine fait de
 * `backgroundColor: colors.soft` sur une carte —, sans changer ni la forme ni l'ombre :
 * une carte qui change de fond reste une carte.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    spaced: Boolean = true,
    bottomSpacing: Dp = AppTheme.spacing.md,
    onClick: (() -> Unit)? = null,
    background: Color = AppTheme.colors.paper,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    val shape = RoundedCornerShape(AppTheme.radius.card)

    val surface = Modifier
        .then(if (spaced) Modifier.padding(bottom = bottomSpacing) else Modifier)
        .cardShadow(AppTheme.shadow, shape)
        .background(background, shape)
        .border(1.dp, colors.line, shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(role = Role.Button, onClick = onClick)
            } else {
                Modifier
            },
        )

    Column(
        modifier = modifier.then(surface).padding(spacing.lg),
        content = content,
    )
}
