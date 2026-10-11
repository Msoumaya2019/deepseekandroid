package com.msoumaya.deepseekandroid.core.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Les favoris d'un contenu du jour
// ---------------------------------------------------------------------------
// Portage de `favorites()` et `setFavorite()` de `src/services/dailyContents.ts` — la partie qui
// parle au serveur. La fenêtre de trente secondes, le dédoublonnage des lectures concurrentes et
// la voie du visiteur vivent dans `DailyContentFavoritesRepository` ; ici il n'y a que « quelle
// table, quelles colonnes, quel filtre ».
//
// **Ce contrat porte une table par compte, et c'est le seul de ce module.** `content_favorites`
// est la seule table des contenus du jour que `anon` ne peut pas lire : le `grant` de
// `supabase/daily-contents.sql` accorde `select,insert,update,delete` à `authenticated` et **rien**
// à `anon`, et la politique `favorites_own` restreint chaque ligne à `user_id = auth.uid()`. Les
// trois méthodes prennent donc l'identifiant du compte, comme `RecitationSource.listMine` — et
// pour la même raison : la politique le vérifie de toute façon, mais la requête doit **dire** ce
// qu'elle veut. Sans le filtre, elle demanderait au serveur de choisir, et une politique mal
// écrite rendrait alors les favoris de tout le monde.
//
// **Ce qui n'est pas déclaré ici, et pourquoi.** `favoriteContents(ids, type, catégorie, offset)`
// — la lecture paginée des fiches mises en favori — n'est **pas** portée. Elle n'appartient pas à
// la fiche mais à l'**écran de liste**, dont elle porte la fenêtre, le tri et le filtre par
// catégorie : elle arrivera avec lui. La déclarer maintenant serait une surface que rien
// n'appelle, et qu'aucun test ne pourrait couvrir autrement qu'en la singeant.
// ---------------------------------------------------------------------------

/**
 * Ce que la fiche demande au serveur à propos des favoris.
 *
 * Trois méthodes, et chacune correspond à une écriture ou à une lecture de l'original. Elles
 * rendent le **résultat** du geste, ou rien : c'est le dépôt qui tient le cache et qui décide
 * quand une relecture a lieu, et une source qui garderait son propre état aurait deux mémoires
 * pour une seule vérité.
 */
interface DailyContentFavoriteSource {

    /**
     * Les identifiants des contenus mis en favori par [userId].
     *
     * **Aucun tri n'est demandé**, et c'est délibéré : l'original n'en demande pas non plus, et
     * rien ne lit l'ordre de cette liste — le seul usage est le test d'appartenance. Ajouter un
     * `order` ici inventerait une règle que le serveur ne promettait pas, et ferait payer un tri
     * pour une information que personne ne regarde.
     */
    suspend fun list(userId: String): Set<String>

    /**
     * Ajoute un favori, **sans échouer s'il existe déjà**.
     *
     * L'original écrit `upsert({user_id, content_id})`, c'est-à-dire un `ON CONFLICT DO UPDATE`
     * sur la clé primaire `(user_id, content_id)`. Le geste est donc idempotent, et c'est ce qui
     * le rend sûr : l'écran qui appuie sur le cœur travaille sur une liste qui peut avoir jusqu'à
     * trente secondes de retard, donc sur une décision qui peut être déjà prise.
     */
    suspend fun add(userId: String, contentId: String)

    /**
     * Retire un favori, **sans échouer s'il n'existe pas**.
     *
     * Le filtre porte sur le couple `(user_id, content_id)` et non sur `content_id` seul : c'est
     * la clé primaire, et c'est ce qui garantit que le geste ne touche que la ligne de ce compte.
     * Un `delete` filtré sur le seul `content_id` serait refusé par la politique — mais il
     * demanderait au serveur de refuser, au lieu de dire ce qu'il veut.
     */
    suspend fun remove(userId: String, contentId: String)
}

/**
 * Une ligne de `content_favorites`, réduite à la colonne que la lecture demande.
 *
 * **`content_id` a bien besoin de son `@SerialName`** : le client Postgrest est configuré sans
 * conversion de propriété, donc un champ écrit `contentId` désignerait une colonne qui n'existe
 * pas. PostgREST ne s'en plaindrait pas — il rendrait la colonne demandée, la clé serait ignorée
 * par `AppJson`, et la propriété tomberait sur sa valeur par défaut. Comme elle n'en a pas, la
 * désérialisation lèverait ici ; mais c'est la même faute qui, sur un champ à défaut, ferait
 * disparaître des favoris en silence. `DailyContentSourceShapeTest` la mesure sur un vrai
 * document JSON.
 */
@Serializable
internal data class FavoriteReadRow(
    @SerialName("content_id") val contentId: String,
)

/**
 * La ligne écrite dans `content_favorites`.
 *
 * Deux colonnes, et c'est tout ce que la table porte en plus de `created_at`, qui a une valeur par
 * défaut côté serveur. `user_id` est écrit alors que la politique d'insertion l'exige
 * (`with check(user_id = auth.uid())`) : l'écrire rend cette exigence visible à l'endroit où la
 * ligne se forme, au lieu de dépendre d'un défaut que le serveur ne connaît pas.
 */
@Serializable
internal data class FavoriteWriteRow(
    @SerialName("user_id") val userId: String,
    @SerialName("content_id") val contentId: String,
)
