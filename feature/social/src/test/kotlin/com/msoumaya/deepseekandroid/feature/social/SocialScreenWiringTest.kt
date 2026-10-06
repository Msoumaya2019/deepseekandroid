package com.msoumaya.deepseekandroid.feature.social

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient le **branchement de l'écran « Amis »**.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * `SocialScreen` est une fonction `@Composable` : la déclencher demande un hôte Compose, un
 * appareil ou un émulateur, et le projet n'a aucun outillage de test d'interface. Le branchement
 * n'est donc atteignable par aucun test de comportement — ce n'est pas un raccourci, c'est le seul
 * moyen. Le calcul, lui, est éprouvé pour de vrai dans `SocialRendererTest` : ce fichier ne
 * surveille que le câblage.
 *
 * ## Ce qui disparaîtrait sans un mot
 *
 * Trois choses, et c'est pour elles que ce fichier existe :
 *
 *  - **l'appel de `onVisible()`** : sans lui, l'écran s'ouvre sur ce que le dépôt a lu à sa
 *    construction et **ne se relit plus jamais**. Rien ne le signale : la liste s'affiche, elle
 *    est simplement périmée — un ami accepté depuis l'autre onglet n'apparaîtrait pas ;
 *  - **la fabrique du `ViewModel`** : elle passe le conteneur applicatif. La perdre ferait
 *    construire un `ViewModel` sans dépôt, et l'écran resterait sur « Chargement de tes amis… »
 *    indéfiniment ;
 *  - **les rappels de gestes** : chacun a une valeur par défaut vide. Un rappel oublié compile,
 *    s'affiche, et laisse un bouton sans effet — c'est déjà arrivé ailleurs dans ce dépôt.
 *
 * ## Ce qu'il ne prouve pas
 *
 * Il ne prouve pas que l'écran est beau, ni que ses libellés sont les bons : les mots vivent dans
 * `SocialText`, et c'est `SocialTextTest` qui les tient.
 */
class SocialScreenWiringTest {

    @Test
    fun `l'ecran construit son ViewModel depuis le conteneur applicatif`() {
        assertTrue(
            sourceDeLEcran().contains("SocialViewModel.factory(LocalAppContainer.current)"),
            "L'écran ne construit plus son ViewModel depuis le conteneur : il n'aurait plus de " +
                "dépôt, et resterait sur « Chargement de tes amis… ».",
        )
    }

    @Test
    fun `l'ecran se relit a l'ouverture`() {
        // Le dépôt charge déjà à sa construction, mais il ne sait pas quand l'onglet s'ouvre.
        // C'est la seule chose que l'écran lui apprend.
        assertTrue(
            sourceDeLEcran().contains("LaunchedEffect(Unit) { viewModel.onVisible() }"),
            "L'écran n'appelle plus `onVisible()` : il s'ouvrirait sur la lecture faite à la " +
                "construction du dépôt, et ne se relirait plus jamais — sans rien dire.",
        )
    }

    @Test
    fun `l'ecran branche toutes les saisies et tous les gestes`() {
        // Chaque rappel de `SocialContent` a une valeur par défaut vide : les brancher est ce qui
        // les rend utiles, et les oublier ne casse rien. La liste est celle de l'appel, et non de
        // la déclaration — c'est pourquoi elle cherche la référence au `ViewModel`.
        val source = sourceDeLEcran()
        for (reference in listOf(
            "onQueryChange = viewModel::onQueryChange",
            "onFilterSelected = viewModel::onFilterSelected",
            "onToggleAll = viewModel::onToggleAll",
            "onToggleOptions = viewModel::onToggleOptions",
            "onCodeChange = viewModel::onCodeChange",
            "onSendInvitation = viewModel::onSendInvitation",
            "onGroupNameChange = viewModel::onGroupNameChange",
            "onCreateGroup = viewModel::onCreateGroup",
            "onAccept = viewModel::onAccept",
            "onDecline = viewModel::onDecline",
            "onRemove = viewModel::onRemove",
            "onBlock = viewModel::onBlock",
            "onUnblock = viewModel::onUnblock",
            "onCodeCopied = viewModel::onCodeCopied",
            "onAdminContact = viewModel::onOpenAdminContact",
            "onOpenConversation = viewModel::onOpenConversation",
        )) {
            assertTrue(
                source.contains(reference),
                "L'écran ne branche plus `$reference` : le geste correspondant n'aurait plus " +
                    "d'effet, et rien d'autre ne le dirait.",
            )
        }
    }

    @Test
    fun `l'ecran porte la bande d'en-tete et laisse le contenu a part`() {
        val source = sourceDeLEcran()
        assertTrue(source.contains("AppHero("), "L'écran n'a plus de bande d'en-tête.")
        assertTrue(
            source.contains("internal fun SocialContent("),
            "Le contenu n'est plus séparé de l'écran : il n'y aurait plus rien à prévisualiser " +
                "sans conteneur applicatif.",
        )
    }

    @Test
    fun `l'onglet n'est plus un panneau de phase`() {
        // L'absence est surveillée parce qu'elle est le **fait** de cette livraison : le panneau
        // annonçait « Phase D » à une personne qui ouvrait l'onglet.
        assertFalse(
            sourceDeLEcran().contains("PhasePlaceholder"),
            "L'écran des amis rend de nouveau un panneau de phase : l'onglet annoncerait " +
                "« Phase D » à la place de la liste.",
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
        val relatif = "src/main/kotlin/com/msoumaya/deepseekandroid/feature/social/SocialScreen.kt"
        val candidats = listOf(File(relatif), File("feature/social/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "SocialScreen.kt introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }
}
