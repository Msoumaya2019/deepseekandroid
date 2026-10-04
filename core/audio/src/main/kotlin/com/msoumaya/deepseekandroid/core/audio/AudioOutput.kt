package com.msoumaya.deepseekandroid.core.audio

import kotlinx.coroutines.flow.Flow

/**
 * Le lecteur audio, vu par la logique d'enchaînement.
 *
 * L'interface existe pour une seule raison : `ExoPlayer` ne tourne pas sur la JVM, et un
 * enchaînement qu'on ne peut pas éprouver est un enchaînement qu'on découvre faux sur un
 * téléphone — après avoir écouté un verset de trop, ou pas assez.
 *
 * **Contrat de [stop]** : arrêter doit aussi **oublier les fins non encore consommées**. Sans
 * cela, la fin d'un verset de la séance précédente resterait en attente et ferait avancer la
 * séance suivante d'un verset, dès son premier instant.
 */
interface AudioOutput {

    /** Émet une fois par fichier arrivé à sa fin. */
    val completions: Flow<Unit>

    /** Émet le message à montrer quand un fichier ne peut pas être lu. */
    val failures: Flow<String>

    /**
     * Joue [url].
     *
     * [startSeconds] place la tête de lecture ; `null` joue depuis le début. Un fichier déjà
     * terminé et rejoué à l'identique doit être rembobiné, sinon la seconde écoute ne produit
     * aucun son : c'est pourquoi la position est toujours donnée, même à zéro.
     */
    fun play(url: String, startSeconds: Double? = null)

    fun setSpeed(speed: Float)

    fun pause()

    fun resume()

    /** Arrête la lecture et oublie les fins en attente. */
    fun stop()

    /** Libère les ressources. L'objet n'est plus utilisable ensuite. */
    fun release()
}
