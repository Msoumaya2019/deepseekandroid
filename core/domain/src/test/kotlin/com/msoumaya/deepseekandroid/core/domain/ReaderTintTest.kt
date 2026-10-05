package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AdminDifficultyStamp
import com.msoumaya.deepseekandroid.core.model.DifficultyMarker
import com.msoumaya.deepseekandroid.core.model.DifficultyStamp
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Priorité des teintes de la page.
 *
 * C'est la seule chose que cette règle a à décider : **laquelle l'emporte** quand un verset
 * porte plusieurs marques. Le reste — quels versets sont marqués — est l'affaire de
 * `ReaderMarksTest` ; la couleur, elle, est l'affaire de la vue.
 *
 * Le cas qui distingue vraiment les deux rendus de la source est **signet contre lecture** :
 * `MushafPage.tsx` donne le signet gagnant, `coranTest/html.ts` donne la lecture gagnante. Ce
 * portage suit le premier, et ces tests le figent — sans quoi un ordre recopié de l'autre rendu
 * passerait inaperçu, et la couleur d'un verset changerait sans que rien ne le dise.
 */
class ReaderTintTest {

    @Before
    fun setUp() = QuranFixture.install()

    @Test
    fun `un verset sans marque n'est pas teinte`() {
        assertNull(ReaderTint.kindOf(verseId = 8, playingVerse = null, bookmarkIds = emptySet(), difficultIds = emptySet()))
    }

    @Test
    fun `un verset difficile est teinte en difficile`() {
        assertEquals(
            TintKind.DIFFICULT,
            ReaderTint.kindOf(8, null, emptySet(), setOf(8)),
        )
    }

    @Test
    fun `un verset en signet est teinte en signet`() {
        assertEquals(
            TintKind.BOOKMARK,
            ReaderTint.kindOf(8, null, setOf(8), emptySet()),
        )
    }

    @Test
    fun `le verset en cours d'ecoute est teinte en lecture`() {
        assertEquals(
            TintKind.PLAYING,
            ReaderTint.kindOf(8, 8, emptySet(), emptySet()),
        )
    }

    @Test
    fun `la difficulte l'emporte sur le signet`() {
        assertEquals(
            TintKind.DIFFICULT,
            ReaderTint.kindOf(8, null, bookmarkIds = setOf(8), difficultIds = setOf(8)),
        )
    }

    @Test
    fun `la difficulte l'emporte sur la lecture`() {
        assertEquals(
            TintKind.DIFFICULT,
            ReaderTint.kindOf(8, playingVerse = 8, bookmarkIds = emptySet(), difficultIds = setOf(8)),
        )
    }

    @Test
    fun `le signet l'emporte sur la lecture`() {
        // Le cas qui sépare les deux rendus de la source. L'inverser ferait changer la couleur
        // d'un verset qu'on écoute et qu'on a mis en signet, sans qu'aucun autre test ne bouge.
        assertEquals(
            TintKind.BOOKMARK,
            ReaderTint.kindOf(8, playingVerse = 8, bookmarkIds = setOf(8), difficultIds = emptySet()),
        )
    }

    @Test
    fun `la difficulte l'emporte sur les deux autres marques reunies`() {
        assertEquals(
            TintKind.DIFFICULT,
            ReaderTint.kindOf(8, playingVerse = 8, bookmarkIds = setOf(8), difficultIds = setOf(8)),
        )
    }

    @Test
    fun `un verset hors du corpus n'est jamais teinte`() {
        // Aucune borne n'est vérifiée : un identifiant qui n'existe pas ne peut être ni en
        // signet, ni difficile, ni en cours d'écoute. Il n'y a rien à refuser, et rien à lever.
        assertNull(ReaderTint.kindOf(9999, null, setOf(8), setOf(9)))
    }

    @Test
    fun `la teinte se decide sur les ensembles reels du domaine`() {
        // Les deux règles se composent : ce que l'état dit marqué est ce qui décide la couleur.
        var state = QuranFixture.onboardedState()
        state = Bookmarks.saveBookmark(state, 8, at = "2026-10-05T10:00:00Z")
        state = state.copy(
            difficultyMarkers = mapOf(
                "9" to DifficultyMarker(admin = AdminDifficultyStamp("2026-10-05", "à revoir")),
                "10" to DifficultyMarker(user = DifficultyStamp("2026-10-05")),
            ),
        )

        val bookmarks = Bookmarks.bookmarkedIds(state)
        val difficult = Review.difficultIds(state)

        assertEquals(TintKind.BOOKMARK, ReaderTint.kindOf(8, playingVerse = 8, bookmarks, difficult))
        assertEquals(TintKind.DIFFICULT, ReaderTint.kindOf(9, playingVerse = 9, bookmarks, difficult))
        assertEquals(TintKind.DIFFICULT, ReaderTint.kindOf(10, playingVerse = 9, bookmarks, difficult))
        assertEquals(TintKind.PLAYING, ReaderTint.kindOf(11, playingVerse = 11, bookmarks, difficult))
        assertNull(ReaderTint.kindOf(12, playingVerse = 11, bookmarks, difficult))
    }
}
