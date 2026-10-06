package com.msoumaya.deepseekandroid.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tient le **branchement de l'onglet « Amis »** dans la coquille.
 *
 * ## Pourquoi ce contrôle existe
 *
 * L'onglet « Amis » a longtemps rendu un `PhasePlaceholder` — un panneau qui annonce ce qui
 * viendra. C'est, avec le Quiz, le dernier onglet dans ce cas. Le remplacer par l'écran réel est
 * une livraison qui **ne casse rien si on l'oublie** : la route continue de compiler, l'onglet
 * continue de s'afficher, et il annonce simplement toujours la même chose. Rien, ni la
 * compilation ni l'exécution, ne dirait que la liste d'amis n'est jamais rendue.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que la route est servie, et qu'elle rend bien l'écran des amis. Il ne prouve pas que
 * l'écran affiche juste — c'est `SocialRendererTest`, dans `feature:social`, qui l'éprouve sur des
 * états fabriqués —, ni que l'écran lui-même n'est pas redevenu un panneau : cela se lit dans le
 * source de la fonctionnalité, et c'est `SocialScreenWiringTest` qui le surveille, là où le
 * fichier vit.
 */
class AppScaffoldFriendsEntryTest {

    @Test
    fun `la route des Amis est servie exactement une fois`() {
        // Une route nommée mais non servie ferait planter la première navigation — c'est la règle
        // que la coquille s'impose. Deux fois, et le second `composable` serait mort.
        val occurrences = sourceDeLaCoquille()
            .lines()
            .count { it.contains("composable(AppDestination.FRIENDS.route)") }

        assertEquals(1, occurrences, "La route des Amis est servie $occurrences fois.")
    }

    @Test
    fun `l'onglet Amis rend l'ecran des amis`() {
        assertTrue(
            amisDeLaCoquille().contains("SocialScreen("),
            "L'onglet « Amis » ne rend plus `SocialScreen` : la route existe, mais plus rien " +
                "n'est affiché pour elle.",
        )
    }
}
