package com.msoumaya.deepseekandroid.core.data.remote

// ---------------------------------------------------------------------------
// L'adresse signée d'un média de contenu du jour
// ---------------------------------------------------------------------------
// Portage de `resolveContentMedia` de `src/services/dailyContentMedia.ts` : la moitié du fichier
// qui **lit**. Les trois gestes d'administration du même fichier restent dehors — voir le KDoc de
// `DailyContentMedia`, qui porte la raison et la ligne de `ANDROID_MIGRATION.md` dont elle vient.
//
// **L'interface porte deux choses, et la seconde surprend.** [signedUrl] est ce qu'on attend d'un
// accès au compartiment ; [root] est l'adresse publique du compartiment, et elle n'est pas un
// appel réseau — c'est une **valeur de configuration**. Elle est là parce que l'original la lit
// du client (`getPublicUrl('')`) et non d'une constante, et que ce portage doit faire de même :
// `SupabaseConfig.PROJECT_URL` est le repli de `BuildConfig.SUPABASE_URL`, pas sa source, donc
// composer la racine sur la constante ferait échouer la reconnaissance des adresses sur toute
// compilation pointée ailleurs — sans erreur, et sans vignette. La valeur vit ici, auprès du
// client qui la porte, et la **règle** qui la fabrique vit dans `DailyContentMedia.publicRoot`.
//
// **L'interface existe pour la même raison que les autres.** Le dépôt qui la consomme porte une
// règle qui ne se voit pas à l'écran : une adresse signée est réutilisée pendant cinquante-cinq
// minutes, et une adresse **hors du compartiment** est rendue telle quelle sans qu'aucun appel ne
// parte. Ces deux règles se mesurent en substituant une doublure — sans réseau, sans compartiment
// et sans appareil.
// ---------------------------------------------------------------------------

/**
 * Ce que la résolution d'un média demande au compartiment.
 *
 * Deux membres, et aucun ne prend l'identifiant du compte : le compartiment
 * `daily-content-media` est lisible par `anon` **tant qu'un contenu actif cite l'objet**
 * (`daily_media_active_read`, dans `supabase/daily-content-media.sql`). Les médias éditoriaux
 * n'appartiennent à personne, et passer un compte laisserait croire que le client décide pour qui
 * il signe.
 */
interface DailyContentMediaSource {

    /**
     * L'adresse publique du compartiment, **barre oblique finale comprise**.
     *
     * C'est `getPublicUrl('')` de l'original, figée à la construction. Elle sert à reconnaître les
     * adresses rangées en base : une adresse qui ne commence pas par elle n'est pas signable, et
     * doit être rendue telle quelle — c'est une adresse externe, ou celle d'un autre projet.
     */
    val root: String

    /**
     * Une adresse d'écoute ou d'affichage, signée et datée, pour un objet du compartiment.
     *
     * Le compartiment est **privé** : le chemin seul ne donne accès à rien. L'adresse rendue n'est
     * valable que [com.msoumaya.deepseekandroid.core.domain.DailyContentMedia.SIGNED_URL_SECONDS]
     * secondes.
     *
     * @param path chemin de l'objet dans le compartiment, sans la racine — tel que
     *   [com.msoumaya.deepseekandroid.core.domain.DailyContentMedia.pathOf] le rend.
     */
    suspend fun signedUrl(path: String): String
}
