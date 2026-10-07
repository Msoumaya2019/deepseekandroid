package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.LocalRecitation
import com.msoumaya.deepseekandroid.core.model.RecitationKind
import com.msoumaya.deepseekandroid.core.model.RecitationSyncStatus
import com.msoumaya.deepseekandroid.core.model.RemoteRecitation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Les regles de l'ecran « Mes recitations ».
 *
 * Porte depuis `src/RecitationsScreen.tsx`. Ce banc tourne sans appareil : il n'y a ici ni
 * lecteur audio, ni compartiment de stockage, ni Compose.
 *
 * Deux decisions de ce fichier meritent d'etre cherchées des yeux, parce qu'elles sont invisibles
 * a la lecture rapide et qu'un cas de falsification les a visees :
 *
 *  * **les minutes du temps ecoule ne sont pas remplies** — `1:00`, et non `01:00` —, alors que
 *    celles de l'enregistreur le sont ([RecitationRecorder.clock]) : deux formateurs coexistent
 *    dans le client d'origine, et le banc fixe les deux ;
 *  * **une correction l'emporte sur une ecoute** pour un passage du Coran, mais **pas** pour une
 *    invocation, qui n'a pas de versets a corriger.
 */
class RecitationsListTest {

    private fun locale(
        id: String,
        createdAt: String = "2026-10-07T10:00:00.000Z",
        kind: RecitationKind = RecitationKind.QURAN,
        start: Int = 1,
        end: Int = 7,
        durationMs: Long = 5_000L,
        syncStatus: RecitationSyncStatus = RecitationSyncStatus.PENDING,
        invocationId: String? = null,
    ) = LocalRecitation(
        id = id,
        userId = "moi",
        start = start,
        end = end,
        durationMs = durationMs,
        uri = "file:///cache/$id.m4a",
        createdAt = createdAt,
        syncStatus = syncStatus,
        kind = kind,
        invocationId = invocationId,
    )

    private fun distante(
        id: String,
        createdAt: String = "2026-10-07T10:00:00.000Z",
        kind: RecitationKind = RecitationKind.QURAN,
        start: Int? = 1,
        end: Int? = 7,
        durationMs: Long = 5_000L,
        storagePath: String = "moi/$id.m4a",
        listenedAt: String? = null,
        invocationId: String? = null,
    ) = RemoteRecitation(
        id = id,
        userId = "moi",
        startVerseId = start,
        endVerseId = end,
        durationMs = durationMs,
        storagePath = storagePath,
        createdAt = createdAt,
        listenedAt = listenedAt,
        kind = kind,
        invocationId = invocationId,
    )

    /** Une reference de passage, sans charger le Coran : la regle la recoit en parametre. */
    private val reference: (Int, Int) -> String = { start, end -> "reference $start-$end" }

    // -----------------------------------------------------------------------
    // La fusion
    // -----------------------------------------------------------------------

    @Test
    fun `le distant l'emporte sur le local qui double la meme recitation`() {
        val local = locale("r1", syncStatus = RecitationSyncStatus.SYNCED)
        val distant = distante("r1", listenedAt = "2026-10-07T11:00:00.000Z")

        val lignes = RecitationsList.merge(listOf(local), listOf(distant))

        assertEquals(1, lignes.size, "une seule ligne : les deux decrivent la meme recitation")
        assertEquals("2026-10-07T11:00:00.000Z", lignes.single().listenedAt)
    }

    @Test
    fun `une recitation seulement locale est ajoutee avec un chemin de stockage vide`() {
        val lignes = RecitationsList.merge(listOf(locale("r1")), emptyList())

        assertEquals(1, lignes.size)
        assertEquals("", lignes.single().storagePath)
    }

    @Test
    fun `une recitation seulement locale n'annonce aucune ecoute`() {
        // Un relecteur ne peut pas avoir ecoute un fichier qui ne lui est pas encore parvenu.
        val lignes = RecitationsList.merge(listOf(locale("r1")), emptyList())

        assertNull(lignes.single().listenedAt)
    }

    @Test
    fun `les lignes se rangent de la plus recente a la plus ancienne`() {
        val ancienne = distante("a", createdAt = "2026-10-01T08:00:00.000Z")
        val recente = distante("b", createdAt = "2026-10-07T08:00:00.000Z")
        val milieu = distante("c", createdAt = "2026-10-04T08:00:00.000Z")

        val lignes = RecitationsList.merge(emptyList(), listOf(ancienne, recente, milieu))

        assertEquals(listOf("b", "c", "a"), lignes.map { it.id })
    }

    @Test
    fun `a horodatage egal la ligne distante passe avant la ligne locale`() {
        val instant = "2026-10-07T10:00:00.000Z"
        val locale = locale("locale", createdAt = instant)
        val distant = distante("distante", createdAt = instant)

        val lignes = RecitationsList.merge(listOf(locale), listOf(distant))

        assertEquals(listOf("distante", "locale"), lignes.map { it.id })
    }

    @Test
    fun `fusionner deux listes vides rend une liste vide`() {
        assertTrue(RecitationsList.merge(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `deux recitations locales distinctes sont toutes deux gardees`() {
        val lignes = RecitationsList.merge(listOf(locale("a"), locale("b")), emptyList())

        assertEquals(2, lignes.size)
    }

    @Test
    fun `une recitation locale se presente avec la nature et les bornes de sa copie`() {
        val local = locale("r1", kind = RecitationKind.INVOCATION, start = 0, end = 0, invocationId = "inv-1")

        val ligne = RecitationsList.merge(listOf(local), emptyList()).single()

        assertEquals(RecitationKind.INVOCATION, ligne.kind)
        assertEquals(0, ligne.startVerseId)
        assertEquals(0, ligne.endVerseId)
        assertEquals("inv-1", ligne.invocationId)
    }

    // -----------------------------------------------------------------------
    // Le filtre
    // -----------------------------------------------------------------------

    @Test
    fun `le filtre toutes accepte les deux natures`() {
        assertTrue(RecitationsList.matches(RecitationsList.Filter.ALL, RecitationKind.QURAN))
        assertTrue(RecitationsList.matches(RecitationsList.Filter.ALL, RecitationKind.INVOCATION))
    }

    @Test
    fun `le filtre Coran refuse une invocation`() {
        assertTrue(!RecitationsList.matches(RecitationsList.Filter.QURAN, RecitationKind.INVOCATION))
    }

    @Test
    fun `le filtre Invocations refuse un passage du Coran`() {
        assertTrue(!RecitationsList.matches(RecitationsList.Filter.INVOCATION, RecitationKind.QURAN))
    }

    @Test
    fun `une nature absente compte comme un passage du Coran`() {
        // L'original ecrit `item.recording_type ?? 'quran'` : une ligne ecrite avant que la
        // colonne existe n'est pas une invocation, et la ranger parmi elles la ferait disparaitre
        // du filtre « Coran ».
        assertTrue(RecitationsList.matches(RecitationsList.Filter.QURAN, null))
        assertTrue(!RecitationsList.matches(RecitationsList.Filter.INVOCATION, null))
    }

    @Test
    fun `le filtre garde l'ordre des lignes`() {
        val recente = distante("b", createdAt = "2026-10-07T08:00:00.000Z")
        val ancienne = distante("a", createdAt = "2026-10-01T08:00:00.000Z")

        val retenues = RecitationsList.filtered(listOf(recente, ancienne), RecitationsList.Filter.ALL)

        assertEquals(listOf("b", "a"), retenues.map { it.id })
    }

    @Test
    fun `le filtre d'une liste vide rend une liste vide`() {
        assertTrue(RecitationsList.filtered(emptyList(), RecitationsList.Filter.QURAN).isEmpty())
    }

    // -----------------------------------------------------------------------
    // L'etiquette de depot
    // -----------------------------------------------------------------------

    @Test
    fun `une recitation sans copie locale se lit Synchronise`() {
        assertEquals(RecitationText.SYNC_SYNCED, RecitationsList.syncLabel(null))
    }

    @Test
    fun `une recitation deposee se lit Synchronise`() {
        assertEquals(RecitationText.SYNC_SYNCED, RecitationsList.syncLabel(RecitationSyncStatus.SYNCED))
    }

    @Test
    fun `un depot en cours se lit En cours d'envoi`() {
        assertEquals(RecitationText.SYNC_UPLOADING, RecitationsList.syncLabel(RecitationSyncStatus.UPLOADING))
    }

    @Test
    fun `un depot echoue se lit Echec de synchronisation`() {
        assertEquals(RecitationText.SYNC_FAILED, RecitationsList.syncLabel(RecitationSyncStatus.FAILED))
    }

    @Test
    fun `un depot jamais tente se lit En attente`() {
        assertEquals(RecitationText.SYNC_PENDING, RecitationsList.syncLabel(RecitationSyncStatus.PENDING))
    }

    @Test
    fun `les cinq etats de depot rendent des mots distincts`() {
        val mots = listOf(
            RecitationsList.syncLabel(null),
            RecitationsList.syncLabel(RecitationSyncStatus.SYNCED),
            RecitationsList.syncLabel(RecitationSyncStatus.UPLOADING),
            RecitationsList.syncLabel(RecitationSyncStatus.FAILED),
            RecitationsList.syncLabel(RecitationSyncStatus.PENDING),
        )

        // Sans copie locale et « depose » disent la meme chose : quatre mots, pas cinq.
        assertEquals(4, mots.toSet().size, "les cinq etats se lisent avec quatre mots : $mots")
    }

    // -----------------------------------------------------------------------
    // L'etat d'une recitation ouverte
    // -----------------------------------------------------------------------

    @Test
    fun `une invocation ecoutee se lit Ecoutee`() {
        assertEquals(
            RecitationText.STATUS_LISTENED,
            RecitationsList.statusLabel(RecitationKind.INVOCATION, listened = true, correctionCount = 0, feedbackCount = 0),
        )
    }

    @Test
    fun `une invocation jamais ecoutee se lit A ecouter`() {
        assertEquals(
            RecitationText.STATUS_TO_LISTEN,
            RecitationsList.statusLabel(RecitationKind.INVOCATION, listened = false, correctionCount = 0, feedbackCount = 0),
        )
    }

    @Test
    fun `une invocation corrigee ne se lit pas Corrigee`() {
        // Une invocation n'a pas de versets : la corriger n'a pas de sens, et l'original ne le
        // propose pas. Une correction rangee sous une invocation ne doit donc rien changer.
        assertEquals(
            RecitationText.STATUS_TO_LISTEN,
            RecitationsList.statusLabel(RecitationKind.INVOCATION, listened = false, correctionCount = 3, feedbackCount = 2),
        )
    }

    @Test
    fun `un passage corrige se lit Corrigee`() {
        assertEquals(
            RecitationText.STATUS_CORRECTED,
            RecitationsList.statusLabel(RecitationKind.QURAN, listened = false, correctionCount = 1, feedbackCount = 0),
        )
    }

    @Test
    fun `un retour general seul suffit a lire Corrigee`() {
        assertEquals(
            RecitationText.STATUS_CORRECTED,
            RecitationsList.statusLabel(RecitationKind.QURAN, listened = false, correctionCount = 0, feedbackCount = 1),
        )
    }

    @Test
    fun `un passage ecoute sans correction se lit Ecoutee`() {
        assertEquals(
            RecitationText.STATUS_LISTENED,
            RecitationsList.statusLabel(RecitationKind.QURAN, listened = true, correctionCount = 0, feedbackCount = 0),
        )
    }

    @Test
    fun `un passage ni ecoute ni corrige se lit En attente de correction`() {
        assertEquals(
            RecitationText.STATUS_AWAITING_CORRECTION,
            RecitationsList.statusLabel(RecitationKind.QURAN, listened = false, correctionCount = 0, feedbackCount = 0),
        )
    }

    @Test
    fun `la correction l'emporte sur l'ecoute pour un passage`() {
        assertEquals(
            RecitationText.STATUS_CORRECTED,
            RecitationsList.statusLabel(RecitationKind.QURAN, listened = true, correctionCount = 1, feedbackCount = 0),
        )
    }

    @Test
    fun `les trois etats d'un passage se lisent avec trois mots distincts`() {
        val mots = setOf(
            RecitationsList.statusLabel(RecitationKind.QURAN, false, 0, 0),
            RecitationsList.statusLabel(RecitationKind.QURAN, true, 0, 0),
            RecitationsList.statusLabel(RecitationKind.QURAN, false, 1, 0),
        )

        assertEquals(3, mots.size, "les trois etats doivent se distinguer : $mots")
    }

    // -----------------------------------------------------------------------
    // Le titre
    // -----------------------------------------------------------------------

    @Test
    fun `un passage se titre avec le prefixe CORAN et sa reference`() {
        assertEquals(
            "CORAN · reference 2-9",
            RecitationsList.title(RecitationKind.QURAN, 2, 9, invocationTitle = null, reference = reference),
        )
    }

    @Test
    fun `une invocation se titre avec le prefixe INVOCATION et son titre`() {
        assertEquals(
            "INVOCATION · Invocation du matin",
            RecitationsList.title(RecitationKind.INVOCATION, 0, 0, "Invocation du matin", reference),
        )
    }

    @Test
    fun `une invocation sans titre se replie sur Ma prononciation`() {
        assertEquals(
            "INVOCATION · ${RecitationText.LIST_INVOCATION_FALLBACK}",
            RecitationsList.title(RecitationKind.INVOCATION, 0, 0, null, reference),
        )
    }

    @Test
    fun `un titre vide reste vide, il ne se replie pas`() {
        // Le repli porte sur `null` seulement, comme le `??` de l'original : une chaine vide est
        // un choix du serveur, et le portage ne le corrige pas.
        assertEquals(
            "INVOCATION · ",
            RecitationsList.title(RecitationKind.INVOCATION, 0, 0, "", reference),
        )
    }

    @Test
    fun `le repli de la liste differe de celui de l'enregistreur`() {
        // Deux ecrans, deux replis : c'est l'original, et les confondre effacerait l'ecart.
        assertNotEquals(RecitationText.INVOCATION_FALLBACK, RecitationText.LIST_INVOCATION_FALLBACK)

        // Comparer les deux CONSTANTES ne dit rien de ce que la liste **emploie** : le repli se
        // prouve sur le titre rendu. Sans cela, un titre qui prendrait le repli de l'enregistreur
        // passerait inapercu — c'est un cas de falsification qui l'a montre, en survivant a une
        // mutation qui faisait exactement cela.
        val titre = RecitationsList.title(RecitationKind.INVOCATION, 0, 0, null, reference)
        assertTrue(
            titre.endsWith(RecitationText.LIST_INVOCATION_FALLBACK),
            "la liste emploie son propre repli : $titre",
        )
        assertTrue(
            !titre.endsWith(RecitationText.INVOCATION_FALLBACK),
            "la liste n'emploie pas celui de l'enregistreur : $titre",
        )
    }

    @Test
    fun `les deux prefixes de nature sont distincts`() {
        assertNotEquals(RecitationText.TITLE_PREFIX_QURAN, RecitationText.TITLE_PREFIX_INVOCATION)
    }

    @Test
    fun `un passage sans bornes se titre sans reference, au lieu de tomber`() {
        // Divergence assumee : l'original affirme que les bornes existent (`start_verse_id!`) et
        // leverait une erreur. Ici la ligne dit ce qu'elle sait, et les autres lignes restent
        // affichables.
        assertEquals(
            "CORAN · ",
            RecitationsList.title(RecitationKind.QURAN, null, null, null, reference),
        )
    }

    @Test
    fun `une invocation ne consulte jamais la reference du Coran`() {
        var appels = 0
        val compte: (Int, Int) -> String = { _, _ -> appels++; "jamais" }

        RecitationsList.title(RecitationKind.INVOCATION, 0, 0, "titre", compte)

        assertEquals(0, appels, "une invocation n'a pas de versets a nommer")
    }

    @Test
    fun `le libelle d'un verset corrige nomme la sourate et le rang du verset`() {
        assertEquals("Al-Baqara · verset 255", RecitationsList.verseLabel("Al-Baqara", 255))
    }

    // -----------------------------------------------------------------------
    // Le temps
    // -----------------------------------------------------------------------

    @Test
    fun `soixante secondes se lisent une minute, sans zero de tete`() {
        assertEquals("1:00", RecitationsList.clock(60_000L))
    }

    @Test
    fun `cinq secondes se lisent avec un zero de tete`() {
        assertEquals("0:05", RecitationsList.clock(5_000L))
    }

    @Test
    fun `une heure ne se replie pas sur zero`() {
        // Les minutes ne sont pas repliees a soixante : un compteur qui repartirait a zero ferait
        // croire a un redemarrage.
        assertEquals("60:00", RecitationsList.clock(3_600_000L))
    }

    @Test
    fun `une duree nulle se lit zero`() {
        assertEquals("0:00", RecitationsList.clock(0L))
    }

    @Test
    fun `les millisecondes sont tronquees et non arrondies`() {
        assertEquals("0:01", RecitationsList.clock(1_999L))
    }

    @Test
    fun `le format de la liste differe de celui de l'enregistreur`() {
        // Le seul ecart est le remplissage des minutes, et c'est l'original :
        // `RecitationsScreen.tsx` ligne 13 n'a pas de `padStart`, `RecitationRecorder.tsx`
        // ligne 15 en a un. Les confondre changerait le texte d'un des deux ecrans.
        assertNotEquals(RecitationRecorder.clock(60_000L), RecitationsList.clock(60_000L))
        assertEquals("01:00", RecitationRecorder.clock(60_000L))
        assertEquals("1:00", RecitationsList.clock(60_000L))
    }

    @Test
    fun `les deux formats s'accordent sur les secondes`() {
        // La divergence porte sur les minutes, et sur elles seules : les secondes sont remplies
        // des deux cotes, sinon le banc fixerait un ecart qui n'existe pas.
        assertEquals("5:07", RecitationsList.clock(307_000L))
        assertEquals("05:07", RecitationRecorder.clock(307_000L))
    }

    // -----------------------------------------------------------------------
    // La barre de progression et le deplacement
    // -----------------------------------------------------------------------

    @Test
    fun `la moitie d'une piste remplit la moitie de la barre`() {
        assertEquals(50f, RecitationsList.progressPercent(5_000L, 10_000L), 0.001f)
    }

    @Test
    fun `le debut remplit zero`() {
        assertEquals(0f, RecitationsList.progressPercent(0L, 10_000L), 0.001f)
    }

    @Test
    fun `une position au-dela de la duree est plafonnee`() {
        assertEquals(100f, RecitationsList.progressPercent(20_000L, 10_000L), 0.001f)
    }

    @Test
    fun `une duree nulle rend une barre pleine, comme la division d'origine`() {
        // L'original divise par la duree, ce qui donne l'infini en JavaScript, que `Math.min`
        // ramene a 100. Reproduire evite un `NaN`, qui ferait disparaitre la barre.
        assertEquals(100f, RecitationsList.progressPercent(0L, 0L), 0.001f)
    }

    @Test
    fun `reculer depuis le debut reste au debut`() {
        assertEquals(0L, RecitationsList.seekBackward(3_000L))
    }

    @Test
    fun `reculer de dix secondes depuis vingt-cinq rend quinze`() {
        assertEquals(15_000L, RecitationsList.seekBackward(25_000L))
    }

    @Test
    fun `avancer de dix secondes depuis vingt-cinq rend trente-cinq`() {
        assertEquals(35_000L, RecitationsList.seekForward(25_000L))
    }

    @Test
    fun `avancer n'est pas plafonne par la fin de la piste`() {
        // C'est l'original (`seekTo(position + 10)`) : c'est au lecteur de decider ce qu'il fait
        // d'une position au-dela de la fin. Plafonner ici inventerait une regle.
        assertEquals(15_000L, RecitationsList.seekForward(5_000L, stepMs = 10_000L))
        assertTrue(RecitationsList.seekForward(95_000L) > 100_000L)
    }

    @Test
    fun `le pas de deplacement est de dix secondes`() {
        assertEquals(10_000L, RecitationsList.SEEK_STEP_MS)
    }
}
