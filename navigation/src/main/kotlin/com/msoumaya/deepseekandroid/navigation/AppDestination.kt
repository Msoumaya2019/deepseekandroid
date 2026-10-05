package com.msoumaya.deepseekandroid.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector
import com.msoumaya.deepseekandroid.core.domain.StudySession

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

    /**
     * Le premier verset de la plage servie, en argument facultatif.
     *
     * Les bornes servent **les deux formes de tâche** — séance et révision —, et c'est pourquoi
     * elles ne sont pas nommées d'après l'une d'elles. Elles ne voyagent jamais seules : une
     * lecture libre n'en porte pas. Voir [readerRoute].
     */
    const val READER_FROM = "de"

    /** Le dernier verset de la plage servie, en argument facultatif. Voir [READER_FROM]. */
    const val READER_TO = "a"

    /**
     * L'identifiant de la **tâche de révision** servie, en argument facultatif.
     *
     * Un identifiant, et non la tâche entière : ses bornes sont déjà portées par `de` et `a`, et sa
     * catégorie par [READER_CATEGORY]. Recopier la tâche entière ferait deux sources pour la même
     * chose, et c'est celle qu'on ne relit pas qui resterait.
     */
    const val READER_TASK = "tache"

    /**
     * La catégorie de la tâche de révision — `recent`, `habitual` ou `priority`.
     *
     * Elle n'est pas décorative : c'est elle qui décide si le lecteur propose l'**étape de
     * consolidation**. Une tâche transportée sans sa catégorie serait traitée comme une révision
     * ordinaire, et une consolidation ne s'ouvrirait jamais — sans que rien ne le dise.
     */
    const val READER_CATEGORY = "categorie"

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
            "&$READER_TO={$READER_TO}" +
            "&$READER_TASK={$READER_TASK}" +
            "&$READER_CATEGORY={$READER_CATEGORY}"

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
        reviewTaskId: String? = null,
        reviewCategory: String? = null,
    ): String {
        // Les bornes sont calculées **une fois**, puis posées par la branche de la tâche qui les
        // porte. Elles ne voyagent jamais seules : une lecture libre n'a pas de progression à
        // valider, et lui prêter des bornes ferait croire à une tâche — le lecteur écrirait alors
        // une validation que personne n'a demandée.
        val bornes = if (from != null && to != null) {
            listOf("$READER_FROM=$from", "$READER_TO=$to")
        } else {
            emptyList()
        }
        val arguments = buildList {
            if (verseId != null) add("$READER_VERSE=$verseId")
            // Les trois arguments d'une séance voyagent **ensemble** : une séance sans ses bornes
            // ne serait pas ouvrable, et des bornes sans séance ne seraient pas validables. Les
            // séparer produirait une route qui s'ouvre — donc un défaut muet, puisqu'on croirait
            // la séance servie alors qu'elle ne l'est pas.
            if (sessionId != null && bornes.isNotEmpty()) {
                add("$READER_SESSION=$sessionId")
                addAll(bornes)
            }
            // Une tâche de révision voyage avec sa **catégorie**, et avec ses bornes : sans elles,
            // le lecteur ne saurait pas quelle plage compter, et la validation serait perdue.
            //
            // C'est ici qu'un défaut a réellement été écrit, puis attrapé : les bornes étaient
            // posées dans le seul bloc de la séance, donc une révision — qui n'a pas
            // d'identifiant de séance — partait sans `de` ni `a`. La route s'ouvrait, la page
            // s'affichait, et rien ne disait que la plage manquait.
            if (reviewTaskId != null && reviewCategory != null && bornes.isNotEmpty()) {
                add("$READER_TASK=$reviewTaskId")
                add("$READER_CATEGORY=$reviewCategory")
                addAll(bornes)
            }
        }
        return if (arguments.isEmpty()) READER else "$READER?" + arguments.joinToString("&")
    }

    /**
     * La route du lecteur pour une **tâche**, quelle qu'elle soit.
     *
     * Une seule fonction pour les trois formes — séance, révision, consolidation —, et c'est ce
     * qui rend le transport fidèle. Écrire la route à la main chez l'appelant a déjà produit un
     * défaut **muet** : [readerRoute] retire les trois arguments d'une séance quand `sessionId`
     * est nul, donc une révision ouverte depuis le programme partait en lecture libre et n'était
     * jamais enregistrée. Ici, ce que la requête porte est ce que la route porte.
     *
     * **`consolidation` n'est pas transporté, et c'est délibéré.** Il vaut « la catégorie est
     * `recent` » — la règle du renderer, et celle du client d'origine. Le transporter créerait une
     * seconde source pour la même décision, et les deux finiraient par diverger : une route
     * pourrait annoncer une consolidation que sa catégorie dément.
     *
     * **`revisionId` n'est pas transporté non plus** : c'est un champ du modèle de révision
     * « legacy », que ce client n'écrit jamais.
     */
    fun studyRoute(request: StudySession.Request): String = readerRoute(
        verseId = request.range.start,
        sessionId = request.sessionId,
        from = request.range.start,
        to = request.range.end,
        reviewTaskId = request.reviewTask?.id,
        reviewCategory = request.reviewTask?.category?.name?.lowercase(),
    )

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
