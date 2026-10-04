package com.msoumaya.deepseekandroid.navigation

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// Traits de séparation
// ---------------------------------------------------------------------------
// `Modifier.border` dessine un cadre complet. Les deux barres de navigation du dépôt d'origine
// ne portent qu'un seul trait — `borderTopWidth` pour celle du bas, `borderBottomWidth` pour
// celle du haut — et ce trait est **à l'intérieur** de la boîte, comme toutes les bordures
// React Native. `drawBehind` + `drawRect` reproduit exactement cela, sans consommer de place
// dans la mise en page.
//
// Ces deux modificateurs sont partagés par `AppBottomBar` et `AppTopBar` : c'est la même règle
// de dessin aux deux endroits, et la dupliquer aurait laissé deux versions divergentes.
// ---------------------------------------------------------------------------

/** Trait horizontal de [thickness] en haut du composant. */
internal fun Modifier.topDivider(color: Color, thickness: Dp = 1.dp): Modifier = drawBehind {
    drawRect(
        color = color,
        topLeft = Offset.Zero,
        size = Size(size.width, thickness.toPx()),
    )
}

/** Trait horizontal de [thickness] en bas du composant. */
internal fun Modifier.bottomDivider(color: Color, thickness: Dp = 1.dp): Modifier = drawBehind {
    val hauteur = thickness.toPx()
    drawRect(
        color = color,
        topLeft = Offset(0f, size.height - hauteur),
        size = Size(size.width, hauteur),
    )
}
