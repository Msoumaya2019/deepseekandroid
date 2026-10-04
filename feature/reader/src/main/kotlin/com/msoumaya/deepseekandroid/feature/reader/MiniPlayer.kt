package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.msoumaya.deepseekandroid.core.audio.AudioSessionState
import com.msoumaya.deepseekandroid.core.design.component.AppIconButton
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.Audio
import com.msoumaya.deepseekandroid.core.domain.AudioSession

/**
 * Le mini-lecteur : une ligne, et rien de plus.
 *
 * Il est **dans le flux**, comme la coquille : il prend sa hauteur au lieu de recouvrir la
 * page. Un lecteur flottant masquerait le dernier verset de la page, et il faudrait le faire
 * disparaître pour finir de lire — exactement ce qu'on ne veut pas d'un lecteur d'écoute.
 *
 * Aucun bouton mort : quand la séance est terminée, le bouton de lecture **disparaît** au lieu
 * de rester là sans effet. Il reste la croix pour fermer, et l'action « écouter la page » dans
 * la coquille pour repartir.
 */
@Composable
internal fun MiniPlayer(
    state: AudioSessionState,
    reciterName: String,
    countLabel: String,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val position = state.position

    // La référence est calculée une fois par verset : `verseAudioLabel` remonte le référentiel.
    val reference = remember(position?.verseId) {
        position?.let { runCatching { Audio.verseAudioLabel(it.verseId) }.getOrNull() }
    }

    val detail = when {
        state.error != null -> state.error.orEmpty()
        state.isFinished -> "Séance terminée"
        position == null -> reciterName
        else -> "$reciterName · ${position.repetition}/$countLabel"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.paper)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reference ?: "Écoute du passage",
                fontSize = AppTheme.typeScale.card,
                color = colors.text,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = detail,
                fontSize = AppTheme.typeScale.metadata,
                color = if (state.error != null) colors.red else colors.muted,
                maxLines = 2,
            )
        }

        if (!state.isFinished && state.error == null) {
            AppIconButton(
                icon = if (state.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                label = if (state.isPlaying) "Mettre en pause" else "Reprendre l'écoute",
                onClick = onToggle,
            )
        }

        AppIconButton(
            icon = Icons.Outlined.Close,
            label = "Arrêter l'écoute",
            onClick = onStop,
        )
    }
}

/** Le symbole d'illimité. Il n'est pas décoratif : une séance sans fin et une séance de trois
 * écoutes ne se comportent pas pareil, et rien d'autre à l'écran ne le dirait. */
internal const val INFINITE = "∞"

/**
 * Le nombre d'écoutes tel que le mini-lecteur l'affiche : `3`, `20`, ou `∞`.
 *
 * L'affichage doit dire ce que le moteur **fait**, et rien d'autre. Une saisie libre abîmée est
 * ramenée à 1 par [AudioSession.resolveCount] ; afficher `∞` dans ce cas annoncerait une
 * répétition sans fin qui n'aura pas lieu — une promesse que la séance ne tiendra pas.
 *
 * Le lecteur s'ouvre dans tous les cas : c'est [AudioSession.resolveCount] qui garantit qu'une
 * préférence illisible ne fait pas échouer la lecture, et `AudioSessionControllerTest` qui le
 * mesure.
 */
internal fun countLabelOf(settings: AudioSession): String =
    settings.resolveCount()?.toString() ?: INFINITE
