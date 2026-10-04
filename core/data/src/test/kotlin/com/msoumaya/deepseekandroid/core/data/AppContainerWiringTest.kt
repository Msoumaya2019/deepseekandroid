package com.msoumaya.deepseekandroid.core.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Éprouve le **branchement** du conteneur, en lisant son source.
 *
 * ## Pourquoi lire le source, alors que le reste est éprouvé par le comportement
 *
 * Le conteneur ne se construit qu'avec un `Context` Android : dans une épreuve JVM, il n'y a pas
 * de `Context`, et rien de ce qu'il fait au démarrage ne peut être exécuté. Or deux choses y sont
 * invisibles à l'exécution :
 *
 *  - **la relecture des réglages d'écoute au démarrage.** Sans elle, le lecteur part des valeurs
 *    par défaut et le premier réglage écrase le choix enregistré, sans que rien ne le dise ;
 *  - **l'emplacement du document.** Un fichier rangé dans un dossier de compte ferait changer de
 *    réglages en changeant de compte, alors que la façon d'écouter tient à l'appareil.
 *
 * Ce sont des branchements, pas des calculs : le comportement du dépôt est éprouvé par
 * [com.msoumaya.deepseekandroid.core.data.repository.AudioSettingsRepositoryTest], et c'est ici
 * qu'on vérifie qu'il est atteint.
 *
 * ## Ce que ce contrôle ne dit pas
 *
 * Il ne dit **pas** que la relecture aboutit, ni que le document écrit est bien formé — seulement
 * que l'appel est écrit. Il ne dit pas non plus qu'aucun autre endroit ne range des réglages
 * d'écoute ailleurs : il n'en existe pas aujourd'hui, et un second document ne serait pas vu ici.
 */
class AppContainerWiringTest {

    @Test
    fun `le conteneur relit les reglages d'ecoute au demarrage`() {
        val source = sourceDuConteneur()

        assertTrue(
            source.contains("audioSettings.prime()"),
            "le conteneur doit lancer la relecture des reglages d'ecoute : sans elle, le lecteur " +
                "part des valeurs par defaut et le premier reglage ecrase le choix enregistre",
        )
    }

    @Test
    fun `le document des reglages est unique et hors des comptes`() {
        val source = sourceDuConteneur()

        assertTrue(
            source.contains("""File(root, "audio.json")"""),
            "le document doit etre unique et pose a la racine de l'etat, et non dans le dossier " +
                "d'un compte : la facon d'ecouter tient a l'appareil",
        )
    }

    /**
     * Le source du conteneur, tel qu'il est écrit.
     *
     * La tâche de test s'exécute dans le dossier du module ; lancée autrement, elle le fait
     * depuis la racine du dépôt. On regarde donc les deux emplacements, et l'échec nomme ceux
     * qui ont été cherchés : un chemin faux qui ne dit rien ferait passer ce contrôle pour une
     * panne de l'outillage.
     */
    private fun sourceDuConteneur(): String {
        val relatif = "src/main/kotlin/com/msoumaya/deepseekandroid/core/data/AppContainer.kt"
        val candidats = listOf(File(relatif), File("core/data/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error("AppContainer.kt introuvable. Cherche dans : " + candidats.joinToString { it.absolutePath })
        return fichier.readText()
    }
}
