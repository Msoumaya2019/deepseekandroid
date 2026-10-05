package com.msoumaya.deepseekandroid.feature.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.QuranState
import com.msoumaya.deepseekandroid.core.data.quranFailureMessage
import com.msoumaya.deepseekandroid.core.data.repository.UserRepository
import com.msoumaya.deepseekandroid.core.domain.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Programme
// ---------------------------------------------------------------------------
// Portage de `ProgramScreen` (`src/ui/MainScreens.tsx:35`).
//
// Ce fichier ne contient que le câblage : il relie l'état applicatif, l'avancement du référentiel
// coranique et la période choisie, puis délègue le calcul à `ProgramRenderer`. Le calcul vit
// là-bas parce qu'il est pur, donc éprouvable sans coroutine ni horloge.
//
// **La période est tenue ici, et non dans l'écran.** Le client d'origine la garde dans un
// `useState` du composable, ce qui va de soi en React Native ; ici, la garder dans l'écran
// ferait deux sources de vérité — la sélection affichée, et la liste filtrée — qui pourraient
// diverger sans que rien ne le dise. Elle est donc une **entrée** du calcul, publiée avec le
// reste de l'état.
//
// **Ce qui n'est pas encore là.** Le client d'origine ouvre depuis cet écran l'assistant de
// choix d'objectif (« Modifier mon objectif ») et le tableau de bord des révisions. Les deux
// écrans arrivent avec la même phase ; les rappels sont donc des paramètres, comme pour
// l'accueil — un écran ne décide pas où mène un bouton, la coquille le décide.
// ---------------------------------------------------------------------------

/**
 * Prépare l'affichage du programme.
 *
 * @param repository source de l'état : le disque d'abord, le serveur ensuite.
 * @param quranState avancement du chargement du référentiel coranique. Il est **observé** et non
 *   supposé prêt : lire le domaine avant qu'il ne le soit donnerait des sourates vides au lieu
 *   d'une erreur.
 * @param today jour courant, injectable pour que les tests ne dépendent pas de l'horloge.
 */
class ProgramViewModel(
    repository: UserRepository,
    quranState: StateFlow<QuranState>,
    private val today: () -> String = { Dates.todayLocal() },
) : ViewModel() {

    /** La période choisie. Elle entre dans le calcul, donc elle est observée comme le reste. */
    private val period = MutableStateFlow(ProgramPeriod.DAY)

    private val _state = MutableStateFlow(ProgramUiState())

    /** État affichable du programme. */
    val state: StateFlow<ProgramUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Un seul `collect` sur les trois sources : deux collectes séparées — l'une pour
            // l'état, l'autre pour la période — liraient deux valeurs différentes au moment où
            // la période change, et publieraient un état intermédiaire que personne n'a demandé.
            combine(repository.state, quranState, period) { appState, quran, periode ->
                Triple(appState, quran, periode)
            }.collect { (appState, quran, periode) ->
                _state.value = when (quran) {
                    QuranState.Loading -> ProgramUiState(period = periode)
                    is QuranState.Failed -> ProgramUiState(
                        loading = false,
                        failure = quranFailureMessage(quran.cause),
                        period = periode,
                    )

                    QuranState.Ready -> ProgramRenderer.render(appState, today(), periode)
                }
            }
        }
    }

    /** Change la période d'affichage de la liste « À venir ». */
    fun onPeriodSelected(value: ProgramPeriod) {
        period.value = value
    }

    companion object {
        /** Fabrique rattachée au conteneur, pour que l'écran n'ait pas à le connaître. */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ProgramViewModel(container.userState, container.quranState) as T
                }
            }
    }
}
