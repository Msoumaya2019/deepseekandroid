package com.msoumaya.deepseekandroid.core.domain

/**
 * Mesure la durée réellement captée, **pauses exclues**.
 *
 * ## Pourquoi cette classe existe
 *
 * Le client d'origine lit `recorder.getStatus().durationMillis` : `expo-audio` lui donne la durée
 * de l'enregistrement, pauses déjà déduites. Le portage n'a pas cette chance — `MediaRecorder`,
 * l'enregistreur natif d'Android, **n'expose aucune durée**. Il ne dit ni depuis quand il capte,
 * ni combien de temps il a capté. La durée doit donc être mesurée, et cette mesure est une
 * **règle** : elle vit ici, où elle s'éprouve sans appareil, et non dans la classe qui parle au
 * matériel.
 *
 * ## Ce que la mesure doit respecter, et pourquoi
 *
 *  - **Une pause ne compte pas.** Afficher un temps qui continue pendant une pause ferait croire
 *    que la capture n'a pas été suspendue, et la durée écrite dans la ligne serait fausse — or
 *    c'est elle que le relecteur voit avant d'écouter. La durée est donc la **somme des segments**
 *    captés, et non l'écart entre le premier appui et l'arrêt.
 *  - **Pauser deux fois ne compte qu'une fois**, comme reprendre deux fois. Un écran qui reçoit
 *    deux appuis rapprochés — une main qui tremble, un rendu en retard — ne doit pas fabriquer un
 *    segment de durée négative ni un second départ qui doublerait le temps.
 *  - **Un arrêt fige la durée.** L'appelant lit la durée **avant** de libérer le matériel, comme
 *    l'original ; la lire ensuite doit rendre la même chose, et non le temps écoulé depuis.
 *  - **Un nouveau départ repart de zéro.** « Recommencer » ne reconstruit pas l'enregistreur, ni
 *    dans l'original ni ici : une horloge qui garderait son cumul ferait apparaître la récitation
 *    suivante plus longue qu'elle n'est.
 *
 * ## L'horloge
 *
 * [nowMs] est injectable pour que tout ceci soit éprouvable. Celle par défaut est **monotone**
 * (`System.nanoTime`) et non l'heure murale : un réglage d'heure, un passage à l'heure d'été ou
 * une synchronisation réseau ne doivent pas faire reculer un chronomètre, ni le faire bondir.
 */
class RecordingStopwatch(
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
) {

    private var startedAt: Long? = null
    private var accumulatedMs: Long = 0
    private var stopped = true

    /** `true` tant que le chronomètre court — c'est-à-dire hors pause et après un départ. */
    val isRunning: Boolean get() = startedAt != null

    /** Repart de zéro et démarre. */
    fun start() {
        accumulatedMs = 0
        startedAt = nowMs()
        stopped = false
    }

    /** Suspend le cumul. Sans effet si le chronomètre ne court pas. */
    fun pause() {
        val from = startedAt ?: return
        accumulatedMs += nowMs() - from
        startedAt = null
    }

    /** Reprend le cumul là où il s'était arrêté. Sans effet s'il court déjà, ou s'il est arrêté. */
    fun resume() {
        if (startedAt != null || stopped) return
        startedAt = nowMs()
    }

    /** La durée captée jusqu'ici, segment en cours compris. */
    fun elapsedMs(): Long = accumulatedMs + (startedAt?.let { nowMs() - it } ?: 0L)

    /**
     * Arrête la mesure et rend la durée captée.
     *
     * La valeur rendue ne bouge plus ensuite : c'est celle qui part dans la ligne, et l'original
     * la lit au même instant — juste avant de libérer le matériel.
     */
    fun stop(): Long {
        pause()
        stopped = true
        return accumulatedMs
    }
}
