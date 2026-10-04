package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AudioPosition
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.RepeatMode

/**
 * Ce qu'il faut faire après qu'un verset a fini de jouer.
 *
 * La décision est ici, et non dans le lecteur audio, pour une raison précise : c'est la
 * partie qu'on peut éprouver sans appareil. Le lecteur, lui, ne fait qu'obéir — il joue ce
 * qu'on lui donne, après le silence qu'on lui donne.
 */
sealed interface AudioStep {

    /** Jouer [position] après [waitMs] millisecondes de silence. */
    data class Play(val position: AudioPosition, val waitMs: Int) : AudioStep

    /** La séance est terminée. */
    data object Stop : AudioStep
}

/**
 * Enchaînement des versets d'un passage.
 *
 * Porté depuis `src/PassageAudioPlayer.tsx`. Deux silences se superposent, et les confondre
 * donnerait un enchaînement faux :
 *
 *  - une **marge technique** de [Audio.DEFAULT_AYAH_GAP_MS] ms, toujours présente, même à
 *    zéro réglage : elle laisse au fichier suivant le temps de s'ouvrir et évite que deux
 *    versets se chevauchent ;
 *  - un **silence choisi** par l'utilisateur, qui ne s'applique qu'aux reprises — quand on
 *    répète le même verset, ou quand le passage repart de son début. Entre deux versets
 *    voisins, il ne s'applique pas : on veut entendre la continuité du texte.
 *
 * Le silence effectif est le **plus grand** des deux, jamais leur somme.
 */
object AudioQueue {

    /**
     * La position suivante, avec le silence qui la précède.
     *
     * Rend [AudioStep.Stop] quand le passage est fini : soit parce que le nombre d'écoutes est
     * atteint, soit parce que la fin du passage est atteinte sans répétition illimitée.
     */
    fun next(
        range: Range,
        current: AudioPosition,
        mode: RepeatMode,
        count: Int?,
        autoStop: Boolean = true,
        gapSeconds: Int = AudioSettings.DEFAULT_GAP_SECONDS,
    ): AudioStep {
        val position = Audio.nextAudioPosition(range, current, mode, count, autoStop)
            ?: return AudioStep.Stop
        return AudioStep.Play(
            position = position,
            waitMs = waitBefore(range, current, position, mode, gapSeconds),
        )
    }

    /**
     * Le silence à observer avant de jouer [next], en millisecondes.
     *
     * [next] doit être la position que [Audio.nextAudioPosition] vient de rendre : c'est cette
     * fonction qui décide s'il s'agit d'une reprise, et la décider une seconde fois ailleurs
     * serait le meilleur moyen que les deux avis divergent.
     */
    fun waitBefore(
        range: Range,
        current: AudioPosition,
        next: AudioPosition,
        mode: RepeatMode,
        gapSeconds: Int,
    ): Int {
        val restart = next.verseId == range.start && current.verseId == range.end
        val repeatedVerse = mode == RepeatMode.EACH_VERSE &&
            next.verseId == current.verseId &&
            next.repetition > current.repetition
        val chosen = if (restart || repeatedVerse) {
            gapSeconds.coerceAtLeast(0) * 1000
        } else {
            0
        }
        return maxOf(Audio.DEFAULT_AYAH_GAP_MS, chosen)
    }

    /**
     * Déroule une séance entière, dans l'ordre, jusqu'à l'arrêt.
     *
     * Sert à éprouver l'enchaînement plutôt qu'à le décrire : on vérifie la suite exacte des
     * versets et des silences qu'un auditeur entendrait. [limit] borne le nombre de pas pour
     * qu'une répétition illimitée ne fasse pas tourner le test sans fin.
     */
    fun sequence(
        range: Range,
        mode: RepeatMode,
        count: Int?,
        autoStop: Boolean = true,
        gapSeconds: Int = AudioSettings.DEFAULT_GAP_SECONDS,
        start: AudioPosition = AudioPosition(range.start, 1),
        limit: Int = 1000,
    ): List<AudioStep> {
        val steps = ArrayList<AudioStep>()
        var current = start
        repeat(limit) {
            val step = next(range, current, mode, count, autoStop, gapSeconds)
            steps.add(step)
            if (step is AudioStep.Stop) return steps
            current = (step as AudioStep.Play).position
        }
        error("La séance ne s'arrête pas : $limit pas dépassés.")
    }
}
