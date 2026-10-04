package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.Division
import com.msoumaya.deepseekandroid.core.model.QuranData
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.Surah
import com.msoumaya.deepseekandroid.core.model.Verse

/**
 * Référentiel coranique et opérations de plages.
 *
 * Porté depuis `src/core/quran.ts`. Les signatures et les résultats sont identiques :
 * c'est ce qui permet à un client Android et à un client React Native de désigner
 * exactement les mêmes versets avec les mêmes identifiants.
 *
 * Le référentiel est injecté au démarrage ([initialize]) plutôt que lu depuis un
 * singleton de module, ce qui rend la logique testable sans effet de bord global.
 */
object Quran {

    @Volatile
    private var current: QuranData = QuranData.EMPTY

    @Volatile
    private var pageLookup: IntArray? = null

    val data: QuranData get() = current
    val verses: List<Verse> get() = current.verses
    val surahs: List<Surah> get() = current.surahs
    val juzs: List<Division> get() = current.juzs
    val quarters: List<Division> get() = current.quarters
    val hizbs: List<Division> get() = current.hizbs
    val halves: List<Division> get() = current.halves
    val pages: List<com.msoumaya.deepseekandroid.core.model.PageRef> get() = current.pages
    val weights: IntArray get() = current.weights
    val totalVolume: Int get() = current.totalVolume

    fun initialize(reference: QuranData) {
        current = reference
        pageLookup = null
    }

    // -----------------------------------------------------------------------
    // Correspondances verset ↔ sourate ↔ page
    // -----------------------------------------------------------------------

    /** Identifiant global du verset, ou `null` si la référence n'existe pas. */
    fun verseId(surah: Int, ayah: Int): Int? {
        val s = current.surahs.getOrNull(surah - 1) ?: return null
        return if (ayah in 1..s.count) s.start + ayah - 1 else null
    }

    fun verseAt(id: Int): Verse = current.verses[id - 1]

    fun surahAt(id: Int): Surah = current.surahs[verseAt(id).surah - 1]

    /**
     * Page du moushaf contenant un verset.
     *
     * Le client d'origine fait une recherche binaire sur `pages` à chaque appel. Ici, la
     * table est précalculée une fois : le lecteur appelle cette fonction pour chaque verset
     * affiché et à chaque changement de page, une recherche binaire par appel serait un coût
     * inutile sur un écran qui défile.
     */
    fun pageOf(id: Int): Int {
        val lookup = pageLookup ?: buildPageLookup().also { pageLookup = it }
        if (id in 1 until lookup.size && lookup[id] > 0) return lookup[id]
        throw IllegalStateException("Page introuvable pour ${verseAt(id).surah}:${verseAt(id).ayah}")
    }

    private fun buildPageLookup(): IntArray {
        val lookup = IntArray(current.verses.size + 1)
        for (page in current.pages) {
            val first = verseId(page.first[0], page.first[1]) ?: continue
            val last = verseId(page.last[0], page.last[1]) ?: continue
            for (id in first..last) if (id in lookup.indices) lookup[id] = page.page
        }
        return lookup
    }

    fun pageRange(page: Int): Range {
        val p = current.pages.getOrNull(page - 1) ?: throw IllegalArgumentException("Page invalide")
        val start = verseId(p.first[0], p.first[1]) ?: throw IllegalArgumentException("Page invalide")
        val end = verseId(p.last[0], p.last[1]) ?: throw IllegalArgumentException("Page invalide")
        return Range(start, end)
    }

    // -----------------------------------------------------------------------
    // Plages
    // -----------------------------------------------------------------------

    fun intersect(a: Range, b: Range): Range? {
        val start = maxOf(a.start, b.start)
        val end = minOf(a.end, b.end)
        return if (start <= end) Range(start, end) else null
    }

    /**
     * Trie, filtre et fusionne les plages. Deux plages **adjacentes** sont fusionnées
     * (`r.start <= last.end + 1`) : sans cela, deux séances contiguës produiraient deux
     * plages distinctes là où le client d'origine n'en produit qu'une.
     */
    fun normalizeRanges(ranges: List<Range>): List<Range> {
        val sorted = ranges
            .filter { it.start >= 1 && it.end <= current.verses.size && it.start <= it.end }
            .sortedBy { it.start }
        val out = ArrayList<Range>(sorted.size)
        for (r in sorted) {
            val last = out.lastOrNull()
            if (last != null && r.start <= last.end + 1) {
                out[out.size - 1] = last.copy(end = maxOf(last.end, r.end))
            } else {
                out.add(r)
            }
        }
        return out
    }

    fun expand(ranges: List<Range>): List<Int> {
        val ids = ArrayList<Int>()
        for (r in normalizeRanges(ranges)) for (id in r.start..r.end) ids.add(id)
        return ids
    }

    /** Volume d'un ensemble de versets, mesuré en lettres arabes. */
    fun volume(ids: Collection<Int>): Int {
        var sum = 0
        for (id in ids) if (id in 1..current.weights.size) sum += current.weights[id - 1]
        return sum
    }

    fun volume(range: Range): Int = volume((range.start..range.end).toList())

    /** Libellé d'une plage, identique à celui du client d'origine. */
    fun reference(range: Range): String {
        val a = verseAt(range.start)
        val b = verseAt(range.end)
        return if (a.surah == b.surah) {
            "${surahAt(range.start).name} ${a.ayah}–${b.ayah}"
        } else {
            "${surahAt(range.start).name} ${a.ayah} → ${surahAt(range.end).name} ${b.ayah}"
        }
    }

    /** Tous les identifiants d'une plage, sans passer par la normalisation. */
    fun idsOf(ranges: List<Range>): List<Int> =
        ranges.flatMap { r -> (r.start..r.end).toList() }

    /** Vrai si tous les identifiants de [range] appartiennent à [set]. */
    fun full(set: Set<Int>, range: Range): Boolean {
        for (id in range.start..range.end) if (id !in set) return false
        return true
    }

    /** Division (juz’, hizb, nisf, rub‘) contenant un verset. */
    fun divisionContaining(divisions: List<Division>, id: Int): Division? =
        divisions.firstOrNull { id >= it.start && id <= it.end }
}
