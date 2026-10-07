package com.msoumaya.deepseekandroid.core.data.remote

import com.msoumaya.deepseekandroid.core.domain.Recitations
import com.msoumaya.deepseekandroid.core.model.GeneralFeedback
import com.msoumaya.deepseekandroid.core.model.RecitationKind
import com.msoumaya.deepseekandroid.core.model.RemoteRecitation
import com.msoumaya.deepseekandroid.core.model.VerseCorrection
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// La lecture des récitations
// ---------------------------------------------------------------------------
// Portage de la moitié lecture de `src/services/recitations.ts` : la liste distante, les
// corrections de versets, les retours généraux, l'adresse signée, et la suppression.
//
// **Pourquoi un second contrat, et pas une méthode de plus sur `RecitationUploader`.** Les deux
// moitiés ne servent pas la même chose : le dépôt sert la **file**, qui doit marcher sans qu'un
// écran soit ouvert ; la lecture sert les **écrans**, qui n'existent pas encore tous. Les réunir
// obligerait la file à porter une surface qu'elle n'appelle jamais, et un dépôt qui implémente
// `RecitationUploader` seul — c'est le cas de la doublure du banc — se verrait forcé d'écrire cinq
// méthodes vides. La frontière est d'ailleurs celle du fichier d'origine lui-même.
//
// **Ce qui n'est pas déclaré ici, et pourquoi.** Les gestes d'administration — `publishCorrections`,
// `finalizeRecitationCorrection`, `publishGeneralFeedback`, `adminCorrectionIds`, et la lecture
// `forAdmin` — restent dehors, pour la raison déjà écrite pour le Quiz : les politiques de
// `supabase/recitations.sql` les réservent à `private.is_app_admin()`, et l'administration reste
// sur le web. Un client qui les porterait ne pourrait rien en faire.
//
// **Les noms d'arguments sont ceux de la table, en `snake_case`**, comme pour la file : le client
// Postgrest est configuré sans conversion de propriété, donc les `@SerialName` ci-dessous sont le
// seul endroit où la correspondance est écrite.
//
// **Les signatures du SDK ont été relevées dans l'artefact livré**, et non devinées —
// `storage-kt-android-3.8.0.aar`, classe `BucketApi`, et `postgrest-kt-android-3.8.0.aar`,
// classe `PostgrestQueryBuilder` :
//
//   - `createSignedUrl(path: String, expiresIn: Duration, …): String` — le paramètre est une
//     `Duration`, et non un nombre de secondes : le nom compilé est manglé (`createSignedUrl-dWUq8MI`),
//     ce qui signale un paramètre de type *value class*, et la vue `javap` le donne en `long`.
//     L'original, lui, passe `600` — **des secondes**. Écrire `600` ici ne compilerait pas ; écrire
//     `600.seconds` est la traduction, et c'est la constante du domaine qui porte le nombre ;
//   - `delete(vararg paths: String)`, sur le compartiment ;
//   - `select(columns, builder)`, `order(colonne, Order.DESCENDING)`, `limit(n)`,
//     `decodeList<T>()` — la forme déjà employée par `SupabaseSocialSource`.
// ---------------------------------------------------------------------------

/** Compartiment de stockage des récitations, tel que `recitations.sql` le déclare. */
private const val BUCKET_RECITATIONS = "recitations"

/** Table des récitations. */
private const val TABLE_RECITATIONS = "recitations"

/** Table des corrections de versets. */
private const val TABLE_CORRECTIONS = "recitation_corrections"

/** Table des retours généraux. */
private const val TABLE_FEEDBACK = "recitation_feedback"

/**
 * Ce que les écrans demandent au serveur à propos des récitations.
 *
 * L'interface existe pour la même raison que celle du dépôt : la doublure permet d'éprouver ce qui
 * décide — quel état s'affiche, ce qui reste à l'écran quand une lecture échoue — sans réseau, sans
 * compartiment et sans appareil.
 *
 * **L'identifiant du compte est un paramètre de [listMine]**, comme pour l'espace social. La
 * politique de la table le vérifie de toute façon (`auth.uid()`), mais la requête, elle, doit
 * **dire** ce qu'elle veut : sans filtre, elle demanderait au serveur de choisir, et une
 * politique mal écrite rendrait alors les récitations de tout le monde.
 */
interface RecitationSource {

    /**
     * Les récitations de [userId], de la plus récente à la plus ancienne, bornées à
     * [Recitations.REMOTE_LIST_LIMIT].
     */
    suspend fun listMine(userId: String): List<RemoteRecitation>

    /**
     * Les corrections de versets d'une récitation, de la plus récente à la plus ancienne.
     *
     * Aucun filtre par compte : une correction appartient à la récitation, et c'est la politique
     * de la table qui décide qui peut la lire — le propriétaire, l'administrateur, et les amis à
     * qui la récitation a été partagée.
     */
    suspend fun corrections(recitationId: String): List<VerseCorrection>

    /** Les retours généraux d'une récitation, de la plus récente à la plus ancienne. */
    suspend fun generalFeedback(recitationId: String): List<GeneralFeedback>

    /**
     * Une adresse d'écoute, signée et datée, pour un fichier du compartiment.
     *
     * Le compartiment est **privé** : le chemin seul ne donne accès à rien. L'adresse rendue
     * n'est valable que [Recitations.SIGNED_URL_SECONDS] secondes, et c'est ce qui permet de
     * l'écrire dans un lecteur sans donner un accès permanent au fichier.
     */
    suspend fun signedAudioUrl(path: String): String

    /**
     * Efface le fichier **puis** la ligne.
     *
     * L'ordre est celui de l'original, et il compte : la ligne est ce qui **désigne** le fichier.
     * Effacer la ligne d'abord laisserait, si le second appel échoue, des octets dans le
     * compartiment que plus rien ne nomme — invisibles, et impossibles à supprimer depuis
     * l'application.
     *
     * La ligne est filtrée par `id` **et** par `user_id` : c'est la même garde que celle de
     * l'original, et elle empêche qu'un identifiant reçu d'ailleurs fasse supprimer la ligne d'un
     * autre — le compte, lui, est celui de [recitation].
     */
    suspend fun deleteRemote(recitation: RemoteRecitation)
}

/**
 * Implémentation réelle, adossée au projet Supabase partagé avec le client React Native.
 *
 * @param client client déjà construit. Il n'est **jamais** construit ici : hors configuration, le
 *   conteneur ne crée pas de source du tout, et aucun appel ne part sans projet.
 */
class SupabaseRecitationSource(private val client: SupabaseClient) : RecitationSource {

    override suspend fun listMine(userId: String): List<RemoteRecitation> =
        client.postgrest.from(TABLE_RECITATIONS)
            .select(
                columns = Columns.list(
                    "id",
                    "user_id",
                    "start_verse_id",
                    "end_verse_id",
                    "duration_ms",
                    "storage_path",
                    "created_at",
                    "listened_at",
                    "recording_type",
                    "invocation_id",
                ),
            ) {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
                limit(Recitations.REMOTE_LIST_LIMIT.toLong())
            }
            .decodeList<RecitationReadRow>()
            .map { it.toModel() }

    override suspend fun corrections(recitationId: String): List<VerseCorrection> =
        client.postgrest.from(TABLE_CORRECTIONS)
            .select(
                columns = Columns.list(
                    "id",
                    "recitation_id",
                    "verse_id",
                    "comment",
                    "voice_path",
                    "created_at",
                    "resolved_at",
                ),
            ) {
                filter { eq("recitation_id", recitationId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<CorrectionReadRow>()
            .map { it.toModel() }

    override suspend fun generalFeedback(recitationId: String): List<GeneralFeedback> =
        client.postgrest.from(TABLE_FEEDBACK)
            .select(
                columns = Columns.list(
                    "id",
                    "recitation_id",
                    "comment",
                    "voice_path",
                    "created_at",
                ),
            ) {
                filter { eq("recitation_id", recitationId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<FeedbackReadRow>()
            .map { it.toModel() }

    override suspend fun signedAudioUrl(path: String): String =
        client.storage.from(BUCKET_RECITATIONS)
            .createSignedUrl(path, Recitations.SIGNED_URL_SECONDS.seconds)

    override suspend fun deleteRemote(recitation: RemoteRecitation) {
        client.storage.from(BUCKET_RECITATIONS).delete(recitation.storagePath)
        client.postgrest.from(TABLE_RECITATIONS).delete {
            filter {
                eq("id", recitation.id)
                eq("user_id", recitation.userId)
            }
        }
    }
}

/**
 * Une ligne de `recitations`, telle que la table la porte.
 *
 * `invocation_snapshot` n'est **pas** demandé à la requête, et ce n'est pas un oubli : c'est un
 * document entier (`daily_contents`) que le portage ne modèle pas encore. Le demander obligerait à
 * décrire sa forme ici, pour un écran qui ne l'affiche pas. Le titre d'une invocation se replie
 * donc sur le mot de la liste — l'écart est celui du modèle, pas celui de la requête.
 */
@Serializable
internal data class RecitationReadRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("start_verse_id") val startVerseId: Int? = null,
    @SerialName("end_verse_id") val endVerseId: Int? = null,
    @SerialName("duration_ms") val durationMs: Long,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("listened_at") val listenedAt: String? = null,
    /**
     * La nature, telle que le serveur la nomme (`quran` / `invocation`).
     *
     * Elle a une valeur par défaut parce que la colonne a été ajoutée après coup : une ligne
     * écrite avant n'en porte pas, et la replier sur un passage du Coran est exactement la règle
     * du client d'origine (`item.recording_type ?? 'quran'`).
     */
    @SerialName("recording_type") val recordingType: RecitationKind = RecitationKind.QURAN,
    @SerialName("invocation_id") val invocationId: String? = null,
) {
    fun toModel(): RemoteRecitation = RemoteRecitation(
        id = id,
        userId = userId,
        startVerseId = startVerseId,
        endVerseId = endVerseId,
        durationMs = durationMs,
        storagePath = storagePath,
        createdAt = createdAt,
        listenedAt = listenedAt,
        kind = recordingType,
        invocationId = invocationId,
    )
}

/** Une ligne de `recitation_corrections`. */
@Serializable
internal data class CorrectionReadRow(
    val id: String,
    @SerialName("recitation_id") val recitationId: String,
    @SerialName("verse_id") val verseId: Int,
    val comment: String? = null,
    @SerialName("voice_path") val voicePath: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("resolved_at") val resolvedAt: String? = null,
) {
    fun toModel(): VerseCorrection = VerseCorrection(
        id = id,
        recitationId = recitationId,
        verseId = verseId,
        comment = comment,
        voicePath = voicePath,
        createdAt = createdAt,
        resolvedAt = resolvedAt,
    )
}

/** Une ligne de `recitation_feedback`. */
@Serializable
internal data class FeedbackReadRow(
    val id: String,
    @SerialName("recitation_id") val recitationId: String,
    val comment: String? = null,
    @SerialName("voice_path") val voicePath: String? = null,
    @SerialName("created_at") val createdAt: String,
) {
    fun toModel(): GeneralFeedback = GeneralFeedback(
        id = id,
        recitationId = recitationId,
        comment = comment,
        voicePath = voicePath,
        createdAt = createdAt,
    )
}
