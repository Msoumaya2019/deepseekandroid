package com.msoumaya.deepseekandroid.core.data.local

import com.msoumaya.deepseekandroid.core.domain.ArchivePhase
import com.msoumaya.deepseekandroid.core.domain.ArchiveProgress
import com.msoumaya.deepseekandroid.core.domain.QuranArchive
import com.msoumaya.deepseekandroid.core.domain.ZIP_LINES_PER_PAGE
import com.msoumaya.deepseekandroid.core.domain.zipLineFileName
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.UnknownHostException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Éprouve l'installation de la source « Coran 1441 ».
 *
 * Trois règles y sont tenues, et ce sont les trois qui casseraient en silence — c'est-à-dire
 * sans qu'aucun écran ne se plaigne, seulement par des pages fausses :
 *
 *  1. **une reprise en 206 s'ajoute, une réponse 200 remplace.** Écrire la suite d'un partiel à
 *     partir d'un flux qui repart de zéro donne une archive de **taille correcte et de contenu
 *     faux** : elle passe le contrôle de taille, s'installe, et se voit à l'écran sur une page
 *     décalée. C'est le pire des défauts possibles ici ;
 *  2. **le témoin n'est écrit qu'à la fin.** Sa présence est une preuve, pas une intention : une
 *     installation interrompue ne doit jamais passer pour complète, sinon le lecteur affiche un
 *     moushaf amputé sans le dire ;
 *  3. **une archive douteuse est refusée en entier.** Une entrée qui ressemble à une page et sort
 *     du moushaf, une image de mauvaise dimension, un compte de fichiers faux : refuser vaut
 *     mieux que d'installer des pages qui ne sont pas celles qu'on croit.
 *
 * Le paquet réel pèse 102 608 011 octets, ce que les tests ne fabriquent pas : la taille attendue
 * est **injectée**, et c'est la même valeur qui règle la reprise, l'avancement et le contrôle
 * final. Une doublure réseau remplace le transport — l'installation ne s'éprouve pas « quand il y
 * a du réseau ».
 */
class QuranArchiveInstallerTest {

    private val created = mutableListOf<File>()

    @AfterTest
    fun cleanup() {
        created.forEach { it.deleteRecursively() }
    }

    // ---------------------------------------------------------------- doublures

    /** Un transport qui sert [payload] au décalage demandé, avec le code voulu. */
    private class FakeTransport(
        private val payload: ByteArray,
        private val status: (Long) -> Int = { 206 },
    ) : ArchiveTransport {
        val asked = mutableListOf<Long>()

        override suspend fun open(fromByte: Long): ArchiveStream {
            asked += fromByte
            val code = status(fromByte)
            val body = when (code) {
                206 -> payload.copyOfRange(fromByte.toInt().coerceAtMost(payload.size), payload.size)
                200 -> payload
                else -> ByteArray(0)
            }
            return ArchiveStream(code, ByteArrayInputStream(body))
        }
    }

    // ---------------------------------------------------------------- fabriques

    /** Une image de ligne valide : signature PNG sur deux octets et dimensions 1440×232. */
    private fun png(
        width: Int = QuranArchive.IMAGE_WIDTH,
        height: Int = QuranArchive.IMAGE_HEIGHT,
        extra: Int = 0,
    ): ByteArray {
        val bytes = ByteArray(24 + extra)
        bytes[0] = 137.toByte()
        bytes[1] = 80.toByte()
        writeUint32(bytes, 16, width)
        writeUint32(bytes, 20, height)
        return bytes
    }

    private fun writeUint32(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = (value ushr 24).toByte()
        bytes[at + 1] = (value ushr 16).toByte()
        bytes[at + 2] = (value ushr 8).toByte()
        bytes[at + 3] = value.toByte()
    }

    private fun archive(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /**
     * Le paquet complet, construit une fois : 9 060 pages **plus** des entrées sans rapport.
     *
     * Les entrées parasites sont là exprès : une archive réelle en contient — fichiers de
     * description, dossiers — et une installation qui les refuserait échouerait sur une archive
     * parfaitement valide.
     */
    private val fullArchive: ByteArray by lazy {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        entries += "readme.txt" to "coran 1441".toByteArray()
        entries += "width_1440/" to ByteArray(0)
        entries += "width_1440/7/12.jpg" to ByteArray(0)
        for (page in 1..604) {
            for (line in 1..ZIP_LINES_PER_PAGE) {
                entries += "width_1440/$page/$line.png" to png()
            }
        }
        archive(*entries.toTypedArray())
    }

    private fun temp(): File =
        Files.createTempDirectory("quran-archive-test").toFile().also { created += it }

    private fun installer(
        directory: File,
        transport: ArchiveTransport,
        expectedBytes: Long,
        clock: () -> String = { "2026-10-04T00:00:00Z" },
    ) = QuranArchiveInstaller(directory, transport, expectedBytes, clock)

    private fun zipOf(directory: File) = File(directory, QuranArchive.ZIP_FILE)

    private fun witnessOf(directory: File) = File(directory, QuranArchive.READY_FILE)

    // ---------------------------------------------------------------- reprise

    @Test
    fun `une reprise en 206 ajoute au partiel au lieu de le remplacer`() = runTest {
        val payload = ByteArray(4000) { (it % 251).toByte() }
        val directory = temp()
        zipOf(directory).writeBytes(payload.copyOfRange(0, 1000))
        val transport = FakeTransport(payload, status = { 206 })

        // L'extraction échouera — ce n'est pas un ZIP — mais le téléchargement, lui, doit aboutir.
        assertFailsWith<IOException> { installer(directory, transport, 4000L).install() }

        assertEquals(listOf(1000L), transport.asked, "la reprise doit repartir du partiel")
        assertContentEquals(payload, zipOf(directory).readBytes())
    }

    @Test
    fun `une reponse 200 jette le partiel au lieu d'y ajouter`() = runTest {
        val payload = ByteArray(4000) { (it % 251).toByte() }
        val directory = temp()
        zipOf(directory).writeBytes(payload.copyOfRange(0, 1000))
        // Le serveur ignore le décalage et renvoie tout : ajouter à l'aveugle donnerait
        // 5 000 octets dont les 1 000 premiers seraient comptés deux fois.
        val transport = FakeTransport(payload, status = { 200 })

        assertFailsWith<IOException> { installer(directory, transport, 4000L).install() }

        assertEquals(listOf(1000L), transport.asked)
        assertContentEquals(payload, zipOf(directory).readBytes())
    }

    @Test
    fun `un partiel plus long que l'attendu est efface avant de reprendre`() = runTest {
        val payload = ByteArray(4000) { 7 }
        val directory = temp()
        zipOf(directory).writeBytes(ByteArray(9000) { 3 })
        val transport = FakeTransport(payload, status = { 206 })

        assertFailsWith<IOException> { installer(directory, transport, 4000L).install() }

        assertEquals(listOf(0L), transport.asked, "un fichier trop long n'est pas un partiel")
        assertContentEquals(payload, zipOf(directory).readBytes())
    }

    @Test
    fun `un partiel de la bonne taille sans marque repart de zero`() = runTest {
        val payload = ByteArray(4000) { 5 }
        val directory = temp()
        // Bonne taille, mais aucune marque : on ne peut pas savoir si le fichier est complet,
        // et le supposer serait installer des octets dont personne n'a vérifié la fin.
        zipOf(directory).writeBytes(payload)
        val transport = FakeTransport(payload, status = { 206 })

        assertFailsWith<IOException> { installer(directory, transport, 4000L).install() }

        assertEquals(listOf(0L), transport.asked)
        assertContentEquals(payload, zipOf(directory).readBytes())
    }

    @Test
    fun `une marque avec la bonne taille evite de retélécharger`() = runTest {
        val payload = ByteArray(4000) { 9 }
        val directory = temp()
        zipOf(directory).writeBytes(payload)
        File(directory, QuranArchive.COMPLETE_FILE).writeText("{}")
        val transport = FakeTransport(payload, status = { 206 })

        assertFailsWith<IOException> { installer(directory, transport, 4000L).install() }

        assertTrue(transport.asked.isEmpty(), "le paquet est déjà là, entier et marqué")
    }

    // ---------------------------------------------------------------- échecs réseau

    @Test
    fun `une taille fausse apres telechargement est refusee`() = runTest {
        val directory = temp()
        val transport = FakeTransport(ByteArray(1000) { 1 }, status = { 200 })

        val error = assertFailsWith<IOException> { installer(directory, transport, 4000L).install() }

        assertEquals(QuranArchive.INCOMPLETE, error.message)
        assertFalse(witnessOf(directory).exists(), "aucun témoin ne doit être écrit")
    }

    @Test
    fun `un code refuse arrete l'installation`() = runTest {
        val directory = temp()
        val transport = FakeTransport(ByteArray(4000), status = { 404 })
        val states = mutableListOf<ArchiveProgress>()

        val error = assertFailsWith<IOException> {
            installer(directory, transport, 4000L).install { states += it }
        }

        assertTrue(error.message!!.contains("404"), "le code doit apparaître : ${error.message}")
        assertEquals(ArchivePhase.ERROR, states.last().phase)
        assertFalse(zipOf(directory).exists(), "rien n'a été écrit")
    }

    @Test
    fun `une coupure reseau laisse le partiel en place et donne le message de connexion`() = runTest {
        val directory = temp()
        val prefix = ByteArray(1500) { 4 }
        val transport = ArchiveTransport {
            ArchiveStream(206, bytesThenFail(prefix, UnknownHostException("dns")))
        }
        val states = mutableListOf<ArchiveProgress>()

        assertFailsWith<UnknownHostException> {
            installer(directory, transport, 4000L).install { states += it }
        }

        assertEquals(prefix.size.toLong(), zipOf(directory).length(), "le partiel doit survivre")
        assertEquals(QuranArchive.NETWORK, states.last().message)
        assertFalse(witnessOf(directory).exists())
    }

    private fun bytesThenFail(prefix: ByteArray, error: Throwable): InputStream = object : InputStream() {
        private var at = 0
        override fun read(): Int {
            if (at < prefix.size) return prefix[at++].toInt() and 0xFF
            throw error
        }
    }

    // ---------------------------------------------------------------- extraction

    @Test
    fun `une installation complete ecrit les 9060 lignes et le temoin en dernier`() = runTest {
        val directory = temp()
        val transport = FakeTransport(fullArchive, status = { 200 })
        val archive = installer(directory, transport, fullArchive.size.toLong())
        val states = mutableListOf<ArchiveProgress>()

        archive.install { states += it }

        assertTrue(archive.isInstalled())
        assertEquals(ArchivePhase.READY, states.last().phase)
        assertEquals(1f, states.last().progress)
        assertEquals(1, states.count { it.phase == ArchivePhase.READY })
        assertEquals(ArchivePhase.DOWNLOADING, states.first().phase)

        // Les 9 060 fichiers sont là, et eux seuls.
        val pages = directory.listFiles()!!.filter { it.name.endsWith(".png") }
        assertEquals(9060, pages.size)
        assertEquals(604 * ZIP_LINES_PER_PAGE, pages.size)
        assertTrue(pages.all { it.length() > 0 })
        assertEquals(emptyList(), archive.missingLines(1))
        assertEquals(emptyList(), archive.missingLines(604))
        assertTrue(archive.hasLine(7, 12))

        // Les entrées sans rapport n'ont pas été recopiées.
        assertFalse(File(directory, "readme.txt").exists())
        assertFalse(File(directory, "width_1440/7/12.jpg").exists())

        // Le paquet et la marque sont effacés : 102 Mo n'ont pas à rester après extraction.
        assertFalse(zipOf(directory).exists())
        assertFalse(File(directory, QuranArchive.COMPLETE_FILE).exists())

        // Le témoin dit ce qu'il doit dire, et porte l'heure qu'on lui a donnée.
        val witness = witnessOf(directory).readText()
        assertTrue(QuranArchive.isReady(witness))
        assertTrue(witness.contains("2026-10-04T00:00:00Z"), witness)

        // Une seconde installation ne refait rien.
        archive.install()
        assertEquals(1, transport.asked.size)

        // Une ligne effacée est signalée, et elle seule.
        File(directory, zipLineFileName(7, 12)).delete()
        assertEquals(listOf(12), archive.missingLines(7))
        assertFalse(archive.hasLine(7, 12))
    }

    @Test
    fun `une entree qui sort du moushaf fait echouer l'installation`() = runTest {
        val directory = temp()
        val payload = archive("width_1440/605/1.png" to png())

        val error = assertFailsWith<IOException> {
            installer(directory, FakeTransport(payload, { 200 }), payload.size.toLong()).install()
        }

        assertEquals(QuranArchive.INVALID_ENTRY, error.message)
        assertFalse(witnessOf(directory).exists())
    }

    @Test
    fun `une image de mauvaise dimension fait echouer l'installation`() = runTest {
        val directory = temp()
        val payload = archive("width_1440/7/12.png" to png(height = QuranArchive.IMAGE_HEIGHT - 1))

        val error = assertFailsWith<IOException> {
            installer(directory, FakeTransport(payload, { 200 }), payload.size.toLong()).install()
        }

        assertEquals(QuranArchive.INVALID_IMAGE, error.message)
        assertFalse(witnessOf(directory).exists())
    }

    @Test
    fun `une image trop volumineuse est refusee pendant la lecture`() = runTest {
        val directory = temp()
        val payload = archive("width_1440/7/12.png" to png(extra = QuranArchive.MAX_IMAGE_BYTES))

        val error = assertFailsWith<IOException> {
            installer(directory, FakeTransport(payload, { 200 }), payload.size.toLong()).install()
        }

        assertEquals(QuranArchive.TOO_LARGE, error.message)
    }

    @Test
    fun `une archive incomplete est refusee`() = runTest {
        val directory = temp()
        val payload = archive("width_1440/1/1.png" to png())

        val error = assertFailsWith<IOException> {
            installer(directory, FakeTransport(payload, { 200 }), payload.size.toLong()).install()
        }

        assertEquals(QuranArchive.MISSING_FILES, error.message)
        assertFalse(witnessOf(directory).exists())
    }

    // ---------------------------------------------------------------- divers

    @Test
    fun `une page hors bornes est refusee`() = runTest {
        val directory = temp()
        val archive = installer(directory, FakeTransport(ByteArray(0)), 0L)

        assertFailsWith<IllegalArgumentException> { archive.lineFile(0, 1) }
        assertFailsWith<IllegalArgumentException> { archive.lineFile(605, 1) }
        assertFailsWith<IllegalArgumentException> { archive.lineFile(1, 0) }
        assertFailsWith<IllegalArgumentException> { archive.lineFile(1, ZIP_LINES_PER_PAGE + 1) }
    }

    @Test
    fun `un partiel en place est annonce comme en pause avec son avancement`() = runTest {
        val directory = temp()
        zipOf(directory).writeBytes(ByteArray(4000))
        val archive = installer(directory, FakeTransport(ByteArray(0)), 10_000L)

        val state = archive.progress()

        assertEquals(ArchivePhase.PAUSED, state.phase)
        assertEquals(0.4f, state.progress, 0.001f)
    }

    @Test
    fun `une installation absente est annoncee comme telle`() = runTest {
        val directory = temp()

        assertEquals(
            ArchiveProgress(ArchivePhase.IDLE, 0f),
            installer(directory, FakeTransport(ByteArray(0)), 10_000L).progress(),
        )
    }

    @Test
    fun `deux installations concurrentes ne telechargent qu'une fois`() = runTest {
        val directory = temp()
        val opens = AtomicInteger(0)
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val transport = ArchiveTransport {
            opens.incrementAndGet()
            entered.complete(Unit)
            gate.await()
            ArchiveStream(200, ByteArrayInputStream(fullArchive))
        }
        val archive = installer(directory, transport, fullArchive.size.toLong())

        val first = launch { archive.install() }
        entered.await()
        val second = launch { archive.install() }
        gate.complete(Unit)
        first.join()
        second.join()

        // Le second démarre pendant que le premier tient le verrou, et le total est mesuré ici.
        // Une assertion prise « à cet instant » serait plus faible qu'elle n'en a l'air : le
        // second peut n'être encore que programmé sur un fil d'entrées-sorties qui n'a pas
        // démarré, et elle passerait sans rien prouver. Sans verrou, le second rouvre le paquet
        // et le compte vaut deux — c'est ce que ce test refuse.
        assertEquals(
            1,
            opens.get(),
            "deux écritures dans le même fichier donneraient une archive fausse",
        )
        assertTrue(archive.isInstalled())
    }
}
