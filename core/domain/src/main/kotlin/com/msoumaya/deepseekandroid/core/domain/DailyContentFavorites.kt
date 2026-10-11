package com.msoumaya.deepseekandroid.core.domain

/**
 * Règles des favoris d'un contenu du jour — **pures**, donc éprouvables sans appareil.
 *
 * Porté depuis les deux fonctions `favorites()` et `setFavorite()` de
 * `src/services/dailyContents.ts`. Ce qui décide vit ici ; ce qui interroge le serveur, lit le
 * disque et retient le résultat vit dans `DailyContentFavoritesRepository`.
 *
 * ## Les favoris sont **par compte**, et c'est la seule table qui le soit dans ce fichier
 *
 * `content_favorites` porte une clé primaire `(user_id, content_id)` et la politique
 * `favorites_own`, qui n'ouvre la table qu'à `authenticated` et seulement pour ses propres
 * lignes. Le `grant` de `supabase/daily-contents.sql` le confirme : `select,insert,update,delete`
 * sont accordés à `authenticated`, et **`anon` n'en reçoit aucun**. Un visiteur ne peut donc pas
 * écrire un favori côté serveur, et c'est pourquoi l'original tient une seconde voie pour lui —
 * une clé `AsyncStorage` locale, `guest-content-favorites`. Les deux voies partagent le même
 * cache et la même fenêtre ; elles ne partagent pas le même stockage, et c'est la politique du
 * serveur qui l'impose.
 */
object DailyContentFavorites {

    /**
     * Durée pendant laquelle une liste de favoris est réutilisée, en millisecondes.
     *
     * Trente secondes, comme l'original (`Date.now()-cached.at<30000`).
     *
     * **C'est une fenêtre, et non une invalidation.** Rien ne vide ce cache quand un contenu est
     * enregistré ou supprimé : `contentChanged()` de l'original réveille les **écrans**, pas ce
     * cache-là. Un favori écrit depuis un autre appareil — ou depuis l'administration — reste
     * donc invisible jusqu'à trente secondes. C'est une fidélité assumée : la durée est courte,
     * et l'invalider demanderait de relier deux objets que l'original ne relie pas.
     */
    const val CACHE_MILLIS: Long = 30_000L

    /**
     * Vrai si la liste lue à l'instant [at] peut encore servir à l'instant [now].
     *
     * L'original compare une **différence** et non une échéance :
     * `Date.now() - cached.at < 30000`. Écrire `now - at < CACHE_MILLIS` reproduit exactement
     * cela, et non `at + CACHE_MILLIS > now` : les deux formes ne diffèrent que si l'horloge
     * recule, et la première — celle de l'original — traite alors l'entrée comme **fraîche**
     * plutôt que comme expirée. C'est le sens sûr, puisqu'une relecture n'apprendrait rien de
     * plus qu'une lecture faite juste avant, et le détail est écrit ici pour qu'un futur
     * « nettoyage » ne le remplace pas par une comparaison d'échéance sans le dire.
     */
    fun isFresh(at: Long, now: Long): Boolean = now - at < CACHE_MILLIS

    /**
     * La liste des favoris après le geste.
     *
     * L'original écrit `enabled ? [...new Set([...ids, id])] : ids.filter(x => x !== id)`. Les
     * deux règles qu'il porte sont celles-ci, et aucune ne lève quand elle est fausse :
     *
     *  - **l'ajout est idempotent** : ajouter un favori déjà présent ne le compte pas deux fois.
     *    La table s'en moquerait — sa clé primaire l'interdit —, mais la liste **en mémoire**,
     *    elle, porterait un doublon, et l'écran qui la parcourt afficherait deux fois la même
     *    fiche ;
     *  - **le retrait est total** : retirer un favori absent ne retire rien d'autre. C'est ce qui
     *    permet d'appeler ce geste sans avoir d'abord vérifié l'état — ce que fait précisément
     *    `setFavorite`, qui calcule le suivant à partir d'une liste dont rien ne garantit qu'elle
     *    est à jour.
     *
     * Le `Set` rend la première règle structurelle : elle n'est plus une ligne de code qu'on
     * pourrait retirer, et c'est la raison du type. L'ordre n'est pas conservé, et il n'a pas à
     * l'être : la liste vient d'une requête sans `order`, donc son ordre n'a jamais rien voulu
     * dire — le seul usage de cette liste est le test d'appartenance.
     */
    fun toggled(ids: Set<String>, id: String, enabled: Boolean): Set<String> =
        if (enabled) ids + id else ids - id
}
