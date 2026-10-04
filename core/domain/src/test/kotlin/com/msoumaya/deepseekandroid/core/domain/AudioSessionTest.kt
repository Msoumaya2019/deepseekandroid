package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.RepeatMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Réglages de la séance d'écoute.
 *
 * Ce qui compte ici n'est pas la liste des choix mais la **relecture** : une préférence
 * enregistrée par une version antérieure, ou abîmée, ne doit pas empêcher d'écouter. Chaque
 * champ est donc validé séparément, et un champ invalide ne doit pas emporter ses voisins.
 */
class AudioSessionTest {

    @Test
    fun `les sept choix de repetition sont proposes dans l'ordre d'origine`() {
        assertEquals(
            listOf("1", "2", "3", "5", "10", "Autre", "∞"),
            AudioCount.ALL.map { it.label },
        )
        assertEquals(7, AudioCount.ALL.size)
    }

    @Test
    fun `le choix illimite porte le symbole d'infini, le choix libre le mot Autre`() {
        assertEquals("∞", AudioCount.CONTINUOUS.label)
        assertEquals("Autre", AudioCount.CUSTOM.label)
    }

    @Test
    fun `les reglages par defaut sont ceux du client d'origine`() {
        val session = AudioSession()
        assertEquals(AudioCount.THREE, session.countChoice)
        assertEquals("20", session.customCount)
        assertEquals(RepeatMode.PASSAGE, session.mode)
        assertEquals(0, session.gapSeconds)
        assertEquals(1f, session.speed)
        assertTrue(session.autoStop)
        assertEquals(3, session.resolveCount())
    }

    @Test
    fun `les vitesses et les silences proposes sont ceux du client d'origine`() {
        assertEquals(listOf(0.75f, 1f, 1.25f), AudioSettings.SPEED_CHOICES)
        assertEquals(listOf(0, 2, 5, 10), AudioSettings.GAP_CHOICES)
        assertEquals(200, Audio.DEFAULT_AYAH_GAP_MS)
    }

    @Test
    fun `un choix fixe rend son nombre sans lire la saisie`() {
        assertEquals(1, AudioCount.ONE.resolve(""))
        assertEquals(2, AudioCount.TWO.resolve(""))
        assertEquals(5, AudioCount.FIVE.resolve(""))
        assertEquals(10, AudioCount.TEN.resolve(""))
    }

    @Test
    fun `le choix illimite ne rend aucun nombre`() {
        assertNull(AudioCount.CONTINUOUS.resolve("20"))
        assertNull(AudioSession(countChoice = AudioCount.CONTINUOUS).resolveCount())
    }

    @Test
    fun `une saisie libre hors bornes est refusee avec le message d'origine`() {
        for (text in listOf("", "  ", "abc", "2.5", "0", "-1", "1000", "9999")) {
            val failure = assertFailsWith<IllegalStateException>(text) {
                AudioCount.CUSTOM.resolve(text)
            }
            assertEquals(AudioSettings.CUSTOM_COUNT_MESSAGE, failure.message)
        }
    }

    @Test
    fun `une saisie libre aux bornes est acceptee`() {
        assertEquals(1, AudioCount.CUSTOM.resolve("1"))
        assertEquals(999, AudioCount.CUSTOM.resolve("999"))
        assertEquals(20, AudioCount.CUSTOM.resolve("20"))
    }

    @Test
    fun `une saisie libre entouree d'espaces est acceptee`() {
        assertEquals(20, AudioCount.CUSTOM.resolve("  20  "))
    }

    @Test
    fun `une ecriture exponentielle est refusee plutot que lue comme un nombre`() {
        // En JavaScript, `Number('1e2')` vaut 100 et `Number('0x10')` vaut 16. Les accepter
        // ferait diverger les deux clients sur un nombre d'ecoutes, pour une ecriture qui n'a
        // aucun sens dans un champ numerique. On refuse, avec le meme message.
        assertFailsWith<IllegalStateException> { AudioCount.CUSTOM.resolve("1e2") }
        assertFailsWith<IllegalStateException> { AudioCount.CUSTOM.resolve("0x10") }
    }

    @Test
    fun `la forme enregistree est celle du client d'origine`() {
        assertEquals("1", AudioCount.ONE.wire)
        assertEquals("10", AudioCount.TEN.wire)
        assertEquals("custom", AudioCount.CUSTOM.wire)
        assertEquals("continuous", AudioCount.CONTINUOUS.wire)
        assertEquals("passage", RepeatMode.PASSAGE.wire)
        assertEquals("each-verse", RepeatMode.EACH_VERSE.wire)
    }

    @Test
    fun `un enregistrement absent rend les reglages par defaut`() {
        assertEquals(AudioSession(), AudioSession.fromStored(null))
    }

    @Test
    fun `un enregistrement complet est relu tel quel`() {
        val session = AudioSession.fromStored(
            StoredAudioPreferences(
                countChoice = "5",
                customCount = "42",
                repeatMode = "each-verse",
                gap = 10,
                speed = 0.75f,
                autoStop = false,
            ),
        )
        assertEquals(AudioCount.FIVE, session.countChoice)
        assertEquals("42", session.customCount)
        assertEquals(RepeatMode.EACH_VERSE, session.mode)
        assertEquals(10, session.gapSeconds)
        assertEquals(0.75f, session.speed)
        assertFalse(session.autoStop)
    }

    @Test
    fun `un champ hors bornes est ignore sans emporter les autres`() {
        val session = AudioSession.fromStored(
            StoredAudioPreferences(
                countChoice = "7", // ce choix n'existe pas
                customCount = "42",
                repeatMode = "chaque-verset", // ce mode n'existe pas
                gap = 7, // ce silence n'est pas propose
                speed = 2f, // cette vitesse n'est pas proposee
                autoStop = false,
            ),
        )
        // Les quatre champs invalides retombent au defaut...
        assertEquals(AudioSettings.DEFAULT_COUNT_CHOICE, session.countChoice)
        assertEquals(RepeatMode.PASSAGE, session.mode)
        assertEquals(AudioSettings.DEFAULT_GAP_SECONDS, session.gapSeconds)
        assertEquals(AudioSettings.DEFAULT_SPEED, session.speed)
        // ...et les deux champs valides survivent.
        assertEquals("42", session.customCount)
        assertFalse(session.autoStop)
    }

    @Test
    fun `l'aller-retour d'enregistrement conserve les reglages`() {
        val session = AudioSession(
            countChoice = AudioCount.CUSTOM,
            customCount = "7",
            mode = RepeatMode.EACH_VERSE,
            gapSeconds = 5,
            speed = 1.25f,
            autoStop = false,
        )
        assertEquals(session, AudioSession.fromStored(session.stored()))
    }

    @Test
    fun `le mode illimite vient du choix infini ou du refus d'arreter`() {
        assertTrue(AudioSession(countChoice = AudioCount.CONTINUOUS).isUnlimited)
        assertTrue(AudioSession(countChoice = AudioCount.THREE, autoStop = false).isUnlimited)
        assertFalse(AudioSession(countChoice = AudioCount.THREE, autoStop = true).isUnlimited)
    }

    @Test
    fun `une saisie libre illisible est ramenee a une ecoute, pas refusee`() {
        // Deux regles differentes, et il faut les deux. L'ECRAN refuse la saisie avec le
        // message d'origine (voir le test des bornes ci-dessus). Le MOTEUR, lui, ne juge pas :
        // le client d'origine ecrit dans `settingsRef`
        // `count==='continuous'?count:Number.isInteger(count)&&count>0?count:1`, donc une
        // saisie illisible devient 1. Faire lever le moteur tuerait le collecteur qui conduit
        // la seance, et la lecon se figerait sans rien dire.
        for (text in listOf("abc", "", "  ", "2.5", "0", "-1")) {
            assertEquals(
                1,
                AudioSession(countChoice = AudioCount.CUSTOM, customCount = text).resolveCount(),
                "saisie : « $text »",
            )
        }
    }

    @Test
    fun `le moteur ne rejuge pas une saisie libre deja acceptee par l'ecran`() {
        // `begin()` refuse au-dela de 999, mais `Number.isInteger(1000)&&1000>0` est vrai :
        // une fois le reglage pose, le moteur applique ce qu'on lui donne. Les deux regles ne
        // se contredisent pas — l'ecran empeche d'y arriver, le moteur ne re-juge pas.
        assertEquals(
            1000,
            AudioSession(countChoice = AudioCount.CUSTOM, customCount = "1000").resolveCount(),
        )
    }

    @Test
    fun `l'ecran ne refuse que la saisie libre illisible`() {
        // Les choix fixes portent leur nombre, et « ∞ » n'en a pas besoin : une saisie libre
        // abîmée ne les atteint pas. Seul « Autre » peut être faux — c'est le seul cas où
        // l'écran doit parler, et donc le seul où il ne doit pas se taire.
        for (count in AudioCount.ALL - AudioCount.CUSTOM) {
            assertNull(
                AudioSettings.validationMessage(
                    AudioSession(countChoice = count, customCount = "abc"),
                ),
                "choix : $count",
            )
        }
    }

    @Test
    fun `l'ecran refuse une saisie libre hors bornes avec le message d'origine`() {
        for (text in listOf("", "  ", "abc", "2.5", "0", "-1", "1000")) {
            assertEquals(
                AudioSettings.CUSTOM_COUNT_MESSAGE,
                AudioSettings.validationMessage(
                    AudioSession(countChoice = AudioCount.CUSTOM, customCount = text),
                ),
                "saisie : « $text »",
            )
        }
        for (text in listOf("1", "20", " 42 ", "999")) {
            assertNull(
                AudioSettings.validationMessage(
                    AudioSession(countChoice = AudioCount.CUSTOM, customCount = text),
                ),
                "saisie : « $text »",
            )
        }
    }
}
