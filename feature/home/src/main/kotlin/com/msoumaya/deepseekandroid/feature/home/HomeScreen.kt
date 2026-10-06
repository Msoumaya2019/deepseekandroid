package com.msoumaya.deepseekandroid.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import com.msoumaya.deepseekandroid.core.design.component.AppInlineIcon
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.component.AppSectionHeader
import com.msoumaya.deepseekandroid.core.design.component.ArabicText
import com.msoumaya.deepseekandroid.core.design.component.ProgressTrack
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.design.theme.ReadingArt
import com.msoumaya.deepseekandroid.core.design.theme.ThemeArtHeroOpacity
import com.msoumaya.deepseekandroid.core.design.theme.themeArt
import com.msoumaya.deepseekandroid.core.domain.StudySession

// ---------------------------------------------------------------------------
// Écran d'accueil
// ---------------------------------------------------------------------------
// Portage de `Home` (`src/ui/MainScreens.tsx`).
//
// Trois mesures relevées à la source, qui ne se devinent pas :
//
//   1. La bande d'en-tête **déborde** de la marge des autres blocs : la liste est en retrait de
//      18 px et l'image remonte de −18 px pour occuper toute la largeur. En Compose, cela
//      s'exprime en sortant la bande de la colonne en retrait, ce qui évite une marge négative
//      — plus court à lire, et le résultat est le même.
//
//   2. Le fond de thème est posé à **0,55** d'opacité, pas 0,68 : les deux valeurs existent
//      dans le dépôt d'origine, la seconde servant la bande de l'écran Coran.
//
//   3. Les barres du bandeau hebdomadaire et les pastilles de régularité ne couvrent **pas** la
//      même période. Les barres vont du lundi au dimanche de la semaine en cours ; les pastilles
//      vont des six jours précédents à aujourd'hui. Les inverser afficherait des barres justes
//      mais un chapelet de pastilles faux.
// ---------------------------------------------------------------------------

/**
 * L'accueil.
 *
 * @param onOpenReader ouverture du lecteur sur un verset.
 * @param onOpenProgram bascule vers l'onglet Programme.
 * @param onOpenProgress bascule vers l'onglet Progrès.
 * @param onOpenReviews ouverture du tableau de bord des révisions.
 * @param onOpenQuiz ouverture de l'écran Quiz. **La porte principale du Quiz** : c'est par la
 *   carte de l'accueil qu'on y arrive sans avoir d'ami à défier.
 * @param onOpenFriends bascule vers l'onglet « Amis ». Distinct de [onOpenQuiz] : l'une ouvre le
 *   Quiz, l'autre va chercher quelqu'un à défier — et c'est l'original qui les sépare, en donnant
 *   à chaque carte son propre geste.
 * @param viewModel état de l'écran. Par défaut, celui du conteneur applicatif.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenReader: (Int) -> Unit = {},
    // Ouvre le lecteur **sur une séance**. Distinct de `onOpenReader`, et non un cas
    // particulier de celui-ci : une lecture libre n'a pas de progression à valider, une séance
    // en a une, et les confondre ferait perdre la seconde sans que rien ne le dise.
    onOpenStudy: (StudySession.Request) -> Unit = {},
    onOpenProgram: () -> Unit = {},
    onOpenProgress: () -> Unit = {},
    onOpenReviews: () -> Unit = {},
    onOpenQuiz: () -> Unit = {},
    onOpenFriends: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(LocalAppContainer.current)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    HomeContent(
        state = state,
        modifier = modifier,
        onOpenReader = onOpenReader,
        onOpenStudy = onOpenStudy,
        onOpenProgram = onOpenProgram,
        onOpenProgress = onOpenProgress,
        onOpenReviews = onOpenReviews,
        onOpenQuiz = onOpenQuiz,
        onOpenFriends = onOpenFriends,
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    modifier: Modifier = Modifier,
    onOpenReader: (Int) -> Unit = {},
    onOpenStudy: (StudySession.Request) -> Unit = {},
    onOpenProgram: () -> Unit = {},
    onOpenProgress: () -> Unit = {},
    onOpenReviews: () -> Unit = {},
    onOpenQuiz: () -> Unit = {},
    onOpenFriends: () -> Unit = {},
) {
    when {
        state.failure != null -> HomeFailure(message = state.failure, modifier = modifier)

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

        else -> Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            HomeHero(firstName = state.firstName)

            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                state.resume?.let { resume ->
                    ResumeCard(resume = resume, onContinue = { onOpenReader(resume.verseId) })
                }

                AppSectionHeader(
                    title = "Aujourd'hui",
                    action = "Voir tout",
                    onAction = onOpenProgram,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppDailyTaskCard(
                        title = "Apprentissage",
                        passage = state.learning.passage,
                        details = state.learning.details,
                        // Une séance d'apprentissage s'ouvre **comme séance** : le lecteur doit
                        // savoir ce qu'il sert, sinon le bandeau ne s'affiche pas et rien ne peut
                        // être validé. Le verset reste le repli d'une carte qui n'aurait pas de
                        // séance — et le programme, celui d'une carte qui n'aurait rien du tout.
                        onPress = {
                            val seance = state.learning.study
                            val target = state.learning.verseId
                            when {
                                seance != null -> onOpenStudy(seance)
                                target != null -> onOpenReader(target)
                                else -> onOpenProgram()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    AppDailyTaskCard(
                        title = "Révision",
                        passage = state.revision.passage,
                        details = state.revision.details,
                        onPress = {
                            val target = state.revision.verseId
                            if (target != null) onOpenReader(target) else onOpenReviews()
                        },
                        modifier = Modifier.weight(1f),
                        revision = true,
                    )
                }

                // Les deux cartes de quiz, **entre** « Aujourd'hui » et « Ma semaine » — c'est
                // leur place dans l'original. Les remonter en tête reléguerait la reprise de
                // lecture au second plan, alors que le Coran reste la raison d'être de l'écran.
                //
                // Elles ne sont composées que si l'état les porte : `null` veut dire « instantané
                // pas encore lu », et l'accueil n'affiche de toute façon rien avant.
                state.quiz?.let { cards ->
                    QuizHomeCards(
                        cards = cards,
                        onQuiz = onOpenQuiz,
                        onFriends = onOpenFriends,
                    )
                }

                AppSectionHeader(
                    title = "Ma semaine",
                    action = "Voir mes progrès",
                    onAction = onOpenProgress,
                )
                WeekCard(week = state.week)
            }
        }
    }
}

/**
 * Bande d'en-tête : illustration du thème, salutation, prénom.
 *
 * Le titre « As-Salâm 'Alaykoum, » et le prénom n'ont pas la même couleur dans le dépôt
 * d'origine : le premier est du texte courant, le second est un `Heading`, dont la couleur par
 * défaut est le vert du thème. Ce n'est pas un détail de style, c'est ce qui fait ressortir le
 * prénom.
 */
@Composable
private fun HomeHero(firstName: String?, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp)
            .background(colors.cream),
    ) {
        Image(
            painter = painterResource(themeArt(AppTheme.themeName)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = ThemeArtHeroOpacity,
            modifier = Modifier.matchParentSize(),
        )
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            AppLabel(text = "As-Salâm ‘Alaykoum,", selectable = false, fontSize = 16.sp)
            AppHeading(text = firstName ?: "Bienvenue", size = 32.sp)
            AppLabel(
                text = "Prêt à continuer ton apprentissage ?",
                selectable = false,
                fontSize = 12.sp,
                color = colors.muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Carte « Continuer ma lecture » : illustration, sourate, position, avancement, bouton. */
@Composable
private fun ResumeCard(
    resume: Resume,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors

    AppCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppInlineIcon(
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                tint = colors.green,
                size = 20.dp,
            )
            AppHeading(text = "Continuer ma lecture", size = 19.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Image(
                painter = painterResource(ReadingArt),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .weight(0.46f)
                    .height(132.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
            Column(
                modifier = Modifier.weight(0.54f),
                verticalArrangement = Arrangement.Center,
            ) {
                AppLabel(text = "Sourate", selectable = false, fontSize = 11.sp, color = colors.muted)
                AppHeading(text = resume.surahName, size = 20.sp)
                ArabicText(text = resume.surahArabic, color = colors.gold)
                AppLabel(
                    text = "Verset ${resume.ayah} • Page ${resume.page}",
                    selectable = false,
                    fontSize = 11.sp,
                    color = colors.muted,
                    modifier = Modifier.padding(vertical = 7.dp),
                )
                ProgressTrack(value = resume.ratio)
                AppButton(text = "Continuer ›", onClick = onContinue, small = true)
            }
        }
    }
}

/** Bandeau « Ma semaine » : versets appris, régularité, objectif. */
@Composable
private fun WeekCard(week: WeekSummary, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors

    AppCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AppLabel(
                    text = "Versets appris cette semaine",
                    selectable = false,
                    fontSize = 12.sp,
                    color = colors.muted,
                )
                AppHeading(text = week.verses.toString(), size = 28.sp)
                WeeklyBars(values = week.daily)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .startDivider(colors.line)
                    .padding(start = 12.dp),
            ) {
                AppLabel(
                    text = "Régularité",
                    selectable = false,
                    fontSize = 12.sp,
                    color = colors.muted,
                )
                AppLabel(
                    text = "${week.streak} jour${if (week.streak > 1) "s" else ""} d'affilée",
                    selectable = false,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 5.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    week.activeDays.forEach { active ->
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (active) colors.green else colors.surfaceSecondary)
                                .then(Modifier.circleOutline(colors.line)),
                        )
                    }
                }
            }
        }

        AppLabel(
            text = "Objectif de la semaine · ${week.goalPercent} %",
            selectable = false,
            fontSize = 11.sp,
            color = colors.muted,
            modifier = Modifier.padding(top = 9.dp, bottom = 4.dp),
        )
        ProgressTrack(value = week.goalRatio)
    }
}

/**
 * Sept barres, du lundi au dimanche de la semaine en cours.
 *
 * La hauteur est proportionnelle au maximum de la semaine et non à l'objectif : une semaine
 * faible doit rester lisible, et une barre nulle garde 3 px pour que le jour existe visuellement
 * au lieu de disparaître.
 */
@Composable
private fun WeeklyBars(values: List<Int>, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val peak = maxOf(1, values.maxOrNull() ?: 1)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(top = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { index, value ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(((value.toFloat() / peak) * 23f).coerceAtLeast(3f).dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (value > 0) colors.green else colors.line),
                )
                AppLabel(
                    text = WEEKDAY_INITIALS[index],
                    selectable = false,
                    fontSize = 8.sp,
                    color = colors.muted,
                )
            }
        }
    }
}

/** Écran d'échec : le référentiel coranique n'a pas pu être chargé. */
@Composable
private fun HomeFailure(message: String, modifier: Modifier = Modifier) {
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

/** Trait vertical de 1 px sur le bord gauche, à l'intérieur du composant. */
private fun Modifier.startDivider(color: Color, thickness: Dp = 1.dp): Modifier =
    drawBehind {
        drawRect(
            color = color,
            topLeft = Offset.Zero,
            size = Size(thickness.toPx(), size.height),
        )
    }

/** Contour de 1 px, sans remplissage. */
private fun Modifier.circleOutline(color: Color): Modifier = drawBehind {
    val trait = 1.dp.toPx()
    drawCircle(
        color = color,
        radius = size.minDimension / 2f - trait / 2f,
        style = Stroke(width = trait),
    )
}

/** Initiales des jours, du lundi au dimanche, dans l'ordre du dépôt d'origine. */
private val WEEKDAY_INITIALS = listOf("L", "M", "M", "J", "V", "S", "D")
