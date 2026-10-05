package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.MushafSource
import com.msoumaya.deepseekandroid.core.model.VerseBookmark
import com.msoumaya.deepseekandroid.core.model.effectiveBookmarks
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Les règles de l'écran des marques-pages : ce qu'une ligne montre, dans quel ordre, et ce
 * qu'elle fait d'un état qu'elle n'a pas écrit.
 *
 * ## Ce que ces tests couvrent, et ce qu'ils ne peuvent pas couvrir
 *
 * Deux choses sont mesurables dès maintenant et le sont donc ici :
 *
 *  * la **source** change la page d'un signet — 56 versets sur 6 236 ne tombent pas sur la même
 *    page dans le découpage de Médine et dans celui du paquet « Coran 1441 » ;
 *  * un **état hors corpus** ne fait pas tomber l'écran : la ligne est omise.
 *
 * Une troisième ne l'est pas, et il serait malhonnête de la simuler : la **page notée** au
 * moment de la pose. Dans les bornes livrées, un verset n'occupe qu'une seule page par
 * découpage, donc `MushafSourceNavigation.versePage` rend toujours la page du verset, que la
 * page notée soit fournie ou non. Le test `dans le decoupage livre, un verset n'occupe qu'une
 * seule page` **mesure l'hypothèse** au lieu de la supposer : le jour où les données changeront,
 * il échouera et forcera à relire `Bookmarks.pageFor`.
 */
class BookmarksScreenRulesTest {

    @Before
    fun setUp() = QuranFixture.install()

    private val t1 = "2026-10-05T10:00:00Z"
    private val t2 = "2026-10-05T11:00:00Z"
    private val t3 = "2026-10-06T10:00:00Z"

    // -----------------------------------------------------------------------
    // Ce qu'une ligne montre
    // -----------------------------------------------------------------------

    @Test
    fun `une ligne porte la sourate, le verset et le texte du referentiel`() {
        val state = Bookmarks.saveBookmark(QuranFixture.onboardedState(), 8, at = t1)
        val ligne = Bookmarks.rows(state, MushafSource.MEDINA).single()

        assertEquals(8, ligne.verseId)
        assertEquals("Al Baqarah", ligne.surahName)
        assertEquals(1, ligne.ayah)
        assertEquals(2, ligne.page)
        assertEquals(Quran.verseAt(8).text, ligne.text)
        assertFalse(ligne.lastUsed, "un signet jamais repris n'est pas « dernière reprise »")
    }

    @Test
    fun `une ligne se construit sur le verset, pas sur les champs enregistres`() {
        val valide = Bookmarks.saveBookmark(QuranFixture.onboardedState(), 8, at = t1)
        // Un signet dont les champs enregistrés mentent : le verset, lui, ne ment pas.
        val menteur = valide.effectiveBookmarks.getValue("8").copy(surah = 1, ayah = 99)
        val state = valide.copy(bookmarks = mapOf("8" to menteur))

        val ligne = Bookmarks.rows(state, MushafSource.MEDINA).single()
        assertEquals("Al Baqarah", ligne.surahName)
        assertEquals(1, ligne.ayah)
        assertEquals(Quran.verseAt(8).text, ligne.text)
    }

    // -----------------------------------------------------------------------
    // L'ordre et la « dernière reprise »
    // -----------------------------------------------------------------------

    @Test
    fun `les lignes suivent l'ordre des signets visibles`() {
        var state = QuranFixture.onboardedState()
        state = Bookmarks.saveBookmark(state, 1, at = t1)
        state = Bookmarks.saveBookmark(state, 100, at = t2)
        state = Bookmarks.useBookmark(state, 1, at = t3)

        assertEquals(listOf(1, 100), Bookmarks.rows(state, MushafSource.MEDINA).map { it.verseId })
    }

    @Test
    fun `la derniere reprise marque le signet repris, et lui seul`() {
        var state = QuranFixture.onboardedState()
        state = Bookmarks.saveBookmark(state, 8, at = t1)
        state = Bookmarks.saveBookmark(state, 100, at = t2)
        state = Bookmarks.useBookmark(state, 8, at = t3)

        assertEquals(8, Bookmarks.lastUsedId(state))
        val marques = Bookmarks.rows(state, MushafSource.MEDINA).filter { it.lastUsed }.map { it.verseId }
        assertEquals(listOf(8), marques)
    }

    @Test
    fun `un signet jamais repris n'est pas une derniere reprise`() {
        var state = QuranFixture.onboardedState()
        state = Bookmarks.saveBookmark(state, 8, at = t1)
        state = Bookmarks.saveBookmark(state, 100, at = t2)

        // Le signet le plus récemment *modifié* est le 100 : c'est la tête de la liste, et c'est
        // pour cela que `lastUsedId` retrie au lieu de la lire. « Dernière reprise » ne se dit
        // que d'un signet qu'on a effectivement repris.
        assertEquals(100, Bookmarks.visibleBookmarks(state).first().verseId)
        assertNull(Bookmarks.lastUsedId(state), "aucun signet n'a été repris")
        assertTrue(Bookmarks.rows(state, MushafSource.MEDINA).none { it.lastUsed })
    }

    // -----------------------------------------------------------------------
    // Ce qu'un état venu d'ailleurs ne doit pas casser
    // -----------------------------------------------------------------------

    @Test
    fun `un signet hors corpus est omis sans faire tomber l'ecran`() {
        val valide = Bookmarks.saveBookmark(QuranFixture.onboardedState(), 8, at = t1)
        val horsCorpus = VerseBookmark(
            verseId = 7000,
            surah = 200,
            ayah = 1,
            page = 700,
            createdAt = t1,
            updatedAt = t2,
        )
        val state = valide.copy(bookmarks = valide.effectiveBookmarks + ("7000" to horsCorpus))

        assertEquals(listOf(8), Bookmarks.rows(state, MushafSource.MEDINA).map { it.verseId })

        // La garde de `rows` porte réellement quelque chose : sans elle, la même ligne ferait
        // lever la construction du nom de sourate et du texte. Le type exact de l'erreur n'est
        // pas le sujet — c'est une erreur d'index — mais le fait qu'elle lève, oui.
        assertFailsWith<RuntimeException> { Bookmarks.pageFor(state, MushafSource.MEDINA, 7000) }
    }

    @Test
    fun `un signet supprime ne fait plus de ligne`() {
        var state = Bookmarks.saveBookmark(QuranFixture.onboardedState(), 8, at = t1)
        state = Bookmarks.deleteBookmark(state, 8, at = t2)

        assertTrue(Bookmarks.rows(state, MushafSource.MEDINA).isEmpty())
    }

    // -----------------------------------------------------------------------
    // Le découpage de la source
    // -----------------------------------------------------------------------

    @Test
    fun `la page d'un signet suit le decoupage de la source affichee`() {
        val id = 746 // Al Mâ'idah 77 : page 121 à Médine, page 120 dans le paquet 1441.
        val medine = Quran.pageOf(id)
        val zip = ZipQuranSource.versePage(id)
        assertNotEquals(
            medine,
            zip,
            "les deux découpages doivent diverger sur ce verset, sinon ce test ne prouve rien",
        )

        val state = Bookmarks.saveBookmark(QuranFixture.onboardedState(), id, at = t1)
        assertEquals(medine, Bookmarks.pageFor(state, MushafSource.MEDINA, id))
        assertEquals(zip, Bookmarks.pageFor(state, MushafSource.CORAN_1441, id))
    }

    @Test
    fun `dans le decoupage livre, un verset n'occupe qu'une seule page`() {
        // C'est l'hypothèse qui rend inerte la branche « conserver la page notée » de
        // `MushafSourceNavigation.versePage`. Elle est mesurée ici, et non supposée : le jour où
        // un verset s'étalera sur deux pages, ce test échouera — et c'est le signal qu'il faut
        // relire `Bookmarks.pageFor`, pas un test à réparer.
        val aCheval = (1..Quran.verses.size).filter { ZipQuranSource.versePages(it).size > 1 }
        assertTrue(aCheval.isEmpty(), "versets à cheval sur deux pages : $aCheval")
    }

    @Test
    fun `la cle d'une page de signet n'est pas la cle repliee des pages d'etude`() {
        assertEquals(
            ZipQuranSource.ID,
            MushafSource.CORAN_1441.persistedKey,
            "l'identifiant du paquet et la clé persistée doivent être le même mot",
        )
        assertNotEquals(
            StudyProgressCalculator.sourceKey(MushafSource.CORAN_TEST),
            MushafSource.CORAN_TEST.persistedKey,
            "le repli et la clé persistée sont deux règles différentes",
        )
    }

    // -----------------------------------------------------------------------
    // Les mots
    // -----------------------------------------------------------------------

    @Test
    fun `les mots de l'ecran et du panneau sont ceux du client d'origine`() {
        // Ces chaînes sont reprises caractère pour caractère du client React Native. Le test ne
        // peut pas relire la source — elle n'est pas dans ce dépôt, et elle est en lecture
        // seule — il fige donc ce qui a été lu : c'est un garde-fou de fidélité contre une
        // retouche future, pas une preuve de la lecture initiale.
        assertEquals("Marques-pages", BookmarksText.PANEL_TITLE)
        assertEquals("Placer un marque-page sur un verset", BookmarksText.PLACE)
        assertEquals("Mes marques-pages", BookmarksText.OPEN_LIST)
        assertEquals("Touche le verset exact à enregistrer", BookmarksText.PLACE_NOTICE)
        assertEquals("Marque-page enregistré", BookmarksText.SAVED_NOTICE)
        assertEquals("Mes marques-pages", BookmarksText.TITLE)
        assertEquals("Retrouve facilement tes passages enregistrés", BookmarksText.SUBTITLE)
        assertEquals(
            "Chaque marque-page conserve le verset exact où reprendre ta lecture.",
            BookmarksText.EXPLANATION,
        )
        assertEquals(
            "Aucun marque-page enregistré. Touche « Marque-page », puis un verset sur la page.",
            BookmarksText.EMPTY,
        )
        assertEquals("Dernière reprise", BookmarksText.LAST_USED)
        assertEquals("Reprendre", BookmarksText.RESUME)
        assertEquals("Retour à la lecture", BookmarksText.BACK)
        assertEquals("Supprimer ce marque-page ?", BookmarksText.DELETE_TITLE)
        assertEquals("Le verset restera disponible dans le Coran.", BookmarksText.DELETE_BODY)
        assertEquals("Annuler", BookmarksText.DELETE_CANCEL)
        assertEquals("Supprimer", BookmarksText.DELETE_CONFIRM)
    }

    @Test
    fun `le libelle d'une ligne porte la page de la source et le numero du verset`() {
        assertEquals("Page 120 · Verset 77", BookmarksText.pageLabel(120, 77))
        assertEquals(
            "Supprimer le marque-page Al Mâ'idah verset 77",
            BookmarksText.deleteLabel("Al Mâ'idah", 77),
        )
    }
}
