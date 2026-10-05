package com.msoumaya.deepseekandroid.core.domain

/**
 * Le récitateur retenu à l'ouverture du lecteur, et ce qu'il faut en faire.
 *
 * Porté depuis `src/PassageAudioPlayer.tsx` : la valeur initiale (ligne 43), la clé par
 * utilisateur (ligne 44), l'effet de lecture (ligne 181) et `rememberReciter` (ligne 45).
 *
 * ## Deux mémoires, et laquelle gagne
 *
 * Le client d'origine garde le récitateur à **deux** endroits, et ce n'est pas un doublon :
 *
 *  - l'**état du compte** (`state.audioPreferences.reciterId`), qui suit la personne d'un
 *    appareil à l'autre ;
 *  - une clé d'`AsyncStorage` **propre à l'utilisateur** (`audio-reciter-hafs:<userId>`), qui
 *    survit hors ligne et avant même que le compte soit chargé.
 *
 * L'état du compte **gagne** : `let id = reciterPreference ?? await getItem(reciterKey)`. C'est
 * ce qui fait qu'un choix fait ailleurs s'applique ici au lieu d'être écrasé par la mémoire
 * locale.
 *
 * ## La clé locale est par utilisateur, et ce n'est pas un détail
 *
 * `AsyncStorage` ne connaît pas la notion de compte : la portée par utilisateur est écrite dans
 * la **clé**. Sans elle, deux comptes sur le même appareil partageraient la même mémoire — et
 * comme [Resolution.pushToState] reporte la valeur de l'appareil vers le compte, le choix du
 * premier serait **écrit dans le compte du second**. La fuite ne serait pas seulement affichée :
 * elle serait persistée.
 *
 * Le portage garde la même décision dans un document unique, en y ajoutant le **propriétaire**
 * de la valeur retenue. C'est l'équivalent exact de la clé suffixée, et cela évite de nommer un
 * second fichier d'après un identifiant qui n'est pas encore connu au démarrage.
 *
 * ## L'ancienne clé globale, et son marqueur de propriétaire
 *
 * Le client d'origine a déjà eu une clé **globale** (`audio-reciter-hafs`, sans suffixe). Son
 * effet la reprend, mais **une seule fois**, et seulement pour le premier compte qui la trouve :
 * le marqueur `audio-reciter-legacy-owner` l'en empêche ensuite. C'est pourquoi une valeur
 * **sans propriétaire** est adoptable ici — c'est exactement ce marqueur.
 *
 * ## Le report vers le compte, et sa condition
 *
 * Quand la valeur vient de l'appareil et que le compte n'en a pas, l'effet la **pousse** vers
 * l'état (`if(!reciterPreference) onReciterPreference?.(id)`). C'est une migration en un sens
 * unique : ce que l'appareil sait et que le compte ignore remonte une fois. La condition est
 * l'**absence** de valeur côté compte, et non sa différence — un compte qui aurait déjà choisi
 * n'est jamais réécrit par un appareil.
 *
 * ## Une valeur inconnue du catalogue fait écran, elle ne se contourne pas
 *
 * `let id = reciterPreference ?? …` : la valeur du compte **masque** la mémoire locale même
 * lorsqu'aucun récitateur ne porte cet identifiant — la suite fait alors
 * `reciters.find(…) ?? defaultReciter`. Le portage suit : un identifiant inconnu côté compte
 * donne le récitateur **par défaut**, et non celui de l'appareil. Descendre d'un cran
 * appliquerait ici le choix d'un autre appareil au moment précis où le compte dit autre chose.
 *
 * ## Ce que cette règle ne fait pas
 *
 * Elle ne **choisit** pas de récitateur : elle dit lequel des deux souvenirs retenir, ou `null`
 * quand aucun ne vaut — et c'est le lecteur qui retombe alors sur son défaut. Elle ne connaît ni
 * le disque, ni le réseau, ni Compose : elle est donc éprouvable sans appareil.
 */
object ReciterPreference {

    /**
     * Le propriétaire d'une valeur choisie **hors connexion**.
     *
     * C'est le `userId ?? 'guest'` de la source : un choix fait sans compte appartient à
     * « personne », et le premier compte venu ne doit pas le reprendre. Un identifiant qui n'est
     * pas un identifiant de compte réel est le seul moyen de le dire sans un second champ.
     */
    const val GUEST: String = "guest"

    /**
     * Ce qu'il faut faire du récitateur à l'ouverture.
     *
     * @param reciterId le récitateur à adopter, ou `null` pour laisser le lecteur à son défaut.
     * @param ownerId le propriétaire à réécrire dans la mémoire de l'appareil, ou `null` quand
     *   rien ne doit être retenu.
     *
     *   **Invariant, et il porte une décision** : [reciterId] et [ownerId] sont nuls **ensemble**.
     *   Non nul, [ownerId] veut donc dire « retiens [reciterId] sous ce nom » — les deux
     *   s'écrivent d'un seul geste. Nommer un propriétaire sans écrire la valeur laisserait
     *   l'appareil porter le choix d'un **autre** compte sous le nom du compte courant, et la
     *   fuite ne se verrait qu'au lancement suivant : c'est précisément ce que la portée par
     *   utilisateur existe pour empêcher.
     * @param pushToState vrai si la valeur vient de l'**appareil** et que le compte ne la connaît
     *   pas encore. C'est la migration en un sens unique ; un compte qui a déjà choisi ne la
     *   reçoit jamais.
     */
    data class Resolution(
        val reciterId: String?,
        val ownerId: String?,
        val pushToState: Boolean,
    )

    /**
     * Décide lequel des deux souvenirs retenir.
     *
     * @param synced le récitateur de l'état du compte, ou `null` s'il n'en porte pas.
     * @param stored la mémoire de l'appareil, ou `null` si rien n'y a jamais été écrit.
     * @param owner le propriétaire de [stored]. `null` veut dire « sans propriétaire », et donc
     *   adoptable — c'est l'ancienne clé globale, ou un document écrit avant cette règle.
     * @param userId le compte connecté, ou `null` hors connexion.
     * @param known dit si un identifiant désigne un récitateur du catalogue.
     */
    fun resolve(
        synced: String?,
        stored: String?,
        owner: String?,
        userId: String?,
        known: (String) -> Boolean = { id -> Audio.reciters.any { it.id == id } },
    ): Resolution {
        val who = userId ?: GUEST

        // Le compte d'abord, et il fait écran même quand son identifiant est inconnu : c'est lui
        // qui fait autorité, et retomber sur l'appareil appliquerait un autre choix au moment
        // précis où le compte dit autre chose.
        if (synced != null) {
            return if (known(synced)) Resolution(synced, who, pushToState = false)
            else Resolution(null, null, pushToState = false)
        }

        // L'appareil ensuite — mais seulement s'il appartient à ce compte, ou à personne.
        val storedIsMine = stored != null && known(stored) && (owner == null || owner == who)
        if (storedIsMine) {
            return Resolution(stored, who, pushToState = true)
        }

        return Resolution(null, null, pushToState = false)
    }
}
