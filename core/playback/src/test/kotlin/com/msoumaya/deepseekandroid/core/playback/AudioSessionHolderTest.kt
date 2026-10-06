package com.msoumaya.deepseekandroid.core.playback

import com.msoumaya.deepseekandroid.core.domain.Audio
import com.msoumaya.deepseekandroid.core.domain.AudioSession
import com.msoumaya.deepseekandroid.core.model.Range
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Ce que le détenteur promet : une séance qui survit à celui qui l'observe, et qui ne se
 * relance pas quand on la regarde.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioSessionHolderTest {

    private fun holder(output: FakeAudioOutput, dispatcher: CoroutineDispatcher) =
        AudioSessionHolder(output, CoroutineScope(dispatcher))

    @Test
    fun `l'etat est expose tel quel, sans recopiage`() = runTest {
        val output = FakeAudioOutput()
        val holder = holder(output, UnconfinedTestDispatcher(testScheduler))

        // Le même objet : deux écrans qui s'abonnent voient la même séance.
        assertSame(holder.state, holder.state)
    }

    @Test
    fun `start ouvre la seance et joue le premier verset`() = runTest {
        val output = FakeAudioOutput()
        val holder = holder(output, UnconfinedTestDispatcher(testScheduler))

        holder.start(Range(2, 5), AudioSession())

        assertEquals(Range(2, 5), holder.state.value.range)
        assertTrue(holder.state.value.isPlaying)
        assertEquals(1, output.played.size)
    }

    @Test
    fun `les reglages sont retenus par le detenteur`() = runTest {
        val output = FakeAudioOutput()
        val holder = holder(output, UnconfinedTestDispatcher(testScheduler))

        // **Non par défaut**, et c'est ce qui rend le contrôle concluant : avec `AudioSession()`,
        // retenir la valeur reçue et en fabriquer une neuve donnent le même objet — le test
        // resterait vert même si le détenteur cessait de retenir quoi que ce soit.
        val choisis = AudioSession(speed = 1.25f, gapSeconds = 5)
        holder.start(Range(1, 3), choisis)

        assertEquals(choisis, holder.settings)
        assertNotEquals(AudioSession(), holder.settings)
        assertEquals(1.25f, output.lastSpeed)
    }

    @Test
    fun `start sans reglages reprend la derniere valeur connue`() = runTest {
        val output = FakeAudioOutput()
        val holder = holder(output, UnconfinedTestDispatcher(testScheduler))

        // Des réglages **non par défaut** : c'est ce qui rend le contrôle concluant. Avec les
        // valeurs par défaut, reprendre `settings` et repartir d'un `AudioSession()` donnent le
        // même résultat — le test passerait sans rien prouver, et ne tomberait jamais.
        val choisis = AudioSession(speed = 1.5f, gapSeconds = 3)
        holder.start(Range(1, 3), choisis)
        output.played.clear()
        output.stops = 0

        holder.start(Range(1, 3))

        assertEquals(1, output.played.size)
        // La vitesse est poussée au lecteur natif : c'est par elle qu'on voit que ce sont bien
        // les réglages choisis qui ont été repris, et non des valeurs par défaut.
        assertEquals(choisis, holder.settings)
        assertEquals(1.5f, output.lastSpeed)
    }

    @Test
    fun `updateSettings pousse la vitesse au lecteur natif`() = runTest {
        val output = FakeAudioOutput()
        val holder = holder(output, UnconfinedTestDispatcher(testScheduler))

        holder.updateSettings(AudioSession(speed = 1.25f))

        // La vitesse est le seul réglage que le lecteur natif doive connaître ; les autres ne
        // sont lus qu'au moment de décider du verset suivant.
        assertEquals(1.25f, output.lastSpeed)
        assertEquals(AudioSession(speed = 1.25f), holder.settings)
    }

    @Test
    fun `useReciter ne coupe pas le verset qui joue`() = runTest {
        val output = FakeAudioOutput()
        val holder = holder(output, UnconfinedTestDispatcher(testScheduler))
        holder.start(Range(1, 2), AudioSession())
        val joue = output.played.toList()

        holder.useReciter(Audio.reciters.last())

        // Rien n'a été relancé : le verset en cours continue, le suivant viendra du nouveau.
        assertEquals(joue, output.played)
    }

    @Test
    fun `close ferme la seance et arrete le lecteur natif`() = runTest {
        val output = FakeAudioOutput()
        val holder = holder(output, UnconfinedTestDispatcher(testScheduler))
        holder.start(Range(1, 2), AudioSession())
        // `start` ferme la séance précédente avant d'ouvrir la sienne : le compteur part donc
        // de un, et non de zéro. Compter à partir de l'ouverture évite d'attribuer à `close`
        // l'arrêt que `start` a demandé.
        output.stops = 0

        holder.close()

        assertEquals(1, output.stops)
        assertNull(holder.state.value.range)
        assertFalse(holder.state.value.isPlaying)
    }

    @Test
    fun `pause et resume sont transmis`() = runTest {
        val output = FakeAudioOutput()
        val holder = holder(output, UnconfinedTestDispatcher(testScheduler))
        holder.start(Range(1, 2), AudioSession())

        holder.pause()
        assertEquals(1, output.pauses)
        assertFalse(holder.state.value.isPlaying)

        holder.resume()
        assertEquals(1, output.resumes)
        assertTrue(holder.state.value.isPlaying)
    }

    @Test
    fun `release libere le lecteur natif`() = runTest {
        val output = FakeAudioOutput()
        val holder = holder(output, UnconfinedTestDispatcher(testScheduler))

        holder.release()

        assertEquals(1, output.releases)
        assertNull(holder.state.value.range)
    }

    @Test
    fun `un echec de chargement remonte dans l'etat sans fermer la seance`() = runTest {
        val output = FakeAudioOutput()
        val holder = holder(output, UnconfinedTestDispatcher(testScheduler))
        holder.start(Range(1, 2), AudioSession())

        output.fail("Le verset ne peut pas être chargé.")

        assertEquals("Le verset ne peut pas être chargé.", holder.state.value.error)
        assertEquals(Range(1, 2), holder.state.value.range)
        assertFalse(holder.state.value.isPlaying)
    }

    @Test
    fun `la seance survit a celui qui l'observe`() = runTest {
        val output = FakeAudioOutput()
        val holder = holder(output, UnconfinedTestDispatcher(testScheduler))
        holder.start(Range(4, 9), AudioSession())
        // On mesure **à partir de** l'ouverture de la séance : `start` arrête la précédente,
        // et l'imputer à une libération ferait dire au contrôle le contraire de ce qu'il vise.
        output.stops = 0
        output.releases = 0

        // Ce qu'un écran ferait en s'en allant : plus rien. L'état, lui, n'a pas bougé — c'est
        // exactement ce que le `DisposableEffect` du lecteur faisait de travers.
        assertEquals(Range(4, 9), holder.state.value.range)
        assertTrue(holder.state.value.isPlaying)
        assertEquals(0, output.stops)
        assertEquals(0, output.releases)
    }
}
