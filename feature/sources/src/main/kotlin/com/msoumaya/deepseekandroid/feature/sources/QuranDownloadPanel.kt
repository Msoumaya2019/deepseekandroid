package com.msoumaya.deepseekandroid.feature.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.msoumaya.deepseekandroid.core.data.LocalAppContainer
import com.msoumaya.deepseekandroid.core.design.component.AppButton
import com.msoumaya.deepseekandroid.core.design.component.ProgressTrack
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.ArchivePhase
import com.msoumaya.deepseekandroid.core.domain.QuranDownloadText

/**
 * Le panneau d'installation du paquet « Coran 1441 ».
 *
 * Porté depuis `src/ui/QuranDownload.tsx`. Ce qu'il dit et ce qu'il propose est décidé par
 * `QuranDownloadText`, dans le domaine : quels mots, quel pourcentage, et quel bouton pendant
 * quelle phase. Le composable ne fait que les disposer — c'est ce qui permet d'éprouver les
 * règles sans écran.
 *
 * ## La pause quand l'écran disparaît
 *
 * Le panneau met l'installation en pause dès que l'application passe en arrière-plan. Sans
 * cela, Android tuerait le processus en cours de transfert — il n'y a pas de service
 * d'avant-plan — et la reprise repartirait du partiel, ce qui fonctionne mais laisse une
 * coupure inutile. Le client d'origine fait de même sur `AppState`.
 *
 * @param onReady l'installation est terminée. Appelé **une fois**, quand la phase devient
 *   `READY` : c'est le signal qui fait basculer la source et referme le panneau.
 * @param onBack revenir en arrière sans installer, quand l'appelant le permet.
 */
@Composable
fun QuranDownloadPanel(
    onReady: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: QuranSourceViewModel = viewModel(
        factory = QuranSourceViewModel.factory(LocalAppContainer.current),
    ),
) {
    val colors = AppTheme.colors
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()

    // La fin de l'installation est annoncée une fois par phase : sans la clé, une
    // recomposition quelconque rappellerait `onReady` et rejouerait le changement de source.
    LaunchedEffect(progress.phase) {
        if (progress.phase == ArchivePhase.READY) onReady()
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.pauseDownload()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            // Et aussi quand le panneau **disparaît** : replié, ou remplacé par le lecteur. Le
            // client d'origine fait de même au démontage (`QuranDownload.tsx:15`), et ce n'est
            // pas une perte — la pause laisse le partiel en place, et la reprise repart de là.
            viewModel.pauseDownload()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = QuranDownloadText.TITLE,
            fontSize = AppTheme.typeScale.card,
            fontWeight = FontWeight.Bold,
            color = colors.text,
        )
        Text(
            text = QuranDownloadText.SUBTITLE,
            // 12 et non 11 : le client d'origine écrit ce sous-titre en 13 (`QuranDownload.tsx:19`),
            // et `secondary` est le jeton le plus proche de cette taille.
            fontSize = AppTheme.typeScale.secondary,
            color = colors.muted,
        )

        // L'avancement n'est annoncé que pendant un travail : afficher « 0 % » à l'arrêt
        // donnerait à croire qu'un transfert est en cours.
        QuranDownloadText.progressLabel(progress.phase, progress.progress)?.let { label ->
            Text(
                text = label,
                fontSize = AppTheme.typeScale.body,
                color = colors.text,
            )
            ProgressTrack(value = progress.progress)
        }

        // Le message de l'installation, ou celui d'un changement de source refusé : les deux
        // occupent la même place, et un refus doit se voir au même endroit que l'erreur qu'il
        // remplace.
        val message = progress.message ?: failure
        if (message != null) {
            Text(
                text = message,
                fontSize = AppTheme.typeScale.body,
                color = colors.text,
            )
        }

        // Un seul bouton à la fois, jamais deux, et jamais aucun pendant l'écriture des pages :
        // c'est `QuranDownloadText` qui en décide, et c'est éprouvé.
        when {
            QuranDownloadText.showsPause(progress.phase) -> AppButton(
                text = QuranDownloadText.PAUSE,
                secondary = true,
                onClick = { viewModel.pauseDownload() },
            )

            QuranDownloadText.showsAction(progress.phase) -> AppButton(
                text = QuranDownloadText.actionLabel(progress.phase),
                onClick = { viewModel.startDownload() },
            )
        }

        onBack?.let { back ->
            AppButton(
                text = QuranDownloadText.BACK,
                secondary = true,
                onClick = back,
            )
        }
    }
}
