package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.remote.AuthGateway
import com.msoumaya.deepseekandroid.core.data.remote.OwnerStore
import com.msoumaya.deepseekandroid.core.domain.AuthInput
import com.msoumaya.deepseekandroid.core.domain.AuthOutcome
import com.msoumaya.deepseekandroid.core.domain.AuthPhase
import com.msoumaya.deepseekandroid.core.domain.AuthStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * Connexion, inscription, déconnexion — et tenue à jour du propriétaire courant.
 *
 * Le point délicat est le **propriétaire** : c'est lui qui décide quel fichier d'état est lu
 * et quel état distant est accepté. Il est écrit ici, à partir de la seule source fiable,
 * c'est-à-dire l'état réel de la session, et non la valeur saisie par l'utilisateur.
 *
 * Un mot de passe refusé n'écrit rien : le propriétaire ne change que si une session existe
 * réellement. C'est ce qui évite qu'une tentative ratée fasse basculer l'application sur le
 * fichier d'état d'un autre compte.
 */
class AuthRepository(
    private val gateway: AuthGateway,
    private val session: OwnerStore,
) {

    /**
     * État de la session, doublé de l'adoption du propriétaire.
     *
     * **Une session absente n'efface pas le propriétaire**, et c'est la règle qui rend le
     * lancement hors ligne possible. Un jeton expiré, un téléphone sans réseau et un projet
     * injoignable produisent tous les trois « pas de session » : effacer le propriétaire dans
     * ces cas ferait disparaître la progression locale de l'écran, alors que c'est exactement
     * ce que l'application doit afficher quand elle n'a pas de réseau.
     *
     * La déconnexion est donc **explicite** : c'est [signOut] qui efface, et rien d'autre. Un
     * utilisateur dont le jeton a expiré retrouve sa progression et peut se reconnecter ; il
     * n'est pas mis dehors par une panne de réseau.
     *
     * `onEach` est idempotent : réécrire la même valeur n'émet rien de neuf, donc plusieurs
     * collecteurs ne provoquent pas d'écritures en cascade.
     */
    val status: Flow<AuthStatus> = gateway.status.onEach { remember(it) }

    /** Attend que la session enregistrée ait été relue, sans bloquer l'affichage. */
    suspend fun awaitReady() = gateway.awaitReady()

    /**
     * Reprend le propriétaire depuis la session réellement ouverte, **sans jamais l'effacer**.
     *
     * Appelé au démarrage pour le cas d'une session restaurée depuis le trousseau sans que le
     * propriétaire ait été écrit — installation neuve avec restauration de sauvegarde, par
     * exemple. L'absence de session ne dit rien : c'est [signOut] qui déconnecte, pas le
     * silence du réseau. Voir [status].
     */
    suspend fun refreshOwner() {
        gateway.currentUserId()?.let { session.setOwner(it) }
    }

    /**
     * Propriétaire courant, tel qu'il a été retenu.
     *
     * C'est l'identifiant du fichier d'état à ouvrir, et il ne dépend pas du réseau : après une
     * déconnexion il vaut `null`, après une connexion réussie il vaut l'utilisateur.
     */
    suspend fun currentOwner(): String? = session.currentOwner()

    suspend fun signIn(email: String, password: String): AuthOutcome {
        // À la connexion, seul le vide est refusé localement : la longueur d'un mot de passe
        // existant appartient au serveur, pas à cette application. Voir
        // `AuthInput.emptyPasswordProblem`.
        val refused = AuthInput.emailProblem(email) ?: AuthInput.emptyPasswordProblem(password)
        if (refused != null) return refused

        val outcome = gateway.signIn(AuthInput.normalizeEmail(email), password)
        if (outcome == AuthOutcome.Success) session.setOwner(gateway.currentUserId())
        return outcome
    }

    suspend fun signUp(email: String, password: String): AuthOutcome {
        val refused = AuthInput.emailProblem(email) ?: AuthInput.passwordProblem(password)
        if (refused != null) return refused

        val outcome = gateway.signUp(AuthInput.normalizeEmail(email), password)
        if (outcome != AuthOutcome.Success) return outcome

        val userId = gateway.currentUserId()
        return if (userId == null) {
            // Inscription acceptée sans session : le projet exige une confirmation par
            // courriel. Le dire, plutôt que d'afficher un succès suivi d'un écran figé.
            AuthOutcome.EmailConfirmationRequired
        } else {
            session.setOwner(userId)
            AuthOutcome.Success
        }
    }

    /**
     * Déconnexion.
     *
     * L'état du compte n'est **pas** effacé : il reste sur l'appareil, et c'est délibéré.
     * D'une part une reconnexion hors ligne retrouve ainsi la progression ; d'autre part
     * l'isolation entre comptes ne dépend pas de cet effacement mais du `userId` porté par
     * l'état, que le domaine vérifie avant tout usage (`Program.accountState`). Un second
     * utilisateur ne peut donc pas lire le programme du premier, même sans purge.
     */
    suspend fun signOut() {
        gateway.signOut()
        session.setOwner(null)
    }

    /**
     * Renvoie le courriel de confirmation d'inscription.
     *
     * Le contrôle d'adresse est fait ici, comme pour la connexion : inutile d'appeler le
     * serveur pour une adresse manifestement incomplète.
     */
    suspend fun resendSignUpConfirmation(email: String): AuthOutcome {
        AuthInput.emailProblem(email)?.let { return it }
        return gateway.resendSignUpConfirmation(AuthInput.normalizeEmail(email))
    }

    /** Envoie un lien de création ou de changement de mot de passe. */
    suspend fun requestPasswordReset(email: String): AuthOutcome {
        AuthInput.emailProblem(email)?.let { return it }
        return gateway.requestPasswordReset(AuthInput.normalizeEmail(email))
    }

    private suspend fun remember(status: AuthStatus) {
        // Seule une session **ouverte** écrit le propriétaire. Les trois autres états
        // (`INITIALIZING`, `SIGNED_OUT`, `REFRESH_FAILURE`) n'ont rien à dire sur le
        // propriétaire : ils décrivent l'état d'un jeton, pas la propriété d'une progression.
        if (status.phase == AuthPhase.SIGNED_IN) session.setOwner(status.userId)
    }
}
