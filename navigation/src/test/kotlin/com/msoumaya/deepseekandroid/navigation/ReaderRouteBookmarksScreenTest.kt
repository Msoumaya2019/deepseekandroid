package com.msoumaya.deepseekandroid.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient le **branchement** de l'écran des marques-pages dans la route du lecteur.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * Les deux conditions sont réunies, et il faut les deux.
 *
 *  1. **Le comportement est hors de portée.** `ReaderRoute` est une fonction `@Composable` qui
 *     prend un `AppContainer` réel : la déclencher demanderait un hôte Compose **et** un
 *     conteneur complet. Monter un conteneur entier pour vérifier qu'une ligne ouvre une fenêtre
 *     serait disproportionné.
 *  2. **Sa disparition serait silencieuse.** Si la route cessait d'afficher l'écran, ou cessait
 *     de lui donner ses lignes, rien ne le dirait : `BookmarksScreenWiringTest` resterait vert
 *     (il ne regarde que l'écran), la compilation passe, et le seul symptôme est un bouton
 *     « Mes marques-pages » qui n'ouvre rien — ou une liste vide alors que des signets existent.
 *
 * ## Le point que ce contrôle vise, et c'est le même que pour le marquage
 *
 * La page de reprise suit le **découpage de la source affichée**. Les deux découpages du projet
 * ne placent pas les mêmes versets au même endroit — 56 versets sur 6 236 changent de page — donc
 * une reprise qui ignorerait la source ouvrirait **à côté** du verset enregistré, sans que rien
 * ne le signale : la page s'afficherait, elle serait simplement fausse.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que la route **branche** l'écran par les règles du domaine. Il ne prouve pas que
 * `Bookmarks.rows` est juste : c'est `BookmarksScreenRulesTest`, dans `core:domain`.
 */
class ReaderRouteBookmarksScreenTest {

    @Test
    fun `la route affiche l'ecran des marques-pages`() {
        // La condition est cherchée avec l'appel, et non l'appel seul : `BookmarksScreen(` seul
        // resterait vrai sous un `if (false)`, et le contrôle ne mesurerait alors rien.
        assertTrue(
            sourceDeLaRoute().contains("if (bookmarksOpen) {\n        BookmarksScreen("),
            "La route n'affiche plus l'écran des marques-pages : le bouton « Mes marques-pages » " +
                "n'ouvrirait rien, et rien d'autre ne le dirait.",
        )
    }

    @Test
    fun `ouvrir la liste oublie le verset en attente`() {
        // Sans cette remise à zéro, reprendre deux fois de suite le **même** signet ne le
        // sélectionnerait qu'une fois : la clé de l'effet n'aurait pas changé, et le lecteur
        // ouvrirait la bonne page sans désigner le verset. Un défaut qu'on ne voit qu'en
        // recommençant, donc jamais pendant un essai rapide.
        assertTrue(
            sourceDeLaRoute().contains("onOpenBookmarks = {\n            pendingVerse = null"),
            "L'ouverture de la liste ne remet plus le verset en attente à zéro : une reprise " +
                "répétée du même signet cesserait de désigner son verset.",
        )
    }

    @Test
    fun `la route donne a l'ecran les lignes de la regle du domaine`() {
        assertTrue(
            sourceDeLaRoute().contains("Bookmarks.rows(it, source)"),
            "Les lignes ne viennent plus de la règle du domaine, ou plus de la **source " +
                "affichée** : la page de chaque signet cesserait de suivre le découpage affiché.",
        )
    }

    @Test
    fun `la reprise ouvre la page du decoupage affiche`() {
        val source = sourceDeLaRoute()
        assertTrue(
            source.contains("Bookmarks.pageFor(it, source, id)"),
            "La reprise ne calcule plus sa page par la règle du domaine : elle sauterait à la " +
                "page d'un autre découpage, et le verset affiché ne serait pas celui du signet.",
        )
        assertTrue(
            source.contains("Bookmarks.useBookmark(state, id, pageOverride = target)"),
            "La reprise n'enregistre plus la page retenue : « Dernière reprise » pointerait sur " +
                "la page par défaut du verset, et non sur celle où l'on a repris.",
        )
    }

    @Test
    fun `la suppression passe par la regle du domaine`() {
        assertTrue(
            sourceDeLaRoute().contains("Bookmarks.deleteBookmark(state, id)"),
            "La suppression ne passe plus par la règle du domaine : le signet serait supprimé " +
                "autrement — ou pas du tout —, et la synchronisation ne le saurait pas.",
        )
    }

    @Test
    fun `le verset repris est rappele au lecteur`() {
        assertTrue(
            sourceDeLaRoute().contains("initialVerse = pendingVerse,"),
            "Le verset repris n'est plus transmis au lecteur : la reprise ouvrirait la bonne " +
                "page sans désigner le verset qu'on venait chercher.",
        )
    }

    @Test
    fun `l'ecran est pose par-dessus le lecteur, qui reste monte`() {
        // L'écran est appelé **après** `ReaderScreen`, et sans `return` entre les deux : c'est
        // ce qui en fait une fenêtre par-dessus un lecteur vivant, et non un remplacement qui
        // le démonterait — en libérant au passage le lecteur audio, donc en arrêtant l'écoute.
        val entre = sourceDeLaRoute()
            .substringAfter("ReaderScreen(")
            .substringBefore("BookmarksScreen(")
        assertFalse(
            entre.contains("return"),
            "La route quitte avant d'afficher l'écran des marques-pages : le lecteur est alors " +
                "démonté, et l'écoute en cours s'arrête parce qu'on consulte ses signets.",
        )
    }

    @Test
    fun `le source lu est bien celui de la route`() {
        // Garde-fou sur le fichier lui-même : si le chemin résolu désignait autre chose, les
        // contrôles ci-dessus chercheraient leurs chaînes dans le mauvais document — et
        // échoueraient, ce qui est le bon comportement, mais pour une raison trompeuse.
        assertTrue(
            sourceDeLaRoute().contains("fun ReaderRoute("),
            "Le fichier lu ne déclare pas `fun ReaderRoute(` : le chemin résolu ne désigne pas " +
                "la route du lecteur.",
        )
    }

    /**
     * Le source de la route du lecteur.
     *
     * Deux chemins sont essayés, et non un seul : la tâche `Test` de Gradle s'exécute dans le
     * dossier **du module**, alors qu'un contrôle joué à la main depuis la racine du dépôt part
     * de là.
     */
    private fun sourceDeLaRoute(): String {
        val relatif = "src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt"
        val candidats = listOf(File(relatif), File("navigation/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "ReaderRoute.kt introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }
}
