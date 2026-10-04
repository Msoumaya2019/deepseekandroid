package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.local.ArchiveStream
import com.msoumaya.deepseekandroid.core.data.local.ArchiveTransport
import com.msoumaya.deepseekandroid.core.data.local.QuranArchiveInstaller
import com.msoumaya.deepseekandroid.core.data.local.TestArchives
import com.msoumaya.deepseekandroid.core.domain.ArchivePhase
import com.msoumaya.deepseekandroid.core.domain.PageReadiness
import com.msoumaya.deepseekandroid.core.domain.QuranArchive
import com.msoumaya.deepseekandroid.core.domain.QuranSourceReady
import com.msoumaya.deepseekandroid.core.domain.ZIP_LINES_PER_PAGE
import com.msoumaya.deepseekandroid.core.domain.zipLineFileName
import com.msoumaya.deepseekandroid.core.model.MushafSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.UnknownHostException
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Éprouve le magasin du paquet « Coran 1441 » : ce que l'écran peut lui demander, et ce qu'il
 * répond.
 *
 * Trois règles y sont tenues, et ce sont celles qui se voient à l'usage :
 *
 *  1. **une pause laisse l'installation en pause**, pas en travail et pas en erreur. Le partiel
 *     reste en place, et une reprise repart de sa longueur — sans quoi une connexion capricieuse
 *     rendrait le paquet ininstallable ;
 *  2. **une panne publie un message.** Une installation qui échoue en silence laisserait un
 *     bouton qui ne fait rien, ce qui est pire qu'une erreur ;
 *  3. **une page hors bornes est refusée sans exception.** Le magasin lit les lignes du disque
 *     pour répondre, et une page 605 ferait lever la lecture au lieu de rendre le refus prévu.
 */
class QuranArchiveStoreTest {

    private val created = mutableListOf<File>()
    private val scopes = mutableListOf<CoroutineScope>()
    private val releases = mutableListOf<CountDownLatch>()

    @AfterTest
    fun cleanup() {
        // Les débloquer d'abord : un flux de test encore arrêté tiendrait un fil occupé, et
        // l'annulation d'un répartiteur ne l'atteint pas — il est bloqué dans une lecture.
        releases.forEach { it.countDown() }
        scopes.forEach { it.cancel() }
        created.forEach { it.deleteRecursively() }
    }

    // ---------------------------------------------------------------- outillage

    private fun temp(): File =
        Files.createTempDirectory("quran-store-test").toFile().also { created += it }

    /**
     * Le répartiteur sur lequel le magasin travaille.
     *
     * `backgroundScope` du test aurait été plus court à écrire, mais son répartiteur est
     * **mono-thread** : l'installation écrit un fichier en bloc, et un test qui attend l'ouverture
     * du paquet bloquerait le seul fil capable de la faire démarrer. Il échouerait alors sur
     * « le téléchargement n'a pas démarré » — un verdict sur le harnais, pas sur le code.
     * La production passe `Dispatchers.Default` : c'est ce qu'on reproduit ici.
     */
    private fun scopeDeTravail(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it }

    private fun installer(
        directory: File,
        transport: ArchiveTransport,
        expectedBytes: Long,
    ) = QuranArchiveInstaller(
        directory = directory,
        transport = transport,
        expectedBytes = expectedBytes,
        clock = { "2026-10-04T00:00:00Z" },
    )

    private fun jamaisAppele(): ArchiveTransport = ArchiveTransport { error("le transport ne doit pas être ouvert") }

    /** Marque l'installation comme complète sans télécharger 102 Mo. */
    private fun markInstalled(directory: File) {
        directory.mkdirs()
        File(directory, QuranArchive.READY_FILE)
            .writeText(QuranArchive.readyJson("2026-10-04T00:00:00Z"))
    }

    private fun writeLines(directory: File, page: Int, lines: IntRange) {
        lines.forEach { File(directory, zipLineFileName(page, it)).writeBytes(TestArchives.png()) }
    }

    /**
     * Un flux qui s'arrête une fois, pour que le test puisse annuler **pendant** la boucle
     * d'écriture.
     *
     * Sans ce point d'arrêt, l'écriture pourrait finir avant que l'annulation n'arrive, et le
     * test passerait sans avoir rien éprouvé — c'est-à-dire pour la mauvaise raison. Le loquet de
     * sortie est retenu : une panne du test ne doit pas laisser un fil bloqué pour toujours.
     */
    private fun fluxBloque(engage: CountDownLatch, peutContinuer: CountDownLatch, total: Int): InputStream {
        releases += peutContinuer
        return object : InputStream() {
            private var at = 0

            override fun read(): Int {
                val un = ByteArray(1)
                val n = read(un, 0, 1)
                return if (n < 0) -1 else un[0].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (at == 0) {
                    engage.countDown()
                    peutContinuer.await()
                }
                if (at >= total) return -1
                val n = minOf(len, 4096)
                at += n
                return n
            }
        }
    }

    /**
     * Attend qu'une condition devienne vraie.
     *
     * Le magasin travaille sur de vrais fils d'entrées-sorties et une horloge réelle : il n'y a
     * donc rien à « avancer », seulement du temps à laisser s'écouler. La borne empêche qu'un
     * défaut fasse tourner le test indéfiniment — et le message dit alors ce qui n'est jamais
     * arrivé.
     */
    private fun attendre(cause: String, condition: () -> Boolean) {
        val limite = System.nanoTime() + 20_000_000_000L
        while (System.nanoTime() < limite) {
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue(condition(), "condition jamais atteinte : $cause")
    }

    // ------------------------------------------------------- état lu sur le disque

    @Test
    fun `une installation en place est reconnue des la construction`() = runTest {
        val directory = temp()
        markInstalled(directory)
        writeLines(directory, 12, 1..ZIP_LINES_PER_PAGE)
        val store = QuranArchiveStore(installer(directory, jamaisAppele(), 0L), scopeDeTravail())

        assertTrue(store.downloaded.value)
        assertEquals(ArchivePhase.READY, store.progress.value.phase)
        assertEquals(PageReadiness.Ready, store.readiness(MushafSource.CORAN_1441, 12))
        // Une page dont une seule ligne manque est refusée, et avec le message d'origine.
        assertEquals(
            PageReadiness.Refused(QuranSourceReady.MISSING_PAGE_IMAGES),
            store.readiness(MushafSource.CORAN_1441, 13),
        )
    }

    @Test
    fun `une source non installee demande le telechargement`() = runTest {
        val directory = temp()
        val store = QuranArchiveStore(installer(directory, jamaisAppele(), 0L), scopeDeTravail())

        assertFalse(store.downloaded.value)
        assertEquals(PageReadiness.DownloadNeeded, store.readiness(MushafSource.CORAN_1441, 12))
        // Une source embarquée ne demande rien, même sans paquet.
        assertEquals(PageReadiness.Ready, store.readiness(MushafSource.MEDINA, 12))
    }

    @Test
    fun `une page hors bornes est refusee sans lever`() = runTest {
        val directory = temp()
        markInstalled(directory)
        val store = QuranArchiveStore(installer(directory, jamaisAppele(), 0L), scopeDeTravail())

        // Le magasin lit les lignes du disque pour répondre : sans le garde-fou de bornes,
        // cette lecture lèverait au lieu de rendre le refus prévu.
        for (page in listOf(0, -3, 605, 9999)) {
            assertEquals(
                PageReadiness.Refused(QuranSourceReady.INVALID_PAGE),
                store.readiness(MushafSource.CORAN_1441, page),
                "page $page",
            )
        }
    }

    // ---------------------------------------------------------------- la pause

    @Test
    fun `une pause laisse l'installation en pause et le partiel en place`() = runTest {
        val directory = temp()
        val engage = CountDownLatch(1)
        val peutContinuer = CountDownLatch(1)
        val transport = ArchiveTransport {
            ArchiveStream(200, fluxBloque(engage, peutContinuer, total = 100_000))
        }
        val store = QuranArchiveStore(installer(directory, transport, 400_000L), scopeDeTravail())

        store.start()
        assertTrue(engage.await(10, TimeUnit.SECONDS), "le téléchargement n'a pas démarré")
        store.pause()
        peutContinuer.countDown()

        attendre("l'installation doit finir en pause") {
            store.progress.value.phase == ArchivePhase.PAUSED
        }
        val partiel = File(directory, QuranArchive.ZIP_FILE)
        assertTrue(partiel.isFile, "le partiel doit survivre à une pause")
        assertTrue(partiel.length() > 0, "le partiel ne doit pas être vide")
        assertFalse(store.downloaded.value, "une pause n'installe rien")
    }

    @Test
    fun `apres une pause, une reprise repart de la longueur du partiel`() = runTest {
        val directory = temp()
        val engage = CountDownLatch(1)
        val peutContinuer = CountDownLatch(1)
        val demandes = CopyOnWriteArrayList<Long>()
        val transport = ArchiveTransport { from ->
            demandes += from
            if (demandes.size == 1) {
                ArchiveStream(200, fluxBloque(engage, peutContinuer, total = 100_000))
            } else {
                ArchiveStream(200, ByteArrayInputStream(ByteArray(0)))
            }
        }
        val store = QuranArchiveStore(installer(directory, transport, 400_000L), scopeDeTravail())

        store.start()
        assertTrue(engage.await(10, TimeUnit.SECONDS), "le téléchargement n'a pas démarré")
        store.pause()
        peutContinuer.countDown()
        attendre("la pause doit être publiée") { store.progress.value.phase == ArchivePhase.PAUSED }

        val partiel = File(directory, QuranArchive.ZIP_FILE).length()
        assertTrue(partiel > 0, "le partiel doit être en place")
        store.start()
        attendre("la reprise doit rouvrir le paquet") { demandes.size == 2 }
        assertEquals(partiel, demandes[1], "la reprise doit partir de la longueur du partiel")
    }

    // ------------------------------------------------------------ succès et échec

    @Test
    fun `une installation complete rend la source disponible`() = runTest {
        val directory = temp()
        val transport = ArchiveTransport { ArchiveStream(200, ByteArrayInputStream(TestArchives.full)) }
        val store = QuranArchiveStore(
            installer(directory, transport, TestArchives.full.size.toLong()),
            scopeDeTravail(),
        )

        assertFalse(store.downloaded.value)
        assertEquals(PageReadiness.DownloadNeeded, store.readiness(MushafSource.CORAN_1441, 12))

        store.start()
        attendre("l'installation doit aboutir") { store.downloaded.value }

        assertEquals(ArchivePhase.READY, store.progress.value.phase)
        assertEquals(PageReadiness.Ready, store.readiness(MushafSource.CORAN_1441, 12))
        assertEquals(PageReadiness.Ready, store.readiness(MushafSource.CORAN_1441, 604))
    }

    @Test
    fun `un echec publie le message et ne fait pas tomber l'application`() = runTest {
        val directory = temp()
        val transport = ArchiveTransport { throw UnknownHostException("dns") }
        val store = QuranArchiveStore(installer(directory, transport, 4000L), scopeDeTravail())

        store.start()
        attendre("l'erreur doit être publiée") { store.progress.value.phase == ArchivePhase.ERROR }

        assertEquals(QuranArchive.NETWORK, store.progress.value.message)
        assertFalse(store.downloaded.value)
    }

    @Test
    fun `trois appuis ne font pas trois telechargements`() = runTest {
        val directory = temp()
        val ouvertures = CopyOnWriteArrayList<Long>()
        // Une charge qui n'est pas une archive : l'installation échouera à l'extraction, ce qui
        // est sans importance ici. Ce qui est mesuré est le nombre d'ouvertures du paquet.
        val charge = ByteArray(4000) { 7 }
        val transport = ArchiveTransport { from ->
            ouvertures += from
            ArchiveStream(200, ByteArrayInputStream(charge))
        }
        val store = QuranArchiveStore(
            installer(directory, transport, charge.size.toLong()),
            scopeDeTravail(),
        )

        store.start()
        store.start()
        store.start()
        attendre("l'installation doit se terminer") { store.progress.value.phase == ArchivePhase.ERROR }

        assertEquals(1, ouvertures.size, "trois appuis ne font pas trois téléchargements")
    }
}
