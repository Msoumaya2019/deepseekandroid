package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.local.ProblemReportStore
import com.msoumaya.deepseekandroid.core.data.remote.OwnerStore
import com.msoumaya.deepseekandroid.core.data.remote.ProblemReportSender
import com.msoumaya.deepseekandroid.core.domain.ProblemReportOutcome
import com.msoumaya.deepseekandroid.core.domain.ProblemReportText
import com.msoumaya.deepseekandroid.core.domain.ProblemReports
import com.msoumaya.deepseekandroid.core.model.ProblemReportType
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Éprouve le dépôt « Signalements » : ce qu'il garde, ce qu'il envoie, dans quel ordre, et ce
 * qu'un échec fait au reste.
 *
 * ## Ce qui est mesuré ici, et pourquoi c'est ici
 *
 * Le dépôt ne contient que des règles d'**ordonnancement et de survie** : la capture avant la
 * ligne, la ligne avant la confirmation, la confirmation avant le retrait de la file, une capture
 * déjà déposée qui n'arrête pas la passe, un signalement hors connexion qui est gardé, une
 * confirmation qui manque et qui **retient** le signalement. Aucune ne lève d'exception quand elle
 * est fausse — et chacune a une conséquence visible :
 *
 *  - retirer l'entrée avant la confirmation perd un signalement sur un doute : le serveur l'a
 *    peut-être accepté, et personne ne pourra plus le renvoyer ;
 *  - écrire la ligne avant la capture produit un signalement dont l'image n'existe pas, et que le
 *    serveur accepte — rien ne lie la colonne au fichier ;
 *  - ne pas tolérer une capture déjà présente laisse un signalement en échec **pour toujours**, en
 *    renvoyant à chaque tentative des octets que le serveur a déjà ;
 *  - tenter quand même hors connexion paie une attente pour rien, et l'issue annoncée doit rester
 *    « gardé » ;
 *  - une issue lue sur la tentative au lieu de la file peut annoncer « envoyé » sur un signalement
 *    qui attend encore.
 *
 * ## Ce que ce fichier ne couvre pas, et où la limite passe
 *
 * **L'implémentation Supabase n'est pas exercée ici.** Elle est éprouvée par lecture de
 * l'artefact installé — `storage-kt-android-3.8.0.aar` et `postgrest-kt-android-3.8.0.aar`,
 * relevés au `javap` —, et les signatures employées sont recopiées dans le fichier de source. Ce
 * qui **est** mesuré ici, c'est ce que le dépôt demande : l'adresse, le type MIME, les octets et
 * la forme de la ligne, qui sont les seules choses dont il décide.
 *
 * ## Ce qui n'est pas mesuré, et qui est nommé
 *
 * Le **minuteur** de `start()` : il demande une horloge virtuelle, et l'éprouver ne dirait rien de
 * plus que « une boucle qui attend finit par appeler ». Ce qui compte — que [ProblemReportRepository.flush]
 * fasse son travail — est mesuré directement, et c'est ce que le minuteur appelle.
 */
class ProblemReportRepositoryTest {

    private val moi = "11111111-2222-3333-4444-555555555555"
    private val autre = "99999999-8888-7777-6666-555555555555"

    private lateinit var root: File
    private lateinit var capture: File
    private lateinit var store: ProblemReportStore
    private var ids = 0
    private var horsLigne = false

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("problem-report-repository-test").toFile()
        capture = File(root, "choisie.png").also { it.writeBytes(byteArrayOf(4, 3, 2, 1)) }
        ids = 0
        horsLigne = false
        store = nouvelleBoite()
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun nouvelleBoite(): ProblemReportStore =
        ProblemReportStore(root = root, newId = { "sig-${++ids}" })

    private fun TestScope.depot(
        owners: OwnerStore,
        sender: ProblemReportSender?,
    ): ProblemReportRepository = ProblemReportRepository(
        store = store,
        sender = sender,
        session = owners,
        scope = backgroundScope,
        appVersion = "0.1.0",
        horsLigne = { horsLigne },
        nowIso = { "2026-10-09T09:00:00Z" },
    )

    private fun piece(mime: String = ProblemReports.MIME_PNG) =
        ProblemReportAttachment(path = capture.absolutePath, mime = mime)

    // ------------------------------------------------------------------
    // Sans compte
    // ------------------------------------------------------------------

    @Test
    fun `sans compte, rien n'est ecrit et le depot le dit`() = runTest {
        val sender = FakeProblemReportSender()
        val depot = depot(FakeOwners(null), sender)

        assertNull(depot.send(ProblemReportType.BUG, "Un bug."))

        assertEquals(ProblemReportText.NOT_SIGNED_IN, depot.state.value.notice)
        assertTrue(store.all().isEmpty(), "aucun signalement ne doit etre ecrit sans compte")
        assertTrue(sender.calls.isEmpty(), "aucun appel ne doit partir sans compte")
    }

    // ------------------------------------------------------------------
    // Les refus, avant toute ecriture
    // ------------------------------------------------------------------

    @Test
    fun `une description invalide est refusee avant toute ecriture`() = runTest {
        val sender = FakeProblemReportSender()
        val depot = depot(FakeOwners(moi), sender)

        assertNull(depot.send(ProblemReportType.BUG, "   "))

        assertEquals(ProblemReportText.DESCRIPTION_INVALID, depot.state.value.notice)
        assertTrue(store.all().isEmpty(), "rien ne doit etre ecrit")
        assertTrue(sender.calls.isEmpty(), "rien ne doit partir")
        assertFalse(depot.state.value.done, "un refus n'est pas une issue")
    }

    @Test
    fun `une capture au mauvais format est refusee avant toute ecriture`() = runTest {
        val depot = depot(FakeOwners(moi), FakeProblemReportSender())

        assertNull(depot.send(ProblemReportType.BUG, "Un bug.", piece("image/gif")))

        assertEquals(ProblemReportText.ATTACHMENT_FORMAT, depot.state.value.notice)
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `une capture trop lourde est refusee avant toute ecriture`() = runTest {
        // La taille est relue du **fichier**, et non reçue : le test l'obtient donc en écrivant
        // un vrai fichier trop gros, ce qui est aussi la seule façon de vérifier que le contrôle
        // ne porte pas sur un nombre que l'appelant aurait pu inventer.
        val grosse = File(root, "grosse.png").also {
            it.writeBytes(ByteArray(ProblemReports.SCREENSHOT_MAX_BYTES.toInt() + 1))
        }
        val depot = depot(FakeOwners(moi), FakeProblemReportSender())

        assertNull(
            depot.send(
                ProblemReportType.BUG,
                "Un bug.",
                ProblemReportAttachment(grosse.absolutePath, ProblemReports.MIME_PNG),
            ),
        )

        assertEquals(ProblemReportText.ATTACHMENT_TOO_LARGE, depot.state.value.notice)
        assertTrue(store.all().isEmpty())
        assertFalse(store.folder.exists() && store.folder.listFiles()?.isNotEmpty() == true)
    }

    // ------------------------------------------------------------------
    // Hors connexion
    // ------------------------------------------------------------------

    @Test
    fun `hors ligne, le signalement est garde et annonce comme tel`() = runTest {
        val sender = FakeProblemReportSender()
        val depot = depot(FakeOwners(moi), sender)
        horsLigne = true

        val issue = depot.send(ProblemReportType.AFFICHAGE, "Le texte se chevauche.", piece())

        assertEquals(ProblemReportOutcome.QUEUED, issue)
        assertEquals(ProblemReportText.QUEUED, depot.state.value.notice)
        assertTrue(depot.state.value.done, "l'issue est une issue, meme gardee")
        assertFalse(depot.state.value.busy)
        assertTrue(sender.calls.isEmpty(), "hors ligne, aucun appel ne doit partir")
        assertEquals(1, store.list(moi).size, "le signalement doit etre garde")
    }

    @Test
    fun `hors ligne, la capture est tout de meme copiee`() = runTest {
        // Sans la copie, la capture choisie disparaîtrait dès que le fournisseur d'images reprend
        // son fichier, et le signalement gardé désignerait une image introuvable.
        val depot = depot(FakeOwners(moi), FakeProblemReportSender())
        horsLigne = true

        depot.send(ProblemReportType.BUG, "Un bug.", piece())

        val entree = store.list(moi).single()
        assertTrue(store.exists(entree), "la capture doit avoir ete copiee")
        assertContentEquals(byteArrayOf(4, 3, 2, 1), store.bytesOf(entree))
    }

    @Test
    fun `un signalement garde hors ligne part au retour du reseau`() = runTest {
        val sender = FakeProblemReportSender()
        val depot = depot(FakeOwners(moi), sender)
        horsLigne = true
        depot.send(ProblemReportType.BUG, "Un bug.", piece())
        assertEquals(1, store.list(moi).size)

        horsLigne = false
        assertTrue(depot.flush())

        assertEquals(1, sender.rows.size, "le signalement doit partir")
        assertTrue(store.list(moi).isEmpty(), "la file doit etre videe")
        assertEquals(listOf("capture", "ligne", "confirmation"), sender.calls)
    }

    // ------------------------------------------------------------------
    // En ligne
    // ------------------------------------------------------------------

    @Test
    fun `en ligne, le signalement part et l'accuse le dit`() = runTest {
        val sender = FakeProblemReportSender()
        val depot = depot(FakeOwners(moi), sender)

        val issue = depot.send(ProblemReportType.AUDIO, "Le son grésille.", piece())

        assertEquals(ProblemReportOutcome.SENT, issue)
        assertEquals(ProblemReportText.SENT, depot.state.value.notice)
        assertTrue(depot.state.value.done)
        assertTrue(store.all().isEmpty(), "la file doit etre videe")
    }

    @Test
    fun `la capture part avant la ligne, et la confirmation apres`() = runTest {
        // L'ordre est celui de l'original, et chacun des trois gestes a sa raison : la ligne avant
        // la capture désignerait une image que le compartiment ne contient pas ; la confirmation
        // avant la ligne confirmerait un signalement qui n'existe pas.
        val sender = FakeProblemReportSender()
        val depot = depot(FakeOwners(moi), sender)

        depot.send(ProblemReportType.BUG, "Un bug.", piece())

        assertEquals(listOf("capture", "ligne", "confirmation"), sender.calls)
    }

    @Test
    fun `la ligne porte le compte, la version, la plateforme et le statut`() = runTest {
        val sender = FakeProblemReportSender()
        val depot = depot(FakeOwners(moi), sender)

        depot.send(ProblemReportType.NOTIFICATION, "Aucune notification.", piece())

        val ligne = sender.rows.single()
        assertEquals(moi, ligne.userId)
        assertEquals(ProblemReportType.NOTIFICATION, ligne.type)
        assertEquals("Aucune notification.", ligne.description)
        assertEquals("0.1.0", ligne.appVersion)
        assertEquals(ProblemReports.PLATFORM_ANDROID, ligne.platform)
        assertEquals(ProblemReports.STATUS_OPEN, ligne.status)
        assertEquals("2026-10-09T09:00:00Z", ligne.createdAt)
        assertEquals(ProblemReports.screenshotPath(moi, ligne.id, ProblemReports.EXTENSION_PNG), ligne.screenshotPath)
    }

    @Test
    fun `la capture part avec son adresse et son type mime`() = runTest {
        val sender = FakeProblemReportSender()
        val depot = depot(FakeOwners(moi), sender)

        depot.send(ProblemReportType.BUG, "Un bug.", piece())

        val envoi = sender.uploads.single()
        val ligne = sender.rows.single()
        assertEquals(ligne.screenshotPath, envoi.path)
        assertEquals(ProblemReports.MIME_PNG, envoi.mimeType)
        assertEquals(4, envoi.size, "les octets envoyes sont ceux de la copie")
    }

    @Test
    fun `sans capture, aucun depot de fichier n'a lieu`() = runTest {
        val sender = FakeProblemReportSender()
        val depot = depot(FakeOwners(moi), sender)

        val issue = depot.send(ProblemReportType.AUTRE, "Une remarque.", null)

        assertEquals(ProblemReportOutcome.SENT, issue)
        assertEquals(listOf("ligne", "confirmation"), sender.calls)
        assertTrue(sender.uploads.isEmpty())
        assertNull(sender.rows.single().screenshotPath)
    }

    @Test
    fun `sans projet configure, le signalement est garde et le depot le dit`() = runTest {
        // Le mode sans projet est **normal** — l'application fonctionne entièrement hors ligne —,
        // et il ne doit pas se lire comme une panne : le signalement est gardé, et la phrase
        // annonce une mise en attente.
        val depot = depot(FakeOwners(moi), sender = null)

        val issue = depot.send(ProblemReportType.BUG, "Un bug.", piece())

        assertEquals(ProblemReportOutcome.QUEUED, issue)
        assertEquals(ProblemReportText.QUEUED, depot.state.value.notice)
        assertEquals(1, store.list(moi).size)
        assertFalse(depot.flush(), "sans expediteur, la passe ne fait rien")
        assertEquals(1, store.list(moi).size)
    }

    // ------------------------------------------------------------------
    // Les pannes
    // ------------------------------------------------------------------

    @Test
    fun `une capture deja deposee n'arrete pas la passe`() = runTest {
        // Le refus d'un fichier déjà présent signifie que les octets sont arrivés : l'étape est
        // faite, et il ne manque que la ligne. Traiter ce refus comme une panne laisserait le
        // signalement dans la file **pour toujours**.
        val sender = FakeProblemReportSender()
        sender.refuseUpload = { IOException("The resource already exists") }
        val depot = depot(FakeOwners(moi), sender)

        val issue = depot.send(ProblemReportType.BUG, "Un bug.", piece())

        assertEquals(ProblemReportOutcome.SENT, issue)
        assertEquals(listOf("capture", "ligne", "confirmation"), sender.calls)
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `une ligne non confirmee garde le signalement`() = runTest {
        // Le cas qui protège d'une perte : le serveur ne rend pas la ligne. Le signalement reste
        // dans la file, et l'issue annoncée est « gardé » — jamais « envoyé ».
        val sender = FakeProblemReportSender()
        sender.confirme = { false }
        val depot = depot(FakeOwners(moi), sender)

        val issue = depot.send(ProblemReportType.BUG, "Un bug.", piece())

        assertEquals(ProblemReportOutcome.QUEUED, issue)
        assertEquals(ProblemReportText.QUEUED, depot.state.value.notice)
        assertEquals(1, store.list(moi).size, "le signalement doit rester dans la file")
        assertEquals(listOf("capture", "ligne", "confirmation"), sender.calls)
    }

    @Test
    fun `une confirmation qui echoue garde le signalement`() = runTest {
        // Une panne de **lecture** n'est pas une absence : les deux gardent le signalement, et
        // c'est le bon défaut — mieux vaut un doublon possible qu'un signalement perdu.
        val sender = FakeProblemReportSender()
        sender.refuseConfirm = IOException("reseau perdu")
        val depot = depot(FakeOwners(moi), sender)

        val issue = depot.send(ProblemReportType.BUG, "Un bug.", piece())

        assertEquals(ProblemReportOutcome.QUEUED, issue)
        assertEquals(1, store.list(moi).size)
    }

    @Test
    fun `une ligne refusee garde le signalement`() = runTest {
        val sender = FakeProblemReportSender()
        sender.refuseInsert = IOException("RLS refuse")
        val depot = depot(FakeOwners(moi), sender)

        val issue = depot.send(ProblemReportType.BUG, "Un bug.", piece())

        // L'échec de la passe est **absorbé** par l'envoi : l'issue est lue sur la file, et elle
        // vaut « gardé ». La personne voit une issue — le signalement est enregistré —, et non
        // une panne : c'est exactement ce que l'original annonce.
        assertEquals(ProblemReportOutcome.QUEUED, issue)
        assertEquals(ProblemReportText.QUEUED, depot.state.value.notice)
        assertTrue(depot.state.value.done)
        assertEquals(1, store.list(moi).size)
    }

    @Test
    fun `une capture disparue ne part pas et reste dans la file`() = runTest {
        // Écrire la ligne désignerait une image que personne ne pourra ouvrir. Le signalement
        // reste donc dans la file, faute de mieux — et c'est visible, ce qui vaut mieux qu'une
        // ligne fantôme.
        val sender = FakeProblemReportSender()
        val depot = depot(FakeOwners(moi), sender)
        horsLigne = true
        depot.send(ProblemReportType.BUG, "Un bug.", piece())
        store.fileOf(store.list(moi).single())?.delete()

        horsLigne = false
        val issue = depot.send(ProblemReportType.AUTRE, "Une autre remarque.", null)

        assertNotNull(issue)
        assertTrue(sender.rows.isEmpty(), "aucune ligne ne doit etre ecrite")
        assertEquals(2, store.list(moi).size, "les deux signalements restent dans la file")
    }

    // ------------------------------------------------------------------
    // L'ordre de la file
    // ------------------------------------------------------------------

    @Test
    fun `la file est videe depuis la tete`() = runTest {
        val sender = FakeProblemReportSender()
        val depot = depot(FakeOwners(moi), sender)
        horsLigne = true
        depot.send(ProblemReportType.BUG, "premier", null)
        depot.send(ProblemReportType.AUDIO, "deuxieme", null)
        depot.send(ProblemReportType.AUTRE, "troisieme", null)

        horsLigne = false
        depot.flush()

        assertEquals(
            listOf("premier", "deuxieme", "troisieme"),
            sender.rows.map { it.description },
            "les signalements doivent partir dans leur ordre d'arrivee",
        )
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `un echec en tete retient les suivants, comme dans l'original`() = runTest {
        // C'est la différence assumée avec la file des récitations : l'original des signalements
        // n'a pas de `try` dans sa boucle, donc une erreur en sort — et la passe **lève**, au lieu
        // de rendre la main. Le premier signalement échoue, et le second — pourtant valide — n'est
        // même pas tenté. C'est acceptable ici parce que la boîte est minuscule, qu'une capture
        // vit dans un dossier que le système ne récupère pas, et qu'une panne réseau est
        // transitoire : la tête de la file est précisément ce qu'il faut réessayer en premier.
        val sender = FakeProblemReportSender()
        sender.refuseInsert = IOException("RLS refuse")
        val depot = depot(FakeOwners(moi), sender)
        horsLigne = true
        depot.send(ProblemReportType.BUG, "premier", null)
        depot.send(ProblemReportType.AUDIO, "deuxieme", null)

        horsLigne = false
        assertFailsWith<IOException> { depot.flush() }

        assertEquals(2, store.list(moi).size, "les deux signalements restent dans la file")
        assertEquals(
            listOf("ligne"),
            sender.calls,
            "la passe doit s'arreter au premier echec, sans presenter le suivant",
        )
    }

    // ------------------------------------------------------------------
    // Le compte
    // ------------------------------------------------------------------

    @Test
    fun `un signalement d'un autre compte ne part pas sous le mien`() = runTest {
        // La file est commune, et la lecture filtre par compte : c'est ce filtre qui empêche un
        // signalement d'être poussé sous le jeton d'un autre.
        val sender = FakeProblemReportSender()
        val owners = FakeOwners(autre)
        val depot = depot(owners, sender)
        horsLigne = true
        depot.send(ProblemReportType.BUG, "le sien", null)

        owners.setOwner(moi)
        depot.flush()

        assertTrue(sender.rows.isEmpty(), "le signalement d'un autre compte ne doit pas partir")
        assertEquals(1, store.all().size)
        assertTrue(store.list(moi).isEmpty(), "la file du compte ouvert est vide")
    }

    @Test
    fun `le compte est lu a chaque passe, et non garde`() = runTest {
        val sender = FakeProblemReportSender()
        val owners = FakeOwners(moi)
        val depot = depot(owners, sender)

        depot.send(ProblemReportType.BUG, "Un bug.", null)
        assertEquals(moi, sender.rows.single().userId)

        owners.setOwner(autre)
        depot.send(ProblemReportType.BUG, "Un autre bug.", null)
        assertEquals(autre, sender.rows.last().userId)
        assertTrue(store.all().isEmpty())
    }

    // ------------------------------------------------------------------
    // La garde
    // ------------------------------------------------------------------

    @Test
    fun `un second envoi pendant une passe n'envoie pas deux fois les memes octets`() = runTest {
        // Deux appels concurrents — un geste et le retour du réseau — sérialisent sur la garde :
        // le second attend, puis trouve la file vide. Sans elle, les deux repartiraient de la même
        // tête et déposeraient deux fois les mêmes octets.
        val sender = FakeProblemReportSender()
        val depot = depot(FakeOwners(moi), sender)
        horsLigne = true
        depot.send(ProblemReportType.BUG, "Un bug.", piece())

        horsLigne = false
        val premier = backgroundScope.async { depot.flush() }
        val second = backgroundScope.async { depot.flush() }
        premier.await()
        second.await()

        assertEquals(1, sender.rows.size, "le signalement ne doit partir qu'une fois")
        assertEquals(1, sender.uploads.size, "les octets ne doivent partir qu'une fois")
        assertTrue(store.all().isEmpty())
    }

    // ------------------------------------------------------------------
    // La remise à zéro de l'écran
    // ------------------------------------------------------------------

    @Test
    fun `la remise a zero efface ce que le dernier geste a laisse`() = runTest {
        // L'écran de signalement est transitoire : il est monté à l'ouverture et démonté à la
        // fermeture, donc ses `remember` renaissent vides. Ceux du dépôt, non — et sans cette
        // remise à zéro, une feuille rouverte montrerait la confirmation du signalement d'avant,
        // si bien que personne n'écrirait le suivant.
        val sender = FakeProblemReportSender()
        val depot = depot(FakeOwners(moi), sender)

        assertEquals(ProblemReportOutcome.SENT, depot.send(ProblemReportType.BUG, "Un bug.", null))
        assertTrue(depot.state.value.done, "le premier envoi doit atteindre la confirmation")
        assertNotNull(depot.state.value.notice)

        depot.reset()

        assertFalse(depot.state.value.done, "la feuille rouverte montrerait la confirmation")
        assertNull(depot.state.value.notice, "la phrase du geste precedent resterait affichee")
        assertFalse(depot.state.value.busy)
    }

    @Test
    fun `la remise a zero est refusee pendant un envoi`() = runTest {
        // `busy` est ce qui empêche un second appui : le remettre à zéro en pleine passe rouvrirait
        // le bouton, et deux signalements partiraient pour un seul geste. La porte tient la passe
        // ouverte **sans dépendre du temps** — une attente en millisecondes ferait un test qui
        // passe ici et échoue ailleurs.
        val sender = FakeProblemReportSender()
        val porte = CompletableDeferred<Unit>()
        sender.porte = porte
        val depot = depot(FakeOwners(moi), sender)

        val envoi = backgroundScope.async { depot.send(ProblemReportType.BUG, "Un bug.", null) }
        attendre("l'ecriture de la ligne") { sender.calls.isNotEmpty() }
        assertTrue(depot.state.value.busy, "le depot doit se dire en vol")

        depot.reset()

        assertTrue(depot.state.value.busy, "un effacement en pleine passe rouvrirait le bouton")

        porte.complete(Unit)
        assertEquals(ProblemReportOutcome.SENT, envoi.await())
        assertTrue(store.all().isEmpty(), "le signalement doit partir malgre l'effacement refuse")
    }

    /**
     * Attend qu'un prédicat devienne vrai, avec une borne.
     *
     * Une boucle sans borne ne dit rien quand elle échoue : elle bloque le test jusqu'au délai de
     * l'exécuteur, et le rapport ne nomme pas ce qui manquait. Ici, l'échec est explicite.
     */
    private fun TestScope.attendre(quoi: String, predicat: () -> Boolean) {
        val limite = System.nanoTime() + DELAI_DE_GARDE_NANOS
        while (!predicat()) {
            if (System.nanoTime() > limite) error("$quoi n'est pas arrive dans le delai")
            runCurrent()
            Thread.sleep(2)
        }
    }

    private companion object {
        /** La borne d'une attente, en nanosecondes. Large : elle n'agit que sur un test qui échoue. */
        const val DELAI_DE_GARDE_NANOS = 5_000_000_000L
    }
}
