package com.msoumaya.deepseekandroid.core.playback

import com.msoumaya.deepseekandroid.core.audio.AudioOutput
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Un lecteur natif de laboratoire.
 *
 * Il ne joue rien : il **retient** ce qu'on lui demande, dans l'ordre. C'est ce qui permet de
 * vérifier ce qu'un écran a réellement commandé sans appareil, sans Media3 et sans réseau.
 *
 * Les canaux sont `CONFLATED`, comme dans le vrai lecteur : une fin émise avant que le
 * détenteur s'abonne doit l'attendre, pas se perdre.
 */
internal class FakeAudioOutput : AudioOutput {

    private val completionsChannel = Channel<Unit>(Channel.CONFLATED)
    private val failuresChannel = Channel<String>(Channel.CONFLATED)

    override val completions: Flow<Unit> = completionsChannel.receiveAsFlow()
    override val failures: Flow<String> = failuresChannel.receiveAsFlow()

    /** Les URL jouées, dans l'ordre. */
    val played: MutableList<String> = mutableListOf()

    /** La dernière vitesse demandée. Ce nom évite le `setSpeed(Float)` qu'engendrerait `speed`. */
    var lastSpeed: Float = 1f

    /** Le nombre de fois où chaque commande a été demandée, pour distinguer « transmis » de « transmis deux fois ». */
    var pauses: Int = 0
    var resumes: Int = 0
    var stops: Int = 0
    var releases: Int = 0

    override fun play(url: String, startSeconds: Double?) {
        played.add(url)
    }

    override fun setSpeed(speed: Float) {
        this.lastSpeed = speed
    }

    override fun pause() {
        pauses++
    }

    override fun resume() {
        resumes++
    }

    override fun stop() {
        stops++
        // Comme le vrai lecteur : une fin non consommée est oubliée.
        completionsChannel.tryReceive()
    }

    override fun release() {
        releases++
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
