package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.remote.RecitationSource
import com.msoumaya.deepseekandroid.core.data.remote.RecitationUploadRow
import com.msoumaya.deepseekandroid.core.data.remote.RecitationUploader
import com.msoumaya.deepseekandroid.core.model.GeneralFeedback
import com.msoumaya.deepseekandroid.core.model.RemoteRecitation
import com.msoumaya.deepseekandroid.core.model.VerseCorrection
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

// ---------------------------------------------------------------------------
// Doublure du lecteur distant
// ---------------------------------------------------------------------------
// **Pourquoi elle arrive maintenant, et pas avec `RecitationSource`.** Une doublure n'existe que
// pour un consommateur : tant que rien n'appelait la lecture distante, elle n'aurait éprouvé
// qu'elle-même. Elle arrive avec le dépôt qui la consomme — comme la doublure du déposant est
// arrivée avec la file, et non avec le déposant.
//
// **Ce qu'elle retient.** L'**ordre** des appels, comme l'autre : une suppression qui lirait la
// liste après avoir supprimé, ou une lecture distante qui partirait avant la lecture locale,
// seraient deux défauts que des valeurs rendues ne montreraient pas.
//
// **Le refus est réglable séparément** pour la lecture et pour la suppression : la panne qu'on
// veut éprouver est celle d'**une** opération, et un refus global ne dirait pas laquelle a été
// absorbée.
// ---------------------------------------------------------------------------

/** Doublure en mémoire du lecteur distant. */
internal class FakeRecitationSource : RecitationSource {

    /** Les appels, dans leur ordre, sous une forme lisible par un test. */
    val calls = mutableListOf<String>()

    /** Ce que la lecture de la liste rend, par compte. Suspendue : un test peut y changer de compte. */
    var byOwner: suspend (String) -> List<RemoteRecitation> = { emptyList() }

    /** Ce que la lecture des corrections rend, par récitation. */
    var correctionsBy: (String) -> List<VerseCorrection> = { emptyList() }

    /** Ce que la lecture des retours généraux rend, par récitation. */
    var feedbackBy: (String) -> List<GeneralFeedback> = { emptyList() }

    /** Refus de la lecture de la liste, ou `null`. */
    var refuseList: Throwable? = null

    /** Refus de la suppression, ou `null`. */
    var refuseDelete: Throwable? = null

    /** Les récitations supprimées, dans leur ordre. */
    val deleted = mutableListOf<RemoteRecitation>()

    /** Ce que rend l'adresse signée d'un chemin. */
    var signedUrl: (String) -> String = { "https://exemple.test/$it" }

    override suspend fun listMine(userId: String): List<RemoteRecitation> {
        calls += "liste"
        refuseList?.let { throw it }
        return byOwner(userId)
    }

    override suspend fun corrections(recitationId: String): List<VerseCorrection> {
        calls += "corrections"
        return correctionsBy(recitationId)
    }

    override suspend fun generalFeedback(recitationId: String): List<GeneralFeedback> {
        calls += "retours"
        return feedbackBy(recitationId)
    }

    override suspend fun signedAudioUrl(path: String): String {
        calls += "adresse"
        return signedUrl(path)
    }

    override suspend fun deleteRemote(recitation: RemoteRecitation) {
        calls += "suppression"
        refuseDelete?.let { throw it }
        deleted += recitation
    }
}
