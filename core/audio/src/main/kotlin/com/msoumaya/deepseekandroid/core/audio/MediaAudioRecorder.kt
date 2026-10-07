package com.msoumaya.deepseekandroid.core.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.msoumaya.deepseekandroid.core.domain.RecitationRecorder
import com.msoumaya.deepseekandroid.core.domain.RecordingStopwatch
import java.io.File
import java.util.UUID

/**
 * L'enregistreur natif d'Android, bâti sur `MediaRecorder`.
 *
 * ## Ce que cette classe fait, et ce qu'elle ne fait pas
 *
 * Elle **exécute** : elle demande le matériel, écrit dans un fichier, et rend la durée captée.
 * Elle ne décide rien. Le format, le débit, les canaux, la fréquence et l'extension viennent tous
 * de [RecitationRecorder] — c'est-à-dire du domaine, où ils sont éprouvés sans appareil. Une
 * implémentation qui choisirait elle-même son extension produirait un fichier que
 * `Recitations.extensionFor` nommerait faux, et le dépôt enverrait au serveur un type MIME qui ne
 * correspond pas aux octets — sans que rien ne le signale avant l'écoute.
 *
 * ## Le fichier est temporaire, et c'est voulu
 *
 * L'enregistrement va dans [directory], que l'appelant pointe sur le cache de l'application. C'est
 * exactement ce que fait le client d'origine : `expo-audio` écrit dans un dossier que le système
 * peut effacer. La copie vers le dossier de l'application est faite **ensuite**, par
 * `RecitationStore.add` — et c'est cette copie qui fait survivre une récitation non déposée à un
 * redémarrage. Écrire directement dans le dossier définitif priverait ce portage d'un filet : un
 * enregistrement jeté après coup laisserait un fichier orphelin que plus rien ne référence.
 *
 * ## Deux pièges du matériel, et ce qui les traite
 *
 *  - **`stop()` lève quand rien n'a été capté.** `MediaRecorder` refuse d'écrire un fichier de
 *    moins d'une seconde et signale l'échec par une `RuntimeException`. L'appelant reçoit alors
 *    `null`, et l'écran dit que l'enregistrement n'a rien donné — au lieu de tomber pour un appui
 *    suivi d'un regret.
 *  - **`MediaRecorder` n'a pas d'horloge.** Contrairement à `expo-audio`, qui expose
 *    `getStatus().durationMillis`, le matériel d'Android ne dit nulle part combien de temps il a
 *    capté. La durée est donc **mesurée** par [RecordingStopwatch], et les pauses en sont
 *    exclues : afficher un temps qui continue pendant une pause ferait croire que la capture n'a
 *    pas été suspendue.
 *
 * ## Ce que l'appelant doit faire avant d'appeler [start]
 *
 * Couper la séance d'écoute. Le client d'origine appelle `stopActiveAudio()` puis met le lecteur
 * en pause avant d'ouvrir le microphone : sur Android, deux flux audio simultanés se disputent
 * l'entrée, et une récitation lue pendant qu'on enregistre serait captée par le micro. C'est une
 * décision d'ordonnancement, et elle appartient à l'écran — cette classe ne connaît pas le
 * lecteur, et lui donner une prise sur lui ferait deux endroits d'où la lecture s'arrête.
 *
 * ## Ce qui n'est pas éprouvé ici, et ne peut pas l'être
 *
 * Que le matériel accepte cette configuration, que la permission soit accordée, que la pause
 * conserve le fichier lisible, et que les octets produits soient réellement de l'AAC dans un
 * conteneur MPEG-4. Ces quatre points se prouvent sur un appareil ; ils sont dits comme tels dans
 * `README.md` plutôt que supposés verts.
 */
class MediaAudioRecorder(
    private val context: Context,
    private val directory: File,
    private val stopwatch: RecordingStopwatch = RecordingStopwatch(),
    private val newName: () -> String = { UUID.randomUUID().toString() },
) : AudioRecorder {

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var released = false

    override val isRecording: Boolean get() = recorder != null

    override fun start(): Boolean {
        check(!released) { "L'enregistreur a été libéré." }
        if (recorder != null) return false

        directory.mkdirs()
        val target = File(directory, newName() + RecitationRecorder.RECORDED_EXTENSION)
        val created = build()

        return try {
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setAudioChannels(RecitationRecorder.CHANNELS)
            created.setAudioSamplingRate(RecitationRecorder.SAMPLE_RATE)
            created.setAudioEncodingBitRate(RecitationRecorder.BIT_RATE)
            created.setOutputFile(target)
            created.prepare()
            created.start()

            recorder = created
            file = target
            stopwatch.start()
            true
        } catch (error: Exception) {
            // Un demarrage refuse laisse un objet a moitie configure et un fichier vide : les deux
            // sont jetes ici, sans quoi le geste suivant partirait d'un etat incoherent.
            runCatching { created.release() }
            target.delete()
            recorder = null
            file = null
            false
        }
    }

    override fun pause() {
        val current = recorder ?: return
        runCatching { current.pause() }
        stopwatch.pause()
    }

    override fun resume() {
        val current = recorder ?: return
        runCatching { current.resume() }
        stopwatch.resume()
    }

    override fun stop(): RecordedAudio? {
        val current = recorder ?: return null
        val target = file

        // La duree est lue **avant** l'arret, comme dans le client d'origine : apres, l'objet est
        // libere et il ne reste plus rien a lire.
        val durationMs = stopwatch.stop()

        return try {
            current.stop()
            current.release()
            if (target == null) {
                null
            } else {
                RecordedAudio(target.absolutePath, durationMs)
            }
        } catch (error: RuntimeException) {
            // `MediaRecorder.stop()` leve quand rien n'a ete capte. Le fichier est illisible : il
            // est efface, et l'appelant recoit `null` plutot qu'une exception.
            runCatching { current.release() }
            target?.delete()
            null
        } finally {
            recorder = null
            file = null
        }
    }

    override fun cancel() {
        val current = recorder ?: return
        runCatching { current.stop() }
        runCatching { current.release() }
        file?.delete()
        recorder = null
        file = null
        stopwatch.stop()
    }

    override fun release() {
        cancel()
        released = true
    }

    /**
     * Le `MediaRecorder` de la version d'Android qui l'accueille.
     *
     * Le constructeur qui prend un `Context` n'existe qu'à partir d'Android 12 : c'est celui qu'il
     * faut, parce qu'il rattache l'enregistreur à l'application et permet au système de couper une
     * capture qui survit à son propriétaire. En dessous, il n'y a que le constructeur sans
     * argument — déprécié depuis, mais le seul disponible, et `minSdk` est 26.
     */
    @Suppress("DEPRECATION")
    private fun build(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
}
