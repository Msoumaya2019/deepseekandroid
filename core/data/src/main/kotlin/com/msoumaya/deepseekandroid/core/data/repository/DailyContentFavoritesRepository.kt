package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.local.GuestFavoritesStore
import com.msoumaya.deepseekandroid.core.data.remote.DailyContentFavoriteSource
import com.msoumaya.deepseekandroid.core.data.remote.OwnerStore
import com.msoumaya.deepseekandroid.core.domain.DailyContentFavorites
import com.msoumaya.deepseekandroid.core.domain.DailyContentFavoritesText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

// ---------------------------------------------------------------------------
// Dépôt « favoris des contenus du jour »
// ---------------------------------------------------------------------------
// Portage des deux fonctions `favorites()` et `setFavorite()` de `src/services/dailyContents.ts` :
// la liste, le geste, et les trois choses que l'original tient autour d'elles — une fenêtre de
// trente secondes, un dédoublonnage des lectures concurrentes, et une voie locale pour le visiteur.
//
// ## Pourquoi ce dépôt n'a ni état publié ni observation du compte
//
// Les quatre autres dépôts par compte — Quiz, espace social, récitations, état applicatif —
// publient un `StateFlow` et **observent** `OwnerStore`, parce qu'ils alimentent un écran qui
// s'affiche avant que quoi que ce soit ne soit lu, et qui doit se vider quand le compte change.
// Celui-ci ne le fait pas, et ce n'est pas une économie : il n'a **rien à publier**. Ses deux
// méthodes rendent une valeur à l'appelant, et c'est l'écran qui garde son propre état — ce que
// fait exactement l'original, dont `ContentCard` tient un `useState(false)` et le pose après avoir
// lu la liste. Publier un flux ici créerait une seconde vérité, et il faudrait décider laquelle
// l'emporte le jour où les deux divergeraient.
//
// L'observation du compte est remplacée par une lecture **à chaque appel** : `currentOwner()` est
// relu, et c'est lui qui choisit la clé du cache. Un changement de compte se traduit donc
// immédiatement par une autre clé — donc par une lecture serveur —, sans qu'aucune coroutine de
// fond n'ait à s'en occuper.
//
// ## Les trois règles que ce fichier porte, et ce qu'elles coûtent quand elles sont fausses
//
//  - **la fenêtre de trente secondes** — deux affichages rapprochés de la même fiche ne font
//    qu'une requête. Sans elle, une liste de trente fiches ferait trente lectures identiques ;
//  - **le dédoublonnage des lectures concurrentes** — la fiche et l'écran de liste demandent la
//    liste au même moment, et l'original ne fait partir qu'une requête. Sans lui, deux requêtes
//    identiques partent, et la seconde arrive après coup ;
//  - **la voie du visiteur** — `anon` n'a **aucun** `grant` sur `content_favorites`. Un visiteur
//    qui écrirait côté serveur recevrait un refus, et son cœur ne se remplirait jamais. Sa liste
//    vit donc sur le disque, et le geste n'appelle jamais le réseau.
//
// ## Ce que le dédoublonnage ne garantit pas
//
// L'original ne peut pas courir : JavaScript n'exécute qu'un appel à la fois, donc entre sa
// lecture du cache et son enregistrement de la requête en vol, rien ne s'intercale. Ici,
// `currentOwner()` suspend **avant** cette paire, et deux appels partis en même temps sur deux fils
// peuvent tous deux manquer le cache avant que le premier ne s'inscrive. Le second fera alors sa
// propre requête. C'est une course étroite — les deux instructions qui la séparent ne suspendent
// pas —, et sa seule conséquence est une **lecture de trop** : la liste est la même, le cache est
// écrit deux fois avec la même valeur, et rien de faux ne s'affiche. La fermer demanderait une
// seconde vérification sous verrou ; elle n'est pas là parce que le coût dépasse le défaut, et ce
// paragraphe existe pour que le prochain lecteur n'ait pas à le redécouvrir.
//
// ## Ce que la fenêtre ne fait pas sur la voie du visiteur, et pourquoi elle y est quand même
//
// **Mesuré, et contre l'intuition** : la fenêtre et le dédoublonnage sont **inobservables** sur la
// voie du visiteur. `GuestFavoritesStore` s'appuie sur `JsonFileStore`, qui garde son document en
// mémoire dès la première lecture : une seconde lecture ne touche pas le disque et rend la même
// liste, que le dépôt consulte son cache ou non. Un contrôle écrit pour mesurer la fenêtre sur
// cette voie ne peut donc **pas** tomber sous la mutation qui la supprime — et c'est exactement ce
// qui s'est produit : la falsification l'a déclaré faux, et il a été retiré plutôt que gardé comme
// une couverture apparente. Le contrôle `un visiteur lit sa liste locale` continue, lui, de
// mesurer ce qui est observable : que le serveur n'est **pas** appelé.
//
// La fenêtre y est **quand même**, pour deux raisons. L'original cache les deux voies dans la
// **même** carte (`favoriteCache.set(userId ?? 'guest', …)`), donc les séparer serait un écart que
// rien ne motive ; et le jour où le magasin du visiteur n'aurait plus de mémoire — un document relu
// à chaque appel —, c'est cette fenêtre qui éviterait une relecture par affichage.
// ---------------------------------------------------------------------------

/**
 * Lit et modifie les favoris du compte courant — ou du visiteur.
 *
 * @param source accès à la table des favoris, ou `null` si aucun projet Supabase n'est configuré.
 *   Le `null` ne concerne que la voie du **compte** : la voie du visiteur, elle, ne touche pas au
 *   réseau et fonctionne sans projet.
 * @param guest le document local du visiteur, à la racine de l'état.
 * @param session propriétaire courant, relu à chaque appel. C'est lui qui choisit la clé du cache,
 *   et donc ce qui empêche les favoris d'un compte d'apparaître sous un autre.
 * @param now instant courant, injectable pour que la fenêtre de trente secondes se mesure sans
 *   dépendre de l'horloge.
 */
class DailyContentFavoritesRepository(
    private val source: DailyContentFavoriteSource?,
    private val guest: GuestFavoritesStore,
    private val session: OwnerStore,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    /** Une liste retenue, et l'instant où elle a été lue. */
    private data class Entry(val ids: Set<String>, val at: Long)

    /**
     * Les listes retenues, **une par propriétaire**.
     *
     * La carte n'est jamais vidée, comme celle de l'original : changer de compte ne retire pas
     * l'entrée du précédent, et y revenir dans les trente secondes rend la liste retenue. C'est
     * sans danger — chaque clé ne porte que les favoris de son compte —, et c'est une fidélité :
     * une invalidation au changement de compte serait un geste que l'original ne fait pas.
     *
     * Concurrente pour la raison dite plus haut : deux écrans peuvent lire en même temps, et
     * l'original ne le peut pas.
     */
    private val cache = ConcurrentHashMap<String, Entry>()

    /**
     * Les lectures **en vol**, une par propriétaire.
     *
     * C'est la traduction de `favoriteRequests` de l'original. Une `CompletableDeferred` et non un
     * `async` : elle n'appartient à aucune portée, donc un échec ne se propage pas aux autres
     * travaux de l'appelant, et l'annulation du premier lecteur n'annule pas les suivants
     * autrement que par l'échec qu'elle a réellement produit.
     */
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Set<String>>>()

    /**
     * Les identifiants des contenus mis en favori par le compte courant — ou par le visiteur.
     *
     * Elle **lève** quand la lecture échoue, comme l'original : c'est la fiche qui porte son
     * propre avis d'erreur, parce que seule elle sait ce qu'elle avait à l'écran.
     */
    suspend fun ids(): Set<String> = idsFor(session.currentOwner())

    /**
     * Pose ou retire le favori [id], selon [enabled].
     *
     * **L'ordre des trois temps est celui de l'original, et il compte.** La liste est d'abord lue
     * — depuis le cache quand il est frais —, la nouvelle liste est calculée, puis l'écriture a
     * lieu, et **seulement ensuite** le cache est remplacé. Écrire le cache avant l'écriture
     * ferait afficher un favori que le serveur n'a pas reçu : le cœur serait rempli, et il se
     * viderait au prochain affichage sans que rien n'ait signalé l'échec.
     *
     * Aucune valeur n'est rendue : l'original n'en rend pas non plus, et la liste vient d'être
     * mise en cache — un appelant qui veut la connaître appelle [ids], et obtient la valeur
     * fraîche sans requête.
     *
     * @param id contenu visé.
     * @param enabled `true` pour ajouter, `false` pour retirer.
     */
    suspend fun setFavorite(id: String, enabled: Boolean) {
        val owner = session.currentOwner()
        val next = DailyContentFavorites.toggled(idsFor(owner), id, enabled)

        when {
            // Le visiteur n'a pas de ligne à écrire : `anon` n'a aucun `grant` sur la table. Sa
            // liste ne quitte pas l'appareil, et c'est le serveur qui l'impose, pas ce client.
            owner == null -> guest.replace(next)
            enabled -> sourceFor(owner).add(owner, id)
            else -> sourceFor(owner).remove(owner, id)
        }

        cache[keyOf(owner)] = Entry(next, now())
    }

    /**
     * La liste de [owner] — `null` pour le visiteur —, retenue ou lue.
     *
     * Le propriétaire est **passé** et non relu : un appel qui relirait `currentOwner()` entre la
     * lecture et l'écriture pourrait servir la liste d'un compte et l'écrire sous la clé d'un
     * autre, si le compte changeait dans cet intervalle.
     */
    private suspend fun idsFor(owner: String?): Set<String> {
        val key = keyOf(owner)

        cache[key]?.let { if (DailyContentFavorites.isFresh(it.at, now())) return it.ids }

        val mine = CompletableDeferred<Set<String>>()
        val running = inFlight.putIfAbsent(key, mine)
        if (running != null) return running.await()

        try {
            val ids = if (owner == null) guest.current() else sourceFor(owner).list(owner)
            cache[key] = Entry(ids, now())
            mine.complete(ids)
            return ids
        } catch (cancelled: CancellationException) {
            // L'annulation est reportée aux lecteurs qui attendent, puis relancée : l'avaler
            // casserait l'annulation coopérative, et la portée croirait le travail fini.
            mine.completeExceptionally(cancelled)
            throw cancelled
        } catch (error: Exception) {
            // L'échec est reporté aux lecteurs qui attendent — c'est le sens du dédoublonnage :
            // ils ont demandé la même chose, ils reçoivent la même réponse, et il n'y a pas de
            // seconde tentative à leur place.
            mine.completeExceptionally(error)
            throw error
        } finally {
            // `remove(key, mine)` et non `remove(key)` : retirer l'entrée d'un autre lecteur qui
            // se serait inscrit entre-temps rendrait son dédoublonnage muet.
            inFlight.remove(key, mine)
        }
    }

    /**
     * La source du compte, ou un échec nommé.
     *
     * Voir [DailyContentFavoritesText.NO_PROJECT] : la branche est inatteignable aujourd'hui — un
     * propriétaire n'existe que si une session a été ouverte, donc si un projet est configuré —,
     * et un échec nommé y vaut mieux qu'une liste vide, qui affirmerait « aucun favori ».
     */
    private fun sourceFor(owner: String): DailyContentFavoriteSource =
        source ?: throw IllegalStateException(DailyContentFavoritesText.NO_PROJECT)

    private companion object {
        /**
         * La clé du visiteur.
         *
         * C'est le mot de l'original (`userId ?? 'guest'`). Il ne peut pas entrer en collision avec
         * un identifiant de compte, qui est un UUID.
         */
        const val GUEST_KEY = "guest"

        fun keyOf(owner: String?): String = owner ?: GUEST_KEY
    }
}
