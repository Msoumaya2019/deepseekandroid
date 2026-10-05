package com.msoumaya.deepseekandroid.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Éprouve la règle qui gouverne l'écran immersif : **qui a le droit d'agir**, et sur quoi.
 *
 * Trois pages peuvent être montées à la fois et chacune a son pont ; un message arrive donc
 * toujours accompagné de la page qui l'a émis. C'est cette décision-là qui est éprouvée ici —
 * et elle est éprouvée sur des pages fabriquées, parce qu'aucun de ces cas ne se distingue à
 * l'œil sur un appareil : un message de voisine accepté à tort désigne un verset qu'on ne voit
 * pas, et un message de la page courante refusé à tort laisse l'écran en chargement pour
 * toujours.
 *
 * ## Le contrôle qui porte le plus
 *
 * `error` est accepté de la page **demandée**, pas de la page **affichée** — les deux diffèrent
 * pendant un changement de page. Les confondre ferait dire « la page n'a pas pu être chargée »
 * à propos d'une page qui, elle, s'affiche très bien.
 */
class TestPageSessionTest {

    /** Un résolveur de clés fabriqué : le référentiel n'a rien à voir avec cette règle. */
    private val known: (String) -> Int? = { key -> if (key == "2:1") 8 else null }

    // --- l'analyse du message ---

    @Test
    fun `un message de page mesuree est lu avec sa page`() {
        assertEquals(
            TestPageSession.Message.Ready(7),
            TestPageSession.parse("""{"type":"ready","page":7,"words":[{"id":8}]}"""),
        )
    }

    @Test
    fun `un appui court est lu avec la cle du verset`() {
        assertEquals(
            TestPageSession.Message.Tap("2:1"),
            TestPageSession.parse("""{"type":"tap","key":"2:1"}"""),
        )
    }

    @Test
    fun `un appui court hors de tout verset est lu sans cle`() {
        assertEquals(
            TestPageSession.Message.Tap(null),
            TestPageSession.parse("""{"type":"tap","key":null}"""),
        )
    }

    @Test
    fun `un appui long est lu avec sa cle`() {
        assertEquals(
            TestPageSession.Message.LongPress("2:1"),
            TestPageSession.parse("""{"type":"longpress","key":"2:1"}"""),
        )
    }

    @Test
    fun `un balayage est lu avec ses deux distances`() {
        assertEquals(
            TestPageSession.Message.Swipe(-120.0, 10.0),
            TestPageSession.parse("""{"type":"swipe","dx":-120,"dy":10,"fromEdge":false}"""),
        )
    }

    @Test
    fun `un echec de page est lu`() {
        assertEquals(TestPageSession.Message.Failed, TestPageSession.parse("""{"type":"error","page":7}"""))
    }

    @Test
    fun `un type inconnu est ignore`() {
        assertNull(TestPageSession.parse("""{"type":"study"}"""))
    }

    @Test
    fun `un texte illisible est ignore`() {
        assertNull(TestPageSession.parse("pas du json"))
        assertNull(TestPageSession.parse("""[1,2,3]"""))
    }

    @Test
    fun `un balayage sans distance est ignore plutot que devine`() {
        // Deviner une distance ferait tourner la page au hasard — pire que de ne rien faire.
        assertNull(TestPageSession.parse("""{"type":"swipe","dy":10}"""))
    }

    @Test
    fun `une page mesuree sans numero est ignoree`() {
        assertNull(TestPageSession.parse("""{"type":"ready","words":[]}"""))
    }

    // --- ce qu'il faut charger, et ce qu'il faut garder ---

    @Test
    fun `une page non mesuree se demande seule`() {
        // Préparer les voisines d'une page qu'on attend encore ajouterait trois encodages de
        // police à l'attente, sans rien rendre plus rapide.
        assertEquals(listOf(300), TestPageSession.toLoad(page = 300, measured = false))
    }

    @Test
    fun `une page mesuree fait preparer ses deux voisines`() {
        assertEquals(listOf(299, 300, 301), TestPageSession.toLoad(page = 300, measured = true))
    }

    @Test
    fun `la premiere page mesuree n'a qu'une voisine`() {
        assertEquals(listOf(1, 2), TestPageSession.toLoad(page = 1, measured = true))
    }

    @Test
    fun `trois documents sont gardes au plus`() {
        assertEquals(setOf(299, 300, 301), TestPageSession.toKeep(300))
        assertEquals(setOf(1, 2), TestPageSession.toKeep(1))
    }

    // --- le routage : qui a le droit d'agir ---

    @Test
    fun `la page demandee qui se mesure devient la page affichee`() {
        assertEquals(
            TestPageSession.Decision.Measured(300, display = true),
            TestPageSession.route(300, current = 300, displayed = 299, selecting = false, TestPageSession.Message.Ready(300), known),
        )
    }

    @Test
    fun `une voisine qui se mesure se prepare sans s'afficher`() {
        assertEquals(
            TestPageSession.Decision.Measured(301, display = false),
            TestPageSession.route(301, current = 300, displayed = 300, selecting = false, TestPageSession.Message.Ready(301), known),
        )
    }

    @Test
    fun `une page qui annonce une autre page que la sienne est ignoree`() {
        // Une surface recyclée peut répondre pour la page qu'elle portait avant : sans cette
        // condition, son message ferait afficher une page qui n'est pas celle qu'on a demandée.
        assertEquals(
            TestPageSession.Decision.Ignored,
            TestPageSession.route(301, current = 300, displayed = 300, selecting = false, TestPageSession.Message.Ready(299), known),
        )
    }

    @Test
    fun `un echec de la page demandee est rapporte`() {
        assertEquals(
            TestPageSession.Decision.Failed,
            TestPageSession.route(300, current = 300, displayed = 299, selecting = false, TestPageSession.Message.Failed, known),
        )
    }

    @Test
    fun `un echec d'une voisine ne fait pas croire que la page lue est cassee`() {
        assertEquals(
            TestPageSession.Decision.Ignored,
            TestPageSession.route(301, current = 300, displayed = 300, selecting = false, TestPageSession.Message.Failed, known),
        )
    }

    @Test
    fun `un appui sur une voisine est ignore`() {
        assertEquals(
            TestPageSession.Decision.Ignored,
            TestPageSession.route(301, current = 300, displayed = 300, selecting = true, TestPageSession.Message.Tap("2:1"), known),
        )
    }

    @Test
    fun `un appui en mode de pose enregistre le verset designe`() {
        assertEquals(
            TestPageSession.Decision.Select(8),
            TestPageSession.route(300, current = 300, displayed = 300, selecting = true, TestPageSession.Message.Tap("2:1"), known),
        )
    }

    @Test
    fun `un appui en mode de pose sur une cle inconnue ne designe rien`() {
        assertEquals(
            TestPageSession.Decision.Tap,
            TestPageSession.route(300, current = 300, displayed = 300, selecting = true, TestPageSession.Message.Tap("9:9"), known),
        )
    }

    @Test
    fun `un appui hors du mode de pose ne designe rien`() {
        assertEquals(
            TestPageSession.Decision.Tap,
            TestPageSession.route(300, current = 300, displayed = 300, selecting = false, TestPageSession.Message.Tap("2:1"), known),
        )
    }

    @Test
    fun `un appui sur le vide ne designe rien`() {
        assertEquals(
            TestPageSession.Decision.Tap,
            TestPageSession.route(300, current = 300, displayed = 300, selecting = true, TestPageSession.Message.Tap(null), known),
        )
    }

    @Test
    fun `un appui long sur un verset ouvre ses actions`() {
        assertEquals(
            TestPageSession.Decision.Mark(8),
            TestPageSession.route(300, current = 300, displayed = 300, selecting = false, TestPageSession.Message.LongPress("2:1"), known),
        )
    }

    @Test
    fun `un appui long sur le vide ouvre les options`() {
        assertEquals(
            TestPageSession.Decision.BlankLongPress,
            TestPageSession.route(300, current = 300, displayed = 300, selecting = false, TestPageSession.Message.LongPress(null), known),
        )
    }

    @Test
    fun `un balayage de la page affichee demande a tourner`() {
        assertEquals(
            TestPageSession.Decision.Turn(-120.0, 10.0),
            TestPageSession.route(300, current = 300, displayed = 300, selecting = false, TestPageSession.Message.Swipe(-120.0, 10.0), known),
        )
    }

    @Test
    fun `un balayage d'une voisine est ignore`() {
        assertEquals(
            TestPageSession.Decision.Ignored,
            TestPageSession.route(301, current = 300, displayed = 300, selecting = false, TestPageSession.Message.Swipe(-120.0, 10.0), known),
        )
    }
}
