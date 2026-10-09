package com.msoumaya.deepseekandroid.feature.home

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient le **branchement de l'écran de signalement**.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * `ProblemReportCard` et `ProblemReportSheet` sont des fonctions `@Composable` : les déclencher
 * demande un hôte Compose, un appareil ou un émulateur, et le projet n'a aucun outillage de test
 * d'interface. Le branchement n'est donc atteignable par aucun test de comportement — ce n'est pas
 * un raccourci, c'est le seul moyen. Les décisions, elles, sont éprouvées pour de vrai dans
 * `ProblemReportRendererTest` : ce fichier ne surveille que le câblage.
 *
 * ## Ce qui disparaîtrait sans un mot
 *
 * Cinq choses, et chacune laisserait l'application parfaitement fonctionnelle en apparence :
 *
 *  - **la composition de la carte** : `ProblemReportCard()` n'a aucun paramètre obligatoire, donc
 *    retirer l'appel compile, l'accueil s'affiche, et le signalement n'a plus **aucune** porte.
 *    C'est le défaut que ce fichier existe pour attraper ;
 *  - **la remise à zéro à l'ouverture** : le dépôt garde `done` et `notice`, et la feuille ne
 *    renaît pas avec eux. Sans `repository.reset()`, une feuille rouverte afficherait la
 *    confirmation du geste **précédent** — et personne n'écrirait le suivant ;
 *  - **la borne du champ** : un `maxLength = 500` écrit dans l'écran compilerait et marcherait,
 *    jusqu'au jour où le domaine changerait de borne. Le champ laisserait alors taper 500
 *    caractères sur une règle qui en accepte 400 ;
 *  - **l'observation du dépôt** : sans elle, `busy` et `done` resteraient faux pour toujours — le
 *    bouton ne dirait jamais « Envoi… », et la confirmation ne s'afficherait jamais ;
 *  - **le passage par la capacité de capture** : appeler directement un sélecteur d'images dans
 *    l'écran compilerait, mais l'écran cesserait d'être éprouvable sans appareil.
 *
 * ## Ce qu'il ne prouve pas
 *
 * Ni la mise en page, ni les mots, ni le geste du sélecteur : les mots sont mesurés dans
 * `core:domain/ProblemReportsTest`, et le sélecteur demande un appareil.
 */
class ProblemReportScreenWiringTest {

    @Test
    fun `l'accueil compose la carte de signalement`() {
        assertTrue(
            sourceDeLAccueil().contains("ProblemReportCard()"),
            "L'accueil ne compose plus la carte de signalement : le signalement n'aurait plus " +
                "aucune porte, et rien ne le dirait.",
        )
    }

    @Test
    fun `la carte est composee apres le bandeau de la semaine`() {
        // C'est sa place dans l'original (`MainScreens.tsx`, `<ProblemReportCard/>` en dernier
        // enfant de l'accueil). La remonter en tête mettrait une porte vers l'administrateur
        // devant le Coran, le programme du jour et la progression.
        val source = sourceDeLAccueil()
        val semaine = source.indexOf("WeekCard(week = state.week)")
        val carte = source.indexOf("ProblemReportCard()")

        assertTrue(semaine >= 0, "le bandeau de la semaine a disparu de l'accueil")
        assertTrue(carte > semaine, "la carte de signalement n'est plus après le bandeau de la semaine")
    }

    @Test
    fun `la feuille est montee a l'ouverture, et demontee a la fermeture`() {
        // La feuille n'existe que lorsqu'elle est ouverte : c'est ce qui fait renaître ses
        // `remember` vides à chaque ouverture, comme les `useState` de l'original.
        assertTrue(
            sourceDeLEcran().contains("if (open) {"),
            "La feuille n'est plus conditionnée par l'ouverture : elle serait toujours montée, et " +
                "le texte du geste précédent serait retrouvé.",
        )
    }

    @Test
    fun `la feuille efface ce que le depot a garde du geste precedent`() {
        assertTrue(
            sourceDeLEcran().contains("repository.reset()"),
            "La feuille n'efface plus l'état du dépôt à l'ouverture : une feuille rouverte " +
                "montrerait la confirmation du signalement précédent.",
        )
    }

    @Test
    fun `la feuille observe l'etat du depot`() {
        val source = sourceDeLEcran()

        assertTrue(
            source.contains("repository.state.collectAsStateWithLifecycle()"),
            "La feuille n'observe plus l'état du dépôt : le bouton ne dirait jamais « Envoi… », " +
                "et la confirmation ne s'afficherait jamais.",
        )
        assertTrue(
            source.contains("repository.send(type, description, attachment)"),
            "L'envoi ne passe plus par le dépôt : le signalement ne serait ni gardé ni déposé.",
        )
    }

    @Test
    fun `le champ porte la borne du domaine, et non un nombre ecrit ici`() {
        val source = sourceDeLEcran()

        assertTrue(
            source.contains("maxLength = ui.descriptionMax"),
            "Le champ ne reçoit plus la borne calculée : elle serait recopiée, et divergerait du " +
                "domaine au premier changement.",
        )
        assertFalse(
            source.contains("maxLength = 500"),
            "La borne est écrite en clair dans l'écran : elle doit venir de `ProblemReports`.",
        )
    }

    @Test
    fun `les cinq natures viennent du modele, et non d'une liste ecrite ici`() {
        // Le renderer lit `ProblemReportType.all` ; l'écran, lui, prend la **première** comme
        // valeur initiale. Une liste écrite dans l'un ou l'autre oublierait la sixième nature le
        // jour où elle existerait.
        val renderer = sourceDuRenderer()
        assertTrue(
            renderer.contains("natures = ProblemReportType.all"),
            "Le renderer ne lit plus les natures du modèle.",
        )
        assertTrue(
            sourceDeLEcran().contains("ProblemReportType.all.first()"),
            "La nature cochée par défaut n'est plus la première du modèle.",
        )
        assertFalse(
            renderer.contains("ProblemReportType.BUG"),
            "Le renderer nomme une nature en dur : le défaut doit suivre l'ordre du modèle.",
        )
    }

    @Test
    fun `le bouton d'envoi est desactive par la regle calculee`() {
        assertTrue(
            sourceDeLEcran().contains("enabled = ui.canSend"),
            "Le bouton d'envoi n'utilise plus la règle calculée : il s'allumerait sur une " +
                "description faite d'espaces.",
        )
    }

    @Test
    fun `le choix de capture passe par la capacite injectee`() {
        val source = sourceDeLEcran()

        assertTrue(
            source.contains("picker.pick()"),
            "Le choix de capture ne passe plus par la capacité : l'écran deviendrait " +
                "inéprouvable sans appareil.",
        )
        assertTrue(
            source.contains("rememberProblemReportPicker()"),
            "La capacité n'est plus construite : `picker` ne serait branché sur rien.",
        )
    }

    @Test
    fun `la feuille ne se referme pas pendant un envoi`() {
        // Trois chemins referment la feuille — le fond, la poignée, le retour système —, et les
        // trois reçoivent **la même** garde. Une seule qui l'oublierait refermerait la feuille en
        // plein envoi, et laisserait la personne sans réponse sur un geste qu'elle a fait.
        val source = sourceDeLEcran()

        assertTrue(
            source.contains("val fermer: () -> Unit = { if (!ui.busy) onClose() }"),
            "La garde de fermeture pendant un envoi a disparu : la feuille se refermerait en plein " +
                "envoi.",
        )
        assertTrue(
            source.contains("onDismissRequest = fermer,"),
            "Le retour système ne passe plus par la garde.",
        )
        assertTrue(
            source.contains("onClick = fermer,"),
            "Le fond de la feuille ne passe plus par la garde.",
        )
        assertTrue(
            source.contains("onDismiss = fermer,"),
            "La poignée ne passe plus par la garde.",
        )
    }

    /**
     * Le source d'un fichier du module.
     *
     * Deux chemins sont essayés, et non un seul : la tâche `Test` de Gradle s'exécute dans le
     * dossier **du module**, alors qu'un contrôle joué à la main depuis la racine du dépôt part de
     * là.
     */
    private fun source(nom: String): String {
        val relatif = "src/main/kotlin/com/msoumaya/deepseekandroid/feature/home/$nom"
        val candidats = listOf(File(relatif), File("feature/home/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "$nom introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }

    private fun sourceDeLAccueil(): String = source("HomeScreen.kt")

    private fun sourceDeLEcran(): String = source("ProblemReportSheet.kt")

    private fun sourceDuRenderer(): String = source("ProblemReportRenderer.kt")
}
