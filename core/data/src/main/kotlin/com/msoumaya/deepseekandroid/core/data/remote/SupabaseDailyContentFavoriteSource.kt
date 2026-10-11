package com.msoumaya.deepseekandroid.core.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

// ---------------------------------------------------------------------------
// Implémentation Supabase des favoris
// ---------------------------------------------------------------------------
// Trois requêtes, et rien d'autre : ce fichier ne décide d'aucune règle. Le cache, la fenêtre et
// la voie du visiteur sont dans `DailyContentFavoritesRepository`, et la seule décision écrite ici
// est celle de la **clé de conflit**, commentée sur `add`.
//
// **Les colonnes sont nommées, là où l'original écrit `select('content_id')`.** C'est la même
// colonne ; la nommer par `Columns.list` est la forme qu'emploient les quatre autres sources de ce
// module, et elle évite d'avoir une requête écrite autrement que ses voisines pour la même chose.
//
// **Les signatures du SDK sont celles déjà employées par les sources voisines** — `select` +
// `filter` + `decodeList` comme `SupabaseRecitationSource.listMine`, `upsert` + `onConflict` comme
// `SupabaseProblemReportSender.insert`, `delete` + `filter` comme
// `SupabaseRecitationSource.deleteRemote`. Aucune n'est devinée ici : elles ont été relevées dans
// `postgrest-kt-android-3.8.0.aar`, classe `PostgrestQueryBuilder`.
// ---------------------------------------------------------------------------

/** Table des favoris, telle que `supabase/daily-contents.sql` la déclare. */
private const val TABLE_FAVORITES = "content_favorites"

/**
 * Implémentation réelle, adossée au projet Supabase partagé avec le client React Native.
 *
 * @param client client déjà construit. Il n'est **jamais** construit ici : hors configuration, le
 *   conteneur ne crée pas de source du tout, et aucune de ces requêtes ne peut donc partir sans
 *   projet — plutôt que d'échouer une par une sur une adresse vide.
 */
class SupabaseDailyContentFavoriteSource(
    private val client: SupabaseClient,
) : DailyContentFavoriteSource {

    override suspend fun list(userId: String): Set<String> =
        client.postgrest.from(TABLE_FAVORITES)
            .select(columns = Columns.list("content_id")) {
                filter { eq("user_id", userId) }
            }
            .decodeList<FavoriteReadRow>()
            .map { it.contentId }
            .toSet()

    /**
     * `onConflict` est écrit, alors que l'original ne le passe pas.
     *
     * L'original écrit `upsert({user_id, content_id})` sans option, et `postgrest-js` envoie alors
     * `Prefer: resolution=merge-duplicates` **sans** `on_conflict` : c'est la **clé primaire** que
     * PostgREST prend pour cible. Ici la clé primaire est un couple — `primary key(user_id,
     * content_id)` —, donc la cible est ce couple, et l'écrire le dit. S'en remettre au défaut
     * marcherait aussi ; le nommer évite qu'un lecteur se demande si le couple est bien la cible,
     * et c'est la même raison qui fait écrire `upsert = false` dans `SupabaseProblemReportSender`.
     *
     * `ignoreDuplicates` n'est **pas** écrit : son défaut est `false`, c'est-à-dire la fusion, et
     * c'est exactement ce que l'original obtient. Le passer à `true` ferait un
     * `ON CONFLICT DO NOTHING` — sans conséquence observable sur une table qui ne porte que ses
     * deux colonnes de clé, mais ce ne serait plus le geste de l'original.
     */
    override suspend fun add(userId: String, contentId: String) {
        client.postgrest.from(TABLE_FAVORITES)
            .upsert(FavoriteWriteRow(userId = userId, contentId = contentId)) {
                onConflict = "user_id,content_id"
            }
    }

    override suspend fun remove(userId: String, contentId: String) {
        client.postgrest.from(TABLE_FAVORITES).delete {
            filter {
                eq("user_id", userId)
                eq("content_id", contentId)
            }
        }
    }
}
