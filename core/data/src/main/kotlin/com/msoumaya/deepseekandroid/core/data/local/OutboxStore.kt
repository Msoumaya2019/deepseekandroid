package com.msoumaya.deepseekandroid.core.data.local

import com.msoumaya.deepseekandroid.core.domain.SyncOperation
import com.msoumaya.deepseekandroid.core.domain.SyncQueueStore
import kotlinx.serialization.Serializable

/**
 * Une action faite hors ligne, en attente d'envoi.
 *
 * [payload] porte l'instantané complet en JSON pour une opération d'état. C'est délibéré :
 * le client d'origine envoie un instantané complet plutôt qu'un delta, ce qui rend l'envoi
 * **idempotent** — un renvoi après coupure réseau ne peut pas appliquer deux fois la même
 * modification.
 */
@Serializable
data class PendingOperation(
    override val id: String,
    override val userId: String,
    val kind: String,
    val payload: String,
    val createdAt: String,
) : SyncOperation

/** Contenu du fichier de file d'attente. */
@Serializable
data class Outbox(val operations: List<PendingOperation> = emptyList())

/** Types d'opération connus. Le champ reste une chaîne libre pour ne pas figer le format. */
object OperationKind {
    const val STATE = "state"
}

/**
 * File d'attente persistante.
 *
 * Elle implémente [SyncQueueStore] pour être utilisable directement par le `SyncWorker` du
 * domaine : la mécanique d'envoi (un seul envoi à la fois, dernière opération seulement,
 * accusé après confirmation) reste dans le domaine, testable sans Android.
 *
 * La file survit à la fermeture de l'application : c'est ce qui permet à une validation
 * d'apprentissage faite dans le métro d'arriver au serveur une fois le réseau revenu, même
 * si l'utilisateur a fermé l'application entre-temps.
 */
class OutboxStore(
    private val store: JsonFileStore<Outbox>,
    private val ownerId: suspend () -> String?,
    private val nowIso: () -> String,
    private val newId: () -> String,
) : SyncQueueStore<PendingOperation> {

    override suspend fun owner(): String? = ownerId()

    override suspend fun list(userId: String): List<PendingOperation> =
        store.current().operations.filter { it.userId == userId }

    override suspend fun acknowledge(ids: List<String>) {
        if (ids.isEmpty()) return
        val removed = ids.toSet()
        store.update { current ->
            current.copy(operations = current.operations.filterNot { it.id in removed })
        }
    }

    /**
     * Met en file une opération, en remplaçant toute opération de même [kind] et de même
     * utilisateur.
     *
     * Sans ce remplacement, chaque modification hors ligne empilerait un instantané complet :
     * la file grossirait sans borne pendant une longue période sans réseau, alors que seul
     * le dernier instantané porte l'information.
     */
    suspend fun enqueueReplacing(kind: String, userId: String, payload: String) {
        val operation = PendingOperation(
            id = newId(),
            userId = userId,
            kind = kind,
            payload = payload,
            createdAt = nowIso(),
        )
        store.update { current ->
            current.copy(
                operations = current.operations.filterNot { it.userId == userId && it.kind == kind } + operation,
            )
        }
    }

    /** Contenu de la file, tous utilisateurs confondus. Utile au diagnostic. */
    suspend fun snapshot(): Outbox = store.current()
}
