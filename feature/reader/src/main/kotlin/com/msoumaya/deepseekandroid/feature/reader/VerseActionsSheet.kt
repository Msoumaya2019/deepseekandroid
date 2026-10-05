package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.msoumaya.deepseekandroid.core.domain.VerseActionsText

/**
 * Le panneau « Actions du verset ».
 *
 * Porté depuis le panneau `sessionPanel === 'verse'` de `src/App.tsx`. Il s'ouvre **en même temps
 * que la fiche du verset**, et non après elle : la fiche dit ce qu'on a touché, le panneau dit ce
 * qu'on peut en faire. C'est pourquoi il répète le numéro du verset ([VerseActionsText.verseLabel])
 * — la fiche peut avoir défilé hors de l'écran, et le panneau doit rester lisible seul.
 *
 * ## Une action qui ne mène nulle part n'est pas affichée
 *
 * Les quatre destinations sont des paramètres **facultatifs**, et la décision appartient à
 * [VerseActionsText.rows], éprouvée dans `core:domain` — même règle, et même raison, que la
 * feuille d'options et le panneau des marques-pages. Aujourd'hui, « Sélectionner un passage »
 * suppose un choix de plage qui n'est pas porté : l'entrée disparaît au lieu de mener nulle part.
 *
 * ## Écart assumé : le portage pose un voile, le source non
 *
 * Le client d'origine dessine ce panneau en **encart flottant**, sans voile, et la page reste
 * visible au-dessus de lui. Le portage réserve le voile à **tous** ses panneaux — options,
 * réglages d'écoute, traduction, marques-pages — et celui-ci suit la même anatomie plutôt que
 * d'introduire une cinquième forme. L'information perdue est compensée : le panneau répète le
 * numéro du verset, et la fiche qui le décrit est encore là quand on le referme.
 *
 * @param ayah le numéro du verset dans sa sourate, tel que la fiche l'affiche.
 * @param markedByUser l'état du marqueur de l'**élève** sur ce verset : il décide du mot de la
 *   dernière entrée, et non `Review.isDifficult`, qui compte aussi le professeur.
 * @param onClose referme le panneau, y compris en tirant la poignée vers le bas.
 * @param onListen joue le verset une fois. `null` quand l'appelant ne sait pas le faire.
 * @param onRepeat ouvre les réglages d'écoute, mode « passage complet ». `null` sinon.
 * @param onSelectRange désigne une plage sur la page. `null` tant que ce geste n'est pas porté.
 * @param onMark bascule le marqueur de difficulté de l'élève. `null` quand l'appelant ne sait pas
 *   l'écrire.
 */
@Composable
internal fun VerseActionsSheet(
    ayah: Int,
    markedByUser: Boolean,
    onClose: () -> Unit,
    onListen: (() -> Unit)? = null,
    onRepeat: (() -> Unit)? = null,
    onSelectRange: (() -> Unit)? = null,
    onMark: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors

    val destinations: Map<VerseActionsText.Action, () -> Unit> = buildMap {
        onListen?.let { put(VerseActionsText.Action.LISTEN, it) }
        onRepeat?.let { put(VerseActionsText.Action.REPEAT, it) }
        onSelectRange?.let { put(VerseActionsText.Action.SELECT_RANGE, it) }
        onMark?.let { put(VerseActionsText.Action.MARK, it) }
    }

    // Rien à proposer : le panneau ne s'ouvre pas du tout. Se réduire à un titre et à une
    // poignée serait une impasse — et c'est à l'appelant de ne pas le proposer dans ce cas, ce
    // que le lecteur fait déjà par `isUseful`.
    if (!VerseActionsText.isUseful(destinations.keys)) return

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
                        // Le numéro du verset est répété depuis la fiche : le panneau doit dire
                        // sur quoi il porte, même si la fiche a quitté l'écran.
                        AppLabel(
                            text = VerseActionsText.verseLabel(ayah),
                            modifier = Modifier.weight(1f),
                            fontSize = AppTheme.typeScale.section,
                            fontWeight = FontWeight.ExtraBold,
                            selectable = false,
                        )

                        SheetCloseButton(
                            onClose = onClose,
                            description = VerseActionsText.CLOSE,
                        )
                    }

                    for (row in VerseActionsText.rows(destinations.keys, markedByUser)) {
                        AppButton(
                            text = row.title,
                            onClick = destinations.getValue(row.action),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = ENTRY_GAP),
                            // Écouter est le geste le plus courant, et le seul qui ne fasse pas
                            // qu'ouvrir autre chose : il est mis en avant. Les trois autres sont
                            // secondaires, comme dans le client d'origine.
                            secondary = row.action != VerseActionsText.Action.LISTEN,
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
