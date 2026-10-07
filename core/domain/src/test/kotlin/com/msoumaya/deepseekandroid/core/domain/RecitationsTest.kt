package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.RecitationKind
import com.msoumaya.deepseekandroid.core.model.RecitationSyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Les règles de la récitation.
 *
 * ## Ce que ces tests couvrent, et ce qu'ils ne peuvent pas couvrir
 *
 * Tout ce qui est éprouvé ici est **une décision** : quelles bornes désignent un passage réel,
 * quelle extension déduire d'une adresse, quelles bornes écrire pour une invocation, ce qui
 * empêche d'enregistrer, et ce qui empêche de corriger. Ces décisions se prouvent sans appareil.
 *
 * Ce qui n'est **pas** ici, et ne peut pas y être : que le microphone enregistre, que le fichier
 * soit copié au bon endroit, que le dépôt aboutisse, qu'une adresse signée ouvre l'audio. Ces
 * choses-là se prouvent sur un appareil, et elles sont dites comme telles dans `README.md`
 * plutôt que supposées vertes.
 */
class RecitationsTest {

    // --- validRange --------------------------------------------------------

    @Test
    fun `un passage reel du Coran est accepte`() {
        assertTrue(Recitations.validRange(1, 1))
        assertTrue(Recitations.validRange(2, 5))
        assertTrue(Recitations.validRange(1, Recitations.VERSE_COUNT))
    }

    @Test
    fun `le premier verset est valide et zero ne l'est pas`() {
        assertTrue(Recitations.validRange(1, 10))
        assertFalse(Recitations.validRange(0, 10))
    }

    @Test
    fun `le dernier verset est valide et le suivant ne l'est pas`() {
        assertTrue(Recitations.validRange(Recitations.VERSE_COUNT, Recitations.VERSE_COUNT))
        assertFalse(Recitations.validRange(1, Recitations.VERSE_COUNT + 1))
    }

    @Test
    fun `un intervalle inverse est refuse`() {
        // Les deux bornes sont valides prises separement : c'est bien l'ordre qui est refuse.
        assertTrue(Recitations.validRange(2, 2))
        assertTrue(Recitations.validRange(5, 5))
        assertFalse(Recitations.validRange(5, 2))
    }

    @Test
    fun `un seul verset est un passage`() {
        assertTrue(Recitations.validRange(100, 100))
    }

    // --- extensionFor et contentType ---------------------------------------

    @Test
    fun `une source en trois-gp donne l'extension trois-gp`() {
        assertEquals(Recitations.EXTENSION_3GP, Recitations.extensionFor("/tmp/note.3gp"))
    }

    @Test
    fun `une source en m-quatre donne l'extension m-quatre`() {
        assertEquals(Recitations.EXTENSION_MP4, Recitations.extensionFor("/tmp/note.m4a"))
    }

    @Test
    fun `une source sans extension connue donne m-quatre`() {
        // Le client d'origine ne connait que `.3gp` ; tout le reste est du m4a, y compris ce qui
        // ne ressemble a rien. C'est le comportement par defaut, et il est explicite ici.
        assertEquals(Recitations.EXTENSION_MP4, Recitations.extensionFor("/tmp/note.wav"))
    }

    @Test
    fun `la casse de l'extension ne change rien`() {
        // Une adresse peut porter `.3GP` : la comparaison ignore la casse.
        assertEquals(Recitations.EXTENSION_3GP, Recitations.extensionFor("/tmp/NOTE.3GP"))
    }

    @Test
    fun `le type de contenu suit l'extension`() {
        assertEquals("audio/3gpp", Recitations.contentType(Recitations.EXTENSION_3GP))
        assertEquals("audio/mp4", Recitations.contentType(Recitations.EXTENSION_MP4))
    }

    // --- storagePath -------------------------------------------------------

    @Test
    fun `le chemin range le fichier sous son proprietaire`() {
        // Le premier segment est l'identifiant du proprietaire : c'est lui qui porte les
        // politiques d'acces du compartiment.
        assertEquals("u-1/r-9.m4a", Recitations.storagePath("u-1", "r-9", Recitations.EXTENSION_MP4))
        assertEquals("u-1/r-9.3gp", Recitations.storagePath("u-1", "r-9", Recitations.EXTENSION_3GP))
    }

    // --- remoteBounds ------------------------------------------------------

    @Test
    fun `un passage du Coran ecrit ses bornes`() {
        assertEquals(3 to 7, Recitations.remoteBounds(RecitationKind.QURAN, 3, 7))
    }

    @Test
    fun `une invocation n'ecrit pas de bornes`() {
        // Ni zero, ni un intervalle vide : `null`. Une invocation n'est pas un passage du Coran,
        // et ecrire un intervalle qui ne veut rien dire rendrait les corrections possibles.
        assertEquals(null to null, Recitations.remoteBounds(RecitationKind.INVOCATION, 3, 7))
    }

    // --- saveProblem -------------------------------------------------------

    @Test
    fun `un enregistrement complet ne pose aucun probleme`() {
        assertNull(
            Recitations.saveProblem(
                kind = RecitationKind.QURAN,
                start = 2,
                end = 5,
                userId = "u-1",
                sourceUri = "/tmp/note.m4a",
                durationMs = 1_000L,
            )
        )
    }

    @Test
    fun `un passage hors du Coran est refuse`() {
        assertEquals(
            RecitationSaveProblem.RANGE_INVALID,
            Recitations.saveProblem(RecitationKind.QURAN, 0, 5, "u-1", "/tmp/a.m4a", 1_000L),
        )
    }

    @Test
    fun `une invocation est acceptee sans bornes de versets`() {
        // C'est la seule branche ou les bornes ne sont pas exigees : `!invocation && !validRange`
        // dans le client d'origine. Une invocation enregistree porte (0, 0) et passe.
        assertNull(
            Recitations.saveProblem(RecitationKind.INVOCATION, 0, 0, "u-1", "/tmp/a.m4a", 1_000L)
        )
    }

    @Test
    fun `sans proprietaire rien ne s'enregistre`() {
        assertEquals(
            RecitationSaveProblem.OWNER_MISSING,
            Recitations.saveProblem(RecitationKind.QURAN, 1, 5, "", "/tmp/a.m4a", 1_000L),
        )
    }

    @Test
    fun `sans source rien ne s'enregistre`() {
        assertEquals(
            RecitationSaveProblem.SOURCE_MISSING,
            Recitations.saveProblem(RecitationKind.QURAN, 1, 5, "u-1", "", 1_000L),
        )
    }

    @Test
    fun `une duree nulle ou negative est refusee`() {
        assertEquals(
            RecitationSaveProblem.DURATION_NOT_POSITIVE,
            Recitations.saveProblem(RecitationKind.QURAN, 1, 5, "u-1", "/tmp/a.m4a", 0L),
        )
        assertEquals(
            RecitationSaveProblem.DURATION_NOT_POSITIVE,
            Recitations.saveProblem(RecitationKind.QURAN, 1, 5, "u-1", "/tmp/a.m4a", -1L),
        )
    }

    @Test
    fun `l'ordre des controles est celui du client d'origine`() {
        // Les trois manques sont presents a la fois. C'est `RANGE_INVALID` qui doit sortir, car
        // le client d'origine teste les bornes en premier — changer cet ordre changerait le
        // message montre a la personne, et pas seulement l'ordre des controles.
        assertEquals(
            RecitationSaveProblem.RANGE_INVALID,
            Recitations.saveProblem(RecitationKind.QURAN, 99, 5, "", "", 0L),
        )
    }

    // --- correctionProblem -------------------------------------------------

    @Test
    fun `aucun verset selectionne est refuse`() {
        assertEquals(
            CorrectionProblem.NOTHING_SELECTED,
            Recitations.correctionProblem(1, 5, emptyList()) { true },
        )
    }

    @Test
    fun `une invocation ne porte pas de versets a corriger`() {
        assertEquals(
            CorrectionProblem.NO_VERSE_RANGE,
            Recitations.correctionProblem(null, null, listOf(3)) { true },
        )
    }

    @Test
    fun `une borne manquante suffit a refuser`() {
        // Le client d'origine refuse des que l'une **ou** l'autre borne manque.
        assertEquals(
            CorrectionProblem.NO_VERSE_RANGE,
            Recitations.correctionProblem(1, null, listOf(3)) { true },
        )
        assertEquals(
            CorrectionProblem.NO_VERSE_RANGE,
            Recitations.correctionProblem(null, 5, listOf(3)) { true },
        )
    }

    @Test
    fun `un verset hors des bornes de la recitation est refuse`() {
        assertEquals(
            CorrectionProblem.VERSE_OUTSIDE,
            Recitations.correctionProblem(10, 20, listOf(9)) { true },
        )
        assertEquals(
            CorrectionProblem.VERSE_OUTSIDE,
            Recitations.correctionProblem(10, 20, listOf(21)) { true },
        )
    }

    @Test
    fun `un verset inconnu du referentiel est refuse`() {
        assertEquals(
            CorrectionProblem.VERSE_UNKNOWN,
            Recitations.correctionProblem(10, 20, listOf(15)) { false },
        )
    }

    @Test
    fun `des versets dans les bornes et connus sont acceptes`() {
        assertNull(Recitations.correctionProblem(10, 20, listOf(10, 15, 20)) { true })
    }

    @Test
    fun `les bornes elles-memes sont corrigibles`() {
        // Le client d'origine compare `verseId < start || verseId > end` : les deux bornes sont
        // donc **incluses**, et une correction sur le premier ou le dernier verset passe.
        assertNull(Recitations.correctionProblem(10, 20, listOf(10)) { true })
        assertNull(Recitations.correctionProblem(10, 20, listOf(20)) { true })
    }

    // --- feedbackEmpty -----------------------------------------------------

    @Test
    fun `un retour sans rien ne dit rien`() {
        assertTrue(Recitations.feedbackEmpty("", null))
    }

    @Test
    fun `un commentaire fait d'espaces ne dit rien`() {
        assertTrue(Recitations.feedbackEmpty("   ", null))
    }

    @Test
    fun `un commentaire suffit a un retour`() {
        assertFalse(Recitations.feedbackEmpty("Reprends le madd", null))
    }

    @Test
    fun `une correction vocale suffit a un retour`() {
        assertFalse(Recitations.feedbackEmpty("", "u-1/voix.m4a"))
    }

    @Test
    fun `une adresse faite d'espaces compte comme presente`() {
        // Fidelite assumee : le client d'origine ecrit `!comment.trim() && !voicePath`, donc il
        // rogne le commentaire mais **pas** l'adresse. Une adresse d'espaces y passe. La refuser
        // ici ferait diverger les deux clients sur ce qui est acceptable, ce qui est pire qu'une
        // bizarrerie partagee.
        assertFalse(Recitations.feedbackEmpty("", "   "))
    }

    // --- mayDelete ---------------------------------------------------------

    @Test
    fun `seul le proprietaire peut supprimer`() {
        assertTrue(Recitations.mayDelete("u-1", "u-1"))
    }

    @Test
    fun `un autre compte ne peut pas supprimer`() {
        assertFalse(Recitations.mayDelete("u-1", "u-2"))
    }

    // --- RecitationSyncStatus.awaitsUpload ---------------------------------

    @Test
    fun `une ligne deja deposee ne repart pas`() {
        assertFalse(RecitationSyncStatus.SYNCED.awaitsUpload)
    }

    @Test
    fun `une ligne en cours de depot repart quand meme`() {
        // C'est le point qui evite un blocage definitif : une application tuee en pleine
        // transmission laisse la ligne en `UPLOADING`, et la considerer comme « en cours »
        // ferait qu'elle ne repartirait jamais.
        assertTrue(RecitationSyncStatus.UPLOADING.awaitsUpload)
        assertTrue(RecitationSyncStatus.PENDING.awaitsUpload)
        assertTrue(RecitationSyncStatus.FAILED.awaitsUpload)
    }

    @Test
    fun `seule la ligne deposee est exclue du depot`() {
        val aDeposer = RecitationSyncStatus.entries.filter { it.awaitsUpload }
        assertEquals(3, aDeposer.size)
        assertFalse(aDeposer.contains(RecitationSyncStatus.SYNCED))
    }
}
