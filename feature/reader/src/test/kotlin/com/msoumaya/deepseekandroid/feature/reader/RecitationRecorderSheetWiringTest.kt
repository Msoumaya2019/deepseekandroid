package com.msoumaya.deepseekandroid.feature.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tient le **cadre du panneau d'enregistrement** : son titre, sa fermeture, et la garde qui
 * empêche de perdre une capture.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * Deux conditions sont réunies, et il faut les deux.
 *
 *  1. **Le comportement est hors de portée.** [RecitationRecorderSheet] est une fonction
 *     `@Composable` montée dans une fenêtre de dialogue : la déclencher demande un hôte Compose,
 *     et le projet n'a aucun outillage de test d'interface.
 *  2. **Sa disparition serait silencieuse.** Le panneau n'apporte que son cadre — le reste est
 *     dans [RecitationRecorderBar] et dans `core:domain`. Si la garde disparaissait, ou si l'un
 *     des quatre chemins de fermeture cessait de passer par elle, **rien** ne le dirait : la
 *     compilation passerait, les deux autres suites resteraient vertes, et le seul symptôme serait
 *     une capture perdue en touchant à côté du panneau.
 *
 * ## La garde, et pourquoi elle est tenue ici plutôt qu'ailleurs
 *
 * L'original grise pendant une capture trois choses : le bouton de fermeture du panneau, la barre
 * flottante du lecteur, et la barre d'action d'une révision. Seule la **première** est portée, et
 * ce contrôle le fixe : les deux autres sont inatteignables — la barre flottante est derrière le
 * voile du dialogue, et la barre de révision ne se dessine que pour le panneau de séance, dont
 * l'ouverture referme celui-ci. Recopier ces deux-là ajouterait des paramètres que rien ne peut
 * rendre vrais.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que **la garde est écrite, et qu'elle couvre les quatre issues**. Il ne prouve pas
 * qu'une capture survit à un vrai toucher : cela demande un appareil.
 */
class RecitationRecorderSheetWiringTest {

    @Test
    fun `une seule garde couvre les quatre fermetures`() {
        val source = sourceDeLaFeuille()
        assertTrue(
            source.contains("val fermer = { if (!recordingActive) onClose() }"),
            "La garde de fermeture a disparu ou changé de forme : pendant une capture, le panneau " +
                "se refermerait, et la prise serait perdue sans que rien ne le dise.",
        )
        // Les quatre chemins : la touche de retour système, le voile, la poignée, le bouton. Une
        // garde posée sur trois d'entre eux laisserait une porte ouverte, et c'est exactement ce
        // qu'une relecture ne voit pas.
        //
        // Le motif est `= fermer` **sans virgule**, et c'est mesuré : `SheetHandle(onDismiss =
        // fermer)` est le seul argument de son appel, donc il n'est pas suivi d'une virgule, alors
        // que les trois autres le sont. Compter `= fermer,` n'en trouverait que trois, et le
        // contrôle annoncerait une porte ouverte qui n'existe pas.
        val appels = source.windowed("= fermer".length).count { it == "= fermer" }
        assertEquals(
            4,
            appels,
            "Le nombre de fermetures gardées a changé ($appels au lieu de 4) : un chemin de " +
                "fermeture a été ajouté sans la garde, ou en a perdu une.",
        )
    }

    @Test
    fun `le bouton de fermeture est grise pendant une capture`() {
        assertTrue(
            sourceDeLaFeuille().contains("enabled = !recordingActive,"),
            "Le bouton de fermeture n'est plus grisé pendant une capture : il resterait actif " +
                "alors que son rappel refuse, donc il mentirait sur ce qu'il fait.",
        )
    }

    @Test
    fun `le panneau porte le titre et la fermeture de l'original`() {
        val source = sourceDeLaFeuille()
        assertTrue(
            source.contains("text = RecitationText.PANEL_TITLE,"),
            "Le panneau n'affiche plus le titre de l'original — « Ma récitation » — : il " +
                "s'annoncerait autrement, ou pas du tout.",
        )
        assertTrue(
            source.contains("description = RecitationText.PANEL_CLOSE,"),
            "Le bouton de fermeture n'est plus décrit : un lecteur d'écran l'annoncerait comme " +
                "un bouton sans nom.",
        )
        assertTrue(
            source.contains("SheetCloseButton("),
            "Le panneau n'a plus de bouton de fermeture : la seule issue serait la poignée et la " +
                "touche de retour, et le cadre ne ressemblerait plus aux six autres feuilles.",
        )
    }

    @Test
    fun `la barre recoit la capacite, la plage et le rapport`() {
        val source = sourceDeLaFeuille()
        val appel = source.substringAfter("RecitationRecorderBar(")
        assertTrue(
            appel.length < source.length,
            "Le panneau ne monte plus la barre d'enregistrement : il s'ouvrirait sur un titre et " +
                "rien d'autre.",
        )
        assertTrue(
            appel.contains("capability = capability,"),
            "La barre ne reçoit plus la capacité : elle n'aurait ni microphone ni lecteur, et " +
                "aucun geste ne pourrait aboutir.",
        )
        assertTrue(
            appel.contains("range = range,"),
            "La barre ne reçoit plus la plage : la prise serait inscrite sans bornes, ou sur des " +
                "bornes choisies ailleurs.",
        )
        assertTrue(
            appel.contains("onRecordingChange = onRecordingChange,"),
            "La barre ne rapporte plus son occupation : la garde de fermeture ne saurait jamais " +
                "qu'une capture est en cours, et ne garderait donc rien.",
        )
    }

    @Test
    fun `le panneau est une fenetre de dialogue qui couvre la page`() {
        val source = sourceDeLaFeuille()
        assertTrue(
            source.contains("Dialog("),
            "Le panneau n'est plus une fenêtre de dialogue : c'est ce qui met la coquille du " +
                "lecteur derrière son voile, et donc ce qui rend inatteignable la barre flottante " +
                "que l'original grisait.",
        )
        assertTrue(
            source.contains("DialogProperties(usePlatformDefaultWidth = false)"),
            "Le dialogue se limite à la largeur d'un téléphone en paysage : le panneau ne " +
                "couvrirait plus toute la largeur de l'écran.",
        )
        assertTrue(
            source.contains(".align(Alignment.BottomCenter)"),
            "Le panneau n'est plus ancré en bas : il ne se lirait plus comme une feuille, alors " +
                "que les six autres le font.",
        )
    }

    /**
     * Le source de la feuille d'enregistrement.
     *
     * Deux chemins sont essayés, et non un seul : la tâche `Test` de Gradle s'exécute dans le
     * dossier **du module**, alors qu'un contrôle joué à la main depuis la racine du dépôt part de
     * là.
     */
    private fun sourceDeLaFeuille(): String {
        val relatif =
            "src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/RecitationRecorderSheet.kt"
        val candidats = listOf(File(relatif), File("feature/reader/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "RecitationRecorderSheet.kt introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }
}
