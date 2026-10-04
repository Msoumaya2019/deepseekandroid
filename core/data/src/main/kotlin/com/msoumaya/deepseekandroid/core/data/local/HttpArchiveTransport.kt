package com.msoumaya.deepseekandroid.core.data.local

import com.msoumaya.deepseekandroid.core.domain.ZIP_ARCHIVE_URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ouvre le paquet « Coran 1441 » par une requête HTTP reprenable.
 *
 * `HttpURLConnection` plutôt que le client Ktor du projet : Ktor n'est là que par transitivité,
 * pour le SDK Supabase, et aucun code ne l'utilise directement. Une seule requête `GET`, sans
 * en-tête d'authentification, ne justifie pas de déclarer une seconde pile réseau ni de faire
 * entrer un moteur supplémentaire dans l'application.
 *
 * **Le décalage est demandé par l'en-tête `Range`, et rien d'autre ne le porte.** Le serveur
 * répond `206` s'il l'honore et `200` s'il renvoie tout le fichier ; c'est l'installeur qui
 * décide quoi faire de cette différence, et il jette le partiel sur un `200`. Un transport qui
 * « arrangerait » la réponse — en découpant lui-même le flux, par exemple — priverait
 * l'installeur de l'information qui distingue une reprise d'un fichier entier.
 *
 * @param connectTimeoutMs le délai d'établissement de la connexion.
 * @param readTimeoutMs le délai **entre deux octets**, et non pour le téléchargement entier :
 *   102 Mo sur une connexion lente prennent légitimement plusieurs minutes, et un délai global
 *   rejetterait une connexion qui progresse.
 */
class HttpArchiveTransport(
    private val url: String = ZIP_ARCHIVE_URL,
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
) : ArchiveTransport {

    override suspend fun open(fromByte: Long): ArchiveStream = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            rangeHeader(fromByte)?.let { setRequestProperty("Range", it) }
        }

        val status = try {
            connection.responseCode
        } catch (error: IOException) {
            connection.disconnect()
            throw error
        }

        // Un refus est entièrement traité ici : le corps d'erreur n'a rien à apprendre à
        // l'installeur, et le laisser ouvert garderait une connexion en suspens.
        if (status !in 200..299) {
            connection.disconnect()
            return@withContext ArchiveStream(status, ByteArrayInputStream(ByteArray(0)))
        }

        // Le corps est rendu tel quel : c'est lui qui alimente la boucle d'écriture, et sa
        // fermeture rend la connexion au pool.
        ArchiveStream(status, connection.inputStream)
    }

    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 20_000
        const val DEFAULT_READ_TIMEOUT_MS = 30_000
    }
}

/**
 * L'en-tête de reprise, ou `null` quand il n'y a rien à reprendre.
 *
 * Une demande `bytes=0-` serait légale mais inutile, et certains serveurs la traitent comme une
 * plage explicite plutôt que comme le fichier entier. Ne pas l'envoyer du tout laisse la première
 * requête ordinaire.
 */
internal fun rangeHeader(from: Long): String? = if (from > 0) "bytes=$from-" else null
