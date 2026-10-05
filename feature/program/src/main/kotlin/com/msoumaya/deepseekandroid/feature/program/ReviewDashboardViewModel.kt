package com.msoumaya.deepseekandroid.feature.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.QuranState
import com.msoumaya.deepseekandroid.core.data.quranFailureMessage
import com.msoumaya.deepseekandroid.core.data.repository.UserRepository
import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.Review
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Tableau de bord des révisions
// ---------------------------------------------------------------------------
// Portage de `ReviewDashboard.tsx`. Ce fichier ne contient que le câblage : il relie l'état
// applicatif et l'avancement du référentiel coranique, puis délègue le calcul à
// `ReviewDashboardRenderer`. Le calcul vit là-bas parce qu'il est pur, donc éprouvable sans
// coroutine ni horloge.
//
// **Ce fichier écrit, et c'est sa seule décision.** Le tableau de bord est le premier écran de
// cette phase qui **modifie** l'état : les deux sélecteurs du cycle (durée et quantité)
// appellent `Review.setReviewCycle` et `Review.setReviewQuantity`. C'est pour cela qu'il dépend
// du dépôt, et non seulement du domaine.
//
// **L'écriture passe par `mutate`, et non par une copie locale.** `UserRepository.mutate`
// applique la transformation sur l'état **courant du disque**, horodate le résultat et le
// publie. Lire l'état, le transformer puis l'écrire produirait une course : deux appuis rapides
// sur deux durées différentes partiraient du même état lu, et le second écraserait le premier.
//
// **La durée et la quantité sont deux réglages, et non un seul.** `setReviewCycle` pose
// `mode = "cycle"`, `setReviewQuantity` pose `mode = "quantity"` : le mode enregistré dit lequel
// des deux fait foi, et c'est lui que le renderer relit pour choisir la forme affichée. Les
// appeler tous les deux ferait afficher une durée que la personne n'a pas choisie.
// ---------------------------------------------------------------------------

/**
 * Prépare l'affichage du tableau de bord des révisions.
 *
 * @param repository source de l'état : le disque d'abord, le serveur ensuite. Il sert aussi à
 *   écrire les deux réglages du cycle.
 * @param quranState avancement du chargement du référentiel coranique. Il est **observé** et non
 *   supposé prêt : `Quran.reference` lève sur un référentiel vide, donc lire le domaine trop tôt
 *   ferait planter l'écran au lieu d'afficher une attente.
 * @param today jour courant, injectable pour que les tests ne dépendent pas de l'horloge.
 */
class ReviewDashboardViewModel(
    private val repository: UserRepository,
    quranState: StateFlow<QuranState>,
    private val today: () -> String = { Dates.todayLocal() },
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewDashboardUiState())

    /** État affichable du tableau de bord. */
    val state: StateFlow<ReviewDashboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.state, quranState) { appState, quran -> appState to quran }
                .collect { (appState, quran) ->
                    _state.value = when (quran) {
                        QuranState.Loading -> ReviewDashboardUiState()
                        is QuranState.Failed -> ReviewDashboardUiState(
                            loading = false,
                            failure = quranFailureMessage(quran.cause),
                        )

                        QuranState.Ready -> ReviewDashboardRenderer.render(appState, today())
                    }
                }
        }
    }

    /**
     * Change la durée du cycle.
     *
     * `setReviewCycle` **reconstruit** le cycle : les versets déjà revus repartent dans le cycle
     * suivant, et le compteur du jour repart à 1. Ce n'est pas une remise à zéro des
     * connaissances — le cycle est un découpage, pas une progression.
     */
    fun onCycleSelected(days: Int) {
        viewModelScope.launch {
            repository.mutate { Review.setReviewCycle(it, days, at = today()) }
        }
    }

    /** Change la quantité quotidienne. Même reconstruction du cycle, autre découpage. */
    fun onQuantitySelected(quantity: String) {
        viewModelScope.launch {
            repository.mutate { Review.setReviewQuantity(it, quantity, at = today()) }
        }
    }

    companion object {
        /** Fabrique rattachée au conteneur, pour que l'écran n'ait pas à le connaître. */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ReviewDashboardViewModel(container.userState, container.quranState) as T
                }
            }
    }
}
