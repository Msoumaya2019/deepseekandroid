package com.msoumaya.deepseekandroid.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.QuranState
import com.msoumaya.deepseekandroid.core.data.quranFailureMessage
import com.msoumaya.deepseekandroid.core.data.repository.DailyContentsState
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
// coranique, l'instantané du Quiz et les contenus du jour, puis délègue le calcul à
// `HomeRenderer`. Le calcul vit là-bas parce qu'il est pur, donc éprouvable sans coroutine ni
// horloge.
//
// **Ce qui n'est pas encore là.** L'accueil d'origine porte aussi le compteur de messages non lus
// (`unreadCount`), qui dépend de Supabase et arrive avec sa phase. Il n'est pas remplacé par un
// équivalent local — et le signalement de problème, lui, est déjà porté par `HomeScreen`.
//
// **Les cartes de quiz et le carrousel, eux, y sont.** Les unes ne demandent au Quiz que son
// instantané — le jour, la question publiée et mes réponses —, l'autre ne demande au dépôt des
// contenus que son document ; les deux dépôts publient **depuis le disque avant tout appel
// réseau**. L'accueil n'attend donc rien du serveur pour les afficher, et il les affiche même
// sans compte : les cartes annoncent alors la question du jour, ce qui est exact, et le carrousel
// affiche ce qui est publié — ou son absence, qui est une information —, ce qui l'est aussi.
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
 * @param dailyState état des contenus du jour. Observé comme les deux autres, et pour la même
 *   raison : sa première valeur est « en attente », donc l'afficher telle quelle ferait annoncer
 *   « Chargement » à un carrousel dont le document est déjà sur le disque. **Aucun compte n'est
 *   requis** : les contenus du jour sont publics, et le dépôt ne s'amorce par utilisateur.
 * @param today jour courant, injectable pour que les tests ne dépendent pas de l'horloge.
 */
class HomeViewModel(
    repository: UserRepository,
    quranState: StateFlow<QuranState>,
    quizState: StateFlow<QuizState>,
    dailyState: StateFlow<DailyContentsState>,
    private val today: () -> String = { Dates.todayLocal() },
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())

    /** État affichable de l'accueil. */
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Les quatre sources sont combinées en **une** collecte : des collectes séparées
            // publieraient un état intermédiaire où l'instantané du Quiz est celui du compte
            // précédent, ou l'inverse. Le calcul, lui, a besoin des quatre à la fois — y compris
            // du document des contenus du jour, qui se publie à son propre rythme et ne dépend
            // d'aucune des trois autres.
            combine(
                repository.state,
                quranState,
                quizState,
                dailyState,
            ) { appState, quran, quiz, daily ->
                when (quran) {
                    QuranState.Loading -> HomeUiState()

                    is QuranState.Failed -> HomeUiState(
                        loading = false,
                        failure = failureMessage(quran.cause),
                    )

                    // Les deux états d'attente et de panne du référentiel ne portent **pas** de
                    // carrousel : `daily` y reste `null`, c'est-à-dire « pas calculé ». Un
                    // carrousel vide y serait un mensonge — l'accueil n'a rien pu lire du tout,
                    // et « Aucun contenu disponible aujourd'hui » serait une affirmation sur la
                    // base alors que c'est le référentiel qui manque.
                    QuranState.Ready -> HomeRenderer.render(appState, today(), quiz.snapshot, daily)
                }
            }.collect { _state.value = it }
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
                        container.dailyContents.state,
                    ) as T
                }
            }
    }
}
