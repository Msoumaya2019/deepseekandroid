package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.MushafSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Éprouve la décision prise **avant** d'afficher une page.
 *
 * C'est la règle qui empêche le défaut le plus difficile à voir de cette application : une page
 * blanche ne se distingue pas d'une page en cours de chargement. Si la décision se trompe, rien
 * ne se plaint — l'écran reste vide, ou pire, il affiche la mauvaise source.
 *
 * Deux propriétés sont tenues ici, et chacune répond à un cas réel :
 *
 *  1. **une page hors bornes est refusée avant que la source soit regardée.** Sans cet ordre, une
 *     page 605 d'une source téléchargée déclencherait 102 Mo de téléchargement pour finir sur une
 *     erreur de fichier — l'utilisateur paierait le téléchargement pour rien ;
 *  2. **le manque de paquet passe avant le manque de lignes.** Sans cet ordre, « les images de
 *     cette page sont manquantes » serait vrai de **toutes** les pages et enverrait chercher un
 *     défaut de fichiers là où il n'y a qu'un téléchargement à faire.
 */
class QuranSourceReadyTest {

    private val toutes = MushafSource.entries.toList()

    // ------------------------------------------------------------- les bornes

    @Test
    fun `une page hors bornes est refusee quel que soit l'etat`() {
        for (page in listOf(-1, 0, 605, 1000, Int.MAX_VALUE)) {
            for (source in toutes) {
                for (installe in listOf(false, true)) {
                    assertEquals(
                        PageReadiness.Refused(QuranSourceReady.INVALID_PAGE),
                        QuranSourceReady.readiness(source, page, downloaded = installe),
                        "page $page, source $source, installé=$installe",
                    )
                }
            }
        }
    }

    @Test
    fun `une page hors bornes ne declenche pas de telechargement`() {
        // L'ordre des cas est la règle : refuser la page d'abord, regarder la source ensuite.
        // Sinon, une page 605 demanderait 102 Mo avant d'échouer.
        val decision = QuranSourceReady.readiness(
            source = MushafSource.CORAN_1441,
            page = 605,
            downloaded = false,
        )
        assertEquals(PageReadiness.Refused(QuranSourceReady.INVALID_PAGE), decision)
    }

    @Test
    fun `les deux pages extremes sont valides`() {
        assertEquals(
            PageReadiness.Ready,
            QuranSourceReady.readiness(MushafSource.MEDINA, 1),
        )
        assertEquals(
            PageReadiness.Ready,
            QuranSourceReady.readiness(MushafSource.MEDINA, 604),
        )
    }

    // ------------------------------------------------- le paquet téléchargé

    @Test
    fun `une source non installee demande le telechargement`() {
        assertEquals(
            PageReadiness.DownloadNeeded,
            QuranSourceReady.readiness(MushafSource.CORAN_1441, 12, downloaded = false),
        )
    }

    @Test
    fun `le manque de paquet passe avant le manque de lignes`() {
        // Sans paquet, toutes les lignes manquent : dire « les images de cette page sont
        // manquantes » serait vrai et pourtant trompeur — il n'y a qu'un téléchargement à faire.
        val decision = QuranSourceReady.readiness(
            source = MushafSource.CORAN_1441,
            page = 12,
            downloaded = false,
            missingLines = (1..15).toList(),
        )
        assertEquals(PageReadiness.DownloadNeeded, decision)
    }

    @Test
    fun `une ligne manquante est refusee avec le message d'origine`() {
        val decision = QuranSourceReady.readiness(
            source = MushafSource.CORAN_1441,
            page = 12,
            downloaded = true,
            missingLines = listOf(7),
        )
        assertEquals(PageReadiness.Refused(QuranSourceReady.MISSING_PAGE_IMAGES), decision)
    }

    @Test
    fun `une source installee et complete est prete`() {
        val decision = QuranSourceReady.readiness(
            source = MushafSource.CORAN_1441,
            page = 12,
            downloaded = true,
            missingLines = emptyList(),
        )
        assertEquals(PageReadiness.Ready, decision)
    }

    // ------------------------------------------------------- les autres sources

    @Test
    fun `le rendu par police n'a pas d'image a preparer`() {
        // Le même écran doit savoir peindre du texte coranique : il n'y a aucun fichier à
        // charger, et une image « indisponible » n'est pas un refus.
        for (source in listOf(MushafSource.CORAN_TEST, MushafSource.SIMPLIFIED)) {
            assertEquals(
                PageReadiness.FontRendered,
                QuranSourceReady.readiness(source, 12, pageImageAvailable = false),
                "source $source",
            )
        }
    }

    @Test
    fun `une image embarquee absente est refusee`() {
        for (source in listOf(MushafSource.MEDINA, MushafSource.TAJWEED_PAGES)) {
            assertEquals(
                PageReadiness.Refused(QuranSourceReady.PAGE_UNAVAILABLE),
                QuranSourceReady.readiness(source, 12, pageImageAvailable = false),
                "source $source",
            )
        }
    }

    @Test
    fun `les sources heritees se comportent comme une source par images`() {
        for (source in toutes.filter { it.isLegacy }) {
            assertEquals(
                PageReadiness.Ready,
                QuranSourceReady.readiness(source, 12, pageImageAvailable = true),
                "source $source",
            )
            assertEquals(
                PageReadiness.Refused(QuranSourceReady.PAGE_UNAVAILABLE),
                QuranSourceReady.readiness(source, 12, pageImageAvailable = false),
                "source $source",
            )
        }
    }

    // ------------------------------------------------------------- les prédicats

    @Test
    fun `une seule source est un paquet telecharge`() {
        // Par élément, et non par comptage : un garde-fou qui compte ne voit pas la source
        // qu'on aurait oublié d'y mettre.
        for (source in toutes) {
            assertEquals(
                source == MushafSource.CORAN_1441,
                QuranSourceReady.isZipSource(source),
                "source $source",
            )
        }
        // Témoin de relecture : ajouter une source doit faire échouer ce test, pour forcer à
        // relire la règle plutôt qu'à la laisser s'appliquer par défaut.
        assertEquals(8, toutes.size, "l'énumération des sources a changé : relire cette règle")
    }

    @Test
    fun `le telechargement n'est demande que pour une source non installee`() {
        for (source in toutes) {
            val attendu = source == MushafSource.CORAN_1441
            assertEquals(attendu, QuranSourceReady.needsDownload(source, downloaded = false), "$source")
            // Installée, plus aucune source ne demande de téléchargement — y compris celle qui
            // en demande un quand elle manque.
            assertFalse(QuranSourceReady.needsDownload(source, downloaded = true), "$source")
        }
    }

    @Test
    fun `les messages sont ceux du client d'origine`() {
        // Mot pour mot : les deux clients doivent dire la même chose à la même personne.
        assertEquals("Page du Coran invalide.", QuranSourceReady.INVALID_PAGE)
        assertEquals("Les images de cette page sont manquantes.", QuranSourceReady.MISSING_PAGE_IMAGES)
        assertEquals("Page du Coran indisponible.", QuranSourceReady.PAGE_UNAVAILABLE)
    }

    @Test
    fun `une phase de travail en cours est reconnue comme telle`() {
        assertTrue(ArchiveProgress(ArchivePhase.DOWNLOADING, 0.5f).isBusy)
        assertTrue(ArchiveProgress(ArchivePhase.EXTRACTING, 0.5f).isBusy)
        for (phase in listOf(
            ArchivePhase.IDLE,
            ArchivePhase.READY,
            ArchivePhase.PAUSED,
            ArchivePhase.ERROR,
        )) {
            assertFalse(ArchiveProgress(phase, 0f).isBusy, "phase $phase")
        }
    }

    @Test
    fun `deux disponibilites sur quatre empechent d'adopter une source`() {
        // C'est la règle qu'applique un changement de présentation : il refuse ce qui
        // afficherait une page incomplète, et il dit pourquoi. Un `else` aurait laissé passer
        // une cinquième disponibilité sans que personne ne s'en aperçoive.
        assertNull(QuranSourceReady.refusalMessage(PageReadiness.Ready))
        assertNull(QuranSourceReady.refusalMessage(PageReadiness.FontRendered))
        assertEquals(
            QuranSourceReady.MISSING_PAGE_IMAGES,
            QuranSourceReady.refusalMessage(PageReadiness.Refused(QuranSourceReady.MISSING_PAGE_IMAGES)),
        )
        assertEquals(
            QuranSourceReady.PAGE_UNAVAILABLE,
            QuranSourceReady.refusalMessage(PageReadiness.Refused(QuranSourceReady.PAGE_UNAVAILABLE)),
        )
        assertEquals(
            QuranDownloadText.NOT_INSTALLED,
            QuranSourceReady.refusalMessage(PageReadiness.DownloadNeeded),
        )
    }

    @Test
    fun `un refus explique dit toujours quelque chose`() {
        // Un message vide serait un refus muet : l'écran se fermerait sans raison visible, ce
        // qui est pire qu'un refus explicite.
        for (message in listOf(
            QuranSourceReady.INVALID_PAGE,
            QuranSourceReady.MISSING_PAGE_IMAGES,
            QuranSourceReady.PAGE_UNAVAILABLE,
            QuranDownloadText.NOT_INSTALLED,
        )) {
            assertTrue(message.isNotBlank(), "message vide")
        }
    }
}
