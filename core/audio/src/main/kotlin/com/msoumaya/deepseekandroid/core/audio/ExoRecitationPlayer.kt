package com.msoumaya.deepseekandroid.core.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Le lecteur réel d'une récitation enregistrée, sur `ExoPlayer`.
 *
 * ## Trois choix qui se recopient mal
 *
 * **Un second `ExoPlayer`, et pas celui de l'enchaînement.** Le client d'origine a lui aussi deux
 * lecteurs : celui de `App`, qui conduit une séance de versets, et celui de `RecitationsScreen`,
 * qui joue un fichier. Les fondre ferait qu'ouvrir une récitation **remplacerait le média** de la
 * séance en cours — `play` fait `setMediaItem`, qui écarte ce qui jouait. Les deux écrans ne
 * s'ouvrent pas ensemble, donc deux lecteurs ne coûtent rien à l'usage, et évitent une
 * interférence que rien ne signalerait.
 *
 * **Un chemin de fichier nu est jouable, et c'est vérifié.** `LocalRecitation.uri` porte un
 * **chemin** (`RecitationStore` écrit `target.absolutePath`), pas une URI. Avant d'écrire
 * `MediaItem.fromUri`, la question a été tranchée dans l'artefact : `Util.isLocalFileUri` de
 * media3 1.9.4 se lit
 *
 * ```
 * String scheme = uri.getScheme();
 * return TextUtils.isEmpty(scheme) || Objects.equals("file", scheme);
 * ```
 *
 * — donc un schéma **absent** compte comme un fichier local, et `DefaultDataSource` ouvre alors le
 * `FileDataSource`. Une URL `https` prend l'autre branche. Les deux formes de l'application
 * passent par le même appel.
 *
 * **La position avance par tics.** `ExoPlayer` ne publie pas sa tête de lecture en continu : il
 * faut la lui demander. Un tic de [TICK_MS] la relève, exactement comme le `setInterval(500)` du
 * client d'origine — mais **dans le lecteur** au lieu de l'écran, parce que la position est une
 * propriété du lecteur et non de la vue. Le tic s'arrête dès qu'il n'a plus rien à dire : en pause,
 * à la fin, sur une erreur, et à l'arrêt. Une boucle qui tournerait pendant une pause publierait
 * deux fois par seconde une valeur identique, et ferait recomposer l'écran pour rien.
 */
class ExoRecitationPlayer(context: Context) : RecitationPlayer {

    /**
     * Le fil principal, et non un fil d'arrière-plan : `ExoPlayer` exige d'être piloté depuis le
     * `Looper` qui l'a construit, et les tics lisent `currentPosition` sur ce même fil.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(RecitationPlayback())

    override val state: StateFlow<RecitationPlayback> = _state.asStateFlow()

    private var ticker: Job? = null

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        // Mêmes attributs que le lecteur d'enchaînement : c'est de la parole, et le focus audio est
        // délégué à ExoPlayer — un appel entrant met en pause sans qu'on ait à l'écrire.
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
                    if (playbackState != Player.STATE_ENDED) return
                    // La piste est finie : le tic n'a plus rien à relever, et `ended` est la seule
                    // chose qui distingue « en pause au milieu » de « terminée ».
                    stopTicker()
                    publier(ended = true, playing = false)
                }

                override fun onPlayerError(error: PlaybackException) {
                    stopTicker()
                    _state.value = _state.value.copy(
                        playing = false,
                        error = LOAD_FAILURE,
                    )
                }
            },
        )
    }

    override fun play(uri: String) {
        // L'état est posé **avant** la préparation : l'écran doit basculer sur « Pause » à
        // l'appui, comme l'original qui pose `setPlaying(true)` sans attendre la première image
        // sonore. Un état qui attendrait la lecture réelle ferait clignoter le bouton pendant la
        // mise en mémoire tampon.
        _state.value = RecitationPlayback(uri = uri, playing = true)
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        // Le rembobinage est explicite : un lecteur qui a terminé son fichier reste à la fin, et
        // rejouer la même piste ne produirait alors aucun son.
        exoPlayer.seekTo(0L)
        exoPlayer.playWhenReady = true
        startTicker()
    }

    override fun pause() {
        exoPlayer.playWhenReady = false
        stopTicker()
        publier(playing = false)
    }

    override fun resume() {
        exoPlayer.playWhenReady = true
        // `ended` n'est **pas** remis à faux ici : sur une piste réellement terminée, ce serait
        // mentir sur l'état, et c'est justement ce mensonge qui rendrait le bouton muet. Le
        // domaine ne demande jamais une reprise dans ce cas — il demande un rechargement.
        _state.value = _state.value.copy(playing = true, error = null)
        startTicker()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs.coerceAtLeast(0L))
        // Un déplacement après la fin rend la piste jouable de nouveau : sans cela, `ended`
        // resterait vrai et l'appui suivant rechargerait au lieu de reprendre.
        publier(ended = false)
    }

    override fun stop() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        stopTicker()
        // `uri` redevient nul : rouvrir la même ligne ne doit pas croire la piste encore chargée.
        _state.value = RecitationPlayback()
    }

    override fun release() {
        stopTicker()
        scope.cancel()
        exoPlayer.release()
        _state.value = RecitationPlayback()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                publier()
                delay(TICK_MS)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    /**
     * Relève la tête de lecture et l'état, et les publie.
     *
     * `playing` suit `playWhenReady` et non `isPlaying` : pendant la mise en mémoire tampon,
     * `isPlaying` est faux alors que la lecture a bel et bien été demandée, et suivre `isPlaying`
     * ferait clignoter le bouton à chaque appui. Une piste terminée coupe le son même si
     * `playWhenReady` est resté vrai.
     */
    private fun publier(ended: Boolean = _state.value.ended, playing: Boolean? = null) {
        _state.value = _state.value.copy(
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
            playing = playing ?: (exoPlayer.playWhenReady && !ended),
            ended = ended,
        )
    }

    private companion object {
        /** Le pas du client d'origine : `setInterval(..., 500)`. */
        const val TICK_MS = 500L

        const val LOAD_FAILURE = "Lecture impossible : le fichier audio est introuvable."
    }
}
