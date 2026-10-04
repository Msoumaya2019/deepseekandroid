package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.local.JsonFileStore
import com.msoumaya.deepseekandroid.core.data.local.LocalStateStore
import com.msoumaya.deepseekandroid.core.data.local.Outbox
import com.msoumaya.deepseekandroid.core.data.local.OutboxStore
import com.msoumaya.deepseekandroid.core.data.remote.OwnerStore
import com.msoumaya.deepseekandroid.core.data.remote.RemoteStateSource
import com.msoumaya.deepseekandroid.core.domain.Program
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.QuranDataLoader
import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.AudioPreferences
import com.msoumaya.deepseekandroid.core.model.Mastery
import com.msoumaya.deepseekandroid.core.model.MushafSource
import com.msoumaya.deepseekandroid.core.model.ReaderPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Éprouve le dépôt d'état : lecture locale, mise en file, synchronisation.
 *
 * Les tests tournent sur la JVM avec de vrais fichiers dans un dossier temporaire : le
 * stockage ne dépend que d'un `File`, jamais d'un `Context` Android. Ce qui est mesuré ici
 * est donc exactement le code qui tourne sur l'appareil.
 *
 * Le serveur est remplacé par une carte en mémoire qui peut refuser de répondre : c'est la
 * seule façon d'éprouver sérieusement le chemin hors ligne, qui est le plus difficile à
 * observer sur un appareil.
 */
class UserRepositoryTest {

    private val user = "11111111-2222-3333-4444-555555555555"
    private val other = "99999999-8888-7777-6666-555555555555"

    private lateinit var root: File
    private lateinit var stores: LocalStateStore
    private lateinit var owners: FakeOwners
    private lateinit var server: FakeServer
    private var counter = 0

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("deepseekandroid-test").toFile()
        // Un seul magasin par test : deux instances auraient chacune leur cache mémoire et
        // les tests mesureraient cette divergence au lieu de la synchronisation.
        stores = LocalStateStore(root)
        owners = FakeOwners()
        server = FakeServer()
        counter = 0
        Quran.initialize(QuranDataLoader.loadFromClasspath())
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    /**
     * Un état dont le lecteur porte déjà un moushaf non hérité : sans cela
     * `migrateReaderState` modifierait l'état en douce et le test mesurerait la migration.
     */
    private fun state(
        owner: String?,
        updatedAt: String,
        knowledge: Map<String, Mastery> = emptyMap(),
    ): AppState = Program.defaultState().copy(
        onboardingDone = true,
        userId = owner,
        updatedAt = updatedAt,
        knowledge = knowledge,
        reader = ReaderPreferences(mushaf = MushafSource.CORAN_1441),
    )

    private fun repository(remote: RemoteStateSource? = server): UserRepository {
        val outbox = OutboxStore(
            store = JsonFileStore(File(root, "outbox.json"), Outbox.serializer()) { Outbox() },
            ownerId = { owners.currentOwner() },
            nowIso = { "2026-01-01T00:00:00.000Z" },
            newId = { "op-${counter++}" },
        )
        return UserRepository(stores, owners, outbox, remote)
    }

    private suspend fun seedLocalAccount(owner: String, value: AppState) {
        stores.accountFor(owner).update { value }
    }

    // ------------------------------------------------------------------
    // Lecture locale
    // ------------------------------------------------------------------

    @Test
    fun `le chargement local ne touche pas au reseau`() = runTest {
        seedLocalAccount(user, state(user, "2026-02-01T10:00:00.000Z", mapOf("1" to Mastery.PERFECT)))
        owners.setOwner(user)

        val loaded = repository().load()

        assertEquals(mapOf("1" to Mastery.PERFECT), loaded.knowledge)
        assertEquals(0, server.loads, "l'affichage ne doit jamais dépendre du réseau")
    }

    // ------------------------------------------------------------------
    // Hors ligne
    // ------------------------------------------------------------------

    @Test
    fun `hors ligne la synchronisation ne modifie rien et la file est conservee`() = runTest {
        owners.setOwner(user)
        val repository = repository()

        repository.mutate { it.copy(knowledge = mapOf("1" to Mastery.PERFECT)) }
        server.offline = true

        assertEquals(SyncResult.Offline, repository.sync())
        assertEquals(1, repository.pendingCount(), "la modification doit rester en file")
        assertEquals(mapOf("1" to Mastery.PERFECT), repository.load().knowledge)
        assertTrue(server.rows.isEmpty(), "rien ne doit avoir été écrit pendant la coupure")
    }

    @Test
    fun `une modification hors ligne survit a une horloge en retard`() = runTest {
        // Le serveur porte un horodatage très en avance : l'horloge de l'appareil est en
        // retard sur la sienne. Sans la fusion à trois voies, la règle fondée sur
        // l'horodatage donnerait raison au serveur et la validation disparaîtrait.
        server.rows[user] = state(user, "2099-01-01T00:00:00.000Z", mapOf("1" to Mastery.PERFECT))
        owners.setOwner(user)
        val repository = repository()

        repository.sync()
        repository.mutate { it.copy(knowledge = it.knowledge + ("2" to Mastery.PERFECT)) }
        assertEquals(SyncResult.Synced, repository.sync())

        assertEquals(Mastery.PERFECT, repository.load().knowledge["2"], "le verset appris hors ligne a disparu")
        assertEquals(Mastery.PERFECT, server.rows[user]?.knowledge?.get("2"), "le serveur n'a pas reçu la validation")
    }

    // ------------------------------------------------------------------
    // Premier chargement
    // ------------------------------------------------------------------

    @Test
    fun `un appareil neuf ne remplace pas le compte du serveur`() = runTest {
        server.rows[user] = state(
            user,
            "2026-02-01T10:00:00.000Z",
            mapOf("1" to Mastery.PERFECT, "2" to Mastery.PERFECT),
        )
        owners.setOwner(user)

        val repository = repository()
        assertEquals(SyncResult.Synced, repository.sync())

        assertEquals(
            setOf("1", "2"),
            repository.load().knowledge.keys,
            "le compte du serveur a été écrasé par un état vide",
        )
        assertEquals(setOf("1", "2"), server.rows[user]?.knowledge?.keys)
    }

    @Test
    fun `le compte precedent n'est jamais melange au nouveau`() = runTest {
        seedLocalAccount(other, state(other, "2026-02-01T10:00:00.000Z", mapOf("9" to Mastery.PERFECT)))
        server.rows[user] = state(user, "2026-02-01T10:00:00.000Z", mapOf("1" to Mastery.PERFECT))
        owners.setOwner(user)

        val repository = repository()
        repository.sync()

        val loaded = repository.load()
        assertNull(loaded.knowledge["9"], "la progression d'un autre compte a fui")
        assertEquals(mapOf("1" to Mastery.PERFECT), loaded.knowledge)
    }

    @Test
    fun `chaque compte garde son propre etat local`() = runTest {
        // Régression : avec un fichier d'état unique partagé, un utilisateur qui se
        // reconnecte hors ligne retrouvait un état vide au lieu de sa progression.
        seedLocalAccount(other, state(other, "2026-02-01T10:00:00.000Z", mapOf("9" to Mastery.PERFECT)))
        owners.setOwner(user)

        val repository = repository()

        // Le compte connecté ne voit rien qui vienne de l'autre.
        val fresh = repository.load()
        assertNull(fresh.knowledge["9"])
        assertEquals(user, fresh.userId)

        // Une fois synchronisé, il a son propre état…
        server.rows[user] = state(user, "2026-02-01T10:00:00.000Z", mapOf("1" to Mastery.PERFECT))
        repository.sync()
        assertEquals(mapOf("1" to Mastery.PERFECT), repository.state.first().knowledge)

        // …et l'autre compte retrouve le sien, sans réseau.
        server.offline = true
        owners.setOwner(other)
        assertEquals(
            mapOf("9" to Mastery.PERFECT),
            repository.state.first().knowledge,
            "le compte précédent a perdu sa progression locale",
        )
    }

    // ------------------------------------------------------------------
    // Fichier d'état illisible
    // ------------------------------------------------------------------

    @Test
    fun `un fichier d'etat illisible fait adopter le serveur en une seule synchronisation`() = runTest {
        // Le magasin d'état rend toujours un document, même sans fichier : un compte inconnu
        // reçoit `defaultState().copy(userId = id)`. Envoyé comme « état local » à la fusion,
        // ce document est traité comme une **remise à zéro volontaire** — `mergeOfflineState`
        // renvoie un vide, qui est écrit sur le disque, et la base de fusion est écrasée par
        // ce vide. La donnée finit par revenir du serveur, mais l'application affiche un
        // programme vide en attendant.
        //
        // Ce test fixe le comportement attendu : un seul aller-retour, et rien de vide n'est
        // jamais écrit.
        owners.setOwner(user)
        server.rows[user] = state(
            user,
            "2026-02-01T10:00:00.000Z",
            mapOf("1" to Mastery.PERFECT, "2" to Mastery.PERFECT),
        )
        val first = repository()
        assertEquals(SyncResult.Synced, first.sync())
        assertEquals(setOf("1", "2"), first.load().knowledge.keys)

        // Le fichier d'état devient illisible ; la base de fusion, elle, a survécu.
        val stateFile = File(root, "state_account_${LocalStateStore.fileToken(user)}.json")
        assertTrue(stateFile.isFile, "le fichier d'état du compte devrait exister")
        stateFile.writeText("{ ceci n’est pas du JSON")

        // Magasin neuf : sans cela, le cache mémoire masquerait la corruption et le test ne
        // mesurerait rien.
        stores = LocalStateStore(root)
        val second = repository()

        assertEquals(SyncResult.Synced, second.sync())
        assertEquals(
            setOf("1", "2"),
            second.load().knowledge.keys,
            "l'état local a été remplacé par un vide au lieu d'adopter le serveur",
        )

        // Et la synchronisation suivante ne doit rien pousser de vide.
        assertEquals(SyncResult.Synced, second.sync())
        assertEquals(setOf("1", "2"), server.rows[user]?.knowledge?.keys, "le serveur a reçu un état vide")
    }

    // ------------------------------------------------------------------
    // Envoi et absence de doublons
    // ------------------------------------------------------------------

    @Test
    fun `la file ne s'empile pas et se vide apres un envoi confirme`() = runTest {
        owners.setOwner(user)
        server.rows[user] = state(user, "2026-02-01T10:00:00.000Z")
        val repository = repository()

        repository.sync()
        repository.mutate { it.copy(knowledge = mapOf("1" to Mastery.PERFECT)) }
        repository.mutate { it.copy(knowledge = it.knowledge + ("2" to Mastery.PERFECT)) }

        assertEquals(1, repository.pendingCount(), "deux modifications ont empilé deux opérations")

        assertEquals(SyncResult.Synced, repository.sync())
        assertEquals(0, repository.pendingCount(), "la file n'a pas été vidée après confirmation")
        assertEquals(setOf("1", "2"), server.rows[user]?.knowledge?.keys)
        assertEquals(1, server.rows.size, "une seule ligne par compte : l'écriture est un upsert")
    }

    @Test
    fun `une seconde synchronisation sans changement ne modifie pas l'etat du serveur`() = runTest {
        owners.setOwner(user)
        server.rows[user] = state(user, "2026-02-01T10:00:00.000Z")
        val repository = repository()

        repository.sync()
        repository.mutate { it.copy(knowledge = mapOf("1" to Mastery.PERFECT)) }
        repository.sync()
        val afterFirst = server.rows[user]!!.knowledge

        repository.sync()

        assertEquals(afterFirst, server.rows[user]!!.knowledge, "l'envoi doit être idempotent")
        assertEquals(0, repository.pendingCount())
    }

    // ------------------------------------------------------------------
    // Sans projet configuré
    // ------------------------------------------------------------------

    @Test
    fun `sans projet configure l'application reste utilisable et la file reste vide`() = runTest {
        val repository = repository(remote = null)

        assertEquals(SyncResult.NotConfigured, repository.sync())
        val mutated = repository.mutate { it.copy(knowledge = mapOf("1" to Mastery.PERFECT)) }

        assertEquals(mapOf("1" to Mastery.PERFECT), mutated.knowledge)
        assertEquals(0, repository.pendingCount(), "sans compte, rien à mettre en file")
    }

    // ------------------------------------------------------------------
    // Remise à zéro
    // ------------------------------------------------------------------

    @Test
    fun `la remise a zero efface la progression et garde l'identite et le recitant`() = runTest {
        owners.setOwner(user)
        val repository = repository()

        repository.mutate {
            it.copy(
                knowledge = mapOf("1" to Mastery.PERFECT),
                audioPreferences = AudioPreferences("ar.shaatree"),
            )
        }

        val after = repository.resetProgress()

        assertTrue(after.knowledge.isEmpty(), "la progression devait être effacée")
        assertEquals(user, after.userId, "l'identité du compte doit survivre")
        assertEquals("ar.shaatree", after.audioPreferences?.reciterId, "le récitant choisi doit survivre")
    }

    // ------------------------------------------------------------------
    // Doublures
    // ------------------------------------------------------------------

    private class FakeOwners(initial: String? = null) : OwnerStore {
        private val flow = MutableStateFlow(initial)
        override val ownerId: Flow<String?> = flow
        override suspend fun currentOwner(): String? = flow.value
        override suspend fun setOwner(userId: String?) {
            flow.value = userId
        }
    }

    /** Serveur en mémoire, avec la possibilité de tomber en panne réseau. */
    private class FakeServer : RemoteStateSource {
        val rows = mutableMapOf<String, AppState>()
        var offline = false
        var loads = 0

        override suspend fun loadState(userId: String): AppState? {
            loads++
            if (offline) throw IOException("réseau indisponible")
            return rows[userId]
        }

        override suspend fun saveState(userId: String, state: AppState) {
            if (offline) throw IOException("réseau indisponible")
            rows[userId] = state
        }

        override suspend fun deleteState(userId: String) {
            if (offline) throw IOException("réseau indisponible")
            rows.remove(userId)
        }
    }
}
