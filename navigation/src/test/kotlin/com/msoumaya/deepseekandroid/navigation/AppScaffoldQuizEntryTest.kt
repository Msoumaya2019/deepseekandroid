package com.msoumaya.deepseekandroid.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tient le **point d'entrée du Quiz** dans la coquille.
 *
 * ## Pourquoi ce contrôle existe
 *
 * L'écran du Quiz a été livré **avant d'avoir une porte**. La route était nommée, servie par
 * `composable(AppRoutes.QUIZ)`, et **rien ne naviguait vers elle** : l'écran compilait, ses tests
 * passaient, et il n'était atteignable par aucun geste de l'application. C'est le défaut le plus
 * silencieux de ce dépôt — un écran entier, vert de partout, que personne ne pouvait ouvrir.
 *
 * La porte existe maintenant, et elle est double : le bouton « 🏆 Défier » d'une conversation, qui
 * apporte **l'ami**, et — à venir — l'accueil, qui n'apporte rien. Le Quiz est un écran **plein
 * écran** : ni onglet, ni bouton dans la barre supérieure, donc ces appels sont ses seuls chemins.
 *
 * ## Ce qui disparaîtrait sans un mot
 *
 * Trois choses, et chacune laisserait l'application parfaitement fonctionnelle en apparence :
 *
 *  - **le motif à la place de la route nue** : la navigation ignorerait `?ami=…`, et « Défier cet
 *    ami » ouvrirait l'**accueil** du Quiz au lieu de la création d'un défi. Aucune erreur, aucun
 *    écran vide — juste une intention perdue ;
 *  - **les deux valeurs par défaut** : un argument rendu requis ferait **échouer** l'ouverture du
 *    Quiz sans argument, c'est-à-dire depuis l'accueil, par une exception de navigation ;
 *  - **le rappel `onChallenge` de l'écran des amis** : sans lui, le bouton « 🏆 Défier »
 *    s'afficherait et n'agirait pas. C'est le cas que `ConversationScreenWiringTest` a été écrit
 *    pour attraper, et il ne peut pas l'attraper ici : la valeur par défaut du rappel est vide, et
 *    seule la coquille sait qu'on y branche la navigation.
 *
 * ## Ce qu'il ne prouve pas
 *
 * Il ne prouve pas que la navigation aboutit, ni que l'écran rend juste : les règles de route sont
 * mesurées par `AppRoutesQuizTest`, et le rendu par `QuizRendererTest`, dans `feature:quiz`.
 */
class AppScaffoldQuizEntryTest {

    @Test
    fun `la route du Quiz est servie par son motif`() {
        // Servir la route **nue** laisserait les deux arguments sans effet : la navigation
        // ignorerait la partie `?ami=…`, et l'écran s'ouvrirait sur son accueil — l'appelant
        // annonçant un défi et le Quiz en faisant un autre.
        val occurrences = sourceDeLaCoquille()
            .lines()
            .count { it.contains("route = AppRoutes.QUIZ_PATTERN,") }

        assertEquals(
            1,
            occurrences,
            "La route du Quiz n'est plus servie par son motif ($occurrences fois) : l'ami à " +
                "défier n'atteindrait jamais l'écran, et « Défier » ouvrirait l'accueil du Quiz.",
        )
    }

    @Test
    fun `les deux arguments du Quiz sont facultatifs`() {
        // Une route nue doit rester valide : c'est l'ouverture depuis l'accueil, où l'on ne vient
        // défier personne. Un argument requis ferait échouer cette ouverture — et l'échec serait
        // une exception de navigation, donc un plantage.
        val bloc = quizDeLaCoquille()

        assertTrue(
            bloc.contains("navArgument(AppRoutes.QUIZ_FRIEND)"),
            "L'ami n'est plus déclaré comme argument de la route du Quiz.",
        )
        assertTrue(
            bloc.contains("navArgument(AppRoutes.QUIZ_CHALLENGE)"),
            "Le défi n'est plus déclaré comme argument de la route du Quiz.",
        )
        assertEquals(
            2,
            bloc.lines().count { it.contains("defaultValue = \"\"") },
            "Les deux arguments du Quiz n'ont plus chacun leur valeur par défaut : ouvrir le " +
                "Quiz sans argument échouerait, et l'échec serait un plantage.",
        )
    }

    @Test
    fun `la route recoit ses deux arguments`() {
        // `takeIf { it.isNotEmpty() }` est la sentinelle : un argument absent n'arrive pas en
        // `null` mais en chaîne vide, et une chaîne vide n'est pas un identifiant. La laisser
        // passer ferait chercher un ami qui n'existe pas — l'écran s'ouvrirait normalement, ce
        // qui est le propre d'un défaut muet.
        val bloc = quizDeLaCoquille()

        assertTrue(
            bloc.contains(
                "friendId = entry.arguments?.getString(AppRoutes.QUIZ_FRIEND)?.takeIf { it.isNotEmpty() },",
            ),
            "La route ne transmet plus l'ami de son argument : « Défier » ouvrirait le Quiz " +
                "sans destinataire, et le défi serait proposé à personne.",
        )
        assertTrue(
            bloc.contains(
                "challengeId = entry.arguments?.getString(AppRoutes.QUIZ_CHALLENGE)?.takeIf { it.isNotEmpty() },",
            ),
            "La route ne transmet plus le défi de son argument : une notification qui ouvrirait " +
                "un défi précis mènerait à l'accueil du Quiz.",
        )
    }

    @Test
    fun `la route du Quiz sait se fermer`() {
        // Le Quiz est plein écran : il n'a pas de barre de navigation, donc son bouton « ← » est
        // **le** geste de sortie. Un écran qui appellerait `popBackStack` lui-même ne pourrait
        // plus être ouvert autrement que depuis la pile — c'est la règle que la coquille s'impose
        // pour le lecteur, le tableau de bord et l'objectif.
        assertTrue(
            quizDeLaCoquille().contains("onClose = { navController.popBackStack() },"),
            "La route du Quiz ne fournit plus de quoi se fermer : le bouton « ← » de l'écran " +
                "n'aurait plus d'effet, et l'on resterait enfermé dans le Quiz.",
        )
    }

    @Test
    fun `l'accueil ouvre le Quiz`() {
        // **La porte principale.** Le Quiz est plein écran, donc sans onglet : ses seules entrées
        // sont des appels d'écran à écran. La carte de l'accueil est celle qui ne demande rien —
        // ni ami, ni notification —, et sans elle il faudrait connaître quelqu'un pour ouvrir une
        // question du jour.
        //
        // Le bloc est **borné à l'accueil** par `blocApres` : une recherche sur le fichier entier
        // serait satisfaite par un autre appel, et le contrôle ne dirait plus ce qu'il annonce.
        val accueil = accueilDeLaCoquille()

        assertTrue(
            accueil.contains(
                "onOpenQuiz = { navController.navigate(AppRoutes.quizRoute()) { launchSingleTop = true } },",
            ),
            "L'accueil n'ouvre plus le Quiz : l'écran n'aurait plus aucune porte depuis " +
                "l'accueil, et rien ne le dirait — les cartes s'afficheraient normalement.",
        )
        assertTrue(
            accueil.contains("onOpenFriends = { navController.navigateToTab(AppDestination.FRIENDS) },"),
            "La carte « Amis » de l'accueil ne bascule plus vers l'onglet des amis : elle serait " +
                "un bouton mort.",
        )
    }

    @Test
    fun `le bouton Defier d'une conversation ouvre le Quiz sur l'ami`() {        // La porte elle-même. `quizRoute` est appelée **ici** et non recollée : une route écrite
        // en deux endroits finit par diverger, et la divergence serait muette.
        val amis = amisDeLaCoquille()

        assertTrue(
            amis.contains("onChallenge = { friendId ->"),
            "L'écran des amis ne reçoit plus de quoi défier : le bouton « 🏆 Défier » " +
                "s'afficherait et n'agirait pas — c'est le défaut que ce rappel existe pour " +
                "éviter.",
        )
        assertTrue(
            amis.contains("navController.navigate(AppRoutes.quizRoute(friendId = friendId))"),
            "L'écran des amis n'ouvre plus le Quiz par `quizRoute` : la route serait recollée à " +
                "la main, et la divergence serait muette.",
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
}
