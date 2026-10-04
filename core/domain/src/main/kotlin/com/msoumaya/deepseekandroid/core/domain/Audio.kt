package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AudioPosition
import com.msoumaya.deepseekandroid.core.model.AudioSegment
import com.msoumaya.deepseekandroid.core.model.ChapterAudio
import com.msoumaya.deepseekandroid.core.model.ChapterVerseSpan
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.Reciter
import com.msoumaya.deepseekandroid.core.model.RepeatMode

/**
 * Règles de lecture audio.
 *
 * Porté depuis `src/core/audio.ts`. Le client d'origine ne télécharge rien pour désigner un
 * verset : il construit une URL déterministe. Ce fichier reproduit exactement les deux
 * familles d'URL (voir [Reciter]) afin que les mêmes fichiers soient servis aux deux clients.
 */
object Audio {

    /**
     * Racine du CDN par verset. Reprise de `src/data/ipa-audio-source.json`
     * (`baseUrl: https://cdn.islamic.network/quran/audio`).
     */
    const val BASE_URL: String = "https://cdn.islamic.network/quran/audio"

    /** Racine du second fournisseur, utilisé quand un récitateur déclare `verseFolder`. */
    const val EVERYAYAH_BASE_URL: String = "https://everyayah.com/data"

    val reciters: List<Reciter> get() = Texts.reciters

    val defaultReciter: Reciter get() = Texts.defaultReciter

    const val DEFAULT_AYAH_GAP_MS: Int = Texts.DEFAULT_AYAH_GAP_MS

    /**
     * URL du fichier audio d'un verset.
     *
     * Deux formes, exactement comme le client d'origine :
     *  - `verseFolder` présent → `…/data/<dossier>/SSSAAA.mp3`, numéros de sourate et de
     *    verset sur trois chiffres ;
     *  - sinon → `<BASE_URL>/<débit>/<identifiant récitateur>/<id global>.mp3`.
     */
    fun verseAudioUrl(id: Int, reciter: Reciter = defaultReciter): String {
        require(id in 1..Quran.verses.size) { "Verset audio invalide." }
        val folder = reciter.verseFolder
        if (folder != null) {
            val verse = Quran.verseAt(id)
            val surah = verse.surah.toString().padStart(3, '0')
            val ayah = verse.ayah.toString().padStart(3, '0')
            return "$EVERYAYAH_BASE_URL/$folder/$surah$ayah.mp3"
        }
        return "$BASE_URL/${reciter.bitrate}/${reciter.id}/$id.mp3"
    }

    /** Segment audio d'un verset. Le client d'origine n'utilise pas encore les bornes. */
    fun resolveAudioSegment(id: Int, reciter: Reciter = defaultReciter): AudioSegment {
        require(id in 1..Quran.verses.size) { "Verset audio invalide." }
        return AudioSegment(url = verseAudioUrl(id, reciter))
    }

    /** Construit une plage valide, ou échoue avec le message du client d'origine. */
    fun audioRange(start: Int, end: Int): Range {
        require(start >= 1 && end <= Quran.verses.size && start <= end) {
            "Choisis une plage de versets valide."
        }
        return Range(start, end)
    }

    /**
     * Décode les horodatages d'un fichier audio de sourate entière.
     *
     * Lève une erreur si l'URL n'est pas HTTPS, si les bornes ne sont pas strictement
     * croissantes, ou si le nombre de versets ne correspond pas à celui de la sourate :
     * une piste incomplète produirait un surlignage faux, mieux vaut ne rien afficher.
     */
    fun parseChapterAudio(
        audioUrl: String?,
        timestamps: List<RawTimestamp>,
        chapter: Int,
    ): ChapterAudio {
        val surah = Quran.surahs.getOrNull(chapter - 1)
            ?: error("Timestamps audio absents.")
        if (audioUrl == null || !audioUrl.startsWith("https://")) {
            error("Timestamps audio absents.")
        }
        val timings = LinkedHashMap<Int, ChapterVerseSpan>()
        var previous = 0.0
        for (t in timestamps) {
            val key = t.verseKey
            val parts = key.split(":")
            if (parts.size != 2) error("Timestamps audio invalides.")
            val s = parts[0].toIntOrNull() ?: error("Timestamps audio invalides.")
            val a = parts[1].toIntOrNull() ?: error("Timestamps audio invalides.")
            val id = Quran.verseId(s, a) ?: error("Timestamps audio invalides.")
            if (s != chapter || timings.containsKey(id)) error("Timestamps audio invalides.")
            if (t.from.isNaN() || t.to.isNaN()) error("Timestamps audio invalides.")
            if (t.from < previous || t.to <= t.from) error("Timestamps audio invalides.")
            timings[id] = ChapterVerseSpan(t.from / 1000.0, t.to / 1000.0)
            previous = t.to
        }
        if (timings.size != surah.count) error("Timestamps incomplets.")
        return ChapterAudio(url = audioUrl, verses = timings)
    }

    /** Une entrée brute de la piste d'une sourate, avant validation. */
    data class RawTimestamp(val verseKey: String, val from: Double, val to: Double)

    /**
     * Avance le surlignage dans une piste continue.
     *
     * Ne déplace jamais la source audio entre deux versets contigus : seul le repère visuel
     * progresse. C'est ce que fait le client d'origine, et cela évite les micro-coupures.
     */
    fun continuousAudioPosition(
        timeline: ChapterAudio,
        range: Range,
        position: AudioPosition,
        time: Double,
    ): AudioPosition {
        var id = position.verseId
        while (id < range.end) {
            val next = timeline.verses[id + 1] ?: break
            if (time < next.start) break
            id++
        }
        return position.copy(verseId = id)
    }

    /**
     * Position suivante selon le mode de répétition.
     *
     * `count` peut valoir `null` pour une répétition illimitée (ce que le client d'origine
     * exprime par `'continuous'`). `autoStop = false` produit le même effet.
     */
    fun nextAudioPosition(
        range: Range,
        current: AudioPosition,
        mode: RepeatMode,
        count: Int?,
        autoStop: Boolean = true,
    ): AudioPosition? {
        audioRange(range.start, range.end)
        require(current.verseId in range.start..range.end && current.repetition >= 1) {
            "Position audio invalide."
        }
        val unlimited = count == null || !autoStop
        val limit = if (count == null) 1 else maxOf(1, count)

        if (mode == RepeatMode.EACH_VERSE) {
            if (count == null) return current.copy(repetition = current.repetition + 1)
            if (current.repetition < limit) return current.copy(repetition = current.repetition + 1)
            if (current.verseId < range.end) return AudioPosition(current.verseId + 1, 1)
            return if (unlimited) AudioPosition(range.start, 1) else null
        }

        if (current.verseId < range.end) return AudioPosition(current.verseId + 1, current.repetition)
        if (unlimited || current.repetition < limit) return AudioPosition(range.start, current.repetition + 1)
        return null
    }

    /** Libellé lisible d'un verset, pour le mini-lecteur. */
    fun verseAudioLabel(id: Int): String {
        val v = Quran.verseAt(id)
        return "sourate ${v.surah}, verset ${v.ayah}"
    }
}
