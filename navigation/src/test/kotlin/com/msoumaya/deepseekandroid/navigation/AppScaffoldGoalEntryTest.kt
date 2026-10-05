package com.msoumaya.deepseekandroid.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient le **branchement de l'écran d'objectif** dans la coquille.
 *
 * ## Pourquoi ce contrôle existe
 *
 * L'écran d'objectif est un écran **plein écran** : ni onglet, ni barre supérieure. Sa seule porte
 * est la carte « Objectif » du programme, et sa seule sortie est le rappel `onClose` qu'il reçoit.
 * Or il a longtemps été servi par un **panneau de phase** — `ProfileScreen(mode = ProfileMode.GOAL)`
 * —, c'est-à-dire par un écran qui annonçait ce qui viendrait.
 *
 * Le jour où `GoalScreen` a été livré, deux erreurs sont devenues possibles, et **aucune des deux
 * ne casse la compilation** :
 *
 *   1. la route continuerait de rendre l'emplacement de phase, et l'écran livré serait
 *      inatteignable — tout en compilant, puisque `ProfileScreen` existe toujours ;
 *   2. l'écran serait branché **sans** `onClose`, et « Enregistrer mon programme » fermerait sur
 *      rien : plein écran, sans barre, l'utilisateur n'aurait plus aucun moyen de revenir.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que la route est servie, qu'elle rend `GoalScreen`, et qu'elle lui donne de quoi se
 * fermer. Il ne prouve pas que l'écran calcule juste : c'est `GoalRendererTest`, qui l'éprouve
 * sans Compose et sans conteneur applicatif.
 *
 * ## Pourquoi il lit le source
 *
 * Un test de navigation complet demanderait un hôte de test Compose, le conteneur applicatif et le
 * référentiel coranique. Ce contrôle-ci est la partie qui tient sans rien de tout cela : ce qui
 * est **branché**, et sur quoi.
 */
class AppScaffoldGoalEntryTest {

    @Test
    fun `la route de l'objectif est servie`() {
        // Une route nommée mais non servie ferait planter la première navigation : c'est la règle
        // que la coquille s'impose, et la raison pour laquelle `RECITATIONS` n'y figure pas.
        assertTrue(
            sourceDeLaCoquille().contains("composable(AppRoutes.GOAL) {"),
            "La route de l'objectif n'est plus servie : la carte « Objectif » du programme " +
                "mènerait nulle part, et la première navigation planterait.",
        )
    }

    @Test
    fun `la route de l'objectif rend l'ecran livre, et non un panneau de phase`() {
        val route = routeGoalDeLaCoquille()

        assertTrue(
            route.contains("GoalScreen("),
            "La route de l'objectif ne rend plus `GoalScreen` : l'écran livré serait " +
                "inatteignable, et c'est un emplacement de phase que l'utilisateur verrait.",
        )
        assertFalse(
            route.contains("ProfileScreen("),
            "La route de l'objectif rend de nouveau une section de `ProfileScreen` : " +
                "`GoalScreen` a son propre fichier depuis qu'il est livré.",
        )
    }

    @Test
    fun `l'ecran d'objectif recoit de quoi se fermer`() {
        assertTrue(
            routeGoalDeLaCoquille().contains("onClose = { navController.popBackStack() }"),
            "L'écran d'objectif ne reçoit plus de quoi se fermer. Plein écran, sans onglet ni " +
                "barre supérieure, il n'aurait plus aucun moyen de revenir en arrière — et " +
                "« Enregistrer mon programme » refermerait sur rien.",
        )
    }
}
