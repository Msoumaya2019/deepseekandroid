package com.msoumaya.deepseekandroid.feature.progress

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tient le **branchement du bloc « Quiz »** dans l'écran « Progrès ».
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * `ProgressScreen` est une fonction `@Composable` : la déclencher demande un hôte Compose, un
 * appareil ou un émulateur, et le projet n'a aucun outillage de test d'interface. Le branchement
 * n'est donc atteignable par aucun test de comportement. Le calcul, lui, est éprouvé pour de vrai
 * dans `ProgressQuizStatsTest` : ce fichier ne surveille que le câblage.
 *
 * ## Ce qui disparaîtrait sans un mot
 *
 * Trois choses :
 *
 *  - **la composition du bloc** : retirer l'appel compile, l'écran s'affiche normalement, et le
 *    bloc de quiz n'est simplement plus jamais peint. Rien, ni la compilation ni l'exécution, ne
 *    le dirait — c'est le défaut qu'un `PhasePlaceholder` remplaçait sans le signaler ;
 *  - **sa place** : le bloc est un cumul depuis le début, et l'original le pose **entre** la carte
 *    d'objectif et les compteurs. Le déplacer sous le sélecteur de période ferait croire qu'il
 *    change avec elle ;
 *  - **l'observation de l'instantané** : sans `container.quiz.state` dans la fabrique, le
 *    `ViewModel` n'aurait aucun instantané, et le bloc disparaîtrait pour toujours — un écran
 *    complet, vert, où une fonctionnalité livrée n'apparaît jamais.
 *
 * ## Ce qu'il ne prouve pas
 *
 * Il ne prouve pas que les trois lignes sont justes : elles viennent de `Quiz.statistics` et de
 * `QuizText`, mesurées par `ProgressQuizStatsTest`.
 */
class ProgressScreenWiringTest {

    @Test
    fun `l'ecran compose le bloc de quiz quand l'etat le porte`() {
        assertTrue(
            sourceDeLEcran().contains("state.quiz?.let { summary -> QuizStats(summary = summary) }"),
            "L'écran « Progrès » ne compose plus le bloc de quiz : une fonctionnalité livrée ne " +
                "serait jamais affichée, et rien ne le dirait.",
        )
    }

    @Test
    fun `le bloc est pose entre la carte d'objectif et les compteurs`() {
        // L'ordre est une décision, pas un hasard : le bloc de quiz est un **cumul**, il ne suit
        // pas la période, et le sélecteur « Jour / Semaine / Mois » est plus haut. Le poser après
        // les compteurs ou après le graphique le ferait lire comme la suite du graphique.
        //
        // La borne est le `AppSectionHeader(title = ProgressText.STATISTICS)` : le contrôle exige
        // que le bloc soit **avant** lui, et non simplement présent dans le fichier.
        val source = sourceDeLEcran()

        val positionBloc = source.indexOf("QuizStats(summary = summary)")
        val positionCompteurs = source.indexOf("AppSectionHeader(title = ProgressText.STATISTICS)")

        assertTrue(
            positionBloc >= 0 && positionCompteurs >= 0,
            "Le bloc de quiz ou l'en-tête « Statistiques » a disparu du source : le contrôle ne " +
                "peut plus dire où le bloc est posé.",
        )
        assertTrue(
            positionBloc < positionCompteurs,
            "Le bloc de quiz n'est plus posé avant les compteurs : c'est sa place dans " +
                "l'original, et l'inverser le ferait lire comme la suite du graphique.",
        )
    }

    @Test
    fun `le ViewModel observe l'instantane du Quiz`() {
        val source = sourceDuViewModel()

        assertTrue(
            source.contains("quizState: StateFlow<QuizState>,"),
            "Le `ViewModel` de « Progrès » ne reçoit plus l'instantané du Quiz : le bloc " +
                "disparaîtrait pour toujours.",
        )
        assertTrue(
            source.contains("container.quiz.state,"),
            "La fabrique ne branche plus le dépôt du Quiz : l'écran n'aurait jamais d'instantané.",
        )
    }

    @Test
    fun `le renderer recoit l'instantane et le compte`() {
        // Les deux entrées qui décident du bloc : sans l'instantané il n'existe pas, et sans le
        // compte les victoires se lisent du mauvais côté. `render` les accepte toutes deux
        // facultativement, donc les oublier compile.
        val source = sourceDuViewModel()

        assertTrue(
            source.contains("quiz = sources.quiz.snapshot,"),
            "Le `ViewModel` n'appelle plus `render` avec l'instantané : le bloc de quiz " +
                "n'apparaîtrait jamais.",
        )
        assertTrue(
            source.contains("userId = sources.quiz.ownerId,"),
            "Le `ViewModel` n'appelle plus `render` avec l'identifiant du compte : les défis se " +
                "liraient du mauvais côté, et victoires et défaites seraient inversées.",
        )
    }

    /**
     * Le source d'un fichier du module.
     *
     * Deux chemins sont essayés, et non un seul : la tâche `Test` de Gradle s'exécute dans le
     * dossier **du module**, alors qu'un contrôle joué à la main depuis la racine du dépôt part de
     * là.
     */
    private fun source(nom: String): String {
        val relatif = "src/main/kotlin/com/msoumaya/deepseekandroid/feature/progress/$nom"
        val candidats = listOf(File(relatif), File("feature/progress/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "$nom introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }

    private fun sourceDeLEcran(): String = source("ProgressScreen.kt")

    private fun sourceDuViewModel(): String = source("ProgressViewModel.kt")
}
