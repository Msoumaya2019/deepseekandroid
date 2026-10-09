package com.msoumaya.deepseekandroid.core.data.remote

import com.msoumaya.deepseekandroid.core.domain.ProblemReports
import com.msoumaya.deepseekandroid.core.model.ProblemReportType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Le dépôt d'un signalement de problème
// ---------------------------------------------------------------------------
// Portage de la moitié « envoi » de `src/services/problemReports.ts` : déposer la capture dans le
// compartiment, écrire la ligne, puis **relire la ligne** avant de retirer l'entrée de la file.
//
// **Pourquoi ce contrat est étroit.** Le fichier d'origine porte aussi trois fonctions
// d'**administration** — `adminProblemReports`, `resolveProblemReport`, `problemScreenshotUrl` —,
// et elles ne sont pas déclarées ici. Ce n'est pas un oubli : les politiques de
// `supabase/problem-reports.sql` réservent la lecture de tous les signalements à
// `private.is_app_admin()` (ligne 16) et la mise à jour à la même garde (ligne 20). C'est la ligne
// déjà écrite pour le Quiz et pour les récitations — « l'administration reste sur le web » —,
// appliquée à la même situation. Un client qui les porterait ne pourrait rien en faire.
//
// **Ce qui reste, et qui n'est pas de l'administration.** Une personne peut lire **ses** propres
// signalements : la politique `problem_reports_read` le permet (`user_id=auth.uid()`). L'écran de
// l'original ne les affiche pas — il ne montre qu'un formulaire et une confirmation —, et cette
// lecture n'est donc pas portée : ce serait un écran qui n'existe pas, comme la borne de 200 de
// `Recitations.REMOTE_LIST_LIMIT`.
//
// **Les noms d'arguments sont ceux de la table, en `snake_case`.** Le client Postgrest est
// configuré sans conversion de propriété ; les `@SerialName` ci-dessous sont donc le seul endroit
// où la correspondance est écrite, comme pour les lignes sociales et celles des récitations.
//
// **Les signatures du SDK ont été relevées dans l'artefact livré**, et non devinées —
// `storage-kt-android-3.8.0.aar`, classe `BucketApi`, et `postgrest-kt-android-3.8.0.aar`,
// classes `PostgrestQueryBuilder` et `PostgrestResult` :
//
//   - `upload(path: String, bytes: ByteArray, options: UploadOptionBuilder.() -> Unit)`, avec
//     `contentType` et `upsert` ;
//   - `upsert(row) { onConflict: String?; ignoreDuplicates: Boolean }` ;
//   - `select(columns, builder)` puis `decodeSingleOrNull<T>()` — relevé par `javap` sur
//     `PostgrestResult`, qui porte bien `decodeSingleOrNull`.
// ---------------------------------------------------------------------------

/** Compartiment des captures, tel que `problem-reports.sql` le déclare. */
private const val BUCKET_SCREENSHOTS = "problem-report-screenshots"

/** Table des signalements. */
private const val TABLE_REPORTS = "app_problem_reports"

/**
 * La ligne écrite dans `app_problem_reports`.
 *
 * **Les colonnes sont exactement celles du schéma**, et trois d'entre elles sont contraintes :
 * `type` par un `check` sur cinq valeurs, `description` par une longueur, `screenshot_path` par
 * deux formes possibles. Le modèle ne peut donc pas se permettre d'écrire autre chose, et les
 * règles qui produisent ces valeurs vivent dans [ProblemReports].
 *
 * **`status` est écrit alors que la colonne a une valeur par défaut.** Le `with check` de la
 * politique d'insertion exige `status='open'` ; l'écrire rend cette exigence visible à l'endroit
 * où la ligne se forme, au lieu de dépendre d'un défaut que seul le serveur connaît.
 *
 * **Les champs nuls sont omis du corps**, comme pour les récitations : `AppJson` porte
 * `explicitNulls = false`, donc un `screenshot_path` nul n'est pas envoyé, et la colonne prend sa
 * valeur par défaut — nulle. C'est la même chose qu'un envoi explicite de `null`, sans dépendre de
 * ce que PostgREST fait d'un `null` écrit à la main.
 */
@Serializable
data class ProblemReportRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: ProblemReportType,
    val description: String,
    @SerialName("screenshot_path") val screenshotPath: String? = null,
    @SerialName("app_version") val appVersion: String,
    val platform: String,
    @SerialName("created_at") val createdAt: String,
    val status: String = ProblemReports.STATUS_OPEN,
)

/**
 * Ce que la file demande au serveur : déposer une capture, écrire une ligne, la relire.
 *
 * L'interface existe pour la même raison que celles du Quiz, de l'espace social et des
 * récitations : la file porte des règles qui ne se voient pas à l'écran — ne pas renvoyer
 * éternellement une capture déjà déposée, ne pas retirer une entrée avant que le serveur ne rende
 * la ligne, ne pas écrire une ligne vers une capture qui n'existe plus. Ces règles se mesurent en
 * substituant une doublure, sans réseau, sans compartiment et sans appareil.
 *
 * **Aucune méthode ne prend l'identifiant du compte courant en premier argument.** Les politiques
 * du compartiment et de la table comparent le chemin et `user_id` à `auth.uid()` ; le serveur
 * tranche, et le passer laisserait croire que le client décide pour qui il dépose. [confirm] fait
 * exception, et pour une raison inverse : elle **relit une ligne précise**, et le couple
 * `(id, user_id)` est ce qui dit laquelle — c'est le filtre de l'original, et il empêche qu'un
 * identifiant reçu d'ailleurs fasse confirmer le signalement d'un autre.
 */
interface ProblemReportSender {

    /**
     * Dépose [bytes] au chemin [path] du compartiment.
     *
     * Le dépôt **n'écrase pas** : une capture déjà présente fait échouer l'appel. C'est le
     * compartiment qui le refuse, et c'est voulu — voir
     * [ProblemReports.uploadFailureIsBenign], qui décide si ce refus arrête la file ou la laisse
     * continuer.
     *
     * @param path chemin dans le compartiment, de la forme `userId/id.jpg`.
     * @param mimeType type MIME réel des octets. Nommé ainsi et non `contentType` parce que
     *   l'option du client de stockage porte ce dernier nom : deux `contentType` dans la même
     *   expression se départageraient par une règle de portée, ce qui n'est pas une façon
     *   d'écrire un contrat.
     */
    suspend fun uploadScreenshot(path: String, bytes: ByteArray, mimeType: String)

    /**
     * Écrit la ligne du signalement, **sans écraser une ligne existante**.
     *
     * L'original demande `ignoreDuplicates: true`, c'est-à-dire `ON CONFLICT DO NOTHING`. C'est ce
     * qui rend un renvoi sans effet : la ligne existe déjà, et rien n'est réécrit — donc rien de ce
     * que l'administrateur a pu changer entre-temps — le statut `resolved`, par exemple — n'est
     * perdu. Sans cette option, un signalement rejoué après avoir été traité le remettrait à
     * `open`, et le travail de l'administrateur serait défait par le client.
     */
    suspend fun insert(row: ProblemReportRow)

    /**
     * Relit la ligne pour vérifier qu'elle est bien arrivée.
     *
     * Rend `true` si le serveur la rend, `false` s'il ne la rend pas. **Lève** quand la lecture
     * elle-même échoue, et la distinction est celle de l'original : une panne de lecture n'est pas
     * une absence, et les confondre ferait retirer l'entrée de la file sur un doute — c'est-à-dire
     * perdre un signalement que le serveur n'a peut-être pas.
     */
    suspend fun confirm(id: String, userId: String): Boolean
}

/**
 * Implémentation réelle, adossée au projet Supabase partagé avec le client React Native.
 *
 * @param client client déjà construit. Il n'est **jamais** construit ici : hors configuration, le
 *   conteneur ne crée pas d'expéditeur du tout, et aucun appel ne part sans projet.
 */
class SupabaseProblemReportSender(private val client: SupabaseClient) : ProblemReportSender {

    /**
     * `upsert = false` est écrit alors que c'est déjà le défaut : c'est l'option que l'original
     * passe, et la lire ici évite d'avoir à se demander si le défaut a changé.
     */
    override suspend fun uploadScreenshot(path: String, bytes: ByteArray, mimeType: String) {
        client.storage.from(BUCKET_SCREENSHOTS).upload(path, bytes) {
            upsert = false
            contentType = ContentType.parse(mimeType)
        }
    }

    override suspend fun insert(row: ProblemReportRow) {
        client.postgrest.from(TABLE_REPORTS).upsert(row) {
            onConflict = "id"
            ignoreDuplicates = true
        }
    }

    /**
     * `maybeSingle()` de l'original, et il faut **deux** filtres.
     *
     * Le filtre par `id` seul suffirait à trouver la ligne — c'est la clé primaire —, mais
     * l'original filtre aussi par `user_id`, et c'est cette seconde égalité qui rend la
     * confirmation **propre au compte** : la politique de lecture l'impose de toute façon
     * (`user_id=auth.uid()`), et l'écrire ici fait que la requête dit ce qu'elle veut au lieu de
     * compter sur la politique pour le lui dire.
     *
     * `decodeSingleOrNull` et non `decodeSingle` : une lecture qui ne trouve rien n'est pas une
     * panne, c'est précisément le cas que cette méthode existe pour distinguer. Un `decodeSingle`
     * lèverait, et la file confondrait « pas encore visible » avec « panne de lecture ».
     */
    override suspend fun confirm(id: String, userId: String): Boolean =
        client.postgrest.from(TABLE_REPORTS)
            .select(columns = Columns.list("id")) {
                filter {
                    eq("id", id)
                    eq("user_id", userId)
                }
                limit(1)
            }
            .decodeSingleOrNull<IdRow>() != null

    /** La seule colonne que la confirmation demande. Une ligne entière serait du poids inutile. */
    @Serializable
    private data class IdRow(val id: String)
}
