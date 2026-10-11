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
 * de `Context`, et rien de ce qu'il fait au démarrage ne peut être exécuté. Or quatre choses y sont
 * invisibles à l'exécution :
 *
 *  - **la relecture des réglages d'écoute au démarrage.** Sans elle, le lecteur part des valeurs
 *    par défaut et le premier réglage écrase le choix enregistré, sans que rien ne le dise ;
 *  - **l'emplacement du document.** Un fichier rangé dans un dossier de compte ferait changer de
 *    réglages en changeant de compte, alors que la façon d'écouter tient à l'appareil ;
 *  - **la reprise du réseau.** L'original pousse la file des modifications d'état quand le réseau
 *    revient, puis son observateur de Quiz relit, et son observateur de signalements vide la boîte
 *    d'envoi. Un `onRestore = {}` compilerait parfaitement : le bandeau s'afficherait, annoncerait
 *    le retour, et la modification faite hors ligne resterait dans la file. C'est le défaut le plus
 *    silencieux de ce dépôt, parce qu'il ne se produit que sur une vraie coupure — donc presque
 *    jamais pendant qu'on écrit le code ;
 *  - **la lecture du réseau que reçoit la boîte d'envoi.** Elle est une **référence en avant** vers
 *    l'observateur, déclaré plus bas : une lambda qui lirait autre chose — une seconde lecture
 *    `NetInfo` —, ou un `horsLigne = { false }` par défaut, compilerait aussi. La conséquence
 *    serait une tentative d'envoi à chaque geste hors connexion, et une phrase « envoyé » sur un
 *    signalement qui n'a pas bougé.
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

    @Test
    fun `la reprise du reseau vide la boite des signalements`() {
        // Un `onRestore` qui pousse l'etat et relit le Quiz mais oublie la boite laisserait un
        // signalement ecrit hors connexion dans la file jusqu'a la prochaine ouverture — et rien
        // ne le dirait, puisque la boite ne s'affiche nulle part : elle n'est pas une liste qu'on
        // relit, c'est une boite d'envoi.
        val bloc = blocDeLaReprise()

        assertTrue(
            bloc.contains("problemReports.flush()"),
            "La reprise du reseau ne vide plus la boite des signalements : un signalement ecrit " +
                "hors connexion resterait dans la file sans que rien ne le signale.",
        )
    }

    @Test
    fun `le conteneur donne a la boite la lecture du reseau du bandeau`() {
        // La boite ne doit pas porter une **seconde** lecture du reseau : deux lectures de la meme
        // verite finiraient par diverger, et le bandeau pourrait annoncer une coupure pendant que
        // la file essaie quand meme. Le defaut `horsLigne = { false }` compilerait aussi, et
        // ferait tenter un envoi a chaque geste hors connexion.
        val source = sourceDuConteneur()

        assertTrue(
            source.contains("connectivity.state.value.horsLigne"),
            "La boite des signalements ne lit plus l'etat du reseau publie par l'observateur : " +
                "elle porte une seconde lecture de la meme verite, ou aucune.",
        )
        assertTrue(
            source.contains("ProblemReportStore(root)"),
            "La boite des signalements doit vivre sous la racine de l'etat, hors de tout dossier " +
                "de compte : c'est le document qui porte le compte de chaque entree.",
        )
    }

    // ---------------------------------------------------------------- le retour au premier plan

    @Test
    fun `le conteneur branche le retour au premier plan sur les cinq files`() {
        // `onReprise = {}` compilerait : l'observateur s'inscrirait, compterait les activites, et
        // ne reveillerait rien. C'est le defaut le plus silencieux de la tranche — rien ne se
        // plaint, et une recitation enregistree hors connexion attend le minuteur.
        //
        // **Le retour au premier plan est le seul moment qui relit les contenus du jour.** C'est
        // l'original qui le dit : son `TodayContents` recharge a l'ouverture, au retour au premier
        // plan et au changement de date, mais **pas** au retour du reseau. La reprise du reseau,
        // elle, ne les appelle donc pas — et c'est une decision, pas un oubli.
        val bloc = blocDeLaReprisePremierPlan()

        for (appel in listOf(
            "userState.sync()",
            "recitations.syncInBackground()",
            "quiz.refresh()",
            "problemReports.flush()",
            "dailyContents.refresh()",
        )) {
            assertTrue(
                bloc.contains(appel),
                "Le retour au premier plan n'appelle plus `$appel` : la file correspondante " +
                    "attendrait la prochaine occasion, et rien ne le dirait.",
            )
        }
    }

    @Test
    fun `le retour au premier plan pousse l'etat avant de relire le Quiz`() {
        // Meme regle que pour la reprise du reseau : relire le Quiz d'abord montrerait une
        // progression que le serveur n'a pas encore recue.
        val bloc = blocDeLaReprisePremierPlan()
        val etat = bloc.indexOf("userState.sync()")
        val quiz = bloc.indexOf("quiz.refresh()")

        assertTrue(etat >= 0 && quiz >= 0, "Les deux appels de la reprise doivent etre presents.")
        assertTrue(
            etat < quiz,
            "Le retour au premier plan relit le Quiz **avant** de pousser l'etat : la progression " +
                "affichee serait calculee sur un etat que le serveur n'a pas encore recu.",
        )
    }

    @Test
    fun `l'application demarre l'observation du premier plan`() {
        // Sans cet appel, l'observateur n'est jamais inscrit : le compteur reste a zero, aucune
        // reprise n'a lieu, et **tous les autres controles de ce fichier restent verts** — ils
        // lisent le conteneur, pas l'application.
        val application = sourceDeLApplication()

        assertTrue(
            application.contains("container.premierPlan.start()"),
            "L'application ne demarre pas l'observation du premier plan : le declencheur existe " +
                "et n'est jamais branche.",
        )
    }

    @Test
    fun `les deux observateurs absorbent l'echec de leur reprise`() {
        // Le defaut que ce controle tient, et qui a ete **trouve** en ecrivant cette tranche :
        // `onRestore` et `onReprise` touchent des files qui **propagent** un echec non tolere —
        // `QuizRepository.refresh` par son `compte { }`, et `ProblemReportRepository.flush` par son
        // `push`. Sans capture, l'exception remonte a une portee sans `CoroutineExceptionHandler`,
        // et l'application s'arrete au moment precis ou la personne revient.
        for ((observateur, rappel) in listOf(
            "ConnectivityObserver" to "onRestore",
            "ForegroundObserver" to "onReprise",
        )) {
            val bloc = blocDuRappelDeFond(observateur, rappel)

            assertTrue(
                bloc.contains("catch (cancelled: CancellationException)"),
                "`$observateur` ne relance plus l'annulation : avaler `CancellationException` " +
                    "casserait l'annulation cooperative, et la portee croirait le travail fini.",
            )
            assertTrue(
                bloc.contains("catch (_: Exception)"),
                "`$observateur` ne capture plus l'echec de son rappel : une panne au moment du " +
                    "retour remonterait a une portee sans gestionnaire, et l'application " +
                    "s'arreterait au moment precis ou la personne revient.",
            )
        }
    }

    /**
     * Le bloc de `onReprise`, borne a la declaration de l'observateur.
     *
     * Meme raison que [blocDeLaReprise] : `userState.sync()` et `quiz.refresh()` figurent aussi
     * dans la reprise du reseau, quelques lignes plus haut. Une assertion sur le fichier entier
     * serait satisfaite par **cet autre** bloc, et resterait verte sur un `onReprise` vide.
     */
    private fun blocDeLaReprisePremierPlan(): String {
        val bloc = sourceDuConteneur().substringAfter("val premierPlan").take(FENETRE_REPRISE)
        check(bloc.contains("onReprise")) {
            "Le bloc de `onReprise` est introuvable apres `val premierPlan` : la fenetre de " +
                "lecture ne delimite plus l'observateur, et une assertion ecrite dessus " +
                "mesurerait autre chose que la reprise."
        }
        return bloc
    }

    /**
     * Le corps du `scope.launch` d'un observateur de fond, borne a ce lancement.
     *
     * Le controle d'absorption porte sur une capture ecrite **dans** ce bloc ; une assertion sur le
     * fichier entier serait satisfaite par une capture ecrite ailleurs, ou par la **prose** d'un
     * commentaire qui nommerait `catch (_: Exception)`. La garde refuse de mesurer tout le fichier
     * quand la borne n'a pas mordu, et refuse un bloc qui ne contient plus le rappel attendu — les
     * deux facons dont cette assertion pourrait devenir vraie pour la mauvaise raison.
     */
    private fun blocDuRappelDeFond(observateur: String, rappel: String): String {
        val source = sourceDeLObservateur(observateur)
        val apres = source.substringAfter("scope.launch {")
        check(apres.length < source.length) {
            "Aucun `scope.launch` dans $observateur : le rappel de fond n'est plus lance."
        }
        val bloc = apres.substringBefore("}\n    }")
        check(bloc.length < apres.length) {
            "La borne du bloc `scope.launch` n'a pas mordu dans $observateur : le controle " +
                "mesurerait tout le fichier, et serait satisfait par une capture ecrite ailleurs."
        }
        check(bloc.contains(rappel)) {
            "Le bloc de `scope.launch` de $observateur ne contient plus `$rappel` : la borne ne " +
                "delimite plus le rappel de fond."
        }
        return bloc
    }

    /** Le source de `DeepSeekApplication`, depuis le repertoire du module `core:data`. */
    private fun sourceDeLApplication(): String =
        sourceDe("src/main/kotlin/com/msoumaya/deepseekandroid/DeepSeekApplication.kt", "app")

    /** Le source d'un observateur de `core:data/system`, depuis le repertoire du module. */
    private fun sourceDeLObservateur(nom: String): String =
        sourceDe("src/main/kotlin/com/msoumaya/deepseekandroid/core/data/system/$nom.kt", "core/data")

    /**
     * Lit un fichier du depot depuis le repertoire du module.
     *
     * Un test de `:core:data` s'execute avec le repertoire du module pour origine
     * (`deepseekandroid/core/data`), et non la racine du depot : c'est ce que Gradle donne a `Test`
     * par defaut. Les fichiers vises vivent donc un ou deux crans plus haut. Les chemins sont
     * essayes dans l'ordre, et l'erreur nomme **tous** les candidats, pour qu'un deplacement futur
     * se diagnostique en une lecture plutot qu'en une chasse.
     */
    private fun sourceDe(cheminRelatif: String, prefixe: String): String {
        val candidats = listOf(
            File(cheminRelatif),
            File("$prefixe/$cheminRelatif"),
            File("../$prefixe/$cheminRelatif"),
            File("../../$prefixe/$cheminRelatif"),
        )
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "$cheminRelatif introuvable. Chemins essayes : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
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
