package com.msoumaya.deepseekandroid.navigation

import com.msoumaya.deepseekandroid.core.domain.Review
import com.msoumaya.deepseekandroid.core.domain.StudySession
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.ReviewCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le transport d'une tâche par la route du lecteur, éprouvé **pour de vrai**.
 *
 * Ce fichier existe à cause d'un défaut **muet** : la route d'une séance retirait ses trois
 * arguments quand son identifiant était nul. Comme une révision n'a pas d'identifiant de séance,
 * la révision ouverte depuis le programme partait en **lecture libre** — le lecteur s'ouvrait, la
 * page s'affichait, et la validation n'était enregistrée nulle part. Rien ne le signalait : ni la
 * compilation, ni l'écran, ni le test qui vérifiait que le renderer produisait bien la tâche.
 *
 * D'où [AppRoutes.studyRoute] : une seule fonction pour les trois formes de tâche, et ce contrôle
 * qui mesure ce qu'elle écrit. Ce sont des chaînes, donc elles se comparent exactement.
 */
class AppRoutesStudyTest {

    private fun tache(id: String, start: Int, end: Int, category: ReviewCategory) =
        Review.ReviewTask(start = start, end = end, id = id, category = category)

    // -----------------------------------------------------------------------
    // Les trois formes de tâche
    // -----------------------------------------------------------------------

    @Test
    fun `une seance voyage avec son identifiant et ses bornes`() {
        assertEquals(
            "lecteur?verset=746&seance=2026-10-05-746-0&de=746&a=750",
            AppRoutes.studyRoute(
                StudySession.Request(range = Range(746, 750), sessionId = "2026-10-05-746-0"),
            ),
        )
    }

    @Test
    fun `une revision voyage avec sa tache et sa categorie`() {
        // L'identité de la tâche est ce sous quoi la validation sera écrite. La perdre, c'est
        // ouvrir une lecture libre qui ne valide rien — le défaut que ce fichier surveille.
        assertEquals(
            "lecteur?verset=100&tache=habitual-100-105&categorie=habitual&de=100&a=105",
            AppRoutes.studyRoute(
                StudySession.forTask(tache("habitual-100-105", 100, 105, ReviewCategory.HABITUAL)),
            ),
        )
    }

    @Test
    fun `une consolidation porte la categorie qui la distingue d'une revision`() {
        // C'est la **catégorie** qui décide de l'étape des trois jours : `recent` ouvre la
        // consolidation, `habitual` ne l'ouvre pas. Une route qui perdrait la catégorie ferait
        // passer une consolidation pour une révision ordinaire, et l'étape ne serait jamais
        // proposée.
        val route = AppRoutes.studyRoute(
            StudySession.forTask(
                tache("consolidation-6010-6010", 6010, 6010, ReviewCategory.RECENT),
                consolidation = true,
            ),
        )

        assertEquals("lecteur?verset=6010&tache=consolidation-6010-6010&categorie=recent&de=6010&a=6010", route)
        assertFalse(route.contains(AppRoutes.READER_SESSION), "une consolidation n'est pas une séance")
    }

    @Test
    fun `une revision n'emprunte jamais les arguments d'une seance`() {
        // Le défaut d'origine, exactement : une route qui porterait une révision sous l'argument
        // `seance` serait servie comme un apprentissage, et la progression serait écrite sous une
        // clé que le plan de révision ne relit pas.
        val route = AppRoutes.studyRoute(
            StudySession.forTask(tache("priority-6000-6000", 6000, 6000, ReviewCategory.PRIORITY)),
        )

        assertTrue(route.contains("${AppRoutes.READER_TASK}=priority-6000-6000"))
        assertFalse(route.contains("${AppRoutes.READER_SESSION}="))
    }

    @Test
    fun `une lecture libre ne porte aucun identifiant de tache`() {
        // Une lecture libre ne promet rien : lui prêter une tâche ferait écrire une validation que
        // personne n'a demandée.
        val route = AppRoutes.studyRoute(StudySession.Request(range = Range(746, 750)))

        assertEquals("lecteur?verset=746", route)
        assertFalse(route.contains(AppRoutes.READER_TASK))
        assertFalse(route.contains(AppRoutes.READER_CATEGORY))
    }

    // -----------------------------------------------------------------------
    // Clé de catégorie
    // -----------------------------------------------------------------------

    @Test
    fun `la categorie se relit depuis la cle que l'encodeur ecrit`() {
        // L'encodeur est `Review.grouped`, qui construit `"${'$'}{category.name.lowercase()}-…"`.
        // Le décodeur est `Review.categoryOf`. Les deux sont éprouvés ensemble ici : une
        // convention écrite à deux endroits finirait par diverger.
        for (categorie in ReviewCategory.entries) {
            val tache = tache("x", 1, 1, categorie)
            val route = AppRoutes.studyRoute(StudySession.forTask(tache))
            val cle = route.substringAfter("${AppRoutes.READER_CATEGORY}=").substringBefore("&")
            assertEquals(categorie, Review.categoryOf(cle))
        }
    }

    @Test
    fun `une cle de categorie inconnue ne rend pas de categorie`() {
        // Un repli silencieux sur `habitual` ferait enregistrer une révision sous une catégorie
        // que personne n'a choisie — et une consolidation ne serait jamais reconnue.
        assertNull(Review.categoryOf("inconnue"))
        assertNull(Review.categoryOf(""))
        assertNull(Review.categoryOf(null))
    }

    @Test
    fun `une route de tache reste plein ecran et sans barre basse`() {
        val route = AppRoutes.studyRoute(
            StudySession.forTask(tache("habitual-100-105", 100, 105, ReviewCategory.HABITUAL)),
        )

        assertTrue(AppRoutes.isFullScreen(route))
        assertTrue(AppRoutes.isEdgeToEdge(route))
        assertTrue(AppRoutes.hidesBottomBar(route))
        assertNull(AppDestination.fromRoute(route))
    }
}
