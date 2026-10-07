package com.msoumaya.deepseekandroid.feature.recitations

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient le **branchement de l'écran « Mes récitations »**.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * `RecitationsScreen` est une fonction `@Composable` : la déclencher demande un hôte Compose, un
 * appareil ou un émulateur, et le projet n'a aucun outillage d'interface. Le branchement n'est donc
 * atteignable par aucun test de comportement — ce n'est pas un raccourci, c'est le seul moyen. Le
 * calcul, lui, est éprouvé pour de vrai dans `RecitationsRendererTest` : ce fichier ne surveille
 * que le câblage.
 *
 * ## Ce qui disparaîtrait sans un mot
 *
 * Trois choses, et c'est pour elles que ce fichier existe :
 *
 *  - **l'appel de `onVisible()`** : sans lui, l'écran s'ouvre sur ce que le dépôt a lu à sa
 *    construction et **ne se relit plus jamais**. Rien ne le signale : la liste s'affiche, elle est
 *    simplement périmée — une récitation envoyée depuis un autre appareil n'apparaîtrait pas ;
 *  - **la fabrique du `ViewModel`** : elle passe le conteneur applicatif. La perdre ferait
 *    construire un `ViewModel` sans dépôt, et l'écran resterait vide indéfiniment ;
 *  - **les rappels de gestes** : chacun a une valeur par défaut vide. Un rappel oublié compile,
 *    s'affiche, et laisse un bouton sans effet — c'est déjà arrivé ailleurs dans ce dépôt.
 *
 * ## Ce qu'il ne prouve pas
 *
 * Il ne prouve pas que l'écran est beau, ni que ses libellés sont les bons : les mots vivent dans
 * `RecitationText`, et c'est `RecitationsListTest` qui les tient.
 */
class RecitationsScreenWiringTest {

    @Test
    fun `l'ecran construit son ViewModel depuis le conteneur applicatif`() {
        assertTrue(
            sourceDeLEcran().contains("RecitationsViewModel.factory(LocalAppContainer.current)"),
            "L'écran ne construit plus son ViewModel depuis le conteneur : il n'aurait plus de " +
                "dépôt, et la liste resterait vide.",
        )
    }

    @Test
    fun `l'ecran se relit a l'ouverture`() {
        // Le dépôt charge déjà à sa construction, mais il ne sait pas quand l'écran s'ouvre :
        // c'est la seule chose que l'écran lui apprend.
        assertTrue(
            sourceDeLEcran().contains("LaunchedEffect(Unit) { viewModel.onVisible() }"),
            "L'écran n'appelle plus `onVisible()` : il s'ouvrirait sur la lecture faite à la " +
                "construction du dépôt, et ne se relirait plus jamais — sans rien dire.",
        )
    }

    @Test
    fun `l'ecran branche toutes les saisies et tous les gestes`() {
        // Chaque rappel de `RecitationsContent` a une valeur par défaut vide : les brancher est ce
        // qui les rend utiles, et les oublier ne casse rien. La liste est celle de l'appel, et non
        // de la déclaration — c'est pourquoi elle cherche la référence au `ViewModel`.
        val source = sourceDeLEcran()
        for (reference in listOf(
            "onRefresh = viewModel::onRefresh",
            "onFilterSelected = viewModel::onFilterSelected",
            "onOpen = viewModel::onOpen",
            "onDelete = viewModel::onDelete",
            "onClose = onClose",
        )) {
            assertTrue(
                source.contains(reference),
                "L'écran ne branche plus `$reference` : le geste correspondant n'aurait plus " +
                    "d'effet, et rien d'autre ne le dirait.",
            )
        }
    }

    @Test
    fun `l'ecran porte le bouton de retour et laisse le contenu a part`() {
        val source = sourceDeLEcran()
        assertTrue(
            source.contains("RecitationText.LIST_BACK"),
            "L'écran n'a plus de bouton de retour : plein écran, sans onglet ni barre " +
                "supérieure, il n'aurait plus aucun moyen de revenir en arrière.",
        )
        assertTrue(
            source.contains("internal fun RecitationsContent("),
            "Le contenu n'est plus séparé de l'écran : il n'y aurait plus rien à prévisualiser " +
                "sans conteneur applicatif.",
        )
    }

    @Test
    fun `l'ecran n'offre pas encore d'ecoute, et c'est delibere`() {
        // L'original a un bouton de lecture, une avance et un retour de dix secondes. La couche
        // audio n'est pas branchée dans ce module : un bouton qui ne joue rien est le geste mort
        // que ce dépôt s'interdit. Ce contrôle épingle l'absence pour qu'on ne l'ajoute pas
        // **sans** la capacité — et il devra être inversé quand elle arrivera.
        val source = sourceDeLEcran()
        assertFalse(
            source.contains("RecitationText.LIST_PLAY") || source.contains("RecitationText.SEEK_"),
            "L'écran offre un geste d'écoute alors que rien ne peut jouer : le premier appui " +
                "serait sans effet, et rien d'autre ne le dirait.",
        )
        assertFalse(
            source.contains("RecitationText.SHARE_"),
            "L'écran offre le partage alors que `shareRecitation` n'existe dans aucune couche du " +
                "portage : le bouton mènerait à une porte qui n'existe pas.",
        )
    }

    /**
     * Le source de l'écran.
     *
     * Deux chemins sont essayés, et non un seul : la tâche `Test` de Gradle s'exécute dans le
     * dossier **du module**, alors qu'un contrôle joué à la main depuis la racine du dépôt part de
     * là.
     */
    private fun sourceDeLEcran(): String {
        val relatif =
            "src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsScreen.kt"
        val candidats = listOf(File(relatif), File("feature/recitations/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "RecitationsScreen.kt introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }
}
