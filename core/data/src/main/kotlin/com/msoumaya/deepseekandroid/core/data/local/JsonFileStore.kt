package com.msoumaya.deepseekandroid.core.data.local

import com.msoumaya.deepseekandroid.core.model.AppJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import java.io.File

/**
 * Un document JSON persisté, relu et écrit sans jamais perdre de donnée.
 *
 * **Pourquoi pas `DataStore` typé.** La bibliothèque AndroidX expose bien un `Serializer<T>`,
 * mais son gestionnaire de corruption (`CorruptionHandler`) est déclaré `internal` en Kotlin :
 * il est public dans le bytecode et inaccessible depuis un autre module. La seule classe
 * fournie, `ReplaceFileCorruptionHandler`, est `final` et **écrase** le fichier fautif par une
 * valeur par défaut. Pour une progression d'apprentissage, cela signifie : un octet de travers
 * et des mois de mémorisation disparaissent sans trace. Ce magasin-ci fait l'inverse — il
 * **met le fichier de côté** sous `<nom>.corrupt-<horodatage>` et redémarre sur un état vide,
 * la donnée restant inspectable.
 *
 * Le fichier reste volontairement petit (un document par usage) : il est lu en entier au
 * premier accès, gardé en mémoire, et réécrit en entier à chaque modification. C'est le
 * modèle du client d'origine — l'état complet est l'unité d'échange — ce qui rend l'envoi
 * idempotent et la fusion à trois voies possible.
 *
 * L'écriture passe par [BlobFile] : fichier temporaire puis renommage atomique, donc une
 * coupure en pleine écriture laisse l'ancien contenu intact.
 */
class JsonFileStore<T : Any>(
    private val file: File,
    private val serializer: KSerializer<T>,
    /**
     * Refuse un document lisible mais inexploitable (schéma inconnu, par exemple).
     *
     * Placé **avant** [default] à dessein : la lambda finale d'un appel doit être la valeur
     * par défaut, sinon `JsonFileStore(fichier, serializer) { … }` lierait la lambda à ce
     * contrôle et le compilateur réclamerait la valeur par défaut. Le piège est silencieux
     * tant que les deux types coïncident.
     */
    private val validate: (T) -> Boolean = { true },
    private val default: () -> T,
) {

    private val blob = BlobFile(file)
    private val mutex = Mutex()
    private val state = MutableStateFlow<T?>(null)

    /** Valeur courante. Le disque n'est lu qu'au premier appel, puis le cache sert. */
    suspend fun current(): T = mutex.withLock { loaded() }

    /**
     * Applique [transform] à la valeur courante et écrit le résultat.
     *
     * La transformation s'exécute **sous le verrou** : deux modifications concurrentes ne
     * peuvent pas partir de la même valeur et s'écraser l'une l'autre. Si [transform] lève,
     * rien n'est écrit — l'ancien contenu reste en place.
     */
    suspend fun update(transform: (T) -> T): T = mutex.withLock {
        val next = transform(loaded())
        blob.write(AppJson.encodeToString(serializer, next).encodeToByteArray())
        state.value = next
        next
    }

    /**
     * Flux des valeurs successives.
     *
     * `onStart` déclenche la lecture initiale avant l'abonnement : le premier collecteur
     * reçoit immédiatement l'état du disque, jamais un `null` transitoire. C'est ce qui permet
     * à l'écran d'accueil de s'afficher sans attendre le réseau.
     */
    val changes: Flow<T> = state.filterNotNull().onStart { current() }

    /** Vrai si un contenu non vide existe déjà sur le disque. */
    fun exists(): Boolean = blob.exists()

    private suspend fun loaded(): T = state.value ?: read().also { state.value = it }

    private suspend fun read(): T {
        val bytes = blob.read() ?: return default()
        val text = bytes.decodeToString()
        // Un fichier vide est un premier démarrage, pas une corruption.
        if (text.isBlank()) return default()
        return try {
            val value = AppJson.decodeFromString(serializer, text)
            // Un document qui se décode mais que [validate] refuse est traité comme corrompu.
            // Le cas réel : un état dont le schéma n'est pas celui attendu. Il serait
            // parfaitement lisible et pourtant inexploitable ; le garder ferait échouer
            // l'application à chaque démarrage, sans trace exploitable.
            if (validate(value)) value else default().also { setAside() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Tout échec de lecture est traité comme une corruption : on archive et on
            // redémarre. Laisser l'exception remonter bloquerait l'application au démarrage,
            // et c'est précisément ce que l'exigence « hors ligne d'abord » interdit.
            setAside()
            default()
        }
    }

    private suspend fun setAside() {
        runCatching {
            val parent = file.parentFile ?: return@runCatching
            if (!file.exists()) return@runCatching
            file.renameTo(File(parent, "${file.name}.corrupt-${System.currentTimeMillis()}"))
        }
        // Un archivage impossible (fichier verrouillé) ne doit pas empêcher l'application
        // de démarrer : le fichier sera simplement réécrit au prochain `update`.
    }
}
