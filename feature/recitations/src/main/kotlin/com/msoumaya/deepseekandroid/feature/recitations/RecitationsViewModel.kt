package com.msoumaya.deepseekandroid.feature.recitations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.repository.RecitationRepository
import com.msoumaya.deepseekandroid.core.domain.RecitationText
import com.msoumaya.deepseekandroid.core.domain.RecitationsList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Récitations enregistrées
// ---------------------------------------------------------------------------
// Portage de la liste de `RecitationsScreen` (`src/RecitationsScreen.tsx`).
//
// Ce fichier ne contient que le câblage : il relie l'état du dépôt aux saisies de la personne,
// puis délègue la mise en forme à `RecitationsRenderer`. Le calcul vit là-bas parce qu'il est pur,
// donc éprouvable sans coroutine ni horloge.
//
// **Les saisies sont tenues ici, et non dans l'écran.** Le filtre choisi et la ligne dépliée sont
// des **entrées** du rendu : la liste affichée et les cartes en dépendent. Les garder dans les
// composables ferait deux sources pour la même décision — la puce allumée à l'écran, et la liste
// calculée ailleurs —, et un changement de filtre publierait un état où les deux ne seraient pas
// d'accord.
//
// **Ce que ce fichier fait des corrections.** Il les **demande** quand une ligne s'ouvre, et les
// **étiquette** par cette ligne (`RecitationsDetails`). C'est la seule façon d'être sûr qu'une
// réponse arrivée en retard ne s'affichera pas sous une autre récitation : le rendu ne garde que
// les détails dont l'étiquette est celle de la ligne ouverte.
//
// **Ce qu'il ne fait pas encore : écouter.** Le bouton de lecture, l'avance et le retour de dix
// secondes demandent une couche audio qui n'est pas branchée dans ce module — et un bouton qui ne
// mène nulle part est exactement ce que ce dépôt s'interdit. Ils viendront avec la capacité, comme
// la doublure de `RecitationSource` est venue avec son consommateur.
// ---------------------------------------------------------------------------

/**
 * Prépare l'affichage de la liste des récitations.
 *
 * @param repository source de l'état des récitations, et destination des gestes.
 */
class RecitationsViewModel(
    private val repository: RecitationRepository,
) : ViewModel() {

    /** Les saisies, qui entrent dans le calcul. */
    private val inputs = MutableStateFlow(RecitationsInputs())

    /** Ce qui a été chargé pour la ligne ouverte, étiqueté par elle. */
    private val details = MutableStateFlow(RecitationsDetails())

    private val _state = MutableStateFlow(RecitationsUiState())

    /** État affichable de l'écran. */
    val state: StateFlow<RecitationsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Un seul `collect` sur les trois sources : des collectes séparées liraient des
            // valeurs de moments différents, et publieraient une liste calculée sur un filtre
            // déjà remplacé — ou des cartes sans la ligne qui les porte.
            combine(repository.state, inputs, details) { snapshot, saisies, chargees ->
                Triple(snapshot, saisies, chargees)
            }.collect { (snapshot, saisies, chargees) ->
                val pourLaLigne = chargees.forOpen(saisies.openId)
                _state.value = RecitationsRenderer.render(
                    state = snapshot,
                    inputs = saisies,
                    corrections = pourLaLigne.corrections,
                    feedback = pourLaLigne.feedback,
                )
            }
        }
    }

    /**
     * Relit à l'ouverture de l'écran.
     *
     * **Sans effet si un chargement est déjà en cours.** L'écran s'ouvre presque toujours après la
     * construction du dépôt, qui charge déjà : sans cette garde, la première ouverture coûterait
     * deux lectures, et deux lectures qui se suivent sur la même donnée ne s'affichent pas, elles
     * se remplacent.
     */
    fun onVisible() {
        if (repository.state.value.loading) return
        viewModelScope.launch { repository.refresh() }
    }

    /** Relit à la demande : c'est le geste « Rafraîchir » de l'écran, et il n'a pas de garde. */
    fun onRefresh() {
        viewModelScope.launch { repository.refresh() }
    }

    /**
     * Retient le filtre touché.
     *
     * La comparaison porte sur le **libellé**, parce que c'est ce que le sélecteur segmenté rend.
     * Un libellé inconnu laisse le filtre en place : le composant ne choisit pas à la place de
     * l'écran, et l'écran ne doit pas inventer un filtre qu'aucun segment n'a demandé.
     */
    fun onFilterSelected(label: String) {
        val filtre = filtreDe(label) ?: return
        inputs.value = inputs.value.copy(filter = filtre)
    }

    /**
     * Déplie la ligne touchée, ou la replie si c'était déjà elle.
     *
     * **Les corrections sont demandées à l'ouverture, et non à la lecture de la liste** : c'est
     * l'original, où la liste ne porte que les lignes et où les cartes viennent d'un second appel.
     * Tout replier **vide** les détails en mémoire, pour qu'une réponse encore en vol ne laisse
     * pas ses cartes derrière elle.
     */
    fun onOpen(id: String) {
        val ouverte = inputs.value.openId
        val nouvelle = if (ouverte == id) null else id
        inputs.value = inputs.value.copy(openId = nouvelle)

        if (nouvelle == null) {
            details.value = RecitationsDetails()
            return
        }

        viewModelScope.launch {
            details.value = RecitationsDetails(
                id = nouvelle,
                corrections = repository.corrections(nouvelle),
                feedback = repository.generalFeedback(nouvelle),
            )
        }
    }

    /**
     * Retire une récitation.
     *
     * **La copie distante l'emporte quand elle existe** : le dépôt emporte alors la copie locale
     * dans le même geste. Ne retirer que le fichier de l'appareil laisserait la ligne listée, et le
     * prochain dépôt la renverrait au serveur — annulant la suppression demandée.
     *
     * Une ligne qui n'existe dans **aucune** des deux listes ne fait rien : elle vient d'être
     * retirée par une relecture, et redemander sa suppression serait un aller-retour pour rien.
     */
    fun onDelete(id: String) {
        val distante = repository.state.value.remote.firstOrNull { it.id == id }
        val locale = repository.state.value.items.firstOrNull { it.id == id }

        viewModelScope.launch {
            when {
                distante != null -> repository.deleteRemote(distante)
                locale != null -> repository.delete(locale)
            }
        }
    }

    /** Le filtre que porte un libellé, ou `null` si aucun segment ne le réclame. */
    private fun filtreDe(label: String): RecitationsList.Filter? = when (label) {
        RecitationText.FILTER_ALL -> RecitationsList.Filter.ALL
        RecitationText.FILTER_QURAN -> RecitationsList.Filter.QURAN
        RecitationText.FILTER_INVOCATION -> RecitationsList.Filter.INVOCATION
        else -> null
    }

    companion object {
        /** Fabrique rattachée au conteneur, pour que l'écran n'ait pas à le connaître. */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return RecitationsViewModel(container.recitations) as T
                }
            }
    }
}
