package com.msoumaya.deepseekandroid.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Les règles de route des récitations, éprouvées **pour de vrai**.
 *
 * Comme [AppRoutesQuizTest] et [AppRoutesReaderTest], ce fichier n'est pas un contrôle de forme :
 * [AppRoutes] est un objet de Kotlin pur, sans dépendance Android, et ses règles se mesurent.
 *
 * ## Ce que ces règles protègent, et qui ne se voit pas
 *
 * La liste des récitations est **plein écran** et n'a pas de barre de navigation : son bouton
 * « ‹ Retour » est son seul geste de sortie, et il ne l'obtient que si la route est reconnue comme
 * pleine écran. Or la pile de navigation rend le **motif** déclaré —
 * `recitations?partage={partage}` — et non la route concrète que l'appelant a écrite. Un ensemble
 * qui ne contiendrait que `recitations` ne reconnaîtrait donc plus la liste dès qu'elle reçoit un
 * argument : deux barres s'afficheraient par-dessus l'écran, et le défaut ne se verrait qu'à
 * l'exécution, sur une liste ouverte depuis le lecteur.
 *
 * ## Le second défaut, plus silencieux encore
 *
 * `recitationsRoute` doit rendre **exactement** la route nue quand on ne lui donne rien. Une route
 * qui ajouterait `?partage=` — un argument vide — serait acceptée par la navigation, l'écran
 * s'ouvrirait normalement, et l'on aurait seulement cessé de reconnaître la liste comme plein
 * écran. C'est le genre de divergence qui ne se voit que sur un appareil.
 *
 * ## Et le troisième, qui est le vrai sujet
 *
 * Le partage depuis le lecteur n'existe que si l'identifiant **arrive** jusqu'à la liste. Une route
 * qui perdrait son argument ouvrirait la liste sans ouvrir le choix d'ami : le geste mènerait
 * quelque part, mais pas là où il promettait — et rien ne le dirait.
 */
class AppRoutesRecitationsTest {

    @Test
    fun `la route des recitations reste nue sans argument`() {
        assertEquals(AppRoutes.RECITATIONS, AppRoutes.recitationsRoute())
        assertEquals(AppRoutes.RECITATIONS, AppRoutes.recitationsRoute(null))
    }

    @Test
    fun `la route des recitations porte la recitation a partager`() {
        // La route ne fait que transporter ce qu'on lui donne : c'est l'identifiant de la
        // récitation, celui que le registre lui a attribué et que le partage transmet.
        assertEquals("recitations?partage=r1", AppRoutes.recitationsRoute("r1"))
    }

    @Test
    fun `le motif des recitations declare l'argument facultatif`() {
        // La comparaison est **exacte**, et non partielle : un motif déclaré à moitié laisserait
        // passer un argument oublié, donc une liste qui s'ouvrirait sur elle-même alors que
        // l'appelant annonçait une récitation à partager — sans le dire.
        assertEquals("recitations?partage={partage}", AppRoutes.RECITATIONS_PATTERN)
    }

    @Test
    fun `la base d'une route de recitations est la route nue`() {
        assertEquals(AppRoutes.RECITATIONS, AppRoutes.baseRoute(AppRoutes.RECITATIONS))
        assertEquals(AppRoutes.RECITATIONS, AppRoutes.baseRoute(AppRoutes.RECITATIONS_PATTERN))
        assertEquals(AppRoutes.RECITATIONS, AppRoutes.baseRoute(AppRoutes.recitationsRoute("r1")))
    }

    @Test
    fun `les recitations restent plein ecran avec leur argument`() {
        // C'est **le** point de ce fichier : la route est reconnue pleine écran par son motif, donc
        // par la base. Une liste ouverte depuis le lecteur est le cas courant, pas le cas limite.
        assertTrue(AppRoutes.isFullScreen(AppRoutes.RECITATIONS))
        assertTrue(AppRoutes.isFullScreen(AppRoutes.RECITATIONS_PATTERN))
        assertTrue(AppRoutes.isFullScreen(AppRoutes.recitationsRoute("r1")))
        assertFalse(AppRoutes.isFullScreen(AppDestination.HOME.route))
    }

    @Test
    fun `les recitations cachent la barre basse`() {
        assertTrue(AppRoutes.hidesBottomBar(AppRoutes.RECITATIONS))
        assertTrue(AppRoutes.hidesBottomBar(AppRoutes.RECITATIONS_PATTERN))
        assertTrue(AppRoutes.hidesBottomBar(AppRoutes.recitationsRoute("r1")))
    }

    @Test
    fun `les recitations ne vont pas jusqu'aux bords`() {
        // Une décision, et non un oubli : la liste est plein écran mais garde la marge de la barre
        // d'état, parce qu'elle porte un en-tête avec un bouton « ‹ Retour » — qui serait sous
        // l'horloge du système s'il allait jusqu'aux bords. Le lecteur, lui, gère ses propres
        // marges.
        assertFalse(AppRoutes.isEdgeToEdge(AppRoutes.RECITATIONS_PATTERN))
        assertFalse(AppRoutes.isEdgeToEdge(AppRoutes.recitationsRoute("r1")))
        assertFalse(AppRoutes.isEdgeToEdge(AppRoutes.RECITATIONS))
    }

    @Test
    fun `la route des recitations n'est pas un onglet`() {
        // `fromRoute` sert à trouver l'onglet courant. S'il confondait la liste avec un onglet, la
        // barre basse se croirait sur un onglet et l'onglet actif serait faux.
        assertNull(AppDestination.fromRoute(AppRoutes.RECITATIONS_PATTERN))
        assertNull(AppDestination.fromRoute(AppRoutes.recitationsRoute("r1")))
        assertEquals(AppDestination.HOME, AppDestination.fromRoute(AppDestination.HOME.route))
    }
}
