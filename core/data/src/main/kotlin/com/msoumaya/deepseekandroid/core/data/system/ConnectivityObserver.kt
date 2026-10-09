package com.msoumaya.deepseekandroid.core.data.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.msoumaya.deepseekandroid.core.domain.ConnectivityEffect
import com.msoumaya.deepseekandroid.core.domain.ConnectivityReading
import com.msoumaya.deepseekandroid.core.domain.ConnectivityState
import com.msoumaya.deepseekandroid.core.domain.transition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Observe le réseau de l'appareil et publie l'état du bandeau.
 *
 * ## Ce qui est ici, et ce qui n'y est pas
 *
 * Les **décisions** — quand on est hors ligne, quand le bandeau s'affiche, quel texte il porte,
 * combien de temps il reste — vivent dans `core:domain`, où elles s'éprouvent sans appareil.
 * Ce qui vit ici, c'est la seule chose qui ne peut pas s'y trouver : la **lecture** du réseau par
 * `ConnectivityManager`, et l'appel de synchronisation que le domaine se contente de **demander**.
 *
 * ## La lecture, et pourquoi elle est écrite ainsi
 *
 * L'original lit `NetInfo`, qui rend deux booléens nullables : `isConnected` (y a-t-il un
 * réseau ?) et `isInternetReachable` (le système a-t-il vérifié qu'on atteint vraiment
 * Internet ?). La transposition est directe :
 *
 *  - **aucun réseau actif** → `isConnected = false` : c'est le cas « avion » ou « hors
 *    couverture », et le domaine le déclare hors ligne ;
 *  - **un réseau actif** → `isConnected = true`, et `isInternetReachable` suit
 *    `NET_CAPABILITY_VALIDATED`, qui est exactement ce que le système a vérifié. Un portail
 *    captif donne donc `true`/`false` — connecté mais pas joignable —, et le domaine le déclare
 *    hors ligne, comme l'original ;
 *  - **capacités indisponibles** → `isInternetReachable = null`, et non `false` : une mesure
 *    qu'on n'a pas ne doit pas se traduire par une panne annoncée. C'est la règle du domaine,
 *    et c'est pour cela que le champ reste nullable jusqu'ici.
 *
 * ## L'horloge
 *
 * [horloge] est injectable et **monotone** (`SystemClock.elapsedRealtime`) : l'échéance du
 * bandeau est un instant de cette horloge, et une horloge murale qui recule — un réglage
 * d'heure, un passage à l'heure d'été — éteindrait le bandeau trop tôt ou le laisserait allumé.
 * Elle est **publique** parce que l'affichage doit lire la même : le bandeau demande
 * `bandeauVisible(etat, horloge())`, et deux horloges différentes rendraient la question
 * indécidable.
 *
 * @param context contexte, réduit au contexte applicatif dès la construction.
 * @param scope portée des travaux : c'est elle qui exécute [onRestore].
 * @param onRestore ce qu'il faut faire quand le réseau revient — l'équivalent du
 *   `flushPendingSync()` de l'original, que le domaine demande par un effet et n'exécute pas.
 */
class ConnectivityObserver(
    context: Context,
    private val scope: CoroutineScope,
    private val onRestore: suspend () -> Unit,
    val horloge: () -> Long = { SystemClock.elapsedRealtime() },
) {

    /**
     * Le service système, ou `null` s'il n'existe pas.
     *
     * `null` n'est pas un cas d'appareil : c'est le cas d'un **test**. Le conteneur se construit
     * sans appareil, et un `getSystemService` rend `null` sur la JVM. Un observateur sans service
     * reste alors inerte — [start] ne s'inscrit pas, l'état reste celui du départ, et aucune
     * panne n'est annoncée à tort.
     */
    private val gestion: ConnectivityManager? =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val _state = MutableStateFlow(ConnectivityState())

    /** L'état publié. Il est lu par le bandeau, et par rien d'autre. */
    val state: StateFlow<ConnectivityState> = _state.asStateFlow()

    private var inscrit = false

    private val rappel = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            appliquer()
        }

        override fun onLost(network: Network) {
            // Relire, et non déclarer hors ligne : perdre un réseau n'est pas perdre le dernier.
            // Le passage d'un Wi-Fi au réseau mobile produit exactement cette suite d'événements,
            // et conclure sur `onLost` annoncerait une panne qui n'a pas eu lieu.
            appliquer()
        }

        override fun onCapabilitiesChanged(network: Network, capacites: NetworkCapabilities) {
            // C'est ici que la validation arrive : un réseau est disponible **avant** d'être
            // validé, et c'est le passage de l'un à l'autre qui décide de `isInternetReachable`.
            appliquer()
        }
    }

    /**
     * S'inscrit aux changements de réseau et publie une première lecture.
     *
     * Sans effet si c'est déjà fait, ou si le service système est absent. L'inscription est
     * tentée sous `runCatching` : elle lève si le nombre d'inscriptions simultanées est atteint,
     * et un observateur qui ne s'inscrit pas doit rester silencieux plutôt que de faire tomber
     * l'application au démarrage.
     */
    fun start() {
        if (inscrit) return
        // Le service est lu dans une **locale** avant le `runCatching`, et ce n'est pas un
        // détail : le smart cast d'une propriété nullable ne s'applique pas à l'intérieur d'un
        // lambda, qui pourrait être appelé plus tard. Écrire `gestion.register…` dans le lambda
        // ne compile donc pas, et c'est tant mieux — la locale dit que la vérification et
        // l'usage portent bien sur la même valeur.
        val gestion = this.gestion ?: return
        inscrit = runCatching { gestion.registerDefaultNetworkCallback(rappel) }.isSuccess
        // La première lecture, comme le `addEventListener` de NetInfo qui livre l'état courant
        // dès l'inscription : sans elle, une application ouverte sans réseau n'afficherait rien
        // avant le premier changement.
        appliquer()
    }

    /** Se désinscrit. Sans effet si l'inscription n'a pas eu lieu. */
    fun stop() {
        if (!inscrit) return
        runCatching { gestion?.unregisterNetworkCallback(rappel) }
        inscrit = false
    }

    private fun appliquer() {
        val suite = transition(_state.value, lire(), horloge())
        _state.value = suite.etat
        if (ConnectivityEffect.DemanderSynchronisation in suite.effets) {
            // Lancé, et non attendu : `onAvailable` s'exécute sur le fil du système, et y faire
            // une requête réseau bloquerait la livraison des événements suivants.
            scope.launch { onRestore() }
        }
    }

    private fun lire(): ConnectivityReading {
        val gestion = this.gestion ?: return ConnectivityReading(null, null)
        val actif = gestion.activeNetwork
            ?: return ConnectivityReading(isConnected = false, isInternetReachable = null)
        val capacites = gestion.getNetworkCapabilities(actif)
            ?: return ConnectivityReading(isConnected = true, isInternetReachable = null)
        return ConnectivityReading(
            isConnected = true,
            isInternetReachable = capacites.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        )
    }
}
