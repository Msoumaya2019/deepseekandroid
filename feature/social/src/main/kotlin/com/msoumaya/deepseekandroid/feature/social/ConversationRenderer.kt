package com.msoumaya.deepseekandroid.feature.social

import com.msoumaya.deepseekandroid.core.data.repository.RoomState
import com.msoumaya.deepseekandroid.core.data.repository.SocialState
import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.Social
import com.msoumaya.deepseekandroid.core.domain.SocialText
import com.msoumaya.deepseekandroid.core.model.ChatMessage
import com.msoumaya.deepseekandroid.core.model.ChatMessageKind
import com.msoumaya.deepseekandroid.core.model.FriendLink
import com.msoumaya.deepseekandroid.core.model.FriendLinkStatus
import com.msoumaya.deepseekandroid.core.model.FriendOverview
import com.msoumaya.deepseekandroid.core.model.GroupMember
import com.msoumaya.deepseekandroid.core.model.GroupRole
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.ReviewAppointment
import com.msoumaya.deepseekandroid.core.model.SharedGoal

// ---------------------------------------------------------------------------
// Rendu de l'écran « Conversation »
// ---------------------------------------------------------------------------
// Portage de la conversation de `FriendsScreen` (`src/SocialScreens.tsx:168-208`).
//
// **Pourquoi un objet à part, et pur.** Dans l'original, la conversation et la liste d'amis
// vivent dans **le même composant** : `selected` bascule de l'une à l'autre, et l'en-tête, les
// cartes d'outils, la discussion et le compositeur sont écrits à la suite du même `return`.
// Reproduire cette forme en Compose aurait donné un composable de six cents lignes où le moindre
// libellé ne s'éprouve qu'avec un appareil.
//
// Ici, ce qui **décide** vit dans `core:domain` — qui peut supprimer un message, quel est l'état
// d'un rendez-vous, la page est-elle pleine —, ce qui **met en forme** vit dans `SocialText`, et
// ce qui **choisit les blocs** vit ici. Les trois s'éprouvent sans appareil.
//
// **Ce que ce fichier ne fait pas.** Il ne touche ni au réseau, ni à l'horloge : l'instant
// courant lui est **passé**, comme au rendu de la liste d'amis.
//
// **Ce qui n'est pas porté, faute de matière.** Le direct — « Écrit un message… », l'arrivée
// d'un message sans relecture — demande le canal temps réel, qui n'existe pas encore ici :
// `otherTyping` est donc toujours faux, et le libellé correspondant reste inatteignable.
// L'**écoute** d'une récitation partagée demande une URL signée pour un fichier déposé dans un
// espace privé, et un lecteur qui ne se confonde pas avec la séance en cours du lecteur
// coranique : le bloc affiche donc le passage et sa durée, sans bouton. Le bouton « 🏆 Défier »
// n'est pas porté non plus : il mène à l'écran Quiz, qui n'existe pas.
// ---------------------------------------------------------------------------

/**
 * Traduit l'état du dépôt en état affichable pour la conversation.
 *
 * **Un état fermé est rendu dans deux cas, et pas un de plus** : aucune pièce n'est ouverte, ou
 * le profil n'est pas encore lu. Le second compte : sans identifiant de compte, on ne sait pas
 * quel message est « le mien », donc ni le côté, ni la couleur, ni l'accusé de lecture, ni les
 * droits de modération ne sont calculables. Afficher la pièce quand même montrerait les messages
 * de tout le monde du même côté, et attribuerait à chacun les droits de tous — un écran qui ment
 * vaut moins qu'un écran qui attend.
 */
internal object ConversationRenderer {

    /**
     * @param nowIso instant courant, injecté. Il sert à deux choses : dire si la messagerie est
     *   suspendue, et si un rendez-vous est encore à venir. Un test qui dépendrait de l'horloge
     *   changerait de verdict au fil des jours.
     */
    fun render(
        state: SocialState,
        inputs: ConversationInputs,
        nowIso: String = Dates.nowIso(),
    ): ConversationUiState {
        val room = state.room ?: return ConversationUiState()
        val me = state.profile?.id ?: return ConversationUiState()

        val open = room.room
        val isLink = open.linkId != null
        val adminContact = Social.isAdminContact(state.groups, open.groupId)
        val link = open.linkId?.let { id -> state.links.firstOrNull { it.id == id } }
        val suspended = Social.isSuspended(state.suspension, nowIso)

        // Le bloc « Profil et entraide » n'existe pas dans le cercle de l'administration : ni
        // aperçu d'un ami — il n'y en a pas —, ni objectifs, ni rendez-vous, ni membres.
        val showTools = Social.showsFriendTools(adminContact)
        val toolsOpen = showTools && inputs.toolsOpen

        return ConversationUiState(
            open = true,
            loading = room.loading,
            failure = room.failure,
            // L'avis du dépôt suit la pièce : c'est ici qu'il est **lisible**, la liste d'amis
            // ayant quitté l'écran. Le détail des messages concernés est dans `ConversationUiState`.
            notice = state.notice,

            title = when {
                isLink -> link?.other?.displayName ?: SocialText.FRIEND
                // **Le nom du serveur est écrasé, comme dans l'original.** Là-bas, le titre
                // venait du clic — `name:'Administration'` — et non de la liste : le serveur, lui,
                // nomme ce cercle « Contact · <nom> », et l'afficher tel quel ferait diverger les
                // deux clients sur le même écran.
                adminContact -> SocialText.ADMIN_CIRCLE_NAME
                else -> state.groups.firstOrNull { it.id == open.groupId }?.name ?: SocialText.CIRCLE
            },
            statusLabel = if (!isLink) {
                null
            } else {
                // `adminContact` est **toujours faux** dans cette branche : le cercle de
                // l'administration est un groupe, et l'original place cette ligne dans la branche
                // du lien. Le paramètre est passé quand même, parce que la règle est partagée et
                // qu'un futur appelant pourrait l'appeler ailleurs — le figer ici recopierait
                // dans l'écran une contrainte qui appartient au domaine.
                Social.statusLine(
                    otherTyping = false,
                    adminContact = adminContact,
                    isOnline = room.overview?.isOnline,
                )
            },
            canReport = isLink,

            showTools = showTools,
            toolsOpen = toolsOpen,
            overview = if (toolsOpen) overviewCard(room.overview) else null,
            goals = if (toolsOpen && isLink) goalSection(room.goals, inputs, me, state.busy) else null,
            appointments = if (toolsOpen && isLink) appointmentSection(room.appointments, inputs, me) else null,
            members = if (toolsOpen && !isLink) membersCard(room.members, state.links, me) else null,

            olderLabel = when {
                !room.history.hasOlder -> null
                room.loadingOlder -> SocialText.LOADING_OLDER
                else -> SocialText.LOAD_OLDER
            },
            canLoadOlder = room.history.hasOlder && !room.loadingOlder,
            suspension = state.suspension
                ?.takeIf { suspended }
                ?.let { SocialText.suspended(it.reason) },
            messages = room.messages.map { messageRow(it, me, isLink, room, state.links) },
            report = inputs.reportTarget?.let {
                ReportForm(
                    targetId = it,
                    reason = inputs.reportReason,
                    // **Écart assumé.** L'original n'ajoute pas `busy` ici, et un second appui
                    // pendant l'envoi dépose donc **deux** signalements. Le reste de l'écran
                    // porte la garde partout ailleurs ; la reproduire ici aurait recopié une
                    // négligence que le serveur paie en données dupliquées.
                    canSend = Social.validReportReason(inputs.reportReason) && !state.busy,
                )
            },

            draft = inputs.draft,
            canSend = Social.canSendMessage(inputs.draft, state.busy, suspended),
            // **Le clavier n'entre pas dans ce calcul.** L'original masque ce bouton quand le
            // clavier est ouvert ; c'est une décision de **disposition**, que l'écran prend seul
            // — il est le seul à savoir si le clavier est là.
            canShareProgress = Social.canShareProgress(isLink, adminContact) && !state.busy,
        )
    }

    // ------------------------------------------------------------------ cartes d'outils

    /**
     * Carte d'aperçu de l'ami, ou `null` s'il ne partage rien.
     *
     * L'original écrit **soit** l'objectif et la semaine, **soit** « Progression privée ». Les
     * deux champs sont donc liés : dès qu'un objectif est renseigné, la mention de vie privée
     * disparaît, et réciproquement.
     */
    private fun overviewCard(overview: FriendOverview?): OverviewCard? {
        if (overview == null) return null
        val goal = overview.goalLabel
        val start = overview.currentStart
        val end = overview.currentEnd
        return OverviewCard(
            title = SocialText.friendLine(overview.displayName, overview.isOnline),
            goalLine = goal?.let { SocialText.goalReached(it, overview.goalPercent) },
            weekLine = if (goal == null) {
                null
            } else {
                SocialText.weekStats(overview.weeklyVerses, overview.weeklySessions)
            },
            privateNote = if (goal == null) SocialText.PRIVATE_PROGRESS else null,
            passageLine = if (start == null || end == null) {
                null
            } else {
                referenceOf(start, end)?.let { SocialText.currentPassage(it) }
            },
        )
    }

    /**
     * Section « Objectif partagé ».
     *
     * La garde `busy` est ajoutée ici comme au signalement, et pour la même raison : l'original
     * laisse le bouton actif pendant un envoi, donc deux appuis proposent deux objectifs pour la
     * même semaine.
     */
    private fun goalSection(
        goals: List<SharedGoal>,
        inputs: ConversationInputs,
        me: String,
        busy: Boolean,
    ): GoalSection = GoalSection(
        target = inputs.goalTarget,
        canPropose = Social.targetSessions(inputs.goalTarget) != null && !busy,
        rows = goals.map {
            GoalRow(
                id = it.id,
                label = SocialText.weekGoal(it.weekStart, it.targetSessions),
                state = if (it.acceptedAt != null) SocialText.GOAL_ACCEPTED else SocialText.GOAL_PENDING,
                canAccept = Social.canAcceptProposal(it.acceptedAt, it.proposedBy, me),
            )
        },
    )

    /**
     * Section « Rendez-vous de révision ».
     *
     * Le libellé d'un rendez-vous retombe sur l'instant **brut** quand il est illisible, là où
     * l'original écrit « Invalid Date ». Un instant brut reste copiable et vérifiable ; un
     * message d'erreur ne dit rien de la donnée.
     */
    private fun appointmentSection(
        appointments: List<ReviewAppointment>,
        inputs: ConversationInputs,
        me: String,
    ): AppointmentSection = AppointmentSection(
        text = inputs.appointmentText,
        rows = appointments.map {
            AppointmentRow(
                id = it.id,
                label = SocialText.appointmentStamp(it.startsAt) ?: it.startsAt,
                state = if (it.acceptedAt != null) {
                    SocialText.APPOINTMENT_CONFIRMED
                } else {
                    SocialText.APPOINTMENT_PENDING
                },
                canAccept = Social.canAcceptProposal(it.acceptedAt, it.proposedBy, me),
            )
        },
    )

    /**
     * Carte des membres d'un cercle.
     *
     * **Trois gestes, trois droits distincts**, et c'est le domaine qui les rend : rejoindre ou
     * refuser sa propre invitation, nommer ou démettre un modérateur — propriétaire seulement —,
     * retirer un membre — propriétaire ou modérateur, mais jamais le propriétaire.
     *
     * Le libellé du bouton de modération dépend du rôle **actuel** du membre : « Nommer » ou
     * « Retirer la modération ». Il est donc résolu ici, ligne par ligne, et vaut `null` quand
     * aucun des deux ne s'applique — ce qui distingue « pas de bouton » de « bouton sans
     * libellé ».
     */
    private fun membersCard(
        members: List<GroupMember>,
        links: List<FriendLink>,
        me: String,
    ): MembersCard = MembersCard(
        title = SocialText.membersCount(Social.acceptedMemberCount(members)),
        rows = members.map { member ->
            MemberRow(
                userId = member.userId,
                label = SocialText.memberLine(
                    name = member.profile?.displayName ?: SocialText.MEMBER,
                    role = SocialText.role(member.role),
                    pending = member.acceptedAt == null,
                ),
                canJoin = Social.isPendingForMe(member, me),
                canDecline = Social.isPendingForMe(member, me),
                moderatorLabel = if (Social.canToggleModerator(member, members, me)) {
                    if (member.role == GroupRole.MODERATOR) {
                        SocialText.UNNAME_MODERATOR
                    } else {
                        SocialText.NAME_MODERATOR
                    }
                } else {
                    null
                },
                // Le sens du geste suit le rôle **actuel** : on donne la modération à qui ne l'a
                // pas, on la retire à qui l'a. La condition est celle du libellé, mais elle est
                // rendue sur une **valeur** : l'écran n'a pas à interpréter une phrase pour savoir
                // ce que le bouton fera.
                grantsModerator = member.role != GroupRole.MODERATOR,
                canRemove = Social.canRemoveGroupMember(member, members, me),
            )
        },
        invites = if (!Social.canInviteToGroup(members, me)) {
            emptyList()
        } else {
            links.filter { it.status == FriendLinkStatus.ACCEPTED }.map {
                InviteRow(
                    userId = Social.otherId(it, me),
                    label = SocialText.inviteMember(it.other?.displayName ?: SocialText.FRIEND),
                )
            }
        },
        canDeleteGroup = Social.canDeleteGroup(members, me),
    )

    // ------------------------------------------------------------------ messages

    /**
     * Un message, prêt à disposer.
     *
     * L'accusé de lecture n'apparaît que sur **mes** messages et dans un **tête-à-tête** :
     * dans un cercle, « lu par qui ? » n'a pas de réponse, et le domaine ne rend qu'un état à
     * deux valeurs. L'original pose exactement ces deux conditions.
     */
    private fun messageRow(
        message: ChatMessage,
        me: String,
        isLink: Boolean,
        room: RoomState,
        links: List<FriendLink>,
    ): MessageRow {
        val mine = Social.isMine(message, me)
        return MessageRow(
            id = message.id,
            mine = mine,
            stamp = SocialText.messageStamp(
                sender = Social.senderName(message.senderId, me, room.members, links),
                clock = SocialText.clock(message.createdAt),
            ),
            body = message.body,
            recitation = if (message.kind == ChatMessageKind.RECITATION) {
                recitationRow(message)
            } else {
                null
            },
            receipt = if (mine && isLink) Social.readState(message, room.otherReadAt) else null,
            canDelete = Social.canDeleteMessage(message, me, room.members),
        )
    }

    /**
     * Bloc d'une récitation partagée.
     *
     * **Deux causes, un seul repli.** L'enregistrement peut manquer — l'original le dit
     * « Enregistrement indisponible » —, ou porter une plage que le référentiel ne connaît pas.
     * Dans les deux cas le bloc n'a rien à **nommer**, et il le dit plutôt que d'afficher un
     * passage vide.
     *
     * La durée ne s'affiche que s'il y a un enregistrement : c'est la durée de **ce qu'on
     * écouterait**, et annoncer « Durée : 1:35 » sous « Enregistrement indisponible » serait se
     * contredire.
     */
    private fun recitationRow(message: ChatMessage): RecitationRow {
        val attachment = message.recitation
        val reference = attachment?.let { referenceOf(it.startVerseId, it.endVerseId) }
        if (attachment == null || reference == null) {
            return RecitationRow(reference = SocialText.RECORDING_MISSING)
        }
        return RecitationRow(
            reference = reference,
            durationLabel = SocialText.durationLabel(attachment.durationMs),
        )
    }

    /**
     * Nom d'une plage coranique, ou `null` si elle sort du référentiel.
     *
     * **Pourquoi ce garde-fou.** `Quran.reference` **lève** hors du corpus — il lit un tableau
     * indexé. Les deux plages nommées ici viennent du **serveur** : celle d'une récitation
     * partagée, et le passage en cours d'un ami. Une ligne corrompue ferait donc planter l'écran
     * à distance, sans que rien ne le laisse prévoir.
     *
     * L'original n'a pas ce cas : son référentiel est embarqué, et c'est la même application qui
     * écrit les plages, donc elles sont toujours valides. Ici la donnée traverse le réseau, et
     * deux versions peuvent diverger. Chaque appelant décide donc de son repli : la récitation se
     * dit indisponible, le passage en cours disparaît.
     */
    private fun referenceOf(start: Int, end: Int): String? =
        runCatching { Quran.reference(Range(start, end)) }.getOrNull()
}
