package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.ProblemReportType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le banc des règles du signalement de problème.
 *
 * Chaque test nomme la règle qu'il protège. Les cas marqués « comme dans l'original » portent une
 * décision **mesurée** dans `src/services/problemReports.ts` et `src/ui/ProblemReport.tsx`, et non
 * déduite : ce sont ceux qui ont l'air d'un défaut et n'en sont pas, donc ceux qu'un relecteur
 * « corrigerait ».
 *
 * Les deux règles de l'original qui **disparaissent** dans le portage — `problemTypes.includes` et
 * le test du littéral `'Duplicate'` sur un code d'état — ne sont pas éprouvées ici : elles ne sont
 * plus des règles, et un test qui les chercherait mesurerait une absence. Elles sont nommées dans
 * `ProblemReports`, à l'endroit où elles s'appliquaient.
 */
class ProblemReportsTest {

    /** Un texte de [longueur] caractères, tous distincts pour qu'une troncature se voie. */
    private fun texte(longueur: Int): String = "a".repeat(longueur)

    // ---------------------------------------------------------------- la description

    @Test
    fun `une description vide est refusee`() {
        assertEquals(
            ProblemReportProblem.DESCRIPTION_EMPTY,
            ProblemReports.descriptionProblem(""),
        )
    }

    @Test
    fun `une description faite d'espaces est vide, comme dans l'original`() {
        // L'original écrit `!text` sur le texte **rogné** : trois espaces sont un texte vide, et
        // non un texte trop court. La phrase affichée est celle de la borne, et elle a l'air
        // fausse — c'est le comportement de l'original, et le rogner ici serait un écart que rien
        // à l'écran ne montrerait.
        assertEquals(
            ProblemReportProblem.DESCRIPTION_EMPTY,
            ProblemReports.descriptionProblem("   "),
        )
        assertEquals(
            ProblemReportProblem.DESCRIPTION_EMPTY,
            ProblemReports.descriptionProblem("\n\t "),
        )
    }

    @Test
    fun `la borne est de cinq cents caracteres`() {
        assertNull(ProblemReports.descriptionProblem(texte(500)))
        assertEquals(
            ProblemReportProblem.DESCRIPTION_TOO_LONG,
            ProblemReports.descriptionProblem(texte(501)),
        )
    }

    @Test
    fun `la borne se mesure sur le texte rogne, comme dans l'original`() {
        // 501 caractères dont les deux derniers sont des espaces : rognés, il en reste 499, et le
        // serveur compte `length(btrim(description))` — il acceptera. Refuser ici serait un écart
        // silencieux entre les deux clients.
        assertNull(ProblemReports.descriptionProblem(texte(499) + "  "))
        // Et le rognage ne sauve pas un texte trop long : 501 caractères utiles restent 501.
        assertEquals(
            ProblemReportProblem.DESCRIPTION_TOO_LONG,
            ProblemReports.descriptionProblem("  " + texte(501) + "  "),
        )
    }

    // ---------------------------------------------------------------- la capture

    @Test
    fun `le format est controle avant la taille, comme dans l'original`() {
        // Une image de 6 Mo au format GIF reçoit le message de **format** : l'original contrôle
        // le type MIME avant la taille, et inverser les deux changerait le message affiché.
        assertEquals(
            ProblemReportAttachmentProblem.FORMAT_UNSUPPORTED,
            ProblemReports.attachmentProblem("image/gif", 6L * 1024L * 1024L),
        )
    }

    @Test
    fun `la borne de taille est de cinq megaeoctets, et elle est stricte`() {
        assertNull(ProblemReports.attachmentProblem(ProblemReports.MIME_JPEG, 5_242_880L))
        assertEquals(
            ProblemReportAttachmentProblem.TOO_LARGE,
            ProblemReports.attachmentProblem(ProblemReports.MIME_JPEG, 5_242_881L),
        )
    }

    @Test
    fun `la borne de la capture est celle du compartiment`() {
        // Le `5*1024*1024` du client et le `file_size_limit` du compartiment sont le **même
        // nombre** (5 242 880). Si l'un des deux changeait, une capture acceptée par l'écran
        // serait refusée par le serveur, et la personne ne l'apprendrait qu'après l'attente d'un
        // dépôt.
        assertEquals(5_242_880L, ProblemReports.SCREENSHOT_MAX_BYTES)
    }

    @Test
    fun `les deux formats acceptes sont ceux du compartiment`() {
        assertTrue(ProblemReports.isAcceptedMime(ProblemReports.MIME_JPEG))
        assertTrue(ProblemReports.isAcceptedMime(ProblemReports.MIME_PNG))
        assertFalse(ProblemReports.isAcceptedMime("image/gif"))
        assertFalse(ProblemReports.isAcceptedMime("image/webp"))
        assertFalse(ProblemReports.isAcceptedMime(""))
        // La comparaison est **exacte**, comme le `includes` de l'original : un type MIME qui
        // porte un paramètre n'est pas reconnu.
        assertFalse(ProblemReports.isAcceptedMime("image/jpeg;charset=utf-8"))
    }

    // ---------------------------------------------------------------- le type MIME

    @Test
    fun `un type mime absent se deduit de l'extension`() {
        assertEquals(
            ProblemReports.MIME_PNG,
            ProblemReports.resolveMime(null, "content://media/external/images/1000.PNG"),
        )
        assertEquals(
            ProblemReports.MIME_JPEG,
            ProblemReports.resolveMime(null, "content://media/external/images/1000.jpg"),
        )
    }

    @Test
    fun `le repli ne reconnait que la fin de l'adresse`() {
        // `/\.png$/` : l'extension doit terminer l'adresse. Une adresse qui la porte au milieu
        // n'est pas une image PNG.
        assertEquals(
            ProblemReports.MIME_JPEG,
            ProblemReports.resolveMime(null, "content://dossier.png/photo"),
        )
    }

    @Test
    fun `un type mime declare gagne sur l'extension`() {
        // `asset.mimeType ?? …` : le type MIME du sélecteur n'est **jamais** contredit par
        // l'adresse, même quand les deux ne disent pas la même chose.
        assertEquals(
            ProblemReports.MIME_JPEG,
            ProblemReports.resolveMime(ProblemReports.MIME_JPEG, "photo.png"),
        )
        assertEquals(
            ProblemReports.MIME_PNG,
            ProblemReports.resolveMime(ProblemReports.MIME_PNG, "photo.jpg"),
        )
    }

    @Test
    fun `une extension inconnue est declaree jpeg, comme dans l'original`() {
        // Le repli est « .png, ou rien » : un `.gif` sans type MIME est **déclaré** JPEG et
        // déposé comme tel. C'est une imprécision de l'original, et elle est reproduite — le
        // serveur ne lit que le type déclaré, et le corriger ici ferait diverger les deux clients
        // sur ce qui est acceptable.
        assertEquals(
            ProblemReports.MIME_JPEG,
            ProblemReports.resolveMime(null, "animation.gif"),
        )
    }

    @Test
    fun `l'extension se deduit du type mime, par egalite exacte`() {
        assertEquals(ProblemReports.EXTENSION_PNG, ProblemReports.extensionFor(ProblemReports.MIME_PNG))
        assertEquals(ProblemReports.EXTENSION_JPG, ProblemReports.extensionFor(ProblemReports.MIME_JPEG))
        // `mime === 'image/png'` : une comparaison exacte, et non une recherche. Un type MIME qui
        // porterait « png » ailleurs qu'à la fin donne `jpg` — c'est le comportement de
        // l'original, et il est sans conséquence parce que [ProblemReports.resolveMime] ne rend
        // jamais autre chose que ses deux valeurs.
        assertEquals(
            ProblemReports.EXTENSION_JPG,
            ProblemReports.extensionFor("image/png;charset=utf-8"),
        )
    }

    // ---------------------------------------------------------------- l'adresse

    @Test
    fun `l'adresse de la capture porte le compte en tete`() {
        assertEquals("moi-0000/abc.jpg", ProblemReports.screenshotPath("moi-0000", "abc", "jpg"))
        assertEquals("moi-0000/abc.png", ProblemReports.screenshotPath("moi-0000", "abc", "png"))
    }

    @Test
    fun `les deux formes d'adresse sont exactement celles de la colonne`() {
        // La contrainte `screenshot_path` du schéma n'accepte que ces deux formes :
        // `user_id||'/'||id||'.jpg'` et la même avec `.png`. Le test rejoue la composition du
        // schéma à partir de la même entrée, et compare : un désaccord ferait refuser l'insertion,
        // et le signalement resterait dans la file **pour toujours**.
        val compte = "11111111-2222-3333-4444-555555555555"
        val id = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        val acceptees = setOf(
            "$compte/$id.jpg",
            "$compte/$id.png",
        )
        assertEquals(
            acceptees,
            setOf(
                ProblemReports.screenshotPath(compte, id, ProblemReports.EXTENSION_JPG),
                ProblemReports.screenshotPath(compte, id, ProblemReports.EXTENSION_PNG),
            ),
        )
    }

    // ---------------------------------------------------------------- les tolérances

    @Test
    fun `un fichier deja depose est tolere, par le code ou par le message`() {
        assertTrue(ProblemReports.uploadFailureIsBenign("409", null))
        assertTrue(ProblemReports.uploadFailureIsBenign("Duplicate", null))
        assertTrue(ProblemReports.uploadFailureIsBenign(null, "already exists"))
        assertTrue(ProblemReports.uploadFailureIsBenign(null, "Duplicate entry"))
        assertTrue(ProblemReports.uploadFailureIsBenign("409", "quelque chose"))
    }

    @Test
    fun `la comparaison du message ignore la casse et cherche au milieu`() {
        assertTrue(ProblemReports.uploadFailureIsBenign(null, "ALREADY EXISTS"))
        assertTrue(ProblemReports.uploadFailureIsBenign(null, "the object already exists in the bucket"))
        assertTrue(ProblemReports.uploadFailureIsBenign(null, "Duplicate"))
    }

    @Test
    fun `un echec sans texte n'est jamais tenu pour benin`() {
        // Sans texte, rien ne prouve que l'échec est bénin, et le tenir pour tel ferait écrire une
        // ligne vers une capture que le compartiment ne contient pas.
        assertFalse(ProblemReports.uploadFailureIsBenign(null, null))
        assertFalse(ProblemReports.uploadFailureIsBenign(null, ""))
    }

    @Test
    fun `un refus quelconque n'est pas benin`() {
        assertFalse(ProblemReports.uploadFailureIsBenign("400", "bad request"))
        assertFalse(ProblemReports.uploadFailureIsBenign("500", "internal error"))
        assertFalse(ProblemReports.uploadFailureIsBenign("403", "new row violates row-level security"))
    }

    // ---------------------------------------------------------------- la tentative, et l'issue

    @Test
    fun `hors ligne, on ne tente pas`() {
        assertFalse(ProblemReports.shouldAttempt(horsLigne = true))
        assertTrue(ProblemReports.shouldAttempt(horsLigne = false))
    }

    @Test
    fun `l'issue se lit sur la file, et non sur la tentative`() {
        // La dernière ligne de `sendProblemReport` : `db.getFirstSync(…)?'queued':'sent'`.
        assertEquals(ProblemReportOutcome.QUEUED, ProblemReports.outcome(stillQueued = true))
        assertEquals(ProblemReportOutcome.SENT, ProblemReports.outcome(stillQueued = false))
    }

    // ---------------------------------------------------------------- l'accord avec le schéma

    @Test
    fun `les cinq natures sont celles de la colonne type`() {
        // `check(type in('Bug','Affichage','Audio','Notification','Autre'))`. Un `in` est un
        // **appartenance**, pas un ordre : c'est donc en ensemble que l'accord se verifie, et
        // l'ordre — dont l'ecran depend — est fige par `ProblemReportTypeKeyTest` cote
        // `core:model`. Les `@SerialName` sont ces chaines-la : les traduire ferait ecrire au
        // client une valeur que le serveur refuse, et le refus n'arriverait qu'au depot.
        assertEquals(
            setOf("Bug", "Affichage", "Audio", "Notification", "Autre"),
            ProblemReportType.all.map { it.wire }.toSet(),
        )
    }

    @Test
    fun `les trois plateformes sont celles de la colonne platform`() {
        assertEquals("ios", ProblemReports.PLATFORM_IOS)
        assertEquals("android", ProblemReports.PLATFORM_ANDROID)
        assertEquals("web", ProblemReports.PLATFORM_WEB)
    }

    @Test
    fun `le statut ecrit est celui qu'exige la politique d'insertion`() {
        // `with check(user_id=auth.uid() and status='open')`. Un signalement rejoué par la file
        // doit encore porter cette valeur, sans quoi le serveur le refuserait — et le refus
        // n'arriverait qu'au dépôt, jamais au geste.
        assertEquals("open", ProblemReports.STATUS_OPEN)
    }

    @Test
    fun `la borne de la version est celle de la colonne`() {
        assertEquals(32, ProblemReports.APP_VERSION_MAX)
    }

    // ---------------------------------------------------------------- les mots

    @Test
    fun `l'accuse de reception est celui de l'original`() {
        assertEquals(
            "Ton signalement a été envoyé à l’administrateur. Merci !",
            ProblemReportText.SENT,
        )
    }

    @Test
    fun `l'apostrophe de l'accuse de reception est typographique`() {
        // Mesuré dans `src/ui/ProblemReport.tsx` : le caractère est U+2019, et non une apostrophe
        // droite. Un portage qui la redresse produit un texte presque identique, et rien ne le
        // signale.
        assertTrue(ProblemReportText.SENT.contains('\u2019'))
        assertFalse(ProblemReportText.SENT.contains('\''))
    }

    @Test
    fun `la mise en attente est celle de l'original`() {
        assertEquals(
            "Ton signalement est enregistré. Il sera envoyé automatiquement dès que la " +
                "connexion sera disponible.",
            ProblemReportText.QUEUED,
        )
    }

    @Test
    fun `les deux issues ont chacune leur phrase`() {
        assertEquals(
            ProblemReportText.SENT,
            ProblemReportText.outcomeMessage(ProblemReportOutcome.SENT),
        )
        assertEquals(
            ProblemReportText.QUEUED,
            ProblemReportText.outcomeMessage(ProblemReportOutcome.QUEUED),
        )
    }

    @Test
    fun `les refus sont ceux de l'original`() {
        assertEquals("Connecte-toi pour envoyer un signalement.", ProblemReportText.NOT_SIGNED_IN)
        assertEquals(
            "Décris le problème en 500 caractères maximum.",
            ProblemReportText.DESCRIPTION_INVALID,
        )
        assertEquals(
            "Choisis une capture au format JPEG ou PNG.",
            ProblemReportText.ATTACHMENT_FORMAT,
        )
        assertEquals("La capture doit faire moins de 5 Mo.", ProblemReportText.ATTACHMENT_TOO_LARGE)
        assertEquals(
            "Connexion au serveur indisponible.",
            ProblemReportText.SERVER_UNAVAILABLE,
        )
        assertEquals(
            "Confirmation du signalement en attente.",
            ProblemReportText.CONFIRMATION_PENDING,
        )
    }

    @Test
    fun `la phrase de description porte le chiffre de la borne`() {
        // La phrase et la borne disent le même nombre, et elles sont écrites à deux endroits. Un
        // test les tient ensemble : changer la borne sans la phrase ferait dire « 500 » à un
        // refus qui en exige 600.
        assertTrue(ProblemReportText.DESCRIPTION_INVALID.contains(ProblemReports.DESCRIPTION_MAX.toString()))
    }
}
