package com.msoumaya.deepseekandroid.feature.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient la **forme** de l'écran des marques-pages, et le choix qui le distingue du source.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * Les deux conditions sont réunies, et il faut les deux.
 *
 *  1. **Le comportement est hors de portée.** L'écran est une fonction `@Composable` : le
 *     déclencher demande un hôte Compose, un appareil ou un émulateur, et le projet n'a aucun
 *     outillage de test d'interface.
 *  2. **Sa disparition serait silencieuse.** Ce que ce fichier porte, ce sont des **décisions de
 *     disposition** : une fenêtre plutôt qu'un remplacement, une confirmation avant la
 *     suppression, un texte borné. Aucune n'a de test de comportement possible, et toutes se
 *     perdent sans bruit dans une refonte d'écran.
 *
 * ## Les deux points que ce contrôle vise vraiment
 *
 *  - **L'écran est une fenêtre, pas un remplacement.** C'est l'écart assumé du portage : si
 *    l'écran remplaçait le lecteur, le lecteur serait **démonté**, son `DisposableEffect`
 *    libérerait le lecteur audio, et l'écoute s'arrêterait au moment précis où l'on consulte ses
 *    marques-pages. Rien d'autre ne le dirait — l'écran, lui, s'afficherait parfaitement.
 *  - **La suppression est confirmée.** Le bouton de la ligne **n'efface pas** : il arme une
 *    confirmation. Un `onDelete` branché directement ferait disparaître un signet au premier
 *    appui, et l'utilisateur ne saurait pas ce qu'il a touché.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que **la forme est écrite**. Il ne prouve pas que les lignes sont justes : c'est
 * `BookmarksScreenRulesTest`, dans `core:domain`, qui les éprouve sur les vraies données.
 */
class BookmarksScreenWiringTest {

    @Test
    fun `l'ecran est une fenetre, et non un remplacement du lecteur`() {
        assertTrue(
            sourceDeLEcran().contains("Dialog(\n        onDismissRequest = onClose,"),
            "L'écran des marques-pages n'est plus une fenêtre : s'il remplace le lecteur, celui-ci " +
                "est démonté, le lecteur audio est libéré, et l'écoute s'arrête parce qu'on " +
                "consulte ses signets.",
        )
    }

    @Test
    fun `la fenetre occupe la page`() {
        assertTrue(
            sourceDeLEcran().contains("properties = DialogProperties(usePlatformDefaultWidth = false),"),
            "La fenêtre se limite de nouveau à la largeur d'un téléphone : la liste flotterait au " +
                "milieu de l'écran au lieu de l'occuper.",
        )
    }

    @Test
    fun `la suppression demande confirmation avant de deleguer`() {
        val source = sourceDeLEcran()
        assertTrue(
            source.contains("var pendingDelete by rememberSaveable { mutableStateOf<Int?>(null) }"),
            "Le signet à supprimer n'est plus retenu en attente : la suppression partirait au " +
                "premier appui, sans confirmation.",
        )
        // Le bouton de la ligne **arme** la confirmation, et n'appelle pas la suppression. Sans
        // cette borne, la première assertion passerait encore avec un bouton branché en direct
        // sur `onDelete` — le dialogue existerait, mais personne ne l'ouvrirait.
        assertTrue(
            source.contains("onDelete = { pendingDelete = row.verseId },"),
            "Le bouton de la ligne n'arme plus la confirmation : il appelle la suppression " +
                "directement, et le dialogue ne s'ouvre jamais.",
        )
        assertTrue(
            source.contains("                        onDelete(verseId)"),
            "La confirmation n'appelle plus la suppression : le dialogue s'ouvrirait, et rien ne " +
                "serait effacé — ou l'inverse, selon le chemin.",
        )
    }

    @Test
    fun `la confirmation survit a une rotation`() {
        // `rememberSaveable` et non `remember` : une rotation pendant que le dialogue est ouvert
        // doit le retrouver. Un `remember` le refermerait sans rien dire, et la personne
        // croirait avoir annulé — ou pire, ne saurait plus si elle a confirmé.
        assertTrue(
            sourceDeLEcran().contains("rememberSaveable { mutableStateOf<Int?>(null) }"),
            "La confirmation de suppression n'est plus sauvegardée : une rotation la referme " +
                "silencieusement.",
        )
    }

    @Test
    fun `la vue ne calcule aucune ligne`() {
        // Les lignes arrivent résolues. Une vue qui appellerait `Bookmarks.rows` ou toucherait
        // `Quran` se mettrait à connaître le référentiel, et le partage qui rend le domaine
        // éprouvable sans écran tomberait.
        val source = sourceDeLEcran()
        assertFalse(
            source.contains("Bookmarks.rows("),
            "L'écran calcule lui-même ses lignes : il dépend alors du référentiel, et la règle " +
                "éprouvée dans `core:domain` n'est plus la seule à décider ce qui s'affiche.",
        )
        assertFalse(
            source.contains("Quran."),
            "L'écran touche au référentiel : une ligne qui existe n'est plus une ligne qui " +
                "s'affiche, et une référence hors corpus ferait tomber l'écran.",
        )
    }

    @Test
    fun `le texte coranique est borne et aligne a droite`() {
        val source = sourceDeLEcran()
        assertTrue(
            source.contains("maxLines = ROW_TEXT_MAX_LINES,"),
            "Le texte coranique n'est plus borné : un verset long repousserait le bouton " +
                "« Reprendre » de sa propre carte hors de l'écran.",
        )
        assertTrue(
            source.contains("textAlign = TextAlign.Right,"),
            "Le texte coranique n'est plus aligné à droite : il se lirait du mauvais côté.",
        )
    }

    @Test
    fun `la carte d'explication se distingue des lignes`() {
        // Le source pose `backgroundColor: colors.soft` sur la carte d'explication, et rien sur
        // les lignes. C'est ce contraste qui fait lire la consigne comme une consigne.
        assertTrue(
            sourceDeLEcran().contains("AppCard(background = colors.soft) {"),
            "La carte d'explication a perdu son fond doux : elle ne se distingue plus des lignes " +
                "de signets, et se lit comme l'une d'elles.",
        )
    }

    @Test
    fun `l'en-tete porte le titre et le sous-titre du source`() {
        val source = sourceDeLEcran()
        assertTrue(
            source.contains("AppTitle(text = BookmarksText.TITLE)"),
            "L'écran n'affiche plus le titre de l'écran des marques-pages.",
        )
        assertTrue(
            source.contains("text = BookmarksText.SUBTITLE,"),
            "L'écran n'affiche plus son sous-titre : « Retrouve facilement tes passages " +
                "enregistrés ».",
        )
    }

    @Test
    fun `le source lu est bien celui de l'ecran`() {
        // Garde-fou sur le fichier lui-même : si le chemin résolu désignait autre chose, les
        // contrôles ci-dessus chercheraient leurs chaînes dans le mauvais document — et
        // échoueraient, ce qui est le bon comportement, mais pour une raison trompeuse.
        assertTrue(
            sourceDeLEcran().contains("fun BookmarksScreen("),
            "Le fichier lu ne déclare pas `fun BookmarksScreen(` : le chemin résolu ne désigne " +
                "pas l'écran des marques-pages.",
        )
    }

    /**
     * Le source de l'écran des marques-pages.
     *
     * Deux chemins sont essayés, et non un seul : la tâche `Test` de Gradle s'exécute dans le
     * dossier **du module**, alors qu'un contrôle joué à la main depuis la racine du dépôt part
     * de là.
     */
    private fun sourceDeLEcran(): String {
        val relatif = "src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/BookmarksScreen.kt"
        val candidats = listOf(File(relatif), File("feature/reader/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "BookmarksScreen.kt introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }
}
