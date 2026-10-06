package com.msoumaya.deepseekandroid.feature.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient le **branchement du panneau de séance** dans le lecteur.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * Le lecteur est une fonction `@Composable` : la déclencher demande un hôte Compose, un appareil
 * ou un émulateur, et le projet n'a aucun outillage de test d'interface. Le branchement n'est donc
 * atteignable par aucun test de comportement — ce n'est pas un raccourci, c'est le seul moyen.
 *
 * ## Ce qui disparaîtrait sans un mot
 *
 * C'est la question qui décide de ce que ce fichier surveille, et la réponse tient en une phrase :
 * **une destination qui cesse d'être passée ne casse rien, elle retire une entrée**. Le domaine
 * reste vert — `SessionPanelTextTest` ne connaît pas le lecteur —, la compilation passe, et le seul
 * symptôme est une entrée absente d'un panneau. Personne ne s'en apercevrait avant de le chercher à
 * la main.
 *
 * Trois de ces disparitions sont plus graves que les autres, et c'est pour elles que ce fichier
 * existe :
 *
 *  - **la branche du bandeau** : aplatie, une étape de consolidation n'est plus validable du tout,
 *    puisque son bouton ne vit que dans le panneau ;
 *  - **la garde de l'étape** : retirée, le panneau propose un bouton « J+7 » qui ne fait rien
 *    quand les trois étapes sont faites — le bouton mort que ce lecteur refuse partout ailleurs ;
 *  - **la notation qui ouvre la feuille** : sans elle, les trois notes de la barre n'écrivent rien
 *    et ne disent rien — elles ont l'air d'agir.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que le passage est **écrit**. Il ne prouve pas que le panneau s'affiche correctement —
 * cela se lit dans un diff — ni que les entrées sont les bonnes : c'est `SessionPanelTextTest`,
 * dans `core:domain`, et c'est là que la règle est éprouvée pour de vrai.
 */
class SessionPanelWiringTest {

    @Test
    fun `le lecteur recoit les quatre destinations de la seance`() {
        val source = sourceDuLecteur()
        for (declaration in listOf(
            "onValidateConsolidation: (() -> Unit)? = null,",
            "onRelearn: (() -> Unit)? = null,",
            "onWorkAgain: (() -> Unit)? = null,",
            "onPostpone: (() -> Unit)? = null,",
        )) {
            assertTrue(
                source.contains(declaration),
                "Le lecteur ne reçoit plus `$declaration` : l'entrée correspondante disparaîtra " +
                    "du panneau, et rien d'autre ne le dira.",
            )
        }
    }

    @Test
    fun `le lecteur porte la demande d'ouverture venue de la route`() {
        // La seconde porte du panneau. Le sélecteur de présentation est rendu par la **route**, qui
        // ne peut pas toucher `panel` — l'état du lecteur. Sans ce paramètre et son effet, la barre
        // de notation serait inatteignable pour une révision : son seul autre accès est le bandeau,
        // qui ouvre la feuille de validation.
        val source = sourceDuLecteur()
        assertTrue(
            source.contains("sessionPanelRequest: Int = 0,"),
            "Le lecteur ne reçoit plus la demande d'ouvrir le panneau de séance.",
        )
        assertTrue(
            source.contains("LaunchedEffect(sessionPanelRequest) {"),
            "La demande d'ouverture n'est plus écoutée : le panneau ne s'ouvrirait jamais depuis " +
                "le sélecteur de présentation.",
        )
        assertTrue(
            source.contains("if (sessionPanelRequest > 0) panel = ReaderPanel.SESSION"),
            "La demande d'ouverture n'ouvre plus le panneau de séance.",
        )
    }

    @Test
    fun `le panneau de seance est rendu par la coquille`() {
        // Le `?.let` n'est pas décoratif : comme le panneau du verset, celui-ci n'a rien à montrer
        // sans sa valeur — et c'est `SessionPanelText.entries` qui décide ensuite s'il a quelque
        // chose à proposer, une lecture libre n'ayant aucune entrée.
        val source = sourceDuLecteur()
        assertTrue(
            source.contains("ReaderPanel.SESSION -> seance?.let { etat ->"),
            "Le panneau de séance n'est plus rendu, ou n'est plus gardé par la séance.",
        )
        assertTrue(
            source.contains("SessionPanelSheet("),
            "La destination existe, mais plus rien n'est rendu pour elle : le panneau " +
                "s'ouvrirait sur du vide.",
        )
    }

    @Test
    fun `le panneau recoit la requete et l'etape de consolidation`() {
        // La **requête**, et non seulement ce qu'on en a tiré : c'est d'elle que dépendent les
        // entrées, et aucun couple de bornes ne dit si la tâche était un apprentissage ou une
        // révision. L'**étape**, elle, est le chiffre du libellé de consolidation — « J+3 ».
        val panneau = appelDuPanneau()
        assertTrue(
            panneau.contains("request = etat.request,"),
            "Le panneau ne reçoit plus la requête : il ne saurait plus quelles entrées proposer.",
        )
        assertTrue(
            panneau.contains("consolidationOffset = etat.consolidationOffset,"),
            "Le panneau ne reçoit plus l'étape de consolidation : le libellé retomberait " +
                "toujours sur J+7, quelle que soit l'échéance réelle.",
        )
    }

    @Test
    fun `la consolidation n'est proposee que s'il reste une etape`() {
        // Les trois étapes faites, le client d'origine affiche quand même un bouton « J+7 » — le
        // libellé retombe sur la dernière échéance — et ce bouton ne fait rien : sa garde sort
        // avant d'écrire. C'est un bouton mort, et c'est ce que ce lecteur refuse ailleurs. La
        // garde est donc ici, côté appelant, seul endroit qui sache ce qu'il peut réellement faire.
        assertTrue(
            appelDuPanneau().contains("?.takeIf { etat.consolidationOffset != null }"),
            "Le panneau propose une consolidation même quand les trois étapes sont faites : le " +
                "bouton n'aurait plus rien à valider.",
        )
    }

    @Test
    fun `la notation passe par la feuille, et le grade est ignore`() {
        // Mesure du client d'origine, et non oubli : sa lambda de notation **jette** son argument
        // et ouvre la feuille, qui redemande la note et enregistre celle qu'on y choisit. La
        // transcrire autrement — en écrivant directement le grade reçu — changerait ce qui est
        // enregistré, et la personne n'aurait plus l'occasion de confirmer.
        val panneau = appelDuPanneau()
        assertTrue(
            panneau.contains("onGrade = { _ ->"),
            "La notation ne jette plus son grade : elle écrit peut-être une note que la personne " +
                "n'a pas confirmée — ou elle n'écrit plus rien.",
        )
        val notation = panneau.substringAfter("onGrade = { _ ->").substringBefore("},")
        assertTrue(
            notation.contains("completionOpen = true"),
            "La notation n'ouvre plus la feuille de validation : les trois notes de la barre " +
                "n'auraient aucun effet, et elles ont l'air d'en avoir un.",
        )
    }

    @Test
    fun `le geste en cours de la barre est l'ecoute ouverte`() {
        // C'est le `audioDock ? 'audio' : null` du client d'origine, et le seul état qu'il passe
        // jamais. Le perdre ne casserait rien : la barre s'afficherait simplement sans jamais
        // montrer ce qui joue.
        assertTrue(
            sourceDuLecteur().contains(
                "active = if (audioState?.isOpen == true) ReviewText.Action.LISTEN else null,",
            ),
            "La barre ne sait plus quel geste est en cours : « Écouter » ne se distinguerait " +
                "plus quand la lecture est ouverte.",
        )
    }

    @Test
    fun `aucune destination d'enregistrement n'est branchee, et c'est dit`() {
        // L'absence est **voulue**, et c'est pourquoi elle est surveillée : « Ma voix » dépend d'un
        // enregistreur qui n'est pas porté. Le jour où il arrivera, ce contrôle tombera — et c'est
        // exactement ce qu'on lui demande, puisqu'il faudra alors écrire la destination.
        assertFalse(
            sourceDuLecteur().contains("onRecord ="),
            "Le lecteur branche un enregistrement : le panneau de la voix n'existe pas encore, " +
                "donc l'entrée mènerait nulle part.",
        )
    }

    /**
     * L'appel du panneau de séance, et lui seul.
     *
     * **Borné**, et pour une raison mesurée : `completionOpen = true` apparaît aussi dans le geste
     * du bandeau, plus haut, et `consolidationOffset` dans la construction de la coquille, dans un
     * autre module. Une recherche sur le fichier entier resterait verte même si le panneau ne
     * recevait plus rien.
     *
     * La borne est accompagnée de son propre contrôle : `substringAfter` **sans repli** rend la
     * chaîne entière quand le délimiteur est absent, et le bloc lu serait alors le fichier — où
     * `completionOpen` se trouve justement **avant** l'appel du panneau.
     */
    private fun appelDuPanneau(): String {
        val source = sourceDuLecteur()
        val marqueur = "SessionPanelSheet("
        assertTrue(source.contains(marqueur), "Le lecteur n'ouvre plus le panneau de séance.")
        val appel = source.substringAfter(marqueur).substringBefore("\n        }\n\n        ReaderPanel.SURAH")
        assertTrue(
            appel.length < source.length,
            "La borne du panneau n'a pas mordu : le bloc lu est le fichier entier, et le " +
                "`completionOpen` trouvé serait celui du bandeau.",
        )
        return appel
    }

    /**
     * Le source du lecteur.
     *
     * Deux chemins sont essayés, et non un seul : la tâche `Test` de Gradle s'exécute dans le
     * dossier **du module**, alors qu'un contrôle joué à la main depuis la racine du dépôt part
     * de là.
     */
    private fun sourceDuLecteur(): String {
        val relatif = "src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt"
        val candidats = listOf(File(relatif), File("feature/reader/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "ReaderScreen.kt introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }
}
