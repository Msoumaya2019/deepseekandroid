package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.local.RecitationStore
import com.msoumaya.deepseekandroid.core.data.remote.OwnerStore
import com.msoumaya.deepseekandroid.core.data.remote.RecitationSource
import com.msoumaya.deepseekandroid.core.data.remote.RecitationUploadRow
import com.msoumaya.deepseekandroid.core.data.remote.RecitationUploader
import com.msoumaya.deepseekandroid.core.domain.RecitationText
import com.msoumaya.deepseekandroid.core.model.AppJson
import com.msoumaya.deepseekandroid.core.model.GeneralFeedback
import com.msoumaya.deepseekandroid.core.model.LocalRecitation
import com.msoumaya.deepseekandroid.core.model.RecitationKind
import com.msoumaya.deepseekandroid.core.model.RecitationSyncStatus
import com.msoumaya.deepseekandroid.core.model.RemoteRecitation
import com.msoumaya.deepseekandroid.core.model.VerseCorrection
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
 * ## Pourquoi [settle] attend le dépôt, et non une accalmie de l'état
 *
 * Le dépôt lit et écrit par `Dispatchers.IO` (`BlobFile`, et la copie du fichier) : le travail
 * quitte le répartiteur de test et y revient par un **vrai fil**, que l'horloge virtuelle
 * n'attend pas. Il faut donc **sonder**, et non se suspendre : c'est la seule façon d'avancer.
 *
 * Mais sonder quoi ? La première version de cette attente guettait trois lectures stables de
 * l'état publié. Elle passait ici et tombait en intégration continue, et c'est la leçon de ce
 * fichier : **une accalmie de l'état n'est pas une fin de travail.** L'envoi des octets ne change
 * rien d'autre que `syncing`, qui reste vrai du début à la fin de la passe ; « l'état ne bouge
 * plus » se produit donc aussi **pendant** le dépôt. Deux tests lisaient un statut `uploading` en
 * croyant lire un statut final.
 *
 * On sonde maintenant ce que le dépôt sait de lui-même : [RecitationRepository.enTravail]. Le
 * sommeil n'est plus l'attente, il n'en est que le pas d'échantillonnage, et le délai de garde
 * échoue en le disant plutôt que de rendre la main sur une lecture du milieu.
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
        lecteur: RecitationSource? = null,
    ): RecitationRepository = RecitationRepository(
        store = store,
        uploader = uploader,
        source = lecteur,
        session = owners,
        scope = backgroundScope,
    )

    /**
     * Laisse le travail de fond aboutir, en attendant que le dépôt n'ait plus rien en vol.
     *
     * Deux tours sans rien en vol, et non un seul : une opération en lance une autre — la lecture
     * du registre précède la passe de dépôt, et l'enregistrement lance une passe —, donc un tour
     * calme peut être un tour **entre** deux travaux. Le premier tour ne prouve rien ; le second
     * non plus, mais deux d'affilée, oui.
     */
    private fun TestScope.settle(repository: RecitationRepository, millis: Long = 1_000) {
        advanceTimeBy(millis)

        val limite = System.nanoTime() + DELAI_DE_GARDE_NANOS
        var tours = 0
        while (tours < 2 && System.nanoTime() < limite) {
            runCurrent()
            tours = if (repository.enTravail) 0 else tours + 1
            Thread.sleep(2)
        }

        if (tours < 2) {
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

    // ------------------------------------------------------------------
    // Lire le serveur
    // ------------------------------------------------------------------

    /** Une récitation distante, réduite à ce que ces tests regardent. */
    private fun distante(
        id: String,
        owner: String = moi,
        createdAt: String = "2026-01-01T10:00:00Z",
        durationMs: Long = 5_000L,
    ) = RemoteRecitation(
        id = id,
        userId = owner,
        durationMs = durationMs,
        storagePath = "$owner/$id.m4a",
        createdAt = createdAt,
    )

    @Test
    fun `la lecture distante publie ce que le serveur porte`() = runTest {
        val lecteur = FakeRecitationSource()
        lecteur.byOwner = { listOf(distante("rec-a"), distante("rec-b")) }
        val repository = recitations(FakeOwners(moi), FakeRecitationUploader(), lecteur)
        settle(repository)

        assertEquals(listOf("rec-a", "rec-b"), repository.state.value.remote.map { it.id })
    }

    @Test
    fun `la lecture distante est demandee pour le compte ouvert`() = runTest {
        val lecteur = FakeRecitationSource()
        var demande: String? = null
        lecteur.byOwner = { owner ->
            demande = owner
            emptyList()
        }
        val repository = recitations(FakeOwners(moi), FakeRecitationUploader(), lecteur)
        settle(repository)

        assertEquals(moi, demande, "la liste distante est demandee pour le compte, et pas pour tous")
    }

    @Test
    fun `le registre local est publie avant que la liste distante ne soit demandee`() = runTest {
        // L'ordre n'est pas cosmetique : si le distant etait publie avant le local, une panne du
        // serveur laisserait l'ecran sur une liste **vide** alors que les fichiers sont sur
        // l'appareil — et ce sont precisement ceux qu'on ne peut pas re-telecharger.
        enregistrer()
        lateinit var repository: RecitationRepository
        var itemsVus: Int? = null
        val lecteur = FakeRecitationSource()
        lecteur.byOwner = {
            itemsVus = repository.state.value.items.size
            emptyList()
        }
        repository = recitations(FakeOwners(moi), FakeRecitationUploader(), lecteur)
        settle(repository)

        assertEquals(1, itemsVus, "la liste locale est deja publiee quand le serveur est interroge")
    }

    @Test
    fun `une panne de la lecture distante laisse les fichiers locaux en place`() = runTest {
        enregistrer()
        val lecteur = FakeRecitationSource()
        lecteur.refuseList = IOException("reseau absent")
        val repository = recitations(FakeOwners(moi), FakeRecitationUploader(), lecteur)
        settle(repository)

        assertEquals(1, repository.state.value.items.size, "les fichiers de l'appareil restent la")
        assertTrue(repository.state.value.remote.isEmpty())
        assertEquals(
            RecitationText.listLocalOnly("reseau absent"),
            repository.state.value.notice,
            "la phrase est celle de l'original",
        )
    }

    @Test
    fun `une panne de la lecture distante sans message ne finit pas sur une espace`() = runTest {
        val lecteur = FakeRecitationSource()
        // Une exception sans texte : `restMessage()` rend `null`.
        lecteur.refuseList = RuntimeException()
        val repository = recitations(FakeOwners(moi), FakeRecitationUploader(), lecteur)
        settle(repository)

        assertEquals(RecitationText.LOCAL_ONLY, repository.state.value.notice)
    }

    @Test
    fun `sans lecteur, aucune liste distante n'est demandee`() = runTest {
        val repository = recitations(FakeOwners(moi), FakeRecitationUploader(), lecteur = null)
        settle(repository)

        assertTrue(repository.state.value.remote.isEmpty())
        assertNull(repository.state.value.notice, "l'absence de compte n'est pas une panne")
    }

    @Test
    fun `la liste distante d'un compte change en vol n'est pas publiee`() = runTest {
        // La lecture est suspendue : le compte peut changer pendant qu'elle l'est. Publier alors
        // la liste de l'ancien compte sous le nouveau serait pire qu'un echec.
        //
        // La relecture du nouveau compte **échoue**, et c'est délibéré : un échec ne touche pas à
        // la liste distante, donc ce que l'état porte encore à la fin est exactement ce que la
        // première lecture a publié. Sans cela, la relecture republierait sa propre liste vide et
        // l'assertion passerait même sans la revérification — c'est ce qui est arrivé, deux fois :
        // le cas de falsification a rendu FAUX tant que la relecture rendait une liste vide.
        val owners = FakeOwners(moi)
        val lecteur = FakeRecitationSource()
        lecteur.byOwner = { owner ->
            if (owner == moi) {
                owners.setOwner(autre)
                listOf(distante("rec-a", owner = moi))
            } else {
                throw IOException("le serveur ne repond pas")
            }
        }
        val repository = recitations(owners, FakeRecitationUploader(), lecteur)
        settle(repository)

        assertTrue(
            repository.state.value.remote.isEmpty(),
            "la liste de l'ancien compte ne doit pas se publier sous le nouveau",
        )
        assertEquals(
            RecitationText.listLocalOnly("le serveur ne repond pas"),
            repository.state.value.notice,
            "le nouveau compte a bien ete relu : sans cela, l'assertion precedente ne prouve rien",
        )
    }

    // ------------------------------------------------------------------
    // Retirer du serveur
    // ------------------------------------------------------------------

    @Test
    fun `le retrait distant efface le fichier, la ligne et la copie locale`() = runTest {
        val item = enregistrer()
        val lecteur = FakeRecitationSource()
        val repository = recitations(FakeOwners(moi), FakeRecitationUploader(), lecteur)
        settle(repository)
        assertEquals(1, repository.state.value.items.size)

        assertTrue(repository.deleteRemote(distante(item.id)))

        assertEquals(listOf(item.id), lecteur.deleted.map { it.id }, "le serveur est sollicite")
        assertTrue(repository.state.value.items.isEmpty(), "la copie locale part avec la ligne")
        assertTrue(store.all().isEmpty(), "le fichier ne revient pas au prochain depot")
    }

    @Test
    fun `le retrait distant ne part pas pour la recitation d'un autre`() = runTest {
        val lecteur = FakeRecitationSource()
        val repository = recitations(FakeOwners(moi), FakeRecitationUploader(), lecteur)
        settle(repository)

        assertFalse(repository.deleteRemote(distante("rec-a", owner = autre)))

        assertTrue(lecteur.deleted.isEmpty(), "aucun appel ne doit partir")
        assertEquals(RecitationText.NOT_MINE, repository.state.value.notice)
    }

    @Test
    fun `le retrait distant sans lecteur le dit`() = runTest {
        val repository = recitations(FakeOwners(moi), FakeRecitationUploader(), lecteur = null)
        settle(repository)

        assertFalse(repository.deleteRemote(distante("rec-a")))

        assertEquals(RecitationText.CONNECTION_REQUIRED, repository.state.value.notice)
    }

    @Test
    fun `un echec du retrait distant laisse la liste en place`() = runTest {
        val item = enregistrer()
        val lecteur = FakeRecitationSource()
        lecteur.refuseDelete = IOException("refus du serveur")
        val repository = recitations(FakeOwners(moi), FakeRecitationUploader(), lecteur)
        settle(repository)

        assertFalse(repository.deleteRemote(distante(item.id)))

        assertEquals(1, repository.state.value.items.size, "rien n'est retire quand le serveur refuse")
        assertEquals("refus du serveur", repository.state.value.notice)
    }

    @Test
    fun `un echec du retrait sans message ne laisse pas un mot vide`() = runTest {
        // Un `notice` nul serait un echec **muet** : la personne aurait appuye, rien ne serait
        // arrive, et rien ne le dirait.
        val lecteur = FakeRecitationSource()
        lecteur.refuseDelete = RuntimeException()
        val repository = recitations(FakeOwners(moi), FakeRecitationUploader(), lecteur)
        settle(repository)

        assertFalse(repository.deleteRemote(distante("rec-a")))

        val notice = repository.state.value.notice
        assertTrue(!notice.isNullOrBlank(), "un echec muet ne dit rien a personne : $notice")
    }

    // ------------------------------------------------------------------
    // Ecouter et lire les corrections
    // ------------------------------------------------------------------

    @Test
    fun `l'adresse signee vient du lecteur`() = runTest {
        val lecteur = FakeRecitationSource()
        lecteur.signedUrl = { "https://signe.test/$it" }
        val repository = recitations(FakeOwners(moi), FakeRecitationUploader(), lecteur)
        settle(repository)

        assertEquals("https://signe.test/$moi/rec-a.m4a", repository.signedUrl("$moi/rec-a.m4a"))
        assertTrue("adresse" in lecteur.calls)
    }

    @Test
    fun `l'adresse signee sans lecteur le dit`() = runTest {
        val repository = recitations(FakeOwners(moi), FakeRecitationUploader(), lecteur = null)
        settle(repository)

        val erreur = runCatching { repository.signedUrl("chemin") }.exceptionOrNull()

        assertEquals(RecitationText.AUDIO_UNAVAILABLE, erreur?.message)
    }

    @Test
    fun `les corrections et les retours viennent du lecteur`() = runTest {
        val lecteur = FakeRecitationSource()
        lecteur.correctionsBy = { listOf(correction("c-1", it)) }
        lecteur.feedbackBy = { listOf(retour("f-1", it)) }
        val repository = recitations(FakeOwners(moi), FakeRecitationUploader(), lecteur)
        settle(repository)

        assertEquals(listOf("c-1"), repository.corrections("rec-a").map { it.id })
        assertEquals(listOf("f-1"), repository.generalFeedback("rec-a").map { it.id })
    }

    @Test
    fun `sans lecteur, aucune correction n'est rendue`() = runTest {
        val repository = recitations(FakeOwners(moi), FakeRecitationUploader(), lecteur = null)
        settle(repository)

        assertTrue(repository.corrections("rec-a").isEmpty())
        assertTrue(repository.generalFeedback("rec-a").isEmpty())
    }

    private fun correction(id: String, recitationId: String) = VerseCorrection(
        id = id,
        recitationId = recitationId,
        verseId = 3,
        createdAt = "2026-01-01T10:00:00Z",
    )

    private fun retour(id: String, recitationId: String) = GeneralFeedback(
        id = id,
        recitationId = recitationId,
        createdAt = "2026-01-01T10:00:00Z",
    )

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
