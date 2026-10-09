package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.remote.ProblemReportRow
import com.msoumaya.deepseekandroid.core.data.remote.ProblemReportSender
import kotlinx.coroutines.CompletableDeferred

// ---------------------------------------------------------------------------
// Doublure des tests du dépôt « Signalements »
// ---------------------------------------------------------------------------
// **Ce qu'elle mesure.** Elle retient l'**ordre** de ses appels — la capture, puis la ligne, puis
// la confirmation — et le **contenu** de ce qui part : l'adresse, le type MIME, les octets, la
// ligne.
//
// Cet ordre n'est pas une commodité. Déposer la ligne avant la capture écrirait un signalement
// dont l'image n'existe pas — le serveur l'accepterait, la colonne n'étant liée à aucune clé
// étrangère. Confirmer avant d'écrire la ligne confirmerait un signalement qui n'existe pas.
// Retirer l'entrée de la file avant la confirmation la perdrait sur un doute. Une doublure qui se
// contenterait de rendre des valeurs ne pourrait apercevoir aucun des trois.
//
// **Le refus est réglable séparément** pour chacun des trois gestes : la panne qu'on veut éprouver
// est celle d'**une** opération, et un refus global ne dirait pas laquelle a été absorbée.
//
// **La confirmation se refuse de deux façons, et elles ne sont pas la même.** Rendre `false` dit
// « le serveur ne me rend pas la ligne » ; lever dit « la lecture a échoué ». La première doit
// laisser le signalement dans la file — il n'est peut-être pas arrivé —, et la seconde aussi :
// c'est précisément ce que la doublure permet de distinguer.
// ---------------------------------------------------------------------------

/** Doublure en mémoire de l'expéditeur. */
internal class FakeProblemReportSender : ProblemReportSender {

    /** Une capture, telle qu'elle est partie. */
    data class Upload(val path: String, val size: Int, val mimeType: String)

    /** Les appels, dans leur ordre, sous une forme lisible par un test. */
    val calls = mutableListOf<String>()

    val uploads = mutableListOf<Upload>()
    val rows = mutableListOf<ProblemReportRow>()

    /** Les couples `(id, compte)` demandés à la confirmation, dans leur ordre. */
    val confirmations = mutableListOf<Pair<String, String>>()

    /** Refus d'un dépôt de capture, par adresse. Rendre `null` laisse l'appel aboutir. */
    var refuseUpload: (path: String) -> Throwable? = { null }

    /** Refus de l'écriture de la ligne, ou `null`. */
    var refuseInsert: Throwable? = null

    /**
     * Une porte qui retient l'écriture de la ligne, ou `null`.
     *
     * Elle existe pour observer un envoi **en vol** sans dépendre du temps : un test qui veut
     * vérifier ce qui se passe pendant qu'un signalement part doit pouvoir tenir la passe ouverte,
     * et une attente en millisecondes ferait un test qui passe sur une machine rapide et échoue
     * ailleurs — c'est exactement le défaut qui a fait échouer l'intégration continue du Quiz.
     */
    var porte: CompletableDeferred<Unit>? = null

    /** Refus de la **lecture** de confirmation, ou `null`. Ce n'est pas la même chose que `false`. */
    var refuseConfirm: Throwable? = null

    /** Ce que la confirmation rend. Par défaut, la ligne est rendue. */
    var confirme: (id: String) -> Boolean = { true }

    override suspend fun uploadScreenshot(path: String, bytes: ByteArray, mimeType: String) {
        calls += "capture"
        refuseUpload(path)?.let { throw it }
        uploads += Upload(path = path, size = bytes.size, mimeType = mimeType)
    }

    override suspend fun insert(row: ProblemReportRow) {
        calls += "ligne"
        porte?.await()
        refuseInsert?.let { throw it }
        rows += row
    }

    override suspend fun confirm(id: String, userId: String): Boolean {
        calls += "confirmation"
        confirmations += id to userId
        refuseConfirm?.let { throw it }
        return confirme(id)
    }
}
