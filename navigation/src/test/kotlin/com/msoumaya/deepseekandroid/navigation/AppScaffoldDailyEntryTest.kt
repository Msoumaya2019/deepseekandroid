package com.msoumaya.deepseekandroid.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient **le branchement de la liste des contenus du jour** dans la coquille.
 *
 * ## Pourquoi ce contrôle existe
 *
 * La liste est un écran **plein écran** : ni onglet, ni bouton dans la barre supérieure. Sa seule
 * porte est le carrousel de l'accueil, et sa seule sortie le rappel `onClose` qu'elle reçoit. Or
 * `onOpenDaily` était resté à sa valeur par défaut — donc un bouton **inerte** — depuis que le
 * carrousel existe : l'écran de liste n'était pas porté, et un geste sans destination vaut mieux
 * qu'un geste qui mène ailleurs. L'écran existe maintenant, et les deux moitiés doivent être là —
 * la route **servie** et le rappel **branché**.
 *
 * ## Ce qui disparaîtrait sans un mot
 *
 *  - **le motif à la place de la route nue** : l'argument serait ignoré, et le carrousel ouvrirait
 *    la liste **sans** la fiche qu'il vise — le geste mènerait quelque part, mais pas là où il
 *    promettait ;
 *  - **la valeur par défaut de l'argument** : ouvrir la liste sans rien à ouvrir échouerait, et
 *    l'échec serait une exception de navigation, donc un plantage ;
 *  - **le rappel branché** : le carrousel resterait un bouton mort, et rien à l'écran ne le dirait ;
 *  - **la sentinelle `takeIf { it.isNotEmpty() }`** : un argument absent n'arrive pas en `null`
 *    mais en chaîne vide, et une chaîne vide n'est pas un identifiant.
 *
 * ## Ce qu'il ne prouve pas
 *
 * Il ne prouve pas que la navigation aboutit : il n'y a pas d'appareil ici. Les règles de route
 * sont mesurées par [AppRoutesDailyTest], et le rendu de l'écran par les contrôles de
 * `feature:dailycontents`.
 */
class AppScaffoldDailyEntryTest {

    @Test
    fun `la route des contenus est servie par son motif`() {
        // Servir la route **nue** laisserait l'argument sans effet : la navigation ignorerait la
        // partie `?contenu=…`, et la liste s'ouvrirait sur elle-même — l'appelant annonçant une
        // fiche et l'écran n'en ouvrant aucune.
        val occurrences = sourceDeLaCoquille()
            .lines()
            .count { it.contains("route = AppRoutes.DAILY_PATTERN,") }

        assertEquals(
            1,
            occurrences,
            "La route des contenus du jour n'est plus servie par son motif ($occurrences fois) : " +
                "le contenu visé par le carrousel n'atteindrait jamais la liste.",
        )
    }

    @Test
    fun `l'argument des contenus est facultatif`() {
        // Une route nue doit rester valide : c'est l'ouverture de la liste sans rien ouvrir. Un
        // argument requis ferait échouer cette ouverture — et l'échec serait un plantage.
        val bloc = quotidienDeLaCoquille()

        assertTrue(
            bloc.contains("navArgument(AppRoutes.DAILY_CONTENT)"),
            "Le contenu à ouvrir n'est plus déclaré comme argument de la route : il ne pourrait " +
                "plus voyager du carrousel jusqu'à la liste.",
        )
        assertTrue(
            bloc.contains("defaultValue = \"\""),
            "L'argument des contenus n'a plus de valeur par défaut : ouvrir la liste sans rien " +
                "ouvrir échouerait, et l'échec serait un plantage.",
        )
    }

    @Test
    fun `la liste recoit le contenu a ouvrir`() {
        // `takeIf { it.isNotEmpty() }` est la sentinelle : un argument absent n'arrive pas en
        // `null` mais en chaîne vide, et une chaîne vide n'est pas un identifiant. La laisser
        // passer ferait chercher un contenu qui n'existe pas — l'écran s'ouvrirait normalement, ce
        // qui est le propre d'un défaut muet.
        val bloc = quotidienDeLaCoquille()

        assertTrue(
            bloc.contains("initialId = entry.arguments"),
            "La liste ne reçoit plus le contenu à ouvrir : le geste du carrousel ouvrirait la " +
                "liste sans jamais montrer la fiche visée.",
        )
        assertTrue(
            bloc.contains("?.getString(AppRoutes.DAILY_CONTENT)") &&
                bloc.contains("?.takeIf { it.isNotEmpty() }"),
            "La route ne lit plus son argument avec sa sentinelle : un argument absent passerait " +
                "pour un identifiant, et l'écran chercherait un contenu qui n'existe pas.",
        )
    }

    @Test
    fun `la liste est rendue et peut se fermer`() {
        val bloc = quotidienDeLaCoquille()

        assertTrue(
            bloc.contains("DailyContentsScreen(") &&
                bloc.contains("onClose = { navController.popBackStack() },"),
            "La route est servie mais ne rend plus l'écran, ou ne le laisse plus se fermer : " +
                "l'appui ouvrirait une page vide ou sans retour, et « la route est servie » ne " +
                "prouverait rien à lui seul.",
        )
    }

    @Test
    fun `le carrousel de l'accueil ouvre la liste`() {
        // C'était le dernier rappel inerte de l'accueil : `onOpenDaily` valait `{}`, donc les
        // vignettes du carrousel ne menaient nulle part. Un rappel déclaré mais jamais branché ne
        // se voit nulle part — la compilation passe, et la vignette devient un bouton mort.
        val accueil = accueilDeLaCoquille()

        assertTrue(
            accueil.contains("onOpenDaily = { id ->"),
            "Le carrousel des contenus du jour ne mène plus nulle part : ses vignettes seraient " +
                "des boutons morts, et rien d'autre ne le dirait.",
        )
        assertTrue(
            accueil.contains("navController.navigate(AppRoutes.dailyRoute(id))"),
            "Le carrousel n'ouvre plus la liste par `dailyRoute` : la route serait recollée à la " +
                "main, et la divergence serait muette — elle ouvrirait la liste au lieu de la " +
                "fiche visée.",
        )
    }

    @Test
    fun `la route ne recoit pas un identifiant ecrit a la main`() {
        // Le motif, et non une route recollée : une route écrite en deux endroits finit par
        // diverger, et la divergence serait muette.
        val bloc = quotidienDeLaCoquille()

        assertFalse(
            bloc.contains("\"quotidien?contenu=\""),
            "La coquille recolle la route des contenus à la main : elle doit passer par " +
                "`dailyRoute`, seul endroit qui connaisse la forme de l'argument.",
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
