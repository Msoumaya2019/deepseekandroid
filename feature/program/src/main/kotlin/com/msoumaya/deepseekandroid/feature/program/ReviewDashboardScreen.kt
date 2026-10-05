package com.msoumaya.deepseekandroid.feature.program

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.msoumaya.deepseekandroid.core.data.LocalAppContainer
import com.msoumaya.deepseekandroid.core.design.component.AppButton
import com.msoumaya.deepseekandroid.core.design.component.AppCard
import com.msoumaya.deepseekandroid.core.design.component.AppIconButton
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.component.AppTitle
import com.msoumaya.deepseekandroid.core.design.component.ProgressTrack
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.ReviewText
import com.msoumaya.deepseekandroid.core.domain.StudySession

// ---------------------------------------------------------------------------
// Tableau de bord des révisions
// ---------------------------------------------------------------------------
// Portage de `ReviewDashboard.tsx` (les cinq cartes) et de `RevisionBottomActionBar.tsx` (les mots
// de la barre d'action, portés par `ReviewText`).
//
// **L'écran ne calcule rien.** Tout ce qui se lit — les comptes, les pourcentages, les dates, les
// mots des étapes — arrive déjà résolu dans `ReviewDashboardUiState`. Il ne reste ici que trois
// états d'interface : le dépliage des consolidations, celui des versets prioritaires, et
// l'ouverture du sélecteur de cycle. Ce sont des décisions d'affichage : elles ne changent rien à
// ce que l'application croit appris.
//
// **Quatre mesures relevées à la source, qui ne se devinent pas.**
//
//   1. Le tableau de bord **n'a pas de bande d'en-tête** : il ouvre sur un chevron de retour, un
//      titre et un sous-titre, dans la marge commune de 16 px. La route est déclarée plein écran
//      (`AppRoutes.fullScreen`), donc ni barre supérieure ni barre basse — le retour est à la
//      charge de l'écran, sans quoi il n'aurait aucune sortie.
//
//   2. La carte du jour a un **fond doux** et non `paper`. C'est le seul endroit du client
//      d'origine où une carte change de fond pour se distinguer : elle porte la séance, les
//      quatre autres portent des explications. `AppCard` accepte donc un fond, sans changer ni la
//      forme ni l'ombre.
//
//   3. Les trois étapes d'une consolidation portent un **trait supérieur de 2 px**, vert quand
//      l'étape est faite ou due, couleur de trait sinon. Ce n'est pas une décoration : c'est le
//      seul repère qui dit, d'un balayage, où en est la consolidation. Un trait de 1 px se
//      confondrait avec la bordure de la ligne.
//
//   4. Le bandeau de rythme, dans la carte du cycle, est une **surface douce sans bordure ni
//      ombre** — un `padding:12` avec un rayon de 16 et un fond `soft`. Ce n'est donc pas une
//      carte imbriquée : lui en donner la bordure ou l'ombre ajouterait un cadre que l'original
//      n'a pas.
//
// **Ce qui n'est pas encore là.** Les cinq gestes de la barre d'action (`Parfait`, `Quelques
// hésitations`, `À retravailler`, `Écouter`, `Ma voix`) sont portés par `ReviewText`, mais la
// barre elle-même s'affiche dans le **lecteur**, et le lecteur ne transporte pas encore l'identité
// d'une tâche de révision par sa route. Les mots sont donc prêts et éprouvés, la barre attend le
// branchement du lecteur — c'est le même ordre que pour le programme, dont le renderer a précédé
// l'écran.
// ---------------------------------------------------------------------------

/**
 * Le tableau de bord des révisions : la séance du jour, le cycle, les consolidations, les versets
 * à retravailler, le suivi.
 *
 * @param onClose fermeture de l'écran. Le tableau de bord ne connaît pas la navigation : il
 *   demande à fermer, et c'est la coquille qui décide où l'on retourne.
 * @param onOpenStudy ouverture du lecteur **sur une tâche** — la séance du jour, une consolidation,
 *   un verset prioritaire. Une seule entrée pour les trois : les trois portent une tâche à valider,
 *   et les distinguer ferait décider à l'écran ce que la requête décide déjà.
 * @param onStatistics ouverture des statistiques — le bouton de la carte « Mon suivi ».
 * @param onRecitations ouverture des récitations partagées.
 * @param viewModel état de l'écran. Par défaut, celui du conteneur applicatif.
 */
@Composable
fun ReviewDashboardScreen(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    onOpenStudy: (StudySession.Request) -> Unit = {},
    onStatistics: () -> Unit = {},
    onRecitations: () -> Unit = {},
    viewModel: ReviewDashboardViewModel = viewModel(
        factory = ReviewDashboardViewModel.factory(LocalAppContainer.current),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ReviewDashboardContent(
        state = state,
        modifier = modifier,
        onClose = onClose,
        onOpenStudy = onOpenStudy,
        onStatistics = onStatistics,
        onRecitations = onRecitations,
        onCycleSelected = viewModel::onCycleSelected,
        onQuantitySelected = viewModel::onQuantitySelected,
    )
}

@Composable
private fun ReviewDashboardContent(
    state: ReviewDashboardUiState,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    onOpenStudy: (StudySession.Request) -> Unit = {},
    onStatistics: () -> Unit = {},
    onRecitations: () -> Unit = {},
    onCycleSelected: (Int) -> Unit = {},
    onQuantitySelected: (String) -> Unit = {},
) {
    val colors = AppTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 30.dp),
    ) {
        // Le retour est porté par l'écran : la route est plein écran, donc il n'y a pas de barre
        // supérieure pour le poser. Sans lui, l'écran n'aurait aucune sortie.
        AppIconButton(
            icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
            label = "Retour",
            onClick = onClose,
        )

        AppTitle(text = ReviewText.TITLE)

        AppLabel(
            text = ReviewText.SUBTITLE,
            selectable = false,
            color = colors.muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        state.failure?.let { message ->
            AppCard(background = colors.soft) {
                AppLabel(text = message, selectable = false, color = colors.muted)
            }
        }

        if (state.loading) {
            AppLabel(
                text = "Ouverture de l’application…",
                selectable = false,
                color = colors.muted,
            )
            return@Column
        }

        // Une reprise de révision se pose **au-dessus** des cartes, comme dans le client
        // d'origine : c'est une tâche interrompue, et la retrouver en tête évite de la chercher
        // dans le cycle.
        state.resumes.forEach { ligne ->
            StudyResumeCard(line = ligne, onResume = { onOpenStudy(ligne.study) })
        }

        DayCard(state = state, onOpenStudy = onOpenStudy)
        CycleCard(
            state = state,
            onCycleSelected = onCycleSelected,
            onQuantitySelected = onQuantitySelected,
        )
        ConsolidationCard(state = state, onOpenStudy = onOpenStudy)
        PriorityCard(state = state, onOpenStudy = onOpenStudy)
        TrackingCard(state = state, onStatistics = onStatistics, onRecitations = onRecitations)
    }
}

// ---------------------------------------------------------------------------
// En-tête de carte
// ---------------------------------------------------------------------------

/**
 * L'en-tête d'une carte : une icône, un titre, et une action facultative.
 *
 * Le client d'origine le construit une fois et le réutilise cinq fois. Le titre porte une graisse
 * **ExtraBold** (800) que ni `AppHeading` ni `AppSectionHeader` ne posent : ce sont les titres de
 * section de l'application, qui sont SemiBold. Le composer ici évite d'ajouter un paramètre de
 * graisse au design system pour un seul écran.
 */
@Composable
private fun DashboardHeading(
    icon: ImageVector,
    title: String,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AppTheme.colors.green,
            modifier = Modifier.size(22.dp),
        )
        AppLabel(
            text = title,
            selectable = false,
            modifier = Modifier.weight(1f),
            fontSize = AppTheme.typeScale.card,
            fontWeight = FontWeight.ExtraBold,
        )
        action?.invoke()
    }
}

// ---------------------------------------------------------------------------
// Carte « Ma révision du jour »
// ---------------------------------------------------------------------------

@Composable
private fun DayCard(
    state: ReviewDashboardUiState,
    onOpenStudy: (StudySession.Request) -> Unit,
) {
    AppCard(background = AppTheme.colors.soft) {
        DashboardHeading(icon = Icons.Outlined.EventAvailable, title = ReviewText.DAY_HEADING)

        // `IntrinsicSize.Min` et `fillMaxHeight` : le client d'origine **étire** les trois
        // colonnes, et son trait de séparation court donc sur la hauteur de la plus haute. Sans
        // eux, chaque trait s'arrêterait à la hauteur de sa propre colonne, et la ligne
        // paraîtrait cassée.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(bottom = 16.dp),
        ) {
            state.summary.forEachIndexed { index, colonne ->
                // Le trait est posé **entre** les colonnes, et non sur chacune : le poser à droite
                // de toutes ferait courir un trait sur le bord droit de la carte.
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(AppTheme.colors.line),
                    )
                }
                SummaryColumnView(colonne = colonne, modifier = Modifier.weight(1f))
            }
        }

        AppButton(
            text = ReviewText.START,
            onClick = { state.day.start?.let(onOpenStudy) },
            enabled = state.day.hasSession,
            modifier = Modifier.fillMaxWidth(),
        )

        AppLabel(
            text = state.day.footer,
            selectable = false,
            color = AppTheme.colors.muted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

/** Une colonne du résumé : une icône, un mot, un volume, une référence. */
@Composable
private fun SummaryColumnView(
    colonne: SummaryColumn,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = summaryIcon(colonne.kind),
            contentDescription = null,
            tint = colors.green,
            modifier = Modifier.size(20.dp),
        )
        AppLabel(
            text = colonne.kind.label,
            selectable = false,
            fontSize = AppTheme.typeScale.metadata,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        AppLabel(
            text = colonne.quantity,
            selectable = false,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        AppLabel(
            text = colonne.reference,
            selectable = false,
            color = colors.muted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * L'icône d'une colonne du résumé.
 *
 * Trois correspondances, et aucune n'est évidente : le client d'origine nomme ses icônes en
 * MaterialCommunityIcons, dont le jeu n'existe pas sur Android.
 *
 *  - `book-open-variant` → `MenuBook`, le livre ouvert de Material Icons ;
 *  - `sprout` → `Eco` : **aucune icône « pousse » n'existe** dans le jeu livré, et `Eco` est la
 *    feuille la plus proche. Le sens reste « ce qui vient de germer », qui est exactement la
 *    consolidation ;
 *  - `refresh` → `Refresh`, qui existe à l'identique.
 */
private fun summaryIcon(kind: ReviewText.SummaryKind): ImageVector = when (kind) {
    ReviewText.SummaryKind.CYCLE -> Icons.AutoMirrored.Outlined.MenuBook
    ReviewText.SummaryKind.CONSOLIDATION -> Icons.Outlined.Eco
    ReviewText.SummaryKind.PRIORITY -> Icons.Outlined.Refresh
}

// ---------------------------------------------------------------------------
// Carte « Mon cycle de révision »
// ---------------------------------------------------------------------------

@Composable
private fun CycleCard(
    state: ReviewDashboardUiState,
    onCycleSelected: (Int) -> Unit,
    onQuantitySelected: (String) -> Unit,
) {
    val colors = AppTheme.colors
    val cycle = state.cycle
    // Le sélecteur est un état d'interface : `rememberSaveable` le fait survivre à une rotation
    // sans le confier au ViewModel, qui ne publie que ce qui décide.
    var expanded by rememberSaveable { mutableStateOf(false) }

    AppCard {
        DashboardHeading(
            icon = Icons.Outlined.BarChart,
            title = ReviewText.CYCLE_HEADING,
            action = {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 44.dp, minWidth = 64.dp)
                        .clickable(role = Role.Button) { expanded = !expanded }
                        .semantics { contentDescription = ReviewText.CYCLE_EDIT_LABEL },
                    contentAlignment = Alignment.Center,
                ) {
                    AppLabel(
                        text = ReviewText.CYCLE_EDIT,
                        selectable = false,
                        color = colors.green,
                        fontSize = AppTheme.typeScale.secondary,
                    )
                }
            },
        )

        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ReviewText.CYCLE_DAY_OPTIONS.forEach { days ->
                    Box(modifier = Modifier.weight(1f)) {
                        AppButton(
                            text = ReviewText.cycleOptionLabel(days),
                            onClick = {
                                onCycleSelected(days)
                                expanded = false
                            },
                            // Le bouton de la durée **retenue** est le seul plein : c'est la
                            // variante secondaire du client d'origine, lue sur la durée du cycle.
                            secondary = cycle.lengthDays != days,
                            small = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ReviewText.QUANTITY_KEYS.forEach { key ->
                    AppButton(
                        text = ReviewText.quantityLabel(key),
                        onClick = {
                            onQuantitySelected(key)
                            expanded = false
                        },
                        // La quantité retenue n'est marquée que si le mode est `quantity` : en mode
                        // `cycle`, aucune quantité ne fait foi, et en marquer une mentirait.
                        secondary = cycle.mode != ReviewText.QUANTITY_MODE || cycle.quantity != key,
                        small = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AppLabel(text = cycle.modeLabel, selectable = false)
            AppLabel(text = cycle.dayCounter, selectable = false)
        }

        ProgressTrack(value = cycle.ratio)

        AppLabel(
            text = cycle.percent,
            selectable = false,
            color = colors.green,
            fontSize = AppTheme.typeScale.secondary,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )

        AppLabel(
            text = cycle.corpusSummary,
            selectable = false,
            color = colors.muted,
            fontSize = AppTheme.typeScale.metadata,
            modifier = Modifier.padding(top = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            cycle.stats.forEach { stat ->
                Column(modifier = Modifier.weight(1f)) {
                    AppLabel(
                        text = stat.value,
                        selectable = false,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    AppLabel(
                        text = stat.label,
                        selectable = false,
                        color = colors.muted,
                        fontSize = AppTheme.typeScale.metadata,
                    )
                }
            }
        }

        // Surface douce, **sans bordure ni ombre** : voir la note de tête, point 4.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.soft)
                .padding(12.dp),
        ) {
            AppLabel(
                text = ReviewText.RHYTHM_LABEL,
                selectable = false,
                color = colors.muted,
                fontSize = AppTheme.typeScale.secondary,
            )
            AppLabel(
                text = cycle.rhythm,
                selectable = false,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        AppLabel(
            text = cycle.footnote,
            selectable = false,
            color = colors.muted,
            fontSize = AppTheme.typeScale.secondary,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Carte « Nouveaux versets à consolider »
// ---------------------------------------------------------------------------

@Composable
private fun ConsolidationCard(
    state: ReviewDashboardUiState,
    onOpenStudy: (StudySession.Request) -> Unit,
) {
    val colors = AppTheme.colors
    var all by rememberSaveable { mutableStateOf(false) }

    AppCard {
        DashboardHeading(icon = Icons.Outlined.Eco, title = ReviewText.CONSOLIDATION_HEADING)

        AppLabel(
            text = ReviewText.CONSOLIDATION_NOTE,
            selectable = false,
            color = colors.muted,
            fontSize = AppTheme.typeScale.secondary,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        if (state.consolidations.isEmpty()) {
            AppLabel(
                text = ReviewText.CONSOLIDATION_EMPTY,
                selectable = false,
                color = colors.muted,
            )
            return@AppCard
        }

        // Le repli est une décision d'**affichage** : la liste arrive complète, et l'écran en
        // montre cinq tant que le bouton n'a pas été touché.
        val visibles = if (all) state.consolidations else state.consolidations.take(COLLAPSED)
        visibles.forEach { ligne ->
            ConsolidationRowView(ligne = ligne, onOpenStudy = onOpenStudy)
        }

        if (state.consolidationOverflow) {
            AppButton(
                text = ReviewText.consolidationToggle(all),
                onClick = { all = !all },
                secondary = true,
                small = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Une ligne de consolidation, avec ses trois étapes. */
@Composable
private fun ConsolidationRowView(
    ligne: ConsolidationLine,
    onOpenStudy: (StudySession.Request) -> Unit,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            // L'ordre de la chaîne compte : `clip` avant `background` pour que le fond suive le
            // rayon, `clickable` après `background` pour que l'ondulation reste visible.
            .clip(shape)
            .background(colors.paper)
            .border(1.dp, colors.line, shape)
            .clickable(role = Role.Button, onClick = { onOpenStudy(ligne.study) })
            .semantics { contentDescription = ligne.label }
            .padding(12.dp),
    ) {
        AppLabel(text = ligne.reference, selectable = false, fontWeight = FontWeight.ExtraBold)
        AppLabel(
            text = ligne.detail,
            selectable = false,
            color = colors.muted,
            fontSize = AppTheme.typeScale.secondary,
            modifier = Modifier.padding(top = 4.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            ligne.steps.forEach { step ->
                StepView(step = step, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Une étape de consolidation : un trait, une icône, l'échéance, l'état, la date.
 *
 * Le trait supérieur fait **2 px**, et non 1 : à 1 px il se confondrait avec la bordure de la
 * ligne, et le seul repère qui dit où en est la consolidation disparaîtrait. Sa couleur suit le
 * fait, et l'icône suit la même règle que lui — verte dès que l'étape est faite ou due.
 */
@Composable
private fun StepView(step: StepLine, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val enRegle = step.completed || step.dueOrPast

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (step.completed) colors.green else colors.line),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Icon(
            imageVector = when {
                step.completed -> Icons.Outlined.CheckCircle
                step.dueOrPast -> Icons.Outlined.Schedule
                else -> Icons.Outlined.RadioButtonUnchecked
            },
            contentDescription = null,
            tint = if (enRegle) colors.green else colors.muted,
            modifier = Modifier.size(20.dp),
        )
        AppLabel(
            text = step.label,
            selectable = false,
            fontSize = AppTheme.typeScale.secondary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp),
        )
        AppLabel(
            text = step.status,
            selectable = false,
            color = colors.muted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
        AppLabel(
            text = step.date,
            selectable = false,
            color = colors.muted,
            fontSize = 10.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// Carte « À retravailler »
// ---------------------------------------------------------------------------

@Composable
private fun PriorityCard(
    state: ReviewDashboardUiState,
    onOpenStudy: (StudySession.Request) -> Unit,
) {
    val colors = AppTheme.colors
    var all by rememberSaveable { mutableStateOf(false) }

    AppCard {
        DashboardHeading(icon = Icons.Outlined.Refresh, title = ReviewText.PRIORITY_HEADING)

        // Le compte en tête est celui des versets **dus aujourd'hui**, et non celui de la liste :
        // la carte montre tous les versets marqués difficiles, mais n'en annonce que le travail du
        // jour. Le mot est calculé par le renderer, qui lit la même colonne du résumé.
        AppLabel(
            text = state.priorityHeadline,
            selectable = false,
            color = colors.green,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (state.priorities.isEmpty()) {
            AppLabel(
                text = ReviewText.PRIORITY_EMPTY,
                selectable = false,
                color = colors.muted,
                fontSize = AppTheme.typeScale.secondary,
            )
            return@AppCard
        }

        val visibles = if (all) state.priorities else state.priorities.take(COLLAPSED)
        visibles.forEach { ligne ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 58.dp)
                    .clickable(role = Role.Button, onClick = { onOpenStudy(ligne.study) })
                    .padding(vertical = 10.dp),
            ) {
                // Le chevron est une décoration du client d'origine, et non un mot : il est écrit
                // ici plutôt que dans `ReviewText`, qui ne porte que ce qui peut mentir.
                AppLabel(
                    text = "${ligne.reference} ›",
                    selectable = false,
                    fontWeight = FontWeight.Bold,
                )
                AppLabel(
                    text = ligne.detail,
                    selectable = false,
                    color = colors.muted,
                    fontSize = AppTheme.typeScale.secondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.line),
            )
        }

        if (state.priorityOverflow) {
            AppButton(
                text = ReviewText.priorityToggle(all),
                onClick = { all = !all },
                secondary = true,
                small = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Carte « Mon suivi »
// ---------------------------------------------------------------------------

@Composable
private fun TrackingCard(
    state: ReviewDashboardUiState,
    onStatistics: () -> Unit,
    onRecitations: () -> Unit,
) {
    AppCard {
        DashboardHeading(icon = Icons.Outlined.ShowChart, title = ReviewText.TRACKING_HEADING)

        AppLabel(
            text = state.tracking.corpusSummary,
            selectable = false,
            color = AppTheme.colors.muted,
            fontSize = AppTheme.typeScale.secondary,
        )
        AppLabel(
            text = state.tracking.revisions,
            selectable = false,
            modifier = Modifier.padding(top = 8.dp),
        )

        AppButton(
            text = ReviewText.STATISTICS,
            onClick = onStatistics,
            secondary = true,
            modifier = Modifier.fillMaxWidth(),
        )
        AppButton(
            text = ReviewText.RECITATIONS,
            onClick = onRecitations,
            secondary = true,
            small = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Combien de lignes une carte montre avant de se replier.
 *
 * C'est le `slice(0, 5)` du client d'origine. La valeur est ici, et non dans le renderer, parce
 * que le repli est une décision d'affichage : la liste arrive complète, et c'est le bouton
 * « Voir toutes… » qui la déplie.
 */
private const val COLLAPSED = 5
