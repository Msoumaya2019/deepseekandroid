package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AdminDifficultyStamp
import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.DifficultyMarker
import com.msoumaya.deepseekandroid.core.model.DifficultyStamp
import com.msoumaya.deepseekandroid.core.model.effectiveDifficultyMarkers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Les mots du panneau des actions du verset, et la règle de son libellé de marquage.
 *
 * ## Ce que ce fichier défend
 *
 * Une seule chose, mais elle compte : le libellé du bouton de marquage suit `user` **seul**, là
 * où `Review.isDifficult` dit « user **ou** admin ». Confondre les deux afficherait « Retirer des
 * révisions prioritaires » sur un verset que l'appui viendrait de **marquer** — et rien à l'écran
 * ne le dirait, puisque le verset paraîtrait simplement difficile.
 *
 * Le cas décisif est celui du marqueur posé **par le professeur seul** : `isDifficult` répond
 * vrai, et le libellé doit malgré tout annoncer « Marquer comme difficile ».
 *
 * ## Par où il passe
 *
 * Les cas interrogent [VerseActionsText.rows] — la **même porte** que le panneau — plutôt qu'une
 * fonction de libellé qui n'existerait que pour eux. Un test qui passe par un chemin que l'écran
 * n'emprunte pas mesure ce chemin-là, et rien de plus.
 *
 * ## Ce qu'il ne mesure pas
 *
 * Le **rendu** du panneau : c'est un `@Composable`, et le projet n'a pas d'outillage de test
 * d'interface. Les contrôles de forme du module `feature:reader` tiennent le branchement.
 */
class VerseActionsTextTest {

    private val verset = 5
    private val marqueSeule = setOf(VerseActionsText.Action.MARK)

    /** L'état du programme, avec un marqueur de difficulté sur [verset]. */
    private fun etat(marqueur: DifficultyMarker?): AppState {
        val base = Program.defaultState().copy(onboardingDone = true)
        return if (marqueur == null) {
            base
        } else {
            base.copy(difficultyMarkers = mapOf(verset.toString() to marqueur))
        }
    }

    /** Le titre que le panneau afficherait pour le bouton de marquage. */
    private fun libelle(state: AppState, id: Int): String =
        VerseActionsText.rows(marqueSeule, id in VerseActionsText.userMarkedIds(state))
            .single()
            .title

    private val parLeleve = DifficultyMarker(user = DifficultyStamp("2026-01-01"))
    private val parLeProfesseur = DifficultyMarker(admin = AdminDifficultyStamp("2026-01-01"))
    private val parLesDeux = DifficultyMarker(
        user = DifficultyStamp("2026-01-01"),
        admin = AdminDifficultyStamp("2026-01-02"),
    )

    // -----------------------------------------------------------------------
    // Le libellé suit l'élève, et non « difficile »
    // -----------------------------------------------------------------------

    @Test
    fun `un verset non marque propose de le marquer`() {
        assertEquals(VerseActionsText.MARK_DIFFICULT, libelle(etat(null), verset))
    }

    @Test
    fun `un verset marque par l'eleve propose de le retirer`() {
        assertEquals(VerseActionsText.UNMARK_DIFFICULT, libelle(etat(parLeleve), verset))
    }

    @Test
    fun `un verset marque par le professeur seul propose de le marquer`() {
        // Le cas décisif. `Review.isDifficult` répond **vrai** ici — le verset est bien
        // difficile, et la page le colore — mais le bouton, lui, doit annoncer qu'il **ajoute**
        // le marqueur de l'élève. Suivre `isDifficult` ferait mentir le libellé.
        val marque = etat(parLeProfesseur)
        assertTrue(
            Review.isDifficult(marque.effectiveDifficultyMarkers[verset.toString()]),
            "Le marqueur du professeur doit compter comme difficile : c'est la règle des marques.",
        )
        assertEquals(VerseActionsText.MARK_DIFFICULT, libelle(marque, verset))
    }

    @Test
    fun `un verset marque par les deux propose de retirer`() {
        assertEquals(VerseActionsText.UNMARK_DIFFICULT, libelle(etat(parLesDeux), verset))
    }

    @Test
    fun `le libelle ignore le verset voisin`() {
        // Le marqueur est lu sur **le** verset, et non sur la carte entière : un verset difficile
        // ne doit pas faire proposer « Retirer » sur son voisin.
        val base = Program.defaultState().copy(onboardingDone = true)
        val voisinMarque = base.copy(
            difficultyMarkers = mapOf((verset + 1).toString() to parLeleve),
        )
        assertEquals(VerseActionsText.MARK_DIFFICULT, libelle(voisinMarque, verset))
        assertFalse(verset in VerseActionsText.userMarkedIds(voisinMarque))
        assertTrue(verset + 1 in VerseActionsText.userMarkedIds(voisinMarque))
    }

    // -----------------------------------------------------------------------
    // L'accord entre le libellé et la bascule
    // -----------------------------------------------------------------------

    @Test
    fun `le libelle annonce ce que la bascule fera, sur les quatre origines`() {
        // C'est le test qui tient l'accord : le bouton promet une action, et la bascule la
        // tient. Les quatre combinaisons d'origines sont parcourues, parce que c'est la
        // **seule** façon de voir la différence entre « user » et « user ou admin ».
        val cas = listOf(
            "aucun marqueur" to null,
            "élève" to parLeleve,
            "professeur" to parLeProfesseur,
            "les deux" to parLesDeux,
        )

        for ((nom, marqueur) in cas) {
            val avant = etat(marqueur)
            val apres = Review.toggleDifficulty(avant, verset)
            val marqueurApres = apres.effectiveDifficultyMarkers[verset.toString()]
            val marqueurDeLEleveRetire = marqueurApres?.user == null
            val boutonAnnonceUnRetrait = libelle(avant, verset) == VerseActionsText.UNMARK_DIFFICULT

            assertEquals(
                boutonAnnonceUnRetrait,
                marqueurDeLEleveRetire,
                "Le libellé ment pour le cas « $nom » : le bouton " +
                    (if (boutonAnnonceUnRetrait) "annonce un retrait" else "annonce un marquage") +
                    " alors que la bascule fait l'inverse.",
            )
        }
    }

    @Test
    fun `la bascule ne touche jamais au marqueur du professeur`() {
        // L'autre moitié de la même promesse : retirer le marqueur de l'élève ne doit pas
        // effacer le signalement du professeur, sans quoi le canal enseignant serait perdu par
        // un geste d'élève.
        val apres = Review.toggleDifficulty(etat(parLesDeux), verset)
        val marqueur = apres.effectiveDifficultyMarkers[verset.toString()]
        assertEquals(null, marqueur?.user, "Le marqueur de l'élève devait être retiré.")
        assertEquals(
            "2026-01-02",
            marqueur?.admin?.createdAt,
            "Le marqueur du professeur a été effacé par un geste d'élève.",
        )
    }

    @Test
    fun `l'ensemble des versets marques par l'eleve ne retient que ceux-la`() {
        val melange = Program.defaultState().copy(onboardingDone = true).copy(
            difficultyMarkers = mapOf(
                "1" to parLeleve,
                "2" to parLeProfesseur,
                "3" to parLesDeux,
                "clef-illisible" to parLeleve,
            ),
        )
        assertEquals(
            setOf(1, 3),
            VerseActionsText.userMarkedIds(melange),
            "Seuls les versets portant un marqueur d'élève doivent y figurer — et une clef non " +
                "numérique est écartée plutôt que de faire tomber l'écran.",
        )
    }

    // -----------------------------------------------------------------------
    // Les mots, repris du client d'origine
    // -----------------------------------------------------------------------

    @Test
    fun `les mots du panneau sont ceux du client d'origine`() {
        // La fidélité des chaînes se vérifie ici, et non dans un diff : un mot changé dans un
        // objet de texte ne ferait rougir aucun autre test.
        assertEquals("Actions du verset", VerseActionsText.TITLE)
        assertEquals("Écouter ce verset", VerseActionsText.LISTEN)
        assertEquals("Répéter ce verset", VerseActionsText.REPEAT)
        assertEquals("Sélectionner un passage", VerseActionsText.SELECT_RANGE)
        assertEquals("Marquer comme difficile", VerseActionsText.MARK_DIFFICULT)
        assertEquals("Retirer des révisions prioritaires", VerseActionsText.UNMARK_DIFFICULT)
        assertEquals("Verset 7", VerseActionsText.verseLabel(7))
    }

    @Test
    fun `les quatre actions sont celles du client d'origine, dans son ordre`() {
        assertEquals(
            listOf(
                VerseActionsText.Action.LISTEN,
                VerseActionsText.Action.REPEAT,
                VerseActionsText.Action.SELECT_RANGE,
                VerseActionsText.Action.MARK,
            ),
            VerseActionsText.Action.entries.toList(),
        )
    }

    // -----------------------------------------------------------------------
    // Ce que le panneau affiche
    // -----------------------------------------------------------------------

    private val toutes = VerseActionsText.Action.entries.toSet()

    @Test
    fun `une action non branchee est retiree, pas grisee`() {
        // Tant que le choix de plage n'est pas porté, seule une partie des actions est branchée :
        // le panneau ne doit montrer que celles-là.
        val branchees = setOf(
            VerseActionsText.Action.LISTEN,
            VerseActionsText.Action.MARK,
        )
        assertEquals(
            listOf(VerseActionsText.LISTEN, VerseActionsText.MARK_DIFFICULT),
            VerseActionsText.rows(branchees, markedByUser = false).map { it.title },
        )
    }

    @Test
    fun `l'ordre du panneau survit au filtrage`() {
        // L'ensemble est construit dans le désordre : l'ordre doit rester celui de `Action`.
        assertEquals(
            listOf(
                VerseActionsText.LISTEN,
                VerseActionsText.REPEAT,
                VerseActionsText.SELECT_RANGE,
                VerseActionsText.MARK_DIFFICULT,
            ),
            VerseActionsText.rows(toutes.reversed().toSet(), markedByUser = false).map { it.title },
        )
    }

    @Test
    fun `les mots du panneau viennent de la regle, et non de l'appelant`() {
        // Les entrées composées ne doivent pas être des chaînes recopiées à l'écran : c'est la
        // règle qui les porte, et le test le vérifie sur les quatre titres.
        assertEquals(
            listOf(
                VerseActionsText.LISTEN,
                VerseActionsText.REPEAT,
                VerseActionsText.SELECT_RANGE,
                VerseActionsText.UNMARK_DIFFICULT,
            ),
            VerseActionsText.rows(toutes, markedByUser = true).map { it.title },
        )
    }

    @Test
    fun `sans action branchee le panneau n'a rien a proposer`() {
        assertTrue(VerseActionsText.rows(emptySet(), markedByUser = false).isEmpty())
        assertFalse(VerseActionsText.isUseful(emptySet()))
        assertTrue(VerseActionsText.isUseful(setOf(VerseActionsText.Action.MARK)))
    }
}
