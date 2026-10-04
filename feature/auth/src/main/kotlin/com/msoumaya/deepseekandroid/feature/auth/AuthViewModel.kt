package com.msoumaya.deepseekandroid.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.repository.AuthRepository
import com.msoumaya.deepseekandroid.core.data.repository.SyncResult
import com.msoumaya.deepseekandroid.core.data.repository.UserRepository
import com.msoumaya.deepseekandroid.core.domain.AccountAccess
import com.msoumaya.deepseekandroid.core.domain.AccountStage
import com.msoumaya.deepseekandroid.core.domain.AuthFeedback
import com.msoumaya.deepseekandroid.core.domain.AuthFeedbackRules
import com.msoumaya.deepseekandroid.core.domain.AuthOutcome
import com.msoumaya.deepseekandroid.core.model.AppState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * La porte d'entrée : décide ce qui s'affiche, et porte le formulaire de connexion.
 *
 * Un seul ViewModel pour les deux, parce qu'ils ne peuvent pas vivre l'un sans l'autre :
 * l'ouverture de la coquille dépend de la connexion, et la connexion n'a de sens que parce
 * qu'elle ouvre la coquille.
 *
 * **Le point délicat est le tirage du serveur.** Un compte connu dont aucun état n'a encore été
 * rapatrié ne doit pas ouvrir la coquille : elle afficherait un programme vide, et la première
 * séance validée dans ce programme vide serait ensuite poussée au serveur, écrasant le vrai
 * compte. La décision appartient à `AccountAccess`, qui s'appuie sur l'existence du fichier
 * d'état ; ce ViewModel ne fait que lui fournir ce témoin et déclencher le tirage quand il
 * manque.
 *
 * Rien ici n'attend le réseau avant d'afficher : le propriétaire vient de DataStore, et la
 * relecture de la session est un travail de fond lancé en parallèle.
 */
class AuthViewModel(
    private val auth: AuthRepository,
    private val userState: UserRepository,
) : ViewModel() {

    private val _form = MutableStateFlow(AuthUiState())
    val form: StateFlow<AuthUiState> = _form.asStateFlow()

    private val _stage = MutableStateFlow(AccountStage.CHECKING)
    val stage: StateFlow<AccountStage> = _stage.asStateFlow()

    /**
     * Raison pour laquelle la porte reste fermée, quand elle ne peut pas s'ouvrir.
     *
     * Distinct du message du formulaire : celui-ci parle d'une tentative de connexion, celui-là
     * d'un compte déjà connu dont les données n'ont pas pu être récupérées.
     */
    private val _gateFailure = MutableStateFlow<String?>(null)
    val gateFailure: StateFlow<String?> = _gateFailure.asStateFlow()

    /** Empêche deux tirages simultanés — le flux d'état peut émettre plusieurs fois. */
    private var fetching = false

    init {
        viewModelScope.launch {
            // Lecture locale d'abord, et volontairement sans `awaitReady()` : le propriétaire
            // vient de DataStore et de la session déjà en mémoire. Attendre l'initialisation
            // du client Supabase ferait dépendre l'affichage d'un appel réseau, ce que
            // l'exigence « le lancement ne doit jamais être bloqué par le réseau » interdit.
            auth.refreshOwner()
            userState.state.collect(::evaluate)
        }
        viewModelScope.launch {
            // La relecture de la session peut nécessiter un rafraîchissement de jeton, donc du
            // réseau. Elle se fait en fond et ne conditionne pas ce qui est affiché.
            auth.awaitReady()
            auth.refreshOwner()
        }
    }

    // ------------------------------------------------------------------
    // Décision d'ouverture
    // ------------------------------------------------------------------

    private suspend fun evaluate(state: AppState) {
        val owner = state.userId
        // Le témoin est lu avant la décision : c'est lui qui distingue « compte jamais
        // rapatrié » de « compte à la progression vide ».
        val hasLocalState = !owner.isNullOrBlank() && userState.hasStoredState(owner)

        when (AccountAccess.stage(state, hasLocalState)) {
            AccountStage.READY -> {
                _gateFailure.value = null
                _stage.value = AccountStage.READY
            }

            AccountStage.WELCOME -> {
                _gateFailure.value = null
                _stage.value = AccountStage.WELCOME
            }

            AccountStage.CHECKING -> {
                _stage.value = AccountStage.CHECKING
                // Seul cas où le tirage est indispensable, et il ne peut viser qu'un compte
                // identifié : quand l'état n'est pas encore lu, il n'y a personne à interroger.
                if (!owner.isNullOrBlank()) fetch(owner)
            }
        }
    }

    /**
     * Lit le serveur pour [owner], puis laisse le flux d'état rouvrir la porte.
     *
     * Une réussite écrit l'état local, donc le flux émet et `evaluate` repasse en `READY` : il
     * n'y a rien à forcer. Un échec, lui, n'émet rien — sans le repli explicite ci-dessous,
     * l'écran d'attente resterait affiché indéfiniment.
     */
    private fun fetch(owner: String) {
        if (fetching) return
        fetching = true
        viewModelScope.launch {
            try {
                val failure = when (val result = userState.sync()) {
                    SyncResult.Synced -> null
                    SyncResult.Offline -> "Pas de réseau : ta progression n’a pas pu être récupérée."
                    SyncResult.NotConfigured -> "Aucun serveur n’est configuré dans cette version."
                    SyncResult.SignedOut -> "La session a expiré. Reconnecte-toi."
                    is SyncResult.Failed -> "Récupération impossible : ${result.message}"
                }
                _gateFailure.value = failure
                if (failure != null) _stage.value = AccountStage.WELCOME
            } finally {
                fetching = false
            }
        }
    }

    /**
     * Réessaie la restauration complète : relecture de la session, puis tirage.
     *
     * C'est le bouton « Réessayer la restauration de ma session » du client d'origine, qui
     * relançait `restoreSession` et non le seul tirage.
     */
    fun retry() {
        if (fetching) return
        viewModelScope.launch {
            auth.awaitReady()
            auth.refreshOwner()
            val owner = auth.currentOwner()
            if (owner.isNullOrBlank()) {
                _gateFailure.value = "Aucune session enregistrée sur ce téléphone."
                _stage.value = AccountStage.WELCOME
                return@launch
            }
            _gateFailure.value = null
            _stage.value = AccountStage.CHECKING
            fetch(owner)
        }
    }

    // ------------------------------------------------------------------
    // Formulaire
    // ------------------------------------------------------------------

    fun openForm(mode: AuthMode) {
        _form.value = _form.value.copy(mode = mode, feedback = null)
    }

    fun closeForm() {
        _form.value = AuthUiState()
    }

    fun setEmail(value: String) {
        _form.value = _form.value.copy(email = value, feedback = null)
    }

    fun setPassword(value: String) {
        _form.value = _form.value.copy(password = value, feedback = null)
    }

    fun dismissFeedback() {
        _form.value = _form.value.copy(feedback = null)
    }

    /** Tente la connexion ou l'inscription selon le mode courant. */
    fun submit() {
        val current = _form.value
        val mode = current.mode ?: return
        if (!current.submitEnabled) return

        _form.value = current.copy(busy = true, feedback = null)
        viewModelScope.launch {
            val registering = mode == AuthMode.SIGNUP
            val outcome = if (registering) {
                auth.signUp(current.email, current.password)
            } else {
                auth.signIn(current.email, current.password)
            }
            apply(AuthFeedbackRules.of(outcome, registering))
        }
    }

    /** Renvoie le courriel de confirmation d'inscription. */
    fun resendConfirmation() {
        val current = _form.value
        if (!current.emailReady) return

        _form.value = current.copy(busy = true, feedback = null)
        viewModelScope.launch {
            _form.value = _form.value.copy(
                busy = false,
                feedback = notice(
                    outcome = auth.resendSignUpConfirmation(current.email),
                    success = "Courriel de confirmation renvoyé.",
                    registering = true,
                ),
            )
        }
    }

    /** Demande un lien de création ou de changement de mot de passe. */
    fun requestPasswordLink() {
        val current = _form.value
        if (!current.emailReady) return

        _form.value = current.copy(busy = true, feedback = null)
        viewModelScope.launch {
            _form.value = _form.value.copy(
                busy = false,
                feedback = notice(
                    outcome = auth.requestPasswordReset(current.email),
                    success = "Un lien de réinitialisation a été envoyé.",
                    registering = false,
                ),
            )
        }
    }

    private fun apply(feedback: AuthFeedback) {
        when (feedback) {
            // La session est ouverte et le propriétaire est déjà écrit par le dépôt : le flux
            // d'état va basculer sur le fichier du compte. On ne force surtout pas l'ouverture
            // ici — c'est la porte qui décide, et elle exige que le serveur ait été lu.
            AuthFeedback.Proceed -> _form.value = AuthUiState()

            is AuthFeedback.Info, is AuthFeedback.Error ->
                _form.value = _form.value.copy(busy = false, feedback = feedback)
        }
    }

    /**
     * Message d'une action secondaire, qui réussit sans ouvrir de session.
     *
     * `AuthFeedbackRules` traduit `Success` en « continuer », ce qui n'a pas de sens pour un
     * renvoi de courriel : le succès y est donc traité ici, avant la règle.
     */
    private fun notice(outcome: AuthOutcome, success: String, registering: Boolean): AuthFeedback =
        if (outcome == AuthOutcome.Success) AuthFeedback.Info(success) else AuthFeedbackRules.of(outcome, registering)

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { AuthViewModel(container.auth, container.userState) }
        }
    }
}
