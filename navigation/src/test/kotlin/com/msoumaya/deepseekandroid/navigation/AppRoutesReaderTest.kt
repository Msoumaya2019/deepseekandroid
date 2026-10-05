package com.msoumaya.deepseekandroid.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Les règles de route du lecteur, éprouvées **pour de vrai**.
 *
 * Ce fichier n'est pas un contrôle de forme : [AppRoutes] est un objet de Kotlin pur, sans
 * dépendance Android, et ses règles se mesurent. C'est le cas favorable — là où un test de
 * comportement est possible, il faut le préférer à une lecture de source.
 *
 * ## Ce que ces règles protègent, et qui ne se voit pas
 *
 * Le lecteur doit occuper tout l'écran, sans barre supérieure ni barre basse. La visibilité de
 * ces barres se **déduit** de la route courante, et la pile de navigation rend le **motif**
 * déclaré — `lecteur?verset={verset}` — et non la route concrète que l'appelant a écrite. Un
 * ensemble qui ne contiendrait que `lecteur` ne reconnaîtrait donc plus le lecteur dès que
 * celui-ci reçoit un argument : deux barres s'afficheraient par-dessus un écran qui gère ses
 * propres marges, et le défaut ne se verrait qu'à l'exécution, sur un écran ouvert depuis
 * l'accueil.
 */
class AppRoutesReaderTest {

    @Test
    fun `la route du lecteur porte son verset`() {
        assertEquals("lecteur?verset=746", AppRoutes.readerRoute(746))
    }

    @Test
    fun `la route du lecteur reste nue sans verset`() {
        // Une ouverture libre n'a pas de verset, et lui en inventer un ferait mémoriser une
        // position que personne n'a lue. La route nue doit donc rester **exactement** la base.
        assertEquals(AppRoutes.READER, AppRoutes.readerRoute(null))
        assertEquals(AppRoutes.READER, AppRoutes.readerRoute())
    }

    @Test
    fun `le motif declare l'argument facultatif`() {
        // L'argument est **facultatif** : c'est ce qui permet à la route nue de rester valide
        // tout en étant servie par le motif. Un argument requis ferait échouer l'ouverture
        // libre, et l'échec serait une exception de navigation, pas un écran vide.
        assertEquals("lecteur?verset={verset}", AppRoutes.READER_PATTERN)
    }

    @Test
    fun `la base d'une route parametree est la route nue`() {
        assertEquals(AppRoutes.READER, AppRoutes.baseRoute(AppRoutes.readerRoute(746)))
        assertEquals(AppRoutes.READER, AppRoutes.baseRoute(AppRoutes.READER))
        assertEquals(AppRoutes.READER, AppRoutes.baseRoute(AppRoutes.READER_PATTERN))
        assertNull(AppRoutes.baseRoute(null))
    }

    @Test
    fun `le lecteur reste plein ecran avec son argument`() {
        assertTrue(AppRoutes.isFullScreen(AppRoutes.READER_PATTERN))
        assertTrue(AppRoutes.isFullScreen(AppRoutes.readerRoute(746)))
        assertTrue(AppRoutes.isFullScreen(AppRoutes.READER))
        assertFalse(AppRoutes.isFullScreen(AppDestination.HOME.route))
    }

    @Test
    fun `le lecteur va jusqu'aux bords avec son argument`() {
        assertTrue(AppRoutes.isEdgeToEdge(AppRoutes.READER_PATTERN))
        assertTrue(AppRoutes.isEdgeToEdge(AppRoutes.readerRoute(746)))
        assertFalse(AppRoutes.isEdgeToEdge(AppDestination.QURAN.route))
    }

    @Test
    fun `la barre basse est masquee par le motif comme par la route`() {
        assertTrue(AppRoutes.hidesBottomBar(AppRoutes.READER_PATTERN))
        assertTrue(AppRoutes.hidesBottomBar(AppRoutes.readerRoute(1)))
        assertTrue(AppRoutes.hidesBottomBar(AppRoutes.GOAL))
        assertFalse(AppRoutes.hidesBottomBar(AppDestination.HOME.route))
    }

    @Test
    fun `un ecran d'outil garde son titre derriere un argument`() {
        // Aucun écran d'outil ne reçoit d'argument aujourd'hui : le contrôle porte donc sur la
        // **règle de lecture**, et non sur un cas d'usage. Il est là pour que le jour où l'un
        // d'eux en reçoit un, le titre ne disparaisse pas sans un mot — un écran d'outil sans
        // titre n'a plus de bouton de retour, donc plus de sortie.
        assertEquals("Mon objectif", AppRoutes.utilityTitle(AppRoutes.GOAL))
        assertEquals("Mon objectif", AppRoutes.utilityTitle("${AppRoutes.GOAL}?x=1"))
        assertNull(AppRoutes.utilityTitle(AppDestination.HOME.route))
    }

    @Test
    fun `la route parametree du lecteur n'est pas un onglet`() {
        // `fromRoute` sert à trouver l'onglet courant. S'il confondait la route du lecteur avec
        // un onglet, la barre basse se croirait sur un onglet et l'onglet actif serait faux.
        assertNull(AppDestination.fromRoute(AppRoutes.READER_PATTERN))
        assertNull(AppDestination.fromRoute(AppRoutes.readerRoute(746)))
        assertEquals(AppDestination.HOME, AppDestination.fromRoute(AppDestination.HOME.route))
    }
}
