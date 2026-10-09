package com.msoumaya.deepseekandroid.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Le banc de la connectivité.
 *
 * Chaque test nomme la règle qu'il protège. Les cas marqués « comme dans l'original » portent une
 * décision **mesurée** dans `src/services/connectivity.ts` et `src/App.tsx`, et non déduite : ce
 * sont ceux qui ont l'air d'un défaut et n'en sont pas, donc ceux qu'un relecteur « corrigerait ».
 */
class ConnectivityTest {

    /** Une lecture où les deux mesures s'accordent — le cas ordinaire. */
    private fun accorde(enLigne: Boolean) = ConnectivityReading(enLigne, enLigne)

    /** L'état du tout premier démarrage. */
    private fun depart() = ConnectivityState()

    // ---------------------------------------------------------------- la lecture stricte

    @Test
    fun `une mesure absente compte comme en ligne`() {
        // Deux `=== false` dans l'original : `null` n'est pas `false`, donc l'appareil est
        // déclaré en ligne tant que la mesure n'est pas arrivée. C'est ce que fait `NetInfo`
        // avant sa première réponse.
        assertFalse(estHorsLigne(null, null))
        assertFalse(estHorsLigne(true, null))
        assertFalse(estHorsLigne(null, true))
    }

    @Test
    fun `une seule mesure fausse suffit a declarer hors ligne`() {
        assertTrue(estHorsLigne(false, true))
        assertTrue(estHorsLigne(true, false))
        assertTrue(estHorsLigne(false, null))
        assertTrue(estHorsLigne(null, false))
    }

    @Test
    fun `deux mesures vraies laissent en ligne`() {
        assertFalse(estHorsLigne(true, true))
    }

    @Test
    fun `la lecture porte exactement la regle de la fonction`() {
        // La règle est écrite une seule fois ; ce test tient l'accord entre les deux entrées.
        assertEquals(estHorsLigne(false, null), ConnectivityReading(false, null).horsLigne)
        assertEquals(estHorsLigne(null, null), ConnectivityReading(null, null).horsLigne)
    }

    // ---------------------------------------------------------------- le premier démarrage

    @Test
    fun `le premier demarrage en ligne n'annonce aucun retour`() {
        // Le cas qui compte : un « Connexion rétablie » au lancement ferait croire à une panne
        // qui n'a pas eu lieu.
        val suite = transition(depart(), accorde(true), maintenantMs = 0)
        assertEquals(null, suite.etat.retourJusquaMs)
        assertFalse(bandeauVisible(suite.etat, 0))
        assertEquals(emptyList(), suite.effets)
    }

    @Test
    fun `le premier demarrage hors ligne affiche le bandeau de panne`() {
        val suite = transition(depart(), accorde(false), maintenantMs = 0)
        assertTrue(suite.etat.horsLigne)
        assertEquals(null, suite.etat.retourJusquaMs)
        assertTrue(bandeauVisible(suite.etat, 0))
        assertEquals(TEXTE_HORS_LIGNE, texteDuBandeau(suite.etat))
    }

    @Test
    fun `le premier demarrage ne demande aucune synchronisation`() {
        // Rien ne synchronise au démarrage dans l'original : le vidage est appelé **dans** le
        // rappel du retour de réseau, et nulle part ailleurs.
        assertEquals(emptyList(), transition(depart(), accorde(true), 0).effets)
        assertEquals(emptyList(), transition(depart(), accorde(false), 0).effets)
    }

    @Test
    fun `au repos le bandeau est invisible`() {
        assertFalse(bandeauVisible(depart(), 0))
    }

    // ---------------------------------------------------------------- le retour

    @Test
    fun `un retour demande la synchronisation une seule fois`() {
        val panne = transition(depart(), accorde(false), 0).etat
        val retour = transition(panne, accorde(true), 100)
        assertEquals(listOf(ConnectivityEffect.DemanderSynchronisation), retour.effets)
        assertFalse(retour.etat.horsLigne)
        assertTrue(retour.etat.retablieA(100))
    }

    @Test
    fun `deux lectures en ligne d'affilee n'annoncent qu'un retour`() {
        val panne = transition(depart(), accorde(false), 0).etat
        val retour = transition(panne, accorde(true), 100).etat
        val suite = transition(retour, accorde(true), 200)
        assertEquals(emptyList(), suite.effets)
        // Et l'échéance n'est pas repoussée : la seconde lecture en ligne n'est pas un retour.
        assertEquals(3_100L, suite.etat.retourJusquaMs)
    }

    @Test
    fun `une lecture hors ligne ne demande jamais de synchronisation`() {
        val panne = transition(depart(), accorde(false), 0).etat
        assertEquals(emptyList(), transition(panne, accorde(false), 100).effets)
    }

    @Test
    fun `une mesure absente apres une panne annonce un retour, comme dans l'original`() {
        // Ce cas a l'air d'un défaut et n'en est pas : `null` n'est pas `false`, donc la lecture
        // n'est pas « hors ligne », et l'appareil qui était hors ligne est déclaré revenu. Une
        // mesure perdue est traitée comme une mesure en ligne, exactement comme les deux
        // `=== false` de l'original.
        val panne = transition(depart(), accorde(false), 0).etat
        val retour = transition(panne, ConnectivityReading(null, null), 100)
        assertFalse(retour.etat.horsLigne)
        assertEquals(listOf(ConnectivityEffect.DemanderSynchronisation), retour.effets)
    }

    // ---------------------------------------------------------------- la fenêtre du bandeau

    @Test
    fun `le bandeau de retour dure trois secondes`() {
        val panne = transition(depart(), accorde(false), 0).etat
        val retour = transition(panne, accorde(true), 1_000).etat
        assertTrue(retour.retablieA(1_000))
        assertTrue(retour.retablieA(3_999))
        // À l'instant pile où le minuteur de l'original se déclencherait, le bandeau est éteint.
        assertFalse(retour.retablieA(4_000))
        assertFalse(retour.retablieA(4_001))
    }

    @Test
    fun `le bandeau de retour est visible sans etre hors ligne`() {
        val panne = transition(depart(), accorde(false), 0).etat
        val retour = transition(panne, accorde(true), 0).etat
        assertFalse(retour.horsLigne)
        assertTrue(bandeauVisible(retour, 0))
        assertFalse(bandeauVisible(retour, DUREE_RETOUR_MS))
    }

    @Test
    fun `un second retour rearre l'echeance au lieu d'en laisser deux`() {
        // C'est le `clearTimeout(timer)` de l'original. Sans réarmement, le premier minuteur
        // éteindrait le bandeau du second retour avant l'heure : ici, l'échéance de 3 000 est
        // remplacée par celle de 4 000, et le bandeau reste allumé à 3 500.
        val panne = transition(depart(), accorde(false), 0).etat
        val premier = transition(panne, accorde(true), 0).etat
        val repanne = transition(premier, accorde(false), 1_000).etat
        val second = transition(repanne, accorde(true), 1_000).etat
        assertEquals(4_000L, second.retourJusquaMs)
        assertTrue(second.retablieA(3_500))
        assertFalse(second.retablieA(4_000))
    }

    @Test
    fun `reperdre le reseau n'efface pas l'annonce du retour`() {
        // `restored` n'est touché que par le minuteur dans l'original : une panne pendant les
        // trois secondes du bandeau ne l'éteint pas.
        val panne = transition(depart(), accorde(false), 0).etat
        val retour = transition(panne, accorde(true), 100).etat
        val repanne = transition(retour, accorde(false), 200).etat
        assertEquals(3_100L, repanne.retourJusquaMs)
        assertTrue(repanne.horsLigne)
    }

    // ---------------------------------------------------------------- le texte

    @Test
    fun `le texte repasse a la panne pendant que le retour est affiche`() {
        // Le bandeau est visible (retour en cours) mais le réseau est retombé : il doit dire la
        // panne, pas la réparation.
        val panne = transition(depart(), accorde(false), 0).etat
        val retour = transition(panne, accorde(true), 100).etat
        val repanne = transition(retour, accorde(false), 200).etat
        assertTrue(bandeauVisible(repanne, 200))
        assertEquals(TEXTE_HORS_LIGNE, texteDuBandeau(repanne))
    }

    @Test
    fun `le texte hors ligne est celui de l'original`() {
        assertEquals(
            "Mode hors connexion \u2014 les modifications seront synchronisées automatiquement",
            TEXTE_HORS_LIGNE,
        )
    }

    @Test
    fun `le cadratin du texte hors ligne est typographique`() {
        // Mesuré dans `src/App.tsx` : le séparateur est U+2014, et non un trait d'union ni un
        // tiret demi-cadratin. Un portage qui le redresse produit un texte presque identique.
        assertTrue(TEXTE_HORS_LIGNE.contains('\u2014'))
        assertFalse(TEXTE_HORS_LIGNE.contains('\u2013'))
        assertFalse(TEXTE_HORS_LIGNE.contains('-'))
    }

    @Test
    fun `le texte du retour est celui de l'original`() {
        assertEquals("Connexion rétablie", TEXTE_RETABLIE)
    }

    @Test
    fun `le bandeau ne dit le retour que lorsqu'il n'y a pas de panne`() {
        val panne = transition(depart(), accorde(false), 0).etat
        assertEquals(TEXTE_HORS_LIGNE, texteDuBandeau(panne))
        assertEquals(TEXTE_RETABLIE, texteDuBandeau(transition(panne, accorde(true), 0).etat))
    }
}
