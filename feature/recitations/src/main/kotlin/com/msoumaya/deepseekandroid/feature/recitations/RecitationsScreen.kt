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
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.RecitationText

// ---------------------------------------------------------------------------
// L'écran « Mes récitations »
// ---------------------------------------------------------------------------
// Portage de `RecitationsScreen` (`src/RecitationsScreen.tsx`), dont le calcul vit dans
// `RecitationsRenderer` — pur, donc éprouvé sans appareil. Ce fichier ne fait que **disposer**.
//
// **Ce que l'écran n'offre pas encore, et pourquoi.** L'original a trois gestes de plus :
// réécouter, avancer ou reculer de dix secondes, et partager avec un ami. Aucun des trois n'est
// ici, et ce n'est pas un oubli :
//
//  - **écouter** demande une couche audio branchée dans ce module, et un bouton de lecture qui ne
//    joue rien est exactement le geste mort que ce dépôt s'interdit ;
//  - **partager** n'a de capacité dans **aucune** couche du portage : `shareRecitation` n'existe
//    nulle part, et l'écran ne doit pas mener à une porte qui n'existe pas.
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
 * Une récitation de la liste.
 *
 * **Les cartes du professeur ne s'affichent que sous la ligne ouverte** : l'état ne les porte que
 * pour elle — le rendu les vide dès que l'étiquette change —, donc une carte sous une autre ligne
 * serait le signe que deux vérités ont divergé.
 *
 * @param suppressionDemandee vrai quand la personne a touché « Supprimer » sur **cette** ligne. La
 *   confirmation est demandée **dans la carte**, et non dans une boîte de dialogue : le dépôt n'a
 *   pas d'outillage d'interface, et une confirmation qui vit dans la ligne se vérifie à l'œil.
 */
@Composable
private fun RecitationRowCard(
    row: RecitationRow,
    suppressionDemandee: Boolean,
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

        feedback.forEach { carte ->
            AppLabel(text = carte.title, selectable = false)
            AppLabel(
                text = carte.comment ?: RecitationText.FEEDBACK_FALLBACK,
                selectable = false,
            )
        }

        corrections.forEach { carte ->
            AppLabel(text = carte.label, selectable = false)
            AppLabel(
                text = carte.comment ?: RecitationText.CORRECTION_FALLBACK,
                selectable = false,
            )
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
