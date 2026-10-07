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
 * Quatre choses, et c'est pour elles que ce fichier existe :
 *
 *  - **l'appel de `onVisible()`** : sans lui, l'écran s'ouvre sur ce que le dépôt a lu à sa
 *    construction et **ne se relit plus jamais**. Rien ne le signale : la liste s'affiche, elle est
 *    simplement périmée — une récitation envoyée depuis un autre appareil n'apparaîtrait pas ;
 *  - **la fabrique du `ViewModel`** : elle passe le conteneur applicatif. La perdre ferait
 *    construire un `ViewModel` sans dépôt, et l'écran resterait vide indéfiniment ;
 *  - **les rappels de gestes** : chacun a une valeur par défaut vide. Un rappel oublié compile,
 *    s'affiche, et laisse un bouton sans effet — c'est déjà arrivé ailleurs dans ce dépôt ;
 *  - **la garde de l'écoute** : sans elle, l'écran offrirait un bouton de lecture alors qu'aucun
 *    lecteur n'a été fourni au conteneur, et le premier appui ne ferait rien.
 *
 * ## Ce qu'il ne prouve pas
 *
 * Il ne prouve pas que l'écran est beau, ni que ses libellés sont les bons : les mots vivent dans
 * `RecitationText`, et c'est `RecitationsListTest` qui les tient. Il ne prouve pas non plus *ce
 * qu'un appui sur le bouton de lecture doit faire* : cette règle vit dans
 * `RecitationsList.playbackAction`, et c'est là-bas qu'elle s'éprouve.
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
            "onPlayPause = viewModel::onPlayPause",
            "onSeekBackward = viewModel::onSeekBackward",
            "onSeekForward = viewModel::onSeekForward",
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
    fun `l'ecran offre l'ecoute, ses deux avances et sa barre`() {
        // L'original a un bouton de lecture, une avance et un retour de dix secondes, et une barre
        // de progression. Les trois libellés fixes viennent de `RecitationText` — celui du bouton
        // de lecture, lui, est déjà résolu par le rendu (« Pause » quand ça joue) et ne peut donc
        // pas être cherché ici.
        val source = sourceDeLEcran()
        for (reference in listOf(
            "RecitationText.SEEK_BACK",
            "RecitationText.SEEK_FORWARD",
            // L'ancre porte la division : le composant attend une fraction, l'état un
            // pourcentage, et c'est cette ligne qui fait le passage.
            "ProgressTrack(value = gestes.progressPercent / 100f)",
        )) {
            assertTrue(
                source.contains(reference),
                "L'écran n'offre plus `$reference` : le geste d'écoute correspondant a disparu, " +
                    "et rien d'autre ne le dirait.",
            )
        }
    }

    @Test
    fun `l'ecoute n'est offerte que si un lecteur existe`() {
        // Sans cette garde, l'écran poserait un bouton de lecture alors qu'aucun lecteur n'a été
        // fourni au conteneur — le geste mort que ce dépôt s'interdit, et il ne se verrait qu'à
        // l'usage : le bouton s'affiche, et le premier appui ne fait rien.
        //
        // **L'ancre porte sur la garde, et non sur le nom du champ.** `state.canListen` apparaît
        // aussi dans le commentaire d'en-tête du fichier : chercher le nom seul serait satisfait
        // par le commentaire, et le contrôle passerait alors que la garde a disparu du code.
        assertTrue(
            sourceDeLEcran().contains("if (state.canListen"),
            "L'écran n'interroge plus `state.canListen` : il offrirait l'écoute même sans lecteur, " +
                "et le bouton ne jouerait rien.",
        )
    }

    @Test
    fun `l'ecran branche le partage, du bouton a la confirmation`() {
        // Le partage a désormais une capacité dans **toutes** les couches : une règle dans
        // `RecitationsList` (ce qui est partageable), une autre dans `Social` (à qui), un geste
        // dans `SocialRepository` (par où), et des libellés dans `RecitationText`. Ce contrôle
        // épingle le dernier maillon. Sans lui, les quatre couches existeraient et l'écran
        // n'offrirait rien — c'est exactement l'état où ce fichier a été écrit la première fois,
        // quand il épinglait l'absence.
        val source = sourceDeLEcran()
        for (reference in listOf(
            "RecitationText.SHARE_FRIEND",
            "RecitationText.SHARE_HINT",
            "RecitationText.SHARE_TITLE",
            "RecitationText.SHARE_CONFIRM",
            "RecitationText.SHARE_NO_FRIEND",
            // **La garde de capacité.** Sans elle, l'écran poserait un bouton de partage alors
            // qu'aucune couche sociale n'a été fournie au conteneur : il n'y aurait personne à
            // qui envoyer, et l'appui ne ferait rien.
            "if (state.canShare",
            // **Les quatre gestes.** Chacun a une valeur par défaut vide : les oublier compile,
            // s'affiche, et laisse un bouton sans effet.
            "onShare = viewModel::onShare",
            "onPickFriend = viewModel::onPickFriend",
            "onConfirmShare = viewModel::onConfirmShare",
            "onCancelShare = viewModel::onCancelShare",
        )) {
            assertTrue(
                source.contains(reference),
                "L'écran n'offre plus `$reference` : le partage a une capacité dans toutes les " +
                    "couches, et plus rien ne le brancherait.",
            )
        }
    }

    @Test
    fun `l'ecran ne partage rien lui-meme`() {
        // **L'envoi passe par le ViewModel, jamais par un composable.** Un appel à
        // `shareRecitation` depuis la ligne partirait à chaque recomposition — et la position de
        // lecture en provoque une tous les dixièmes de seconde : le même partage serait envoyé des
        // dizaines de fois, et aucun test de comportement ne le verrait.
        assertFalse(
            sourceDeLEcran().contains("shareRecitation"),
            "L'écran appelle `shareRecitation` lui-même : un partage lancé depuis une " +
                "recomposition partirait plusieurs fois.",
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
