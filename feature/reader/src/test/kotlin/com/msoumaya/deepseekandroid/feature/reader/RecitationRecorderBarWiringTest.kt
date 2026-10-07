package com.msoumaya.deepseekandroid.feature.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient les **décisions de la surface d'enregistrement** — celles qui ont besoin d'un appareil, et
 * qui ne peuvent donc vivre nulle part ailleurs.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * Deux conditions sont réunies, et il faut les deux.
 *
 *  1. **Le comportement est hors de portée.** [RecitationRecorderBar] est une fonction
 *     `@Composable` : la déclencher demande un hôte Compose, un microphone, une `Activity` pour la
 *     permission, et le projet n'a aucun outillage de test d'interface. Les gestes ne sont atteints
 *     par aucun test de comportement — ce n'est pas un raccourci, c'est le seul moyen.
 *  2. **Leur disparition serait silencieuse.** Ce qui reste dans ce fichier est mince, et c'est
 *     voulu : la phase, les gestes et les mots sont dans `core:domain/RecitationRecorder.kt`, la
 *     ligne d'état dans `RecitationRecorderRenderer.kt`. Mais ce qui reste est précisément ce
 *     qu'aucune des deux suites ne regarde — **quelle capacité est appelée, dans quel ordre, et
 *     avec quelle condition**. Si `startCapture` cessait de couper l'écoute, ou si `finish`
 *     cessait d'effacer une prise annulée, les deux autres suites resteraient vertes : elles ne
 *     connaissent ni le lecteur ni l'enregistreur.
 *
 * ## Les décisions que ce contrôle vise
 *
 *  - **la capture coupe l'écoute avant d'ouvrir le microphone** — sans quoi la récitation qu'on
 *    vient de réécouter sortirait du haut-parleur et entrerait dans la prise ;
 *  - **aucun brouillon n'est posé au départ** — un brouillon à zéro figerait la ligne d'état sur
 *    `00:00` pendant toute la capture, la durée affichée prenant le brouillon d'abord ;
 *  - **l'annulation efface, la garde arrête** — `cancel()` sur le chemin qui jette, `stop()` sur
 *    celui qui garde, et rien ne traîne après une prise annulée ;
 *  - **la garde exige un compte et une prise** — les deux, et pas seulement la seconde ;
 *  - **reprendre suspend l'écoute, il ne la détruit pas** ;
 *  - **le partage exige la récitation et sa destination** — les deux conditions de l'original, et
 *    la seconde est ce qui fait *disparaître* le geste quand la route ne sait pas quoi en faire ;
 *  - **le drapeau d'occupation retombe au départ de la barre** — sans quoi l'écran qui le garde
 *    resterait bloqué : fermeture refusée, barre de révision grisée.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que ces appels sont **écrits**, dans cet ordre et sous ces conditions. Il ne prouve
 * pas qu'ils produisent l'effet voulu sur un appareil : cela demande un microphone, et se lit dans
 * un essai manuel — c'est dit dans `ARCHITECTURE.md`.
 */
class RecitationRecorderBarWiringTest {

    @Test
    fun `la capture coupe l'ecoute avant d'ouvrir le microphone`() {
        val bloc = blocDeLaCapture()
        val coupure = bloc.indexOf("capability.player.pause()")
        val ouverture = bloc.indexOf("capability.recorder.start()")
        assertTrue(
            coupure >= 0,
            "La capture n'arrête plus l'écoute : la récitation en cours sortirait du haut-parleur " +
                "et entrerait dans la prise.",
        )
        assertTrue(
            ouverture >= 0,
            "La capture n'ouvre plus le microphone : le premier geste ne capterait rien, et la " +
                "suite de la barre ne partirait jamais.",
        )
        assertTrue(
            coupure < ouverture,
            "L'écoute est arrêtée **après** l'ouverture du microphone : les premières secondes de " +
                "la prise porteraient la récitation qu'on venait de réécouter.",
        )
    }

    @Test
    fun `aucun brouillon n'est pose avant l'arret`() {
        val bloc = blocDeLaCapture()
        assertFalse(
            bloc.contains("draft ="),
            "Un brouillon est posé au départ de la capture. La durée affichée prend le brouillon " +
                "d'abord : la ligne d'état resterait figée sur `00:00` pendant toute la capture, " +
                "alors que le compteur vivant, lui, avance.",
        )
        assertTrue(
            bloc.contains("phase = RecitationPhase.RECORDING"),
            "La capture n'entre plus dans la phase d'enregistrement : le domaine ne rendrait " +
                "aucun geste d'arrêt, et la prise ne pourrait pas être gardée.",
        )
    }

    @Test
    fun `l'annulation efface la prise au lieu de la garder`() {
        val bloc = blocDeLArret()
        val abandon = bloc.substringAfter("if (!save) {")
        assertTrue(
            abandon.length < bloc.length,
            "Le chemin d'annulation n'est plus reconnaissable : la borne « if (!save) { » est " +
                "absente, donc la branche lue est le fichier entier.",
        )
        assertTrue(
            abandon.contains("capability.recorder.cancel()"),
            "L'annulation n'efface plus la prise : elle laisse son fichier dans le cache du " +
                "système, et l'on annule souvent plusieurs prises d'affilée.",
        )
        assertFalse(
            abandon.substringBefore("return@launch").contains("capability.recorder.stop()"),
            "L'annulation arrête l'enregistreur au lieu de l'effacer : `stop()` rend une prise et " +
                "peut lever quand rien n'a été capté — c'est le chemin de la garde, pas celui-ci.",
        )
    }

    @Test
    fun `la garde exige un compte et une prise`() {
        assertTrue(
            blocDeLArret().contains("capability.owner().isNullOrEmpty() || capture == null"),
            "La garde ne réclame plus les deux — un compte et une prise — : une récitation " +
                "pourrait être inscrite sous aucun compte, ou une capture vide passerait pour " +
                "une récitation.",
        )
    }

    @Test
    fun `reprendre suspend l'ecoute, il ne la detruit pas`() {
        val bloc = blocDeLaReprise()
        assertTrue(
            bloc.contains("capability.player.pause()"),
            "Reprendre ne suspend plus l'écoute : l'original met son lecteur en pause, et la " +
                "piste suivante est de toute façon rechargée par `play`.",
        )
        assertFalse(
            bloc.contains("capability.player.stop()"),
            "Reprendre détruit l'écoute au lieu de la suspendre : c'est un écart à l'original, " +
                "qui n'appelle jamais `stop()` à cet endroit.",
        )
    }

    @Test
    fun `le partage exige la recitation et sa destination`() {
        assertTrue(
            sourceDeLaBarre().contains("canShare = item != null && capability.share != null,"),
            "Le partage ne réclame plus ses deux conditions. La seconde n'est pas décorative : " +
                "la route du lecteur ne sait pas encore où partager, donc sa capacité porte " +
                "`share = null` — et sans cette condition, le geste « Partager » apparaîtrait " +
                "dans la barre sans rien faire.",
        )
    }

    @Test
    fun `le geste de partage passe par la destination facultative`() {
        assertTrue(
            sourceDeLaBarre().contains("capability.share?.invoke(gardee)"),
            "Le geste de partage n'appelle plus la destination par son accès sûr : soit il " +
                "appelle une lambda qui peut être absente, soit il partage une récitation qui " +
                "n'existe pas encore.",
        )
    }

    @Test
    fun `le drapeau retombe quand la barre quitte l'ecran`() {
        val bloc = blocDuDrapeau()
        assertTrue(
            bloc.contains("DisposableEffect(Unit)"),
            "Le drapeau d'occupation n'est plus rapporté au démontage : l'écran qui le garde " +
                "resterait bloqué après le départ de la barre — fermeture refusée, barre de " +
                "révision grisée, et pour toujours.",
        )
        assertTrue(
            bloc.contains("onDispose { notifier.value(false) }"),
            "Le démontage de la barre ne remet plus le drapeau à faux : l'écran se croirait " +
                "occupé par un enregistrement qui n'existe plus.",
        )
        // Ce troisième fait est vérifié sur le **fichier**, et non sur le bloc : la référence
        // tenue à jour est le marqueur qui borne le bloc, donc `substringAfter` l'a déjà retirée.
        // L'y chercher demandait au bloc de contenir sa propre borne — un contrôle qui ne pouvait
        // pas être vert, et qui l'a dit.
        assertTrue(
            sourceDeLaBarre().contains("rememberUpdatedState(onRecordingChange)"),
            "Le rappel n'est plus tenu à jour : un rappel capturé au premier passage viserait un " +
                "état périmé, et le drapeau retomberait chez le mauvais propriétaire.",
        )
    }

    @Test
    fun `les deux issues de l'arret sont deux gestes distincts`() {
        val bloc = blocDesGestes()
        assertTrue(
            bloc.contains("RecitationAction.FINISH -> finish(save = true)"),
            "Le geste de fin ne garde plus la prise : la récitation enregistrée serait jetée au " +
                "moment même où on la termine.",
        )
        assertTrue(
            bloc.contains("RecitationAction.CANCEL -> finish(save = false)"),
            "Le geste d'annulation ne jette plus la prise : elle serait inscrite alors que la " +
                "personne a demandé à l'abandonner.",
        )
    }

    /**
     * Le corps de `startCapture`, et lui seul.
     *
     * **Borné** : `capability.player.pause()` est appelé deux fois dans ce fichier — ici et dans
     * `restart()` —, et `phase = RecitationPhase.RECORDING` l'est aussi (la reprise). Une
     * recherche sans borne resterait verte si la capture perdait son propre appel.
     */
    private fun blocDeLaCapture(): String = bloc(
        marqueur = "    fun startCapture() {",
        borne = "\n    // La permission du microphone.",
        quoi = "la capture",
    )

    /**
     * Le corps de `finish`, et lui seul.
     *
     * **Borné** : `capability.recorder.stop()` et `phase = RecitationPhase.IDLE` vivent aussi dans
     * `restart()`, et `capability.recorder.cancel()` nulle part ailleurs — mais c'est la branche
     * qui compte, et elle est bornée une seconde fois à l'intérieur du test.
     */
    private fun blocDeLArret(): String = bloc(
        marqueur = "    fun finish(save: Boolean) {",
        borne = "\n    /** Garde le brouillon",
        quoi = "l'arrêt",
    )

    /** Le corps de `restart`, et lui seul. `capability.player.pause()` est appelé deux fois. */
    private fun blocDeLaReprise(): String = bloc(
        marqueur = "    fun restart() {",
        borne = "\n    fun onGesture(action: RecitationAction) {",
        quoi = "la reprise",
    )

    /** L'effet de démontage, et lui seul. */
    private fun blocDuDrapeau(): String = bloc(
        marqueur = "    val notifier = rememberUpdatedState(onRecordingChange)",
        borne = "\n\n    /**",
        quoi = "le drapeau d'occupation",
    )

    /** Le corps de `onGesture`, et lui seul. */
    private fun blocDesGestes(): String = bloc(
        marqueur = "    fun onGesture(action: RecitationAction) {",
        borne = "\n    val ui = RecitationRecorderRenderer.render(",
        quoi = "les gestes",
    )

    /**
     * Un bloc délimité du source de la barre.
     *
     * La borne est **vérifiée**, et non supposée : `substringAfter` et `substringBefore` sans repli
     * rendent la chaîne entière quand le marqueur ou le délimiteur manque, et un contrôle qui
     * lirait alors tout le fichier serait vert pour la mauvaise raison. L'échec dit lequel des
     * deux manque.
     */
    private fun bloc(marqueur: String, borne: String, quoi: String): String {
        val source = sourceDeLaBarre()
        assertTrue(
            source.contains(marqueur),
            "Le marqueur de « $quoi » est absent : `$marqueur`. Le fichier a changé de forme, et " +
                "le contrôle ne lit plus ce qu'il croit lire.",
        )
        val reste = source.substringAfter(marqueur)
        val extrait = reste.substringBefore(borne)
        // La comparaison porte sur le **reste**, et non sur le fichier. `substringAfter` a déjà
        // raccourci la chaîne : comparer l'extrait à `source.length` serait vrai même si la borne
        // manquait, donc un contrôle qui ne peut pas échouer. Ici, une borne absente rend
        // `extrait == reste`, et l'assertion tombe — c'est ce qu'on veut savoir.
        assertTrue(
            extrait.length < reste.length,
            "La borne de « $quoi » est absente : `$borne`. Le bloc lu va jusqu'à la fin du " +
                "fichier, donc toute recherche y serait vraie.",
        )
        return extrait
    }

    /**
     * Le source de la barre d'enregistrement.
     *
     * Deux chemins sont essayés, et non un seul : la tâche `Test` de Gradle s'exécute dans le
     * dossier **du module**, alors qu'un contrôle joué à la main depuis la racine du dépôt part de
     * là. Un chemin unique ferait passer le test ici et échouer là-bas — ou l'inverse.
     */
    private fun sourceDeLaBarre(): String {
        val relatif =
            "src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/RecitationRecorderBar.kt"
        val candidats = listOf(File(relatif), File("feature/reader/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "RecitationRecorderBar.kt introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }
}
