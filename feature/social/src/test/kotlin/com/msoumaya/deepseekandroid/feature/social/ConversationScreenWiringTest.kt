package com.msoumaya.deepseekandroid.feature.social

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tient le **branchement de l'écran « Conversation »**, et celui qui y mène.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * `ConversationScreen` est une fonction `@Composable` : la déclencher demande un hôte Compose, un
 * appareil ou un émulateur, et le projet n'a aucun outillage de test d'interface. Le branchement
 * n'est donc atteignable par aucun test de comportement — ce n'est pas un raccourci, c'est le seul
 * moyen. Le calcul, lui, est éprouvé pour de vrai dans `ConversationRendererTest` : ce fichier ne
 * surveille que le câblage.
 *
 * ## Ce qui disparaîtrait sans un mot
 *
 * Cinq choses, et c'est pour elles que ce fichier existe :
 *
 *  - **l'appel de `onVisible()`** : sans lui, la pièce ouverte reste sur ce que le dépôt a lu au
 *    moment de l'ouverture, et **ne se relit plus jamais**. Rien ne le signale : les messages
 *    s'affichent, ils sont simplement périmés ;
 *  - **la fabrique du `ViewModel`** : elle passe le conteneur applicatif. La perdre ferait
 *    construire un `ViewModel` sans dépôt, et l'écran resterait sur « Chargement des messages… »
 *    indéfiniment ;
 *  - **les rappels de gestes** : chacun a une valeur par défaut vide. Un rappel oublié compile,
 *    s'affiche, et laisse un bouton sans effet — c'est déjà arrivé ailleurs dans ce dépôt ;
 *  - **la bascule depuis la liste** : c'est `SocialScreen` qui compose la conversation à la place
 *    de la liste. Sans elle, la pièce s'ouvrirait dans le dépôt et **rien ne la montrerait** ;
 *  - **les trois portes** : la ligne d'un ami, son bouton « Message », et le bouton « Ouvrir »
 *    d'un cercle. Ce sont les seuls chemins vers la conversation ; en perdre une rend un contenu
 *    inatteignable, et la perte serait silencieuse.
 *
 * ## Ce qu'il ne prouve pas
 *
 * Il ne prouve pas que l'écran est beau, ni que ses libellés sont les bons : les mots vivent dans
 * `SocialText`, et c'est `SocialTextTest` qui les tient.
 */
class ConversationScreenWiringTest {

    @Test
    fun `l'ecran construit son ViewModel depuis le conteneur applicatif`() {
        assertTrue(
            sourceDeLEcran().contains("ConversationViewModel.factory(LocalAppContainer.current)"),
            "L'écran ne construit plus son ViewModel depuis le conteneur : il n'aurait plus de " +
                "dépôt, et resterait sur « Chargement des messages… ».",
        )
    }

    @Test
    fun `l'ecran se relit a l'ouverture`() {
        // Le dépôt a lu la pièce au moment de l'ouvrir, mais il ne sait pas quand elle arrive à
        // l'écran. C'est la seule chose que l'écran lui apprend.
        assertTrue(
            sourceDeLEcran().contains("LaunchedEffect(Unit) { viewModel.onVisible() }"),
            "L'écran n'appelle plus `onVisible()` : il afficherait la lecture faite à l'ouverture " +
                "de la pièce, et ne se relirait plus jamais — sans rien dire.",
        )
    }

    @Test
    fun `l'ecran branche toutes les saisies et tous les gestes`() {
        // Chaque rappel de `ConversationContent` a une valeur par défaut vide : les brancher est
        // ce qui les rend utiles, et les oublier ne casse rien. La liste est celle de l'appel, et
        // non de la déclaration — c'est pourquoi elle cherche la référence au `ViewModel`.
        val source = sourceDeLEcran()
        for (reference in listOf(
            "onClose = viewModel::onClose",
            "onToggleTools = viewModel::onToggleTools",
            "onDraftChange = viewModel::onDraftChange",
            "onSend = viewModel::onSend",
            "onShareProgress = viewModel::onShareProgress",
            "onLoadOlder = viewModel::onLoadOlder",
            "onDeleteMessage = viewModel::onDeleteMessage",
            "onReport = viewModel::onReport",
            "onReportDismiss = viewModel::onReportDismiss",
            "onSendReport = viewModel::onSendReport",
            "onReportReasonChange = viewModel::onReportReasonChange",
            "onGoalTargetChange = viewModel::onGoalTargetChange",
            "onProposeGoal = viewModel::onProposeGoal",
            "onAcceptGoal = viewModel::onAcceptGoal",
            "onAppointmentTextChange = viewModel::onAppointmentTextChange",
            "onProposeAppointment = viewModel::onProposeAppointment",
            "onAcceptAppointment = viewModel::onAcceptAppointment",
            "onCancelAppointment = viewModel::onCancelAppointment",
            "onJoinGroup = viewModel::onJoinGroup",
            "onDeclineGroupInvite = viewModel::onDeclineGroupInvite",
            "onInviteFriend = viewModel::onInviteFriend",
            "onToggleModerator = viewModel::onToggleModerator",
            "onRemoveMember = viewModel::onRemoveMember",
            "onDeleteGroup = viewModel::onDeleteGroup",
        )) {
            assertTrue(
                source.contains(reference),
                "L'écran ne branche plus `$reference` : le geste correspondant n'aurait plus " +
                    "d'effet, et rien d'autre ne le dirait.",
            )
        }
    }

    @Test
    fun `la liste bascule sur la conversation quand une piece est ouverte`() {
        // **Le seul endroit qui rend la conversation visible.** La pièce s'ouvre dans le dépôt ;
        // sans cette bascule, elle s'ouvrirait pour personne.
        val source = sourceDesAmis()
        assertTrue(
            source.contains("if (state.conversationOpen) {"),
            "L'écran des amis ne regarde plus si une pièce est ouverte : la conversation " +
                "s'ouvrirait dans le dépôt sans que rien ne la montre.",
        )
        assertTrue(
            source.contains("ConversationSection(modifier = modifier, onChallenge = onChallenge)"),
            "L'écran des amis ne compose plus la conversation à la place de la liste.",
        )
    }

    @Test
    fun `les trois portes de la conversation sont ouvertes`() {
        // L'original en portait trois, et ce sont les seuls chemins vers une pièce. En perdre une
        // rend un contenu inatteignable — et la perte serait silencieuse : la liste s'afficherait
        // normalement, avec une ligne qui ne réagit plus.
        val source = sourceDesAmis()
        for ((reference, quoi) in listOf(
            "onOpen(row.id, false)" to "la ligne d'un ami",
            "MessageButton(name = row.name, onClick = { onOpen(row.id, false) })" to
                "le bouton « Message » d'une ligne",
            "onOpen(circle.id, true)" to "le bouton « Ouvrir » d'un cercle",
        )) {
            assertTrue(
                source.contains(reference),
                "L'écran des amis ne branche plus $quoi : ce chemin vers la conversation est " +
                    "fermé, et rien ne le signale.",
            )
        }
    }

    @Test
    fun `le bouton Defier suit l'etat et ouvre le Quiz sur l'ami`() {
        // L'original pose « 🏆 Défier » juste après le dépliant « Profil et entraide », sous la
        // même garde que lui. Ici, ses trois conditions — pas un contact d'administration, un lien
        // d'amitié, un destinataire connu — sont repliées dans `challengeFriendId`, qui vit dans
        // le domaine et s'y éprouve. L'écran n'a donc qu'à composer le bouton si l'état lui donne
        // quelqu'un, et à lui passer cet identifiant.
        //
        // Ce qui disparaîtrait sans un mot : un `let` remplacé par une garde sur `showTools` ferait
        // naître le bouton dans un cercle où il n'a pas de sens — et `onChallenge` a une valeur par
        // défaut vide, donc un bouton qui n'appelle rien compile et s'affiche.
        val source = sourceDeLEcran()

        assertTrue(
            source.contains("state.challengeFriendId?.let { friendId ->"),
            "Le bouton « 🏆 Défier » ne dépend plus de l'état : il pourrait naître sans " +
                "destinataire, et « Défier » ne défierait personne.",
        )
        assertTrue(
            source.contains("text = SocialText.CHALLENGE,"),
            "Le bouton « 🏆 Défier » n'emprunte plus son libellé à `SocialText`.",
        )
        assertTrue(
            source.contains("onClick = { onChallenge(friendId) },"),
            "Le bouton « 🏆 Défier » n'ouvre plus le Quiz sur l'ami : ce serait un bouton mort, " +
                "et rien d'autre ne le dirait.",
        )
    }

    @Test
    fun `le compositeur est hors de la zone defilante`() {
        // L'original pose son champ **hors** de la `ScrollView`, dans une bande fixe en bas : c'est
        // ce qui permet d'écrire en lisant un message ancien. La forme qui le garantit ici est le
        // `weight(1f)` posé sur la colonne défilante — le compositeur est sa sœur, pas son
        // dernier enfant.
        assertTrue(
            sourceDeLEcran().contains(".weight(1f)"),
            "La colonne défilante de la conversation n'est plus pondérée : le compositeur serait " +
                "repoussé hors de l'écran, ou pousserait la discussion hors de l'écran.",
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
        val relatif = "src/main/kotlin/com/msoumaya/deepseekandroid/feature/social/$nom"
        val candidats = listOf(File(relatif), File("feature/social/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "$nom introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }

    private fun sourceDeLEcran(): String = source("ConversationScreen.kt")

    private fun sourceDesAmis(): String = source("SocialScreen.kt")
}
