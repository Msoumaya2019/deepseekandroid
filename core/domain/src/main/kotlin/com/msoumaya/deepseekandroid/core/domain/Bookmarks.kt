package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.VerseBookmark

/**
 * Signets.
 *
 * Porté depuis `src/core/bookmarks.ts`. Le point délicat est la **suppression logique** :
 * un signet supprimé n'est pas retiré de la carte, il reçoit un `deletedAt`. Sans ce
 * marqueur, un autre appareil qui possède encore le signet le réintroduirait à la
 * synchronisation suivante.
 */
object Bookmarks {

    fun saveBookmark(
        state: AppState,
        id: Int,
        at: String = Dates.nowIso(),
        sourcePage: Pair<String, Int>? = null,
    ): AppState {
        require(id in 1..Quran.verses.size) { "Verset inexistant." }
        val verse = Quran.verseAt(id)
        val old = state.bookmarks?.get(id.toString())
        val existing = state.bookmarks ?: emptyMap()
        val updated = VerseBookmark(
            verseId = id,
            surah = verse.surah,
            ayah = verse.ayah,
            page = Quran.pageOf(id),
            createdAt = old?.createdAt ?: at,
            updatedAt = at,
            sourcePages = sourcePage?.let { (source, page) ->
                (old?.sourcePages ?: emptyMap()) + (source to page)
            } ?: old?.sourcePages,
        )
        return state.copy(
            updatedAt = at,
            lastRead = com.msoumaya.deepseekandroid.core.model.LastRead(
                page = sourcePage?.second ?: Quran.pageOf(id),
                verseId = id,
                readAt = at,
            ),
            bookmarks = existing + (id.toString() to updated),
        )
    }

    fun deleteBookmark(state: AppState, id: Int, at: String = Dates.nowIso()): AppState {
        val item = state.bookmarks?.get(id.toString()) ?: return state
        return state.copy(
            updatedAt = at,
            bookmarks = (state.bookmarks ?: emptyMap()) +
                (id.toString() to item.copy(deletedAt = at, updatedAt = at)),
        )
    }

    fun useBookmark(
        state: AppState,
        id: Int,
        at: String = Dates.nowIso(),
        pageOverride: Int? = null,
    ): AppState {
        val item = state.bookmarks?.get(id.toString()) ?: return state
        if (item.deletedAt != null) return state
        return state.copy(
            updatedAt = at,
            lastRead = com.msoumaya.deepseekandroid.core.model.LastRead(
                page = pageOverride ?: Quran.pageOf(id),
                verseId = id,
                readAt = at,
            ),
            bookmarks = (state.bookmarks ?: emptyMap()) +
                (id.toString() to item.copy(lastUsedAt = at, updatedAt = at)),
        )
    }

    /** Signets visibles, du plus récemment utilisé au plus ancien. */
    fun visibleBookmarks(state: AppState): List<VerseBookmark> =
        (state.bookmarks ?: emptyMap()).values
            .filter { it.deletedAt == null }
            .sortedByDescending { it.lastUsedAt ?: it.updatedAt }

    /** Fusion par date de modification, marqueurs de suppression inclus. */
    fun mergeBookmarks(
        a: Map<String, VerseBookmark>?,
        b: Map<String, VerseBookmark>?,
    ): Map<String, VerseBookmark>? {
        if (a == null) return b
        if (b == null) return a
        val merged = a.toMutableMap()
        for ((id, item) in b) {
            val current = merged[id]
            if (current == null || item.updatedAt > current.updatedAt) merged[id] = item
        }
        return merged
    }
}
