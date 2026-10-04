package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppState

// ---------------------------------------------------------------------------
// Ouverture de l'application
// ---------------------------------------------------------------------------
// Portage de `initialAccountAccess` et de la machine `accountIntro` de `src/App.tsx`.
//
// L'application d'origine déclare trois états — `'checking'`, `'show'`, `'done'` — mais n'en
// atteint que deux : `setAccountIntro('checking')` n'est écrit nulle part, et la valeur
// initiale vaut `state.userId ? 'done' : 'show'`. La raison est que `loadState()` lit
// AsyncStorage de façon synchrone : au premier rendu, l'état est déjà là, et l'écran
// « Ouverture de l'application… » n'a jamais l'occasion de s'afficher.
//
// Sur Android la lecture du disque est asynchrone (un flux qui démarre par une lecture de
// fichier). L'écran d'attente devient donc **réel**, et il l'est pour une seconde raison :
// l'application ne peut pas s'ouvrir sur un compte dont aucun état n'a encore été rapatrié.
// Ces deux raisons sont les seules différences avec l'original ; la règle qui décide de
// l'accès, elle, est reprise telle quelle.
// ---------------------------------------------------------------------------

/**
 * Ce que l'application doit montrer avant d'afficher la coquille.
 *
 * Correspondance avec les trois valeurs de `accountIntro` du dépôt d'origine :
 * [CHECKING] ↔ `'checking'`, [WELCOME] ↔ `'show'`, [READY] ↔ `'done'`.
 */
enum class AccountStage {
    /** L'état local n'a pas encore été lu : rien ne permet encore de trancher. */
    CHECKING,

    /** Aucun compte n'est enregistré : il faut passer par l'écran de bienvenue. */
    WELCOME,

    /** Un compte est enregistré : la coquille s'ouvre, réseau ou non. */
    READY,
}

/**
 * Décide de l'accès à partir de l'état local, et de lui seul.
 *
 * **La règle est celle du dépôt d'origine** : c'est le `userId` enregistré dans l'état qui
 * ouvre l'application, pas la session d'authentification. C'est ce qui rend le lancement hors
 * ligne possible — une session expirée mais un état présent doivent afficher la progression,
 * pas un formulaire de connexion. Le rafraîchissement de la session est un travail de fond,
 * traité ailleurs.
 *
 * Un `userId` vide est traité comme absent. JavaScript confond `''` et `null` dans un test de
 * vérité, et l'original s'appuyait là-dessus (`state.userId ? 'done' : 'show'`) ; Kotlin ne le
 * fait pas, donc l'équivalence est écrite explicitement. Sans cela, un état portant une chaîne
 * vide ouvrirait une coquille anonyme, sans compte et sans moyen d'en créer un.
 */
object AccountAccess {

    /**
     * @param hasLocalState vrai si un état a **déjà été écrit sur cet appareil** pour le compte
     *   porté par [state].
     *
     *   C'est le témoin du seul cas où l'application peut s'ouvrir sans avoir jamais lu le
     *   serveur. Un compte dont le fichier n'existe pas encore — première connexion depuis un
     *   appareil neuf, ou fichier d'état mis de côté comme illisible — ne doit **pas** ouvrir la
     *   coquille : elle afficherait un programme vide, et la première séance validée dans ce
     *   programme vide serait ensuite poussée au serveur, écrasant le vrai compte. Voir
     *   `UserRepository.hasStoredState`.
     */
    fun stage(state: AppState?, hasLocalState: Boolean): AccountStage = when {
        state == null -> AccountStage.CHECKING
        state.userId.isNullOrBlank() -> AccountStage.WELCOME
        hasLocalState -> AccountStage.READY
        // Un compte est connu mais rien n'a encore été rapatrié : c'est l'écran d'attente, et
        // c'est à ce moment que le tirage du serveur est déclenché.
        else -> AccountStage.CHECKING
    }
}
