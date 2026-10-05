package com.msoumaya.deepseekandroid.feature.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient le **branchement de la coquille d'étude** dans le lecteur.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * Le lecteur est une fonction `@Composable` : la déclencher demande un hôte Compose, un appareil
 * ou un émulateur, et le projet n'a aucun outillage de test d'interface.
 *
 * ## Ce qui disparaîtrait sans un mot
 *
 *  - **Le bandeau n'existe que si la validation est branchée.** C'est la règle du lecteur — aucun
 *    bouton mort — et elle est écrite en une ligne : `study?.takeIf { onValidateStudy != null }`.
 *    Si le `takeIf` disparaissait, le bandeau s'afficherait, s'annoncerait comme une séance, et
 *    son geste n'ouvrirait rien. Rien, nulle part, ne le dirait : le paramètre `onValidateStudy`
 *    a pour valeur par défaut `null`, et un appelant qui l'omettrait obtiendrait un bandeau
 *    décoratif sans qu'aucun avertissement ne soit émis.
 *  - **La feuille reçoit la page affichée.** Le point d'arrêt proposé en dépend : c'est la fin de
 *    la page qu'on vient de lire. Une page fausse proposerait un verset d'un autre endroit, et le
 *    verset serait plausible — donc jamais signalé.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que le passage est écrit. Il ne prouve pas qu'un bandeau s'affiche au bon endroit :
 * cela se lit dans `ReaderScreen`, et la règle qui décide *ce que le bandeau dit* est éprouvée
 * dans `core:domain` (`StudySessionTest`).
 */
class StudyChromeWiringTest {

    @Test
    fun `le lecteur recoit la seance et de quoi la valider`() {
        val source = sourceDuLecteur()
        assertTrue(
            source.contains("study: StudyChromeState? = null,"),
            "Le lecteur ne reçoit plus la séance : aucun bandeau ne s'afficherait, et rien ne " +
                "pourrait être validé.",
        )
        assertTrue(
            source.contains("onValidateStudy: ((Int, ReviewGrade) -> Unit)? = null,"),
            "Le lecteur ne reçoit plus de quoi valider la séance.",
        )
    }

    @Test
    fun `le bandeau n'existe que si la validation est branchee`() {
        // La règle du lecteur, et elle tient en une ligne. Sans elle, un appelant qui omet
        // `onValidateStudy` obtiendrait un bandeau qui s'annonce comme une séance et dont le
        // geste ne mène nulle part — un bouton mort, exactement ce que ce lecteur refuse.
        assertTrue(
            sourceDuLecteur().contains("val seance = study?.takeIf { onValidateStudy != null }"),
            "Le bandeau ne dépend plus de la validation : il pourrait s'afficher sans que rien " +
                "ne puisse être validé.",
        )
    }

    @Test
    fun `le bandeau est pose dans la colonne et avant la page`() {
        // **Dans** la colonne : c'est ce qui lui fait prendre sa hauteur, donc ce qui laisse la
        // page entière. Le poser par-dessus masquerait le premier verset — celui qu'on vient de
        // commencer à apprendre.
        //
        // L'ancre est `banner = seance.banner,` et non la signature d'appel entière : le bandeau a
        // reçu une **seconde** ligne, le branchement de son geste, et une ancre écrite sur
        // `StudyBanner(banner = …` serait tombée pour une raison qui n'a rien à voir avec ce que
        // ce contrôle mesure. C'est le piège documenté du dépôt : une ancre encode souvent ce
        // qu'on **attendait lire**, pas ce que l'artefact **dit**.
        val source = sourceDuLecteur()
        val colonne = source.indexOf("BoxWithConstraints(")
        val bandeau = source.indexOf("banner = seance.banner,")
        assertTrue(colonne >= 0, "Le lecteur n'a plus de colonne de page.")
        assertTrue(bandeau >= 0, "Le bandeau n'est plus posé dans le lecteur.")
        assertTrue(
            bandeau < colonne,
            "Le bandeau est posé après la page : il la recouvrirait au lieu de prendre sa " +
                "hauteur, et le premier verset serait masqué.",
        )
    }

    @Test
    fun `le geste du bandeau suit le genre de la tache`() {
        // Le client d'origine écrit `reader.consolidation ? setSessionPanel('session') :
        // setCompletionOpen(true)`. La branche compte, et pas seulement son existence : une étape
        // de consolidation se valide **dans le panneau de séance**, où vit son bouton, tandis
        // qu'une séance ou une révision ouvre directement la feuille du point d'arrêt. L'aplatir
        // sur `completionOpen = true` rendrait une consolidation invalidable, et rien à l'écran
        // ne le dirait — le bouton existe, mais nulle part où l'atteindre.
        val geste = gesteDuBandeau()
        assertTrue(
            geste.contains("seance.request.consolidation"),
            "Le geste du bandeau ne distingue plus une consolidation : son bouton de validation " +
                "serait inatteignable.",
        )
        assertTrue(
            geste.contains("panel = ReaderPanel.SESSION"),
            "Le geste du bandeau n'ouvre plus le panneau de séance pour une consolidation.",
        )
        assertTrue(
            geste.contains("completionOpen = true"),
            "Le geste du bandeau n'ouvre plus la feuille de validation pour une séance ou une " +
                "révision.",
        )
    }

    /**
     * Le geste du bandeau, et lui seul.
     *
     * **Borné**, et pour une raison mesurée : `completionOpen = true` apparaît deux fois de plus
     * dans le fichier — dans le panneau de séance, où c'est le geste qui ouvre la feuille. Une
     * recherche sur le fichier entier resterait donc verte même si le bandeau n'ouvrait plus rien.
     *
     * La borne est accompagnée de son propre contrôle : `substringBefore` **sans repli** rend la
     * chaîne entière quand le délimiteur est absent, et le bloc lu serait alors le fichier — où
     * l'autre `completionOpen` se trouve justement **après** celui du bandeau.
     */
    private fun gesteDuBandeau(): String {
        val source = sourceDuLecteur()
        val marqueur = "onPress = {"
        val debut = source.indexOf(marqueur)
        assertTrue(debut >= 0, "Le bandeau n'a plus de geste.")
        val bloc = source.substring(debut).substringBefore("\n            )")
        assertTrue(
            bloc.length < source.length,
            "La borne du geste n'a pas mordu : le bloc lu est le fichier entier, et le " +
                "`completionOpen` trouvé serait celui du panneau de séance.",
        )
        return bloc
    }

    @Test
    fun `la feuille recoit la page affichee`() {
        // Le point d'arrêt proposé par défaut est la fin de la page affichée. Une autre page
        // proposerait un verset d'un autre endroit — et il serait plausible, donc invisible.
        //
        // La recherche est **bornée à l'appel de la feuille**. Le fichier contient un second
        // `currentPage = page,` — celui du sélecteur de sourate, plus haut — et une recherche
        // globale restait donc verte quand la feuille recevait une page **constante**. C'est le
        // falsificateur qui l'a dit, pas une relecture : la mutation `currentPage = 1,` ne
        // faisait tomber **aucun** test.
        val feuille = appelDeLaFeuille()
        assertTrue(
            feuille.contains("currentPage = page,"),
            "La feuille ne reçoit plus la page affichée : le point d'arrêt proposé serait " +
                "calculé sur une autre page.",
        )
    }

    /**
     * L'appel de la feuille de validation, et lui seul.
     *
     * **Borné**, et pour une raison mesurée : `currentPage = page,` apparaît aussi dans l'appel du
     * sélecteur de sourate, plus haut dans le même fichier. Une recherche sur le fichier entier
     * restait donc verte quand la feuille recevait une page constante.
     *
     * La borne est accompagnée de son propre contrôle : `substringAfter` **sans repli** rend la
     * chaîne entière quand le délimiteur est absent, et le bloc lu serait alors le fichier — où
     * l'autre `currentPage` se trouve justement **avant** celui de la feuille.
     */
    private fun appelDeLaFeuille(): String {
        val source = sourceDuLecteur()
        val marqueur = "StudyCompletionSheet("
        assertTrue(
            source.contains(marqueur),
            "Le lecteur n'ouvre plus la feuille de validation.",
        )
        val appel = source.substringAfter(marqueur).substringBefore("\n    }")
        assertTrue(
            appel.length < source.length,
            "La borne de la feuille n'a pas mordu : le bloc lu est le fichier entier, et le " +
                "`currentPage` trouvé serait celui du sélecteur de sourate.",
        )
        return appel
    }

    @Test
    fun `la feuille est gardee par la meme condition que le bandeau`() {
        // Elle ne s'ouvre que par le bandeau : la condition doit donc être la même. Un drapeau
        // `completionOpen` seul survivrait à un état sans séance — une rotation pendant que
        // l'état du compte se recharge, par exemple — et la feuille calculerait alors un point
        // d'arrêt sur une plage qui n'existe plus.
        assertTrue(
            sourceDuLecteur()
                .contains("if (completionOpen && seance != null && onValidateStudy != null) {"),
            "La feuille n'est plus gardée par la présence d'une séance : elle pourrait " +
                "s'ouvrir sur une plage qui n'existe plus.",
        )
    }

    @Test
    fun `la feuille est fermable`() {
        val source = sourceDuLecteur()
        assertTrue(
            source.contains("onClose = { completionOpen = false },"),
            "La feuille ne peut plus être refermée depuis le lecteur.",
        )
        assertFalse(
            source.contains("completionOpen = true,\n        )"),
            "La feuille reçoit une valeur constante au lieu d'un état : elle ne pourrait plus " +
                "se refermer.",
        )
    }

    /**
     * Le source du lecteur.
     *
     * Deux chemins sont essayés, et non un seul : la tâche `Test` de Gradle s'exécute dans le
     * dossier **du module**, alors qu'un contrôle joué à la main depuis la racine du dépôt part
     * de là.
     */
    private fun sourceDuLecteur(): String {
        val relatif = "src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt"
        val candidats = listOf(File(relatif), File("feature/reader/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "ReaderScreen.kt introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }
}
