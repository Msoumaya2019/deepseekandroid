package com.msoumaya.deepseekandroid.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient le **branchement du panneau de séance** dans la route du lecteur.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * `ReaderRoute` est une fonction `@Composable` qui prend un `AppContainer` réel : la déclencher
 * demanderait un hôte Compose **et** un conteneur complet. Monter un conteneur entier pour
 * vérifier qu'une ligne appelle une règle serait disproportionné.
 *
 * ## Ce qui disparaîtrait sans un mot
 *
 * La règle vit dans `core:domain` et y est éprouvée pour de vrai — `SessionPanelTextTest`,
 * `ProgramTest`, `ReviewTest`. Ce qui n'est éprouvable nulle part ailleurs, c'est son
 * **branchement**, et quatre de ces branchements ont une conséquence qui ne se voit pas :
 *
 *  - sans la **requête entière** dans la coquille, le panneau ne saurait plus quelles entrées
 *    proposer — et aucun couple de bornes ne dit si la tâche était un apprentissage ou une
 *    révision ;
 *  - sans l'**étape de consolidation**, le libellé retomberait toujours sur J+7, quelle que soit
 *    l'échéance réelle : le bouton annoncerait une étape qui n'est pas celle qu'il valide ;
 *  - sans la **régénération** après « à réapprendre », le verset repartirait à un jour sans que le
 *    programme en tienne compte, et il ne serait proposé nulle part — un verset marqué que plus
 *    rien ne reproposerait ;
 *  - dans le mauvais **ordre** — fermer avant d'écrire —, l'écriture est annulée en vol par la mort
 *    de la portée, et le symptôme est exactement celui de l'absence d'écriture.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que le branchement est **écrit**, avec les bonnes valeurs et dans le bon ordre. Il ne
 * prouve pas que `Program.gradeRevision` ou `Review.completeConsolidation` calculent juste : c'est
 * leur banc, dans `core:domain`.
 */
class ReaderRouteSessionTest {

    @Test
    fun `la coquille d'etude recoit la requete entiere`() {
        // Les quatre faits de la tâche — apprentissage, révision, consolidation, révision
        // historique — sont ce dont le panneau tire ses entrées. La plage **prévue** ne les
        // remplace pas : elle dit « quels versets », jamais « quel genre de tâche ».
        assertTrue(
            sourceDeLaRoute().contains("request = session,"),
            "La coquille ne reçoit plus la requête : le panneau ne saurait plus quelles entrées " +
                "proposer, et une révision pourrait se voir offrir une clôture d'apprentissage.",
        )
    }

    @Test
    fun `la coquille d'etude recoit l'etape de consolidation`() {
        assertTrue(
            sourceDeLaRoute()
                .contains("consolidationOffset = StudySession.consolidationOffset(etat, session),"),
            "La coquille ne reçoit plus l'étape de consolidation : le libellé du bouton " +
                "retomberait toujours sur J+7, quelle que soit l'échéance réelle.",
        )
    }

    @Test
    fun `la route porte la demande d'ouvrir le panneau`() {
        // La seconde porte du panneau, celle qui rend la barre de notation atteignable pour une
        // révision. Le compteur est un **événement** : il n'est pas sauvegardé, parce qu'une
        // rotation ne doit pas rouvrir un panneau qu'on venait de refermer.
        val source = sourceDeLaRoute()
        assertTrue(
            source.contains("var sessionPanelRequest by remember { mutableIntStateOf(0) }"),
            "La route ne tient plus la demande d'ouverture : le panneau de séance serait " +
                "inatteignable depuis le sélecteur de présentation.",
        )
        assertTrue(
            source.contains("sessionPanelRequest = sessionPanelRequest,"),
            "La demande d'ouverture n'est plus transmise au lecteur.",
        )
        assertTrue(
            source.contains("sessionPanelRequest++"),
            "L'entrée du sélecteur n'incrémente plus la demande : elle ne ferait rien.",
        )
        assertFalse(
            source.contains("sessionPanelRequest by rememberSaveable"),
            "La demande d'ouverture est sauvegardée : une rotation rouvrirait un panneau qu'on " +
                "venait de refermer, alors que le client d'origine ne le rouvre pas.",
        )
    }

    @Test
    fun `le selecteur de presentation recoit l'entree de la seance`() {
        assertTrue(
            sourceDeLaRoute().contains("sessionActions = actionsSeance,"),
            "Le sélecteur ne reçoit plus l'entrée de la séance : la barre de notation serait " +
                "inatteignable pour une révision.",
        )
    }

    @Test
    fun `l'entree du selecteur n'existe que pour une tache`() {
        // `focused` du client d'origine : une séance d'apprentissage ou une révision. Une lecture
        // libre n'a pas d'actions de séance — et son panneau n'aurait aucune entrée.
        assertTrue(
            sourceDeLaRoute().contains("session?.takeIf { it.focused }"),
            "L'entrée du sélecteur n'est plus gardée par la présence d'une tâche : une lecture " +
                "libre se verrait proposer des actions de séance.",
        )
    }

    @Test
    fun `l'entree du selecteur nomme la plage ouverte`() {
        // La plage **ouverte** — le reste d'une tâche reprise —, comme dans le client d'origine,
        // où le libellé est `reference(reader.range)` et où `reader.range` est la plage de reprise.
        // La calculer sur la plage demandée annoncerait des versets qu'on ne relira pas.
        val source = sourceDeLaRoute()
        assertTrue(
            source.contains("val ouverte = userState?.let { StudySession.opening(it, requete) } ?: requete"),
            "La plage annoncée n'est plus la plage ouverte : l'entrée nommerait des versets que " +
                "la reprise ne relira pas.",
        )
        assertTrue(
            source.contains("runCatching { Quran.reference(ouverte.range) }.getOrNull()"),
            "La référence n'est plus résolue par la règle du domaine, ou une référence " +
                "irrésoluble produirait une entrée fautive au lieu de ne pas en produire.",
        )
    }

    @Test
    fun `reapprendre note puis regenere le programme`() {
        // Les deux, et la régénération reçoit l'état **noté**. Le client d'origine écrit
        // `update(value==='relearn' ? generateProgram(next) : next)`, où `next` est l'état déjà
        // noté : la régénération ne voit jamais l'état d'avant le marquage.
        //
        // Le lien entre les deux est une **imbrication**, et non l'ordre du texte — c'est la
        // mesure qui l'a montré. La notation est un *argument* de la régénération :
        // `generateProgram(` s'écrit donc **avant** `gradeRevision(` dans le fichier, alors que
        // c'est la notation qui s'exécute en premier. Éprouver `note < generation` accusait une
        // route juste. Ce qui compte est que la notation soit **dans** l'appel qui régénère.
        val rappel = rappel("onRelearn = {")
        val note = rappel.indexOf("Program.gradeRevision(")
        val generation = rappel.indexOf("Program.generateProgram(")
        assertTrue(note >= 0, "« À réapprendre » ne note plus la révision.")
        assertTrue(generation >= 0, "« À réapprendre » ne régénère plus le programme.")
        assertTrue(
            rappel.substringAfter("Program.generateProgram(").contains("Program.gradeRevision("),
            "La régénération ne reçoit plus l'état noté : le programme serait régénéré sans tenir " +
                "compte du marquage, et le verset ne serait reproposé nulle part.",
        )
        assertTrue(
            rappel.contains("LegacyReviewGrade.RELEARN"),
            "Le grade du marquage n'est plus `RELEARN` : la révision ne repartirait pas à un jour.",
        )
    }

    @Test
    fun `les deux gestes de cloture passent par la regle du domaine`() {
        // Les deux sont des écritures de séance, et aucune ne s'écrit à la main : la règle du
        // domaine porte le statut, la date prévue et la reprise d'une séance partielle.
        assertTrue(
            rappel("onWorkAgain = {").contains("Program.completeSession(state, seance, memorized = false)"),
            "« Je dois encore le travailler » n'écrit plus la séance comme inachevée.",
        )
        assertTrue(
            rappel("onPostpone = {").contains("Program.postponeSession(state, seance)"),
            "« Reporter cette séance » n'écrit plus le report.",
        )
    }

    @Test
    fun `valider une consolidation vise l'etape relue`() {
        // L'étape est **relue** au moment de l'appui, et non reçue du panneau : elle peut avoir
        // changé depuis son ouverture. Elle est passée en `targetOffset` — sans quoi
        // `completeConsolidation` validerait la première étape non faite, et un second appui
        // validerait l'étape **suivante**.
        val rappel = rappel("onValidateConsolidation = {")
        assertTrue(
            rappel.contains("StudySession.consolidationOffset(etat, r)"),
            "L'étape visée n'est plus relue : elle pourrait être périmée.",
        )
        assertTrue(
            rappel.contains("targetOffset = cible,"),
            "L'étape visée n'est plus épinglée : un second appui validerait l'étape suivante.",
        )
    }

    @Test
    fun `les quatre destinations ecrivent avant de fermer`() {
        // L'**ordre**, et il faut les deux bornes : sans elles, un `indexOf` qui rend `-1`
        // passerait pour « écriture avant fermeture » alors qu'il ne trouve rien. La portée meurt
        // avec l'écran, donc fermer d'abord annule l'écriture en vol — et le symptôme est celui
        // d'une écriture qui n'a jamais eu lieu.
        for ((marqueur, ecriture) in listOf(
            "onValidateConsolidation = {" to "Review.completeConsolidation(",
            "onRelearn = {" to "Program.gradeRevision(",
            "onWorkAgain = {" to "Program.completeSession(",
            "onPostpone = {" to "Program.postponeSession(",
        )) {
            val bloc = rappel(marqueur)
            val iEcriture = bloc.indexOf(ecriture)
            val iFermeture = bloc.indexOf("quitter()")
            assertTrue(iEcriture >= 0, "`$marqueur` n'écrit plus : `$ecriture` a disparu.")
            assertTrue(iFermeture >= 0, "`$marqueur` ne referme plus le lecteur.")
            assertTrue(
                iEcriture < iFermeture,
                "`$marqueur` ferme avant d'écrire : la portée meurt avec l'écran, donc rien " +
                    "n'est enregistré — alors que l'écran se ferme normalement.",
            )
            assertFalse(
                bloc.contains("onClose()"),
                "`$marqueur` referme par `onClose` au lieu de `quitter` : la position de " +
                    "lecture ne serait pas enregistrée.",
            )
        }
    }

    /**
     * Un rappel de la route, borné.
     *
     * La borne est `\n        },` — la fin d'un rappel nommé dans l'appel du lecteur. Elle est
     * accompagnée de son propre contrôle : `substringBefore` **sans repli** rend la chaîne entière
     * quand le délimiteur est absent, et le bloc lu serait alors le fichier — où le `quitter()`
     * trouvé serait celui d'un **autre** rappel. C'est le piège documenté du dépôt : un contrôle
     * de forme mesure ce qu'il trouve, pas ce qu'il vise.
     */
    private fun rappel(marqueur: String): String {
        val source = sourceDeLaRoute()
        assertTrue(source.contains(marqueur), "La route ne branche plus `$marqueur`.")
        val bloc = source.substringAfter(marqueur).substringBefore("\n        },")
        assertTrue(
            bloc.length < source.length,
            "La borne de `$marqueur` n'a pas mordu : le bloc lu est le fichier entier.",
        )
        return bloc
    }

    /**
     * Le source de la route du lecteur.
     *
     * Deux chemins sont essayés, et non un seul : la tâche `Test` de Gradle s'exécute dans le
     * dossier **du module**, alors qu'un contrôle joué à la main depuis la racine du dépôt part
     * de là.
     */
    private fun sourceDeLaRoute(): String {
        val relatif = "src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt"
        val candidats = listOf(File(relatif), File("navigation/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "ReaderRoute.kt introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }
}
