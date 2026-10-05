package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msoumaya.deepseekandroid.core.design.component.AppButton
import com.msoumaya.deepseekandroid.core.design.component.AppCard
import com.msoumaya.deepseekandroid.core.design.component.AppField
import com.msoumaya.deepseekandroid.core.design.component.AppHeading
import com.msoumaya.deepseekandroid.core.design.component.AppHero
import com.msoumaya.deepseekandroid.core.design.component.AppIconButton
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.component.AppSegmentedControl
import com.msoumaya.deepseekandroid.core.design.component.ArabicText
import com.msoumaya.deepseekandroid.core.design.component.QuranNumberMedallion
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.design.theme.ReadingArt
import com.msoumaya.deepseekandroid.core.domain.QuranText
import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.Range

// ---------------------------------------------------------------------------
// L'écran du Coran : la liste des sourates, des juz’ et des hizb
// ---------------------------------------------------------------------------
// Portage de `QuranScreen` (`src/ui/MainScreens.tsx`). Ce n'est **pas** le lecteur : l'écran
// d'origine porte deux écrans distincts que leurs noms ne distinguent pas. Celui-ci liste, et son
// ouverture mène au moushaf page à page.
//
// **L'écran ne connaît ni le conteneur ni le stockage.** C'est la contrainte du module : le lecteur
// doit s'ouvrir en avion, donc `feature:reader` ne dépend pas de `core:data`. L'écran reçoit l'état
// du compte et rend ce qu'on lui demande d'ouvrir ; c'est `QuranRoute`, dans `navigation`, qui lit
// l'état et écrit. C'est le même partage que pour `ReaderScreen`.
//
// **Trois états d'interface vivent ici, et trois seulement** : la vue, la recherche et le filtre.
// Le client d'origine les tenait déjà dans son composant. Ils sont `rememberSaveable` : une
// rotation ne doit ni ramener la liste à la première vue, ni effacer une recherche en cours.
//
// **Changer de vue efface la recherche**, et c'est la règle de l'original — `setQuery('')` dans le
// `onChange` du sélecteur. Elle n'est pas cosmétique : les champs cherchés diffèrent d'une vue à
// l'autre — « Al-Baqara » ne désigne rien dans la liste des hizb —, donc garder la saisie donnerait
// une liste vide sans que rien ne dise pourquoi.
//
// **La bande d'en-tête est hors du retrait.** Le client d'origine l'englobe dans un conteneur en
// retrait de 18 px et la remonte de −18 px pour occuper toute la largeur. Ici la bande est un
// élément de liste **sans** retrait, et le retrait est porté par les éléments qui le demandent :
// même résultat, sans marge négative.
// ---------------------------------------------------------------------------

/** Retrait horizontal du contenu, valeur de la source (`paddingHorizontal: 18`). */
private val SCREEN_PADDING = 18.dp

/** Marge basse de la liste : le bouton flottant ne doit pas couvrir la dernière carte. */
private val LIST_BOTTOM_PADDING = 90.dp

/** Marge interne d'une ligne de liste (`padding: 10`). */
private val ROW_PADDING = 10.dp

/** Écart sous une ligne (`marginBottom: 5`). */
private val ROW_SPACING = 5.dp

/** Écart entre les colonnes d'une ligne (`gap: 10`). */
private val ROW_GAP = 10.dp

/** Corps du nom d'une ligne (`size={18}`). */
private val ROW_NAME_SIZE = 18.sp

/** Corps du sens, sous le nom (`fontSize: 12`). */
private val ROW_MEANING_SIZE = 12.sp

/** Corps des deux mentions de la pastille (`fontSize: 10`). */
private val ROW_META_SIZE = 10.sp

/** Corps du nom arabe d'une ligne (`size={20}`). */
private val ROW_ARABIC_SIZE = 20.sp

/** Part de la largeur qu'un nom arabe peut occuper (`maxWidth: '24%'`). */
private const val ARABIC_SHARE = 0.24f

/** Côté du chevron d'une ligne (`size={19}`). */
private val CHEVRON_SIZE = 19.dp

/** Rayon et marge interne de la pastille de lieu de révélation (`borderRadius:10, padding 7/3`). */
private val BADGE_RADIUS = 10.dp
private val BADGE_PADDING_H = 7.dp
private val BADGE_PADDING_V = 3.dp

/** Écart entre la pastille et le compte de versets (`gap: 4`). */
private val BADGE_GAP = 4.dp

/** Vignette de la carte du pied (`width:105,height:75,borderRadius:13`). */
private val TAJWID_ART_WIDTH = 105.dp
private val TAJWID_ART_HEIGHT = 75.dp
private val TAJWID_ART_RADIUS = 13.dp

/** Bouton flottant : 70 x 70, rayon 35, posé à 18 px du bord et 12 px du bas. */
private val FAB_SIZE = 70.dp
private val FAB_RADIUS = 35.dp
private val FAB_END = 18.dp
private val FAB_BOTTOM = 12.dp

/** Icône et libellé du bouton flottant (`size={24}`, `fontSize: 10`). */
private val FAB_ICON_SIZE = 24.dp
private val FAB_LABEL_SIZE = 10.sp

/** Côté de l'icône de la carte des connaissances — la taille par défaut de l'original. */
private val KNOWN_ICON_SIZE = 24.dp

/** Hauteur d'une ligne du choix de filtre, pour que le doigt la trouve. */
private val FILTER_ROW_HEIGHT = 48.dp

/** Le premier verset du Coran, repli du bouton de dernière lecture. */
private const val FIRST_VERSE = 1

/**
 * L'onglet « Coran ».
 *
 * @param state l'état du compte, ou `null` tant qu'il n'est pas arrivé. `null` n'est pas un cas de
 *   bord : au premier rendu, la lecture du disque n'a rien publié, et confondre cet état avec un
 *   état vide afficherait une liste vide au lieu d'une attente.
 * @param failure la raison d'un référentiel coranique indisponible, ou `null`. Elle est fournie par
 *   l'appelant : l'écran ne sait pas pourquoi les données manquent, et inventer une raison serait
 *   pire que de n'en donner aucune.
 * @param onOpen la plage de versets à ouvrir dans le lecteur.
 * @param onEditKnowledge le crayon de la carte des connaissances.
 * @param onSwitchToTest la carte du pied : elle bascule le lecteur sur le Coran composé.
 */
@Composable
fun QuranScreen(
    state: AppState?,
    modifier: Modifier = Modifier,
    failure: String? = null,
    onOpen: (Range) -> Unit = {},
    onEditKnowledge: () -> Unit = {},
    onSwitchToTest: () -> Unit = {},
) {
    // Les trois états d'interface de l'écran. `rememberSaveable` comme `ReaderPanel` l'est dans le
    // lecteur : une énumération est sérialisable, donc l'état survit à une rotation.
    var view by rememberSaveable { mutableStateOf(QuranText.View.LIST) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(QuranText.Filter.ALL) }

    // Le calcul ne se rejoue que si l'une de ses cinq entrées change. `remember` et non
    // `rememberSaveable` : le résultat est dérivable, et le sauvegarder ferait survivre une liste à
    // l'état qui l'a produite.
    val ui = remember(state, view, query, filter, failure) {
        when {
            failure != null -> QuranUiState(loading = false, failure = failure)
            state == null -> QuranUiState()
            else -> QuranListRenderer.render(state, view, query, filter)
        }
    }

    QuranContent(
        state = ui,
        view = view,
        query = query,
        modifier = modifier,
        onView = { choisie ->
            view = choisie
            // La règle de l'original : changer de vue efface la recherche.
            query = ""
        },
        onQuery = { query = it },
        onFilter = { filter = it },
        onOpen = onOpen,
        onEditKnowledge = onEditKnowledge,
        onSwitchToTest = onSwitchToTest,
    )
}

/** Le contenu de l'écran, sans état propre : c'est ce qui le rend lisible et éprouvable. */
@Composable
internal fun QuranContent(
    state: QuranUiState,
    view: QuranText.View,
    query: String,
    modifier: Modifier = Modifier,
    onView: (QuranText.View) -> Unit = {},
    onQuery: (String) -> Unit = {},
    onFilter: (QuranText.Filter) -> Unit = {},
    onOpen: (Range) -> Unit = {},
    onEditKnowledge: () -> Unit = {},
    onSwitchToTest: () -> Unit = {},
) {
    // Capturé une fois : la triade se lit alors sans répéter `state.failure` trois fois, et le
    // message reste non nul dans la branche qui l'affiche.
    val echec = state.failure

    when {
        echec != null -> Box(
            modifier = modifier.fillMaxSize().background(AppTheme.colors.cream),
            contentAlignment = Alignment.Center,
        ) {
            AppLabel(
                text = echec,
                modifier = Modifier.padding(horizontal = SCREEN_PADDING),
                selectable = false,
                color = AppTheme.colors.muted,
                textAlign = TextAlign.Center,
            )
        }

        state.loading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AppLabel(
                text = "Ouverture de l'application…",
                selectable = false,
                color = AppTheme.colors.muted,
            )
        }

        else -> QuranList(
            state = state,
            view = view,
            query = query,
            modifier = modifier,
            onView = onView,
            onQuery = onQuery,
            onFilter = onFilter,
            onOpen = onOpen,
            onEditKnowledge = onEditKnowledge,
            onSwitchToTest = onSwitchToTest,
        )
    }
}

@Composable
private fun QuranList(
    state: QuranUiState,
    view: QuranText.View,
    query: String,
    modifier: Modifier = Modifier,
    onView: (QuranText.View) -> Unit = {},
    onQuery: (String) -> Unit = {},
    onFilter: (QuranText.Filter) -> Unit = {},
    onOpen: (Range) -> Unit = {},
    onEditKnowledge: () -> Unit = {},
    onSwitchToTest: () -> Unit = {},
) {
    val colors = AppTheme.colors

    // L'ouverture du choix de filtre est une présentation : l'oublier au rechargement ne perd rien.
    // `rememberSaveable` la garde pourtant, parce que refermer une fenêtre sous les doigts serait
    // une surprise.
    var filterOpen by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().background(colors.cream)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = LIST_BOTTOM_PADDING),
        ) {
            // La bande occupe toute la largeur : elle est donc posée sans le retrait que les autres
            // éléments portent.
            item(key = "hero") {
                AppHero(title = state.title, subtitle = state.subtitle)
            }

            item(key = "known") {
                KnownCard(
                    state = state,
                    modifier = Modifier.padding(horizontal = SCREEN_PADDING),
                    onEditKnowledge = onEditKnowledge,
                )
            }

            item(key = "search") {
                SearchRow(
                    state = state,
                    query = query,
                    modifier = Modifier.padding(horizontal = SCREEN_PADDING),
                    onQuery = onQuery,
                    onOpenFilter = { filterOpen = true },
                )
            }

            item(key = "view") {
                AppSegmentedControl(
                    options = state.views,
                    value = view.label,
                    // Le sélecteur rend un libellé : c'est le seul vocabulaire qu'il connaisse. La
                    // correspondance libellé -> vue est refaite ici, et un libellé inconnu ne change
                    // rien plutôt que de choisir à la place de la personne.
                    onSelect = { libelle ->
                        QuranText.View.entries.firstOrNull { it.label == libelle }?.let(onView)
                    },
                    modifier = Modifier.padding(horizontal = SCREEN_PADDING),
                )
            }

            if (state.rows.isEmpty()) {
                item(key = "empty") {
                    AppLabel(
                        text = state.empty,
                        modifier = Modifier.padding(
                            horizontal = SCREEN_PADDING,
                            vertical = 16.dp,
                        ),
                        selectable = false,
                        color = colors.muted,
                    )
                }
            }

            items(state.rows, key = { it.key }) { row ->
                QuranRowCard(
                    row = row,
                    modifier = Modifier.padding(horizontal = SCREEN_PADDING),
                    onOpen = onOpen,
                )
            }

            item(key = "tajwid") {
                TajwidCard(
                    state = state,
                    modifier = Modifier.padding(horizontal = SCREEN_PADDING),
                    onSwitchToTest = onSwitchToTest,
                )
            }
        }

        LastReadButton(
            state = state,
            modifier = Modifier.align(Alignment.BottomEnd),
            onOpen = onOpen,
        )
    }

    if (filterOpen) {
        FilterDialog(
            onDismiss = { filterOpen = false },
            onChoose = { choix ->
                onFilter(choix)
                filterOpen = false
            },
        )
    }
}

// ---------------------------------------------------------------------------
// La carte « J’ai appris jusqu’à »
// ---------------------------------------------------------------------------

/**
 * La carte des connaissances : l'icône, le libellé, la référence, et le crayon.
 *
 * La référence vient du calcul, et non de l'écran : elle demande le dernier verset validé, la
 * sourate qui le porte et son rang dans cette sourate. La recomposer ici ferait dépendre
 * l'affichage du référentiel, que l'écran ne connaît pas.
 *
 * La carte **n'est pas cliquable** : dans le client d'origine, seule la zone du crayon l'est. Rendre
 * la carte entière sensible ferait ouvrir les connaissances en touchant la référence, qui se lit.
 */
@Composable
private fun KnownCard(
    state: QuranUiState,
    modifier: Modifier = Modifier,
    onEditKnowledge: () -> Unit = {},
) {
    val colors = AppTheme.colors

    AppCard(
        modifier = modifier.padding(top = 8.dp),
        spaced = false,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = colors.green,
                modifier = Modifier.size(KNOWN_ICON_SIZE),
            )
            Column(modifier = Modifier.weight(1f)) {
                AppLabel(
                    text = state.knownLabel,
                    selectable = false,
                    fontSize = AppTheme.typeScale.secondary,
                    color = colors.muted,
                )
                AppHeading(text = state.knownValue, size = AppTheme.typeScale.card)
            }
            AppIconButton(
                icon = Icons.Outlined.Edit,
                label = state.editKnowledgeLabel,
                onClick = onEditKnowledge,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// La recherche et le filtre
// ---------------------------------------------------------------------------

@Composable
private fun SearchRow(
    state: QuranUiState,
    query: String,
    modifier: Modifier = Modifier,
    onQuery: (String) -> Unit = {},
    onOpenFilter: () -> Unit = {},
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            AppField(value = query, onValueChange = onQuery, placeholder = state.searchPlaceholder)
        }
        // Le bouton n'existe que dans la vue des sourates : une division n'a pas de lieu de
        // révélation. Le proposer ailleurs ouvrirait un réglage qui ne s'applique à rien.
        if (state.showFilter) {
            AppIconButton(
                icon = Icons.Outlined.FilterList,
                label = state.filterLabel,
                onClick = onOpenFilter,
            )
        }
    }
}

/**
 * Le choix du lieu de révélation.
 *
 * Le client d'origine emploie `Alert.alert`, qui empile quatre boutons sous son titre. Material
 * n'offre que deux emplacements — confirmation et renoncement —, donc les trois choix sont des
 * lignes et « Annuler » reste au bas de la fenêtre. Les quatre options sont conservées : les trois
 * filtres **et** la sortie sans rien changer.
 */
@Composable
private fun FilterDialog(
    onDismiss: () -> Unit,
    onChoose: (QuranText.Filter) -> Unit,
) {
    val colors = AppTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { AppHeading(text = QuranText.FILTER_TITLE, size = AppTheme.typeScale.card) },
        text = {
            Column {
                QuranText.Filter.entries.forEach { choix ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(FILTER_ROW_HEIGHT)
                            .clickable(role = Role.Button) { onChoose(choix) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppLabel(text = choix.label, selectable = false)
                    }
                }
            }
        },
        // « Annuler » est le quatrième bouton de l'original, celui que `style: 'cancel'` marque. Il
        // referme sans rien changer, et c'est la seule sortie qui ne décide pas à la place de la
        // personne.
        confirmButton = {
            AppButton(text = QuranText.FILTER_CANCEL, onClick = onDismiss, small = true)
        },
        containerColor = colors.paper,
    )
}

// ---------------------------------------------------------------------------
// Une ligne
// ---------------------------------------------------------------------------

/**
 * Une ligne de la liste : le médaillon, le nom, le sens, la pastille, le nom arabe, le chevron.
 *
 * **L'étiquette vocale s'ajoute au texte de la ligne, elle ne le remplace pas.** Le client
 * d'origine pose `accessibilityLabel={'Ouvrir ' + nom}`, ce qui **supprime** tout le reste pour un
 * lecteur d'écran : le nombre de versets et le lieu de révélation disparaissent. Compose, lui,
 * fusionne l'étiquette et les textes de la ligne. C'est une différence assumée — elle est du côté de
 * qui entend l'écran, et personne n'y perd d'information.
 */
@Composable
private fun QuranRowCard(
    row: QuranRow,
    modifier: Modifier = Modifier,
    onOpen: (Range) -> Unit = {},
) {
    val colors = AppTheme.colors

    AppCard(
        modifier = modifier.semantics { contentDescription = row.openLabel },
        bottomSpacing = ROW_SPACING,
        padding = ROW_PADDING,
        onClick = { onOpen(row.range) },
    ) {
        // `BoxWithConstraints` : la borne du nom arabe est un **pourcentage** de la largeur de la
        // ligne (`maxWidth: '24%'`), et un pourcentage ne s'exprime pas sans connaître cette
        // largeur. Une valeur en dur se tromperait sur tout écran plus étroit que celui sur lequel
        // on l'aurait relevée — c'est-à-dire précisément dans le cas que la borne protège.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val largeurMaxArabe = maxWidth * ARABIC_SHARE

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
            ) {
                QuranNumberMedallion(number = row.number)

                Column(modifier = Modifier.weight(1f)) {
                    AppHeading(text = row.name, size = ROW_NAME_SIZE)
                    AppLabel(
                        text = row.meaning,
                        modifier = Modifier.padding(top = 2.dp),
                        selectable = false,
                        maxLines = 1,
                        fontSize = ROW_MEANING_SIZE,
                        color = colors.muted,
                    )
                }

                // La pastille est nulle pour une division : elle n'a ni lieu de révélation ni compte
                // de versets propre. C'est le seul endroit où les deux genres de ligne diffèrent.
                row.badge?.let { PlaceBadge(badge = it) }

                row.arabic?.let { nom ->
                    ArabicText(
                        text = nom,
                        modifier = Modifier.widthIn(max = largeurMaxArabe),
                        color = colors.green,
                        fontSize = ROW_ARABIC_SIZE,
                        // Le nom se lit de droite à gauche : sans alignement à droite, la
                        // ponctuation et les signes diacritiques se placent du mauvais côté.
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }

                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier.size(CHEVRON_SIZE),
                )
            }
        }
    }
}

/**
 * La pastille d'une sourate : son lieu de révélation, puis son nombre de versets.
 *
 * Les deux couleurs viennent du **fait**, porté par la pastille, et non du mot affiché : comparer le
 * libellé à une chaîne ferait dépendre une couleur d'une égalité de texte, et corriger une faute de
 * frappe changerait un fond.
 */
@Composable
private fun PlaceBadge(badge: QuranBadge) {
    val colors = AppTheme.colors
    val accent = if (badge.isMeccan) colors.green else colors.review
    val fond = if (badge.isMeccan) colors.selected else colors.reviewSoft

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BADGE_GAP),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(BADGE_RADIUS))
                .background(fond)
                .padding(horizontal = BADGE_PADDING_H, vertical = BADGE_PADDING_V),
        ) {
            AppLabel(
                text = badge.place,
                selectable = false,
                fontSize = ROW_META_SIZE,
                color = accent,
            )
        }
        AppLabel(
            text = badge.count,
            selectable = false,
            fontSize = ROW_META_SIZE,
            color = colors.muted,
        )
    }
}

// ---------------------------------------------------------------------------
// Le pied de liste : le Coran composé
// ---------------------------------------------------------------------------

/**
 * La carte qui mène au Coran composé, celui qui porte les règles de Tajwid.
 *
 * Elle **change la source** en plus d'ouvrir le lecteur : c'est le seul chemin par lequel on bascule
 * sur le Coran composé depuis cet écran. L'écriture appartient à la route, qui seule connaît le
 * conteneur ; la carte ne fait que demander.
 */
@Composable
private fun TajwidCard(
    state: QuranUiState,
    modifier: Modifier = Modifier,
    onSwitchToTest: () -> Unit = {},
) {
    val colors = AppTheme.colors

    AppCard(
        modifier = modifier.padding(top = 12.dp),
        spaced = false,
        padding = 8.dp,
        onClick = onSwitchToTest,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(ReadingArt),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(TAJWID_ART_WIDTH, TAJWID_ART_HEIGHT)
                    .clip(RoundedCornerShape(TAJWID_ART_RADIUS)),
            )
            Column(modifier = Modifier.weight(1f)) {
                AppHeading(text = state.tajwidTitle, size = AppTheme.typeScale.card)
                AppLabel(
                    text = state.tajwidBody,
                    modifier = Modifier.padding(top = 3.dp),
                    selectable = false,
                    fontSize = AppTheme.typeScale.secondary,
                    color = colors.muted,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colors.green,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Le bouton flottant
// ---------------------------------------------------------------------------

/**
 * Le bouton « Dernière lecture », posé sur la liste.
 *
 * Il ouvre **le verset retenu**, et non la page : c'est le même point de reprise que la carte
 * « Continuer » de l'accueil, atteint depuis un autre onglet.
 *
 * Quand rien n'a jamais été lu, le client d'origine ouvre le premier verset du Coran
 * (`lastRead?.verseId ?? 1`). Le repli est posé ici, et non dans le calcul : `null` y dit la vérité
 * — on n'a rien lu —, là où un `1` en dur ferait passer un repli pour une lecture.
 */
@Composable
private fun LastReadButton(
    state: QuranUiState,
    modifier: Modifier = Modifier,
    onOpen: (Range) -> Unit = {},
) {
    val colors = AppTheme.colors
    val cible = state.lastReadVerse ?: FIRST_VERSE

    Box(
        modifier = modifier
            .padding(end = FAB_END, bottom = FAB_BOTTOM)
            .size(FAB_SIZE)
            .clip(RoundedCornerShape(FAB_RADIUS))
            .background(colors.green)
            .semantics { contentDescription = state.lastRead }
            .clickable(role = Role.Button) { onOpen(Range(cible, cible)) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(FAB_ICON_SIZE),
            )
            AppLabel(
                text = state.lastRead,
                selectable = false,
                fontSize = FAB_LABEL_SIZE,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}
