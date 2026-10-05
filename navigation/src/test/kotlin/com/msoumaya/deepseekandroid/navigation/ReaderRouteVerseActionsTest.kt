package com.msoumaya.deepseekandroid.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tient l'**écriture** du marqueur de difficulté dans la route du lecteur.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * Les deux conditions sont réunies, et il faut les deux.
 *
 *  1. **Le comportement est hors de portée.** `ReaderRoute` est une fonction `@Composable` qui
 *     prend un `AppContainer` réel : la déclencher demanderait un hôte Compose **et** un
 *     conteneur complet — stockage, session, dépôts. Monter un conteneur entier pour vérifier
 *     qu'une ligne appelle `toggleDifficulty` serait disproportionné.
 *  2. **Sa disparition serait silencieuse.** Si la route cessait d'écrire le marqueur, rien ne le
 *     dirait : `VerseActionsWiringTest` resterait vert (il ne regarde que le lecteur), la
 *     compilation passe, et le seul symptôme est un bouton qui ne marque rien. Pire, il pourrait
 *     **paraître** marcher : le panneau se referme, l'écran reste identique, et personne ne
 *     verrait que le verset n'a pas été enregistré.
 *
 * ## Le point que ce contrôle vise
 *
 * La route fournit **deux** ensembles, et ils ne disent pas la même chose : `difficultIds` compte
 * le marqueur de l'élève **et** celui du professeur — c'est la bonne règle pour teinter une page —
 * tandis que le mot du bouton suit le marqueur de l'élève **seul**, parce que la bascule ne touche
 * jamais à celui du professeur. Fournir le premier là où le second est attendu ferait annoncer
 * « Retirer des révisions prioritaires » sur un verset dont l'appui **ajoute** un marqueur.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que la route **écrit** par la règle du domaine, sur l'état observé, et qu'elle
 * distingue les deux ensembles. Il ne prouve pas que `toggleDifficulty` est juste : c'est
 * `ReviewTest`, dans `core:domain`.
 */
class ReaderRouteVerseActionsTest {

    @Test
    fun `la route bascule le marqueur par la regle du domaine`() {
        assertTrue(
            sourceDeLaRoute().contains("Review.toggleDifficulty(state, verseId)"),
            "La route ne bascule plus le marqueur par la règle du domaine : il serait écrit " +
                "autrement, ou pas du tout, et rien ne le dirait.",
        )
    }

    @Test
    fun `la route distingue les versets marques par l'eleve`() {
        // `userMarkedIds` et non `difficultIds` : les deux ensembles diffèrent dès qu'un
        // professeur marque un verset, et le libellé du bouton suit le premier.
        assertTrue(
            sourceDeLaRoute().contains("VerseActionsText.userMarkedIds(it)"),
            "La route ne calcule plus le sous-ensemble marqué par l'élève : le libellé du " +
                "bouton suivrait tous les versets difficiles, et mentirait sur ceux que le " +
                "professeur a marqués.",
        )
    }

    @Test
    fun `la route transmet le sous-ensemble au lecteur`() {
        assertTrue(
            sourceDeLaRoute().contains("userMarkedIds = userMarkedIds,"),
            "Le sous-ensemble est calculé mais plus transmis : le lecteur retomberait sur son " +
                "défaut vide, et **tous** les versets proposeraient de les marquer.",
        )
    }

    @Test
    fun `l'ecriture passe par le magasin de l'etat`() {
        // La recherche est bornée à ce qui suit `onMarkDifficulty = {` : `container.userState
        // .mutate` sert aussi au signet, et la chaîne nue passerait donc même si le marquage
        // cessait de l'utiliser — un contrôle qui ne mesurerait rien.
        assertTrue(
            blocDuMarquage().contains("container.userState.mutate { state ->"),
            "Le marqueur n'est plus écrit dans le magasin de l'état : il serait perdu au premier " +
                "redémarrage, ou écrit dans une copie que personne ne relit.",
        )
    }

    @Test
    fun `un echec d'ecriture n'emporte pas le lecteur`() {
        // Même raison : le signet a son propre `runCatching`.
        assertTrue(
            blocDuMarquage().contains("runCatching {"),
            "L'écriture n'est plus protégée : un disque plein ferait tomber le lecteur, alors " +
                "que la lecture, elle, n'a besoin de rien.",
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

    /**
     * Ce qui suit `onMarkDifficulty = {` : la seule écriture du panneau des actions.
     *
     * Borner la recherche à ce bloc est ce qui rend les deux contrôles ci-dessus falsifiables :
     * `container.userState.mutate` et `runCatching` servent aussi au signet, plus haut dans le
     * fichier, et la chaîne nue passerait même si le marquage perdait les siens.
     */
    private fun blocDuMarquage(): String =
        sourceDeLaRoute().substringAfter("onMarkDifficulty = {")
}
