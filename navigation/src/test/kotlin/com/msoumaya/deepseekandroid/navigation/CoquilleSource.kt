package com.msoumaya.deepseekandroid.navigation

import java.io.File

/**
 * Le source de la coquille, lu depuis la tâche de test.
 *
 * Deux chemins sont essayés, et non un seul : la tâche `Test` de Gradle s'exécute dans le dossier
 * **du module**, alors qu'un contrôle joué à la main depuis la racine du dépôt part de là. C'est
 * ce qui permet de rejouer un contrôle seul, sans passer par Gradle.
 *
 * La lecture est **partagée** par les contrôles qui portent sur la coquille — l'entrée du lecteur,
 * celle du tableau de bord — au lieu d'être recopiée chez chacun. Deux copies d'un résolveur de
 * chemin finiraient par diverger : celle qu'on ne relit pas chercherait un fichier qui a bougé, et
 * son contrôle échouerait pour une raison qui n'a rien à voir avec ce qu'il surveille.
 */
internal fun sourceDeLaCoquille(): String {
    val relatif = "src/main/kotlin/com/msoumaya/deepseekandroid/navigation/AppScaffold.kt"
    val candidats = listOf(File(relatif), File("navigation/$relatif"))
    val fichier = candidats.firstOrNull { it.isFile }
        ?: error(
            "AppScaffold.kt introuvable. Chemins essayés : " +
                candidats.joinToString { it.absolutePath },
        )
    return fichier.readText()
}

/**
 * Le bloc qui **suit** [marqueur], borné au prochain `composable(` de la pile.
 *
 * ## Pourquoi la borne existe
 *
 * `substringAfter` seul rend la **fin du fichier**, et non le bloc. Une assertion écrite sur ce
 * résultat peut donc être satisfaite par un bloc **voisin** : c'est arrivé, et c'est le banc de
 * falsification qui l'a dit. Le cas « le tableau de bord ne reçoit plus de quoi se fermer »
 * remplaçait le `onClose` du tableau de bord par `{}` — la source mutée, le contrôle restait
 * vert, parce que le bloc du **lecteur** qui suit porte la même ligne et se trouvait plus loin
 * dans la même chaîne. Un contrôle qui ne dit plus ce qu'il annonce est pire qu'aucun contrôle.
 *
 * ## Pourquoi elle refuse de dégrader
 *
 * Si la borne disparaissait — une pile remaniée, un `composable(` écrit autrement —, `substringBefore`
 * rendrait la chaîne entière **sans le dire**, et l'on retomberait exactement sur le défaut
 * ci-dessus. Le `check` transforme donc la dégradation en échec nommé : un contrôle qui ne peut
 * plus borner doit le dire, pas mesurer autre chose.
 */
internal fun blocApres(marqueur: String): String {
    val reste = sourceDeLaCoquille().substringAfter(marqueur)
    check(reste.contains(BORNE_DE_BLOC)) {
        "Aucune borne `composable(` après $marqueur : le bloc ne peut pas être délimité, et une " +
            "assertion non bornée serait satisfaite par le bloc suivant — c'est-à-dire par une " +
            "autre porte que celle qu'elle surveille."
    }
    return reste.substringBefore(BORNE_DE_BLOC)
}

/** Le début du `composable(` suivant, dans la pile : huit espaces d'indentation, et un saut avant. */
private const val BORNE_DE_BLOC = "\n        composable("

/**
 * Le bloc de l'accueil seul.
 *
 * Le contrôle porte sur **l'accueil**, et non sur le fichier : depuis la phase C le programme
 * ouvre lui aussi le lecteur, avec la même lambda. Chercher dans tout le fichier ferait donc
 * passer le contrôle pour la mauvaise raison — il trouverait le branchement du programme alors
 * que celui de l'accueil aurait disparu.
 */
internal fun accueilDeLaCoquille(): String = blocApres("HomeScreen(")

/**
 * Le bloc du tableau de bord seul.
 *
 * Même raison que pour l'accueil : trois écrans ouvrent une tâche par `studyRoute`, donc une
 * recherche sur le fichier entier resterait verte si le tableau de bord perdait la sienne — elle
 * trouverait celle du programme.
 */
internal fun tableauDeBordDeLaCoquille(): String = blocApres("composable(AppRoutes.REVIEW)")

/** Le bloc du programme seul. */
internal fun programmeDeLaCoquille(): String = blocApres("ProgramScreen(")
