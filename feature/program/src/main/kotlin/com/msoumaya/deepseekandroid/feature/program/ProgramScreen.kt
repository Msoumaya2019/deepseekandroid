package com.msoumaya.deepseekandroid.feature.program

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.msoumaya.deepseekandroid.core.data.LocalAppContainer
import com.msoumaya.deepseekandroid.core.design.component.AppButton
import com.msoumaya.deepseekandroid.core.design.component.AppCard
import com.msoumaya.deepseekandroid.core.design.component.AppDailyTaskCard
import com.msoumaya.deepseekandroid.core.design.component.AppHeading
import com.msoumaya.deepseekandroid.core.design.component.AppHero
import com.msoumaya.deepseekandroid.core.design.component.AppIconButton
import com.msoumaya.deepseekandroid.core.design.component.AppInlineIcon
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.component.AppSectionHeader
import com.msoumaya.deepseekandroid.core.design.component.AppSegmentedControl
import com.msoumaya.deepseekandroid.core.design.component.ArabicText
import com.msoumaya.deepseekandroid.core.design.component.ProgressTrack
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.StudySession

// ---------------------------------------------------------------------------
// Écran de programme
// ---------------------------------------------------------------------------
// Portage de `ProgramScreen` (`src/ui/MainScreens.tsx:35`).
//
// **Une redondance de l'original, reproduite telle quelle.** La séance du jour apparaît **deux
// fois** : une fois dans la carte « Aujourd'hui », qui porte la date et le bouton d'ouverture,
// une fois dans le couple « Prochaine séance » / « À revoir aujourd'hui », qui la résume. Ce
// n'est pas un oubli de portage : c'est la disposition du client d'origine. La supprimer serait
// une décision de conception, et le cahier des charges demande un portage — la décision se
// prendra sur l'écran, pas dans la traduction.
//
// **Ce qui n'est pas encore là.** Le client d'origine ouvre depuis cet écran le tableau de bord
// des révisions, qui arrive avec la même phase. Le rappel existe donc déjà (`onOpenReviews`),
// et la carte de révision y mène quand aucun passage n'est dû.
//
// Trois mesures relevées à la source, qui ne se devinent pas :
//
//   1. La bande d'en-tête **déborde** de la marge des autres blocs. Elle est donc posée hors de
//      la colonne en retrait de 18 px, comme sur l'accueil — pas de marge négative.
//
//   2. Le sélecteur de période est **deux fois plus large que le titre** qu'il accompagne
//      (« À venir » occupe 35 %, le sélecteur 65 %). Un partage égal écraserait le sélecteur et
//      laisserait un grand vide à gauche.
//
//   3. La pastille de date d'une séance à venir **n'est pas un carré** : 42 px de large pour une
//      hauteur libre, avec le jour de la semaine au-dessus du quantième. C'est ce qui la
//      distingue d'un simple badge.
//
//   4. Deux cartes **surchargent leur marge interne** : la liste « À venir » la réduit à 4 px
//      pour que ses traits de séparation courent presque d'un bord à l'autre, et le bandeau de
//      la semaine à 12 px. Laisser la marge par défaut de 16 px aurait creusé un vide que
//      l'original n'a pas.
//
//   5. Le nom arabe d'une séance à venir est **borné** à 21 % de la ligne, et non large d'une
//      valeur fixe : voir `UpcomingRow`.
// ---------------------------------------------------------------------------

/**
 * Le programme : la séance du jour, l'objectif, le rattrapage, les séances à venir, l'historique.
 *
 * @param onOpenReader ouverture du lecteur sur un verset, en lecture libre.
 * @param onOpenStudy ouverture du lecteur **sur une séance**. Distinct de [onOpenReader] : une
 *   lecture libre n'a pas de progression à valider, une séance en a une, et les confondre ferait
 *   perdre la seconde sans que rien ne le dise.
 * @param onOpenGoal ouverture de l'écran d'objectif — le crayon de la carte « Mon objectif », et
 *   le repli d'une carte d'apprentissage sans séance.
 * @param onOpenReviews ouverture du tableau de bord des révisions, quand aucun passage n'est dû.
 * @param viewModel état de l'écran. Par défaut, celui du conteneur applicatif.
 */
@Composable
fun ProgramScreen(
    modifier: Modifier = Modifier,
    onOpenReader: (Int) -> Unit = {},
    onOpenStudy: (StudySession.Request) -> Unit = {},
    onOpenGoal: () -> Unit = {},
    onOpenReviews: () -> Unit = {},
    viewModel: ProgramViewModel = viewModel(
        factory = ProgramViewModel.factory(LocalAppContainer.current),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ProgramContent(
        state = state,
        modifier = modifier,
        onOpenReader = onOpenReader,
        onOpenStudy = onOpenStudy,
        onOpenGoal = onOpenGoal,
        onOpenReviews = onOpenReviews,
        onPeriodSelected = viewModel::onPeriodSelected,
    )
}

@Composable
private fun ProgramContent(
    state: ProgramUiState,
    modifier: Modifier = Modifier,
    onOpenReader: (Int) -> Unit = {},
    onOpenStudy: (StudySession.Request) -> Unit = {},
    onOpenGoal: () -> Unit = {},
    onOpenReviews: () -> Unit = {},
    onPeriodSelected: (ProgramPeriod) -> Unit = {},
) {
    when {
        state.failure != null -> ProgramFailure(message = state.failure, modifier = modifier)

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

        else -> {
            // Les deux gestes du jour, résolus **une fois** et partagés par les cartes qui les
            // proposent : la carte « Aujourd'hui » et le couple « Prochaine séance » /
            // « À revoir aujourd'hui » montrent la même tâche, et deux résolutions séparées
            // pourraient finir par ne plus mener au même endroit.
            val openLearning: () -> Unit = { ouvrir(state.today?.learning, onOpenStudy, onOpenReader, onOpenGoal) }
            val openReview: () -> Unit = { ouvrir(state.today?.revision, onOpenStudy, onOpenReader, onOpenReviews) }

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                AppHero(
                    title = "Mon programme",
                    subtitle = "Ton parcours du jour, calculé selon ton objectif",
                )

                Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                    state.today?.let { today ->
                        TodayCard(today = today, onOpenLearning = openLearning, onOpenReview = openReview)
                    }

                    state.goal?.let { goal ->
                        GoalCard(goal = goal, onEdit = onOpenGoal)
                    }

                    state.resumes.forEach { line ->
                        StudyResumeCard(
                            line = line,
                            // Une reprise rouvre **la séance** sur son reste : c'est ce qui permet
                            // de continuer la progression au lieu d'en recommencer une. La requête
                            // est déjà construite par le renderer ; la rebâtir ici obligerait
                            // l'écran à savoir comment une reprise devient une séance, et deux
                            // constructions de la même requête finissent par diverger.
                            onResume = { onOpenStudy(line.study) },
                        )
                    }

                    if (state.today != null) {
                        NextRow(today = state.today, onOpenLearning = openLearning, onOpenReview = openReview)
                    }

                    state.week?.let { week -> WeekCard(week = week) }

                    if (state.catchUp.isNotEmpty()) {
                        CatchUpCard(lines = state.catchUp, onOpenStudy = onOpenStudy)
                    }

                    UpcomingSection(
                        period = state.period,
                        lines = state.upcoming,
                        onPeriodSelected = onPeriodSelected,
                        onOpenStudy = onOpenStudy,
                    )

                    HistorySection(lines = state.history)
                }
            }
        }
    }
}

/**
 * Résout un geste de tâche en une des trois destinations.
 *
 * L'ordre est celui du dépôt d'origine et il compte : une tâche à servir ouvre **la séance**,
 * une tâche sans séance ouvre la lecture libre de son verset, et une carte sans rien du tout
 * renvoie vers l'écran qui permet de la programmer. Un `verseId` nul n'est pas une erreur : c'est
 * exactement ainsi que « aucune séance » se distingue de « une séance ».
 */
private fun ouvrir(
    task: Task?,
    onOpenStudy: (StudySession.Request) -> Unit,
    onOpenReader: (Int) -> Unit,
    onFallback: () -> Unit,
) {
    val seance = task?.study
    val cible = task?.verseId
    when {
        seance != null -> onOpenStudy(seance)
        cible != null -> onOpenReader(cible)
        else -> onFallback()
    }
}

/** Carte « Aujourd'hui » : la date en toutes lettres, les deux tâches, l'ouverture du jour. */
@Composable
private fun TodayCard(
    today: Today,
    onOpenLearning: () -> Unit,
    onOpenReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors

    AppCard(
        modifier = modifier.padding(top = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppInlineIcon(icon = Icons.Outlined.EventAvailable, tint = colors.green)
            Column {
                AppHeading(text = "Aujourd'hui")
                AppLabel(
                    text = today.dateLabel,
                    selectable = false,
                    fontSize = 12.sp,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppDailyTaskCard(
                title = "Apprentissage",
                passage = today.learning.passage,
                details = today.learning.details,
                onPress = onOpenLearning,
                modifier = Modifier.weight(1f),
            )
            today.revision?.let { revision ->
                AppDailyTaskCard(
                    title = "Révision",
                    passage = revision.passage,
                    details = revision.details,
                    onPress = onOpenReview,
                    modifier = Modifier.weight(1f),
                    revision = true,
                )
            }
        }

        today.nextSession?.let { next ->
            AppLabel(
                text = next,
                selectable = false,
                fontSize = 11.sp,
                color = colors.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        AppButton(text = "Ouvrir la lecture du jour ›", onClick = onOpenLearning)
    }
}

/** Carte « Mon objectif » : libellé, rythme, avancement, crayon. */
@Composable
private fun GoalCard(goal: GoalLine, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors

    AppCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppInlineIcon(icon = Icons.Outlined.TrackChanges, tint = colors.green)
            Column(modifier = Modifier.weight(1f)) {
                AppHeading(text = "Mon objectif")
                AppLabel(
                    text = goal.label,
                    selectable = false,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                AppLabel(
                    text = "Rythme : ${goal.pace}",
                    selectable = false,
                    fontSize = 12.sp,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            AppIconButton(
                icon = Icons.Outlined.Edit,
                label = "Modifier mon objectif",
                onClick = onEdit,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                ProgressTrack(value = goal.ratio)
            }
            AppLabel(text = "${goal.percent} %", selectable = false, fontSize = 12.sp, color = colors.muted)
        }
    }
}

/** Le couple « Prochaine séance » / « À revoir aujourd'hui ». */
@Composable
private fun NextRow(
    today: Today,
    onOpenLearning: () -> Unit,
    onOpenReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppDailyTaskCard(
            title = "Prochaine séance",
            // La forme **courte** de la tâche, et non celle de la carte « Aujourd'hui » : ici le
            // couple résume — « 5 versets » sans la page —, et ses replis disent autre chose que
            // ceux de la carte du jour. Voir `Task`.
            passage = today.learning.compactPassage,
            details = today.learning.compactDetails,
            onPress = onOpenLearning,
            modifier = Modifier.weight(1f),
        )
        today.revision?.let { revision ->
            AppDailyTaskCard(
                title = "À revoir aujourd'hui",
                passage = revision.compactPassage,
                details = revision.compactDetails,
                onPress = onOpenReview,
                modifier = Modifier.weight(1f),
                revision = true,
            )
        }
    }
}

/** Bandeau « Objectif de la semaine ». */
@Composable
private fun WeekCard(week: WeekLine, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors

    // Marge interne de 12 px, relevée à la source : la carte du dépôt d'origine la surcharge.
    AppCard(modifier = modifier, padding = 12.dp) {
        AppLabel(
            text = "Objectif de la semaine · ${week.percent} %",
            selectable = false,
            fontSize = 12.sp,
            color = colors.muted,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        ProgressTrack(value = week.ratio)
    }
}

/** Carte « À rattraper » : les séances en retard, les dix premières. */
@Composable
private fun CatchUpCard(
    lines: List<CatchUpLine>,
    onOpenStudy: (StudySession.Request) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        AppHeading(text = "À rattraper", size = 18.sp)
        lines.forEach { line ->
            AppButton(
                text = line.label,
                onClick = { onOpenStudy(line.study) },
                secondary = true,
                small = true,
            )
        }
    }
}

/** Le titre « À venir », son sélecteur de période, et la liste. */
@Composable
private fun UpcomingSection(
    period: ProgramPeriod,
    lines: List<UpcomingLine>,
    onPeriodSelected: (ProgramPeriod) -> Unit,
    onOpenStudy: (StudySession.Request) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.weight(0.35f)) {
            AppHeading(text = "À venir")
        }
        Box(modifier = Modifier.weight(0.65f)) {
            AppSegmentedControl(
                options = ProgramPeriod.labels,
                value = period.label,
                onSelect = { label ->
                    ProgramPeriod.entries.firstOrNull { it.label == label }?.let(onPeriodSelected)
                },
            )
        }
    }

    // Marge interne de 4 px, relevée à la source : les traits de séparation des lignes courent
    // ainsi presque d'un bord à l'autre, au lieu de s'arrêter à 16 px du cadre.
    AppCard(padding = 4.dp) {
        if (lines.isEmpty()) {
            AppLabel(
                text = "Aucune séance sur les 10 prochains jours.",
                selectable = false,
                color = colors.muted,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            lines.forEach { line -> UpcomingRow(line = line, onOpenStudy = onOpenStudy) }
        }
    }
}

/** Part de la largeur d'une ligne que le nom arabe peut occuper au plus. Relevé à la source. */
private const val ARABIC_SHARE = 0.21f

/**
 * Une séance à venir.
 *
 * La pastille porte l'initiale du jour au-dessus du quantième ; le nom arabe de la sourate de
 * tête est rappelé à droite, puis le chevron. Le clic ouvre **la séance**, pas le verset : c'est
 * ce qui permet de la valider.
 *
 * Le nom arabe est **borné** à 21 % de la ligne, comme à la source — une borne, et non une
 * largeur. Un nom court garde sa largeur naturelle ; un nom long ne peut pas écraser la référence
 * du passage. Une largeur fixe ferait l'un ou l'autre selon la taille de l'écran, et la source
 * dit « au plus », pas « exactement ».
 */
@Composable
private fun UpcomingRow(
    line: UpcomingLine,
    onOpenStudy: (StudySession.Request) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxArabic = maxWidth * ARABIC_SHARE

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable(role = Role.Button) { onOpenStudy(line.study) }
                .bottomDivider(colors.line)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier
                    .width(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(colors.selected)
                    .padding(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppLabel(text = line.weekday, selectable = false, fontSize = 9.sp, color = colors.green)
                AppHeading(text = line.day.toString(), size = 23.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                AppHeading(text = line.passage, size = 17.sp)
                AppLabel(
                    text = line.details,
                    selectable = false,
                    fontSize = 11.sp,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
                AppLabel(text = line.whenLabel, selectable = false, fontSize = 10.sp, color = colors.muted)
            }

            Box(modifier = Modifier.widthIn(max = maxArabic)) {
                ArabicText(text = line.arabic, color = colors.gold, fontSize = 20.sp)
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * L'historique, replié par défaut.
 *
 * Le repli est un état d'interface : il est tenu par `rememberSaveable`, donc il survit à une
 * rotation, et il ne remonte pas dans l'état affichable parce qu'il ne change rien à ce que
 * l'application croit appris.
 */
@Composable
private fun HistorySection(lines: List<HistoryLine>, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    AppSectionHeader(
        title = "Historique",
        action = if (expanded) "Masquer" else "Voir tout",
        onAction = { expanded = !expanded },
        modifier = modifier,
    )

    if (expanded) {
        lines.forEach { line ->
            AppCard {
                AppLabel(
                    text = line.label,
                    selectable = false,
                    fontSize = 12.sp,
                    color = AppTheme.colors.muted,
                )
                AppHeading(text = line.passage, size = 17.sp)
            }
        }
    }
}

/** Écran d'échec : le référentiel coranique n'a pas pu être chargé. */
@Composable
private fun ProgramFailure(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        AppHeading(text = "Contenu indisponible", size = 21.sp)
        AppLabel(
            text = message,
            selectable = false,
            color = AppTheme.colors.muted,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Trait horizontal de 1 px sur le bord bas, à l'intérieur du composant. */
private fun Modifier.bottomDivider(color: Color, thickness: Dp = 1.dp): Modifier =
    drawBehind {
        drawRect(
            color = color,
            topLeft = Offset(0f, size.height - thickness.toPx()),
            size = Size(size.width, thickness.toPx()),
        )
    }
