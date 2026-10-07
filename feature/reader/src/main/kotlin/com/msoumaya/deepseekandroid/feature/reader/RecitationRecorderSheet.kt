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
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.RecitationText
import com.msoumaya.deepseekandroid.core.model.Range

/**
 * Le panneau « Ma récitation » : l'enregistreur, et rien d'autre.
 *
 * Porté depuis le panneau `sessionPanel === 'record'` de `src/App.tsx` (ligne 507), qui monte
 * `RecitationRecorder` en mode `compact` (ligne 508). Le panneau n'apporte que son cadre — un
 * titre, une poignée, une fermeture — ; tout ce qui se dit et tout ce qui se fait est dans
 * [RecitationRecorderBar], et toutes les décisions sont dans `core:domain/RecitationRecorder.kt`.
 *
 * ## La plage enregistrée
 *
 * L'original écrit `range={focused ? reader.range : sourcePageRange}` : quand une séance est
 * servie, on enregistre **le passage de la séance** ; sinon, la page affichée. Le portage garde la
 * règle telle quelle, et c'est l'appelant qui choisit — le lecteur seul connaît les deux, et lui
 * seul sait si une séance est ouverte.
 *
 * ## L'original remonte son composant, ce portage n'en a pas besoin
 *
 * La ligne 508 pose `key={`${reader.range.start}-${reader.range.end}`}` : changer de plage
 * reconstruit l'enregistreur, donc jette sa phase, son brouillon et son message. Ici, la fermeture
 * du panneau **défait la composition** — l'état `remember` de la barre meurt avec elle — et la
 * plage ne peut pas changer pendant qu'il est ouvert, le panneau étant une fenêtre de dialogue qui
 * couvre la page comme la coquille. La clé n'aurait donc rien à faire qu'une seconde fois.
 *
 * ## La fermeture est gardée, et c'est le seul endroit où elle peut l'être
 *
 * L'original grise pendant une capture **trois** choses : le bouton de fermeture du panneau, la
 * barre flottante du lecteur (`disabled={recordingActive}`, ligne 503), et la barre d'action d'une
 * révision (ligne 511). Les trois gardes ne se portent pas de la même façon, et il faut le dire
 * plutôt que de les recopier :
 *
 *  - **la fermeture du panneau** — ici, et elle est réelle : c'est la seule issue qui ferait
 *    perdre une capture en cours. Elle est donc tenue sur les quatre chemins qui ferment — la
 *    touche de retour, le toucher à côté, la poignée tirée, et le bouton ;
 *  - **la barre flottante** — inatteignable dans ce portage. L'original la laisse visible sous son
 *    panneau, qui est une vue positionnée ; les sept panneaux de ce lecteur sont des fenêtres de
 *    dialogue, donc la coquille est derrière leur voile et aucun toucher ne l'atteint. Un
 *    `disabled` y serait un paramètre qui ne peut jamais être vrai ;
 *  - **la barre d'action d'une révision** — inatteignable **dans l'original aussi**. Elle ne se
 *    dessine que pour `sessionPanel === 'session'` (ligne 511), et `recordingActive` n'est vrai que
 *    pendant `'record'`, dont l'ouverture referme le premier. Les deux conditions s'excluent : le
 *    `disabled` de la ligne 511 ne peut pas être vrai une seule fois. C'est une garde morte de la
 *    source, et la recopier n'ajouterait pas une sécurité — elle ajouterait un paramètre.
 *
 * @param capability ce avec quoi enregistrer. La feuille ne connaît rien d'autre du conteneur.
 * @param range le passage à enregistrer, déjà choisi par l'appelant.
 * @param recordingActive vrai tant que l'enregistreur occupe la personne. **Reçu** de l'appelant,
 *   et non tenu ici : c'est l'appelant qui décide de ce qui se ferme, et deux copies de ce fait
 *   finiraient par diverger.
 * @param onRecordingChange rapporte les changements d'occupation, pour que l'appelant tienne la
 *   valeur qu'il renvoie ici.
 * @param onClose referme le panneau. Ignoré pendant une capture.
 */
@Composable
internal fun RecitationRecorderSheet(
    capability: RecitationRecorderCapability,
    range: Range,
    recordingActive: Boolean,
    onRecordingChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors

    // Les quatre issues de fermeture passent par ici, et une seule garde les couvre : c'est ce qui
    // évite qu'un chemin ajouté plus tard oublie la sienne. La touche de retour système entre par
    // `onDismissRequest`, le toucher à côté par le voile, la poignée et le bouton par leur rappel.
    val fermer = { if (!recordingActive) onClose() }

    Dialog(
        onDismissRequest = fermer,
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
                        onClick = fermer,
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
                    SheetHandle(onDismiss = fermer)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = TITLE_GAP),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppLabel(
                            text = RecitationText.PANEL_TITLE,
                            modifier = Modifier.weight(1f),
                            fontSize = AppTheme.typeScale.section,
                            fontWeight = FontWeight.ExtraBold,
                            selectable = false,
                        )

                        SheetCloseButton(
                            onClose = fermer,
                            description = RecitationText.PANEL_CLOSE,
                            // La garde de l'original (`disabled={recordingActive}`, ligne 507) :
                            // pendant une capture, le panneau ne se referme pas.
                            enabled = !recordingActive,
                        )
                    }

                    RecitationRecorderBar(
                        capability = capability,
                        range = range,
                        onRecordingChange = onRecordingChange,
                    )
                }
            }
        }
    }
}

/**
 * L'écart sous le titre, avant la barre.
 *
 * L'original pose `marginBottom: 0` pour ce panneau — et `10` pour les cinq autres —, parce que sa
 * barre porte elle-même l'écart sous sa ligne d'état, et que son titre a la taille du corps du
 * texte. Zéro ne se recopie donc pas ici : notre titre est `section` (21), et collé à une ligne de
 * 12, le titre et l'état se liraient comme un seul bloc. C'est le seul écart de ce panneau qui
 * s'écarte de la source, et il est **nommé** pour que personne ne le prenne pour un chiffre mesuré.
 *
 * Le **remplissage** du panneau, lui, suit [SHEET_PADDING] — le jeton des six autres feuilles — et
 * non les 10 points de la source, qui resserre celui-ci parce que ses trois gestes doivent tenir
 * sur une ligne. Un jeton propre à ce panneau pour quatre points d'écart créerait une exception là
 * où le dépôt en a déjà une.
 */
private val TITLE_GAP = 6.dp
