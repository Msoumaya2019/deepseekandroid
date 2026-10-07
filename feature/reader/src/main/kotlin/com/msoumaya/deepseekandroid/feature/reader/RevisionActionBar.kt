package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.ReviewText
import com.msoumaya.deepseekandroid.core.model.ReviewGrade

/**
 * La barre d'action d'une révision : trois notes, puis deux gestes d'écoute.
 *
 * Porté depuis `src/ui/RevisionBottomActionBar.tsx`, mesuré ligne à ligne. C'est le seul endroit
 * où une révision se note **sans passer par la feuille de validation**, et c'est ce qui justifie
 * qu'il vive dans le panneau « Ma séance » plutôt que sur le bandeau : noter est un geste de
 * décision, et un bandeau qui le porterait à côté de « Terminer » proposerait deux fins pour la
 * même séance.
 *
 * ## Le grade est **reçu** par l'appelant, et l'appelant d'aujourd'hui n'en fait rien
 *
 * C'est une mesure, pas un oubli. Le source écrit
 * `onGrade={()=>{setSessionPanel(null);…setCompletionOpen(true);}}` : la lambda **ignore son
 * argument** et se contente d'ouvrir la feuille de validation, qui redemande la note et enregistre
 * celle qu'on y choisit. Le portage transcrit cela — le paramètre reste, parce que la barre a bien
 * un grade à donner, et le lecteur l'ignore pour la même raison que le source : la note est
 * choisie **une fois**, dans la feuille, et deux endroits qui la choisiraient finiraient par en
 * écrire deux.
 *
 * Ce n'est donc pas la barre qui enregistre. Elle **annonce** un choix et ouvre l'écran où il se
 * confirme.
 *
 * ## Une destination absente retire son geste
 *
 * [onAudio] et [onRecord] sont facultatifs, et [ReviewText.actionBar] retire les gestes qui n'ont
 * pas de destination. C'est la règle du dépôt, et elle vaut pour les deux : un geste qui ne mène
 * nulle part est **retiré**, jamais grisé.
 *
 * ## Le `disabled` de la source, et pourquoi il ne revient pas avec l'enregistreur
 *
 * Le source accepte un `disabled` global, vrai pendant un enregistrement : il grise la barre
 * entière et la met à `opacity: .5`. Ce portage ne l'a pas, et ce n'est ni une simplification ni un
 * report : cette garde est **morte dans la source elle-même**. La barre ne se dessine que pour
 * `sessionPanel === 'session'` (ligne 511), et `recordingActive` n'est vrai que pendant `'record'`
 * — dont l'ouverture referme le premier, puisque `sessionPanel` n'admet qu'une valeur. Les deux
 * conditions s'excluent : `disabled={recordingActive}` ne peut pas être vrai une seule fois.
 *
 * Ce qui protège réellement une capture en cours est ailleurs dans la source : la fermeture du
 * panneau (ligne 507) et la barre flottante du lecteur (ligne 503), toutes deux
 * `disabled={recordingActive}`. La première est portée — c'est la garde de
 * `RecitationRecorderSheet`. La seconde est inatteignable ici, nos panneaux étant des fenêtres de
 * dialogue : voir la note de tête de `RecitationRecorderSheet`.
 *
 * @param onGrade reçoit le grade du geste touché. Voir la note de tête : le lecteur l'ignore
 *   aujourd'hui, et ouvre la feuille de validation.
 * @param onAudio joue la révision. `null` retire « Écouter ».
 * @param onRecord ouvre l'enregistrement de la voix. `null` retire « Ma voix ».
 * @param active le geste **en cours** — « Écouter » quand la lecture est ouverte. `null` quand
 *   aucun ne l'est. Seul [ReviewText.Action.LISTEN] est jamais passé, comme dans le source, où
 *   l'expression est `audioDock ? 'audio' : null`.
 */
@Composable
internal fun RevisionActionBar(
    onGrade: (ReviewGrade) -> Unit,
    modifier: Modifier = Modifier,
    onAudio: (() -> Unit)? = null,
    onRecord: (() -> Unit)? = null,
    active: ReviewText.Action? = null,
) {
    val colors = AppTheme.colors

    // Chaque geste reçoit sa destination ici, une fois. Les trois notes partagent `onGrade` et lui
    // passent **leur** grade : c'est le seul endroit qui sache lequel des trois a été touché.
    val destinations: Map<ReviewText.Action, () -> Unit> = buildMap {
        for (action in ReviewText.Action.entries) {
            when (action) {
                ReviewText.Action.LISTEN -> onAudio?.let { put(action, it) }
                ReviewText.Action.RECORD -> onRecord?.let { put(action, it) }
                else -> {
                    val grade = action.grade ?: continue
                    put(action) { onGrade(grade) }
                }
            }
        }
    }

    val gestes = ReviewText.actionBar(destinations.keys)

    // Aucun geste : la barre ne se dessine pas du tout. Un cadre vide avec un trait au milieu
    // serait le pire des deux mondes — il prendrait la place sans rien permettre.
    if (gestes.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            // Les marges du source : 6 de chaque côté, 5 en haut et en bas. Elles sont sur la
            // barre, et non sur le panneau qui la contient — c'est ce qui la détache du bloc
            // au-dessus d'elle.
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(BAR_RADIUS))
            .background(colors.paper)
            .border(1.dp, colors.line, RoundedCornerShape(BAR_RADIUS))
            .padding(BAR_PADDING)
            // `alignItems: 'stretch'` du source : les cinq gestes ont la **même** hauteur, celle du
            // plus haut. `IntrinsicSize.Min` est ce qui l'obtient sans écrire de hauteur en dur —
            // une hauteur fixe mentirait dès que la police grandit, et le libellé sur deux lignes
            // dépasserait alors les autres sans que rien ne le signale. Même mécanisme que le
            // bandeau de séance, et pour la même raison.
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((rang, action) in gestes.withIndex()) {
            // Le trait tombe **avant le quatrième rang**, donc entre les trois notes et les deux
            // gestes d'écoute. C'est le seul endroit du rendu qui sache où il va, et c'est
            // pourquoi l'ordre de `ReviewText.Action` est figé par un test du domaine.
            if (rang == SEPARATOR_BEFORE) Separator()

            Geste(
                action = action,
                selectionne = action == active,
                onClick = destinations.getValue(action),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Le trait vertical entre les notes et les gestes d'écoute. */
@Composable
private fun Separator() {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier
            .padding(horizontal = SEPARATOR_GAP, vertical = SEPARATOR_INSET)
            .width(1.dp)
            .fillMaxHeight()
            .background(colors.line),
    )
}

/**
 * Un geste de la barre : son icône, son mot, et son état.
 *
 * Il est **sélectionné** quand c'est lui qui est en cours — vert plein, contenu blanc — et neutre
 * sinon. Le mot garde sa couleur de texte ordinaire quand il est neutre, comme dans le source : le
 * fond doux n'est pas un fond de bouton principal, et y écrire du vert donnerait deux accents pour
 * une seule intention.
 */
@Composable
private fun Geste(
    action: ReviewText.Action,
    selectionne: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Column(
        modifier = modifier
            // Le geste occupe toute la hauteur de la barre, décidée par le plus haut des cinq :
            // c'est le `stretch` du source.
            .fillMaxHeight()
            // Le `marginHorizontal: 2` du source. Il est posé en marge intérieure, et non en
            // espacement de la rangée : les deux bords extérieurs ont donc aussi leur 2, comme
            // dans le source. La répartition des cinq colonnes diffère alors de quelques
            // dixièmes de point — les marges du source sont retirées avant le partage, ici elles
            // sont comprises dedans — et cela ne se voit pas.
            .padding(horizontal = GESTE_MARGIN)
            .sizeIn(minWidth = GESTE_MIN_WIDTH, minHeight = GESTE_MIN_HEIGHT)
            .clip(RoundedCornerShape(GESTE_RADIUS))
            .background(if (selectionne) colors.green else colors.soft)
            .alpha(if (pressed) PRESSED_ALPHA else 1f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = ReviewText.actionLabel(action).replace('\n', ' '),
                onClick = onClick,
            )
            .semantics { selected = selectionne }
            .padding(horizontal = 1.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = action.icon(),
            contentDescription = null,
            tint = if (selectionne) Color.White else colors.green,
            modifier = Modifier.size(ICON_SIZE),
        )
        AppLabel(
            text = ReviewText.actionLabel(action),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = LABEL_GAP),
            color = if (selectionne) Color.White else colors.text,
            // La source pose 10 à la main. L'échelle du dépôt n'a pas de jeton à 10 : `metadata`
            // (11) est le plus proche, et l'inventaire des tailles est celui du client d'origine —
            // y ajouter un jeton serait sortir du vocabulaire qu'on a décidé de reprendre tel quel.
            // Même arbitrage que le titre du sélecteur de présentation.
            fontSize = AppTheme.typeScale.metadata,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            selectable = false,
            // La hauteur de ligne, elle, n'est pas un arbitrage : la source écrit 13 pour 10, et
            // c'est ce qui tient le libellé sur deux lignes **dans** les 64 points du geste. La
            // laisser au défaut ferait grandir les deux gestes coupés, et eux seuls.
            style = TextStyle(lineHeight = LABEL_LINE_HEIGHT),
        )
    }
}

/**
 * L'icône d'un geste.
 *
 * ## Trois correspondances sur cinq sont exactes, deux ne le sont pas
 *
 * Le source nomme ses icônes dans le vocabulaire de MaterialCommunityIcons — `check`, `signal`,
 * `refresh`, `play`, `microphone` — et Material Icons n'a pas les mêmes noms. Quatre se
 * correspondent directement : `Check`, `Refresh`, `PlayArrow` et `Mic`.
 *
 * **`signal` n'a pas d'équivalent exact.** Mesuré dans les deux paquets livrés : `Signal` n'existe
 * pas comme nom dans `material-icons-extended`, qui porte pourtant deux mille quatre-vingt-trois
 * icônes tracées. Les plus proches sont `SignalCellularAlt` et `NetworkCell` ; le premier est
 * retenu parce qu'il est le seul à porter les **trois barres** de force croissante du dessin
 * d'origine — `NetworkCell` n'en porte qu'une, et une barre unique se lirait comme un état
 * quelconque plutôt que comme « quelques hésitations ».
 *
 * La fonction est exhaustive et sans `else` : ajouter un geste à [ReviewText.Action] sans lui
 * donner d'icône ne compile pas, ce qui vaut mieux qu'un dessin par défaut que personne ne
 * remarque.
 */
private fun ReviewText.Action.icon(): ImageVector = when (this) {
    ReviewText.Action.PERFECT -> Icons.Outlined.Check
    ReviewText.Action.HESITANT -> Icons.Outlined.SignalCellularAlt
    ReviewText.Action.REWORK -> Icons.Outlined.Refresh
    ReviewText.Action.LISTEN -> Icons.Outlined.PlayArrow
    ReviewText.Action.RECORD -> Icons.Outlined.Mic
}

/** Le rayon de la barre. L'original pose 24. */
private val BAR_RADIUS = 24.dp

/** La marge intérieure de la barre. L'original pose 5. */
private val BAR_PADDING = 5.dp

/**
 * Le rang qui porte le trait de séparation, compté depuis zéro.
 *
 * Trois, donc : après « À retravailler », avant « Écouter ». Le nommer plutôt qu'écrire `3` dans
 * la boucle dit **ce qu'il sépare** — les trois notes des deux gestes d'écoute — et c'est ce qui
 * manquerait à la lecture d'un littéral.
 */
private const val SEPARATOR_BEFORE = 3

/** L'écart horizontal du trait. L'original pose 3. */
private val SEPARATOR_GAP = 3.dp

/** Son retrait vertical. L'original pose 10. */
private val SEPARATOR_INSET = 10.dp

/** Le rayon d'un geste. L'original pose 18. */
private val GESTE_RADIUS = 18.dp

/** Sa marge horizontale. L'original pose 2. */
private val GESTE_MARGIN = 2.dp

/**
 * Le plancher d'un geste : 44 de large, 64 de haut.
 *
 * Les deux chiffres sont ceux du source. Le plancher de largeur **ne mord jamais** sur un
 * téléphone : cinq colonnes se partagent la largeur, et la plus étroite en laisse bien plus que
 * 44 à chacune. Il est transcrit quand même — c'est un plancher d'accessibilité, et le retirer
 * serait décider à la place du propriétaire du projet qu'il ne sert à rien.
 */
private val GESTE_MIN_WIDTH = 44.dp

/** Voir [GESTE_MIN_WIDTH]. Celui-ci mord : c'est lui qui donne sa hauteur à la barre. */
private val GESTE_MIN_HEIGHT = 64.dp

/** La taille de l'icône. L'original pose 22. */
private val ICON_SIZE = 22.dp

/** L'écart entre l'icône et son mot. L'original pose 4. */
private val LABEL_GAP = 4.dp

/** La hauteur de ligne du mot. L'original pose 13 pour une police de 10. */
private val LABEL_LINE_HEIGHT = 13.sp
