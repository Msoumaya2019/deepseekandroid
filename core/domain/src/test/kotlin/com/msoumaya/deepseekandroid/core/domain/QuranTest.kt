package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.Range
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Référentiel coranique et arithmétique des plages.
 *
 * Ces tests portent sur des valeurs **mesurées dans les données réelles**, pas sur des
 * constantes recopiées : c'est ce qui garantit qu'un identifiant de verset calculé ici
 * désigne le même verset que dans le client React Native.
 */
class QuranTest {

    @Before
    fun setUp() = QuranFixture.install()

    @Test
    fun `le referentiel porte les volumes attendus`() {
        assertEquals(6236, Quran.verses.size)
        assertEquals(114, Quran.surahs.size)
        assertEquals(30, Quran.juzs.size)
        assertEquals(240, Quran.quarters.size)
        assertEquals(604, Quran.pages.size)
        // Un hizb = 4 rub‘, un nisf = 2 rub‘, recalculés comme dans le client d'origine.
        assertEquals(60, Quran.hizbs.size)
        assertEquals(120, Quran.halves.size)
    }

    @Test
    fun `les identifiants globaux sont contigus`() {
        assertEquals(1, Quran.verseId(1, 1))
        assertEquals(7, Quran.verseId(1, 7))
        assertEquals(8, Quran.verseId(2, 1))
        assertEquals(6231, Quran.verseId(114, 1))
        assertEquals(6236, Quran.verseId(114, 6))
        // Un verset hors des bornes de sa sourate n'existe pas.
        assertNull(Quran.verseId(1, 8))
        assertNull(Quran.verseId(114, 7))
        assertNull(Quran.verseId(115, 1))
    }

    @Test
    fun `verseAt et surahAt se correspondent`() {
        val first = Quran.verseAt(1)
        assertEquals(1, first.surah)
        assertEquals(1, first.ayah)
        assertEquals(1, Quran.surahAt(1).number)

        val last = Quran.verseAt(6236)
        assertEquals(114, last.surah)
        assertEquals(6, last.ayah)
        assertEquals("An Nâs", Quran.surahAt(6236).name)
    }

    @Test
    fun `pageOf et pageRange decrivent les memes bornes que pages json`() {
        assertEquals(1, Quran.pageOf(1))
        assertEquals(1, Quran.pageOf(7))
        assertEquals(2, Quran.pageOf(8))
        assertEquals(200, Quran.pageOf(Quran.verseId(9, 80)!!))
        assertEquals(604, Quran.pageOf(6236))

        assertEquals(Range(1, 7), Quran.pageRange(1))
        assertEquals(Range(8, 12), Quran.pageRange(2))
        // Dernière page : de la première sourate 112 au dernier verset du Coran.
        assertEquals(Range(6222, 6236), Quran.pageRange(604))
    }

    @Test
    fun `les divisions couvrent tout le Coran sans trou`() {
        assertEquals(1, Quran.juzs.first().start)
        assertEquals(6236, Quran.juzs.last().end)
        assertEquals(1, Quran.hizbs.first().start)
        assertEquals(6236, Quran.hizbs.last().end)
        assertEquals(1, Quran.quarters.first().start)
        assertEquals(6236, Quran.quarters.last().end)
        assertEquals(6236, Quran.quarters.last().end)

        // Contiguïté : chaque division commence juste après la précédente.
        for (i in 1 until Quran.juzs.size) {
            assertEquals(
                Quran.juzs[i - 1].end + 1,
                Quran.juzs[i].start,
                "juz ${i + 1} n'est pas contigu au précédent",
            )
        }
    }

    @Test
    fun `normalizeRanges fusionne les plages adjacentes`() {
        // 1..3 et 4..7 se touchent : le client d'origine n'en fait qu'une plage.
        assertEquals(
            listOf(Range(1, 7)),
            Quran.normalizeRanges(listOf(Range(1, 3), Range(4, 7))),
        )
        // 1..3 et 5..7 laissent le verset 4 hors plage : pas de fusion.
        assertEquals(
            listOf(Range(1, 3), Range(5, 7)),
            Quran.normalizeRanges(listOf(Range(1, 3), Range(5, 7))),
        )
        // Ordre indifférent, chevauchement absorbé : 1..5 ∪ 4..7 = 1..7, puis 8..10 colle.
        assertEquals(
            listOf(Range(1, 10)),
            Quran.normalizeRanges(listOf(Range(8, 10), Range(1, 5), Range(4, 7))),
        )
        // Un verset manquant empêche la fusion : 1..6 et 8..10 laissent le verset 7 dehors.
        assertEquals(
            listOf(Range(1, 6), Range(8, 10)),
            Quran.normalizeRanges(listOf(Range(1, 5), Range(4, 6), Range(8, 10))),
        )
    }

    @Test
    fun `normalizeRanges ecarte les plages invalides ou hors bornes`() {
        assertEquals(
            listOf(Range(2, 5)),
            Quran.normalizeRanges(listOf(Range(0, 5), Range(2, 5), Range(6237, 6300), Range(9, 3))),
        )
        assertEquals(emptyList(), Quran.normalizeRanges(emptyList()))
    }

    @Test
    fun `expand renvoie les identifiants dans l'ordre croissant`() {
        assertEquals(listOf(1, 2, 3, 4, 5), Quran.expand(listOf(Range(3, 5), Range(1, 2))))
        assertEquals(listOf(1, 2, 3), Quran.expand(listOf(Range(1, 3), Range(2, 3))))
    }

    @Test
    fun `volume compte les lettres arabes sans diacritiques`() {
        val page1 = Quran.volume(Quran.pageRange(1))
        assertTrue(page1 > 0, "le volume d'une page doit être strictement positif")
        // Le volume d'une plage est la somme des poids de ses versets, sans double compte.
        assertEquals(
            (1..7).sumOf { Quran.weights[it - 1] },
            page1,
        )
        // Une plage disjointe vaut la somme de ses morceaux.
        val a = Quran.volume(Range(1, 3))
        val b = Quran.volume(Range(4, 7))
        assertEquals(page1, a + b)
    }

    @Test
    fun `reference nomme une plage comme le client d'origine`() {
        assertEquals("Al Fâtiha 1–1", Quran.reference(Range(1, 1)))
        assertEquals("Al Fâtiha 1–7", Quran.reference(Range(1, 7)))
        assertEquals("Al Fâtiha 7 → Al Baqarah 1", Quran.reference(Range(7, 8)))
    }

    @Test
    fun `intersect et full se repondent`() {
        assertEquals(Range(5, 8), Quran.intersect(Range(1, 8), Range(5, 20)))
        assertNull(Quran.intersect(Range(1, 3), Range(5, 8)))

        val set = (1..10).toSet()
        assertTrue(Quran.full(set, Range(1, 10)))
        assertTrue(!Quran.full(set, Range(1, 11)))
    }

    @Test
    fun `divisionContaining trouve la division d'un verset`() {
        assertEquals(1, Quran.divisionContaining(Quran.juzs, 1)?.number)
        assertEquals(30, Quran.divisionContaining(Quran.juzs, 6236)?.number)
        assertEquals(60, Quran.divisionContaining(Quran.hizbs, 6236)?.number)
        assertEquals(1, Quran.divisionContaining(Quran.halves, 1)?.number)
    }

    @Test
    fun `goalFromPreset decrit exactement les bornes de l'objectif`() {
        val amma = Program.goalFromPreset(com.msoumaya.deepseekandroid.core.model.GoalPreset.AMMA)
        assertEquals(listOf(Range(5673, 6236)), amma.ranges)

        val lastTen = Program.goalFromPreset(com.msoumaya.deepseekandroid.core.model.GoalPreset.LAST_TEN)
        assertEquals(listOf(Range(6189, 6236)), lastTen.ranges)

        val all = Program.goalFromPreset(com.msoumaya.deepseekandroid.core.model.GoalPreset.ALL)
        assertEquals(listOf(Range(1, 6236)), all.ranges)
    }
}
