package com.msoumaya.deepseekandroid.core.domain

import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Éprouve l'état de surimpression envoyé au document immersif.
 *
 * C'est un **format d'échange**, et c'est ce qui justifie la précision des attentes ci-dessous :
 * le document lit ces champs par leur nom, et un nom faux ne lève rien — il fait simplement
 * qu'aucune marque n'apparaît, ou que la page garde son fond par défaut. Un défaut silencieux
 * dans un contrat se paie en le cherchant à l'œil sur une capture d'écran.
 */
class TestPageOverlayTest {

    @Before
    fun setUp() = QuranFixture.install()

    private fun markers(
        selected: Int? = 8,
        playing: Int? = 1,
        bookmarks: Set<Int> = setOf(8),
        difficult: Set<Int> = setOf(1),
    ) = TestPageOverlay.Markers(
        selected = selected,
        playing = playing,
        bookmarks = bookmarks,
        difficult = difficult,
        selecting = false,
        background = "#faf7f2",
        primary = "#153F36",
        selection = "#EAF2EC",
        gold = "#B39559",
    )

    @Test
    fun `le message porte exactement les champs que le document lit`() {
        assertEquals(
            """{"enabled":true,"selecting":false,"playing":"1:1","selected":"2:1",""" +
                """"bookmarks":["2:1"],"difficulty":["1:1"],"primary":"#153F36",""" +
                """"selection":"#EAF2EC","gold":"#B39559","background":"#faf7f2"}""",
            TestPageOverlay.json(markers()),
        )
    }

    @Test
    fun `un verset qui ne joue pas et un verset non selectionne valent null`() {
        val json = TestPageOverlay.json(markers(selected = null, playing = null))
        assertTrue(json.contains(""""playing":null"""), json)
        assertTrue(json.contains(""""selected":null"""), json)
    }

    @Test
    fun `les deux listes de marques sont toujours presentes`() {
        // Le document appelle `.includes` sur les deux sans les vérifier : une liste absente
        // ferait lever son script, et l'écran ne se mesurerait jamais.
        val json = TestPageOverlay.json(markers(bookmarks = emptySet(), difficult = emptySet()))
        assertTrue(json.contains(""""bookmarks":[]"""), json)
        assertTrue(json.contains(""""difficulty":[]"""), json)
    }

    @Test
    fun `une cle est la sourate et le verset, pas l'identifiant global`() {
        // Le document ne connaît que cette forme : ses mots portent `data-verse="2:1"`. Lui
        // envoyer « 8 » ne désignerait rien du tout, et la surimpression resterait vide.
        val premier = TestPageOverlay.json(markers(playing = 1, selected = null))
        assertTrue(premier.contains(""""playing":"1:1""""), premier)

        // Le dernier verset du Mushaf : la clé doit suivre le référentiel jusqu'au bout.
        val dernier = TestPageOverlay.json(markers(playing = 6236, selected = null))
        assertTrue(dernier.contains(""""playing":"114:6""""), dernier)
    }

    @Test
    fun `un verset hors du corpus est omis au lieu de faire tomber l'ecran`() {
        // Atteignable : les marqueurs viennent de l'état du compte, qui peut en porter un écrit
        // par une autre version. L'original lève ici ; le portage l'omet.
        val json = TestPageOverlay.json(markers(bookmarks = setOf(8, 99_999), difficult = setOf(99_999)))
        assertTrue(json.contains(""""bookmarks":["2:1"]"""), json)
        assertTrue(json.contains(""""difficulty":[]"""), json)
    }

    @Test
    fun `un marqueur orphelin n'emporte pas les marqueurs valides`() {
        val json = TestPageOverlay.json(markers(selected = 99_999, playing = 8))
        assertTrue(json.contains(""""selected":null"""), json)
        assertTrue(json.contains(""""playing":"2:1""""), json)
    }

    @Test
    fun `les cles d'une meme liste sont rangees dans un ordre fixe`() {
        // L'ordre n'importe pas au document, qui cherche par appartenance ; il importe au
        // message, qui doit être le même pour le même état — sans quoi rien de tout ceci ne
        // pourrait être éprouvé.
        val a = TestPageOverlay.json(markers(bookmarks = setOf(1, 8, 20), difficult = emptySet()))
        val b = TestPageOverlay.json(markers(bookmarks = setOf(20, 1, 8), difficult = emptySet()))
        assertEquals(a, b)
    }

    @Test
    fun `le bandeau de seance n'est pas envoye`() {
        // Le document porté n'a pas de bandeau de séance et ne lit donc aucun de ces champs.
        // Les envoyer laisserait croire que quelque chose s'en sert.
        val json = TestPageOverlay.json(markers())
        assertFalse(json.contains(""""session""""), json)
        assertFalse(json.contains("sessionThrough"), json)
        assertFalse(json.contains("sessionDone"), json)
        assertFalse(json.contains("sessionColor"), json)
    }

    @Test
    fun `la surimpression est toujours active`() {
        assertTrue(TestPageOverlay.json(markers()).contains(""""enabled":true"""))
    }

    @Test
    fun `le texte produit ne contient que des caracteres surs dans un litteral JavaScript`() {
        // Le JSON est écrit **tel quel** dans une expression JavaScript par la surface WebView.
        // JSON est un sous-ensemble de JavaScript à une exception près — U+2028 et U+2029 y sont
        // des caractères de ligne — et cette exception est celle qui rend l'interpolation
        // dangereuse dès qu'un texte libre entre dans le message.
        //
        // Il n'en entre aucun : les clés viennent d'entiers du référentiel, les couleurs de
        // constantes du thème. C'est cette propriété qui rend l'écriture sûre, et elle se mesure
        // plutôt que de se supposer — c'est le contrôle qui autorise la surface à interpoler.
        val json = TestPageOverlay.json(
            markers(
                selected = 6236,
                playing = 1,
                bookmarks = setOf(1, 8, 20, 6236),
                difficult = setOf(2, 3000),
            ),
        )
        val surs = Regex("""^[A-Za-z0-9#:\[\]{}",]+$""")
        assertTrue(surs.matches(json), json)
    }
}
