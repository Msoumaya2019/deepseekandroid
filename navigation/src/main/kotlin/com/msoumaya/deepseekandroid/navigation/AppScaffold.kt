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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.feature.home.HomeScreen
import com.msoumaya.deepseekandroid.feature.profile.ProfileMode
import com.msoumaya.deepseekandroid.feature.profile.ProfileScreen
import com.msoumaya.deepseekandroid.feature.program.ProgramScreen
import com.msoumaya.deepseekandroid.feature.progress.ProgressScreen
import com.msoumaya.deepseekandroid.feature.quiz.QuizScreen
import com.msoumaya.deepseekandroid.feature.reader.QuranScreen
import com.msoumaya.deepseekandroid.feature.reader.ReaderScreen
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
// edges={['top']}` d'origine : le lecteur de moushaf démarre donc lui aussi sous la barre
// d'état. Le passage en plein écran réel du lecteur viendra avec son écran, en phase B.
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
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            if (route !in AppRoutes.fullScreen) {
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
        composable(AppDestination.HOME.route) { HomeScreen() }
        composable(AppDestination.QURAN.route) { QuranScreen() }
        composable(AppDestination.PROGRAM.route) { ProgramScreen() }
        composable(AppDestination.PROGRESS.route) { ProgressScreen() }
        composable(AppDestination.FRIENDS.route) { SocialScreen() }

        composable(AppRoutes.READER) { ReaderScreen() }
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
