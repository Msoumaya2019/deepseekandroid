package com.msoumaya.deepseekandroid.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Libellés de l'écran « Progrès »
// ---------------------------------------------------------------------------
// Ces chaînes décident de ce que la personne croit avoir fait : « Versets appris ce mois »,
// « 3 jours d'affilée ». Un mot faux y est une affirmation fausse sur son propre travail, et
// cela ne se relit pas à l'œil dans un composable.
//
// Les périodes sont énumérées dans l'ordre du sélecteur segmenté : l'ordre est **visible**, donc
// il se vérifie.
// ---------------------------------------------------------------------------

class ProgressTextTest {

    // -----------------------------------------------------------------------
    // Périodes
    // -----------------------------------------------------------------------

    @Test
    fun `les trois periodes sont dans l'ordre du selecteur`() {
        assertEquals(
            listOf("Jour", "Semaine", "Mois"),
            ProgressText.Period.entries.map { it.label },
        )
    }

    @Test
    fun `le titre de la carte des versets suit la periode`() {
        assertEquals("Versets appris aujourd’hui", ProgressText.learnedTitle(ProgressText.Period.DAY))
        assertEquals("Versets appris cette semaine", ProgressText.learnedTitle(ProgressText.Period.WEEK))
        assertEquals("Versets appris ce mois", ProgressText.learnedTitle(ProgressText.Period.MONTH))
    }

    @Test
    fun `le titre du graphique ne suit pas le nom de la periode`() {
        // « Jour » décrit une fenêtre glissante de sept jours, « Mois » le mois calendaire : les
        // deux titres diffèrent donc de leur étiquette de sélecteur, et c'est délibéré.
        assertEquals("Les 7 derniers jours", ProgressText.graphTitle(ProgressText.Period.DAY))
        assertEquals("Cette semaine", ProgressText.graphTitle(ProgressText.Period.WEEK))
        assertEquals("Ce mois", ProgressText.graphTitle(ProgressText.Period.MONTH))

        assertFalse(ProgressText.graphTitle(ProgressText.Period.DAY) == ProgressText.Period.DAY.label)
        assertFalse(ProgressText.graphTitle(ProgressText.Period.MONTH) == ProgressText.Period.MONTH.label)
    }

    @Test
    fun `chaque periode a un titre distinct`() {
        val titres = ProgressText.Period.entries.map { ProgressText.learnedTitle(it) }
        assertEquals(titres.size, titres.toSet().size, "deux périodes ne peuvent pas dire la même chose")
    }

    // -----------------------------------------------------------------------
    // Série de jours
    // -----------------------------------------------------------------------

    @Test
    fun `la serie s'accorde au singulier et au pluriel`() {
        assertEquals("1 jour d’affilée", ProgressText.streak(1))
        assertEquals("2 jours d’affilée", ProgressText.streak(2))
        assertEquals("12 jours d’affilée", ProgressText.streak(12))
    }

    @Test
    fun `la serie de zero jour reste au singulier`() {
        // L'usage français met zéro au singulier, et c'est déjà ce que fait l'écran d'accueil.
        assertEquals("0 jour d’affilée", ProgressText.streak(0))
    }

    @Test
    fun `la serie utilise l'apostrophe typographique`() {
        // Le client d'origine écrit « d’affilée » avec U+2019, pas l'apostrophe droite : les deux
        // caractères ne se ressemblent qu'à l'écran, et une apostrophe droite se verrait sur un
        // libellé à côté d'un verset arabe.
        assertTrue(ProgressText.streak(3).contains('’'), "apostrophe typographique attendue")
        assertFalse(ProgressText.streak(3).contains('\''), "pas d'apostrophe droite")
        assertTrue(ProgressText.learnedTitle(ProgressText.Period.DAY).contains('’'))
    }

    // -----------------------------------------------------------------------
    // Comptes et étiquettes
    // -----------------------------------------------------------------------

    @Test
    fun `le pourcentage est arrondi a l'entier et suivi du signe`() {
        assertEquals("0 %", ProgressText.percent(0))
        assertEquals("42 %", ProgressText.percent(42))
        assertEquals("100 %", ProgressText.percent(100))
    }

    @Test
    fun `le denominateur du compteur de versets est precede d'une barre oblique`() {
        assertEquals("/ 6236", ProgressText.ofTotal(6236))
    }

    @Test
    fun `les cinq barres du graphique mensuel sont numerotees a partir de un`() {
        assertEquals(listOf("S1", "S2", "S3", "S4", "S5"), (0..4).map { ProgressText.weekLabel(it) })
    }

    @Test
    fun `le bouton du graphique dit ce qu'il fera`() {
        assertEquals("Voir le graphique de la période", ProgressText.graphToggle(shown = false))
        assertEquals("Masquer le graphique", ProgressText.graphToggle(shown = true))
    }

    @Test
    fun `les etiquettes fixes sont toutes renseignees`() {
        val fixes = listOf(
            ProgressText.TITLE,
            ProgressText.SUBTITLE,
            ProgressText.MEMORIZED_VERSES,
            ProgressText.REGULARITY,
            ProgressText.MEMORIZED_PAGES,
            ProgressText.REVISIONS_DONE,
            ProgressText.GOALS_TITLE,
            ProgressText.SEE_ALL,
            ProgressText.CURRENT_GOAL,
            ProgressText.STATISTICS,
            ProgressText.JUZ_DONE,
            ProgressText.ACTIVE_DAYS,
            ProgressText.PAGES_READ,
            ProgressText.VERSES_MEMORIZED,
        )
        assertTrue(fixes.all { it.isNotBlank() }, "aucune étiquette ne doit être vide")
        assertEquals(fixes.size, fixes.toSet().size, "deux étiquettes fixes ne peuvent pas être identiques")
    }

    @Test
    fun `le titre de l'ecran differe de celui de l'onglet`() {
        // L'onglet s'appelle « Progrès » ; la carte de tête dit « Ma progression ». Deux mots
        // différents pour la même chose seraient une inattention, pas un choix.
        assertEquals("Ma progression", ProgressText.TITLE)
        assertTrue(ProgressText.TITLE != "Progrès")
    }
}
