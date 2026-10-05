package com.msoumaya.deepseekandroid.feature.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tient la **reprise d'un signet** telle que le lecteur la reçoit.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * Les deux conditions sont réunies, et il faut les deux.
 *
 *  1. **Le comportement est hors de portée.** `ReaderScreen` est une fonction `@Composable` :
 *     la déclencher demande un hôte Compose, un appareil ou un émulateur, et le projet n'a
 *     aucun outillage de test d'interface.
 *  2. **Sa disparition serait silencieuse.** L'écran des marques-pages, lui, est tenu par
 *     `BookmarksScreenWiringTest` ; la route qui lui donne ses lignes est tenue par
 *     `ReaderRouteBookmarksScreenTest`. Entre les deux, personne ne regarde ce que le
 *     **lecteur** fait du verset qu'on lui désigne. Or l'effet peut disparaître sans un mot :
 *     la page s'ouvre, elle est la bonne, et le verset qu'on venait chercher n'est
 *     simplement plus désigné.
 *
 * ## Les deux règles tenues ici, et elles viennent du source
 *
 * Le `onResume` de `App.tsx` fait trois choses, dans cet ordre : il enregistre la reprise,
 * il **désigne le verset** (`setSelectedVerse(id)`), et il **referme le panneau** qu'on avait
 * quitté (`setSessionPanel(null)`). Le portage confie les deux dernières au lecteur, qui seul
 * possède la fiche et le panneau ; les deux sont donc vérifiées ici, sur son source.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que **l'effet est écrit**. Il ne prouve pas que la page de reprise est la bonne :
 * c'est `BookmarksScreenRulesTest`, dans `core:domain`, qui l'éprouve sur les vraies données.
 */
class ReaderScreenResumeTest {

    @Test
    fun `le lecteur declare le verset a designer`() {
        // Sans le paramètre, la route ne compilerait plus — c'est le compilateur qui le
        // tiendrait. Mais le paramètre **et** son usage peuvent disparaître ensemble, et c'est
        // alors la reprise entière qui perd son verset sans que rien ne rougisse.
        assertTrue(
            sourceDuLecteur().contains("initialVerse: Int? = null,"),
            "Le lecteur n'accepte plus de verset à désigner : une reprise de signet ouvrirait " +
                "la bonne page sans désigner le verset qu'on venait chercher.",
        )
    }

    @Test
    fun `la reprise designe le verset demande`() {
        assertTrue(
            blocDeLaReprise().contains("verseState.value = initialVerse"),
            "La reprise n'enregistre plus le verset demandé : la page s'ouvrirait, et la fiche " +
                "du verset — donc la traduction et la marque — n'apparaîtrait pas.",
        )
    }

    @Test
    fun `la reprise referme le panneau qu'on avait quitte`() {
        // C'est le `setSessionPanel(null)` du source. Le lecteur n'étant **pas** démonté quand
        // on consulte ses signets, le panneau qu'on avait ouvert avant de partir survivrait à
        // l'aller-retour : on reviendrait sur une feuille que personne n'a demandée.
        assertTrue(
            blocDeLaReprise().contains("panel = ReaderPanel.NONE"),
            "La reprise ne referme plus le panneau : en revenant de la liste, la feuille quittée " +
                "réapparaîtrait par-dessus la page, sans que personne ne l'ait demandée.",
        )
    }

    @Test
    fun `le source lu est bien celui du lecteur`() {
        // Garde-fou sur le fichier lui-même : si le chemin résolu désignait autre chose, les
        // contrôles ci-dessus chercheraient leurs chaînes dans le mauvais document — et
        // échoueraient, ce qui est le bon comportement, mais pour une raison trompeuse.
        assertTrue(
            sourceDuLecteur().contains("fun ReaderScreen("),
            "Le fichier lu ne déclare pas `fun ReaderScreen(` : le chemin résolu ne désigne pas " +
                "le lecteur.",
        )
    }

    /**
     * L'effet qui répond à une reprise de signet, et lui seul.
     *
     * **Borné**, et non cherché dans tout le fichier : `panel = ReaderPanel.NONE` vit aussi
     * ailleurs — au moins dans la fermeture du panneau des actions du verset. Une recherche
     * sans borne resterait donc verte si cet effet perdait sa propre ligne, et le contrôle ne
     * mesurerait rien.
     *
     * La borne est accompagnée de son propre contrôle : `substringAfter` **sans repli** rend la
     * chaîne entière quand le délimiteur est absent — une borne muette rendrait donc tout le
     * fichier, et l'assertion redeviendrait vraie pour la mauvaise raison.
     */
    private fun blocDeLaReprise(): String {
        val source = sourceDuLecteur()
        val marqueur = "LaunchedEffect(initialVerse) {"
        assertTrue(
            source.contains(marqueur),
            "Le lecteur n'a plus d'effet sur `initialVerse` : le verset d'une reprise ne serait " +
                "plus désigné, et le panneau quitté resterait ouvert.",
        )
        return source.substringAfter(marqueur).substringBefore("\n    }")
    }

    /**
     * Le source du lecteur.
     *
     * Deux chemins sont essayés, et non un seul : la tâche `Test` de Gradle s'exécute dans le
     * dossier **du module**, alors qu'un contrôle joué à la main depuis la racine du dépôt part
     * de là.
     */
    private fun sourceDuLecteur(): String {
        val relatif = "src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt"
        val candidats = listOf(File(relatif), File("feature/reader/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "ReaderScreen.kt introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }
}
