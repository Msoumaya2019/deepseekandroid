package com.msoumaya.deepseekandroid.core.data.local

import kotlinx.serialization.Serializable

/**
 * Une réponse au Quiz en attente d'envoi.
 *
 * [id] vaut `« <compte>:<jour> »` — **exactement la clé primaire de `quiz_outbox`** dans le
 * client d'origine. Cette forme n'est pas décorative : elle rend l'enfilement idempotent, une
 * seconde réponse pour le même compte et le même jour ne pouvant pas produire une seconde
 * entrée. C'est la règle « une seule participation par jour » appliquée à la file.
 *
 * [payload] porte le document JSON d'un `DailyAnswerPayload`, sérialisé **au moment de
 * l'enfilement** et jamais recomposé ensuite. C'est ce qui permet de renvoyer plus tard
 * exactement ce qui avait été préparé — y compris l'instant de la réponse sur l'appareil, que
 * le serveur compare à la fenêtre du jour. Le type vit dans `core.data.remote` et non ici :
 * c'est un corps de requête, pas une forme de rangement, et deux déclarations en auraient
 * divergé au premier champ ajouté.
 */
@Serializable
data class QuizPendingAnswer(
    val id: String,
    val userId: String,
    val payload: String,
    val createdAt: String,
)

/** Contenu du fichier de file d'attente du Quiz. */
@Serializable
data class QuizOutbox(val entries: List<QuizPendingAnswer> = emptyList())

/**
 * File d'attente des réponses au Quiz faites hors ligne.
 *
 * **Pourquoi une file à part, et non celle de l'état.** [OutboxStore] rend ses opérations par
 * utilisateur **sans filtrer leur type**, et le worker de synchronisation les envoie toutes
 * comme des instantanés d'état — `SyncQueueStore.list` ne connaît qu'un compte, pas une nature.
 * Y verser une réponse de Quiz ferait donc pousser `{p_question, p_answer, …}` comme un état
 * complet : un envoi **accepté** par le serveur, et une progression écrasée. L'original a
 * d'ailleurs deux tables distinctes (`outbox` et `quiz_outbox`) pour la même raison, et il
 * n'aurait pas suffi de nommer les choses autrement.
 *
 * **Ce que la file garantit, et ce qu'elle ne garantit pas.** Elle garantit qu'une réponse
 * enfilée ne peut pas être perdue par une écriture concurrente : [enqueue] n'écrase jamais une
 * entrée existante, et [acknowledge] n'est appelé qu'après une acceptation du serveur. Elle ne
 * garantit pas l'ordre d'arrivée : deux comptes différents peuvent avoir des entrées en
 * attente, et c'est voulu — chacune n'est envoyée que pour le compte qui la relit.
 */
class QuizOutboxStore(
    private val store: JsonFileStore<QuizOutbox>,
    private val nowIso: () -> String,
) {

    /** Les réponses en attente du compte [userId], dans l'ordre d'enfilement. */
    suspend fun list(userId: String): List<QuizPendingAnswer> =
        store.current().entries.filter { it.userId == userId }

    /**
     * Met une réponse en file, **sans jamais écraser une entrée de même [id]**.
     *
     * C'est l'`INSERT OR IGNORE` de l'original. Remplacer au lieu d'ignorer serait *presque*
     * toujours équivalent — la règle « une participation par jour » empêche d'enfiler deux fois
     * le même jour —, mais « presque » ne suffit pas : la première réponse est celle que
     * l'utilisateur a vue, et une seconde écriture ne doit pas pouvoir la déplacer sous ses
     * yeux.
     */
    suspend fun enqueue(userId: String, id: String, payload: String) {
        store.update { current ->
            if (current.entries.any { it.id == id }) {
                current
            } else {
                current.copy(
                    entries = current.entries + QuizPendingAnswer(
                        id = id,
                        userId = userId,
                        payload = payload,
                        createdAt = nowIso(),
                    ),
                )
            }
        }
    }

    /**
     * Retire les entrées [ids] : le serveur les a acceptées.
     *
     * La suppression se fait par identifiant seul, et c'est suffisant : l'identifiant contient
     * déjà le compte, donc aucune entrée d'un autre compte ne peut porter le même.
     */
    suspend fun acknowledge(ids: List<String>) {
        if (ids.isEmpty()) return
        val removed = ids.toSet()
        store.update { current ->
            current.copy(entries = current.entries.filterNot { it.id in removed })
        }
    }

    /** Reste-t-il une réponse en attente pour [userId] ? */
    suspend fun hasPending(userId: String): Boolean =
        store.current().entries.any { it.userId == userId }

    /** Contenu de la file, tous comptes confondus. Utile au diagnostic. */
    suspend fun snapshot(): QuizOutbox = store.current()
}
