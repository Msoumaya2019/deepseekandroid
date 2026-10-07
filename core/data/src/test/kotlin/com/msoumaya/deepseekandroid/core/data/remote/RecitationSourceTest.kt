package com.msoumaya.deepseekandroid.core.data.remote

import com.msoumaya.deepseekandroid.core.domain.Recitations
import com.msoumaya.deepseekandroid.core.model.AppJson
import com.msoumaya.deepseekandroid.core.model.RecitationKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.decodeFromString

/**
 * La lecture des recitations : ce qui se prouve sans serveur.
 *
 * L'adaptateur lui-meme — `SupabaseRecitationSource` — ne s'eprouve qu'avec un projet : il ne fait
 * que traduire des appels. Ce qui **decide** de quelque chose, et qui se mesure ici, c'est la
 * correspondance entre le `snake_case` de la table et le modele — donc les `@SerialName` —, et les
 * deux regles que l'adaptateur porte sans le dire :
 *
 *  * **une nature absente compte comme un passage du Coran.** La colonne `recording_type` a ete
 *    ajoutee apres coup ; une ligne ecrite avant n'en porte pas, et l'original la replie
 *    (`item.recording_type ?? 'quran'`). Sans cette regle, une ligne ancienne ferait echouer le
 *    decodage — donc disparaitrait de la liste **sans que rien ne le dise** ;
 *  * **une colonne que la requete ne demande pas ne casse pas le decodage.** `invocation_snapshot`
 *    est un document que ce portage ne modelise pas ; la requete l'omet deliberement, et
 *    `AppJson` porte `ignoreUnknownKeys`. Le banc fixe les deux, parce que retirer l'un des deux
 *    ferait tomber la lecture d'une invocation.
 *
 * Les charges utiles sont ecrites **comme le serveur les envoie** : noms `snake_case`, et les
 * colonnes que la requete ne demande pas y figurent quand meme — c'est le cas reel, PostgREST ne
 * filtrant que ce qu'on lui demande de filtrer.
 */
class RecitationSourceTest {

    // -----------------------------------------------------------------------
    // La ligne d'une recitation
    // -----------------------------------------------------------------------

    @Test
    fun `une ligne complete se traduit champ par champ`() {
        val json = """
            {
              "id": "r1",
              "user_id": "moi",
              "start_verse_id": 2,
              "end_verse_id": 9,
              "duration_ms": 41000,
              "storage_path": "moi/r1.m4a",
              "created_at": "2026-10-07T10:00:00.000Z",
              "listened_at": "2026-10-07T11:00:00.000Z",
              "recording_type": "quran",
              "invocation_id": null
            }
        """.trimIndent()

        val ligne = AppJson.decodeFromString<RecitationReadRow>(json).toModel()

        assertEquals("r1", ligne.id)
        assertEquals("moi", ligne.userId)
        assertEquals(2, ligne.startVerseId)
        assertEquals(9, ligne.endVerseId)
        assertEquals(41_000L, ligne.durationMs)
        assertEquals("moi/r1.m4a", ligne.storagePath)
        assertEquals("2026-10-07T10:00:00.000Z", ligne.createdAt)
        assertEquals("2026-10-07T11:00:00.000Z", ligne.listenedAt)
        assertEquals(RecitationKind.QURAN, ligne.kind)
        assertNull(ligne.invocationId)
    }

    @Test
    fun `une nature absente compte comme un passage du Coran`() {
        // La colonne a ete ajoutee apres coup : une ligne ecrite avant n'en porte pas. La refuser
        // ferait disparaitre la ligne de la liste sans que rien ne le dise.
        val json = """
            {
              "id": "r1", "user_id": "moi", "duration_ms": 1000,
              "storage_path": "moi/r1.m4a", "created_at": "2026-10-07T10:00:00.000Z"
            }
        """.trimIndent()

        val ligne = AppJson.decodeFromString<RecitationReadRow>(json).toModel()

        assertEquals(RecitationKind.QURAN, ligne.kind)
    }

    @Test
    fun `une invocation se traduit avec sa nature et son invocation`() {
        val json = """
            {
              "id": "r2", "user_id": "moi", "start_verse_id": null, "end_verse_id": null,
              "duration_ms": 2000, "storage_path": "moi/r2.m4a",
              "created_at": "2026-10-07T10:00:00.000Z",
              "recording_type": "invocation", "invocation_id": "inv-1"
            }
        """.trimIndent()

        val ligne = AppJson.decodeFromString<RecitationReadRow>(json).toModel()

        assertEquals(RecitationKind.INVOCATION, ligne.kind)
        assertEquals("inv-1", ligne.invocationId)
        assertNull(ligne.startVerseId)
        assertNull(ligne.endVerseId)
    }

    @Test
    fun `une colonne non demandee ne casse pas le decodage`() {
        // `invocation_snapshot` est un document entier que ce portage ne modelise pas : la requete
        // l'omet, et le serveur peut malgre tout le renvoyer. `AppJson` porte `ignoreUnknownKeys`,
        // et c'est ce qui permet a une invocation de se lire quand meme.
        val json = """
            {
              "id": "r2", "user_id": "moi", "duration_ms": 2000,
              "storage_path": "moi/r2.m4a", "created_at": "2026-10-07T10:00:00.000Z",
              "recording_type": "invocation",
              "invocation_snapshot": {"id": "inv-1", "title": "Invocation du matin"}
            }
        """.trimIndent()

        val ligne = AppJson.decodeFromString<RecitationReadRow>(json).toModel()

        assertEquals(RecitationKind.INVOCATION, ligne.kind)
    }

    @Test
    fun `une ligne jamais ecoutee ne porte pas d'instant d'ecoute`() {
        val json = """
            {
              "id": "r1", "user_id": "moi", "duration_ms": 1000,
              "storage_path": "moi/r1.m4a", "created_at": "2026-10-07T10:00:00.000Z"
            }
        """.trimIndent()

        assertNull(AppJson.decodeFromString<RecitationReadRow>(json).toModel().listenedAt)
    }

    @Test
    fun `un instant d'ecoute nul se lit comme une absence d'ecoute`() {
        // La colonne peut valoir `null` explicitement : c'est le cas d'une ligne deja relue apres
        // l'ajout de la colonne. Les deux formes doivent mener au meme etat.
        val json = """
            {
              "id": "r1", "user_id": "moi", "duration_ms": 1000,
              "storage_path": "moi/r1.m4a", "created_at": "2026-10-07T10:00:00.000Z",
              "listened_at": null
            }
        """.trimIndent()

        assertNull(AppJson.decodeFromString<RecitationReadRow>(json).toModel().listenedAt)
    }

    // -----------------------------------------------------------------------
    // La correction d'un verset
    // -----------------------------------------------------------------------

    @Test
    fun `une correction se traduit champ par champ`() {
        val json = """
            {
              "id": "c1", "recitation_id": "r1", "verse_id": 5,
              "comment": "Allonge la voyelle", "voice_path": "admin/c1.m4a",
              "created_at": "2026-10-07T12:00:00.000Z",
              "resolved_at": "2026-10-08T09:00:00.000Z"
            }
        """.trimIndent()

        val correction = AppJson.decodeFromString<CorrectionReadRow>(json).toModel()

        assertEquals("c1", correction.id)
        assertEquals("r1", correction.recitationId)
        assertEquals(5, correction.verseId)
        assertEquals("Allonge la voyelle", correction.comment)
        assertEquals("admin/c1.m4a", correction.voicePath)
        assertEquals("2026-10-07T12:00:00.000Z", correction.createdAt)
        assertEquals("2026-10-08T09:00:00.000Z", correction.resolvedAt)
    }

    @Test
    fun `une correction non traitee ne porte pas d'instant de traitement`() {
        val json = """
            {
              "id": "c1", "recitation_id": "r1", "verse_id": 5,
              "created_at": "2026-10-07T12:00:00.000Z"
            }
        """.trimIndent()

        val correction = AppJson.decodeFromString<CorrectionReadRow>(json).toModel()

        assertNull(correction.resolvedAt)
        assertNull(correction.comment)
        assertNull(correction.voicePath)
    }

    // -----------------------------------------------------------------------
    // Le retour general
    // -----------------------------------------------------------------------

    @Test
    fun `un retour general se traduit champ par champ`() {
        val json = """
            {
              "id": "f1", "recitation_id": "r1", "comment": "Tres bien",
              "voice_path": "admin/f1.m4a", "created_at": "2026-10-07T13:00:00.000Z"
            }
        """.trimIndent()

        val retour = AppJson.decodeFromString<FeedbackReadRow>(json).toModel()

        assertEquals("f1", retour.id)
        assertEquals("r1", retour.recitationId)
        assertEquals("Tres bien", retour.comment)
        assertEquals("admin/f1.m4a", retour.voicePath)
        assertEquals("2026-10-07T13:00:00.000Z", retour.createdAt)
    }

    @Test
    fun `un retour general sans commentaire garde sa voix`() {
        // C'est le cas d'une correction **vocale** : l'ecran affiche alors le repli
        // « Commentaire vocal du professeur ». Le modele doit donc distinguer les deux absences.
        val json = """
            {
              "id": "f1", "recitation_id": "r1", "comment": null,
              "voice_path": "admin/f1.m4a", "created_at": "2026-10-07T13:00:00.000Z"
            }
        """.trimIndent()

        val retour = AppJson.decodeFromString<FeedbackReadRow>(json).toModel()

        assertNull(retour.comment)
        assertEquals("admin/f1.m4a", retour.voicePath)
    }

    // -----------------------------------------------------------------------
    // Les bornes du domaine, que l'adaptateur emploie
    // -----------------------------------------------------------------------

    @Test
    fun `l'adresse signee dure dix minutes`() {
        assertEquals(600, Recitations.SIGNED_URL_SECONDS)
    }

    @Test
    fun `la liste distante est bornee a cent lignes`() {
        // L'original borne a 100 pour la personne et a 200 pour l'administrateur. Seule la
        // premiere est portee : les gestes d'administration restent sur le web.
        assertEquals(100, Recitations.REMOTE_LIST_LIMIT)
    }
}
