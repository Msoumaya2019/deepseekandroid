package com.msoumaya.deepseekandroid.core.data.remote

import com.msoumaya.deepseekandroid.core.model.AppJson
import com.msoumaya.deepseekandroid.core.model.QuizSnapshot
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

// ---------------------------------------------------------------------------
// Implémentation Supabase du Quiz
// ---------------------------------------------------------------------------
// Portage de `src/services/quiz.ts`, fonction par fonction. C'est la seule partie du dépôt Quiz
// qui touche au réseau, et c'est délibéré : les décisions vivent dans `core:domain/Quiz.kt`,
// l'ordonnancement dans `QuizRepository`, et il ne reste ici que « quelle fonction, quels
// arguments ».
//
// **Les arguments sont nommés exactement comme dans `supabase/quiz.sql`, `p_` compris.**
// PostgREST apparie les arguments d'un appel RPC **par nom** : un `p_day` écrit `day` ne
// produirait pas une erreur de type mais une fonction introuvable, donc un message qui désigne
// l'appel et non l'argument. Les noms sont donc recopiés de la migration, pas devinés.
//
// **`quiz_snapshot` rend un document, pas une ligne de table.** Il n'y a donc **aucun
// `@SerialName` ici** : le serveur compose déjà ses clés en `camelCase`, exactement celles du
// modèle. C'est l'inverse des tables sociales, dont les colonnes sont en `snake_case` et
// exigent une ligne intermédiaire. Un `@SerialName` ajouté par symétrie ne ferait rien de
// visible — et masquerait le jour où les deux formes divergeraient.
//
// **Aucune garde n'est posée sur les arguments.** Un identifiant vide, un nombre de questions
// hors de 5 et 10, un fuseau inventé : le serveur refuse les trois, et il les refuse mieux —
// il connaît l'état des questions et la liste des fuseaux, que le client ne connaît pas. Les
// revérifier ici donnerait deux vérités pour une seule règle.
//
// **`quiz_answer_daily` fait exception à la règle des arguments écrits un par un**, et pour une
// raison mesurable : le même document est rangé dans la file hors ligne puis renvoyé depuis
// elle. Le composer ici en quatre `put` obligerait le dépôt à le défaire pour le ranger, puis à
// le refaire pour l'envoyer — deux occasions de divergence pour un seul contrat. Le corps est
// donc **dérivé de [DailyAnswerPayload]**, dont les `@SerialName` sont les seuls endroits où ces
// quatre noms sont écrits.
//
// L'appel passe par un `JsonObject` et non par le type lui-même : la surcharge générique de
// `rpc` n'est pas atteignable depuis Kotlin ici — le compilateur ne propose que les formes
// prenant un `JsonObject` —, et c'est cette forme-là qu'utilisent déjà les quatre autres appels.
// ---------------------------------------------------------------------------

/** Fonctions du projet partagé, nommées une fois. */
private const val RPC_SNAPSHOT = "quiz_snapshot"
private const val RPC_ANSWER_DAILY = "quiz_answer_daily"
private const val RPC_CREATE_CHALLENGE = "quiz_create_challenge"
private const val RPC_ANSWER_CHALLENGE = "quiz_answer_challenge"
private const val RPC_SET_NOTIFICATIONS = "quiz_set_notifications"

/**
 * Implémentation réelle, adossée au projet Supabase partagé avec le client React Native.
 *
 * @param client client déjà construit. Il n'est **jamais** construit ici : hors configuration,
 *   le conteneur ne crée pas de source du tout, et aucune de ces requêtes ne peut donc partir
 *   sans projet — plutôt que d'échouer une par une sur une adresse vide.
 */
class SupabaseQuizSource(private val client: SupabaseClient) : QuizSource {

    override suspend fun snapshot(day: String): QuizSnapshot =
        client.postgrest.rpc(RPC_SNAPSHOT, buildJsonObject { put("p_day", day) })
            .decodeAs<QuizSnapshot>()

    /**
     * Le corps de la requête est **dérivé du document rangé dans la file**, et non recomposé :
     * les noms d'arguments ne sont donc écrits qu'une fois, dans [DailyAnswerPayload]. Ce qui a
     * été enfilé est mot pour mot ce qui part.
     */
    override suspend fun answerDaily(payload: DailyAnswerPayload) {
        client.postgrest.rpc(
            RPC_ANSWER_DAILY,
            AppJson.encodeToJsonElement(payload).jsonObject,
        )
    }

    /**
     * Crée un défi et rend son identifiant.
     *
     * `p_set` est envoyé **même quand il est nul**, comme le fait `services/quiz.ts`
     * (`p_set: quizSet ?? null`). Ce n'est pas une précaution : la fonction le déclare
     * `uuid default null`, donc l'omettre donnerait la même valeur — mais l'envoyer garde la
     * requête identique à celle du client d'origine, ce qui rend les deux traces comparables.
     */
    override suspend fun createChallenge(
        opponentId: String,
        count: Int,
        quizSetId: String?,
    ): String =
        client.postgrest.rpc(
            RPC_CREATE_CHALLENGE,
            buildJsonObject {
                put("p_opponent", opponentId)
                put("p_count", count)
                put("p_set", quizSetId)
            },
        ).decodeAs<String>()

    override suspend fun answerChallenge(
        challengeId: String,
        questionId: String,
        answerId: String,
    ) {
        client.postgrest.rpc(
            RPC_ANSWER_CHALLENGE,
            buildJsonObject {
                put("p_challenge", challengeId)
                put("p_question", questionId)
                put("p_answer", answerId)
            },
        )
    }

    override suspend fun setNotifications(enabled: Boolean, timezone: String) {
        client.postgrest.rpc(
            RPC_SET_NOTIFICATIONS,
            buildJsonObject {
                put("p_enabled", enabled)
                put("p_timezone", timezone)
            },
        )
    }
}
