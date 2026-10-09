package com.msoumaya.deepseekandroid.core.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Éprouve le **branchement** du conteneur, en lisant son source.
 *
 * ## Pourquoi lire le source, alors que le reste est éprouvé par le comportement
 *
 * Le conteneur ne se construit qu'avec un `Context` Android : dans une épreuve JVM, il n'y a pas
 * de `Context`, et rien de ce qu'il fait au démarrage ne peut être exécuté. Or deux choses y sont
 * invisibles à l'exécution :
 *
 *  - **la relecture des réglages d'écoute au démarrage.** Sans elle, le lecteur part des valeurs
 *    par défaut et le premier réglage écrase le choix enregistré, sans que rien ne le dise ;
 *  - **l'emplacement du document.** Un fichier rangé dans un dossier de compte ferait changer de
 *    réglages en changeant de compte, alors que la façon d'écouter tient à l'appareil ;
 *  - **la reprise du réseau.** L'original pousse la file des modifications d'état quand le réseau
 *    revient, puis son observateur de Quiz relit. Un `onRestore = {}` compilerait parfaitement :
 *    le bandeau s'afficherait, annoncerait le retour, et la modification faite hors ligne
 *    resterait dans la file. C'est le défaut le plus silencieux de ce dépôt, parce qu'il ne se
 *    produit que sur une vraie coupure — donc presque jamais pendant qu'on écrit le code.
 *
 * Ce sont des branchements, pas des calculs : le comportement du dépôt est éprouvé par
 * [com.msoumaya.deepseekandroid.core.data.repository.AudioSettingsRepositoryTest], et c'est ici
 * qu'on vérifie qu'il est atteint.
 *
 * ## Ce que ce contrôle ne dit pas
 *
 * Il ne dit **pas** que la relecture aboutit, ni que le document écrit est bien formé — seulement
 * que l'appel est écrit. Il ne dit pas non plus qu'aucun autre endroit ne range des réglages
 * d'écoute ailleurs : il n'en existe pas aujourd'hui, et un second document ne serait pas vu ici.
 * Il ne dit pas, enfin, que la **lecture** du réseau est juste : elle demande un appareil, et
 * `ConnectivityObserver` documente ce qui n'est pas éprouvable sans lui.
 */
class AppContainerWiringTest {

    @Test
    fun `le conteneur relit les reglages d'ecoute au demarrage`() {
        val source = sourceDuConteneur()

        assertTrue(
            source.contains("audioSettings.prime()"),
            "le conteneur doit lancer la relecture des reglages d'ecoute : sans elle, le lecteur " +
                "part des valeurs par defaut et le premier reglage ecrase le choix enregistre",
        )
    }

    @Test
    fun `le document des reglages est unique et hors des comptes`() {
        val source = sourceDuConteneur()

        assertTrue(
            source.contains("""File(root, "audio.json")"""),
            "le document doit etre unique et pose a la racine de l'etat, et non dans le dossier " +
                "d'un compte : la facon d'ecouter tient a l'appareil",
        )
    }

    @Test
    fun `la reprise du reseau pousse l'etat et relit le Quiz`() {
        // `onRestore = {}` compilerait : le bandeau s'afficherait, annoncerait le retour, et la
        // modification faite hors ligne resterait dans la file pour toujours.
        val bloc = blocDeLaReprise()

        assertTrue(
            bloc.contains("userState.sync()"),
            "La reprise du reseau ne pousse plus l'etat : une modification faite hors ligne " +
                "resterait dans la file sans que rien ne le dise.",
        )
        assertTrue(
            bloc.contains("quiz.refresh()"),
            "La reprise du reseau ne relit plus le Quiz : la progression affichee resterait " +
                "celle du cache jusqu'a la prochaine ouverture de l'ecran.",
        )
    }

    @Test
    fun `la reprise pousse l'etat avant de relire le Quiz`() {
        // L'ordre est une regle, et non un hasard de lecture : relire le Quiz d'abord montrerait
        // un etat serveur qui ne contient pas encore ce que l'appareil vient d'ecrire hors ligne.
        val bloc = blocDeLaReprise()
        val etat = bloc.indexOf("userState.sync()")
        val quiz = bloc.indexOf("quiz.refresh()")

        assertTrue(etat >= 0 && quiz >= 0, "Les deux appels de la reprise doivent etre presents.")
        assertTrue(
            etat < quiz,
            "La reprise relit le Quiz **avant** de pousser l'etat : la progression affichee " +
                "serait calculee sur un etat que le serveur n'a pas encore recu.",
        )
    }

    /**
     * Le bloc de `onRestore`, borné à la déclaration de l'observateur.
     *
     * ## Pourquoi la borne
     *
     * `userState.sync()` et `quiz.refresh()` peuvent apparaître ailleurs dans le conteneur — et
     * `sync` est le nom d'une méthode assez courant pour qu'un appel voisin soit ajouté un jour.
     * Une assertion écrite sur le fichier entier serait alors satisfaite par cet autre appel, et
     * resterait verte sur un `onRestore` vidé.
     *
     * ## Pourquoi elle refuse de dégrader
     *
     * Si la déclaration changeait de forme — l'observateur construit par une fabrique, par
     * exemple —, la fenêtre ne contiendrait plus `onRestore`, et les deux assertions ci-dessus
     * mesureraient un bloc qui n'est pas celui de la reprise. Le `check` transforme cette
     * dégradation en échec nommé.
     */
    private fun blocDeLaReprise(): String {
        val bloc = sourceDuConteneur().substringAfter("val connectivity").take(FENETRE_REPRISE)
        check(bloc.contains("onRestore")) {
            "Le bloc de `onRestore` est introuvable apres `val connectivity` : la fenetre de " +
                "lecture ne delimite plus l'observateur, et une assertion ecrite dessus " +
                "mesurerait autre chose que la reprise."
        }
        return bloc
    }

    /**
     * Le source du conteneur, tel qu'il est écrit.
     *
     * La tâche de test s'exécute dans le dossier du module ; lancée autrement, elle le fait
     * depuis la racine du dépôt. On regarde donc les deux emplacements, et l'échec nomme ceux
     * qui ont été cherchés : un chemin faux qui ne dit rien ferait passer ce contrôle pour une
     * panne de l'outillage.
     */
    private fun sourceDuConteneur(): String {
        val relatif = "src/main/kotlin/com/msoumaya/deepseekandroid/core/data/AppContainer.kt"
        val candidats = listOf(File(relatif), File("core/data/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error("AppContainer.kt introuvable. Cherche dans : " + candidats.joinToString { it.absolutePath })
        return fichier.readText()
    }

    private companion object {
        /**
         * La fenêtre de lecture du bloc de reprise, en caractères.
         *
         * Assez large pour contenir la déclaration de l'observateur et son `onRestore` — mesuré
         * à moins de 250 caractères —, et assez courte pour ne pas atteindre un appel voisin du
         * conteneur. C'est la borne qui fait que les assertions portent sur **ce** bloc.
         */
        const val FENETRE_REPRISE = 400
    }
}
