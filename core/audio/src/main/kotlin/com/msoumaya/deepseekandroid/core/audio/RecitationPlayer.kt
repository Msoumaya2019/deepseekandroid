package com.msoumaya.deepseekandroid.core.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Où en est l'écoute d'**une** récitation enregistrée.
 *
 * **La durée n'est pas ici, et ce n'est pas un oubli.** Le client d'origine affiche
 * `item.duration_ms` — la durée *enregistrée dans la ligne*, au moment du dépôt —, et non celle
 * que le lecteur finit par découvrir. Les deux peuvent différer de quelques dizaines de
 * millisecondes, et c'est la première que la barre et la phrase de position doivent suivre : deux
 * durées pour la même piste feraient sauter le libellé au premier tic de lecture.
 *
 * @param uri ce qui est chargé, ou `null` si rien ne l'est. Il ne sert pas à décider — le
 *   `ViewModel` retient de lui-même quelle ligne il a chargée —, mais un état de lecture qui ne dit
 *   pas *quoi* il lit rend le diagnostic impossible, et ce dépôt préfère un état qui s'explique.
 * @param playing vrai si le son sort en ce moment. Il est publié **dès la demande de lecture**, et
 *   non à la première image sonore : l'écran doit basculer sur « Pause » à l'appui, comme
 *   l'original qui pose `setPlaying(true)` sans attendre.
 * @param positionMs la tête de lecture, en millisecondes. Elle avance par tics — c'est la seule
 *   valeur de cet état qui change toute seule.
 * @param ended vrai si la piste chargée est arrivée à sa fin. **C'est la seule chose qui distingue
 *   « en pause au milieu » de « terminée »** : sans elle, un appui sur « Réécouter » demanderait une
 *   reprise, et un lecteur arrivé à la fin y reste — le bouton serait muet.
 * @param error le message à montrer quand le fichier n'a pas pu être lu, ou `null`.
 */
data class RecitationPlayback(
    val uri: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val ended: Boolean = false,
    val error: String? = null,
) {
    /** `true` tant qu'un fichier est chargé, en pause comprise. */
    val isLoaded: Boolean get() = uri != null
}

/**
 * Le lecteur d'une récitation enregistrée, vu par l'écran qui l'écoute.
 *
 * ## Pourquoi un port de plus, à côté d'[AudioOutput]
 *
 * Parce que les deux usages n'ont rien en commun, et que les fondre en une seule interface
 * coûterait plus cher que deux. [AudioOutput] conduit une **séance** : une plage du moushaf, un
 * récitateur, des répétitions, un silence entre deux versets — et sa fin est un *événement* dont
 * dépend le verset suivant. Il ne publie ni position ni durée, et il n'a pas à le faire : la barre
 * de progression n'existe pas dans le mini-lecteur d'enchaînement, et une barre qui avance mal est
 * plus gênante qu'une barre absente.
 *
 * Ici, c'est l'inverse : **un seul fichier**, aucune décision à prendre après sa fin, et trois
 * gestes qui n'ont de sens que par la position — suspendre, reculer de dix secondes, avancer de dix
 * secondes —, plus une barre qui doit suivre la tête de lecture. Étendre [AudioOutput] pour cela
 * obligerait ses deux doublures et son contrôleur à porter une position dont ils n'ont aucun usage,
 * et ferait d'une interface d'enchaînement une interface à tout faire.
 *
 * **Deux lecteurs vivent donc en même temps**, comme dans le client d'origine : celui de
 * l'enchaînement, qui appartient à l'application, et celui-ci, qui appartient à l'écran des
 * récitations. Ils ne jouent pas ensemble — l'écran des récitations n'est pas l'écran de lecture —,
 * et les partager ferait qu'ouvrir une récitation remplacerait le média de la séance en cours.
 *
 * ## Le contrat de [stop]
 *
 * Arrêter **vide** ce qui est chargé : `uri` redevient `null` et la position revient à zéro. Sans
 * cela, rouvrir la même ligne après l'avoir repliée croirait la piste encore chargée et la
 * reprendrait au milieu — alors que la personne vient de la fermer.
 */
interface RecitationPlayer {

    /** Ce que l'écran affiche : la position, l'état, et l'erreur éventuelle. */
    val state: StateFlow<RecitationPlayback>

    /**
     * Charge [uri] et joue depuis le début.
     *
     * **Toujours depuis le début**, même si c'est le même fichier : c'est le geste « Réécouter » de
     * l'original, et c'est aussi le seul moyen de relancer une piste arrivée à sa fin — un lecteur
     * qui a terminé son fichier reste à la fin, et `resume()` n'en sortirait pas.
     */
    fun play(uri: String)

    fun pause()

    /**
     * Reprend là où la tête de lecture s'est arrêtée.
     *
     * **Sans effet sur une piste terminée**, et ce n'est pas au lecteur de le rattraper : c'est la
     * décision de [com.msoumaya.deepseekandroid.core.domain.RecitationsList.playbackAction], qui
     * demande un rechargement dans ce cas. Un lecteur qui devinerait ici rendrait la règle
     * inéprouvable.
     */
    fun resume()

    /** Place la tête de lecture. La valeur vient déjà bornée de l'appelant. */
    fun seekTo(positionMs: Long)

    /** Arrête et **oublie** ce qui est chargé. */
    fun stop()

    /** Libère les ressources. L'objet n'est plus utilisable ensuite. */
    fun release()
}
