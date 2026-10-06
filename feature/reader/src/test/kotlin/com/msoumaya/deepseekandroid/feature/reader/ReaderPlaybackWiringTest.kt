package com.msoumaya.deepseekandroid.feature.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient la **portée de la séance d'écoute** dans le lecteur.
 *
 * ## Le défaut que ce fichier empêche de revenir
 *
 * Le lecteur créait sa séance dans un `remember` et la libérait dans un `DisposableEffect` :
 * quitter l'écran **coupait la récitation**, et y revenir la faisait repartir du premier verset.
 * Rien ne le disait — ni la compilation, ni le domaine, ni les autres tests — parce que le
 * défaut n'est pas dans une règle : il est dans une **portée**. Reconstruire le lecteur à
 * l'identique, ou déplacer l'appel à `release()` dans un écran par commodité, réintroduirait le
 * défaut sans qu'une seule assertion rougisse.
 *
 * ## Pourquoi un contrôle de forme
 *
 * Le lecteur est un `@Composable` : l'ouvrir demande un hôte Compose, et le projet n'a aucun
 * outillage de test d'interface. La portée n'est donc atteignable par aucun test de
 * comportement. Le comportement de la séance, lui, est éprouvé pour de vrai dans
 * `AudioSessionHolderTest` (`core:playback`) — ce fichier-ci ne vérifie que le **branchement**.
 */
class ReaderPlaybackWiringTest {

    @Test
    fun `le lecteur recoit la seance au lieu de la creer`() {
        val source = sourceDuLecteur()
        assertTrue(
            source.contains("playback: AudioSessionHolder? = null,"),
            "Le lecteur ne reçoit plus la séance d'écoute : il la recréera, et elle mourra " +
                "avec l'écran.",
        )
    }

    @Test
    fun `le lecteur ne construit plus de lecteur audio`() {
        // Le jour où un `remember` reconstruit un `AudioSessionController` ici, la séance
        // appartient de nouveau à l'écran — et le défaut est de retour, en silence.
        val source = sourceDuLecteur()
        for (interdit in listOf(
            "AudioSessionController(",
            "ExoAudioOutput(",
        )) {
            assertFalse(
                source.contains(interdit),
                "Le lecteur construit `$interdit` : la séance redevient celle de l'écran, et " +
                    "quitter le lecteur coupera de nouveau la récitation.",
            )
        }
    }

    @Test
    fun `le lecteur ne libere plus la seance en partant`() {
        // C'est la moitié la plus facile à réintroduire : un `DisposableEffect` qui appelle
        // `release()` « pour libérer les ressources » a l'air soigneux, et il ramène exactement
        // le défaut. La libération appartient à la fin de l'application, pas à un écran.
        val source = sourceDuLecteur()
        assertFalse(
            source.contains("playback.release()") || source.contains("playback?.release()"),
            "Le lecteur libère la séance en partant : la récitation s'arrêtera avec l'écran. " +
                "La libération appartient au conteneur, pas à un écran.",
        )
    }

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
