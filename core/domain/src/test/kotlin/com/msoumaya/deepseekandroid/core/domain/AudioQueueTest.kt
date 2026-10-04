package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AudioPosition
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.RepeatMode
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Enchaînement des versets.
 *
 * Le point délicat est le silence : deux règles se superposent, et les confondre donne un
 * enchaînement faux — soit deux versets qui se chevauchent, soit un silence de dix secondes
 * entre deux versets voisins que personne n'a demandé.
 */
class AudioQueueTest {

    @Before
    fun setUp() = QuranFixture.install()

    private val range = Range(1, 3)

    private fun play(verseId: Int, repetition: Int, waitMs: Int) =
        AudioStep.Play(AudioPosition(verseId, repetition), waitMs)

    @Test
    fun `la marge technique separe deux versets voisins meme sans silence choisi`() {
        val step = AudioQueue.next(range, AudioPosition(1, 1), RepeatMode.PASSAGE, 3, true, 0)
        assertEquals(play(2, 1, 200), step)
    }

    @Test
    fun `le silence choisi ne s'applique pas entre deux versets voisins`() {
        // Dix secondes choisies ne doivent pas couper la continuite du texte.
        val step = AudioQueue.next(range, AudioPosition(1, 1), RepeatMode.PASSAGE, 3, true, 10)
        assertEquals(play(2, 1, 200), step)
    }

    @Test
    fun `repeter le meme verset applique le silence choisi`() {
        val step = AudioQueue.next(range, AudioPosition(2, 1), RepeatMode.EACH_VERSE, 3, true, 5)
        assertEquals(play(2, 2, 5000), step)
    }

    @Test
    fun `revenir au debut du passage applique le silence choisi`() {
        val step = AudioQueue.next(range, AudioPosition(3, 1), RepeatMode.PASSAGE, 3, true, 10)
        assertEquals(play(1, 2, 10000), step)
    }

    @Test
    fun `le silence effectif est le plus grand des deux, jamais leur somme`() {
        // Un silence choisi de 2 s depasse la marge technique : il la remplace.
        assertEquals(
            2000,
            AudioQueue.waitBefore(
                range,
                AudioPosition(2, 1),
                AudioPosition(2, 2),
                RepeatMode.EACH_VERSE,
                2,
            ),
        )
        // Sans reprise, la marge technique reste seule.
        assertEquals(
            200,
            AudioQueue.waitBefore(
                range,
                AudioPosition(1, 1),
                AudioPosition(2, 1),
                RepeatMode.PASSAGE,
                10,
            ),
        )
    }

    @Test
    fun `un silence negatif ne retire pas la marge technique`() {
        assertEquals(
            200,
            AudioQueue.waitBefore(
                range,
                AudioPosition(2, 1),
                AudioPosition(2, 2),
                RepeatMode.EACH_VERSE,
                -5,
            ),
        )
    }

    @Test
    fun `le mode passage avance sans toucher au compteur de repetitions`() {
        assertEquals(
            play(2, 2, 200),
            AudioQueue.next(range, AudioPosition(1, 2), RepeatMode.PASSAGE, 5, true, 0),
        )
    }

    @Test
    fun `le mode chaque verset repete le verset avant d'avancer`() {
        // Deux ecoutes du verset 1, puis on avance et le compteur repart a 1.
        assertEquals(
            play(1, 2, 200),
            AudioQueue.next(range, AudioPosition(1, 1), RepeatMode.EACH_VERSE, 2, true, 0),
        )
        assertEquals(
            play(2, 1, 200),
            AudioQueue.next(range, AudioPosition(1, 2), RepeatMode.EACH_VERSE, 2, true, 0),
        )
    }

    @Test
    fun `la fin du passage arrete quand le nombre d'ecoutes est atteint`() {
        assertEquals(
            AudioStep.Stop,
            AudioQueue.next(range, AudioPosition(3, 3), RepeatMode.PASSAGE, 3, true, 0),
        )
    }

    @Test
    fun `une repetition illimitee ne s'arrete jamais`() {
        assertIs<AudioStep.Play>(
            AudioQueue.next(range, AudioPosition(3, 99), RepeatMode.PASSAGE, null, true, 0),
        )
    }

    @Test
    fun `refuser d'arreter rend la repetition illimitee`() {
        assertIs<AudioStep.Play>(
            AudioQueue.next(range, AudioPosition(3, 3), RepeatMode.PASSAGE, 3, false, 0),
        )
    }

    @Test
    fun `la seance deroule exactement la suite attendue`() {
        val steps = AudioQueue.sequence(range, RepeatMode.PASSAGE, 3)
        val plays = steps.filterIsInstance<AudioStep.Play>()
        // Le verset de depart n'apparait pas : il est deja en train de jouer quand on demande
        // la suite. Trois versets, trois ecoutes chacun, dans l'ordre.
        assertEquals(listOf(2, 3, 1, 2, 3, 1, 2, 3), plays.map { it.position.verseId })
        assertEquals(listOf(1, 1, 2, 2, 2, 3, 3, 3), plays.map { it.position.repetition })
        assertEquals(AudioStep.Stop, steps.last())
        assertEquals(9, steps.size)
    }

    @Test
    fun `le mode chaque verset deroule chaque verset le nombre de fois demande`() {
        val steps = AudioQueue.sequence(Range(1, 2), RepeatMode.EACH_VERSE, 2)
        val plays = steps.filterIsInstance<AudioStep.Play>()
        assertEquals(
            listOf(AudioPosition(1, 2), AudioPosition(2, 1), AudioPosition(2, 2)),
            plays.map { it.position },
        )
        assertEquals(AudioStep.Stop, steps.last())
        assertEquals(4, steps.size)
    }

    @Test
    fun `un verset unique repete applique le silence choisi a chaque reprise`() {
        val steps = AudioQueue.sequence(Range(1, 1), RepeatMode.EACH_VERSE, 3, gapSeconds = 5)
        val plays = steps.filterIsInstance<AudioStep.Play>()
        assertEquals(listOf(5000, 5000), plays.map { it.waitMs })
        assertEquals(AudioStep.Stop, steps.last())
    }

    @Test
    fun `une plage hors du moushaf est refusee`() {
        assertFailsWith<IllegalArgumentException> {
            AudioQueue.next(Range(1, 6237), AudioPosition(1, 1), RepeatMode.PASSAGE, 1, true, 0)
        }
    }

    @Test
    fun `une position hors de la plage est refusee`() {
        assertFailsWith<IllegalArgumentException> {
            AudioQueue.next(range, AudioPosition(9, 1), RepeatMode.PASSAGE, 3, true, 0)
        }
    }

    @Test
    fun `un deroule qui ne s'arrete pas est signale au lieu de tourner sans fin`() {
        assertFailsWith<IllegalStateException> {
            AudioQueue.sequence(range, RepeatMode.PASSAGE, null, limit = 10)
        }
    }
}
