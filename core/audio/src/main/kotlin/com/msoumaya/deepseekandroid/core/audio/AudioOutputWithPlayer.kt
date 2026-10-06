package com.msoumaya.deepseekandroid.core.audio

import androidx.media3.common.Player

/**
 * Le lecteur qui **accepte d'être publié**.
 *
 * Publier une séance vers l'écran verrouillé, les écouteurs Bluetooth ou le volant suppose un
 * `MediaSession`, et un `MediaSession` se construit autour d'un `Player` — pas autour d'un
 * `AudioOutput`. Le lecteur réel a donc un `Player` à donner ; un double de test, lui, n'en a
 * aucun, et n'a aucune raison d'en fabriquer un.
 *
 * D'où cette **sous-interface**, et non un membre ajouté à [AudioOutput] : ajouter
 * `val player: Player` à l'interface principale obligerait chaque double — il y en a un par
 * banc — à rendre un objet media3 dont il ne fait rien, et ferait dépendre tous les tests
 * audio du bytecode d'ExoPlayer. Ici, seul [ExoAudioOutput] la déclare, et le type reste
 * demandable par ceux qui en ont besoin :
 *
 * ```kotlin
 * val published = output as? AudioOutputWithPlayer
 * ```
 *
 * `player` n'est **pas** nullable : une instance de cette interface garantit qu'un lecteur
 * existe. C'est ce que le constructeur de `MediaSession` exige.
 */
interface AudioOutputWithPlayer : AudioOutput {

    /** Le lecteur sous-jacent, à donner au `MediaSession`. */
    val player: Player
}
