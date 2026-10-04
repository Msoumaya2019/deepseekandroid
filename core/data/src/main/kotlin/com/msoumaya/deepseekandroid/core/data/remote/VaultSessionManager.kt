package com.msoumaya.deepseekandroid.core.data.remote

import com.msoumaya.deepseekandroid.core.model.AppJson
import com.msoumaya.deepseekandroid.core.data.local.BlobFile
import com.msoumaya.deepseekandroid.core.data.security.SecretVault
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession

/**
 * Conservation de la session Supabase, chiffrée par le magasin de clés Android.
 *
 * Le SDK Supabase propose par défaut un stockage en clair. Un jeton d'accès placé en clair
 * donne accès à toutes les données de l'utilisateur à quiconque lit le fichier : c'est
 * inacceptable pour un carnet d'apprentissage personnel.
 *
 * Le jeton est donc chiffré avant d'atteindre le disque. La clé reste dans le magasin de clés
 * du système, inaccessible même en lisant les fichiers de l'application.
 */
class VaultSessionManager(
    private val vault: SecretVault,
    private val blob: BlobFile,
) : SessionManager {

    override suspend fun saveSession(session: UserSession) {
        val json = AppJson.encodeToString(UserSession.serializer(), session)
        blob.write(vault.encrypt(json.encodeToByteArray()))
    }

    /**
     * Relit la session.
     *
     * Une session illisible est traitée comme une absence de session, et non comme une
     * erreur : cela se produit légitimement après une restauration de sauvegarde sur un
     * appareil dont le magasin de clés ne contient pas la clé. L'utilisateur se reconnecte,
     * ce qui est le comportement attendu.
     */
    override suspend fun loadSession(): UserSession {
        val encrypted = blob.read() ?: throw NoSessionFoundException()
        val plain = vault.decrypt(encrypted) ?: run {
            // Le secret est inutilisable : on l'efface pour ne pas le relire indéfiniment.
            blob.delete()
            throw NoSessionFoundException()
        }
        return runCatching {
            AppJson.decodeFromString(UserSession.serializer(), plain.decodeToString())
        }.getOrElse {
            blob.delete()
            throw NoSessionFoundException()
        }
    }

    override suspend fun deleteSession() {
        blob.delete()
    }
}
