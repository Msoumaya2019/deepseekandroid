package com.msoumaya.deepseekandroid.feature.reader

import com.msoumaya.deepseekandroid.core.domain.RecitationAction
import com.msoumaya.deepseekandroid.core.domain.RecitationPhase
import com.msoumaya.deepseekandroid.core.domain.RecitationRecorder
import com.msoumaya.deepseekandroid.core.domain.RecitationText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Éprouve ce que la barre d'enregistrement réduite **affiche**, phase par phase.
 *
 * ## Ce que ces contrôles séparent
 *
 * Trois choses distinctes, et les confondre rendrait l'une des trois fausse :
 *
 *  - **la ligne d'état** — le mot de la phase, le séparateur, et la durée qui s'y accole ou non ;
 *  - **les gestes** — lesquels, dans quel ordre, avec quel mot et quelle mise en avant ;
 *  - **le message** — quand il y en a un.
 *
 * ## Pourquoi les mots ne sont pas recopiés ici
 *
 * Les libellés vivent dans `RecitationText` et le **choix** entre les mots de la barre réduite et
 * ceux de la pleine page est une règle du domaine, déjà éprouvée là-bas. Les recopier ici ferait
 * une seconde copie de la même vérité : le jour où un mot changerait, ce test-ci resterait vert en
 * affirmant l'ancien. Ce qui s'éprouve ici est donc ce que le **rendu** ajoute — l'ordre, la
 * composition, la mise en avant, la disparition —, et non les mots eux-mêmes.
 *
 * La seule exception est le **séparateur**, qui n'appartient à personne d'autre : l'original
 * l'écrit dans son JSX, et son point de code est fixé ici parce qu'un point médian remplacé par un
 * point ordinaire ne se voit pas à la lecture.
 */
class RecitationRecorderRendererTest {

    // -----------------------------------------------------------------------
    // La ligne d'état
    // -----------------------------------------------------------------------

    @Test
    fun `la ligne d'etat de chaque phase est celle de l'original`() {
        assertEquals(
            RecitationText.PHASE_IDLE,
            barre(RecitationPhase.IDLE).status,
        )
        assertEquals(
            RecitationText.RECORDING + " \u00B7 00:07",
            barre(RecitationPhase.RECORDING, liveMs = 7_000).status,
        )
        assertEquals(
            RecitationText.PAUSED_COMPACT + " \u00B7 00:07",
            barre(RecitationPhase.PAUSED, liveMs = 7_000).status,
        )
        assertEquals(
            RecitationText.PHASE_PREVIEW + " \u00B7 00:07",
            barre(RecitationPhase.PREVIEW, liveMs = 7_000).status,
        )
        assertEquals(
            RecitationText.PHASE_SAVED + " \u00B7 00:07",
            barre(RecitationPhase.SAVED, liveMs = 7_000).status,
        )
    }

    @Test
    fun `la phase de repos ne porte aucune duree`() {
        // Un `00:00` figé à côté de « Enregistrement personnel » se lirait comme un enregistrement
        // vide. L'original n'ajoute la durée que si la phase n'est pas `idle`.
        val ligne = barre(RecitationPhase.IDLE, draftMs = 5_000, itemMs = 6_000, liveMs = 7_000).status
        assertEquals(RecitationText.PHASE_IDLE, ligne)
        assertFalse(ligne.contains(RecitationRecorderRenderer.STATUS_SEPARATOR))
    }

    @Test
    fun `le separateur est un point median entre deux espaces`() {
        // Trois caractères, et le deuxième est U+00B7 — le `' · '` de l'original. L'éprouver par
        // son point de code plutôt que par sa forme : c'est la seule façon de distinguer un point
        // médian d'un point ordinaire ou d'un caractère devenu `?` en chemin.
        val separateur = RecitationRecorderRenderer.STATUS_SEPARATOR
        assertEquals(3, separateur.length)
        assertEquals(' ', separateur[0])
        assertEquals(0x00B7, separateur[1].code)
        assertEquals(' ', separateur[2])
    }

    @Test
    fun `la duree apparait exactement quand le domaine en rend une`() {
        // L'original décide de la présence de la durée par la **phase** (`phase!=='idle'`) ; le
        // rendu, lui, la décide par la **présence d'une durée**. Deux façons de décider, une seule
        // vérité : elles doivent coïncider sur les cinq phases, et pas seulement sur celle qu'on
        // pense à vérifier.
        for (phase in RecitationPhase.entries) {
            val duree = RecitationRecorder.shownDurationMs(
                phase,
                draftMs = 5_000,
                itemMs = 6_000,
                liveMs = 7_000,
            )
            val ligne = barre(phase, draftMs = 5_000, itemMs = 6_000, liveMs = 7_000).status
            assertEquals(
                duree != null,
                ligne.contains(RecitationRecorderRenderer.STATUS_SEPARATOR),
                "$phase : le domaine rend $duree, la ligne dit « $ligne »",
            )
        }
    }

    @Test
    fun `le brouillon prime sur la recitation gardee, qui prime sur le compteur vivant`() {
        // L'ordre des trois sources est une règle du domaine, et c'est ici qu'on voit qu'elle est
        // bien suivie : les trois valeurs sont distinctes, donc chacune des trois lignes nomme
        // celle qui a gagné.
        assertEquals(
            RecitationText.PHASE_PREVIEW + " \u00B7 00:05",
            barre(RecitationPhase.PREVIEW, draftMs = 5_000, itemMs = 65_000, liveMs = 7_000).status,
        )
        assertEquals(
            RecitationText.PHASE_SAVED + " \u00B7 01:05",
            barre(RecitationPhase.SAVED, draftMs = null, itemMs = 65_000, liveMs = 7_000).status,
        )
        assertEquals(
            RecitationText.RECORDING + " \u00B7 00:07",
            barre(RecitationPhase.RECORDING, draftMs = null, itemMs = null, liveMs = 7_000).status,
        )
    }

    @Test
    fun `la minute ne se replie pas a soixante`() {
        // `clock` est une règle du domaine, déjà éprouvée là-bas. Ce qui se vérifie ici est qu'elle
        // est bien **traversée** par le rendu, et non remplacée par un formatage local : une heure
        // d'enregistrement s'affiche `60:00`, jamais `00:00`.
        assertEquals(
            RecitationText.RECORDING + " \u00B7 60:00",
            barre(RecitationPhase.RECORDING, liveMs = 3_600_000).status,
        )
    }

    // -----------------------------------------------------------------------
    // Les gestes
    // -----------------------------------------------------------------------

    @Test
    fun `les gestes de chaque phase sont ceux de l'original, dans l'ordre`() {
        // La transcription des six conditions indépendantes de l'original (lignes 84 à 89). L'ordre
        // compte : c'est une barre d'actions, et permuter deux boutons change le geste du pouce.
        assertEquals(
            listOf(RecitationAction.BEGIN),
            actions(RecitationPhase.IDLE),
        )
        assertEquals(
            listOf(RecitationAction.PAUSE, RecitationAction.FINISH, RecitationAction.CANCEL),
            actions(RecitationPhase.RECORDING),
        )
        assertEquals(
            listOf(RecitationAction.RESUME, RecitationAction.FINISH, RecitationAction.CANCEL),
            actions(RecitationPhase.PAUSED),
        )
        assertEquals(
            listOf(RecitationAction.LISTEN, RecitationAction.RESTART, RecitationAction.SAVE),
            actions(RecitationPhase.PREVIEW),
        )
        assertEquals(
            listOf(RecitationAction.LISTEN, RecitationAction.RESTART, RecitationAction.SHARE),
            actions(RecitationPhase.SAVED, canShare = true),
        )
    }

    @Test
    fun `le partage n'apparait qu'une fois la recitation gardee, et seulement si l'ecran sait le faire`() {
        // Les deux conditions de l'original (`item && onShare`) sont réunies en un seul booléen. Ce
        // contrôle fixe les quatre combinaisons qui existent — il n'y en a pas d'autre, aucune
        // phase autre que `saved` ne peut offrir le partage.
        assertFalse(actions(RecitationPhase.SAVED, canShare = false).contains(RecitationAction.SHARE))
        assertTrue(actions(RecitationPhase.SAVED, canShare = true).contains(RecitationAction.SHARE))

        for (phase in RecitationPhase.entries - RecitationPhase.SAVED) {
            for (partage in listOf(true, false)) {
                assertFalse(
                    actions(phase, canShare = partage).contains(RecitationAction.SHARE),
                    "$phase offre le partage alors que rien n'est encore gardé",
                )
            }
        }
    }

    @Test
    fun `aucune phase ne laisse la barre sans geste`() {
        // Contrairement à `RevisionActionBar`, qui disparaît quand aucun geste n'a de destination,
        // cette barre est le seul chemin : une phase sans geste serait un cul-de-sac.
        for (phase in RecitationPhase.entries) {
            for (partage in listOf(true, false)) {
                assertTrue(
                    actions(phase, canShare = partage).isNotEmpty(),
                    "$phase n'offre aucun geste",
                )
            }
        }
    }

    @Test
    fun `un seul geste est mis en avant, sauf quand il n'y a plus rien a engager`() {
        // L'original met en avant le geste qu'on attend — commencer, terminer, enregistrer — et
        // aucun autre. La phase `saved` n'en a **aucun** : la récitation est gardée, il ne reste
        // que des relectures, et en mettre une en avant inviterait à recommencer un travail fini.
        for (phase in RecitationPhase.entries) {
            val mis = barre(phase, canShare = true).actions.count { it.primary }
            if (phase == RecitationPhase.SAVED) {
                assertEquals(0, mis, "une récitation gardée ne met aucun geste en avant")
            } else {
                assertEquals(1, mis, "$phase devrait mettre exactement un geste en avant, il en met $mis")
            }
        }
    }

    @Test
    fun `le geste mis en avant est celui du domaine, et pas un autre`() {
        // Le rendu ne choisit pas : il demande `isPrimary`. Ce contrôle relie les deux, phase par
        // phase, pour qu'un geste ajouté au domaine sans mise en avant se voie ici.
        for (phase in RecitationPhase.entries) {
            for (geste in barre(phase, canShare = true).actions) {
                assertEquals(
                    RecitationRecorder.isPrimary(geste.action),
                    geste.primary,
                    "$phase : $geste",
                )
            }
        }
    }

    @Test
    fun `le libelle d'un geste vient du domaine, et n'est pas recopie ici`() {
        for (phase in RecitationPhase.entries) {
            for (geste in barre(phase, canShare = true).actions) {
                assertEquals(
                    RecitationRecorder.compactActionLabel(geste.action),
                    geste.label,
                    "$phase : le rendu a écrit un mot que le domaine ne dit pas pour ${geste.action}",
                )
            }
        }
    }

    @Test
    fun `les mots sont ceux de la barre reduite, pas ceux de la pleine page`() {
        // Deux gestes portent deux mots selon la mise en page. La barre réduite doit dire les
        // siens : c'est précisément ce qui a fait sortir les libellés de l'énumération.
        val commencer = barre(RecitationPhase.IDLE).actions.single { it.action == RecitationAction.BEGIN }
        assertEquals(RecitationText.BAR_BEGIN, commencer.label)
        assertTrue(RecitationText.BAR_BEGIN != RecitationText.PAGE_BEGIN)

        val partager = barre(RecitationPhase.SAVED, canShare = true)
            .actions.single { it.action == RecitationAction.SHARE }
        assertEquals(RecitationText.BAR_SHARE, partager.label)
        assertTrue(RecitationText.BAR_SHARE != RecitationText.PAGE_SHARE)

        val terminer = barre(RecitationPhase.RECORDING)
            .actions.single { it.action == RecitationAction.FINISH }
        assertEquals(RecitationText.BAR_FINISH, terminer.label)
        assertTrue(RecitationText.BAR_FINISH != RecitationText.PAGE_FINISH_QURAN)
    }

    // -----------------------------------------------------------------------
    // La couleur de la ligne et le message
    // -----------------------------------------------------------------------

    @Test
    fun `la capture est la seule phase qui prend la couleur d'alerte`() {
        for (phase in RecitationPhase.entries) {
            assertEquals(
                phase == RecitationPhase.RECORDING,
                barre(phase).capturing,
                "$phase : la couleur d'alerte ne va qu'à la capture",
            )
        }
    }

    @Test
    fun `un message vide ne se dessine pas, une phrase si`() {
        assertNull(barre(RecitationPhase.IDLE, message = null).message)
        assertNull(barre(RecitationPhase.IDLE, message = "").message)

        // L'original teste la **longueur** de la chaîne (`!!message`), pas son contenu : une phrase
        // faite d'espaces reste affichée. `ifBlank` serait un écart, et ce contrôle l'empêcherait.
        val espaces = barre(RecitationPhase.IDLE, message = "   ")
        assertEquals("   ", espaces.message)

        val phrase = RecitationText.RECORDING_CANCELLED
        assertEquals(phrase, barre(RecitationPhase.IDLE, message = phrase).message)
        assertNotNull(barre(RecitationPhase.RECORDING, message = phrase).message)
    }

    // -----------------------------------------------------------------------
    // Le confort du test
    // -----------------------------------------------------------------------

    /** L'état affiché d'une phase, avec les valeurs qu'on ne précise pas. */
    private fun barre(
        phase: RecitationPhase,
        draftMs: Long? = null,
        itemMs: Long? = null,
        liveMs: Long = 0,
        canShare: Boolean = false,
        message: String? = null,
    ): RecitationRecorderUi = RecitationRecorderRenderer.render(
        phase = phase,
        draftMs = draftMs,
        itemMs = itemMs,
        liveMs = liveMs,
        canShare = canShare,
        message = message,
    )

    /** Les gestes d'une phase, réduits à leur identité. */
    private fun actions(phase: RecitationPhase, canShare: Boolean = false): List<RecitationAction> =
        barre(phase, canShare = canShare).actions.map { it.action }
}
