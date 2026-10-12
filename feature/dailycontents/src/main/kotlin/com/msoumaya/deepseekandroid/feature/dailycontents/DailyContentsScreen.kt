package com.msoumaya.deepseekandroid.feature.dailycontents

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.msoumaya.deepseekandroid.core.data.LocalAppContainer
import com.msoumaya.deepseekandroid.core.design.component.AppButton
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.component.AppTitle
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.DailyContentsText
import com.msoumaya.deepseekandroid.core.model.DailyContentType

// ---------------------------------------------------------------------------
// L'écran de liste des contenus du jour
// ---------------------------------------------------------------------------
// Portage de `DailyContentsScreen` (`src/DailyContentsScreen.tsx:53`). Le calcul vit dans
// `DailyContentsListRenderer` — pur, donc éprouvé sans appareil —, et ce fichier ne fait que
// **disposer** : il pose les textes que le renderer a résolus et branche les gestes.
//
// **La fiche remplace l'écran entier.** L'original écrit `if(detail)return <ScrollView>…` : la
// fiche n'est pas une feuille posée sur la liste, c'est **le même écran** dans un autre état. Le
// bouton de retour et le titre restent donc à la même place, et la liste n'est pas composée
// derrière — c'est ce qui garantit qu'un retour ne laisse pas deux listes empilées.
//
// **Le geste du carrousel et celui de la liste mènent au même endroit.** Une fiche ouverte **par
// son identifiant** — depuis le carrousel de l'accueil, ou depuis une notification — est la raison
// d'être de l'écran : son bouton « Retour » ressort donc entièrement. Une fiche ouverte depuis la
// liste, elle, y ramène. La décision est prise par `DailyContentsListRenderer.detailBackExits`, et
// non ici : c'est une règle, donc elle se mesure.
//
// **Ce qui n'est pas porté.** L'écoute de la fiche, l'enregistrement de la prononciation et l'avis
// local de la carte : `DailyContentCard` porte les trois absences, et les raisons avec elles.
//
// **Un écart assumé sur la zone tactile.** Le libellé « Ouvrir la fiche › » de l'original fait
// quinze pixels de haut, et c'est pourtant la **seule** façon d'ouvrir une fiche depuis la liste.
// Le portage lui donne la hauteur minimale des autres commandes du dépôt — 44 px —, sans quoi le
// geste le plus fréquent de l'écran serait le plus difficile à viser. L'écart coûte de la hauteur,
// et il est visible : c'est le prix de la seule correction que cet écran s'autorise.
// ---------------------------------------------------------------------------

/** Marge latérale de l'écran, comme celle des autres écrans de liste (`padding:18` à la source). */
private val SCREEN_PADDING = 18.dp

/** Marge basse du contenu (`paddingBottom:40` à la source) : la dernière carte ne colle pas au bord. */
private val SCREEN_BOTTOM = 40.dp

/** Hauteur minimale du geste qui ouvre la fiche. Voir l'en-tête du fichier. */
private val OPEN_MIN_HEIGHT = 44.dp

/** Écart entre deux commandes de l'en-tête (`gap:8` et `marginLeft:8` à la source). */
private val HEADER_GAP = 8.dp

/** Marge verticale de la rangée d'onglets (`marginVertical:12` à la source). */
private val TABS_PADDING = 12.dp

/** Marge basse du libellé d'ouverture (`marginBottom:5` à la source). */
private val OPEN_LABEL_BOTTOM = 5.dp

/**
 * La liste des contenus du jour, et la fiche qu'elle ouvre.
 *
 * Écran **plein écran** : il n'a ni onglet ni barre supérieure, donc il porte lui-même son bouton
 * de retour, et c'est la coquille qui décide où l'on retourne.
 *
 * @param onClose fermeture de l'écran. La coquille y branche son retour.
 * @param initialId le contenu **à ouvrir** en arrivant, ou `null`. C'est l'`initialId` de
 *   l'original : le carrousel de l'accueil le porte, et c'est lui qui décide aussi de ce que fait
 *   le bouton « Retour » de la fiche.
 * @param viewModel état de l'écran. Par défaut, celui du conteneur applicatif.
 */
@Composable
fun DailyContentsScreen(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    initialId: String? = null,
    viewModel: DailyContentsViewModel = viewModel(
        factory = DailyContentsViewModel.factory(LocalAppContainer.current, initialId),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DailyContentsContent(
        state = state,
        modifier = modifier,
        onClose = onClose,
        initialId = initialId,
        // Le dépôt des médias est passé par sa **fonction**, pas par son objet : une carte n'a
        // rien à connaître de `core:data`, et une prévisualisation peut alors la remplacer.
        resolve = viewModel.media::resolve,
        onSelectType = viewModel::selectType,
        onSelectCategory = viewModel::selectCategory,
        onToggleFavorites = viewModel::toggleFavorites,
        onLoadMore = viewModel::loadMore,
        onOpen = viewModel::openDetail,
        onCloseDetail = viewModel::closeDetail,
        onToggleFavorite = viewModel::setFavorite,
    )
}

/**
 * L'écran, à partir d'un état déjà résolu.
 *
 * Séparé de [DailyContentsScreen] pour la même raison qu'ailleurs : l'état se fabrique à la main
 * dans une prévisualisation, sans conteneur, sans réseau et sans compte.
 *
 * @param resolve signature d'une adresse de média, transmise aux fiches. La valeur par défaut
 *   rend l'adresse telle quelle : c'est exactement ce que fait le dépôt quand aucun projet
 *   Supabase n'est configuré, donc une prévisualisation hors ligne montre la même chose.
 * @param onOpen ouverture d'une fiche, **par identifiant** — le seul jeton que le rendu publie.
 */
@Composable
internal fun DailyContentsContent(
    state: DailyListState,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    initialId: String? = null,
    resolve: suspend (String?) -> String? = { it },
    onSelectType: (DailyContentType) -> Unit = {},
    onSelectCategory: (String?) -> Unit = {},
    onToggleFavorites: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onOpen: (String) -> Unit = {},
    onCloseDetail: () -> Unit = {},
    onToggleFavorite: (String, Boolean) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val share: (String) -> Unit = { text -> shareText(context, text) }

    val detail = state.detail
    if (detail != null) {
        // L'original **sort** de sa liste : `if(detail)return <ScrollView>…`. La fiche remplace
        // donc l'écran, et la liste n'est pas composée derrière elle.
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SCREEN_PADDING)
                .padding(bottom = SCREEN_BOTTOM),
        ) {
            BackButton(
                onPress = {
                    // Une fiche ouverte **par son identifiant** est la raison d'être de l'écran :
                    // « Retour » en ressort entièrement. Une fiche ouverte depuis la liste y
                    // ramène. La règle est mesurée, elle n'est pas décidée ici.
                    if (DailyContentsListRenderer.detailBackExits(initialId)) {
                        onClose()
                    } else {
                        onCloseDetail()
                    }
                },
            )
            AppTitle(text = DailyContentsText.LIST_TITLE, maxLines = Int.MAX_VALUE)
            DailyContentCard(
                card = DailyContentsListRenderer.card(
                    content = detail,
                    favorite = state.favoriteIds.contains(detail.id),
                ),
                resolve = resolve,
                onToggleFavorite = {
                    onToggleFavorite(detail.id, !state.favoriteIds.contains(detail.id))
                },
                onShare = share,
                // **La fiche, elle, reçoit le message.** Elle n'a pas d'en-tête visible sous
                // lequel le poser, contrairement à la liste, et l'original le pose au bas de sa
                // carte. Voir l'écart n° 3 de `DailyContentCard`.
                notice = state.error?.takeIf { it.isNotBlank() },
            )
        }
        return
    }

    val header = DailyContentsListRenderer.header(state)
    val body = DailyContentsListRenderer.body(state)
    val footer = DailyContentsListRenderer.footer(state)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SCREEN_PADDING)
            .padding(bottom = SCREEN_BOTTOM),
    ) {
        BackButton(onPress = onClose)

        // Le titre respire sur deux lignes si la police est large : l'original n'impose aucune
        // limite, et « Rappels & Invocations » n'y tient pas toujours sur une seule.
        AppTitle(text = DailyContentsText.LIST_TITLE, maxLines = Int.MAX_VALUE)

        // Les deux onglets : deux boutons de largeur **égale**, l'actif plein et l'autre bordé.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = TABS_PADDING),
            horizontalArrangement = Arrangement.spacedBy(HEADER_GAP),
        ) {
            header.tabs.forEach { tab ->
                AppButton(
                    text = tab.label,
                    onClick = { onSelectType(tab.type) },
                    secondary = !tab.selected,
                    small = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Les pastilles : « Tout » **retire** le filtre, les autres le posent. La rangée défile
        // horizontalement, comme l'original — une base peut porter plus de catégories que l'écran
        // n'a de largeur.
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(HEADER_GAP),
        ) {
            header.categories.forEach { chip ->
                AppButton(
                    text = chip.label,
                    onClick = { onSelectCategory(chip.id) },
                    secondary = !chip.selected,
                    small = true,
                )
            }
        }

        AppButton(
            text = header.favorites.label,
            onClick = onToggleFavorites,
            secondary = !header.favorites.selected,
            small = true,
        )

        // Le message d'erreur reste **dans l'en-tête**, où il demeure visible même quand la liste
        // porte des lignes — c'est la place que lui donne l'original.
        header.error?.let { message ->
            AppLabel(text = message, selectable = false)
        }

        when (body) {
            is DailyListBody.Empty -> AppLabel(text = body.message, selectable = false)

            is DailyListBody.Rows -> body.rows.forEach { row ->
                DailyRow(
                    row = row,
                    resolve = resolve,
                    onOpen = { onOpen(row.id) },
                    onToggleFavorite = { onToggleFavorite(row.id, !row.card.favorite) },
                    onShare = share,
                )
            }
        }

        // **Le pied est posé après le corps, et même quand le corps est vide** : c'est ce que
        // produit la liste de l'original, dont le `ListFooterComponent` ne dépend pas des lignes.
        // Voir l'écart n° 2 du renderer. Le bouton est **inactif** pendant une lecture, comme
        // l'original : appuyer deux fois empilerait deux fois la même page.
        footer?.let { label ->
            AppButton(
                text = label,
                onClick = onLoadMore,
                secondary = true,
                enabled = !state.busy,
            )
        }
    }
}

/**
 * Une ligne de la liste : le geste qui ouvre la fiche, puis la fiche.
 *
 * **Le libellé visible est constant** — « Ouvrir la fiche › » —, et ce que le lecteur d'écran
 * annonce est `row.openLabel`, résolu par le renderer. Les deux ne disent pas la même chose :
 * l'un nomme le geste, l'autre le contenu.
 */
@Composable
private fun DailyRow(
    row: DailyListRow,
    resolve: suspend (String?) -> String?,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: (String) -> Unit,
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = OPEN_MIN_HEIGHT)
                .semantics(mergeDescendants = true) { contentDescription = row.openLabel }
                .clickable(role = Role.Button, onClick = onOpen),
            contentAlignment = Alignment.CenterStart,
        ) {
            AppLabel(
                text = DailyContentsText.OPEN_SHEET,
                selectable = false,
                fontSize = AppTheme.typeScale.secondary,
                color = AppTheme.colors.green,
                modifier = Modifier.padding(bottom = OPEN_LABEL_BOTTOM),
            )
        }

        DailyContentCard(
            card = row.card,
            resolve = resolve,
            onToggleFavorite = onToggleFavorite,
            onShare = onShare,
        )
    }
}

/** Le bouton de retour, **identique dans les deux vues** — c'est ce qui les fait se ressembler. */
@Composable
private fun BackButton(onPress: () -> Unit) {
    AppButton(
        text = DailyContentsText.BACK,
        onClick = onPress,
        secondary = true,
    )
}

/**
 * Ouvre la feuille de partage du système sur [text].
 *
 * C'est exactement ce que faisait `Share.share({message})` côté React Native : un `ACTION_SEND` de
 * texte présenté par le sélecteur. Le titre du sélecteur n'est **pas** fourni, comme dans
 * l'original, qui n'en passe aucun.
 *
 * Rien n'est rattrapé : le sélecteur du système répond à tous les coups, même quand aucune
 * application ne sait ouvrir un texte — il propose alors le presse-papiers. C'est la règle déjà
 * tenue par le partage d'un lien d'invitation (`feature:social`).
 */
private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null))
}
