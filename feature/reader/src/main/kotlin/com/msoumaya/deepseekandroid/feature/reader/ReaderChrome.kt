package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.msoumaya.deepseekandroid.core.design.component.AppIconButton
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import kotlin.math.roundToInt

/**
 * La coquille du lecteur : discrète, et **dans le flux**.
 *
 * Elle prend sa hauteur au lieu de recouvrir la page. C'est le choix du client d'origine, et
 * ce n'est pas un détail : une barre flottante masque le bas de la page — donc le dernier
 * verset — et il faut la faire disparaître pour lire la fin d'une page. Ici, tant qu'elle est
 * visible, la page est plus petite mais **entière**.
 *
 * Aucun bouton mort : une action dont l'écran n'est pas encore écrit est **absente**, pas
 * grisée. Un bouton qui ne fait rien fait douter du reste de l'écran.
 *
 * @param onListen écoute la page affichée. `null` quand elle ne peut pas être écoutée — le
 *   référentiel n'est pas chargé, ou la page n'a pas de plage de versets : dans ce cas le
 *   bouton est **absent** plutôt que présent et sans effet.
 */
@Composable
internal fun ReaderChrome(
    page: Int,
    totalPages: Int,
    surahName: String,
    zoomed: Boolean,
    onPage: (Int) -> Unit,
    onClose: () -> Unit,
    onResetZoom: () -> Unit,
    // `modifier` reste le **premier paramètre optionnel** : sinon `onListen` prendrait sa place,
    // et le lecteur d'un appelant qui passe sa mise en page au doigt ne s'appliquerait plus.
    modifier: Modifier = Modifier,
    onListen: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.paper)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AppIconButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                label = "Fermer le lecteur",
                onClick = onClose,
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = surahName,
                    fontSize = AppTheme.typeScale.card,
                    color = colors.text,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = "Page $page sur $totalPages",
                    fontSize = AppTheme.typeScale.metadata,
                    color = colors.muted,
                    maxLines = 1,
                )
            }

            // Le retour au zoom entier n'apparaît que lorsqu'il sert : un bouton
            // « réinitialiser » toujours visible est du bruit.
            if (zoomed) {
                AppIconButton(
                    icon = Icons.Outlined.CenterFocusStrong,
                    label = "Revenir à la page entière",
                    onClick = onResetZoom,
                )
            }

            // Écouter la page. Absent quand la page n'a pas de plage connue : un bouton qui ne
            // peut rien faire vaut moins que pas de bouton.
            if (onListen != null) {
                AppIconButton(
                    icon = Icons.Outlined.Headphones,
                    label = "Écouter cette page",
                    onClick = onListen,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppIconButton(
                icon = Icons.Outlined.ChevronLeft,
                label = "Page précédente",
                enabled = page > 1,
                onClick = { onPage(page - 1) },
            )

            Box(modifier = Modifier.weight(1f)) {
                PageSlider(page = page, totalPages = totalPages, onPage = onPage)
            }

            AppIconButton(
                icon = Icons.Outlined.ChevronRight,
                label = "Page suivante",
                enabled = page < totalPages,
                onClick = { onPage(page + 1) },
            )
        }
    }
}

/**
 * Curseur de page.
 *
 * Le curseur est piloté par une **valeur locale** pendant le glissement, et ne remonte au
 * lecteur qu'au relâchement. Sans cela, chaque pixel parcouru déclencherait un changement de
 * page et un geste ferait défiler cinquante pages du moushaf.
 */
@Composable
private fun PageSlider(page: Int, totalPages: Int, onPage: (Int) -> Unit) {
    val colors = AppTheme.colors
    // `remember(page)` : changer de page autrement — au doigt, ou par les flèches — doit
    // replacer le curseur, sinon il mentirait sur la page affichée.
    var local by remember(page) { mutableFloatStateOf(page.toFloat()) }

    Slider(
        value = local,
        onValueChange = { local = it },
        onValueChangeFinished = {
            val target = local.roundToInt().coerceIn(1, totalPages)
            local = target.toFloat()
            if (target != page) onPage(target)
        },
        valueRange = 1f..totalPages.toFloat(),
        colors = SliderDefaults.colors(
            thumbColor = colors.green,
            activeTrackColor = colors.green,
            inactiveTrackColor = colors.line,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
    )
}
