package com.msoumaya.deepseekandroid.core.data.remote

import com.msoumaya.deepseekandroid.core.domain.DailyContentMedia
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import kotlin.time.Duration.Companion.seconds

// ---------------------------------------------------------------------------
// Implémentation Supabase des médias de contenu du jour
// ---------------------------------------------------------------------------
// Une seule méthode réseau, et deux arguments : le chemin, et une durée. Tout le reste — la
// reconnaissance de la racine, la fenêtre de réutilisation, le repli sur une adresse non
// signable — vit dans `DailyContentMedia` et dans le dépôt, et rien n'est redécidé ici.
//
// **La signature du SDK a été relevée dans l'artefact livré**, et non devinée —
// `storage-kt-android-3.8.0.aar`, classe `BucketApi` :
//
//   - `createSignedUrl(path: String, expiresIn: Duration, …): String` — le nom compilé est manglé
//     (`createSignedUrl-dWUq8MI`), ce qui signale un paramètre de type *value class*, et la vue
//     `javap` le donne en `long`. L'original passe `3600`, **des secondes** ; écrire `3600` ici ne
//     compilerait pas, et `DailyContentMedia.SIGNED_URL_SECONDS.seconds` est la traduction.
//
// C'est la même signature que celle employée par `SupabaseRecitationSource.signedAudioUrl`, et
// c'est délibéré : deux endroits qui signent une adresse dans le même projet doivent lire la même
// documentation du SDK, sans quoi l'un des deux finirait par suivre une version qui a changé.
// ---------------------------------------------------------------------------

/**
 * Implémentation réelle, adossée au projet Supabase partagé avec le client React Native.
 *
 * @param client client déjà construit. Il n'est **jamais** construit ici : hors configuration, le
 *   conteneur ne crée pas de source du tout, et aucune signature ne peut donc partir sans projet.
 * @param projectUrl adresse avec laquelle [client] a été construit — c'est-à-dire
 *   `BuildConfig.SUPABASE_URL`, telle que le conteneur la reçoit. **Et non la constante du
 *   projet** : voir le KDoc du fichier pour ce que cette confusion coûterait.
 */
class SupabaseDailyContentMediaSource(
    private val client: SupabaseClient,
    projectUrl: String,
) : DailyContentMediaSource {

    override val root: String = DailyContentMedia.publicRoot(projectUrl)

    override suspend fun signedUrl(path: String): String =
        client.storage.from(DailyContentMedia.BUCKET)
            .createSignedUrl(path, DailyContentMedia.SIGNED_URL_SECONDS.seconds)
}
