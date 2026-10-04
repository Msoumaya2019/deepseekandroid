package com.msoumaya.deepseekandroid.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.QuranState
import com.msoumaya.deepseekandroid.core.data.repository.UserRepository
import com.msoumaya.deepseekandroid.core.domain.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Accueil
// ---------------------------------------------------------------------------
// Portage de `Home` et de ses deux fonctions auxiliaires (`activity`, `TinyWeek`) de
// `src/ui/MainScreens.tsx`.
//
// Ce fichier ne contient que le câblage : il relie l'état applicatif et l'avancement du
// référentiel coranique, puis délègue le calcul à `HomeRenderer`. Le calcul vit là-bas parce
// qu'il est pur, donc éprouvable sans coroutine ni horloge.
//
// **Ce qui n'est pas encore là.** L'accueil d'origine porte aussi les cartes de quiz
// (`QuizHomeCards`), les contenus du jour (`TodayContents`), le compteur de messages non lus et
// le signalement de problème (`ProblemReportCard`). Les quatre dépendent de Supabase et
// arrivent avec leur phase — quiz et amis en phase D. Ils ne sont pas remplacés par un
// équivalent local : un quiz hors ligne n'aurait pas de question du jour à poser.
// ---------------------------------------------------------------------------

/**
 * Prépare l'affichage de l'accueil.
 *
 * @param repository source de l'état : le disque d'abord, le serveur ensuite.
 * @param quranState avancement du chargement du référentiel coranique. Il est **observé** et non
 *   supposé prêt : le référentiel se charge en arrière-plan pour ne pas retarder la première
 *   image, et lire le domaine avant qu'il ne soit prêt donnerait des sourates vides au lieu
 *   d'une erreur.
 * @param today jour courant, injectable pour que les tests ne dépendent pas de l'horloge.
 */
class HomeViewModel(
    repository: UserRepository,
    quranState: StateFlow<QuranState>,
    private val today: () -> String = { Dates.todayLocal() },
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())

    /** État affichable de l'accueil. */
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.state, quranState) { appState, quran -> appState to quran }
                .collect { (appState, quran) ->
                    _state.value = when (quran) {
                        QuranState.Loading -> HomeUiState()
                        is QuranState.Failed -> HomeUiState(
                            loading = false,
                            failure = failureMessage(quran.cause),
                        )
                        QuranState.Ready -> HomeRenderer.render(appState, today())
                    }
                }
        }
    }

    companion object {
        /** Message affiché quand le référentiel coranique n'a pas pu être chargé. */
        internal fun failureMessage(cause: Throwable): String =
            "Le référentiel coranique n'a pas pu être chargé : " +
                (cause.message ?: cause::class.simpleName.orEmpty())

        /** Fabrique rattachée au conteneur, pour que l'écran n'ait pas à le connaître. */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return HomeViewModel(container.userState, container.quranState) as T
                }
            }
    }
}
