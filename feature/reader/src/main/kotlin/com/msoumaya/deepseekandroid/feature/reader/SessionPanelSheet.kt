package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.msoumaya.deepseekandroid.core.design.component.AppButton
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.ReviewText
import com.msoumaya.deepseekandroid.core.domain.SessionPanelText
import com.msoumaya.deepseekandroid.core.domain.StudySession
import com.msoumaya.deepseekandroid.core.model.ReviewGrade

/**
 * Le panneau « Ma séance » : le carrefour d'une tâche.
 *
 * Porté depuis le panneau `sessionPanel === 'session'` de `src/App.tsx` (ligne 511). Il ne
 * s'ouvre que sur une tâche, et il rassemble les gestes qui n'ont de sens que pendant qu'on la
 * sert : valider une étape de consolidation, noter une révision, valider un point d'arrêt, clore
 * ou reporter une séance.
 *
 * ## Ce que le panneau décide, et ce qu'il ne décide pas
 *
 * Il ne décide **rien** : quelles entrées existent, et dans quel ordre, vient de
 * [SessionPanelText.entries], éprouvé dans `core:domain`. Ici on dispose, et c'est tout. La raison
 * est la même que partout ailleurs dans ce lecteur — une entrée qui manque ne se voit pas, et une
 * entrée en trop propose un geste dont la conséquence n'est pas celle qu'on croit.
 *
 * ## Une destination absente retire son entrée
 *
 * Les sept destinations sont des paramètres **facultatifs**, et c'est la règle du dépôt : une
 * entrée sans destination est **retirée**, et non grisée. « Ma voix » en est l'exemple vivant :
 * elle a disparu tant que l'enregistreur n'était pas porté, et elle est revenue le jour où
 * l'appelant a eu de quoi la brancher — sans qu'une ligne de ce fichier change. « À réapprendre »,
 * elle, dépend de `revisionId`, que ce client n'écrit jamais : elle ne figure pas à l'écran, au
 * lieu d'y figurer sans effet.
 *
 * ## Le bloc « Après ma séance » est le seul qui se déploie
 *
 * Il porte un intertitre et deux boutons, et il est gouverné par **une** condition : c'est un
 * apprentissage. [SessionPanelText.Entry.AFTER] reste donc une seule entrée, et c'est le rendu qui
 * la déploie — trois entrées pour une seule condition auraient écrit trois fois la même règle.
 *
 * @param request la tâche servie. Ses quatre faits suffisent à décider des entrées.
 * @param consolidationOffset l'étape de consolidation proposée, ou `null` quand les trois sont
 *   faites — auquel cas le libellé retombe sur la dernière, comme le bandeau.
 * @param onClose referme le panneau, y compris en tirant la poignée vers le bas.
 * @param onValidateConsolidation valide l'étape proposée. `null` retire l'entrée.
 * @param onGrade reçoit le grade du geste touché dans la barre. `null` retire la barre entière.
 * @param onAudio joue la révision. `null` retire « Écouter » de la barre.
 * @param onRecord ouvre l'enregistrement. `null` retire « Ma voix » de la barre.
 * @param onRelearn déclare qu'il faut réapprendre le passage. `null` retire l'entrée.
 * @param onValidate ouvre la feuille du point d'arrêt. `null` retire l'entrée.
 * @param onWorkAgain clôt la séance en la déclarant inachevée. `null` retire son bouton.
 * @param onPostpone reporte la séance entière. `null` retire son bouton.
 * @param active le geste en cours dans la barre — « Écouter » quand la lecture est ouverte.
 */
@Composable
internal fun SessionPanelSheet(
    request: StudySession.Request,
    consolidationOffset: Int?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onValidateConsolidation: (() -> Unit)? = null,
    onGrade: ((ReviewGrade) -> Unit)? = null,
    onAudio: (() -> Unit)? = null,
    onRecord: (() -> Unit)? = null,
    onRelearn: (() -> Unit)? = null,
    onValidate: (() -> Unit)? = null,
    onWorkAgain: (() -> Unit)? = null,
    onPostpone: (() -> Unit)? = null,
    active: ReviewText.Action? = null,
) {
    val colors = AppTheme.colors

    val destinations: Set<SessionPanelText.Entry> = buildSet {
        onValidateConsolidation?.let { add(SessionPanelText.Entry.CONSOLIDATION) }
        onGrade?.let { add(SessionPanelText.Entry.GRADES) }
        onRelearn?.let { add(SessionPanelText.Entry.RELEARN) }
        onValidate?.let { add(SessionPanelText.Entry.VALIDATE) }
        // Le bloc « Après ma séance » se contente d'**un** de ses deux boutons : l'intertitre
        // annonce une intention, et une seule des deux façons de la suivre suffit à la rendre
        // utile. Exiger les deux aurait retiré le bloc entier pour un bouton manquant.
        if (onWorkAgain != null || onPostpone != null) add(SessionPanelText.Entry.AFTER)
    }

    val entrees = SessionPanelText.entries(request, destinations)

    // Rien à proposer : le panneau ne s'ouvre pas du tout. Se réduire à un titre et à une poignée
    // serait une impasse — et c'est à l'appelant de ne pas l'ouvrir dans ce cas, ce que le lecteur
    // fait déjà par `isUseful`. Une lecture libre est exactement ce cas.
    if (entrees.isEmpty()) return

    Dialog(
        onDismissRequest = onClose,
        // Sans cela, le panneau se limite à la largeur d'un téléphone en paysage.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // La zone au-dessus du panneau : le toucher referme. Sans couleur — la fenêtre de
            // dialogue pose déjà son voile, et en ajouter un second assombrirait deux fois.
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
                modifier = modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // Le bas du panneau doit rester au-dessus de la barre de gestes : c'est le
                    // seul bord qui compte, le panneau étant ancré en bas.
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
                            text = SessionPanelText.TITLE,
                            modifier = Modifier.weight(1f),
                            fontSize = AppTheme.typeScale.section,
                            fontWeight = FontWeight.ExtraBold,
                            selectable = false,
                        )

                        SheetCloseButton(
                            onClose = onClose,
                            description = SessionPanelText.CLOSE,
                        )
                    }

                    for (entree in entrees) {
                        when (entree) {
                            // `?.let` et non une garde qui interromprait la boucle : l'entrée
                            // n'existe que si sa destination est là — c'est `entries` qui l'a
                            // décidé — donc ce bloc s'exécute toujours. L'écrire ainsi dit
                            // l'invariant sans le répéter, et ne laisse aucun chemin où une entrée
                            // serait dessinée sans destination.
                            SessionPanelText.Entry.CONSOLIDATION -> onValidateConsolidation?.let { action ->
                                AppButton(
                                    text = SessionPanelText.consolidationLabel(consolidationOffset),
                                    onClick = action,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = ENTRY_GAP),
                                )
                            }

                            SessionPanelText.Entry.GRADES -> onGrade?.let { noter ->
                                RevisionActionBar(
                                    onGrade = noter,
                                    onAudio = onAudio,
                                    onRecord = onRecord,
                                    active = active,
                                    modifier = Modifier.padding(bottom = ENTRY_GAP),
                                )
                            }

                            // Secondaire, comme dans le source : c'est un constat de recul, pas le
                            // geste qu'on vient faire. Les trois notes et les deux validations sont
                            // primaires, et c'est voulu — elles disent ce qui a été fait.
                            SessionPanelText.Entry.RELEARN -> onRelearn?.let { action ->
                                AppButton(
                                    text = SessionPanelText.RELEARN,
                                    onClick = action,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = ENTRY_GAP),
                                    secondary = true,
                                )
                            }

                            SessionPanelText.Entry.VALIDATE -> onValidate?.let { action ->
                                AppButton(
                                    text = SessionPanelText.VALIDATE,
                                    onClick = action,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = ENTRY_GAP),
                                )
                            }

                            SessionPanelText.Entry.AFTER -> {
                                AppLabel(
                                    text = SessionPanelText.AFTER_SESSION,
                                    modifier = Modifier.padding(top = 12.dp, bottom = ENTRY_GAP),
                                    fontWeight = FontWeight.Bold,
                                    selectable = false,
                                )
                                onWorkAgain?.let { action ->
                                    AppButton(
                                        text = SessionPanelText.WORK_AGAIN,
                                        onClick = action,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = ENTRY_GAP),
                                        secondary = true,
                                    )
                                }
                                onPostpone?.let { action ->
                                    AppButton(
                                        text = SessionPanelText.POSTPONE,
                                        onClick = action,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = ENTRY_GAP),
                                        secondary = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * L'espace entre deux entrées.
 *
 * La colonne n'utilise pas `Arrangement.spacedBy` : l'espacement doit tomber **après** chaque
 * entrée, y compris la dernière, sans quoi le bas du panneau collerait à la barre de gestes. Même
 * valeur, et même raison, que les deux autres panneaux du lecteur.
 */
private val ENTRY_GAP = 8.dp
