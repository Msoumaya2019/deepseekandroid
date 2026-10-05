package com.msoumaya.deepseekandroid.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient le **branchement du tableau de bord des révisions** dans la coquille.
 *
 * ## Pourquoi ce contrôle existe
 *
 * Le tableau de bord est un écran **plein écran** : ni onglet, ni bouton dans la barre
 * supérieure. Ses seules portes sont des appels depuis l'accueil et depuis le programme, et sa
 * seule sortie est le rappel `onClose` qu'il reçoit. Si l'un de ces branchements disparaissait —
 * une ligne retirée en refactorant la coquille, un `ProgramScreen()` remis sans argument —, la
 * compilation passerait, tous les autres tests resteraient verts, et le tableau de bord
 * deviendrait **inatteignable** : la carte de révision du programme serait un bouton mort, et
 * personne ne le saurait.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que la route est servie, que l'écran reçoit de quoi se fermer et d'ouvrir une tâche,
 * et que le lecteur sait reconstruire les **trois** formes de tâche depuis ses arguments. Il ne
 * prouve pas que la navigation aboutit : c'est `AppRoutesStudyTest`, qui mesure le transport sur
 * lequel elle s'appuie.
 *
 * ## Pourquoi il lit le source
 *
 * Un test de navigation complet demanderait un hôte de test Compose et un écran réel adossé au
 * conteneur applicatif — donc le référentiel coranique, la session, le stockage. Ce contrôle-ci
 * est la partie qui tient sans rien de tout cela : ce qui est **branché**, et sur quoi.
 */
class AppScaffoldReviewEntryTest {

    @Test
    fun `la route du tableau de bord est servie`() {
        // Une route nommée mais non servie ferait planter la première navigation. C'est
        // exactement ce que la coquille s'interdit, et la raison pour laquelle `DAILY`,
        // `RECITATIONS` et `ADMIN` n'y figurent pas.
        assertTrue(
            sourceDeLaCoquille().contains("composable(AppRoutes.REVIEW) {"),
            "La route du tableau de bord n'est plus servie : la première navigation vers elle " +
                "planterait, et la carte de révision du programme deviendrait un bouton mort.",
        )
    }

    @Test
    fun `le tableau de bord recoit de quoi se fermer et d'ouvrir une tache`() {
        val tableau = tableauDeBordDeLaCoquille()

        assertTrue(
            tableau.contains("onClose = { navController.popBackStack() },"),
            "Le tableau de bord ne reçoit plus de quoi se fermer : plein écran, sans barre " +
                "supérieure ni barre basse, il n'aurait plus aucun moyen de revenir en arrière.",
        )
        assertTrue(
            tableau.contains("onOpenStudy = { request -> navController.navigate(AppRoutes.studyRoute(request)) },"),
            "Le tableau de bord n'ouvre plus sa tâche du jour : le bouton de la carte du jour " +
                "serait mort, et la révision ne serait plus validable.",
        )
        assertTrue(
            tableau.contains("onStatistics = { navController.navigateToTab(AppDestination.PROGRESS) },"),
            "Le tableau de bord n'ouvre plus les statistiques.",
        )
    }

    @Test
    fun `les statistiques basculent vers l'onglet au lieu de l'empiler`() {
        // `navigate` empilerait un second exemplaire de l'onglet par-dessus un écran plein écran :
        // le retour ramènerait alors sur le tableau de bord, et l'onglet ne serait plus un onglet.
        val tableau = tableauDeBordDeLaCoquille()

        assertTrue(
            tableau.contains("navigateToTab(AppDestination.PROGRESS)"),
            "Les statistiques ne basculent plus vers l'onglet « Progrès ».",
        )
        assertFalse(
            tableau.contains("navigate(AppDestination.PROGRESS.route)"),
            "Les statistiques empilent l'onglet au lieu d'y basculer : le retour ramènerait sur " +
                "le tableau de bord, alors qu'un onglet ne s'empile pas.",
        )
    }

    @Test
    fun `les recitations restent non branchees, et c'est delibere`() {
        // La route `RECITATIONS` est nommée mais son écran n'existe pas encore : `SocialScreen`
        // est un panneau de phase. La déclarer ferait planter la première navigation — c'est la
        // règle que la coquille s'impose, et ce contrôle l'épingle pour qu'on ne la « corrige »
        // pas en branchant un rappel qui planterait.
        assertFalse(
            tableauDeBordDeLaCoquille().contains("onRecitations ="),
            "Les récitations sont branchées alors que leur route n'est pas servie : le premier " +
                "appui ferait planter l'application.",
        )
    }

    @Test
    fun `l'accueil et le programme menent au tableau de bord`() {
        // Les deux rappels étaient laissés à leur valeur par défaut, donc deux boutons sans
        // effet : le rappel de révision de l'accueil, et la carte du programme quand aucun
        // passage n'est dû. Un rappel déclaré mais jamais branché ne se voit nulle part.
        assertTrue(
            accueilDeLaCoquille().contains("onOpenReviews = { navController.navigate(AppRoutes.REVIEW)"),
            "Le rappel de révision de l'accueil ne mène plus au tableau de bord.",
        )
        assertTrue(
            programmeDeLaCoquille().contains("onOpenReviews = { navController.navigate(AppRoutes.REVIEW)"),
            "La carte de révision du programme ne mène plus au tableau de bord : elle serait un " +
                "bouton mort les jours où aucun passage n'est dû.",
        )
    }

    @Test
    fun `les rappels de l'accueil qui etaient morts sont branches`() {
        // Ces deux rappels sont **invoqués** par l'écran — le bandeau « Prochain objectif » et la
        // carte de progression — mais n'étaient pas fournis par la coquille. Ils basculent vers
        // un onglet, comme la barre basse.
        val accueil = accueilDeLaCoquille()

        assertTrue(
            accueil.contains("onOpenProgram = { navController.navigateToTab(AppDestination.PROGRAM) },"),
            "Le bandeau « Prochain objectif » de l'accueil ne mène plus au programme.",
        )
        assertTrue(
            accueil.contains("onOpenProgress = { navController.navigateToTab(AppDestination.PROGRESS) },"),
            "La carte de progression de l'accueil ne mène plus à l'onglet « Progrès ».",
        )
    }

    @Test
    fun `les trois portes d'une tache passent par la meme fonction`() {
        // C'est la garantie de fond : une seule fonction transporte une tâche, donc les trois
        // écrans ne peuvent pas diverger. Écrire la route à la main chez un appelant a déjà
        // produit un défaut muet — une révision partie en lecture libre, donc jamais validée.
        val source = sourceDeLaCoquille()

        assertEquals(
            3,
            Regex("AppRoutes\\.studyRoute\\(request\\)").findAll(source).count(),
            "Le nombre d'appelants de `studyRoute` a changé. Les trois écrans qui ouvrent une " +
                "tâche — accueil, programme, tableau de bord — doivent passer par elle : une " +
                "route recollée à la main finit par perdre l'identité ou les bornes de la tâche, " +
                "et la perte est muette.",
        )
    }

    @Test
    fun `le lecteur declare les deux arguments d'une tache`() {
        // Sans ces deux arguments, la navigation ignorerait `tache` et `categorie` : la route
        // s'ouvrirait, et la tâche ne serait jamais reconstruite — donc une révision redeviendrait
        // une lecture libre, sans que rien ne le dise.
        val source = sourceDeLaCoquille()

        assertTrue(
            source.contains("navArgument(AppRoutes.READER_TASK)"),
            "L'argument `tache` n'est plus déclaré : la révision servie par le tableau de bord " +
                "arriverait sans son identité.",
        )
        assertTrue(
            source.contains("navArgument(AppRoutes.READER_CATEGORY)"),
            "L'argument `categorie` n'est plus déclaré : une consolidation ne serait plus " +
                "reconnue comme telle, et l'étape des trois jours ne s'ouvrirait jamais.",
        )
    }

    @Test
    fun `le lecteur reconstruit la tache et relit sa categorie par le decodeur`() {
        val source = sourceDeLaCoquille()

        assertTrue(
            source.contains("session = if (debut != null && fin != null) {"),
            "Le lecteur ne vérifie plus que la tâche est complète avant de la reconstruire : des " +
                "bornes manquantes donneraient une plage inventée.",
        )
        assertTrue(
            source.contains("Review.ReviewTask("),
            "Le lecteur ne reconstruit plus la tâche de révision.",
        )
        assertTrue(
            source.contains("Review.categoryOf(categorie)"),
            "Le lecteur ne relit plus la catégorie par le décodeur posé à côté de l'encodeur : " +
                "une comparaison de chaînes recopiée ici finirait par diverger de la clé écrite.",
        )
    }
}
