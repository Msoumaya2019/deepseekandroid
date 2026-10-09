package com.msoumaya.deepseekandroid.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient le **bandeau de connectivité** : sa place dans la coquille, et l'usage des règles du
 * domaine dans le composable.
 *
 * ## Pourquoi ce contrôle existe
 *
 * Un bandeau se monte en une ligne, et se débranche aussi. Or rien, dans l'application, ne le
 * regretterait : l'application fonctionne parfaitement sans lui, et une coupure de réseau
 * deviendrait simplement silencieuse — la personne verrait ses modifications ne pas partir sans
 * qu'aucun écran ne le lui dise. C'est exactement ce que `ANDROID_MIGRATION.md` désigne comme
 * « à faire » depuis la phase A.
 *
 * ## Ce qui disparaîtrait sans un mot
 *
 * Quatre choses, et aucune ne casse quoi que ce soit :
 *
 *  - **le montage** : retirer `ConnectivityBanner(...)` de la coquille laisse tout compiler. Le
 *    composable devient un fichier que personne n'appelle, et le bandeau n'existe plus ;
 *  - **la place** : descendu **après** la barre supérieure, le bandeau apparaîtrait sous elle.
 *    Descendu après la garde du plein écran, il disparaîtrait du **lecteur** — l'écran où l'on
 *    reste le plus longtemps, donc celui où une coupure se voit le plus ;
 *  - **la marge de la barre d'état** : la coquille n'en pose pas pour les écrans qui vont
 *    jusqu'aux bords. Sans la marge du bandeau, il s'afficherait **sous** l'heure et les icônes
 *    du système — illisible précisément quand il a quelque chose à dire ;
 *  - **les règles du domaine** : un composable qui recalculerait la visibilité lui-même
 *    (`etat.horsLigne || etat.retourJusquaMs != null`) ignorerait l'horloge, et le bandeau de
 *    retour ne s'éteindrait **jamais**. C'est un défaut qui ne se voit qu'après trois secondes
 *    d'attente, donc jamais pendant qu'on développe.
 *
 * ## Ce qu'il ne prouve pas
 *
 * Il ne prouve pas que le bandeau s'affiche au bon moment : c'est la règle du domaine, mesurée
 * par `ConnectivityTest` dans `core:domain`. Il ne prouve pas non plus que la lecture du réseau
 * est juste — cela demande un appareil, et `ConnectivityObserver` documente ce qui n'est pas
 * éprouvable sans lui.
 */
class AppScaffoldConnectivityTest {

    @Test
    fun `le bandeau est monte dans la coquille, et pas seulement importe`() {
        // `ConnectivityBanner(` est la **forme montée** : la seule déclaration du nom, elle, est
        // son propre fichier. Un contrôle écrit sur le nom seul serait satisfait par un import
        // laissé là après un débranchement — c'est le piège que ce contrôle existe pour éviter.
        val occurrences = sansCommentaires(sourceDeLaCoquille())
            .lines()
            .count { it.contains("ConnectivityBanner(") }

        assertEquals(
            1,
            occurrences,
            "Le bandeau de connectivité n'est plus monté dans la coquille ($occurrences fois) : " +
                "une coupure de réseau ne serait plus annoncée nulle part, et rien ne le dirait.",
        )
    }

    @Test
    fun `le bandeau precede la barre superieure`() {
        // L'original l'affiche en **premier enfant** de sa vue racine, donc au-dessus de la barre
        // supérieure. Le déplacer sous elle changerait ce que la personne voit en premier — et
        // sans casser un seul test, puisqu'un bandeau sous la barre reste un bandeau.
        val texte = sansCommentaires(sourceDeLaCoquille())
        val bandeau = texte.indexOf("ConnectivityBanner(")
        val barre = texte.indexOf("AppTopBar(")

        assertTrue(bandeau >= 0, "Le bandeau n'est pas monté dans la coquille.")
        assertTrue(barre >= 0, "La barre supérieure n'est plus montée dans la coquille.")
        assertTrue(
            bandeau < barre,
            "Le bandeau est monté **après** la barre supérieure : il apparaîtrait sous elle, " +
                "alors que l'original le place au-dessus de tout.",
        )
    }

    @Test
    fun `le bandeau n'est pas cache par un ecran plein ecran`() {
        // Le lecteur est plein écran : ni barre supérieure, ni barre d'onglets. C'est l'écran où
        // l'on reste le plus longtemps, donc celui où une coupure doit le plus se voir. Placé
        // dans la garde du plein écran, le bandeau y disparaîtrait — et nulle part ailleurs, ce
        // qui rendrait le défaut invisible à qui essaie l'application sur un autre écran.
        val texte = sansCommentaires(sourceDeLaCoquille())
        val bandeau = texte.indexOf("ConnectivityBanner(")
        val garde = texte.indexOf("if (!AppRoutes.isFullScreen(route))")

        assertTrue(garde >= 0, "La garde du plein écran a disparu de la coquille.")
        assertTrue(
            bandeau < garde,
            "Le bandeau est monté **dans** la garde du plein écran : il serait absent du lecteur, " +
                "c'est-à-dire de l'écran où l'on passe le plus de temps.",
        )
    }

    @Test
    fun `le bandeau porte la marge de la barre d'etat quand l'ecran va jusqu'aux bords`() {
        // La fenêtre est bornée à 300 caractères après l'appel : `AppRoutes.isEdgeToEdge(route)`
        // apparaît **aussi** plus haut, sur la colonne entière. Une assertion écrite sur le
        // fichier entier serait donc satisfaite par cette autre occurrence, et resterait verte
        // sur un bandeau qui aurait perdu sa marge.
        val bloc = sansCommentaires(sourceDeLaCoquille())
            .substringAfter("ConnectivityBanner(")
            .take(FENETRE)

        assertTrue(
            bloc.contains("AppRoutes.isEdgeToEdge(route)"),
            "Le bandeau ne consulte plus `isEdgeToEdge` : il s'afficherait sous la barre d'état " +
                "sur les écrans qui vont jusqu'aux bords.",
        )
        assertTrue(
            bloc.contains("Modifier.windowInsetsPadding(WindowInsets.statusBars)"),
            "Le bandeau ne pose plus la marge de la barre d'état : il s'afficherait **sous** " +
                "l'heure et les icônes du système.",
        )
    }

    @Test
    fun `le bandeau demande sa visibilite au domaine, et lui donne l'horloge`() {
        // La règle dépend du **temps** : sans l'instant courant, le bandeau de retour ne
        // s'éteindrait jamais. Un composable qui recalculerait la visibilité lui-même — par
        // exemple `etat.horsLigne || etat.retourJusquaMs != null` — serait vert partout et
        // laisserait « Connexion rétablie » affiché pour toujours.
        assertTrue(
            sansCommentaires(sourceDuBandeau()).contains("bandeauVisible(etat, maintenant)"),
            "Le bandeau ne demande plus sa visibilité au domaine, ou ne lui passe plus l'instant " +
                "courant : le bandeau de retour ne s'éteindrait jamais.",
        )
    }

    @Test
    fun `le bandeau demande son texte au domaine au lieu de l'ecrire`() {
        // Les deux textes sont ceux de l'original, et l'un porte un **cadratin**. Recopiés dans
        // le composable, ils divergeraient du domaine au premier changement — et une assertion
        // sur le texte du domaine ne verrait pas la copie.
        val bandeau = sansCommentaires(sourceDuBandeau())

        assertTrue(
            bandeau.contains("texteDuBandeau(etat)"),
            "Le bandeau n'emprunte plus son texte au domaine.",
        )
        assertFalse(
            bandeau.contains("Mode hors connexion"),
            "Le bandeau écrit le texte hors ligne en clair au lieu de l'emprunter au domaine : " +
                "les deux textes peuvent désormais diverger.",
        )
    }

    @Test
    fun `le minuteur du bandeau est reclave sur l'echeance`() {
        // C'est le `clearTimeout(timer)` de l'original, obtenu autrement : l'effet est **claveté**
        // sur l'échéance, donc une nouvelle échéance annule la précédente. Un effet claveté sur
        // un booléen — ou sans clé — laisserait deux minuteurs vivants, dont le premier
        // éteindrait le bandeau du second avant l'heure.
        assertTrue(
            sansCommentaires(sourceDuBandeau()).contains("LaunchedEffect(etat.retourJusquaMs)"),
            "Le minuteur du bandeau n'est plus claveté sur l'échéance : un second retour de " +
                "réseau laisserait le premier minuteur éteindre son bandeau avant l'heure.",
        )
    }

    @Test
    fun `le source lu est bien celui de la coquille et celui du bandeau`() {
        // Sans ce contrôle, une erreur de chemin ferait lire un fichier vide — ou le mauvais —,
        // et les assertions ci-dessus seraient satisfaites par l'absence de tout.
        assertTrue(
            sourceDeLaCoquille().contains("fun AppScaffold("),
            "Le fichier lu ne déclare pas `fun AppScaffold(` : le chemin résolu ne désigne pas " +
                "la coquille.",
        )
        assertTrue(
            sourceDuBandeau().contains("fun ConnectivityBanner("),
            "Le fichier lu ne déclare pas `fun ConnectivityBanner(` : le chemin résolu ne " +
                "désigne pas le bandeau.",
        )
    }

    private companion object {
        /**
         * La fenêtre de lecture du bloc du bandeau, en caractères.
         *
         * Assez large pour contenir l'appel et sa marge — mesuré à un peu plus de 200 caractères
         * —, et assez courte pour ne pas atteindre l'usage suivant de `isEdgeToEdge`.
         */
        const val FENETRE = 300
    }
}
