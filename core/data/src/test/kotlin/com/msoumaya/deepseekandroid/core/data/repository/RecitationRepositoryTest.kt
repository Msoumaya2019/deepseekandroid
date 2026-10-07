package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.local.RecitationStore
import com.msoumaya.deepseekandroid.core.data.remote.OwnerStore
import com.msoumaya.deepseekandroid.core.data.remote.RecitationUploadRow
import com.msoumaya.deepseekandroid.core.data.remote.RecitationUploader
import com.msoumaya.deepseekandroid.core.domain.RecitationText
import com.msoumaya.deepseekandroid.core.model.AppJson
import com.msoumaya.deepseekandroid.core.model.LocalRecitation
import com.msoumaya.deepseekandroid.core.model.RecitationKind
import com.msoumaya.deepseekandroid.core.model.RecitationSyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Éprouve le dépôt « Récitations » : ce qu'il enregistre, ce qu'il envoie, dans quel ordre, et
 * ce qu'un échec fait au reste.
 *
 * ## Ce qui est mesuré ici, et pourquoi c'est ici
 *
 * Le dépôt ne contient que des règles d'**ordonnancement et de survie** : le fichier avant la
 * ligne, un fichier déjà déposé qui n'arrête pas la synchronisation, un élément qui échoue sans
 * bloquer les suivants, une récitation déposée qui n'est pas renvoyée, une récitation restée en
 * plein dépôt qui repart, un second dépôt concurrent qui ne renvoie pas les mêmes octets. Aucune
 * ne lève d'exception quand elle est fausse — et chacune a une conséquence visible :
 *
 *  - écrire la ligne avant le fichier produit une récitation que personne ne peut écouter, et que
 *    le serveur accepte : rien ne lie la ligne au fichier ;
 *  - ne pas tolérer un fichier déjà présent laisse une récitation en échec **pour toujours**, en
 *    renvoyant à chaque tentative des mégaoctets que le serveur a déjà ;
 *  - arrêter la boucle au premier échec bloque toutes les récitations suivantes ;
 *  - renvoyer une récitation déposée double la consommation d'un forfait mobile ;
 *  - considérer une récitation en plein dépôt comme « en cours » la bloque **définitivement** :
 *    c'est le seul cas qu'aucune reprise ne rattrape.
 *
 * ## Ce que ce fichier ne couvre pas, et où la limite passe
 *
 * **L'implémentation Supabase n'est pas exercée ici.** Elle est éprouvée par lecture de
 * l'artefact installé — `storage-kt-android-3.8.0.aar` et `postgrest-kt-android-3.8.0.aar`,
 * relevés au `javap` —, et les signatures employées sont recopiées dans le fichier de source. Ce
 * qui **est** mesuré ici, c'est ce que le dépôt demande : le chemin, le type MIME, les octets et
 * la forme de la ligne, qui sont les seules choses dont il décide.
 *
 * ## Pourquoi [settle] attend sur une condition, et non sur une horloge
 *
 * Le dépôt lit et écrit par `Dispatchers.IO` (`BlobFile`, et la copie du fichier) : le travail
 * quitte le répartiteur de test et y revient par un **vrai fil**, que l'horloge virtuelle
 * n'attend pas. On attend donc que l'état publié **cesse de bouger**, avec un délai de garde qui
 * échoue en le disant — plutôt qu'un `Thread.sleep` fixe, qui rendrait le harnais instable au lieu
 * de le rendre juste.
 */
class RecitationRepositoryTest {

    private val moi = "11111111-2222-3333-4444-555555555555"
    private val autre = "99999999-8888-7777-6666-555555555555"

    private lateinit var root: File
    private lateinit var source: File
    private lateinit var store: RecitationStore
    private var ticks = 0
    private var ids = 0

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("recitation-repository-test").toFile()
        source = File(root, "prise.m4a").also { it.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6)) }
        ticks = 0
        ids = 0
        store = nouveauRegistre()
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun nouveauRegistre(): RecitationStore = RecitationStore(
        root = root,
        nowIso = { Instant.ofEpochMilli(1_700_000_000_000L + (ticks++).toLong() * 1_000L).toString() },
        newId = { "rec-${++ids}" },
    )

    private fun TestScope.recitations(
        owners: OwnerStore,
        uploader: RecitationUploader?,
    ): RecitationRepository = RecitationRepository(
        store = store,
        uploader = uploader,
        session = owners,
        scope = backgroundScope,
    )

    /**
     * Laisse le travail de fond aboutir, en attendant que l'état publié cesse de bouger.
     *
     * Trois lectures stables, et non une : entre deux étapes du dépôt — le marquage `uploading`,
     * l'envoi des octets, l'écriture de la ligne, le marquage `synced` — l'état ne bouge pas
     * pendant l'aller-retour réseau, et s'arrêter à la première accalmie reviendrait à lire l'état
     * du milieu.
     */
    private fun TestScope.settle(repository: RecitationRepository, millis: Long = 1_000) {
        advanceTimeBy(millis)
        runCurrent()

        val limite = System.nanoTime() + DELAI_DE_GARDE_NANOS
        var vue: RecitationState? = null
        var stables = 0
        while (stables < 3 && System.nanoTime() < limite) {
            runCurrent()
            val courante = repository.state.value
            stables = if (courante == vue) stables + 1 else 0
            vue = courante
            Thread.sleep(2)
        }

        if (stables < 3) {
            error(
                "le travail de fond n'a pas abouti dans le delai : le harnais ne peut pas dire " +
                    "si le depot est fautif ou si l'attente est trop courte",
            )
        }
    }

    private suspend fun enregistrer(
        start: Int = 1,
        end: Int = 7,
        invocationId: String? = null,
    ): LocalRecitation = store.add(source.absolutePath, start, end, 5_000L, moi, invocationId)

    // ------------------------------------------------------------------
    // Sans compte
    // ------------------------------------------------------------------

    @Test
    fun `sans compte ouvert, aucun appel ne part`() = runTest {
        val uploader = FakeRecitationUploader()
        val repository = recitations(FakeOwners(null), uploader)
        settle(repository)

        assertTrue(uploader.calls.isEmpty(), "aucun appel ne doit partir sans compte ouvert")
        assertTrue(repository.state.value.items.isEmpty())
        assertNull(repository.state.value.ownerId)
    }

    @Test
    fun `enregistrer sans compte ne range rien et le dit`() = runTest {
        val repository = recitations(FakeOwners(null), FakeRecitationUploader())
        settle(repository)

        assertFalse(repository.save(source.absolutePath, 1, 7, 5_000L))

        assertEquals(RecitationText.SIGNED_OUT, repository.state.value.notice)
        assertTrue(store.all().isEmpty(), "aucune recitation ne doit etre rangee sans compte")
    }

    // ------------------------------------------------------------------
    // La lecture locale
    // ------------------------------------------------------------------

    @Test
    fun `la liste locale est publiee sans aucun appel reseau`() = runTest {
        val uploader = FakeRecitationUploader()
        val repository = recitations(FakeOwners(moi), uploader)
        settle(repository)

        enregistrer()

        repository.refresh()

        assertEquals(1, repository.state.value.items.size)
        assertEquals(moi, repository.state.value.items.single().userId)
        assertTrue(uploader.calls.isEmpty(), "afficher la liste ne demande rien au serveur")
    }

    @Test
    fun `la liste se vide a la deconnexion`() = runTest {
        val owners = FakeOwners(moi)
        val repository = recitations(owners, FakeRecitationUploader())
        settle(repository)
        enregistrer()
        repository.refresh()
        assertEquals(1, repository.state.value.items.size)

        owners.setOwner(null)
        settle(repository)

        assertTrue(
            repository.state.value.items.isEmpty(),
            "les recitations du compte precedent ne doivent pas rester sous le suivant",
        )
        assertNull(repository.state.value.ownerId)
    }

    @Test
    fun `les recitations d'un autre compte ne sont pas publiees`() = runTest {
        val repository = recitations(FakeOwners(moi), FakeRecitationUploader())
        settle(repository)
        store.add(source.absolutePath, 1, 7, 5_000L, autre)

        repository.refresh()

        assertTrue(repository.state.value.items.isEmpty())
    }

    // ------------------------------------------------------------------
    // Le dépôt : l'ordre
    // ------------------------------------------------------------------

    @Test
    fun `le fichier est depose avant que la ligne ne soit ecrite`() = runTest {
        val uploader = FakeRecitationUploader()
        val repository = recitations(FakeOwners(moi), uploader)
        settle(repository)
        val item = enregistrer()

        assertTrue(repository.syncPending())

        assertEquals(
            listOf("depot", "ligne"),
            uploader.calls,
            "la ligne ecrite avant le fichier decrirait une recitation que personne ne peut ecouter",
        )
        assertEquals(RecitationSyncStatus.SYNCED, store.list(moi).single().syncStatus)
        assertEquals(item.id, uploader.rows.single().id)
        assertEquals(6, uploader.uploads.single().size, "ce sont les octets du fichier")
    }

    @Test
    fun `l'adresse du compartiment commence par le proprietaire`() = runTest {
        // La table porte `recitation_own_path`, qui exige cette forme, et les politiques du
        // compartiment comparent ce premier segment a `auth.uid()` : un chemin range ailleurs
        // serait refuse par le serveur, sans que l'appareil ait pu le prevoir.
        val uploader = FakeRecitationUploader()
        val repository = recitations(FakeOwners(moi), uploader)
        settle(repository)
        val item = enregistrer()

        repository.syncPending()

        val path = uploader.uploads.single().path
        assertEquals("$moi/${item.id}.m4a", path)
        assertEquals(path, uploader.rows.single().storagePath)
        assertEquals(moi, uploader.rows.single().userId, "le proprietaire de la ligne est celui du chemin")
    }

    @Test
    fun `le type mime suit l'extension du fichier`() = runTest {
        val uploader = FakeRecitationUploader()
        val repository = recitations(FakeOwners(moi), uploader)
        settle(repository)
        val troisGp = File(root, "prise.3gp").also { it.writeBytes(byteArrayOf(7)) }
        store.add(troisGp.absolutePath, 1, 7, 5_000L, moi)

        repository.syncPending()

        assertEquals("audio/3gpp", uploader.uploads.single().mimeType)
        assertTrue(uploader.uploads.single().path.endsWith(".3gp"))
    }

    // ------------------------------------------------------------------
    // Le dépôt : les refus
    // ------------------------------------------------------------------

    @Test
    fun `un fichier deja depose laisse la synchronisation aboutir`() = runTest {
        // Le cas reel : une tentative precedente a depose l'audio puis s'est arretee avant
        // d'ecrire la ligne. Refuser ce doublon laisserait la recitation en echec pour toujours.
        val uploader = FakeRecitationUploader()
        uploader.refuseUpload = { IOException("The resource already exists") }
        val repository = recitations(FakeOwners(moi), uploader)
        settle(repository)
        enregistrer()

        repository.syncPending()

        assertEquals(RecitationSyncStatus.SYNCED, store.list(moi).single().syncStatus)
        assertEquals(1, uploader.rows.size, "la ligne doit etre ecrite malgre le refus du fichier")
    }

    @Test
    fun `un refus reel laisse la recitation en echec sans ecrire la ligne`() = runTest {
        val uploader = FakeRecitationUploader()
        uploader.refuseUpload = { IOException("Unable to resolve host") }
        val repository = recitations(FakeOwners(moi), uploader)
        settle(repository)
        enregistrer()

        repository.syncPending()

        assertEquals(RecitationSyncStatus.FAILED, store.list(moi).single().syncStatus)
        assertTrue(uploader.rows.isEmpty(), "aucune ligne ne doit etre ecrite")
    }

    @Test
    fun `un refus de la ligne laisse la recitation en echec`() = runTest {
        val uploader = FakeRecitationUploader()
        uploader.refuseUpsert = IOException("new row violates row-level security policy")
        val repository = recitations(FakeOwners(moi), uploader)
        settle(repository)
        enregistrer()

        repository.syncPending()

        assertEquals(RecitationSyncStatus.FAILED, store.list(moi).single().syncStatus)
    }

    @Test
    fun `un echec n'empeche pas les suivantes de partir`() = runTest {
        val uploader = FakeRecitationUploader()
        val premiere = enregistrer(start = 1, end = 7)
        uploader.refuseUpload = { path ->
            if (path.startsWith("$moi/${premiere.id}")) IOException("quota depasse") else null
        }
        val repository = recitations(FakeOwners(moi), uploader)
        settle(repository)
        val seconde = enregistrer(start = 8, end = 9)

        repository.syncPending()

        val statuts = store.list(moi).associate { it.id to it.syncStatus }
        assertEquals(RecitationSyncStatus.FAILED, statuts[premiere.id])
        assertEquals(
            RecitationSyncStatus.SYNCED,
            statuts[seconde.id],
            "une recitation en echec ne doit pas bloquer celles d'apres",
        )
    }

    @Test
    fun `un fichier disparu marque un echec sans rien deposer`() = runTest {
        // Une restauration de sauvegarde rend le registre sans les fichiers : envoyer la ligne
        // ecrirait une recitation que personne ne pourra ecouter.
        val uploader = FakeRecitationUploader()
        val repository = recitations(FakeOwners(moi), uploader)
        settle(repository)
        val item = enregistrer()
        store.fileOf(item).delete()

        repository.syncPending()

        assertEquals(RecitationSyncStatus.FAILED, store.list(moi).single().syncStatus)
        assertTrue(uploader.calls.isEmpty(), "aucun octet ne doit partir")
    }

    // ------------------------------------------------------------------
    // Le dépôt : la reprise
    // ------------------------------------------------------------------

    @Test
    fun `une recitation deja deposee n'est pas renvoyee`() = runTest {
        val uploader = FakeRecitationUploader()
        val repository = recitations(FakeOwners(moi), uploader)
        settle(repository)
        enregistrer()

        repository.syncPending()
        uploader.calls.clear()
        repository.syncPending()

        assertTrue(uploader.calls.isEmpty(), "des octets deja arrives ne doivent pas repartir")
    }

    @Test
    fun `une recitation restee en plein depot repart`() = runTest {
        // C'est le cas qu'aucune reprise ne rattrape si on l'oublie : une application tuee en
        // pleine transmission laisse l'entree dans cet etat, et la considerer comme « en cours »
        // la bloquerait definitivement.
        val uploader = FakeRecitationUploader()
        val repository = recitations(FakeOwners(moi), uploader)
        settle(repository)
        val item = enregistrer()
        store.markStatus(item.id, RecitationSyncStatus.UPLOADING)

        repository.syncPending()

        assertEquals(RecitationSyncStatus.SYNCED, store.list(moi).single().syncStatus)
        assertEquals(1, uploader.uploads.size)
    }

    @Test
    fun `une recitation en echec est retentee`() = runTest {
        val uploader = FakeRecitationUploader()
        uploader.refuseUpload = { IOException("Unable to resolve host") }
        val repository = recitations(FakeOwners(moi), uploader)
        settle(repository)
        enregistrer()
        repository.syncPending()
        assertEquals(RecitationSyncStatus.FAILED, store.list(moi).single().syncStatus)

        uploader.refuseUpload = { null }
        repository.syncPending()

        assertEquals(RecitationSyncStatus.SYNCED, store.list(moi).single().syncStatus)
    }

    @Test
    fun `un second depot concurrent ne renvoie pas les memes octets`() = runTest {
        val uploader = FakeRecitationUploader()
        val repository = recitations(FakeOwners(moi), uploader)
        settle(repository)
        enregistrer()

        var second: Boolean? = null
        // Le crochet ne rappelle **qu'une fois** : sans cette borne, un garde casse entrerait en
        // recursion jusqu'a faire tomber la pile, et le test echouerait en accusant le harnais
        // plutot que de dire ce qui est faux.
        uploader.onUpload = { if (second == null) second = repository.syncPending() }

        assertTrue(repository.syncPending())

        assertEquals(
            false,
            second,
            "un depot deja en cours doit rendre la main, et non en demarrer un second",
        )
        assertEquals(1, uploader.uploads.size, "les octets ne doivent partir qu'une fois")
    }

    @Test
    fun `une annulation laisse la recitation en plein depot, et non en echec`() = runTest {
        // Une interruption n'est pas un refus du serveur : la marquer en echec ferait lire a la
        // personne un refus qui n'a pas eu lieu. L'etat `uploading` la fait repartir de toute
        // facon, puisque `awaitsUpload` le compte comme a envoyer.
        val uploader = FakeRecitationUploader()
        val repository = recitations(FakeOwners(moi), uploader)
        settle(repository)
        enregistrer()
        uploader.onUpload = { throw CancellationException("interrompu") }

        runCatching { repository.syncPending() }

        assertEquals(RecitationSyncStatus.UPLOADING, store.list(moi).single().syncStatus)
    }

    // ------------------------------------------------------------------
    // La ligne envoyée
    // ------------------------------------------------------------------

    @Test
    fun `la ligne d'une invocation ne porte pas de bornes`() = runTest {
        // La contrainte `recitations_passage_type` exige des bornes nulles pour une invocation.
        val uploader = FakeRecitationUploader()
        val repository = recitations(FakeOwners(moi), uploader)
        settle(repository)
        enregistrer(start = 0, end = 0, invocationId = "inv-1")

        repository.syncPending()

        val row = uploader.rows.single()
        assertNull(row.startVerseId)
        assertNull(row.endVerseId)
        assertEquals(RecitationKind.INVOCATION, row.recordingType)
        assertEquals("inv-1", row.invocationId)
    }

    @Test
    fun `la ligne d'un passage du Coran porte ses deux bornes`() = runTest {
        val uploader = FakeRecitationUploader()
        val repository = recitations(FakeOwners(moi), uploader)
        settle(repository)
        enregistrer(start = 12, end = 34)

        repository.syncPending()

        val row = uploader.rows.single()
        assertEquals(12, row.startVerseId)
        assertEquals(34, row.endVerseId)
        assertEquals(RecitationKind.QURAN, row.recordingType)
        assertNull(row.invocationId)
    }

    @Test
    fun `la ligne envoyee ne porte ni instantane d'invocation ni champ nul`() = runTest {
        // Mesure dans `supabase/daily-contents.sql`, ligne 66 : le declencheur du serveur ecrit
        // `invocation_snapshot` a chaque insertion et **ecrase** ce que le client aurait envoye.
        // L'envoyer ne serait pas faux, ce serait laisser croire que le client en decide.
        //
        // Les champs nuls sont omis (`AppJson` porte `explicitNulls = false`), ce qui laisse la
        // colonne prendre sa valeur par defaut — nulle — plutot que de dependre de ce que
        // PostgREST fait d'un `null` ecrit a la main.
        val uploader = FakeRecitationUploader()
        val repository = recitations(FakeOwners(moi), uploader)
        settle(repository)
        enregistrer(start = 0, end = 0, invocationId = "inv-1")

        repository.syncPending()

        // Le contrôle de **forme** d'abord : la ligne ne doit pas *déclarer* le champ, même nul.
        // Un contrôle qui ne regarderait que le JSON envoyé passerait sur un champ déclaré et
        // resté nul — il ne mesurerait alors rien, et laisserait réintroduire le champ sans un mot.
        //
        // Les noms sont lus sur le descripteur du sérialiseur, et non dans le JSON : c'est la
        // seule façon de voir un champ déclaré qui ne part pas. Les deux accesseurs employés —
        // `elementsCount` et `getElementName` — sont des **membres** de `SerialDescriptor`,
        // relevés dans `kotlinx-serialization-core-jvm-1.11.0.jar` au `javap` ; `elementNames`,
        // qui existe en Kotlin, n'y figure pas comme membre et n'est donc pas employé.
        val descripteur = RecitationUploadRow.serializer().descriptor
        val declares = (0 until descripteur.elementsCount).map { descripteur.getElementName(it) }
        assertFalse(
            declares.contains("invocation_snapshot"),
            "l'instantane est compose par le serveur : le declarer ici laisserait croire que le " +
                "client en decide, alors que le sien est ecrase (daily-contents.sql, ligne 66)",
        )

        val json = AppJson.encodeToJsonElement(uploader.rows.single()).jsonObject
        assertFalse(
            json.containsKey("invocation_snapshot"),
            "l'instantane est compose par le serveur, et le sien ecrase celui du client",
        )
        assertFalse(json.containsKey("start_verse_id"), "un null est omis, pas envoye")
        assertFalse(json.containsKey("end_verse_id"), "un null est omis, pas envoye")
        assertEquals("invocation", json.getValue("recording_type").jsonPrimitive.content)
        assertEquals("inv-1", json.getValue("invocation_id").jsonPrimitive.content)
        assertEquals("5000", json.getValue("duration_ms").jsonPrimitive.content)
    }

    // ------------------------------------------------------------------
    // Enregistrer
    // ------------------------------------------------------------------

    @Test
    fun `enregistrer inscrit la recitation et tente le depot tout de suite`() = runTest {
        val uploader = FakeRecitationUploader()
        val repository = recitations(FakeOwners(moi), uploader)
        settle(repository)

        assertTrue(repository.save(source.absolutePath, 1, 7, 5_000L))
        settle(repository)

        assertEquals(1, repository.state.value.items.size, "la liste publiee porte la nouvelle recitation")
        assertEquals(RecitationSyncStatus.SYNCED, repository.state.value.items.single().syncStatus)
        assertEquals(1, uploader.rows.size, "l'enregistrement tente le depot sans attendre")
    }

    @Test
    fun `un passage invalide est refuse avec le mot de l'original`() = runTest {
        val repository = recitations(FakeOwners(moi), FakeRecitationUploader())
        settle(repository)

        assertFalse(repository.save(source.absolutePath, 10, 5, 5_000L))

        assertEquals(RecitationText.INVALID_RECORDING, repository.state.value.notice)
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `enregistrer sans reseau laisse la recitation en attente`() = runTest {
        // Le mode hors ligne n'est pas une panne : la recitation est sur l'appareil, et elle
        // repartira. C'est toute la raison d'etre du registre local.
        val repository = recitations(FakeOwners(moi), uploader = null)
        settle(repository)

        assertTrue(repository.save(source.absolutePath, 1, 7, 5_000L))
        settle(repository)

        assertEquals(RecitationSyncStatus.PENDING, repository.state.value.items.single().syncStatus)
    }

    @Test
    fun `le retrait efface la recitation de la liste publiee`() = runTest {
        val repository = recitations(FakeOwners(moi), FakeRecitationUploader())
        settle(repository)
        val item = enregistrer()
        repository.refresh()
        assertEquals(1, repository.state.value.items.size)

        assertTrue(repository.delete(item))

        assertTrue(repository.state.value.items.isEmpty())
        assertTrue(store.all().isEmpty())
    }

    private companion object {
        /**
         * Délai de garde de [settle], en nanosecondes.
         *
         * Il ne sert qu'à **échouer en le disant** : un harnais qui attendrait indéfiniment ne
         * saurait pas distinguer un dépôt fautif d'une attente trop courte, et le rapport de test
         * resterait muet.
         */
        const val DELAI_DE_GARDE_NANOS: Long = 30_000_000_000L
    }
}
