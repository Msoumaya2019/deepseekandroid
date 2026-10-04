package com.msoumaya.deepseekandroid.core.data.remote

import com.msoumaya.deepseekandroid.core.model.AppJson
import com.msoumaya.deepseekandroid.core.model.AppState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/**
 * Paramètres de connexion au projet Supabase **existant**.
 *
 * L'application Android se branche sur le même projet que le client React Native
 * (`npbwnvrqmajwqtnncuyv`) : mêmes utilisateurs, mêmes identifiants, mêmes données. Créer un
 * second projet aurait dupliqué les comptes et rompu la continuité d'apprentissage entre les
 * deux clients.
 *
 * [anonKey] est la clé **publique** du projet. Elle est faite pour être embarquée dans un
 * client : les droits réels sont décidés par les politiques RLS du serveur. La clé
 * `service_role` ne doit jamais se trouver ici — elle contourne RLS et donnerait à quiconque
 * l'extrait du binaire un accès total à toutes les données.
 */
data class SupabaseConfig(
    val url: String,
    val anonKey: String,
) {
    /** Vrai si l'application peut réellement se connecter. */
    val isConfigured: Boolean
        get() = url.startsWith("https://") && anonKey.isNotBlank()

    companion object {
        /** Projet partagé avec le client React Native. */
        const val PROJECT_URL = "https://npbwnvrqmajwqtnncuyv.supabase.co"

        val PLACEHOLDER = SupabaseConfig(PROJECT_URL, "")
    }
}

/** Fabrique du client Supabase. */
object SupabaseProvider {

    /**
     * Crée le client.
     *
     * - `autoLoadFromStorage` : la session est relue au démarrage, donc l'application
     *   s'ouvre connectée sans réseau ;
     * - `autoSaveToStorage` : un jeton rafraîchi est persisté immédiatement ;
     * - `alwaysAutoRefresh` : le jeton est rafraîchi avant expiration même si l'utilisateur
     *   ne fait rien, ce qui évite un échec au premier appel après une longue pause.
     */
    fun create(config: SupabaseConfig, sessionManager: SessionManager): SupabaseClient =
        createSupabaseClient(config.url, config.anonKey) {
            install(Auth) {
                this.sessionManager = sessionManager
                autoLoadFromStorage = true
                autoSaveToStorage = true
                alwaysAutoRefresh = true
            }
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }

    /** Sérialise un état pour le diagnostic et les journaux. */
    fun encode(state: AppState): String = AppJson.encodeToString(AppState.serializer(), state)
}
