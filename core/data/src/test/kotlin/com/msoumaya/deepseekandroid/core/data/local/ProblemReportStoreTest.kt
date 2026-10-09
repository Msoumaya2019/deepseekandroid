package com.msoumaya.deepseekandroid.core.data.local

import com.msoumaya.deepseekandroid.core.domain.ProblemReports
import com.msoumaya.deepseekandroid.core.model.ProblemReportType
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Éprouve la boîte d'envoi des signalements et les captures qui vont avec.
 *
 * ## Ce qui est mesuré ici, et pourquoi c'est ici
 *
 * La boîte ne porte que des règles qui n'ont **pas d'écran** : la copie de la capture au moment du
 * geste, le rognage de la description, l'ordre d'arrivée, le partage par compte, et ce qu'un
 * retrait efface. Aucune ne lève d'exception quand elle est fausse — et chacune a une conséquence
 * visible :
 *
 *  - sans copie, une capture choisie mais pas encore envoyée disparaîtrait dès que le fournisseur
 *    d'images reprend son fichier ;
 *  - une description rangée non rognée ferait relire une valeur différente de celle qui a été
 *    validée, et le serveur la refuserait ;
 *  - une file rendue dans un autre ordre ferait envoyer un signalement plus récent avant un plus
 *    ancien, et un échec du premier les retiendrait tous ;
 *  - une file non filtrée montrerait — et enverrait — les signalements d'un autre compte ;
 *  - un retrait qui ne touche pas la capture laisserait des mégaoctets invisibles.
 *
 * ## Ce qui n'est pas mesuré ici
 *
 * La **mise de côté** d'un document illisible est le comportement de [JsonFileStore], et elle est
 * éprouvée avec lui. La reprise après un redémarrage, elle, est mesurée : c'est la seule façon de
 * savoir que la file est bien sur le disque et non en mémoire.
 */
class ProblemReportStoreTest {

    private val moi = "11111111-2222-3333-4444-555555555555"
    private val autre = "99999999-8888-7777-6666-555555555555"

    private lateinit var root: File
    private lateinit var capture: File
    private var ids = 0

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("problem-report-store-test").toFile()
        capture = File(root, "choisie.png").also { it.writeBytes(byteArrayOf(9, 8, 7, 6)) }
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun nouvelleBoite(): ProblemReportStore =
        ProblemReportStore(root = root, newId = { "sig-${++ids}" })

    private fun brouillon(
        userId: String = moi,
        type: ProblemReportType = ProblemReportType.BUG,
        description: String = "L’écran du lecteur se ferme tout seul.",
        avecCapture: Boolean = true,
    ) = ProblemReportDraft(
        userId = userId,
        type = type,
        description = description,
        appVersion = "0.1.0",
        platform = ProblemReports.PLATFORM_ANDROID,
        createdAt = "2026-10-09T09:00:00Z",
        attachmentPath = if (avecCapture) capture.absolutePath else null,
        attachmentExtension = if (avecCapture) ProblemReports.EXTENSION_PNG else null,
        attachmentMime = if (avecCapture) ProblemReports.MIME_PNG else null,
    )

    // ------------------------------------------------------------------
    // L'écriture : la copie de la capture
    // ------------------------------------------------------------------

    @Test
    fun `l'ecriture copie la capture dans le dossier de l'application`() = runTest {
        val boite = nouvelleBoite()
        val entree = boite.enqueue(brouillon())

        val fichier = boite.fileOf(entree)
        assertTrue(fichier != null && fichier.isFile, "la copie doit exister")
        assertEquals(
            boite.folder.canonicalFile,
            fichier.canonicalFile.parentFile,
            "la copie doit vivre dans le dossier des captures",
        )
        assertContentEquals(capture.readBytes(), fichier.readBytes(), "la copie doit etre identique")
    }

    @Test
    fun `l'identifiant sert de nom de fichier, extension comprise`() = runTest {
        val boite = nouvelleBoite()
        val entree = boite.enqueue(brouillon())

        assertEquals("${entree.id}.png", boite.fileOf(entree)?.name)
    }

    @Test
    fun `l'adresse distante porte le compte, l'identifiant et l'extension`() = runTest {
        // C'est la contrainte `screenshot_path` du schéma : `user_id||'/'||id||'.jpg'`, ou la
        // même avec `.png`. Une adresse composée autrement ferait refuser l'insertion, et le
        // signalement resterait dans la file **pour toujours**.
        val boite = nouvelleBoite()
        val entree = boite.enqueue(brouillon())

        assertEquals(
            ProblemReports.screenshotPath(moi, entree.id, ProblemReports.EXTENSION_PNG),
            entree.report.screenshotPath,
        )
        assertEquals("$moi/${entree.id}.png", entree.report.screenshotPath)
    }

    @Test
    fun `sans capture, ni copie ni adresse`() = runTest {
        val boite = nouvelleBoite()
        val entree = boite.enqueue(brouillon(avecCapture = false))

        assertNull(entree.localPath)
        assertNull(entree.report.screenshotPath)
        assertNull(entree.mime)
        assertNull(boite.fileOf(entree))
        assertFalse(boite.folder.exists() && boite.folder.listFiles()?.isNotEmpty() == true)
    }

    @Test
    fun `la description est rognee avant d'etre rangee`() = runTest {
        // L'original range `description:text` où `text` est rogné : c'est la valeur validée qui
        // doit être la valeur envoyée, sans quoi le serveur refuserait une longueur que le client
        // avait mesurée autrement.
        val boite = nouvelleBoite()
        val entree = boite.enqueue(brouillon(description = "  un bug  "))

        assertEquals("un bug", entree.report.description)
    }

    @Test
    fun `le compte et la nature sont ceux du brouillon`() = runTest {
        val boite = nouvelleBoite()
        val entree = boite.enqueue(brouillon(userId = autre, type = ProblemReportType.AUDIO))

        assertEquals(autre, entree.userId)
        assertEquals(autre, entree.report.userId)
        assertEquals(ProblemReportType.AUDIO, entree.report.type)
        assertEquals(ProblemReports.PLATFORM_ANDROID, entree.report.platform)
        assertEquals("0.1.0", entree.report.appVersion)
        assertEquals("2026-10-09T09:00:00Z", entree.report.createdAt)
        assertEquals(ProblemReports.STATUS_OPEN, entree.report.status)
    }

    @Test
    fun `l'identifiant et le compte sont lus sur la ligne`() = runTest {
        // La table d'origine les porte en colonnes **et** dans la charge utile. Ici il n'y a
        // qu'une source, et l'entrée les lit dessus : deux copies d'un même fait finiraient par
        // diverger, et une divergence retirerait de la file l'entrée d'un autre compte.
        val boite = nouvelleBoite()
        val entree = boite.enqueue(brouillon(userId = autre))

        assertEquals(entree.report.id, entree.id)
        assertEquals(entree.report.userId, entree.userId)
    }

    // ------------------------------------------------------------------
    // Les refus
    // ------------------------------------------------------------------

    @Test
    fun `une description vide est refusee et rien n'est copie`() = runTest {
        val boite = nouvelleBoite()

        assertFailsWith<IllegalArgumentException> {
            boite.enqueue(brouillon(description = "   "))
        }

        assertTrue(boite.all().isEmpty(), "aucune entree ne doit etre ecrite")
        assertFalse(
            boite.folder.exists() && boite.folder.listFiles()?.isNotEmpty() == true,
            "aucune capture ne doit rester dans le dossier",
        )
    }

    @Test
    fun `une description trop longue est refusee`() = runTest {
        val boite = nouvelleBoite()

        assertFailsWith<IllegalArgumentException> {
            boite.enqueue(brouillon(description = "a".repeat(ProblemReports.DESCRIPTION_MAX + 1)))
        }
        assertTrue(boite.all().isEmpty())
    }

    // ------------------------------------------------------------------
    // La file
    // ------------------------------------------------------------------

    @Test
    fun `la file est rendue dans l'ordre d'arrivee`() = runTest {
        // L'ordre est celui de l'original (`ORDER BY rowid`), et il compte : la file est vidée
        // **depuis la tête**, et un échec laisse l'entrée en place. Rendre la plus récente en
        // premier ferait envoyer un signalement avant un plus ancien, et un échec du premier les
        // retiendrait tous.
        val boite = nouvelleBoite()
        val premier = boite.enqueue(brouillon(description = "premier"))
        val deuxieme = boite.enqueue(brouillon(description = "deuxieme"))
        val troisieme = boite.enqueue(brouillon(description = "troisieme"))

        assertEquals(
            listOf(premier.id, deuxieme.id, troisieme.id),
            boite.list(moi).map { it.id },
        )
        assertEquals(premier.id, boite.first(moi)?.id)
    }

    @Test
    fun `la file ne rend que les signalements du compte demande`() = runTest {
        val boite = nouvelleBoite()
        val mien = boite.enqueue(brouillon())
        boite.enqueue(brouillon(userId = autre))

        assertEquals(listOf(mien.id), boite.list(moi).map { it.id })
        assertEquals(2, boite.all().size, "la boite porte bien les deux entrees")
        assertNull(boite.first("inconnu"))
    }

    // ------------------------------------------------------------------
    // Le retrait
    // ------------------------------------------------------------------

    @Test
    fun `le retrait efface la capture et l'entree`() = runTest {
        val boite = nouvelleBoite()
        val entree = boite.enqueue(brouillon())
        val fichier = boite.fileOf(entree)

        boite.remove(entree)

        assertTrue(fichier != null && !fichier.isFile, "la capture doit etre effacee")
        assertTrue(boite.all().isEmpty(), "l'entree doit quitter la boite")
    }

    @Test
    fun `le retrait ne touche pas l'entree d'un autre compte`() = runTest {
        // Le filtre porte sur le couple (identifiant, compte) : une entrée ne peut pas être
        // retirée au nom d'un autre, et sa capture ne doit pas partir avec.
        val boite = nouvelleBoite()
        val entree = boite.enqueue(brouillon())

        boite.remove(entree.copy(report = entree.report.copy(userId = autre)))

        assertEquals(1, boite.all().size, "l'entree doit survivre")
        assertTrue(boite.exists(entree), "la capture doit survivre")
    }

    @Test
    fun `une capture deja effacee ne bloque pas le retrait`() = runTest {
        // L'effacement rend `false` au lieu de lever : bloquer le retrait de l'entrée sur un
        // fichier déjà absent laisserait un signalement dans la file pour toujours.
        val boite = nouvelleBoite()
        val entree = boite.enqueue(brouillon())
        boite.fileOf(entree)?.delete()

        boite.remove(entree)

        assertTrue(boite.all().isEmpty())
    }

    // ------------------------------------------------------------------
    // Les octets, et la persistance
    // ------------------------------------------------------------------

    @Test
    fun `les octets rendus sont ceux de la capture`() = runTest {
        val boite = nouvelleBoite()
        val entree = boite.enqueue(brouillon())

        assertContentEquals(byteArrayOf(9, 8, 7, 6), boite.bytesOf(entree))
        assertTrue(boite.exists(entree))
    }

    @Test
    fun `une capture disparue rend null`() = runTest {
        // Le cas réel : le fournisseur d'images reprend son fichier, ou une restauration de
        // sauvegarde rend la file sans les captures. La synchronisation doit alors **refuser**
        // d'écrire la ligne plutôt que de désigner une image que personne ne pourra ouvrir.
        val boite = nouvelleBoite()
        val entree = boite.enqueue(brouillon())
        boite.fileOf(entree)?.delete()

        assertNull(boite.bytesOf(entree))
        assertFalse(boite.exists(entree))
    }

    @Test
    fun `la boite survit a un redemarrage`() = runTest {
        val premiere = nouvelleBoite()
        val entree = premiere.enqueue(brouillon())
        val chemin = entree.localPath

        val seconde = nouvelleBoite()

        val relue = seconde.list(moi).single()
        assertEquals(entree.id, relue.id)
        assertEquals(entree.report.description, relue.report.description)
        assertEquals(entree.report.type, relue.report.type)
        assertEquals(entree.report.screenshotPath, relue.report.screenshotPath)
        assertEquals(chemin, relue.localPath)
        assertEquals(entree.mime, relue.mime)
        assertTrue(seconde.exists(relue), "la capture doit avoir survecu au redemarrage")
    }

    @Test
    fun `la file est un document unique, hors de tout dossier de compte`() = runTest {
        // Elle est **publique** parce que le test doit nommer le document qu'il inspecte :
        // vérifier que la file n'est pas rangée dans un dossier de compte demande de le lire.
        val boite = nouvelleBoite()
        boite.enqueue(brouillon())

        val document = File(root, ProblemReportStore.QUEUE_FILE)
        assertTrue(document.isFile, "le document de la file doit exister a la racine de l'etat")
        assertEquals(File(root, ProblemReportStore.DIRECTORY), boite.folder)
    }
}
