package com.msoumaya.deepseekandroid.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.QuranState
import com.msoumaya.deepseekandroid.core.data.quranFailureMessage
import com.msoumaya.deepseekandroid.core.data.repository.QuizState
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
// Ce fichier ne contient que le câblage : il relie l'état applicatif, l'avancement du référentiel
// coranique et l'instantané du Quiz, puis délègue le calcul à `HomeRenderer`. Le calcul vit là-bas
// parce qu'il est pur, donc éprouvable sans coroutine ni horloge.
//
// **Ce qui n'est pas encore là.** L'accueil d'origine porte aussi les contenus du jour
// (`TodayContents`), le compteur de messages non lus et le signalement de problème
// (`ProblemReportCard`). Les trois dépendent de Supabase et arrivent avec leur phase. Ils ne sont
// pas remplacés par un équivalent local.
//
// **Les cartes de quiz, elles, y sont.** Elles ne demandent au Quiz que son instantané — le jour,
// la question publiée et mes réponses —, que le dépôt publie depuis le disque **avant** tout appel
// réseau. L'accueil n'attend donc rien du serveur pour les afficher, et il les affiche même sans
// compte : elles annoncent alors la question du jour, ce qui est exact.
// ---------------------------------------------------------------------------

/**
 * Prépare l'affichage de l'accueil.
 *
 * @param repository source de l'état : le disque d'abord, le serveur ensuite.
 * @param quranState avancement du chargement du référentiel coranique. Il est **observé** et non
 *   supposé prêt : le référentiel se charge en arrière-plan pour ne pas retarder la première
 *   image, et lire le domaine avant qu'il ne soit prêt donnerait des sourates vides au lieu
 *   d'une erreur.
 * @param quizState instantané du Quiz. Il est **observé** pour la même raison que le référentiel :
 *   sa première valeur est « en attente », et l'afficher telle quelle donnerait deux cartes qui
 *   annoncent « Question du jour » alors que la réponse est déjà sur le disque.
 * @param today jour courant, injectable pour que les tests ne dépendent pas de l'horloge.
 */
class HomeViewModel(
    repository: UserRepository,
    quranState: StateFlow<QuranState>,
    quizState: StateFlow<QuizState>,
    private val today: () -> String = { Dates.todayLocal() },
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())

    /** État affichable de l'accueil. */
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Les trois sources sont combinées en **une** collecte : trois collectes séparées
            // publieraient un état intermédiaire où l'instantané du Quiz est celui du compte
            // précédent, ou l'inverse. Le calcul, lui, a besoin des trois à la fois.
            combine(repository.state, quranState, quizState) { appState, quran, quiz ->
                Triple(appState, quran, quiz)
            }.collect { (appState, quran, quiz) ->
                _state.value = when (quran) {
                    QuranState.Loading -> HomeUiState()
                    is QuranState.Failed -> HomeUiState(
                        loading = false,
                        failure = failureMessage(quran.cause),
                    )

                    QuranState.Ready -> HomeRenderer.render(appState, today(), quiz.snapshot)
                }
            }
        }
    }

    companion object {
        /**
         * Message affiché quand le référentiel coranique n'a pas pu être chargé.
         *
         * Délégué à `core:data`, à côté de l'état qu'il décrit : l'écran de programme pose la
         * même phrase, et deux copies finiraient par décrire la même panne de deux façons.
         */
        internal fun failureMessage(cause: Throwable): String = quranFailureMessage(cause)

        /** Fabrique rattachée au conteneur, pour que l'écran n'ait pas à le connaître. */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return HomeViewModel(
                        container.userState,
                        container.quranState,
                        container.quiz.state,
                    ) as T
                }
            }
    }
}
