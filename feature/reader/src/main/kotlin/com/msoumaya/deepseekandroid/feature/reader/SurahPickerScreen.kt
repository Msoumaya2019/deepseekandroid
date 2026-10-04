package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardOptions
import com.msoumaya.deepseekandroid.core.design.component.AppButton
import com.msoumaya.deepseekandroid.core.design.component.AppField
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.component.AppTitle
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.QuranSourceTransition
import com.msoumaya.deepseekandroid.core.domain.SurahPickerText
import com.msoumaya.deepseekandroid.core.model.Surah

/**
 * Le sélecteur de sourate : les 114 sourates, et un accès direct à une page.
 *
 * Porté depuis `src/SurahPicker.tsx`. Deux façons d'atteindre un endroit du Coran, et elles ne
 * s'adressent pas au même besoin : **la sourate** est ce qu'on cherche quand on sait quoi lire,
 * **la page** quand on sait où l'on en était — un repère pris sur un exemplaire papier, une
 * note, un numéro lu à quelqu'un.
 *
 * ## Ce que l'écran ne décide pas
 *
 * Le choix d'une sourate ne dit **pas** où aller. Les sources n'ont pas le même découpage : la
 * première page de la sourate 18 n'est pas la même dans le moushaf de Médine et dans le paquet
 * « Coran 1441 ». La conversion vit donc chez l'appelant, qui seul connaît la source affichée
 * — c'est `MushafSourceNavigation.versePage`, et elle est éprouvée. Cet écran rend la sourate
 * choisie, rien de plus.
 *
 * ## La page demandée est validée avant d'être suivie
 *
 * Une page acceptée à tort ne se voit qu'après : l'écran se ferme, et le lecteur affiche une
 * page qui n'existe pas. La règle est donc appliquée **ici**, par `SurahPickerText`, et une
 * saisie refusée laisse la fenêtre ouverte avec sa raison — plutôt que de la fermer sur rien.
 *
 * @param currentSurah la sourate affichée, surlignée dans la liste et atteinte au défilement.
 * @param currentPage la page affichée, proposée dans le champ. Le champ est **réinitialisé**
 *   si elle change : sans cela, ouvrir le sélecteur après avoir tourné trois pages proposerait
 *   encore la page d'avant.
 * @param onPage la page demandée, **déjà validée** : elle est dans `1..totalPages`, et
 *   l'appelant n'a pas à la revérifier.
 * @param onSelect la sourate choisie. L'appelant en déduit la page, dans le découpage de sa
 *   source.
 * @param totalPages le découpage de la source affichée. Toutes n'ont pas le même nombre de
 *   pages, et une page acceptée ici pour l'une ne l'est pas forcément pour l'autre.
 */
@Composable
fun SurahPickerScreen(
    currentSurah: Int,
    currentPage: Int,
    onPage: (Int) -> Unit,
    onSelect: (Surah) -> Unit,
    onClose: () -> Unit,
    totalPages: Int = QuranSourceTransition.TOTAL_PAGES,
) {
    val colors = AppTheme.colors
    val keyboard = LocalSoftwareKeyboardController.current

    // Le référentiel peut n'être pas chargé — au tout premier lancement, avant que les données
    // soient lues. Une liste vide vaut mieux qu'un écran qui tombe : la personne peut revenir.
    val surahs = remember { runCatching { Quran.surahs }.getOrElse { emptyList() } }

    // `remember(currentPage)` et non `remember` : la page affichée peut changer pendant que la
    // fenêtre est ouverte — l'écoute suit les versets — et le champ ne doit pas mentir.
    var pageText by remember(currentPage) { mutableStateOf(currentPage.toString()) }
    var refused by remember { mutableStateOf(false) }

    if (refused) {
        AlertDialog(
            onDismissRequest = { refused = false },
            title = {
                AppLabel(
                    text = SurahPickerText.INVALID_PAGE_TITLE,
                    fontSize = AppTheme.typeScale.card,
                    fontWeight = FontWeight.Bold,
                    selectable = false,
                )
            },
            text = {
                AppLabel(text = SurahPickerText.INVALID_PAGE_BODY, selectable = false)
            },
            // « OK » est le bouton que le client d'origine obtient sans le demander : sur
            // Android, `Alert.alert` sans bouton en ajoute un. Ne rien mettre ici donnerait une
            // alerte qu'on ne peut pas fermer autrement qu'en touchant à côté.
            confirmButton = {
                AppButton(text = OK, onClick = { refused = false }, small = true)
            },
            containerColor = colors.paper,
        )
    }

    Dialog(
        onDismissRequest = onClose,
        // Sans cela, la fenêtre se limite à la largeur d'un téléphone en mode paysage et la
        // liste flotte au milieu de l'écran. La feuille doit occuper la page, comme l'original.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = colors.cream) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    AppButton(
                        text = SurahPickerText.CLOSE,
                        onClick = onClose,
                        secondary = true,
                        small = true,
                    )

                    AppTitle(text = SurahPickerText.TITLE)

                    AppLabel(
                        text = SurahPickerText.SUBTITLE,
                        color = colors.muted,
                        selectable = false,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            AppField(
                                value = pageText,
                                onValueChange = { pageText = it },
                                placeholder = SurahPickerText.PAGE_PLACEHOLDER,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                // Le champ est le seul endroit où la longueur se borne sans
                                // risque : au-delà de quatre chiffres, aucune page n'existe plus,
                                // et laisser grandir la saisie ne ferait que repousser le refus.
                                maxLength = PAGE_INPUT_MAX_LENGTH,
                            )
                        }

                        AppButton(
                            text = SurahPickerText.GO,
                            small = true,
                            onClick = {
                                val page = SurahPickerText.pageFromInput(pageText, totalPages)
                                if (page == null) {
                                    refused = true
                                } else {
                                    keyboard?.hide()
                                    onPage(page)
                                }
                            },
                        )
                    }
                }

                HorizontalDivider(color = colors.line)

                val listState = rememberLazyListState(
                    // La sourate affichée doit être sous les yeux à l'ouverture : ouvrir une
                    // liste de 114 entrées à la première page obligerait à défiler pour
                    // retrouver celle qu'on lit. La borne protège d'un numéro incohérent.
                    initialFirstVisibleItemIndex = (currentSurah - 1)
                        .coerceIn(0, maxOf(0, surahs.size - 1)),
                )

                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    items(items = surahs, key = { it.number }) { surah ->
                        SurahRow(
                            surah = surah,
                            selected = surah.number == currentSurah,
                            onSelect = { onSelect(surah) },
                        )
                    }
                }
            }
        }
    }
}

/** Une ligne de la liste : numéro, nom, nombre de versets, nom arabe. */
@Composable
private fun SurahRow(surah: Surah, selected: Boolean, onSelect: () -> Unit) {
    val colors = AppTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .background(if (selected) colors.selected else colors.paper)
            .clickable(role = Role.Button, onClick = onSelect)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppLabel(
            text = surah.number.toString(),
            modifier = Modifier.width(30.dp),
            color = colors.green,
            fontWeight = FontWeight.Bold,
            selectable = false,
        )

        Column(modifier = Modifier.weight(1f)) {
            AppLabel(
                text = surah.name,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                selectable = false,
            )
            AppLabel(
                text = SurahPickerText.versesLabel(surah.count),
                fontSize = AppTheme.typeScale.secondary,
                color = colors.muted,
                selectable = false,
            )
        }

        // Le nom arabe se lit de droite à gauche. Sans `TextDirection.Rtl`, la ponctuation et
        // les signes diacritiques se placent du mauvais côté, et le nom s'affiche à l'envers
        // pour qui sait le lire.
        //
        // 22 et non 23 : le client d'origine pose cette taille à la main, et `tokens.ts`
        // n'offre pas de jeton à 23. `arabic` est le plus proche, et l'écart est d'un point.
        AppLabel(
            text = surah.arabic,
            fontSize = AppTheme.typeScale.arabic,
            color = colors.green,
            style = TextStyle(textDirection = TextDirection.Rtl),
            selectable = false,
        )
    }
}

/** Le bouton de l'alerte. Le client d'origine l'obtient sans le nommer, on le nomme. */
private const val OK = "OK"

/** Quatre chiffres : la plus grande page possible est 604. */
private const val PAGE_INPUT_MAX_LENGTH = 4

/** La hauteur d'une ligne. Le défilement initial la compte pour atteindre la bonne entrée. */
private val ROW_HEIGHT = 64.dp
