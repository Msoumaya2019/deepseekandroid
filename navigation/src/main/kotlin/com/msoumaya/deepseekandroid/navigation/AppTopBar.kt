package com.msoumaya.deepseekandroid.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msoumaya.deepseekandroid.core.design.component.AppIconButton
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.component.AppProfileButton
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme

// ---------------------------------------------------------------------------
// Barre supérieure
// ---------------------------------------------------------------------------
// Portage de `AppTopNavigation` (`src/ui/Premium.tsx`). Le composant d'origine a deux formes, et
// elles ne partagent presque rien : une forme « écran d'outil » avec un retour et un titre
// centré, et une forme « onglet » avec l'icône du livre, le profil, les réglages et une rangée
// d'onglets. Elles sont réunies ici sous un seul point d'entrée, comme en React Native, pour que
// la correspondance reste lisible ligne à ligne.
//
// Deux mesures relevées à la source, qui ne se devinent pas :
//
//   1. `titleFont()` renvoie la famille **Cormorant-Semibold**, c'est-à-dire la graisse 600 déjà
//      découpée. La forme « retour » ne pose aucun `fontWeight`, et la forme « onglet » pose
//      `fontWeight: '600'` : les deux rendent donc la même graisse. Écrire `Normal` d'un côté et
//      `SemiBold` de l'autre aurait introduit une différence que l'application n'a pas.
//
//   2. Les libellés d'onglet portent `fontWeight: '700'` s'ils sont actifs, `'500'` sinon. Une
//      seule graisse étant enregistrée sous ce nom de famille, les deux se résolvent sur la même
//      découpe : **la graisse ne change rien**, seule la couleur distingue l'onglet actif. Le
//      portage reproduit le rendu, pas l'intention — sinon l'onglet actif paraîtrait plus gras
//      sur Android que dans l'application actuelle.
//
// La rangée d'onglets du haut double la barre du bas : c'est le comportement du dépôt d'origine,
// qui affiche les cinq mêmes destinations aux deux endroits. Reproduit tel quel.
// ---------------------------------------------------------------------------

/**
 * Barre supérieure de l'application.
 *
 * @param title titre affiché, centré si [onBack] est fourni, aligné à gauche sinon.
 * @param onProfile ouverture du profil, bouton de droite.
 * @param onSettings ouverture des réglages, bouton d'extrême droite.
 * @param firstName prénom servant à l'initiale du bouton de profil.
 * @param current onglet actif, ou `null` pour une forme sans rangée d'onglets.
 * @param onSelect appelé avec l'onglet touché. La rangée n'apparaît que si [current] et
 *   [onSelect] sont tous deux fournis, ce qui correspond au `showTabs={!utilityView}` d'origine.
 * @param onBack retour vers l'écran précédent. Fourni, il remplace toute la forme « onglet ».
 */
@Composable
fun AppTopBar(
    title: String,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    firstName: String? = null,
    current: AppDestination? = null,
    onSelect: ((AppDestination) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    if (onBack != null) {
        AppBackTopBar(title = title, onBack = onBack, modifier = modifier)
        return
    }

    val colors = AppTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.paper)
            .bottomDivider(colors.line),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = colors.gold,
                modifier = Modifier.size(26.dp),
            )
            AppLabel(
                text = title,
                selectable = false,
                fontSize = 24.sp,
                fontFamily = AppTheme.fonts.titleFamily,
                fontWeight = FontWeight.SemiBold,
                color = colors.green,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            AppProfileButton(firstName = firstName, onClick = onProfile)
            AppIconButton(
                icon = Icons.Outlined.Settings,
                label = "Ouvrir les réglages",
                onClick = onSettings,
            )
        }

        if (current != null && onSelect != null) {
            Row(modifier = Modifier.fillMaxWidth()) {
                AppDestination.entries.forEach { destination ->
                    AppTabLabel(
                        destination = destination,
                        selected = destination == current,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Onglet de la rangée supérieure.
 *
 * Le trait de 3 px sous l'onglet actif est dessiné **à l'intérieur** des 44 px de hauteur, comme
 * `borderBottomWidth` en React Native, et non ajouté en dessous.
 */
@Composable
private fun AppTabLabel(
    destination: AppDestination,
    selected: Boolean,
    onSelect: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clickable(role = Role.Tab) { onSelect(destination) }
            .then(if (selected) Modifier.bottomDivider(colors.green, 3.dp) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        AppLabel(
            text = destination.label,
            selectable = false,
            fontSize = 16.sp,
            fontFamily = AppTheme.fonts.titleFamily,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) colors.green else colors.muted,
            maxLines = 1,
        )
    }
}

/**
 * Forme « écran d'outil » : un retour, un titre centré, et rien d'autre.
 *
 * La cale de 44 px à droite n'est pas décorative : sans elle, le titre centré glisserait d'une
 * demi-largeur de bouton vers la droite.
 */
@Composable
private fun AppBackTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.paper)
            .bottomDivider(colors.line)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.ChevronLeft,
                contentDescription = "Retour",
                tint = colors.green,
                modifier = Modifier.size(24.dp),
            )
        }
        AppLabel(
            text = title,
            selectable = false,
            fontSize = 28.sp,
            fontFamily = AppTheme.fonts.titleFamily,
            fontWeight = FontWeight.SemiBold,
            color = colors.green,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Box(modifier = Modifier.size(44.dp))
    }
}
