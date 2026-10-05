package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import com.msoumaya.deepseekandroid.core.design.component.AppButton
import com.msoumaya.deepseekandroid.core.design.component.AppCard
import com.msoumaya.deepseekandroid.core.design.component.AppChoice
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.StudyProgressCalculator
import com.msoumaya.deepseekandroid.core.domain.StudySession
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.ReviewGrade

/**
 * La feuille de validation d'une séance : jusqu'où l'on a réellement appris, ou révisé.
 *
 * ## Pourquoi une feuille, et non un simple bouton « Terminer »
 *
 * Parce qu'une séance ne se termine pas toujours. On peut avoir appris trois versets sur cinq et
 * devoir s'arrêter. Un bouton unique obligerait soit à mentir — déclarer appris ce qui ne l'est
 * pas, et le programme ne reviendrait plus dessus — soit à tout reperdre. La feuille existe pour
 * que l'arrêt réel puisse être **dit**.
 *
 * ## Ce qu'elle calcule, et ce qu'elle ne calcule pas
 *
 * Tout ce qui **décide** vient du domaine : le premier verset encore à valider, la proposition
 * par défaut, le verset retenu, la phrase de résumé et le libellé du bouton sont dans
 * `StudySession.Completion`, et éprouvés là-bas. Cette feuille ne garde que ses **états de
 * rendu** — l'unité choisie, le « tout » coché, la note — et les repose.
 *
 * ## Une différence assumée avec le client d'origine
 *
 * L'original choisit la sourate, la page et le verset dans des listes **déroulantes** qui
 * recouvrent le contenu. Ici, la liste est **dans la feuille**, bornée en hauteur et défilante.
 * Le geste est le même — on choisit un point d'arrêt — mais rien ne se superpose : sur un
 * téléphone, un menu déroulant ouvert au-dessus d'une feuille ouverte finit par empiler deux
 * couches, et l'une des deux devient inatteignable.
 *
 * @param learning vrai pour un apprentissage, faux pour une révision. Décide des mots, et rien
 *   d'autre : le calcul est le même.
 * @param range la plage **prévue** — celle que le bandeau compte, et non celle qui a été
 *   demandée. Voir `StudySession.plannedRange`.
 * @param through le dernier verset déjà validé.
 * @param currentPage la page affichée dans le lecteur : c'est elle qui propose le point d'arrêt
 *   par défaut, puisque c'est ce que la personne vient de finir de lire.
 * @param onValidate reçoit le verset retenu et la note. Il n'est appelé qu'une fois, et la
 *   fermeture de la feuille appartient à l'appelant — c'est lui qui sait si l'écran doit rester
 *   ouvert après une validation partielle.
 */
@Composable
internal fun StudyCompletionSheet(
    learning: Boolean,
    range: Range,
    through: Int,
    source: String,
    currentPage: Int,
    onClose: () -> Unit,
    onValidate: (Int, ReviewGrade) -> Unit,
) {
    val colors = AppTheme.colors

    // Les propositions par défaut, recalculées quand la séance change — et non au premier
    // rendu seulement : une validation partielle rouvre la feuille avec un `through` plus
    // avancé, et la proposition doit suivre.
    val propose = StudySession.Completion.initialEndpoint(range, through, currentPage, source)
    var endpoint by remember(range, through) { mutableIntStateOf(propose) }
    var all by remember(range, through) { mutableStateOf(false) }
    var parPage by remember(range, through) { mutableStateOf(false) }
    var note by remember(range, through) { mutableStateOf(ReviewGrade.PERFECT) }

    val next = StudySession.Completion.nextStart(range, through)
    val allowed = Range(next, range.end)
    val selected = StudySession.Completion.selected(range, endpoint, all)

    val metrics = StudyProgressCalculator.studyMetrics(range, through, source)
    val completed = StudyProgressCalculator.studyMetrics(range, selected, source)
    val page = StudyProgressCalculator.studyPage(selected, source)
    val verset = Quran.verseAt(selected)
    val sourate = Quran.surahAt(selected)
    val juz = Quran.juzs.indexOfFirst { selected in it.start..it.end } + 1

    val resume = StudySession.Completion.summary(range, through, selected, source, learning)
    val libelle = StudySession.Completion.validateLabel(
        range = range,
        selected = selected,
        all = all,
        learning = learning,
        unitIsPage = parPage,
        page = page,
        ayah = verset.ayah,
    )

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    ),
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                    ),
                shape = RoundedCornerShape(
                    topStart = AppTheme.radius.sheet,
                    topEnd = AppTheme.radius.sheet,
                ),
                color = colors.paper,
                shadowElevation = SHEET_ELEVATION,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(SHEET_PADDING),
                ) {
                    SheetHandle(onDismiss = onClose)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppLabel(
                            text = if (learning) "Terminer mon apprentissage" else "Terminer ma révision",
                            modifier = Modifier.weight(1f),
                            color = colors.green,
                            fontSize = AppTheme.typeScale.section,
                            fontWeight = FontWeight.SemiBold,
                            selectable = false,
                        )
                        SheetCloseButton(
                            onClose = onClose,
                            description = "Fermer la validation",
                        )
                    }

                    AppCard(background = colors.soft) {
                        AppLabel(
                            text = if (learning) "Apprentissage prévu" else "Révision prévue",
                            fontWeight = FontWeight.ExtraBold,
                            selectable = false,
                        )
                        AppLabel(
                            text = "${metrics.label} · ${metrics.total} ${metrics.unit}",
                            modifier = Modifier.padding(top = 4.dp),
                            selectable = false,
                        )
                    }

                    // « J'ai tout appris » n'existe qu'en apprentissage : une révision se juge à
                    // sa note, et proposer « j'ai tout révisé » à côté de trois notes ferait
                    // deux réponses pour une même question.
                    if (learning) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppButton(
                                text = "J'ai tout appris",
                                onClick = { all = true },
                                modifier = Modifier.weight(1f),
                                secondary = !all,
                                small = true,
                            )
                            AppButton(
                                text = "J'ai appris jusqu'ici",
                                onClick = { all = false },
                                modifier = Modifier.weight(1f),
                                secondary = all,
                                small = true,
                            )
                        }
                    }

                    AppCard {
                        AppLabel(
                            text = if (learning) "J'ai appris jusqu'ici" else "J'ai révisé jusqu'ici",
                            fontSize = AppTheme.typeScale.card,
                            fontWeight = FontWeight.SemiBold,
                            selectable = false,
                        )
                        AppLabel(
                            text = "Choisissez le dernier verset que vous avez réellement " +
                                if (learning) "appris." else "révisé.",
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = colors.muted,
                            fontSize = AppTheme.typeScale.secondary,
                            selectable = false,
                        )

                        if (!all) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AppButton(
                                    text = "Page",
                                    onClick = {
                                        parPage = true
                                        endpoint = StudyProgressCalculator.studyEndpointForPage(
                                            page = page,
                                            range = allowed,
                                            source = source,
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    secondary = !parPage,
                                    small = true,
                                )
                                AppButton(
                                    text = "Verset",
                                    onClick = { parPage = false },
                                    modifier = Modifier.weight(1f),
                                    secondary = parPage,
                                    small = true,
                                )
                            }

                            if (parPage) {
                                Choix(
                                    label = if (learning) "Dernière page apprise" else "Dernière page révisée",
                                    options = metrics.pageList
                                        .filter {
                                            StudyProgressCalculator.studyEndpointForPage(it, allowed, source) >= next
                                        }
                                        .map { it to "Page $it" },
                                    value = page,
                                    onSelect = { choix ->
                                        endpoint = StudyProgressCalculator.studyEndpointForPage(
                                            page = choix,
                                            range = allowed,
                                            source = source,
                                        )
                                    },
                                )
                            } else {
                                Choix(
                                    label = "Sourate",
                                    options = StudyProgressCalculator.studySurahs(allowed)
                                        .map { it.number to "${it.name} (${it.number})" },
                                    value = sourate.number,
                                    onSelect = { choix ->
                                        StudyProgressCalculator.studyVerses(allowed, choix)
                                            .firstOrNull()
                                            ?.let { endpoint = it }
                                    },
                                )
                                Choix(
                                    label = if (learning) "Dernier verset appris" else "Dernier verset révisé",
                                    options = StudyProgressCalculator.studyVerses(allowed, sourate.number)
                                        .map { it to "Verset ${Quran.verseAt(it).ayah}" },
                                    value = endpoint,
                                    onSelect = { choix -> endpoint = choix },
                                )
                            }
                        }

                        Bloc(
                            title = "Passage actuel",
                            lines = listOf("Juz $juz · ${sourate.name} · verset ${verset.ayah}"),
                            modifier = Modifier.padding(top = 12.dp),
                        )

                        // Ce qui va être écrit, dit avant que ce le soit. C'est le dernier
                        // endroit où la personne peut le voir : après, la progression est
                        // enregistrée et le programme s'appuie dessus.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(AppTheme.radius.small))
                                .border(
                                    1.dp,
                                    colors.line,
                                    RoundedCornerShape(AppTheme.radius.small),
                                )
                                .padding(12.dp),
                        ) {
                            AppLabel(
                                text = resume,
                                color = colors.green,
                                fontWeight = FontWeight.ExtraBold,
                                selectable = false,
                            )
                            AppLabel(
                                text = "Arrêt exact : ${sourate.name} · verset ${verset.ayah}",
                                modifier = Modifier.padding(top = 5.dp),
                                color = colors.muted,
                                fontSize = AppTheme.typeScale.secondary,
                                selectable = false,
                            )
                            AppLabel(
                                text = "${completed.done} / ${metrics.total} ${metrics.unit}",
                                modifier = Modifier.padding(top = 5.dp),
                                fontSize = AppTheme.typeScale.secondary,
                                selectable = false,
                            )
                        }
                    }

                    if (!learning) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            for ((valeur, texte) in NOTES) {
                                AppButton(
                                    text = texte,
                                    onClick = { note = valeur },
                                    modifier = Modifier.weight(1f),
                                    secondary = note != valeur,
                                    small = true,
                                )
                            }
                        }
                    }

                    AppButton(
                        text = libelle,
                        onClick = { onValidate(selected, note) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                    AppButton(
                        text = "Annuler",
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth(),
                        secondary = true,
                    )
                }
            }
        }
    }
}

/**
 * Un choix parmi une liste bornée, défilante, **dans** la feuille.
 *
 * Voir la note de la feuille sur cette différence avec les listes déroulantes de l'original. La
 * hauteur est bornée par [CHOICE_MAX_HEIGHT] : sans borne, une révision d'un hizb ferait une
 * feuille de plusieurs milliers de pixels, et les boutons de validation partiraient hors de
 * l'écran — donc hors de portée.
 *
 * Une seule option n'ouvre pas de liste : le champ dit sa valeur, et la liste serait un geste
 * pour rien.
 */
@Composable
private fun Choix(
    label: String,
    options: List<Pair<Int, String>>,
    value: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = AppTheme.colors
    val courante = options.firstOrNull { it.first == value }?.second ?: value.toString()

    Column(modifier = Modifier.padding(top = 10.dp)) {
        AppLabel(
            text = label,
            modifier = Modifier.padding(bottom = 6.dp),
            fontSize = AppTheme.typeScale.secondary,
            selectable = false,
        )
        if (options.size <= 1) {
            AppLabel(
                text = courante,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppTheme.radius.small))
                    .background(colors.soft)
                    .padding(12.dp),
                selectable = false,
            )
            return@Column
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = CHOICE_MAX_HEIGHT)
                .clip(RoundedCornerShape(AppTheme.radius.small))
                .border(1.dp, colors.line, RoundedCornerShape(AppTheme.radius.small))
                .background(colors.paper)
                .verticalScroll(rememberScrollState())
                .padding(6.dp),
        ) {
            for ((valeur, texte) in options) {
                AppChoice(
                    label = texte,
                    selected = valeur == value,
                    onPress = { onSelect(valeur) },
                )
            }
        }
    }
}

/** Un bloc de texte en fond doux, avec un intertitre. */
@Composable
private fun Bloc(title: String, lines: List<String>, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.radius.small))
            .background(colors.soft)
            .padding(12.dp),
    ) {
        AppLabel(
            text = title,
            color = colors.green,
            fontSize = AppTheme.typeScale.secondary,
            selectable = false,
        )
        for (ligne in lines) {
            AppLabel(
                text = ligne,
                modifier = Modifier.padding(top = 4.dp),
                color = colors.muted,
                fontSize = AppTheme.typeScale.secondary,
                selectable = false,
            )
        }
    }
}

/** Les trois notes d'une révision, dans l'ordre du client d'origine. */
private val NOTES: List<Pair<ReviewGrade, String>> = listOf(
    ReviewGrade.PERFECT to "Parfait",
    ReviewGrade.HESITANT to "Quelques hésitations",
    ReviewGrade.REWORK to "À retravailler",
)

/** La hauteur maximale d'une liste de choix. L'original borne son menu à 180. */
private val CHOICE_MAX_HEIGHT = 180.dp
