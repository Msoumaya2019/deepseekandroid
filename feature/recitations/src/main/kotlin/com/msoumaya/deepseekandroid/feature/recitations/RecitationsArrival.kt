package com.msoumaya.deepseekandroid.feature.recitations

// ---------------------------------------------------------------------------
// L'arrivée d'une récitation qu'on est venu partager
// ---------------------------------------------------------------------------
// Le lecteur enregistre une récitation, puis **quitte l'écran** pour la liste, en emportant
// l'identifiant de ce qu'il vient d'écrire (`App.tsx`, `onShareRecitation`). La liste reçoit donc
// un identifiant **en attente**, et elle doit l'attendre : la récitation vient d'être inscrite au
// registre local, mais son dépôt est un autre geste — la ligne peut n'être **pas encore** sur le
// serveur, et le partage ne s'ouvre que sur une récitation distante.
//
// L'original fait donc deux choses, et elles sont dans deux effets distincts
// (`RecitationsScreen.tsx:34-35`) : quand l'identifiant attendu **arrive** dans la liste
// distante, il ouvre la ligne **et** ouvre son choix d'ami ; tant qu'il n'y est pas, il **relit**
// la liste jusqu'à dix fois, toutes les trois secondes, puis renonce.
//
// Ce fichier porte la **décision** de ces deux effets, et rien d'autre. C'est ce qui la rend
// éprouvable : l'attente, elle, demande une horloge, et un test qui attendrait vraiment trente
// secondes ne serait pas joué. Ici, « faut-il ouvrir, relire, ou renoncer ? » est une fonction de
// quatre faits, et les deux constantes de l'attente sont nommées pour que le `ViewModel` n'ait pas
// à les écrire lui-même — un nombre recopié à deux endroits finit par diverger.
// ---------------------------------------------------------------------------

/**
 * Ce qu'il faut faire de la récitation en attente de partage.
 *
 * ## Ce que la règle ne fait pas
 *
 * Elle **ne compte pas** les secondes et **ne relit pas** la liste : elle dit seulement ce qu'il
 * faut faire, et c'est l'appelant qui attend et qui relit. Une règle qui déclencherait elle-même
 * l'attente serait inséparable de son horloge, donc inséparable d'un test qui dure.
 *
 * ## Pourquoi la ligne doit être **distante**
 *
 * Le partage s'ouvre sur une récitation qui est **sur le serveur** : c'est ce que la fonction
 * `can_play_shared_recitation` exige, et l'écran ne propose le geste que là où il peut aboutir.
 * Une récitation seulement locale — déposée plus tard — ne se partage pas encore, d'où l'attente.
 */
internal object RecitationsArrival {

    /**
     * Le nombre de relectures avant de renoncer.
     *
     * Dix, comme l'original (`attempts > 10`). Ce n'est pas un délai choisi : c'est le compte
     * d'essais de la source, et le réduire ferait renoncer plus tôt qu'elle sur un dépôt lent.
     */
    const val MAX_ATTEMPTS: Int = 10

    /**
     * Le temps entre deux relectures, en millisecondes.
     *
     * Trois mille, comme l'original. Avec [MAX_ATTEMPTS], cela borne l'attente à **trente
     * secondes** : au-delà, la récitation n'est pas arrivée, et la liste reste ouverte sur
     * elle-même plutôt que de faire attendre indéfiniment quelqu'un qui a déjà tourné la page.
     */
    const val INTERVAL_MS: Long = 3_000L

    /** Ce qu'il faut faire de l'identifiant attendu. */
    enum class Decision {
        /**
         * Rien à faire, et il n'y a plus rien à espérer.
         *
         * Deux situations la donnent, et elles se ressemblent : **aucun** identifiant en attente,
         * et un identifiant qui désigne la ligne **déjà ouverte**. Dans les deux cas, ouvrir
         * n'aurait pas de sens — et dans le second, ce serait pire : `onOpen` **referme** une
         * ligne déjà ouverte, donc rouvrir refermerait la récitation qu'on venait de partager.
         */
        IDLE,

        /** La récitation est arrivée : l'ouvrir, **puis** ouvrir son choix d'ami, dans cet ordre. */
        OPEN,

        /** Elle n'est pas encore là : attendre, puis relire la liste. */
        RETRY,

        /** Elle n'est pas là et les essais sont épuisés : renoncer, sans rien dire à personne. */
        GIVE_UP,
    }

    /**
     * Ce qu'il faut faire de [pendingId], au vu de trois faits et du compte d'essais déjà faits.
     *
     * @param pendingId la récitation à partager, ou `null` quand on n'est pas venu pour cela.
     * @param remoteIds les identifiants des récitations **distantes** connues.
     * @param openId la ligne actuellement ouverte, ou `null`.
     * @param attempts le nombre de relectures déjà faites.
     */
    fun decide(
        pendingId: String?,
        remoteIds: Collection<String>,
        openId: String?,
        attempts: Int,
    ): Decision {
        if (pendingId == null) return Decision.IDLE
        if (pendingId in remoteIds) {
            // Déjà ouverte : ne rien faire. `onOpen` **bascule**, donc rouvrir refermerait.
            return if (openId == pendingId) Decision.IDLE else Decision.OPEN
        }
        return if (attempts >= MAX_ATTEMPTS) Decision.GIVE_UP else Decision.RETRY
    }
}
