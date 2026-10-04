package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.*
import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.AppTheme
import com.msoumaya.deepseekandroid.core.model.Mastery
import com.msoumaya.deepseekandroid.core.model.SessionStatus
import com.msoumaya.deepseekandroid.core.model.StudyMode
import com.msoumaya.deepseekandroid.core.model.StudyProgress
import com.msoumaya.deepseekandroid.core.model.StudyStatus
import com.msoumaya.deepseekandroid.core.model.StudyValidation
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fusion hors ligne à trois voies.
 *
 * Ces tests portent sur les décisions qui font perdre du travail quand elles sont fausses :
 * un champ local inchangé doit hériter du serveur, un champ local modifié doit gagner, et
 * un journal ne doit jamais être raccourci.
 */
class OfflineMergeTest {

    @Before
    fun setUp() = QuranFixture.install()

    private fun base(): AppState = QuranFixture.onboardedState().copy(
        userId = "user-1",
        updatedAt = "2026-10-04T10:00:00Z",
    )

    @Test
    fun `un champ local inchange herite de la valeur du serveur`() {
        val base = base().copy(theme = AppTheme.WHITE)
        val local = base.copy(updatedAt = "2026-10-04T11:00:00Z")
        val remote = base.copy(theme = AppTheme.CLASSIC, updatedAt = "2026-10-04T12:00:00Z")

        val merged = OfflineMerge.mergeOfflineState(base, local, remote)
        assertEquals(AppTheme.CLASSIC, merged.theme, "le thème n'a pas été modifié localement")
    }

    @Test
    fun `un champ local modifie l'emporte sur le serveur`() {
        val base = base().copy(theme = AppTheme.WHITE)
        val local = base.copy(theme = AppTheme.NIGHT, updatedAt = "2026-10-04T11:00:00Z")
        val remote = base.copy(theme = AppTheme.CLASSIC, updatedAt = "2026-10-04T12:00:00Z")

        val merged = OfflineMerge.mergeOfflineState(base, local, remote)
        assertEquals(AppTheme.NIGHT, merged.theme, "une édition locale explicite gagne le conflit")
    }

    @Test
    fun `les connaissances des deux cotes sont conservees`() {
        val base = base()
        val local = base.copy(knowledge = mapOf("1" to Mastery.PERFECT))
        val remote = base.copy(knowledge = mapOf("2" to Mastery.PERFECT))

        val merged = OfflineMerge.mergeOfflineState(base, local, remote)
        assertEquals(Mastery.PERFECT, merged.knowledge["1"])
        assertEquals(Mastery.PERFECT, merged.knowledge["2"])
    }

    @Test
    fun `les listes de pages lues sont reunies et triees sans doublon`() {
        val base = base().copy(readPages = listOf(1, 2))
        val local = base.copy(readPages = listOf(1, 2, 3))
        val remote = base.copy(readPages = listOf(2, 4, 5))

        val merged = OfflineMerge.mergeOfflineState(base, local, remote)
        assertEquals(listOf(1, 2, 3, 4, 5), merged.readPages)
    }

    @Test
    fun `les journaux de validations ne sont jamais raccourcis`() {
        val first = StudyValidation(start = 1, end = 3, date = "2026-10-04")
        val second = StudyValidation(start = 4, end = 6, date = "2026-10-05")

        fun progress(validations: List<StudyValidation>) = StudyProgress(
            id = "s1",
            mode = StudyMode.LEARNING,
            start = 1,
            end = 6,
            through = validations.maxOfOrNull { it.end } ?: 0,
            page = 1,
            source = "coranTest",
            updatedAt = "2026-10-05T10:00:00Z",
            status = StudyStatus.PARTIAL,
            validations = validations,
        )

        val base = base().copy(studyProgress = mapOf("learning:s1" to progress(emptyList())))
        val local = base.copy(studyProgress = mapOf("learning:s1" to progress(listOf(first))))
        val remote = base.copy(studyProgress = mapOf("learning:s1" to progress(listOf(second))))

        val merged = OfflineMerge.mergeOfflineState(base, local, remote)
        val validations = merged.effectiveStudyProgress.getValue("learning:s1").validations
        assertEquals(2, validations.size, "aucune validation ne doit être perdue")
        assertTrue(validations.any { it.date == "2026-10-04" })
        assertTrue(validations.any { it.date == "2026-10-05" })
    }

    @Test
    fun `le statut d'un suivi se recalcule a partir de through et end`() {
        fun entry(end: Int, status: StudyStatus, updatedAt: String) = StudyProgress(
            id = "s1", mode = StudyMode.LEARNING, start = 1, end = end, through = 3, page = 1,
            source = "coranTest", updatedAt = updatedAt, status = status,
        )

        // Les trois états diffèrent (par `end` et `updatedAt`) : la fusion descend vraiment
        // dans l'objet, et le statut y est recalculé plutôt que recopié.
        val base = base().copy(studyProgress = mapOf("learning:s1" to entry(6, StudyStatus.COMPLETED, "2026-10-04T10:00:00Z")))
        val local = base.copy(
            updatedAt = "2026-10-04T11:00:00Z",
            studyProgress = mapOf("learning:s1" to entry(10, StudyStatus.COMPLETED, "2026-10-04T11:00:00Z")),
        )
        val remote = base.copy(
            updatedAt = "2026-10-04T12:00:00Z",
            studyProgress = mapOf("learning:s1" to entry(6, StudyStatus.COMPLETED, "2026-10-04T12:00:00Z")),
        )

        val merged = OfflineMerge.mergeOfflineState(base, local, remote)
        val result = merged.effectiveStudyProgress.getValue("learning:s1")
        assertEquals(10, result.end, "la fin reculée localement est conservée")
        assertEquals(StudyStatus.PARTIAL, result.status, "le statut ne peut plus rester « terminé »")
    }

    @Test
    fun `updatedAt depasse strictement les deux horodatages lus`() {
        val base = base()
        val local = base.copy(updatedAt = "2026-10-04T11:00:00Z")
        val remote = base.copy(updatedAt = "2026-10-04T12:00:00Z")

        val merged = OfflineMerge.mergeOfflineState(base, local, remote)
        assertTrue(
            Dates.parseIsoMillis(merged.updatedAt) > Dates.parseIsoMillis(local.updatedAt),
            "l'état fusionné doit être plus récent que le local",
        )
        assertTrue(Dates.parseIsoMillis(merged.updatedAt) > Dates.parseIsoMillis(remote.updatedAt))
    }

    @Test
    fun `une remise a zero volontaire n'est pas annulee par le serveur`() {
        val base = base().copy(onboardingDone = true)
        val local = base.copy(onboardingDone = false, updatedAt = "2026-10-04T11:00:00Z")
        val remote = base.copy(updatedAt = "2026-10-04T12:00:00Z")

        val merged = OfflineMerge.mergeOfflineState(base, local, remote)
        assertEquals(local, merged, "l'état local est rendu tel quel")
    }

    @Test
    fun `deux comptes differents ne sont jamais fusionnes`() {
        val base = base()
        val local = base.copy(theme = AppTheme.NIGHT)
        val remote = base.copy(userId = "user-2", theme = AppTheme.CLASSIC)

        assertEquals(local, OfflineMerge.mergeOfflineState(base, local, remote))
    }

    @Test
    fun `sans base ou sans serveur, l'etat local est rendu inchange`() {
        val local = base().copy(theme = AppTheme.NIGHT)
        assertEquals(local, OfflineMerge.mergeOfflineState(null, local, base()))
        assertEquals(local, OfflineMerge.mergeOfflineState(base(), local, null))
    }

    @Test
    fun `une seance terminee cote serveur survit a une suppression locale`() {
        // Les deux côtés ont divergé : le local a retiré la séance, le serveur l'a terminée.
        val base = base().copy(sessions = listOf(QuranFixture.session("s1", "2026-10-05", 1, 3)))
        val local = base.copy(sessions = emptyList(), updatedAt = "2026-10-04T11:00:00Z")
        val remote = base.copy(
            updatedAt = "2026-10-04T12:00:00Z",
            sessions = listOf(QuranFixture.session("s1", "2026-10-05", 1, 3, SessionStatus.DONE)),
        )

        val merged = OfflineMerge.mergeOfflineState(base, local, remote)
        val session = merged.sessions.singleOrNull { it.id == "s1" }
        assertNotNull(session, "une séance terminée ne doit pas disparaître")
        assertEquals(SessionStatus.DONE, session.status)
    }

    @Test
    fun `la fusion est idempotente`() {
        val base = base().copy(theme = AppTheme.WHITE, readPages = listOf(1, 2))
        val local = base.copy(theme = AppTheme.NIGHT, readPages = listOf(1, 2, 3))
        val remote = base.copy(theme = AppTheme.CLASSIC, readPages = listOf(2, 4))

        val once = OfflineMerge.mergeOfflineState(base, local, remote)
        val twice = OfflineMerge.mergeOfflineState(base, once, remote)
        assertEquals(once.theme, twice.theme)
        assertEquals(once.readPages, twice.readPages)
        assertEquals(once.knowledge, twice.knowledge)
    }
}
