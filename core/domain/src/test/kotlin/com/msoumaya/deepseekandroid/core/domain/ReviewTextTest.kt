package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.ReviewGrade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Les mots du tableau de bord des révisions
// ---------------------------------------------------------------------------
// Trois familles de chaînes sont éprouvées ici, et ce sont celles qui peuvent **mentir** :
//
//   1. Les comptes et les pourcentages — « Jour 3 / 7 », « 43 % réellement révisés », le résumé du
//      corpus. Un décalage d'un jour annonce une échéance qui n'est pas celle du jour.
//   2. L'état d'une étape de consolidation — « Aujourd'hui » / « À rattraper » / « À venir ». Il
//      repose sur une comparaison de dates **en texte** ; ce test fige cette dépendance.
//   3. Les apostrophes. Le client d'origine écrit « n’est » avec une apostrophe typographique, et
//      la recopier avec une apostrophe droite changerait le texte à l'écran sans qu'aucun
//      compilateur ne s'en plaigne.
//
// Les dates sont **fixées** (« 2026-03-10 » est un mardi) et non lues depuis l'horloge : un test
// qui dépend du jour où on le lance passe un jour et échoue le lendemain.
// ---------------------------------------------------------------------------

class ReviewTextTest {

    private val mardi = "2026-03-10"
    private val janvier = "2026-01-05"

    // -----------------------------------------------------------------------
    // Résumé
    // -----------------------------------------------------------------------

    @Test
    fun `le resume annonce la reference du premier verset, ou son absence`() {
        assertEquals("Rien à revoir", ReviewText.summaryReference(0, "Al-Fatiha 1–7"))
        assertEquals("Al-Fatiha 1–7", ReviewText.summaryReference(1, "Al-Fatiha 1–7"))
    }

    @Test
    fun `une colonne qui porte plusieurs taches le dit par des points de suspension`() {
        // Sans eux, on croirait que la colonne ne contient qu'une plage — et le résumé sert
        // justement à décider si l'on ouvre la carte.
        assertEquals("Al-Fatiha 1–7…", ReviewText.summaryReference(3, "Al-Fatiha 1–7"))
    }

    @Test
    fun `le resume du corpus accorde le compte des versets`() {
        // Écart assumé avec la source, qui écrit « 1 versets mémorisés » : le cas n = 1 est
        // atteint dès la première révision, et la faute se voit.
        assertEquals("1 verset mémorisé · 0 Juz’ · 1 Rubu’ · 2 Nisf", ReviewText.corpusSummary(1, 0, 1, 2))
        assertEquals(
            "12 versets mémorisés · 3 Juz’ · 1 Rubu’ · 0 Nisf",
            ReviewText.corpusSummary(12, 3, 1, 0),
        )
    }

    // -----------------------------------------------------------------------
    // Carte du jour
    // -----------------------------------------------------------------------

    @Test
    fun `le pied de la carte du jour suit la presence d'une seance`() {
        assertTrue(ReviewText.dayFooter(true).startsWith("La séance du jour regroupe"))
        assertTrue(ReviewText.dayFooter(false).startsWith("Ta révision du jour est terminée"))
    }

    // -----------------------------------------------------------------------
    // Carte du cycle
    // -----------------------------------------------------------------------

    @Test
    fun `les deux formes du mode de cycle ne se confondent pas`() {
        // Une seule des deux s'affiche, et c'est `reviewSettings.mode` qui choisit. Les
        // confondre ferait annoncer un rythme quotidien là où c'est la durée du cycle qui
        // compte — deux choses différentes pour la personne qui doit décider quoi réviser.
        assertEquals("1 Hizb / jour", ReviewText.cycleModeLabel("quantity", "hizb", 7))
        assertEquals("2 Juz / jour", ReviewText.cycleModeLabel("quantity", "juz2", 7))
        assertEquals("Cycle de 14 jours", ReviewText.cycleModeLabel("cycle", "hizb", 14))
    }

    @Test
    fun `un mode absent retombe sur la duree du cycle`() {
        // Le client d'origine teste `mode==='quantity'` et rien d'autre : tout le reste — y
        // compris l'absence de réglage — s'affiche comme une durée.
        assertEquals("Cycle de 7 jours", ReviewText.cycleModeLabel(null, null, 7))
        assertEquals("Cycle de 30 jours", ReviewText.cycleModeLabel("", null, 30))
    }

    @Test
    fun `une quantite inconnue retombe sur le hizb`() {
        // Repli nommé et non silencieux : il s'affiche, donc un réglage corrompu se voit au lieu
        // de produire un bouton vide.
        assertEquals("1 Nisf / jour", ReviewText.quantityLabel("nisf"))
        assertEquals("1 Hizb / jour", ReviewText.quantityLabel(null))
        assertEquals("1 Hizb / jour", ReviewText.quantityLabel("nimporte-quoi"))
    }

    @Test
    fun `les quantites proposees suivent l'ordre du client d'origine`() {
        // Du plus petit au plus grand : un ordre différent ferait choisir « 2 Juz » en croyant
        // choisir « 1 Nisf ».
        assertEquals(listOf("nisf", "hizb", "juz", "juz2"), ReviewText.QUANTITY_KEYS)
        assertEquals(listOf(7, 14, 21, 30), ReviewText.CYCLE_DAY_OPTIONS)
        assertEquals("7 jours", ReviewText.cycleOptionLabel(7))
    }

    @Test
    fun `le compteur de cycle porte le jour et la duree`() {
        assertEquals("Jour 3 / 7", ReviewText.dayCounter(3, 7))
        assertEquals("Jour 1 / 30", ReviewText.dayCounter(1, 30))
    }

    @Test
    fun `le pourcentage est arrondi a l'entier`() {
        // Le client d'origine arrondit avec `Math.round`. Deux arrondis différents feraient
        // afficher « 42 % » ici et « 43 % » là pour le même état.
        assertEquals("43 % réellement révisés", ReviewText.percentReviewed(0.425))
        assertEquals("44 % réellement révisés", ReviewText.percentReviewed(0.435))
        assertEquals("0 % réellement révisés", ReviewText.percentReviewed(0.0))
        assertEquals("100 % réellement révisés", ReviewText.percentReviewed(1.0))
    }

    @Test
    fun `le rythme d'un cycle vide n'annonce pas zero verset par jour`() {
        // « 0 verset / jour » se lirait comme un rythme choisi alors que c'est un corpus absent.
        assertEquals(ReviewText.RHYTHM_EMPTY, ReviewText.rhythmLine(false, "1 Hizb / jour"))
        assertEquals("1 Hizb / jour", ReviewText.rhythmLine(true, "1 Hizb / jour"))
    }

    // -----------------------------------------------------------------------
    // Carte des consolidations
    // -----------------------------------------------------------------------

    @Test
    fun `l'etat d'une etape suit sa validation puis son echeance`() {
        assertEquals("Consolidé", ReviewText.stepStatus(completed = mardi, due = "2026-03-08", at = mardi))
        assertEquals("Aujourd’hui", ReviewText.stepStatus(completed = null, due = mardi, at = mardi))
        assertEquals("À rattraper", ReviewText.stepStatus(completed = null, due = "2026-03-08", at = mardi))
        assertEquals("À venir", ReviewText.stepStatus(completed = null, due = "2026-03-12", at = mardi))
    }

    @Test
    fun `une etape validee reste consolidee meme si son echeance est passee`() {
        // C'est le fait qui l'emporte sur l'échéance : afficher « À rattraper » sur une étape
        // faite demanderait de refaire un travail déjà fait.
        assertEquals("Consolidé", ReviewText.stepStatus(completed = "2026-03-20", due = "2026-03-01", at = mardi))
    }

    @Test
    fun `une etape validee affiche sa date, une etape due la sienne`() {
        // Montrer l'échéance d'une étape déjà validée ferait croire qu'elle est en retard.
        assertEquals("10 mars", ReviewText.stepDate(completed = null, due = mardi))
        assertEquals("5 janv.", ReviewText.stepDate(completed = janvier, due = mardi))
    }

    @Test
    fun `le mois d'une echeance est abrege, celui d'un apprentissage est en toutes lettres`() {
        // Deux formats distincts chez le client d'origine : `{day:'numeric',month:'short'}` pour
        // une échéance, la date complète pour un apprentissage. En mars les deux se ressemblent —
        // c'est janvier qui les sépare.
        assertEquals("5 janv.", ReviewText.stepDate(completed = null, due = janvier))
        assertEquals("1 verset · appris le 5 janvier 2026", ReviewText.consolidationDetail("1 verset", janvier))
    }

    @Test
    fun `le detail d'une consolidation joint la quantite et la date d'apprentissage`() {
        assertEquals("5 versets · appris le 10 mars 2026", ReviewText.consolidationDetail("5 versets", mardi))
    }

    @Test
    fun `une cle qui n'est pas une date s'affiche telle quelle`() {
        // Repli délibéré, et écart assumé avec la source : le client d'origine écrirait
        // « Invalid Date » à l'écran. Ici la valeur fautive reste lisible.
        assertEquals("pas-une-date", ReviewText.stepDate(completed = null, due = "pas-une-date"))
        assertEquals("1 verset · appris le 2026-13-45", ReviewText.consolidationDetail("1 verset", "2026-13-45"))
    }

    @Test
    fun `les deux bascules se replient avec le meme mot`() {
        assertEquals("Voir toutes les consolidations", ReviewText.consolidationToggle(false))
        assertEquals("Réduire", ReviewText.consolidationToggle(true))
        assertEquals("Voir tous les versets prioritaires", ReviewText.priorityToggle(false))
        assertEquals("Réduire", ReviewText.priorityToggle(true))
    }

    @Test
    fun `le libelle lu par un lecteur d'ecran nomme la plage consolidee`() {
        // Le bouton n'a pas de texte visible : sans ce libellé, TalkBack annoncerait un bouton
        // sans nom et l'on ne saurait pas quelle plage on consolide.
        assertEquals("Consolider Al-Fatiha 1–7", ReviewText.consolidateLabel("Al-Fatiha 1–7"))
    }

    // -----------------------------------------------------------------------
    // Carte des versets prioritaires
    // -----------------------------------------------------------------------

    @Test
    fun `le compte des versets prioritaires precede son detail`() {
        assertEquals("3 versets à retravailler aujourd’hui", ReviewText.priorityHeadline("3 versets"))
        assertEquals(
            "1 verset · marqué lors d’une révision précédente",
            ReviewText.priorityDetail("1 verset"),
        )
    }

    // -----------------------------------------------------------------------
    // Carte du suivi
    // -----------------------------------------------------------------------

    @Test
    fun `le compte des revisions s'accorde en nombre`() {
        // Écart assumé avec la source, qui écrit « 1 révisions effectuées ». Le cas n = 1 est
        // atteint dès la première révision.
        assertEquals("1 révision effectuée", ReviewText.revisionCount(1))
        assertEquals("0 révisions effectuées", ReviewText.revisionCount(0))
        assertEquals("3 révisions effectuées", ReviewText.revisionCount(3))
    }

    // -----------------------------------------------------------------------
    // Barre d'action
    // -----------------------------------------------------------------------

    @Test
    fun `les cinq gestes portent les mots du client d'origine`() {
        assertEquals("Parfait", ReviewText.actionLabel(ReviewText.Action.PERFECT))
        assertEquals("Quelques\nhésitations", ReviewText.actionLabel(ReviewText.Action.HESITANT))
        assertEquals("À\nretravailler", ReviewText.actionLabel(ReviewText.Action.REWORK))
        assertEquals("Écouter", ReviewText.actionLabel(ReviewText.Action.LISTEN))
        assertEquals("Ma voix", ReviewText.actionLabel(ReviewText.Action.RECORD))
    }

    @Test
    fun `deux gestes sur cinq portent un saut de ligne`() {
        // Ce n'est pas une mise en forme : sur une barre à cinq colonnes, le mot entier ne
        // tiendrait pas, et le client d'origine coupe au même endroit.
        val coupes = ReviewText.Action.entries.filter { ReviewText.actionLabel(it).contains("\n") }
        assertEquals(listOf(ReviewText.Action.HESITANT, ReviewText.Action.REWORK), coupes)
    }

    @Test
    fun `trois gestes sur cinq portent un grade, et deux n'en portent pas`() {
        // « Écouter » et « Ma voix » ne notent pas la récitation. Leur donner un grade ferait
        // valider une révision qu'on n'a pas faite.
        assertEquals(ReviewGrade.PERFECT, ReviewText.Action.PERFECT.grade)
        assertEquals(ReviewGrade.HESITANT, ReviewText.Action.HESITANT.grade)
        assertEquals(ReviewGrade.REWORK, ReviewText.Action.REWORK.grade)
        assertNull(ReviewText.Action.LISTEN.grade)
        assertNull(ReviewText.Action.RECORD.grade)
    }

    @Test
    fun `l'ordre des gestes suit celui du client d'origine`() {
        // Un trait sépare les trois grades des deux gestes d'écoute, et c'est le troisième rang
        // qui le porte : déplacer un geste déplacerait le trait.
        assertEquals(
            listOf("Parfait", "Quelques\nhésitations", "À\nretravailler", "Écouter", "Ma voix"),
            ReviewText.Action.entries.map { ReviewText.actionLabel(it) },
        )
    }

    @Test
    fun `le mot d'un grade est celui de son bouton`() {
        // Dérivé de `actionLabel` plutôt qu'écrit une seconde fois : deux copies finiraient par
        // dire deux choses différentes, et le grade validé ne serait plus celui qu'on a lu.
        for (grade in ReviewGrade.entries) {
            assertEquals(ReviewText.actionLabel(ReviewText.Action.entries.first { it.grade == grade }), ReviewText.gradeLabel(grade))
        }
        assertEquals("Parfait", ReviewText.gradeLabel(ReviewGrade.PERFECT))
    }

    // -----------------------------------------------------------------------
    // Apostrophes et ordre du résumé
    // -----------------------------------------------------------------------

    @Test
    fun `les apostrophes des libelles sont typographiques, comme dans la source`() {
        // Recopier « n'est » avec une apostrophe droite changerait le texte à l'écran sans
        // qu'aucun compilateur ne s'en plaigne. Le client d'origine écrit « n’est ».
        assertTrue(ReviewText.DAY_FOOTER_EMPTY.contains("n’est"))
        assertFalse(ReviewText.DAY_FOOTER_EMPTY.contains("n'est"))
        assertTrue(ReviewText.CONSOLIDATION_NOTE.contains("d’intégrer"))
        assertFalse(ReviewText.CONSOLIDATION_NOTE.contains("d'intégrer"))
        assertTrue(ReviewText.PRIORITY_EMPTY.contains("aujourd’hui"))
        assertTrue(ReviewText.priorityDetail("1 verset").contains("d’une"))
    }

    @Test
    fun `le resume suit l'ordre des cartes qui le suivent`() {
        // Le cycle du jour, puis les consolidations, puis les priorités : c'est l'ordre des
        // cartes de l'écran. Un ordre différent ferait lire le résumé à l'envers.
        assertEquals(
            listOf("Cycle du jour", "Consolidation", "À retravailler"),
            ReviewText.SummaryKind.entries.map { it.label },
        )
        assertEquals(listOf("à revoir", "révisés", "restants"), ReviewText.CYCLE_STAT_LABELS)
    }
}
