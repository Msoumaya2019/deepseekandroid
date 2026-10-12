package com.msoumaya.deepseekandroid.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Les règles de route des contenus du jour, éprouvées **pour de vrai**.
 *
 * Comme [AppRoutesQuizTest], [AppRoutesReaderTest] et [AppRoutesRecitationsTest], ce fichier n'est
 * pas un contrôle de forme : [AppRoutes] est un objet de Kotlin pur, sans dépendance Android, et
 * ses règles se mesurent.
 *
 * ## Ce que ces règles protègent, et qui ne se voit pas
 *
 * La liste des contenus du jour est **plein écran** : elle n'a ni onglet ni barre supérieure, donc
 * son bouton « ‹ Retour » est son seul geste de sortie — et il ne l'est que si la route est
 * reconnue pleine écran. Or la pile de navigation rend le **motif** déclaré —
 * `quotidien?contenu={contenu}` — et non la route concrète écrite par l'appelant. Un ensemble qui
 * ne contiendrait que `quotidien` ne reconnaîtrait donc plus la liste dès que le carrousel lui
 * donne un contenu : deux barres s'afficheraient par-dessus l'écran, et le défaut ne se verrait
 * qu'à l'exécution.
 *
 * ## Le second défaut, plus silencieux encore
 *
 * `dailyRoute` doit rendre **exactement** la route nue quand on ne lui donne rien. Une route qui
 * ajouterait `?contenu=` — un argument vide — serait acceptée par la navigation, l'écran
 * s'ouvrirait normalement, et l'on aurait seulement cessé de reconnaître la liste comme plein
 * écran.
 *
 * ## Et le troisième, qui est le vrai sujet
 *
 * Le geste du carrousel n'existe que si l'identifiant **arrive** jusqu'à l'écran. Une route qui
 * perdrait son argument ouvrirait la liste **sans** la fiche visée : le geste mènerait quelque
 * part, mais pas là où il promettait — et rien ne le dirait. C'est aussi cet argument qui décide
 * de ce que fait le bouton « Retour » de la fiche.
 */
class AppRoutesDailyTest {

    @Test
    fun `la route des contenus reste nue sans argument`() {
        assertEquals(AppRoutes.DAILY, AppRoutes.dailyRoute())
        assertEquals(AppRoutes.DAILY, AppRoutes.dailyRoute(null))
    }

    @Test
    fun `la route des contenus porte le contenu a ouvrir`() {
        // La route ne fait que transporter ce qu'on lui donne : c'est l'identifiant du contenu,
        // celui que la base lui a attribué et que le carrousel transmet.
        assertEquals("quotidien?contenu=c1", AppRoutes.dailyRoute("c1"))
    }

    @Test
    fun `le motif des contenus declare l'argument facultatif`() {
        // La comparaison est **exacte**, et non partielle : un motif déclaré à moitié laisserait
        // passer un argument oublié, donc une liste qui s'ouvrirait sur elle-même alors que
        // l'appelant annonçait un contenu à ouvrir — sans le dire.
        assertEquals("quotidien?contenu={contenu}", AppRoutes.DAILY_PATTERN)
    }

    @Test
    fun `la base d'une route de contenus est la route nue`() {
        assertEquals(AppRoutes.DAILY, AppRoutes.baseRoute(AppRoutes.DAILY))
        assertEquals(AppRoutes.DAILY, AppRoutes.baseRoute(AppRoutes.DAILY_PATTERN))
        assertEquals(AppRoutes.DAILY, AppRoutes.baseRoute(AppRoutes.dailyRoute("c1")))
    }

    @Test
    fun `les contenus restent plein ecran avec leur argument`() {
        // C'est **le** point de ce fichier : la route est reconnue pleine écran par son motif, donc
        // par la base. Une liste ouverte depuis le carrousel est le cas courant, pas le cas limite.
        assertTrue(AppRoutes.isFullScreen(AppRoutes.DAILY))
        assertTrue(AppRoutes.isFullScreen(AppRoutes.DAILY_PATTERN))
        assertTrue(AppRoutes.isFullScreen(AppRoutes.dailyRoute("c1")))
        assertFalse(AppRoutes.isFullScreen(AppDestination.HOME.route))
    }

    @Test
    fun `les contenus cachent la barre basse`() {
        assertTrue(AppRoutes.hidesBottomBar(AppRoutes.DAILY))
        assertTrue(AppRoutes.hidesBottomBar(AppRoutes.DAILY_PATTERN))
        assertTrue(AppRoutes.hidesBottomBar(AppRoutes.dailyRoute("c1")))
    }

    @Test
    fun `les contenus ne vont pas jusqu'aux bords`() {
        // Une décision, et non un oubli : la liste est plein écran mais garde la marge de la barre
        // d'état, parce qu'elle porte un en-tête avec un bouton « ‹ Retour » — qui serait sous
        // l'horloge du système s'il allait jusqu'aux bords. Le lecteur, lui, gère ses propres
        // marges.
        assertFalse(AppRoutes.isEdgeToEdge(AppRoutes.DAILY_PATTERN))
        assertFalse(AppRoutes.isEdgeToEdge(AppRoutes.dailyRoute("c1")))
        assertFalse(AppRoutes.isEdgeToEdge(AppRoutes.DAILY))
    }

    @Test
    fun `la route des contenus n'est pas un onglet`() {
        // `fromRoute` sert à trouver l'onglet courant. S'il confondait la liste avec un onglet, la
        // barre basse se croirait sur un onglet et l'onglet actif serait faux.
        assertNull(AppDestination.fromRoute(AppRoutes.DAILY_PATTERN))
        assertNull(AppDestination.fromRoute(AppRoutes.dailyRoute("c1")))
        assertEquals(AppDestination.HOME, AppDestination.fromRoute(AppDestination.HOME.route))
    }

    @Test
    fun `les contenus ne sont pas un ecran d'outil`() {
        // Un écran d'outil garde la barre supérieure **avec** un retour. La liste est plein écran
        // et porte son propre retour : lui prêter un titre d'outil afficherait une seconde barre
        // au-dessus du sien.
        assertNull(AppRoutes.utilityTitle(AppRoutes.DAILY))
        assertNull(AppRoutes.utilityTitle(AppRoutes.dailyRoute("c1")))
    }
}
