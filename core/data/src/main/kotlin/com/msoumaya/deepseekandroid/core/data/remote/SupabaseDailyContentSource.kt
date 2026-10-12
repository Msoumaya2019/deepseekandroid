package com.msoumaya.deepseekandroid.core.data.remote

import com.msoumaya.deepseekandroid.core.domain.DailyContents
import com.msoumaya.deepseekandroid.core.model.ContentCategory
import com.msoumaya.deepseekandroid.core.model.DailyContent
import com.msoumaya.deepseekandroid.core.model.DailyContentType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ---------------------------------------------------------------------------
// Implémentation Supabase des contenus du jour
// ---------------------------------------------------------------------------
// Portage de `src/services/dailyContents.ts`, fonction par fonction. C'est la seule partie du
// dépôt qui touche au réseau, et c'est délibéré : les décisions vivent dans
// `core:domain/DailyContents.kt`, l'ordonnancement dans `DailyContentRepository`, et il ne reste
// ici que « quelle fonction, quels arguments ».
//
// **Les noms d'arguments sont ceux de `supabase/daily-contents.sql`, `p_` compris.** PostgREST
// apparie les arguments d'un appel RPC **par nom** : un `p_date` écrit `date` ne produirait pas
// une erreur de type mais une fonction introuvable, donc un message qui désigne l'appel et non
// l'argument. Le nom est donc recopié de la migration, pas deviné.
//
// **Les colonnes sont nommées une par une, là où l'original écrit `select('*')`.** L'écart est
// volontaire et il est mesurable : `daily_contents` porte `created_by`, `created_at` et
// `updated_at`, qu'aucun écran n'affiche, et une colonne ajoutée demain changerait la forme lue
// sans que rien ne le dise. Nommer les colonnes rend le contrat lisible **ici**, et c'est
// `DailyContentRow` qui le porte — les deux listes ne peuvent pas diverger, puisque la ligne
// n'accepte que ce qu'elle déclare (`AppJson` ignore le reste, mais la requête ne le demande
// pas).
//
// **Aucune garde n'est posée sur les arguments.** Un identifiant vide, un décalage négatif, une
// date mal formée : le serveur les refuse ou les ignore, et les revérifier ici donnerait deux
// vérités pour une seule règle. La seule exception est [DailyContents.PAGE_SIZE], qui n'est pas
// une garde mais **le** nombre de la fenêtre : il décide aussi de l'existence d'une suite à
// l'écran, donc il doit être le même des deux côtés, et il vit dans le domaine.
// ---------------------------------------------------------------------------

/** Table des contenus. */
private const val TABLE_CONTENTS = "daily_contents"

/** Table des catégories. */
private const val TABLE_CATEGORIES = "content_categories"

/** Fonction du projet partagé qui rend le contenu d'une date. */
private const val RPC_FOR_DATE = "daily_content_for_date"

/**
 * Les colonnes de `daily_contents` que ce client lit, dans l'ordre de la table.
 *
 * Elles sont écrites **une seule fois** : les deux lectures — celle du jour et la paginée — les
 * partagent, donc une colonne ajoutée au modèle ne peut pas être demandée par l'une et oubliée
 * par l'autre.
 */
private val COLONNES_CONTENU = Columns.list(
    "id",
    "type",
    "title",
    "arabic_text",
    "phonetic_text",
    "french_text",
    "explanation",
    "source",
    "reference",
    "category_id",
    "audio_url",
    "image_url",
    "is_active",
)

/** Les colonnes de `content_categories` que ce client lit. */
private val COLONNES_CATEGORIE = Columns.list(
    "id",
    "name",
    "icon",
    "type",
    "display_order",
    "is_active",
)

/**
 * Implémentation réelle, adossée au projet Supabase partagé avec le client React Native.
 *
 * @param client client déjà construit. Il n'est **jamais** construit ici : hors configuration, le
 *   conteneur ne crée pas de source du tout, et aucune de ces requêtes ne peut donc partir sans
 *   projet — plutôt que d'échouer une par une sur une adresse vide.
 */
class SupabaseDailyContentSource(private val client: SupabaseClient) : DailyContentSource {

    override suspend fun dayContents(day: String): List<DailyContent> =
        client.postgrest.rpc(RPC_FOR_DATE, buildJsonObject { put("p_date", day) })
            .decodeList<DailyContentRow>()
            .map { it.toModel() }

    override suspend fun categories(): List<ContentCategory> =
        client.postgrest.from(TABLE_CATEGORIES)
            .select(COLONNES_CATEGORIE) {
                // `is_active` d'abord : c'est le filtre qui distingue la lecture publique de la
                // lecture d'administration, et c'est le seul des deux que ce client porte.
                filter { eq("is_active", true) }
                // Les deux tris sont ceux de l'original, dans cet ordre : `display_order` puis
                // `name`. Le second départage les catégories de même rang.
                order("display_order", Order.ASCENDING)
                order("name", Order.ASCENDING)
            }
            .decodeList<ContentCategoryRow>()
            .map { it.toModel() }

    override suspend fun contents(
        type: DailyContentType,
        categoryId: String?,
        offset: Int,
    ): List<DailyContent> =
        client.postgrest.from(TABLE_CONTENTS)
            .select(COLONNES_CONTENU) {
                filter {
                    // `type.key` et non `type` : `eq` écrit la valeur telle quelle dans le filtre
                    // PostgREST, et un `toString()` d'énumération enverrait `REMINDER` — un
                    // littéral que la colonne ne contient pas. La liste serait simplement vide.
                    eq("type", type.key)
                    eq("is_active", true)
                    if (categoryId != null) eq("category_id", categoryId)
                }
                order("created_at", Order.DESCENDING)
                // La fenêtre est **inclusive des deux bornes**, comme `range(offset, offset+29)` :
                // `offset + PAGE_SIZE - 1`. Écrire `offset + PAGE_SIZE` demanderait une entrée de
                // trop, et `pageComplete` conclurait à tort qu'il n'y a pas de suite.
                range(offset.toLong(), (offset + DailyContents.PAGE_SIZE - 1).toLong())
            }
            .decodeList<DailyContentRow>()
            .map { it.toModel() }

    override suspend fun favoriteContents(
        ids: List<String>,
        type: DailyContentType,
        categoryId: String?,
        offset: Int,
    ): List<DailyContent> =
        client.postgrest.from(TABLE_CONTENTS)
            .select(COLONNES_CONTENU) {
                filter {
                    // **`isIn`, et non un `eq` par identifiant.** PostgREST rend `id=in.(…)` en une
                    // seule requête ; boucler donnerait trente appels pour une page, et l'ordre
                    // `created_at` ne serait plus celui du serveur.
                    isIn("id", ids)
                    eq("type", type.key)
                    // **Aucun `eq("is_active", true)` ici, contrairement à `contents`.** C'est la
                    // divergence de l'original, reproduite : un contenu désactivé après avoir été
                    // mis en favori reste dans les favoris de qui l'avait gardé. Le KDoc de
                    // l'interface dit pourquoi, et un test de forme tient l'absence.
                    if (categoryId != null) eq("category_id", categoryId)
                }
                order("created_at", Order.DESCENDING)
                range(offset.toLong(), (offset + DailyContents.PAGE_SIZE - 1).toLong())
            }
            .decodeList<DailyContentRow>()
            .map { it.toModel() }

    override suspend fun content(id: String): DailyContent? =
        client.postgrest.from(TABLE_CONTENTS)
            .select(COLONNES_CONTENU) { filter { eq("id", id) } }
            .decodeSingleOrNull<DailyContentRow>()
            ?.toModel()
}
