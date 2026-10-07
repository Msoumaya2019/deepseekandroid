package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.RecitationKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Les décisions de l'enregistreur de récitation.
 *
 * ## Ce que ces tests couvrent, et ce qu'ils ne peuvent pas couvrir
 *
 * Tout ce qui est éprouvé ici est **une décision** : quel format produire, ce qui empêche de
 * commencer, quels gestes sont offerts dans quelle phase, quel mot s'affiche, quel temps écoulé. Ces décisions se prouvent sans appareil.
 *
 * Ce qui n'est **pas** ici, et ne peut pas y être : que le microphone capte réellement, que
 * `MediaRecorder` accepte la configuration demandée, que la pause conserve le fichier ouvert, que
 * le système accorde la permission. Ces choses-là se prouvent sur un appareil, et elles sont
 * dites comme telles dans `README.md` plutôt que supposées vertes.
 *
 * ## Pourquoi l'ordre des gestes est éprouvé
 *
 * Une barre d'actions n'est pas un ensemble, c'est une **suite** : le pouce apprend une place, et
 * permuter deux boutons change le geste. Un test qui vérifierait seulement la présence des gestes
 * laisserait passer exactement le défaut qui compte.
 */
class RecitationRecorderTest {

    // --- le format produit -------------------------------------------------

    @Test
    fun `le format annonce est celui que le depot saura nommer`() {
        assertEquals(".m4a", RecitationRecorder.RECORDED_EXTENSION)
        assertEquals("audio/mp4", RecitationRecorder.RECORDED_MIME_TYPE)
    }

    @Test
    fun `l'extension et le type MIME ne peuvent pas diverger`() {
        // Les deux passent par les memes fonctions que le depot : si l'une change, l'autre suit.
        assertEquals(
            Recitations.extensionFor("enregistrement" + RecitationRecorder.RECORDED_EXTENSION),
            RecitationRecorder.RECORDED_EXTENSION,
        )
        assertEquals(
            Recitations.contentType(RecitationRecorder.RECORDED_EXTENSION),
            RecitationRecorder.RECORDED_MIME_TYPE,
        )
    }

    @Test
    fun `une adresse de depot batie sur ce format porte bien l'extension`() {
        val chemin = Recitations.storagePath(
            "8f1c",
            "rec-1",
            RecitationRecorder.RECORDED_EXTENSION,
        )
        assertEquals("8f1c/rec-1.m4a", chemin)
    }

    @Test
    fun `le debit et les canaux sont ceux que l'original demandait`() {
        assertEquals(64_000, RecitationRecorder.BIT_RATE)
        assertEquals(1, RecitationRecorder.CHANNELS)
        assertEquals(44_100, RecitationRecorder.SAMPLE_RATE)
    }

    // --- la notice ---------------------------------------------------------

    @Test
    fun `la cle de la notice porte le compte`() {
        assertEquals("recitation-info-8f1c", RecitationRecorder.noticeKey("8f1c"))
    }

    @Test
    fun `deux comptes sur le meme appareil ne partagent pas la notice`() {
        assertNotEquals(
            RecitationRecorder.noticeKey("8f1c"),
            RecitationRecorder.noticeKey("2b7e"),
        )
    }

    @Test
    fun `seul le mot attendu vaut acceptation`() {
        assertTrue(RecitationRecorder.noticeAccepted("yes"))
        assertFalse(RecitationRecorder.noticeAccepted("YES"))
        assertFalse(RecitationRecorder.noticeAccepted("oui"))
        assertFalse(RecitationRecorder.noticeAccepted("yes "))
        assertFalse(RecitationRecorder.noticeAccepted(""))
        assertFalse(RecitationRecorder.noticeAccepted(null))
    }

    // --- ce qui empeche de commencer ---------------------------------------

    @Test
    fun `sans compte rien ne commence, meme si tout le reste est pret`() {
        assertEquals(
            RecitationStartProblem.OWNER_MISSING,
            RecitationRecorder.startProblem(
                userId = null,
                noticeAccepted = true,
                microphoneGranted = true,
            ),
        )
    }

    @Test
    fun `un compte vide vaut une absence de compte`() {
        assertEquals(
            RecitationStartProblem.OWNER_MISSING,
            RecitationRecorder.startProblem("", true, true),
        )
    }

    @Test
    fun `la notice passe avant le microphone`() {
        assertEquals(
            RecitationStartProblem.NOTICE_PENDING,
            RecitationRecorder.startProblem("8f1c", noticeAccepted = false, microphoneGranted = false),
        )
    }

    @Test
    fun `le microphone refuse est dit quand la notice est passee`() {
        assertEquals(
            RecitationStartProblem.MICROPHONE_DENIED,
            RecitationRecorder.startProblem("8f1c", noticeAccepted = true, microphoneGranted = false),
        )
    }

    @Test
    fun `quand tout est la, rien ne bloque`() {
        assertNull(RecitationRecorder.startProblem("8f1c", true, true))
    }

    // --- les phases --------------------------------------------------------

    @Test
    fun `terminer n'a d'effet que pendant la capture`() {
        assertTrue(RecitationRecorder.canFinish(RecitationPhase.RECORDING))
        assertTrue(RecitationRecorder.canFinish(RecitationPhase.PAUSED))
        assertFalse(RecitationRecorder.canFinish(RecitationPhase.IDLE))
        assertFalse(RecitationRecorder.canFinish(RecitationPhase.PREVIEW))
        assertFalse(RecitationRecorder.canFinish(RecitationPhase.SAVED))
    }

    @Test
    fun `une invocation passe par l'apercu avant d'etre gardee`() {
        assertEquals(
            RecitationPhase.PREVIEW,
            RecitationRecorder.phaseAfterFinish(isInvocation = true, compact = false),
        )
    }

    @Test
    fun `un enregistreur reduit passe par l'apercu`() {
        assertEquals(
            RecitationPhase.PREVIEW,
            RecitationRecorder.phaseAfterFinish(isInvocation = false, compact = true),
        )
    }

    @Test
    fun `un passage du Coran en pleine page va droit a l'enregistre`() {
        assertEquals(
            RecitationPhase.SAVED,
            RecitationRecorder.phaseAfterFinish(isInvocation = false, compact = false),
        )
    }

    // --- les gestes offerts ------------------------------------------------

    @Test
    fun `au repos il n'y a qu'un geste, et c'est commencer`() {
        assertEquals(
            listOf(RecitationAction.BEGIN),
            RecitationRecorder.actionsFor(RecitationPhase.IDLE, canShare = false),
        )
    }

    @Test
    fun `pendant la capture on suspend, on termine ou on annule, dans cet ordre`() {
        assertEquals(
            listOf(
                RecitationAction.PAUSE,
                RecitationAction.FINISH,
                RecitationAction.CANCEL,
            ),
            RecitationRecorder.actionsFor(RecitationPhase.RECORDING, canShare = false),
        )
    }

    @Test
    fun `en pause on reprend, on termine ou on annule`() {
        assertEquals(
            listOf(
                RecitationAction.RESUME,
                RecitationAction.FINISH,
                RecitationAction.CANCEL,
            ),
            RecitationRecorder.actionsFor(RecitationPhase.PAUSED, canShare = false),
        )
    }

    @Test
    fun `l'apercu propose de reecouter, recommencer ou garder`() {
        assertEquals(
            listOf(
                RecitationAction.LISTEN,
                RecitationAction.RESTART,
                RecitationAction.SAVE,
            ),
            RecitationRecorder.actionsFor(RecitationPhase.PREVIEW, canShare = false),
        )
    }

    @Test
    fun `une recitation gardee se reecoute ou se recommence`() {
        assertEquals(
            listOf(RecitationAction.LISTEN, RecitationAction.RESTART),
            RecitationRecorder.actionsFor(RecitationPhase.SAVED, canShare = false),
        )
    }

    /**
     * Les deux moitiés du nom, et la seconde manquait.
     *
     * Le test affirmait « s'ajoute en dernier » et rien de « et seulement s'il y a de quoi et
     * qui » : la branche **sans destinataire** n'était donc pas éprouvée. La campagne de
     * falsification l'a montré — une mutation qui fait apparaître le partage sans destinataire
     * passait inaperçue.
     */
    @Test
    fun `le partage s'ajoute en dernier, et seulement s'il y a de quoi et qui`() {
        assertEquals(
            listOf(
                RecitationAction.LISTEN,
                RecitationAction.RESTART,
                RecitationAction.SHARE,
            ),
            RecitationRecorder.actionsFor(RecitationPhase.SAVED, canShare = true),
        )
        assertEquals(
            listOf(RecitationAction.LISTEN, RecitationAction.RESTART),
            RecitationRecorder.actionsFor(RecitationPhase.SAVED, canShare = false),
            "sans destinataire, le partage ne s'ajoute pas",
        )
    }

    @Test
    fun `aucune phase n'offre deux fois le meme geste`() {
        for (phase in RecitationPhase.entries) {
            for (partage in listOf(false, true)) {
                val gestes = RecitationRecorder.actionsFor(phase, canShare = partage)
                assertEquals(gestes.distinct(), gestes, "phase $phase, partage $partage")
            }
        }
    }

    @Test
    fun `aucune phase n'offre les deux gestes opposes de la capture`() {
        for (phase in RecitationPhase.entries) {
            val gestes = RecitationRecorder.actionsFor(phase, canShare = false)
            val suspendre = gestes.contains(RecitationAction.PAUSE)
            val reprendre = gestes.contains(RecitationAction.RESUME)
            val capte = gestes.contains(RecitationAction.FINISH)
            assertFalse(suspendre && reprendre, "phase $phase : suspendre et reprendre ensemble")
            assertFalse(capte && gestes.contains(RecitationAction.SAVE), "phase $phase : deux gardes")
            assertFalse(capte && gestes.contains(RecitationAction.BEGIN), "phase $phase : commencer et terminer")
        }
    }

    @Test
    fun `au plus un geste est mis en avant par phase`() {
        for (phase in RecitationPhase.entries) {
            val enAvant = RecitationRecorder.actionsFor(phase, canShare = true)
                .filter { RecitationRecorder.isPrimary(it) }
            assertTrue(enAvant.size <= 1, "phase $phase : $enAvant")
        }
    }

    @Test
    fun `les phases ou il reste quelque chose a faire ont un geste mis en avant`() {
        for (phase in listOf(
            RecitationPhase.IDLE,
            RecitationPhase.RECORDING,
            RecitationPhase.PAUSED,
            RecitationPhase.PREVIEW,
        )) {
            val enAvant = RecitationRecorder.actionsFor(phase, canShare = true)
                .filter { RecitationRecorder.isPrimary(it) }
            assertEquals(1, enAvant.size, "phase $phase : $enAvant")
        }
    }

    /**
     * Après sauvegarde, **rien** n'est mis en avant — et ce n'est pas un oubli de l'original.
     *
     * Le geste mis en avant ailleurs est celui qui achève le travail : commencer, terminer,
     * garder. Une fois la récitation gardée, il n'y a plus rien à achever ; les deux gestes qui
     * restent — réécouter, recommencer — sont des issues, et en désigner une inviterait à effacer
     * un enregistrement qu'on voulait garder.
     *
     * Ce test a d'abord été écrit faux : il exigeait un geste mis en avant dans **chaque** phase,
     * et il a refusé `SAVED` à juste titre. Le défaut était dans l'attente, pas dans le code.
     */
    @Test
    fun `une recitation gardee ne met rien en avant`() {
        // Les **deux** listes de `SAVED`, et non celle du partage seulement : la branche sans
        // destinataire était le point aveugle. La campagne de falsification l'a montré — y
        // remplacer « réécouter, recommencer » par « réécouter, enregistrer » passait inaperçu,
        // alors que « Enregistrer » est un geste mis en avant.
        for (partage in listOf(false, true)) {
            val gestes = RecitationRecorder.actionsFor(RecitationPhase.SAVED, canShare = partage)
            assertTrue(gestes.none { RecitationRecorder.isPrimary(it) }, "canShare=$partage : $gestes")
        }
    }

    @Test
    fun `les gestes mis en avant sont ceux qu'on attend`() {
        assertTrue(RecitationRecorder.isPrimary(RecitationAction.BEGIN))
        assertTrue(RecitationRecorder.isPrimary(RecitationAction.FINISH))
        assertTrue(RecitationRecorder.isPrimary(RecitationAction.SAVE))
        for (geste in listOf(
            RecitationAction.PAUSE,
            RecitationAction.RESUME,
            RecitationAction.CANCEL,
            RecitationAction.LISTEN,
            RecitationAction.RESTART,
            RecitationAction.SHARE,
        )) {
            assertFalse(RecitationRecorder.isPrimary(geste), "$geste ne doit pas etre mis en avant")
        }
    }

    // --- le temps ecoule ---------------------------------------------------

    @Test
    fun `le temps est ecrit en minutes et secondes`() {
        assertEquals("00:00", RecitationRecorder.clock(0))
        assertEquals("00:01", RecitationRecorder.clock(1_000))
        assertEquals("00:59", RecitationRecorder.clock(59_999))
        assertEquals("01:00", RecitationRecorder.clock(60_000))
        assertEquals("01:01", RecitationRecorder.clock(61_000))
        assertEquals("59:59", RecitationRecorder.clock(3_599_999))
    }

    @Test
    fun `les minutes ne sont pas repliees a soixante`() {
        assertEquals("60:00", RecitationRecorder.clock(3_600_000))
        assertEquals("120:00", RecitationRecorder.clock(7_200_000))
    }

    @Test
    fun `les millisecondes sont tronquees et non arrondies`() {
        assertEquals("00:00", RecitationRecorder.clock(999))
        assertEquals("00:01", RecitationRecorder.clock(1_999))
        assertEquals("00:02", RecitationRecorder.clock(2_999))
    }

    @Test
    fun `le zero de tete est ecrit des la premiere minute`() {
        assertEquals("09:05", RecitationRecorder.clock(545_000))
    }

    /**
     * Divergence assumée, et **épinglée** pour qu'elle ne se perde pas.
     *
     * L'expression d'origine divise en nombres à virgule et rend `-1:-1` ; l'arithmétique entière
     * de ce portage rend `-1:59`. Aucun appelant ne peut produire une durée négative — elle vient
     * du compteur du système —, mais la valeur est fixée ici pour que le jour où quelqu'un
     * croiserait le cas, il lise une décision et non une surprise.
     */
    @Test
    fun `une duree negative suit l'arithmetique entiere du portage`() {
        assertEquals("-1:59", RecitationRecorder.clock(-1))
    }

    // --- l'occupation ------------------------------------------------------

    @Test
    fun `l'enregistreur occupe la personne pendant la capture et pendant une sauvegarde`() {
        assertTrue(RecitationRecorder.isActive(RecitationPhase.RECORDING, busy = false))
        assertTrue(RecitationRecorder.isActive(RecitationPhase.PAUSED, busy = false))
        assertTrue(RecitationRecorder.isActive(RecitationPhase.IDLE, busy = true))
        assertTrue(RecitationRecorder.isActive(RecitationPhase.PREVIEW, busy = true))
    }

    @Test
    fun `au repos et sans travail l'enregistreur ne retient personne`() {
        assertFalse(RecitationRecorder.isActive(RecitationPhase.IDLE, busy = false))
        assertFalse(RecitationRecorder.isActive(RecitationPhase.PREVIEW, busy = false))
        assertFalse(RecitationRecorder.isActive(RecitationPhase.SAVED, busy = false))
    }

    // --- le titre et la ligne ----------------------------------------------

    @Test
    fun `le titre suit ce qu'on enregistre`() {
        assertEquals(
            RecitationText.RECORDER_TITLE_QURAN,
            RecitationRecorder.title(isInvocation = false),
        )
        assertEquals(
            RecitationText.RECORDER_TITLE_INVOCATION,
            RecitationRecorder.title(isInvocation = true),
        )
    }

    @Test
    fun `la ligne d'un passage est sa reference`() {
        assertEquals(
            Quran.reference(Range(1, 7)),
            RecitationRecorder.subtitle(isInvocation = false, invocationTitle = null, range = Range(1, 7)),
        )
    }

    @Test
    fun `la ligne d'une invocation suit son titre`() {
        assertEquals(
            "Le matin",
            RecitationRecorder.subtitle(isInvocation = true, invocationTitle = "Le matin", range = null),
        )
    }

    @Test
    fun `un titre absent devient le mot invocation`() {
        assertEquals(
            RecitationText.INVOCATION_FALLBACK,
            RecitationRecorder.subtitle(isInvocation = true, invocationTitle = null, range = null),
        )
    }

    @Test
    fun `un titre vide reste vide, il ne devient pas invocation`() {
        // Le `??` de l'original ne replie que sur null : une chaine vide est un choix du serveur.
        assertEquals(
            "",
            RecitationRecorder.subtitle(isInvocation = true, invocationTitle = "", range = null),
        )
    }

    @Test
    fun `la nature decide de la ligne, pas la presence d'un titre`() {
        // Un titre d'invocation ne doit pas s'afficher pour un passage du Coran...
        assertEquals(
            Quran.reference(Range(1, 7)),
            RecitationRecorder.subtitle(isInvocation = false, invocationTitle = "Le matin", range = Range(1, 7)),
        )
        // ...et une plage ne doit pas s'afficher pour une invocation.
        assertEquals(
            "Le matin",
            RecitationRecorder.subtitle(isInvocation = true, invocationTitle = "Le matin", range = Range(1, 7)),
        )
    }

    @Test
    fun `sans passage ni invocation la ligne est vide et non absente`() {
        assertEquals(
            "",
            RecitationRecorder.subtitle(isInvocation = false, invocationTitle = null, range = null),
        )
    }

    // --- les libelles d'etat ----------------------------------------------

    @Test
    fun `chaque phase a son mot dans la barre reduite`() {
        assertEquals(RecitationText.RECORDING, RecitationRecorder.compactLabel(RecitationPhase.RECORDING))
        assertEquals(RecitationText.PAUSED_COMPACT, RecitationRecorder.compactLabel(RecitationPhase.PAUSED))
        assertEquals(RecitationText.PHASE_PREVIEW, RecitationRecorder.compactLabel(RecitationPhase.PREVIEW))
        assertEquals(RecitationText.PHASE_SAVED, RecitationRecorder.compactLabel(RecitationPhase.SAVED))
        assertEquals(RecitationText.PHASE_IDLE, RecitationRecorder.compactLabel(RecitationPhase.IDLE))
    }

    @Test
    fun `la pleine page ne nomme que la capture`() {
        assertEquals(RecitationText.RECORDING, RecitationRecorder.fullLabel(RecitationPhase.RECORDING))
        assertEquals(RecitationText.PAUSED_FULL, RecitationRecorder.fullLabel(RecitationPhase.PAUSED))
        assertNull(RecitationRecorder.fullLabel(RecitationPhase.IDLE))
        assertNull(RecitationRecorder.fullLabel(RecitationPhase.PREVIEW))
        assertNull(RecitationRecorder.fullLabel(RecitationPhase.SAVED))
    }

    @Test
    fun `les deux mises en page ne disent pas la meme chose en pause`() {
        // L'original ecrit « II En pause » en pleine page et « En pause » dans la barre reduite.
        assertNotEquals(RecitationText.PAUSED_COMPACT, RecitationText.PAUSED_FULL)
        assertTrue(RecitationText.PAUSED_FULL.endsWith(RecitationText.PAUSED_COMPACT))
        // Et les deux fonctions **rendent** bien ces deux mots : comparer les constantes entre
        // elles ne dit rien de la correspondance phase -> mot, et la campagne de falsification l'a
        // montre — faire rendre « En pause » a la pleine page passait inapercu.
        assertNotEquals(
            RecitationRecorder.compactLabel(RecitationPhase.PAUSED),
            RecitationRecorder.fullLabel(RecitationPhase.PAUSED),
        )
    }

    // --- les libelles des gestes ------------------------------------------

    @Test
    fun `chaque geste a son mot dans la barre reduite`() {
        assertEquals(RecitationText.BAR_BEGIN, RecitationRecorder.compactActionLabel(RecitationAction.BEGIN))
        assertEquals(RecitationText.PAUSE, RecitationRecorder.compactActionLabel(RecitationAction.PAUSE))
        assertEquals(RecitationText.RESUME, RecitationRecorder.compactActionLabel(RecitationAction.RESUME))
        assertEquals(RecitationText.BAR_FINISH, RecitationRecorder.compactActionLabel(RecitationAction.FINISH))
        assertEquals(RecitationText.CANCEL, RecitationRecorder.compactActionLabel(RecitationAction.CANCEL))
        assertEquals(RecitationText.LISTEN, RecitationRecorder.compactActionLabel(RecitationAction.LISTEN))
        assertEquals(RecitationText.RESTART, RecitationRecorder.compactActionLabel(RecitationAction.RESTART))
        assertEquals(RecitationText.SAVE, RecitationRecorder.compactActionLabel(RecitationAction.SAVE))
        assertEquals(RecitationText.BAR_SHARE, RecitationRecorder.compactActionLabel(RecitationAction.SHARE))
    }

    @Test
    fun `les deux mots propres a la barre reduite sont ceux de l'original`() {
        // « Commencer » et « Partager » ne sont **pas** partages avec la pleine page, qui dit
        // « Enregistrer ma voix » et « Partager avec un ami ». Les figer empeche qu'une reecriture
        // de la barre les aligne sur l'autre mise en page sans que rien ne le dise : c'est la
        // seule paire de mots que les deux mises en page ne partagent pas.
        assertEquals("Commencer", RecitationText.BAR_BEGIN)
        assertEquals("Partager", RecitationText.BAR_SHARE)
        assertNotEquals(RecitationText.BAR_BEGIN, RecitationText.PAGE_BEGIN)
        assertNotEquals(RecitationText.BAR_SHARE, RecitationText.PAGE_SHARE)
        assertEquals("Commencer", RecitationRecorder.compactActionLabel(RecitationAction.BEGIN))
        assertEquals("Partager", RecitationRecorder.compactActionLabel(RecitationAction.SHARE))
    }

    @Test
    fun `aucun geste ne se lit deux fois dans la barre reduite`() {
        // Deux gestes qui porteraient le meme mot rendraient la barre ambigue : on ne saurait plus
        // lequel arrete en gardant et lequel arrete en jetant. L'original les distingue
        // (« Terminer » contre « Annuler »), et c'est cette distinction qui est verifiee — un
        // `map` exhaustif la couvre pour les neuf gestes d'un coup.
        val mots = RecitationAction.entries.map { RecitationRecorder.compactActionLabel(it) }
        assertEquals(mots.size, mots.toSet().size, "deux gestes portent le meme mot : $mots")
    }

    @Test
    fun `tout geste offert par une phase porte un mot dans la barre reduite`() {
        // Le lien entre les deux fonctions : ce qu'une phase offre doit pouvoir s'ecrire. Un geste
        // ajoute a `actionsFor` sans libelle donnerait un bouton vide, et le compilateur ne le
        // dirait pas — l'exhaustivite de `compactActionLabel` couvre le cas neuf, pas le cas
        // « offre mais pas encore nomme ».
        for (phase in RecitationPhase.entries) {
            for (geste in RecitationRecorder.actionsFor(phase, canShare = true)) {
                assertTrue(
                    RecitationRecorder.compactActionLabel(geste).isNotBlank(),
                    "$phase offre $geste, qui n'a pas de mot dans la barre reduite",
                )
            }
        }
    }

    // --- le cadre du panneau -----------------------------------------------

    @Test
    fun `le panneau de l'enregistreur porte les mots de l'original`() {
        // Les deux mots du **cadre**, écrits dans `App.tsx` (ligne 507) et non dans le composant de
        // l'enregistreur : le titre du panneau, et le nom de son bouton de fermeture.
        //
        // Le second **diverge** de l'original, et c'est le seul des deux : la source y écrit un
        // libellé générique — « Fermer le panneau » — parce qu'un seul gestionnaire ferme cinq de
        // ses six panneaux. Le figer ici empêche qu'une réécriture y retombe sans que rien ne le
        // dise : un lecteur d'écran qui les annonce tous de la même façon n'apprend plus **lequel**
        // il ferme.
        assertEquals("Ma récitation", RecitationText.PANEL_TITLE)
        assertEquals("Fermer l’enregistrement", RecitationText.PANEL_CLOSE)
        // Le panneau et la liste sont deux écrans, et l'original leur donne deux titres. Les
        // confondre ferait dire « Ma récitation » à l'écran qui les liste toutes.
        assertNotEquals(RecitationText.PANEL_TITLE, RecitationText.LIST_TITLE)
    }

    // --- la duree affichee -------------------------------------------------

    @Test
    fun `la barre reduite ne montre aucune duree au repos`() {
        assertNull(
            RecitationRecorder.shownDurationMs(RecitationPhase.IDLE, draftMs = 5_000, itemMs = 7_000, liveMs = 9_000),
        )
    }

    @Test
    fun `le brouillon passe avant la recitation gardee, qui passe avant le compteur vivant`() {
        val phase = RecitationPhase.PREVIEW
        assertEquals(5_000, RecitationRecorder.shownDurationMs(phase, 5_000, 7_000, 9_000))
        assertEquals(7_000, RecitationRecorder.shownDurationMs(phase, null, 7_000, 9_000))
        assertEquals(9_000, RecitationRecorder.shownDurationMs(phase, null, null, 9_000))
    }

    @Test
    fun `un brouillon de zero milliseconde n'est pas un brouillon absent`() {
        // `?:` en Kotlin et `??` en JavaScript tombent tous deux sur null et non sur zero.
        assertEquals(0, RecitationRecorder.shownDurationMs(RecitationPhase.PREVIEW, 0, 7_000, 9_000))
    }

    @Test
    fun `la pleine page ne lit que le compteur vivant`() {
        assertEquals(9_000, RecitationRecorder.fullDurationMs(RecitationPhase.RECORDING, 9_000))
        assertEquals(9_000, RecitationRecorder.fullDurationMs(RecitationPhase.PAUSED, 9_000))
        assertNull(RecitationRecorder.fullDurationMs(RecitationPhase.IDLE, 9_000))
        assertNull(RecitationRecorder.fullDurationMs(RecitationPhase.PREVIEW, 9_000))
        assertNull(RecitationRecorder.fullDurationMs(RecitationPhase.SAVED, 9_000))
    }

    // --- les mots ----------------------------------------------------------

    @Test
    fun `les deux mises en page partagent le meme mot pour arreter, reprendre et garder`() {
        assertEquals("Pause", RecitationText.PAUSE)
        assertEquals("Reprendre", RecitationText.RESUME)
        assertEquals("Réécouter", RecitationText.LISTEN)
        assertEquals("Recommencer", RecitationText.RESTART)
        assertEquals("Enregistrer", RecitationText.SAVE)
    }

    @Test
    fun `les deux mises en page ne partagent pas le mot de l'arret`() {
        assertNotEquals(RecitationText.BAR_FINISH, RecitationText.PAGE_FINISH_QURAN)
        assertEquals("Terminer", RecitationText.BAR_FINISH)
        assertEquals("Terminer et sauvegarder", RecitationText.PAGE_FINISH_QURAN)
        assertEquals("Arrêter", RecitationText.PAGE_FINISH_INVOCATION)
    }

    @Test
    fun `la notice porte ses deux issues`() {
        assertEquals("Annuler", RecitationText.CANCEL)
        assertEquals("Compris, enregistrer", RecitationText.NOTICE_ACCEPT)
        assertEquals("Tes récitations", RecitationText.NOTICE_TITLE)
    }

    @Test
    fun `la notice dit ce que devient l'enregistrement et comment le faire supprimer`() {
        // Le corps de la notice n'est pas decoratif : ces trois faits doivent y etre.
        assertTrue(RecitationText.NOTICE_BODY.contains("administrateur"))
        assertTrue(RecitationText.NOTICE_BODY.contains("restent sur ce téléphone"))
        assertTrue(RecitationText.NOTICE_BODY.contains("demander leur suppression"))
    }

    @Test
    fun `les apostrophes de la notice sont typographiques`() {
        // L'original ecrit « l'administrateur » avec U+2019, et non avec une apostrophe droite.
        assertTrue(RecitationText.NOTICE_BODY.contains("l’administrateur"))
        assertFalse(RecitationText.NOTICE_BODY.contains("l'administrateur"))
        assertTrue(RecitationText.RECORDING_CANCELLED.contains("n’a"))
        assertFalse(RecitationText.RECORDING_CANCELLED.contains("n'a"))
    }

    @Test
    fun `le symbole de pause de la pleine page est un chiffre romain`() {
        // U+2161, et non deux barres verticales : les deux ne se ressemblent pas a l'ecran.
        assertEquals('Ⅱ', RecitationText.PAUSED_FULL[0])
        assertEquals(0x2161, RecitationText.PAUSED_FULL[0].code)
    }
}
