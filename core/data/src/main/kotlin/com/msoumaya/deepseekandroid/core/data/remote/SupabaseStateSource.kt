package com.msoumaya.deepseekandroid.core.data.remote

import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.model.AppJson
import com.msoumaya.deepseekandroid.core.model.AppState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Accès à l'état utilisateur distant.
 *
 * L'interface existe pour que le dépôt soit éprouvable sans réseau : les tests substituent
 * une source en mémoire et mesurent la logique de fusion et de file d'attente, qui est la
 * partie où une faute coûte des données.
 */
interface RemoteStateSource {

    /** État du serveur, ou `null` si l'utilisateur n'en a pas encore. */
    suspend fun loadState(userId: String): AppState?

    /** Écrit l'état. Idempotent : c'est un `upsert` sur la clé primaire `user_id`. */
    suspend fun saveState(userId: String, state: AppState)

    suspend fun deleteState(userId: String)
}

/**
 * Ligne de `public.user_state`.
 *
 * Schéma du projet partagé avec le client React Native :
 * `user_id uuid primary key references auth.users, data jsonb, updated_at timestamptz`.
 * Les noms de colonnes sont en `snake_case` côté base et en `camelCase` côté Kotlin : la
 * correspondance est explicite ici, jamais implicite.
 */
@Serializable
data class UserStateRow(
    @SerialName("user_id") val userId: String,
    val data: JsonObject,
    @SerialName("updated_at") val updatedAt: String,
)

/** Implémentation Supabase, seule partie du dépôt qui touche au réseau. */
class SupabaseStateSource(
    private val client: SupabaseClient,
    private val nowIso: () -> String = { Dates.nowIso() },
) : RemoteStateSource {

    override suspend fun loadState(userId: String): AppState? {
        val row = client.postgrest.from(TABLE)
            .select { filter { eq("user_id", userId) } }
            .decodeSingleOrNull<UserStateRow>()
            ?: return null
        // Le décodage est tolérant (`ignoreUnknownKeys`, `coerceInputValues`) : un état écrit
        // par un client plus récent reste lisible ici, et un champ disparu devient sa valeur
        // par défaut au lieu de faire échouer la connexion.
        return AppJson.decodeFromJsonElement(AppState.serializer(), row.data)
    }

    override suspend fun saveState(userId: String, state: AppState) {
        val row = UserStateRow(
            userId = userId,
            data = AppJson.encodeToJsonElement(AppState.serializer(), state).jsonObject,
            updatedAt = nowIso(),
        )
        client.postgrest.from(TABLE).upsert(row)
    }

    override suspend fun deleteState(userId: String) {
        client.postgrest.from(TABLE).delete { filter { eq("user_id", userId) } }
    }

    companion object {
        const val TABLE = "user_state"
    }
}
