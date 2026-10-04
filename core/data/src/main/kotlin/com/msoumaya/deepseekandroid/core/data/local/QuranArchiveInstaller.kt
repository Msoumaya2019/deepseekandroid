package com.msoumaya.deepseekandroid.core.data.local

import com.msoumaya.deepseekandroid.core.domain.ArchivePhase
import com.msoumaya.deepseekandroid.core.domain.ArchiveProgress
import com.msoumaya.deepseekandroid.core.domain.QuranArchive
import com.msoumaya.deepseekandroid.core.domain.QuranSourceTransition
import com.msoumaya.deepseekandroid.core.domain.ZIP_ARCHIVE_BYTES
import com.msoumaya.deepseekandroid.core.domain.ZIP_LINES_PER_PAGE
import com.msoumaya.deepseekandroid.core.domain.ZIP_TOTAL_FILES
import com.msoumaya.deepseekandroid.core.domain.zipLineFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/**
 * Une lecture du paquet, ouverte à un décalage donné.
 *
 * [status] vaut 206 quand le serveur a honoré la reprise, 200 quand il a renvoyé le fichier
 * entier. La distinction est essentielle : écrire la suite d'un fichier à partir d'un flux qui
 * repart de zéro donnerait une archive de **taille correcte et de contenu faux** — le pire cas,
 * puisqu'elle passerait tous les contrôles et ne se verrait qu'à l'écran, sur une page décalée.
 *
 * La taille annoncée par le serveur n'est **pas** exposée, et c'est délibéré. En réponse 206, un
 * `Content-Length` vaut la longueur du **fragment** et non celle de l'archive : la comparer à la
 * taille attendue refuserait chaque reprise, c'est-à-dire exactement le cas qu'on veut rendre
 * possible. Le juge de la complétude est la longueur du fichier écrit, et elle seule.
 */
class ArchiveStream(
    val status: Int,
    val body: InputStream,
) : Closeable {
    override fun close() = body.close()
}

/**
 * Ouvre le paquet distant. Remplacé par une doublure dans les tests : c'est le seul endroit qui
 * demande un réseau, et l'installation ne doit pas être éprouvée « quand il y a du réseau ».
 */
fun interface ArchiveTransport {
    suspend fun open(fromByte: Long): ArchiveStream
}

/**
 * Installe la source « Coran 1441 » : 102 608 011 octets, 9 060 images.
 *
 * Porté depuis `src/services/quranDownload.ts`. Trois propriétés sont tenues, et chacune répond
 * à un défaut précis :
 *
 *  1. **Reprise.** Le téléchargement repart du partiel existant. Sans cela, 102 Mo sur une
 *     connexion mobile ne s'installent jamais : chaque coupure ramènerait à zéro.
 *  2. **Témoin écrit en dernier.** `ready-v1.json` n'est écrit qu'après les 9 060 fichiers. Sa
 *     présence est donc une **preuve**, et non une intention — c'est ce qui distingue une
 *     installation complète d'une installation interrompue.
 *  3. **Refus d'une archive douteuse.** Une entrée qui ressemble à une page et sort du moushaf
 *     fait échouer l'installation. L'accepter donnerait des pages décalées, qu'aucun contrôle
 *     d'existence de fichier ne verrait.
 *
 * Différence assumée avec le client d'origine : il enregistre un `resume.json` parce que son
 * gestionnaire de fichiers garde les octets repris ailleurs que dans le fichier cible. Ici la
 * **longueur du partiel est la position de reprise** : c'est plus simple et cela ne peut pas
 * désynchroniser la marque et le fichier.
 *
 * @param expectedBytes la taille de l'archive complète. Les tests passent la leur : fabriquer
 *   102 Mo dans une suite de tests prendrait plus de temps que la suite entière et
 *   n'apprendrait rien de plus. Tout — décision de reprise, avancement, contrôle final — se
 *   règle sur **cette** valeur et non sur une constante lue à côté : une règle injectée qui
 *   laisse le reste du code sur l'ancienne constante n'éprouverait pas le chemin de production.
 * @param clock l'horodatage du témoin, injecté pour que les tests n'aient pas d'heure.
 */
class QuranArchiveInstaller(
    private val directory: File,
    private val transport: ArchiveTransport,
    private val expectedBytes: Long = ZIP_ARCHIVE_BYTES,
    private val clock: () -> String = { Instant.now().toString() },
) {

    /**
     * Une seule installation à la fois.
     *
     * Deux appels concurrents — un utilisateur qui appuie deux fois, une reprise automatique qui
     * croise un appui — écriraient tous les deux dans le même fichier et produiraient une archive
     * que rien ne signalerait comme fausse avant l'affichage.
     */
    private val mutex = Mutex()

    private val zip: File get() = File(directory, QuranArchive.ZIP_FILE)
    private val witness: File get() = File(directory, QuranArchive.READY_FILE)
    private val completeMarker: File get() = File(directory, QuranArchive.COMPLETE_FILE)

    /** Une installation complète est-elle déjà en place ? */
    fun isInstalled(): Boolean = QuranArchive.isReady(readWitness())

    /** L'état à afficher avant toute action, sans toucher au réseau. */
    fun progress(): ArchiveProgress = when {
        isInstalled() -> ArchiveProgress(ArchivePhase.READY, 1f)
        // Un partiel, ou une marque sans partiel, veut dire « commencé, pas fini ». Le dire
        // permet à l'écran de proposer de reprendre plutôt que de recommencer.
        zip.isFile || completeMarker.isFile ->
            ArchiveProgress(ArchivePhase.PAUSED, fraction(zip.length()))
        else -> ArchiveProgress(ArchivePhase.IDLE, 0f)
    }

    /** Le fichier d'une ligne de page. */
    fun lineFile(page: Int, line: Int): File {
        require(page in 1..QuranSourceTransition.TOTAL_PAGES) { "Page du Coran invalide : $page" }
        require(line in 1..ZIP_LINES_PER_PAGE) { "Ligne invalide : $line" }
        return File(directory, zipLineFileName(page, line))
    }

    fun hasLine(page: Int, line: Int): Boolean =
        lineFile(page, line).let { it.isFile && it.length() > 0 }

    /** Les lignes absentes d'une page, dans l'ordre. Vide quand la page est complète. */
    fun missingLines(page: Int): List<Int> = (1..ZIP_LINES_PER_PAGE).filterNot { hasLine(page, it) }

    /**
     * Installe la source, en reprenant un téléchargement interrompu.
     *
     * Échoue avec le message du client d'origine et **laisse le partiel en place** : la reprise
     * suivante repartira de là. Un échec qui efface son travail transforme une connexion
     * capricieuse en impossibilité définitive.
     *
     * [onProgress] est appelé **depuis un fil d'entrées-sorties**, y compris pour l'état final.
     * Un appelant qui écrit dans un état d'interface doit donc repasser sur son fil principal ;
     * le faire ici obligerait cette couche à connaître l'interface.
     */
    suspend fun install(onProgress: (ArchiveProgress) -> Unit = {}) = mutex.withLock {
        if (isInstalled()) {
            onProgress(ArchiveProgress(ArchivePhase.READY, 1f))
            return@withLock
        }
        try {
            download(onProgress)
            extract(onProgress)
            // Le témoin en dernier : sa présence atteste que tout ce qui précède est allé au bout.
            witness.writeText(QuranArchive.readyJson(clock()))
            // Le paquet ne sert plus à rien une fois extrait, et il pèse 102 Mo.
            zip.delete()
            completeMarker.delete()
            onProgress(ArchiveProgress(ArchivePhase.READY, 1f))
        } catch (error: Throwable) {
            onProgress(
                ArchiveProgress(
                    phase = ArchivePhase.ERROR,
                    // L'avancement réel plutôt que zéro : sur une coupure au quatre-vingtième
                    // mégaoctet, annoncer 0 ferait croire que la reprise n'a rien gardé.
                    progress = fraction(zip.length()),
                    message = QuranArchive.errorMessage(error),
                ),
            )
            throw error
        }
    }

    private suspend fun download(onProgress: (ArchiveProgress) -> Unit) = withContext(Dispatchers.IO) {
        directory.mkdirs()

        // Le téléchargement est terminé quand la marque **et** la taille concordent. La marque
        // seule ne suffit pas (le fichier peut avoir été tronqué), et la taille seule non plus
        // (un fichier de la bonne taille peut être le mauvais paquet).
        if (completeMarker.isFile && zip.length() == expectedBytes) return@withContext

        // Un fichier qui n'est pas plus court que l'attendu n'est pas un partiel : c'est soit une
        // installation complète dont la marque manque, soit un fichier étranger. Dans les deux cas
        // il faut repartir de zéro — écrire la suite d'un fichier étranger donnerait une archive
        // de taille correcte et de contenu faux.
        if (zip.length() >= expectedBytes) zip.delete()

        val from = zip.length()
        // L'effacement a échoué : reprendre depuis la fin d'un fichier déjà complet ne mènerait
        // qu'à une réponse 416 et à un message d'erreur incompréhensible.
        if (from >= expectedBytes) throw IOException(QuranArchive.INCOMPLETE)

        val stream = transport.open(from)
        try {
            if (!QuranArchive.isAcceptedStatus(stream.status)) {
                throw IOException("Téléchargement refusé (HTTP ${stream.status}).")
            }
            // Un serveur qui répond 200 a renvoyé le fichier entier : écrire la suite d'un
            // partiel à partir de là donnerait une archive fausse de la bonne taille.
            val resuming = from > 0 && stream.status == 206
            if (!resuming) zip.delete()

            FileOutputStream(zip, resuming).use { out ->
                val buffer = ByteArray(BUFFER_BYTES)
                var written = if (resuming) from else 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val read = stream.body.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    written += read
                    onProgress(
                        ArchiveProgress(ArchivePhase.DOWNLOADING, fraction(written)),
                    )
                }
                out.flush()
            }
        } finally {
            stream.close()
        }

        if (zip.length() != expectedBytes) throw IOException(QuranArchive.INCOMPLETE)
        // Contenu volontairement vide : la marque ne vaut que par sa **présence**. Y écrire un
        // champ que personne ne lit donnerait à croire qu'il est vérifié.
        completeMarker.writeText("{}")
    }

    private suspend fun extract(onProgress: (ArchiveProgress) -> Unit) = withContext(Dispatchers.IO) {
        if (!zip.isFile) throw IOException(QuranArchive.INCOMPLETE)
        var installed = 0
        ZipInputStream(BufferedInputStream(FileInputStream(zip))).use { entries ->
            while (true) {
                coroutineContext.ensureActive()
                val entry = entries.nextEntry ?: break
                when (val decision = QuranArchive.entryDecision(entry.name)) {
                    QuranArchive.EntryDecision.Skip -> Unit
                    QuranArchive.EntryDecision.Reject -> throw IOException(QuranArchive.INVALID_ENTRY)
                    is QuranArchive.EntryDecision.Extract -> {
                        val bytes = readBounded(entries)
                        if (!QuranArchive.imageIsValid(bytes)) {
                            throw IOException(QuranArchive.INVALID_IMAGE)
                        }
                        File(directory, decision.name).writeBytes(bytes)
                        installed++
                        onProgress(
                            ArchiveProgress(
                                phase = ArchivePhase.EXTRACTING,
                                progress = (installed.toDouble() / ZIP_TOTAL_FILES).toFloat(),
                            ),
                        )
                    }
                }
                entries.closeEntry()
            }
        }
        if (installed != ZIP_TOTAL_FILES) throw IOException(QuranArchive.MISSING_FILES)
    }

    /**
     * Lit une image en refusant d'aller au-delà de la taille maximale.
     *
     * Le plafond est atteint **en lisant**, et non en regardant la taille annoncée : une archive
     * peut annoncer 1 ko et en fournir 200 Mo, et lire d'abord pour vérifier ensuite ne protège
     * de rien.
     */
    private fun readBounded(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (out.size() + read > QuranArchive.MAX_IMAGE_BYTES) {
                throw IOException(QuranArchive.TOO_LARGE)
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /** Un témoin illisible vaut « non installé », et une lecture qui échoue aussi. */
    private fun readWitness(): String? = runCatching { witness.readText() }.getOrNull()

    private fun fraction(written: Long): Float =
        (written.toDouble() / expectedBytes).toFloat().coerceIn(0f, 1f)

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
    }
}
