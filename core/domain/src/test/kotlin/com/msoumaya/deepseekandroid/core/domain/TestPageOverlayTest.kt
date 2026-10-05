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
 *
 * ## Les trois champs de séance, et le quatrième écarté
 *
 * `session`, `sessionDone` et `sessionColor` portent les **repères de marge** : la suite des clés
 * **dans l'ordre** (une place par verset), le nombre de repères pleins, et la teinte. Le
 * quatrième champ de l'original — `sessionThrough` — n'est pas envoyé : mesuré, le document ne le
 * lit nulle part. Le bandeau de séance, lui, est rendu en Compose au-dessus de la page : il n'est
 * pas dans ce message du tout, et le porter ici ferait deux bandeaux pour une seule séance.
 */
class TestPageOverlayTest {

    @Before
    fun setUp() = QuranFixture.install()

    private fun markers(
        selected: Int? = 8,
        playing: Int? = 1,
        bookmarks: Set<Int> = setOf(8),
        difficult: Set<Int> = setOf(1),
        session: List<Int> = emptyList(),
        sessionDone: Int = 0,
        sessionColor: String? = null,
    ) = TestPageOverlay.Markers(
        selected = selected,
        playing = playing,
        bookmarks = bookmarks,
        difficult = difficult,
        selecting = false,
        session = session,
        sessionDone = sessionDone,
        sessionColor = sessionColor,
        background = "#faf7f2",
        primary = "#153F36",
        selection = "#EAF2EC",
        gold = "#B39559",
    )

    @Test
    fun `le message porte exactement les champs que le document lit`() {
        assertEquals(
            """{"enabled":true,"selecting":false,"playing":"1:1","selected":"2:1",""" +
                """"bookmarks":["2:1"],"difficulty":["1:1"],"session":["2:1","2:2","2:3"],""" +
                """"sessionDone":1,"sessionColor":"#246B48","primary":"#153F36",""" +
                """"selection":"#EAF2EC","gold":"#B39559","background":"#faf7f2"}""",
            TestPageOverlay.json(
                markers(session = listOf(8, 9, 10), sessionDone = 1, sessionColor = "#246B48"),
            ),
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

    // --- les repères de marge ---

    @Test
    fun `les trois champs de la seance sont envoyes, et le bandeau ne l'est pas`() {
        // Le document lit `session` (pour ranger chaque clé à sa place), `sessionDone` (pour
        // savoir quels repères sont pleins) et `sessionColor` (avec repli sur `primary`).
        // `sessionThrough` n'est lu **nulle part** : l'envoyer serait un champ que personne ne
        // lit, donc du faux.
        val json = TestPageOverlay.json(
            markers(session = listOf(8, 9), sessionDone = 1, sessionColor = "#246B48"),
        )
        assertTrue(json.contains(""""session":["2:1","2:2"]"""), json)
        assertTrue(json.contains(""""sessionDone":1"""), json)
        assertTrue(json.contains(""""sessionColor":"#246B48""""), json)
        assertFalse(json.contains("sessionThrough"), json)
        // Le bandeau de séance est rendu en Compose, au-dessus de la page : le porter ici ferait
        // deux bandeaux pour une seule séance.
        assertFalse(json.contains(""""study""""), json)
    }

    @Test
    fun `une seance vide est ecrite comme une liste vide, jamais absente`() {
        // Le document fait `readerState.session||[]` : un champ absent passerait donc, mais par
        // accident. On l'écrit quand même, pour que le contrat se lise.
        val json = TestPageOverlay.json(markers())
        assertTrue(json.contains(""""session":[]"""), json)
        assertTrue(json.contains(""""sessionDone":0"""), json)
        assertTrue(json.contains(""""sessionColor":null"""), json)
    }

    @Test
    fun `un verset hors du corpus garde sa place dans la seance au lieu de la decaler`() {
        // `session` est une suite **numérotée**, et non un ensemble : le document y range chaque
        // clé à sa position, et c'est cette position qui donne au repère son numéro. Omettre un
        // verset — ce que `mapNotNull` fait pour les signets — décalerait tous les suivants d'un
        // cran, et le document annoncerait alors les numéros d'autres versets : un décalage
        // **plausible**, donc invisible, exactement comme une page fausse.
        val json = TestPageOverlay.json(markers(session = listOf(8, 99_999, 9)))
        assertTrue(json.contains(""""session":["2:1",null,"2:2"]"""), json)
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
                session = listOf(1, 8, 6236),
                sessionDone = 2,
                sessionColor = "#246B48",
            ),
        )
        val surs = Regex("""^[A-Za-z0-9#:\[\]{}",]+$""")
        assertTrue(surs.matches(json), json)
    }
}
