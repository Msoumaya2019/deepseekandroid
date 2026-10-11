package com.msoumaya.deepseekandroid.core.domain

/**
 * Le retour au premier plan — les décisions.
 *
 * Porté depuis `src/services/problemReports.ts:47`, `src/App.tsx:144/181/187` et
 * `src/services/recitations.ts`, où **quatre** écouteurs indépendants se branchent sur le même
 * événement : `AppState.addEventListener('change', s => { if (s === 'active') … })`.
 *
 * ## La frontière du portage
 *
 * React Native expose l'état de l'application entière par un seul booléen déguisé — `'active'` ou
 * `'background'` —, et **ne notifie que les transitions** : il n'annonce pas `'active'` au
 * démarrage, et ne dit rien quand une seconde fenêtre s'ouvre alors que la première est déjà
 * visible. Android, lui, livre le cycle de vie **par activité** : `onActivityStarted` /
 * `onActivityStopped` se déclenchent une fois par activité, donc plusieurs fois pour un même
 * passage perçu. Ce qui est porté ici, c'est la **réduction** d'une suite d'événements par activité
 * en une seule décision « le processus vient-il d'entrer au premier plan ? ». L'inscription
 * elle-même — `Application.ActivityLifecycleCallbacks` — vit côté Android, dans le module qui
 * porte le contexte, et n'est pas éprouvable sans appareil.
 *
 * ## Pourquoi un compteur, et non un booléen
 *
 * Un booléen `auPremierPlan` ne saurait pas distinguer « la première activité démarre » de « une
 * seconde activité démarre alors que la première est déjà visible » : les deux appellent
 * `onActivityStarted`, et les deux écriraient `true`. La reprise serait alors demandée à
 * l'ouverture d'un lien profond ou dans une fenêtre partagée, là où l'original ne dit rien.
 *
 * Le compteur rend la distinction **structurelle** : seul le passage de **zéro** à **un** est une
 * entrée au premier plan. C'est le calcul que fait `ProcessLifecycleOwner` dans AndroidX ; il est
 * écrit ici, en quinze lignes éprouvables, plutôt que d'ajouter une dépendance
 * (`lifecycle-process` n'est pas déclarée) pour un compteur.
 *
 * ## Ce que la réduction ne dit pas, et qui est assumé
 *
 * Un **changement de configuration** — rotation, passage en écran partagé, changement de langue —
 * fait `onActivityStopped` sur l'ancienne puis `onActivityStarted` sur la nouvelle : le compteur
 * retombe à zéro et remonte, donc une reprise **supplémentaire** est demandée. L'original ne la
 * produit pas. `ProcessLifecycleOwner` l'absorbe par une temporisation de 700 ms ; l'ajouter
 * coûterait un `delay`, un état d'attente et un réveil à annuler, pour un effet **sans
 * conséquence observable** : sur une file vide la passe ne fait rien, et sur une file pleine le
 * dépôt devait être tenté de toute façon. C'est un écart nommé, pas un oubli.
 *
 * Un **premier démarrage** demande aussi une reprise (0 → 1), alors que l'original vide une seule
 * fois — par son `sync()` immédiat. Le doublon est bénin pour la même raison, et il est préférable
 * à l'alternative : distinguer « premier » de « retour » demanderait un état « a déjà été visible »,
 * qui rendrait la règle dépendante d'un fait qu'aucun appelant ne peut observer.
 */

/** Ce qu'une activité annonce au processus. */
enum class ForegroundEvent {

    /** Une activité devient visible : `onActivityStarted`. */
    ACTIVITY_STARTED,

    /** Une activité cesse d'être visible : `onActivityStopped`. */
    ACTIVITY_STOPPED,
}

/**
 * Combien d'activités sont visibles en ce moment.
 *
 * Le champ est **privé de sens pris seul** — deux activités visibles n'ont pas d'interprétation
 * métier —, et c'est pourquoi il n'est lu que par [transitionForeground]. Il n'est pas exposé par
 * un accesseur nommé : un appelant qui aurait besoin de « suis-je au premier plan ? » poserait la
 * question au mauvais endroit, et une seconde réponse finirait par diverger de celle-ci.
 *
 * Le compteur ne descend **jamais sous zéro** : voir [transitionForeground].
 */
data class ForegroundState(
    val activitesVisibles: Int = 0,
) {
    /** Vrai quand au moins une activité est visible. */
    val auPremierPlan: Boolean get() = activitesVisibles > 0
}

/** Ce qu'une transition demande de faire au monde extérieur. */
sealed interface ForegroundEffect {

    /**
     * Reprendre ce qui attendait le retour de la personne.
     *
     * L'original branche quatre écouteurs sur ce moment : la file des modifications d'état
     * (`flushPendingSync()`), la file des récitations (`syncPendingRecitations()`), la relecture
     * du Quiz et la boîte des signalements. Le domaine **nomme l'intention une seule fois** ;
     * c'est le conteneur qui décide qui elle réveille, et dans quel ordre.
     */
    data object DemanderReprise : ForegroundEffect
}

/** Le nouvel état, et les effets à exécuter. */
data class ForegroundTransition(
    val etat: ForegroundState,
    val effets: List<ForegroundEffect>,
)

/**
 * Applique un événement de cycle de vie.
 *
 * Reproduit la réduction que l'original obtient de `AppState` :
 *
 *  1. un **démarrage** incrémente le compteur ; s'il passe de **zéro à un**, le processus vient
 *     d'entrer au premier plan et la reprise est demandée ;
 *  2. un **arrêt** décrémente le compteur, sans jamais le laisser descendre sous zéro, et ne
 *     demande **jamais** de reprise — l'original ne vide qu'au passage à `'active'`, pas à
 *     `'background'`.
 *
 * **Le plancher à zéro n'est pas une coquetterie.** Un compteur qui passerait à `-1` — un
 * `onActivityStopped` reçu sans `onActivityStarted`, ce qui arrive si l'observateur s'inscrit après
 * le démarrage d'une activité — ferait que le prochain démarrage rendrait `0`, et non `1` :
 * **aucune** reprise n'aurait plus lieu, sans qu'aucun écran ne le montre. Le `coerceAtLeast`
 * transforme ce défaut en impossibilité.
 */
fun transitionForeground(
    etat: ForegroundState,
    evenement: ForegroundEvent,
): ForegroundTransition = when (evenement) {
    ForegroundEvent.ACTIVITY_STARTED -> {
        val suite = etat.activitesVisibles + 1
        ForegroundTransition(
            etat = ForegroundState(suite),
            effets = if (etat.activitesVisibles == 0) {
                listOf(ForegroundEffect.DemanderReprise)
            } else {
                emptyList()
            },
        )
    }

    ForegroundEvent.ACTIVITY_STOPPED -> ForegroundTransition(
        etat = ForegroundState((etat.activitesVisibles - 1).coerceAtLeast(0)),
        effets = emptyList(),
    )
}
