package com.msoumaya.deepseekandroid.feature.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.msoumaya.deepseekandroid.core.data.LocalAppContainer
import com.msoumaya.deepseekandroid.core.design.component.AppButton
import com.msoumaya.deepseekandroid.core.design.component.AppCard
import com.msoumaya.deepseekandroid.core.design.component.AppField
import com.msoumaya.deepseekandroid.core.design.component.AppInlineIcon
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.component.AppTitle
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.SocialText

// ---------------------------------------------------------------------------
// Écran « Conversation »
// ---------------------------------------------------------------------------
// Portage de la conversation de `FriendsScreen` (`src/SocialScreens.tsx:168-208`).
//
// **L'écran ne calcule rien.** Ce qui décide — quel message est le mien, qui peut supprimer, quel
// état porte un rendez-vous, la page est-elle pleine — vit dans `core:domain` et s'éprouve
// là-bas ; ce qui met en forme vit dans `SocialText` ; ce qui choisit les blocs vit dans
// `ConversationRenderer`. Ici il n'y a que la disposition : quel bloc va où, et à quelle taille.
//
// **Le compositeur ne défile pas avec la discussion.** L'original le pose **hors** de sa
// `ScrollView`, dans une bande fixe en bas : c'est ce qui permet d'écrire en lisant un message
// ancien sans avoir à redescendre. La colonne défilante est donc celle du corps, et la bande du
// compositeur lui est sœur — d'où le `weight(1f)` sur la première.
//
// **Ce que l'écran ne fait pas encore, et pourquoi il le tait.** Deux capacités de l'original
// n'ont pas de matière ici, et chacune serait un bouton qui n'agit pas :
//
//  1. **l'écoute d'une récitation partagée** — elle demande une URL **signée** pour un fichier
//     déposé dans un espace privé, et un lecteur qui ne se confonde pas avec la séance en cours du
//     lecteur coranique. Le bloc affiche donc le passage et sa durée, sans bouton ;
//  2. **le direct** — « Écrit un message… » et l'arrivée d'un message sans rien toucher demandent
//     le canal temps réel. L'état de l'en-tête n'a donc jamais cette valeur.
//
// **Le bouton « 🏆 Défier », lui, n'est plus tu.** Il l'a été tant que l'écran Quiz n'existait
// pas ; il est là depuis que la route du Quiz porte l'ami à défier. Son destinataire — le compte de
// l'ami, `other.id` — vient de l'état, où les trois conditions de l'original sont déjà repliées :
// l'écran ne compose le bouton que si l'état lui donne quelqu'un à défier.
//
// **Un écart assumé, de défilement celui-là.** L'original ouvre la conversation **en haut** de la
// page chargée, c'est-à-dire sur le plus ancien des cinquante derniers messages : il faut faire
// défiler pour lire ce qui vient d'arriver. Ici, la position d'ouverture n'est pas forcée non
// plus — mais pour une autre raison : placer la vue sur le dernier message demande de connaître
// la hauteur mesurée du contenu, donc un essai sur un appareil. Reproduire la position de
// l'original en attendant est un défaut **connu**, pas un défaut caché.
// ---------------------------------------------------------------------------

/** Côté du médaillon de l'en-tête. Plus petit que la liste : ce n'est pas une ligne de liste. */
private val HEADER_AVATAR = 44.dp

/**
 * Retrait d'un message selon son auteur (`marginLeft/Right: 48` à la source).
 *
 * C'est ce qui détache mes messages des autres : ils ne sont pas du même côté. Les deux
 * propriétés sont nommées `start`/`end` et non `left`/`right` — le texte coranique impose le sens
 * de lecture inversé à certains blocs, et une marge « gauche » y basculerait du mauvais côté.
 */
private val MESSAGE_INSET = 48.dp

/** Longueur maximale d'un message (`maxLength={2000}` à la source). */
private const val MESSAGE_MAX = 2000

/**
 * La conversation ouverte.
 *
 * Construit son propre `ViewModel` depuis le conteneur applicatif, comme l'écran des amis : c'est
 * ce qui lui permet d'être appelée sans rien recevoir. Elle est `internal` parce qu'elle n'est pas
 * une destination de navigation — c'est `SocialScreen` qui la compose à la place de la liste.
 */
@Composable
internal fun ConversationSection(
    modifier: Modifier = Modifier,
    /**
     * Ouverture du Quiz sur l'ami de cette conversation.
     *
     * **Le rappel, et non la navigation.** Cet écran n'est pas une destination : c'est
     * `SocialScreen` qui le compose, et c'est la route qui sait où l'on va. Le rappel porte
     * l'identifiant **du compte de l'ami** — `other.id` —, celui que le Quiz attend pour créer un
     * défi ; il vient de l'état, où les trois conditions de l'original sont déjà repliées.
     */
    onChallenge: (String) -> Unit = {},
    viewModel: ConversationViewModel = viewModel(
        factory = ConversationViewModel.factory(LocalAppContainer.current),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // La pièce est déjà ouverte — c'est la liste qui l'a ouverte —, mais le `ViewModel` ne sait
    // pas quand elle arrive à l'écran : c'est la seule chose que l'écran lui apprend. Le rappel
    // est sans effet pendant une lecture, donc l'ouverture immédiate ne coûte pas une seconde
    // requête.
    LaunchedEffect(Unit) { viewModel.onVisible() }

    ConversationContent(
        state = state,
        modifier = modifier,
        onChallenge = onChallenge,
        onClose = viewModel::onClose,
        onToggleTools = viewModel::onToggleTools,
        onDraftChange = viewModel::onDraftChange,
        onSend = viewModel::onSend,
        onShareProgress = viewModel::onShareProgress,
        onLoadOlder = viewModel::onLoadOlder,
        onDeleteMessage = viewModel::onDeleteMessage,
        onReport = viewModel::onReport,
        onReportDismiss = viewModel::onReportDismiss,
        onSendReport = viewModel::onSendReport,
        onReportReasonChange = viewModel::onReportReasonChange,
        onGoalTargetChange = viewModel::onGoalTargetChange,
        onProposeGoal = viewModel::onProposeGoal,
        onAcceptGoal = viewModel::onAcceptGoal,
        onAppointmentTextChange = viewModel::onAppointmentTextChange,
        onProposeAppointment = viewModel::onProposeAppointment,
        onAcceptAppointment = viewModel::onAcceptAppointment,
        onCancelAppointment = viewModel::onCancelAppointment,
        onJoinGroup = viewModel::onJoinGroup,
        onDeclineGroupInvite = viewModel::onDeclineGroupInvite,
        onInviteFriend = viewModel::onInviteFriend,
        onToggleModerator = viewModel::onToggleModerator,
        onRemoveMember = viewModel::onRemoveMember,
        onDeleteGroup = viewModel::onDeleteGroup,
    )
}

/**
 * La conversation, à partir d'un état déjà résolu.
 *
 * Séparée de [ConversationSection] pour la même raison qu'au Progrès et à la liste d'amis :
 * l'état se fabrique à la main dans une prévisualisation, sans conteneur, sans réseau et sans
 * compte.
 */
@Composable
internal fun ConversationContent(
    state: ConversationUiState,
    modifier: Modifier = Modifier,
    /**
     * Ouverture du Quiz sur l'ami de cette conversation, porté par l'état.
     *
     * Le rappel descend jusqu'au corps, qui affiche le bouton : c'est la même valeur que
     * [ConversationUiState.challengeFriendId], donc l'écran n'a pas à décider si le bouton a lieu
     * d'être — il ne le compose que si l'état lui donne un destinataire.
     */
    onChallenge: (String) -> Unit = {},
    onClose: () -> Unit = {},
    onToggleTools: () -> Unit = {},
    onDraftChange: (String) -> Unit = {},
    onSend: () -> Unit = {},
    onShareProgress: () -> Unit = {},
    onLoadOlder: () -> Unit = {},
    onDeleteMessage: (String) -> Unit = {},
    onReport: () -> Unit = {},
    onReportDismiss: () -> Unit = {},
    onSendReport: () -> Unit = {},
    onReportReasonChange: (String) -> Unit = {},
    onGoalTargetChange: (String) -> Unit = {},
    onProposeGoal: () -> Unit = {},
    onAcceptGoal: (String) -> Unit = {},
    onAppointmentTextChange: (String) -> Unit = {},
    onProposeAppointment: () -> Unit = {},
    onAcceptAppointment: (String) -> Unit = {},
    onCancelAppointment: (String) -> Unit = {},
    onJoinGroup: () -> Unit = {},
    onDeclineGroupInvite: () -> Unit = {},
    onInviteFriend: (String) -> Unit = {},
    onToggleModerator: (String, Boolean) -> Unit = { _, _ -> },
    onRemoveMember: (String) -> Unit = {},
    onDeleteGroup: () -> Unit = {},
) {
    // La confirmation de suppression du cercle. **Non sauvegardée** : une rotation referme la
    // boîte, et rien n'est perdu — alors que la sauvegarder demanderait un `Saver` pour un
    // aiguillage dont la seule raison d'être est d'être à l'écran pendant qu'on le regarde. Même
    // raisonnement que le menu d'une amitié, dans la liste.
    var confirmDelete by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.padding(horizontal = SCREEN_PADDING)) {
                // « ← Mes amis ». Le retour est **le** geste de fermeture : il n'y a pas de barre
                // de navigation propre à la conversation, et le bouton du système fait sortir de
                // l'onglet, pas de la pièce.
                AppButton(
                    text = SocialText.BACK_TO_FRIENDS,
                    secondary = true,
                    small = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onClose,
                )

                ConversationHeader(state = state, onReport = onReport)

                // Le bandeau, **sous l'en-tête**, dans l'ordre de l'original : retour, en-tête,
                // avis, corps. C'est l'ordre inverse de la liste d'amis, où l'avis passe avant la
                // bande d'en-tête — et c'est voulu dans les deux cas.
                state.notice?.let { message ->
                    AppCard(modifier = Modifier.padding(top = AppTheme.spacing.sm)) {
                        AppLabel(text = message, selectable = false)
                    }
                }

                when {
                    state.failure != null -> ConversationFailure(message = state.failure)

                    state.loading -> ConversationInfoCard(message = SocialText.LOADING_MESSAGES)

                    else -> ConversationBody(
                        state = state,
                        onChallenge = onChallenge,
                        onToggleTools = onToggleTools,
                        onLoadOlder = onLoadOlder,
                        onDeleteMessage = onDeleteMessage,
                        onReportDismiss = onReportDismiss,
                        onSendReport = onSendReport,
                        onReportReasonChange = onReportReasonChange,
                        onGoalTargetChange = onGoalTargetChange,
                        onProposeGoal = onProposeGoal,
                        onAcceptGoal = onAcceptGoal,
                        onAppointmentTextChange = onAppointmentTextChange,
                        onProposeAppointment = onProposeAppointment,
                        onAcceptAppointment = onAcceptAppointment,
                        onCancelAppointment = onCancelAppointment,
                        onJoinGroup = onJoinGroup,
                        onDeclineGroupInvite = onDeclineGroupInvite,
                        onInviteFriend = onInviteFriend,
                        onToggleModerator = onToggleModerator,
                        onRemoveMember = onRemoveMember,
                        onAskDeleteGroup = { confirmDelete = true },
                    )
                }
            }
        }

        Composer(
            draft = state.draft,
            canSend = state.canSend,
            canShareProgress = state.canShareProgress,
            onDraftChange = onDraftChange,
            onSend = onSend,
            onShareProgress = onShareProgress,
        )
    }

    if (confirmDelete) {
        DeleteGroupDialog(
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDeleteGroup()
            },
        )
    }
}

// ---------------------------------------------------------------------------
// En-tête : nom, état, signalement
// ---------------------------------------------------------------------------

/**
 * L'en-tête de la pièce ouverte.
 *
 * **Deux formes, et la différence est celle de l'original.** Un **tête-à-tête** porte un
 * médaillon, le nom, le geste de signalement et une ligne d'état ; un **cercle** ne porte que son
 * nom — il n'y a pas de présence à afficher pour un groupe, et `statusLabel` vaut alors `null`.
 *
 * Le médaillon est celui de la liste d'amis, en plus petit et **sans pastille de présence** :
 * l'état est écrit en toutes lettres juste en dessous, et un point de couleur le dirait deux
 * fois.
 */
@Composable
private fun ConversationHeader(state: ConversationUiState, onReport: () -> Unit) {
    val colors = AppTheme.colors

    if (state.statusLabel == null) {
        AppTitle(
            text = state.title,
            modifier = Modifier.padding(top = AppTheme.spacing.sm),
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FriendMedallion(name = state.title, size = HEADER_AVATAR)

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // `fill = false` : le titre prend la place qu'il lui faut, et « Signaler » se pose
                // juste après lui — c'est la disposition de l'original. Un titre long est coupé
                // plutôt que de pousser le geste hors de la ligne.
                AppTitle(text = state.title, modifier = Modifier.weight(1f, fill = false))

                if (state.canReport) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 44.dp)
                            .clickable(role = Role.Button, onClick = onReport)
                            .semantics {
                                contentDescription = SocialText.reportConversationWith(state.title)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppLabel(
                            text = SocialText.REPORT,
                            selectable = false,
                            color = colors.green,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            AppLabel(
                text = state.statusLabel,
                selectable = false,
                fontSize = 12.sp,
                color = colors.muted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Les deux états sans discussion
// ---------------------------------------------------------------------------

/**
 * Panne de la lecture des messages.
 *
 * Un écran distinct d'une discussion vide, et c'est tout l'intérêt : une discussion vide se lit
 * « personne ne t'a jamais écrit », qui est une affirmation fausse sur un tiers quand le réseau a
 * échoué. C'est la même distinction que la panne de la liste d'amis.
 */
@Composable
private fun ConversationFailure(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = AppTheme.spacing.section),
    ) {
        AppTitle(text = SocialText.DISCUSSION)
        AppLabel(
            text = message,
            selectable = false,
            color = AppTheme.colors.muted,
            modifier = Modifier.padding(top = AppTheme.spacing.sm),
        )
    }
}

/** Carte d'information neutre : la première lecture des messages. */
@Composable
private fun ConversationInfoCard(message: String) {
    AppCard(modifier = Modifier.padding(top = AppTheme.spacing.sm)) {
        AppLabel(text = message, selectable = false)
    }
}

// ---------------------------------------------------------------------------
// Le corps : outils, membres, discussion, signalement
// ---------------------------------------------------------------------------

@Composable
private fun ConversationBody(
    state: ConversationUiState,
    onToggleTools: () -> Unit,
    onLoadOlder: () -> Unit,
    onDeleteMessage: (String) -> Unit,
    onReportDismiss: () -> Unit,
    onSendReport: () -> Unit,
    onReportReasonChange: (String) -> Unit,
    onGoalTargetChange: (String) -> Unit,
    onProposeGoal: () -> Unit,
    onAcceptGoal: (String) -> Unit,
    onAppointmentTextChange: (String) -> Unit,
    onProposeAppointment: () -> Unit,
    onAcceptAppointment: (String) -> Unit,
    onCancelAppointment: (String) -> Unit,
    onJoinGroup: () -> Unit,
    onDeclineGroupInvite: () -> Unit,
    onInviteFriend: (String) -> Unit,
    onToggleModerator: (String, Boolean) -> Unit,
    onRemoveMember: (String) -> Unit,
    onAskDeleteGroup: () -> Unit,
    onChallenge: (String) -> Unit,
) {
    // Le dépliant « Profil et entraide ». Il n'existe pas dans le cercle de l'administration :
    // c'est `showTools` qui le dit, et il vient du domaine.
    if (state.showTools) {
        AppButton(
            text = if (state.toolsOpen) SocialText.TOOLS_CLOSE else SocialText.TOOLS,
            secondary = true,
            small = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppTheme.spacing.sm),
            onClick = onToggleTools,
        )
    }

    // « 🏆 Défier », juste après le dépliant — c'est la place de l'original, où les deux boutons
    // se suivent. La garde d'existence est **la même** que la sienne (`!adminContact`), mais elle
    // n'est pas réécrite ici : elle est déjà repliée dans `challengeFriendId`, qui vaut `null`
    // pour le cercle de l'administration, pour un lien qui n'est pas une amitié, et pour une
    // amitié dont le compte d'en face est inconnu. Le bouton ne peut donc pas naître sans
    // quelqu'un à défier.
    state.challengeFriendId?.let { friendId ->
        AppButton(
            text = SocialText.CHALLENGE,
            secondary = true,
            small = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppTheme.spacing.sm),
            onClick = { onChallenge(friendId) },
        )
    }

    state.overview?.let { card ->
        AppCard(modifier = Modifier.padding(top = AppTheme.spacing.sm)) {
            AppLabel(text = card.title, selectable = false, fontWeight = FontWeight.Bold)
            card.goalLine?.let { AppLabel(text = it, selectable = false) }
            card.weekLine?.let { AppLabel(text = it, selectable = false) }
            card.privateNote?.let {
                AppLabel(text = it, selectable = false, color = AppTheme.colors.muted)
            }
            card.passageLine?.let { AppLabel(text = it, selectable = false) }
        }
    }

    state.goals?.let { section ->
        SectionHeading(text = SocialText.SHARED_GOAL_TITLE)
        AppLabel(
            text = SocialText.SHARED_GOAL_HINT,
            selectable = false,
            color = AppTheme.colors.muted,
            fontSize = 13.sp,
        )
        AppField(
            value = section.target,
            onValueChange = onGoalTargetChange,
            placeholder = SocialText.GOAL_PLACEHOLDER,
            // Le pavé numérique de l'original. La saisie est quand même vérifiée au domaine : le
            // clavier restreint ce qu'on peut taper, il ne décide pas de ce qui est valide.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        AppButton(
            text = SocialText.PROPOSE_GOAL,
            secondary = true,
            enabled = section.canPropose,
            modifier = Modifier.fillMaxWidth(),
            onClick = onProposeGoal,
        )
        section.rows.forEach { row ->
            AppCard {
                AppLabel(text = row.label, selectable = false)
                AppLabel(
                    text = row.state,
                    selectable = false,
                    fontSize = 12.sp,
                    color = AppTheme.colors.muted,
                )
                if (row.canAccept) {
                    AppButton(
                        text = SocialText.ACCEPT,
                        small = true,
                        onClick = { onAcceptGoal(row.id) },
                    )
                }
            }
        }
    }

    state.appointments?.let { section ->
        SectionHeading(text = SocialText.APPOINTMENT_TITLE)
        AppField(
            value = section.text,
            onValueChange = onAppointmentTextChange,
            placeholder = SocialText.APPOINTMENT_PLACEHOLDER,
        )
        // **Pas de bouton grisé pendant la frappe.** La saisie est vérifiée au moment du clic, et
        // un rendez-vous mal formé se refuse par un message qui **explique le format**. Un bouton
        // grisé ne dirait pas pourquoi il l'est — c'est le choix de l'original, et le domaine le
        // porte : `AppointmentSection` n'a pas de `canPropose`.
        AppButton(
            text = SocialText.PROPOSE_APPOINTMENT,
            secondary = true,
            modifier = Modifier.fillMaxWidth(),
            onClick = onProposeAppointment,
        )
        section.rows.forEach { row ->
            AppCard {
                AppLabel(text = row.label, selectable = false)
                AppLabel(
                    text = row.state,
                    selectable = false,
                    fontSize = 12.sp,
                    color = AppTheme.colors.muted,
                )
                if (row.canAccept) {
                    AppButton(
                        text = SocialText.ACCEPT,
                        small = true,
                        onClick = { onAcceptAppointment(row.id) },
                    )
                }
                // « Annuler » est **toujours** offert, y compris sur un rendez-vous que j'ai
                // proposé : c'est l'original, et c'est ce qui permet de se raviser.
                AppButton(
                    text = SocialText.CANCEL,
                    secondary = true,
                    small = true,
                    onClick = { onCancelAppointment(row.id) },
                )
            }
        }
    }

    state.members?.let { card ->
        AppCard {
            AppLabel(text = card.title, selectable = false, fontWeight = FontWeight.Bold)
            card.rows.forEach { member ->
                AppLabel(text = member.label, selectable = false)
                if (member.canJoin) {
                    AppButton(
                        text = SocialText.JOIN,
                        small = true,
                        onClick = onJoinGroup,
                    )
                }
                if (member.canDecline) {
                    AppButton(
                        text = SocialText.DECLINE,
                        secondary = true,
                        small = true,
                        onClick = onDeclineGroupInvite,
                    )
                }
                member.moderatorLabel?.let { label ->
                    // Le **sens** du geste est porté par la ligne, et non déduit de son libellé :
                    // comparer deux chaînes pour savoir s'il faut donner ou retirer la modération
                    // marcherait jusqu'au jour où l'une des deux change de mot.
                    AppButton(
                        text = label,
                        secondary = true,
                        small = true,
                        onClick = { onToggleModerator(member.userId, member.grantsModerator) },
                    )
                }
                if (member.canRemove) {
                    AppButton(
                        text = SocialText.REMOVE_FROM_GROUP,
                        secondary = true,
                        small = true,
                        onClick = { onRemoveMember(member.userId) },
                    )
                }
            }
        }

        // Les invitations sont **hors** de la carte des membres, comme dans l'original : elles ne
        // décrivent pas le cercle, elles proposent de l'agrandir.
        card.invites.forEach { invite ->
            AppButton(
                text = invite.label,
                secondary = true,
                small = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onInviteFriend(invite.userId) },
            )
        }

        if (card.canDeleteGroup) {
            AppButton(
                text = SocialText.DELETE_GROUP,
                secondary = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = onAskDeleteGroup,
            )
        }
    }

    SectionHeading(text = SocialText.DISCUSSION)

    state.olderLabel?.let { label ->
        AppButton(
            text = label,
            secondary = true,
            small = true,
            enabled = state.canLoadOlder,
            modifier = Modifier.fillMaxWidth(),
            onClick = onLoadOlder,
        )
    }

    state.suspension?.let { message ->
        AppCard(background = AppTheme.colors.soft) {
            AppLabel(text = message, selectable = false, color = AppTheme.colors.red)
        }
    }

    state.messages.forEach { row ->
        MessageCard(row = row, onDelete = { onDeleteMessage(row.id) })
    }

    state.report?.let { form ->
        ReportForm(
            form = form,
            onReasonChange = onReportReasonChange,
            onSend = onSendReport,
            onDismiss = onReportDismiss,
        )
    }
}

// ---------------------------------------------------------------------------
// Un message
// ---------------------------------------------------------------------------

/**
 * Un message, du côté de son auteur.
 *
 * **Le côté est porté par les marges, pas par l'alignement du texte.** L'original fait exactement
 * cela : mes messages sont détachés du bord gauche, ceux des autres du bord droit, et le texte
 * reste aligné au début dans les deux cas. Centrer mes messages serait plus lisible, mais
 * couperait les longues phrases en drapeau, ce que la source évite.
 */
@Composable
private fun MessageCard(row: MessageRow, onDelete: () -> Unit) {
    val colors = AppTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (row.mine) MESSAGE_INSET else 0.dp,
                end = if (row.mine) 0.dp else MESSAGE_INSET,
            ),
    ) {
        AppCard(background = if (row.mine) colors.soft else colors.paper) {
            AppLabel(
                text = row.stamp,
                selectable = false,
                fontSize = 11.sp,
                color = colors.muted,
            )

            AppLabel(
                text = row.body,
                selectable = false,
                modifier = Modifier.padding(vertical = 7.dp),
            )

            row.recitation?.let { RecitationBlock(row = it) }

            // L'accusé de lecture, à droite sous le message. Il n'existe que sur les miens et dans
            // un tête-à-tête : dans un cercle, « lu par qui ? » n'a pas de réponse.
            row.receipt?.let { receipt ->
                AppLabel(
                    text = receipt,
                    selectable = false,
                    fontSize = 10.sp,
                    color = colors.muted,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }

            if (row.canDelete) {
                AppButton(
                    text = SocialText.DELETE,
                    secondary = true,
                    small = true,
                    onClick = onDelete,
                )
            }
        }
    }
}

/**
 * Bloc d'une récitation partagée : le passage, sa durée, et **rien à écouter**.
 *
 * L'original y pose un bouton « ▶ Écouter la récitation », désactivé quand l'enregistrement
 * manque. Ici le bloc n'a pas de bouton du tout, et la raison est en tête de fichier : il faudrait
 * une URL signée pour un fichier d'espace privé, et un lecteur distinct de la séance coranique. Un
 * bouton qui ne joue rien est pire qu'un bouton absent.
 */
@Composable
private fun RecitationBlock(row: RecitationRow) {
    val colors = AppTheme.colors

    AppCard(background = colors.paper, padding = AppTheme.spacing.sm) {
        AppLabel(text = row.reference, selectable = false, fontWeight = FontWeight.Bold)
        row.durationLabel?.let {
            AppLabel(text = it, selectable = false, fontSize = 12.sp, color = colors.muted)
        }
    }
}

// ---------------------------------------------------------------------------
// Signalement
// ---------------------------------------------------------------------------

@Composable
private fun ReportForm(
    form: ReportForm,
    onReasonChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppCard(modifier = Modifier.padding(top = AppTheme.spacing.sm)) {
        AppLabel(text = SocialText.REPORT_TITLE, selectable = false)
        AppField(
            value = form.reason,
            onValueChange = onReasonChange,
            placeholder = SocialText.REPORT_PLACEHOLDER,
        )
        AppButton(
            text = SocialText.SEND,
            small = true,
            enabled = form.canSend,
            onClick = onSend,
        )
        AppButton(
            text = SocialText.CANCEL,
            secondary = true,
            small = true,
            onClick = onDismiss,
        )
    }
}

// ---------------------------------------------------------------------------
// Confirmation de suppression d'un cercle
// ---------------------------------------------------------------------------

/**
 * Confirmation de la suppression d'un cercle.
 *
 * `AlertDialog` Material, comme la gestion d'une amitié : la boîte native du client d'origine
 * (`Alert.alert`) a exactement cette forme. La suppression est **définitive** — les messages du
 * cercle partent avec lui —, donc elle demande confirmation, et le corps de la boîte le dit.
 */
@Composable
private fun DeleteGroupDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            AppLabel(
                text = SocialText.DELETE_GROUP_TITLE,
                fontSize = AppTheme.typeScale.card,
                fontWeight = FontWeight.Bold,
                selectable = false,
            )
        },
        text = {
            AppLabel(text = SocialText.DELETE_GROUP_BODY, selectable = false)
        },
        confirmButton = {
            AppButton(
                text = SocialText.DELETE,
                small = true,
                onClick = onConfirm,
            )
        },
        dismissButton = {
            AppButton(
                text = SocialText.CANCEL,
                secondary = true,
                small = true,
                onClick = onDismiss,
            )
        },
        containerColor = AppTheme.colors.paper,
    )
}

// ---------------------------------------------------------------------------
// Le compositeur
// ---------------------------------------------------------------------------

/**
 * La bande d'écriture, fixée sous la discussion.
 *
 * **Elle ne défile pas.** C'est la structure de l'original, où le champ est hors de la
 * `ScrollView` : on peut écrire en lisant un message ancien sans avoir à redescendre.
 *
 * **Le bouton de partage est conditionné par l'état, pas par le clavier.** L'original le masque
 * aussi quand le clavier est ouvert ; c'est une décision de **disposition**, et l'écran est le
 * seul à savoir si le clavier est là. Elle n'est pas prise ici : mesurer le clavier demande un
 * appareil, et un bouton qu'on ne peut pas éprouver vaut mieux visible que caché par erreur.
 */
@Composable
private fun Composer(
    draft: String,
    canSend: Boolean,
    canShareProgress: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onShareProgress: () -> Unit,
) {
    val colors = AppTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.cream),
    ) {
        // L'original pose une bordure **supérieure** seule. Compose n'a pas de bordure partielle :
        // un trait d'un pixel au-dessus rend le même séparateur. Même procédé que le pied de la
        // liste d'amis.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.line),
        )

        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    AppField(
                        value = draft,
                        onValueChange = onDraftChange,
                        placeholder = SocialText.COMPOSER,
                        multiline = true,
                        // `maxHeight:110` à la source, pour une ligne d'environ 20 px : cinq
                        // lignes, puis le champ défile au lieu de manger la discussion.
                        maxLines = 5,
                        maxLength = MESSAGE_MAX,
                    )
                }

                AppButton(
                    text = SocialText.SEND,
                    small = true,
                    enabled = canSend,
                    onClick = onSend,
                )
            }

            if (canShareProgress) {
                AppButton(
                    text = SocialText.SHARE_STEP,
                    secondary = true,
                    small = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    onClick = onShareProgress,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Le bouton « Message » d'une ligne d'ami
// ---------------------------------------------------------------------------

/**
 * Le bouton « Message » d'une ligne d'ami, dans la liste.
 *
 * **Pourquoi ce n'est pas un `AppButton`.** L'original en fait un pavé à fond doux, sans bordure,
 * qui porte une icône **et** un mot — une forme que `AppButton` ne rend pas, lui qui n'écrit qu'un
 * libellé. Le composer ici garde les deux, et garde la cible tactile de 44 px que l'original
 * demande.
 *
 * **Le compte de non-lus n'est pas dedans**, contrairement à l'original : il est posé sur le
 * médaillon de la ligne. C'est l'écart déclaré en tête de `SocialScreen.kt`.
 */
@Composable
internal fun MessageButton(name: String, onClick: () -> Unit) {
    val colors = AppTheme.colors

    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.soft)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = SocialText.messageTo(name) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppInlineIcon(icon = Icons.Outlined.ChatBubbleOutline, tint = colors.green, size = 18.dp)
            AppLabel(
                text = SocialText.MESSAGE,
                selectable = false,
                color = colors.green,
                fontSize = 10.sp,
            )
        }
    }
}

/** Le bouton d'ouverture d'un cercle, depuis la liste. */
