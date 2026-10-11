package com.msoumaya.deepseekandroid.feature.home

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tient le **branchement de l'accueil** : les deux cartes de quiz et le carrousel des contenus du
 * jour.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * `HomeScreen` est une fonction `@Composable` : la déclencher demande un hôte Compose, un appareil
 * ou un émulateur, et le projet n'a aucun outillage de test d'interface. Le branchement n'est donc
 * atteignable par aucun test de comportement — ce n'est pas un raccourci, c'est le seul moyen. Le
 * calcul, lui, est éprouvé pour de vrai dans `HomeQuizCardsTest` et `HomeDailyCarouselTest` : ce
 * fichier ne surveille que le câblage.
 *
 * ## Ce qui disparaîtrait sans un mot
 *
 * Cinq choses, et chacune laisserait l'application parfaitement fonctionnelle en apparence :
 *
 *  - **la composition des cartes** : `onOpenQuiz` a une valeur par défaut vide, donc retirer
 *    l'appel compile, l'accueil s'affiche, et l'écran Quiz n'a plus **aucune** porte depuis
 *    l'accueil. C'est le défaut que ce fichier existe pour attraper ;
 *  - **la transmission des rappels** : `HomeScreen` reçoit les deux rappels et les passe à
 *    `HomeContent`. Une transmission oubliée donnerait un `HomeScreen` correct — la coquille lui
 *    passe bien de quoi ouvrir le Quiz — et un corps qui n'en fait rien ;
 *  - **l'observation de l'instantané** : sans `container.quiz.state` dans la fabrique, le
 *    `ViewModel` observerait un flux qui n'existe pas, ou lèverait à la construction. Le premier
 *    cas est le plus dangereux : les cartes seraient là, avec « Question du jour », pour toujours ;
 *  - **la composition du carrousel** : `HomeUiState.daily` vaut `null` par défaut, donc retirer le
 *    bloc compile et le carrousel quitte l'accueil en silence — rien d'autre ne dépend de lui ;
 *  - **l'observation des contenus du jour** : sans `container.dailyContents.state`, le carrousel
 *    resterait sur « Chargement du contenu du jour… » pour toujours. C'est le même défaut que pour
 *    les cartes, et il est plus difficile à voir : un carrousel qui charge a l'air d'un carrousel
 *    qui attend, et l'attente est un état normal.
 *
 * ## Ce qu'il ne prouve pas
 *
 * Il ne prouve pas que les cartes sont belles, ni que leurs libellés sont les bons : les mots
 * vivent dans `QuizText` et `DailyContentsText`, et ce sont `HomeQuizCardsTest` et
 * `HomeDailyCarouselTest` qui les mesurent.
 */
class HomeScreenWiringTest {

    @Test
    fun `l'ecran compose les deux cartes de quiz`() {
        assertTrue(
            sourceDeLEcran().contains("state.quiz?.let { cards ->"),
            "L'accueil ne compose plus les cartes de quiz : l'écran Quiz n'aurait plus aucune " +
                "porte depuis l'accueil, et rien ne le dirait.",
        )
    }

    @Test
    fun `chaque carte recoit son propre geste`() {
        // Les deux cartes ne mènent pas au même endroit : l'une ouvre le Quiz, l'autre va chercher
        // un ami à défier. Les confondre — ou brancher la même lambda sur les deux — ferait de
        // « Défie tes amis » un second bouton vers le Quiz, sans que rien ne le signale.
        val source = sourceDeLEcran()

        assertTrue(
            source.contains("onQuiz = onOpenQuiz,"),
            "La carte « Quiz » n'ouvre plus l'écran Quiz : elle serait un bouton mort.",
        )
        assertTrue(
            source.contains("onFriends = onOpenFriends,"),
            "La carte « Amis » ne bascule plus vers l'onglet des amis : elle serait un bouton mort.",
        )
    }

    @Test
    fun `l'ecran transmet les deux rappels au corps`() {
        // `HomeScreen` construit le `ViewModel` ; `HomeContent` compose. Le rappel traverse donc
        // deux fonctions, et une transmission oubliée laisserait le premier correct et le second
        // muet — c'est-à-dire l'écran complet en apparence, et deux cartes sans effet.
        val source = sourceDeLEcran()

        assertTrue(
            source.contains("onOpenQuiz = onOpenQuiz,"),
            "`HomeScreen` ne transmet plus `onOpenQuiz` au corps : la coquille aurait beau le " +
                "brancher, les cartes n'en sauraient rien.",
        )
        assertTrue(
            source.contains("onOpenFriends = onOpenFriends,"),
            "`HomeScreen` ne transmet plus `onOpenFriends` au corps : même défaut que ci-dessus.",
        )
    }

    @Test
    fun `le ViewModel observe l'instantane du Quiz`() {
        // Sans cette ligne, le `ViewModel` ne saurait rien du Quiz : les cartes resteraient sur
        // « Question du jour » alors que la réponse est sur le disque, et elles ne se
        // rafraîchiraient jamais.
        val source = sourceDuViewModel()

        assertTrue(
            source.contains("quizState: StateFlow<QuizState>,"),
            "Le `ViewModel` de l'accueil ne reçoit plus l'instantané du Quiz.",
        )
        assertTrue(
            source.contains("container.quiz.state,"),
            "La fabrique ne branche plus le dépôt du Quiz : l'écran n'aurait jamais d'instantané.",
        )
    }

    @Test
    fun `le renderer recoit l'instantane, et pas seulement l'etat applicatif`() {
        // Le point exact où le calcul peut perdre sa troisième entrée : `render` accepte un
        // instantané facultatif, donc l'oublier compile et rend des cartes qui annoncent
        // éternellement la question du jour.
        assertTrue(
            sourceDuViewModel().contains("HomeRenderer.render(appState, today(), quiz.snapshot, daily)"),
            "Le `ViewModel` n'appelle plus `render` avec l'instantané : les cartes seraient " +
                "figées sur « Question du jour », quoi qu'il arrive.",
        )
    }

    // -----------------------------------------------------------------------
    // Le carrousel des contenus du jour
    // -----------------------------------------------------------------------

    @Test
    fun `l'ecran compose le carrousel des contenus du jour`() {
        // `HomeUiState.daily` vaut `null` par défaut, donc retirer ce bloc compile, l'accueil
        // s'affiche — et le carrousel disparaît de l'écran sans qu'aucune erreur ne le dise.
        assertTrue(
            sourceDeLEcran().contains("state.daily?.let { carousel ->"),
            "L'accueil ne compose plus le carrousel des contenus du jour.",
        )
    }

    @Test
    fun `le carrousel recoit son propre geste`() {
        // Le geste a une valeur par défaut vide : le remplacer par une lambda vide compile, et
        // ferait une vignette qui s'ouvre — mais pas sur le contenu qu'elle annonce.
        assertTrue(
            sourceDeLEcran().contains("TodayContents(carousel = carousel, onOpen = onOpenDaily)"),
            "Le carrousel n'ouvre plus le contenu qu'il annonce : ce serait une vignette morte.",
        )
    }

    @Test
    fun `l'ecran transmet le geste du carrousel au corps`() {
        // Même défaut que pour les cartes de quiz : le rappel traverse deux fonctions, et une
        // transmission oubliée laisserait la coquille correcte et le corps muet.
        assertTrue(
            sourceDeLEcran().contains("onOpenDaily = onOpenDaily,"),
            "`HomeScreen` ne transmet plus `onOpenDaily` au corps : le carrousel serait inerte.",
        )
    }

    @Test
    fun `le ViewModel observe les contenus du jour`() {
        // Sans cette ligne, le carrousel resterait sur « Chargement du contenu du jour… » pour
        // toujours, quoi qu'il arrive — et c'est le seul défaut du carrousel qui ne lève rien.
        val source = sourceDuViewModel()

        assertTrue(
            source.contains("dailyState: StateFlow<DailyContentsState>,"),
            "Le `ViewModel` de l'accueil ne reçoit plus les contenus du jour.",
        )
        assertTrue(
            source.contains("container.dailyContents.state,"),
            "La fabrique ne branche plus le dépôt des contenus du jour.",
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

    private fun sourceDeLEcran(): String = source("HomeScreen.kt")

    private fun sourceDuViewModel(): String = source("HomeViewModel.kt")
}
