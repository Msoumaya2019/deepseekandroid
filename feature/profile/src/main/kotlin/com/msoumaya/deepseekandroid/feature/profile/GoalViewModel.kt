package com.msoumaya.deepseekandroid.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.QuranState
import com.msoumaya.deepseekandroid.core.data.quranFailureMessage
import com.msoumaya.deepseekandroid.core.data.repository.UserRepository
import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.GoalText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Écran d'objectif
// ---------------------------------------------------------------------------
// Portage de `GoalScreen` (`src/ui/GoalScreen.tsx`).
//
// Ce fichier ne contient que le câblage : il tient les champs de saisie, observe l'état du compte
// et l'avancement du référentiel coranique, et délègue le calcul à `GoalRenderer`. Le calcul vit
// là-bas parce qu'il est pur, donc éprouvable sans Compose, sans coroutine et sans horloge.
//
// **Les champs sont amorcés une seule fois.** Le client d'origine les tient dans des `useState`
// dont les valeurs initiales sont calculées au montage ; les redériver à chaque émission de
// l'état écraserait la saisie en cours. Voir la note de `GoalUiState`.
//
// **L'écriture lit l'état au moment de l'appui.** `mutate` reçoit l'état courant, et non celui
// qu'on a observé au rendu : entre les deux, une synchronisation a pu passer. C'est la règle du
// dépôt pour toute écriture, et elle vaut ici comme ailleurs — l'objectif se calcule sur ce que le
// compte porte **maintenant**.
// ---------------------------------------------------------------------------

/**
 * Prépare l'écran d'objectif.
 *
 * @param repository source de l'état : le disque d'abord, le serveur ensuite.
 * @param quranState avancement du chargement du référentiel coranique. Il est **observé** et non
 *   supposé prêt : `GoalRenderer.seed` lit les sourates pour situer le dernier verset connu, et
 *   lire un référentiel vide donnerait des listes vides au lieu d'une attente.
 * @param today jour courant, injectable pour que les tests ne dépendent pas de l'horloge.
 */
class GoalViewModel(
    private val repository: UserRepository,
    quranState: StateFlow<QuranState>,
    private val today: () -> String = { Dates.todayLocal() },
) : ViewModel() {

    /**
     * Les champs de saisie, ou `null` tant qu'ils n'ont pas été amorcés.
     *
     * `null` n'est pas « des champs vides » : c'est « on ne sait pas encore ce que le compte
     * porte ». Les amorcer avant que le référentiel soit prêt donnerait un dernier verset connu
     * faux, donc des champs faux, et l'écran les afficherait comme s'ils venaient du compte.
     */
    private val fields = MutableStateFlow<GoalFields?>(null)

    private val _state = MutableStateFlow(GoalUiState())

    /** État affichable de l'écran. */
    val state: StateFlow<GoalUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.state, quranState, fields) { app, quran, champs ->
                Triple(app, quran, champs)
            }.collect { (app, quran, champs) ->
                if (quran !is QuranState.Ready) {
                    _state.value = GoalUiState(
                        loading = quran is QuranState.Loading,
                        failure = (quran as? QuranState.Failed)?.let { quranFailureMessage(it.cause) },
                        // Les champs déjà amorcés sont **conservés** pendant un rechargement : les
                        // effacer ferait perdre une saisie en cours pour une panne de référentiel.
                        fields = champs ?: GoalFields(),
                    )
                    return@collect
                }
                val amorces = champs ?: GoalRenderer.seed(app)
                if (champs == null) fields.value = amorces
                _state.value = GoalRenderer.render(app, amorces, today())
            }
        }
    }

    // --- La carte « Je connais déjà » ---------------------------------------

    /** Change l'unité dans laquelle on déclare ses connaissances. */
    fun onKnownUnitSelected(label: String) {
        val unit = GoalUnit.of(label) ?: return
        edit { it.copy(knownUnit = unit, knownDivision = 1) }
    }

    /** Déclare la dernière sourate apprise. Le verset repart à 1 : c'est la sourate qu'on change. */
    fun onSurahSelected(number: Int) {
        edit { it.copy(surah = number, ayah = 1, knownEdited = true) }
    }

    /** Déclare le dernier verset appris de la sourate choisie. */
    fun onAyahSelected(number: Int) {
        edit { it.copy(ayah = number, knownEdited = true) }
    }

    /** Déclare le dernier hizb ou juz’ appris. */
    fun onKnownDivisionSelected(number: Int) {
        edit { it.copy(knownDivision = number, knownEdited = true) }
    }

    // --- La carte « Mon objectif » ------------------------------------------

    /** Change l'unité de l'objectif. Le numéro repart à 1, comme dans le client d'origine. */
    fun onGoalUnitSelected(label: String) {
        val unit = GoalUnit.of(label) ?: return
        edit { it.copy(goalUnit = unit, goalIndex = 1, goalEdited = true) }
    }

    /** Choisit la division visée. */
    fun onGoalSelected(number: Int) {
        edit { it.copy(goalIndex = number, goalEdited = true) }
    }

    /** Bascule entre « Sans date » et « Choisir une date ». Le texte saisi est conservé. */
    fun onDeadlineMode(choisir: Boolean) {
        edit { it.copy(deadlineOn = choisir) }
    }

    /** Retient le texte de la date, **tel quel**. Il n'est validé qu'à l'enregistrement. */
    fun onDeadlineChange(text: String) {
        edit { it.copy(deadline = text) }
    }

    // --- La carte « Mon rythme » --------------------------------------------

    /**
     * Change l'unité de rythme, et pose le rythme qui la représente.
     *
     * Le rythme courant est **remplacé**, comme dans le client d'origine : passer de « 3 versets »
     * à « Par page » ne peut pas garder les versets. Revenir à l'unité d'origine ne restitue donc
     * pas le rythme d'avant.
     */
    fun onPaceUnitSelected(label: String) {
        val unit = GoalPaceUnit.of(label) ?: return
        edit { it.copy(paceUnit = unit, pace = GoalRenderer.defaultPace(unit)) }
    }

    /** Avance ou recule d'un cran dans les rythmes de l'unité courante. */
    fun onPaceShift(delta: Int) {
        edit { it.copy(pace = GoalRenderer.shiftPace(it.pace, it.paceUnit, delta)) }
    }

    // --- L'enregistrement ---------------------------------------------------

    /**
     * Enregistre le programme.
     *
     * La date est refusée **avant** toute écriture, et le refus reste affiché : c'est la seule
     * chose qui empêche d'enregistrer un repère illisible, le domaine ne la lisant jamais.
     *
     * @param onSaved appelé après l'écriture, et **seulement** si elle a eu lieu. C'est
     *   l'appelant qui referme l'écran — un `ViewModel` ne décide pas de la navigation.
     */
    fun onSave(onSaved: () -> Unit) {
        val champs = fields.value ?: return
        if (champs.deadlineOn && !GoalRenderer.validDate(champs.deadline)) {
            edit { it.copy(error = GoalText.BAD_DATE) }
            return
        }
        edit { it.copy(error = null) }
        viewModelScope.launch {
            // `runCatching` : un disque plein ne doit pas emporter l'écran. L'écriture qui échoue
            // laisse le programme tel qu'il était, et l'écran se ferme comme si elle avait eu
            // lieu — c'est l'écart assumé de tous les autres écrans de ce dépôt.
            runCatching {
                repository.mutate { state -> GoalRenderer.save(state, champs, today()) }
            }
            onSaved()
        }
    }

    /** Applique une modification aux champs, en tolérant qu'ils ne soient pas encore amorcés. */
    private fun edit(bloc: (GoalFields) -> GoalFields) {
        fields.update { bloc(it ?: GoalFields()) }
    }

    companion object {
        /** Fabrique rattachée au conteneur, pour que l'écran n'ait pas à le connaître. */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return GoalViewModel(container.userState, container.quranState) as T
                }
            }
    }
}
