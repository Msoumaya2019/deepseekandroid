package com.msoumaya.deepseekandroid.core.data.remote

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Qui possède l'état local, et donc quel fichier d'état est lu.
 *
 * L'interface existe pour que les dépôts soient éprouvables sans Android : les tests
 * substituent une implémentation en mémoire et mesurent la logique de synchronisation, qui
 * est la partie où une faute coûte des données.
 */
interface OwnerStore {

    val ownerId: Flow<String?>

    suspend fun currentOwner(): String?

    suspend fun setOwner(userId: String?)
}

/**
 * Réglages de session, en clair et volontairement minimaux.
 *
 * Ce fichier ne contient **que** l'identifiant du propriétaire courant. Le jeton d'accès est
 * ailleurs, chiffré par le magasin de clés (voir `VaultSessionManager`). Séparer les deux
 * permet de savoir qui est connecté — information nécessaire dès le démarrage, avant tout
 * déchiffrement — sans jamais manipuler le secret pour autant.
 */
class SessionPreferences(private val store: DataStore<Preferences>) : OwnerStore {

    override val ownerId: Flow<String?> = store.data.map { it[KEY_OWNER] }

    override suspend fun currentOwner(): String? = store.data.first()[KEY_OWNER]

    override suspend fun setOwner(userId: String?) {
        store.edit { prefs ->
            if (userId == null) prefs.remove(KEY_OWNER) else prefs[KEY_OWNER] = userId
        }
    }

    companion object {
        private val KEY_OWNER = stringPreferencesKey("owner_id")

        /**
         * Ouvre le fichier de réglages.
         *
         * Ici, et ici seulement, un fichier illisible est remplacé par des réglages vides :
         * la perte se limite à `owner_id`, que le dépôt réécrit de toute façon à partir de la
         * session Supabase au démarrage. La progression d'apprentissage, elle, vit dans un
         * `JsonFileStore` qui archive le fichier fautif au lieu de l'écraser.
         */
        fun open(
            file: File,
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ): SessionPreferences {
            file.parentFile?.mkdirs()
            val store = PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = scope,
                produceFile = { file },
            )
            return SessionPreferences(store)
        }
    }
}
