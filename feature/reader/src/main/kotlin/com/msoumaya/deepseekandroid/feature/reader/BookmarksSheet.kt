package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.msoumaya.deepseekandroid.core.design.component.AppButton
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.BookmarksText

/**
 * Le panneau « Marques-pages » du lecteur.
 *
 * Porté depuis le panneau `sessionPanel === 'bookmarks'` de `src/App.tsx`. Il propose **deux**
 * choses, et rien d'autre : poser un signet sur un verset, ou ouvrir la liste des signets
 * enregistrés.
 *
 * ## Pourquoi le mode de pose passe par ici
 *
 * Le client d'origine n'a **aucune** action directe qui entre en mode de pose : le bouton
 * « Marque-page » de la coquille ouvre ce panneau, et c'est la première entrée qui arme le
 * geste. Ce détour n'est pas une lourdeur — c'est ce qui empêche qu'un appui malencontreux sur
 * la coquille fasse basculer le lecteur en mode de pose, où un appui sur la page n'a plus le
 * même sens.
 *
 * ## Une entrée qui ne mène nulle part n'est pas affichée
 *
 * Les deux destinations sont des paramètres **facultatifs**, et la décision appartient à
 * [BookmarksText.panelRows], éprouvée dans `core:domain` — même règle, et même raison, que la
 * feuille d'options : une entrée grisée laisserait croire que l'écran existe. Tant que l'écran
 * de la liste n'est pas écrit, le panneau ne propose donc que la pose.
 *
 * ## Écart assumé : les deux entrées sont deux boutons, pas une liste
 *
 * L'original empile un bouton plein et un bouton secondaire. Ici, ce sont deux `AppButton` dans
 * le même ordre, le second en style secondaire. Le rendu est celui du reste de l'application
 * plutôt que celui d'un écran isolé — l'important étant que **poser** soit le geste mis en
 * avant, et **consulter** le second.
 *
 * @param onClose referme le panneau, y compris en tirant la poignée vers le bas.
 * @param onPlace arme le mode de pose. `null` quand le lecteur ne sait pas enregistrer de
 *   signet — l'entrée disparaît alors au lieu de rester sans effet.
 * @param onOpenList ouvre la liste des signets. `null` quand cet écran n'existe pas encore.
 */
@Composable
internal fun BookmarksSheet(
    onClose: () -> Unit,
    onPlace: (() -> Unit)? = null,
    onOpenList: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors

    val destinations: Map<BookmarksText.PanelAction, () -> Unit> = buildMap {
        onPlace?.let { put(BookmarksText.PanelAction.PLACE, it) }
        onOpenList?.let { put(BookmarksText.PanelAction.OPEN_LIST, it) }
    }

    // Rien à proposer : le panneau ne s'ouvre pas du tout. Se réduire à un titre et à une
    // poignée serait une impasse — et c'est à l'appelant de ne pas proposer le bouton dans ce
    // cas, ce que le lecteur fait déjà par `isPanelUseful`.
    if (!BookmarksText.isPanelUseful(destinations.keys)) return

    Dialog(
        onDismissRequest = onClose,
        // Sans cela, le panneau se limite à la largeur d'un téléphone en paysage.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // La zone au-dessus du panneau : le toucher referme. Sans couleur — la fenêtre de
            // dialogue pose déjà son voile, et en ajouter un second assombrirait deux fois.
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
                    // Le bas du panneau doit rester au-dessus de la barre de gestes : c'est le
                    // seul bord qui compte, le panneau étant ancré en bas.
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
                            text = BookmarksText.PANEL_TITLE,
                            modifier = Modifier.weight(1f),
                            fontSize = AppTheme.typeScale.section,
                            fontWeight = FontWeight.ExtraBold,
                            selectable = false,
                        )

                        SheetCloseButton(
                            onClose = onClose,
                            description = BookmarksText.CLOSE,
                        )
                    }

                    for (row in BookmarksText.panelRows(destinations.keys)) {
                        AppButton(
                            text = row.title,
                            onClick = destinations.getValue(row.action),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = ENTRY_GAP),
                            // L'original pose la seconde en style secondaire : poser un signet
                            // est le geste neuf, consulter la liste est celui de la relecture.
                            secondary = row.action == BookmarksText.PanelAction.OPEN_LIST,
                        )
                    }
                }
            }
        }
    }
}

/**
 * L'espace entre deux entrées.
 *
 * La colonne n'utilise pas `Arrangement.spacedBy` : l'espacement doit tomber **après** chaque
 * entrée, y compris la dernière, sans quoi le bas du panneau collerait à la barre de gestes.
 */
private val ENTRY_GAP = 8.dp
