package com.msoumaya.deepseekandroid.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.msoumaya.deepseekandroid.core.domain.StudySession
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.feature.home.HomeScreen
import com.msoumaya.deepseekandroid.feature.profile.ProfileMode
import com.msoumaya.deepseekandroid.feature.profile.ProfileScreen
import com.msoumaya.deepseekandroid.feature.program.ProgramScreen
import com.msoumaya.deepseekandroid.feature.progress.ProgressScreen
import com.msoumaya.deepseekandroid.feature.quiz.QuizScreen
import com.msoumaya.deepseekandroid.feature.reader.QuranScreen
import com.msoumaya.deepseekandroid.feature.social.SocialScreen

// ---------------------------------------------------------------------------
// Coquille de l'application
// ---------------------------------------------------------------------------
// Portage de la structure de `App.tsx`. Le dépôt d'origine n'utilisait pas de pile de
// navigation : il gardait un onglet dans un état et empilait les écrans par-dessus avec des
// booléens (`reader`, `quizOpen`, `utilityView`, `reviewOpen`…). Sept booléens pour décrire un
// seul état, c'est ce qui rendait les combinaisons impossibles à tenir.
//
// Ici c'est une vraie pile, et la visibilité des barres se **déduit** de la route courante au
// lieu d'être recomposée à partir de drapeaux. Les règles sont dans `AppRoutes` :
//
//   onglet        -> barre supérieure avec rangée d'onglets + barre basse
//   écran d'outil -> barre supérieure avec retour, pas de barre basse
//   plein écran   -> aucune des deux
//
// La correspondance avec les conditions d'origine :
//
//   `!reader && accountIntro==='done' && wizard===null && !reviewOpen && !recitationsOpen &&
//    !dailyOpen && !quizOpen && socialView!=='admin'`      -> en-tête visible
//   … et `!utilityView` de plus                             -> barre basse visible
//
// Le décalage de la barre d'état est posé **sur la colonne entière**, comme le `SafeAreaView
// edges={['top']}` d'origine — sauf sur les routes déclarées `edgeToEdge`, qui vont jusqu'aux
// bords et posent elles-mêmes leurs marges. Le lecteur de moushaf en fait partie depuis la
// phase B : il centre la page dans l'espace sûr, barres système et découpes retirées.
// ---------------------------------------------------------------------------

/**
 * Coquille de l'application : barre supérieure, contenu, barre basse.
 *
 * @param firstName prénom pour l'initiale du bouton de profil. Sera lu depuis la session
 *   Supabase quand l'authentification sera branchée ; en attendant, l'appelant le fournit.
 * @param navController contrôleur de navigation. Exposé pour que les tests puissent en injecter
 *   un et vérifier les routes, plutôt que d'observer un effet de bord.
 */
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    firstName: String? = null,
) {
    val colors = AppTheme.colors
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tab = AppDestination.fromRoute(route)
    val utilityTitle = AppRoutes.utilityTitle(route)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.cream),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Le décalage de la barre d'état est posé **sur la colonne entière**, comme le
                // `SafeAreaView edges={['top']}` d'origine — sauf pour les écrans qui vont
                // jusqu'aux bords et gèrent leurs propres marges. Le lecteur en fait partie :
                // sans cette exception, il resterait une bande morte au-dessus de la page, et
                // la page ne serait plus centrée dans l'écran réel.
                .then(
                    if (AppRoutes.isEdgeToEdge(route)) {
                        Modifier
                    } else {
                        Modifier.windowInsetsPadding(WindowInsets.statusBars)
                    },
                ),
        ) {
            if (!AppRoutes.isFullScreen(route)) {
                AppTopBar(
                    title = utilityTitle ?: DEFAULT_TITLE,
                    firstName = firstName,
                    // La rangée d'onglets n'apparaît que sur un onglet : c'est le
                    // `showTabs={!utilityView}` d'origine, exprimé par la présence de l'onglet.
                    current = if (utilityTitle == null) tab else null,
                    onSelect = { navController.navigateToTab(it) },
                    // `launchSingleTop` : appuyer deux fois sur le bouton de profil ne doit pas
                    // empiler deux écrans de profil, ce que faisait l'état booléen d'origine
                    // sans pouvoir le faire.
                    onProfile = { navController.navigate(AppRoutes.PROFILE) { launchSingleTop = true } },
                    onSettings = { navController.navigate(AppRoutes.SETTINGS) { launchSingleTop = true } },
                    onBack = if (utilityTitle != null) {
                        { navController.popBackStack() }
                    } else {
                        null
                    },
                )
            }

            AppNavHost(
                navController = navController,
                modifier = Modifier.weight(1f),
            )

            if (!AppRoutes.hidesBottomBar(route)) {
                AppBottomBar(
                    current = tab,
                    onSelect = { navController.navigateToTab(it) },
                )
            }
        }
    }
}

/**
 * Les routes de l'application.
 *
 * Les cinq onglets sont branchés sur leur écran. Les routes sans écran ne sont pas déclarées ici :
 * une route déclarée mais non servie planterait à la première navigation. Elles seront ajoutées
 * avec leur écran — `REVIEW`, `DAILY`, `RECITATIONS` et `ADMIN` sont déjà nommées dans
 * [AppRoutes] et attendent leur implémentation.
 */
@Composable
private fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.start.route,
        modifier = modifier,
    ) {
        // L'accueil ouvre le lecteur : c'est le seul point d'entrée aujourd'hui, l'écran
        // « Coran » n'étant pas encore construit. Le verset de la carte « Continuer » est
        // **passé** plutôt que relu par la route : lui seul sait ce qui a été demandé, et c'est
        // ce verset-là qui sera retenu en refermant — le relire ailleurs ferait mémoriser le
        // premier verset de la page, et l'on reviendrait un verset plus haut à chaque fois.
        composable(AppDestination.HOME.route) {
            HomeScreen(
                onOpenReader = { verseId ->
                    navController.navigate(AppRoutes.readerRoute(verseId))
                },
                // Une séance voyage avec son **identifiant** et ses bornes. Les trois sont
                // posés : c'est `StudySession.plannedRange` qui redonne la priorité à la séance
                // présente dans l'état, donc les bornes ne sont pas une seconde source de
                // vérité — elles servent tant que l'état du compte n'est pas arrivé, sans quoi
                // le lecteur ne saurait pas quelle page ouvrir.
                onOpenStudy = { request ->
                    navController.navigate(
                        AppRoutes.readerRoute(
                            verseId = request.range.start,
                            sessionId = request.sessionId,
                            from = request.range.start,
                            to = request.range.end,
                        ),
                    )
                },
            )
        }
        composable(AppDestination.QURAN.route) { QuranScreen() }
        composable(AppDestination.PROGRAM.route) { ProgramScreen() }
        composable(AppDestination.PROGRESS.route) { ProgressScreen() }
        composable(AppDestination.FRIENDS.route) { SocialScreen() }

        // Le lecteur ne connaît pas la navigation : il demande à fermer, et c'est la coquille
        // qui décide où l'on retourne. Un écran qui appelle `popBackStack` lui-même ne peut
        // plus être ouvert autrement que depuis la pile.
        //
        // La route — et non l'écran — porte la **porte** : une source en paquet dont
        // l'installation n'est pas en place remplace la page par le panneau de téléchargement,
        // et le choix de présentation se fait par-dessus. Le lecteur, lui, ne sait ni ce qu'est
        // un paquet ni où il est stocké.
        //
        // Le motif porte l'argument, et non la route nue : sans lui, la navigation ne pourrait
        // pas distinguer « ouvrir le lecteur sur le verset 746 » de « ouvrir le lecteur ».
        // `defaultValue = 0` sert de sentinelle — aucun verset du Coran ne porte ce numéro, et
        // la route nue reste donc valide et sans verset.
        composable(
            route = AppRoutes.READER_PATTERN,
            arguments = listOf(
                navArgument(AppRoutes.READER_VERSE) {
                    type = NavType.IntType
                    defaultValue = 0
                },
                navArgument(AppRoutes.READER_SESSION) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(AppRoutes.READER_FROM) {
                    type = NavType.IntType
                    defaultValue = 0
                },
                navArgument(AppRoutes.READER_TO) {
                    type = NavType.IntType
                    defaultValue = 0
                },
            ),
        ) { entry ->
            // La séance n'est reconstruite que si ses **trois** arguments sont là. Deux sur trois
            // donneraient une plage inventée : mieux vaut une lecture libre, qui ne promet rien,
            // qu'une séance qui annoncerait une progression fausse.
            val seance = entry.arguments?.getString(AppRoutes.READER_SESSION)?.takeIf { it.isNotEmpty() }
            val debut = entry.arguments?.getInt(AppRoutes.READER_FROM)?.takeIf { it > 0 }
            val fin = entry.arguments?.getInt(AppRoutes.READER_TO)?.takeIf { it > 0 }
            ReaderRoute(
                startVerse = entry.arguments?.getInt(AppRoutes.READER_VERSE)?.takeIf { it > 0 },
                session = if (seance != null && debut != null && fin != null) {
                    StudySession.Request(range = Range(debut, fin), sessionId = seance)
                } else {
                    null
                },
                onClose = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.QUIZ) { QuizScreen() }

        composable(AppRoutes.PROFILE) { ProfileScreen(mode = ProfileMode.PROFILE) }
        composable(AppRoutes.SETTINGS) { ProfileScreen(mode = ProfileMode.SETTINGS) }
        composable(AppRoutes.APPEARANCE) { ProfileScreen(mode = ProfileMode.APPEARANCE) }
        composable(AppRoutes.GOAL) { ProfileScreen(mode = ProfileMode.GOAL) }
    }
}

/**
 * Bascule vers un onglet.
 *
 * Le motif est celui d'une barre d'onglets : on ne garde qu'une seule entrée d'onglet dans la
 * pile, et l'état de chaque onglet est sauvegardé puis restauré. Sans `popUpTo`, appuyer dix fois
 * sur « Coran » empilerait dix écrans et le retour système devrait être pressé dix fois.
 */
fun NavHostController.navigateToTab(destination: AppDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Titre de l'en-tête hors écran d'outil : `title='Apprendre le Coran'` par défaut à l'origine. */
private const val DEFAULT_TITLE = "Apprendre le Coran"
