package com.msoumaya.deepseekandroid.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tient le **branchement de l'écran « Progrès »** dans la coquille.
 *
 * ## Pourquoi ce contrôle existe
 *
 * L'écran « Progrès » construit son propre `ViewModel` depuis `LocalAppContainer` : la coquille ne
 * lui passe donc **aucun état**. En revanche, il reçoit ses **gestes** par des rappels, et chaque
 * rappel a une valeur par défaut vide — `onOpenGoal: () -> Unit = {}`. C'est ce qui rend un défaut
 * possible, et **il ne casse pas la compilation** :
 *
 *   `composable(AppDestination.PROGRESS.route) { ProgressScreen() }` compile, s'affiche
 *   correctement, et laisse **deux boutons sans effet** — l'action « Voir tout » de l'en-tête
 *   « Mes objectifs » et la carte d'objectif, qui est cliquable en entier. C'est déjà arrivé sur
 *   l'accueil, où trois rappels oubliés faisaient trois boutons morts.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que la route est servie, qu'elle rend bien `ProgressScreen` avec sa porte, et que
 * cette porte mène à l'écran d'objectif sans empiler deux écrans. Il ne prouve pas que le calcul
 * est juste : c'est `ProgressRendererTest`, qui l'éprouve sur le vrai corpus.
 *
 * ## Pourquoi il lit le source, commentaires retirés
 *
 * Un test de navigation complet demanderait un hôte Compose et le conteneur applicatif. Les
 * commentaires sont retirés par [progresDeLaCoquille] : le bloc porte l'explication de la lecture
 * seule, qui nomme `AppRoutes.GOAL`, et un contrôle sur le texte brut y trouverait sa propre
 * documentation — il lirait ce qu'on **attend** de la route au lieu de ce qu'elle **fait**.
 */
class AppScaffoldProgressEntryTest {

    @Test
    fun `la route du Progres est servie exactement une fois`() {
        // Une route nommée mais non servie ferait planter la première navigation — c'est la règle
        // que la coquille s'impose. Deux fois, et le second `composable` serait mort.
        val occurrences = sourceDeLaCoquille()
            .lines()
            .count { it.contains("composable(AppDestination.PROGRESS.route)") }

        assertEquals(1, occurrences, "La route du Progrès est servie $occurrences fois.")
    }

    @Test
    fun `l'ecran du Progres recoit de quoi ouvrir l'objectif`() {
        // Sans ce rappel, la carte d'objectif et l'action « Voir tout » sont des boutons sans
        // effet — et rien ne le dit : ni la compilation, ni l'affichage.
        val route = progresDeLaCoquille()

        assertTrue(
            route.contains("ProgressScreen("),
            "L'onglet « Progrès » ne rend plus `ProgressScreen`.",
        )
        assertTrue(
            route.contains("onOpenGoal ="),
            "L'écran du Progrès ne reçoit plus de quoi ouvrir l'écran d'objectif : la carte " +
                "d'objectif et l'action « Voir tout » seraient deux boutons sans effet.",
        )
    }

    @Test
    fun `la carte d'objectif mene a l'ecran d'objectif`() {
        assertTrue(
            progresDeLaCoquille().contains("AppRoutes.GOAL"),
            "La carte d'objectif du Progrès ne mène plus à l'écran d'objectif.",
        )
    }

    @Test
    fun `l'ouverture de l'objectif n'empile pas deux ecrans`() {
        // La carte est cliquable **en entier** et l'en-tête porte la même action : deux appuis
        // rapprochés empileraient deux exemplaires de l'écran d'objectif, et le retour ramènerait
        // sur le premier. `launchSingleTop` est la règle du programme, du Coran et d'ici.
        assertTrue(
            progresDeLaCoquille().contains("launchSingleTop = true"),
            "L'ouverture de l'écran d'objectif depuis le Progrès n'est plus `launchSingleTop`.",
        )
    }
}
