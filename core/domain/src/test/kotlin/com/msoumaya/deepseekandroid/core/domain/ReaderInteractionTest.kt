package com.msoumaya.deepseekandroid.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ce qu'un glissement fait, et quelles pages restent en mémoire.
 *
 * Ces deux règles se trompent **sans le dire** : une page qui tourne alors qu'on voulait
 * regarder son bord, ou cinq pages gardées au lieu de trois, ne lèvent aucune erreur. Elles
 * ne se voient que sur l'appareil — c'est exactement pour cela qu'elles sont éprouvées ici.
 */
class ReaderInteractionTest {

    // -----------------------------------------------------------------------
    // Intention d'un glissement
    // -----------------------------------------------------------------------

    @Test
    fun `un glissement vers la droite avance`() {
        // Convention du client d'origine, portée telle quelle par `pageAfterSwipe` : c'est
        // elle qui fait foi, et le test d'accord plus bas la relie aux deux règles.
        assertEquals(DragIntent.TURN_NEXT, ReaderGesture.dragIntent(dx = 200f, dy = 0f, scale = 1f))
    }

    @Test
    fun `un glissement vers la gauche recule`() {
        assertEquals(DragIntent.TURN_PREVIOUS, ReaderGesture.dragIntent(dx = -200f, dy = 0f, scale = 1f))
    }

    @Test
    fun `un glissement trop court ne fait rien`() {
        assertEquals(DragIntent.NONE, ReaderGesture.dragIntent(dx = -30f, dy = 0f, scale = 1f))
    }

    @Test
    fun `la limite de distance est celle du client d'origine`() {
        // La borne elle-même compte : c'est elle qui décide, et une erreur d'un pixel
        // rendrait le lecteur tantôt trop sensible, tantôt inerte.
        assertEquals(DragIntent.TURN_NEXT, ReaderGesture.dragIntent(dx = 60f, dy = 0f, scale = 1f))
        assertEquals(DragIntent.NONE, ReaderGesture.dragIntent(dx = 59f, dy = 0f, scale = 1f))
    }

    @Test
    fun `un glissement surtout vertical ne tourne pas la page`() {
        // Il fait défiler le contenu : tourner la page ici serait un saut involontaire.
        assertEquals(DragIntent.NONE, ReaderGesture.dragIntent(dx = -80f, dy = 200f, scale = 1f))
    }

    @Test
    fun `le rapport horizontal est exige a la limite`() {
        // 150 pour 100 : exactement 1,5 fois. La règle exige *au moins* 1,5, donc le
        // glissement compte — la comparaison est stricte dans l'autre sens.
        assertEquals(DragIntent.TURN_PREVIOUS, ReaderGesture.dragIntent(dx = -150f, dy = 100f, scale = 1f))
        // Un cheveu en dessous et ce n'est plus un glissement de page.
        assertEquals(DragIntent.NONE, ReaderGesture.dragIntent(dx = -149f, dy = 100f, scale = 1f))
    }

    @Test
    fun `une page agrandie se deplace au lieu de tourner`() {
        // Le cas qui manquait : regarder le bord d'une page zoomée ne doit pas la perdre.
        assertEquals(DragIntent.PAN, ReaderGesture.dragIntent(dx = -200f, dy = 0f, scale = 2f))
    }

    @Test
    fun `une page agrandie se deplace meme pour un glissement court`() {
        // Agrandie, la page suit le doigt sans seuil : c'est un déplacement, pas une décision.
        assertEquals(DragIntent.PAN, ReaderGesture.dragIntent(dx = -10f, dy = 0f, scale = 2f))
    }

    @Test
    fun `une page agrandie se deplace meme si le geste est vertical`() {
        assertEquals(DragIntent.PAN, ReaderGesture.dragIntent(dx = 0f, dy = 300f, scale = 1.8f))
    }

    @Test
    fun `une echelle revenue a un par pincement ne bloque pas la page`() {
        // Après un pincement qui revient à la page entière, l'échelle calculée vaut
        // 1,0000001 et non 1. Sans tolérance, la page resterait figée en mode déplacement
        // alors qu'elle est visuellement entière : le lecteur semblerait mort.
        assertEquals(DragIntent.TURN_NEXT, ReaderGesture.dragIntent(dx = 200f, dy = 0f, scale = 1.0000001f))
        assertEquals(DragIntent.TURN_NEXT, ReaderGesture.dragIntent(dx = 200f, dy = 0f, scale = 1.005f))
    }

    @Test
    fun `juste au-dessus du seuil la page se deplace`() {
        assertEquals(DragIntent.PAN, ReaderGesture.dragIntent(dx = -200f, dy = 0f, scale = 1.02f))
    }

    @Test
    fun `le seuil de zoom est distinct du minimum de zoom`() {
        // Ils ne mesurent pas la même chose : l'un borne l'échelle demandée, l'autre décide
        // si la page est visuellement agrandie. Les confondre ramènerait le défaut ci-dessus.
        assertTrue(ReaderGesture.ZOOMED_THRESHOLD > ReaderZoomGeometry.MIN_SCALE)
    }

    @Test
    fun `aucun glissement ne rend autre chose que les quatre intentions`() {
        // Balayage systématique : il n'existe pas de cas où la fonction se taise ou mente.
        val valeurs = listOf(-1000f, -200f, -60f, -59f, 0f, 59f, 60f, 200f, 1000f)
        val echelles = listOf(1f, 1.01f, 1.5f, 3f)
        for (dx in valeurs) {
            for (dy in valeurs) {
                for (scale in echelles) {
                    val intent = ReaderGesture.dragIntent(dx, dy, scale)
                    assertTrue(
                        intent in DragIntent.entries,
                        "intention inattendue $intent pour dx=$dx dy=$dy scale=$scale",
                    )
                }
            }
        }
    }

    @Test
    fun `le glissement et la navigation par page s'accordent sur le sens`() {
        // `PageNavigation.pageAfterSwipe` décide du **numéro**, `dragIntent` décide de
        // l'**intention** : les deux doivent désigner la même direction. S'ils divergeaient,
        // la page affichée ne serait pas celle que le doigt a demandée — et c'est
        // exactement l'erreur qui a été commise puis corrigée ici.
        for (dx in listOf(-1000f, -200f, -60f, 60f, 200f, 1000f)) {
            val numero = PageNavigation.pageAfterSwipe(page = 100, dx = dx, dy = 0f)
            val intention = ReaderGesture.dragIntent(dx = dx, dy = 0f, scale = 1f)
            val attendu = when {
                numero > 100 -> DragIntent.TURN_NEXT
                numero < 100 -> DragIntent.TURN_PREVIOUS
                else -> DragIntent.NONE
            }
            assertEquals(attendu, intention, "désaccord pour dx=$dx : page $numero, intention $intention")
        }
    }

    @Test
    fun `l'intention decrit le geste, la borne est l'affaire de la navigation`() {
        // À la première page, un glissement qui reculerait reste un **vrai geste** :
        // l'intention l'annonce, et c'est `pageAfterSwipe` qui le neutralise. Ce partage
        // compte : si l'écran réimplémentait la borne de son côté, les deux règles
        // finiraient par diverger.
        assertEquals(DragIntent.TURN_PREVIOUS, ReaderGesture.dragIntent(dx = -200f, dy = 0f, scale = 1f))
        assertEquals(1, PageNavigation.pageAfterSwipe(page = 1, dx = -200f, dy = 0f), "la borne neutralise le geste")

        assertEquals(DragIntent.TURN_NEXT, ReaderGesture.dragIntent(dx = 200f, dy = 0f, scale = 1f))
        assertEquals(604, PageNavigation.pageAfterSwipe(page = 604, dx = 200f, dy = 0f), "la borne neutralise le geste")
    }

    // -----------------------------------------------------------------------
    // Fenêtre de préchargement
    // -----------------------------------------------------------------------

    @Test
    fun `la page courante vient en premier`() {
        // C'est elle qu'on attend ; les voisines ne servent qu'au glissement suivant.
        assertEquals(100, ReaderPreload.pages(100).first())
    }

    @Test
    fun `les voisines encadrent la page courante`() {
        assertEquals(listOf(100, 99, 101), ReaderPreload.pages(100))
    }

    @Test
    fun `trois pages au maximum, jamais plus`() {
        // Le moushaf pèse 118,2 Mo sur disque (118 203 707 octets, mesurés) : garder tout le
        // moushaf en mémoire fait tomber l'application. La borne est la règle, pas une
        // optimisation.
        for (page in listOf(1, 2, 100, 603, 604)) {
            assertTrue(ReaderPreload.pages(page).size <= ReaderPreload.WINDOW, "page $page")
        }
    }

    @Test
    fun `au debut du moushaf il n'y a pas de page precedente`() {
        assertEquals(listOf(1, 2), ReaderPreload.pages(1))
    }

    @Test
    fun `a la fin du moushaf il n'y a pas de page suivante`() {
        assertEquals(listOf(604, 603), ReaderPreload.pages(604))
    }

    @Test
    fun `aucune page hors du moushaf n'est prechargee`() {
        for (page in 1..604) {
            for (candidate in ReaderPreload.pages(page)) {
                assertTrue(candidate in 1..604, "page $candidate demandée depuis $page")
            }
        }
    }

    @Test
    fun `une page hors bornes est ramenee dans le moushaf`() {
        // Un état restauré peut porter une page invalide ; le lecteur doit s'ouvrir quand
        // même, sur une page réelle, plutôt que de ne rien afficher.
        assertEquals(listOf(1, 2), ReaderPreload.pages(0))
        assertEquals(listOf(604, 603), ReaderPreload.pages(700))
    }

    @Test
    fun `un moushaf d'une seule page ne donne qu'une page`() {
        assertEquals(listOf(1), ReaderPreload.pages(1, totalPages = 1))
    }

    @Test
    fun `un moushaf vide ne precharge rien`() {
        assertEquals(emptyList(), ReaderPreload.pages(1, totalPages = 0))
    }

    @Test
    fun `les pages prechargees sont distinctes et dans l'ordre`() {
        for (page in 1..604) {
            val pages = ReaderPreload.pages(page)
            assertEquals(pages.distinct(), pages, "doublon pour la page $page")
            assertEquals(page, pages.first())
        }
    }
}
