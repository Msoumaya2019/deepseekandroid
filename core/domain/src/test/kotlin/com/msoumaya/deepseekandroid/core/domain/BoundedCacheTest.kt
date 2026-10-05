package com.msoumaya.deepseekandroid.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Éprouve la règle de conservation du cache du chargeur de pages.
 *
 * Ce qui est éprouvé ici n'est pas « une `LinkedHashMap` fonctionne » mais **ce que l'original
 * décide** : quelle entrée sort quand la borne est franchie, et le fait qu'une lecture protège
 * l'entrée lue. Les deux se voient sur trois entrées, et aucune des deux ne se verrait sur un
 * appareil avant que la mémoire ne monte.
 */
class BoundedCacheTest {

    @Test
    fun `un cache neuf ne retient rien`() {
        val cache = BoundedCache<Int, String>(limit = 5)
        assertEquals(0, cache.size)
        assertNull(cache.get(1))
        assertEquals(emptyList(), cache.keys())
    }

    @Test
    fun `une lecture retrouve ce qui a ete pose`() {
        val cache = BoundedCache<Int, String>(limit = 5)
        cache.put(7, "sept")
        assertEquals("sept", cache.get(7))
    }

    @Test
    fun `la borne evince la plus ancienne, et elle seule`() {
        val cache = BoundedCache<Int, String>(limit = 5)
        for (page in 1..6) cache.put(page, "page $page")
        assertEquals(5, cache.size)
        assertEquals(listOf(2, 3, 4, 5, 6), cache.keys())
        assertNull(cache.get(1))
        assertEquals("page 6", cache.get(6))
    }

    @Test
    fun `une lecture protege de l'eviction suivante`() {
        val cache = BoundedCache<Int, String>(limit = 5)
        for (page in 1..5) cache.put(page, "page $page")
        // On relit la plus ancienne, puis on en pose une sixième : c'est exactement le geste
        // « je reviens sur mes pas » que la récence existe pour rendre gratuit.
        assertEquals("page 1", cache.get(1))
        // `keys()` rend de la plus ancienne à la plus récente : la page relue passe donc en
        // **fin** de liste, et c'est la page 2 — la plus ancienne qu'on n'a pas relue — qui sort.
        assertEquals(listOf(2, 3, 4, 5, 1), cache.keys())
        cache.put(6, "page 6")
        assertEquals(listOf(3, 4, 5, 1, 6), cache.keys())
        assertEquals("page 1", cache.get(1))
        assertNull(cache.get(2))
    }

    @Test
    fun `une seconde lecture de la meme entree ne la deplace pas davantage`() {
        val cache = BoundedCache<Int, String>(limit = 5)
        for (page in 1..5) cache.put(page, "page $page")
        // Deux lectures de la même page : la seconde la retrouve déjà la plus récente, donc
        // l'ordre est celui d'une seule lecture. Sans cela, relire deux fois une page la
        // placerait devant une page relue une fois, et l'ordre de récence ne voudrait plus rien
        // dire.
        cache.get(1)
        cache.get(1)
        assertEquals(listOf(2, 3, 4, 5, 1), cache.keys())
        cache.put(6, "page 6")
        assertEquals(listOf(3, 4, 5, 1, 6), cache.keys())
    }

    @Test
    fun `reescrire une cle ne consomme pas de place`() {
        val cache = BoundedCache<Int, String>(limit = 5)
        cache.put(1, "premier")
        cache.put(2, "deuxieme")
        cache.put(1, "corrige")
        assertEquals(2, cache.size)
        assertEquals(listOf(2, 1), cache.keys())
        assertEquals("corrige", cache.get(1))
    }

    @Test
    fun `une borne nulle ou negative est refusee`() {
        assertFailsWith<IllegalArgumentException> { BoundedCache<Int, String>(limit = 0) }
        assertFailsWith<IllegalArgumentException> { BoundedCache<Int, String>(limit = -1) }
    }
}
