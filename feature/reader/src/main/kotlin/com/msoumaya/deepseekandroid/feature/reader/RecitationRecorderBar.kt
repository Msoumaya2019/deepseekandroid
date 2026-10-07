package com.msoumaya.deepseekandroid.feature.reader

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.RecitationAction
import com.msoumaya.deepseekandroid.core.domain.RecitationPhase
import com.msoumaya.deepseekandroid.core.domain.RecitationRecorder
import com.msoumaya.deepseekandroid.core.domain.RecitationStartProblem
import com.msoumaya.deepseekandroid.core.domain.RecitationText
import com.msoumaya.deepseekandroid.core.model.LocalRecitation
import com.msoumaya.deepseekandroid.core.model.Range
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// La barre d'enregistrement, dans le panneau du lecteur
// ---------------------------------------------------------------------------
// Portage de la branche `compact` de `src/RecitationRecorder.tsx` (lignes 18 à 91), montée par
// `App.tsx:508` dans le panneau `sessionPanel === 'record'`.
//
// **Ce que ce fichier fait, et ce qu'il ne fait pas.** Il **exécute** : il tient la phase, le
// brouillon, la récitation gardée et le message, et il appelle la capacité reçue. Il ne **décide**
// rien — quelle phase suit quelle autre, quels gestes sont offerts, ce qui empêche de commencer et
// quel mot porte un geste sont dans `core:domain/RecitationRecorder.kt` —, et il ne **compose**
// rien non plus : la ligne d'état, la durée, l'ordre et la mise en avant des gestes viennent de
// `RecitationRecorderRenderer`. C'est ce qui rend cette surface mince, et c'est voulu : ce qui
// reste ici est ce qui a besoin d'un appareil — le microphone, une coroutine, un écran.
//
// **Les trois portes, et leur ordre.** L'original contrôle le compte, puis la notice, puis la
// permission du microphone, et **cet ordre compte** : on ne réclame pas un accès avant d'avoir dit
// à quoi il sert. La règle est celle du domaine (`startProblem`), appelée deux fois — une fois
// avant de demander, avec `microphoneGranted = true`, ce qui revient à demander « qu'est-ce qui
// m'arrête, en dehors du micro ? », puis une fois avec la réponse réelle.
//
// **Ce qui diffère de l'original, et pourquoi.** Sa demande de permission est une promesse
// (`await requestRecordingPermissionsAsync()`), donc une suite d'instructions. Ici c'est un
// **rappel** : la demande passe par une `Activity`, et le code qui suit s'exécute dans la réponse.
// La suite est la même, découpée autrement — et c'est la seule façon de le faire sur Android.
// ---------------------------------------------------------------------------

/**
 * Ce que la barre retient d'un enregistrement arrêté, en attendant qu'on le garde.
 *
 * Le **passage** est figé ici, et pas relu à l'écran : la page peut tourner pendant qu'on
 * enregistre ou qu'on réécoute, et un brouillon qui suivrait la page changerait de bornes après
 * coup. C'est ce que l'original capture dans `recordingContext.current` au moment de commencer, et
 * un contrôle de son banc le fixe explicitement.
 */
private data class RecitationDraft(val path: String, val durationMs: Long)

/**
 * La barre d'enregistrement, telle qu'elle apparaît dans le panneau du lecteur.
 *
 * @param capability ce avec quoi enregistrer : les deux ports, le compte, la notice, la garde et
 *   le partage. Voir `RecitationRecorderCapability` — la surface ne connaît rien d'autre.
 * @param range le passage que la barre enregistre, **figé au moment de commencer**.
 * @param onRecordingChange prévient l'écran qui l'abrite : `true` tant que l'enregistreur occupe la
 *   personne. C'est ce qui empêche de fermer le panneau en pleine capture, et ce qui grise la
 *   barre de révision.
 */
@Composable
internal fun RecitationRecorderBar(
    capability: RecitationRecorderCapability,
    range: Range,
    modifier: Modifier = Modifier,
    onRecordingChange: (Boolean) -> Unit = {},
) {
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(RecitationPhase.IDLE) }
    var draft by remember { mutableStateOf<RecitationDraft?>(null) }
    var item by remember { mutableStateOf<LocalRecitation?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var noticeOpen by remember { mutableStateOf(false) }

    // La durée vivante. Elle est **lue** sur l'enregistreur, à la cadence de l'affichage, et non
    // comptée ici : c'est le même compteur qui donnera la durée du fichier, donc la ligne d'état
    // annonce exactement ce qui sera gardé. Voir la note du port `AudioRecorder.elapsedMs`.
    var liveMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(phase) {
        while (phase == RecitationPhase.RECORDING || phase == RecitationPhase.PAUSED) {
            liveMs = capability.recorder.elapsedMs
            delay(LIVE_REFRESH_MS)
        }
    }

    // L'occupation, telle que l'écran qui l'abrite doit la connaître. La règle est celle du
    // domaine : un enregistrement en cours de sauvegarde occupe encore la personne.
    LaunchedEffect(phase, busy) {
        onRecordingChange(RecitationRecorder.isActive(phase, busy))
    }

    // Et elle doit **retomber** quand la barre quitte l'écran. L'original le fait à son démontage,
    // et sans cela l'écran qui garde le drapeau resterait bloqué — barre de révision grisée,
    // fermeture refusée — après le départ de la barre. La référence est tenue à jour : un rappel
    // capturé au premier passage viserait un état périmé.
    val notifier = rememberUpdatedState(onRecordingChange)
    DisposableEffect(Unit) {
        onDispose { notifier.value(false) }
    }

    /**
     * Démarre la capture, une fois les trois portes franchies.
     *
     * Le passage est figé **ici**, et non lu à l'affichage : c'est le moment où l'enregistrement
     * commence, et la page peut changer pendant qu'il dure.
     */
    fun startCapture() {
        // L'original coupe son lecteur avant de préparer le microphone (`player.current?.pause()`).
        // Ce n'est pas une politesse : la récitation qu'on vient de réécouter sortirait du
        // haut-parleur et entrerait dans la nouvelle capture.
        capability.player.pause()
        if (!capability.recorder.start()) {
            message = RecitationText.AUDIO_UNAVAILABLE
            return
        }
        // **Aucun brouillon n'est posé ici**, et c'est ce qui fait vivre le chronomètre : la
        // durée affichée prend le brouillon d'abord, puis la récitation gardée, puis le compteur
        // vivant. Un brouillon à zéro posé au départ figerait la ligne d'état sur `00:00`
        // pendant toute la capture. Le brouillon naît à l'arrêt, quand il a une durée à porter.
        phase = RecitationPhase.RECORDING
        message = null
    }

    // La permission du microphone. La demande est un objet d'`Activity`, donc un lanceur : voir la
    // note de tête sur ce qui diffère de la promesse de l'original.
    val microphone = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { accordee ->
        if (accordee) startCapture() else message = RecitationText.MICROPHONE_DENIED
    }

    /** Le premier appui : le compte, puis la notice, puis le microphone. */
    fun begin() {
        val proprietaire = capability.owner()
        // Le compte est la seule des trois causes qui se lise **sans attendre** : la règle du
        // domaine est donc appelée ici avec les deux autres acquises, ce qui isole sa réponse.
        if (RecitationRecorder.startProblem(
                proprietaire,
                noticeAccepted = true,
                microphoneGranted = true,
            ) == RecitationStartProblem.OWNER_MISSING
        ) {
            message = RecitationText.RECORD_OWNER_MISSING
            return
        }
        val id = proprietaire ?: return
        scope.launch {
            busy = true
            try {
                if (capability.noticeAccepted(id)) {
                    microphone.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    noticeOpen = true
                }
            } finally {
                busy = false
            }
        }
    }

    /**
     * Arrête la capture, et la garde ou la jette.
     *
     * `save = false` n'efface rien du disque : il **jette** le brouillon. C'est le geste de
     * l'original, et le mot qu'il affiche le dit — « Enregistrement annulé. Rien n'a été envoyé. »
     */
    fun finish(save: Boolean) {
        if (!RecitationRecorder.canFinish(phase)) return
        scope.launch {
            busy = true
            try {
                if (!save) {
                    // L'original appelle `recorder.stop()` puis jette le brouillon, ce qui laisse
                    // son fichier dans le cache du système. Le port a un geste de plus — `cancel()`
                    // — et s'en sert : rien ne traîne après un enregistrement annulé, ce qui compte
                    // ici, où l'on annule souvent plusieurs prises d'affilée. Le message, lui, est
                    // celui de l'original, mot pour mot.
                    capability.recorder.cancel()
                    phase = RecitationPhase.IDLE
                    message = RecitationText.RECORDING_CANCELLED
                    return@launch
                }
                val capture = capability.recorder.stop()
                phase = RecitationPhase.IDLE
                if (capability.owner().isNullOrEmpty() || capture == null) {
                    message = RecitationText.AUDIO_UNAVAILABLE
                    return@launch
                }
                draft = RecitationDraft(path = capture.path, durationMs = capture.durationMs)
                // La barre réduite passe toujours par l'aperçu : sa référence est déjà sous les
                // yeux, et l'aperçu tient lieu de confirmation. La règle est celle du domaine.
                phase = RecitationRecorder.phaseAfterFinish(isInvocation = false, compact = true)
            } finally {
                busy = false
            }
        }
    }

    /** Garde le brouillon : la copie au registre, puis la récitation inscrite. */
    fun saveDraft() {
        val courant = draft ?: return
        scope.launch {
            busy = true
            try {
                val inscrite = capability.save(courant.path, courant.durationMs, range)
                if (inscrite == null) {
                    message = RecitationText.CONNECTION_REQUIRED
                    return@launch
                }
                item = inscrite
                draft = null
                phase = RecitationPhase.SAVED
                message = RecitationText.DRAFT_SAVED
            } finally {
                busy = false
            }
        }
    }

    /** Réécoute : la récitation gardée si elle existe, le brouillon sinon. */
    fun listen() {
        val adresse = item?.uri ?: draft?.path ?: return
        capability.player.play(adresse)
    }

    /** Tout reprendre à zéro : l'écoute s'arrête, et rien n'est gardé. */
    fun restart() {
        // L'original **met en pause** son lecteur, il ne le détruit pas (`player.current?.pause()`) :
        // la piste suivante est de toute façon rechargée par `play`, qui repose son élément.
        capability.player.pause()
        draft = null
        item = null
        phase = RecitationPhase.IDLE
        message = null
    }

    fun onGesture(action: RecitationAction) {
        when (action) {
            RecitationAction.BEGIN -> begin()
            RecitationAction.PAUSE -> {
                capability.recorder.pause()
                phase = RecitationPhase.PAUSED
            }

            RecitationAction.RESUME -> {
                capability.recorder.resume()
                phase = RecitationPhase.RECORDING
            }

            RecitationAction.FINISH -> finish(save = true)
            RecitationAction.CANCEL -> finish(save = false)
            RecitationAction.LISTEN -> listen()
            RecitationAction.RESTART -> restart()
            RecitationAction.SAVE -> saveDraft()
            RecitationAction.SHARE -> item?.let { gardee -> capability.share?.invoke(gardee) }
        }
    }

    val ui = RecitationRecorderRenderer.render(
        phase = phase,
        draftMs = draft?.durationMs,
        liveMs = liveMs,
        itemMs = item?.durationMs,
        // Les deux conditions de l'original — `item && onShare` —, et il faut bien les deux. La
        // seconde n'est pas décorative : le lecteur n'a pas encore de sortie vers le partage, donc
        // la capacité qu'il reçoit porte `share = null`, et le geste **disparaît** au lieu
        // d'apparaître sans effet. C'est la règle du dépôt, et c'est la seule façon de l'écrire.
        canShare = item != null && capability.share != null,
        message = message,
    )

    Column(modifier = modifier) {
        AppLabel(
            text = ui.status,
            color = if (ui.capturing) colors.red else colors.muted,
            // L'original pose 13 ; l'échelle du dépôt n'a pas de jeton à 13, et `secondary` (12)
            // est le plus proche. Même arbitrage que la barre de révision, qui écrit 10 et prend
            // `metadata`.
            fontSize = AppTheme.typeScale.secondary,
            selectable = false,
            modifier = Modifier.padding(bottom = STATUS_GAP),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GESTURE_GAP),
        ) {
            for (geste in ui.actions) {
                RecitationGesture(
                    gesture = geste,
                    disabled = busy,
                    onClick = { onGesture(geste.action) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        ui.message?.let { phrase ->
            AppLabel(
                text = phrase,
                color = colors.muted,
                fontSize = AppTheme.typeScale.metadata,
                // L'original borne la phrase à deux lignes (`numberOfLines={2}`) : elle dit ce qui
                // vient de se passer, elle n'est pas un journal.
                maxLines = MESSAGE_LINES,
                selectable = false,
                modifier = Modifier.padding(top = MESSAGE_GAP),
            )
        }
    }

    if (noticeOpen) {
        RecitationNoticeDialog(
            onDismiss = { noticeOpen = false },
            onAccept = {
                noticeOpen = false
                val id = capability.owner() ?: return@RecitationNoticeDialog
                scope.launch {
                    // L'acceptation est écrite **avant** de demander le microphone : si la
                    // permission est refusée, la notice reste lue, et l'appui suivant ne la
                    // repose pas.
                    capability.acceptNotice(id)
                    microphone.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
        )
    }
}

/**
 * La notice qui dit ce que deviennent les récitations.
 *
 * Elle est **bloquante** au sens de l'original : deux issues, « Annuler » et « Compris,
 * enregistrer », et pas de fermeture par le fond — la `Alert` du client d'origine ne se ferme pas
 * en touchant à côté non plus.
 */
@Composable
private fun RecitationNoticeDialog(onDismiss: () -> Unit, onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { AppLabel(text = RecitationText.NOTICE_TITLE, selectable = false) },
        text = { AppLabel(text = RecitationText.NOTICE_BODY, selectable = false) },
        confirmButton = {
            TextButton(onClick = onAccept) {
                AppLabel(
                    text = RecitationText.NOTICE_ACCEPT,
                    color = AppTheme.colors.green,
                    fontWeight = FontWeight.SemiBold,
                    selectable = false,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                AppLabel(text = RecitationText.CANCEL, color = AppTheme.colors.muted, selectable = false)
            }
        },
    )
}

/**
 * Un geste de la barre : son icône, son mot, et son état.
 *
 * Mis en avant quand le domaine le dit — vert plein, contenu blanc —, et doux sinon. **Grisé
 * pendant une opération**, comme l'original, qui pose `disabled={busy}` et divise l'opacité par
 * deux sur chaque bouton : un geste qui partirait deux fois, une main qui tremble, écrirait deux
 * fois.
 */
@Composable
private fun RecitationGesture(
    gesture: RecitationActionUi,
    disabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .sizeIn(minHeight = GESTURE_MIN_HEIGHT)
            .clip(RoundedCornerShape(GESTURE_RADIUS))
            .background(if (gesture.primary) colors.green else colors.soft)
            .alpha(if (disabled) DISABLED_ALPHA else 1f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !disabled,
                role = Role.Button,
                onClickLabel = gesture.label,
                onClick = onClick,
            )
            .padding(horizontal = GESTURE_PADDING, vertical = GESTURE_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = gesture.action.icon(),
            contentDescription = null,
            tint = if (gesture.primary) Color.White else colors.green,
            modifier = Modifier.size(ICON_SIZE),
        )
        AppLabel(
            text = gesture.label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = LABEL_GAP),
            color = if (gesture.primary) Color.White else colors.green,
            fontSize = AppTheme.typeScale.metadata,
            textAlign = TextAlign.Center,
            selectable = false,
        )
    }
}

/**
 * L'icône d'un geste.
 *
 * Le client d'origine nomme les siennes dans le vocabulaire de MaterialCommunityIcons —
 * `microphone`, `pause`, `play`, `stop`, `close`, `refresh`, `check`, `share-variant` — et Material
 * Icons n'a pas les mêmes noms. Les huit se correspondent directement, à une exception près qui
 * n'en est pas une : `play` sert **deux** gestes, « Reprendre » et « Réécouter », et c'est
 * exactement ce que fait l'original, qui passe la même icône aux deux.
 *
 * La fonction est exhaustive et sans `else` : ajouter un geste à [RecitationAction] sans lui donner
 * d'icône ne compile pas, ce qui vaut mieux qu'un dessin par défaut que personne ne remarque.
 */
private fun RecitationAction.icon(): ImageVector = when (this) {
    RecitationAction.BEGIN -> Icons.Outlined.Mic
    RecitationAction.PAUSE -> Icons.Outlined.Pause
    RecitationAction.RESUME -> Icons.Outlined.PlayArrow
    RecitationAction.FINISH -> Icons.Outlined.Stop
    RecitationAction.CANCEL -> Icons.Outlined.Close
    RecitationAction.LISTEN -> Icons.Outlined.PlayArrow
    RecitationAction.RESTART -> Icons.Outlined.Refresh
    RecitationAction.SAVE -> Icons.Outlined.Check
    RecitationAction.SHARE -> Icons.Outlined.Share
}

/**
 * La cadence de rafraîchissement de la durée, en millisecondes.
 *
 * L'original interroge son enregistreur toutes les 250 ms (`useAudioRecorderState(recorder, 250)`).
 * C'est la cadence de son horloge : plus rapide, on redessinerait la même seconde plusieurs fois ;
 * plus lent, le compteur sauterait des secondes visibles.
 */
private const val LIVE_REFRESH_MS = 250L

/** L'écart entre deux gestes. L'original pose 6. */
private val GESTURE_GAP = 6.dp

/** Le rayon d'un geste. L'original pose 14. */
private val GESTURE_RADIUS = 14.dp

/** Sa hauteur plancher. L'original pose 44 — un plancher d'accessibilité, conservé tel quel. */
private val GESTURE_MIN_HEIGHT = 44.dp

/** Sa marge intérieure. L'original pose 4 en horizontal. */
private val GESTURE_PADDING = 4.dp

/** La taille de l'icône. L'original pose 18. */
private val ICON_SIZE = 18.dp

/** L'écart entre l'icône et son mot. L'original pose 4. */
private val LABEL_GAP = 4.dp

/** L'écart sous la ligne d'état. L'original pose 8. */
private val STATUS_GAP = 8.dp

/** L'écart au-dessus du message. L'original pose 6. */
private val MESSAGE_GAP = 6.dp

/** Le nombre de lignes du message. L'original pose 2. */
private const val MESSAGE_LINES = 2

/** L'opacité d'un geste pendant une opération. L'original pose 0.5. */
private const val DISABLED_ALPHA = 0.5f
