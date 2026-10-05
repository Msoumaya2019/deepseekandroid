package com.msoumaya.deepseekandroid.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Les décisions du panneau des marques-pages : quelles entrées, et quelle notice.
 *
 * ## Les trois endroits où une erreur serait silencieuse
 *
 * Le panneau est une fenêtre sans état propre : tout ce qu'il montre vient d'ici. Une erreur ne
 * lèverait donc rien — elle afficherait simplement autre chose :
 *
 *  - une entrée **absente** alors que sa destination est branchée : la personne ne peut plus
 *    poser de signet, et rien ne le dit ;
 *  - une entrée **présente** alors qu'elle ne mène nulle part : le panneau se referme sur rien ;
 *  - une notice **remplacée** par une autre : on demanderait de toucher un verset sous un
 *    message annonçant que c'est déjà fait.
 *
 * Ces trois-là sont éprouvées ici. Le **rendu** ne l'est pas : c'est un `@Composable`, et
 * `ARCHITECTURE.md` dit ce que le projet ne mesure pas.
 */
class BookmarksPanelTest {

    private val pose = BookmarksText.PanelAction.PLACE
    private val liste = BookmarksText.PanelAction.OPEN_LIST

    // -----------------------------------------------------------------------
    // Les entrées
    // -----------------------------------------------------------------------

    @Test
    fun `le panneau propose poser puis consulter, dans cet ordre`() {
        // L'ordre vient du client d'origine : on pose, puis on consulte. L'inverse inviterait à
        // chercher une liste qui n'existe pas encore.
        assertEquals(
            listOf(BookmarksText.PLACE, BookmarksText.OPEN_LIST),
            BookmarksText.PANEL.map { it.title },
        )
    }

    @Test
    fun `les deux entrees sont celles du client d'origine`() {
        assertEquals(
            listOf(pose, liste),
            BookmarksText.PANEL.map { it.action },
        )
        assertEquals(2, BookmarksText.PANEL.size, "Le panneau n'a que deux destinations.")
    }

    @Test
    fun `une destination non branchee est retiree, pas grisee`() {
        // Tant que l'écran de la liste n'existe pas, seul l'appelant qui sait enregistrer passe
        // PLACE : le panneau ne doit alors montrer que la pose.
        assertEquals(
            listOf(BookmarksText.PLACE),
            BookmarksText.panelRows(setOf(pose)).map { it.title },
        )
        assertEquals(
            listOf(BookmarksText.OPEN_LIST),
            BookmarksText.panelRows(setOf(liste)).map { it.title },
        )
    }

    @Test
    fun `l'ordre du panneau survit au filtrage`() {
        // Les deux sont branchées : l'ordre doit rester celui de `PANEL`, quelle que soit la
        // façon dont l'ensemble a été construit.
        assertEquals(
            listOf(BookmarksText.PLACE, BookmarksText.OPEN_LIST),
            BookmarksText.panelRows(setOf(liste, pose)).map { it.title },
        )
    }

    @Test
    fun `sans destination le panneau n'a rien a proposer`() {
        assertTrue(BookmarksText.panelRows(emptySet()).isEmpty())
        assertFalse(BookmarksText.isPanelUseful(emptySet()))
        assertTrue(BookmarksText.isPanelUseful(setOf(pose)))
        assertTrue(BookmarksText.isPanelUseful(setOf(liste)))
    }

    // -----------------------------------------------------------------------
    // La notice
    // -----------------------------------------------------------------------

    @Test
    fun `en mode de pose la notice dit le geste a faire`() {
        assertEquals(
            BookmarksText.PLACE_NOTICE,
            BookmarksText.notice(placing = true, saved = false),
        )
    }

    @Test
    fun `apres la pose la notice confirme`() {
        assertEquals(
            BookmarksText.SAVED_NOTICE,
            BookmarksText.notice(placing = false, saved = true),
        )
    }

    @Test
    fun `la pose l'emporte sur la confirmation`() {
        // Le cas réel : on pose un signet, la confirmation s'affiche, et on en pose un second
        // avant qu'elle s'efface. Montrer « enregistré » alors qu'on demande de toucher un
        // verset serait un contresens.
        assertEquals(
            BookmarksText.PLACE_NOTICE,
            BookmarksText.notice(placing = true, saved = true),
        )
    }

    @Test
    fun `sans pose ni confirmation il n'y a pas de notice`() {
        assertNull(BookmarksText.notice(placing = false, saved = false))
    }

    // -----------------------------------------------------------------------
    // Les mots, repris du client d'origine
    // -----------------------------------------------------------------------

    @Test
    fun `les mots du panneau sont ceux du client d'origine`() {
        // La fidélité des chaînes se vérifie ici, et non dans un diff : un mot changé dans un
        // objet de texte ne ferait rougir aucun autre test.
        assertEquals("Marques-pages", BookmarksText.PANEL_TITLE)
        assertEquals("Placer un marque-page sur un verset", BookmarksText.PLACE)
        assertEquals("Mes marques-pages", BookmarksText.OPEN_LIST)
        assertEquals("Touche le verset exact à enregistrer", BookmarksText.PLACE_NOTICE)
        assertEquals("Marque-page enregistré", BookmarksText.SAVED_NOTICE)
    }

    @Test
    fun `la duree de la confirmation est celle du client d'origine`() {
        // Le source : `setTimeout(() => setBookmarkNotice(''), 2500)`.
        assertEquals(2500L, BookmarksText.SAVED_NOTICE_MS)
    }
}
