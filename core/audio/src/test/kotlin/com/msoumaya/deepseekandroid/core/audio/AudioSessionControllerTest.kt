// L'horloge virtuelle (`advanceTimeBy`, `runCurrent`) est marquée expérimentale par la
// bibliothèque. L'opt-in est déclaré ici, une fois : sans lui, chaque appel produisait un
// avertissement, et vingt-cinq avertissements de bruit finissent par masquer celui qui compte.
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.msoumaya.deepseekandroid.core.audio

import com.msoumaya.deepseekandroid.core.domain.Audio
import com.msoumaya.deepseekandroid.core.domain.AudioCount
import com.msoumaya.deepseekandroid.core.domain.AudioSession
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.QuranDataLoader
import com.msoumaya.deepseekandroid.core.model.AudioPosition
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.Reciter
import com.msoumaya.deepseekandroid.core.model.RepeatMode
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Conduite d'une séance d'écoute.
 *
 * L'horloge est virtuelle : le silence de 200 ms entre deux versets est **observé** par les
 * tests, pas attendu. C'est la seule façon d'éprouver un enchaînement sans rendre la suite
 * lente, et sans accepter qu'un test dépende du temps réel.
 */
class AudioSessionControllerTest {

    @Before
    fun setUp() = Quran.initialize(QuranDataLoader.loadFromClasspath())

    private fun url(id: Int, reciter: Reciter = Audio.defaultReciter) = Audio.verseAudioUrl(id, reciter)

    /**
     * Fait avancer l'horloge jusqu'à ce que le contrôleur ait fini de réagir.
     *
     * `advanceUntilIdle()` **ne convient pas ici**, et c'est mesuré, pas supposé : il n'exécute
     * pas le travail d'un `backgroundScope`, où vivent les collecteurs du contrôleur. Un test
     * qui s'en servirait verrait un enchaînement qui ne se produit jamais — et, pire, il
     * passerait quand on lui demande de constater une absence.
     */
    private fun TestScope.settle(millis: Long = 10_000) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `demarrer joue le premier verset de la plage`() = runTest {
        val output = FakeAudioOutput()
        val controller = AudioSessionController(output, backgroundScope)

        controller.start(Range(1, 3), AudioSession())

        assertEquals(listOf(url(1)), output.played)
        assertEquals(AudioPosition(1, 1), controller.state.value.position)
        assertTrue(controller.state.value.isPlaying)
        assertTrue(controller.state.value.isOpen)
        assertFalse(controller.state.value.isFinished)
    }

    @Test
    fun `la fin d'un verset enchaine sur le suivant apres la marge technique`() = runTest {
        val output = FakeAudioOutput()
        val controller = AudioSessionController(output, backgroundScope)
        controller.start(Range(1, 3), AudioSession(countChoice = AudioCount.ONE))
        runCurrent()

        output.finish()
        advanceTimeBy(199)
        runCurrent()
        assertEquals(1, output.played.size, "le verset suivant ne doit pas avoir commence")

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(url(1), url(2)), output.played)
        assertEquals(AudioPosition(2, 1), controller.state.value.position)
    }

    @Test
    fun `le silence choisi s'applique en repetant le meme verset`() = runTest {
        val output = FakeAudioOutput()
        val controller = AudioSessionController(output, backgroundScope)
        controller.start(
            Range(1, 1),
            AudioSession(
                countChoice = AudioCount.THREE,
                mode = RepeatMode.EACH_VERSE,
                gapSeconds = 5,
            ),
        )
        runCurrent()

        output.finish()
        advanceTimeBy(4999)
        runCurrent()
        assertEquals(1, output.played.size, "cinq secondes de silence ont ete demandees")

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(url(1), url(1)), output.played)
        assertEquals(AudioPosition(1, 2), controller.state.value.position)
    }

    @Test
    fun `la fin du passage arrete la lecture`() = runTest {
        val output = FakeAudioOutput()
        val controller = AudioSessionController(output, backgroundScope)
        controller.start(Range(1, 1), AudioSession(countChoice = AudioCount.ONE))
        runCurrent()

        output.finish()
        settle()

        assertTrue(controller.state.value.isFinished)
        assertFalse(controller.state.value.isPlaying)
        assertEquals(1, output.played.size)
    }

    @Test
    fun `la repetition illimitee ne s'arrete pas`() = runTest {
        val output = FakeAudioOutput()
        val controller = AudioSessionController(output, backgroundScope)
        controller.start(Range(1, 1), AudioSession(countChoice = AudioCount.CONTINUOUS))
        runCurrent()

        repeat(4) {
            output.finish()
            settle()
        }

        assertEquals(5, output.played.size)
        assertFalse(controller.state.value.isFinished)
    }

    @Test
    fun `le compteur de repetition suit le mode chaque verset`() = runTest {
        val output = FakeAudioOutput()
        val controller = AudioSessionController(output, backgroundScope)
        controller.start(
            Range(1, 2),
            AudioSession(countChoice = AudioCount.TWO, mode = RepeatMode.EACH_VERSE),
        )
        runCurrent()

        assertEquals(AudioPosition(1, 1), controller.state.value.position)
        output.finish()
        settle()
        assertEquals(AudioPosition(1, 2), controller.state.value.position)
        output.finish()
        settle()
        assertEquals(AudioPosition(2, 1), controller.state.value.position)
    }

    @Test
    fun `mettre en pause puis reprendre`() = runTest {
        val output = FakeAudioOutput()
        val controller = AudioSessionController(output, backgroundScope)
        controller.start(Range(1, 3), AudioSession())
        runCurrent()

        controller.pause()
        assertFalse(controller.state.value.isPlaying)
        assertTrue(output.paused)

        controller.resume()
        assertTrue(controller.state.value.isPlaying)
        assertFalse(output.paused)
    }

    @Test
    fun `fermer la seance interrompt l'enchainement`() = runTest {
        val output = FakeAudioOutput()
        val controller = AudioSessionController(output, backgroundScope)
        controller.start(Range(1, 3), AudioSession())
        runCurrent()

        controller.close()
        assertFalse(controller.state.value.isOpen)
        assertTrue(output.stopped)

        // Une fin qui arrive apres la fermeture ne doit rien declencher. La suite est laissee
        // ouverte assez longtemps pour qu'un enchainement fautif ait le temps de se produire :
        // sans cela, le test passerait meme si le controleur obeissait encore.
        output.finish()
        settle()
        assertEquals(1, output.played.size)
    }

    @Test
    fun `une fin oubliee ne fait pas avancer la seance suivante`() = runTest {
        val output = FakeAudioOutput()
        val controller = AudioSessionController(output, backgroundScope)
        controller.start(Range(1, 1), AudioSession(countChoice = AudioCount.ONE))
        runCurrent()

        // La fin arrive, mais la seance est fermee avant que le controleur la traite : elle
        // doit etre oubliee, sans quoi la seance suivante sauterait son premier verset.
        output.finish()
        controller.close()
        runCurrent()

        controller.start(Range(1, 1), AudioSession(countChoice = AudioCount.ONE))
        runCurrent()
        settle()

        assertEquals(listOf(url(1), url(1)), output.played)
        assertFalse(controller.state.value.isFinished)
        assertEquals(AudioPosition(1, 1), controller.state.value.position)
    }

    @Test
    fun `la vitesse choisie est appliquee au lecteur`() = runTest {
        val output = FakeAudioOutput()
        val controller = AudioSessionController(output, backgroundScope)

        controller.start(Range(1, 3), AudioSession(speed = 1.25f))
        assertEquals(1.25f, output.lastSpeed)

        controller.updateSettings(AudioSession(speed = 0.75f))
        assertEquals(0.75f, output.lastSpeed)
    }

    @Test
    fun `changer de recitateur change l'URL du verset suivant`() = runTest {
        val output = FakeAudioOutput()
        val controller = AudioSessionController(output, backgroundScope)
        controller.start(Range(1, 3), AudioSession(countChoice = AudioCount.ONE))
        runCurrent()

        val husary = Audio.reciters.first { it.id == "ar.husary" }
        controller.useReciter(husary)
        output.finish()
        settle()
        output.finish()
        settle()

        assertEquals(url(3, husary), output.played.last())
        assertNotEquals(url(3), output.played.last())
    }

    @Test
    fun `un fichier illisible remonte le message et arrete le compteur`() = runTest {
        val output = FakeAudioOutput()
        val controller = AudioSessionController(output, backgroundScope)
        controller.start(Range(1, 3), AudioSession())
        runCurrent()

        output.fail("Le verset ne peut pas être chargé.")
        runCurrent()

        assertEquals("Le verset ne peut pas être chargé.", controller.state.value.error)
        assertFalse(controller.state.value.isPlaying)
    }

    @Test
    fun `une plage hors du moushaf est refusee`() = runTest {
        val output = FakeAudioOutput()
        val controller = AudioSessionController(output, backgroundScope)

        assertFailsWith<IllegalArgumentException> {
            controller.start(Range(1, 6237), AudioSession())
        }
        assertTrue(output.played.isEmpty())
    }

    @Test
    fun `liberer le lecteur ferme la seance`() = runTest {
        val output = FakeAudioOutput()
        val controller = AudioSessionController(output, backgroundScope)
        controller.start(Range(1, 3), AudioSession())
        runCurrent()

        controller.release()

        assertTrue(output.released)
        assertFalse(controller.state.value.isOpen)
    }

    @Test
    fun `une saisie d'ecoutes illisible ne fige pas la seance`() = runTest {
        val output = FakeAudioOutput()
        val controller = AudioSessionController(output, backgroundScope)
        // « abc » n'est pas un entier. Le moteur doit ramener le compte a 1, et surtout NE PAS
        // echouer : l'exception serait absorbee par la portee de coroutines, le collecteur qui
        // conduit la seance mourrait, et plus rien n'avancerait — sans le moindre message.
        controller.start(
            Range(1, 1),
            AudioSession(countChoice = AudioCount.CUSTOM, customCount = "abc"),
        )
        runCurrent()

        output.finish()
        settle()

        // Le compte vaut 1 : la seance se termine proprement, au lieu de se figer.
        assertNull(controller.state.value.error)
        assertTrue(controller.state.value.isFinished, "la seance doit s'arreter proprement")
        assertFalse(controller.state.value.isPlaying)
        assertEquals(1, output.played.size)

        // Et la preuve directe que le collecteur est VIVANT : une seance neuve, sur des reglages
        // lisibles, doit encore enchainer. S'il etait mort, cette fin ne declencherait rien.
        controller.start(Range(1, 3), AudioSession(countChoice = AudioCount.THREE))
        runCurrent()
        output.finish()
        settle()

        assertEquals(listOf(url(1), url(1), url(2)), output.played)
        assertEquals(AudioPosition(2, 1), controller.state.value.position)
    }

    @Test
    fun `changer les reglages s'applique a la seance en cours`() = runTest {
        val output = FakeAudioOutput()
        val controller = AudioSessionController(output, backgroundScope)
        controller.start(
            Range(1, 2),
            AudioSession(countChoice = AudioCount.ONE, mode = RepeatMode.PASSAGE),
        )
        runCurrent()

        // Le client d'origine reconstruit ses reglages a **chaque rendu** : changer le mode et le
        // nombre d'ecoutes vaut pour la seance en cours, sans qu'il faille la relancer.
        controller.updateSettings(
            AudioSession(countChoice = AudioCount.THREE, mode = RepeatMode.EACH_VERSE),
        )
        assertEquals(1, output.played.size, "le verset en cours ne doit pas etre coupe")

        output.finish()
        settle()

        // Mode « chaque verset » : le meme verset est repris, et non le suivant. Si le controleur
        // n'avait retenu que la vitesse, il serait passe au verset 2.
        assertEquals(AudioPosition(1, 2), controller.state.value.position)
        assertEquals(listOf(url(1), url(1)), output.played)
    }
}
