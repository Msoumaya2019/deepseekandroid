package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.msoumaya.deepseekandroid.core.design.component.AppButton
import com.msoumaya.deepseekandroid.core.design.component.AppCheckChoice
import com.msoumaya.deepseekandroid.core.design.component.AppChoice
import com.msoumaya.deepseekandroid.core.design.component.AppField
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.Audio
import com.msoumaya.deepseekandroid.core.domain.AudioCount
import com.msoumaya.deepseekandroid.core.domain.AudioSession
import com.msoumaya.deepseekandroid.core.domain.AudioSettings
import com.msoumaya.deepseekandroid.core.domain.AudioSettingsText
import com.msoumaya.deepseekandroid.core.model.Reciter
import com.msoumaya.deepseekandroid.core.model.RepeatMode

/**
 * Les réglages d'écoute : récitateur, répétitions, mode, vitesse et silence.
 *
 * Portée depuis le bloc `advanced` de `src/PassageAudioPlayer.tsx`. C'est le seul endroit où
 * l'on choisit **comment** on écoute : combien de fois, à quelle vitesse, avec quel silence
 * entre deux reprises, et chez quel récitateur.
 *
 * ## Les réglages s'appliquent à la séance en cours
 *
 * Chaque appui remonte immédiatement les nouveaux réglages à l'appelant, qui les passe au
 * contrôleur. C'est le comportement du client d'origine, où `settingsRef` est reconstruit à
 * chaque rendu : changer le nombre d'écoutes pendant une séance vaut pour cette séance, sans
 * avoir à la relancer. Le verset en train de jouer n'est pas coupé — on ne coupe pas une
 * récitation au milieu d'un mot parce qu'un réglage a bougé.
 *
 * ## Le refus est ici, la tolérance est dans le moteur
 *
 * « Lancer ce passage » vérifie d'abord la saisie libre et refuse avec le message du client
 * d'origine ([AudioSettings.validationMessage]). Le moteur, lui, ne refuse jamais : une saisie
 * illisible y est ramenée à 1. Les deux règles coexistent parce qu'elles ne servent pas la même
 * personne — celle-ci empêche d'y arriver, celui-là empêche qu'une préférence abîmée fige la
 * séance.
 *
 * ## Les réglages sont enregistrés, mais pas par cette feuille
 *
 * La feuille ne fait que **remonter** chaque changement : elle n'écrit rien, et ne sait pas où
 * cela s'écrit. C'est l'appelant qui enregistre — le lecteur, seul à connaître le conteneur.
 * Une feuille qui écrirait elle-même se croirait propriétaire d'un réglage qui vaut pour toute
 * l'application, et deux endroits écriraient le même document.
 *
 * @param settings les réglages affichés. Ils viennent de l'appelant : c'est lui qui les tient,
 *   parce qu'il est aussi celui qui les applique au contrôleur.
 * @param reciter le récitateur courant.
 * @param onSettings remonte des réglages modifiés. Appelé à **chaque** appui, et non à la
 *   validation : c'est ce qui fait qu'un changement vaut pour la séance en cours.
 * @param onReciter remonte le récitateur choisi.
 * @param onLaunch lance l'écoute du passage affiché avec les réglages en place. `null` quand la
 *   plage du passage n'est pas connue : le bouton est alors absent, plutôt que présent et sans
 *   effet — la même règle que pour [onRestart].
 * @param onRestart reprend la séance ouverte depuis son premier verset. `null` quand aucune
 *   séance n'est ouverte : le bouton n'a alors rien à redémarrer, et il est absent plutôt que
 *   présent et sans effet.
 * @param onClose referme la feuille. La poignée le fait aussi, par un glissement vers le bas.
 */
@Composable
internal fun AudioSettingsSheet(
    settings: AudioSession,
    reciter: Reciter,
    onSettings: (AudioSession) -> Unit,
    onReciter: (Reciter) -> Unit,
    onLaunch: (() -> Unit)?,
    onRestart: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val colors = AppTheme.colors

    // Le refus de la dernière tentative de lancement. Vidé dès qu'un choix de répétition change :
    // laisser « Choisis entre 1 et 999 écoutes. » affiché alors que le choix est « 3 » serait
    // un message faux, et l'écran ne doit pas mentir sur son propre état.
    var error by remember { mutableStateOf<String?>(null) }
    var recitersShown by rememberSaveable { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // La zone au-dessus de la feuille : le toucher referme. Sans couleur, comme la
            // feuille d'options : la fenêtre de dialogue pose déjà son voile.
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
                    // Cette feuille porte bien plus de réglages que la feuille d'options : bornée
                    // à une fraction de l'écran, elle défile au lieu de recouvrir tout le
                    // moushaf. Régler l'écoute ne doit pas faire perdre de vue ce qu'on écoute.
                    .heightIn(max = maxHeight * SETTINGS_HEIGHT_FRACTION)
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
                            .padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppLabel(
                            text = AudioSettingsText.TITLE,
                            modifier = Modifier.weight(1f),
                            fontSize = AppTheme.typeScale.section,
                            fontWeight = FontWeight.ExtraBold,
                            selectable = false,
                        )
                        SheetCloseButton(
                            onClose = onClose,
                            description = AudioSettingsText.CLOSE,
                        )
                    }

                    ReciterPicker(
                        reciter = reciter,
                        expanded = recitersShown,
                        onToggle = { recitersShown = !recitersShown },
                        onPick = { chosen ->
                            recitersShown = false
                            onReciter(chosen)
                        },
                    )

                    SectionLabel(AudioSettingsText.REPEAT_LABEL)
                    ChoiceGrid(
                        items = AudioCount.ALL,
                        perRow = COUNT_PER_ROW,
                        label = { AudioSettingsText.countLabel(it) },
                        isSelected = { it == settings.countChoice },
                        onPick = { chosen ->
                            error = null
                            onSettings(settings.copy(countChoice = chosen))
                        },
                    )

                    // Le champ libre n'apparaît que pour « Autre » : un champ visible en
                    // permanence laisserait croire qu'il agit sur les choix fixes.
                    if (settings.countChoice == AudioCount.CUSTOM) {
                        AppField(
                            value = settings.customCount,
                            onValueChange = { text ->
                                error = null
                                onSettings(settings.copy(customCount = text))
                            },
                            placeholder = AudioSettingsText.CUSTOM_PLACEHOLDER,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }

                    ChoiceGrid(
                        items = MODES,
                        perRow = MODES.size,
                        label = { if (it == RepeatMode.PASSAGE) AudioSettingsText.MODE_PASSAGE else AudioSettingsText.MODE_EACH_VERSE },
                        isSelected = { it == settings.mode },
                        onPick = { chosen -> onSettings(settings.copy(mode = chosen)) },
                    )

                    SectionLabel(AudioSettingsText.SPEED_LABEL)
                    ChoiceGrid(
                        items = AudioSettings.SPEED_CHOICES,
                        perRow = AudioSettings.SPEED_CHOICES.size,
                        label = { AudioSettingsText.speedLabel(it) },
                        isSelected = { it == settings.speed },
                        onPick = { chosen -> onSettings(settings.copy(speed = chosen)) },
                    )

                    SectionLabel(AudioSettingsText.GAP_LABEL)
                    // La note est sous le titre, avant les choix : elle explique ce que « Aucune »
                    // ne dit pas, et doit donc se lire avant de le choisir.
                    AppLabel(
                        text = AudioSettingsText.technicalMarginNote(),
                        modifier = Modifier.padding(bottom = 6.dp),
                        fontSize = AppTheme.typeScale.metadata,
                        color = colors.muted,
                        selectable = false,
                    )
                    ChoiceGrid(
                        items = AudioSettings.GAP_CHOICES,
                        perRow = AudioSettings.GAP_CHOICES.size,
                        label = { AudioSettingsText.gapLabel(it) },
                        isSelected = { it == settings.gapSeconds },
                        onPick = { chosen -> onSettings(settings.copy(gapSeconds = chosen)) },
                    )

                    AppCheckChoice(
                        label = AudioSettingsText.AUTO_STOP,
                        // L'état affiché n'est pas le réglage : quand la répétition est illimitée,
                        // la case paraît décochée. Voir `AudioSettingsText.autoStopShown`.
                        selected = AudioSettingsText.autoStopShown(settings),
                        onPress = { onSettings(settings.copy(autoStop = !settings.autoStop)) },
                    )

                    onLaunch?.let { launch ->
                        AppButton(
                            text = AudioSettingsText.START,
                            onClick = {
                                val message = AudioSettings.validationMessage(settings)
                                error = message
                                if (message == null) launch()
                            },
                        )
                    }

                    if (onRestart != null) {
                        AppButton(
                            text = AudioSettingsText.RESTART,
                            onClick = onRestart,
                            secondary = true,
                            small = true,
                        )
                    }

                    error?.let { message ->
                        AppLabel(
                            text = message,
                            modifier = Modifier.padding(top = 6.dp),
                            fontSize = AppTheme.typeScale.secondary,
                            color = colors.red,
                            selectable = false,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Le récitateur courant, et la liste qu'il ouvre.
 *
 * La liste se **replie** après un choix, comme dans le client d'origine : la garder ouverte
 * laisserait la personne devant une liste de dix noms alors qu'elle a déjà choisi.
 */
@Composable
private fun ReciterPicker(
    reciter: Reciter,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPick: (Reciter) -> Unit,
) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.radius.small))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onToggle,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppLabel(
            text = AudioSettingsText.reciterLine(reciter.name),
            modifier = Modifier.weight(1f),
            fontSize = AppTheme.typeScale.body,
            fontWeight = FontWeight.Bold,
            color = colors.green,
            selectable = false,
        )
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = colors.green,
        )
    }

    if (expanded) {
        for (item in Audio.reciters) {
            AppChoice(
                label = item.name,
                subtitle = item.arabicName,
                selected = item.id == reciter.id,
                onPress = { onPick(item) },
            )
        }
    }
}

/** Un titre de section. La taille vient du thème, comme partout ailleurs. */
@Composable
private fun SectionLabel(text: String) {
    AppLabel(
        text = text,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
        fontSize = AppTheme.typeScale.secondary,
        fontWeight = FontWeight.Bold,
        selectable = false,
    )
}

/**
 * Une grille de pastilles, toutes de même largeur.
 *
 * Les rangées incomplètes sont **complétées par des espaces** plutôt que laissées courtes :
 * sans cela, les pastilles de la dernière rangée s'étireraient pour remplir la ligne, et la
 * grille paraîtrait cassée — c'est le `flexWrap` de l'original, sans son irrégularité.
 */
@Composable
private fun <T> ChoiceGrid(
    items: List<T>,
    perRow: Int,
    label: (T) -> String,
    isSelected: (T) -> Boolean,
    onPick: (T) -> Unit,
) {
    for (chunk in items.chunked(perRow)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (item in chunk) {
                AppButton(
                    text = label(item),
                    onClick = { onPick(item) },
                    modifier = Modifier.weight(1f),
                    // Le choix courant est le bouton plein, les autres sont doux : c'est la
                    // convention de l'original, et elle se lit sans légende.
                    secondary = !isSelected(item),
                    small = true,
                )
            }
            repeat(perRow - chunk.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Les deux modes, dans l'ordre du client d'origine : le passage, puis le verset. */
private val MODES = listOf(RepeatMode.PASSAGE, RepeatMode.EACH_VERSE)

/** Quatre pastilles par rangée : au-delà, les libellés se chevauchent sur un téléphone. */
private const val COUNT_PER_ROW = 4

/**
 * La part de l'écran que la feuille peut occuper.
 *
 * 85 % : assez pour tous les réglages sur un téléphone, et il reste toujours un bandeau de
 * moushaf visible — la feuille sert à régler une écoute, pas à remplacer ce qu'on écoute.
 */
private const val SETTINGS_HEIGHT_FRACTION = 0.85f
