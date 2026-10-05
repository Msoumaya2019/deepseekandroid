package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.msoumaya.deepseekandroid.core.design.component.AppButton
import com.msoumaya.deepseekandroid.core.design.component.AppCard
import com.msoumaya.deepseekandroid.core.design.component.AppIconButton
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.component.AppTitle
import com.msoumaya.deepseekandroid.core.design.component.ArabicText
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.Bookmarks
import com.msoumaya.deepseekandroid.core.domain.BookmarksText

/**
 * L'écran des marques-pages : la liste des signets, et les trois gestes qui les accompagnent.
 *
 * Porté depuis `src/BookmarksScreen.tsx`. L'en-tête ne vient pas de `AppBackTopBar` : celle-ci
 * **centre** son titre, alors que le source pose une flèche de retour, puis un titre et un
 * sous-titre alignés à gauche. L'écart se lit à l'œil, donc l'en-tête est écrit ici.
 *
 * ## Les lignes sont calculées par l'appelant
 *
 * [rows] arrive déjà résolue : nom de sourate, numéro de verset, texte, et **page dans le
 * découpage de la source affichée**. C'est `Bookmarks.rows` qui la produit, et c'est aussi elle
 * qui **omet** un signet hors corpus au lieu de faire tomber l'écran. La vue n'a donc aucun
 * accès au référentiel — une ligne qui existe est une ligne qui s'affiche, et une ligne qui
 * n'existe pas ne fait rien tomber.
 *
 * ## Écart assumé : l'écran est une fenêtre, le lecteur reste monté
 *
 * Le client d'origine **remplace** le lecteur par cet écran. Ici, c'est une fenêtre plein écran
 * posée par-dessus, comme le sélecteur de sourate — et ce n'est pas un détail de présentation :
 * remplacer le lecteur le **démonterait**, ce qui libérerait le lecteur audio et **arrêterait
 * l'écoute** au moment précis où l'on consulte ses marques-pages. La fenêtre garde la séance
 * vivante ; le retour la retrouve exactement où elle était.
 *
 * ## Deux tailles en dur, ramenées au jeton le plus proche
 *
 * Le source pose 18 pour le nom de sourate et 23 pour le texte coranique. Ni l'une ni l'autre
 * n'est dans `tokens.ts` : ce sont `card` (17) et `arabic` (22) qui s'en approchent le plus, à un
 * point près. C'est la règle déjà suivie par le sélecteur de sourate, et l'écart est le même.
 *
 * @param rows les lignes à afficher, dans l'ordre, déjà résolues et déjà filtrées.
 * @param onClose revient à la lecture.
 * @param onResume reprend un signet : l'appelant ouvre sa page et l'enregistre comme reprise.
 * @param onDelete supprime un signet. L'écran **ne supprime pas** lui-même : il demande
 *   confirmation, puis délègue — c'est le seul endroit qui connaisse l'état.
 *
 * Public, et non `internal` comme les feuilles du lecteur : c'est la **route** qui l'affiche,
 * et non `ReaderScreen` — elle seule connaît l'état du compte et la source affichée, dont
 * dépend la page de chaque signet.
 */
@Composable
fun BookmarksScreen(
    rows: List<Bookmarks.Row>,
    onClose: () -> Unit,
    onResume: (Int) -> Unit,
    onDelete: (Int) -> Unit,
) {
    val colors = AppTheme.colors

    // Le signet dont la suppression attend confirmation. **Sauvegardé** : une rotation pendant
    // la confirmation ne doit pas la faire disparaître en laissant croire que rien n'a été
    // demandé — ni, pire, laisser la suppression partir sans que personne ne l'ait confirmée.
    var pendingDelete by rememberSaveable { mutableStateOf<Int?>(null) }

    pendingDelete?.let { verseId ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = {
                AppLabel(
                    text = BookmarksText.DELETE_TITLE,
                    fontSize = AppTheme.typeScale.card,
                    fontWeight = FontWeight.Bold,
                    selectable = false,
                )
            },
            text = {
                AppLabel(text = BookmarksText.DELETE_BODY, selectable = false)
            },
            confirmButton = {
                AppButton(
                    text = BookmarksText.DELETE_CONFIRM,
                    small = true,
                    onClick = {
                        pendingDelete = null
                        onDelete(verseId)
                    },
                )
            },
            // L'ordre du source est « Annuler » puis « Supprimer ». En Compose, `dismissButton`
            // est posé à gauche de `confirmButton` : la disposition est donc la même, et les
            // deux rôles sont nommés plutôt que devinés par leur position.
            dismissButton = {
                AppButton(
                    text = BookmarksText.DELETE_CANCEL,
                    secondary = true,
                    small = true,
                    onClick = { pendingDelete = null },
                )
            },
            containerColor = colors.paper,
        )
    }

    Dialog(
        onDismissRequest = onClose,
        // Sans cela, la fenêtre se limite à la largeur d'un téléphone en paysage et la liste
        // flotte au milieu de l'écran. L'écran doit occuper la page, comme le source.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = colors.cream) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppIconButton(
                        icon = Icons.Outlined.ChevronLeft,
                        label = BookmarksText.BACK,
                        onClick = onClose,
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        AppTitle(text = BookmarksText.TITLE)
                        AppLabel(
                            text = BookmarksText.SUBTITLE,
                            color = colors.muted,
                            fontSize = AppTheme.typeScale.secondary,
                            selectable = false,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 30.dp),
                ) {
                    // L'explication est posée sur le fond doux du thème, comme le source : c'est
                    // ce qui la distingue d'une ligne de signet, qui est une carte blanche.
                    AppCard(background = colors.soft) {
                        AppLabel(text = BookmarksText.EXPLANATION, selectable = false)
                    }

                    // L'état vide est une carte **blanche**, et non douce : le source l'écrit
                    // ainsi, et le contraste avec la carte d'explication est ce qui la fait lire
                    // comme une ligne à venir plutôt que comme une consigne.
                    if (rows.isEmpty()) {
                        AppCard {
                            AppLabel(text = BookmarksText.EMPTY, selectable = false)
                        }
                    }

                    for (row in rows) {
                        BookmarkRowCard(
                            row = row,
                            onResume = { onResume(row.verseId) },
                            onDelete = { pendingDelete = row.verseId },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Une ligne de la liste : la sourate, la page et le verset, le texte, puis « Reprendre ».
 *
 * L'ordre est celui du source : l'en-tête, puis — **entre** l'en-tête et le texte — la mention
 * « Dernière reprise ». Elle est donc lue avant le verset qu'elle qualifie, ce qui est le bon
 * ordre : on cherche d'abord où l'on s'était arrêté.
 */
@Composable
private fun BookmarkRowCard(
    row: Bookmarks.Row,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AppTheme.colors

    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Décorative : le nom de la sourate et le numéro du verset sont juste à côté, et
            // les répéter à l'oral n'ajouterait rien à qui écoute la ligne.
            Icon(
                imageVector = Icons.Filled.Bookmark,
                contentDescription = null,
                tint = colors.green,
                modifier = Modifier.size(24.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                AppLabel(
                    text = row.surahName,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = AppTheme.typeScale.card,
                    maxLines = 1,
                    selectable = false,
                )
                AppLabel(
                    text = BookmarksText.pageLabel(row.page, row.ayah),
                    color = colors.muted,
                    fontSize = AppTheme.typeScale.secondary,
                    selectable = false,
                )
            }

            AppIconButton(
                icon = Icons.Outlined.MoreVert,
                label = BookmarksText.deleteLabel(row.surahName, row.ayah),
                onClick = onDelete,
            )
        }

        if (row.lastUsed) {
            AppLabel(
                text = BookmarksText.LAST_USED,
                modifier = Modifier.padding(top = 8.dp),
                color = colors.green,
                fontSize = AppTheme.typeScale.metadata,
                selectable = false,
            )
        }

        // Le texte coranique se lit de droite à gauche, et il est borné à deux lignes : une ligne
        // de signet doit rester une ligne, sinon la liste devient illisible dès quelques versets
        // longs. Le source fait le même choix, et c'est `ArabicText` qui porte l'interligne.
        ArabicText(
            text = row.text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            textAlign = TextAlign.Right,
            maxLines = ROW_TEXT_MAX_LINES,
        )

        AppButton(
            text = BookmarksText.RESUME,
            secondary = true,
            onClick = onResume,
        )
    }
}

/**
 * Deux lignes de texte coranique par signet, comme le `numberOfLines={2}` du source.
 *
 * La borne n'est pas cosmétique : sans elle, un verset long — et il y en a — repousserait les
 * boutons de sa propre carte hors de l'écran, et « Reprendre » deviendrait inatteignable sur la
 * ligne qu'on veut justement reprendre.
 */
private const val ROW_TEXT_MAX_LINES = 2
