package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.Range
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Libellés du programme
// ---------------------------------------------------------------------------
// Ce sont les seuls textes de l'écran Programme qui ne se lisent pas sur une carte : ils se
// calculent. Un compte faux d'un verset annonce une séance qui n'existe pas, et un
// « À apprendre » posé sur une ligne entamée contredit la progression enregistrée. Les éprouver
// ici coûte quelques millisecondes ; les éprouver à l'écran demanderait un appareil.
//
// Les dates sont **fixées** (« 2026-03-10 » est un mardi) et non lues depuis l'horloge : un test
// qui dépend du jour où on le lance passe le mardi et échoue le dimanche.
// ---------------------------------------------------------------------------

class ProgramTextTest {

    private val mardi = "2026-03-10"
    private val dimanche = "2026-03-08"
    private val mercredi = "2026-03-11"

    // -----------------------------------------------------------------------
    // Comptes
    // -----------------------------------------------------------------------

    @Test
    fun `le compte de versets s'accorde au singulier et au pluriel`() {
        assertEquals("1 verset", ProgramText.verseCount(Range(10, 10)))
        assertEquals("2 versets", ProgramText.verseCount(Range(10, 11)))
        assertEquals("5 versets", ProgramText.verseCount(Range(6000, 6004)))
    }

    @Test
    fun `un verset unique ne prend pas la marque du pluriel`() {
        // Le pluriel fautif se verrait sur chaque carte de tâche d'une séance d'un seul verset.
        assertTrue(ProgramText.verseCount(Range(7, 7)).endsWith("1 verset"))
    }

    // -----------------------------------------------------------------------
    // Dates
    // -----------------------------------------------------------------------

    @Test
    fun `la date du jour s'ecrit en toutes lettres`() {
        assertEquals("mardi 10 mars 2026", ProgramText.longDate(mardi))
    }

    @Test
    fun `la prochaine seance s'ecrit sans l'annee`() {
        assertEquals("mercredi 11 mars", ProgramText.mediumDate(mercredi))
    }

    @Test
    fun `l'initiale du jour est en majuscules`() {
        assertEquals("MAR.", ProgramText.shortWeekday(mardi))
        assertEquals("DIM.", ProgramText.shortWeekday(dimanche))
    }

    @Test
    fun `les trois lettres du jour sont en minuscules, pour une barre de graphique`() {
        // Le client d'origine demande le jour abrégé puis en garde trois caractères : c'est un
        // format **tronqué**, et le reproduire évite d'inventer un format que l'original n'a pas.
        assertEquals("mar", ProgramText.weekdayInitials(mardi))
        assertEquals("dim", ProgramText.weekdayInitials(dimanche))
        assertEquals("mer", ProgramText.weekdayInitials(mercredi))
    }

    @Test
    fun `les sept jours tiennent en trois lettres, sans perte a la troncature`() {
        // Une troncature n'est sans perte que si elle est vraie pour les sept jours : « mer. » et
        // « sam. » font trois lettres, mais rien ne le garantit sans le mesurer.
        val semaine = (8..14).map { "2026-03-${it.toString().padStart(2, '0')}" }
        val initiales = semaine.map { ProgramText.weekdayInitials(it) }
        assertEquals(listOf("dim", "lun", "mar", "mer", "jeu", "ven", "sam"), initiales)
        assertTrue(
            initiales.all { it.length == 3 },
            "chaque initiale doit faire trois caractères : $initiales",
        )
    }

    @Test
    fun `une seance lointaine s'ecrit jour et mois`() {
        assertEquals("10 mars", ProgramText.dayMonth(mardi))
    }

    @Test
    fun `la locale est fixee au francais, et non celle de l'appareil`() {
        // Le client d'origine demande explicitement `fr-FR` à `toLocaleDateString`. Si la locale
        // suivait l'appareil, un téléphone réglé en anglais afficherait « March » et les deux
        // clients ne diraient plus la même chose pour le même état.
        assertTrue(
            ProgramText.longDate(mardi).contains("mars"),
            "la date doit rester française : ${ProgramText.longDate(mardi)}",
        )
    }

    @Test
    fun `une cle qui n'est pas une date s'affiche telle quelle`() {
        // Repli délibéré, et écart assumé avec la source : le client d'origine écrirait
        // « Invalid Date » à l'écran. Ici la valeur fautive reste lisible, ce qui dit mieux ce
        // qui n'allait pas — un mot qui ne dit rien de l'erreur ne vaut pas mieux qu'un trou.
        assertEquals("pas-une-date", ProgramText.longDate("pas-une-date"))
        assertEquals("2026-13-45", ProgramText.dayMonth("2026-13-45"))
        assertEquals("", ProgramText.shortWeekday(""))
    }

    // -----------------------------------------------------------------------
    // Statut d'une ligne de reprise
    // -----------------------------------------------------------------------

    @Test
    fun `les cinq mots suivent l'etat de la ligne et le mode de la tache`() {
        assertEquals("Appris", ProgramText.rowStatus(done = true, partial = false, learning = true))
        assertEquals("Révisée", ProgramText.rowStatus(done = true, partial = false, learning = false))
        assertEquals("À continuer", ProgramText.rowStatus(done = false, partial = true, learning = true))
        assertEquals("À continuer", ProgramText.rowStatus(done = false, partial = true, learning = false))
        assertEquals("À apprendre", ProgramText.rowStatus(done = false, partial = false, learning = true))
        assertEquals("À réviser", ProgramText.rowStatus(done = false, partial = false, learning = false))
    }

    @Test
    fun `une ligne faite ne peut pas etre entamee`() {
        // Les deux drapeaux peuvent être vrais si l'appelant les calcule mal — une ligne dont le
        // dernier verset est validé l'est aussi de son premier. C'est l'ordre des cas qui décide,
        // et le fait doit l'emporter : peindre une ligne finie comme entamée ferait mentir le
        // décompte des pages apprises, qui compte les pages terminées.
        assertEquals("Appris", ProgramText.rowStatus(done = true, partial = true, learning = true))
        assertEquals("Révisée", ProgramText.rowStatus(done = true, partial = true, learning = false))
    }
}
