package com.msoumaya.deepseekandroid.core.audio

import com.msoumaya.deepseekandroid.core.domain.RecordingStopwatch

/**
 * L'enregistreur, vu par l'écran de récitation.
 *
 * L'interface existe pour la même raison que [AudioOutput] : `MediaRecorder` ne tourne pas sur la
 * JVM. Un écran qui parlerait directement à `MediaRecorder` ne serait éprouvable que sur un
 * téléphone, et le seul moyen de le vérifier serait de parler dans un micro.
 *
 * ## Ce que l'interface ne décide pas
 *
 * Le format produit, le débit, les canaux et l'horloge viennent de
 * [com.msoumaya.deepseekandroid.core.domain.RecitationRecorder] — c'est-à-dire du domaine, où ils
 * sont éprouvés sans appareil. Une implémentation qui choisirait elle-même son extension
 * produirait un fichier que le dépôt nommerait faux, et rien ne le dirait avant l'écoute.
 *
 * ## Contrat d'arrêt
 *
 * [stop] rend un fichier **complet** et sa durée ; [cancel] jette le fichier. Les deux libèrent le
 * matériel. Après l'un ou l'autre, l'objet est de nouveau disponible pour un nouvel
 * enregistrement : l'original enchaîne « Recommencer » sans reconstruire son enregistreur, et une
 * implémentation qui garderait un `MediaRecorder` libéré lèverait au geste suivant.
 */
interface AudioRecorder {

    /** `true` tant que la capture est en cours, pause comprise. */
    val isRecording: Boolean

    /**
     * Demande le microphone et commence à capter.
     *
     * @return `false` si le matériel refuse — permission absente, micro déjà pris par un appel,
     *   encodage indisponible. L'appelant doit alors le dire à la personne, et non lever : un
     *   refus du système n'est pas une panne de l'application.
     *
     * Après [release], l'objet n'est plus utilisable et l'appel **lève** : c'est une faute de
     *   programmation, et non un refus du matériel — les deux ne se traitent pas pareil.
     */
    fun start(): Boolean

    /** Suspend la capture sans fermer le fichier. Sans effet si rien n'est en cours. */
    fun pause()

    /** Reprend la capture suspendue. Sans effet si la capture n'est pas suspendue. */
    fun resume()

    /**
     * Arrête la capture et rend le fichier, avec la durée **réellement captée**.
     *
     * @return `null` si rien n'a pu être gardé. `MediaRecorder` refuse un arrêt immédiat — moins
     *   d'une seconde de son produit un fichier illisible —, et lever ici ferait tomber l'écran
     *   pour un geste légitime : un appui suivi d'un regret.
     */
    fun stop(): RecordedAudio?

    /** Arrête la capture et jette le fichier. Sans effet si rien n'est en cours. */
    fun cancel()

    /** Libère le matériel. L'objet n'est plus utilisable ensuite. */
    fun release()
}

/**
 * Un enregistrement terminé, tel qu'il sort de l'enregistreur.
 *
 * @param path chemin **absolu** du fichier, sans schéma `file://`. C'est la convention du portage
 *   (voir `RecitationStore`) : un préfixe de schéma construirait un `File` qui n'existe pas, et
 *   silencieusement, puisqu'un `File` inexistant ne lève qu'à la lecture.
 * @param durationMs durée captée, **pauses exclues**. Elle vient de [RecordingStopwatch] et non du
 *   matériel : `MediaRecorder` n'expose aucune durée.
 */
data class RecordedAudio(val path: String, val durationMs: Long)
