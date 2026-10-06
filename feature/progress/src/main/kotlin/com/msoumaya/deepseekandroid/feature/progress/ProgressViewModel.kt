package com.msoumaya.deepseekandroid.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.QuranState
import com.msoumaya.deepseekandroid.core.data.quranFailureMessage
import com.msoumaya.deepseekandroid.core.data.repository.QuizState
import com.msoumaya.deepseekandroid.core.data.repository.UserRepository
import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.ProgressText
import com.msoumaya.deepseekandroid.core.model.AppState
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
// coranique, l'instantané du Quiz et la période choisie, puis délègue le calcul à
// `ProgressRenderer`. Le calcul vit là-bas parce qu'il est pur, donc éprouvable sans coroutine ni
// horloge.
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
// **Le bloc `QuizStats` y est.** Il ne demande au Quiz que son instantané et l'identifiant du
// compte — le premier vient du disque avant tout appel réseau, le second est connu du dépôt. Il
// s'affiche donc même hors ligne, et même sans compte : à zéro, ce qui est exact.
//
// **Rien n'est écrit.** Contrairement au tableau de bord des révisions, cet écran ne modifie pas
// l'état : il observe et il compte. Il ne dépend donc pas du dépôt en écriture, seulement de sa
// lecture — et la fabrique le lui passe tel quel.
// ---------------------------------------------------------------------------

/**
 * Les quatre entrées du calcul, liées pour n'être lues qu'**une** fois.
 *
 * `combine` accepte quatre flux, mais sa fonction de transformation doit rendre **une** valeur :
 * il n'existe pas de `Quadruple` en Kotlin. Cette classe est privée au fichier, et c'est ce qui
 * évite de replier quatre sources dans une `List<Any?>` — où une inversion d'ordre ne se verrait
 * qu'à l'exécution.
 */
private data class Sources(
    val appState: AppState,
    val quran: QuranState,
    val quiz: QuizState,
    val period: ProgressText.Period,
)

/**
 * Prépare l'affichage de l'écran « Progrès ».
 *
 * @param repository source de l'état : le disque d'abord, le serveur ensuite.
 * @param quranState avancement du chargement du référentiel coranique. Il est **observé** et non
 *   supposé prêt : `Quran.verseAt` lève sur un référentiel vide, et le balayage des 604 pages
 *   d'un référentiel absent afficherait zéro plutôt qu'une panne.
 * @param quizState instantané du Quiz. Observé pour la même raison : sa première valeur est
 *   « en attente », et publier un bloc de statistiques à zéro sur cette valeur ferait annoncer
 *   « tu n'as pas encore joué » à quelqu'un qui a joué.
 * @param today jour courant, injectable pour que les tests ne dépendent pas de l'horloge.
 */
class ProgressViewModel(
    repository: UserRepository,
    quranState: StateFlow<QuranState>,
    quizState: StateFlow<QuizState>,
    private val today: () -> String = { Dates.todayLocal() },
) : ViewModel() {

    /** La période choisie. Elle entre dans le calcul, donc elle est observée comme le reste. */
    private val period = MutableStateFlow(ProgressText.Period.WEEK)

    private val _state = MutableStateFlow(ProgressUiState())

    /** État affichable de l'écran. */
    val state: StateFlow<ProgressUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Un seul `collect` sur les quatre sources : des collectes séparées — l'une pour
            // l'état, l'autre pour la période — liraient deux valeurs différentes au moment où la
            // période change, et publieraient un état intermédiaire que personne n'a demandé.
            combine(repository.state, quranState, quizState, period) { appState, quran, quiz, periode ->
                Sources(appState = appState, quran = quran, quiz = quiz, period = periode)
            }.collect { sources ->
                val periode = sources.period
                _state.value = when (val quran = sources.quran) {
                    QuranState.Loading -> ProgressUiState(period = periode)
                    is QuranState.Failed -> ProgressUiState(
                        loading = false,
                        failure = quranFailureMessage(quran.cause),
                        period = periode,
                    )

                    QuranState.Ready -> ProgressRenderer.render(
                        state = sources.appState,
                        period = periode,
                        at = today(),
                        quiz = sources.quiz.snapshot,
                        userId = sources.quiz.ownerId,
                    )
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
                    return ProgressViewModel(
                        container.userState,
                        container.quranState,
                        container.quiz.state,
                    ) as T
                }
            }
    }
}
