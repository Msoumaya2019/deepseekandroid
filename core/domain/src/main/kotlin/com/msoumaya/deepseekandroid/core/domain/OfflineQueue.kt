package com.msoumaya.deepseekandroid.core.domain

import kotlinx.coroutines.sync.Mutex

/**
 * File de synchronisation hors ligne.
 *
 * Porté depuis `src/core/offlineQueue.ts`. Le principe du client d'origine est conservé tel
 * quel, car il règle un vrai problème :
 *
 *  1. un seul envoi à la fois — deux synchronisations concurrentes enverraient deux fois la
 *     même opération ;
 *  2. on n'envoie que la **dernière** opération : un instantané complet contient déjà les
 *     éditions antérieures ;
 *  3. on n'accuse réception qu'**après** confirmation du serveur. Une opération créée
 *     pendant la requête reste donc en file et sera envoyée au tour suivant — c'est ce qui
 *     garantit qu'aucune action hors ligne n'est perdue.
 */
interface SyncOperation {
    val id: String
    val userId: String
}

/** Accès au magasin persistant des opérations en attente. */
interface SyncQueueStore<T : SyncOperation> {
    /** Propriétaire courant (session ouverte), ou `null` si personne n'est connecté. */
    suspend fun owner(): String?

    /** Opérations en attente, dans l'ordre d'insertion. */
    suspend fun list(userId: String): List<T>

    /**
     * Retire les opérations confirmées par le serveur.
     *
     * L'accusé est groupé : le magasin réel écrit sur disque, une écriture par opération
     * serait autant d'écritures atomiques pour un seul envoi.
     */
    suspend fun acknowledge(ids: List<String>)
}

/**
 * Envoie la file en attente vers le serveur.
 *
 * L'objet est réutilisable : appeler [run] pendant qu'un envoi est en cours rend la main
 * immédiatement (`false`), l'appelant n'a pas à attendre. C'est le comportement de la
 * promesse partagée du client d'origine.
 */
class SyncWorker<T : SyncOperation>(
    private val store: SyncQueueStore<T>,
    private val send: suspend (T) -> Unit,
) {
    private val mutex = Mutex()

    /** Renvoie `true` si l'appel a effectivement déclenché un envoi. */
    suspend fun run(): Boolean {
        if (!mutex.tryLock()) return false
        try {
            val owner = store.owner() ?: return true
            val operations = store.list(owner)
            if (operations.isEmpty() || store.owner() != owner) return true
            send(operations.last())
            store.acknowledge(operations.map { it.id })
            return true
        } finally {
            mutex.unlock()
        }
    }
}
