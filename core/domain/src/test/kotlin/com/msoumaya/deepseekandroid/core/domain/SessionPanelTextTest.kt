package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.ReviewCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Les mots du panneau « Ma séance »
// ---------------------------------------------------------------------------
// Ce banc éprouve deux choses, et ce sont les deux qui peuvent être fausses **en silence** :
//
//   1. Quelles entrées existent, pour chacune des cinq formes de tâche. Une entrée en trop
//      propose un geste qui n'a pas de sens — « Valider une partie ou toute la séance » sur une
//      consolidation, par exemple, où le bouton propre à l'étape existe déjà. Une entrée en
//      moins rend un geste inatteignable, et rien à l'écran ne le dit.
//   2. L'ordre. Il va du geste le plus spécifique au plus général, et un ordre différent ferait
//      lire « Reporter cette séance » — le seul geste qui n'enregistre aucun verset — avant la
//      note qu'on est venu donner.
//
// Aucun état n'est nécessaire : les cinq conditions se lisent toutes sur la **requête**, et
// c'est précisément ce que ce fichier vérifie. C'est aussi pourquoi ce banc n'installe pas le
// référentiel coranique — rien ici ne résout un verset.
// ---------------------------------------------------------------------------

class SessionPanelTextTest {

    /** L'ensemble des destinations branchées par un appelant qui sait tout faire. */
    private val toutes = SessionPanelText.Entry.entries.toSet()

    @Test
    fun `une lecture libre n'ouvre pas le panneau`() {
        // Le cas qui commande tout le reste. Sans tâche, `focused` est faux, donc aucune entrée
        // n'est retenue — et le panneau ne s'ouvre pas du tout. C'est la même règle que le
        // bandeau, qui ne s'affiche pas non plus : une lecture libre ne se valide pas.
        val libre = StudySession.Request(range = Range(100, 110))

        assertEquals(emptyList(), SessionPanelText.entries(libre, toutes))
        assertFalse(SessionPanelText.isUseful(libre, toutes))
    }

    @Test
    fun `une seance d'apprentissage propose de valider puis de clore`() {
        // Pas de barre de notes : une séance d'apprentissage ne se note pas, elle se valide. Et
        // « Après ma séance » n'a de sens que là — une révision, elle, est déjà finie quand on
        // l'a notée.
        val seance = StudySession.Request(range = Range(100, 110), sessionId = "s1")

        assertEquals(
            listOf(SessionPanelText.Entry.VALIDATE, SessionPanelText.Entry.AFTER),
            SessionPanelText.entries(seance, toutes),
        )
        assertTrue(SessionPanelText.isUseful(seance, toutes))
    }

    @Test
    fun `une revision propose la barre des notes puis la validation`() {
        val revision = StudySession.forTask(tache("rev-1"))

        assertEquals(
            listOf(SessionPanelText.Entry.GRADES, SessionPanelText.Entry.VALIDATE),
            SessionPanelText.entries(revision, toutes),
        )
    }

    @Test
    fun `une consolidation ne propose que son propre bouton`() {
        // C'est le cas où la règle `!consolidation` compte : une étape de consolidation se
        // valide d'un geste, et lui offrir en plus la feuille du point d'arrêt proposerait deux
        // chemins pour un même geste — dont un seul enregistre l'étape de consolidation.
        val etape = StudySession.forTask(tache("consolidation-100-110", ReviewCategory.RECENT))

        assertTrue(etape.consolidation, "Le banc doit porter une vraie consolidation.")
        assertEquals(
            listOf(SessionPanelText.Entry.CONSOLIDATION),
            SessionPanelText.entries(etape, toutes),
        )
    }

    @Test
    fun `une revision historique propose en plus de reapprendre`() {
        // L'entrée dépend de `revisionId`, et non de `reviewing` : c'est l'identifiant du modèle
        // de révision « legacy ». Elle est entre la barre et la validation, comme dans la source.
        val historique = StudySession.forTask(tache("rev-1")).copy(revisionId = "r-100-110")

        assertEquals(
            listOf(
                SessionPanelText.Entry.GRADES,
                SessionPanelText.Entry.RELEARN,
                SessionPanelText.Entry.VALIDATE,
            ),
            SessionPanelText.entries(historique, toutes),
        )
    }

    @Test
    fun `l'ordre des entrees suit celui du client d'origine`() {
        // Une seule liste d'entrées possible, et elle est écrite dans l'ordre du source. Un ordre
        // différent ferait lire « Reporter cette séance » avant la note, ou déplacerait le bouton
        // de consolidation sous la barre qui n'existe pas pour lui.
        assertEquals(
            listOf(
                "CONSOLIDATION",
                "GRADES",
                "RELEARN",
                "VALIDATE",
                "AFTER",
            ),
            SessionPanelText.Entry.entries.map { it.name },
        )
    }

    @Test
    fun `une entree sans destination est retiree, pas grisee`() {
        // La règle du dépôt, et sa raison : une entrée grisée laisse croire que le geste existe
        // et qu'il est momentanément indisponible, alors qu'il n'existe pas. Le jour où
        // l'appelant ne saura plus écrire une consolidation, l'entrée disparaîtra.
        val etape = StudySession.forTask(tache("consolidation-100-110", ReviewCategory.RECENT))
        val sansConsolidation = toutes - SessionPanelText.Entry.CONSOLIDATION

        assertEquals(emptyList(), SessionPanelText.entries(etape, sansConsolidation))
        assertFalse(SessionPanelText.isUseful(etape, sansConsolidation))
    }

    @Test
    fun `retirer une entree ne touche pas les autres`() {
        // La vérification qui manquerait si l'on se contentait du cas ci-dessus : le retrait doit
        // porter sur **une** entrée, et non vider la liste.
        val revision = StudySession.forTask(tache("rev-1"))
        val sansNotes = toutes - SessionPanelText.Entry.GRADES

        assertEquals(
            listOf(SessionPanelText.Entry.VALIDATE),
            SessionPanelText.entries(revision, sansNotes),
        )
        assertTrue(SessionPanelText.isUseful(revision, sansNotes))
    }

    @Test
    fun `le libelle de consolidation porte son echeance`() {
        // L'échéance dit ce qu'on valide : une étape faite en retard reste l'étape qu'elle était,
        // et le bouton doit annoncer la même chose que le bandeau qui l'a proposée.
        assertEquals("Valider la consolidation · J+1", SessionPanelText.consolidationLabel(1))
        assertEquals("Valider la consolidation · J+3", SessionPanelText.consolidationLabel(3))
        assertEquals("Valider la consolidation · J+7", SessionPanelText.consolidationLabel(7))
    }

    @Test
    fun `les trois etapes faites, le libelle retombe sur J+7`() {
        // Le repli est celui de `banner`, et il vient du même endroit : `LAST_CONSOLIDATION_OFFSET`.
        // Deux replis différents feraient annoncer « J+3 » sur le bandeau et « J+7 » sur le
        // bouton qui le suit — pour le même état.
        assertEquals(
            "Valider la consolidation · J+${StudySession.LAST_CONSOLIDATION_OFFSET}",
            SessionPanelText.consolidationLabel(null),
        )
        assertEquals(
            SessionPanelText.consolidationLabel(StudySession.LAST_CONSOLIDATION_OFFSET),
            SessionPanelText.consolidationLabel(null),
        )
    }

    @Test
    fun `les mots du panneau sont ceux du client d'origine`() {
        assertEquals("Ma séance", SessionPanelText.TITLE)
        assertEquals("À réapprendre", SessionPanelText.RELEARN)
        assertEquals("Valider une partie ou toute la séance", SessionPanelText.VALIDATE)
        assertEquals("Après ma séance", SessionPanelText.AFTER_SESSION)
        assertEquals("Je dois encore le travailler", SessionPanelText.WORK_AGAIN)
        assertEquals("Reporter cette séance", SessionPanelText.POSTPONE)
    }

    @Test
    fun `le bouton de fermeture nomme ce qu'il ferme`() {
        // Écart assumé avec la source, qui n'a qu'un seul en-tête pour ses cinq panneaux et
        // écrit donc « Fermer le panneau ». Le portage a une feuille par panneau, et chacune
        // nomme la sienne — c'est la règle posée dans `BookmarksText`.
        assertEquals("Fermer la séance", SessionPanelText.CLOSE)
        assertFalse(SessionPanelText.CLOSE == "Fermer le panneau")
    }

    // -----------------------------------------------------------------------
    // Fabriques
    // -----------------------------------------------------------------------

    private fun tache(id: String, categorie: ReviewCategory = ReviewCategory.HABITUAL) =
        Review.ReviewTask(start = 100, end = 110, id = id, category = categorie)
}
