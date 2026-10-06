package com.msoumaya.deepseekandroid.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Les règles de route du Quiz, éprouvées **pour de vrai**.
 *
 * Comme [AppRoutesReaderTest], ce fichier n'est pas un contrôle de forme : [AppRoutes] est un objet
 * de Kotlin pur, sans dépendance Android, et ses règles se mesurent. C'est le cas favorable.
 *
 * ## Ce que ces règles protègent, et qui ne se voit pas
 *
 * Le Quiz est **plein écran** et n'a pas de barre de navigation : son bouton « ← » est son seul
 * geste de sortie, et il ne l'obtient que si la route est reconnue comme pleine écran. Or la pile
 * de navigation rend le **motif** déclaré — `quiz?ami={ami}&defi={defi}` — et non la route
 * concrète que l'appelant a écrite. Un ensemble qui ne contiendrait que `quiz` ne reconnaîtrait
 * donc plus le Quiz dès qu'il reçoit un argument : deux barres s'afficheraient par-dessus l'écran,
 * et le défaut ne se verrait qu'à l'exécution, sur un écran ouvert depuis « 🏆 Défier ».
 *
 * ## Le second défaut, plus silencieux encore
 *
 * `quizRoute` doit rendre **exactement** la route nue quand on ne lui donne rien. Une route qui
 * ajouterait `?ami=&defi=` — deux arguments vides — serait acceptée par la navigation, l'écran
 * s'ouvrirait normalement, et l'on aurait seulement cessé de reconnaître le Quiz comme plein
 * écran. C'est le genre de divergence qui ne se voit que sur un appareil.
 */
class AppRoutesQuizTest {

    @Test
    fun `la route du Quiz reste nue sans argument`() {
        assertEquals(AppRoutes.QUIZ, AppRoutes.quizRoute())
        assertEquals(AppRoutes.QUIZ, AppRoutes.quizRoute(null, null))
    }

    @Test
    fun `la route du Quiz porte l'ami a defier`() {
        // L'identifiant du **compte**, et non celui du lien d'amitié : le Quiz défie un joueur, et
        // c'est ce que le serveur attend. La route ne fait que transporter ce qu'on lui donne.
        assertEquals("quiz?ami=u1", AppRoutes.quizRoute(friendId = "u1"))
    }

    @Test
    fun `la route du Quiz porte le defi a ouvrir`() {
        assertEquals("quiz?defi=c1", AppRoutes.quizRoute(challengeId = "c1"))
    }

    @Test
    fun `la route du Quiz porte les deux arguments`() {
        // L'ordre est celui du motif, et il est stable : `ami` puis `defi`. Un ordre qui
        // changerait selon l'appel produirait deux routes distinctes pour la même intention, donc
        // deux entrées de pile pour le même écran.
        assertEquals("quiz?ami=u1&defi=c1", AppRoutes.quizRoute("u1", "c1"))
    }

    @Test
    fun `le motif du Quiz declare les deux arguments facultatifs`() {
        // La comparaison est **exacte**, et non partielle : un motif déclaré à moitié laisserait
        // passer un argument oublié, donc un Quiz qui s'ouvrirait sur son accueil alors que
        // l'appelant annonçait un défi — sans le dire.
        assertEquals("quiz?ami={ami}&defi={defi}", AppRoutes.QUIZ_PATTERN)
    }

    @Test
    fun `la base d'une route de Quiz est la route nue`() {
        assertEquals(AppRoutes.QUIZ, AppRoutes.baseRoute(AppRoutes.QUIZ))
        assertEquals(AppRoutes.QUIZ, AppRoutes.baseRoute(AppRoutes.QUIZ_PATTERN))
        assertEquals(AppRoutes.QUIZ, AppRoutes.baseRoute(AppRoutes.quizRoute("u1", "c1")))
        assertEquals(AppRoutes.QUIZ, AppRoutes.baseRoute(AppRoutes.quizRoute(friendId = "u1")))
    }

    @Test
    fun `le Quiz reste plein ecran avec ses arguments`() {
        // C'est **le** point de ce fichier : la route est reconnue pleine écran par son motif, donc
        // par la base. Un Quiz ouvert sur un ami est le cas courant, pas le cas limite.
        assertTrue(AppRoutes.isFullScreen(AppRoutes.QUIZ_PATTERN))
        assertTrue(AppRoutes.isFullScreen(AppRoutes.quizRoute(friendId = "u1")))
        assertTrue(AppRoutes.isFullScreen(AppRoutes.quizRoute("u1", "c1")))
        assertTrue(AppRoutes.isFullScreen(AppRoutes.QUIZ))
        assertFalse(AppRoutes.isFullScreen(AppDestination.HOME.route))
    }

    @Test
    fun `le Quiz cache la barre basse`() {
        assertTrue(AppRoutes.hidesBottomBar(AppRoutes.QUIZ_PATTERN))
        assertTrue(AppRoutes.hidesBottomBar(AppRoutes.quizRoute("u1", "c1")))
        assertTrue(AppRoutes.hidesBottomBar(AppRoutes.QUIZ))
    }

    @Test
    fun `le Quiz ne va pas jusqu'aux bords`() {
        // Une décision, et non un oubli : le Quiz est plein écran mais garde la marge de la barre
        // d'état, parce qu'il porte un en-tête avec un bouton « ← » — qui serait sous l'horloge du
        // système s'il allait jusqu'aux bords. Le lecteur, lui, gère ses propres marges.
        assertFalse(AppRoutes.isEdgeToEdge(AppRoutes.QUIZ_PATTERN))
        assertFalse(AppRoutes.isEdgeToEdge(AppRoutes.quizRoute(friendId = "u1")))
        assertFalse(AppRoutes.isEdgeToEdge(AppRoutes.QUIZ))
    }

    @Test
    fun `la route du Quiz n'est pas un onglet`() {
        // `fromRoute` sert à trouver l'onglet courant. S'il confondait le Quiz avec un onglet, la
        // barre basse se croirait sur un onglet et l'onglet actif serait faux.
        assertNull(AppDestination.fromRoute(AppRoutes.QUIZ_PATTERN))
        assertNull(AppDestination.fromRoute(AppRoutes.quizRoute(friendId = "u1")))
        assertEquals(AppDestination.HOME, AppDestination.fromRoute(AppDestination.HOME.route))
    }
}
