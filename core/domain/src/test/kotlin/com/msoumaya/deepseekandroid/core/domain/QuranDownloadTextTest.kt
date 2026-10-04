package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.MushafSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Éprouve les mots et les règles du panneau de téléchargement.
 *
 * Deux d'entre elles se voient à l'usage et se paient cher :
 *
 *  1. **le bouton « Mettre en pause » n'existe que pendant le transfert.** Proposé pendant
 *     l'écriture des 9 060 fichiers, il interromprait une écriture au milieu d'un fichier ;
 *  2. **le bouton d'action disparaît pendant qu'un travail est en cours.** Deux boutons
 *     concurrents sur un même écran donnent deux chemins d'exécution, et l'un des deux est
 *     forcément faux.
 */
class QuranDownloadTextTest {

    private val phases = ArchivePhase.entries.toList()

    @Test
    fun `les libelles sont ceux du client d'origine`() {
        assertEquals("Coran 1441", QuranDownloadText.TITLE)
        assertEquals(
            "Téléchargement initial · environ 98 Mo. Les pages resteront disponibles hors connexion.",
            QuranDownloadText.SUBTITLE,
        )
        assertEquals("Téléchargement", QuranDownloadText.DOWNLOADING)
        assertEquals("Installation des pages", QuranDownloadText.EXTRACTING)
        assertEquals("Mettre en pause", QuranDownloadText.PAUSE)
        assertEquals("Télécharger et utiliser", QuranDownloadText.START)
        assertEquals("Reprendre le téléchargement", QuranDownloadText.RESUME)
        assertEquals("Retour", QuranDownloadText.BACK)
        assertEquals("Affichage du Coran", QuranDownloadText.PICKER_TITLE)
        assertEquals("Coran de Médine", QuranDownloadText.MEDINA_LABEL)
        assertEquals("Coran avec règles de Tajwid", QuranDownloadText.TEST_LABEL)
        assertEquals("Lecture simplifiée", QuranDownloadText.SIMPLIFIED_LABEL)
    }

    @Test
    fun `le chargement d'une source est annonce`() {
        // Une préparation peut durer : le paquet fait 102 Mo. Sans cette ligne, un appui sur une
        // présentation donnerait un écran qui ne bouge pas, et l'appui serait à refaire.
        assertEquals("Chargement du Coran…", QuranDownloadText.LOADING_SOURCE)
    }

    @Test
    fun `un echec de changement de source dit que la page precedente est conservee`() {
        // Le message est **fixe**, celui du client d'origine (`App.tsx:461`), et il porte deux
        // choses : que rien n'a changé, et qu'il faut réessayer. Le remplacer par le message de
        // l'exception ferait perdre la seconde — et le premier point est celui qui rassure.
        assertEquals(
            "Cette source n’a pas pu être chargée. La source précédente est conservée. Réessaie.",
            QuranDownloadText.SWITCH_FAILED,
        )
        // L'apostrophe est typographique, comme dans tous les libellés affichés de ce portage :
        // une apostrophe droite se voit à l'écran, et c'est le genre de détail qui trahit un
        // texte écrit ailleurs.
        assertFalse(QuranDownloadText.SWITCH_FAILED.contains("'"), "apostrophe droite")
        assertTrue(QuranDownloadText.SWITCH_FAILED.contains("’"), "apostrophe typographique attendue")
    }

    @Test
    fun `le message de paquet absent est le notre, et il le dit`() {
        // Ce libellé n'existe pas dans le client d'origine : son `ensureQuranSourcePage`
        // télécharge au lieu de refuser. Le test le fige pour qu'il ne soit pas pris plus tard
        // pour une traduction, et pour que son apostrophe reste typographique.
        assertEquals(
            "Le paquet « Coran 1441 » n’est pas encore installé.",
            QuranDownloadText.NOT_INSTALLED,
        )
        assertFalse(QuranDownloadText.NOT_INSTALLED.contains("'"), "apostrophe droite")
    }

    @Test
    fun `l'avancement n'est annonce que pendant un travail`() {
        for (phase in phases) {
            val attendu = phase == ArchivePhase.DOWNLOADING || phase == ArchivePhase.EXTRACTING
            assertEquals(attendu, QuranDownloadText.progressLabel(phase, 0.5f) != null, "phase $phase")
        }
        assertEquals(6, phases.size, "l'énumération des phases a changé : relire cette règle")
    }

    @Test
    fun `l'etiquette d'avancement nomme la phase et arrondit le pourcentage`() {
        assertEquals("Téléchargement · 0 %", QuranDownloadText.progressLabel(ArchivePhase.DOWNLOADING, 0f))
        assertEquals("Téléchargement · 37 %", QuranDownloadText.progressLabel(ArchivePhase.DOWNLOADING, 0.37f))
        assertEquals("Téléchargement · 50 %", QuranDownloadText.progressLabel(ArchivePhase.DOWNLOADING, 0.5f))
        // Arrondi au plus proche, comme `Math.round` du client d'origine : 98,5 % vaut 99 %.
        assertEquals("Téléchargement · 99 %", QuranDownloadText.progressLabel(ArchivePhase.DOWNLOADING, 0.985f))
        assertEquals("Installation des pages · 100 %", QuranDownloadText.progressLabel(ArchivePhase.EXTRACTING, 1f))
        assertEquals("Installation des pages · 12 %", QuranDownloadText.progressLabel(ArchivePhase.EXTRACTING, 0.12f))
    }

    @Test
    fun `une phase sans travail n'annonce aucun avancement`() {
        for (phase in listOf(ArchivePhase.IDLE, ArchivePhase.READY, ArchivePhase.PAUSED, ArchivePhase.ERROR)) {
            assertNull(QuranDownloadText.progressLabel(phase, 0.42f), "phase $phase")
        }
    }

    @Test
    fun `la pause n'est proposee que pendant le transfert`() {
        for (phase in phases) {
            assertEquals(
                phase == ArchivePhase.DOWNLOADING,
                QuranDownloadText.showsPause(phase),
                "phase $phase : mettre en pause pendant l'écriture interromprait un fichier",
            )
        }
    }

    @Test
    fun `le bouton d'action n'apparait que lorsqu'aucun travail n'est en cours`() {
        for (phase in phases) {
            val travail = phase == ArchivePhase.DOWNLOADING || phase == ArchivePhase.EXTRACTING
            assertEquals(!travail, QuranDownloadText.showsAction(phase), "phase $phase")
        }
    }

    @Test
    fun `le bouton d'action propose de commencer ou de reprendre`() {
        assertEquals(QuranDownloadText.START, QuranDownloadText.actionLabel(ArchivePhase.IDLE))
        for (phase in listOf(ArchivePhase.PAUSED, ArchivePhase.ERROR, ArchivePhase.READY)) {
            assertEquals(QuranDownloadText.RESUME, QuranDownloadText.actionLabel(phase), "phase $phase")
        }
    }

    @Test
    fun `jamais deux boutons a la fois`() {
        for (phase in phases) {
            assertFalse(
                QuranDownloadText.showsPause(phase) && QuranDownloadText.showsAction(phase),
                "phase $phase : deux boutons concurrents donneraient deux chemins d'exécution",
            )
        }
    }

    @Test
    fun `l'ecriture des pages ne propose aucun bouton`() {
        // Ni pause — on n'interrompt pas l'écriture de 9 060 fichiers au milieu d'un fichier —
        // ni action : le travail est déjà en cours, et un bouton « Télécharger » pendant
        // l'installation ferait croire que rien ne se passe. C'est le comportement du client
        // d'origine : pendant cette phase, la barre de progression parle seule.
        assertFalse(QuranDownloadText.showsPause(ArchivePhase.EXTRACTING))
        assertFalse(QuranDownloadText.showsAction(ArchivePhase.EXTRACTING))
    }

    @Test
    fun `le selecteur propose trois choix simples, et pas le paquet`() {
        // « Coran 1441 » n'est **pas** dans cette liste : sélectionné sans son paquet, il
        // déplie un panneau de téléchargement au lieu de changer la source. Le mettre ici
        // donnerait un choix qui change la source vers une page sans images.
        val choix = QuranDownloadText.SIMPLE_PRESENTATIONS

        assertEquals(
            listOf(
                MushafSource.MEDINA to "Coran de Médine",
                MushafSource.CORAN_TEST to "Coran avec règles de Tajwid",
                MushafSource.SIMPLIFIED to "Lecture simplifiée",
            ),
            choix,
        )
        for ((source, _) in choix) {
            assertFalse(QuranSourceReady.isZipSource(source), "source $source : un paquet n'est pas un choix simple")
        }
    }

    @Test
    fun `le sous-titre du choix annonce le poids et le hors connexion`() {
        assertEquals(
            "Pages originales · téléchargement à la demande · lecture hors connexion",
            QuranDownloadText.CHOICE_SUBTITLE,
        )
        assertEquals("Affichage du Coran", QuranDownloadText.PICKER_TITLE)
    }
}
