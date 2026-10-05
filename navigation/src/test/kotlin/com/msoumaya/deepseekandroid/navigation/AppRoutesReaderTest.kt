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
 * déclaré — `lecteur?verset={verset}&seance={seance}&de={de}&a={a}` — et non la route concrète
 * que l'appelant a écrite. Un ensemble qui ne contiendrait que `lecteur` ne reconnaîtrait donc
 * plus le lecteur dès que celui-ci reçoit un argument : deux barres s'afficheraient par-dessus
 * un écran qui gère ses propres marges, et le défaut ne se verrait qu'à l'exécution, sur un
 * écran ouvert depuis l'accueil.
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
    fun `la route du lecteur porte la seance et ses bornes`() {
        assertEquals(
            "lecteur?verset=746&seance=2026-10-05-746-0&de=746&a=750",
            AppRoutes.readerRoute(
                verseId = 746,
                sessionId = "2026-10-05-746-0",
                from = 746,
                to = 750,
            ),
        )
    }

    @Test
    fun `une seance sans bornes ne produit pas de route de seance`() {
        // Les trois arguments voyagent **ensemble**. Une séance sans ses bornes ne serait pas
        // ouvrable — le lecteur ne saurait pas quelle page ouvrir. La route rend donc la lecture
        // libre, qui ne promet rien, plutôt qu'une séance qui annoncerait une plage inventée :
        // le second défaut serait muet, puisqu'il produirait une page plausible.
        val route = AppRoutes.readerRoute(sessionId = "s1")
        assertEquals(AppRoutes.READER, route)
        assertFalse(route.contains(AppRoutes.READER_SESSION))
    }

    @Test
    fun `le motif declare les six arguments facultatifs`() {
        // L'argument est **facultatif** : c'est ce qui permet à la route nue de rester valide
        // tout en étant servie par le motif. Un argument requis ferait échouer l'ouverture
        // libre, et l'échec serait une exception de navigation, pas un écran vide.
        //
        // La comparaison est **exacte**, et non partielle : un motif déclaré à moitié laisserait
        // passer un argument oublié, donc une séance — ou une révision — qui s'ouvre sans que le
        // lecteur sache laquelle, et il la servirait comme une lecture libre, sans le dire.
        assertEquals(
            "lecteur?verset={verset}&seance={seance}&de={de}&a={a}&tache={tache}&categorie={categorie}",
            AppRoutes.READER_PATTERN,
        )
    }

    @Test
    fun `la base d'une route parametree est la route nue`() {
        assertEquals(AppRoutes.READER, AppRoutes.baseRoute(AppRoutes.readerRoute(746)))
        assertEquals(
            AppRoutes.READER,
            AppRoutes.baseRoute(AppRoutes.readerRoute(746, "s1", 746, 750)),
        )
        assertEquals(AppRoutes.READER, AppRoutes.baseRoute(AppRoutes.READER))
        assertEquals(AppRoutes.READER, AppRoutes.baseRoute(AppRoutes.READER_PATTERN))
        assertNull(AppRoutes.baseRoute(null))
    }

    @Test
    fun `le lecteur reste plein ecran avec son argument`() {
        assertTrue(AppRoutes.isFullScreen(AppRoutes.READER_PATTERN))
        assertTrue(AppRoutes.isFullScreen(AppRoutes.readerRoute(746)))
        assertTrue(AppRoutes.isFullScreen(AppRoutes.readerRoute(746, "s1", 746, 750)))
        assertTrue(AppRoutes.isFullScreen(AppRoutes.READER))
        assertFalse(AppRoutes.isFullScreen(AppDestination.HOME.route))
    }

    @Test
    fun `le lecteur va jusqu'aux bords avec son argument`() {
        assertTrue(AppRoutes.isEdgeToEdge(AppRoutes.READER_PATTERN))
        assertTrue(AppRoutes.isEdgeToEdge(AppRoutes.readerRoute(746)))
        assertTrue(AppRoutes.isEdgeToEdge(AppRoutes.readerRoute(746, "s1", 746, 750)))
        assertFalse(AppRoutes.isEdgeToEdge(AppDestination.QURAN.route))
    }

    @Test
    fun `la barre basse est masquee par le motif comme par la route`() {
        assertTrue(AppRoutes.hidesBottomBar(AppRoutes.READER_PATTERN))
        assertTrue(AppRoutes.hidesBottomBar(AppRoutes.readerRoute(1)))
        assertTrue(AppRoutes.hidesBottomBar(AppRoutes.readerRoute(1, "s1", 1, 5)))
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
        assertNull(AppDestination.fromRoute(AppRoutes.readerRoute(746, "s1", 746, 750)))
        assertEquals(AppDestination.HOME, AppDestination.fromRoute(AppDestination.HOME.route))
    }
}
