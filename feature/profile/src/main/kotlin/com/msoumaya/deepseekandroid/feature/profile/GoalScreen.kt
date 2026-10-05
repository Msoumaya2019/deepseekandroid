package com.msoumaya.deepseekandroid.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.msoumaya.deepseekandroid.core.data.LocalAppContainer
import com.msoumaya.deepseekandroid.core.design.component.AppButton
import com.msoumaya.deepseekandroid.core.design.component.AppCard
import com.msoumaya.deepseekandroid.core.design.component.AppField
import com.msoumaya.deepseekandroid.core.design.component.AppHeading
import com.msoumaya.deepseekandroid.core.design.component.AppHero
import com.msoumaya.deepseekandroid.core.design.component.AppIconButton
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.component.AppSegmentedControl
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.GoalText

// ---------------------------------------------------------------------------
// Écran d'objectif
// ---------------------------------------------------------------------------
// Portage de `GoalScreen` (`src/ui/GoalScreen.tsx`).
//
// L'écran ne calcule rien : il dispose ce que `GoalRenderer` a décidé. Les quatre cartes sont
// dans l'ordre du client d'origine — ce qu'on sait, ce qu'on vise, à quel rythme, et ce que cela
// produira.
//
// **Le sélecteur d'options est un dépliant en ligne, et c'est celui de l'original.** On pourrait
// croire que `SelectField` ouvre une feuille modale ; il n'en fait rien. Il tient un `open` local,
// dessine une ligne bordée qui porte la valeur choisie et un chevron, puis déplie **sous elle** une
// liste bordée de 180 px de haut au plus, où l'option courante porte une coche. La feuille modale
// du fichier voisin est celle de la validation de séance, pas celle-ci. La géométrie est reprise
// telle quelle : ligne de 46 px, rayon 14, remplissage 12, liste de 180 px, ligne d'option de
// 44 px, coche de 18.
//
// **Une seule liste est ouverte à la fois.** Le client d'origine laisse chaque champ tenir son
// propre `open`, donc deux listes peuvent rester dépliées ensemble — et la carte des connaissances
// en porte deux, dont l'une propose 114 sourates. Ici, ouvrir un champ referme le précédent. La
// fermeture au choix est déjà dans l'original (`setOpen(false)`) : c'est son intention, et la
// carte n'a pas de sens avec trois listes ouvertes.
//
// **« Options avancées · passages et jours » n'est pas porté.** Le client d'origine ouvre par ce
// bouton la gestion de passages séparés et des jours d'apprentissage ; ce geste n'existe pas ici,
// et l'entrée est donc **retirée** au lieu d'être grisée — la règle de ce dépôt, déjà appliquée à
// « Sélectionner un passage » dans le panneau des actions d'un verset. Le mot n'est pas conservé
// dans `GoalText` : une chaîne que rien n'affiche est du code que rien ne joue.
//
// **Trois mesures relevées à la source, qui ne se devinent pas :**
//
//   1. La bande d'en-tête **déborde** de la marge des autres blocs : elle est posée **hors** de la
//      colonne en retrait de 18 px, comme sur l'accueil et le programme.
//   2. Le libellé d'un champ de choix est **au-dessus** de lui, en corps 13, et non dedans.
//   3. Le rythme s'annonce en corps **23**, plus gros que le titre de sa carte — c'est la valeur
//      qu'on vient lire.
// ---------------------------------------------------------------------------

/** Marge horizontale des blocs, valeur du client d'origine. */
private val SCREEN_PADDING = 18.dp

/** Marge basse de la colonne, valeur du client d'origine. */
private val BOTTOM_PADDING = 24.dp

/** Hauteur maximale d'une liste dépliée, valeur du client d'origine. */
private val CHOICE_MAX_HEIGHT = 180.dp

/** Hauteur d'une ligne d'option, valeur du client d'origine. */
private val CHOICE_MIN_HEIGHT = 44.dp

/** Hauteur de la ligne qui porte la valeur choisie, valeur du client d'origine. */
private val FIELD_MIN_HEIGHT = 46.dp

/** Remplissage des lignes, valeur du client d'origine. */
private val FIELD_PADDING = 12.dp

/** Rayon des lignes et de la liste, valeur du client d'origine. */
private val FIELD_RADIUS = 14.dp

/** Corps du libellé d'un champ, en dur dans le composant d'origine. */
private val FIELD_LABEL_SIZE = 13.sp

/** Corps du rythme, en dur dans le composant d'origine. */
private val PACE_SIZE = 23.sp

/** Corps de l'aperçu du programme, en dur dans le composant d'origine. */
private val PREVIEW_SIZE = 18.sp

/** Écart entre le libellé d'un champ et la ligne qui le suit. */
private val LABEL_GAP = 6.dp

/** Taille du chevron d'un champ. */
private val CHEVRON_SIZE = 20.dp

/** Taille de la coche d'une option choisie. */
private val CHECK_SIZE = 18.dp

// Les clés des quatre champs dépliables. Des chaînes, et non un index : une carte réordonnée ne
// doit pas rouvrir le mauvais champ.
private const val FIELD_SURAH = "sourate"
private const val FIELD_AYAH = "verset"
private const val FIELD_KNOWN_DIVISION = "division-connue"
private const val FIELD_GOAL = "objectif"

/**
 * L'écran d'objectif : ce qu'on sait déjà, ce qu'on vise, à quel rythme, et l'aperçu du programme.
 *
 * @param onClose fermeture de l'écran, appelée **après** l'enregistrement, et seulement s'il a eu
 *   lieu. Un refus de date laisse l'écran ouvert avec son message.
 * @param viewModel état de l'écran. Par défaut, celui du conteneur applicatif.
 */
@Composable
fun GoalScreen(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    viewModel: GoalViewModel = viewModel(
        factory = GoalViewModel.factory(LocalAppContainer.current),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    GoalContent(
        state = state,
        modifier = modifier,
        onKnownUnitSelected = viewModel::onKnownUnitSelected,
        onSurahSelected = viewModel::onSurahSelected,
        onAyahSelected = viewModel::onAyahSelected,
        onKnownDivisionSelected = viewModel::onKnownDivisionSelected,
        onGoalUnitSelected = viewModel::onGoalUnitSelected,
        onGoalSelected = viewModel::onGoalSelected,
        onDeadlineMode = viewModel::onDeadlineMode,
        onDeadlineChange = viewModel::onDeadlineChange,
        onPaceUnitSelected = viewModel::onPaceUnitSelected,
        onPaceShift = viewModel::onPaceShift,
        onSave = { viewModel.onSave(onClose) },
    )
}

/** Le contenu de l'écran, sans `ViewModel` : c'est ce qui le rend lisible et éprouvable. */
@Composable
internal fun GoalContent(
    state: GoalUiState,
    modifier: Modifier = Modifier,
    onKnownUnitSelected: (String) -> Unit = {},
    onSurahSelected: (Int) -> Unit = {},
    onAyahSelected: (Int) -> Unit = {},
    onKnownDivisionSelected: (Int) -> Unit = {},
    onGoalUnitSelected: (String) -> Unit = {},
    onGoalSelected: (Int) -> Unit = {},
    onDeadlineMode: (Boolean) -> Unit = {},
    onDeadlineChange: (String) -> Unit = {},
    onPaceUnitSelected: (String) -> Unit = {},
    onPaceShift: (Int) -> Unit = {},
    onSave: () -> Unit = {},
) {
    // Le champ déplié. Il vit dans l'écran et non dans l'état publié : c'est une présentation, pas
    // une saisie — l'oublier au rechargement ne perd rien. `rememberSaveable` le garde pourtant à
    // travers une rotation, parce que refermer un champ de 114 lignes sous les doigts serait une
    // surprise.
    var openField by rememberSaveable { mutableStateOf<String?>(null) }

    when {
        state.failure != null -> GoalFailure(message = state.failure, modifier = modifier)

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
            val fields = state.fields
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(AppTheme.colors.cream)
                    .verticalScroll(rememberScrollState()),
            ) {
                AppHero(title = GoalText.TITLE, subtitle = GoalText.SUBTITLE)

                Column(
                    modifier = Modifier.padding(
                        horizontal = SCREEN_PADDING,
                        vertical = BOTTOM_PADDING,
                    ),
                ) {
                    KnownCard(
                        state = state,
                        openField = openField,
                        onOpenField = { openField = it },
                        onKnownUnitSelected = onKnownUnitSelected,
                        onSurahSelected = onSurahSelected,
                        onAyahSelected = onAyahSelected,
                        onKnownDivisionSelected = onKnownDivisionSelected,
                    )

                    GoalCard(
                        state = state,
                        openField = openField,
                        onOpenField = { openField = it },
                        onGoalUnitSelected = onGoalUnitSelected,
                        onGoalSelected = onGoalSelected,
                        onDeadlineMode = onDeadlineMode,
                        onDeadlineChange = onDeadlineChange,
                    )

                    PaceCard(
                        state = state,
                        onPaceUnitSelected = onPaceUnitSelected,
                        onPaceShift = onPaceShift,
                    )

                    PreviewCard(state = state, onSave = onSave)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Carte « Je connais déjà »
// ---------------------------------------------------------------------------

@Composable
private fun KnownCard(
    state: GoalUiState,
    openField: String?,
    onOpenField: (String?) -> Unit,
    onKnownUnitSelected: (String) -> Unit,
    onSurahSelected: (Int) -> Unit,
    onAyahSelected: (Int) -> Unit,
    onKnownDivisionSelected: (Int) -> Unit,
) {
    val colors = AppTheme.colors
    val fields = state.fields

    AppCard {
        AppHeading(text = GoalText.KNOWN_TITLE)

        if (state.nothingKnown) {
            AppLabel(
                text = GoalText.NOTHING_KNOWN,
                modifier = Modifier.padding(vertical = 8.dp),
                fontSize = AppTheme.typeScale.secondary,
                color = colors.muted,
                selectable = false,
            )
        }

        AppSegmentedControl(
            options = GoalText.units,
            value = fields.knownUnit.label,
            onSelect = onKnownUnitSelected,
        )

        if (fields.knownUnit == GoalUnit.SURAH) {
            ChoiceField(
                label = GoalText.LAST_SURAH,
                value = labelOf(state.knownChoices, fields.surah),
                choices = state.knownChoices,
                choisi = fields.surah,
                expanded = openField == FIELD_SURAH,
                onToggle = { onOpenField(if (openField == FIELD_SURAH) null else FIELD_SURAH) },
                onPick = onSurahSelected,
            )
            ChoiceField(
                label = GoalText.LAST_VERSE,
                value = fields.ayah.toString(),
                choices = state.verseChoices,
                choisi = fields.ayah,
                expanded = openField == FIELD_AYAH,
                onToggle = { onOpenField(if (openField == FIELD_AYAH) null else FIELD_AYAH) },
                onPick = onAyahSelected,
            )
        } else {
            ChoiceField(
                label = GoalText.lastDivision(fields.knownUnit.label),
                value = labelOf(state.knownChoices, fields.knownDivision),
                choices = state.knownChoices,
                choisi = fields.knownDivision,
                expanded = openField == FIELD_KNOWN_DIVISION,
                onToggle = {
                    onOpenField(
                        if (openField == FIELD_KNOWN_DIVISION) null else FIELD_KNOWN_DIVISION,
                    )
                },
                onPick = onKnownDivisionSelected,
            )
        }

        AppLabel(
            text = GoalText.KNOWN_NOTE,
            modifier = Modifier.padding(top = 8.dp),
            fontSize = AppTheme.typeScale.metadata,
            color = colors.muted,
            selectable = false,
        )
    }
}

// ---------------------------------------------------------------------------
// Carte « Mon objectif »
// ---------------------------------------------------------------------------

@Composable
private fun GoalCard(
    state: GoalUiState,
    openField: String?,
    onOpenField: (String?) -> Unit,
    onGoalUnitSelected: (String) -> Unit,
    onGoalSelected: (Int) -> Unit,
    onDeadlineMode: (Boolean) -> Unit,
    onDeadlineChange: (String) -> Unit,
) {
    val colors = AppTheme.colors
    val fields = state.fields

    AppCard {
        AppHeading(text = GoalText.GOAL_TITLE)

        AppSegmentedControl(
            options = GoalText.units,
            value = fields.goalUnit.label,
            onSelect = onGoalUnitSelected,
        )

        ChoiceField(
            label = GoalText.GOAL_FIELD,
            value = labelOf(state.goalChoices, fields.goalIndex),
            choices = state.goalChoices,
            choisi = fields.goalIndex,
            expanded = openField == FIELD_GOAL,
            onToggle = { onOpenField(if (openField == FIELD_GOAL) null else FIELD_GOAL) },
            onPick = onGoalSelected,
        )

        // Le rappel décrit l'objectif **enregistré** : il se tait dès qu'on en choisit un autre,
        // sans quoi il annoncerait comme actuel celui qu'on est en train de quitter.
        if (!fields.goalEdited) {
            AppLabel(
                text = GoalText.currentGoal(state.savedGoalLabel),
                modifier = Modifier.padding(top = 8.dp),
                fontSize = AppTheme.typeScale.secondary,
                color = colors.muted,
                selectable = false,
            )
        }

        Column(modifier = Modifier.padding(top = 10.dp)) {
            AppLabel(
                text = GoalText.DEADLINE,
                fontSize = AppTheme.typeScale.secondary,
                color = colors.muted,
                selectable = false,
            )
            AppSegmentedControl(
                options = listOf(GoalText.NO_DEADLINE, GoalText.PICK_DATE),
                value = if (fields.deadlineOn) GoalText.PICK_DATE else GoalText.NO_DEADLINE,
                onSelect = { onDeadlineMode(it == GoalText.PICK_DATE) },
            )
            if (fields.deadlineOn) {
                AppField(
                    value = fields.deadline,
                    onValueChange = onDeadlineChange,
                    placeholder = GoalText.DATE_PLACEHOLDER,
                )
            }
            AppLabel(
                text = GoalText.DEADLINE_NOTE,
                fontSize = AppTheme.typeScale.metadata,
                color = colors.muted,
                selectable = false,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Carte « Mon rythme »
// ---------------------------------------------------------------------------

@Composable
private fun PaceCard(
    state: GoalUiState,
    onPaceUnitSelected: (String) -> Unit,
    onPaceShift: (Int) -> Unit,
) {
    AppCard {
        AppHeading(text = GoalText.PACE_TITLE)

        AppSegmentedControl(
            options = GoalText.paceUnits,
            value = state.fields.paceUnit.label,
            onSelect = onPaceUnitSelected,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AppIconButton(
                icon = Icons.Outlined.Remove,
                label = GoalText.PACE_DOWN,
                onClick = { onPaceShift(-1) },
            )
            AppHeading(text = state.paceLabel, size = PACE_SIZE)
            AppIconButton(
                icon = Icons.Outlined.Add,
                label = GoalText.PACE_UP,
                onClick = { onPaceShift(1) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Carte « Programme généré »
// ---------------------------------------------------------------------------

@Composable
private fun PreviewCard(state: GoalUiState, onSave: () -> Unit) {
    val colors = AppTheme.colors

    AppCard {
        AppHeading(text = GoalText.PREVIEW_TITLE)
        AppLabel(
            text = GoalText.PREVIEW_NOTE,
            modifier = Modifier.padding(vertical = 10.dp),
            fontSize = AppTheme.typeScale.secondary,
            color = colors.muted,
            selectable = false,
        )
        AppHeading(text = state.preview, size = PREVIEW_SIZE)
        AppButton(text = GoalText.SAVE, onClick = onSave)

        state.fields.error?.let { message ->
            AppLabel(
                text = message,
                color = colors.red,
                fontSize = AppTheme.typeScale.secondary,
                selectable = false,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Le sélecteur d'options
// ---------------------------------------------------------------------------

/**
 * Un champ de choix : la valeur retenue, et sous elle la liste dépliée.
 *
 * La géométrie est celle de `SelectField` du client d'origine — voir la note de tête de l'écran.
 *
 * @param label le libellé, **au-dessus** de la ligne.
 * @param value ce qui s'affiche dans la ligne. Vide si la valeur ne correspond à aucune option,
 *   comme le `options.find(…)?.label` de l'original.
 * @param choices les options.
 * @param choisi la valeur retenue, qui porte la coche.
 * @param expanded `true` si la liste est dépliée.
 * @param onToggle ouverture ou fermeture du champ.
 * @param onPick choix d'une option. C'est l'appelant qui referme : le champ ne referme que s'il
 *   est seul à savoir ce qu'ouvrir veut dire.
 */
@Composable
private fun ChoiceField(
    label: String,
    value: String,
    choices: List<GoalChoice>,
    choisi: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(FIELD_RADIUS)

    Column(modifier = Modifier.padding(top = 10.dp)) {
        AppLabel(
            text = label,
            modifier = Modifier.padding(bottom = LABEL_GAP),
            fontSize = FIELD_LABEL_SIZE,
            color = colors.text,
            selectable = false,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FIELD_MIN_HEIGHT)
                .clip(shape)
                .background(colors.soft)
                .border(1.dp, colors.line, shape)
                .clickable(
                    role = Role.Button,
                    onClickLabel = label,
                    onClick = onToggle,
                )
                .padding(FIELD_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppLabel(
                text = value,
                modifier = Modifier.weight(1f),
                color = colors.text,
                selectable = false,
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = colors.green,
                modifier = Modifier.size(CHEVRON_SIZE),
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = CHOICE_MAX_HEIGHT)
                    .clip(shape)
                    .background(colors.paper)
                    .border(1.dp, colors.line, shape)
                    .verticalScroll(rememberScrollState()),
            ) {
                for (option in choices) {
                    val estChoisi = option.number == choisi
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = CHOICE_MIN_HEIGHT)
                            .clickable(role = Role.Button, onClick = { onPick(option.number) })
                            .semantics { selected = estChoisi }
                            .padding(FIELD_PADDING),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppLabel(
                            text = option.label,
                            modifier = Modifier.weight(1f),
                            color = colors.text,
                            selectable = false,
                        )
                        if (estChoisi) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = colors.green,
                                modifier = Modifier.size(CHECK_SIZE),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// L'échec
// ---------------------------------------------------------------------------

/**
 * Le référentiel coranique n'a pas pu être lu.
 *
 * Aucune liste n'est calculable sans lui : les sourates, les hizbs et les juz’ viennent tous de
 * là. Un écran qui afficherait ses cartes avec des listes vides laisserait croire à un objectif
 * sans divisions.
 */
@Composable
private fun GoalFailure(message: String, modifier: Modifier = Modifier) {
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
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Le libellé de l'option portant [number], ou une chaîne vide s'il n'y en a pas. */
private fun labelOf(choices: List<GoalChoice>, number: Int): String =
    choices.firstOrNull { it.number == number }?.label ?: ""
