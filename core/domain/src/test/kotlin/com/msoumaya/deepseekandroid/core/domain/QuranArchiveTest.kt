package com.msoumaya.deepseekandroid.core.domain

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Règles d'installation de la source « Coran 1441 ».
 *
 * Ce qui est éprouvé ici n'est pas le téléchargement — il demande un réseau — mais ce qui décide
 * si une installation est **utilisable**. Une page blanche ne se distingue pas d'une page chargée
 * tant qu'on n'a pas regardé les dimensions : c'est pour cela que la validité d'image est une
 * règle du domaine, et non un détail de la couche réseau.
 */
class QuranArchiveTest {

    private fun pngHeader(width: Int, height: Int, signature: ByteArray = PNG_SIGNATURE): ByteArray {
        val bytes = ByteArray(24)
        signature.copyInto(bytes)
        putUint32(bytes, 16, width.toLong())
        putUint32(bytes, 20, height.toLong())
        return bytes
    }

    private fun putUint32(bytes: ByteArray, at: Int, value: Long) {
        bytes[at] = ((value shr 24) and 0xFF).toByte()
        bytes[at + 1] = ((value shr 16) and 0xFF).toByte()
        bytes[at + 2] = ((value shr 8) and 0xFF).toByte()
        bytes[at + 3] = (value and 0xFF).toByte()
    }

    @Test
    fun `les constantes du paquet sont celles du client d'origine`() {
        assertEquals("https://files.quran.app/hafs/madani_1441/zips/images_1440.zip", ZIP_ARCHIVE_URL)
        assertEquals(102_608_011L, ZIP_ARCHIVE_BYTES)
        assertEquals(9_060, ZIP_TOTAL_FILES)
        assertEquals(15, ZIP_LINES_PER_PAGE)
        assertEquals(1, ZIP_READY_VERSION)
        assertEquals(604 * 15, ZIP_TOTAL_FILES)
    }

    @Test
    fun `le nom d'un fichier est la page sur trois chiffres et la ligne sur deux`() {
        assertEquals("001-01.png", zipLineFileName(1, 1))
        assertEquals("007-12.png", zipLineFileName(7, 12))
        assertEquals("604-15.png", zipLineFileName(604, 15))
    }

    @Test
    fun `les neuf mille soixante noms sont distincts`() {
        val names = HashSet<String>(9_060 * 2)
        for (page in 1..604) {
            for (line in 1..ZIP_LINES_PER_PAGE) names.add(zipLineFileName(page, line))
        }
        assertEquals(ZIP_TOTAL_FILES, names.size)
    }

    @Test
    fun `une entree utile est extraite avec son nom definitif`() {
        val decision = QuranArchive.entryDecision("width_1440/7/12.png")
        assertIs<QuranArchive.EntryDecision.Extract>(decision)
        assertEquals(7, decision.page)
        assertEquals(12, decision.line)
        assertEquals("007-12.png", decision.name)
    }

    @Test
    fun `des zeros de tete dans l'archive ne changent pas la page`() {
        val decision = QuranArchive.entryDecision("width_1440/007/01.png")
        assertIs<QuranArchive.EntryDecision.Extract>(decision)
        assertEquals(7, decision.page)
        assertEquals(1, decision.line)
    }

    @Test
    fun `une entree sans rapport avec le moushaf est ignoree`() {
        val strangers = listOf(
            "readme.txt",
            "width_1440/",
            "width_1440/7/",
            "width_1440/7/12.jpg",
            "width_1440/7/12.png.bak",
            "width_1441/7/12.png",
            "other/7/12.png",
            "width_1440/7/12.png/",
            "width_1440//12.png",
        )
        for (path in strangers) {
            assertEquals(
                QuranArchive.EntryDecision.Skip,
                QuranArchive.entryDecision(path),
                "entree : « $path »",
            )
        }
    }

    @Test
    fun `une entree qui ressemble a une page mais sort du moushaf est refusee`() {
        // Ces entrees signalent une archive qui n'est pas celle qu'on croit : l'installer
        // donnerait des pages decalees, ce qui est pire qu'un echec.
        val outOfRange = listOf(
            "width_1440/0/1.png",
            "width_1440/605/1.png",
            "width_1440/999/1.png",
            "width_1440/1/0.png",
            "width_1440/1/16.png",
            "width_1440/1/99.png",
        )
        for (path in outOfRange) {
            assertEquals(
                QuranArchive.EntryDecision.Reject,
                QuranArchive.entryDecision(path),
                "entree : « $path »",
            )
        }
    }

    @Test
    fun `la taille du paquet est exigee exactement`() {
        assertTrue(QuranArchive.isCompleteSize(102_608_011L))
        assertFalse(QuranArchive.isCompleteSize(102_608_010L), "une taille inferieure est incomplete")
        assertFalse(QuranArchive.isCompleteSize(102_608_012L), "une taille superieure n'est pas ce paquet")
        assertFalse(QuranArchive.isCompleteSize(0L))
    }

    @Test
    fun `seuls 200 et 206 autorisent a ecrire`() {
        assertTrue(QuranArchive.isAcceptedStatus(200))
        assertTrue(QuranArchive.isAcceptedStatus(206))
        for (code in listOf(301, 302, 403, 404, 416, 500)) {
            assertFalse(QuranArchive.isAcceptedStatus(code), "code : $code")
        }
    }

    @Test
    fun `une image de ligne valide est reconnue`() {
        assertTrue(QuranArchive.imageIsValid(pngHeader(1440, 232)))
    }

    @Test
    fun `une image de dimensions fausses est refusee`() {
        // C'est le cas qui compte : une page blanche ou une autre source passerait un controle
        // d'existence de fichier, et ne se verrait qu'a l'ecran.
        assertFalse(QuranArchive.imageIsValid(pngHeader(1440, 231)))
        assertFalse(QuranArchive.imageIsValid(pngHeader(1439, 232)))
        assertFalse(QuranArchive.imageIsValid(pngHeader(1440, 2320)))
        assertFalse(QuranArchive.imageIsValid(pngHeader(1, 1)))
    }

    @Test
    fun `un fichier trop court ou d'une autre nature est refuse`() {
        assertFalse(QuranArchive.imageIsValid(ByteArray(0)))
        assertFalse(QuranArchive.imageIsValid(ByteArray(23)))
        assertFalse(QuranArchive.imageIsValid(pngHeader(1440, 232, signature = byteArrayOf(0, 0))))
    }

    @Test
    fun `le temoin atteste d'une installation complete`() {
        val json = QuranArchive.readyJson("2026-10-04T20:00:00Z")
        assertTrue(QuranArchive.isReady(json))
        assertTrue(json.contains(""""files":9060"""), "le temoin porte le nombre de fichiers : $json")
        assertTrue(json.contains("2026-10-04T20:00:00Z"), "le temoin porte la date : $json")
        // En JavaScript, `1.0 === 1` : un temoin ecrit avec des flottants reste valide.
        assertTrue(QuranArchive.isReady("""{"version":1.0,"files":9060.0}"""))
    }

    @Test
    fun `un temoin ou les nombres sont des chaines est refuse`() {
        // `intOrNull` lirait la chaine « 1 ». Le client d'origine, lui, compare des nombres
        // stricts : l'accepter ici ferait retélécharger 102 Mo a l'un des deux clients.
        assertFalse(QuranArchive.isReady("""{"version":"1","files":"9060"}"""))
        assertFalse(QuranArchive.isReady("""{"version":1,"files":"9060"}"""))
    }

    @Test
    fun `un temoin d'une autre version ou d'un autre compte est refuse`() {
        assertFalse(QuranArchive.isReady("""{"version":2,"files":9060}"""))
        assertFalse(QuranArchive.isReady("""{"version":1,"files":9059}"""))
        assertFalse(QuranArchive.isReady("""{"version":1,"files":9061}"""))
        assertFalse(QuranArchive.isReady("""{"version":1}"""))
        assertFalse(QuranArchive.isReady("""{"files":9060}"""))
    }

    @Test
    fun `un temoin illisible vaut non installe`() {
        // Mieux vaut retélécharger que lire 9 060 images dont on ne sait rien.
        assertFalse(QuranArchive.isReady(null))
        assertFalse(QuranArchive.isReady(""))
        assertFalse(QuranArchive.isReady("pas du json"))
        assertFalse(QuranArchive.isReady("{}"))
        assertFalse(QuranArchive.isReady("[]"))
        assertFalse(QuranArchive.isReady("""{"version":"1","files":"9060"}"""))
    }

    @Test
    fun `une coupure reseau donne le message du client d'origine`() {
        for (error in listOf(
            UnknownHostException("files.quran.app"),
            SocketTimeoutException("timeout"),
            ConnectException("refused"),
            IOException("Network request failed"),
            IOException("The request timed out"),
            IOException("ERR_FILESYSTEM_CANNOT_DOWNLOAD"),
        )) {
            assertEquals(QuranArchive.NETWORK, QuranArchive.errorMessage(error), error.toString())
        }
    }

    @Test
    fun `un disque plein donne le message du client d'origine`() {
        for (error in listOf(
            IOException("ENOSPC: no space left on device"),
            IOException("There is not enough disk space"),
            IOException("Storage full"),
        )) {
            assertEquals(QuranArchive.DISK_FULL, QuranArchive.errorMessage(error), error.toString())
        }
    }

    @Test
    fun `un echec d'une autre nature garde son propre message`() {
        assertEquals("Quelque chose d'inattendu", QuranArchive.errorMessage(RuntimeException("Quelque chose d'inattendu")))
    }
}

/** Signature PNG sur huit octets, réduite aux deux premiers, comme le client d'origine. */
private val PNG_SIGNATURE = byteArrayOf(
    137.toByte(), 80, 78, 71, 13, 10, 26, 10,
)
