package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.*
import com.msoumaya.deepseekandroid.core.model.AdminDifficultyStamp
import com.msoumaya.deepseekandroid.core.model.DifficultyMarker
import com.msoumaya.deepseekandroid.core.model.DifficultyStamp
import com.msoumaya.deepseekandroid.core.model.Mastery
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.ReviewSettings
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Révisions et consolidations.
 *
 * Le point le plus sensible est l'ancrage des consolidations : J+1, J+3 et J+7 sont comptés
 * depuis la **date d'apprentissage**, jamais depuis la date de validation. Une étape faite en
 * avance ou en retard ne déplace pas les suivantes.
 */
class ReviewTest {

    @Before
    fun setUp() = QuranFixture.install()

    @Test
    fun `les trois echeances de consolidation sont J plus un, trois et sept`() {
        assertEquals(listOf(1, 3, 7), Review.consolidationOffsets)
    }

    @Test
    fun `les echeances de consolidation sont ancrees sur la date d'apprentissage`() {
        val state = QuranFixture.onboardedState().copy(
            memorizedAt = mapOf("1" to "2026-10-05"),
        )
        val consolidation = Review.consolidationFor(state, 1)
        assertNotNull(consolidation)
        assertEquals("2026-10-05", consolidation.learnedAt)
        assertEquals(
            mapOf(1 to "2026-10-06", 3 to "2026-10-08", 7 to "2026-10-12"),
            consolidation.scheduledDates,
        )
        assertTrue(consolidation.completed.isEmpty(), "aucune étape n'est inventée comme faite")
    }

    @Test
    fun `consolidationFor est stable dans le temps`() {
        val state = QuranFixture.onboardedState().copy(memorizedAt = mapOf("1" to "2026-10-05"))
        val first = Review.consolidationFor(state, 1)
        val later = Review.consolidationFor(state.copy(updatedAt = "2026-11-01T00:00:00Z"), 1)
        assertEquals(first?.scheduledDates, later?.scheduledDates)
    }

    @Test
    fun `consolidationFor ne renvoie rien pour un verset jamais appris`() {
        assertNull(Review.consolidationFor(QuranFixture.onboardedState(), 1))
    }

    @Test
    fun `les revisions sont actives par defaut avec un cycle de sept jours`() {
        val state = QuranFixture.onboardedState()
        assertTrue(Review.reviewsEnabled(state))
        assertEquals(7, Review.reviewCycleDays(state))
    }

    @Test
    fun `desactiver puis reactiver les revisions est reversible`() {
        var state = QuranFixture.onboardedState()
        state = Review.setReviewsEnabled(state, false, at = "2026-10-05")
        assertTrue(!Review.reviewsEnabled(state))
        state = Review.setReviewsEnabled(state, true, at = "2026-10-05")
        assertTrue(Review.reviewsEnabled(state))
        assertEquals(7, Review.reviewCycleDays(state))
    }

    @Test
    fun `le cycle de revision accepte les durees 7, 14, 21 et 30 jours`() {
        for (days in listOf(7, 14, 21, 30)) {
            val state = Review.setReviewCycle(QuranFixture.onboardedState(), days, at = "2026-10-05")
            assertEquals(days, Review.reviewCycleDays(state))
        }
        // Redemander la durée déjà en place ne change rien : aucun cycle n'est recréé.
        val unchanged = Review.setReviewCycle(QuranFixture.onboardedState(), 7, at = "2026-10-05")
        assertNull(unchanged.reviewCycle)
        // Changer de durée ouvre un cycle de la longueur demandée.
        for (days in listOf(14, 21, 30)) {
            val changed = Review.setReviewCycle(QuranFixture.onboardedState(), days, at = "2026-10-05")
            assertEquals(days, changed.reviewCycle?.lengthDays)
        }
    }

    @Test
    fun `changer de cycle archive le precedent sans le perdre`() {
        var state = Review.setReviewCycle(QuranFixture.onboardedState(), 14, at = "2026-10-05")
        assertEquals(14, state.reviewCycle?.lengthDays)

        state = Review.setReviewCycle(state, 21, at = "2026-10-05")
        assertEquals(1, state.effectiveReviewCycleHistory.size)
        assertEquals(14, state.effectiveReviewCycleHistory.first().lengthDays)
        assertEquals(21, state.reviewCycle?.lengthDays)
    }

    // --- Versets difficiles -------------------------------------------------

    @Test
    fun `un verset difficile garde son etat jusqu'a ce que l'utilisateur le retire`() {
        var state = QuranFixture.onboardedState()
        state = Review.toggleDifficulty(state, 100, at = "2026-10-05")

        assertEquals(DifficultyStamp("2026-10-05"), state.effectiveDifficultyMarkers["100"]?.user)
        assertEquals("2026-10-05", state.effectiveReviewPriorityDue["100"])
        assertEquals(1, state.effectiveDifficultyHistory.size)
        assertEquals("marked", state.effectiveDifficultyHistory.last().action)

        // Un second appel le retire explicitement.
        state = Review.toggleDifficulty(state, 100, at = "2026-10-06")
        assertNull(state.effectiveDifficultyMarkers["100"])
        assertNull(state.effectiveReviewPriorityDue["100"])
        assertEquals("resolved", state.effectiveDifficultyHistory.last().action)
    }

    @Test
    fun `le marqueur du professeur survit au retrait par l'utilisateur`() {
        val state = QuranFixture.onboardedState().copy(
            difficultyMarkers = mapOf(
                "100" to DifficultyMarker(
                    user = DifficultyStamp("2026-10-05"),
                    admin = AdminDifficultyStamp("2026-10-01"),
                ),
            ),
        )
        val resolved = Review.toggleDifficulty(state, 100, at = "2026-10-06")
        val marker = resolved.effectiveDifficultyMarkers["100"]
        assertNotNull(marker, "le marqueur doit subsister tant que le professeur l'a posé")
        assertNull(marker.user)
        assertNotNull(marker.admin)
    }

    @Test
    fun `toggleDifficulty ignore un identifiant hors bornes`() {
        val state = QuranFixture.onboardedState()
        assertEquals(state.knowledge, Review.toggleDifficulty(state, 0).knowledge)
        assertTrue(Review.toggleDifficulty(state, 99999).effectiveDifficultyMarkers.isEmpty())
    }

    // --- Répartition du corpus ----------------------------------------------

    @Test
    fun `partitionReviewCorpus ne perd ni ne duplique aucun verset`() {
        val corpus = (1..70).toList()
        for (length in listOf(7, 14, 21, 30)) {
            val parts = Review.partitionReviewCorpus(corpus, length)
            assertEquals(length, parts.size)
            assertEquals(corpus, parts.flatten().sorted(), "la concaténation doit rendre exactement le corpus")
            assertEquals(corpus.size, parts.flatten().distinct().size, "aucun doublon")
        }
    }

    @Test
    fun `partitionReviewCorpus decoupe un corpus de hizb entiers en unites entieres`() {
        // Deux hizb entiers répartis sur 7 jours : le découpage doit tomber sur des rub‘.
        val corpus = Quran.idsOf(listOf(Quran.hizbs[0].range, Quran.hizbs[1].range))
        val parts = Review.partitionReviewCorpus(corpus, 7)
        assertEquals(7, parts.size)
        assertEquals(corpus.sorted(), parts.flatten().sorted())
    }

    @Test
    fun `reviewWeight rapporte un verset au volume de sa page`() {
        val page1 = Quran.pageRange(1)
        val weights = Quran.expand(listOf(page1)).map { Review.reviewWeight(it) }
        assertTrue(weights.all { it > 0.0 && it <= 1.0 })
        assertEquals(1.0, weights.sum(), 1e-9, "le volume d'une page complète vaut 1")
    }
}
