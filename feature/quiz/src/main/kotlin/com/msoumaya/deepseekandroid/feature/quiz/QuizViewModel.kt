package com.msoumaya.deepseekandroid.feature.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.repository.QuizRepository
import com.msoumaya.deepseekandroid.core.data.repository.SocialState
import com.msoumaya.deepseekandroid.core.domain.Quiz
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Quiz
// ---------------------------------------------------------------------------
// Portage de `QuizScreen` (`src/ui/QuizScreen.tsx`).
//
// Ce fichier ne contient que le câblage : il relie l'état du dépôt du Quiz, la liste d'amis de
// l'espace social et la sélection de l'écran, puis délègue **tout** le calcul à `QuizRenderer`.
// Le calcul vit là-bas parce qu'il est pur, donc éprouvable sans coroutine ni horloge.
//
// **La liste d'amis vient d'un autre dépôt, et c'est délibéré.** L'écran de création d'un défi
// propose les amis **acceptés**, que seul l'espace social connaît. Le `ViewModel` extrait donc
// `SocialState.links` et le passe au renderer en `List<FriendLink>` : le renderer, lui, ne
// connaît ni `SocialState` ni `core:data`, donc il reste pur.
//
// **L'identité du Quiz vient du dépôt du Quiz, et non du profil social.** `QuizState.ownerId` est
// publié par le dépôt, qui le tient de la session : le profil d'`amis` n'est pas toujours chargé,
// et ne l'est pas du tout hors configuration Supabase. Se tromper d'identité afficherait
// « à toi de jouer » sur un défi auquel on a déjà répondu, et le nom du mauvais adversaire.
//
// **La vue et la saisie vivent ici, et non dans l'écran.** `view`, `historyDay`, `challengeId`,
// `friendId`, `quizSetId` et `count` sont publiés avec le reste de l'état : les garder dans
// l'écran ferait deux sources de vérité — la sélection affichée et les chiffres calculés — qui
// pourraient diverger sans que rien ne le dise. C'est la même règle qu'à l'écran « Progrès » pour
// la période.
//
// **Ce qui n'est pas encore là.** Le client d'origine ouvre l'écran sur un défi précis depuis une
// notification ; il n'y a pas encore de notifications, donc la route reçoit ses deux arguments
// mais rien ne les produit hormis la conversation et l'écran des amis.
// ---------------------------------------------------------------------------

/**
 * Prépare l'affichage de l'écran « Quiz ».
 *
 * @param repository source de l'état : le disque d'abord, le serveur ensuite.
 * @param socialState état de l'espace social, **observé** pour la seule liste d'amis. L'observer
 *   plutôt que de le lire une fois est ce qui remplit l'écran de création quand la liste arrive
 *   après l'ouverture.
 * @param initialFriend ami à défier d'emblée, ou `null`. Vient de la route.
 * @param initialChallenge défi à ouvrir d'emblée, ou `null`. Vient de la route.
 */
class QuizViewModel(
    private val repository: QuizRepository,
    socialState: StateFlow<SocialState>,
    initialFriend: String? = null,
    initialChallenge: String? = null,
) : ViewModel() {

    /**
     * Les deux arguments de la route, ramenés à `null` quand ils sont vides.
     *
     * L'original écrit `initialFriend?'create':'home'` : une chaîne **vide** y est donc traitée
     * comme absente, et `friendId` vaut `''` — ce qui désactive le bouton de lancement, parce
     * que `!''` est vrai en JavaScript. En Kotlin, `""` est une valeur comme une autre : sans ce
     * nettoyage, un argument vide ouvrirait l'écran de création avec un ami **déjà choisi**.
     */
    private val ami = initialFriend?.takeIf { it.isNotBlank() }
    private val defi = initialChallenge?.takeIf { it.isNotBlank() }

    private val selection = MutableStateFlow(
        QuizSelection(
            view = QuizRenderer.initialView(ami, defi),
            friendId = ami,
            challengeId = defi,
        ),
    )

    private val _state = MutableStateFlow(QuizUiState())

    /** État affichable de l'écran. */
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // **Un seul `collect` sur les trois sources.** Deux collectes séparées — l'une pour
            // l'état du Quiz, l'autre pour la sélection — liraient deux valeurs différentes au
            // moment où la vue change, et publieraient un état intermédiaire que personne n'a
            // demandé : la coquille d'une vue sur le contenu d'une autre.
            combine(repository.state, socialState, selection) { quiz, social, choix ->
                Triple(quiz, social, choix)
            }.collect { (quiz, social, choix) ->
                // Le renderer ne reçoit que ce qu'il sait lire. `notice` et `failure` viennent du
                // dépôt et ne se recalculent pas : ils sont recopiés tels quels, parce qu'eux
                // seuls savent si un échec laisse l'écran vide ou s'il n'est qu'un avis.
                _state.value = QuizRenderer.render(
                    snapshot = quiz.snapshot ?: Quiz.emptySnapshot(Quiz.quizDay()),
                    userId = quiz.ownerId,
                    selection = choix,
                    friends = social.links,
                    busy = quiz.busy,
                ).copy(loading = quiz.loading, notice = quiz.notice, failure = quiz.failure)
            }
        }

        // L'original relit à l'ouverture (`useEffect(() => { void refreshQuiz() }, [userId])`).
        // Le dépôt relit déjà quand le compte change ; cette relecture-ci couvre l'ouverture de
        // l'écran **plus tard**, quand l'instantané a vieilli. Elle ne pose pas `loading` sur une
        // seconde lecture, donc rien ne clignote.
        viewModelScope.launch { repository.refresh() }
    }

    // --- La coquille ---------------------------------------------------------------------

    /** Ouvre la question du jour, ou celle d'un jour de l'historique. */
    fun onOpenDaily(day: String? = null) {
        selection.value = selection.value.copy(view = QuizView.DAILY, historyDay = day)
    }

    /** Ouvre une vue de la coquille. */
    fun onOpenView(view: QuizView) {
        selection.value = selection.value.copy(view = view)
    }

    /** Ouvre un défi précis, depuis la liste ou depuis l'accueil. */
    fun onOpenChallenge(id: String) {
        selection.value = selection.value.copy(view = QuizView.CHALLENGE, challengeId = id)
    }

    /**
     * « Retour » **à l'intérieur** de l'écran.
     *
     * Depuis l'accueil, cette méthode ne fait rien : c'est l'écran qui ferme, sur
     * [QuizUiState.backCloses]. Le renderer décide de l'un et de l'autre au même endroit, donc
     * les deux ne peuvent pas se contredire.
     */
    fun onBack() {
        val retour = QuizRenderer.back(selection.value.view)
        if (retour.close) return
        selection.value = selection.value.copy(
            view = retour.view,
            // Le jour consulté est oublié en revenant à l'accueil, sinon rouvrir l'historique
            // montrerait la question d'un autre jour.
            historyDay = if (retour.forgetDay) null else selection.value.historyDay,
        )
    }

    // --- La création d'un défi -----------------------------------------------------------

    /** Choisit l'ami à défier. */
    fun onSelectFriend(id: String) {
        selection.value = selection.value.copy(friendId = id)
    }

    /** Choisit la longueur du défi. */
    fun onSelectCount(count: Int) {
        selection.value = selection.value.copy(count = count)
    }

    /**
     * Choisit un quiz thématique, ou `null` pour des questions aléatoires.
     *
     * L'original **force dix questions** quand un thème est choisi (`setQuizSet(q.id);setCount(10)`)
     * : un quiz thématique contient exactement dix questions, donc en demander cinq n'aurait pas
     * de sens. La règle est reproduite ici plutôt que dans l'écran, parce que c'est une
     * contrainte de données et non une disposition.
     */
    fun onSelectSet(id: String?) {
        selection.value = selection.value.copy(
            quizSetId = id,
            count = if (id == null) selection.value.count else THEMED_COUNT,
        )
    }

    /** Lance le défi contre l'ami choisi, et ouvre le défi créé. */
    fun onCreateChallenge() {
        val choix = selection.value
        val opponent = choix.friendId ?: return
        viewModelScope.launch {
            val id = repository.createChallenge(opponent, choix.count, choix.quizSetId)
            // Un échec laisse l'écran où il est, avec l'avis du dépôt : changer de vue cacherait
            // le message au moment précis où il faut le lire.
            if (id != null) {
                selection.value = selection.value.copy(view = QuizView.CHALLENGE, challengeId = id)
            }
        }
    }

    // --- Les réponses --------------------------------------------------------------------

    /** Enregistre la réponse à la question du jour affichée. */
    fun onAnswerDaily(answerId: String) {
        val snapshot = repository.state.value.snapshot ?: return
        val question = QuizRenderer.questionFor(snapshot, selection.value.historyDay) ?: return
        viewModelScope.launch { repository.answerDaily(question, answerId) }
    }

    /** Répond à la question suivante du défi ouvert. */
    fun onAnswerChallenge(answerId: String) {
        val choix = selection.value
        val id = choix.challengeId ?: return
        val snapshot = repository.state.value.snapshot ?: return
        val owner = repository.state.value.ownerId ?: return
        val challenge = snapshot.challenges.firstOrNull { it.id == id } ?: return
        // La question suivante se résout par la règle du renderer : l'état affichable ne porte
        // que du texte, et non l'identifiant de la question.
        val question = QuizRenderer.nextQuestion(challenge, owner) ?: return
        viewModelScope.launch { repository.answerChallenge(id, question.id, answerId) }
    }

    // --- Les notifications ---------------------------------------------------------------

    /**
     * Bascule les notifications du Quiz.
     *
     * **C'est l'inverse de ce qui est affiché qui part.** L'original envoie
     * `p_enabled: data.notificationsEnabled===false`, alors que l'interrupteur s'affiche coché
     * sur `notificationsEnabled!==false` : une valeur absente veut dire « activées », donc le
     * premier appui **désactive**. Voir `NotificationLine`.
     *
     * Le fuseau est celui de l'appareil, comme `Intl.DateTimeFormat().resolvedOptions().timeZone`
     * dans l'original ; c'est le défaut de `QuizRepository.setNotifications`.
     */
    fun onToggleNotifications() {
        val displayed = state.value.home?.notifications?.enabled ?: return
        viewModelScope.launch { repository.setNotifications(enabled = !displayed) }
    }

    /** Relit l'instantané, à la demande. */
    fun onRefresh() {
        viewModelScope.launch { repository.refresh() }
    }

    companion object {
        /** Longueur imposée par un quiz thématique : `quiz_sets` en contient exactement dix. */
        private const val THEMED_COUNT = 10

        /** Fabrique rattachée au conteneur, pour que l'écran n'ait pas à le connaître. */
        fun factory(
            container: AppContainer,
            friendId: String? = null,
            challengeId: String? = null,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return QuizViewModel(container.quiz, container.social.state, friendId, challengeId) as T
            }
        }
    }
}
