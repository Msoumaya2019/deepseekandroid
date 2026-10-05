package com.msoumaya.deepseekandroid.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tient l'**écriture** d'un signet dans la route du lecteur.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * Les deux conditions sont réunies, et il faut les deux.
 *
 *  1. **Le comportement est hors de portée.** `ReaderRoute` est une fonction `@Composable` qui
 *     prend un `AppContainer` réel : la déclencher demanderait un hôte Compose **et** un
 *     conteneur complet — stockage, session, dépôts. Monter un conteneur entier pour vérifier
 *     qu'une ligne appelle `saveBookmark` serait disproportionné.
 *  2. **Sa disparition serait silencieuse.** Si la route cessait d'enregistrer, rien ne le
 *     dirait : `ReaderBookmarkWiringTest` resterait vert (il ne regarde que le lecteur), la
 *     compilation passe, et le seul symptôme est un signet qui ne se retrouve jamais. La notice
 *     annoncerait pourtant « Marque-page enregistré » — un mensonge que personne ne verrait.
 *
 * ## Le point que ce contrôle vise
 *
 * La page enregistrée est celle de la **source affichée**, et non un numéro nu. Les deux
 * découpages du projet ne placent pas les mêmes versets au même endroit — c'est mesuré, 56
 * versets sur 6 236 changent de page entre les deux. Un signet qui perdrait sa source se
 * rouvrirait donc à la mauvaise page, et rien ne l'annoncerait.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que la route **écrit** par la règle du domaine, sur l'état observé. Il ne prouve pas
 * que `saveBookmark` est juste : c'est `BookmarksTest`, dans `core:domain`.
 */
class ReaderRouteBookmarkTest {

    @Test
    fun `la route rapporte le verset a la regle du domaine`() {
        assertTrue(
            sourceDeLaRoute().contains("Bookmarks.saveBookmark("),
            "La route n'enregistre plus le signet par la règle du domaine : le signet serait " +
                "écrit autrement, ou pas du tout, et rien ne le dirait.",
        )
    }

    @Test
    fun `la route passe par le magasin de l'etat, et non par une copie`() {
        assertTrue(
            sourceDeLaRoute().contains("container.userState.mutate { state ->"),
            "Le signet n'est plus écrit dans le magasin de l'état : il serait perdu au premier " +
                "redémarrage, ou écrit dans une copie que personne ne relit.",
        )
    }

    @Test
    fun `le signet retient la page de la source affichee`() {
        // `source.persistedKey` et non un littéral : c'est la clé telle qu'elle est écrite dans
        // l'état synchronisé, et elle est lue sur le descripteur plutôt que recopiée.
        assertTrue(
            sourceDeLaRoute().contains("sourcePage = source.persistedKey to page,"),
            "Le signet ne retient plus la source de sa page : il se rouvrirait à la page d'un " +
                "autre découpage, sans que rien ne le signale.",
        )
    }

    @Test
    fun `un echec d'ecriture n'emporte pas le lecteur`() {
        assertTrue(
            sourceDeLaRoute().contains("runCatching {"),
            "L'écriture n'est plus protégée : un disque plein ferait tomber le lecteur, alors " +
                "que la lecture, elle, n'a besoin de rien.",
        )
    }

    @Test
    fun `le source lu est bien celui de la route`() {
        // Garde-fou sur le fichier lui-même : si le chemin résolu désignait autre chose, les
        // quatre contrôles ci-dessus chercheraient leurs chaînes dans le mauvais document — et
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
