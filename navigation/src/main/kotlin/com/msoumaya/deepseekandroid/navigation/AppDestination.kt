package com.msoumaya.deepseekandroid.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector

// ---------------------------------------------------------------------------
// Destinations
// ---------------------------------------------------------------------------
// Portage de `mainTabs` et de `BottomNavigation` (`src/ui/Premium.tsx`).
//
// Le dépôt d'origine declarait :
//
//   mainTabs = ['Accueil','Coran','Programme','Progrès','Amis']
//   icons    = ['home-outline','book-open-outline','calendar-check-outline','chart-bar',
//               'account-group-outline']
//
// Ces icônes sont des noms MaterialCommunityIcons. La correspondance retenue est celle de
// Material Icons, qui est le jeu natif d'Android :
//
//   home-outline           -> Home
//   book-open-outline      -> MenuBook          (livre ouvert)
//   calendar-check-outline -> EventAvailable    (calendrier avec une coche)
//   chart-bar              -> BarChart
//   account-group-outline  -> Groups
//
// **Cinq onglets, et aucun autre.** Le cahier des charges est explicite : ni réglages, ni
// profil, ni quiz n'ont d'onglet ; ces écrans s'ouvrent depuis l'en-tête ou depuis le contenu.
// ---------------------------------------------------------------------------

/**
 * Une destination de premier niveau.
 *
 * @param route identifiant technique de la route de navigation.
 * @param label libellé affiché sous l'icône — celui du dépôt d'origine, accents compris.
 * @param icon icône de la barre basse et de l'en-tête.
 */
enum class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("accueil", "Accueil", Icons.Outlined.Home),
    QURAN("coran", "Coran", Icons.AutoMirrored.Outlined.MenuBook),
    PROGRAM("programme", "Programme", Icons.Outlined.EventAvailable),
    PROGRESS("progres", "Progrès", Icons.Outlined.BarChart),
    FRIENDS("amis", "Amis", Icons.Outlined.Groups),
    ;

    companion object {
        val start: AppDestination = HOME

        /** Retrouve une destination depuis une route, ou `null` si la route n'est pas un onglet. */
        fun fromRoute(route: String?): AppDestination? =
            entries.firstOrNull { it.route == AppRoutes.baseRoute(route) }
    }
}

/** Routes qui ne sont pas des onglets : elles s'empilent par-dessus la barre basse. */
object AppRoutes {
    /** Le lecteur de moushaf occupe tout l'écran : ni onglets, ni barre basse. */
    const val READER = "lecteur"

    /** Le verset sur lequel le lecteur s'ouvre, en argument **facultatif** de la route. */
    const val READER_VERSE = "verset"

    /**
     * La séance d'apprentissage que le lecteur sert, en argument **facultatif**.
     *
     * Un identifiant, et non la séance entière : l'état du compte la porte déjà, et recopier ses
     * bornes dans la route ferait deux sources de vérité pour la même chose. Elles y sont
     * pourtant, plus bas — mais comme **repli**, pas comme référence.
     */
    const val READER_SESSION = "seance"

    /** Le premier verset de la plage servie, en argument facultatif. Repli de la séance. */
    const val READER_FROM = "de"

    /** Le dernier verset de la plage servie, en argument facultatif. Repli de la séance. */
    const val READER_TO = "a"

    /**
     * Le motif de la route du lecteur, argument compris.
     *
     * L'argument est **facultatif** — `lecteur` seul reste une route valide — et c'est ce qui
     * permet d'ouvrir le lecteur sans savoir sur quoi : une ouverture libre n'a pas de verset,
     * et lui en inventer un ferait mémoriser une position que personne n'a lue.
     */
    val READER_PATTERN: String =
        "$READER?$READER_VERSE={$READER_VERSE}" +
            "&$READER_SESSION={$READER_SESSION}" +
            "&$READER_FROM={$READER_FROM}" +
            "&$READER_TO={$READER_TO}"

    /**
     * La route du lecteur, ouverte sur [verseId] ou librement.
     *
     * Construite ici, et non recollée à la main chez l'appelant : une route écrite en deux
     * endroits finit par diverger, et la divergence serait **muette** — la navigation
     * n'échouerait pas, elle ouvrirait le lecteur sans verset, et la position mémorisée serait
     * celle du repli au lieu de celle qu'on visait.
     */
    fun readerRoute(
        verseId: Int? = null,
        sessionId: String? = null,
        from: Int? = null,
        to: Int? = null,
    ): String {
        val arguments = buildList {
            if (verseId != null) add("$READER_VERSE=$verseId")
            // Les trois arguments d'une séance voyagent **ensemble** : une séance sans ses bornes
            // ne serait pas ouvrable, et des bornes sans séance ne seraient pas validables. Les
            // séparer produirait une route qui s'ouvre — donc un défaut muet, puisqu'on croirait
            // la séance servie alors qu'elle ne l'est pas.
            if (sessionId != null && from != null && to != null) {
                add("$READER_SESSION=$sessionId")
                add("$READER_FROM=$from")
                add("$READER_TO=$to")
            }
        }
        return if (arguments.isEmpty()) READER else "$READER?" + arguments.joinToString("&")
    }

    /**
     * La route **sans** sa partie facultative.
     *
     * Une route paramétrée n'est pas la chaîne de sa base : la pile rend le motif déclaré
     * (`lecteur?verset={verset}`), et non `lecteur`. Comparer l'un aux ensembles écrits avec
     * l'autre laisserait le lecteur avec une barre supérieure et une barre basse — deux barres
     * par-dessus un écran qui doit occuper tout l'espace. La comparaison passe donc toujours
     * par ici.
     */
    fun baseRoute(route: String?): String? = route?.substringBefore("?")

    /** Le défi de quiz, ouvert depuis l'accueil ou depuis un défi d'ami. */
    const val QUIZ = "quiz"

    /** Les contenus du jour : invocation, hadith, verset à méditer. */
    const val DAILY = "quotidien"

    /** Les récitations partagées avec les amis. */
    const val RECITATIONS = "recitations"

    /** Le tableau de bord des révisions. */
    const val REVIEW = "revision"

    /** L'administration, réservée aux comptes qui y ont droit. */
    const val ADMIN = "admin"

    /** Le profil, ouvert depuis l'en-tête. */
    const val PROFILE = "profil"

    /** Les réglages, ouverts depuis l'en-tête. */
    const val SETTINGS = "reglages"

    /** L'apparence : thème, accent, police d'interface. */
    const val APPEARANCE = "apparence"

    /** L'objectif d'apprentissage. */
    const val GOAL = "objectif"

    /**
     * Écrans qui occupent tout l'écran : **ni** barre supérieure, **ni** barre basse.
     *
     * Le dépôt d'origine les excluait tous les deux de la même façon — c'était la condition
     * `!reader && ... && !reviewOpen && !recitationsOpen && !dailyOpen && !quizOpen` répétée
     * devant l'en-tête et devant la barre basse.
     */
    val fullScreen: Set<String> = setOf(READER, QUIZ, DAILY, RECITATIONS, REVIEW, ADMIN)

    /**
     * Écrans qui vont **jusqu'aux bords** de l'écran, barres système comprises.
     *
     * Le lecteur seul est dans ce cas, et c'est une distinction qui compte. `fullScreen` dit
     * « pas de barres de l'application » ; `edgeToEdge` dit « pas de marge de barre d'état non
     * plus ». Un quiz en plein écran a toujours besoin de ne pas passer sous la barre d'état,
     * sinon sa première ligne serait illisible. Le lecteur, lui, **gère ses propres marges** :
     * il centre la page dans l'espace sûr tout en laissant son fond aller jusqu'aux bords —
     * ce qui est la seule façon d'éviter une bande morte sur les appareils à découpe.
     */
    val edgeToEdge: Set<String> = setOf(READER)

    /** Vrai si la route masque les barres de l'application. */
    fun isFullScreen(route: String?): Boolean = baseRoute(route) in fullScreen

    /** Vrai si la route va jusqu'aux bords, barres système comprises. */
    fun isEdgeToEdge(route: String?): Boolean = baseRoute(route) in edgeToEdge

    /**
     * Écrans d'outil : barre supérieure **avec un retour**, et **pas** de barre basse.
     *
     * La valeur est le titre affiché. L'ordre suit celui du ternaire d'origine dans `App.tsx` :
     * `utilityView==='goal' ? 'Mon objectif' : utilityView==='appearance' ? 'Apparence' :
     * utilityView==='profile' ? 'Profil' : utilityView==='settings' ? 'Réglages' : …`.
     *
     * C'est une distinction qu'il ne faut pas confondre avec [fullScreen] : un écran d'outil
     * garde son en-tête, sinon il n'aurait plus aucun moyen de revenir en arrière.
     */
    val utility: Map<String, String> = linkedMapOf(
        GOAL to "Mon objectif",
        APPEARANCE to "Apparence",
        PROFILE to "Profil",
        SETTINGS to "Réglages",
    )

    /** Titre de l'écran d'outil, ou `null` si la route n'est pas un écran d'outil. */
    fun utilityTitle(route: String?): String? = utility[baseRoute(route)]

    /** Vrai si la route masque la barre basse. */
    fun hidesBottomBar(route: String?): Boolean {
        val base = baseRoute(route)
        return base in fullScreen || base in utility
    }
}
