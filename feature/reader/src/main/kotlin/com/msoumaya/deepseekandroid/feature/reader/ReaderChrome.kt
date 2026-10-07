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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Palette
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.msoumaya.deepseekandroid.core.design.component.AppIconButton
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.BookmarksText
import com.msoumaya.deepseekandroid.core.domain.ReaderOptionsText
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
 * @param onOpenSourcePicker ouvre le choix de présentation. `null` quand l'appelant n'en
 *   propose pas : le lecteur ne connaît ni les sources ni le stockage, il demande.
 * @param onOpenOptions ouvre la feuille « Plus d'options ». `null` quand l'appelant n'a aucune
 *   destination à y proposer : le bouton est alors absent plutôt que présent et sans effet.
 * @param onOpenBookmarks ouvre le panneau des marques-pages. `null` quand l'appelant ne sait pas
 *   enregistrer de signet : le bouton est alors **absent**, comme les autres. Il ouvre le
 *   **panneau** et non directement le mode de pose — c'est le détour du client d'origine, et
 *   c'est lui qui empêche qu'un appui malencontreux arme le geste.
 * @param onRecord ouvre le panneau d'enregistrement. `null` quand l'appelant n'a pas
 *   d'enregistreur, ou pas de plage à enregistrer : le bouton est alors **absent**, comme les
 *   autres. C'est la porte de la barre flottante du client d'origine — son action `record`, entre
 *   « Écouter » et « Marque-page » —, et son mot y est « Enregistrer », qui n'est celui ni du
 *   titre du panneau ni d'un geste de la barre réduite : trois choses différentes, trois mots.
 * @param bookmarkActive vrai pendant le mode de pose : le bouton reçoit alors un fond doux, ce
 *   qui est la marque d'activité de la barre flottante d'origine. L'autre cas de l'original —
 *   le panneau ouvert — ne s'applique pas ici : notre panneau est une fenêtre de dialogue, donc
 *   la coquille est derrière son voile et n'est pas visible.
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
    onOpenSourcePicker: (() -> Unit)? = null,
    onListen: (() -> Unit)? = null,
    onRecord: (() -> Unit)? = null,
    onOpenOptions: (() -> Unit)? = null,
    onOpenBookmarks: (() -> Unit)? = null,
    bookmarkActive: Boolean = false,
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

            // Choisir la présentation des pages. Absent quand l'appelant n'en propose pas : le
            // lecteur ne connaît ni les sources ni le paquet, c'est lui qui demande.
            if (onOpenSourcePicker != null) {
                AppIconButton(
                    icon = Icons.Outlined.Palette,
                    label = "Affichage du Coran",
                    onClick = onOpenSourcePicker,
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

            // Enregistrer sa voix. Absent quand l'appelant n'a pas d'enregistreur, ou pas de plage
            // à enregistrer : la porte du panneau, et rien d'autre — la barre réduite décide
            // ensuite de ce qui s'y dit. Sa place est celle de l'original, entre « Écouter » et
            // « Marque-page ».
            if (onRecord != null) {
                AppIconButton(
                    icon = Icons.Outlined.Mic,
                    label = "Enregistrer",
                    onClick = onRecord,
                )
            }

            // Poser un signet, ou retrouver ceux qu'on a posés. Le libellé est celui du
            // panneau : c'est le même mot, et deux chaînes pour la même chose finiraient par
            // diverger. Absent quand l'appelant ne sait pas enregistrer de signet.
            if (onOpenBookmarks != null) {
                AppIconButton(
                    icon = if (bookmarkActive) {
                        Icons.Filled.Bookmark
                    } else {
                        Icons.Outlined.BookmarkBorder
                    },
                    label = BookmarksText.PANEL_TITLE,
                    onClick = onOpenBookmarks,
                    // L'état actif est un **fond**, et non une teinte : c'est ce que fait la
                    // barre d'origine, qui pose `colors.soft` derrière l'action sélectionnée.
                    // Changer la couleur de l'icône inventerait un autre langage visuel.
                    background = if (bookmarkActive) colors.soft else Color.Transparent,
                )
            }

            // Le carrefour du lecteur : sourate, traduction, écoute, présentation. C'est le
            // seul chemin vers le sélecteur de sourate, comme dans le client d'origine, où
            // « Plus » est la dernière des cinq actions. Absent quand l'appelant n'a aucune
            // destination à proposer : une feuille réduite à son titre serait une impasse.
            if (onOpenOptions != null) {
                AppIconButton(
                    icon = Icons.Outlined.MoreHoriz,
                    // Le libellé est celui de la feuille : c'est le même mot, et deux chaînes
                    // pour la même chose finiraient par diverger.
                    label = ReaderOptionsText.TITLE,
                    onClick = onOpenOptions,
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
