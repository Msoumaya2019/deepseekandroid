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
import com.msoumaya.deepseekandroid.core.domain.Review
import com.msoumaya.deepseekandroid.core.domain.StudySession
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.ReviewCategory
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.feature.home.HomeScreen
import com.msoumaya.deepseekandroid.feature.profile.GoalScreen
import com.msoumaya.deepseekandroid.feature.profile.ProfileMode
import com.msoumaya.deepseekandroid.feature.profile.ProfileScreen
import com.msoumaya.deepseekandroid.feature.program.ProgramScreen
import com.msoumaya.deepseekandroid.feature.program.ReviewDashboardScreen
import com.msoumaya.deepseekandroid.feature.progress.ProgressScreen
import com.msoumaya.deepseekandroid.feature.quiz.QuizScreen
import com.msoumaya.deepseekandroid.feature.recitations.RecitationsScreen
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
            // Le bandeau de connectivité est le **premier** enfant de la colonne, comme dans
            // l'original où il précède la barre supérieure et tout écran. Il ne dessine rien
            // tant que le réseau va bien, et n'occupe donc aucune place le reste du temps.
            //
            // Il porte lui-même la marge de la barre d'état sur les écrans qui vont jusqu'aux
            // bords : la colonne n'en pose pas pour eux, et sans cette marge le bandeau
            // s'afficherait **sous** l'heure et les icônes du système — illisible exactement
            // quand il a quelque chose à dire.
            ConnectivityBanner(
                modifier = if (AppRoutes.isEdgeToEdge(route)) {
                    Modifier.windowInsetsPadding(WindowInsets.statusBars)
                } else {
                    Modifier
                },
            )

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
 * Les cinq onglets sont branchés sur leur écran, et les écrans plein écran construits le sont
 * aussi. Les routes **sans écran** ne sont pas déclarées ici : une route déclarée mais non servie
 * planterait à la première navigation. C'est pourquoi `DAILY` et `ADMIN` — déjà nommées dans
 * [AppRoutes] — n'apparaissent pas ci-dessous. Elles attendent leur écran, et les rappels qui y
 * mèneraient restent à leur valeur par défaut : un appui ferait planter l'application, ce qui est
 * pire qu'un bouton sans effet.
 *
 * `RECITATIONS` a quitté cette liste avec l'écran de la liste des récitations : la route est
 * servie, et le rappel du tableau de bord la nomme. C'est le couple qui compte — servir la route
 * **et** brancher le rappel —, et `AppScaffoldReviewEntryTest` tient les deux.
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
        // L'accueil ouvre le lecteur, comme le programme depuis la phase C — et comme l'écran
        // « Coran » depuis qu'il existe. Le verset de la carte « Continuer » est **passé** plutôt
        // que relu par la route : lui seul sait ce qui a été demandé, et c'est ce verset-là qui
        // sera retenu en refermant — le relire ailleurs ferait mémoriser le premier verset de la
        // page, et l'on reviendrait un verset plus haut à chaque fois.
        composable(AppDestination.HOME.route) {
            HomeScreen(
                onOpenReader = { verseId ->
                    navController.navigate(AppRoutes.readerRoute(verseId))
                },
                // Une tâche voyage par **une seule** fonction, `studyRoute`, qui la transporte
                // entière : son identité, sa catégorie et ses bornes. Écrire la route ici à la
                // main a déjà produit un défaut muet — une révision partait en lecture libre,
                // donc sans validation. Voir la note de `studyRoute`.
                onOpenStudy = { request -> navController.navigate(AppRoutes.studyRoute(request)) },
                // Trois rappels de l'accueil étaient laissés à leur valeur par défaut, donc trois
                // boutons sans effet : le bandeau « Prochain objectif », la carte de progression
                // et le rappel de révision. Deux basculent vers un onglet, le troisième ouvre le
                // tableau de bord. Ils sont posés ici parce qu'ils sont **invoqués** par l'écran :
                // un rappel déclaré mais jamais branché ne se voit nulle part.
                onOpenProgram = { navController.navigateToTab(AppDestination.PROGRAM) },
                onOpenProgress = { navController.navigateToTab(AppDestination.PROGRESS) },
                onOpenReviews = { navController.navigate(AppRoutes.REVIEW) { launchSingleTop = true } },
                // La carte « Quiz » de l'accueil — **la porte principale de l'écran**. Sans elle,
                // le Quiz n'aurait que le bouton « 🏆 Défier » d'une conversation : il faudrait un
                // ami pour ouvrir une question du jour.
                //
                // `quizRoute()` sans argument plutôt que la constante `QUIZ` : la route est
                // construite en un seul endroit, et si un jour un argument devient nécessaire,
                // c'est cette fonction qui le portera — la constante, elle, resterait fausse.
                // `launchSingleTop` comme depuis le programme et le Coran : appuyer deux fois ne
                // doit pas empiler deux Quiz.
                onOpenQuiz = { navController.navigate(AppRoutes.quizRoute()) { launchSingleTop = true } },
                // La carte « Amis » **bascule** vers l'onglet au lieu d'empiler un écran : c'est
                // le geste de la barre basse, et l'original écrit `setTab('Amis')`.
                onOpenFriends = { navController.navigateToTab(AppDestination.FRIENDS) },
            )
        }
        // L'écran « Coran » : la liste des sourates, celle des Juz', celle des Hizb. Il est porté
        // par une route du même module, et non appelé directement : l'écran vit dans
        // `feature:reader`, qui ne dépend pas de `core:data` — c'est ce qui permet au lecteur de
        // s'ouvrir en avion — donc il ne peut atteindre ni l'état du compte, ni le référentiel.
        //
        // Une ligne ouvre le lecteur par la **même** fonction que l'accueil et le programme : une
        // seule porte pour le lecteur, donc aucune divergence possible entre les trois écrans. Le
        // crayon de la carte « J'ai appris jusqu'à » mène à l'écran d'objectif, `launchSingleTop`
        // comme depuis le programme — l'appuyer deux fois ne doit pas empiler deux écrans.
        composable(AppDestination.QURAN.route) {
            QuranRoute(
                onOpenReader = { range -> navController.navigate(AppRoutes.readerRoute(range.start)) },
                onEditKnowledge = {
                    navController.navigate(AppRoutes.GOAL) { launchSingleTop = true }
                },
            )
        }
        // Le programme est le **pivot** de la phase C : ses cartes ouvrent le lecteur, et par
        // deux chemins distincts — une séance, qui a une progression à valider, et une lecture
        // libre, qui n'en a pas. Un rappel laissé de côté ne se verrait nulle part : la
        // compilation passe, et la carte devient un bouton qui ne fait rien.
        composable(AppDestination.PROGRAM.route) {
            ProgramScreen(
                onOpenReader = { verseId ->
                    navController.navigate(AppRoutes.readerRoute(verseId))
                },
                // Même règle qu'à l'accueil, et c'est délibéré : les deux écrans, plus le tableau
                // de bord, ouvrent leurs tâches par `studyRoute`. Une seule fonction pour trois
                // appelants, donc aucune divergence possible entre eux.
                onOpenStudy = { request -> navController.navigate(AppRoutes.studyRoute(request)) },
                // Le crayon de la carte « Mon objectif ». `launchSingleTop` : appuyer deux fois
                // sur le crayon ne doit pas empiler deux écrans d'objectif.
                onOpenGoal = { navController.navigate(AppRoutes.GOAL) { launchSingleTop = true } },
                // La carte de révision mène au tableau de bord quand aucun passage n'est dû.
                // C'était le rappel laissé de côté ; il est branché depuis que l'écran existe.
                onOpenReviews = { navController.navigate(AppRoutes.REVIEW) { launchSingleTop = true } },
            )
        }
        // L'écran « Progrès ». Il est en **lecture seule** — il observe et il compte, il n'écrit
        // rien —, mais deux portes mènent de sa carte d'objectif à l'écran d'objectif : l'action
        // « Voir tout » de l'en-tête de section, et la carte elle-même. `launchSingleTop` comme
        // depuis le programme et le Coran : appuyer deux fois ne doit pas empiler deux écrans.
        composable(AppDestination.PROGRESS.route) {
            ProgressScreen(
                onOpenGoal = { navController.navigate(AppRoutes.GOAL) { launchSingleTop = true } },
            )
        }
        // Les amis et les cercles. La conversation qui s'y ouvre porte le bouton « 🏆 Défier », et
        // c'est **ici** que l'on sait où mène un défi : un écran qui appellerait `navigate`
        // lui-même ne pourrait plus être composé ailleurs — même raison que pour le lecteur et le
        // tableau de bord des révisions.
        //
        // La route est construite par `quizRoute` et non recollée sur place : une route écrite en
        // deux endroits finit par diverger, et la divergence serait muette — la navigation
        // n'échouerait pas, elle ouvrirait l'accueil du Quiz au lieu de la création d'un défi.
        composable(AppDestination.FRIENDS.route) {
            SocialScreen(
                onChallenge = { friendId ->
                    navController.navigate(AppRoutes.quizRoute(friendId = friendId))
                },
            )
        }

        // Le tableau de bord des révisions. Plein écran — l'original le posait par-dessus tout —,
        // donc il porte lui-même son bouton de retour, et c'est la coquille qui décide où l'on
        // retourne : un écran qui appelle `popBackStack` lui-même ne peut plus être ouvert
        // autrement que depuis la pile.
        composable(AppRoutes.REVIEW) {
            ReviewDashboardScreen(
                onClose = { navController.popBackStack() },
                // La tâche du jour s'ouvre par la même fonction que depuis le programme et
                // l'accueil. C'est ce qui garantit qu'une consolidation, une révision et une
                // séance arrivent au lecteur sous la même forme, quelle que soit la porte.
                onOpenStudy = { request -> navController.navigate(AppRoutes.studyRoute(request)) },
                // Les statistiques sont l'onglet « Progrès » : on y **bascule** comme depuis la
                // barre basse, au lieu d'empiler un second exemplaire de l'onglet par-dessus le
                // tableau de bord — ce qui laisserait le retour ramener sur un écran plein écran.
                onStatistics = { navController.navigateToTab(AppDestination.PROGRESS) },
                // Les récitations enregistrées : la route est servie **juste après**, et le rappel
                // la nomme. C'est la règle de la coquille — un rappel ne mène qu'à une route
                // servie —, et c'est ce qui rend le bouton de la carte « Suivi » vivant.
                onRecitations = { navController.navigate(AppRoutes.RECITATIONS) { launchSingleTop = true } },
            )
        }

        // Les récitations enregistrées. Écran **plein écran**, comme le tableau de bord : ni
        // onglet ni barre supérieure, donc il porte lui-même son bouton de retour, et c'est la
        // coquille qui décide où l'on retourne — un écran qui appellerait `popBackStack` lui-même
        // ne pourrait plus être ouvert autrement que depuis la pile.
        //
        // `launchSingleTop` : deux appuis sur le bouton du tableau de bord ne doivent pas empiler
        // deux fois le même écran, sans quoi le retour ramènerait sur une copie de l'écran qu'on
        // vient de quitter.
        composable(AppRoutes.RECITATIONS) {
            RecitationsScreen(onClose = { navController.popBackStack() })
        }

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
                navArgument(AppRoutes.READER_TASK) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(AppRoutes.READER_CATEGORY) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            // La tâche n'est reconstruite que si elle est **complète** : une identité sans ses
            // bornes ne saurait pas quelle plage compter, et des bornes sans identité ne seraient
            // pas validables. Deux arguments sur trois donneraient une plage inventée : mieux
            // vaut une lecture libre, qui ne promet rien, qu'une séance qui annoncerait une
            // progression fausse.
            val debut = entry.arguments?.getInt(AppRoutes.READER_FROM)?.takeIf { it > 0 }
            val fin = entry.arguments?.getInt(AppRoutes.READER_TO)?.takeIf { it > 0 }
            val seance = entry.arguments?.getString(AppRoutes.READER_SESSION)?.takeIf { it.isNotEmpty() }
            val tache = entry.arguments?.getString(AppRoutes.READER_TASK)?.takeIf { it.isNotEmpty() }
            val categorie = entry.arguments?.getString(AppRoutes.READER_CATEGORY)?.takeIf { it.isNotEmpty() }

            ReaderRoute(
                startVerse = entry.arguments?.getInt(AppRoutes.READER_VERSE)?.takeIf { it > 0 },
                session = if (debut != null && fin != null) {
                    when {
                        seance != null -> StudySession.Request(
                            range = Range(debut, fin),
                            sessionId = seance,
                        )
                        // La catégorie est relue par le **décodeur** posé à côté de l'encodeur, et
                        // non par une comparaison de chaînes écrite ici : une convention recopiée
                        // à deux endroits finit par diverger, et c'est la copie qu'on ne relit pas
                        // qui reste.
                        //
                        // Le repli sur `habitual` quand la clé est inconnue est délibéré, et il ne
                        // contredit pas le refus de `categoryOf` : perdre la tâche ferait perdre la
                        // validation, alors que la catégorie ne fait que préciser *quelle* révision
                        // c'est. `StudySession.validate` prend d'ailleurs `habitual` par défaut.
                        tache != null -> StudySession.forTask(
                            Review.ReviewTask(
                                start = debut,
                                end = fin,
                                id = tache,
                                category = Review.categoryOf(categorie) ?: ReviewCategory.HABITUAL,
                            ),
                        )
                        else -> null
                    }
                } else {
                    null
                },
                onClose = { navController.popBackStack() },
            )
        }
        // Le Quiz s'ouvre de trois façons : depuis l'accueil — rien à préciser —, depuis le bouton
        // « 🏆 Défier » d'une conversation, qui apporte **l'ami**, et, plus tard, depuis une
        // notification, qui apportera **le défi**. Les deux arguments sont facultatifs : `quiz`
        // seul reste une route valide, et c'est ce qui permet d'ouvrir l'écran sans savoir ce
        // qu'on vient y faire.
        //
        // C'est le motif, et non la route nue, qui est déclaré : sans lui la navigation ne
        // saurait pas distinguer « défier cet ami » de « ouvrir le Quiz », et la seconde
        // intention écraserait la première — le défaut muet que `readerRoute` a déjà connu.
        composable(
            route = AppRoutes.QUIZ_PATTERN,
            arguments = listOf(
                navArgument(AppRoutes.QUIZ_FRIEND) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(AppRoutes.QUIZ_CHALLENGE) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            // `takeIf { it.isNotEmpty() }` : un argument absent n'arrive pas en `null` mais en
            // chaîne vide, et une chaîne vide n'est pas un identifiant. La laisser passer ferait
            // chercher un ami qui n'existe pas — l'écran s'ouvrirait normalement, ce qui est le
            // propre d'un défaut muet.
            QuizScreen(
                friendId = entry.arguments?.getString(AppRoutes.QUIZ_FRIEND)?.takeIf { it.isNotEmpty() },
                challengeId = entry.arguments?.getString(AppRoutes.QUIZ_CHALLENGE)?.takeIf { it.isNotEmpty() },
                onClose = { navController.popBackStack() },
            )
        }

        composable(AppRoutes.PROFILE) { ProfileScreen(mode = ProfileMode.PROFILE) }
        composable(AppRoutes.SETTINGS) { ProfileScreen(mode = ProfileMode.SETTINGS) }
        composable(AppRoutes.APPEARANCE) { ProfileScreen(mode = ProfileMode.APPEARANCE) }
        // L'écran d'objectif a son propre fichier depuis qu'il est livré : il n'est plus une
        // section de `ProfileScreen`. La fermeture est fournie par la route, comme pour les autres
        // écrans d'outil — c'est ici qu'on sait où l'on retourne.
        composable(AppRoutes.GOAL) { GoalScreen(onClose = { navController.popBackStack() }) }
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
