package com.msoumaya.deepseekandroid.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Les mots de l'écran d'objectif
// ---------------------------------------------------------------------------
// Ces contrôles ne sont pas des redites du fichier : ils portent sur ce qu'on **ne voit pas** en
// le relisant. Un libellé recopié à la main peut perdre son apostrophe typographique — `Juz'` au
// lieu de `Juz’` — ou son signe de rub‘, et l'écran s'afficherait alors avec un caractère de
// remplacement ou une apostrophe droite, sans qu'aucune compilation ne s'en plaigne. Ce sont des
// textes d'interface validés par le propriétaire du projet : un caractère changé est un défaut,
// pas une variante.
//
// Les contrôles portent donc sur les points de code (U+2019, U+2018), sur l'ordre des listes que
// le sélecteur affiche tel quel, et sur les phrases composées à partir de la valeur choisie.
// ---------------------------------------------------------------------------

class GoalTextTest {

    /** L'apostrophe typographique de « Juz’ ». */
    private val apostrophe = '\u2019'

    /** Le signe du rub‘, qui n'est pas une apostrophe. */
    private val rubu = '\u2018'

    @Test
    fun `l'apostrophe de Juz’ est typographique`() {
        assertTrue(
            GoalText.JUZ.contains(apostrophe),
            "« Juz’ » doit porter U+2019 : une apostrophe droite se verrait à l'écran.",
        )
        assertFalse(
            GoalText.JUZ.contains('\''),
            "« Juz’ » ne doit pas porter d'apostrophe droite.",
        )
    }

    @Test
    fun `le signe du rub‘ est typographique`() {
        assertTrue(
            GoalText.PER_RUBU.contains(rubu),
            "« Par rubu‘ » doit porter U+2018, et non une apostrophe droite.",
        )
        assertFalse(GoalText.PER_RUBU.contains('\''))
    }

    @Test
    fun `la note de l'apercu porte l'apostrophe typographique`() {
        assertTrue(
            GoalText.PREVIEW_NOTE.contains(apostrophe),
            "« … tes jours d’apprentissage » doit porter U+2019.",
        )
    }

    @Test
    fun `les trois unites sont dans l'ordre du selecteur`() {
        assertEquals(listOf("Sourate", "Hizb", "Juz’"), GoalText.units)
    }

    @Test
    fun `les trois unites de rythme sont dans l'ordre du selecteur`() {
        assertEquals(listOf("Par page", "Par verset", "Par rubu‘"), GoalText.paceUnits)
    }

    @Test
    fun `le libelle de connaissance nomme l'unite affichee`() {
        assertEquals("Dernier Hizb appris", GoalText.lastDivision(GoalText.HIZB))
        assertEquals("Dernier Juz’ appris", GoalText.lastDivision(GoalText.JUZ))
    }

    @Test
    fun `le libelle d'objectif distingue la sourate des deux divisions`() {
        // La sourate porte son **nom**, les deux autres leur **numéro** : deux formes, et les
        // confondre afficherait « Finir le 2 » là où l'on attend « Finir Al-Baqara ».
        assertEquals("Finir Al-Fâtiha", GoalText.finishSurah("Al-Fâtiha"))
        assertEquals("Finir le Hizb 12", GoalText.finishDivision(GoalText.HIZB, 12))
        assertEquals("Finir le Juz’ 30", GoalText.finishDivision(GoalText.JUZ, 30))
    }

    @Test
    fun `le rappel et la ligne de rythme sont des phrases composees`() {
        assertEquals("Objectif actuel : Juz’ ‘Amma", GoalText.currentGoal("Juz’ ‘Amma"))
        assertEquals("3 versets / jour", GoalText.paceLine("3 versets"))
        assertEquals("1 hizb / jour", GoalText.paceLine("1 hizb"))
    }

    @Test
    fun `le refus de date nomme le format attendu`() {
        assertEquals("Indique une date valide au format AAAA-MM-JJ.", GoalText.BAD_DATE)
        assertEquals("AAAA-MM-JJ", GoalText.DATE_PLACEHOLDER)
    }

    @Test
    fun `la note de l'echeance dit que la date ne calcule rien`() {
        // La phrase est le seul endroit où l'application explique que l'échéance est un repère.
        // Mesuré : `Program.generateProgram` ne lit jamais `goal.deadline` — la phrase dit donc
        // exactement ce que le domaine fait, et non une intention.
        assertEquals(
            "La date est un repère ; les séances sont calculées selon ton rythme.",
            GoalText.DEADLINE_NOTE,
        )
    }
}
