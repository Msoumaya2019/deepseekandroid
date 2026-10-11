package com.msoumaya.deepseekandroid.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient le **branchement de l'enregistreur** dans la route du lecteur.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * Les deux conditions sont réunies, et il faut les deux.
 *
 *  1. **Le comportement est hors de portée.** `ReaderRoute` est une fonction `@Composable` qui
 *     prend un `AppContainer` réel : la déclencher demanderait un hôte Compose **et** un conteneur
 *     complet — disque, compte, microphone.
 *  2. **Sa disparition serait silencieuse.** Les règles sont éprouvées ailleurs — la machine à
 *     phases dans `core:domain/RecitationRecorderTest`, le rendu dans
 *     `feature:reader/RecitationRecorderRendererTest`, la surface dans
 *     `feature:reader/RecitationRecorderBarWiringTest`. Entre les trois, **personne ne regarde la
 *     route** : si elle cessait de composer la capacité, ou si elle la composait à chaque
 *     recomposition, les trois suites resteraient vertes et le seul symptôme serait un panneau
 *     d'enregistrement qui n'existe pas, ou un lecteur qui se recompose à chaque image.
 *
 * ## Les décisions que ce contrôle vise
 *
 *  - **la capacité est composée ici, et une seule fois** — ses champs sont des fonctions, donc
 *    deux constructions successives ne sont jamais égales, et en refaire une à chaque
 *    recomposition ferait recomposer le lecteur à chaque image ;
 *  - **`null` quand une pièce manque** — pas de microphone, ou pas de lecteur de récitation : les
 *    deux portes du panneau sont alors **retirées** plutôt que d'ouvrir sur du vide ;
 *  - **le compte est lu au moment du geste** — il peut s'ouvrir ou se fermer sans que le lecteur
 *    soit reconstruit, et une valeur figée à la composition enregistrerait une récitation sous un
 *    compte qui n'est plus le sien ;
 *  - **le partage est branché sur la sortie** — l'original quitte le lecteur pour la liste des
 *    récitations, où le choix d'ami s'ouvre sur la ligne attendue. La route porte désormais cette
 *    sortie : l'identifiant est signalé, puis `quitter` l'emporte, et la coquille ouvre la liste
 *    sur lui. Le geste « Partager » **existe** donc, et c'est `canShare` qui continue de décider
 *    de l'afficher.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que la route **branche** la capacité. Il ne prouve pas qu'un enregistrement aboutit :
 * cela demande un microphone, un compte et un appareil.
 */
class ReaderRouteRecorderTest {

    @Test
    fun `la route compose la capacite d'enregistrement`() {
        val bloc = blocDeLaCapacite()
        assertTrue(
            bloc.contains("RecitationRecorderCapability("),
            "La route ne compose plus la capacité : le lecteur ne recevrait rien, et ses deux " +
                "portes d'enregistrement disparaîtraient sans que rien ne le dise.",
        )
        assertTrue(
            bloc.contains("recorder = enregistreur,"),
            "La capacité ne reçoit plus l'enregistreur du conteneur : la barre n'aurait pas de " +
                "microphone, et aucun geste ne pourrait aboutir.",
        )
        assertTrue(
            bloc.contains("player = lecteurDeRecitation,"),
            "La capacité ne reçoit plus le lecteur de récitation : la capture ne pourrait plus " +
                "couper l'écoute avant d'ouvrir le microphone, et la réécoute serait muette.",
        )
    }

    @Test
    fun `la capacite manque quand une piece manque`() {
        assertTrue(
            blocDeLaCapacite().contains("if (enregistreur == null || lecteurDeRecitation == null) {"),
            "La capacité n'est plus retirée quand une pièce manque : le panneau s'ouvrirait sur " +
                "des gestes qui ne peuvent pas aboutir.",
        )
    }

    @Test
    fun `la capacite est composee une seule fois`() {
        assertTrue(
            blocDeLaCapacite().contains("remember(container, enregistreur, lecteurDeRecitation) {"),
            "La capacité n'est plus mémorisée sur ses trois dépendances : ses champs étant des " +
                "fonctions, deux constructions successives ne sont jamais égales, et le lecteur " +
                "se recomposerait à chaque image.",
        )
    }

    @Test
    fun `le compte est lu au moment du geste`() {
        val source = sourceDeLaRoute()
        assertTrue(
            source.contains("val proprietaireCourant = rememberUpdatedState(userState?.userId)"),
            "Le compte n'est plus tenu à jour : une valeur figée à la composition " +
                "enregistrerait une récitation sous un compte qui n'est plus le sien.",
        )
        assertTrue(
            blocDeLaCapacite().contains("owner = { proprietaireCourant.value },"),
            "La capacité ne lit plus le compte au moment du geste : elle le capturerait à la " +
                "composition, et le domaine refuserait alors un enregistrement que la personne " +
                "croit possible — ou l'inscrirait sous aucun compte.",
        )
    }

    @Test
    fun `le partage est branche sur la sortie du lecteur`() {
        val bloc = blocDeLaCapacite()
        assertTrue(
            bloc.contains("share = { recitation -> partagerCourant.value(recitation.id) },"),
            "La route ne branche plus le partage : le geste « Partager » **disparaîtrait** de la " +
                "barre au lieu de mener à la liste des récitations.",
        )
        assertFalse(
            bloc.contains("share = null,"),
            "La route retire de nouveau le partage alors qu'elle porte la sortie qui le rend " +
                "possible : le geste disparaîtrait sans que rien ne le dise.",
        )
    }

    /**
     * Le partage est **relu à chaque image**, comme le compte.
     *
     * `partager` capture la page affichée — c'est `quitter` qui l'écrit —, donc une valeur figée à
     * la construction de la capacité enregistrerait une position périmée : la personne
     * reprendrait sa lecture un verset plus haut, et rien ne le dirait.
     */
    @Test
    fun `le partage est relu a chaque image`() {
        assertTrue(
            sourceDeLaRoute().contains("val partagerCourant = rememberUpdatedState(partager)"),
            "Le partage n'est plus tenu à jour : il capturerait la page affichée au moment de la " +
                "construction de la capacité, et enregistrerait une position périmée.",
        )
    }

    /**
     * Le partage **emprunte la sortie ordinaire**, au lieu d'écrire la mémoire lui-même.
     *
     * Deux sorties qui écriraient chacune la leur finiraient par diverger, et c'est la copie qu'on
     * ne relit pas qui resterait. L'ordre compte aussi : l'identifiant est signalé **avant** de
     * sortir, parce que c'est la sortie qui l'emporte.
     */
    @Test
    fun `le partage signale avant de sortir`() {
        val source = sourceDeLaRoute()
        val marqueur = "    val partager: (String) -> Unit = { id ->"
        assertTrue(
            source.contains(marqueur),
            "La route ne déclare plus la sortie de partage : le lecteur n'aurait aucun moyen de " +
                "signaler la récitation à partager.",
        )
        val reste = source.substringAfter(marqueur)
        val bloc = reste.substringBefore("\n    }")
        assertTrue(
            bloc.length < reste.length,
            "La borne de la sortie de partage n'a pas mordu : le bloc va jusqu'à la fin du " +
                "fichier, et le contrôle lirait une occurrence étrangère.",
        )
        assertTrue(
            bloc.contains("onShareRecitation(id)"),
            "Le partage n'est plus signalé : la coquille n'aurait aucun identifiant à ouvrir, et " +
                "la liste s'ouvrirait sur elle-même.",
        )
        assertTrue(
            bloc.contains("quitter()"),
            "Le partage n'emprunte plus la sortie ordinaire : la position ne serait pas écrite, " +
                "et c'est exactement ce qu'une seconde écriture finirait par manquer.",
        )
    }

    @Test
    fun `le lecteur recoit la capacite`() {
        assertTrue(
            sourceDeLaRoute().contains("recorder = capaciteDEnregistrement,"),
            "Le lecteur ne reçoit plus la capacité : les deux portes d'enregistrement " +
                "disparaîtraient — la barre de la coquille et « Ma voix » dans une révision.",
        )
    }

    @Test
    fun `le source lu est bien celui de la route`() {
        assertTrue(
            sourceDeLaRoute().contains("fun ReaderRoute("),
            "Le fichier lu ne déclare pas `fun ReaderRoute(` : le chemin résolu ne désigne pas la " +
                "route du lecteur.",
        )
    }

    /**
     * Le bloc qui compose la capacité, et lui seul.
     *
     * **Borné** : `remember(` est employé partout dans ce fichier — la page, les pages du moushaf,
     * le verset de départ —, et `container.` l'est davantage encore. Une recherche sans borne
     * resterait verte si ce bloc perdait sa propre mémorisation.
     *
     * La borne est accompagnée de son contrôle : `substringAfter` et `substringBefore` sans repli
     * rendent la chaîne entière quand le délimiteur est absent, et un contrôle qui lirait alors
     * tout le fichier serait vert pour la mauvaise raison.
     */
    private fun blocDeLaCapacite(): String {
        val source = sourceDeLaRoute()
        val marqueur = "    val capaciteDEnregistrement: RecitationRecorderCapability? ="
        assertTrue(
            source.contains(marqueur),
            "La route ne déclare plus de capacité d'enregistrement : le lecteur ne peut plus " +
                "recevoir de quoi enregistrer.",
        )
        val reste = source.substringAfter(marqueur)
        val bloc = reste.substringBefore("\n\n    ReaderScreen(")
        // La comparaison porte sur le **reste**, et non sur le fichier : `substringAfter` a déjà
        // raccourci la chaîne, donc comparer le bloc à `source.length` serait vrai même si la
        // borne manquait — un contrôle qui ne peut pas échouer.
        assertTrue(
            bloc.length < reste.length,
            "La borne du bloc de capacité est absente : le bloc lu va jusqu'à la fin du fichier, " +
                "donc toute recherche y serait vraie.",
        )
        return bloc
    }

    /**
     * Le source de la route du lecteur.
     *
     * Deux chemins sont essayés, et non un seul : la tâche `Test` de Gradle s'exécute dans le
     * dossier **du module**, alors qu'un contrôle joué à la main depuis la racine du dépôt part de
     * là.
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
