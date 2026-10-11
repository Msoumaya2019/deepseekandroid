package com.msoumaya.deepseekandroid.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Le banc du retour au premier plan.
 *
 * Chaque test nomme la règle qu'il protège. Les cas marqués « comme dans l'original » portent une
 * décision **mesurée** dans `src/services/problemReports.ts`, `src/App.tsx` et
 * `src/services/recitations.ts` — `AppState.addEventListener('change', s => { if (s === 'active')
 * … })` —, et non déduite : ce sont ceux qui ont l'air d'un défaut et n'en sont pas, donc ceux
 * qu'un relecteur « corrigerait ».
 */
class ForegroundTest {

    /** L'état du tout premier démarrage : aucune activité visible. */
    private fun depart() = ForegroundState()

    private fun demarrer(etat: ForegroundState) =
        transitionForeground(etat, ForegroundEvent.ACTIVITY_STARTED)

    private fun arreter(etat: ForegroundState) =
        transitionForeground(etat, ForegroundEvent.ACTIVITY_STOPPED)

    // ---------------------------------------------------------------- l'entrée au premier plan

    @Test
    fun `la premiere activite demande la reprise`() {
        // 0 -> 1 : c'est le seul passage qui compte, et celui que `AppState` annonce par
        // `'active'`. Il se produit au lancement (où l'original vide aussi, par son `sync()`
        // immédiat) comme à chaque retour depuis l'arrière-plan.
        val suite = demarrer(depart())
        assertEquals(ForegroundState(1), suite.etat)
        assertEquals(listOf(ForegroundEffect.DemanderReprise), suite.effets)
    }

    @Test
    fun `une seconde activite ne demande aucune reprise`() {
        // 1 -> 2 : l'application était déjà au premier plan. Un lien profond, une fenêtre
        // partagée ou une activité seconde ouvrent une activité sans que la personne soit
        // « revenue » — et `AppState` n'annonce rien dans ce cas. Un booléen `auPremierPlan`
        // écrirait `true` de nouveau et demanderait une reprise que l'original ne fait pas.
        val visible = demarrer(depart()).etat
        val suite = demarrer(visible)
        assertEquals(ForegroundState(2), suite.etat)
        assertEquals(emptyList(), suite.effets)
    }

    @Test
    fun `un arret ne demande jamais de reprise`() {
        // L'original ne vide qu'au passage à `'active'`, jamais à `'background'` : envoyer une
        // reprise au moment où la personne quitte l'application tenterait un dépôt alors que le
        // processus va être gelé.
        val visible = demarrer(depart()).etat
        val suite = arreter(visible)
        assertEquals(ForegroundState(0), suite.etat)
        assertEquals(emptyList(), suite.effets)
    }

    // ---------------------------------------------------------------- le retour après un arret

    @Test
    fun `un retour apres un arret demande la reprise`() {
        // Le cas réel : l'application passe en arrière-plan, puis revient. 0 -> 1 de nouveau.
        val visible = demarrer(depart()).etat
        val cache = arreter(visible).etat
        val suite = demarrer(cache)
        assertEquals(ForegroundState(1), suite.etat)
        assertEquals(listOf(ForegroundEffect.DemanderReprise), suite.effets)
    }

    @Test
    fun `chaque retour demande la reprise, et pas seulement le premier`() {
        // `AppState` annonce `'active'` à **chaque** retour, pas une fois pour toutes : une
        // récitation enregistrée hors connexion doit partir au premier retour **comme** au
        // dixième. Un état qui ne demanderait la reprise qu'une fois — le piège d'un « a déjà
        // été visible » — laisserait la file se remplir sans jamais se vider.
        var etat = depart()
        repeat(3) { tour ->
            val entree = demarrer(etat)
            assertEquals(
                listOf(ForegroundEffect.DemanderReprise),
                entree.effets,
                "Le retour n° ${tour + 1} ne demande plus la reprise.",
            )
            etat = arreter(entree.etat).etat
        }
    }

    // ---------------------------------------------------------------- le plancher a zero

    @Test
    fun `un arret sans demarrage ne rend pas le compteur negatif`() {
        // Le défaut le plus silencieux de la tranche. Un compteur qui passerait à -1 ferait que
        // le prochain démarrage rendrait 0, et non 1 : **aucune** reprise n'aurait plus lieu,
        // sans qu'aucun écran ne le montre.
        val suite = arreter(depart())
        assertEquals(ForegroundState(0), suite.etat)
        assertFalse(suite.etat.auPremierPlan)
    }

    @Test
    fun `apres un arret orphelin la reprise a toujours lieu`() {
        // La conséquence, écrite comme un cas à part : c'est le comportement qui compte, et non
        // la valeur du compteur. Un `coerceAtLeast` retiré ferait échouer **ce** test, et pas
        // seulement celui de la valeur.
        val orphelin = arreter(arreter(depart()).etat).etat
        assertEquals(ForegroundState(0), orphelin)
        val suite = demarrer(orphelin)
        assertEquals(listOf(ForegroundEffect.DemanderReprise), suite.effets)
    }

    @Test
    fun `deux arrets d'affilee ne descendent pas sous zero`() {
        val visible = demarrer(depart()).etat
        val premier = arreter(visible).etat
        val second = arreter(premier).etat
        assertEquals(ForegroundState(0), premier)
        assertEquals(ForegroundState(0), second)
    }

    // ---------------------------------------------------------------- l'ecart de la rotation

    @Test
    fun `une rotation demande une reprise de plus, comme assume`() {
        // Écart nommé, et non défaut caché : un changement de configuration fait `onStop` sur
        // l'ancienne activité puis `onStart` sur la nouvelle, donc le compteur retombe à zéro et
        // remonte. L'original ne produit pas cette reprise — `AppState` reste `'active'`.
        // L'absorber demanderait une temporisation ; la conséquence observable est nulle, et ce
        // test **fixe** l'écart pour qu'un futur changement ne le déplace pas en silence.
        val visible = demarrer(depart()).etat
        val pendantLaRotation = arreter(visible).etat
        val apresLaRotation = demarrer(pendantLaRotation)
        assertEquals(ForegroundState(1), apresLaRotation.etat)
        assertEquals(listOf(ForegroundEffect.DemanderReprise), apresLaRotation.effets)
    }

    // ---------------------------------------------------------------- l'etat lisible

    @Test
    fun `l'etat dit s'il y a au moins une activite visible`() {
        assertFalse(depart().auPremierPlan)
        assertTrue(demarrer(depart()).etat.auPremierPlan)
        assertTrue(demarrer(demarrer(depart()).etat).etat.auPremierPlan)
    }

    @Test
    fun `le compteur d'un demarrage suit celui de l'etat precedent`() {
        // La règle est écrite une seule fois ; ce test tient l'accord entre la valeur rendue et
        // celle du départ, pour qu'un `+ 1` écrit deux fois ne puisse pas diverger.
        var etat = depart()
        for (attendu in 1..4) {
            etat = demarrer(etat).etat
            assertEquals(attendu, etat.activitesVisibles)
        }
    }
}
