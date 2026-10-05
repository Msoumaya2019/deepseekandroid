package com.msoumaya.deepseekandroid.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient le **branchement de la mémoire de lecture** dans la route du lecteur.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * `ReaderRoute` est une fonction `@Composable` qui prend un `AppContainer` réel : la déclencher
 * demanderait un hôte Compose **et** un conteneur complet. Monter un conteneur entier pour
 * vérifier qu'une ligne écrit l'état serait disproportionné.
 *
 * ## Ce qui disparaîtrait sans un mot
 *
 * La règle de calcul vit dans `core:domain` et y est éprouvée pour de vrai
 * (`ReaderMemoryTest`, dix-neuf cas sur le référentiel entier). Ce qui n'est éprouvable nulle
 * part ailleurs, c'est son **branchement** : que la route l'appelle, avec les bonnes valeurs, et
 * **dans le bon ordre**. Trois disparitions seraient muettes :
 *
 *  - sans l'appel, rien n'est jamais enregistré : le lecteur ne se souvient de rien, et la carte
 *    « Continuer » de l'accueil annonce éternellement la première page ;
 *  - sans le retour système, quitter au geste n'écrit rien — la perte ne se voit que chez qui
 *    quitte vite, donc jamais pendant un essai ;
 *  - dans le mauvais ordre, l'écriture est **annulée en vol** par la mort de la portée, et
 *    l'écran se ferme normalement : le symptôme est exactement celui de l'absence d'appel, ce
 *    qui rend le diagnostic très coûteux.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que le branchement est **écrit**, et que l'écriture précède la fermeture. Il ne
 * prouve pas que `ReaderMemory.close` calcule juste : c'est `ReaderMemoryTest`, dans
 * `core:domain`.
 */
class ReaderRouteMemoryTest {

    @Test
    fun `la route declare le verset d'ouverture`() {
        // Sans le paramètre, la route compilerait toujours : l'ouverture se rabattrait sur le
        // premier verset de la page, et la position dériverait d'un cran à chaque aller-retour.
        assertTrue(
            sourceDeLaRoute().contains("startVerse: Int? = null,"),
            "La route n'accepte plus de verset d'ouverture : une séance ouverte au verset 746 " +
                "se mémoriserait au premier verset de la page où l'on s'est arrêté.",
        )
    }

    @Test
    fun `la sortie ecrit la memoire de lecture`() {
        assertTrue(
            blocDeLaSortie()
                .contains("ReaderMemory.close(state, page = affichee, start = versetDeDepart)"),
            "La sortie n'enregistre plus la position : le lecteur ne se souviendrait de rien, " +
                "et l'accueil annoncerait éternellement la première page.",
        )
    }

    @Test
    fun `le verset retenu suit la seance quand il y en a une`() {
        // Le verset « d'ouverture » n'est pas décoratif : c'est lui qui décide où l'accueil
        // rouvrira. Pour une séance, c'est **son** premier verset — sans quoi une séance ouverte
        // au verset 746 se mémoriserait au premier verset de la page où l'on s'est arrêté, un
        // verset que personne n'a demandé.
        assertTrue(
            sourceDeLaRoute()
                .contains("val versetDeDepart = session?.range?.start ?: startVerse"),
            "Le verset retenu ne suit plus la séance : l'accueil rouvrirait hors de la séance.",
        )
    }

    @Test
    fun `la memoire est ecrite avant la fermeture`() {
        // C'est l'ordre qui garantit l'écriture, et non sa seule présence : la portée de la
        // route meurt avec l'écran, donc une fermeture lancée d'abord annulerait l'écriture en
        // vol — `mutate` suspend sur une écriture de fichier. Ce contrôle mesure donc l'**ordre**,
        // et il faut les deux bornes : sans elles, un `indexOf` qui rend `-1` passerait pour
        // « écriture avant fermeture » alors qu'il ne trouve rien du tout.
        val bloc = blocDeLaSortie()
        val ecriture = bloc.indexOf("ReaderMemory.close(")
        val fermeture = bloc.indexOf("onClose()")
        assertTrue(ecriture >= 0, "L'écriture de la mémoire n'est plus dans la sortie du lecteur.")
        assertTrue(fermeture >= 0, "La sortie du lecteur n'appelle plus la fermeture.")
        assertTrue(
            ecriture < fermeture,
            "La fermeture est lancée avant l'écriture : la portée meurt avec l'écran, donc " +
                "l'écriture est annulée en vol et la position n'est jamais enregistrée — alors " +
                "que l'écran se ferme normalement, ce qui rend le défaut invisible.",
        )
    }

    @Test
    fun `le retour systeme passe par la meme sortie`() {
        // Sans ce branchement, quitter au geste n'écrirait rien : le bouton et le retour
        // système emprunteraient deux chemins différents, dont un seul enregistre.
        assertTrue(
            sourceDeLaRoute().contains("BackHandler(enabled = !bookmarksOpen) { quitter() }"),
            "Le retour système ne passe plus par la sortie du lecteur : quitter au geste " +
                "n'enregistrerait aucune position.",
        )
    }

    @Test
    fun `le retour systeme est desactive sous l'ecran des signets`() {
        // L'écran des signets est posé **par-dessus** le lecteur, qui reste monté : un retour
        // doit alors refermer la liste, et non le lecteur qu'elle recouvre — sinon consulter ses
        // signets et revenir en arrière quitterait la lecture.
        assertTrue(
            sourceDeLaRoute().contains("enabled = !bookmarksOpen"),
            "Le retour système n'est plus désactivé sous l'écran des signets : un retour " +
                "refermerait le lecteur au lieu de la liste.",
        )
    }

    @Test
    fun `le lecteur recoit la sortie qui ecrit`() {
        // C'est le seul lien entre l'écran et l'écriture : le bouton de fermeture du lecteur
        // appelle ce que la route lui donne, et non la fermeture nue.
        assertTrue(
            sourceDeLaRoute().contains("onClose = quitter,"),
            "Le lecteur n'appelle plus la sortie qui écrit : son bouton de fermeture " +
                "n'enregistrerait plus la position.",
        )
    }

    @Test
    fun `la page d'ouverture n'est plus la premiere page`() {
        assertTrue(
            sourceDeLaRoute().contains("mutableIntStateOf(ReaderMemory.FIRST_PAGE)"),
            "La page d'ouverture n'est plus tenue par la règle du domaine : le lecteur " +
                "repartirait de la première page à chaque ouverture.",
        )
    }

    @Test
    fun `l'adoption de la page memoiree cesse quand la page est tournee`() {
        // Sans ce drapeau, l'état du compte arriverait après un premier geste et ramènerait la
        // personne à la page mémorisée — un saut en arrière qu'elle n'a pas demandé.
        assertTrue(
            sourceDeLaRoute().contains("pageTournee = true"),
            "Rien ne marque plus qu'une page a été tournée : l'adoption de la page mémorisée " +
                "déplacerait la personne après qu'elle a choisi sa page.",
        )
    }

    @Test
    fun `le source lu est bien celui de la route`() {
        // Garde-fou sur le fichier lui-même : si le chemin résolu désignait autre chose, les
        // contrôles ci-dessus chercheraient leurs chaînes dans le mauvais document — et
        // échoueraient, ce qui est le bon comportement, mais pour une raison trompeuse.
        assertTrue(
            sourceDeLaRoute().contains("fun ReaderRoute("),
            "Le fichier lu ne déclare pas `fun ReaderRoute(` : le chemin résolu ne désigne pas " +
                "la route du lecteur.",
        )
    }

    /**
     * La sortie du lecteur, et elle seule.
     *
     * **Bornée** : `onClose()` vit aussi dans la porte, sous la forme `onBack = onClose`, et
     * `ReaderMemory.close` pourrait être appelé ailleurs demain. Une recherche sans borne
     * resterait donc verte si la sortie perdait sa propre ligne, et le contrôle de l'ordre ne
     * mesurerait plus rien.
     *
     * La borne est accompagnée de son propre contrôle : `substringAfter` **sans repli** rend la
     * chaîne entière quand le délimiteur est absent — une borne muette rendrait tout le fichier,
     * et `onClose()` y serait trouvé plus loin, dans la porte.
     */
    private fun blocDeLaSortie(): String {
        val source = sourceDeLaRoute()
        val marqueur = "val quitter: () -> Unit = {"
        assertTrue(
            source.contains(marqueur),
            "La route ne déclare plus la sortie du lecteur : la position ne serait plus " +
                "enregistrée en quittant.",
        )
        val bloc = source.substringAfter(marqueur).substringBefore("\n    }")
        assertTrue(
            bloc.length < source.length,
            "La borne de la sortie n'a pas mordu : le bloc lu est le fichier entier, et " +
                "l'ordre mesuré ne serait pas celui de la sortie.",
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
