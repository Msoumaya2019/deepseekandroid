package com.msoumaya.deepseekandroid.core.data.remote

import com.msoumaya.deepseekandroid.core.model.RecitationKind
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Le dépôt d'une récitation
// ---------------------------------------------------------------------------
// Portage de la boucle de `syncPendingRecitations()` de `src/services/recitations.ts` : déposer
// les octets dans le compartiment, puis écrire la ligne.
//
// **Pourquoi ce contrat est étroit.** La moitié lecture de `recitations.ts` — la liste distante,
// les corrections, les retours, l'adresse signée, la suppression — sert des **écrans**, et elle
// sera portée avec eux. Ici il n'y a que ce dont la file a besoin, et c'est exactement la
// frontière que le fichier d'origine trace lui-même : ses soixante-six premières lignes sont le
// registre local et son dépôt, les cent suivantes sont la lecture et les gestes.
//
// **Ce qui n'est pas ici, et pourquoi.** Les gestes d'administration — `publishCorrections`,
// `finalizeRecitationCorrection`, `publishGeneralFeedback`, `adminCorrectionIds` — ne sont **pas**
// déclarés, et ce n'est pas un oubli de portage : les politiques de `supabase/recitations.sql`
// les réservent à `private.is_app_admin()` (lignes 60-67, 73-74), et `finalize_recitation_correction`
// lève « Accès administrateur refusé » pour tout autre compte
// (`supabase/notification-corrections.sql`, ligne 50). C'est la ligne déjà écrite pour le Quiz —
// « l'administration reste sur le web » —, appliquée à la même situation.
//
// **Les noms d'arguments sont ceux de la table, en `snake_case`.** Le client Postgrest est
// configuré sans conversion de propriété ; les `@SerialName` ci-dessous sont donc le seul endroit
// où la correspondance est écrite, comme pour les lignes sociales.
//
// **`invocation_snapshot` n'est pas envoyé, et c'est mesuré.** Le déclencheur
// `private.validate_invocation_recording` (`supabase/daily-contents.sql`, ligne 66) **écrit**
// `new.invocation_snapshot` à partir de `daily_contents` à chaque insertion d'une invocation :
// la valeur venue du client est écrasée, quelle qu'elle soit. L'envoyer ne serait pas faux, ce
// serait inutile — et laisser croire que le client décide de l'instantané. Le serveur le compose,
// et c'est lui qui refuse une invocation indisponible (ligne 67).
// ---------------------------------------------------------------------------

/** Compartiment de stockage des récitations, tel que `recitations.sql` le déclare. */
private const val BUCKET_RECITATIONS = "recitations"

/** Table des récitations. */
private const val TABLE_RECITATIONS = "recitations"

/**
 * La ligne écrite dans `recitations` quand une récitation locale part.
 *
 * **Les bornes sont nullables, et c'est la contrainte de la table qui le dit** : depuis
 * `daily-contents.sql` (lignes 54-62), `start_verse_id` et `end_verse_id` ne sont plus
 * obligatoires, et `recitations_passage_type` impose exactement deux formes — un passage du Coran
 * avec ses deux bornes et **sans** invocation, ou une invocation avec ses bornes **nulles** et un
 * instantané. Le modèle ne peut donc pas se permettre d'écrire un intervalle pour une invocation.
 *
 * **Les champs nuls sont omis du corps, et c'est ce qu'il faut.** `AppJson` porte
 * `explicitNulls = false` : un `invocation_id` nul n'est pas envoyé, donc la colonne prend sa
 * valeur par défaut — nulle. C'est la même chose qu'un envoi explicite de `null`, sans la
 * dépendance à ce que PostgREST fait d'un `null` écrit à la main.
 */
@Serializable
data class RecitationUploadRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("start_verse_id") val startVerseId: Int? = null,
    @SerialName("end_verse_id") val endVerseId: Int? = null,
    @SerialName("recording_type") val recordingType: RecitationKind,
    @SerialName("invocation_id") val invocationId: String? = null,
    @SerialName("duration_ms") val durationMs: Long,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("created_at") val createdAt: String,
)

/**
 * Ce que la file demande au serveur : déposer un fichier, écrire une ligne.
 *
 * L'interface existe pour la même raison que celles du Quiz et de l'espace social : la file porte
 * des règles qui ne se voient pas à l'écran — ne pas renvoyer éternellement un fichier déjà
 * déposé, ne pas marquer « déposée » une récitation dont l'audio a disparu de l'appareil, ne pas
 * laisser une panne d'un élément arrêter les suivants. Ces règles se mesurent en substituant une
 * doublure, sans réseau, sans compartiment et sans appareil.
 *
 * **Aucune méthode ne prend l'identifiant du compte courant.** Les politiques du compartiment et
 * de la table comparent le chemin et `user_id` à `auth.uid()` ; le serveur tranche, et le passer
 * laisserait croire que le client décide pour qui il dépose.
 */
interface RecitationUploader {

    /**
     * Dépose [bytes] au chemin [path] du compartiment.
     *
     * Le dépôt **n'écrase pas** : un fichier déjà présent fait échouer l'appel. C'est le
     * compartiment qui le refuse, et c'est voulu — voir [com.msoumaya.deepseekandroid.core.domain.Recitations.uploadFailureIsBenign],
     * qui décide si ce refus arrête la synchronisation ou la laisse continuer.
     *
     * @param path chemin dans le compartiment, de la forme `userId/id.m4a`.
     * @param mimeType type MIME réel des octets. Nommé ainsi et non `contentType` parce que
     *   l'option du client de stockage porte ce dernier nom : deux `contentType` dans la même
     *   expression se départageraient par une règle de portée, ce qui n'est pas une façon
     *   d'écrire un contrat.
     */
    suspend fun upload(path: String, bytes: ByteArray, mimeType: String)

    /**
     * Écrit la ligne de la récitation, **sans écraser une ligne existante**.
     *
     * L'original demande `ignoreDuplicates: true`, c'est-à-dire `ON CONFLICT DO NOTHING`. C'est ce
     * qui rend un renvoi sans effet : la ligne existe déjà, et rien n'est réécrit — donc rien de
     * ce que le serveur a pu corriger entre-temps n'est perdu.
     */
    suspend fun upsert(row: RecitationUploadRow)
}

/**
 * Implémentation réelle, adossée au projet Supabase partagé avec le client React Native.
 *
 * @param client client déjà construit. Il n'est **jamais** construit ici : hors configuration, le
 *   conteneur ne crée pas de déposant du tout, et aucun appel ne part sans projet.
 */
class SupabaseRecitationUploader(private val client: SupabaseClient) : RecitationUploader {

    /**
     * Les signatures employées ici ont été **relevées dans l'artefact livré** —
     * `storage-kt-android-3.8.0.aar`, classe `BucketApi`, et `postgrest-kt-android-3.8.0.aar`,
     * classe `UpsertRequestBuilder` —, et non devinées :
     *
     *   - `upload(path: String, bytes: ByteArray, options: UploadOptionBuilder.() -> Unit)`
     *   - `UploadOptionBuilder.contentType: io.ktor.http.ContentType`, `upsert: Boolean` (faux par
     *     défaut, ce que l'original demande explicitement)
     *   - `PostgrestQueryBuilder.upsert(row) { onConflict: String?; ignoreDuplicates: Boolean }`
     *
     * `upsert = false` est écrit alors que c'est déjà le défaut : c'est l'option que l'original
     * passe, et la lire ici évite d'avoir à se demander si le défaut a changé.
     */
    override suspend fun upload(path: String, bytes: ByteArray, mimeType: String) {
        client.storage.from(BUCKET_RECITATIONS).upload(path, bytes) {
            upsert = false
            contentType = ContentType.parse(mimeType)
        }
    }

    override suspend fun upsert(row: RecitationUploadRow) {
        client.postgrest.from(TABLE_RECITATIONS).upsert(row) {
            onConflict = "id"
            ignoreDuplicates = true
        }
    }
}
