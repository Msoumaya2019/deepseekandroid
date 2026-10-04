package com.msoumaya.deepseekandroid.feature.reader

import com.msoumaya.deepseekandroid.core.domain.AudioCount
import com.msoumaya.deepseekandroid.core.domain.AudioSession
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ce que le mini-lecteur affiche du nombre d'écoutes.
 *
 * Peu de choses, mais une qui compte : **une préférence locale abîmée ne doit pas empêcher le
 * moushaf de s'ouvrir**. Le repli est donc éprouvé, et pas seulement écrit.
 */
class MiniPlayerTest {

    @Test
    fun `le nombre d'ecoutes par defaut s'affiche tel quel`() {
        assertEquals("3", countLabelOf(AudioSession()))
    }

    @Test
    fun `une repetition illimitee s'affiche avec le symbole d'infini`() {
        assertEquals("∞", INFINITE)
        assertEquals(INFINITE, countLabelOf(AudioSession(countChoice = AudioCount.CONTINUOUS)))
    }

    @Test
    fun `une saisie libre valide s'affiche`() {
        assertEquals(
            "42",
            countLabelOf(AudioSession(countChoice = AudioCount.CUSTOM, customCount = "42")),
        )
    }

    @Test
    fun `une saisie libre abimee s'affiche comme ce que le moteur jouera`() {
        // Le moteur ramene une saisie illisible a 1, comme le client d'origine. Afficher « ∞ »
        // annoncerait une repetition sans fin qui n'aura pas lieu : l'affichage dit ce qui se
        // passera, il ne rassure pas.
        for (text in listOf("abc", "0", "", "  ", "2.5", "-1")) {
            assertEquals(
                "1",
                countLabelOf(AudioSession(countChoice = AudioCount.CUSTOM, customCount = text)),
                "saisie : « $text »",
            )
        }
    }

    @Test
    fun `une saisie libre lisible s'affiche telle quelle, meme hors des bornes de l'ecran`() {
        // L'ecran refuse au-dela de 999, mais le moteur ne re-juge pas ce qui est deja regle :
        // afficher 1000 est exact, puisque c'est ce qui sera joue.
        assertEquals(
            "1000",
            countLabelOf(AudioSession(countChoice = AudioCount.CUSTOM, customCount = "1000")),
        )
    }
}
