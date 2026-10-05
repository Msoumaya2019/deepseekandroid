package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.*
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Marques du lecteur : difficulté et signets.
 *
 * Les deux ensembles alimentent le **même** surlignage de page (`MushafPageView`), mais ils
 * n'obéissent pas aux mêmes règles, et c'est précisément là qu'une erreur serait invisible :
 *
 *  - la difficulté a **deux origines**, l'élève et le professeur. Un verset marqué par le
 *    professeur compte comme difficile même si l'élève ne l'a jamais signalé — c'est tout
 *    l'intérêt du canal enseignant, et l'oublier ferait disparaître de l'écran un verset que
 *    le professeur a justement désigné ;
 *  - le signet est **supprimé logiquement** : il garde un `deletedAt` pour la synchronisation.
 *    Lire la carte brute ferait donc revivre un signet supprimé sur la page.
 *
 * Ces deux règles étaient recopiées à plusieurs endroits. Ce test les tient sur les fonctions
 * qui les nomment désormais, et non sur l'un de leurs appelants.
 */
class ReaderMarksTest {

    @Before
    fun setUp() = QuranFixture.install()

    // -----------------------------------------------------------------------
    // Difficulté
    // -----------------------------------------------------------------------

    @Test
    fun `la regle de difficulte couvre les deux origines et seulement elles`() {
        assertFalse(Review.isDifficult(null), "l'absence de marqueur n'est pas une difficulté")
        assertFalse(Review.isDifficult(DifficultyMarker()), "un marqueur sans origine n'en est pas une")
        assertTrue(Review.isDifficult(DifficultyMarker(user = DifficultyStamp("2026-10-05"))))
        assertTrue(Review.isDifficult(DifficultyMarker(admin = AdminDifficultyStamp("2026-10-05"))))
        assertTrue(
            Review.isDifficult(
                DifficultyMarker(
                    user = DifficultyStamp("2026-10-05"),
                    admin = AdminDifficultyStamp("2026-10-05", "à revoir"),
                ),
            ),
        )
    }

    @Test
    fun `un marqueur utilisateur suffit`() {
        val state = Review.toggleDifficulty(QuranFixture.onboardedState(), 8, at = "2026-10-05")
        assertEquals(setOf(8), Review.difficultIds(state))
    }

    @Test
    fun `un marqueur du professeur suffit sans marqueur utilisateur`() {
        val state = QuranFixture.onboardedState().copy(
            difficultyMarkers = mapOf(
                "8" to DifficultyMarker(admin = AdminDifficultyStamp("2026-10-05", "à revoir")),
            ),
        )
        assertEquals(setOf(8), Review.difficultIds(state))
    }

    @Test
    fun `un marqueur resolu ne colore plus la page`() {
        // `toggleDifficulty` retire la clé quand les deux origines sont nulles ; on construit
        // ici le cas d'une clé qui subsisterait vide, tel qu'un état synchronisé peut en porter.
        val state = QuranFixture.onboardedState().copy(
            difficultyMarkers = mapOf(
                "8" to DifficultyMarker(),
                "9" to DifficultyMarker(user = DifficultyStamp("2026-10-05")),
            ),
        )
        assertEquals(setOf(9), Review.difficultIds(state))
    }

    @Test
    fun `un etat sans marqueur ne rend aucun verset difficile`() {
        assertTrue(Review.difficultIds(QuranFixture.onboardedState()).isEmpty())
        assertTrue(
            Review.difficultIds(QuranFixture.onboardedState().copy(difficultyMarkers = emptyMap())).isEmpty(),
        )
    }

    @Test
    fun `les marques hors corpus sont conservees telles quelles`() {
        // Aucun filtre : ni sur le corpus appris, ni sur les bornes du Coran. Une marque hors
        // corpus reste sans effet à l'affichage, mais la retirer ici ferait disparaître une
        // donnée synchronisée. Même convention que `Program.memorizedIds` pour les clés non
        // numériques : elles sont écartées, faute de pouvoir s'écrire dans un `Set<Int>`.
        val state = QuranFixture.onboardedState().copy(
            difficultyMarkers = mapOf(
                "9999" to DifficultyMarker(user = DifficultyStamp("2026-10-05")),
                "pas-un-nombre" to DifficultyMarker(user = DifficultyStamp("2026-10-05")),
            ),
        )
        assertEquals(setOf(9999), Review.difficultIds(state))
    }

    @Test
    fun `le plan de reprise lit le meme ensemble que la fonction`() {
        var state = QuranFixture.onboardedState()
        state = Program.markKnowledge(state, Range(8, 8), Mastery.PERFECT)
        state = Review.toggleDifficulty(state, 8, at = "2026-10-05")

        assertTrue(8 in Review.difficultIds(state))
        val rework = Review.reviewPlan(state, at = "2026-10-05").rework
        assertTrue(
            rework.any { 8 in it.start..it.end },
            "le verset marqué doit figurer dans les reprises : les deux lisent la même règle",
        )
    }

    // -----------------------------------------------------------------------
    // Signets
    // -----------------------------------------------------------------------

    @Test
    fun `un signet enregistre colore la page`() {
        val state = Bookmarks.saveBookmark(QuranFixture.onboardedState(), 8, at = "2026-10-05T10:00:00Z")
        assertEquals(setOf(8), Bookmarks.bookmarkedIds(state))
    }

    @Test
    fun `un signet supprime ne colore pas la page`() {
        var state = Bookmarks.saveBookmark(QuranFixture.onboardedState(), 8, at = "2026-10-05T10:00:00Z")
        state = Bookmarks.saveBookmark(state, 9, at = "2026-10-05T10:00:00Z")
        state = Bookmarks.deleteBookmark(state, 8, at = "2026-10-06T10:00:00Z")

        assertEquals(setOf(9), Bookmarks.bookmarkedIds(state))
        assertNotNull(
            state.effectiveBookmarks.getValue("8").deletedAt,
            "le marqueur de suppression subsiste, pour que la synchronisation ne le réintroduise pas",
        )
    }

    @Test
    fun `aucun signet ne rend aucun verset marque`() {
        assertTrue(Bookmarks.bookmarkedIds(QuranFixture.onboardedState()).isEmpty())
    }

    @Test
    fun `les deux ensembles sont independants`() {
        // Le lecteur choisit la couleur par l'appartenance à l'un OU l'autre ; les mélanger
        // ferait colorer en vert un verset difficile dès qu'un signet existe ailleurs.
        var state = Bookmarks.saveBookmark(QuranFixture.onboardedState(), 8, at = "2026-10-05T10:00:00Z")
        state = Review.toggleDifficulty(state, 9, at = "2026-10-05")

        assertEquals(setOf(8), Bookmarks.bookmarkedIds(state))
        assertEquals(setOf(9), Review.difficultIds(state))
    }
}
