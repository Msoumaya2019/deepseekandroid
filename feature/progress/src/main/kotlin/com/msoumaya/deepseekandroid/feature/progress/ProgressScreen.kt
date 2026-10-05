package com.msoumaya.deepseekandroid.feature.progress

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.msoumaya.deepseekandroid.core.data.LocalAppContainer
import com.msoumaya.deepseekandroid.core.design.component.AppButton
import com.msoumaya.deepseekandroid.core.design.component.AppCard
import com.msoumaya.deepseekandroid.core.design.component.AppHeading
import com.msoumaya.deepseekandroid.core.design.component.AppHero
import com.msoumaya.deepseekandroid.core.design.component.AppInlineIcon
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.component.AppSectionHeader
import com.msoumaya.deepseekandroid.core.design.component.AppSegmentedControl
import com.msoumaya.deepseekandroid.core.design.component.AppStatCard
import com.msoumaya.deepseekandroid.core.design.component.ProgressRing
import com.msoumaya.deepseekandroid.core.design.component.ProgressTrack
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.design.theme.ReadingArt
import com.msoumaya.deepseekandroid.core.domain.ProgressText

// ---------------------------------------------------------------------------
// Écran « Progrès »
// ---------------------------------------------------------------------------
// Portage de `ProgressScreen` (`src/ui/MainScreens.tsx:46`).
//
// **Ce qui a changé, et pourquoi.** L'original dessinait l'anneau, les cartes de statistiques et
// les quatre compteurs **dans le composable** : les valeurs se recalculaient à chaque rendu, y
// compris le balayage des 604 pages du moushaf. Ici l'écran ne calcule rien — il reçoit un
// `ProgressUiState` déjà résolu par `ProgressRenderer` — et il ne garde qu'une chose : le pli du
// graphique, qui est un état d'interface et non une décision.
//
// **Deux écartements, deux raisons.** La bande d'en-tête occupe toute la largeur et se place donc
// **hors** de la colonne en retrait ; tout le reste est en retrait de 18 px, comme au programme.
// Les cartes de statistiques, elles, sont posées par paires dans une rangée : `AppStatCard` est
// déclarée `spaced = false` — c'est au parent de décider —, et l'écart entre les deux rangées est
// donc porté ici.
//
// **Ce qui n'est pas là.** Le bloc `QuizStats` du client d'origine — bonnes réponses et défis —
// arrive avec l'écran de quiz, en phase D. Il n'est pas remplacé par un équivalent local : sans
// question du jour, un bloc de quiz n'aurait rien à compter. Même choix qu'à l'accueil.
// ---------------------------------------------------------------------------

/** Retrait horizontal du contenu, sous la bande d'en-tête. Valeur du programme et du Coran. */
private val SCREEN_PADDING = 18.dp

/** Hauteur de la zone du graphique, valeur du dépôt d'origine. */
private val GRAPH_HEIGHT = 110.dp

/** Hauteur maximale d'une barre pleine. */
private val BAR_MAX_HEIGHT = 64.dp

/**
 * Hauteur d'une barre vide.
 *
 * Le dépôt d'origine pose un plancher de 3 px : une période sans rien appris garde un trait. Sans
 * lui, la barre disparaîtrait et la période semblerait absente du graphique plutôt que vide.
 */
private val BAR_MIN_HEIGHT = 3.dp

/** Part de la largeur d'une colonne occupée par sa barre (`width:'65%'` à la source). */
private const val BAR_WIDTH_SHARE = 0.65f

/**
 * Ma progression : anneau du Coran, statistiques de la période, graphique, objectif et compteurs.
 *
 * @param onOpenGoal ouverture de l'écran d'objectif. Deux portes y mènent — l'action « Voir tout »
 *   de l'en-tête de section et la carte elle-même —, et c'est délibéré : la carte entière est
 *   cliquable dans le client d'origine, et l'en-tête offre le même raccourci.
 * @param viewModel état de l'écran. Par défaut, celui du conteneur applicatif.
 */
@Composable
fun ProgressScreen(
    modifier: Modifier = Modifier,
    onOpenGoal: () -> Unit = {},
    viewModel: ProgressViewModel = viewModel(
        factory = ProgressViewModel.factory(LocalAppContainer.current),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ProgressContent(
        state = state,
        modifier = modifier,
        onOpenGoal = onOpenGoal,
        onPeriodSelected = viewModel::onPeriodSelected,
    )
}

@Composable
internal fun ProgressContent(
    state: ProgressUiState,
    modifier: Modifier = Modifier,
    onOpenGoal: () -> Unit = {},
    onPeriodSelected: (ProgressText.Period) -> Unit = {},
) {
    when {
        state.failure != null -> ProgressFailure(message = state.failure, modifier = modifier)

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

        else -> ProgressBody(
            state = state,
            modifier = modifier,
            onOpenGoal = onOpenGoal,
            onPeriodSelected = onPeriodSelected,
        )
    }
}

@Composable
private fun ProgressBody(
    state: ProgressUiState,
    modifier: Modifier = Modifier,
    onOpenGoal: () -> Unit = {},
    onPeriodSelected: (ProgressText.Period) -> Unit = {},
) {
    // Le seul état d'interface de l'écran. `rememberSaveable` : une rotation ne doit pas
    // refermer un graphique que la personne vient d'ouvrir.
    var showGraph by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        AppHero(title = ProgressText.TITLE, subtitle = ProgressText.SUBTITLE)

        Column(modifier = Modifier.padding(horizontal = SCREEN_PADDING)) {
            state.ring?.let { ring -> RingCard(ring = ring) }

            AppSegmentedControl(
                options = ProgressText.Period.entries.map { it.label },
                value = state.period.label,
                onSelect = { label ->
                    ProgressText.Period.entries.firstOrNull { it.label == label }?.let(onPeriodSelected)
                },
                modifier = Modifier.padding(top = AppTheme.spacing.sm),
            )

            // Deux rangées de deux cartes. Les paires sont celles du client d'origine : d'un côté
            // ce qui a été appris, de l'autre ce qui a été parcouru et révisé.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppTheme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
            ) {
                state.learned?.let { stat ->
                    AppStatCard(
                        title = stat.title,
                        value = stat.value,
                        icon = Icons.Outlined.BarChart,
                        modifier = Modifier.weight(1f),
                    )
                }
                state.regularity?.let { stat ->
                    AppStatCard(
                        title = stat.title,
                        value = stat.value,
                        icon = Icons.Outlined.EventAvailable,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppTheme.spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
            ) {
                state.memorizedPages?.let { stat ->
                    AppStatCard(
                        title = stat.title,
                        value = stat.value,
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        modifier = Modifier.weight(1f),
                    )
                }
                state.revisions?.let { stat ->
                    AppStatCard(
                        title = stat.title,
                        value = stat.value,
                        icon = Icons.Outlined.Autorenew,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            AppButton(
                text = ProgressText.graphToggle(shown = showGraph),
                onClick = { showGraph = !showGraph },
                secondary = true,
                small = true,
                modifier = Modifier.padding(top = AppTheme.spacing.md),
            )

            if (showGraph) {
                state.graph?.let { graph -> GraphCard(graph = graph) }
            }

            AppSectionHeader(
                title = ProgressText.GOALS_TITLE,
                action = ProgressText.SEE_ALL,
                onAction = onOpenGoal,
            )

            state.goal?.let { goal -> GoalCard(goal = goal, onOpen = onOpenGoal) }

            AppSectionHeader(title = ProgressText.STATISTICS)

            CountersRow(counters = state.counters)
        }
    }
}

// ---------------------------------------------------------------------------
// Carte de l'anneau
// ---------------------------------------------------------------------------

@Composable
private fun RingCard(ring: Ring) {
    val colors = AppTheme.colors

    AppCard(modifier = Modifier.padding(top = AppTheme.spacing.sm), padding = 15.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.lg),
        ) {
            ProgressRing(value = ring.ratio, size = 112.dp) {
                AppHeading(text = ring.percent, size = 29.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                // « 42 » puis « / 6236 » : le dénominateur est plus petit et en gris, comme dans
                // le client d'origine, où il est une `Label` posée dans un `Heading`.
                Row(verticalAlignment = Alignment.Bottom) {
                    AppHeading(text = ring.knownVerses.toString(), size = 28.sp)
                    AppLabel(
                        text = ProgressText.ofTotal(ring.totalVerses),
                        selectable = false,
                        fontSize = 17.sp,
                        color = colors.muted,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }

                AppLabel(
                    text = ProgressText.MEMORIZED_VERSES,
                    selectable = false,
                    fontSize = 13.sp,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Image(
                    painter = painterResource(ReadingArt),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 5.dp)
                        .size(width = 100.dp, height = 52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .alpha(0.65f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Graphique
// ---------------------------------------------------------------------------

@Composable
private fun GraphCard(graph: Graph) {
    val colors = AppTheme.colors

    AppCard(modifier = Modifier.padding(top = AppTheme.spacing.sm)) {
        AppHeading(text = graph.title, size = 19.sp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(GRAPH_HEIGHT)
                .padding(top = AppTheme.spacing.sm),
            // Les colonnes sont alignées par le bas : c'est ce qui donne des barres qui montent
            // depuis une même ligne, et non des barres centrées sur leur propre hauteur.
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
        ) {
            graph.bars.forEach { bar ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AppLabel(
                        text = bar.value.toString(),
                        selectable = false,
                        fontSize = 10.sp,
                        color = colors.muted,
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(BAR_WIDTH_SHARE)
                            .height(maxOf(BAR_MIN_HEIGHT, BAR_MAX_HEIGHT * bar.ratio))
                            .clip(RoundedCornerShape(5.dp))
                            .background(colors.green2),
                    )

                    AppLabel(
                        text = bar.label,
                        selectable = false,
                        fontSize = 10.sp,
                        color = colors.muted,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Carte d'objectif
// ---------------------------------------------------------------------------

@Composable
private fun GoalCard(goal: GoalLine, onOpen: () -> Unit) {
    val colors = AppTheme.colors

    AppCard(padding = AppTheme.spacing.sm, onClick = onOpen) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md),
        ) {
            Image(
                painter = painterResource(ReadingArt),
                contentDescription = null,
                modifier = Modifier
                    .size(width = 95.dp, height = 76.dp)
                    .clip(RoundedCornerShape(13.dp)),
            )

            Column(modifier = Modifier.weight(1f)) {
                AppHeading(text = goal.label, size = 18.sp)
                AppLabel(
                    text = ProgressText.CURRENT_GOAL,
                    selectable = false,
                    fontSize = 12.sp,
                    color = colors.muted,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                ProgressTrack(value = goal.ratio)
                AppLabel(
                    text = goal.percent,
                    selectable = false,
                    fontSize = 11.sp,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Les quatre compteurs
// ---------------------------------------------------------------------------

@Composable
private fun CountersRow(counters: List<Counter>) {
    val colors = AppTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        counters.forEach { counter ->
            AppCard(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 105.dp),
                spaced = false,
                padding = AppTheme.spacing.sm,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    AppInlineIcon(
                        icon = counterIcon(counter.kind),
                        tint = colors.green,
                        size = 22.dp,
                    )
                    AppLabel(
                        text = counter.kind.label,
                        selectable = false,
                        fontSize = 10.sp,
                        color = colors.muted,
                        textAlign = TextAlign.Center,
                    )
                    AppHeading(text = counter.value.toString(), size = 21.sp)
                }
            }
        }
    }
}

/**
 * Correspondance vers le jeu d'icônes natif d'Android.
 *
 * Les noms du client d'origine sont ceux de MaterialCommunityIcons, qui n'existe pas ici. La
 * correspondance est celle des onglets de la barre basse, où le même problème a déjà été tranché :
 *
 *  - `book-open-outline` → `MenuBook`, le livre ouvert ;
 *  - `calendar-check-outline` → `EventAvailable`, le calendrier à coche ;
 *  - `chart-bar` → `BarChart`, à l'identique ;
 *  - `file-outline` → `InsertDriveFile`, le document. **Aucune icône « page » n'existe** dans le
 *    jeu livré ; `InsertDriveFile` est la feuille la plus proche, et le sens reste « ce qui a été
 *    parcouru ». C'est la variante `AutoMirrored` qui est employée : la variante simple est
 *    dépréciée dans la version livrée, parce que le coin plié du document doit se retourner en
 *    lecture de droite à gauche — ce qui est précisément le cas d'un moushaf.
 */
private fun counterIcon(kind: CounterKind): ImageVector = when (kind) {
    CounterKind.JUZ -> Icons.AutoMirrored.Outlined.MenuBook
    CounterKind.ACTIVE_DAYS -> Icons.Outlined.EventAvailable
    CounterKind.PAGES_READ -> Icons.AutoMirrored.Outlined.InsertDriveFile
    CounterKind.VERSES -> Icons.Outlined.BarChart
}

// ---------------------------------------------------------------------------
// Panne du référentiel
// ---------------------------------------------------------------------------

@Composable
private fun ProgressFailure(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(SCREEN_PADDING),
        verticalArrangement = Arrangement.Center,
    ) {
        AppHeading(text = "Contenu indisponible", size = 21.sp)
        AppLabel(
            text = message,
            selectable = false,
            color = AppTheme.colors.muted,
            modifier = Modifier.padding(top = AppTheme.spacing.sm),
        )
    }
}
