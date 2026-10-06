package com.msoumaya.deepseekandroid.feature.quiz

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient le **branchement de l'écran « Quiz »**.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * `QuizScreen` est une fonction `@Composable` : la déclencher demande un hôte Compose, un appareil
 * ou un émulateur, et le projet n'a aucun outillage de test d'interface. Le branchement n'est donc
 * atteignable par aucun test de comportement — ce n'est pas un raccourci, c'est le seul moyen. Le
 * calcul, lui, est éprouvé pour de vrai dans `QuizRendererTest` : ce fichier ne surveille que le
 * câblage.
 *
 * ## Ce que le compilateur garantit déjà, et que ce fichier n'a pas à vérifier
 *
 * `QuizView` est une énumération, et le `when` de l'écran est une expression : **oublier une des six
 * vues ne compile pas**. Les six vues sont donc hors de portée d'une perte silencieuse, et ce
 * fichier ne les compte pas — un contrôle qui compte ce que le compilateur compte ne prouve rien
 * de plus.
 *
 * ## Ce qui disparaîtrait sans un mot
 *
 * Six choses, et c'est pour elles que ce fichier existe :
 *
 *  - **les deux arguments de la route** : la fabrique du `ViewModel` reçoit l'ami à défier et le
 *    défi à ouvrir. Les perdre compile, s'affiche, et ouvre l'accueil là où un défi précis était
 *    demandé — le chemin qui vient d'une notification ou d'une conversation arriverait nulle part ;
 *  - **la fabrique elle-même** : elle passe le conteneur applicatif. La perdre ferait construire un
 *    `ViewModel` sans dépôt, et l'écran resterait sur « Ouverture de l'application… » ;
 *  - **les rappels de gestes** : chacun a une valeur par défaut vide. Un rappel oublié compile,
 *    s'affiche, et laisse un bouton sans effet ;
 *  - **« Retour » et ses deux issues** : depuis l'accueil il **ferme** l'écran, ailleurs il
 *    **remonte**. Perdre la distinction laisse un bouton qui réagit — mais qui ne ramène jamais à
 *    l'accueil, ou qui n'en sort jamais ;
 *  - **l'avis du dépôt et son bouton** : c'est le seul chemin de reprise quand une action a échoué.
 *    Sans lui, la panne est invisible ;
 *  - **la relecture à l'ouverture** : le dépôt relit quand le compte change, pas quand l'écran
 *    s'ouvre. Sans cet appel, l'instantané vieillit en silence.
 *
 * ## Ce qu'il ne prouve pas
 *
 * Il ne prouve pas que l'écran est beau, ni que ses libellés sont les bons : les mots vivent dans
 * `QuizText`, et les combinaisons d'affichage dans `QuizRendererTest`.
 */
class QuizScreenWiringTest {

    @Test
    fun `l'ecran construit son ViewModel depuis le conteneur, avec les deux arguments de la route`() {
        assertTrue(
            sourceDeLEcran().contains(
                "QuizViewModel.factory(LocalAppContainer.current, friendId, challengeId)",
            ),
            "L'écran ne passe plus les arguments de la route à sa fabrique : ouvrir le Quiz " +
                "depuis un défi ou depuis la conversation retomberait sur l'accueil, sans rien dire.",
        )
    }

    @Test
    fun `l'ecran branche toutes les saisies et tous les gestes`() {
        // Chaque rappel de `QuizContent` a une valeur par défaut vide : les brancher est ce qui les
        // rend utiles, et les oublier ne casse rien. La liste est celle de l'appel, et non de la
        // déclaration — c'est pourquoi elle cherche la référence au `ViewModel`.
        val source = sourceDeLEcran()
        for (reference in listOf(
            "onBack = viewModel::onBack",
            "onOpenDaily = viewModel::onOpenDaily",
            "onOpenView = viewModel::onOpenView",
            "onOpenChallenge = viewModel::onOpenChallenge",
            "onSelectFriend = viewModel::onSelectFriend",
            "onSelectCount = viewModel::onSelectCount",
            "onSelectSet = viewModel::onSelectSet",
            "onCreateChallenge = viewModel::onCreateChallenge",
            "onAnswerDaily = viewModel::onAnswerDaily",
            "onAnswerChallenge = viewModel::onAnswerChallenge",
            "onToggleNotifications = viewModel::onToggleNotifications",
            "onRefresh = viewModel::onRefresh",
        )) {
            assertTrue(
                source.contains(reference),
                "L'écran ne branche plus `$reference` : le geste correspondant n'aurait plus " +
                    "d'effet, et rien d'autre ne le dirait.",
            )
        }
    }

    @Test
    fun `Retour ferme l'ecran depuis l'accueil et remonte depuis les autres vues`() {
        // L'état dit laquelle des deux issues, et il le dit au même endroit que le titre : recalculer
        // ici « la vue est-elle l'accueil ? » ferait une seconde copie d'une règle qui a déjà trois
        // branches — fermer, remonter d'un défi, rentrer à l'accueil.
        assertTrue(
            sourceDeLEcran().contains("if (state.backCloses) onClose() else onBack()"),
            "« Retour » ne distingue plus la fermeture de la remontée : depuis l'accueil il ne " +
                "fermerait plus l'écran, ou depuis une vue il n'y ramènerait plus.",
        )
    }

    @Test
    fun `l'avis du depot porte son bouton de relecture`() {
        // Le seul chemin de reprise quand une action a échoué alors que l'écran avait déjà quelque
        // chose à montrer. Sans lui, la panne est invisible : l'écran paraît simplement à jour.
        val source = sourceDeLEcran()
        assertTrue(
            source.contains("state.notice?.let { notice -> NoticeCard(notice = notice, onRefresh = onRefresh) }"),
            "L'écran n'affiche plus l'avis du dépôt : une action qui échoue ne dirait plus rien.",
        )
        assertTrue(
            source.contains("text = QuizText.REFRESH"),
            "La carte d'avis n'a plus son bouton « Actualiser » : l'avis nommerait la panne sans " +
                "offrir de réessayer.",
        )
    }

    @Test
    fun `une proposition annonce son etat au lecteur d'ecran`() {
        // Le libellé d'accessibilité est résolu par le renderer — « A. … · réponse choisie » — et
        // posé par `semantics` : le seul texte de la proposition ne dirait pas qu'elle a été
        // choisie. Même procédé que la ligne d'une révision dans le tableau de bord.
        assertTrue(
            sourceDeLEcran().contains(".semantics { contentDescription = answer.label }"),
            "Une proposition n'annonce plus son libellé d'accessibilité : le lecteur d'écran ne " +
                "dirait plus laquelle a été choisie.",
        )
    }

    @Test
    fun `la correction ouvre son lien par le gestionnaire du systeme`() {
        // `Linking.openURL` dans l'original. Le lien est déjà filtré en `https` par le renderer,
        // donc l'écran n'a pas à le revérifier — mais il doit l'ouvrir.
        assertTrue(
            sourceDeLEcran().contains("handler.openUri(url)"),
            "Le bouton « Voir la source » n'ouvre plus son lien : la correction afficherait une " +
                "adresse sans permettre de la suivre.",
        )
    }

    @Test
    fun `le marqueur de choix n'est pas recopie dans l'ecran`() {
        // L'original écrit `{quizSet===q.id?' ✓':''}` deux fois — sur le bouton des questions
        // aléatoires et sur celui de chaque quiz thématique. La règle vit dans
        // `QuizText.selectedLabel` ; une copie ici divergerait de l'autre sans que rien ne le dise.
        assertFalse(
            sourceDeLEcran().contains("\" ✓\""),
            "L'écran écrit lui-même le marqueur « ✓ » : la règle vit dans `QuizText.selectedLabel`, " +
                "et deux copies d'un même marqueur finissent par ne plus se ressembler.",
        )
    }

    @Test
    fun `le ViewModel se relit a l'ouverture de l'ecran`() {
        // Le dépôt relit quand le compte change. Il ne sait pas quand l'écran s'ouvre : c'est la
        // seule chose que le ViewModel lui apprend, et sans elle l'instantané vieillit en silence.
        assertTrue(
            sourceDuViewModel().contains("viewModelScope.launch { repository.refresh() }"),
            "Le ViewModel ne relit plus à l'ouverture : l'écran afficherait l'instantané lu au " +
                "changement de compte, et ne se relirait plus jamais.",
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
        val relatif = "src/main/kotlin/com/msoumaya/deepseekandroid/feature/quiz/$nom"
        val candidats = listOf(File(relatif), File("feature/quiz/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "$nom introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }

    private fun sourceDeLEcran(): String = source("QuizScreen.kt")

    private fun sourceDuViewModel(): String = source("QuizViewModel.kt")
}
