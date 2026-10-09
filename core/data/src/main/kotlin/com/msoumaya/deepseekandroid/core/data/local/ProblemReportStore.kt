package com.msoumaya.deepseekandroid.core.data.local

import com.msoumaya.deepseekandroid.core.data.remote.ProblemReportRow
import com.msoumaya.deepseekandroid.core.domain.ProblemReports
import com.msoumaya.deepseekandroid.core.model.ProblemReportType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

// ---------------------------------------------------------------------------
// La boîte d'envoi des signalements
// ---------------------------------------------------------------------------
// Portage de la table `problem_report_queue` de `src/services/problemReports.ts` (ligne 15), et du
// dossier `problem-reports/` qui porte les captures.
//
// **Ce n'est pas une table SQL, et ce n'est pas un oubli.** Le portage n'a ni Room ni SQLite : sa
// couche locale est faite de documents JSON (mesuré — `JsonFileStore`, `LocalStateStore`,
// `OutboxStore`, `QuizOutboxStore`, `RecitationStore`, `BlobFile`). La transposition porte donc le
// **comportement** de la table — un enregistrement par signalement, une capture copiée à côté, une
// reprise des non-envoyés —, et non sa forme.
//
// **Ce que la forme JSON fait disparaître.** Le client d'origine range le signalement dans une
// colonne `payload TEXT` et le relit par `JSON.parse`. Ici, la charge utile est un
// [ProblemReportRow] `@Serializable` : il n'y a plus de chaîne à analyser, donc plus d'analyse qui
// puisse échouer. Un document qui ne se décoderait pas est mis de côté par [JsonFileStore] au lieu
// d'être perdu, et c'est ce qui remplace la garantie qu'un `JSON.parse` réussi donnait.
//
// **L'identifiant et le compte ne sont pas recopiés.** La table d'origine les porte en colonnes
// (`id`, `user_id`) **et** dans la charge utile, parce que ce sont ses clés de lecture. Ici ils
// sont lus **sur la charge utile** ([QueuedProblemReport.id], [QueuedProblemReport.userId]) : deux
// copies d'un même fait finissent par diverger, et une divergence ici retirerait de la file
// l'entrée d'un autre compte.
//
// **L'adresse de la capture est un chemin, pas une URI.** Le client d'origine range `file.uri`, de
// la forme `file:///data/...`. Le portage range le chemin absolu, sans schéma : `java.io.File`
// attend un chemin, et un préfixe `file://` construirait un fichier qui n'existe pas —
// silencieusement, puisqu'un `File` inexistant ne lève qu'à la lecture. C'est la même règle que
// pour les récitations.
// ---------------------------------------------------------------------------

/** Contenu de la boîte d'envoi. */
@Serializable
data class ProblemReportQueue(val entries: List<QueuedProblemReport> = emptyList())

/**
 * Un signalement qui attend d'être envoyé.
 *
 * @param report la ligne telle qu'elle partira. C'est **elle** qui porte l'identifiant et le
 *   compte, et non l'entrée : voir la note de tête.
 * @param localPath chemin absolu de la copie de la capture, ou `null` s'il n'y en a pas.
 * @param mime le type MIME à déclarer au dépôt, ou `null` quand il n'y a pas de capture. Il est
 *   rangé à côté du chemin plutôt que déduit de l'extension : le type MIME peut avoir été donné
 *   par le sélecteur d'images, et le redéduire de l'extension perdrait cette information.
 */
@Serializable
data class QueuedProblemReport(
    val report: ProblemReportRow,
    val localPath: String? = null,
    val mime: String? = null,
) {
    /** L'identifiant du signalement, lu sur la ligne. */
    val id: String get() = report.id

    /** Le compte à qui appartient le signalement, lu sur la ligne. */
    val userId: String get() = report.userId
}

/**
 * Ce qu'un signalement apporte **avant** d'avoir un identifiant.
 *
 * L'identifiant est fabriqué par [ProblemReportStore.enqueue], et il ne peut pas en être autrement :
 * il sert **deux fois** — clé de l'entrée, et nom du fichier de la capture. Un appelant qui le
 * fabriquerait devrait aussi nommer le fichier, c'est-à-dire connaître la règle de nommage, qui
 * appartient au magasin.
 */
data class ProblemReportDraft(
    val userId: String,
    val type: ProblemReportType,
    val description: String,
    val appVersion: String,
    val platform: String,
    val createdAt: String,
    /** Chemin du fichier choisi, ou `null` s'il n'y a pas de capture. */
    val attachmentPath: String? = null,
    /** Extension décidée par [ProblemReports.extensionFor], quand il y a une capture. */
    val attachmentExtension: String? = null,
    /** Type MIME décidé par [ProblemReports.resolveMime], quand il y a une capture. */
    val attachmentMime: String? = null,
)

/**
 * La boîte d'envoi des signalements, et les captures qui vont avec.
 *
 * Le document est **unique**, tous comptes confondus, comme la table de l'original : chaque entrée
 * porte son compte, et la lecture filtre. Un fichier par compte aurait dupliqué la file pour un
 * gain nul — les captures, elles, sont bien dans un dossier commun, et c'est la politique du
 * compartiment qui les sépare à distance.
 *
 * @param root racine de l'état applicatif. Le dossier `problem-reports/` est créé dedans, à côté
 *   des documents d'état — c'est le `Paths.document` de l'original, qui est le `filesDir`
 *   d'Android.
 * @param newId fabrique d'identifiant. Il sert **deux fois** : clé de l'entrée, et nom du fichier
 *   sur le disque. L'original fabrique un UUID de version 4, et ce n'est pas cosmétique : la
 *   politique du compartiment n'accepte que `[0-9a-f-]{36}` comme nom de fichier
 *   (`supabase/problem-reports.sql`, ligne 24), donc un identifiant d'une autre forme produirait
 *   une capture que le serveur refuserait.
 */
class ProblemReportStore(
    private val root: File,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    private val queue = JsonFileStore(
        file = File(root, QUEUE_FILE),
        serializer = ProblemReportQueue.serializer(),
        default = { ProblemReportQueue() },
    )

    /** Dossier des captures. Créé à la demande, jamais à la construction. */
    val folder: File get() = File(root, DIRECTORY)

    /** Tous les signalements en attente, tous comptes confondus, dans l'ordre d'arrivée. */
    suspend fun all(): List<QueuedProblemReport> = queue.current().entries

    /**
     * Les signalements en attente du compte [userId], **dans l'ordre d'arrivée**.
     *
     * L'ordre est celui de l'original (`ORDER BY rowid`), et il n'est pas une commodité : la file
     * est vidée **depuis la tête**, et un échec laisse l'entrée en place. L'ordre d'arrivée est
     * donc ce qui garantit qu'un signalement n'est jamais doublé par un plus récent.
     */
    suspend fun list(userId: String): List<QueuedProblemReport> =
        all().filter { it.userId == userId }

    /** La tête de la file d'un compte, ou `null` si elle est vide. */
    suspend fun first(userId: String): QueuedProblemReport? =
        all().firstOrNull { it.userId == userId }

    /**
     * Copie la capture de [draft], inscrit le signalement et rend l'entrée écrite.
     *
     * **La copie est le premier geste, et elle n'est pas une précaution.** Le sélecteur d'images
     * rend une adresse vers un fichier qui appartient au système ou à une autre application : sans
     * copie, une capture choisie mais pas encore envoyée disparaîtrait dès que le fournisseur
     * reprend son fichier. C'est le même raisonnement que pour une récitation, dont le fichier
     * vient d'un brouillon temporaire.
     *
     * **La description écrite est rognée**, et c'est la valeur que la file portera jusqu'au
     * serveur : l'original range `description:text` où `text = description.trim()`. Rogner au
     * moment de l'envoi plutôt qu'ici ferait relire une valeur différente de celle qui a été
     * validée.
     *
     * @throws IllegalArgumentException si [ProblemReports.descriptionProblem] refuse le texte —
     *   l'appelant doit avoir contrôlé avant, et cette levée est le filet de la dernière chance.
     */
    suspend fun enqueue(draft: ProblemReportDraft): QueuedProblemReport {
        ProblemReports.descriptionProblem(draft.description)
            ?.let { throw IllegalArgumentException(it.name) }

        val id = newId()
        val chemin = draft.attachmentPath
        val extension = draft.attachmentExtension

        var localPath: String? = null
        var screenshotPath: String? = null
        if (chemin != null && extension != null) {
            val cible = File(folder, "$id.$extension")
            withContext(Dispatchers.IO) {
                folder.mkdirs()
                File(chemin).copyTo(cible, overwrite = true)
            }
            localPath = cible.absolutePath
            // L'adresse distante est composée **ici**, à côté du nom du fichier local, parce que
            // les deux portent le même identifiant et la même extension : les composer à deux
            // endroits laisserait la ligne désigner une capture que le compartiment ne contient
            // pas.
            screenshotPath = ProblemReports.screenshotPath(draft.userId, id, extension)
        }

        val entry = QueuedProblemReport(
            report = ProblemReportRow(
                id = id,
                userId = draft.userId,
                type = draft.type,
                description = draft.description.trim(),
                screenshotPath = screenshotPath,
                appVersion = draft.appVersion,
                platform = draft.platform,
                createdAt = draft.createdAt,
            ),
            localPath = localPath,
            mime = draft.attachmentMime,
        )
        queue.update { courant -> courant.copy(entries = courant.entries + entry) }
        return entry
    }

    /**
     * Retire le signalement de la file **et efface sa capture**.
     *
     * **L'ordre est celui de l'original** — l'entrée d'abord, le fichier ensuite —, et il est
     * l'**inverse** de celui de `RecitationStore.remove`. Ce n'est pas une inattention : le risque
     * n'est pas orienté de la même façon. Une récitation retirée mais dont le fichier survit est
     * visible dans une liste, donc rattrapable ; un signalement dont l'entrée survit alors que sa
     * capture a disparu serait repris à chaque vidage et **échouerait à chaque fois**, sans que
     * personne puisse le voir ni le retirer. Le fichier orphelin, lui, est sans conséquence : plus
     * rien ne le nomme, et le dossier des captures ne s'affiche nulle part.
     *
     * **Le couple `(id, compte)` est vérifié avant d'effacer quoi que ce soit**, comme pour les
     * récitations : une entrée dont le compte ne correspond pas ne doit pas voir sa capture
     * effacée pendant que sa ligne survit.
     */
    suspend fun remove(entry: QueuedProblemReport) {
        val connue = queue.current().entries.any { it.id == entry.id && it.userId == entry.userId }
        if (!connue) return

        queue.update { courant ->
            courant.copy(
                entries = courant.entries.filterNot {
                    it.id == entry.id && it.userId == entry.userId
                },
            )
        }
        withContext(Dispatchers.IO) { fileOf(entry)?.delete() }
    }

    /** La copie locale de la capture, ou `null` s'il n'y en a pas. */
    fun fileOf(entry: QueuedProblemReport): File? = entry.localPath?.let { File(it) }

    /** Vrai si la capture est encore là. La synchronisation le vérifie avant d'envoyer. */
    fun exists(entry: QueuedProblemReport): Boolean = fileOf(entry)?.isFile == true

    /**
     * Les octets de la capture, ou `null` si elle a disparu.
     *
     * La lecture passe par [BlobFile] pour une raison précise : elle s'exécute sur le répartiteur
     * d'entrées-sorties. Une capture d'écran pèse facilement plusieurs mégaoctets, et la lire sur
     * le fil principal figerait l'écran au moment précis où la personne attend une réponse.
     */
    suspend fun bytesOf(entry: QueuedProblemReport): ByteArray? =
        fileOf(entry)?.let { BlobFile(it).read() }

    companion object {
        /** Nom du document de la file. */
        const val QUEUE_FILE: String = "problem_reports.json"

        /**
         * Dossier des captures, sous la racine de l'état.
         *
         * Il est **public** pour que le test puisse nommer le dossier qu'il inspecte : vérifier
         * qu'une capture a bien été copiée à côté du document demande de lire le dossier, et un
         * nom recopié dans le test ne prouverait que sa propre exactitude.
         */
        const val DIRECTORY: String = "problem-reports"
    }
}
