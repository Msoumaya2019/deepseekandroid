package com.msoumaya.deepseekandroid.core.playback

/**
 * Ce que le service met dans la notification, et **n'invente pas**.
 *
 * Ces trois lignes sont recopiées du client d'origine, où elles sont construites ainsi :
 *
 * ```ts
 * setActiveForLockScreen(true, {
 *   title: `Coran · ${verseAudioLabel(position.verseId)}`,
 *   artist: selectedReciter.name,
 *   albumTitle: 'Hafs ‘an ‘Âsim',
 * })
 * ```
 *
 * Elles sont donc regroupées ici, dans une valeur **pure** — pas dans le service. Un service
 * Android ne se teste pas sur la JVM, mais la mise en forme d'un titre, si : ce fichier est
 * couvert par des tests, et le service ne fait que le lire.
 *
 * Le titre porte le passage, pas seulement l'application : sur un écran verrouillé, « Coran »
 * seul ne dit pas où l'on en est, et c'est justement ce qu'on vient y chercher. Le séparateur
 * est un point médian entouré d'espaces, comme la `·` d'origine, et l'album est le nom de la
 * lecture — Hafs ‘an ‘Âsim — écrit avec les apostrophes typographiques du client d'origine.
 */
object AudioNotification {

    /** Le nom de l'album publié, identique à celui du client d'origine. */
    const val ALBUM: String = "Hafs ‘an ‘Âsim"

    /**
     * Le titre affiché pour un libellé de passage.
     *
     * [verseLabel] est le libellé déjà calculé (« sourate 2, versets 1 à 5 » ou « verset 255 ») :
     * ce fichier ne connaît ni les sourates ni les versets, il ne fait qu'assembler.
     */
    fun title(verseLabel: String): String = "Coran · $verseLabel"

    /**
     * L'artiste affiché : le récitateur.
     *
     * Une fonction, et non un simple passage, parce que la chaîne doit pouvoir être vide : un
     * récitateur sans nom ne doit pas produire une ligne vide dans la notification, ce que le
     * client d'origine évite de la même façon.
     */
    fun artist(reciterName: String): String? = reciterName.trim().ifEmpty { null }
}
