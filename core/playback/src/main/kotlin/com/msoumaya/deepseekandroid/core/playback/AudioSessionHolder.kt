package com.msoumaya.deepseekandroid.core.playback

import com.msoumaya.deepseekandroid.core.audio.AudioOutput
import com.msoumaya.deepseekandroid.core.audio.AudioSessionController
import com.msoumaya.deepseekandroid.core.audio.AudioSessionState
import com.msoumaya.deepseekandroid.core.domain.Audio
import com.msoumaya.deepseekandroid.core.domain.AudioSession
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.Reciter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * La séance d'écoute de l'application — **une seule**, pour toute la vie du processus.
 *
 * Le lecteur créait la sienne dans un `remember` et la libérait dans un `DisposableEffect` :
 * quitter l'écran coupait donc la récitation, et revenir en arrière la faisait repartir du
 * premier verset. C'est la **portée** qui décidait, et elle était celle de l'écran.
 *
 * Ce détenteur retourne la décision : la séance appartient à l'application, exactement comme
 * le client d'origine qui rend son lecteur au niveau de `App` et non dans l'écran de lecture.
 * Un écran qui se ferme ne fait plus que **cesser d'observer** ; il ne libère rien.
 *
 * Ce que ce module ne fait pas encore, et qu'il fera : publier la séance vers l'écran verrouillé
 * et les écouteurs Bluetooth, au moyen d'un `MediaSession` et d'un service d'avant-plan. Tant
 * que ce service n'existe pas, l'écoute survit à l'écran mais **pas** à la mise en arrière-plan
 * du processus — et il faut le dire, plutôt que de le laisser croire.
 *
 * **Le détenteur ne crée pas son `AudioOutput`.** Il le reçoit : construire un lecteur Media3
 * demande un `Context`, donc Android — et cette classe-ci reste ainsi sans dépendance d'écran,
 * éprouvable avec un double. C'est l'assemblage (`:app`) qui fournit le lecteur natif.
 */
class AudioSessionHolder(
    output: AudioOutput,
    scope: CoroutineScope,
    initialReciter: Reciter = Audio.defaultReciter,
) {

    private val controller = AudioSessionController(output, scope, initialReciter)

    /**
     * Ce que le mini-lecteur affiche.
     *
     * Il est **relu** par chaque écran qui s'y abonne, et non recopié : deux écrans ouverts
     * l'un après l'autre voient la même séance, et le second n'a rien à rattraper.
     */
    val state: StateFlow<AudioSessionState> = controller.state

    /**
     * Les réglages appliqués à la séance en cours.
     *
     * Le lecteur les pousse à chaque changement ; le détenteur en garde la dernière valeur pour
     * qu'une reprise — relancer le même passage après l'avoir écouté — n'ait pas à redemander
     * les réglages qu'il vient de recevoir.
     *
     * Déclarés **avant** les méthodes qui les lisent et les écrivent, pour que l'ordre du fichier
     * dise celui de la dépendance.
     */
    var settings: AudioSession = AudioSession()
        private set

    /**
     * Ouvre une séance sur [range] et joue son premier verset.
     *
     * Les réglages sont **retenus ici aussi**, et pas seulement poussés au contrôleur : c'est ce
     * qui donne un sens à la surcharge [start] à un argument. Sans cette mémorisation, une reprise
     * — relancer après avoir écouté — repartirait silencieusement des valeurs par défaut, et
     * rejouerait le passage autrement que la première fois.
     */
    fun start(range: Range, settings: AudioSession) {
        this.settings = settings
        controller.start(range, settings)
    }

    /** Ouvre une séance sur [range] en repartant des derniers réglages connus. */
    fun start(range: Range) = controller.start(range, settings)

    fun pause() = controller.pause()

    fun resume() = controller.resume()

    fun toggle() = controller.toggle()

    /** Ferme la séance. Les fins en attente sont oubliées. */
    fun close() = controller.close()

    fun updateSettings(value: AudioSession) {
        settings = value
        controller.updateSettings(value)
    }

    /** Change de récitateur. Le verset en cours continue ; le suivant vient du nouveau. */
    fun useReciter(value: Reciter) = controller.useReciter(value)

    /**
     * Libère le lecteur natif.
     *
     * À n'appeler **que** lorsque l'application se termine réellement : un écran qui s'en va ne
     * doit plus l'appeler. L'appeler depuis un écran ramènerait exactement le défaut que ce
     * détenteur existe pour corriger.
     */
    fun release() = controller.release()
}
