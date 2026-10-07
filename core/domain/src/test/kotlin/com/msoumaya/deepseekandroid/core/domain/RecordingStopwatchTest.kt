package com.msoumaya.deepseekandroid.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * La mesure de la durée captée.
 *
 * Le client d'origine n'a pas de règle à porter ici : `expo-audio` lui donne la durée, pauses
 * déduites. `MediaRecorder`, lui, n'expose aucune durée — cette mesure est donc une règle que le
 * portage **ajoute**, et c'est exactement pour cela qu'elle est éprouvée : une durée inventée
 * serait écrite dans la ligne distante, et c'est elle que le relecteur lit avant d'écouter.
 *
 * L'horloge est une variable de test, avancée à la main : chaque cas dit alors précisément quel
 * temps il fait passer, et aucune attente réelle n'entre dans le résultat.
 */
class RecordingStopwatchTest {

    /** Une horloge que le test avance lui-même. */
    private class FausseHorloge {
        var instant: Long = 0
        fun avance(ms: Long) {
            instant += ms
        }
    }

    @Test
    fun `sans depart la duree est nulle`() {
        val horloge = FausseHorloge()
        val chrono = RecordingStopwatch { horloge.instant }
        horloge.avance(5_000)
        assertEquals(0, chrono.elapsedMs())
        assertFalse(chrono.isRunning)
    }

    @Test
    fun `la duree suit l'horloge`() {
        val horloge = FausseHorloge()
        val chrono = RecordingStopwatch { horloge.instant }
        chrono.start()
        horloge.avance(1_500)
        assertEquals(1_500, chrono.elapsedMs())
        assertTrue(chrono.isRunning)
    }

    @Test
    fun `une pause exclut le temps suspendu`() {
        val horloge = FausseHorloge()
        val chrono = RecordingStopwatch { horloge.instant }
        chrono.start()
        horloge.avance(1_000)
        chrono.pause()
        horloge.avance(4_000)
        assertEquals(1_000, chrono.elapsedMs())
        assertFalse(chrono.isRunning)
    }

    @Test
    fun `une reprise ajoute un second segment`() {
        val horloge = FausseHorloge()
        val chrono = RecordingStopwatch { horloge.instant }
        chrono.start()
        horloge.avance(1_000)
        chrono.pause()
        horloge.avance(4_000)
        chrono.resume()
        horloge.avance(2_000)
        assertEquals(3_000, chrono.elapsedMs())
        assertTrue(chrono.isRunning)
    }

    @Test
    fun `la duree est la somme des segments, pas l'ecart entre le debut et l'arret`() {
        val horloge = FausseHorloge()
        val chrono = RecordingStopwatch { horloge.instant }
        chrono.start()
        horloge.avance(1_000)
        chrono.pause()
        horloge.avance(4_000)
        chrono.resume()
        horloge.avance(1_000)
        chrono.pause()
        horloge.avance(3_000)
        chrono.resume()
        horloge.avance(500)
        assertEquals(2_500, chrono.stop())
    }

    @Test
    fun `pauser deux fois ne compte qu'une fois`() {
        val horloge = FausseHorloge()
        val chrono = RecordingStopwatch { horloge.instant }
        chrono.start()
        horloge.avance(1_000)
        chrono.pause()
        horloge.avance(4_000)
        chrono.pause()
        assertEquals(1_000, chrono.elapsedMs())

        chrono.resume()
        horloge.avance(1_000)
        assertEquals(2_000, chrono.elapsedMs())
    }

    @Test
    fun `reprendre deux fois ne compte qu'une fois`() {
        val horloge = FausseHorloge()
        val chrono = RecordingStopwatch { horloge.instant }
        chrono.start()
        horloge.avance(1_000)
        chrono.pause()
        horloge.avance(4_000)
        chrono.resume()
        horloge.avance(1_000)
        chrono.resume()
        horloge.avance(1_000)
        // 1000 captees, puis un seul segment de 2000 : la seconde reprise n'a rien ajoute.
        assertEquals(3_000, chrono.elapsedMs())
    }

    @Test
    fun `l'arret fige la duree`() {
        val horloge = FausseHorloge()
        val chrono = RecordingStopwatch { horloge.instant }
        chrono.start()
        horloge.avance(1_000)
        assertEquals(1_000, chrono.stop())
        horloge.avance(8_000)
        assertEquals(1_000, chrono.elapsedMs())
        assertFalse(chrono.isRunning)
    }

    @Test
    fun `reprendre apres l'arret ne relance rien`() {
        val horloge = FausseHorloge()
        val chrono = RecordingStopwatch { horloge.instant }
        chrono.start()
        horloge.avance(1_000)
        chrono.stop()
        horloge.avance(8_000)
        chrono.resume()
        horloge.avance(500)
        assertEquals(1_000, chrono.elapsedMs())
        assertFalse(chrono.isRunning)
    }

    @Test
    fun `un nouveau depart repart de zero`() {
        val horloge = FausseHorloge()
        val chrono = RecordingStopwatch { horloge.instant }
        chrono.start()
        horloge.avance(4_000)
        chrono.stop()
        horloge.avance(5_000)
        chrono.start()
        horloge.avance(500)
        assertEquals(500, chrono.elapsedMs())
    }

    @Test
    fun `un arret sans depart rend zero`() {
        val horloge = FausseHorloge()
        val chrono = RecordingStopwatch { horloge.instant }
        horloge.avance(3_000)
        assertEquals(0, chrono.stop())
    }

    @Test
    fun `une pause avant tout depart ne fausse pas la mesure`() {
        val horloge = FausseHorloge()
        val chrono = RecordingStopwatch { horloge.instant }
        chrono.pause()
        horloge.avance(5_000)
        chrono.start()
        horloge.avance(1_000)
        assertEquals(1_000, chrono.elapsedMs())
    }

    @Test
    fun `une reprise avant tout depart ne fait pas courir le chronometre`() {
        val horloge = FausseHorloge()
        val chrono = RecordingStopwatch { horloge.instant }
        chrono.resume()
        horloge.avance(5_000)
        assertFalse(chrono.isRunning)
        assertEquals(0, chrono.elapsedMs())
    }

    @Test
    fun `l'horloge par defaut mesure un temps qui ne recule pas`() {
        // Le seul cas qui touche la vraie horloge : les autres la remplacent. Il ne verifie pas
        // une duree — elle dependrait de la machine —, mais que la mesure est monotone et que
        // l'arret rend la valeur figee.
        val chrono = RecordingStopwatch()
        chrono.start()
        val pendant = chrono.elapsedMs()
        val arrete = chrono.stop()
        assertTrue(pendant >= 0, "duree en cours negative : $pendant")
        assertTrue(arrete >= pendant, "arret $arrete avant la mesure $pendant")
        assertEquals(arrete, chrono.elapsedMs())
    }
}
