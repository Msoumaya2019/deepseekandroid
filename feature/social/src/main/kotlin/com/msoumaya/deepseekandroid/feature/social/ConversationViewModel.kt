package com.msoumaya.deepseekandroid.feature.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.remote.ChatRoom
import com.msoumaya.deepseekandroid.core.data.repository.SocialRepository
import com.msoumaya.deepseekandroid.core.data.repository.UserRepository
import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.Program
import com.msoumaya.deepseekandroid.core.domain.Social
import com.msoumaya.deepseekandroid.core.domain.SocialText
import com.msoumaya.deepseekandroid.core.model.ChatMessageKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Conversation
// ---------------------------------------------------------------------------
// Portage de la conversation de `FriendsScreen` (`src/SocialScreens.tsx:168-208`).
//
// Ce fichier ne contient que le câblage : il relie l'état du dépôt aux saisies de la personne,
// puis délègue la mise en forme à `ConversationRenderer`. Le calcul vit là-bas parce qu'il est
// pur, donc éprouvable sans coroutine ni horloge.
//
// **La pièce est ouverte par la liste, et refermée ici.** C'est `SocialViewModel` qui appelle
// `openRoom` — le geste part d'une ligne d'ami ou d'un cercle —, et c'est ce `ViewModel` qui la
// referme. Les deux lisent le **même** dépôt : il n'y a donc pas deux vérités sur ce qui est
// ouvert, seulement deux écrans qui la lisent.
//
// **Ce que ce `ViewModel` ne tient pas.** Ni les messages, ni les membres, ni les objectifs : ils
// vivent dans le dépôt, qui sait aussi les relire. Ne sont tenues ici que les **saisies** — le
// brouillon, le formulaire ouvert, les deux champs de proposition —, parce qu'elles n'appartiennent
// qu'à cet écran et qu'elles entrent dans le calcul du rendu.
//
// **Un état fermé ne reçoit rien.** Chaque geste relit l'identifiant de la pièce ouverte et
// s'arrête s'il ne l'a pas : un geste parti après la fermeture écrirait dans une conversation que
// personne ne regarde, et la relecture ne le montrerait nulle part.
// ---------------------------------------------------------------------------

/**
 * Prépare l'affichage de la conversation ouverte.
 *
 * @param repository source de l'état social, et destination des gestes.
 * @param userState état de l'application — programme, objectif, séances —, lu **au moment du
 *   partage** d'étape. C'est la seule chose que la conversation emprunte à un autre écran, et
 *   l'original fait de même : son texte de partage est une propriété que l'application lui passe.
 * @param nowIso instant courant, injectable. Il sert à valider un rendez-vous proposé : « futur »
 *   n'a de sens qu'à un instant donné, et un test ne doit pas dépendre du jour où il tourne.
 */
class ConversationViewModel(
    private val repository: SocialRepository,
    private val userState: UserRepository,
    private val nowIso: () -> String = { Dates.nowIso() },
) : ViewModel() {

    /** Les saisies, qui entrent dans le calcul. */
    private val inputs = MutableStateFlow(ConversationInputs())

    private val _state = MutableStateFlow(ConversationUiState())

    /** État affichable de la conversation. */
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Un seul `collect` sur les deux sources, comme pour la liste d'amis : deux collectes
            // séparées liraient deux valeurs différentes au moment où la personne tape, et
            // publieraient un compositeur calculé sur l'ancien brouillon.
            combine(repository.state, inputs) { snapshot, saisies -> snapshot to saisies }
                .collect { (snapshot, saisies) ->
                    _state.value = ConversationRenderer.render(snapshot, saisies, nowIso())
                }
        }
    }

    // ------------------------------------------------------------------ ouverture

    /**
     * Referme la pièce, et **oublie les saisies**.
     *
     * Le brouillon part avec la pièce : le garder ferait réapparaître, à la réouverture d'une
     * **autre** conversation, un message écrit pour la précédente. Un brouillon par conversation
     * serait juste, mais il demanderait de le ranger quelque part — et rien ne le justifie tant
     * qu'on ne l'a pas demandé.
     */
    fun onClose() {
        repository.closeRoom()
        inputs.value = ConversationInputs()
        // **La liste se relit en refermant, et c'est le seul moment où elle le peut.** Un envoi de
        // message ne relit que la pièce ouverte — `actInRoom` —, donc l'aperçu et le compte de
        // non-lus d'une ligne d'ami restent ceux d'avant la conversation. L'original s'en remet à
        // son interrogation périodique ; ici il n'y a pas de minuteur, et refermer est justement
        // l'instant où la liste redevient visible.
        //
        // Ce n'est pas un chargement à l'aveugle : `refresh()` ne pose `loading` que sur une
        // **première** lecture, donc la liste ne clignote pas sur « Chargement de tes amis… ».
        viewModelScope.launch { repository.refresh() }
    }

    /**
     * Relit la pièce à l'ouverture de l'écran.
     *
     * **Une relecture, pas une boucle.** L'original interroge le serveur toutes les quinze
     * secondes ; ici il n'y a pas de minuteur, donc pas de relecture qui vive en dehors de
     * l'écran. Ce qui manque — l'arrivée d'un message sans rien toucher — viendra avec le canal
     * temps réel, pas avec un minuteur qui réveille le réseau sans raison.
     *
     * Sans effet pendant un chargement : deux lectures qui se suivent sur la même donnée ne
     * s'affichent pas, elles se remplacent.
     */
    fun onVisible() {
        val room = repository.state.value.room ?: return
        if (room.loading) return
        viewModelScope.launch { repository.refreshRoom() }
    }

    // ------------------------------------------------------------------ saisies

    fun onDraftChange(value: String) {
        inputs.value = inputs.value.copy(draft = value)
    }

    fun onToggleTools() {
        inputs.value = inputs.value.copy(toolsOpen = !inputs.value.toolsOpen)
    }

    fun onGoalTargetChange(value: String) {
        inputs.value = inputs.value.copy(goalTarget = value)
    }

    fun onAppointmentTextChange(value: String) {
        inputs.value = inputs.value.copy(appointmentText = value)
    }

    fun onReportReasonChange(value: String) {
        inputs.value = inputs.value.copy(reportReason = value)
    }

    // ------------------------------------------------------------------ discussion

    fun onLoadOlder() {
        viewModelScope.launch { repository.loadOlder() }
    }

    /** Envoie le brouillon, et le vide **seulement** si le serveur l'a accepté. */
    fun onSend() {
        val corps = inputs.value.draft
        viewModelScope.launch {
            if (repository.sendMessage(corps)) {
                inputs.value = inputs.value.copy(draft = "")
            }
        }
    }

    /**
     * Partage mon étape, **lue au moment du geste**.
     *
     * Trois valeurs, et chacune vient d'un écran qui n'est pas celui-ci : le libellé de
     * l'objectif, le ratio atteint, et les versets appris cette semaine. Elles sont lues **à
     * l'instant du partage**, et non tenues dans l'état : c'est ce que fait l'original, dont le
     * texte est recalculé à chaque rendu, et c'est aussi la seule lecture juste — un texte figé à
     * l'ouverture de la conversation annoncerait une progression vieille d'une heure.
     *
     * Les deux calculs sont **ceux du domaine**, les mêmes que l'écran Progrès : recalculer ici
     * ferait diverger deux chiffres que la personne peut comparer.
     */
    fun onShareProgress() {
        viewModelScope.launch {
            val app = userState.state.first()
            val texte = SocialText.sharedProgress(
                goalLabel = app.goal.label,
                goalRatio = Program.progress(app).goal,
                weekVerses = Program.stats(app).week,
            )
            repository.sendMessage(texte, ChatMessageKind.PROGRESS, SocialText.STEP_SHARED)
        }
    }

    fun onDeleteMessage(messageId: String) {
        viewModelScope.launch { repository.deleteMessage(messageId) }
    }

    // ------------------------------------------------------------------ signalement

    /**
     * Ouvre le formulaire sur le **dernier message reçu**.
     *
     * Le choix du message appartient au domaine — c'est `Social.reportable` —, et l'absence de
     * message se **dit** : ouvrir un formulaire vide laisserait croire qu'il y a quelque chose à
     * signaler, et l'envoi échouerait sans explication.
     */
    fun onReport() {
        val me = repository.state.value.profile?.id ?: return
        val message = Social.reportable(repository.state.value.room?.messages.orEmpty(), me)
        if (message == null) {
            repository.announce(SocialText.NOTHING_TO_REPORT)
            return
        }
        inputs.value = inputs.value.copy(reportTarget = message.id, reportReason = "")
    }

    fun onReportDismiss() {
        inputs.value = inputs.value.copy(reportTarget = null, reportReason = "")
    }

    fun onSendReport() {
        val cible = inputs.value.reportTarget ?: return
        val motif = inputs.value.reportReason
        viewModelScope.launch {
            if (repository.reportMessage(cible, motif)) {
                inputs.value = inputs.value.copy(reportTarget = null, reportReason = "")
            }
        }
    }

    // ------------------------------------------------------------------ objectif partagé

    /** Propose l'objectif saisi. Une saisie refusée ne part pas : le bouton est déjà désactivé. */
    fun onProposeGoal() {
        val linkId = ouverte?.linkId ?: return
        val seances = Social.targetSessions(inputs.value.goalTarget) ?: return
        viewModelScope.launch {
            if (repository.proposeSharedGoal(linkId, Social.goalWeek(), seances)) {
                inputs.value = inputs.value.copy(goalTarget = "")
            }
        }
    }

    fun onAcceptGoal(goalId: String) {
        viewModelScope.launch { repository.acceptSharedGoal(goalId) }
    }

    // ------------------------------------------------------------------ rendez-vous

    /**
     * Propose le rendez-vous saisi.
     *
     * **La saisie est validée ici, et pas par un bouton grisé.** C'est le choix de l'original : le
     * bouton reste actif, et une saisie refusée se dit par un message qui **explique le format**.
     * Un bouton grisé pendant la frappe ne dirait pas pourquoi il l'est.
     */
    fun onProposeAppointment() {
        val linkId = ouverte?.linkId ?: return
        val instant = Social.appointment(inputs.value.appointmentText, nowIso())
        if (instant == null) {
            repository.announce(SocialText.BAD_APPOINTMENT)
            return
        }
        viewModelScope.launch {
            if (repository.proposeAppointment(linkId, instant)) {
                inputs.value = inputs.value.copy(appointmentText = "")
            }
        }
    }

    fun onAcceptAppointment(appointmentId: String) {
        viewModelScope.launch { repository.acceptAppointment(appointmentId) }
    }

    fun onCancelAppointment(appointmentId: String) {
        viewModelScope.launch { repository.cancelAppointment(appointmentId) }
    }

    // ------------------------------------------------------------------ cercle

    fun onJoinGroup() {
        val groupId = ouverte?.groupId ?: return
        viewModelScope.launch { repository.acceptGroupInvite(groupId) }
    }

    fun onDeclineGroupInvite() {
        val groupId = ouverte?.groupId ?: return
        viewModelScope.launch { repository.declineGroupInvite(groupId) }
    }

    fun onInviteFriend(friendId: String) {
        val groupId = ouverte?.groupId ?: return
        viewModelScope.launch { repository.inviteToGroup(groupId, friendId) }
    }

    fun onToggleModerator(memberId: String, enabled: Boolean) {
        val groupId = ouverte?.groupId ?: return
        viewModelScope.launch { repository.setModerator(groupId, memberId, enabled) }
    }

    fun onRemoveMember(memberId: String) {
        val groupId = ouverte?.groupId ?: return
        viewModelScope.launch { repository.removeGroupMember(groupId, memberId) }
    }

    /**
     * Supprime le cercle. Le dépôt referme la pièce lui-même — la supprimer en la laissant
     * ouverte afficherait une conversation qui n'existe plus —, et les saisies suivent.
     */
    fun onDeleteGroup() {
        val groupId = ouverte?.groupId ?: return
        viewModelScope.launch {
            if (repository.deleteGroup(groupId)) inputs.value = ConversationInputs()
        }
    }

    /** La pièce ouverte, ou `null`. Chaque geste qui a besoin de son identifiant la relit ici. */
    private val ouverte: ChatRoom?
        get() = repository.state.value.room?.room

    companion object {
        /** Fabrique rattachée au conteneur, pour que l'écran n'ait pas à le connaître. */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ConversationViewModel(container.social, container.userState) as T
                }
            }
    }
}
