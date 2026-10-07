package com.msoumaya.deepseekandroid.core.data.local

import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.Recitations
import com.msoumaya.deepseekandroid.core.model.LocalRecitation
import com.msoumaya.deepseekandroid.core.model.RecitationKind
import com.msoumaya.deepseekandroid.core.model.RecitationSyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

// ---------------------------------------------------------------------------
// Les récitations de l'appareil
// ---------------------------------------------------------------------------
// Portage de la table `local_recitations` de `src/services/recitations.ts`, et du dossier
// `recitations/` qui porte les fichiers.
//
// **Ce n'est pas une table SQL, et ce n'est pas un oubli.** Le portage n'a ni Room ni SQLite :
// sa couche locale est faite de documents JSON (mesuré — `JsonFileStore`, `LocalStateStore`,
// `OutboxStore`, `QuizOutboxStore`, `BlobFile`). La transposition porte donc le **comportement**
// de la table — un enregistrement par récitation, un statut, une reprise des non-déposées —, et
// non sa forme.
//
// **Ce que la forme JSON fait disparaître, et pourquoi c'est légitime.** Le client d'origine
// crée sa table puis vérifie `PRAGMA table_info` pour ajouter la colonne `invocation_json` si
// elle manque : une migration à la main, parce qu'une colonne ajoutée après coup n'existe pas
// dans une base déjà installée. Ici, la récitation est un objet `@Serializable` dont `kind` et
// `invocationId` portent une valeur par défaut : un document écrit **avant** que ces champs
// existent se relit avec leurs défauts, sans migration et sans contrôle de schéma. C'est une
// règle que le portage fait **disparaître**, pas une règle qu'il oublie — et la disparition se
// mesure : il n'y a ici ni `PRAGMA`, ni `ALTER`, ni version de schéma à tenir.
//
// **Ce qui remplace la contrainte `NOT NULL` de la table.** `user_id`, `uri`, `created_at` et le
// statut sont non-nullables dans le modèle, donc un document qui les omettrait ne se décoderait
// pas — et `JsonFileStore` met alors le fichier de côté au lieu de perdre quoi que ce soit.
//
// **L'adresse du fichier est un chemin, pas une URI.** Le client d'origine range `file.uri`, de
// la forme `file:///data/...`. Le portage range le chemin absolu, sans schéma : `java.io.File` et
// l'enregistreur natif attendent tous deux un chemin, et un préfixe `file://` construirait un
// fichier qui n'existe pas — silencieusement, puisqu'un `File` inexistant ne lève qu'à la lecture.
// ---------------------------------------------------------------------------

/** Contenu du registre des récitations de l'appareil. */
@Serializable
data class RecitationLedger(val entries: List<LocalRecitation> = emptyList())

/**
 * Les récitations enregistrées sur l'appareil, et les fichiers qui vont avec.
 *
 * Le registre est **unique**, tous comptes confondus, comme la table de l'original : chaque
 * entrée porte son `userId`, et la lecture filtre. Un fichier par compte aurait dupliqué le
 * registre pour un gain nul — les fichiers audio, eux, sont bien dans un dossier commun, et c'est
 * la politique du compartiment de stockage qui les sépare à distance.
 *
 * @param root racine de l'état applicatif. Le dossier `recitations/` est créé dedans, à côté des
 *   documents d'état — c'est le `Paths.document` de l'original, qui est le `filesDir` d'Android.
 * @param nowIso horloge, injectable pour que l'ordre de la liste soit éprouvable.
 * @param newId fabrique d'identifiant. Il sert **deux fois** : clé de l'entrée, et nom du fichier
 *   sur le disque. Un identifiant qui ne serait pas utilisable comme nom de fichier produirait
 *   une entrée dont l'audio serait introuvable.
 */
class RecitationStore(
    private val root: File,
    private val nowIso: () -> String = { Dates.nowIso() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    private val ledger = JsonFileStore(
        file = File(root, LEDGER_FILE),
        serializer = RecitationLedger.serializer(),
        default = { RecitationLedger() },
    )

    /** Dossier des fichiers audio. Créé à la demande, jamais à la construction. */
    val folder: File get() = File(root, DIRECTORY)

    /**
     * Les récitations du compte [userId], **de la plus récente à la plus ancienne**.
     *
     * L'ordre est celui de l'original (`ORDER BY created_at DESC`), et il compte : la liste des
     * récitations s'ouvre sur ce qui vient d'être enregistré. La comparaison se fait sur
     * l'**instant** et non sur le texte, ce qui donne le même ordre quelle que soit l'écriture de
     * la date — l'original triait le texte d'une chaîne ISO, ce qui n'est correct que tant que
     * toutes les dates ont la même forme.
     */
    suspend fun list(userId: String): List<LocalRecitation> =
        ledger.current().entries
            .filter { it.userId == userId }
            .sortedByDescending { Dates.parseIsoMillis(it.createdAt) }

    /** Toutes les récitations, tous comptes confondus. Utile au diagnostic et à la reprise. */
    suspend fun all(): List<LocalRecitation> = ledger.current().entries

    /**
     * Copie [sourcePath] dans le dossier de l'application et inscrit la récitation au registre.
     *
     * **La copie est le premier geste, et elle n'est pas une précaution.** L'enregistreur écrit
     * dans un fichier temporaire que le système peut effacer ; sans copie, une récitation
     * entendue mais pas encore déposée disparaîtrait au redémarrage.
     *
     * **La nature se déduit de [invocationId], elle ne se déclare pas.** L'original fait de même
     * (`recordingType: invocation ? 'invocation' : 'quran'`) : un seul fait décide des deux
     * champs, donc les deux ne peuvent pas se contredire. Les prendre en deux paramètres aurait
     * ouvert la porte à une invocation marquée `quran`, que le serveur refuserait ensuite sans
     * que l'appareil puisse le prévoir.
     *
     * @param start premier verset. Ignoré pour une invocation, dont les bornes sont nulles.
     * @param end dernier verset. Ignoré pour une invocation.
     * @param invocationId invocation enregistrée, ou `null` pour un passage du Coran.
     * @return la récitation inscrite, statut `pending`.
     * @throws IllegalArgumentException si [Recitations.saveProblem] refuse — l'appelant doit avoir
     *   contrôlé avant, et cette levée est le filet de la dernière chance.
     */
    suspend fun add(
        sourcePath: String,
        start: Int,
        end: Int,
        durationMs: Long,
        userId: String,
        invocationId: String? = null,
    ): LocalRecitation {
        val kind = if (invocationId == null) RecitationKind.QURAN else RecitationKind.INVOCATION
        Recitations.saveProblem(kind, start, end, userId, sourcePath, durationMs)
            ?.let { throw IllegalArgumentException(it.name) }

        val id = newId()
        val extension = Recitations.extensionFor(sourcePath)
        val target = File(folder, "$id$extension")

        withContext(Dispatchers.IO) {
            folder.mkdirs()
            File(sourcePath).copyTo(target, overwrite = true)
        }

        val item = LocalRecitation(
            id = id,
            userId = userId,
            start = start,
            end = end,
            durationMs = durationMs,
            uri = target.absolutePath,
            createdAt = nowIso(),
            syncStatus = RecitationSyncStatus.PENDING,
            kind = kind,
            invocationId = invocationId,
        )
        ledger.update { current -> current.copy(entries = current.entries + item) }
        return item
    }

    /**
     * Change le statut de dépôt d'une récitation.
     *
     * Sans effet si l'identifiant est inconnu : l'entrée a pu être supprimée pendant un envoi, et
     * une écriture qui ne trouve rien ne doit pas en créer une.
     */
    suspend fun markStatus(id: String, status: RecitationSyncStatus) {
        ledger.update { current ->
            current.copy(
                entries = current.entries.map { if (it.id == id) it.copy(syncStatus = status) else it },
            )
        }
    }

    /**
     * Retire la récitation du registre **et efface son fichier**.
     *
     * L'ordre est celui de l'original : le fichier d'abord, la ligne ensuite. L'échec d'effacement
     * n'est pas propagé — `File.delete()` rend `false` au lieu de lever —, et c'est délibéré :
     * bloquer le retrait de la ligne sur un fichier verrouillé laisserait la récitation affichée
     * pour toujours, alors que la personne vient de demander à ne plus la voir. Le fichier
     * orphelin, lui, est sans conséquence : plus rien ne le référence.
     *
     * **Le couple `(id, userId)` est vérifié avant d'effacer quoi que ce soit**, et c'est une
     * divergence assumée par rapport à l'original. Celui-ci efface `item.uri` **avant** de
     * consulter la table : une entrée dont le compte ne correspond pas verrait son audio effacé
     * pendant que sa ligne survit — l'entrée resterait, mais sa lecture serait morte, et rien ne
     * le dirait. Le cas n'est atteignable par aucun appelant de l'original, qui ne passe que des
     * entrées lues du registre ; il le devient dès que la méthode est publique. Le contrôle ne
     * coûte qu'une lecture, et le résultat observable est identique pour tout appelant légitime.
     */
    suspend fun remove(item: LocalRecitation) {
        val connue = ledger.current().entries.any { it.id == item.id && it.userId == item.userId }
        if (!connue) return

        withContext(Dispatchers.IO) { File(item.uri).delete() }
        ledger.update { current ->
            current.copy(
                entries = current.entries.filterNot { it.id == item.id && it.userId == item.userId },
            )
        }
    }

    /** Le fichier audio d'une récitation. Il peut avoir été effacé hors de l'application. */
    fun fileOf(item: LocalRecitation): File = File(item.uri)

    /** Vrai si le fichier audio est encore là. La synchronisation le vérifie avant d'envoyer. */
    fun exists(item: LocalRecitation): Boolean = fileOf(item).isFile

    /**
     * Les octets du fichier audio, ou `null` s'il a disparu.
     *
     * La lecture passe par [BlobFile] pour une raison précise : elle s'exécute sur le
     * répartiteur d'entrées-sorties. Un enregistrement de dix minutes pèse plusieurs mégaoctets,
     * et le lire sur le fil principal figerait l'écran au moment précis où la personne attend.
     */
    suspend fun bytesOf(item: LocalRecitation): ByteArray? = BlobFile(fileOf(item)).read()

    companion object {
        /** Nom du document du registre. */
        const val LEDGER_FILE: String = "recitations.json"

        /** Dossier des fichiers audio, sous la racine de l'état. */
        const val DIRECTORY: String = "recitations"
    }
}
