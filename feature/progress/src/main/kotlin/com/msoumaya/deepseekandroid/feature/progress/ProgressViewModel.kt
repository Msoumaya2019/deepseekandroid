package com.msoumaya.deepseekandroid.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.QuranState
import com.msoumaya.deepseekandroid.core.data.quranFailureMessage
import com.msoumaya.deepseekandroid.core.data.repository.UserRepository
import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.ProgressText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Progrès
// ---------------------------------------------------------------------------
// Portage de `ProgressScreen` (`src/ui/MainScreens.tsx:46`).
//
// Ce fichier ne contient que le câblage : il relie l'état applicatif, l'avancement du référentiel
// coranique et la période choisie, puis délègue le calcul à `ProgressRenderer`. Le calcul vit
// là-bas parce qu'il est pur, donc éprouvable sans coroutine ni horloge.
//
// **La période est tenue ici, et non dans l'écran**, comme au programme : la garder dans l'écran
// ferait deux sources de vérité — la sélection affichée, et les chiffres calculés — qui
// pourraient diverger sans que rien ne le dise. Elle est donc une **entrée** du calcul, publiée
// avec le reste de l'état.
//
// **La période par défaut est la semaine**, et non le jour : c'est celle du client d'origine
// (`useState<'Jour'|'Semaine'|'Mois'>('Semaine')`). L'écran Programme, lui, s'ouvre sur le jour —
// les deux écrans n'ont pas la même période par défaut, et c'est délibéré.
//
// **Ce qui n'est pas encore là.** Le client d'origine affiche sous la carte d'objectif un bloc
// `QuizStats` — les bonnes réponses et les défis. Il arrive avec l'écran de quiz, en phase D, et
// il n'est **pas** remplacé par un équivalent local : sans question du jour, un bloc de quiz
// n'aurait rien à compter. C'est le même choix qu'à l'accueil pour `QuizHomeCards`.
//
// **Rien n'est écrit.** Contrairement au tableau de bord des révisions, cet écran ne modifie pas
// l'état : il observe et il compte. Il ne dépend donc pas du dépôt en écriture, seulement de sa
// lecture — et la fabrique le lui passe tel quel.
// ---------------------------------------------------------------------------

/**
 * Prépare l'affichage de l'écran « Progrès ».
 *
 * @param repository source de l'état : le disque d'abord, le serveur ensuite.
 * @param quranState avancement du chargement du référentiel coranique. Il est **observé** et non
 *   supposé prêt : `Quran.verseAt` lève sur un référentiel vide, et le balayage des 604 pages
 *   d'un référentiel absent afficherait zéro plutôt qu'une panne.
 * @param today jour courant, injectable pour que les tests ne dépendent pas de l'horloge.
 */
class ProgressViewModel(
    repository: UserRepository,
    quranState: StateFlow<QuranState>,
    private val today: () -> String = { Dates.todayLocal() },
) : ViewModel() {

    /** La période choisie. Elle entre dans le calcul, donc elle est observée comme le reste. */
    private val period = MutableStateFlow(ProgressText.Period.WEEK)

    private val _state = MutableStateFlow(ProgressUiState())

    /** État affichable de l'écran. */
    val state: StateFlow<ProgressUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Un seul `collect` sur les trois sources : deux collectes séparées — l'une pour
            // l'état, l'autre pour la période — liraient deux valeurs différentes au moment où
            // la période change, et publieraient un état intermédiaire que personne n'a demandé.
            combine(repository.state, quranState, period) { appState, quran, periode ->
                Triple(appState, quran, periode)
            }.collect { (appState, quran, periode) ->
                _state.value = when (quran) {
                    QuranState.Loading -> ProgressUiState(period = periode)
                    is QuranState.Failed -> ProgressUiState(
                        loading = false,
                        failure = quranFailureMessage(quran.cause),
                        period = periode,
                    )

                    QuranState.Ready -> ProgressRenderer.render(appState, periode, today())
                }
            }
        }
    }

    /** Change la période observée par les statistiques et le graphique. */
    fun onPeriodSelected(value: ProgressText.Period) {
        period.value = value
    }

    companion object {
        /** Fabrique rattachée au conteneur, pour que l'écran n'ait pas à le connaître. */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ProgressViewModel(container.userState, container.quranState) as T
                }
            }
    }
}
