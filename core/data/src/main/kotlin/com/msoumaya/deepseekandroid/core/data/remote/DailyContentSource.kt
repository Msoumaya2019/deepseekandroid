package com.msoumaya.deepseekandroid.core.data.remote

import com.msoumaya.deepseekandroid.core.model.ContentCategory
import com.msoumaya.deepseekandroid.core.model.DailyContent
import com.msoumaya.deepseekandroid.core.model.DailyContentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Lecture des contenus du jour
// ---------------------------------------------------------------------------
// Portage de la moitié lecture de `src/services/dailyContents.ts`.
//
// **L'interface existe pour la même raison que celles du Quiz et des récitations.** Le dépôt qui
// la consomme porte des règles qui ne se voient pas à l'écran : publier le document du disque
// avant d'appeler le serveur, refuser de présenter un document d'hier comme celui d'aujourd'hui,
// et ne pas annoncer « aucun contenu » quand la lecture a échoué. Ces règles se mesurent en
// substituant une source en mémoire — sans réseau, sans serveur et sans appareil.
//
// **Aucune méthode ne prend l'identifiant du compte courant.** Les politiques de
// `supabase/daily-contents.sql` ouvrent la lecture à `anon` comme à `authenticated` : un contenu
// du jour est **public**, et le passer laisserait croire que le client décide pour qui il
// interroge. C'est la différence la plus visible avec `RecitationSource`, et elle est mesurée.
//
// **Ce qui n'est pas déclaré ici, et pourquoi.** Les six gestes d'administration — `saveContent`,
// `deleteContent`, `saveCategory`, `deleteCategory`, `scheduleContent`, `removeSchedule` — et la
// fonction `save_daily_content` ne sont **pas** portés. Les politiques les réservent à
// `private.is_app_admin()`, et la ligne est déjà écrite dans `ANDROID_MIGRATION.md`, section 12 :
// « l'administration reste sur le web ». Les déclarer « pour plus tard » entretiendrait une
// surface que rien n'appelle, et qu'aucun test ne pourrait couvrir autrement qu'en la singeant.
// Le paramètre `admin` de `categories()` et `contents()` disparaît avec eux : il ne servait qu'à
// lever le filtre `is_active`, ce que seul l'écran d'administration demandait.
//
// **Les favoris ne sont pas ici non plus.** `content_favorites` est une table **par compte**, et
// elle porte sa propre règle de cache : une fenêtre de trente secondes et un dédoublonnage des
// lectures concurrentes. Ils arrivent avec le geste qui les écrit, et non avec la lecture.
// ---------------------------------------------------------------------------

/**
 * Ce que les écrans demandent au serveur à propos des contenus du jour.
 *
 * Quatre méthodes, et chacune correspond à une fonction exportée de `services/dailyContents.ts`.
 */
interface DailyContentSource {

    /**
     * Le contenu du jour pour [day] : au plus un rappel et une invocation.
     *
     * C'est `daily_content_for_date`, qui applique **deux** règles côté serveur : un contenu
     * **programmé** pour cette date l'emporte, et à défaut le serveur en choisit un par rotation
     * quotidienne déterministe. Le client ne connaît ni la programmation ni la rotation, et il
     * n'a donc rien à en dire — il envoie la date et reçoit ce qui tombe.
     *
     * @param day jour demandé, au format `AAAA-MM-JJ`, dans le fuseau de l'appareil.
     */
    suspend fun dayContents(day: String): List<DailyContent>

    /**
     * Les catégories **actives**, dans l'ordre d'affichage voulu par l'administration.
     *
     * Le tri est fait par le serveur (`display_order`, puis `name`) et n'est pas refait ici : le
     * refaire donnerait deux vérités pour une seule règle, et la seconde ne se verrait que le jour
     * où les deux ordres divergeraient.
     */
    suspend fun categories(): List<ContentCategory>

    /**
     * Une page de contenus d'un type, de la plus récente à la plus ancienne.
     *
     * La fenêtre fait [com.msoumaya.deepseekandroid.core.domain.DailyContents.PAGE_SIZE] entrées à
     * partir de [offset] : c'est le `range(offset, offset+29)` de l'original, et c'est **la même
     * constante** qui décide ici de la borne et, à l'écran, de l'existence d'une suite.
     *
     * @param categoryId catégorie à filtrer, ou `null` pour toutes.
     * @param offset premier contenu de la page, à partir de zéro.
     */
    suspend fun contents(
        type: DailyContentType,
        categoryId: String? = null,
        offset: Int = 0,
    ): List<DailyContent>

    /**
     * Un contenu par son identifiant, ou `null` s'il n'existe pas.
     *
     * La porte d'entrée d'un écran ouvert sur un contenu précis — par une notification, ou depuis
     * la fiche d'une récitation —, quand le contenu demandé n'est pas dans la page déjà chargée.
     */
    suspend fun content(id: String): DailyContent?
}

/**
 * Une ligne de `daily_contents`, telle que la table la porte.
 *
 * **Les `@SerialName` sont le seul endroit où la correspondance est écrite**, comme pour les
 * lignes des récitations : le client Postgrest est configuré sans conversion de propriété, donc
 * `arabicText` ne désignerait pas la colonne `arabic_text`.
 *
 * **La même ligne sert aux deux chemins de lecture.** `daily_content_for_date` rend
 * `select chosen.*` — les colonnes de la table —, et la lecture paginée interroge la table
 * directement : les deux rendent donc exactement la même forme, et en écrire deux serait deux
 * occasions de diverger pour un seul contrat.
 *
 * **`type` et `is_active` portent une valeur par défaut**, comme `recording_type` de la ligne des
 * récitations : `coerceInputValues` d'`AppJson` remplace par la valeur par défaut une valeur
 * d'énumération qu'il ne connaît pas, et sans défaut il lèverait. Le cas est improbable ici — les
 * deux valeurs sont contraintes par un `check` en base —, mais la règle est la même et il n'y a
 * pas de raison de l'écrire autrement.
 */
@Serializable
internal data class DailyContentRow(
    val id: String,
    val type: DailyContentType = DailyContentType.REMINDER,
    val title: String? = null,
    @SerialName("arabic_text") val arabicText: String? = null,
    @SerialName("phonetic_text") val phoneticText: String? = null,
    @SerialName("french_text") val frenchText: String,
    val explanation: String? = null,
    val source: String,
    val reference: String? = null,
    @SerialName("category_id") val categoryId: String,
    @SerialName("audio_url") val audioUrl: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
) {
    fun toModel(): DailyContent = DailyContent(
        id = id,
        type = type,
        title = title,
        arabicText = arabicText,
        phoneticText = phoneticText,
        frenchText = frenchText,
        explanation = explanation,
        source = source,
        reference = reference,
        categoryId = categoryId,
        audioUrl = audioUrl,
        imageUrl = imageUrl,
        isActive = isActive,
    )
}

/**
 * Une ligne de `content_categories`.
 *
 * `display_order` et `is_active` portent une valeur par défaut pour la même raison que ci-dessus :
 * les deux colonnes sont `not null default`, et une ligne écrite avant leur ajout n'en porterait
 * pas — la replier sur `0` et `true` est exactement ce que fait la base.
 */
@Serializable
internal data class ContentCategoryRow(
    val id: String,
    val name: String,
    val icon: String,
    val type: DailyContentType,
    @SerialName("display_order") val displayOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
) {
    fun toModel(): ContentCategory = ContentCategory(
        id = id,
        name = name,
        icon = icon,
        type = type,
        displayOrder = displayOrder,
        isActive = isActive,
    )
}
