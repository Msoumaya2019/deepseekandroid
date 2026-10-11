package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.remote.DailyContentMediaSource
import com.msoumaya.deepseekandroid.core.domain.DailyContentMedia
import java.util.concurrent.ConcurrentHashMap

// ---------------------------------------------------------------------------
// Dépôt « médias des contenus du jour »
// ---------------------------------------------------------------------------
// Portage de `resolveContentMedia` de `src/services/dailyContentMedia.ts`. Ce que le fichier
// d'origine fait en une fonction et une `Map` module-level, ce dépôt le fait en une méthode et un
// champ : la **règle** — quelle adresse est signable, combien de temps une signature est réutilisée
// — vit dans `DailyContentMedia`, l'appel réseau dans `DailyContentMediaSource`, et il ne reste ici
// que l'enchaînement des deux et la mémoire.
//
// ## Une seule méthode, et elle rend toujours quelque chose d'utilisable
//
// `resolve` ne lève pas sur une adresse qu'elle ne sait pas signer : elle la **rend telle quelle**.
// C'est la règle de l'original (`if(!path)return url`), et elle a une conséquence visible : une
// vignette dont l'adresse est externe — ou dont le chemin est vide — s'affiche quand même, et
// c'est le serveur distant qui décidera. Refuser serait pire : l'écran montrerait une case vide
// pour une adresse qui, peut-être, fonctionnait.
//
// ## La mémoire n'est pas bornée, et c'est un choix
//
// L'original garde une `Map` qui ne se vide jamais : une entrée par adresse **distincte** déjà
// résolue, et rien ne la retire — pas même `removeContentMedia`, qui n'est pas porté. Chaque entrée
// est une chaîne d'adresse et un entier, donc la borne serait de toute façon inutile ici : il
// faudrait avoir affiché des milliers de contenus du jour distincts dans une même session pour que
// cela se voie. Une `BoundedCache` ferait pourtant **autre chose** que l'original : elle évincerait
// une entrée vivante, et la conséquence serait une seconde signature — invisible, sauf dans le
// journal du serveur. La fidélité est donc aussi la solution la plus simple, et la seule à ne pas
// introduire une règle que personne n'a écrite.
//
// ## Un `ConcurrentHashMap`, et ce n'est pas du zèle
//
// Le client d'origine est en JavaScript, où deux appels ne s'exécutent jamais en même temps : sa
// `Map` ne peut pas être écrite par deux lecteurs. Ici, deux écrans peuvent résoudre deux adresses
// depuis deux fils différents, et une `HashMap` écrite concurremment peut perdre une entrée — ou
// boucler — sans que rien ne le dise. La carte concurrente dit la même chose que l'original, sur
// une plateforme où la même chose n'est pas gratuite.
//
// **Ce qui n'est pas dédoublonné, en revanche.** Deux résolutions **de la même adresse** parties
// en même temps feront deux signatures : l'original ne les dédoublonne pas non plus — il n'a pas de
// `Map` de requêtes en vol, contrairement à ses favoris —, et la conséquence est un aller-retour
// de trop, pas un état faux.
// ---------------------------------------------------------------------------

/**
 * Résout l'adresse d'un média de contenu du jour en une adresse signée, et la retient.
 *
 * @param source accès au compartiment, ou `null` si aucun projet Supabase n'est configuré. Le
 *   `null` n'est pas une panne : c'est le mode hors ligne, et il doit se lire comme « rien à
 *   signer ». Sans source, une adresse est rendue **telle quelle** — il n'y a pas de racine pour
 *   la reconnaître, et la refuser retirerait de l'écran une adresse que l'original aurait gardée.
 * @param now instant courant, injectable pour que la fenêtre de réutilisation se mesure sans
 *   dépendre de l'horloge.
 */
class DailyContentMediaRepository(
    private val source: DailyContentMediaSource?,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    /** Une adresse signée et l'instant après lequel elle ne doit plus servir. */
    private data class Signed(val url: String, val expiresAt: Long)

    private val cache = ConcurrentHashMap<String, Signed>()

    /**
     * L'adresse à afficher ou à écouter pour [url], ou `null` si [url] est `null`.
     *
     * Quatre sorties, dans l'ordre de l'original :
     *
     *  1. **`null`** pour une adresse absente — un contenu sans vignette, ou sans écoute. Ce n'est
     *     pas une erreur, c'est la moitié des cas : `image_url` et `audio_url` sont nullables.
     *  2. **l'adresse inchangée** quand aucune source n'est configurée, ou quand l'adresse ne
     *     désigne pas le compartiment. La décision est prise par `DailyContentMedia.pathOf`, et
     *     elle est **mesurée** plutôt que devinée.
     *  3. **l'adresse retenue** quand la signature précédente est encore fraîche — cinquante-cinq
     *     minutes sur une validité de soixante. C'est cette sortie qui évite une signature par
     *     affichage de vignette.
     *  4. **l'adresse fraîchement signée**, écrite dans la mémoire avant d'être rendue.
     *
     * Une erreur de signature **remonte**, et n'est pas retenue : c'est la règle de l'original
     * (`if(error)throw error`), et elle laisse l'appelant décider — la fiche d'un contenu affiche
     * alors un avis, et ne pose pas d'adresse cassée dans un lecteur.
     */
    suspend fun resolve(url: String?): String? {
        if (url == null) return null
        val api = source ?: return url

        val path = DailyContentMedia.pathOf(url, api.root) ?: return url

        val cached = cache[url]
        if (cached != null && DailyContentMedia.isFresh(cached.expiresAt, now())) return cached.url

        val signed = api.signedUrl(path)
        cache[url] = Signed(signed, now() + DailyContentMedia.CACHE_MILLIS)
        return signed
    }
}
