package com.msoumaya.deepseekandroid.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msoumaya.deepseekandroid.core.design.component.AppButton
import com.msoumaya.deepseekandroid.core.design.component.AppCard
import com.msoumaya.deepseekandroid.core.design.component.AppField
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.component.AppTitle
import com.msoumaya.deepseekandroid.core.design.component.ArabicText
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.AuthFeedback

// ---------------------------------------------------------------------------
// Écran de bienvenue
// ---------------------------------------------------------------------------
// Portage de `AccountWelcome` de `src/App.tsx`.
//
// Deux écarts assumés avec l'original, tous deux consignés dans `ANDROID_MIGRATION.md` :
//
//  1. le choix d'une photo à l'inscription n'est pas porté ici. Il demande un sélecteur
//     d'images et un envoi vers le stockage Supabase, qui appartiennent à la phase D
//     (« amis et profils ») ; un bouton inerte serait pire que son absence ;
//  2. le glyphe ۞ est rendu avec la police arabe embarquée plutôt qu'avec la police
//     d'interface. C'est un caractère arabe, et Amiri le porte à coup sûr — alors que
//     Cormorant, qui n'a pas de couverture arabe, laisserait le système choisir un repli.
// ---------------------------------------------------------------------------

/**
 * Écran de bienvenue : trois choix, puis le formulaire.
 *
 * @param failure raison pour laquelle la porte est fermée alors qu'un compte est déjà connu.
 *   Distinct du message du formulaire : celui-ci parle d'une tentative, celui-là d'un compte
 *   dont les données n'ont pas pu être récupérées.
 */
@Composable
fun AccountWelcomeScreen(
    form: AuthUiState,
    failure: String?,
    onOpenForm: (AuthMode) -> Unit,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
    onPasswordLink: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val focus = LocalFocusManager.current

    // `BoxWithConstraints` sert uniquement à centrer verticalement **tout en gardant le
    // défilement** : une colonne défilante est mesurée sans hauteur maximale, donc
    // `Arrangement.Center` n'y aurait aucun espace libre à répartir. La hauteur minimale
    // imposée ici est celle de la fenêtre — qui se réduit quand le clavier s'ouvre, ce qui
    // laisse le formulaire accessible.
    BoxWithConstraints(
        // Le fond est peint ici, et non laissé au fond de fenêtre : celui-ci est fixé en clair
        // dans le thème Android, alors que l'utilisateur peut avoir choisi un thème sombre.
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.cream),
    ) {
        val minHeight = maxHeight

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight)
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 30.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ArabicText(text = "۞", fontSize = 36.sp, color = colors.green)
            AppTitle(text = "Bienvenue")
            AppLabel(
                text = "Crée ton compte pour retrouver ton apprentissage sur tous tes appareils.",
                selectable = false,
                color = colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )

            if (failure != null) {
                Spacer(Modifier.height(AppTheme.spacing.lg))
                AppLabel(
                    text = failure,
                    selectable = false,
                    color = colors.red,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(AppTheme.spacing.xxl))

            AppCard(modifier = Modifier.fillMaxWidth()) {
                if (form.mode == null) {
                    WelcomeChoices(
                        busy = form.busy,
                        onOpenForm = onOpenForm,
                        onRetry = onRetry,
                    )
                } else {
                    CredentialsForm(
                        form = form,
                        onEmail = onEmail,
                        onPassword = onPassword,
                        onSubmit = {
                            focus.clearFocus()
                            onSubmit()
                        },
                        onResend = onResend,
                        onPasswordLink = onPasswordLink,
                        onBack = {
                            focus.clearFocus()
                            onBack()
                        },
                    )
                }
            }
        }
    }
}

/** Les trois choix de départ. */
@Composable
private fun WelcomeChoices(
    busy: Boolean,
    onOpenForm: (AuthMode) -> Unit,
    onRetry: () -> Unit,
) {
    AppButton(
        text = "Se connecter",
        enabled = !busy,
        onClick = { onOpenForm(AuthMode.LOGIN) },
    )
    AppButton(
        text = "Créer mon compte",
        secondary = true,
        enabled = !busy,
        onClick = { onOpenForm(AuthMode.SIGNUP) },
    )
    AppButton(
        text = "Réessayer la restauration de ma session",
        secondary = true,
        small = true,
        enabled = !busy,
        onClick = onRetry,
    )
}

/** Adresse, mot de passe, et les actions qui vont avec. */
@Composable
private fun CredentialsForm(
    form: AuthUiState,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
    onPasswordLink: () -> Unit,
    onBack: () -> Unit,
) {
    AppLabel(
        text = form.formTitle,
        selectable = false,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 12.dp),
    )

    AppField(
        value = form.email,
        onValueChange = onEmail,
        placeholder = "Adresse e-mail",
        enabled = !form.busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
    )

    AppField(
        value = form.password,
        onValueChange = onPassword,
        placeholder = form.passwordHint,
        enabled = !form.busy,
        secure = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )

    AppButton(
        text = form.submitLabel,
        enabled = form.submitEnabled,
        onClick = onSubmit,
    )

    // « Mot de passe oublié » n'apparaît qu'à la connexion : à l'inscription, il n'y a pas
    // encore de mot de passe à retrouver. Repris de l'original.
    if (form.mode == AuthMode.LOGIN) {
        AppButton(
            text = "Mot de passe oublié",
            secondary = true,
            small = true,
            enabled = form.emailReady,
            onClick = onPasswordLink,
        )
    }

    // Le renvoi n'apparaît qu'après un premier message, comme dans l'original : le proposer
    // d'emblée inviterait à renvoyer un courriel qui vient peut-être d'arriver.
    if (form.mode == AuthMode.SIGNUP && form.feedback != null) {
        AppButton(
            text = "Renvoyer la confirmation",
            secondary = true,
            small = true,
            enabled = form.emailReady,
            onClick = onResend,
        )
    }

    form.feedback?.let { feedback ->
        val text = when (feedback) {
            AuthFeedback.Proceed -> null
            is AuthFeedback.Info -> feedback.text
            is AuthFeedback.Error -> feedback.text
        }
        if (text != null) {
            AppLabel(
                text = text,
                selectable = false,
                color = if (feedback is AuthFeedback.Error) AppTheme.colors.red else AppTheme.colors.text,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }

    AppButton(
        text = "Retour",
        secondary = true,
        small = true,
        enabled = !form.busy,
        onClick = onBack,
    )
}
