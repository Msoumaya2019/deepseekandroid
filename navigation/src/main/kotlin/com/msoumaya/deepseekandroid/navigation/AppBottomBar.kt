package com.msoumaya.deepseekandroid.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme

// ---------------------------------------------------------------------------
// Barre d'onglets basse
// ---------------------------------------------------------------------------
// Portage de `BottomNavigation` (`src/ui/Premium.tsx`), au détail près :
//
//   conteneur : ligne, padding haut 5, padding bas 3, bordure haute 1 px `line`, fond `paper`
//   élément   : largeur égale, hauteur minimale 56, contenu centré, espace 3
//   icône     : 23 px, couleur `green` si actif, `muted` sinon
//   libellé   : 10 px, même règle de couleur
//   pastille  : 4 x 4, rayon 2, `green` si actif, transparent sinon
//
// La pastille sous le libellé est le repère d'onglet actif de l'application d'origine : elle
// n'est pas décorative, c'est le second indicateur après la couleur — un utilisateur qui
// distingue mal les couleurs s'y retrouve par sa position, et TalkBack par le rôle `Tab`.
//
// La barre absorbe elle-même l'encoche de navigation gestuelle : c'est le seul élément en bas
// de l'écran, et personne d'autre ne doit consommer cette marge, sinon on obtient une double
// bande.
// ---------------------------------------------------------------------------

/**
 * Barre d'onglets des cinq destinations principales.
 *
 * @param current destination sélectionnée, ou `null` si l'écran courant n'est pas un onglet.
 * @param onSelect appelé avec la destination touchée.
 */
@Composable
fun AppBottomBar(
    current: AppDestination?,
    onSelect: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.paper)
            .topDivider(colors.line)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = 5.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppDestination.entries.forEach { destination ->
            val selected = destination == current
            val teinte = if (selected) colors.green else colors.muted

            Column(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 56.dp)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onSelect(destination) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                // Contenu centré verticalement dans les 56 px, comme le
                // `justifyContent: 'center'` du composant d'origine.
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = null,
                    tint = teinte,
                    modifier = Modifier.size(23.dp),
                )
                Text(
                    text = destination.label,
                    style = TextStyle(
                        color = teinte,
                        fontSize = 10.sp,
                        fontFamily = AppTheme.fonts.interfaceFamily,
                    ),
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (selected) colors.green else Color.Transparent),
                )
            }
        }
    }
}
