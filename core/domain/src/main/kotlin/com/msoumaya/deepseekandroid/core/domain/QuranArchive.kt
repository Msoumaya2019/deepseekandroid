package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppJson
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Installation locale de la source « Coran 1441 ».
 *
 * Porté depuis `src/services/quranDownload.ts`. Le paquet fait **102 608 011 octets** et contient
 * 9 060 images : 604 pages × 15 lignes. Il n'est pas livré avec l'application, il est téléchargé
 * une fois puis lu hors connexion.
 *
 * Les règles sont ici, et non dans la couche réseau, parce que ce sont elles qui décident si une
 * installation est **utilisable** : une page blanche ne se distingue pas d'une page chargée tant
 * qu'on n'a pas vérifié les dimensions. Un fichier présent mais faux est plus dangereux qu'un
 * fichier absent, parce qu'il ne se plaint pas.
 */
object QuranArchive {

    /** Dossier d'installation, sous la racine des documents de l'application. */
    const val DIRECTORY = "quran/coran_1441"

    /** Le paquet en cours de téléchargement. */
    const val ZIP_FILE = "download.zip"

    /** La marque d'un téléchargement terminé, qui évite de le refaire. */
    const val COMPLETE_FILE = "download-complete.json"

    /** Le témoin d'installation : le nombre de fichiers écrits. */
    const val READY_FILE = "ready-v1.json"

    /** Préfixe des entrées utiles dans l'archive. */
    const val ENTRY_PREFIX = "width_1440/"

    /** Dimensions attendues d'une image de ligne. */
    const val IMAGE_WIDTH = 1440
    const val IMAGE_HEIGHT = 232

    /** Une image plus lourde que cela n'est pas une image de moushaf. */
    const val MAX_IMAGE_BYTES = 2 * 1024 * 1024

    /** Longueur minimale d'un en-tête PNG : signature, longueur, type, largeur, hauteur. */
    private const val PNG_HEADER_BYTES = 24

    const val INVALID_ENTRY = "Page invalide dans le ZIP."
    const val TOO_LARGE = "Image trop volumineuse."
    const val INVALID_IMAGE = "Image du Mushaf invalide."
    const val MISSING_FILES = "Certaines pages sont manquantes. Réessayez."
    const val INCOMPLETE = "Téléchargement incomplet. Réessayez avec une connexion stable."
    const val DISK_FULL = "Espace de stockage insuffisant. Libère de la place puis réessaie."
    const val NETWORK =
        "Le téléchargement a été interrompu. Vérifie ta connexion et réessaie en gardant " +
            "l’application ouverte."
    const val MISSING_PAGE_IMAGES = "Les images de cette page sont manquantes."

    /** Ce qu'il faut faire d'une entrée de l'archive. */
    sealed interface EntryDecision {
        /** Écrire cette image sous [name]. */
        data class Extract(val page: Int, val line: Int, val name: String) : EntryDecision

        /** Entrée sans rapport avec le moushaf : ignorer, sans échouer. */
        data object Skip : EntryDecision

        /** Entrée qui ressemble à une page mais sort du moushaf : refuser l'archive entière. */
        data object Reject : EntryDecision
    }

    private val entryPattern = Regex("""^width_1440/(\d+)/(\d+)\.png$""")

    /**
     * Décide du sort d'une entrée de l'archive.
     *
     * Trois issues, et la distinction compte : une archive contient des entrées sans rapport —
     * dossiers, fichiers de description — et les refuser toutes ferait échouer une installation
     * parfaitement valide. Mais une entrée qui **ressemble** à une page et sort du moushaf
     * signale une archive qui n'est pas celle qu'on croit, et l'installer donnerait des pages
     * décalées.
     */
    fun entryDecision(path: String): EntryDecision {
        val match = entryPattern.matchEntire(path) ?: return EntryDecision.Skip
        val page = match.groupValues[1].toIntOrNull() ?: return EntryDecision.Reject
        val line = match.groupValues[2].toIntOrNull() ?: return EntryDecision.Reject
        if (page < 1 || page > 604 || line < 1 || line > ZIP_LINES_PER_PAGE) {
            return EntryDecision.Reject
        }
        return EntryDecision.Extract(page = page, line = line, name = zipLineFileName(page, line))
    }

    /**
     * Le téléchargement est-il complet ?
     *
     * La taille est comparée **exactement**. Une taille supérieure est aussi fausse qu'une taille
     * inférieure : elle signifie que le fichier n'est pas celui attendu, et l'extraire donnerait
     * des images d'une autre source sous le nom de celle-ci.
     */
    fun isCompleteSize(bytes: Long): Boolean = bytes == ZIP_ARCHIVE_BYTES

    /** Un code de réponse qui autorise à écrire les octets reçus. */
    fun isAcceptedStatus(code: Int): Boolean = code == 200 || code == 206

    /**
     * L'image est-elle une ligne de moushaf valide ?
     *
     * **Deux octets de signature seulement**, comme le client d'origine, et les dimensions de
     * l'espace 1440×2320. Être plus strict ici rejetterait des fichiers que l'autre client
     * accepte : les deux doivent s'accorder sur ce qu'ils acceptent, pas seulement sur ce qu'ils
     * produisent.
     */
    fun imageIsValid(bytes: ByteArray): Boolean {
        if (bytes.size < PNG_HEADER_BYTES) return false
        if (bytes[0] != 137.toByte() || bytes[1] != 80.toByte()) return false
        return readUint32(bytes, 16) == IMAGE_WIDTH.toLong() &&
            readUint32(bytes, 20) == IMAGE_HEIGHT.toLong()
    }

    /** Contenu du témoin d'installation. Écrit **après** que les 9 060 fichiers sont en place. */
    fun readyJson(installedAt: String): String =
        """{"version":$ZIP_READY_VERSION,"files":$ZIP_TOTAL_FILES,"installedAt":"$installedAt"}"""

    /**
     * Le témoin atteste-t-il d'une installation complète ?
     *
     * Un témoin illisible vaut « non installé » : mieux vaut retélécharger que lire 9 060 images
     * dont on ne sait rien. C'est aussi ce qui rend le témoin utile — il n'est écrit qu'à la fin,
     * donc sa présence est une preuve.
     *
     * Les nombres sont lus **en tant que nombres**, et pas par leur texte : `intOrNull` accepte
     * aussi la chaîne `"1"`, alors que le client d'origine compare des nombres stricts
     * (`ready.version === 1`). Accepter `{"version":"1"}` ici et le refuser là-bas ferait
     * retélécharger 102 Mo à l'un des deux clients sans raison. En revanche `1` et `1.0` sont
     * acceptés tous les deux : en JavaScript, `1.0 === 1`.
     */
    fun isReady(json: String?): Boolean {
        if (json == null) return false
        return runCatching {
            val root = AppJson.parseToJsonElement(json).jsonObject
            val version = root["version"]?.jsonPrimitive?.numberOrNull()
            val files = root["files"]?.jsonPrimitive?.numberOrNull()
            version == ZIP_READY_VERSION.toDouble() && files == ZIP_TOTAL_FILES.toDouble()
        }.getOrDefault(false)
    }

    /**
     * Un nombre JSON, ou `null` si la valeur est une chaîne ou n'est pas un nombre.
     *
     * `content` plutôt que l'extension `doubleOrNull` : la lecture est la même, mais elle ne
     * dépend pas de la résolution d'une extension dans un fichier qui porte déjà un récepteur
     * qualifié — et une règle de sûreté ne doit pas dépendre de la façon dont on l'écrit.
     */
    private fun JsonPrimitive.numberOrNull(): Double? =
        if (isString) null else content.toDoubleOrNull()

    /**
     * Le message à montrer pour un échec de téléchargement.
     *
     * Le client d'origine reconnaît ses erreurs au **texte** de l'exception, ce qui marche pour
     * un `fetch` JavaScript mais pas pour Kotlin : une coupure réseau y arrive en
     * `UnknownHostException` ou `SocketTimeoutException`, dont le message ne contient ni
     * « network » ni « connection ». On reconnaît donc aussi les **types**, pour rendre le même
     * message à l'utilisateur. C'est le résultat qui doit être fidèle, pas le mécanisme.
     */
    fun errorMessage(error: Throwable): String {
        if (error is UnknownHostException || error is SocketTimeoutException || error is ConnectException) {
            return NETWORK
        }
        val detail = error.message ?: error.toString()
        if (Regex("space|disk|ENOSPC|storage", RegexOption.IGNORE_CASE).containsMatchIn(detail)) {
            return DISK_FULL
        }
        if (Regex("ERR_FILESYSTEM_CANNOT_DOWNLOAD|network|offline|connection|timed? ?out", RegexOption.IGNORE_CASE)
                .containsMatchIn(detail)
        ) {
            return NETWORK
        }
        return detail
    }

    private fun readUint32(bytes: ByteArray, at: Int): Long =
        ((bytes[at].toLong() and 0xFF) shl 24) or
            ((bytes[at + 1].toLong() and 0xFF) shl 16) or
            ((bytes[at + 2].toLong() and 0xFF) shl 8) or
            (bytes[at + 3].toLong() and 0xFF)
}

/** Étape d'une installation de la source « Coran 1441 ». */
enum class ArchivePhase {
    IDLE,
    DOWNLOADING,
    EXTRACTING,
    READY,
    PAUSED,
    ERROR,
}

/**
 * Avancement d'une installation.
 *
 * [progress] va de 0 à 1 dans les deux phases. Une installation reprenable n'est pas un détail :
 * 102 Mo sur une connexion mobile se coupent, et tout reprendre depuis zéro à chaque coupure
 * rendrait la source inutilisable.
 */
data class ArchiveProgress(
    val phase: ArchivePhase,
    val progress: Float,
    val message: String? = null,
) {
    /**
     * Une phase pendant laquelle un travail est en cours.
     *
     * C'est une règle, et non un détail d'affichage : c'est elle qui décide si l'écran propose
     * d'interrompre ou de lancer, et si le pourcentage a un sens. Écrite ici, elle est la même
     * pour l'écran et pour tout contrôle qui voudrait la vérifier.
     */
    val isBusy: Boolean
        get() = phase == ArchivePhase.DOWNLOADING || phase == ArchivePhase.EXTRACTING
}
