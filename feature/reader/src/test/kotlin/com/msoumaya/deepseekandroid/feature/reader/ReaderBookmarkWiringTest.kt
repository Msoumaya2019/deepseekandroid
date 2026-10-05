package com.msoumaya.deepseekandroid.feature.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tient le **branchement** du mode de pose des signets dans le lecteur.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * Deux conditions sont réunies, et il faut les deux.
 *
 *  1. **Le comportement est hors de portée.** Le lecteur est une fonction `@Composable` : la
 *     déclencher demande un hôte Compose, un appareil ou un émulateur, et le projet n'a aucun
 *     outillage de test d'interface. Le branchement n'est donc atteignable par aucun test de
 *     comportement — ce n'est pas un raccourci, c'est le seul moyen.
 *  2. **Sa disparition serait silencieuse.** Si le bouton de la coquille cessait d'être rendu,
 *     ou si le verset touché cessait d'être rapporté à l'appelant, **rien** ne le dirait : le
 *     domaine reste vert (`BookmarksPanelTest`, `ReaderTouchTest` ne connaissent pas le
 *     lecteur), la compilation passe, et le seul symptôme est un bouton absent, ou un appui en
 *     mode de pose qui ne fait rien. Personne ne s'en apercevrait avant de le chercher à la main.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que **le branchement est écrit**. Il ne prouve pas que le panneau s'affiche
 * correctement — cela se lit dans un diff, et c'est dit dans `ARCHITECTURE.md`. Il ne prouve pas
 * non plus que les décisions du panneau sont justes : c'est `BookmarksPanelTest`, dans
 * `core:domain`.
 *
 * ## Le cas qui compte le plus
 *
 * `l'armer referme le panneau` n'est pas une coquetterie de mise en page. Le panneau est une
 * fenêtre de dialogue : elle pose un voile sur la page. Armer le mode de pose **sans** refermer
 * le panneau demanderait de toucher un verset à travers ce voile — c'est-à-dire de ne rien
 * pouvoir faire. C'est l'erreur la plus probable de ce branchement, et la plus invisible.
 */
class ReaderBookmarkWiringTest {

    // -----------------------------------------------------------------------
    // Le bouton de la coquille
    // -----------------------------------------------------------------------

    @Test
    fun `la coquille porte le bouton des marques-pages`() {
        assertTrue(
            sourceDeChrome().contains("if (onOpenBookmarks != null) {"),
            "Le bouton des marques-pages n'est plus conditionné par sa destination : il " +
                "s'afficherait sans rien pouvoir ouvrir, ou disparaîtrait toujours.",
        )
        assertTrue(
            sourceDeChrome().contains("label = BookmarksText.PANEL_TITLE,"),
            "Le bouton ne porte plus le libellé du panneau : deux chaînes pour la même chose " +
                "finiraient par diverger.",
        )
    }

    @Test
    fun `le bouton marque l'activite par un fond, pas par une teinte`() {
        assertTrue(
            sourceDeChrome().contains(
                "background = if (bookmarkActive) colors.soft else Color.Transparent,",
            ),
            "L'état actif du bouton a changé de langage visuel : le client d'origine pose un " +
                "fond doux derrière l'action sélectionnée, et ne touche pas la teinte de l'icône.",
        )
    }

    // -----------------------------------------------------------------------
    // Le lecteur
    // -----------------------------------------------------------------------

    @Test
    fun `le lecteur accepte d'enregistrer un signet et d'ouvrir la liste`() {
        val source = sourceDuLecteur()
        assertTrue(
            source.contains("onSaveBookmark: ((Int) -> Unit)? = null,"),
            "Le lecteur ne reçoit plus de moyen d'enregistrer un signet : le mode de pose " +
                "n'aurait plus rien à quoi rapporter le verset touché.",
        )
        assertTrue(
            source.contains("onOpenBookmarks: (() -> Unit)? = null,"),
            "Le lecteur ne reçoit plus de moyen d'ouvrir la liste des signets.",
        )
    }

    @Test
    fun `la coquille du lecteur reçoit les deux branchements`() {
        val source = sourceDuLecteur()
        assertTrue(
            source.contains("onOpenBookmarks = openBookmarks,"),
            "Le bouton de la coquille n'est plus branché : le panneau ne s'ouvrirait jamais.",
        )
        assertTrue(
            source.contains("bookmarkActive = bookmarkMode,"),
            "Le bouton ne sait plus qu'il est actif : rien ne signalerait que la page attend " +
                "un verset à toucher.",
        )
    }

    @Test
    fun `le panneau des marques-pages est rendu`() {
        assertTrue(
            sourceDuLecteur().contains("ReaderPanel.BOOKMARKS -> BookmarksSheet("),
            "La destination existe, mais plus rien n'est rendu pour elle : le panneau " +
                "s'ouvrirait sur du vide.",
        )
    }

    @Test
    fun `armer le mode de pose referme le panneau`() {
        // Sans cela, le voile du panneau resterait devant la page qu'on demande de toucher :
        // le mode serait armé, et impossible à satisfaire.
        val source = sourceDuLecteur()
        assertTrue(
            source.contains("                    panel = ReaderPanel.NONE\n                    bookmarkMode = true"),
            "Armer le mode de pose ne referme plus le panneau : le voile resterait devant la " +
                "page, et le verset demandé serait intouchable.",
        )
    }

    @Test
    fun `le verset touche est rapporte a l'appelant`() {
        val source = sourceDuLecteur()
        assertTrue(
            source.contains("bookmarkMode && save != null -> {"),
            "Le mode de pose n'est plus la première branche de l'appui : en mode armé, l'appui " +
                "pourrait masquer la coquille au lieu d'enregistrer.",
        )
        assertTrue(
            source.contains("save(touched)"),
            "Le verset touché n'est plus rapporté à l'appelant : le signet ne serait jamais " +
                "enregistré, et rien ne le dirait.",
        )
        assertTrue(
            source.contains("if (touched != null) {"),
            "L'appui n'est plus protégé contre un point qui ne désigne aucun verset : le mode " +
                "se refermerait sur rien, en annonçant un enregistrement.",
        )
    }

    @Test
    fun `le mode de pose partage la regle de l'appui long`() {
        // Deux copies du calcul finiraient par désigner deux versets différents — et l'écart se
        // lirait comme un verset faux, pas comme une erreur de calcul.
        val source = sourceDuLecteur()
        assertTrue(
            source.contains("ReaderTouch.verseAt("),
            "La conversion écran → verset n'est plus confiée à la règle partagée : l'appui long " +
                "et le mode de pose peuvent désormais diverger.",
        )
        assertTrue(
            !source.contains("ReaderData.verseAtImagePoint("),
            "Le lecteur calcule de nouveau la conversion en clair : c'est la duplication que " +
                "`ReaderTouch` a précisément pour but d'éviter.",
        )
    }

    // -----------------------------------------------------------------------
    // La confirmation
    // -----------------------------------------------------------------------

    @Test
    fun `la confirmation s'efface apres la duree portee par le texte`() {
        val source = sourceDuLecteur()
        assertTrue(
            source.contains("delay(BookmarksText.SAVED_NOTICE_MS)"),
            "La confirmation ne s'efface plus toute seule : elle resterait à l'écran " +
                "indéfiniment, en annonçant un geste déjà fait.",
        )
        assertTrue(
            source.contains("BookmarksText.notice(placing = bookmarkMode, saved = savedNotice)"),
            "La notice n'est plus demandée à la règle du domaine : la priorité de la pose sur " +
                "la confirmation serait décidée à l'écran, donc nulle part.",
        )
    }

    // -----------------------------------------------------------------------
    // Lecture des sources
    // -----------------------------------------------------------------------

    /**
     * Le source du lecteur.
     *
     * Deux chemins sont essayés, et non un seul : la tâche `Test` de Gradle s'exécute dans le
     * dossier **du module**, alors qu'un contrôle joué à la main depuis la racine du dépôt part
     * de là. Un chemin unique ferait passer le test ici et échouer là-bas — ou l'inverse.
     */
    private fun sourceDuLecteur(): String = sourceDe(
        "src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
    )

    private fun sourceDeChrome(): String = sourceDe(
        "src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderChrome.kt",
    )

    private fun sourceDe(relatif: String): String {
        val candidats = listOf(File(relatif), File("feature/reader/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "Fichier introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }
}
