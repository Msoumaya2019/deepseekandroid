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

/**
 * La ligne qui sert la route de l'objectif.
 *
 * Elle est **la dernière** du `NavHost` : il n'y a pas de `composable(` après elle, donc la borne
 * de [blocApres] n'existe pas et son `check` refuserait — à raison, puisque rien ne délimiterait
 * le bloc. La route tient sur une seule ligne, et c'est cette ligne qu'on lit.
 *
 * `check` plutôt que `single` : une route disparue et une route dupliquée sont deux défauts
 * différents, et le message doit dire lequel.
 */
internal fun routeGoalDeLaCoquille(): String {
    val lignes = sourceDeLaCoquille().lines().filter { it.contains("composable(AppRoutes.GOAL)") }
    check(lignes.size == 1) {
        "La route de l'objectif doit être servie exactement une fois ; elle l'est ${lignes.size} fois."
    }
    return lignes.single()
}

/**
 * Le bloc de l'écran « Coran » seul, **commentaires retirés**.
 *
 * Le retrait n'est pas une coquetterie : le bloc de cette route est le plus commenté de la
 * coquille, et un contrôle qui cherche `QuranRoute(` ou `AppRoutes.GOAL` dans le texte brut
 * trouverait sa propre documentation. Il lirait alors ce qu'on **attend** de la route au lieu de
 * ce qu'elle **fait** — et resterait vert sur une route débranchée. C'est le même piège qu'un
 * bloc YAML analysé sans en retirer les commentaires.
 *
 * La borne de [blocApres] s'applique ici : le `composable(` du programme suit celui du Coran, donc
 * le bloc est délimité, et une assertion ne peut pas être satisfaite par la porte voisine.
 */
internal fun coranDeLaCoquille(): String =
    sansCommentaires(blocApres("composable(AppDestination.QURAN.route)"))

/**
 * Le bloc de l'écran « Progrès » seul, **commentaires retirés**.
 *
 * Le retrait est nécessaire ici pour la même raison qu'au Coran : le bloc porte le commentaire qui
 * explique pourquoi l'écran est en lecture seule, et ce commentaire nomme `AppRoutes.GOAL`. Un
 * contrôle écrit sur le texte brut y trouverait sa propre documentation — il vérifierait ce qu'on
 * attend de la route, pas ce qu'elle fait.
 *
 * La borne de [blocApres] s'applique : le `composable(` des amis suit celui du Progrès, donc le
 * bloc est délimité et une assertion ne peut pas être satisfaite par la porte voisine.
 */
internal fun progresDeLaCoquille(): String =
    sansCommentaires(blocApres("composable(AppDestination.PROGRESS.route)"))

/**
 * Le bloc de l'écran « Amis » seul, **commentaires retirés**.
 *
 * Le retrait sert ici pour la même raison qu'au Coran et au Progrès : ce bloc peut porter un
 * commentaire qui nomme l'écran des amis, et un contrôle écrit sur le texte brut y trouverait sa
 * propre documentation — il vérifierait ce qu'on **attend** de la route au lieu de ce qu'elle
 * **fait**.
 *
 * La borne de [blocApres] s'applique : le `composable(` du tableau de bord des révisions suit
 * celui des amis, donc le bloc est délimité et une assertion ne peut pas être satisfaite par la
 * porte voisine.
 */
internal fun amisDeLaCoquille(): String =
    sansCommentaires(blocApres("composable(AppDestination.FRIENDS.route)"))

/**
 * Le bloc de l'écran « Quiz » seul, **commentaires retirés**.
 *
 * Le retrait sert ici plus qu'ailleurs : le bloc explique en commentaire pourquoi la route est
 * servie par son **motif** et non par la route nue, et ce commentaire écrit noir sur blanc
 * `takeIf { it.isNotEmpty() }` — l'expression même que le contrôle surveille. Un contrôle écrit
 * sur le texte brut y trouverait sa propre documentation, et resterait vert sur une route
 * débranchée. C'est le piège du bloc du Coran, en plus rapproché.
 *
 * La borne de [blocApres] s'applique : le `composable(` du profil suit celui du Quiz, donc le bloc
 * est délimité et une assertion ne peut pas être satisfaite par la porte voisine.
 */
internal fun quizDeLaCoquille(): String =
    sansCommentaires(blocApres("route = AppRoutes.QUIZ_PATTERN,"))

/** Le texte privé de ses lignes de commentaire — celles qui commencent par `//`. */
internal fun sansCommentaires(texte: String): String =
    texte.lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
