package com.msoumaya.deepseekandroid.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Éprouve la règle d'affichage de la feuille d'options du lecteur.
 *
 * Ce qui se paie cher, ici, n'est pas une ligne oubliée — c'est une ligne **offerte alors que
 * son écran n'existe pas**. On la touche, rien ne se passe, et c'est tout le lecteur qui paraît
 * cassé. La règle qui l'empêche est [ReaderOptionsText.visible], et c'est elle que ces cas
 * mesurent : elle **retire** au lieu de griser.
 *
 * Le second point tenu ici est l'**ordre**. Les quatre lignes vont du plus fréquent au plus
 * rare ; un ensemble ne porte pas d'ordre, donc si `visible` ne le reprenait pas de `ALL`, la
 * feuille changerait d'ordre selon l'appelant — sans qu'aucun écran ne le montre.
 */
class ReaderOptionsTextTest {

    @Test
    fun `les libelles sont ceux du client d'origine`() {
        assertEquals("Plus d’options", ReaderOptionsText.TITLE)
        assertEquals("Fermer les options", ReaderOptionsText.CLOSE)

        assertEquals(
            listOf(
                "Changer de sourate" to "Choisir une sourate ou aller à une page",
                "Traduction française" to "Afficher / masquer la traduction",
                "Réglages audio" to "Récitateur, répétitions et vitesse",
                "Affichage du Coran" to "Choisir le style, la taille et les options",
            ),
            ReaderOptionsText.ALL.map { it.title to it.subtitle },
        )
    }

    @Test
    fun `le titre porte l'apostrophe typographique de l'original`() {
        // Ce n'est pas une coquetterie : l'apostrophe droite se verrait au milieu d'un titre
        // français, et la corriger « pour simplifier » ferait diverger les deux clients sur un
        // texte que l'utilisateur lit. Le cas est donc mesuré, pas supposé.
        assertTrue(
            ReaderOptionsText.TITLE.contains('\u2019'),
            "le titre doit porter U+2019 (apostrophe typographique)",
        )
        assertFalse(
            ReaderOptionsText.TITLE.contains('\''),
            "le titre ne doit pas porter U+0027 (apostrophe droite)",
        )
    }

    @Test
    fun `les quatre destinations sont distinctes et couvertes`() {
        assertEquals(4, ReaderOptionsText.ALL.size)
        assertEquals(
            ReaderOptionsText.Action.entries.toSet(),
            ReaderOptionsText.ALL.map { it.action }.toSet(),
        )
        // Deux lignes de même titre seraient indiscernables à l'écran comme à la voix.
        assertEquals(4, ReaderOptionsText.ALL.map { it.title }.toSet().size)
    }

    @Test
    fun `les quatre destinations branchees donnent les quatre lignes dans l'ordre`() {
        val rows = ReaderOptionsText.visible(ReaderOptionsText.Action.entries.toSet())
        assertEquals(ReaderOptionsText.ALL, rows)
    }

    @Test
    fun `l'ordre d'origine est conserve quel que soit l'ordre recu`() {
        // Un `Set` ne promet aucun ordre. On en construit un qui les présente à l'envers : si
        // `visible` suivait l'ordre reçu, la feuille s'afficherait dans cet ordre-là.
        val reversed = linkedSetOf(
            ReaderOptionsText.Action.DISPLAY,
            ReaderOptionsText.Action.AUDIO,
            ReaderOptionsText.Action.TRANSLATION,
            ReaderOptionsText.Action.SURAH,
        )
        assertEquals(
            listOf(
                ReaderOptionsText.Action.SURAH,
                ReaderOptionsText.Action.TRANSLATION,
                ReaderOptionsText.Action.AUDIO,
                ReaderOptionsText.Action.DISPLAY,
            ),
            ReaderOptionsText.visible(reversed).map { it.action },
        )
    }

    @Test
    fun `une destination absente retire exactement sa ligne`() {
        // Le cas est joué pour **chaque** destination, et non pour une seule : un filtre qui
        // retirerait toujours la même ligne passerait sur un cas isolé.
        for (missing in ReaderOptionsText.Action.entries) {
            val rows = ReaderOptionsText.visible(
                ReaderOptionsText.Action.entries.toSet() - missing,
            )
            assertEquals(3, rows.size, "retirer $missing doit laisser trois lignes")
            assertFalse(
                rows.any { it.action == missing },
                "$missing ne doit plus figurer dans les lignes",
            )
        }
    }

    @Test
    fun `une destination seule ouvre la feuille`() {
        // Chaque destination compte pour un : la feuille n'est pas utile seulement quand le
        // sélecteur de sourate est là.
        for (only in ReaderOptionsText.Action.entries) {
            assertTrue(
                ReaderOptionsText.isUseful(setOf(only)),
                "la feuille doit être utile avec $only seule",
            )
        }
    }

    @Test
    fun `aucune destination branchee ne propose aucune ligne`() {
        assertEquals(emptyList(), ReaderOptionsText.visible(emptySet()))
        assertFalse(ReaderOptionsText.isUseful(emptySet()))
    }

    @Test
    fun `la feuille ne propose jamais une ligne inventee`() {
        // Le filtre ne peut pas fabriquer de ligne : tout ce qu'il rend vient de `ALL`.
        for (action in ReaderOptionsText.Action.entries) {
            for (row in ReaderOptionsText.visible(setOf(action))) {
                assertTrue(
                    ReaderOptionsText.ALL.contains(row),
                    "$row doit venir de ALL, pas d'ailleurs",
                )
            }
        }
    }
}
