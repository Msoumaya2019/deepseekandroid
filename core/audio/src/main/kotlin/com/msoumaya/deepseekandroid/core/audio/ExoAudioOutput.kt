package com.msoumaya.deepseekandroid.core.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Le lecteur réel, sur `ExoPlayer`.
 *
 * Trois choix méritent d'être écrits, parce qu'ils se recopient mal :
 *
 *  - **Les canaux sont `CONFLATED`, et pas des `SharedFlow`.** Un `SharedFlow` sans abonné
 *    **perd** ce qu'on lui envoie ; le contrôleur, lui, s'abonne au moment où il démarre. Un
 *    verset très court pourrait donc finir avant que quiconque écoute, et la séance
 *    s'arrêterait sur un verset qui a bel et bien été joué. Un canal, lui, garde la valeur
 *    jusqu'à ce qu'on la prenne.
 *  - **La prise de focus audio est déléguée à `ExoPlayer`** (`handleAudioFocus = true`), ce qui
 *    remplace le service `audioFocus.ts` du client d'origine : un seul lecteur existe, et une
 *    interruption — appel, autre application — met en pause sans qu'on ait à l'écrire.
 *  - **`setHandleAudioBecomingNoisy(true)`** met en pause quand on débranche le casque. Sans
 *    cela, la récitation sort du haut-parleur dans le bus.
 *
 * Le mode silencieux du téléphone n'a rien à demander ici : sur Android il ne concerne pas le
 * flux multimédia. C'est une différence avec iOS, où `playsInSilentMode` devait être demandé.
 *
 * **Le lecteur est exposé** ([AudioOutputWithPlayer]) parce qu'un `MediaSession` se construit
 * autour d'un `Player`, et non autour de cette interface : c'est le seul moyen de publier la
 * séance vers l'écran verrouillé sans reconstruire un second ExoPlayer — deux lecteurs
 * joueraient la même récitation, l'un par-dessus l'autre.
 */
class ExoAudioOutput(context: Context) : AudioOutputWithPlayer {

    private val completionsChannel = Channel<Unit>(Channel.CONFLATED)
    private val failuresChannel = Channel<String>(Channel.CONFLATED)

    override val completions: Flow<Unit> = completionsChannel.receiveAsFlow()
    override val failures: Flow<String> = failuresChannel.receiveAsFlow()

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */ true,
        )
        setHandleAudioBecomingNoisy(true)
        addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) completionsChannel.trySend(Unit)
                }

                override fun onPlayerError(error: PlaybackException) {
                    failuresChannel.trySend(LOAD_FAILURE)
                }
            },
        )
    }

    /** Le lecteur publié : ce que le `MediaSession` de `core:playback` reçoit. */
    override val player: Player get() = exoPlayer

    override fun play(url: String, startSeconds: Double?) {
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        // Le rembobinage est explicite, même à zéro : un lecteur qui a terminé son fichier reste
        // à la fin, et répéter le même verset ne produirait alors aucun son.
        exoPlayer.seekTo(((startSeconds ?: 0.0) * 1000.0).toLong())
        exoPlayer.playWhenReady = true
    }

    override fun setSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
    }

    override fun pause() {
        exoPlayer.playWhenReady = false
    }

    override fun resume() {
        exoPlayer.playWhenReady = true
    }

    override fun stop() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        // Une fin non encore consommée ferait avancer la séance suivante d'un verset.
        completionsChannel.tryReceive()
    }

    override fun release() {
        exoPlayer.release()
    }

    private companion object {
        const val LOAD_FAILURE = "Le verset ne peut pas être chargé. Vérifie ta connexion et réessaie."
    }
}
