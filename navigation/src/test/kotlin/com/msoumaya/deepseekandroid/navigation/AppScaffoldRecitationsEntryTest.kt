package com.msoumaya.deepseekandroid.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient **la sortie de partage du lecteur** et son arrivée dans la liste des récitations.
 *
 * ## Pourquoi ce contrôle existe
 *
 * L'original enregistre une récitation depuis le lecteur, puis **quitte l'écran** pour la liste des
 * récitations, où le choix d'ami s'ouvre **sur la ligne attendue** (`App.tsx`, `onShareRecitation`).
 * Le portage, lui, portait `share = null` : le geste « Partager » **disparaissait** de la barre au
 * lieu de mener quelque part. Le chemin existe désormais, et il traverse **quatre** pièces que
 * rien, à l'exécution, ne relie visiblement :
 *
 *  - le lecteur **signale** la récitation avant de sortir ;
 *  - la coquille retient l'identifiant ;
 *  - la sortie du lecteur l'emporte, et ouvre la liste **sur** lui ;
 *  - la liste le reçoit, et s'en sert pour ouvrir la ligne et son choix d'ami.
 *
 * ## Ce qui disparaîtrait sans un mot
 *
 *  - **l'identifiant retenu** : le lecteur sortirait, la liste s'ouvrirait, et le choix d'ami
 *    n'apparaîtrait jamais. Aucune erreur — juste un partage qui n'a pas eu lieu ;
 *  - **le motif à la place de la route nue** : l'argument serait ignoré, et le même défaut
 *    s'ensuivrait, avec en plus deux barres par-dessus un écran plein écran ;
 *  - **le lecteur qui cède la place** : le retour depuis la liste ramènerait sur le lecteur qu'on
 *    vient de quitter, ce que l'original ne fait jamais ;
 *  - **la valeur par défaut de l'argument** : ouvrir la liste depuis le tableau de bord — sans rien
 *    à partager — échouerait, et l'échec serait une exception de navigation.
 *
 * ## Ce qu'il ne prouve pas
 *
 * Il ne prouve pas que la navigation aboutit : il n'y a pas d'appareil ici. Les règles de route
 * sont mesurées par `AppRoutesRecitationsTest`, et l'arrivée de la ligne par
 * `RecitationsArrivalTest` et `RecitationsArrivalWiringTest`, dans `feature:recitations`.
 */
class AppScaffoldRecitationsEntryTest {

    @Test
    fun `la route des recitations est servie par son motif`() {
        // Servir la route **nue** laisserait l'argument sans effet : la navigation ignorerait la
        // partie `?partage=…`, et la liste s'ouvrirait sur elle-même — l'appelant annonçant un
        // partage et l'écran n'en faisant rien.
        val occurrences = sourceDeLaCoquille()
            .lines()
            .count { it.contains("route = AppRoutes.RECITATIONS_PATTERN,") }

        assertEquals(
            1,
            occurrences,
            "La route des récitations n'est plus servie par son motif ($occurrences fois) : la " +
                "récitation à partager n'atteindrait jamais la liste.",
        )
    }

    @Test
    fun `l'argument des recitations est facultatif`() {
        // Une route nue doit rester valide : c'est l'ouverture depuis le tableau de bord, où l'on
        // ne vient rien partager. Un argument requis ferait échouer cette ouverture — et l'échec
        // serait une exception de navigation, donc un plantage.
        val bloc = recitationsDeLaCoquille()

        assertTrue(
            bloc.contains("navArgument(AppRoutes.RECITATIONS_SHARE)"),
            "La récitation à partager n'est plus déclarée comme argument de la route : elle ne " +
                "pourrait plus voyager du lecteur jusqu'à la liste.",
        )
        assertTrue(
            bloc.contains("defaultValue = \"\""),
            "L'argument des récitations n'a plus de valeur par défaut : ouvrir la liste sans rien " +
                "partager échouerait, et l'échec serait un plantage.",
        )
    }

    @Test
    fun `la liste recoit la recitation a partager`() {
        // `takeIf { it.isNotEmpty() }` est la sentinelle : un argument absent n'arrive pas en
        // `null` mais en chaîne vide, et une chaîne vide n'est pas un identifiant. La laisser
        // passer ferait chercher une récitation qui n'existe pas — la liste s'ouvrirait
        // normalement, ce qui est le propre d'un défaut muet.
        val bloc = recitationsDeLaCoquille()

        assertTrue(
            bloc.contains("initialSharingId = aPartager,"),
            "La liste ne reçoit plus la récitation à partager : le geste « Partager » du lecteur " +
                "mènerait à la liste sans jamais ouvrir le choix d'ami.",
        )
        assertTrue(
            bloc.contains("?.getString(AppRoutes.RECITATIONS_SHARE)") &&
                bloc.contains("?.takeIf { it.isNotEmpty() }"),
            "La route ne lit plus son argument avec sa sentinelle : un argument absent passerait " +
                "pour un identifiant, et la liste chercherait une récitation qui n'existe pas.",
        )
    }

    @Test
    fun `le lecteur signale la recitation a partager`() {
        val bloc = blocApres("route = AppRoutes.READER_PATTERN,")

        assertTrue(
            bloc.contains("var aPartager by remember { mutableStateOf<String?>(null) }"),
            "La coquille ne retient plus la récitation à partager : le lecteur la signalerait " +
                "dans le vide, et la liste s'ouvrirait sans le choix d'ami.",
        )
        assertTrue(
            bloc.contains("onShareRecitation = { id -> aPartager = id },"),
            "Le lecteur ne reçoit plus de quoi signaler la récitation à partager : le geste " +
                "« Partager » de sa barre disparaîtrait — c'est la règle du dépôt, et elle est " +
                "tenue par `canShare`, jamais par une lambda vide.",
        )
    }

    @Test
    fun `le lecteur cede la place a la liste`() {
        // `setReader(null)` puis `setRecitationsOpen(true)` de l'original, dans cet ordre. Le
        // lecteur **quitte** la pile au lieu de rester sous la liste : sans cela, le retour depuis
        // la liste ramènerait sur l'écran qu'on vient de quitter.
        val bloc = blocApres("route = AppRoutes.READER_PATTERN,")

        assertTrue(
            bloc.contains("val cible = aPartager"),
            "La sortie du lecteur ne lit plus la récitation à partager : la liste ne saurait pas " +
                "quelle ligne ouvrir.",
        )
        assertTrue(
            bloc.contains("navController.navigate(AppRoutes.recitationsRoute(cible))"),
            "La sortie du lecteur n'ouvre plus la liste des récitations par `recitationsRoute` : " +
                "la route serait recollée à la main, et la divergence serait muette.",
        )
        assertTrue(
            bloc.contains("navController.popBackStack()"),
            "Le lecteur ne cède plus la place à la liste : il resterait sous elle, et le retour " +
                "ramènerait sur l'écran qu'on vient de quitter.",
        )
    }

    @Test
    fun `la route ne recoit pas un identifiant ecrit a la main`() {
        // Le motif, et non une route recollée : une route écrite en deux endroits finit par
        // diverger, et la divergence serait muette — la navigation n'échouerait pas, elle
        // ouvrirait la liste sans le choix d'ami.
        val bloc = recitationsDeLaCoquille()

        assertFalse(
            bloc.contains("\"recitations?partage=\""),
            "La coquille recolle la route des récitations à la main : elle doit passer par " +
                "`recitationsRoute`, seul endroit qui connaisse la forme de l'argument.",
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
