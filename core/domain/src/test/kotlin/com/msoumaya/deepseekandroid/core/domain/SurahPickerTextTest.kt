package com.msoumaya.deepseekandroid.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Éprouve la règle de saisie de page du sélecteur de sourate.
 *
 * Ce qui se paie cher, ici, n'est pas une page refusée à tort — c'est une page **acceptée** à
 * tort : on tape 700, l'écran ne dit rien, et le lecteur se retrouve sur une page qui n'existe
 * pas. La page blanche qui suit se lit comme une panne d'affichage.
 *
 * Les cas couverts sont ceux qu'un clavier numérique rend **atteignables**. Les deux formes que
 * JavaScript accepterait et que Kotlin refuse — `0x10` et `1e2` — sont testées elles aussi,
 * pour que l'écart documenté dans le KDoc soit vérifié plutôt que supposé.
 */
class SurahPickerTextTest {

    @Test
    fun `les libelles sont ceux du client d'origine`() {
        assertEquals("Choisir une sourate", SurahPickerText.TITLE)
        assertEquals("Les 114 sourates du Coran", SurahPickerText.SUBTITLE)
        assertEquals("Fermer", SurahPickerText.CLOSE)
        assertEquals("Page 1 à 604", SurahPickerText.PAGE_PLACEHOLDER)
        assertEquals("Aller à la page", SurahPickerText.GO)
        assertEquals("Page invalide", SurahPickerText.INVALID_PAGE_TITLE)
        assertEquals("Choisis une page entre 1 et 604.", SurahPickerText.INVALID_PAGE_BODY)
    }

    @Test
    fun `le nombre de versets est annonce au singulier comme au pluriel`() {
        // « 1 versets » se verrait tout de suite ; l'original ne le corrige pas, et on ne le
        // corrige pas non plus — mais on le mesure, pour que la reprise soit un choix.
        assertEquals("7 versets", SurahPickerText.versesLabel(7))
        assertEquals("1 versets", SurahPickerText.versesLabel(1))
        assertEquals("286 versets", SurahPickerText.versesLabel(286))
    }

    @Test
    fun `les deux pages extremes sont acceptees`() {
        assertEquals(1, SurahPickerText.pageFromInput("1"))
        assertEquals(604, SurahPickerText.pageFromInput("604"))
    }

    @Test
    fun `les espaces autour sont ignores`() {
        // Le client d'origine passe par `Number`, qui rogne les espaces. Kotlin, lui,
        // `toIntOrNull` echoue sur « 12 ». Sans le `trim`, une saisie qui a garde une espace
        // serait refusee ici et acceptee la-bas — deux clients, deux comportements.
        assertEquals(12, SurahPickerText.pageFromInput(" 12 "))
        assertEquals(12, SurahPickerText.pageFromInput("\t12\n"))
    }

    @Test
    fun `les zeros de tete et le signe ne changent pas la page`() {
        assertEquals(7, SurahPickerText.pageFromInput("007"))
        assertEquals(7, SurahPickerText.pageFromInput("+7"))
    }

    @Test
    fun `une saisie vide ne designe aucune page`() {
        assertNull(SurahPickerText.pageFromInput(""))
        assertNull(SurahPickerText.pageFromInput("   "))
    }

    @Test
    fun `les bornes sont refusees des deux cotes`() {
        // 0 est le piege : `Number('')` vaut 0 en JavaScript, donc une saisie vide y donnerait
        // « page 0 » sans le controle de bornes. Ici le vide rend `null` avant, et le 0 explicite
        // est refuse par les bornes — les deux chemins menent au refus.
        assertNull(SurahPickerText.pageFromInput("0"))
        assertNull(SurahPickerText.pageFromInput("-1"))
        assertNull(SurahPickerText.pageFromInput("605"))
        assertNull(SurahPickerText.pageFromInput("9999"))
    }

    @Test
    fun `une saisie qui n'est pas un entier est refusee`() {
        assertNull(SurahPickerText.pageFromInput("12.5"))
        assertNull(SurahPickerText.pageFromInput("abc"))
        assertNull(SurahPickerText.pageFromInput("12a"))
        assertNull(SurahPickerText.pageFromInput("1 2"))
        assertNull(SurahPickerText.pageFromInput("--1"))
    }

    @Test
    fun `un nombre trop grand pour un entier est refuse, pas tronque`() {
        // Le piege serait de passer par un `toLong` puis de reduire : 4294967297 se reduirait a
        // 1, et une saisie absurde ouvrirait la premiere page comme si elle avait ete demandee.
        assertNull(SurahPickerText.pageFromInput("2147483648"))
        assertNull(SurahPickerText.pageFromInput("99999999999999999999"))
    }

    @Test
    fun `l'ecart assume avec JavaScript est verifie, pas suppose`() {
        // `Number('0x10')` vaut 16 et `Number('1e2')` vaut 100 : les deux passeraient les bornes
        // et seraient acceptes par le client d'origine. Aucun clavier numerique ne les produit,
        // et une page 16 qui vient de « 0x10 » n'est pas ce que la personne a voulu ecrire.
        assertNull(SurahPickerText.pageFromInput("0x10"))
        assertNull(SurahPickerText.pageFromInput("1e2"))
    }

    @Test
    fun `le nombre de pages vient de l'appelant, pas d'une constante`() {
        // Les sources n'ont pas toutes le meme decoupage. Une regle qui lirait 604 en dur
        // accepterait une page que la source affichee ne porte pas.
        assertEquals(5, SurahPickerText.pageFromInput("5", totalPages = 5))
        assertNull(SurahPickerText.pageFromInput("6", totalPages = 5))
    }
}
