package com.msoumaya.deepseekandroid.core.data.system

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.msoumaya.deepseekandroid.core.domain.ForegroundEffect
import com.msoumaya.deepseekandroid.core.domain.ForegroundEvent
import com.msoumaya.deepseekandroid.core.domain.ForegroundState
import com.msoumaya.deepseekandroid.core.domain.transitionForeground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Observe le premier plan du processus et publie le retour de la personne.
 *
 * ## Ce qui est ici, et ce qui n'y est pas
 *
 * La **décision** — quand le processus entre au premier plan, et quand une seconde activité ne le
 * fait pas entrer — vit dans `core:domain` ([transitionForeground]), où elle s'éprouve sans
 * appareil. Ce qui vit ici, c'est la seule chose qui ne peut pas s'y trouver : l'**inscription**
 * au cycle de vie des activités par [Application.registerActivityLifecycleCallbacks], et l'appel
 * que le domaine se contente de **demander**.
 *
 * ## Pourquoi le framework, et non `ProcessLifecycleOwner`
 *
 * AndroidX fournit `ProcessLifecycleOwner`, qui fait exactement cette réduction — et qui l'assortit
 * d'une temporisation de 700 ms pour absorber les changements de configuration. Il vit dans
 * `lifecycle-process`, une dépendance qui **n'est pas déclarée** dans ce dépôt. Le framework, lui,
 * offre `Application.ActivityLifecycleCallbacks` : les deux méthodes qui comptent, sans une ligne
 * de dépendance à suivre. La temporisation perdue est un écart **nommé et borné** — voir
 * `core:domain/Foreground.kt`, qui dit ce qu'une rotation coûte et pourquoi c'est tenable.
 *
 * ## L'ordre des événements, et ce qu'il impose
 *
 * Android livre `onActivityStarted` et `onActivityStopped` **par activité**, et pour un changement
 * de configuration l'ordre est : arrêt de l'ancienne, démarrage de la nouvelle. Le compteur du
 * domaine retombe donc à zéro puis remonte, et une reprise supplémentaire est demandée — c'est
 * l'écart assumé ci-dessus. Aucun ordre reçu n'est interprété ici : chaque événement est appliqué
 * tel quel, et c'est la réduction qui décide.
 *
 * ## La lecture est silencieuse en cas d'absence d'application
 *
 * [application] est lue par `as? Application` : sur la JVM d'un test, `applicationContext` n'est
 * pas une `Application`, la valeur vaut `null`, et l'observateur reste **inerte** — [start] ne
 * s'inscrit pas, l'état reste celui du départ, et aucune reprise n'est demandée. C'est la même
 * règle que `ConnectivityObserver`, dont le service système rend `null` sans appareil.
 *
 * @param context contexte, réduit au contexte applicatif dès la construction.
 * @param scope portée des travaux : c'est elle qui exécute [onReprise].
 * @param onReprise ce qu'il faut faire quand la personne revient — les quatre files de l'original
 *   (`flushPendingSync`, `syncPendingRecitations`, la relecture du Quiz, la boîte des
 *   signalements), que le domaine demande par un effet et n'exécute pas.
 */
class ForegroundObserver(
    context: Context,
    private val scope: CoroutineScope,
    private val onReprise: suspend () -> Unit,
) {

    /**
     * L'application, ou `null` s'il n'y en a pas.
     *
     * `null` n'est pas un cas d'appareil : c'est le cas d'un **test**. Le conteneur se construit
     * sans appareil, et `applicationContext` n'y est pas une `Application`. Un observateur sans
     * application reste inerte, et aucune panne n'est annoncée à tort.
     */
    private val application: Application? =
        context.applicationContext as? Application

    private val _state = MutableStateFlow(ForegroundState())

    /** L'état publié : le compteur d'activités visibles, et la reprise qui en découle. */
    val state: StateFlow<ForegroundState> = _state.asStateFlow()

    private var inscrit = false

    private val rappel = object : Application.ActivityLifecycleCallbacks {

        override fun onActivityStarted(activity: Activity) {
            appliquer(ForegroundEvent.ACTIVITY_STARTED)
        }

        override fun onActivityStopped(activity: Activity) {
            appliquer(ForegroundEvent.ACTIVITY_STOPPED)
        }

        // Les cinq autres méthodes de l'interface ne portent aucune décision : la création et la
        // destruction encadrent le démarrage et l'arrêt, la reprise et la pause encadrent la
        // visibilité sans la changer, et l'instantané d'état ne concerne pas cette réduction.
        // Les laisser vides est le contrat, et non un oubli.

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    /**
     * S'inscrit au cycle de vie des activités.
     *
     * Sans effet si c'est déjà fait, ou si l'application est absente. Une inscription unique est
     * ce qui permet à [stop] de rendre l'observateur inerte sans laisser un rappel vivant.
     */
    fun start() {
        if (inscrit) return
        val application = this.application ?: return
        application.registerActivityLifecycleCallbacks(rappel)
        inscrit = true
    }

    /** Se désinscrit. Sans effet si l'inscription n'a pas eu lieu. */
    fun stop() {
        if (!inscrit) return
        application?.unregisterActivityLifecycleCallbacks(rappel)
        inscrit = false
    }

    private fun appliquer(evenement: ForegroundEvent) {
        val suite = transitionForeground(_state.value, evenement)
        _state.value = suite.etat
        if (ForegroundEffect.DemanderReprise in suite.effets) {
            // Lancé, et non attendu : `onActivityStarted` s'exécute sur le fil principal, et y
            // faire une requête réseau gèlerait le rendu de l'écran qui vient de s'ouvrir.
            scope.launch {
                try {
                    onReprise()
                } catch (cancelled: CancellationException) {
                    // L'annulation passe **avant** la capture : l'avaler ferait croire à la portée
                    // que le travail est fini alors qu'il vient d'être interrompu.
                    throw cancelled
                } catch (_: Exception) {
                    // Rien à dire. La reprise touche quatre files, dont deux **propagent** un échec
                    // non toléré — `QuizRepository.refresh` par son `compte { }`, et
                    // `ProblemReportRepository.flush` par son `push`. Sans cette capture, une
                    // panne au moment même du retour remonterait à une portée sans
                    // `CoroutineExceptionHandler` : l'application s'arrêterait précisément quand
                    // la personne revient. Chaque file garde ce qui n'a pas pu partir, et la
                    // prochaine occasion le reprendra.
                }
            }
        }
    }
}
