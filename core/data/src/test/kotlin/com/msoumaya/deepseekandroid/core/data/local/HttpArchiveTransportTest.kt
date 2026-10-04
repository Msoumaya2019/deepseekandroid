package com.msoumaya.deepseekandroid.core.data.local

import kotlinx.coroutines.test.runTest
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Éprouve le transport du paquet « Coran 1441 » contre un **vrai** serveur, sur la boucle locale.
 *
 * Une doublure qui rendrait directement un `ArchiveStream` ne dirait rien de ce qui peut se
 * tromper ici : la **forme de la requête** — l'en-tête `Range` — et la lecture du code de
 * réponse. Or c'est exactement de là que vient le défaut coûteux : un `200` traité comme un `206`
 * donne une archive de taille correcte et de contenu faux.
 */
class HttpArchiveTransportTest {

    private val contenu = ByteArray(4096) { (it % 251).toByte() }

    @Test
    fun `sans decalage, la requete ne porte aucun entete de reprise`() = runTest {
        MiniServeur(contenu).use { serveur ->
            val flux = HttpArchiveTransport(serveur.url).open(0)

            assertEquals(200, flux.status)
            assertContentEquals(contenu, flux.body.readBytes())
            assertNull(serveur.derniereRequete()["range"], "une première requête reste ordinaire")
        }
    }

    @Test
    fun `un decalage demande la suite et le serveur repond 206`() = runTest {
        MiniServeur(contenu).use { serveur ->
            val flux = HttpArchiveTransport(serveur.url).open(1000)

            assertEquals(206, flux.status)
            assertContentEquals(contenu.copyOfRange(1000, contenu.size), flux.body.readBytes())
            assertEquals("bytes=1000-", serveur.derniereRequete()["range"])
        }
    }

    @Test
    fun `un serveur qui ignore le decalage est rapporte tel quel`() = runTest {
        MiniServeur(contenu, honoreRange = false).use { serveur ->
            val flux = HttpArchiveTransport(serveur.url).open(1000)

            // C'est cette distinction que l'installeur lit pour jeter le partiel au lieu de s'y
            // ajouter. Le transport ne doit donc pas la lisser.
            assertEquals(200, flux.status)
            assertContentEquals(contenu, flux.body.readBytes())
            assertEquals("bytes=1000-", serveur.derniereRequete()["range"])
        }
    }

    @Test
    fun `un refus est rapporte sans lever et sans corps`() = runTest {
        MiniServeur(contenu, code = 404).use { serveur ->
            val flux = HttpArchiveTransport(serveur.url).open(0)

            assertEquals(404, flux.status)
            assertEquals(0, flux.body.readBytes().size)
        }
    }

    @Test
    fun `l'entete de reprise n'existe qu'a partir du premier octet`() {
        assertNull(rangeHeader(0))
        assertEquals("bytes=1-", rangeHeader(1))
        assertEquals("bytes=102608011-", rangeHeader(102_608_011))
    }
}

/**
 * Un serveur HTTP minuscule, sur la boucle locale : il lit la requête, honore ou ignore l'en-tête
 * `Range`, et répond en conséquence. Il n'écoute que sur l'adresse de bouclage et sur un port
 * attribué par le système, donc il ne dépend ni du réseau ni d'un port libre choisi à la main.
 */
private class MiniServeur(
    private val body: ByteArray,
    private val honoreRange: Boolean = true,
    private val code: Int = 200,
) : Closeable {

    private val socket = ServerSocket(0, 4, InetAddress.getLoopbackAddress())
    private val recues = CopyOnWriteArrayList<Map<String, String>>()

    val url: String get() = "http://127.0.0.1:${socket.localPort}/archive.zip"

    /** Les en-têtes de la dernière requête reçue. */
    fun derniereRequete(): Map<String, String> = recues.last()

    private val fil = thread(isDaemon = true, name = "mini-serveur") { servir() }

    private fun servir() {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: IOException) {
                return
            }
            client.use { connexion ->
                val lecteur = connexion.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                lecteur.readLine() ?: return@use
                val entetes = mutableMapOf<String, String>()
                while (true) {
                    val ligne = lecteur.readLine() ?: break
                    if (ligne.isEmpty()) break
                    val coupure = ligne.indexOf(':')
                    if (coupure > 0) {
                        entetes[ligne.substring(0, coupure).trim().lowercase()] =
                            ligne.substring(coupure + 1).trim()
                    }
                }
                recues += entetes
                repondre(connexion.getOutputStream(), entetes)
            }
        }
    }

    private fun repondre(sortie: OutputStream, entetes: Map<String, String>) {
        if (code != 200) {
            ecrire(sortie, "HTTP/1.1 $code \r\n", ByteArray(0))
            return
        }
        val debut = entetes["range"]
            ?.removePrefix("bytes=")
            ?.substringBefore('-')
            ?.toIntOrNull()
        val partiel = honoreRange && debut != null && debut > 0 && debut < body.size
        val entete = if (partiel) {
            "HTTP/1.1 206 Partial Content\r\n" +
                "Content-Range: bytes $debut-${body.size - 1}/${body.size}\r\n"
        } else {
            "HTTP/1.1 200 OK\r\n"
        }
        val charge = if (partiel) body.copyOfRange(debut, body.size) else body
        ecrire(sortie, entete, charge)
    }

    private fun ecrire(sortie: OutputStream, entete: String, charge: ByteArray) {
        val complete = entete + "Content-Length: ${charge.size}\r\nConnection: close\r\n\r\n"
        sortie.write(complete.toByteArray(Charsets.ISO_8859_1))
        sortie.write(charge)
        sortie.flush()
    }

    override fun close() {
        socket.close()
        fil.join(2_000)
    }
}
