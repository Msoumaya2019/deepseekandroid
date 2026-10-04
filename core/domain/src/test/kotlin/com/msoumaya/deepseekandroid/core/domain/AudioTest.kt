package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AudioPosition
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.RepeatMode
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lecture audio.
 *
 * Les URL doivent être **identiques** à celles du client React Native : c'est la seule façon
 * de garantir que le même verset produit le même fichier, et donc que le cache d'un client
 * serve l'autre.
 */
class AudioTest {

    @Before
    fun setUp() = QuranFixture.install()

    @Test
    fun `le recitateur par defaut est Abu Bakr Shatri`() {
        assertEquals("ar.shaatree", Audio.defaultReciter.id)
        assertEquals(200, Audio.DEFAULT_AYAH_GAP_MS)
        assertEquals(7, Audio.reciters.size)
    }

    @Test
    fun `l'URL par defaut est nommee par l'identifiant global du verset`() {
        assertEquals(
            "https://cdn.islamic.network/quran/audio/128/ar.shaatree/1.mp3",
            Audio.verseAudioUrl(1),
        )
        assertEquals(
            "https://cdn.islamic.network/quran/audio/128/ar.shaatree/6236.mp3",
            Audio.verseAudioUrl(6236),
        )
    }

    @Test
    fun `un recitateur a dossier de versets est nomme sourate et verset sur trois chiffres`() {
        val ghamidi = Audio.reciters.first { it.id == "ar.ghamidi" }
        assertEquals(
            "https://everyayah.com/data/Ghamadi_40kbps/001001.mp3",
            Audio.verseAudioUrl(1, ghamidi),
        )
        assertEquals(
            "https://everyayah.com/data/Ghamadi_40kbps/114006.mp3",
            Audio.verseAudioUrl(6236, ghamidi),
        )
    }

    @Test
    fun `une URL de verset hors bornes est refusee`() {
        assertFailsWith<IllegalArgumentException> { Audio.verseAudioUrl(0) }
        assertFailsWith<IllegalArgumentException> { Audio.verseAudioUrl(6237) }
    }

    @Test
    fun `audioRange valide la plage demandee`() {
        assertEquals(Range(5, 12), Audio.audioRange(5, 12))
        assertFailsWith<IllegalArgumentException> { Audio.audioRange(0, 12) }
        assertFailsWith<IllegalArgumentException> { Audio.audioRange(12, 5) }
        assertFailsWith<IllegalArgumentException> { Audio.audioRange(1, 6237) }
    }

    @Test
    fun `le mode chaque verset repete puis avance`() {
        val range = Range(10, 12)
        // Répétition 2 fois par verset.
        assertEquals(AudioPosition(10, 2), Audio.nextAudioPosition(range, AudioPosition(10, 1), RepeatMode.EACH_VERSE, 2))
        assertEquals(AudioPosition(11, 1), Audio.nextAudioPosition(range, AudioPosition(10, 2), RepeatMode.EACH_VERSE, 2))
        assertEquals(AudioPosition(12, 1), Audio.nextAudioPosition(range, AudioPosition(11, 2), RepeatMode.EACH_VERSE, 2))
        // Fin de plage, nombre fini de répétitions : arrêt.
        assertNull(Audio.nextAudioPosition(range, AudioPosition(12, 2), RepeatMode.EACH_VERSE, 2))
    }

    @Test
    fun `une repetition illimitee ne s'arrete jamais`() {
        val range = Range(10, 12)
        // Répétition continue : le compteur du verset courant monte indéfiniment.
        assertEquals(AudioPosition(12, 2), Audio.nextAudioPosition(range, AudioPosition(12, 1), RepeatMode.EACH_VERSE, null))
        // Un seul passage demandé, mais sans arrêt automatique : la plage repart au début.
        assertEquals(
            AudioPosition(10, 1),
            Audio.nextAudioPosition(range, AudioPosition(12, 1), RepeatMode.EACH_VERSE, 1, autoStop = false),
        )
    }

    @Test
    fun `le mode passage avance sans repeter chaque verset`() {
        val range = Range(10, 12)
        assertEquals(AudioPosition(11, 1), Audio.nextAudioPosition(range, AudioPosition(10, 1), RepeatMode.PASSAGE, 1))
        assertEquals(AudioPosition(12, 1), Audio.nextAudioPosition(range, AudioPosition(11, 1), RepeatMode.PASSAGE, 1))
        // Après le dernier verset, la plage entière est rejouée.
        assertEquals(AudioPosition(10, 2), Audio.nextAudioPosition(range, AudioPosition(12, 1), RepeatMode.PASSAGE, 2))
        // Deux passages effectués, arrêt.
        assertNull(Audio.nextAudioPosition(range, AudioPosition(12, 2), RepeatMode.PASSAGE, 2))
    }

    @Test
    fun `une position audio hors plage est refusee`() {
        assertFailsWith<IllegalArgumentException> {
            Audio.nextAudioPosition(Range(10, 12), AudioPosition(9, 1), RepeatMode.PASSAGE, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            Audio.nextAudioPosition(Range(10, 12), AudioPosition(10, 0), RepeatMode.PASSAGE, 1)
        }
    }

    @Test
    fun `parseChapterAudio accepte une piste complete et croissante`() {
        // Les horodatages du fournisseur sont en millisecondes ; le modèle les convertit en secondes.
        val timestamps = (1..7).map { ayah ->
            Audio.RawTimestamp("1:$ayah", (ayah - 1) * 5000.0, ayah * 5000.0)
        }
        val chapter = Audio.parseChapterAudio("https://example.org/1.mp3", timestamps, 1)
        assertEquals("https://example.org/1.mp3", chapter.url)
        assertEquals(7, chapter.verses.size)
        assertEquals(0.0, chapter.verses[1]?.start)
        assertEquals(5.0, chapter.verses[1]?.end)
        assertEquals(30.0, chapter.verses[7]?.start)
        assertEquals(35.0, chapter.verses[7]?.end)
    }

    @Test
    fun `parseChapterAudio refuse une piste incomplete ou non croissante`() {
        // Six versets au lieu de sept.
        val incomplete = (1..6).map { Audio.RawTimestamp("1:$it", (it - 1) * 5000.0, it * 5000.0) }
        assertFailsWith<IllegalStateException> {
            Audio.parseChapterAudio("https://example.org/1.mp3", incomplete, 1)
        }
        // Bornes qui reculent.
        val backwards = listOf(
            Audio.RawTimestamp("1:1", 5.0, 10.0),
            Audio.RawTimestamp("1:2", 1.0, 4.0),
        )
        assertFailsWith<IllegalStateException> {
            Audio.parseChapterAudio("https://example.org/1.mp3", backwards, 1)
        }
        // URL non sécurisée.
        assertFailsWith<IllegalStateException> {
            Audio.parseChapterAudio("http://example.org/1.mp3", emptyList(), 1)
        }
    }

    @Test
    fun `le surlignage avance sans depasser la fin de la plage`() {
        val timestamps = (1..7).map { ayah ->
            Audio.RawTimestamp("1:$ayah", (ayah - 1) * 5000.0, ayah * 5000.0)
        }
        val chapter = Audio.parseChapterAudio("https://example.org/1.mp3", timestamps, 1)
        val range = Range(1, 7)
        assertEquals(1, Audio.continuousAudioPosition(chapter, range, AudioPosition(1, 1), 1.0).verseId)
        assertEquals(3, Audio.continuousAudioPosition(chapter, range, AudioPosition(1, 1), 11.0).verseId)
        assertEquals(7, Audio.continuousAudioPosition(chapter, range, AudioPosition(1, 1), 999.0).verseId)
    }

    @Test
    fun `le libelle d'un verset nomme sa sourate et son verset`() {
        assertEquals("sourate 1, verset 1", Audio.verseAudioLabel(1))
        assertEquals("sourate 114, verset 6", Audio.verseAudioLabel(6236))
        assertTrue(Audio.verseAudioLabel(8).startsWith("sourate 2"))
    }
}
