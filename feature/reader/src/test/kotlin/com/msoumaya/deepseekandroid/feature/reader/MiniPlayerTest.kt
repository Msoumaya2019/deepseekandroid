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
    fun `une saisie libre abimee ne fait pas tomber le lecteur`() {
        // `resolveCount()` echoue sur ces saisies. Mieux vaut afficher une repetition illimitee
        // qu'un lecteur qui refuse de s'ouvrir parce qu'un enregistrement local est illisible.
        for (text in listOf("abc", "0", "", "1000", "2.5")) {
            assertEquals(
                INFINITE,
                countLabelOf(AudioSession(countChoice = AudioCount.CUSTOM, customCount = text)),
                "saisie : « $text »",
            )
        }
    }
}
