package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.DailyContent
import com.msoumaya.deepseekandroid.core.model.DailyContentType
import com.msoumaya.deepseekandroid.core.model.DailyContentsSnapshot

/**
 * Règles des contenus du jour.
 *
 * Porté depuis `src/services/dailyContents.ts` et `src/DailyContentsScreen.tsx`. Ce que le
 * serveur décide — quelle entrée tombe un jour donné, quelles catégories existent — n'est pas
 * ici ; ce fichier ne porte que les décisions prises **sur l'appareil**, et il n'en porte que
 * celles dont une erreur ne se verrait pas à l'œil.
 *
 * **Ce qui n'est pas ici, et pourquoi.** La composition du texte partagé, les trois façons
 * d'écrire la ligne de source, le repli du titre sur le nom du type et les quatre phrases de
 * l'écran sont des **libellés** : ils arrivent avec l'écran, comme ceux du Quiz et de l'espace
 * social. Une constante que personne n'affiche est une surface à relire sans preuve.
 *
 * **Ce qui est ici, et pourquoi.** Trois règles qui ne se voient qu'en les mesurant : la taille
 * d'une page, la validité d'un document mis en cache, et le partage entre « rien à montrer » et
 * « rien n'a pu être lu ». Les trois se paient en silence quand elles sont fausses — une liste
 * qui s'arrête trop tôt, un rappel d'hier affiché comme celui du jour, et un message d'erreur
 * remplacé par une affirmation fausse.
 */
object DailyContents {

    /**
     * Nombre de contenus d'une page de liste.
     *
     * C'est la fenêtre de l'original : `range(offset, offset+29)` — **trente** entrées, bornes
     * comprises. Le nombre vit ici, et non dans la requête, parce qu'il décide de **deux** choses :
     * la fenêtre demandée au serveur, et la règle qui dit si la page reçue peut avoir une suite
     * (`pageComplete`). Deux copies finiraient par diverger, et la divergence se lirait comme une
     * liste qui s'arrête au milieu sans rien dire.
     */
    const val PAGE_SIZE = 30

    /**
     * Le jour courant, au format `AAAA-MM-JJ`, en calendrier local.
     *
     * C'est exactement `localDate()` de l'original — `getFullYear`, `getMonth`, `getDate` —, et
     * [Dates.todayLocal] dit la même chose. La délégation est écrite pour que le nom du domaine
     * apparaisse là où il se lit, et non pour ajouter une règle.
     *
     * **Le fuseau est celui de l'appareil, et cela compte.** Le serveur choisit l'entrée du jour
     * à partir de la date qu'on lui envoie (`daily_content_for_date(p_date)`), donc une date
     * calculée en UTC ferait basculer le contenu à une heure fausse pour tout fuseau en avance
     * sur Greenwich — et personne ne le verrait, puisque le contenu resterait cohérent, seulement
     * décalé.
     */
    fun today(): String = Dates.todayLocal()

    /**
     * Un document vide pour [day].
     *
     * Il est rendu **avec sa date** : un document vide dont le jour serait `""` serait traité
     * comme périmé par [isFor] à chaque lecture, et le cache ne servirait plus à rien.
     */
    fun emptySnapshot(day: String = today()): DailyContentsSnapshot =
        DailyContentsSnapshot(day = day)

    /**
     * Vrai si [snapshot] est bien celui de [day].
     *
     * **C'est la règle qui empêche d'afficher le contenu d'hier comme celui d'aujourd'hui.** Le
     * serveur choisit une entrée par type **et par date**, donc un document lu hier décrit un
     * autre jour — et rien dans sa forme ne l'annonce, sinon son champ `day`. Sans cette
     * comparaison, un appareil ouvert le matin montrerait le rappel de la veille avec la mention
     * « Rappel du jour », ce qui est une affirmation fausse sur un contenu religieux.
     */
    fun isFor(snapshot: DailyContentsSnapshot, day: String = today()): Boolean = snapshot.day == day

    /**
     * Vrai si le document ne porte **aucun** contenu du jour.
     *
     * **La question ne se pose qu'à cause d'un échec, jamais toute seule.** Un document vide
     * après une lecture **réussie** est une information — « aucun contenu n'est publié
     * aujourd'hui », ce que l'écran sait dire. Le même document vide après une lecture **ratée**
     * ne dit rien du tout : l'écran ignore s'il n'y a pas de contenu ou s'il n'a pas pu le lire,
     * et afficher « Aucun contenu disponible aujourd'hui » serait alors une affirmation fausse,
     * faite au pire moment. C'est ce partage que le dépôt fait, et c'est la seule raison d'être de
     * cette fonction — la même que `Quiz.isEmpty`.
     */
    fun isEmpty(snapshot: DailyContentsSnapshot): Boolean = snapshot.entries.isEmpty()

    /**
     * Les types que le carrousel de l'accueil affiche, **dans cet ordre**.
     *
     * C'est la liste écrite en clair par l'original — `['reminder','invocation']` —, et non
     * `DailyContentType.entries`. La différence n'est pas cosmétique : coupler cet ordre à
     * l'énumération ferait apparaître une vignette de plus le jour où le `check` de la base
     * accepterait une troisième valeur. L'accueil d'origine n'en affiche que deux, et rien dans le
     * code ne dirait que c'est un choix plutôt qu'un oubli.
     */
    private val CAROUSEL_TYPES = listOf(DailyContentType.REMINDER, DailyContentType.INVOCATION)

    /**
     * Les entrées du carrousel : **au plus une par type**, dans l'ordre de [CAROUSEL_TYPES].
     *
     * L'original écrit `['reminder','invocation'].flatMap(type => items.find(row => row.type ===
     * type) ?: [])`. Deux règles s'y cachent, et aucune ne lève quand elle est fausse :
     *
     *  - **la première de chaque type**, et non toutes. `daily_content_for_date` rend au plus deux
     *    lignes — une par type —, mais rien côté client ne le garantit, et trois vignettes pour
     *    deux titres possibles feraient défiler deux fois le même en-tête ;
     *  - **un type absent ne laisse pas de trou** : le carrousel peut n'avoir qu'une vignette, ou
     *    aucune. C'est le cas courant d'une base où rien n'est publié, et c'est un état affiché et
     *    non une absence de bloc.
     *
     * Ce qui n'est **pas** filtré ici : `isActive`. Les deux lectures du dépôt le filtrent déjà
     * côté serveur, et le refaire ici donnerait deux vérités pour une seule règle — celle qui
     * décide de ce qu'une base publie.
     */
    fun entries(contents: List<DailyContent>): List<DailyContent> =
        CAROUSEL_TYPES.mapNotNull { type -> contents.firstOrNull { it.type == type } }
}
