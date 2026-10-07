package com.msoumaya.deepseekandroid.feature.recitations

import com.msoumaya.deepseekandroid.core.data.repository.RecitationState
import com.msoumaya.deepseekandroid.core.domain.RecitationText
import com.msoumaya.deepseekandroid.core.domain.RecitationsList
import com.msoumaya.deepseekandroid.core.model.GeneralFeedback
import com.msoumaya.deepseekandroid.core.model.LocalRecitation
import com.msoumaya.deepseekandroid.core.model.RecitationKind
import com.msoumaya.deepseekandroid.core.model.RecitationSyncStatus
import com.msoumaya.deepseekandroid.core.model.RemoteRecitation
import com.msoumaya.deepseekandroid.core.model.VerseCorrection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Éprouve le rendu de l'écran « Mes récitations ».
 *
 * ## Ce qui est mesuré ici, et pourquoi c'est ici
 *
 * L'écran lui-même est une fonction `@Composable` : le déclencher demande un hôte Compose et un
 * appareil, et le projet n'a aucun outillage de test d'interface. Ce qui **décide** est donc
 * sorti du composable — `RecitationsRenderer` —, et c'est ici qu'il s'éprouve : la fusion des deux
 * listes, le filtre, le sous-titre, le message, les cartes.
 *
 * ## Le référentiel coranique n'est pas chargé
 *
 * La référence d'un passage et le nom d'un verset sont **passés** au rendu. Sans cela, chaque
 * test devrait charger les 6236 versets pour vérifier une phrase, et le test dirait autant de
 * choses sur `Quran` que sur l'écran.
 *
 * ## Les dates sont recalculées, pas écrites
 *
 * `RecitationText.dateStamp` lit le fuseau de l'appareil. Écrire « 01/01/2026 11:00:00 » en dur
 * ferait passer le test à Paris et échouer à Londres, sans qu'aucun code ne soit faux. L'attendu
 * est donc **recalculé par le JDK**, avec le même motif et le même fuseau — c'est la règle déjà
 * suivie par `SocialTextTest`.
 */
class RecitationsRendererTest {

    private val reference: (Int, Int) -> String = { debut, fin -> "reference $debut-$fin" }
    private val verset: (Int) -> String = { id -> "sourate du verset $id" }

    // ------------------------------------------------------------------
    // La liste
    // ------------------------------------------------------------------

    @Test
    fun `la liste fusionne le distant et le local`() {
        val vue = rendre(etat(items = listOf(locale("loc-1")), remote = listOf(distante("dis-1"))))

        assertEquals(setOf("dis-1", "loc-1"), vue.rows.map { it.id }.toSet())
    }

    @Test
    fun `une recitation que le serveur ne porte pas se supprime par l'appareil`() {
        val vue = rendre(etat(items = listOf(locale("loc-1")), remote = listOf(distante("dis-1"))))

        assertTrue(vue.rows.first { it.id == "loc-1" }.localOnly)
        assertFalse(
            vue.rows.first { it.id == "dis-1" }.localOnly,
            "une recitation arrivee du serveur se supprime par le serveur",
        )
    }

    @Test
    fun `le filtre des invocations ne garde que les invocations`() {
        val vue = rendre(
            etat(
                remote = listOf(
                    distante("coran"),
                    distante("invoc", kind = RecitationKind.INVOCATION, start = null, end = null),
                ),
            ),
            RecitationsInputs(filter = RecitationsList.Filter.INVOCATION),
        )

        assertEquals(listOf("invoc"), vue.rows.map { it.id })
    }

    @Test
    fun `le filtre du Coran ecarte les invocations`() {
        val vue = rendre(
            etat(
                remote = listOf(
                    distante("coran"),
                    distante("invoc", kind = RecitationKind.INVOCATION, start = null, end = null),
                ),
            ),
            RecitationsInputs(filter = RecitationsList.Filter.QURAN),
        )

        assertEquals(listOf("coran"), vue.rows.map { it.id })
    }

    @Test
    fun `les libelles de filtre sont ceux de l'original`() {
        val vue = rendre(etat())

        assertEquals(
            listOf(
                RecitationText.FILTER_ALL,
                RecitationText.FILTER_QURAN,
                RecitationText.FILTER_INVOCATION,
            ),
            vue.filterLabels,
        )
        assertEquals(RecitationText.FILTER_ALL, vue.filterLabel)
    }

    // ------------------------------------------------------------------
    // Le titre et le sous-titre
    // ------------------------------------------------------------------

    @Test
    fun `un titre de Coran porte la reference du passage`() {
        val vue = rendre(etat(remote = listOf(distante("dis-1", start = 1, end = 7))))

        assertEquals("${RecitationText.TITLE_PREFIX_QURAN} · reference 1-7", vue.rows.single().title)
    }

    @Test
    fun `une invocation se titre avec le repli de la liste`() {
        val vue = rendre(
            etat(
                remote = listOf(
                    distante(
                        "invoc",
                        kind = RecitationKind.INVOCATION,
                        start = null,
                        end = null,
                        invocationId = "inv-1",
                    ),
                ),
            ),
        )

        assertEquals(
            "${RecitationText.TITLE_PREFIX_INVOCATION} · ${RecitationText.LIST_INVOCATION_FALLBACK}",
            vue.rows.single().title,
        )
        assertEquals("inv-1", vue.rows.single().invocationId)
    }

    @Test
    fun `le sous-titre porte la date, la duree et le statut`() {
        val vue = rendre(
            etat(remote = listOf(distante("dis-1", createdAt = ISO, durationMs = 60_000L))),
        )

        assertEquals(
            "${stamp(ISO)} · 1:00 · ${RecitationText.SYNC_SYNCED}",
            vue.rows.single().subtitle,
        )
    }

    @Test
    fun `une copie locale en attente se lit En attente`() {
        val vue = rendre(
            etat(
                items = listOf(locale("loc-1", RecitationSyncStatus.PENDING)),
                remote = listOf(distante("loc-1")),
            ),
        )

        assertTrue(vue.rows.single().subtitle.endsWith(RecitationText.SYNC_PENDING))
    }

    @Test
    fun `un instant illisible est omis du sous-titre`() {
        // L'original ecrirait « Invalid Date » a cet endroit, ce qui n'apprend rien a personne.
        val vue = rendre(etat(remote = listOf(distante("dis-1", createdAt = "pas une date"))))

        assertEquals("1:00 · ${RecitationText.SYNC_SYNCED}", vue.rows.single().subtitle)
    }

    // ------------------------------------------------------------------
    // Le message, et le vide
    // ------------------------------------------------------------------

    @Test
    fun `sans compte, le message renvoie au profil`() {
        val vue = rendre(etat(ownerId = null))

        assertFalse(vue.signedIn)
        assertEquals(RecitationText.LIST_SIGNED_OUT_PROFILE, vue.message)
    }

    @Test
    fun `sans projet configure, le message n'envoie pas au profil`() {
        // Il n'y a pas d'ecran de connexion a ouvrir : y renvoyer serait une impasse.
        val vue = rendre(etat(ownerId = null), configured = false)

        assertEquals(RecitationText.LIST_SIGNED_OUT, vue.message)
    }

    @Test
    fun `une liste vide etablie se lit Aucune recitation`() {
        val vue = rendre(etat())

        assertTrue(vue.empty)
        assertNull(vue.message)
    }

    @Test
    fun `une liste vide par panne ne se lit pas Aucune recitation`() {
        val panne = RecitationText.listLocalOnly("reseau absent")
        val vue = rendre(etat(notice = panne))

        assertFalse(vue.empty, "on ne dit pas qu'il n'y a rien quand on n'a pas pu lire")
        assertEquals(panne, vue.message)
    }

    @Test
    fun `une liste vide sans compte ne se lit pas Aucune recitation`() {
        val vue = rendre(etat(ownerId = null))

        assertFalse(vue.empty)
    }

    @Test
    fun `un filtre qui ne laisse rien passer ne dit pas qu'il n'y a rien`() {
        // La personne a des recitations : c'est le filtre qui les cache. Ecrire « aucune
        // recitation enregistree » serait une affirmation fausse sur ce qu'elle a fait.
        val vue = rendre(
            etat(remote = listOf(distante("dis-1"))),
            RecitationsInputs(filter = RecitationsList.Filter.INVOCATION),
        )

        assertTrue(vue.rows.isEmpty())
        assertFalse(vue.empty, "l'ecran ne doit pas affirmer que la personne n'a rien enregistre")
    }

    // ------------------------------------------------------------------
    // La recitation ouverte
    // ------------------------------------------------------------------

    @Test
    fun `la ligne ouverte est celle qui est depliee`() {
        val vue = rendre(
            etat(remote = listOf(distante("dis-1"), distante("dis-2"))),
            RecitationsInputs(openId = "dis-2"),
        )

        assertEquals(listOf("dis-2"), vue.rows.filter { it.open }.map { it.id })
        assertEquals("dis-2", vue.openId)
    }

    @Test
    fun `le bouton de lecture suit l'etat de lecture`() {
        val arret = rendre(
            etat(remote = listOf(distante("dis-1"))),
            RecitationsInputs(openId = "dis-1"),
        )
        val marche = rendre(
            etat(remote = listOf(distante("dis-1"))),
            RecitationsInputs(openId = "dis-1", playing = true),
        )

        assertEquals(RecitationText.LIST_PLAY, arret.playLabel)
        assertEquals(RecitationText.LIST_PAUSE, marche.playLabel)
    }

    @Test
    fun `la position et la barre ne concernent que la recitation ouverte`() {
        val ferme = rendre(etat(remote = listOf(distante("dis-1"))))

        assertEquals("", ferme.positionLabel)
        assertEquals(0f, ferme.progressPercent)
    }

    @Test
    fun `la position et la barre suivent l'avancee`() {
        val vue = rendre(
            etat(remote = listOf(distante("dis-1", durationMs = 60_000L))),
            RecitationsInputs(openId = "dis-1", positionMs = 30_000L),
        )

        assertTrue(vue.positionLabel.contains("0:30"), vue.positionLabel)
        assertEquals(50f, vue.progressPercent)
    }

    @Test
    fun `la barre ne depasse pas cent`() {
        val vue = rendre(
            etat(remote = listOf(distante("dis-1", durationMs = 60_000L))),
            RecitationsInputs(openId = "dis-1", positionMs = 600_000L),
        )

        assertEquals(100f, vue.progressPercent)
    }

    // ------------------------------------------------------------------
    // Les cartes
    // ------------------------------------------------------------------

    @Test
    fun `les cartes ne s'affichent pas quand rien n'est ouvert`() {
        val vue = rendre(
            etat(remote = listOf(distante("dis-1"))),
            corrections = listOf(correction("c-1", "dis-1")),
            feedback = listOf(retour("f-1", "dis-1")),
        )

        assertTrue(vue.corrections.isEmpty())
        assertTrue(vue.feedback.isEmpty())
    }

    @Test
    fun `les cartes de correction portent le verset, le repli et le jour`() {
        val vue = rendre(
            etat(remote = listOf(distante("dis-1"))),
            RecitationsInputs(openId = "dis-1"),
            corrections = listOf(correction("c-1", "dis-1", verseId = 3)),
        )

        val carte = vue.corrections.single()
        assertEquals("sourate du verset 3", carte.label)
        assertEquals(RecitationText.CORRECTION_FALLBACK, carte.comment)
        assertEquals(jour(ISO), carte.date)
    }

    @Test
    fun `une correction commentee garde son commentaire et sa voix`() {
        val vue = rendre(
            etat(remote = listOf(distante("dis-1"))),
            RecitationsInputs(openId = "dis-1"),
            corrections = listOf(
                correction("c-1", "dis-1", comment = "Allonge la voyelle", voice = "voix/c.m4a"),
            ),
        )

        assertEquals("Allonge la voyelle", vue.corrections.single().comment)
        assertEquals("voix/c.m4a", vue.corrections.single().voicePath)
    }

    @Test
    fun `les cartes de retour portent le repli et l'adresse vocale`() {
        val vue = rendre(
            etat(remote = listOf(distante("dis-1"))),
            RecitationsInputs(openId = "dis-1"),
            feedback = listOf(retour("f-1", "dis-1")),
        )

        val carte = vue.feedback.single()
        assertEquals(RecitationText.FEEDBACK_TITLE, carte.title)
        assertEquals(RecitationText.FEEDBACK_FALLBACK, carte.comment)
        assertNull(carte.voicePath)
    }

    @Test
    fun `une correction sans date lisible ne porte pas de jour`() {
        // `dateOnly` rend `null` plutot qu'un texte d'erreur : la carte reste lisible avec ce
        // qu'elle sait, et l'appelant decide de ne rien ecrire.
        val vue = rendre(
            etat(remote = listOf(distante("dis-1"))),
            RecitationsInputs(openId = "dis-1"),
            corrections = listOf(correction("c-1", "dis-1").copy(createdAt = "pas une date")),
        )

        assertNull(vue.corrections.single().date)
    }

    @Test
    fun `une correction presente se lit Corrigee`() {
        val vue = rendre(
            etat(remote = listOf(distante("dis-1"))),
            RecitationsInputs(openId = "dis-1"),
            corrections = listOf(correction("c-1", "dis-1")),
        )

        assertTrue(vue.positionLabel.endsWith(RecitationText.STATUS_CORRECTED), vue.positionLabel)
    }

    @Test
    fun `une invocation ecoutee se lit Ecoutee`() {
        val vue = rendre(
            etat(
                remote = listOf(
                    distante(
                        "invoc",
                        kind = RecitationKind.INVOCATION,
                        start = null,
                        end = null,
                        listenedAt = ISO,
                    ),
                ),
            ),
            RecitationsInputs(openId = "invoc"),
        )

        assertTrue(vue.positionLabel.endsWith(RecitationText.STATUS_LISTENED), vue.positionLabel)
    }

    // ------------------------------------------------------------------
    // Outillage
    // ------------------------------------------------------------------

    private fun rendre(
        state: RecitationState,
        inputs: RecitationsInputs = RecitationsInputs(),
        corrections: List<VerseCorrection> = emptyList(),
        feedback: List<GeneralFeedback> = emptyList(),
        configured: Boolean = true,
    ) = RecitationsRenderer.render(
        state = state,
        inputs = inputs,
        corrections = corrections,
        feedback = feedback,
        configured = configured,
        reference = reference,
        verse = verset,
    )

    private fun etat(
        ownerId: String? = MOI,
        loading: Boolean = false,
        busy: Boolean = false,
        notice: String? = null,
        items: List<LocalRecitation> = emptyList(),
        remote: List<RemoteRecitation> = emptyList(),
    ) = RecitationState(
        ownerId = ownerId,
        loading = loading,
        busy = busy,
        notice = notice,
        items = items,
        remote = remote,
    )

    private fun distante(
        id: String,
        createdAt: String = ISO,
        durationMs: Long = 60_000L,
        kind: RecitationKind = RecitationKind.QURAN,
        start: Int? = 1,
        end: Int? = 7,
        listenedAt: String? = null,
        invocationId: String? = null,
    ) = RemoteRecitation(
        id = id,
        userId = MOI,
        startVerseId = start,
        endVerseId = end,
        durationMs = durationMs,
        storagePath = "$MOI/$id.m4a",
        createdAt = createdAt,
        listenedAt = listenedAt,
        kind = kind,
        invocationId = invocationId,
    )

    private fun locale(
        id: String,
        syncStatus: RecitationSyncStatus = RecitationSyncStatus.SYNCED,
    ) = LocalRecitation(
        id = id,
        userId = MOI,
        start = 1,
        end = 7,
        durationMs = 60_000L,
        uri = "file:///recitations/$id.m4a",
        createdAt = ISO,
        syncStatus = syncStatus,
    )

    private fun correction(
        id: String,
        recitationId: String,
        verseId: Int = 3,
        comment: String? = null,
        voice: String? = null,
    ) = VerseCorrection(
        id = id,
        recitationId = recitationId,
        verseId = verseId,
        comment = comment,
        voicePath = voice,
        createdAt = ISO,
    )

    private fun retour(id: String, recitationId: String) = GeneralFeedback(
        id = id,
        recitationId = recitationId,
        createdAt = ISO,
    )

    /** Le meme motif que le code, mais calcule par le JDK : le test ne depend pas du fuseau. */
    private fun stamp(iso: String): String = DateTimeFormatter
        .ofPattern("dd/MM/yyyy HH:mm:ss", Locale.FRANCE)
        .format(Instant.parse(iso).atZone(ZoneId.systemDefault()))

    private fun jour(iso: String): String = DateTimeFormatter
        .ofPattern("dd/MM/yyyy", Locale.FRANCE)
        .format(Instant.parse(iso).atZone(ZoneId.systemDefault()))

    @Test
    fun `les details d'une autre ligne ne s'affichent pas`() {
        // Les corrections arrivent apres un aller-retour reseau : ouvrir une ligne, puis une
        // autre, laisse la premiere reponse arriver en retard. Sans etiquette, elle s'afficherait
        // sous la seconde ligne, et rien ne le dirait.
        val chargees = RecitationsDetails(
            id = "rec-a",
            corrections = listOf(correction("c-1", "rec-a")),
            feedback = listOf(retour("f-1", "rec-a")),
        )

        assertTrue(
            chargees.forOpen("rec-a") === chargees,
            "les details de la ligne ouverte doivent rester",
        )
        assertTrue(
            chargees.forOpen("rec-b").corrections.isEmpty(),
            "les corrections d'une autre ligne ne doivent pas s'afficher sous celle-ci",
        )
        assertTrue(
            chargees.forOpen(null).feedback.isEmpty(),
            "tout replie, plus aucune carte",
        )
    }

    @Test
    fun `une lecture en vol ne dit pas que la liste est vide`() {
        // Le registre local est publie avant la liste distante : entre les deux, l'ecran est vide
        // parce qu'il **attend**. Annoncer alors « aucune recitation enregistree » serait une
        // affirmation fausse sur ce que la personne a fait, et au pire moment — a l'ouverture.
        val vue = rendre(etat(loading = true), RecitationsInputs())

        assertFalse(
            vue.empty,
            "l'ecran ne doit pas affirmer que la personne n'a rien enregistre pendant qu'il lit",
        )
    }

    private companion object {
        const val MOI = "moi-0000"
        const val ISO = "2026-01-01T10:00:00Z"
    }
}
