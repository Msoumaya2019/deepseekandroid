package com.msoumaya.deepseekandroid.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.msoumaya.deepseekandroid.core.data.LocalAppContainer
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.AccountStage

// ---------------------------------------------------------------------------
// Porte d'entrée
// ---------------------------------------------------------------------------
// Portage du ternaire de `src/App.tsx` :
//
//   {accountIntro === 'checking' ? <attente> : accountIntro === 'show' ? <AccountWelcome> :
//    <le reste de l'application>}
//
// La seule différence est que l'écran d'attente est ici **atteignable** : dans le client
// d'origine, `setAccountIntro('checking')` n'est écrit nulle part et l'état initial vaut
// toujours `'show'` ou `'done'`. Sur Android, deux situations l'atteignent réellement — l'état
// local n'est pas encore lu, et un compte connu n'a pas encore été rapatrié. Voir
// `AccountAccess`.
//
// La porte est le **seul** endroit qui décide de l'affichage : elle enveloppe la coquille au
// lieu d'être appelée depuis elle. C'est ce qui garantit qu'aucun écran de l'application ne
// peut être atteint sans compte.
// ---------------------------------------------------------------------------

/**
 * Affiche soit la coquille, soit l'écran d'attente, soit l'écran de bienvenue.
 *
 * @param content la coquille, affichée seulement quand la porte est ouverte.
 */
@Composable
fun AccountGate(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(LocalAppContainer.current)),
    content: @Composable () -> Unit,
) {
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val failure by viewModel.gateFailure.collectAsStateWithLifecycle()

    when (stage) {
        AccountStage.READY -> content()

        AccountStage.CHECKING -> OpeningScreen(modifier)

        AccountStage.WELCOME -> AccountWelcomeScreen(
            form = form,
            failure = failure,
            onOpenForm = viewModel::openForm,
            onEmail = viewModel::setEmail,
            onPassword = viewModel::setPassword,
            onSubmit = viewModel::submit,
            onResend = viewModel::resendConfirmation,
            onPasswordLink = viewModel::requestPasswordLink,
            onBack = viewModel::closeForm,
            onRetry = viewModel::retry,
            modifier = modifier,
        )
    }
}

/**
 * Écran d'attente, affiché pendant la lecture du disque puis pendant le premier tirage.
 *
 * Le fond est peint ici plutôt que laissé au fond de fenêtre : celui-ci est fixé en clair dans
 * le thème Android, alors que l'utilisateur peut avoir choisi un thème sombre. Sans cela, un
 * lancement en thème sombre commencerait par un écran crème.
 */
@Composable
private fun OpeningScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.cream),
        contentAlignment = Alignment.Center,
    ) {
        AppLabel(text = "Ouverture de l’application…", selectable = false)
    }
}
