package com.msoumaya.deepseekandroid.feature.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tient les **décisions de rendu** de la barre d'action d'une révision.
 *
 * ## Pourquoi un contrôle de forme
 *
 * La barre est une fonction `@Composable` : la déclencher demande un hôte Compose, et le projet n'a
 * aucun outillage de test d'interface. Ce qui est éprouvable sans écran l'est déjà ailleurs —
 * quels gestes existent, leurs mots, et lesquels portent un grade sont dans `ReviewTextTest`, dans
 * `core:domain`. Ce fichier ne garde donc que ce qui vit **dans le rendu**, et qui ne peut être
 * mesuré nulle part ailleurs :
 *
 *  - la **place du trait**, qui sépare les trois notes des deux gestes d'écoute ;
 *  - le **retrait** d'un geste sans destination ;
 *  - la **garde** qui empêche de dessiner une barre vide ;
 *  - le **grade transmis**, seul lien entre une note touchée et la note enregistrée.
 *
 * ## Ce que le trait protège
 *
 * Il tombe avant le quatrième rang, donc après « À retravailler » et avant « Écouter ». Le
 * déplacer ne casse rien : la barre se dessine, les cinq gestes répondent, et le trait sépare
 * simplement autre chose — deux notes, ou rien du tout. Aucun test de comportement ne le verrait,
 * et c'est pourquoi il est figé ici.
 */
class RevisionActionBarWiringTest {

    @Test
    fun `le trait separe les notes des gestes d'ecoute`() {
        val source = sourceDeLaBarre()
        assertTrue(
            source.contains("private const val SEPARATOR_BEFORE = 3"),
            "Le rang du trait de séparation a changé : il ne sépare plus les trois notes des deux " +
                "gestes d'écoute.",
        )
        assertTrue(
            source.contains("if (rang == SEPARATOR_BEFORE) Separator()"),
            "Le trait n'est plus posé sur le rang nommé : sa place serait décidée ailleurs, ou " +
                "nulle part.",
        )
    }

    @Test
    fun `un geste sans destination est retire`() {
        // La règle du dépôt, et sa raison : un geste grisé laisse croire qu'il existe et qu'il est
        // momentanément indisponible. Elle gouverne les deux gestes facultatifs de la barre —
        // « Écouter » et « Ma voix » —, et elle n'a pas changé le jour où l'enregistreur est
        // arrivé : c'est **l'appelant** qui décide s'il a de quoi les brancher, et l'absence de
        // l'un retire son geste au lieu de le laisser mener nulle part.
        assertTrue(
            sourceDeLaBarre().contains("ReviewText.actionBar(destinations.keys)"),
            "La barre ne retire plus les gestes sans destination : un geste sans effet pourrait " +
                "s'afficher.",
        )
    }

    @Test
    fun `une barre sans geste ne se dessine pas`() {
        // Un cadre vide avec un trait au milieu serait le pire des deux mondes : il prendrait la
        // place sans rien permettre.
        assertTrue(
            sourceDeLaBarre().contains("if (gestes.isEmpty()) return"),
            "La barre se dessine sans aucun geste : un cadre vide prendrait la place d'une barre " +
                "utilisable.",
        )
    }

    @Test
    fun `les trois notes partagent le rappel et lui passent leur grade`() {
        // C'est le seul endroit qui sache laquelle des trois a été touchée. Écrire un rappel par
        // note ferait trois copies d'un même branchement, et l'une des trois finirait par passer
        // le mauvais grade — un défaut qui ne se verrait qu'à la relecture de l'historique.
        assertTrue(
            sourceDeLaBarre().contains("put(action) { onGrade(grade) }"),
            "Les notes ne passent plus leur propre grade : la note enregistrée pourrait ne pas " +
                "être celle qu'on a touchée.",
        )
    }

    @Test
    fun `l'icone des hesitations est celle qui a ete mesuree`() {
        // `signal` n'existe pas dans Material Icons — mesuré dans les deux paquets livrés. Le
        // dessin d'origine porte **trois** barres de force croissante, et `SignalCellularAlt` est
        // la seule des candidates à les porter : `NetworkCell` n'en a qu'une, et une barre unique
        // se lirait comme un état quelconque plutôt que comme « quelques hésitations ».
        //
        // Le contrôle fige ce choix parce qu'il est **invisible** à la relecture : une autre icône
        // de la même famille compilerait, s'afficherait, et dirait autre chose.
        assertTrue(
            sourceDeLaBarre()
                .contains("ReviewText.Action.HESITANT -> Icons.Outlined.SignalCellularAlt"),
            "L'icône des hésitations a changé : le dessin ne dit plus la même chose que celui du " +
                "client d'origine.",
        )
    }

    /**
     * Le source de la barre.
     *
     * Deux chemins sont essayés, et non un seul : la tâche `Test` de Gradle s'exécute dans le
     * dossier **du module**, alors qu'un contrôle joué à la main depuis la racine du dépôt part
     * de là.
     */
    private fun sourceDeLaBarre(): String {
        val relatif = "src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/RevisionActionBar.kt"
        val candidats = listOf(File(relatif), File("feature/reader/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "RevisionActionBar.kt introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }
}
