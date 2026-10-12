package com.msoumaya.deepseekandroid.feature.dailycontents

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tient le **branchement de l'écran de liste** des contenus du jour.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * `DailyContentsScreen` est une fonction `@Composable` : la déclencher demande un hôte Compose, un
 * appareil ou un émulateur, et le projet n'a aucun outillage de test d'interface. Le câblage n'est
 * donc atteignable par aucun test de comportement — ce n'est pas un raccourci, c'est le seul moyen.
 * Le calcul, lui, est éprouvé pour de vrai dans `DailyContentsListRendererTest` : ce fichier ne
 * surveille que le branchement.
 *
 * ## Ce qui disparaîtrait sans un mot
 *
 * Cinq choses, et chacune laisserait l'application parfaitement fonctionnelle en apparence :
 *
 *  - **le pied posé après le corps** : `footer` est calculé **indépendamment** de `body`, donc le
 *    déplacer dans le `when` compile, l'écran s'affiche — et le pied disparaît dès que la liste est
 *    vide. C'est exactement l'écart n° 2 du renderer, et il ne se voit qu'à l'écran ;
 *  - **l'identifiant passé à l'ouverture** : `onOpen` a une valeur par défaut vide, donc retirer le
 *    geste compile et les cartes deviennent mortes. Passer l'objet au lieu de son identifiant
 *    compilerait aussi, si la signature changeait des deux côtés — c'est pourquoi la signature est
 *    contrôlée **avec** l'appel ;
 *  - **la règle du retour** : `detailBackExits` décide si « Retour » ressort de l'écran ou revient
 *    à la liste. La remplacer par un `onClose()` constant compilerait, et une fiche ouverte depuis
 *    la liste ferait sortir de l'écran au lieu d'y revenir ;
 *  - **la fonction de résolution des médias** : `resolve` a pour défaut l'identité, donc oublier de
 *    brancher le dépôt compile — et toutes les illustrations resteraient brutes, c'est-à-dire
 *    invisibles pour un contenu dont l'adresse est privée ;
 *  - **le partage par le sélecteur du système** : sans `ACTION_SEND`, le bouton « Partager » serait
 *    un bouton mort, et rien ne le dirait.
 *
 * ## Ce qu'il ne prouve pas
 *
 * Il ne prouve pas que l'écran est beau, ni que ses libellés sont les bons : les mots vivent dans
 * `DailyContentsText` et sont mesurés par `DailyContentsTextTest`, et leur assemblage par
 * `DailyContentsListRendererTest`.
 */
class DailyContentsScreenWiringTest {

    @Test
    fun `la fiche remplace l'ecran entier`() {
        // L'original ecrit `if(detail)return <ScrollView>…` : la fiche n'est pas une feuille posee
        // sur la liste, c'est le meme ecran dans un autre etat. Sans la sortie anticipee, la liste
        // serait composee **derriere** la fiche, et un retour laisserait deux listes empilees.
        val source = sourceDeLEcran()

        assertTrue(
            source.contains("val detail = state.detail"),
            "L'ancre a bouge : l'ecran ne lit plus `state.detail`. Ce controle ne mesure donc plus " +
                "rien, et doit etre repris avant d'etre cru.",
        )
        // La sortie anticipee se mesure **par sa position** : elle doit venir avant le calcul du
        // corps de la liste. Une comparaison de texte sur plusieurs lignes dependrait des fins de
        // ligne du fichier, et un `\r\n` la ferait echouer sans que le code ait bouge.
        //
        // L'ancre est « huit espaces puis `return` », et non le mot seul : le commentaire qui suit
        // la condition ecrit `if(detail)return <ScrollView>` en toutes lettres, et un `indexOf`
        // naif trouverait ce `return`-la — c'est-a-dire un controle vert pour la mauvaise raison.
        val debutFiche = source.indexOf("if (detail != null) {")
        val sortie = source.indexOf("        return", debutFiche)
        val corpsDeListe = source.indexOf("val header = DailyContentsListRenderer.header(state)")

        assertTrue(
            debutFiche >= 0 && sortie >= 0 && corpsDeListe >= 0,
            "L'ancre a bouge : la fiche ou le corps de liste n'est plus ecrit comme attendu. Ce " +
                "controle ne mesure donc plus rien, et doit etre repris avant d'etre cru.",
        )
        assertTrue(
            sortie < corpsDeListe,
            "La fiche ne remplace plus l'ecran : la liste est calculee avant que la fiche ne " +
                "sorte, donc elle serait composee derriere elle — et un retour laisserait deux " +
                "listes empilees.",
        )
    }

    @Test
    fun `le retour de la fiche suit la regle du renderer`() {
        // Deux sorties, une seule regle : une fiche ouverte **par son identifiant** est la raison
        // d'etre de l'ecran, donc « Retour » en ressort entierement ; une fiche ouverte depuis la
        // liste y ramene. Le remplacer par un `onClose()` constant compilerait.
        val source = sourceDeLEcran()

        assertTrue(
            source.contains("DailyContentsListRenderer.detailBackExits(initialId)"),
            "L'ancre a bouge : l'ecran ne lit plus la regle du retour. Ce controle ne mesure donc " +
                "plus rien, et doit etre repris avant d'etre cru.",
        )
        assertTrue(
            source.contains("onClose()"),
            "Une fiche ouverte par son identifiant ne ressort plus entierement de l'ecran.",
        )
        assertTrue(
            source.contains("onCloseDetail()"),
            "Une fiche ouverte depuis la liste ne ramene plus a la liste.",
        )
    }

    @Test
    fun `le pied est pose apres le corps, et non dedans`() {
        // C'est la disposition de l'original, dont le `ListFooterComponent` ne depend pas des
        // lignes : un pied remonte dans le `when` disparaitrait des que la liste est vide — et
        // c'est precisement l'etat ou il doit rester, puisque c'est celui d'une page en chargement.
        val source = sourceDeLEcran()
        val corps = source.indexOf("when (body) {")
        val pied = source.indexOf("footer?.let { label ->")

        assertTrue(
            corps >= 0 && pied >= 0,
            "L'ancre a bouge : le corps ou le pied n'est plus ecrit comme attendu. Ce controle ne " +
                "mesure donc plus rien, et doit etre repris avant d'etre cru.",
        )
        assertTrue(
            pied > corps,
            "Le pied est pose avant le corps, ou dans le `when` : il disparaitrait des que la " +
                "liste est vide, alors que c'est l'etat ou une page se charge.",
        )
    }

    @Test
    fun `le pied est inactif pendant une lecture`() {
        // L'original desactive son bouton tant que la page est en vol : appuyer deux fois
        // empilerait deux fois la meme page, et le doublon ne se verrait qu'a l'ecran.
        assertTrue(
            sourceDeLEcran().contains("enabled = !state.busy"),
            "Le bouton du pied n'est plus desactive pendant une lecture : deux appuis empileraient " +
                "deux fois la meme page.",
        )
    }

    @Test
    fun `l'ouverture d'une fiche passe un identifiant, et non un contenu`() {
        // Le rendu ne publie qu'un **jeton** : `DailyListRow.id`. L'ecran le transmet tel quel, et
        // c'est le ViewModel qui retrouve le contenu — le seul qui sache ce qu'il a en main. Une
        // signature qui prendrait un objet obligerait l'ecran a resoudre lui-meme, et deux
        // resolutions pourraient diverger.
        val source = sourceDeLEcran()

        assertTrue(
            source.contains("onOpen = { onOpen(row.id) }"),
            "Le geste d'ouverture ne passe plus l'identifiant de la ligne : la fiche s'ouvrirait " +
                "sur un contenu que l'ecran aurait resolu lui-meme.",
        )
        assertTrue(
            source.contains("onOpen: (String) -> Unit"),
            "La signature d'ouverture ne prend plus une chaine : le jeton publie par le rendu n'est " +
                "plus ce qui circule.",
        )
    }

    @Test
    fun `le depot des medias est passe par sa fonction`() {
        // La carte n'a rien a connaitre de `core:data` : elle recoit une **fonction**, et une
        // previsualisation peut alors la remplacer. Le defaut de `resolve` est l'identite, donc
        // oublier ce branchement compile — et les illustrations d'un contenu prive resteraient
        // invisibles.
        assertTrue(
            sourceDeLEcran().contains("resolve = viewModel.media::resolve"),
            "Le depot des medias n'est plus branche sur les cartes : une illustration dont " +
                "l'adresse est signee resterait brute, donc invisible.",
        )
    }

    @Test
    fun `le ViewModel est construit avec le contenu a ouvrir`() {
        // `initialId` ne sert pas qu'a ouvrir la fiche : c'est lui qui decide aussi de ce que fait
        // son bouton « Retour ». Le perdre a la construction ferait ouvrir la fiche sans savoir
        // d'ou elle vient, et « Retour » ramener a la liste au lieu de ressortir.
        assertTrue(
            sourceDeLEcran().contains(
                "DailyContentsViewModel.factory(LocalAppContainer.current, initialId)",
            ),
            "Le `ViewModel` n'est plus construit avec le contenu a ouvrir : la fiche perdrait la " +
                "regle de son bouton « Retour ».",
        )
    }

    @Test
    fun `le partage passe par le selecteur du systeme`() {
        // C'est exactement `Share.share({message})` cote React Native : un `ACTION_SEND` de texte
        // presente par le selecteur. Le titre du selecteur n'est **pas** fourni, comme dans
        // l'original, qui n'en passe aucun.
        val source = sourceDeLEcran()

        assertTrue(
            source.contains("Intent(Intent.ACTION_SEND)"),
            "Le partage n'ouvre plus le selecteur du systeme : le bouton « Partager » serait mort.",
        )
        assertTrue(
            source.contains("putExtra(Intent.EXTRA_TEXT, text)"),
            "Le partage ne porte plus son texte : le selecteur s'ouvrirait sur un message vide.",
        )
        assertTrue(
            source.contains("Intent.createChooser(send, null)"),
            "Le partage n'est plus presente par le selecteur, ou lui donne un titre que " +
                "l'original ne passe pas.",
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
        val relatif = "src/main/kotlin/com/msoumaya/deepseekandroid/feature/dailycontents/$nom"
        val candidats = listOf(File(relatif), File("feature/dailycontents/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "$nom introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }

    private fun sourceDeLEcran(): String = source("DailyContentsScreen.kt")
}
