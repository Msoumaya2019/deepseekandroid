package com.msoumaya.deepseekandroid.core.domain

// ---------------------------------------------------------------------------
// Ce qu'il faut dire après une tentative de connexion
// ---------------------------------------------------------------------------
// Portage des messages de `AccountWelcome` dans `src/App.tsx`.
//
// Le dépôt d'origine n'avait que deux phrases, parce que `signIn()` lui rendait `null` dans
// tous les cas d'échec et que seul le formulaire savait s'il tentait une inscription :
//
//   - inscription acceptée sans session → « Un courriel de confirmation t'a été envoyé… »
//   - tout le reste                      → « Connexion impossible. Vérifie ton adresse et ton
//                                          mot de passe. »
//
// Ce dernier message est faux dans un cas très courant : quand le téléphone n'a pas de réseau,
// il accuse le mot de passe. La passerelle Android distingue déjà les causes
// (`AuthOutcome`), et les confondre à l'affichage annulerait ce travail. Chaque cause reçoit
// donc sa phrase, et les deux phrases d'origine sont conservées mot pour mot là où elles
// étaient justes.
// ---------------------------------------------------------------------------

/** Réaction attendue de l'écran après une tentative. */
sealed interface AuthFeedback {

    /** La session est ouverte : l'écran doit passer la main à la coquille. */
    data object Proceed : AuthFeedback

    /** L'action a réussi mais demande quelque chose à l'utilisateur (confirmation, lien…). */
    data class Info(val text: String) : AuthFeedback

    /** L'action a échoué. Le texte dit quoi corriger. */
    data class Error(val text: String) : AuthFeedback
}

/**
 * Traduit une issue d'authentification en message affichable.
 *
 * Fonction pure : aucune dépendance à Android ni au réseau, donc éprouvable directement.
 *
 * @param registering vrai si la tentative était une inscription. Deux messages en dépendent :
 *   « adresse déjà utilisée » se dit différemment selon qu'on essaie d'entrer ou de créer, et
 *   une adresse non confirmée est une étape normale après une inscription mais un blocage
 *   incompréhensible après une connexion.
 */
object AuthFeedbackRules {

    fun of(outcome: AuthOutcome, registering: Boolean): AuthFeedback = when (outcome) {
        AuthOutcome.Success -> AuthFeedback.Proceed

        // Conservé du dépôt d'origine, mot pour mot.
        AuthOutcome.EmailConfirmationRequired -> AuthFeedback.Info(
            "Un courriel de confirmation t’a été envoyé. Ouvre le lien sur ce téléphone, " +
                "puis commence ton programme.",
        )

        AuthOutcome.InvalidCredentials -> AuthFeedback.Error(
            "Connexion impossible. Vérifie ton adresse et ton mot de passe.",
        )

        AuthOutcome.EmailNotConfirmed -> AuthFeedback.Error(
            if (registering) {
                "Ce compte existe déjà mais son adresse n’est pas confirmée. " +
                    "Ouvre le lien reçu par courriel."
            } else {
                "Ton adresse n’est pas encore confirmée. Ouvre le lien reçu par courriel, " +
                    "puis connecte-toi."
            },
        )

        AuthOutcome.EmailAlreadyUsed -> AuthFeedback.Error(
            if (registering) {
                "Cette adresse possède déjà un compte. Utilise « Se connecter »."
            } else {
                "Cette adresse possède déjà un compte."
            },
        )

        AuthOutcome.AccountDisabled -> AuthFeedback.Error(
            "Ce compte est suspendu. Contacte l’administrateur.",
        )

        AuthOutcome.SignUpDisabled -> AuthFeedback.Error(
            "Les inscriptions sont fermées sur ce serveur. Contacte l’administrateur.",
        )

        AuthOutcome.WeakPassword -> AuthFeedback.Error(
            "Mot de passe trop faible : choisis-en un plus long.",
        )

        AuthOutcome.Offline -> AuthFeedback.Error(
            "Pas de réseau : la connexion n’a pas pu être tentée. " +
                "Vérifie ta connexion, puis réessaie.",
        )

        // Ce n'est pas une panne : réessayer n'y changerait rien, et le dire évite de faire
        // chercher une panne réseau là où il n'y a simplement pas de serveur configuré.
        AuthOutcome.NotConfigured -> AuthFeedback.Error(
            "Aucun serveur n’est configuré dans cette version. L’application fonctionne " +
                "hors ligne, mais les comptes sont indisponibles.",
        )

        AuthOutcome.InvalidEmail -> AuthFeedback.Error(
            "Cette adresse e-mail semble incomplète.",
        )

        AuthOutcome.ShortPassword -> AuthFeedback.Error(
            "Le mot de passe doit contenir au moins 6 caractères.",
        )

        is AuthOutcome.Unexpected -> AuthFeedback.Error(
            outcome.message.takeIf { it.isNotBlank() } ?: "Une erreur est survenue. Réessaie.",
        )
    }
}
