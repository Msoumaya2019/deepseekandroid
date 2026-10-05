package com.msoumaya.deepseekandroid.feature.sources

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient l'**entrée « Actions de la séance »** du sélecteur de présentation.
 *
 * ## Pourquoi cette entrée existe, et pourquoi elle est surveillée
 *
 * C'est la **seconde porte** du panneau de séance, et pour une révision c'est la seule : le
 * bandeau du lecteur ouvre la feuille de validation, pas le panneau. Si cette entrée disparaissait,
 * la barre de notation — trois notes et deux gestes d'écoute — deviendrait inatteignable, et rien
 * ne le dirait : le domaine reste vert, la compilation passe, et le seul symptôme est un panneau
 * qui ne s'ouvre plus.
 *
 * ## Pourquoi un contrôle de forme
 *
 * Le sélecteur est une fonction `@Composable` : la déclencher demande un hôte Compose. Et la
 * décision qui compte — **quand** l'entrée existe — n'est pas ici : elle est dans la route, qui
 * seule sait si le lecteur sert une tâche. Ce fichier ne mesure donc que la forme du passage :
 * l'entrée est gardée par un `null`, son libellé vient du domaine, et la référence et l'ouverture
 * voyagent ensemble.
 */
class QuranSourcePickerEntryTest {

    @Test
    fun `le selecteur accepte une entree de seance, ou rien`() {
        // Un paramètre **facultatif**, et c'est la règle du dépôt : une entrée sans destination est
        // retirée, et non grisée. Le sélecteur est ouvert aussi bien pendant une lecture libre, où
        // il n'a rien à proposer de tel.
        assertTrue(
            sourceDuSelecteur().contains("sessionActions: SessionActions? = null,"),
            "Le sélecteur n'accepte plus l'entrée de la séance : le panneau perdrait sa seconde " +
                "porte, et la barre de notation deviendrait inatteignable pour une révision.",
        )
    }

    @Test
    fun `une entree absente ne laisse pas de bouton vide`() {
        // `?.let` et non un bouton désactivé : une entrée grisée laisse croire que l'action existe
        // et qu'elle est momentanément indisponible, alors qu'il n'y a rien à ouvrir.
        assertTrue(
            sourceDuSelecteur().contains("sessionActions?.let { actions ->"),
            "L'entrée de la séance n'est plus gardée par sa présence : un bouton sans " +
                "destination pourrait s'afficher.",
        )
    }

    @Test
    fun `la reference voyage, et le libelle est construit par le domaine`() {
        // Le libellé est bâti par `QuranDownloadText.sessionActions`, à côté des autres mots de ce
        // sélecteur. Le composer ici déplacerait une règle de texte chez la vue, et deux appelants
        // finiraient par écrire deux libellés pour la même entrée.
        val source = sourceDuSelecteur()
        assertTrue(
            source.contains("QuranDownloadText.sessionActions(actions.reference)"),
            "Le libellé de l'entrée ne vient plus du domaine : il pourrait diverger de celui de " +
                "l'autre client.",
        )
        assertTrue(
            source.contains("class SessionActions(val reference: String, val onOpen: () -> Unit)"),
            "La référence et l'ouverture ne voyagent plus ensemble : on pourrait n'en passer " +
                "qu'une, et obtenir soit un bouton sans nom, soit une entrée sans effet.",
        )
        // Deux paramètres facultatifs auraient laissé croire qu'on peut n'en passer qu'un. Le type
        // les rend inséparables, et c'est ce que cette absence surveille.
        assertFalse(
            source.contains("sessionReference:"),
            "La référence est redevenue un paramètre à part : une entrée sans nom devient " +
                "possible.",
        )
    }

    /**
     * Le source du sélecteur.
     *
     * Deux chemins sont essayés, et non un seul : la tâche `Test` de Gradle s'exécute dans le
     * dossier **du module**, alors qu'un contrôle joué à la main depuis la racine du dépôt part
     * de là.
     */
    private fun sourceDuSelecteur(): String {
        val relatif = "src/main/kotlin/com/msoumaya/deepseekandroid/feature/sources/QuranSourcePicker.kt"
        val candidats = listOf(File(relatif), File("feature/sources/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "QuranSourcePicker.kt introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }
}
