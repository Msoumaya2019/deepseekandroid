package com.msoumaya.deepseekandroid.feature.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.msoumaya.deepseekandroid.core.data.LocalAppContainer
import com.msoumaya.deepseekandroid.core.design.component.AppButton
import com.msoumaya.deepseekandroid.core.design.component.AppCard
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.QuranDownloadText
import com.msoumaya.deepseekandroid.core.model.MushafSource

/**
 * Le sélecteur de présentation du Coran.
 *
 * Porté depuis la fenêtre « Affichage du Coran » du lecteur. Trois présentations se choisissent
 * d'un appui ; la quatrième — « Coran 1441 » — se comporte autrement, puisque son paquet peut
 * manquer : c'est [QuranSourceChoice] qui s'en charge.
 *
 * ## La fermeture est commandée par le résultat, pas par l'appui
 *
 * Le panneau ne se referme pas au moment où l'on appuie, mais quand la source **retenue**
 * devient celle qui a été demandée. Un refus — une ligne manquante sur la page courante, une
 * installation interrompue — laisse donc la fenêtre ouverte avec sa raison. Se refermer sur un
 * appui donnerait un échec silencieux : l'écran reviendrait au lecteur, sur la même source, et
 * rien ne dirait pourquoi.
 *
 * @param currentPage la page affichée par le lecteur. C'est **elle** qui est vérifiée avant
 *   d'adopter une source : changer de présentation ne doit pas faire perdre sa page.
 */
@Composable
fun QuranSourcePickerDialog(
    onDismiss: () -> Unit,
    currentPage: Int,
    modifier: Modifier = Modifier,
    viewModel: QuranSourceViewModel = viewModel(
        factory = QuranSourceViewModel.factory(LocalAppContainer.current),
    ),
) {
    val colors = AppTheme.colors
    val source by viewModel.source.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    val switching by viewModel.switching.collectAsStateWithLifecycle()
    var requested by remember { mutableStateOf<MushafSource?>(null) }

    LaunchedEffect(source, requested) {
        val target = requested ?: return@LaunchedEffect
        if (source == target) {
            requested = null
            onDismiss()
        }
    }

    // Demander la source déjà retenue ne changerait rien : on referme, sans écrire d'état.
    val request: (MushafSource) -> Unit = { target ->
        if (target == source) {
            onDismiss()
        } else {
            requested = target
            viewModel.select(target, currentPage)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        AppCard(modifier = modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppLabel(
                    text = QuranDownloadText.PICKER_TITLE,
                    // 21 : `tokens.ts` ne prévoit pas de jeton à 20, la taille que le client
                    // d'origine pose ici à la main (`App.tsx:515`). `section` est le plus proche,
                    // et l'inventaire des tailles est celui du dépôt d'origine — y ajouter un
                    // jeton serait sortir du vocabulaire qu'on a décidé de reprendre tel quel.
                    fontSize = AppTheme.typeScale.section,
                    fontWeight = FontWeight.Bold,
                )

                for ((presentation, label) in QuranDownloadText.SIMPLE_PRESENTATIONS) {
                    AppButton(
                        text = label,
                        secondary = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { request(presentation) },
                    )
                }

                QuranSourceChoice(
                    selected = source == MushafSource.CORAN_1441,
                    onSelect = { request(MushafSource.CORAN_1441) },
                    viewModel = viewModel,
                )

                if (switching) {
                    AppLabel(
                        text = QuranDownloadText.LOADING_SOURCE,
                        // 12, comme le client d'origine (`App.tsx:496`), et non `metadata` (11) :
                        // ces deux lignes-là sont écrites en 12 chez lui, et le jeton qui porte
                        // cette taille est `secondary`.
                        fontSize = AppTheme.typeScale.secondary,
                        color = colors.muted,
                    )
                }

                // Le rouge est celui du client d'origine (`App.tsx:497`) : un refus doit se
                // distinguer d'une information ordinaire, sans quoi il se lit comme une note.
                if (failure != null) {
                    AppLabel(
                        text = failure.orEmpty(),
                        fontSize = AppTheme.typeScale.secondary,
                        color = colors.red,
                    )
                }
            }
        }
    }
}
