package com.msoumaya.deepseekandroid.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Arithmétique de dates calendaires.
 *
 * Les dates sont des chaînes `AAAA-MM-JJ` en calendrier **local**. Ces tests vérifient
 * notamment le passage à l'heure d'été : un décalage d'une heure ne doit jamais faire
 * basculer une date d'un jour.
 */
class DatesTest {

    @Test
    fun `dateKey et parse sont inverses`() {
        assertEquals("2026-10-04", Dates.dateKey(Dates.parse("2026-10-04")))
        assertEquals("2026-01-01", Dates.dateKey(Dates.parse("2026-01-01")))
    }

    @Test
    fun `addDays franchit les fins de mois et d'annee`() {
        assertEquals("2026-10-05", Dates.addDays("2026-10-04", 1))
        assertEquals("2026-10-03", Dates.addDays("2026-10-04", -1))
        assertEquals("2026-11-01", Dates.addDays("2026-10-31", 1))
        assertEquals("2027-01-01", Dates.addDays("2026-12-31", 1))
        assertEquals("2025-12-31", Dates.addDays("2026-01-01", -1))
        assertEquals("2026-10-04", Dates.addDays("2026-10-04", 0))
    }

    @Test
    fun `addDays survit au changement d'heure`() {
        // Passage à l'heure d'été en Europe : dernier dimanche de mars 2026.
        assertEquals("2026-03-29", Dates.addDays("2026-03-28", 1))
        assertEquals("2026-03-30", Dates.addDays("2026-03-29", 1))
        // Passage à l'heure d'hiver : dernier dimanche d'octobre 2026.
        assertEquals("2026-10-25", Dates.addDays("2026-10-24", 1))
        assertEquals("2026-10-26", Dates.addDays("2026-10-25", 1))
    }

    @Test
    fun `addDays gere les annees bissextiles`() {
        assertEquals("2028-02-29", Dates.addDays("2028-02-28", 1))
        assertEquals("2028-03-01", Dates.addDays("2028-02-29", 1))
        assertEquals("2026-03-01", Dates.addDays("2026-02-28", 1))
    }

    @Test
    fun `dayOf suit la convention de JavaScript, dimanche vaut zero`() {
        // 4 octobre 2026 est un dimanche.
        assertEquals(0, Dates.dayOf("2026-10-04"))
        assertEquals(1, Dates.dayOf("2026-10-05"))
        assertEquals(2, Dates.dayOf("2026-10-06"))
        assertEquals(5, Dates.dayOf("2026-10-09"))
        assertEquals(6, Dates.dayOf("2026-10-10"))
    }

    @Test
    fun `age compte les jours entiers`() {
        assertEquals(7, Dates.age("2026-10-04", "2026-10-11"))
        assertEquals(0, Dates.age("2026-10-04", "2026-10-04"))
        assertEquals(-3, Dates.age("2026-10-04", "2026-10-01"))
        assertEquals(365, Dates.age("2026-01-01", "2027-01-01"))
    }

    @Test
    fun `parseIsoMillis tolere une valeur illisible`() {
        assertEquals(0L, Dates.parseIsoMillis("pas une date"))
        assertEquals(0L, Dates.parseIsoMillis(""))
        val millis = Dates.parseIsoMillis("2026-10-04T12:00:00Z")
        assertEquals("2026-10-04T12:00:00Z", Dates.iso(millis))
    }
}
