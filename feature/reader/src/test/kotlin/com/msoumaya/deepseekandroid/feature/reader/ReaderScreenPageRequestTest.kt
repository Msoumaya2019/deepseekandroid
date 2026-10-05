package com.msoumaya.deepseekandroid.feature.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient le **suivi d'une page demandée de l'extérieur** par le lecteur.
 *
 * ## Le défaut que ce contrôle empêche
 *
 * `initialPage` n'est lu qu'à la **première** composition : `pageState` est un
 * `rememberSaveable`, et la lambda qui l'initialise ne se rejoue pas quand le paramètre change.
 * Un appelant qui fait `page = target` — la reprise d'un signet, ou l'adoption de la page
 * mémorisée — croit donc avoir tourné la page, et l'écran continue d'afficher l'ancienne.
 *
 * Ce qui rend le défaut grave n'est pas l'immobilité, c'est ce qu'elle fait écrire : l'appelant
 * tient la page qu'il **croit** affichée, et c'est celle-là qu'il enregistre en refermant le
 * lecteur. La mémoire du lecteur consignerait donc une position que personne n'a vue, et la
 * carte « Continuer » de l'accueil ramènerait à cette page inexistante — un défaut qui n'aurait
 * l'air de rien, puisque la page existe, et qu'elle est simplement fausse.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * `ReaderScreen` est une fonction `@Composable` : la déclencher demande un hôte Compose, un
 * appareil ou un émulateur, et le projet n'a aucun outillage de test d'interface.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que le suivi est **écrit**, qu'il est gardé contre le premier rendu, et qu'il laisse
 * le verset désigné en place. Il ne prouve pas que la page demandée est la bonne : c'est
 * `ReaderMemoryTest`, dans `core:domain`, qui l'éprouve sur le référentiel entier.
 */
class ReaderScreenPageRequestTest {

    @Test
    fun `le lecteur suit une page demandee de l'exterieur`() {
        assertTrue(
            blocDuSuivi().contains("pageState.intValue = initialPage.coerceIn(1, totalPages)"),
            "Le lecteur ne suit plus une page demandée de l'extérieur : l'appelant croirait à " +
                "une page que l'écran n'affiche pas, et enregistrerait cette position-là.",
        )
    }

    @Test
    fun `le suivi de page ne rejoue pas au premier rendu`() {
        // Sans la garde, l'effet referait au premier rendu ce qui vient d'être fait à
        // l'initialisation de `pageState` — et remettrait le zoom à zéro sur une page qu'on
        // vient d'ouvrir, ce qui se voit à l'ouverture d'une reprise de signet.
        assertTrue(
            blocDuSuivi().contains("if (initialPage != pageState.intValue) {"),
            "Le suivi de page n'est plus gardé contre le premier rendu : il rejouerait à " +
                "l'ouverture ce que l'initialisation vient de faire.",
        )
    }

    @Test
    fun `le suivi de page ne touche pas au verset designe`() {
        // C'est l'exception à `goToPage`, et elle doit rester une exception : une page demandée
        // de l'extérieur vient avec le verset qu'on allait chercher — une reprise de signet —
        // donc effacer le verset ici détruirait la reprise entière.
        assertFalse(
            blocDuSuivi().contains("verseState"),
            "Le suivi de page efface le verset désigné : une reprise de signet ouvrirait la " +
                "bonne page sans la fiche du verset qu'on venait chercher.",
        )
    }

    @Test
    fun `le source lu est bien celui du lecteur`() {
        assertTrue(
            sourceDuLecteur().contains("fun ReaderScreen("),
            "Le fichier lu ne déclare pas `fun ReaderScreen(` : le chemin résolu ne désigne pas " +
                "le lecteur.",
        )
    }

    /**
     * L'effet qui répond à une page demandée de l'extérieur, et lui seul.
     *
     * La borne est accompagnée de son propre contrôle : `substringAfter` **sans repli** rend la
     * chaîne entière quand le délimiteur est absent — une borne muette rendrait tout le fichier,
     * et `verseState` y est déclaré, donc le contrôle du verset échouerait pour une raison qui
     * n'a rien à voir avec ce qu'il mesure.
     */
    private fun blocDuSuivi(): String {
        val source = sourceDuLecteur()
        val marqueur = "LaunchedEffect(initialPage) {"
        assertTrue(
            source.contains(marqueur),
            "Le lecteur n'a plus d'effet sur `initialPage` : une page demandée de l'extérieur " +
                "ne serait plus affichée.",
        )
        val bloc = source.substringAfter(marqueur).substringBefore("\n    }")
        assertTrue(
            bloc.length < source.length,
            "La borne du suivi de page n'a pas mordu : le bloc lu est le fichier entier.",
        )
        return bloc
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
