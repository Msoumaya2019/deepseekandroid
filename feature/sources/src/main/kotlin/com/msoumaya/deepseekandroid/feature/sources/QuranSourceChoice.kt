package com.msoumaya.deepseekandroid.feature.sources

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.msoumaya.deepseekandroid.core.data.LocalAppContainer
import com.msoumaya.deepseekandroid.core.design.component.AppChoice
import com.msoumaya.deepseekandroid.core.domain.QuranDownloadText

/**
 * Le choix « Coran 1441 », avec son panneau de téléchargement replié dessous.
 *
 * Porté depuis `DownloadSourceChoice` (`src/ui/QuranDownload.tsx`). Le comportement tient en
 * une ligne, et c'est une règle :
 *
 *  - le paquet **est installé** → le choix sélectionne la source ;
 *  - il **ne l'est pas** → le choix déplie le panneau, et c'est la fin de l'installation qui
 *    sélectionnera la source.
 *
 * Sélectionner d'abord et télécharger ensuite donnerait un lecteur sur une page sans images —
 * c'est-à-dire exactement ce que la transition de source existe pour empêcher.
 *
 * Ce choix vit dans une liste de présentations, et il est le seul à ne pas être un simple
 * bouton : les trois autres sources sont embarquées, donc toujours disponibles.
 */
@Composable
fun QuranSourceChoice(
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuranSourceViewModel = viewModel(
        factory = QuranSourceViewModel.factory(LocalAppContainer.current),
    ),
) {
    val downloaded by viewModel.downloaded.collectAsStateWithLifecycle()
    // `rememberSaveable` : une rotation pendant le téléchargement ne doit pas refermer le
    // panneau, ce qui donnerait l'impression que le transfert s'est arrêté.
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        AppChoice(
            label = QuranDownloadText.TITLE,
            subtitle = QuranDownloadText.CHOICE_SUBTITLE,
            selected = selected,
            onPress = { if (downloaded) onSelect() else expanded = true },
        )

        if (expanded) {
            QuranDownloadPanel(
                onReady = {
                    expanded = false
                    onSelect()
                },
                onBack = { expanded = false },
                viewModel = viewModel,
            )
        }
    }
}
