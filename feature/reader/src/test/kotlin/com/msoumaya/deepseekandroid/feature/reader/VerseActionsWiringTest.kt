package com.msoumaya.deepseekandroid.feature.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient le **branchement** du panneau des actions du verset dans le lecteur.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * Deux conditions sont réunies, et il faut les deux.
 *
 *  1. **Le comportement est hors de portée.** Le lecteur est une fonction `@Composable` : le
 *     déclencher demande un hôte Compose, un appareil ou un émulateur, et le projet n'a aucun
 *     outillage de test d'interface. Le geste n'est donc atteignable par aucun test de
 *     comportement — ce n'est pas un raccourci, c'est le seul moyen.
 *  2. **Sa disparition serait silencieuse.** Si l'appui long cessait d'ouvrir le panneau, si une
 *     action perdait sa destination, ou si le marquage se mettait à lire le mauvais ensemble,
 *     **rien** ne le dirait : le domaine reste vert (`VerseActionsTextTest` ne connaît pas le
 *     lecteur), la compilation passe, et le seul symptôme est un panneau qui ne s'ouvre pas, ou
 *     un libellé qui ment.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que **le branchement est écrit**. Il ne prouve pas que le panneau s'affiche
 * correctement — cela se lit dans un diff, et c'est dit dans `ARCHITECTURE.md`. Il ne prouve pas
 * non plus que le libellé est juste : c'est `VerseActionsTextTest`, dans `core:domain`.
 *
 * ## Les trois points qu'il vise, et pourquoi eux
 *
 *  - **Le geste ouvre le panneau.** C'est la seule porte vers les actions ; sans elle, elles
 *    n'existent pas.
 *  - **« Sélectionner un passage » reste absent.** Son entrée n'a pas de destination : la laisser
 *    revenir la ferait mener nulle part, et rien ne le dirait.
 *  - **Le marquage lit le marqueur de l'élève, et non tous les versets difficiles.** Les deux
 *    ensembles diffèrent dès qu'un professeur marque un verset, et les confondre ferait afficher
 *    « Retirer des révisions prioritaires » sur un verset que l'appui **ajouterait**.
 */
class VerseActionsWiringTest {

    @Test
    fun `l'appui long ouvre le panneau des actions`() {
        assertTrue(
            sourceDuLecteur().contains(
                "panel = if (touched != null && VerseActionsText.isUseful(verseActions)) {",
            ),
            "L'appui long ne pose plus le panneau : le geste désignerait un verset sans rien " +
                "proposer d'en faire, et rien d'autre ne le dirait.",
        )
    }

    @Test
    fun `le panneau des actions est rendu par le lecteur`() {
        assertTrue(
            sourceDuLecteur().contains("VerseActionsSheet("),
            "La destination existe, mais plus rien n'est rendu pour elle : le panneau " +
                "s'ouvrirait sur du vide.",
        )
    }

    @Test
    fun `la fiche du verset cedee la place au panneau`() {
        // La fiche n'est pas supprimée : elle est **masquée** tant que le panneau est ouvert.
        // Le contraire la laisserait sous le voile, avec un texte qui annonce un toucher que le
        // voile capterait.
        assertTrue(
            sourceDuLecteur().contains(
                "selectedVerse?.takeIf { panel != ReaderPanel.VERSE }?.let { verseId ->",
            ),
            "La fiche du verset est de nouveau dessinée sous le panneau des actions : elle " +
                "annoncerait « Toucher la page pour fermer », alors que le voile capterait ce " +
                "toucher.",
        )
    }

    @Test
    fun `ecouter joue la plage d'un seul verset`() {
        assertTrue(
            sourceDuLecteur().contains("audio.start(Range(verseId, verseId), single)"),
            "« Écouter ce verset » ne lance plus la plage d'un seul verset : il jouerait la " +
                "page, ou rien du tout, alors que le bouton annonce ce verset.",
        )
    }

    @Test
    fun `ecouter reprend les trois reglages du client d'origine`() {
        // `setCountChoice(1)` puis `settingsRef.current = {count:1, mode:'passage', autoStop:true}`
        // dans le client d'origine : les trois valeurs, et pas seulement la plage.
        assertTrue(
            sourceDuLecteur().contains("countChoice = AudioCount.ONE,"),
            "« Écouter ce verset » ne fixe plus le nombre d'écoutes à 1 : la séance " +
                "appliquerait le réglage précédent, et rejouerait le verset plusieurs fois.",
        )
    }

    @Test
    fun `repeter ouvre les reglages d'ecoute sans lancer`() {
        val source = sourceDuLecteur()
        assertTrue(
            source.contains("val passage = settings.copy(mode = RepeatMode.PASSAGE)"),
            "« Répéter ce verset » ne met plus le mode sur « passage » : la répétition " +
                "s'appuierait sur le mode précédent.",
        )
        // `panel = ReaderPanel.AUDIO` figure aussi dans la feuille d'options (`onAudio = …`).
        // Chercher la chaîne nue passerait donc même si « Répéter » cessait de l'ouvrir — un
        // contrôle qui ne mesure rien. La recherche est donc bornée à ce qui suit `onRepeat = {`,
        // où la seule occurrence légitime est celle-ci.
        assertTrue(
            source.substringAfter("onRepeat = {").contains("panel = ReaderPanel.AUDIO"),
            "« Répéter ce verset » n'ouvre plus les réglages d'écoute : le bouton ne mènerait " +
                "nulle part, alors que c'est là qu'on choisit le nombre d'écoutes.",
        )
    }

    @Test
    fun `le marquage bascule par l'appelant, et sans fermer le panneau`() {
        // Le client d'origine n'efface ni le panneau ni le verset ici : c'est ce qui permet au
        // libellé de basculer sous les yeux.
        assertTrue(
            sourceDuLecteur().contains("onMark = onMarkDifficulty?.let { mark -> { mark(verseId) } },"),
            "Le marquage ne passe plus par l'appelant : l'entrée resterait affichée sans rien " +
                "écrire, ou la bascule serait tentée dans le lecteur, qui ne connaît pas l'état.",
        )
    }

    @Test
    fun `le libelle du marquage suit le marqueur de l'eleve`() {
        assertTrue(
            sourceDuLecteur().contains("markedByUser = verseId in userMarkedIds,"),
            "Le libellé du marquage ne suit plus l'ensemble des versets marqués par l'élève : " +
                "il pourrait annoncer un retrait là où l'appui ajoute un marqueur.",
        )
    }

    @Test
    fun `le marquage n'est propose que si l'appelant sait l'ecrire`() {
        assertTrue(
            sourceDuLecteur().contains("if (onMarkDifficulty != null) add(VerseActionsText.Action.MARK)"),
            "L'entrée de marquage est ajoutée sans condition : sans destination, elle " +
                "s'afficherait et ne ferait rien.",
        )
    }

    @Test
    fun `selectionner un passage reste absent, faute de destination`() {
        assertFalse(
            sourceDuLecteur().contains("onSelectRange"),
            "« Sélectionner un passage » a une destination : le geste de désignation d'une " +
                "plage n'est pas porté, et l'entrée mènerait nulle part.",
        )
    }

    @Test
    fun `le source lu est bien celui du lecteur`() {
        // Garde-fou sur le fichier lui-même : si le chemin résolu désignait autre chose, les
        // contrôles ci-dessus chercheraient leurs chaînes dans le mauvais document — et
        // échoueraient, ce qui est le bon comportement, mais pour une raison trompeuse.
        assertTrue(
            sourceDuLecteur().contains("fun ReaderScreen("),
            "Le fichier lu ne déclare pas `fun ReaderScreen(` : le chemin résolu ne désigne pas " +
                "le lecteur.",
        )
    }

    /**
     * Le source du lecteur.
     *
     * Deux chemins sont essayés, et non un seul : la tâche `Test` de Gradle s'exécute dans le
     * dossier **du module**, alors qu'un contrôle joué à la main depuis la racine du dépôt part
     * de là. Un chemin unique ferait passer le test ici et échouer là-bas — ou l'inverse.
     *
     * L'échec nomme les chemins essayés : sans cela, « fichier introuvable » ne dit pas si le
     * fichier a bougé ou si c'est le dossier de travail qui a changé.
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
