package com.msoumaya.deepseekandroid.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tient le **calcul des marques** dans la route du lecteur.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * Les deux conditions sont réunies, et il faut les deux.
 *
 *  1. **Le comportement est hors de portée.** `ReaderRoute` est une fonction `@Composable` qui
 *     prend un `AppContainer` réel : la déclencher demanderait un hôte Compose **et** un
 *     conteneur complet — stockage, session, dépôts. Le projet n'a aucun outillage de test
 *     d'interface, et monter un conteneur entier pour lire deux ensembles serait disproportionné.
 *  2. **Sa disparition serait silencieuse.** Le lecteur reçoit les deux ensembles avec, pour
 *     valeur par défaut, l'ensemble **vide**. Si la route cessait de les calculer ou de les
 *     passer, rien ne le dirait : la compilation passe, `ReaderScreenMarksTest` reste vert (il
 *     ne regarde que le lecteur), et la page perd ses marques sans qu'aucun test ne rougisse.
 *
 * ## Pourquoi ce test vit dans le module `navigation`
 *
 * Parce qu'il lit le source de `ReaderRoute.kt`. Placé ailleurs, il ne serait **pas rejoué**
 * quand ce source change — la tâche `Test` de Gradle ne suit que les entrées de son propre
 * module — et resterait vert par oubli. C'est la raison pour laquelle ce module a reçu son
 * premier `src/test`.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que la route **observe** l'état et lui applique les deux règles du domaine. Il ne
 * prouve pas que ces règles sont justes : c'est `ReaderMarksTest`, dans `core:domain`.
 */
class ReaderRouteMarksTest {

    @Test
    fun `la route observe l'etat du compte`() {
        assertTrue(
            sourceDeLaRoute().contains(
                "container.userState.state.collectAsStateWithLifecycle(initialValue = null)",
            ),
            "La route n'observe plus l'état du compte : un signet posé ailleurs ne se verrait " +
                "qu'en rouvrant le lecteur, et la page ne porterait plus aucune marque.",
        )
    }

    @Test
    fun `la route confie les deux regles au domaine`() {
        val source = sourceDeLaRoute()
        assertTrue(
            source.contains("Bookmarks.bookmarkedIds(it)"),
            "Les signets ne sont plus calculés par la règle du domaine : la suppression logique " +
                "risque d'être oubliée, et un signet supprimé reviendrait colorer la page.",
        )
        assertTrue(
            source.contains("Review.difficultIds(it)"),
            "Les versets difficiles ne sont plus calculés par la règle du domaine : le marqueur " +
                "du professeur risque d'être oublié.",
        )
    }

    @Test
    fun `la route transmet les deux ensembles au lecteur`() {
        val source = sourceDeLaRoute()
        assertTrue(
            source.contains("bookmarkIds = bookmarkIds,"),
            "Les signets sont calculés mais ne sont plus passés au lecteur.",
        )
        assertTrue(
            source.contains("difficultIds = difficultIds,"),
            "Les versets difficiles sont calculés mais ne sont plus passés au lecteur.",
        )
    }

    @Test
    fun `le source lu est bien celui de la route`() {
        // Garde-fou sur le fichier lui-même : si le chemin résolu désignait autre chose, les
        // trois contrôles ci-dessus chercheraient leurs chaînes dans le mauvais document — et
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
