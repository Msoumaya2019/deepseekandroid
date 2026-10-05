package com.msoumaya.deepseekandroid.feature.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient le **branchement des marques** dans le lecteur : deux paramètres, reçus puis transmis.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * Les deux conditions sont réunies, et il faut les deux.
 *
 *  1. **Le comportement est hors de portée.** Le lecteur est une fonction `@Composable` : la
 *     déclencher demande un hôte Compose, un appareil ou un émulateur, et le projet n'a aucun
 *     outillage de test d'interface.
 *  2. **Sa disparition serait silencieuse.** Les deux paramètres ont pour valeur par défaut
 *     l'ensemble **vide**. Si `bookmarkIds = bookmarkIds` disparaissait de l'appel, le lecteur
 *     compilerait toujours, les tests du domaine resteraient verts, et la page cesserait
 *     simplement de porter ses marques. Un paramètre inutilisé ne produit aucun avertissement
 *     en Kotlin : rien, nulle part, ne le dirait.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que **le passage est écrit**. Il ne prouve pas qu'une marque s'affiche au bon
 * endroit : cela se lit dans `MushafPageView`, et la règle qui décide *quels* versets sont
 * marqués est éprouvée dans `core:domain` (`ReaderMarksTest`).
 */
class ReaderScreenMarksTest {

    @Test
    fun `le lecteur recoit les deux ensembles de marques`() {
        val source = sourceDuLecteur()
        assertTrue(
            source.contains("bookmarkIds: Set<Int> = emptySet(),"),
            "Le lecteur ne reçoit plus les versets en signet : plus aucun signet ne sera teinté " +
                "ni dessiné en marge.",
        )
        assertTrue(
            source.contains("difficultIds: Set<Int> = emptySet(),"),
            "Le lecteur ne reçoit plus les versets difficiles : plus aucun ne sera teinté en rouge.",
        )
    }

    @Test
    fun `le lecteur transmet les deux ensembles a la page`() {
        val source = sourceDuLecteur()
        assertTrue(
            source.contains("bookmarkIds = bookmarkIds,"),
            "Les signets sont reçus mais ne sont plus passés à la page : le paramètre est mort.",
        )
        assertTrue(
            source.contains("difficultIds = difficultIds,"),
            "Les versets difficiles sont reçus mais ne sont plus passés à la page.",
        )
    }

    @Test
    fun `la page ne recoit plus d'ensembles vides ecrits en dur`() {
        val source = sourceDuLecteur()
        assertFalse(
            source.contains("bookmarkIds = emptySet(),"),
            "La page reçoit de nouveau un ensemble vide écrit en dur : les signets enregistrés " +
                "ne s'afficheront jamais.",
        )
        assertFalse(
            source.contains("difficultIds = emptySet(),"),
            "La page reçoit de nouveau un ensemble vide écrit en dur : les versets difficiles " +
                "ne s'afficheront jamais.",
        )
    }

    /**
     * Le source du lecteur.
     *
     * Deux chemins sont essayés, et non un seul : la tâche `Test` de Gradle s'exécute dans le
     * dossier **du module**, alors qu'un contrôle joué à la main depuis la racine du dépôt part
     * de là. Un chemin unique ferait passer le test ici et échouer là-bas — ou l'inverse.
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
