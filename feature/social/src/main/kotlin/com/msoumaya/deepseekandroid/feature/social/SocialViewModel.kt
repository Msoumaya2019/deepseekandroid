package com.msoumaya.deepseekandroid.feature.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.repository.SocialRepository
import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.Social
import com.msoumaya.deepseekandroid.core.domain.SocialText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Amis
// ---------------------------------------------------------------------------
// Portage de la liste d'amis de `FriendsScreen` (`src/SocialScreens.tsx`).
//
// Ce fichier ne contient que le câblage : il relie l'état du dépôt aux saisies de la personne,
// puis délègue la mise en forme à `SocialRenderer`. Le calcul vit là-bas parce qu'il est pur,
// donc éprouvable sans coroutine ni horloge.
//
// **Les saisies sont tenues ici, et non dans l'écran.** Le texte de recherche, le filtre
// choisi, le dépliage de la liste et les deux champs de saisie sont des **entrées** du rendu :
// la liste affichée en dépend. Les garder dans les composables ferait deux sources pour la même
// décision — la saisie à l'écran, et la liste calculée ailleurs —, et un changement de filtre
// publierait un état où la puce et la liste ne seraient pas d'accord.
//
// **Ce qui n'est pas encore là.** La conversation — ouvrir un ami ou un cercle, lire et écrire
// des messages, signaler, partager une étape, fixer un objectif commun, proposer un
// rendez-vous — arrive avec l'écran de conversation. La liste ne montre donc pas de porte vers
// elle : un bouton qui n'ouvre rien est pire qu'un bouton absent.
// ---------------------------------------------------------------------------

/**
 * Prépare l'affichage de la liste d'amis.
 *
 * @param repository source de l'état social, et destination des gestes.
 * @param nowIso instant courant, injectable pour que les tests ne dépendent pas de l'horloge. Il
 *   ne sert qu'à une chose : dire si une suspension est encore active.
 */
class SocialViewModel(
    private val repository: SocialRepository,
    private val nowIso: () -> String = { Dates.nowIso() },
) : ViewModel() {

    /** Les saisies, qui entrent dans le calcul. */
    private val inputs = MutableStateFlow(SocialInputs())

    private val _state = MutableStateFlow(SocialUiState())

    /** État affichable de l'écran. */
    val state: StateFlow<SocialUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Un seul `collect` sur les deux sources : deux collectes séparées — l'une pour
            // l'état, l'autre pour les saisies — liraient deux valeurs différentes au moment où
            // la personne tape, et publieraient une liste calculée sur l'ancien texte.
            combine(repository.state, inputs) { snapshot, saisies -> snapshot to saisies }
                .collect { (snapshot, saisies) ->
                    _state.value = SocialRenderer.render(snapshot, saisies, nowIso())
                }
        }
    }

    /**
     * Relit à l'ouverture de l'écran.
     *
     * **Sans effet si un chargement est déjà en cours.** L'écran s'ouvre presque toujours après
     * la construction du dépôt, qui charge déjà : sans cette garde, la première ouverture
     * coûterait deux lectures — et deux lectures qui se suivent sur la même donnée ne
     * s'affichent pas, elles se remplacent.
     */
    fun onVisible() {
        if (repository.state.value.loading) return
        viewModelScope.launch { repository.refresh() }
    }

    // ------------------------------------------------------------------ saisies

    fun onQueryChange(value: String) {
        inputs.value = inputs.value.copy(query = value)
    }

    /**
     * Retient le filtre touché.
     *
     * La comparaison porte sur le **libellé**, parce que c'est ce que le sélecteur segmenté
     * rend. Un libellé inconnu laisse le filtre en place : le composant ne choisit pas à la
     * place de l'écran, et l'écran ne doit pas inventer un filtre qu'aucun segment n'a demandé.
     */
    fun onFilterSelected(label: String) {
        val filtre = Social.Filter.entries.firstOrNull { it.label == label } ?: return
        inputs.value = inputs.value.copy(filter = filtre)
    }

    fun onToggleAll() {
        inputs.value = inputs.value.copy(allFriends = !inputs.value.allFriends)
    }

    fun onToggleOptions() {
        inputs.value = inputs.value.copy(optionsOpen = !inputs.value.optionsOpen)
    }

    fun onCodeChange(value: String) {
        inputs.value = inputs.value.copy(code = value)
    }

    fun onGroupNameChange(value: String) {
        inputs.value = inputs.value.copy(groupName = value)
    }

    // ------------------------------------------------------------------ gestes

    /** Envoie la demande d'ami, et vide le champ **seulement** si le serveur l'a acceptée. */
    fun onSendInvitation() {
        val code = inputs.value.code
        viewModelScope.launch {
            if (repository.requestFriend(code)) {
                inputs.value = inputs.value.copy(code = "")
            }
        }
    }

    fun onAccept(linkId: String) {
        viewModelScope.launch { repository.acceptInvitation(linkId) }
    }

    fun onDecline(linkId: String) {
        viewModelScope.launch { repository.declineInvitation(linkId) }
    }

    fun onRemove(linkId: String) {
        viewModelScope.launch { repository.removeFriend(linkId) }
    }

    fun onBlock(otherId: String) {
        viewModelScope.launch { repository.block(otherId) }
    }

    fun onUnblock(otherId: String) {
        viewModelScope.launch { repository.unblock(otherId) }
    }

    /** Crée le cercle, et vide le champ **seulement** si le serveur l'a accepté. */
    fun onCreateGroup() {
        val nom = inputs.value.groupName
        viewModelScope.launch {
            if (repository.createGroup(nom.trim())) {
                inputs.value = inputs.value.copy(groupName = "")
            }
        }
    }

    /**
     * Confirme une copie dans le presse-papiers.
     *
     * Le message passe par le dépôt pour qu'il n'y ait qu'un bandeau : deux sources — l'une du
     * dépôt, l'autre de l'écran — se masqueraient l'une l'autre selon l'ordre des recompositions.
     */
    fun onCodeCopied() {
        repository.announce(SocialText.CODE_COPIED)
    }

    /**
     * Ouvre le cercle « contact administrateur ».
     *
     * **Écart assumé.** Le client d'origine ouvrait ensuite la conversation avec l'administration.
     * La conversation n'existe pas encore ici : le geste crée donc le cercle et **déplie** le bloc
     * où il apparaît, pour qu'il ait un effet visible. Sans cela, il écrirait en base sans que
     * rien ne le montre — et une écriture invisible est pire qu'un bouton absent.
     *
     * Le dépliage n'a lieu que si le serveur a répondu : un échec laisse l'écran tel quel, avec
     * le message d'erreur que le dépôt vient de publier.
     */
    fun onOpenAdminContact() {
        viewModelScope.launch {
            if (repository.openAdminContact() != null) {
                inputs.value = inputs.value.copy(optionsOpen = true)
            }
        }
    }

    companion object {
        /** Fabrique rattachée au conteneur, pour que l'écran n'ait pas à le connaître. */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return SocialViewModel(container.social) as T
                }
            }
    }
}
