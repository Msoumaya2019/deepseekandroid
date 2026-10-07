package com.msoumaya.deepseekandroid.feature.recitations

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.msoumaya.deepseekandroid.core.data.LocalAppContainer
import com.msoumaya.deepseekandroid.core.design.component.AppButton
import com.msoumaya.deepseekandroid.core.design.component.AppCard
import com.msoumaya.deepseekandroid.core.design.component.AppHero
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.component.AppSegmentedControl
import com.msoumaya.deepseekandroid.core.design.component.AppTitle
import com.msoumaya.deepseekandroid.core.design.component.ProgressTrack
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.RecitationText

// ---------------------------------------------------------------------------
// L'écran « Mes récitations »
// ---------------------------------------------------------------------------
// Portage de `RecitationsScreen` (`src/RecitationsScreen.tsx`), dont le calcul vit dans
// `RecitationsRenderer` — pur, donc éprouvé sans appareil. Ce fichier ne fait que **disposer**.
//
// **Ce que l'écran n'offre pas encore, et pourquoi.** L'original a un geste de plus : partager une
// récitation avec un ami. Il n'est pas ici, et ce n'est pas un oubli : `shareRecitation` n'a de
// capacité dans **aucune** couche du portage, et l'écran ne doit pas mener à une porte qui
// n'existe pas.
//
// **L'écoute n'apparaît que si un lecteur existe** — c'est `state.canListen`, et il ne décide pas
// seulement de l'affichage : c'est la présence réelle d'un lecteur qui l'alimente. Un bouton de
// lecture qui ne joue rien est le geste mort que ce dépôt s'interdit, et il le serait ici pour la
// raison la plus banale : aucun lecteur n'a été fourni au conteneur.
//
// **Le repère « locale seule » n'est pas affiché.** Le rendu le porte (`RecitationRow.localOnly`),
// et il servira à masquer les gestes qui exigent le serveur — mais aucune phrase de l'original ne
// le nomme, et ce portage n'en invente pas. Le sous-titre dit déjà ce qu'il faut savoir : le
// statut de synchronisation.
// ---------------------------------------------------------------------------

/** Marge latérale de l'écran, comme celle des autres écrans de liste. */
internal val SCREEN_PADDING = 18.dp

/**
 * La liste des récitations enregistrées.
 *
 * Écran **plein écran** : il n'a ni onglet ni barre supérieure, donc il porte lui-même son bouton
 * de retour, et c'est la coquille qui décide où l'on retourne.
 *
 * @param onClose fermeture de l'écran. La coquille y branche son retour.
 * @param viewModel état de l'écran. Par défaut, celui du conteneur applicatif.
 */
@Composable
fun RecitationsScreen(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    viewModel: RecitationsViewModel = viewModel(
        factory = RecitationsViewModel.factory(LocalAppContainer.current),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Le dépôt charge déjà à sa construction, mais il ne sait pas quand l'écran s'ouvre : c'est la
    // seule chose que l'écran lui apprend. Le rappel est sans effet si un chargement est en cours,
    // donc l'ouverture immédiate après la construction ne coûte pas une seconde lecture.
    LaunchedEffect(Unit) { viewModel.onVisible() }

    RecitationsContent(
        state = state,
        modifier = modifier,
        onClose = onClose,
        onRefresh = viewModel::onRefresh,
        onFilterSelected = viewModel::onFilterSelected,
        onOpen = viewModel::onOpen,
        onDelete = viewModel::onDelete,
        onPlayPause = viewModel::onPlayPause,
        onSeekBackward = viewModel::onSeekBackward,
        onSeekForward = viewModel::onSeekForward,
    )
}

/**
 * L'écran, à partir d'un état déjà résolu.
 *
 * Séparé de [RecitationsScreen] pour la même raison qu'ailleurs : l'état se fabrique à la main
 * dans une prévisualisation, sans conteneur, sans réseau et sans compte.
 */
@Composable
internal fun RecitationsContent(
    state: RecitationsUiState,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onFilterSelected: (String) -> Unit = {},
    onOpen: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onPlayPause: () -> Unit = {},
    onSeekBackward: () -> Unit = {},
    onSeekForward: () -> Unit = {},
) {
    // La récitation dont la suppression est **demandée**, en attente de confirmation.
    //
    // **Non sauvegardée**, contrairement à la confirmation de suppression des signets : ici rien
    // n'est en attente côté serveur, et une rotation referme simplement la demande. Sauvegarder
    // l'identifiant demanderait un `Saver` pour une ligne dont la seule raison d'être est d'être à
    // l'écran pendant qu'on la regarde.
    var aSupprimer by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = SCREEN_PADDING,
                vertical = AppTheme.spacing.sm,
            ),
        ) {
            AppButton(
                text = RecitationText.LIST_BACK,
                onClick = onClose,
                secondary = true,
                small = true,
            )
        }

        // **Le bandeau passe avant la bande d'en-tête**, comme dans l'original : c'est un message
        // transitoire — une panne, une invitation à se connecter —, et le repousser sous une
        // illustration pleine largeur le rendrait invisible tant qu'on n'a pas fait défiler.
        state.message?.let { message ->
            Box(
                modifier = Modifier.padding(
                    horizontal = SCREEN_PADDING,
                    vertical = AppTheme.spacing.sm,
                ),
            ) {
                AppCard(spaced = false) {
                    AppLabel(text = message, selectable = false)
                }
            }
        }

        AppHero(title = RecitationText.LIST_TITLE, subtitle = RecitationText.LIST_SUBTITLE)

        Column(modifier = Modifier.padding(horizontal = SCREEN_PADDING)) {
            if (state.filterLabels.isNotEmpty()) {
                AppSegmentedControl(
                    options = state.filterLabels,
                    value = state.filterLabel,
                    onSelect = onFilterSelected,
                )
            }

            Box(modifier = Modifier.padding(vertical = AppTheme.spacing.sm)) {
                AppButton(
                    text = RecitationText.LIST_REFRESH,
                    onClick = onRefresh,
                    secondary = true,
                )
            }

            if (state.empty) {
                AppCard {
                    AppLabel(text = RecitationText.LIST_EMPTY, selectable = false)
                }
            }

            state.rows.forEach { ligne ->
                RecitationRowCard(
                    row = ligne,
                    suppressionDemandee = aSupprimer == ligne.id,
                    ecoute = if (state.canListen && ligne.open) {
                        RecitationAudio(
                            playLabel = state.playLabel,
                            positionLabel = state.positionLabel,
                            progressPercent = state.progressPercent,
                            onPlayPause = onPlayPause,
                            onSeekBackward = onSeekBackward,
                            onSeekForward = onSeekForward,
                        )
                    } else {
                        null
                    },
                    feedback = state.feedback,
                    corrections = state.corrections,
                    onOpen = { onOpen(ligne.id) },
                    onDemanderSuppression = { aSupprimer = ligne.id },
                    onAnnulerSuppression = { aSupprimer = null },
                    onConfirmerSuppression = {
                        aSupprimer = null
                        onDelete(ligne.id)
                    },
                )
            }
        }
    }
}

/**
 * Les gestes d'écoute d'une ligne, réunis.
 *
 * **Un porteur, et non six paramètres de plus.** Les six valeurs vont toujours ensemble — elles ne
 * concernent que la ligne ouverte, et l'écran les rassemble en un seul endroit. Les passer une à
 * une ferait une signature de quatorze paramètres, où l'oubli d'un rappel ne se verrait pas.
 *
 * `null` veut dire **pas d'écoute du tout** : ou bien aucun lecteur n'est disponible, ou bien cette
 * ligne n'est pas la ligne ouverte. L'écran ne pose alors ni bouton, ni avance, ni barre — plutôt
 * qu'un bouton inerte.
 *
 * @param playLabel « ▶ Réécouter » ou « Pause », déjà résolu par le rendu.
 * @param positionLabel « Position : 0:12 / 1:04 · Corrigée ».
 * @param progressPercent le remplissage de la barre, de 0 à 100.
 */
private data class RecitationAudio(
    val playLabel: String,
    val positionLabel: String,
    val progressPercent: Float,
    val onPlayPause: () -> Unit,
    val onSeekBackward: () -> Unit,
    val onSeekForward: () -> Unit,
)

/**
 * Une récitation de la liste.
 *
 * **Les cartes du professeur ne s'affichent que sous la ligne ouverte** : l'état ne les porte que
 * pour elle — le rendu les vide dès que l'étiquette change —, donc une carte sous une autre ligne
 * serait le signe que deux vérités ont divergé.
 *
 * @param suppressionDemandee vrai quand la personne a touché « Supprimer » sur **cette** ligne. La
 *   confirmation est demandée **dans la carte**, et non dans une boîte de dialogue : le dépôt n'a
 *   pas d'outillage d'interface, et une confirmation qui vit dans la ligne se vérifie à l'œil.
 * @param ecoute les gestes d'écoute, ou `null` quand il n'y a rien à écouter.
 */
@Composable
private fun RecitationRowCard(
    row: RecitationRow,
    suppressionDemandee: Boolean,
    ecoute: RecitationAudio?,
    feedback: List<FeedbackRow>,
    corrections: List<CorrectionRow>,
    onOpen: () -> Unit,
    onDemanderSuppression: () -> Unit,
    onAnnulerSuppression: () -> Unit,
    onConfirmerSuppression: () -> Unit,
) {
    AppCard(onClick = onOpen) {
        AppTitle(text = row.title)

        if (row.subtitle.isNotEmpty()) {
            AppLabel(text = row.subtitle, selectable = false)
        }

        if (!row.open) return@AppCard

        // **L'écoute passe avant les cartes du professeur**, comme dans l'original : on relit
        // d'abord ce qu'on a enregistré, et les remarques viennent après.
        ecoute?.let { gestes ->
            AppButton(
                text = gestes.playLabel,
                onClick = gestes.onPlayPause,
                small = true,
            )

            Row(modifier = Modifier.padding(top = AppTheme.spacing.sm)) {
                Box(modifier = Modifier.weight(1f)) {
                    AppButton(
                        text = RecitationText.SEEK_BACK,
                        onClick = gestes.onSeekBackward,
                        secondary = true,
                        small = true,
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(start = AppTheme.spacing.sm)
                        .weight(1f),
                ) {
                    AppButton(
                        text = RecitationText.SEEK_FORWARD,
                        onClick = gestes.onSeekForward,
                        secondary = true,
                        small = true,
                    )
                }
            }

            Box(modifier = Modifier.padding(top = AppTheme.spacing.sm)) {
                // Le composant attend une fraction, l'état porte un pourcentage : la division est
                // faite ici, au dernier moment, plutôt que de faire porter deux unités au même
                // nombre. La barre borne de toute façon ce qu'elle reçoit.
                ProgressTrack(value = gestes.progressPercent / 100f)
            }

            if (gestes.positionLabel.isNotEmpty()) {
                AppLabel(text = gestes.positionLabel, selectable = false)
            }
        }

        // **Le repli n'est pas répété ici.** `FeedbackRow.comment` et `CorrectionRow.comment` sont
        // non nuls : c'est le rendu qui a déjà remplacé le commentaire absent par « Commentaire
        // vocal du professeur » ou « À retravailler ». Le refaire ici serait du code mort — et le
        // compilateur le dit, ce qui vaut mieux qu'un repli qui ne s'applique jamais.
        feedback.forEach { carte ->
            AppLabel(text = carte.title, selectable = false)
            AppLabel(text = carte.comment, selectable = false)
        }

        corrections.forEach { carte ->
            AppLabel(text = carte.label, selectable = false)
            AppLabel(text = carte.comment, selectable = false)
            carte.date?.let { jour -> AppLabel(text = jour, selectable = false) }
        }

        Box(modifier = Modifier.padding(top = AppTheme.spacing.sm)) {
            if (suppressionDemandee) {
                Column {
                    AppLabel(text = RecitationText.DELETE_TITLE, selectable = false)
                    AppLabel(text = RecitationText.DELETE_BODY, selectable = false)
                    Row {
                        AppButton(
                            text = RecitationText.DELETE_CONFIRM,
                            onClick = onConfirmerSuppression,
                            small = true,
                        )
                        Box(modifier = Modifier.padding(start = AppTheme.spacing.sm)) {
                            AppButton(
                                text = RecitationText.CANCEL,
                                onClick = onAnnulerSuppression,
                                secondary = true,
                                small = true,
                            )
                        }
                    }
                }
            } else {
                AppButton(
                    text = RecitationText.DELETE_CONFIRM,
                    onClick = onDemanderSuppression,
                    secondary = true,
                    small = true,
                )
            }
        }
    }
}
