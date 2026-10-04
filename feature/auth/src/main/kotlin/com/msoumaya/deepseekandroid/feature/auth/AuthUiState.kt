package com.msoumaya.deepseekandroid.feature.auth

import androidx.compose.runtime.Immutable
import com.msoumaya.deepseekandroid.core.domain.AuthFeedback
import com.msoumaya.deepseekandroid.core.domain.AuthInput

// ---------------------------------------------------------------------------
// État du formulaire de connexion
// ---------------------------------------------------------------------------
// Portage de l'état local de `AccountWelcome` dans `src/App.tsx` : `mode`, `email`, `password`,
// `busy`, `message`.
//
// La logique d'activation des boutons est sortie du composable et rassemblée ici, parce que
// c'est la seule partie de cet écran qui puisse être fausse sans que cela se voie : un bouton
// actif pour une saisie que la règle refuse fait cliquer pour rien, et un bouton inactif pour
// une saisie valide bloque sans explication. Étant une valeur pure, elle est éprouvée
// directement.
// ---------------------------------------------------------------------------

/** Intention du formulaire. `null` correspond à l'écran d'accueil, avant tout choix. */
enum class AuthMode { LOGIN, SIGNUP }

@Immutable
data class AuthUiState(
    val mode: AuthMode? = null,
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    /** Dernier message à afficher. `AuthFeedback.Proceed` n'y est jamais stocké. */
    val feedback: AuthFeedback? = null,
) {

    /**
     * Vrai si le bouton principal doit réagir.
     *
     * Repris du composable d'origine : `busy || !email.includes('@') || (inscription ?
     * password.length < 6 : !password)`.
     *
     * Le contrôle reste **volontairement lâche** — la présence d'un `@` suffit. Le rendre plus
     * strict désactiverait le bouton pour une adresse comme `jean@laposte`, sans rien dire à
     * l'utilisateur de ce qui manque : il verrait un bouton grisé et aucun message. En le
     * laissant actif, l'appui déclenche le contrôle complet, qui explique ce qui ne va pas.
     */
    val submitEnabled: Boolean
        get() = when {
            busy -> false
            mode == null -> false
            !email.contains('@') -> false
            mode == AuthMode.SIGNUP -> password.length >= AuthInput.MIN_PASSWORD
            else -> password.isNotEmpty()
        }

    /** Vrai si les actions secondaires qui n'ont besoin que de l'adresse sont utilisables. */
    val emailReady: Boolean get() = !busy && email.contains('@')

    val formTitle: String
        get() = if (mode == AuthMode.SIGNUP) "Créer mon compte" else "Se connecter"

    val submitLabel: String
        get() = when {
            busy -> "Connexion…"
            mode == AuthMode.SIGNUP -> "Créer mon compte"
            else -> "Se connecter"
        }

    /** Le seuil est écrit une seule fois, dans le domaine, et affiché depuis là. */
    val passwordHint: String
        get() = if (mode == AuthMode.SIGNUP) {
            "Mot de passe (au moins ${AuthInput.MIN_PASSWORD} caractères)"
        } else {
            "Mot de passe"
        }
}
