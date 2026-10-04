package com.msoumaya.deepseekandroid.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Les libellés des réglages d'écoute.
 *
 * Ce qui est éprouvé ici n'est pas de la cosmétique : le titre de la feuille doit être **celui
 * de la ligne qui l'ouvre**, les nombres doivent se lire en français, et la note sur la marge
 * technique doit porter le vrai chiffre — c'est la seule chose à l'écran qui dise qu'un silence
 * réglé à « Aucune » n'est pas un silence nul.
 */
class AudioSettingsTextTest {

    @Test
    fun `les libelles sont ceux du client d'origine`() {
        assertEquals("Réglages audio", AudioSettingsText.TITLE)
        assertEquals("Fermer les réglages audio", AudioSettingsText.CLOSE)
        assertEquals("Récitateur", AudioSettingsText.RECITER_LABEL)
        assertEquals("Nombre personnalisé (1 à 999)", AudioSettingsText.CUSTOM_PLACEHOLDER)
        assertEquals("Répétitions", AudioSettingsText.REPEAT_LABEL)
        assertEquals("Passage complet", AudioSettingsText.MODE_PASSAGE)
        assertEquals("Chaque verset", AudioSettingsText.MODE_EACH_VERSE)
        assertEquals("Vitesse", AudioSettingsText.SPEED_LABEL)
        assertEquals("Pause entre deux écoutes", AudioSettingsText.GAP_LABEL)
        assertEquals("Aucune", AudioSettingsText.GAP_NONE)
        assertEquals("Arrêter à la fin des écoutes", AudioSettingsText.AUTO_STOP)
        assertEquals("Lancer ce passage", AudioSettingsText.START)
        assertEquals("Recommencer le passage", AudioSettingsText.RESTART)
    }

    @Test
    fun `le titre de la feuille est celui de la ligne qui l'ouvre`() {
        // Deux endroits portent la même vérité sans pouvoir se lire : le titre de l'écran et le
        // libellé de la ligne de la feuille d'options. S'ils divergent, la personne croit s'être
        // trompée de bouton. Ce test est le seul lien entre les deux.
        val row = ReaderOptionsText.ALL.single { it.action == ReaderOptionsText.Action.AUDIO }
        assertEquals(row.title, AudioSettingsText.TITLE)
        assertEquals("Récitateur, répétitions et vitesse", row.subtitle)
    }

    @Test
    fun `la note sur la marge technique porte le chiffre du domaine`() {
        // La phrase ne doit pas pouvoir mentir si la marge change : elle est construite à partir
        // de la constante, et ce test le vérifie en la relisant dans la phrase.
        val note = AudioSettingsText.technicalMarginNote()
        assertTrue(note.contains(Audio.DEFAULT_AYAH_GAP_MS.toString()), "note : « $note »")
        assertEquals(
            "Une marge technique de ${Audio.DEFAULT_AYAH_GAP_MS} ms reste active entre les versets.",
            note,
        )
        assertEquals(200, Audio.DEFAULT_AYAH_GAP_MS)
    }

    @Test
    fun `un silence nul s'ecrit en mots, les autres en secondes`() {
        assertEquals("Aucune", AudioSettingsText.gapLabel(0))
        assertEquals("2 s", AudioSettingsText.gapLabel(2))
        assertEquals("5 s", AudioSettingsText.gapLabel(5))
        assertEquals("10 s", AudioSettingsText.gapLabel(10))
        // Un silence négatif n'existe pas : il ne doit pas s'écrire « -3 s », qui se lirait
        // comme une durée. L'enchaînement le ramène de toute façon à zéro.
        assertEquals("Aucune", AudioSettingsText.gapLabel(-3))
    }

    @Test
    fun `chaque silence propose s'ecrit distinctement`() {
        val labels = AudioSettings.GAP_CHOICES.map { AudioSettingsText.gapLabel(it) }
        assertEquals(listOf("Aucune", "2 s", "5 s", "10 s"), labels)
        assertEquals(labels.size, labels.toSet().size, "deux silences ne peuvent pas se confondre")
    }

    @Test
    fun `une vitesse s'ecrit avec la virgule francaise`() {
        assertEquals("0,75×", AudioSettingsText.speedLabel(0.75f))
        assertEquals("1×", AudioSettingsText.speedLabel(1f))
        assertEquals("1,25×", AudioSettingsText.speedLabel(1.25f))
    }

    @Test
    fun `chaque vitesse proposee s'ecrit distinctement`() {
        val labels = AudioSettings.SPEED_CHOICES.map { AudioSettingsText.speedLabel(it) }
        assertEquals(listOf("0,75×", "1×", "1,25×"), labels)
        assertEquals(labels.size, labels.toSet().size, "deux vitesses ne peuvent pas se confondre")
    }

    @Test
    fun `la ligne du recitateur reprend la forme d'origine`() {
        assertEquals("Récitateur : Hafs ‘an ‘Âsim", AudioSettingsText.reciterLine("Hafs ‘an ‘Âsim"))
    }

    @Test
    fun `le nombre d'un choix de repetition est son libelle`() {
        assertEquals(
            listOf("1", "2", "3", "5", "10", "Autre", "∞"),
            AudioCount.ALL.map { AudioSettingsText.countLabel(it) },
        )
    }

    @Test
    fun `la case d'arret parait decochee quand la repetition est illimitee`() {
        // Le client d'origine ecrit `selected={autoStop && countChoice!=='continuous'}`.
        assertFalse(
            AudioSettingsText.autoStopShown(
                AudioSession(countChoice = AudioCount.CONTINUOUS, autoStop = true),
            ),
        )
        assertTrue(
            AudioSettingsText.autoStopShown(
                AudioSession(countChoice = AudioCount.THREE, autoStop = true),
            ),
        )
        assertFalse(
            AudioSettingsText.autoStopShown(
                AudioSession(countChoice = AudioCount.THREE, autoStop = false),
            ),
        )
    }
}
