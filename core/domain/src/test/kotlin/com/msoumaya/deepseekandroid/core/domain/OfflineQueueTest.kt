package com.msoumaya.deepseekandroid.core.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * File de synchronisation hors ligne.
 *
 * Trois garanties à ne pas perdre :
 *  1. un seul envoi à la fois ;
 *  2. seule la **dernière** opération est envoyée — un instantané complet contient déjà les
 *     précédentes ;
 *  3. on n'accuse réception qu'**après** confirmation, donc une opération créée pendant la
 *     requête reste en file.
 */
class OfflineQueueTest {

    private data class Operation(override val id: String, override val userId: String) : SyncOperation

    private class FakeStore(private val userId: String?) : SyncQueueStore<Operation> {
        val operations = mutableListOf<Operation>()
        val acknowledged = mutableListOf<String>()

        override suspend fun owner(): String? = userId

        override suspend fun list(userId: String): List<Operation> = operations.toList()

        override suspend fun acknowledge(ids: List<String>) {
            acknowledged.addAll(ids)
            operations.removeAll { it.id in ids }
        }
    }

    @Test
    fun `seule la derniere operation est envoyee puis toutes sont acquittees`() = runTest {
        val store = FakeStore("user-1")
        store.operations += Operation("op1", "user-1")
        store.operations += Operation("op2", "user-1")
        store.operations += Operation("op3", "user-1")

        val sent = mutableListOf<Operation>()
        val worker = SyncWorker(store) { sent.add(it) }
        assertTrue(worker.run())

        assertEquals(listOf("op3"), sent.map { it.id }, "un instantané complet suffit")
        assertEquals(listOf("op1", "op2", "op3"), store.acknowledged)
        assertTrue(store.operations.isEmpty())
    }

    @Test
    fun `sans session ouverte rien n'est envoye`() = runTest {
        val store = FakeStore(null)
        store.operations += Operation("op1", "user-1")
        val sent = mutableListOf<Operation>()
        SyncWorker(store) { sent.add(it) }.run()

        assertTrue(sent.isEmpty())
        assertEquals(1, store.operations.size, "les opérations restent en file")
    }

    @Test
    fun `une operation creee pendant l'envoi reste en file`() = runTest {
        val store = FakeStore("user-1")
        store.operations += Operation("op1", "user-1")

        val sent = mutableListOf<Operation>()
        val worker = SyncWorker(store) { operation ->
            sent.add(operation)
            // Une nouvelle édition arrive pendant que la requête est en vol.
            store.operations += Operation("op2", "user-1")
        }
        worker.run()

        assertEquals(listOf("op1"), sent.map { it.id })
        assertEquals(listOf("op2"), store.operations.map { it.id }, "op2 n'a pas été confirmée : elle reste due")
        assertEquals(listOf("op1"), store.acknowledged)
    }

    @Test
    fun `une file vide ne declenche aucun envoi`() = runTest {
        val store = FakeStore("user-1")
        val sent = mutableListOf<Operation>()
        SyncWorker(store) { sent.add(it) }.run()

        assertTrue(sent.isEmpty())
        assertTrue(store.acknowledged.isEmpty())
    }

    @Test
    fun `un changement de proprietaire annule l'envoi`() = runTest {
        // Le propriétaire lu avant la requête diffère de celui relu juste après : c'est le
        // signe d'une déconnexion pendant la lecture, et rien ne doit être envoyé.
        var calls = 0
        val store = object : SyncQueueStore<Operation> {
            override suspend fun owner(): String? = if (calls++ == 0) "user-1" else "user-2"
            override suspend fun list(userId: String): List<Operation> = listOf(Operation("op1", "user-1"))
            override suspend fun acknowledge(ids: List<String>) = Unit
        }
        val sent = mutableListOf<Operation>()
        SyncWorker(store) { sent.add(it) }.run()
        assertTrue(sent.isEmpty(), "aucune donnée d'un compte ne doit partir sous un autre compte")
    }

    @Test
    fun `un second appel pendant un envoi rend la main sans envoyer`() = runTest {
        val store = FakeStore("user-1")
        store.operations += Operation("op1", "user-1")

        val sent = mutableListOf<Operation>()
        lateinit var worker: SyncWorker<Operation>
        var second: Boolean? = null
        worker = SyncWorker(store) { operation ->
            sent.add(operation)
            // Réentrance pendant l'envoi : l'appel doit être refusé, pas mis en attente.
            second = worker.run()
        }
        worker.run()

        assertEquals(false, second, "un envoi est déjà en cours")
        assertEquals(1, sent.size, "l'opération n'est envoyée qu'une fois")
    }
}
