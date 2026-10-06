package com.msoumaya.deepseekandroid.core.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * La notification, telle qu'on peut l'éprouver **sans appareil**.
 *
 * Le service lui-même ne se teste pas sur la JVM : il demande un `Context` Android, un
 * `MediaSessionService` et un système qui lie le service. Ce qui se teste, c'est **ce qu'on y
 * met** — et c'est justement la partie qu'un relecteur ne peut pas vérifier à l'œil, puisque les
 * mots viennent du client d'origine et non du présent code.
 *
 * Ces trois valeurs sont recopiées de `PassageAudioPlayer.tsx` : le titre préfixé, l'artiste qui
 * est le récitateur, et l'album. Un test qui les fige est ce qui empêche de les « améliorer »
 * plus tard en croyant bien faire.
 */
class AudioNotificationTest {

    @Test
    fun `le titre porte le passage, precede du nom de l'application`() {
        assertEquals("Coran · sourate 2, versets 1 à 5", AudioNotification.title("sourate 2, versets 1 à 5"))
    }

    @Test
    fun `le titre d'un verset seul se lit comme celui d'un passage`() {
        assertEquals("Coran · verset 255", AudioNotification.title("verset 255"))
    }

    @Test
    fun `le separateur est un point median entoure d'espaces`() {
        // Le client d'origine ecrit `Coran · ...`, avec des espaces autour du point median.
        // Sans eux, l'ecran verrouille affiche un titre colle qui se lit mal.
        val titre = AudioNotification.title("verset 1")
        assertEquals("Coran · verset 1", titre)
        kotlin.test.assertTrue(titre.startsWith("Coran · "), "titre attendu prefixe de « Coran · » : $titre")
    }

    @Test
    fun `l'artiste est le nom du recitateur`() {
        assertEquals("Mishary Rashid Alafasy", AudioNotification.artist("Mishary Rashid Alafasy"))
    }

    @Test
    fun `un recitateur sans nom ne produit pas de ligne vide`() {
        // Le client d'origine evite de publier un artiste vide. Une chaine blanche est
        // ramenee a `null`, et non pas publiee telle quelle.
        assertNull(AudioNotification.artist(""))
        assertNull(AudioNotification.artist("   "))
    }

    @Test
    fun `un nom entoure d'espaces est nettoye`() {
        assertEquals("Alafasy", AudioNotification.artist("  Alafasy  "))
    }

    @Test
    fun `l'album est le nom de la lecture, identique au client d'origine`() {
        assertEquals("Hafs ‘an ‘Âsim", AudioNotification.ALBUM)
    }
}
