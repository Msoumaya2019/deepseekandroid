package com.msoumaya.deepseekandroid.core.audio

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Un lecteur qui n'existe que sur la JVM.
 *
 * Il enregistre ce qu'on lui demande de jouer, et rend la main sur la fin d'un verset quand le
 * test le décide. C'est ce qui permet d'éprouver l'enchaînement — silences compris — sans
 * appareil, sans réseau, et sans attendre 200 millisecondes pour de vrai.
 *
 * Les canaux sont `CONFLATED` pour la même raison que dans [ExoAudioOutput] : une fin émise
 * avant que le contrôleur s'abonne doit l'attendre, pas se perdre.
 */
class FakeAudioOutput : AudioOutput {

    private val completionsChannel = Channel<Unit>(Channel.CONFLATED)
    private val failuresChannel = Channel<String>(Channel.CONFLATED)

    override val completions: Flow<Unit> = completionsChannel.receiveAsFlow()
    override val failures: Flow<String> = failuresChannel.receiveAsFlow()

    /** Les URL jouées, dans l'ordre. */
    val played: MutableList<String> = mutableListOf()

    /** La derniere vitesse demandee. Nommee ainsi, et non `speed`, pour ne pas engendrer
     * `setSpeed(Float)` — qui entrerait en collision avec la methode de [AudioOutput]. */
    var lastSpeed: Float = 1f
    var paused: Boolean = false
    var stopped: Boolean = false
    var released: Boolean = false

    override fun play(url: String, startSeconds: Double?) {
        played.add(url)
    }

    override fun setSpeed(speed: Float) {
        this.lastSpeed = speed
    }

    override fun pause() {
        paused = true
    }

    override fun resume() {
        paused = false
    }

    override fun stop() {
        stopped = true
        // Comme le vrai lecteur : une fin non consommée est oubliée.
        completionsChannel.tryReceive()
    }

    override fun release() {
        released = true
    }

    /** Le verset en cours vient d'atteindre sa fin. */
    fun finish() {
        completionsChannel.trySend(Unit)
    }

    /** Le fichier ne peut pas être lu. */
    fun fail(message: String) {
        failuresChannel.trySend(message)
    }
}
