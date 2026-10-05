package com.msoumaya.deepseekandroid.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient le **branchement de la séance** dans la route du lecteur.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * `ReaderRoute` est une fonction `@Composable` qui prend un `AppContainer` réel : la déclencher
 * demanderait un hôte Compose **et** un conteneur complet. Monter un conteneur entier pour
 * vérifier qu'une ligne appelle la validation serait disproportionné.
 *
 * ## Ce qui disparaîtrait sans un mot
 *
 * La règle vit dans `core:domain` et y est éprouvée pour de vrai — `StudySessionTest`, quarante
 * cas sur le référentiel entier. Ce qui n'est éprouvable nulle part ailleurs, c'est son
 * **branchement** :
 *
 *  - sans le paramètre de séance, le lecteur ne saurait pas ce qu'il sert : pas de bandeau, pas
 *    de validation possible, et une séance qui s'ouvre comme une lecture libre ;
 *  - sans l'écriture, la séance se referme normalement et rien n'est enregistré — le symptôme est
 *    celui d'une séance qu'on n'a jamais validée, donc d'un programme qui redemande le même
 *    passage indéfiniment ;
 *  - dans le mauvais ordre, l'écriture est **annulée en vol** par la mort de la portée, et le
 *    symptôme est exactement celui de l'absence d'écriture : le diagnostic coûteux est celui-là ;
 *  - sans la source **repliée**, les règles d'étude cherchent leurs tables sous une clé qui n'en
 *    a pas, et annoncent la page de Médine pour une source qui a son propre découpage.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que le branchement est **écrit**, avec les bonnes valeurs et dans le bon ordre. Il ne
 * prouve pas que `StudySession.validate` calcule juste : c'est `StudySessionTest`.
 */
class ReaderRouteStudyTest {

    @Test
    fun `la route accepte une seance`() {
        // Sans le paramètre, tout le reste de ce fichier serait sans objet — et la séance
        // s'ouvrirait en lecture libre, sans que rien ne le dise.
        assertTrue(
            sourceDeLaRoute().contains("session: StudySession.Request? = null,"),
            "La route n'accepte plus de séance : le lecteur ne peut plus rien valider.",
        )
    }

    @Test
    fun `la page d'ouverture suit la seance`() {
        // Une séance s'ouvre sur son premier verset, et non là où la lecture s'était arrêtée.
        // Sans cette branche, le lecteur ouvrirait la page mémorisée — un passage qui n'est pas
        // celui du jour — et la séance semblerait commencer ailleurs.
        val source = sourceDeLaRoute()
        assertTrue(
            source.contains("val ouverte = userState?.let { StudySession.opening(it, session) } ?: session"),
            "La page d'ouverture ne suit plus la séance : le lecteur ouvrirait là où la lecture " +
                "s'était arrêtée, hors de la séance du jour.",
        )
    }

    @Test
    fun `la coquille d'etude est construite depuis la seance`() {
        val source = sourceDeLaRoute()
        assertTrue(
            source.contains("banner = StudySession.banner(etat, session, sourceEtude),"),
            "Le bandeau n'est plus résolu par le domaine : la route afficherait un texte " +
                "calculé ailleurs, ou rien du tout.",
        )
        assertTrue(
            source.contains("range = StudySession.plannedRange(etat, session),"),
            "La plage de la feuille ne vient plus de la règle du domaine : un point d'arrêt " +
                "serait calculé sur la plage demandée, et non sur celle de la séance.",
        )
        assertTrue(
            source.contains("through = StudySession.through(etat, session),"),
            "Le dernier verset validé ne vient plus de la règle du domaine.",
        )
    }

    @Test
    fun `la source est repliee par la regle du domaine`() {
        // `sourceKey` est le seul endroit qui sache replier les sources que ce client ne rend
        // pas. La replier à la main ici ferait deux tables, et une page d'étude fausse ne se
        // voit pas : elle est plausible.
        assertTrue(
            sourceDeLaRoute().contains("StudyProgressCalculator.sourceKey(source)"),
            "La source n'est plus repliée par la règle du domaine : les pages d'étude seraient " +
                "cherchées sous une clé qui n'a pas de table.",
        )
    }

    @Test
    fun `la coquille d'etude est transmise au lecteur`() {
        val source = sourceDeLaRoute()
        assertTrue(
            source.contains("study = etude,"),
            "Le lecteur ne reçoit plus la séance : aucun bandeau ne s'afficherait.",
        )
        assertTrue(
            source.contains("onValidateStudy = { through, note ->"),
            "Le lecteur ne reçoit plus de quoi valider : le bandeau serait un bouton mort.",
        )
    }

    @Test
    fun `la validation ecrit puis ferme`() {
        // L'**ordre**, et il faut les deux bornes : sans elles, un `indexOf` qui rend `-1`
        // passerait pour « écriture avant fermeture » alors qu'il ne trouve rien du tout. C'est
        // le même piège que celui de la sortie du lecteur, et la raison est la même — la portée
        // meurt avec l'écran, donc fermer d'abord annule l'écriture en vol.
        //
        // Le bloc mesuré est celui de l'**écriture**, et non le rappel entier. Voir
        // `blocDeLEcriture` : le rappel comporte une **seconde** sortie, celle de la garde, et
        // la prendre pour l'écriture a fait tomber ce contrôle sur un code juste.
        val bloc = blocDeLEcriture()
        val ecriture = bloc.indexOf("StudySession.validate(")
        val fermeture = bloc.indexOf("quitter()")
        assertTrue(ecriture >= 0, "La validation n'écrit plus la progression.")
        assertTrue(fermeture >= 0, "La validation ne referme plus le lecteur.")
        assertTrue(
            ecriture < fermeture,
            "La fermeture est lancée avant l'écriture : la portée meurt avec l'écran, donc la " +
                "progression n'est jamais enregistrée — alors que l'écran se ferme normalement, " +
                "ce qui rend le défaut invisible.",
        )
    }

    @Test
    fun `la validation passe par la sortie qui ecrit la memoire`() {
        // `onClose` est le rappel reçu de la coquille ; `quitter` écrit la position **puis**
        // referme. Valider par `onClose` oublierait où l'on s'est arrêté, et la perte ne se
        // verrait qu'en rouvrant le lecteur — donc jamais pendant l'essai qui vient de valider.
        val bloc = blocDeLEcriture()
        assertFalse(
            bloc.contains("onClose()"),
            "La validation referme par `onClose` au lieu de `quitter` : la position ne serait " +
                "pas enregistrée.",
        )
    }

    @Test
    fun `valider sans seance referme sans rien inventer`() {
        // La garde n'est pas du code mort : `StudySession.validate` exige un `Request` **non
        // nul**, et la garde est ce qui permet de l'appeler sans `!!`. Elle doit donc refermer
        // — sinon l'écran resterait ouvert sur une séance qui n'existe plus — et **ne rien
        // écrire** : écrire une progression sans séance inventerait une plage, et cette plage
        // serait plausible, donc jamais signalée.
        val rappel = rappelDeLaValidation()
        assertTrue(
            rappel.contains("if (requete == null) {"),
            "La garde a disparu : la validation ne peut plus être appelée sans séance, ou bien " +
                "elle écrit une progression sur une plage inventée.",
        )
        val garde = rappel.substringAfter("if (requete == null) {").substringBefore("} else {")
        assertTrue(
            garde.contains("quitter()"),
            "Valider sans séance ne referme plus le lecteur : l'écran resterait ouvert.",
        )
        assertFalse(
            garde.contains("StudySession.validate("),
            "Valider sans séance écrit une progression : la plage serait inventée, et " +
                "plausible — donc jamais signalée.",
        )
    }

    /** Le rappel de validation entier, garde comprise. */
    private fun rappelDeLaValidation(): String {
        val source = sourceDeLaRoute()
        val marqueur = "onValidateStudy = { through, note ->"
        assertTrue(
            source.contains(marqueur),
            "La route ne branche plus la validation : une séance ne peut plus être validée.",
        )
        val rappel = source.substringAfter(marqueur).substringBefore("\n        },")
        assertTrue(
            rappel.length < source.length,
            "La borne du rappel n'a pas mordu : le bloc lu est le fichier entier.",
        )
        return rappel
    }

    /**
     * Le **chemin d'écriture** de la validation, et lui seul.
     *
     * Deux bornes, et chacune a sa raison :
     *
     *  - `scope.launch {` isole la branche qui écrit. Le rappel en comporte une autre —
     *    `if (requete == null) { quitter() }` — qui referme **sans** écrire, et dont le
     *    `quitter()` précède l'écriture dans le fichier. Chercher dans le rappel entier trouvait
     *    celui-là et déclarait l'ordre inversé : c'est ainsi que ce contrôle est tombé la
     *    première fois, **sur un code juste**. Un contrôle de forme mesure ce qu'il trouve, pas
     *    ce qu'il vise.
     *  - `\n        },` referme le rappel. `quitter()` vit aussi ailleurs — le retour système
     *    l'appelle, et la sortie le passe en `onClose` — donc une recherche sans borne resterait
     *    verte si la validation perdait sa propre ligne.
     *
     * La borne est accompagnée de son propre contrôle : `substringAfter` **sans repli** rend la
     * chaîne entière quand le délimiteur est absent, et le bloc lu serait alors le fichier, où
     * `quitter()` se trouve plus loin.
     */
    private fun blocDeLEcriture(): String {
        val rappel = rappelDeLaValidation()
        val lancement = "scope.launch {"
        assertTrue(
            rappel.contains(lancement),
            "La validation n'écrit plus dans une portée : elle n'enregistrerait plus rien.",
        )
        val bloc = rappel.substringAfter(lancement).substringBefore("\n        },")
        assertTrue(
            bloc.length < rappel.length,
            "La borne de l'écriture n'a pas mordu : le bloc lu est le rappel entier, et l'ordre " +
                "mesuré serait celui de la garde.",
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
