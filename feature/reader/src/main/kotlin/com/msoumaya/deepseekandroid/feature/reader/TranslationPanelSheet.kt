package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.TranslationPanel

/**
 * Le panneau « Traduction française » : la page affichée, verset par verset, en français.
 *
 * Porté depuis le bloc `sessionPanel === 'translation'` de `src/App.tsx`. C'est le seul endroit
 * où l'on lit le sens sans quitter le moushaf : la page arabe reste derrière, et l'on descend
 * dans la traduction du même passage.
 *
 * ## Ce que la feuille ne décide pas
 *
 * **Quels versets sont montrés.** Les lignes arrivent toutes faites : c'est
 * [TranslationPanel.rows] qui choisit la plage — celle de la séance quand il y en a une, celle
 * de la page sinon — et qui lit la traduction. La feuille ne connaît ni la page, ni la source,
 * ni le référentiel : elle affiche ce qu'on lui donne. La règle est ainsi éprouvée là où elle
 * vit, dans `core:domain`, et non à travers une interface qu'aucun test ne traverse.
 *
 * ## Écarts assumés avec l'original
 *
 * Le panneau d'origine est un bloc posé en bas de l'écran, à 8 points des bords. Ici, c'est une
 * fenêtre de dialogue ancrée en bas, comme les autres feuilles du lecteur — la raison est la
 * même que pour la feuille d'options, et elle est écrite dans `SheetChrome`.
 *
 * Deux différences de mesure, toutes deux héritées de la famille des feuilles : le remplissage
 * intérieur est de 14 points là où l'original en pose 16, et l'arrondi haut est celui du thème
 * (30) au lieu de 24. Les quatre feuilles du lecteur portent les mêmes valeurs, et les faire
 * diverger ici se verrait en passant de l'une à l'autre.
 *
 * Enfin, quand il n'y a rien à montrer, l'original n'affiche rien du tout sous son titre — et
 * dans son cas la liste ne peut être vide que si la plage est vide. Ici, une plage hors du
 * corpus est **ramenée** au lieu de faire tomber l'écran (voir [TranslationPanel.verses]), et
 * une page inconnue ne donne aucune ligne : le panneau le **dit** plutôt que de rester un titre
 * au-dessus du vide.
 *
 * @param rows les lignes à afficher, dans l'ordre des versets. Vide quand il n'y a rien à
 *   montrer — plage hors du corpus, ou plage de page inconnue.
 * @param onClose referme le panneau. La poignée le fait aussi, par un glissement vers le bas.
 */
@Composable
internal fun TranslationPanelSheet(
    rows: List<TranslationPanel.Row>,
    onClose: () -> Unit,
) {
    val colors = AppTheme.colors

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // La zone au-dessus du panneau : le toucher referme. Sans couleur — la fenêtre de
            // dialogue pose déjà son voile, et en ajouter un second assombrirait la page deux
            // fois plus que dans l'original.
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
                    // 70 %, comme l'original. Un passage entier peut dépasser l'écran : le
                    // panneau défile alors au lieu d'être coupé. Et il reste toujours un tiers
                    // de moushaf visible — lire la traduction ne doit pas faire perdre la page.
                    .heightIn(max = maxHeight * PANEL_HEIGHT_FRACTION)
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
                            text = TranslationPanel.TITLE,
                            modifier = Modifier.weight(1f),
                            fontSize = AppTheme.typeScale.section,
                            fontWeight = FontWeight.ExtraBold,
                            selectable = false,
                        )
                        SheetCloseButton(
                            onClose = onClose,
                            description = TranslationPanel.CLOSE,
                        )
                    }

                    if (rows.isEmpty()) {
                        AppLabel(
                            text = EMPTY_MESSAGE,
                            fontSize = AppTheme.typeScale.body,
                            color = colors.muted,
                            selectable = false,
                        )
                    } else {
                        for (row in rows) {
                            TranslationRow(row)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Une ligne : la référence du verset, puis sa traduction.
 *
 * La séparation est **sous chaque ligne**, y compris la dernière : c'est le
 * `borderBottomWidth: 1` de l'original, et il borne le texte du bas comme il sépare les autres.
 *
 * La traduction est sélectionnable, comme tout le reste de l'application : recopier un passage
 * est un usage courant, et il n'y a pas de raison de le refuser ici. La référence, elle, ne
 * l'est pas — c'est un repère, pas un texte qu'on cite.
 */
@Composable
private fun TranslationRow(row: TranslationPanel.Row) {
    val colors = AppTheme.colors

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = ROW_VERTICAL_PADDING),
        ) {
            AppLabel(
                text = row.reference,
                fontSize = AppTheme.typeScale.secondary,
                color = colors.gold,
                selectable = false,
            )
            AppLabel(
                text = row.translation,
                modifier = Modifier.padding(top = REFERENCE_GAP),
                fontSize = AppTheme.typeScale.body,
                // L'interligne de l'original : 25 points pour un texte de 14. C'est ce qui rend
                // une traduction longue lisible plutôt que tassée.
                style = TextStyle(lineHeight = TRANSLATION_LINE_HEIGHT),
            )
        }
        HorizontalDivider(color = colors.line)
    }
}

/** Ce que le panneau dit quand il n'a aucune ligne à montrer. */
private const val EMPTY_MESSAGE = "Aucune traduction à afficher pour cette page."

/** Le `paddingVertical: 10` de l'original, autour de chaque ligne. */
private val ROW_VERTICAL_PADDING = 10.dp

/** L'écart entre la référence et la traduction. C'est le `marginTop: 5` de l'original. */
private val REFERENCE_GAP = 5.dp

/** L'interligne de la traduction, en points. C'est le `lineHeight: 25` de l'original. */
private val TRANSLATION_LINE_HEIGHT = 25.sp

/**
 * La part de l'écran que le panneau peut occuper.
 *
 * 70 %, comme l'original : la traduction d'un passage peut être longue, et il reste un tiers de
 * moushaf visible. C'est plus que la feuille d'options et moins que les réglages d'écoute, dont
 * la hauteur ne sert qu'à eux-mêmes.
 */
private const val PANEL_HEIGHT_FRACTION = 0.70f
