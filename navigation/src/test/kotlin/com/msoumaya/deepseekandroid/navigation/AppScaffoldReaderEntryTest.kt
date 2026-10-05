package com.msoumaya.deepseekandroid.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tient le **point d'entrée du lecteur** dans la coquille.
 *
 * ## Pourquoi ce contrôle existe
 *
 * Le lecteur est un écran **plein écran** : ni onglet, ni bouton dans la barre supérieure. Sa
 * seule porte est donc un appel depuis un autre écran, et aujourd'hui l'accueil est cette porte.
 * Si ce branchement disparaissait — une ligne retirée en refactorant la coquille, un
 * `HomeScreen()` remis sans argument —, la compilation passerait, tous les autres tests
 * resteraient verts, et le lecteur deviendrait **inatteignable** : l'application n'aurait plus
 * de Coran du tout, sans qu'aucun contrôle ne le dise.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que l'accueil reçoit de quoi ouvrir le lecteur, que le verset traverse la route, et
 * que la route est servie par le motif qui porte l'argument. Il ne prouve pas que la navigation
 * aboutit : c'est `AppRoutesReaderTest`, qui mesure les règles sur lesquelles elle s'appuie.
 */
class AppScaffoldReaderEntryTest {

    @Test
    fun `l'accueil ouvre le lecteur`() {
        assertTrue(
            sourceDeLaCoquille().contains("HomeScreen(\n                onOpenReader = { verseId ->"),
            "L'accueil ne reçoit plus de quoi ouvrir le lecteur : le lecteur n'aurait plus " +
                "aucune porte, et l'application n'aurait plus de Coran — sans que rien ne le dise.",
        )
    }

    @Test
    fun `l'accueil passe le verset par la route du lecteur`() {
        // Le verset n'est pas décoratif : c'est lui qui sera retenu en refermant. Une route nue
        // ferait ouvrir le lecteur à la page mémorisée, et mémoriserait le premier verset de
        // cette page — donc l'accueil reviendrait un cran plus haut à chaque aller-retour.
        assertTrue(
            sourceDeLaCoquille().contains("navController.navigate(AppRoutes.readerRoute(verseId))"),
            "L'accueil n'ouvre plus le lecteur sur le verset demandé : le verset serait perdu, " +
                "et la position mémorisée se décalerait à chaque ouverture.",
        )
    }

    @Test
    fun `la route du lecteur est servie par son motif`() {
        // Servir la route **nue** laisserait l'argument sans effet : la navigation ignorerait la
        // partie `?verset=746`, et le lecteur s'ouvrirait sans verset — l'accueil annonçant une
        // chose et le lecteur en faisant une autre.
        assertTrue(
            sourceDeLaCoquille().contains("route = AppRoutes.READER_PATTERN,"),
            "La route du lecteur n'est plus servie par son motif : l'argument du verset " +
                "n'atteindrait jamais la route.",
        )
    }

    @Test
    fun `l'argument du verset est facultatif`() {
        // Une route nue doit rester valide : c'est l'ouverture libre, sans verset. Un argument
        // requis ferait échouer cette ouverture, et l'échec serait une exception de navigation.
        assertTrue(
            sourceDeLaCoquille().contains("defaultValue = 0"),
            "L'argument du verset n'a plus de valeur par défaut : la route du lecteur sans " +
                "verset ne serait plus servie.",
        )
    }

    @Test
    fun `la route recoit le verset de son argument`() {
        // `takeIf { it > 0 }` est la sentinelle : `0` veut dire « aucun verset », et le passer
        // tel quel ferait chercher un verset qui n'existe pas — donc une fermeture qui échoue,
        // ou une position mémorisée au premier verset du Coran.
        assertTrue(
            sourceDeLaCoquille()
                .contains("startVerse = entry.arguments?.getInt(AppRoutes.READER_VERSE)?.takeIf { it > 0 },"),
            "La route ne transmet plus le verset de son argument : le lecteur s'ouvrirait " +
                "toujours sans verset, et la position se décalerait.",
        )
    }

    @Test
    fun `le source lu est bien celui de la coquille`() {
        assertTrue(
            sourceDeLaCoquille().contains("fun AppScaffold("),
            "Le fichier lu ne déclare pas `fun AppScaffold(` : le chemin résolu ne désigne pas " +
                "la coquille.",
        )
    }

    /**
     * Le source de la coquille.
     *
     * Deux chemins sont essayés, et non un seul : la tâche `Test` de Gradle s'exécute dans le
     * dossier **du module**, alors qu'un contrôle joué à la main depuis la racine du dépôt part
     * de là.
     */
    private fun sourceDeLaCoquille(): String {
        val relatif = "src/main/kotlin/com/msoumaya/deepseekandroid/navigation/AppScaffold.kt"
        val candidats = listOf(File(relatif), File("navigation/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "AppScaffold.kt introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }
}
