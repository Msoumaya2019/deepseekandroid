package com.msoumaya.deepseekandroid.core.audio

import com.msoumaya.deepseekandroid.core.domain.Audio
import com.msoumaya.deepseekandroid.core.domain.AudioQueue
import com.msoumaya.deepseekandroid.core.domain.AudioSession
import com.msoumaya.deepseekandroid.core.domain.AudioStep
import com.msoumaya.deepseekandroid.core.model.AudioPosition
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.Reciter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Ce que le mini-lecteur affiche.
 *
 * Volontairement pauvre : une référence de passage, un compteur de répétition, et de quoi
 * savoir si ça joue. Le client d'origine n'affiche pas de barre de progression dans sa forme
 * réduite, et une barre qui avance mal est plus gênante qu'une barre absente.
 */
data class AudioSessionState(
    val range: Range? = null,
    val position: AudioPosition? = null,
    val isPlaying: Boolean = false,
    val isFinished: Boolean = false,
    val error: String? = null,
) {
    /** `true` tant qu'une séance est ouverte, en pause comprise. */
    val isOpen: Boolean get() = range != null
}

/**
 * Conduit une séance d'écoute : quel verset, quand, et après quel silence.
 *
 * Toute la décision vient de [AudioQueue] ; ce qui est ici, c'est le **moment** — attendre le
 * silence, puis demander le verset suivant. Séparer les deux permet d'éprouver la décision
 * sans horloge et l'horloge sans réseau.
 */
class AudioSessionController(
    private val output: AudioOutput,
    private val scope: CoroutineScope,
    initialReciter: Reciter = Audio.defaultReciter,
) {

    private val _state = MutableStateFlow(AudioSessionState())
    val state: StateFlow<AudioSessionState> = _state.asStateFlow()

    private var settings = AudioSession()
    private var reciter = initialReciter

    init {
        // Les deux collecteurs vivent aussi longtemps que le controleur, et non aussi longtemps
        // qu'une seance : les relancer a chaque `start` empilerait des collecteurs qui
        // repondraient tous a la meme fin de verset, et la lecture avancerait de plusieurs
        // versets d'un coup. Quand aucune seance n'est ouverte, [advance] ne fait rien — ce qui
        // draine au passage une fin oubliee.
        scope.launch {
            output.completions.collect { advance() }
        }
        scope.launch {
            output.failures.collect { message ->
                _state.value = _state.value.copy(isPlaying = false, error = message)
            }
        }
    }

    /**
     * Ouvre une séance sur [range] et joue son premier verset.
     *
     * Échoue avec le message du client d'origine si la plage sort du moushaf.
     */
    fun start(range: Range, settings: AudioSession) {
        Audio.audioRange(range.start, range.end)
        close()
        this.settings = settings
        _state.value = AudioSessionState(range = range)
        output.setSpeed(settings.speed)
        play(AudioPosition(range.start, 1))
    }

    fun pause() {
        output.pause()
        _state.value = _state.value.copy(isPlaying = false)
    }

    fun resume() {
        if (_state.value.position == null) return
        output.resume()
        _state.value = _state.value.copy(isPlaying = true, isFinished = false)
    }

    fun toggle() {
        if (_state.value.isPlaying) pause() else resume()
    }

    /** Ferme la séance. Les fins en attente sont oubliées (voir [AudioOutput.stop]). */
    fun close() {
        output.stop()
        _state.value = AudioSessionState()
    }

    /** Applique une vitesse à la lecture en cours, sans interrompre le verset. */
    fun setSpeed(speed: Float) {
        settings = settings.copy(speed = speed)
        output.setSpeed(speed)
    }

    /** Change de récitateur. Le verset en cours continue ; le suivant vient du nouveau. */
    fun useReciter(value: Reciter) {
        reciter = value
    }

    fun release() {
        output.release()
        _state.value = AudioSessionState()
    }

    private fun play(position: AudioPosition) {
        _state.value = _state.value.copy(
            position = position,
            isPlaying = true,
            isFinished = false,
            error = null,
        )
        try {
            output.play(Audio.verseAudioUrl(position.verseId, reciter))
        } catch (error: Exception) {
            _state.value = _state.value.copy(
                isPlaying = false,
                error = error.message ?: "Le verset ne peut pas être chargé.",
            )
        }
    }

    /**
     * Le verset vient de finir : on demande la suite à [AudioQueue], on observe le silence
     * qu'elle impose, puis on joue.
     *
     * Le silence est observé **avant** la demande de lecture, pas après : c'est ce qui produit
     * une vraie pause audible entre deux répétitions, au lieu d'un enchaînement continu suivi
     * d'un silence à la fin.
     */
    private suspend fun advance() {
        val range = _state.value.range ?: return
        val position = _state.value.position ?: return
        val step = AudioQueue.next(
            range = range,
            current = position,
            mode = settings.mode,
            count = settings.resolveCount(),
            autoStop = settings.autoStop,
            gapSeconds = settings.gapSeconds,
        )
        when (step) {
            is AudioStep.Play -> {
                if (step.waitMs > 0) delay(step.waitMs.toLong())
                play(step.position)
            }

            AudioStep.Stop -> {
                _state.value = _state.value.copy(isPlaying = false, isFinished = true)
            }
        }
    }
}
