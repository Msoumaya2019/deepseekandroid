package com.msoumaya.deepseekandroid.core.domain

/**
 * Règles des médias d'un contenu du jour — **pures**, donc éprouvables sans appareil.
 *
 * Porté depuis `src/services/dailyContentMedia.ts`, **moitié lecture seulement** : la fonction
 * `resolveContentMedia`. Les trois gestes d'écriture du même fichier —
 * `chooseAndUploadContentMedia`, `removeContentMedia` et `cleanUnusedContentMedia` — ne sont pas
 * portés, et ce n'est pas un oubli : les politiques de `supabase/daily-content-media.sql`
 * réservent l'écriture du compartiment à `private.is_app_admin()` (`daily_media_admin`), et la
 * ligne « l'administration reste sur le web » est déjà écrite dans `ANDROID_MIGRATION.md`. Un
 * client qui les porterait ne pourrait rien en faire.
 *
 * ## Pourquoi un contenu du jour a besoin d'une adresse signée
 *
 * Le compartiment `daily-content-media` est **privé** : `supabase/daily-content-media.sql` le
 * déclare `public=false`. Or ce que la table range dans `image_url` et `audio_url` est bien une
 * adresse **publique** — celle que `getPublicUrl` a rendue au moment du dépôt. Ces deux faits ne
 * se contredisent pas : l'adresse rangée est un **chemin déguisé**, dont les derniers segments
 * disent où le fichier se trouve, et non un droit d'y accéder. C'est `resolveContentMedia` qui
 * la transforme en droit temporaire.
 *
 * La lecture, elle, n'exige aucun compte : la politique `daily_media_active_read` ouvre le
 * compartiment à `anon` **tant qu'un contenu actif cite l'objet**. Un objet que plus aucun
 * contenu actif ne cite est donc illisible — y compris par le client qui vient de l'afficher —,
 * ce qui est exactement le comportement voulu : le retrait d'un contenu retire aussi sa pièce
 * jointe.
 *
 * ## La racine : d'où elle vient, et pourquoi elle n'est pas une constante du projet
 *
 * L'original lit la racine du **client** — `supabase.storage.from(bucket).getPublicUrl('')` —,
 * plutôt que de la composer. Ce portage la compose, mais à partir de l'adresse **réellement
 * configurée** : celle de `SupabaseConfig.url`, que `:app` remplit avec `BuildConfig.SUPABASE_URL`.
 *
 * **La distinction n'est pas cosmétique.** `SupabaseConfig.PROJECT_URL` est la valeur *par
 * défaut* du projet partagé, et elle est le repli de `BuildConfig.SUPABASE_URL` — pas sa source.
 * Composer la racine sur la constante plutôt que sur l'adresse du client ferait, sur une
 * compilation pointée ailleurs, échouer silencieusement [pathOf] sur **toutes** les adresses :
 * la racine ne serait pas reconnue, l'adresse serait rendue telle quelle, et l'image d'un
 * contenu du jour demanderait un objet privé sans signature — une vignette absente, sans erreur
 * visible, sur une compilation que rien d'autre ne distingue.
 */
object DailyContentMedia {

    /**
     * Compartiment des médias éditoriaux, tel que `daily-content-media.sql` le déclare.
     *
     * Il porte les deux natures à la fois — vignettes **et** écoutes : `image/jpeg`, `image/png`,
     * `image/webp`, `audio/mpeg`, `audio/mp4`, `audio/aac`, pour une limite de 30 Mio. C'est
     * pourquoi une seule fonction résout les deux : `ContentCard` de l'original appelle
     * `resolveContentMedia` sur `image_url` **et** sur `audio_url`.
     */
    const val BUCKET: String = "daily-content-media"

    /**
     * Durée de validité d'une adresse signée, en secondes.
     *
     * Une heure. C'est la constante de l'original (`createSignedUrl(path,3600)`), et elle est
     * **plus longue** que celle des récitations (`Recitations.SIGNED_URL_SECONDS`, dix minutes) :
     * l'écart est celui des usages — une récitation se joue tout de suite, une vignette de
     * contenu du jour peut rester affichée un long moment sur une page ouverte.
     */
    const val SIGNED_URL_SECONDS: Int = 3600

    /**
     * Durée pendant laquelle une adresse signée est réutilisée, en millisecondes.
     *
     * Cinquante-cinq minutes, contre soixante de validité : l'original écrit `3300000`, et les
     * cinq minutes de marge ne sont pas un arrondi. Une adresse réutilisée à la seconde près de
     * son expiration expirerait pendant l'affichage, et une vignette disparaîtrait **après** un
     * rendu réussi — c'est-à-dire au moment où personne ne regarde plus le code qui l'a posée.
     * La marge est ce qui rend la relecture impossible à observer.
     */
    const val CACHE_MILLIS: Long = 3_300_000L

    /**
     * Le morceau d'adresse qui désigne la racine publique d'un compartiment.
     *
     * Recopié de l'original (`/storage/v1/object/public/${bucket}/`), **barre oblique finale
     * comprise** : `getPublicUrl('')` rend exactement cette forme, et c'est elle que
     * [publicRoot] doit reproduire pour que [pathOf] reconnaisse les adresses rangées en base.
     * Privée parce qu'elle n'est qu'un moyen : ce qui se lit et s'éprouve, c'est [publicRoot].
     */
    private const val MARKER: String = "/storage/v1/object/public/$BUCKET/"

    /**
     * La racine publique du compartiment pour un projet donné.
     *
     * C'est `getPublicUrl('')` du client d'origine, composée au lieu d'être demandée. L'appelant
     * lui passe l'adresse avec laquelle le client a **réellement** été construit — voir le KDoc
     * du fichier pour ce que coûterait de passer la constante du projet à la place.
     *
     * Aucune normalisation n'est faite sur [projectUrl] : l'original n'en fait pas non plus, et
     * une adresse qui porterait une barre oblique finale produirait chez lui la même racine
     * fausse — le défaut serait en amont, dans la configuration, pas ici.
     */
    fun publicRoot(projectUrl: String): String = projectUrl + MARKER

    /**
     * Le chemin de l'objet dans le compartiment, ou `null` si [url] n'en désigne pas un.
     *
     * Reçoit la **racine** et non l'adresse du projet : c'est la racine qui est comparée, et la
     * passer telle quelle évite de la recomposer à chaque appel — donc de pouvoir la recomposer
     * autrement qu'à l'endroit où elle a été construite.
     *
     * **Deux refus, et une seule décision.** L'original écrit
     * `if(!url.startsWith(root))return null; return url.split(marker)[1] ?? null;`, et l'appelant
     * ajoute `if(!path)return url`. Trois expressions pour deux cas :
     *
     *  - une adresse **hors du compartiment** — une adresse externe, ou celle d'un autre projet —
     *    n'est pas signable, et c'est le `startsWith` qui la refuse ;
     *  - l'adresse **exactement égale à la racine** est reconnue par le `startsWith`, puis le
     *    `split` ne trouve rien après le marqueur et rend `undefined` — que le `?? null` replie
     *    sur `null`. Ici, le `takeIf { isNotEmpty() }` dit la même chose en un endroit.
     *
     * Le second cas n'est pas théorique : c'est l'adresse qu'un enregistrement fautif — une
     * vignette posée sans chemin — laisserait en base, et le refuser ici évite de demander au
     * serveur de signer le compartiment entier.
     *
     * **Le chemin est retiré par `removePrefix`, et non par un `split`.** Pour toute adresse
     * qu'un dépôt peut produire — `userId/horodatage-aléa.extension`, composé par
     * `chooseAndUploadContentMedia` —, les deux donnent le même résultat. Ils diffèrent si le
     * chemin contenait lui-même le marqueur, auquel cas le `split` **tronquerait** le chemin à sa
     * seconde occurrence : `removePrefix` rend ce que l'original voulait dire.
     */
    fun pathOf(url: String, root: String): String? {
        if (!url.startsWith(root)) return null
        return url.removePrefix(root).takeIf { it.isNotEmpty() }
    }

    /**
     * Vrai si une adresse signée reste utilisable à l'instant [now].
     *
     * L'original écrit `cached.expires > Date.now()` : une **comparaison absolue**, et non une
     * différence. La distinction se voit quand l'horloge recule — une correction NTP, un
     * changement de fuseau par l'utilisateur : l'échéance reste alors dans le futur, et l'adresse
     * est réutilisée jusqu'à ce qu'elle soit réellement expirée côté serveur. C'est le sens sûr,
     * puisqu'une signature refusée fait échouer l'affichage d'une vignette, et il est reproduit
     * tel quel.
     */
    fun isFresh(expiresAt: Long, now: Long): Boolean = expiresAt > now
}
