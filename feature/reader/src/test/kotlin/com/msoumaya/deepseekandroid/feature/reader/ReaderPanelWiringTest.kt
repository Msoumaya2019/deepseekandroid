package com.msoumaya.deepseekandroid.feature.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tient le **branchement** du panneau de traduction dans le lecteur.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * Deux conditions sont réunies, et il faut les deux.
 *
 *  1. **Le comportement est hors de portée.** Le lecteur est une fonction `@Composable` : la
 *     déclencher demande un hôte Compose, un appareil ou un émulateur, et le projet n'a aucun
 *     outillage de test d'interface. Le branchement n'est donc atteignable par aucun test de
 *     comportement — ce n'est pas un raccourci, c'est le seul moyen.
 *  2. **Sa disparition serait silencieuse.** Si `onTranslation` cessait d'être passé à la
 *     feuille, ou si le panneau cessait d'être rendu, **rien** ne le dirait : le domaine reste
 *     vert (`TranslationPanelTest` ne connaît pas le lecteur), la compilation passe, et le seul
 *     symptôme est une ligne absente d'une feuille, ou un panneau vide. Personne ne s'en
 *     apercevrait avant de le chercher à la main.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que **le branchement est écrit**. Il ne prouve pas que le panneau s'affiche
 * correctement — cela se lit dans un diff, et c'est dit dans `ARCHITECTURE.md`. Il ne prouve pas
 * non plus que les lignes sont justes : c'est `TranslationPanelTest`, dans `core:domain`.
 *
 * ## Ce qu'il ne vérifie pas, et pourquoi
 *
 * Le lecteur ajoute aussi `ReaderOptionsText.Action.TRANSLATION` à l'ensemble des destinations
 * passées à la feuille. Ce n'est **pas** vérifié ici : `isUseful` étant déjà vrai par « changer
 * de sourate » et « réglages audio », toujours présentes, cette ligne ne change aujourd'hui
 * aucun affichage. Un contrôle qui la garderait surveillerait du code sans effet.
 */
class ReaderPanelWiringTest {

    @Test
    fun `la ligne de traduction de la feuille ouvre le panneau de traduction`() {
        assertTrue(
            sourceDuLecteur().contains("onTranslation = { panel = ReaderPanel.TRANSLATION },"),
            "La feuille d'options ne reçoit plus de destination pour la traduction : la ligne " +
                "« Traduction française » disparaîtra de la feuille, et rien d'autre ne le dira.",
        )
    }

    @Test
    fun `le panneau de traduction calcule ses lignes`() {
        assertTrue(
            sourceDuLecteur().contains("TranslationPanel.rows("),
            "Le panneau de traduction ne calcule plus ses lignes : il n'affichera rien, ou " +
                "affichera des lignes qui ne viennent plus de la règle éprouvée.",
        )
    }

    @Test
    fun `le panneau de traduction est rendu par la feuille`() {
        assertTrue(
            sourceDuLecteur().contains("TranslationPanelSheet("),
            "La destination existe, mais plus rien n'est rendu pour elle : le panneau " +
                "s'ouvrirait sur du vide.",
        )
    }

    /**
     * Le source du lecteur.
     *
     * Deux chemins sont essayés, et non un seul : la tâche `Test` de Gradle s'exécute dans le
     * dossier **du module**, alors qu'un contrôle joué à la main depuis la racine du dépôt part
     * de là. Un chemin unique ferait passer le test ici et échouer là-bas — ou l'inverse.
     *
     * L'échec nomme les chemins essayés : sans cela, « fichier introuvable » ne dit pas si le
     * fichier a bougé ou si c'est le dossier de travail qui a changé.
     */
    private fun sourceDuLecteur(): String {
        val relatif = "src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt"
        val candidats = listOf(File(relatif), File("feature/reader/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "ReaderScreen.kt introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }
}
