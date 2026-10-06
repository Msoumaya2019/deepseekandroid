package com.msoumaya.deepseekandroid.feature.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.msoumaya.deepseekandroid.core.data.LocalAppContainer
import com.msoumaya.deepseekandroid.core.design.component.AppButton
import com.msoumaya.deepseekandroid.core.design.component.AppCard
import com.msoumaya.deepseekandroid.core.design.component.AppCheckChoice
import com.msoumaya.deepseekandroid.core.design.component.AppHeading
import com.msoumaya.deepseekandroid.core.design.component.AppIconButton
import com.msoumaya.deepseekandroid.core.design.component.AppInlineIcon
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.component.AppSectionHeader
import com.msoumaya.deepseekandroid.core.design.component.AppSegmentedControl
import com.msoumaya.deepseekandroid.core.design.component.ArabicText
import com.msoumaya.deepseekandroid.core.design.component.initialeDe
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.QuizText

// ---------------------------------------------------------------------------
// Écran « Quiz »
// ---------------------------------------------------------------------------
// Portage de `QuizScreen` (`src/ui/QuizScreen.tsx`), six vues dans un seul écran.
//
// **Ce que l'écran ne fait pas.** Il ne calcule rien : `QuizViewModel` publie un [QuizUiState] où
// chaque libellé est déjà résolu, et `QuizRenderer` porte ces décisions — c'est ce qui les rend
// éprouvables sans appareil. L'écran ne garde donc aucun état : la vue courante, le jour
// consulté, l'ami choisi et la longueur du défi vivent dans la sélection du `ViewModel`, publiée
// avec le reste. Deux sources pour la même chose — la sélection affichée et les chiffres
// calculés — divergeraient sans que rien ne le dise.
//
// **« Retour » a trois issues, et c'est l'état qui tranche.** Depuis l'accueil il **ferme**
// l'écran ; depuis un défi il remonte à la liste des défis ; depuis les quatre autres vues il
// rentre à l'accueil. [QuizUiState.backCloses] dit laquelle, et il est décidé au même endroit que
// le titre : recalculer ici « la vue est-elle l'accueil ? » ferait une seconde copie d'une règle
// qui a déjà trois branches.
//
// **Deux cartes sont écrites à la main, et non posées avec `AppCard`.** `AppCard` tire toujours sa
// bordure de `colors.line` : la carte « Question du jour » de l'accueil veut une bordure dorée, et
// une proposition de réponse veut une bordure qui change de couleur selon son état. Le procédé est
// celui d'`AppDailyTaskCard` — fond, bordure, coin, clic —, appliqué ici aux deux seuls endroits
// qui en ont besoin.
//
// **Le médaillon d'un joueur n'affiche que son initiale.** Le chemin d'un avatar distant n'est pas
// transporté par l'état, et `feature:social` a déjà tranché ce point pour la conversation : charger
// une image suppose de signer une URL dans le stockage Supabase. L'initiale est exactement ce que
// l'original affiche quand il n'y a pas d'image, donc le repli n'est pas une invention.
//
// **Ce qui n'est pas là.** `QuizHomeCards` et `QuizStats` — les deux blocs que l'accueil et
// « Progrès » attendent — ne sont pas ici : ils appartiennent à leurs écrans, et ils viendront
// avec eux. La source d'une correction s'ouvre par le gestionnaire d'URI du système, comme
// `Linking.openURL` dans l'original.
// ---------------------------------------------------------------------------

/** Retrait horizontal du contenu. Valeur du programme, du Coran, de la conversation et du Quiz. */
private val SCREEN_PADDING = 18.dp

/**
 * Marge basse du contenu.
 *
 * `paddingBottom:30` à la source : l'écran occupe tout l'écran, sans barre basse, donc le dernier
 * élément doit être détaché du bord.
 */
private val SCREEN_BOTTOM_PADDING = 30.dp

/** Fond de la carte « Question du jour » : `colors.gold+'12'` à la source, soit 18 sur 255. */
private const val GOLD_WASH = 0.07f

/** Bordure de la même carte : `colors.gold+'35'`, soit 53 sur 255. */
private const val GOLD_EDGE = 0.21f

/** Fond d'une mauvaise réponse : `colors.red+'0C'` à la source, soit 12 sur 255. */
private const val WRONG_WASH = 0.05f

/** Côté du médaillon d'un joueur (`size={42}` par défaut dans `FriendAvatar`). */
private val MEDALLION_SIZE = 42.dp

/**
 * Le Quiz : question du jour, historique, défis entre amis.
 *
 * @param onClose fermeture de l'écran, demandée depuis l'accueil. C'est la route qui sait où l'on
 *   retourne ; l'écran ne fait que dire quand.
 * @param friendId ami à défier d'emblée, ou `null`. Argument de la route, transmis au `ViewModel`.
 * @param challengeId défi à ouvrir d'emblée, ou `null`. Argument de la route.
 */
@Composable
fun QuizScreen(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    friendId: String? = null,
    challengeId: String? = null,
    viewModel: QuizViewModel = viewModel(
        factory = QuizViewModel.factory(LocalAppContainer.current, friendId, challengeId),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    QuizContent(
        state = state,
        modifier = modifier,
        onClose = onClose,
        onBack = viewModel::onBack,
        onOpenDaily = viewModel::onOpenDaily,
        onOpenView = viewModel::onOpenView,
        onOpenChallenge = viewModel::onOpenChallenge,
        onSelectFriend = viewModel::onSelectFriend,
        onSelectCount = viewModel::onSelectCount,
        onSelectSet = viewModel::onSelectSet,
        onCreateChallenge = viewModel::onCreateChallenge,
        onAnswerDaily = viewModel::onAnswerDaily,
        onAnswerChallenge = viewModel::onAnswerChallenge,
        onToggleNotifications = viewModel::onToggleNotifications,
        onRefresh = viewModel::onRefresh,
    )
}

@Composable
internal fun QuizContent(
    state: QuizUiState,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    onBack: () -> Unit = {},
    onOpenDaily: (String?) -> Unit = {},
    onOpenView: (QuizView) -> Unit = {},
    onOpenChallenge: (String) -> Unit = {},
    onSelectFriend: (String) -> Unit = {},
    onSelectCount: (Int) -> Unit = {},
    onSelectSet: (String?) -> Unit = {},
    onCreateChallenge: () -> Unit = {},
    onAnswerDaily: (String) -> Unit = {},
    onAnswerChallenge: (String) -> Unit = {},
    onToggleNotifications: () -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    when {
        state.failure != null -> QuizFailure(message = state.failure, modifier = modifier)

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

        else -> QuizBody(
            state = state,
            modifier = modifier,
            onClose = onClose,
            onBack = onBack,
            onOpenDaily = onOpenDaily,
            onOpenView = onOpenView,
            onOpenChallenge = onOpenChallenge,
            onSelectFriend = onSelectFriend,
            onSelectCount = onSelectCount,
            onSelectSet = onSelectSet,
            onCreateChallenge = onCreateChallenge,
            onAnswerDaily = onAnswerDaily,
            onAnswerChallenge = onAnswerChallenge,
            onToggleNotifications = onToggleNotifications,
            onRefresh = onRefresh,
        )
    }
}

@Composable
private fun QuizBody(
    state: QuizUiState,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    onBack: () -> Unit = {},
    onOpenDaily: (String?) -> Unit = {},
    onOpenView: (QuizView) -> Unit = {},
    onOpenChallenge: (String) -> Unit = {},
    onSelectFriend: (String) -> Unit = {},
    onSelectCount: (Int) -> Unit = {},
    onSelectSet: (String?) -> Unit = {},
    onCreateChallenge: () -> Unit = {},
    onAnswerDaily: (String) -> Unit = {},
    onAnswerChallenge: (String) -> Unit = {},
    onToggleNotifications: () -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    val colors = AppTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier.padding(
                start = SCREEN_PADDING,
                end = SCREEN_PADDING,
                bottom = SCREEN_BOTTOM_PADDING,
            ),
        ) {
            QuizHeader(
                title = state.title,
                // La fermeture de l'écran est un geste de navigation : elle appartient à la
                // route, et l'état dit seulement si c'est ce que « Retour » doit faire ici.
                onBack = { if (state.backCloses) onClose() else onBack() },
                onHistory = { onOpenView(QuizView.HISTORY) },
            )

            state.notice?.let { notice -> NoticeCard(notice = notice, onRefresh = onRefresh) }

            if (!state.signedIn) {
                AppLabel(
                    text = QuizText.SIGNED_OUT_SCREEN,
                    selectable = false,
                    fontSize = 13.sp,
                    color = colors.muted,
                )
            }

            when (state.view) {
                QuizView.HOME -> HomeView(
                    home = state.home,
                    onOpenDaily = { onOpenDaily(null) },
                    onOpenChallenges = { onOpenView(QuizView.CHALLENGES) },
                    onOpenCreate = { onOpenView(QuizView.CREATE) },
                    onOpenHistory = { onOpenView(QuizView.HISTORY) },
                    onOpenChallenge = onOpenChallenge,
                    onToggleNotifications = onToggleNotifications,
                )

                QuizView.DAILY -> DailyView(
                    daily = state.daily,
                    onAnswer = onAnswerDaily,
                    onBackToQuiz = { onOpenView(QuizView.HOME) },
                )

                QuizView.HISTORY -> HistoryView(history = state.history, onOpenDay = onOpenDaily)

                QuizView.CHALLENGES -> ChallengesView(
                    challenges = state.challenges,
                    onOpenChallenge = onOpenChallenge,
                    onOpenCreate = { onOpenView(QuizView.CREATE) },
                )

                QuizView.CREATE -> CreateView(
                    create = state.create,
                    onSelectFriend = onSelectFriend,
                    onSelectCount = onSelectCount,
                    onSelectSet = onSelectSet,
                    onCreate = onCreateChallenge,
                )

                QuizView.CHALLENGE -> ChallengeView(
                    challenge = state.challenge,
                    onAnswer = onAnswerChallenge,
                    onRefresh = onRefresh,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// La coquille
// ---------------------------------------------------------------------------

/**
 * L'en-tête : retour, titre, historique.
 *
 * Le titre vient de l'état et non d'un `when` écrit ici : la vue et le titre se décident au même
 * endroit, donc ils ne peuvent pas se contredire — c'est la même raison qui met `backCloses` dans
 * l'état.
 */
@Composable
private fun QuizHeader(title: String, onBack: () -> Unit, onHistory: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = AppTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AppIconButton(
            icon = Icons.Outlined.ChevronLeft,
            label = QuizText.BACK,
            onClick = onBack,
        )
        AppHeading(text = title, size = 21.sp)
        AppIconButton(
            icon = Icons.Outlined.BarChart,
            label = QuizText.HISTORY_LABEL,
            onClick = onHistory,
        )
    }
}

/**
 * L'avis du dépôt : une action a échoué alors que l'écran avait déjà quelque chose à montrer.
 *
 * Le bouton « Actualiser » est **dans** la carte, et c'est l'original : l'avis nomme presque
 * toujours une panne de réseau, et la seule chose utile à faire ensuite est de réessayer.
 */
@Composable
private fun NoticeCard(notice: String, onRefresh: () -> Unit) {
    AppCard {
        AppLabel(
            text = notice,
            selectable = false,
            fontSize = 12.sp,
            color = AppTheme.colors.muted,
        )
        AppButton(
            text = QuizText.REFRESH,
            onClick = onRefresh,
            secondary = true,
            small = true,
        )
    }
}

// ---------------------------------------------------------------------------
// L'accueil
// ---------------------------------------------------------------------------

@Composable
private fun HomeView(
    home: Home?,
    onOpenDaily: () -> Unit,
    onOpenChallenges: () -> Unit,
    onOpenCreate: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenChallenge: (String) -> Unit,
    onToggleNotifications: () -> Unit,
) {
    if (home == null) return
    val colors = AppTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppInlineIcon(icon = Icons.Outlined.EmojiEvents, tint = colors.gold, size = 42.dp)
        AppHeading(text = home.heading, size = 23.sp)
        AppLabel(
            text = home.subtitle,
            selectable = false,
            fontSize = 12.sp,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AppTheme.spacing.xs),
        )
    }

    HomeLinkCard(
        title = QuizText.HOME_DAILY_TITLE,
        subtitle = QuizText.HOME_DAILY_SUB,
        detail = home.dailyStatus,
        detailColor = colors.review,
        icon = Icons.Outlined.WbSunny,
        accent = colors.gold,
        background = colors.gold.copy(alpha = GOLD_WASH),
        border = colors.gold.copy(alpha = GOLD_EDGE),
        trailing = Icons.Outlined.ChevronRight,
        onClick = onOpenDaily,
    )

    HomeLinkCard(
        title = QuizText.HOME_CHALLENGES_TITLE,
        subtitle = QuizText.HOME_CHALLENGES_SUB,
        detail = home.running,
        detailColor = colors.quizPurple,
        icon = Icons.Outlined.Groups,
        accent = colors.quizPurple,
        background = colors.quizLavender,
        // Cette carte-ci garde la bordure commune : l'original ne pose un fond que sur elle, et
        // c'est le fond — lavande — qui la distingue, pas un trait de couleur.
        border = colors.line,
        trailing = Icons.Outlined.SportsEsports,
        onClick = onOpenChallenges,
    )

    AppSectionHeader(
        title = QuizText.HOME_SECTION,
        action = QuizText.HOME_SEE_ALL,
        onAction = onOpenChallenges,
    )

    home.recent.forEach { line ->
        ChallengeRow(line = line, onClick = { onOpenChallenge(line.id) })
    }

    if (home.recent.isEmpty()) {
        AppLabel(
            text = home.noChallenge,
            selectable = false,
            fontSize = 13.sp,
            color = colors.muted,
        )
    }

    AppButton(text = QuizText.CHALLENGES_NEW, onClick = onOpenCreate, secondary = true)
    AppButton(text = QuizText.HOME_HISTORY_BUTTON, onClick = onOpenHistory, secondary = true)
    AppCheckChoice(
        label = home.notifications.label,
        selected = home.notifications.enabled,
        onPress = onToggleNotifications,
        modifier = Modifier.padding(top = AppTheme.spacing.xs),
    )
}

/**
 * Une des deux grandes cartes de l'accueil.
 *
 * Écrite à la main parce que sa bordure **n'est pas** celle d'`AppCard` : la carte de la question
 * du jour porte un liseré doré, ce que `AppCard` ne sait pas faire — elle tire toujours sa
 * bordure de `colors.line`. Le fond, lui, est doré à 7 % : c'est `colors.gold+'12'` de l'original,
 * où les deux derniers chiffres sont un canal alpha, pas une teinte.
 */
@Composable
private fun HomeLinkCard(
    title: String,
    subtitle: String,
    detail: String,
    detailColor: Color,
    icon: ImageVector,
    accent: Color,
    background: Color,
    border: Color,
    trailing: ImageVector,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = AppTheme.spacing.md)
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(17.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            AppInlineIcon(icon = icon, tint = accent, size = 34.dp)

            Column(modifier = Modifier.weight(1f)) {
                AppHeading(text = title, size = 19.sp)
                AppLabel(
                    text = subtitle,
                    selectable = false,
                    fontSize = 13.sp,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 5.dp),
                )
                AppLabel(
                    text = detail,
                    selectable = false,
                    fontSize = 12.sp,
                    color = detailColor,
                    modifier = Modifier.padding(top = 9.dp),
                )
            }

            AppInlineIcon(icon = trailing, tint = accent, size = 22.dp)
        }
    }
}

// ---------------------------------------------------------------------------
// La question du jour
// ---------------------------------------------------------------------------

@Composable
private fun DailyView(daily: Daily?, onAnswer: (String) -> Unit, onBackToQuiz: () -> Unit) {
    val colors = AppTheme.colors

    if (daily == null) {
        AppCard {
            AppLabel(text = QuizText.DAILY_NONE_TITLE, selectable = false)
            AppLabel(
                text = QuizText.DAILY_NONE_SUB,
                selectable = false,
                fontSize = 12.sp,
                color = colors.muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        return
    }

    AppLabel(
        text = daily.progress,
        selectable = false,
        fontSize = 12.sp,
        color = colors.muted,
        modifier = Modifier.padding(bottom = 10.dp),
    )
    AppLabel(text = daily.category, selectable = false, fontSize = 11.sp, color = colors.gold)
    AppHeading(text = daily.question, size = 20.sp)

    Answers(answers = daily.answers, selectable = daily.selectable, onSelect = onAnswer)

    // L'attente **remplace** la correction, et c'est l'original : tant que la réponse n'a pas
    // atteint le serveur, la bonne réponse n'est pas connue, donc `correction` est nulle — mais
    // l'écran doit dire pourquoi il n'y a rien à lire.
    if (daily.pending) {
        AppCard {
            AppLabel(text = QuizText.DAILY_PENDING_TITLE, selectable = false)
            AppLabel(
                text = QuizText.DAILY_PENDING_SUB,
                selectable = false,
                fontSize = 12.sp,
                color = colors.muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    } else {
        daily.correction?.let { correction -> CorrectionCard(correction = correction) }
    }

    AppButton(text = QuizText.BACK_TO_QUIZ, onClick = onBackToQuiz, secondary = true)
}

// ---------------------------------------------------------------------------
// Les propositions et la correction
// ---------------------------------------------------------------------------

@Composable
private fun Answers(
    answers: List<AnswerLine>,
    selectable: Boolean,
    onSelect: (String) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        answers.forEach { answer ->
            AnswerRow(answer = answer, selectable = selectable, onSelect = onSelect)
        }
    }
}

/**
 * Une proposition de réponse.
 *
 * **La teinte vient de l'état, pas d'une phrase.** `AnswerState` dit CORRECTE, FAUSSE, CHOISIE ou
 * NEUTRE ; comparer « la bonne réponse » à un libellé pour choisir une couleur est le défaut que
 * l'écran de conversation a déjà évité pour le geste de modération.
 *
 * Le libellé d'accessibilité est posé par `semantics`, et non par le texte de la proposition :
 * l'original annonce « A. Qui a reçu les premières révélations ? · réponse choisie », ce que le
 * seul texte ne dit pas. Même procédé que la ligne d'une révision dans le tableau de bord.
 */
@Composable
private fun AnswerRow(answer: AnswerLine, selectable: Boolean, onSelect: (String) -> Unit) {
    val colors = AppTheme.colors
    val judged = answer.state == AnswerState.CORRECT || answer.state == AnswerState.WRONG
    val accent = when (answer.state) {
        AnswerState.CORRECT -> colors.review
        AnswerState.WRONG -> colors.red
        AnswerState.CHOSEN, AnswerState.IDLE -> colors.muted
    }
    val background = when (answer.state) {
        AnswerState.CORRECT -> colors.reviewSoft
        AnswerState.WRONG -> colors.red.copy(alpha = WRONG_WASH)
        AnswerState.CHOSEN -> colors.selected
        AnswerState.IDLE -> colors.paper
    }
    val shape = RoundedCornerShape(13.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(background)
            // Une proposition jugée porte la couleur de son verdict ; une proposition seulement
            // choisie garde le trait commun — c'est l'original, où `good||bad` seul change la
            // bordure, et où le choix se lit alors au fond.
            .border(1.dp, if (judged) accent else colors.line, shape)
            .clickable(enabled = selectable, role = Role.Button, onClick = { onSelect(answer.id) })
            .semantics { contentDescription = answer.label }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(25.dp)
                .clip(CircleShape)
                .background(if (judged) accent else colors.surfaceSecondary),
            contentAlignment = Alignment.Center,
        ) {
            AppLabel(
                text = answer.letter,
                selectable = false,
                fontSize = 12.sp,
                color = if (judged) Color.White else colors.muted,
            )
        }

        AppLabel(
            text = answer.text,
            selectable = false,
            fontSize = 14.sp,
            color = if (judged) accent else colors.text,
            modifier = Modifier.weight(1f),
        )

        if (judged) {
            AppInlineIcon(
                icon = if (answer.state == AnswerState.CORRECT) {
                    Icons.Outlined.CheckCircle
                } else {
                    Icons.Outlined.Cancel
                },
                tint = accent,
                size = 20.dp,
            )
        }
    }
}

/**
 * La correction d'une question.
 *
 * La source n'est proposée que si l'état porte une adresse : le renderer a déjà écarté tout ce qui
 * n'est pas `https`, donc un lien `http` ou d'exécution n'arrive pas jusqu'ici.
 */
@Composable
private fun CorrectionCard(correction: Correction) {
    val colors = AppTheme.colors
    val handler = LocalUriHandler.current

    AppCard(modifier = Modifier.padding(top = 13.dp), background = colors.reviewSoft) {
        AppHeading(text = correction.title, size = 18.sp)

        correction.explanation?.let { texte ->
            AppLabel(
                text = texte,
                selectable = false,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = AppTheme.spacing.sm),
            )
        }

        correction.arabic?.let { arabe -> ArabicText(text = arabe) }

        correction.translation?.let { traduction ->
            AppLabel(
                text = traduction,
                selectable = false,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        correction.source?.let { source ->
            AppLabel(
                text = source,
                selectable = false,
                fontSize = 12.sp,
                color = colors.muted,
                modifier = Modifier.padding(top = AppTheme.spacing.sm),
            )
        }

        correction.sourceUrl?.let { url ->
            AppButton(
                text = QuizText.SEE_SOURCE,
                onClick = { handler.openUri(url) },
                secondary = true,
                small = true,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// L'historique
// ---------------------------------------------------------------------------

@Composable
private fun HistoryView(history: List<HistoryLine>, onOpenDay: (String) -> Unit) {
    val colors = AppTheme.colors

    history.forEach { line ->
        AppCard(onClick = { onOpenDay(line.day) }) {
            AppLabel(text = line.label, selectable = false)
            AppLabel(
                text = line.status,
                selectable = false,
                fontSize = 12.sp,
                // La teinte vient de `Tone`, qui a trois valeurs ; l'état ne porte donc pas de
                // couleur, ce qui lierait le domaine à la palette.
                color = when (line.tone) {
                    Tone.MUTED -> colors.muted
                    Tone.GOOD -> colors.review
                    Tone.BAD -> colors.red
                },
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }

    if (history.isEmpty()) {
        AppLabel(text = QuizText.HISTORY_EMPTY, selectable = false)
    }
}

// ---------------------------------------------------------------------------
// La liste des défis
// ---------------------------------------------------------------------------

@Composable
private fun ChallengesView(
    challenges: List<ChallengeLine>,
    onOpenChallenge: (String) -> Unit,
    onOpenCreate: () -> Unit,
) {
    val colors = AppTheme.colors

    AppButton(text = QuizText.CHALLENGES_NEW, onClick = onOpenCreate)
    AppLabel(
        text = QuizText.CHALLENGES_RULE,
        selectable = false,
        fontSize = 12.sp,
        color = colors.muted,
        modifier = Modifier.padding(vertical = AppTheme.spacing.sm),
    )

    challenges.forEach { line ->
        ChallengeRow(line = line, onClick = { onOpenChallenge(line.id) })
    }

    if (challenges.isEmpty()) {
        AppLabel(text = QuizText.CHALLENGES_EMPTY, selectable = false)
    }
}

/**
 * Une ligne de défi.
 *
 * Le statut n'est mis en avant que quand c'est mon tour : `yourTurn` est décidé par le renderer à
 * partir du même statut qui remplit le libellé, donc la couleur et le mot ne peuvent pas se
 * contredire.
 */
@Composable
private fun ChallengeRow(line: ChallengeLine, onClick: () -> Unit) {
    val colors = AppTheme.colors

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        bottomSpacing = 7.dp,
        padding = 12.dp,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PlayerMedallion(name = line.name)

            Column(modifier = Modifier.weight(1f)) {
                AppLabel(
                    text = line.name,
                    selectable = false,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                AppLabel(
                    text = line.status,
                    selectable = false,
                    fontSize = 12.sp,
                    color = if (line.yourTurn) colors.green else colors.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            AppLabel(
                text = line.detail,
                selectable = false,
                fontSize = 11.sp,
                color = colors.muted,
            )

            AppInlineIcon(icon = Icons.Outlined.ChevronRight, tint = colors.muted, size = 20.dp)
        }
    }
}

/**
 * Le médaillon d'un joueur : un rond, une initiale, rien d'autre.
 *
 * `FriendMedallion` de `feature:social` fait la même chose, mais il est `internal` à son module —
 * et il porte une pastille de présence et un compte de messages non lus, dont un adversaire de
 * Quiz n'a que faire. La règle de l'initiale, elle, est partagée : c'est `initialeDe`, promue
 * publique pour cela, qui lit un **point de code** et non une unité UTF-16.
 */
@Composable
private fun PlayerMedallion(name: String, size: Dp = MEDALLION_SIZE) {
    val colors = AppTheme.colors

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.soft)
            .border(1.dp, colors.softBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AppLabel(
            text = initialeDe(name) ?: "?",
            selectable = false,
            color = colors.green,
            // `size*0.43` à la source : la lettre grandit avec le rond.
            fontSize = (size.value * 0.43f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ---------------------------------------------------------------------------
// La création d'un défi
// ---------------------------------------------------------------------------

@Composable
private fun CreateView(
    create: Create?,
    onSelectFriend: (String) -> Unit,
    onSelectCount: (Int) -> Unit,
    onSelectSet: (String?) -> Unit,
    onCreate: () -> Unit,
) {
    if (create == null) return
    val colors = AppTheme.colors

    AppHeading(text = QuizText.CREATE_CHOOSE, size = 19.sp)

    create.friends.forEach { friend ->
        FriendRow(friend = friend, onSelect = { onSelectFriend(friend.id) })
    }

    if (create.friends.isEmpty()) {
        AppLabel(
            text = create.noFriend,
            selectable = false,
            fontSize = 13.sp,
            color = colors.muted,
            modifier = Modifier.padding(vertical = AppTheme.spacing.md),
        )
    }

    AppButton(
        text = QuizText.selectedLabel(QuizText.CREATE_RANDOM, create.randomSelected),
        onClick = { onSelectSet(null) },
        secondary = true,
    )

    create.sets.forEach { set ->
        AppButton(
            text = QuizText.selectedLabel(set.label, set.selected),
            onClick = { onSelectSet(set.id) },
            secondary = true,
        )
    }

    // Le contrôle de longueur n'apparaît que pour des questions aléatoires : l'original écrit
    // `{!quizSet&&<SegmentedControl/>}`, et une liste thématique contient exactement dix
    // questions — en demander cinq n'aurait pas de sens. `randomSelected` porte cette condition.
    if (create.randomSelected) {
        AppSegmentedControl(
            options = create.counts,
            value = QuizText.countLabel(create.count),
            // Le barème libellé → longueur vit dans `QuizText`, lu ici et par le `ViewModel` :
            // deux copies finiraient par diverger, et l'écran annoncerait une longueur que le
            // défi n'a pas.
            onSelect = { label -> QuizText.countOf(label)?.let(onSelectCount) },
        )
    }

    AppLabel(
        text = create.rule,
        selectable = false,
        fontSize = 12.sp,
        color = colors.muted,
        modifier = Modifier.padding(vertical = 10.dp),
    )

    AppButton(text = create.launchLabel, onClick = onCreate, enabled = create.canLaunch)
}

/**
 * Un ami à défier.
 *
 * Écrit à la main plutôt que posé avec `AppChoice` : celui-ci n'a pas de place pour un médaillon,
 * et l'original affiche bien l'avatar de l'ami devant son nom. Le procédé est celui d'`AppChoice`
 * — ligne bordée, coin doux, état `selected` —, et la coche n'apparaît que sur l'ami retenu.
 */
@Composable
private fun FriendRow(friend: FriendLine, onSelect: () -> Unit) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(AppTheme.radius.small)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = AppTheme.spacing.sm)
            .clip(shape)
            .background(if (friend.selected) colors.selected else colors.paper)
            .border(1.dp, if (friend.selected) colors.green else colors.line, shape)
            .selectable(
                selected = friend.selected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlayerMedallion(name = friend.name)

        AppLabel(
            text = friend.name,
            selectable = false,
            modifier = Modifier.weight(1f),
        )

        if (friend.selected) {
            AppInlineIcon(icon = Icons.Outlined.CheckCircle, tint = colors.green, size = 20.dp)
        }
    }
}

// ---------------------------------------------------------------------------
// Le défi ouvert
// ---------------------------------------------------------------------------

@Composable
private fun ChallengeView(
    challenge: Challenge?,
    onAnswer: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = AppTheme.colors

    if (challenge == null) {
        AppCard {
            AppLabel(text = QuizText.CHALLENGE_MISSING, selectable = false)
            AppButton(
                text = QuizText.REFRESH,
                onClick = onRefresh,
                secondary = true,
                small = true,
            )
        }
        return
    }

    AppHeading(text = challenge.heading, size = 20.sp)

    // Les quatre états d'un défi s'excluent, donc ils se lisent d'un `when` sur une interface
    // scellée — et non sur quatre propriétés nulles dont il faudrait deviner laquelle prime.
    when (val play = challenge.play) {
        is ChallengePlay.Finished -> {
            AppCard(modifier = Modifier.padding(top = AppTheme.spacing.sm)) {
                AppHeading(text = QuizText.CHALLENGE_FINISHED, size = 22.sp)

                play.scores.forEach { score ->
                    AppLabel(
                        text = score,
                        selectable = false,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = AppTheme.spacing.sm),
                    )
                }

                AppHeading(
                    text = play.verdict,
                    size = 18.sp,
                    modifier = Modifier.padding(top = AppTheme.spacing.sm),
                )
            }

            // Les questions sont toutes corrigées : le défi est clos, donc `correctAnswerId` est
            // arrivé du serveur et la correction n'est plus un secret.
            play.questions.forEach { answered ->
                AppHeading(
                    text = answered.question,
                    size = 19.sp,
                    modifier = Modifier.padding(top = AppTheme.spacing.md),
                )
                Answers(answers = answered.answers, selectable = false)
                CorrectionCard(correction = answered.correction)
            }
        }

        ChallengePlay.Expired -> AppCard {
            AppHeading(text = QuizText.CHALLENGE_EXPIRED_TITLE, size = 20.sp)
            AppLabel(
                text = QuizText.CHALLENGE_NO_WINNER,
                selectable = false,
                modifier = Modifier.padding(top = AppTheme.spacing.sm),
            )
        }

        is ChallengePlay.Next -> {
            AppLabel(
                text = play.progress,
                selectable = false,
                fontSize = 12.sp,
                color = colors.muted,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            AppLabel(text = play.category, selectable = false, fontSize = 11.sp, color = colors.gold)
            AppHeading(text = play.question, size = 20.sp)

            // `selectable` est faux pendant qu'une réponse est en vol : c'est le `disabled={busy}`
            // de l'original, et c'est la garde de double geste.
            Answers(answers = play.answers, selectable = play.selectable, onSelect = onAnswer)

            AppLabel(
                text = play.reveal,
                selectable = false,
                fontSize = 12.sp,
                color = colors.muted,
                modifier = Modifier.padding(top = AppTheme.spacing.md),
            )
        }

        is ChallengePlay.Waiting -> AppCard(modifier = Modifier.padding(top = AppTheme.spacing.sm)) {
            AppHeading(text = play.title, size = 19.sp)
            AppLabel(
                text = play.waitingFor,
                selectable = false,
                modifier = Modifier.padding(top = AppTheme.spacing.sm),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Panne du Quiz
// ---------------------------------------------------------------------------

@Composable
private fun QuizFailure(message: String, modifier: Modifier = Modifier) {
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
