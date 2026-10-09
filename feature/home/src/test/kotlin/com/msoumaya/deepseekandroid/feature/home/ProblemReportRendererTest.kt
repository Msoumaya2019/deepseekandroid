package com.msoumaya.deepseekandroid.feature.home

import com.msoumaya.deepseekandroid.core.domain.ProblemReportText
import com.msoumaya.deepseekandroid.core.domain.ProblemReports
import com.msoumaya.deepseekandroid.core.model.ProblemReportType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le banc des décisions de l'écran de signalement.
 *
 * ## Ce qui est mesuré ici, et pourquoi c'est ici
 *
 * Les **mots** sont mesurés dans `core:domain/ProblemReportsTest`, là où ils sont écrits. Ce
 * fichier-ci mesure ce que le calcul en **fait** : lequel des deux libellés de la capture
 * s'affiche, quand le bouton d'envoi s'allume, quel message gagne entre un refus de capture et une
 * issue d'envoi, dans quel ordre les cinq natures apparaissent, et ce qu'un fichier refusé fait
 * dire.
 *
 * Aucune de ces décisions ne lève d'exception quand elle est fausse, et chacune a une conséquence
 * visible :
 *
 *  - confondre les deux libellés de la capture annoncerait « Ajouter une capture » sur une capture
 *    déjà jointe, et la personne en ajouterait une seconde en croyant remplacer la première ;
 *  - allumer le bouton sur un texte fait d'espaces enverrait un signalement que le serveur refuse
 *    (`btrim(description) between 1 and 500`), après l'attente ;
 *  - compter le texte rogné ferait **reculer** le compteur quand on ajoute une espace, ce qui se
 *    voit et ne s'explique pas ;
 *  - laisser gagner l'issue d'envoi sur un refus de capture afficherait « enregistré » sous une
 *    capture que personne n'a pu joindre ;
 *  - juger la taille avant le format changerait le message d'une image trop lourde **et** au
 *    mauvais format — l'original dit le format.
 *
 * ## Ce qu'il ne prouve pas
 *
 * Ni la mise en page, ni l'accessibilité, ni le geste du sélecteur d'images : ces trois choses
 * demandent un appareil. Le sélecteur est d'ailleurs injecté pour cette raison — voir
 * `ProblemReportScreenshotPicker`.
 */
class ProblemReportRendererTest {

    /** Un rendu sans rien : c'est ce que la carte de l'accueil demande. */
    private fun vide() = ProblemReportRenderer.render()

    // ---------------------------------------------------------------- les natures

    @Test
    fun `les cinq natures sont celles de l'ecran, dans l'ordre`() {
        // L'ordre est celui de `problemTypes` dans l'original, et celui de l'énumération du modèle.
        // Le renderer ne le compose pas : il le lit. Un test de `core:model` le fige déjà là-bas,
        // et celui-ci vérifie qu'il ne le réordonne pas en chemin.
        assertEquals(
            listOf("Bug", "Affichage", "Audio", "Notification", "Autre"),
            vide().natures.map { it.wire },
        )
    }

    @Test
    fun `la nature cochee par defaut est la premiere de la liste`() {
        // L'original écrit `useState<ProblemType>('Bug')`. Ici, le défaut est **la première de la
        // liste** : si l'ordre changeait, la nature cochée suivrait — et non un `Bug` figé qui
        // désignerait peut-être la mauvaise.
        val ui = vide()
        assertEquals(ui.natures.first(), ui.initialType)
        assertEquals(ProblemReportType.BUG, ui.initialType)
    }

    // ---------------------------------------------------------------- la description

    @Test
    fun `le compteur compte le texte brut, et non le texte rogne`() {
        // `{description.length}/500` dans l'original. Une espace compte donc, et le compteur ne
        // recule jamais quand on en ajoute une — alors que la règle d'envoi, elle, les ignore.
        assertEquals("6/500", ProblemReportRenderer.render(description = "  ab  ").counter)
        assertEquals("2/500", ProblemReportRenderer.render(description = "ab").counter)
        assertEquals("0/500", ProblemReportRenderer.render(description = "").counter)
    }

    @Test
    fun `la borne du champ est celle du domaine`() {
        assertEquals(ProblemReports.DESCRIPTION_MAX, vide().descriptionMax)
    }

    @Test
    fun `le bouton d'envoi s'allume sur un texte non vide, une fois rogne`() {
        assertTrue(ProblemReportRenderer.canSend("un bug", busy = false))
        assertTrue(ProblemReportRenderer.canSend("  un bug  ", busy = false))
        assertFalse(ProblemReportRenderer.canSend("", busy = false))
        // Une description faite d'espaces est **vide** : c'est la règle du domaine, et le bouton la
        // suit. L'activer enverrait un signalement que le serveur refuserait.
        assertFalse(ProblemReportRenderer.canSend("     ", busy = false))
        assertFalse(ProblemReportRenderer.canSend("\n\t ", busy = false))
    }

    @Test
    fun `le bouton d'envoi est inactif pendant un envoi`() {
        assertFalse(ProblemReportRenderer.canSend("un bug", busy = true))
    }

    @Test
    fun `le libelle du bouton dit l'envoi en cours`() {
        assertEquals(ProblemReportText.SEND, vide().sendLabel)
        assertEquals(
            ProblemReportText.SENDING,
            ProblemReportRenderer.render(busy = true).sendLabel,
        )
    }

    // ---------------------------------------------------------------- la capture

    @Test
    fun `les deux libelles de la capture suivent la piece jointe`() {
        val sans = ProblemReportRenderer.render(hasAttachment = false)
        assertEquals(ProblemReportText.ATTACH_ADD, sans.attachTitle)
        assertEquals(ProblemReportText.ATTACH_HINT, sans.attachSubtitle)
        assertFalse(sans.hasAttachment)

        val avec = ProblemReportRenderer.render(hasAttachment = true)
        assertEquals(ProblemReportText.ATTACH_ADDED, avec.attachTitle)
        assertEquals(ProblemReportText.ATTACH_REPLACE, avec.attachSubtitle)
        assertTrue(avec.hasAttachment)
    }

    @Test
    fun `le bouton de retrait est toujours celui de l'original`() {
        // Il n'est affiché que lorsqu'une capture est jointe — c'est l'écran qui le décide —, mais
        // son libellé ne dépend d'aucun état : le rendre variable serait une occasion de le faire
        // diverger de l'original sans que rien ne le montre.
        assertEquals(ProblemReportText.ATTACH_REMOVE, vide().removeLabel)
        assertEquals(ProblemReportText.ATTACH_REMOVE, ProblemReportRenderer.render(hasAttachment = true).removeLabel)
    }

    @Test
    fun `un fichier refuse dit lequel des deux regles le refuse`() {
        assertEquals(
            ProblemReportText.ATTACHMENT_FORMAT,
            ProblemReportRenderer.pickProblem("image/gif", 1_024),
        )
        assertNull(ProblemReportRenderer.pickProblem(ProblemReports.MIME_PNG, 1_024))
        assertNull(ProblemReportRenderer.pickProblem(ProblemReports.MIME_JPEG, 1_024))
        // La borne de taille est **stricte** : un fichier de très exactement cinq mégaoctets passe,
        // comme le compartiment le laisse passer.
        assertNull(
            ProblemReportRenderer.pickProblem(ProblemReports.MIME_PNG, ProblemReports.SCREENSHOT_MAX_BYTES),
        )
        assertEquals(
            ProblemReportText.ATTACHMENT_TOO_LARGE,
            ProblemReportRenderer.pickProblem(
                ProblemReports.MIME_JPEG,
                ProblemReports.SCREENSHOT_MAX_BYTES + 1,
            ),
        )
    }

    @Test
    fun `le format est juge avant la taille`() {
        // Un fichier au mauvais format **et** trop lourd reçoit le message du format. C'est
        // l'original, et l'inverser changerait le message, pas seulement son ordre.
        assertEquals(
            ProblemReportText.ATTACHMENT_FORMAT,
            ProblemReportRenderer.pickProblem(
                "image/gif",
                ProblemReports.SCREENSHOT_MAX_BYTES * 2,
            ),
        )
    }

    // ---------------------------------------------------------------- le message

    @Test
    fun `l'echec du choix de capture l'emporte sur l'issue de l'envoi`() {
        // Les deux ne peuvent pas être posés en même temps — chaque geste commence par effacer les
        // deux, comme le `setNotice('')` de l'original —, mais la règle est écrite ici, et un test
        // la fige : c'est le refus de capture qui doit se lire, parce que c'est le geste que la
        // personne vient de faire.
        assertEquals(
            "refus de capture",
            ProblemReportRenderer.render(
                notice = "issue d'envoi",
                attachNotice = "refus de capture",
            ).notice,
        )
    }

    @Test
    fun `sans refus de capture, c'est l'issue de l'envoi qui se lit`() {
        assertEquals(
            ProblemReportText.QUEUED,
            ProblemReportRenderer.render(notice = ProblemReportText.QUEUED).notice,
        )
        assertNull(vide().notice)
    }

    // ---------------------------------------------------------------- l'ecran de confirmation

    @Test
    fun `l'ecran de confirmation est celui de l'issue, et rien d'autre`() {
        val ui = ProblemReportRenderer.render(done = true, notice = ProblemReportText.SENT)
        assertTrue(ui.done)
        assertEquals(ProblemReportText.SENT, ui.notice)
        // Les deux étiquettes de fermeture sont distinctes : l'une dit « Fermer », l'autre « Fermer
        // le signalement » — la seconde est lue par un lecteur d'écran sur le fond de la feuille, et
        // elle annonce **ce qu'on ferme**.
        assertEquals(ProblemReportText.CLOSE, ui.closeLabel)
        assertEquals(ProblemReportText.DISMISS, ui.dismissLabel)
        assertTrue(ui.closeLabel != ui.dismissLabel)
    }
}
