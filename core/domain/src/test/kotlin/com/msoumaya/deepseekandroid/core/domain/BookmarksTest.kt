package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.*
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Signets.
 *
 * La suppression est **logique** : le signet reçoit un `deletedAt` au lieu d'être retiré de
 * la carte. Sans ce marqueur, un second appareil qui possède encore le signet le
 * réintroduirait à la synchronisation suivante.
 */
class BookmarksTest {

    @Before
    fun setUp() = QuranFixture.install()

    @Test
    fun `un signet enregistre porte sa sourate, son verset et sa page`() {
        val state = Bookmarks.saveBookmark(QuranFixture.onboardedState(), 8, at = "2026-10-05T10:00:00Z")
        val bookmark = state.effectiveBookmarks.getValue("8")
        assertEquals(8, bookmark.verseId)
        assertEquals(2, bookmark.surah)
        assertEquals(1, bookmark.ayah)
        assertEquals(2, bookmark.page)
        assertEquals("2026-10-05T10:00:00Z", bookmark.createdAt)
        assertNull(bookmark.deletedAt)
    }

    @Test
    fun `enregistrer deux fois conserve la date de creation d'origine`() {
        var state = Bookmarks.saveBookmark(QuranFixture.onboardedState(), 8, at = "2026-10-05T10:00:00Z")
        state = Bookmarks.saveBookmark(state, 8, at = "2026-10-06T10:00:00Z")
        val bookmark = state.effectiveBookmarks.getValue("8")
        assertEquals("2026-10-05T10:00:00Z", bookmark.createdAt)
        assertEquals("2026-10-06T10:00:00Z", bookmark.updatedAt)
    }

    @Test
    fun `enregistrer un signet met a jour la reprise de lecture`() {
        val state = Bookmarks.saveBookmark(QuranFixture.onboardedState(), 8, at = "2026-10-05T10:00:00Z")
        assertEquals(8, state.lastRead?.verseId)
        assertEquals(2, state.lastRead?.page)
    }

    @Test
    fun `un verset inexistant ne peut pas etre mis en signet`() {
        assertFailsWith<IllegalArgumentException> {
            Bookmarks.saveBookmark(QuranFixture.onboardedState(), 0)
        }
        assertFailsWith<IllegalArgumentException> {
            Bookmarks.saveBookmark(QuranFixture.onboardedState(), 6237)
        }
    }

    @Test
    fun `la suppression est logique et masque le signet`() {
        var state = Bookmarks.saveBookmark(QuranFixture.onboardedState(), 8, at = "2026-10-05T10:00:00Z")
        state = Bookmarks.deleteBookmark(state, 8, at = "2026-10-06T10:00:00Z")

        assertTrue(Bookmarks.visibleBookmarks(state).isEmpty(), "un signet supprimé n'est plus visible")
        val stored = state.effectiveBookmarks.getValue("8")
        assertNotNull(stored.deletedAt, "le marqueur de suppression doit subsister pour la synchronisation")
        assertEquals("2026-10-06T10:00:00Z", stored.deletedAt)
    }

    @Test
    fun `utiliser un signet supprime ne fait rien`() {
        var state = Bookmarks.saveBookmark(QuranFixture.onboardedState(), 8, at = "2026-10-05T10:00:00Z")
        state = Bookmarks.deleteBookmark(state, 8, at = "2026-10-06T10:00:00Z")
        val used = Bookmarks.useBookmark(state, 8, at = "2026-10-07T10:00:00Z")
        assertEquals(state, used)
    }

    @Test
    fun `les signets visibles sont tries du plus recemment utilise au plus ancien`() {
        var state = QuranFixture.onboardedState()
        state = Bookmarks.saveBookmark(state, 1, at = "2026-10-05T10:00:00Z")
        state = Bookmarks.saveBookmark(state, 100, at = "2026-10-05T11:00:00Z")
        state = Bookmarks.useBookmark(state, 1, at = "2026-10-06T10:00:00Z")

        val visible = Bookmarks.visibleBookmarks(state)
        assertEquals(listOf(1, 100), visible.map { it.verseId })
        assertEquals("2026-10-06T10:00:00Z", visible.first().lastUsedAt)
    }

    @Test
    fun `la fusion garde la version la plus recente et preserve les suppressions`() {
        var a = QuranFixture.onboardedState()
        a = Bookmarks.saveBookmark(a, 1, at = "2026-10-05T10:00:00Z")
        a = Bookmarks.saveBookmark(a, 2, at = "2026-10-05T10:00:00Z")

        var b = QuranFixture.onboardedState()
        b = Bookmarks.saveBookmark(b, 1, at = "2026-10-06T10:00:00Z")
        b = Bookmarks.saveBookmark(b, 3, at = "2026-10-06T10:00:00Z")

        val merged = Bookmarks.mergeBookmarks(a.bookmarks, b.bookmarks)!!
        assertEquals(setOf("1", "2", "3"), merged.keys)
        assertEquals("2026-10-06T10:00:00Z", merged.getValue("1").updatedAt, "la version la plus récente gagne")

        // Une suppression côté b ne doit pas être annulée par la présence du signet côté a.
        val deleted = Bookmarks.deleteBookmark(b, 1, at = "2026-10-07T10:00:00Z")
        val mergedWithDeletion = Bookmarks.mergeBookmarks(a.bookmarks, deleted.bookmarks)!!
        assertNotNull(mergedWithDeletion.getValue("1").deletedAt)
    }

    @Test
    fun `la fusion d'une carte absente rend l'autre telle quelle`() {
        var state = QuranFixture.onboardedState()
        state = Bookmarks.saveBookmark(state, 1, at = "2026-10-05T10:00:00Z")
        assertEquals(state.bookmarks, Bookmarks.mergeBookmarks(null, state.bookmarks))
        assertEquals(state.bookmarks, Bookmarks.mergeBookmarks(state.bookmarks, null))
        assertNull(Bookmarks.mergeBookmarks(null, null))
    }
}
