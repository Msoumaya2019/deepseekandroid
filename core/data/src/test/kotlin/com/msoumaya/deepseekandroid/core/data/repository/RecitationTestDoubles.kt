package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.remote.RecitationUploadRow
import com.msoumaya.deepseekandroid.core.data.remote.RecitationUploader
import kotlinx.coroutines.CompletableDeferred

// ---------------------------------------------------------------------------
// Doublure des tests du dépôt « Récitations »
// ---------------------------------------------------------------------------
// **Ce qu'elle mesure.** Elle retient l'**ordre** de ses appels — le fichier avant la ligne — et
// le **contenu** de ce qui part : le chemin, le type MIME, les octets, la ligne.
//
// Cet ordre n'est pas une commodité : déposer la ligne avant le fichier écrirait une récitation
// que personne ne pourrait écouter, et le serveur l'accepterait — la ligne ne référence le
// fichier par aucune clé étrangère, et rien ne vérifierait qu'il existe. Une doublure qui se
// contenterait de rendre des valeurs ne pourrait pas l'apercevoir.
//
// **Le refus est par chemin, et non global.** C'est la seule façon de mesurer qu'un élément qui
// échoue n'empêche pas le suivant de partir : avec un refus global, les deux échouent, et le test
// passerait en ne mesurant rien.
//
// **Le crochet [onUpload] est appelé pendant le dépôt, sur la coroutine du dépôt.** Il permet
// d'éprouver la garde de concurrence sans deuxième coroutine, sans attente et sans horloge : un
// appel réentrant à `syncPending()` doit rendre la main au lieu de démarrer un second dépôt.
// ---------------------------------------------------------------------------

/** Doublure en mémoire du déposant. */
internal class FakeRecitationUploader : RecitationUploader {

    /** Un dépôt, tel qu'il est parti. */
    data class Upload(val path: String, val size: Int, val mimeType: String)

    /** Les appels, dans leur ordre, sous une forme lisible par un test. */
    val calls = mutableListOf<String>()

    val uploads = mutableListOf<Upload>()
    val rows = mutableListOf<RecitationUploadRow>()

    /** Refus d'un dépôt, par chemin. Rendre `null` laisse l'appel aboutir. */
    var refuseUpload: (path: String) -> Throwable? = { null }

    /** Refus de l'écriture de la ligne, ou `null`. */
    var refuseUpsert: Throwable? = null

    /** Appelé **pendant** le dépôt, avant tout refus : c'est là qu'un appel réentrant se pose. */
    var onUpload: (suspend () -> Unit)? = null

    /** Quand elle est posée, le dépôt attend qu'elle soit franchie. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun upload(path: String, bytes: ByteArray, mimeType: String) {
        calls += "depot"
        onUpload?.invoke()
        gate?.await()
        refuseUpload(path)?.let { throw it }
        uploads += Upload(path = path, size = bytes.size, mimeType = mimeType)
    }

    override suspend fun upsert(row: RecitationUploadRow) {
        calls += "ligne"
        refuseUpsert?.let { throw it }
        rows += row
    }
}
