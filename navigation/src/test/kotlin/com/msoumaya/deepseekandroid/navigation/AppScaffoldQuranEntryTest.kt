package com.msoumaya.deepseekandroid.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tient le **branchement de l'écran « Coran »** dans la coquille.
 *
 * ## Pourquoi ce contrôle existe
 *
 * L'écran « Coran » vit dans `feature:reader`, qui ne dépend pas de `core:data` — c'est ce qui
 * permet au lecteur de s'ouvrir en avion — donc il ne peut atteindre ni l'état du compte, ni le
 * référentiel, ni le conteneur. Il reçoit tout par des rappels, et **chaque rappel a une valeur
 * par défaut vide**. C'est ce qui rend deux défauts possibles, et **aucun des deux ne casse la
 * compilation** :
 *
 *   1. la route servirait l'écran **nu**, sans état : la liste s'afficherait en attente pour
 *      toujours, sans erreur, et rien ne dirait que la lecture n'a jamais été demandée ;
 *   2. un rappel serait laissé à sa valeur par défaut, et le geste correspondant ne ferait
 *      **rien** — c'est déjà arrivé sur l'accueil, où trois rappels oubliés faisaient trois
 *      boutons sans effet.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que la route est servie, qu'elle rend la **route** et non l'écran, et que les deux
 * gestes de l'écran ont une porte. Il ne prouve pas que le calcul est juste : c'est
 * `QuranListRendererTest`, qui l'éprouve sur le vrai corpus ; ni que l'écriture de la carte
 * Tajwid est correcte : c'est le KDoc de `QuranRoute` et les mesures de `ANDROID_MIGRATION.md`.
 *
 * ## Pourquoi il lit le source, commentaires retirés
 *
 * Un test de navigation complet demanderait un hôte Compose, le conteneur applicatif et le
 * référentiel. Ce contrôle-ci est la partie qui tient sans rien de tout cela. Les commentaires
 * sont retirés par [coranDeLaCoquille] : ce bloc est le plus commenté de la coquille, et un
 * contrôle qui lit le texte brut y trouverait sa propre documentation — il vérifierait ce qu'on
 * attend de la route, pas ce qu'elle fait.
 */
class AppScaffoldQuranEntryTest {

    @Test
    fun `la route du Coran est servie exactement une fois`() {
        // Une route nommée mais non servie ferait planter la première navigation — c'est la règle
        // que la coquille s'impose. Deux fois, et le second `composable` serait mort.
        val occurrences = sourceDeLaCoquille()
            .lines()
            .count { it.contains("composable(AppDestination.QURAN.route)") }

        assertEquals(1, occurrences, "La route du Coran est servie $occurrences fois.")
    }

    @Test
    fun `la route du Coran rend la route, et non l'ecran nu`() {
        // L'écran reçoit son état de la route : le rendre directement afficherait une attente
        // perpétuelle, sans erreur et sans que rien ne dise que la lecture a été oubliée.
        assertTrue(
            coranDeLaCoquille().contains("QuranRoute("),
            "L'onglet « Coran » ne rend plus `QuranRoute` : l'écran n'a plus d'état, et sa liste " +
                "resterait en attente pour toujours.",
        )
    }

    @Test
    fun `l'ecran du Coran ouvre le lecteur par la porte commune`() {
        val route = coranDeLaCoquille()

        assertTrue(
            route.contains("onOpenReader ="),
            "L'écran du Coran ne reçoit plus de quoi ouvrir le lecteur : une ligne de la liste " +
                "serait un bouton sans effet.",
        )
        assertTrue(
            route.contains("AppRoutes.readerRoute("),
            "L'écran du Coran n'ouvre plus le lecteur par `AppRoutes.readerRoute` : c'est la " +
                "porte commune à l'accueil, au programme et à cet écran, et la contourner " +
                "rouvrirait le défaut d'une révision qui part sans sa plage.",
        )
    }

    @Test
    fun `le crayon de la carte des connaissances mene a l'objectif`() {
        val route = coranDeLaCoquille()

        assertTrue(
            route.contains("onEditKnowledge ="),
            "L'écran du Coran ne reçoit plus de quoi ouvrir l'écran d'objectif : le crayon de la " +
                "carte « J'ai appris jusqu'à » serait un bouton sans effet.",
        )
        assertTrue(
            route.contains("AppRoutes.GOAL"),
            "Le crayon de la carte des connaissances ne mène plus à l'écran d'objectif.",
        )
    }
}
