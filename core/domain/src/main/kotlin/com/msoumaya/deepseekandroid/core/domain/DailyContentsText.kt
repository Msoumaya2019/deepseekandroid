package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.DailyContentType

/**
 * Libellés des contenus du jour.
 *
 * **Le fichier porte deux choses, et elles n'ont pas la même origine.** [GENERIC_FAILURE] est
 * écrite **par le dépôt** (`DailyContentRepository`), qui doit publier un texte lisible quand le
 * serveur n'en a pas donné — sans quoi une panne deviendrait un **échec muet**, c'est-à-dire
 * exactement ce que le partage panne/avis existe pour éviter. C'est la même règle que
 * `ProblemReportText.SEND_FAILED`, et la même forme : `error.restMessage() ?: <repli>`. Les
 * phrases du **carrousel de l'accueil** sont venues avec lui, comme celles du Quiz et de l'espace
 * social sont venues avec leurs écrans : une constante que personne n'affiche est une surface à
 * relire sans preuve.
 *
 * **Les phrases de l'écran de liste sont arrivées avec lui**, comme cette docstring l'annonçait :
 * les deux onglets (« ☀ Rappel », « ☾ Invocation »), « Tout », « ♡ Mes favoris », « Afficher la
 * suite », « Chargement… », « Ouvrir la fiche › », « ‹ Retour », « Rappels & Invocations », les
 * deux phrases de liste vide — et les libellés de la **fiche** : partager, basculer un favori.
 * L'écoute et l'enregistrement n'ont **pas** les leurs, et les deux blocs ci-dessous disent
 * pourquoi : une constante que personne n'affiche est une surface à relire sans preuve, et ces
 * deux gestes n'ont pas encore de porteur.
 *
 * **La phrase de repli n'est pas celle de l'accueil, et ce n'est pas une divergence.** L'accueil
 * de l'original affiche une phrase **fixe** ([HOME_OFFLINE]) quand sa lecture a échoué, et il
 * jette le message reçu ; l'écran de liste, lui, **affiche** ce message. Le portage publie donc le
 * message — le serveur quand il en donne un, [GENERIC_FAILURE] sinon —, et c'est chaque écran qui
 * décide de ce qu'il en montre. Un seul champ, deux rendus fidèles.
 */
object DailyContentsText {

    /**
     * Publié quand une panne ne dit rien de lisible.
     *
     * Il ne dit pas « hors connexion », contrairement à la phrase de l'accueil, parce qu'il ne le
     * sait pas : le dépôt publie le même texte qu'une requête ait échoué faute de réseau ou que le
     * serveur ait répondu un refus sans message. Nommer une cause qu'on n'a pas mesurée enverrait
     * chercher au mauvais endroit — c'est le raisonnement déjà tenu pour `QuizText`.
     */
    const val GENERIC_FAILURE: String = "Contenu du jour indisponible."

    /**
     * Titre du carrousel quand la vignette est un rappel — **et titre de repli quand il n'y a
     * aucune vignette**.
     *
     * Les deux rôles sont ceux de l'original, qui écrit
     * `entries[index]?.type === 'invocation' ? 'Invocation du jour' : 'Rappel du jour'` : le
     * `else` couvre l'absence d'entrée comme le type rappel, donc un carrousel vide est coiffé de
     * « Rappel du jour ». C'est ce que voit l'écran avant la première lecture, et il vaut mieux
     * qu'un titre vide : la place du bloc reste annoncée.
     */
    const val HOME_REMINDER: String = "Rappel du jour"

    /** Titre du carrousel quand la vignette est une invocation. */
    const val HOME_INVOCATION: String = "Invocation du jour"

    /**
     * Le carrousel attend la première lecture du disque.
     *
     * Le point de suspension n'est pas décoratif : le dépôt publie le document du disque avant
     * tout appel réseau, donc cet état ne dure que le temps d'une lecture de fichier — et s'il
     * durait, ce serait le signe que le disque ne répond pas.
     */
    const val HOME_LOADING: String = "Chargement du contenu du jour…"

    /**
     * Le carrousel n'a **rien pu lire**, et n'a donc rien à montrer.
     *
     * C'est la phrase **fixe** de l'original, et elle nomme « hors connexion » une cause qu'elle
     * ne connaît pas : le carrousel ne reçoit que le fait qu'une panne a été publiée, pas son
     * texte. La reproduire telle quelle est une fidélité assumée — l'écran de liste, lui, affiche
     * le message du serveur. Voir le KDoc du fichier.
     */
    const val HOME_OFFLINE: String = "Contenu du jour indisponible hors connexion."

    /**
     * La lecture a abouti, et **rien n'est publié** pour aujourd'hui.
     *
     * La distinction avec [HOME_OFFLINE] est celle que le partage panne/avis protège : ce
     * message-ci est une information (« il n'y a rien »), l'autre est un aveu (« je n'ai pas pu
     * lire »). Les confondre ferait dire « aucun contenu » après une lecture ratée.
     */
    const val HOME_EMPTY: String = "Aucun contenu disponible aujourd'hui."

    /** Libellé du lecteur d'écran d'une vignette de rappel. */
    const val OPEN_REMINDER: String = "Ouvrir le rappel du jour"

    /** Libellé du lecteur d'écran d'une vignette d'invocation. */
    const val OPEN_INVOCATION: String = "Ouvrir l’invocation du jour"

    /**
     * Titre du carrousel pour [type], ou le titre de repli quand il n'y a pas de vignette.
     *
     * L'apostrophe de [OPEN_INVOCATION] et le point de suspension de [HOME_LOADING] sont ceux de
     * l'original — `l’invocation`, `…` —, et non leurs équivalents dactylographiés : ce sont des
     * caractères que la source a choisis, et les remplacer changerait le rendu sans que rien ne le
     * dise.
     */
    fun homeTitle(type: DailyContentType?): String =
        if (type == DailyContentType.INVOCATION) HOME_INVOCATION else HOME_REMINDER

    // ---------------------------------------------------------------------------------------
    // L'écran de liste et sa fiche
    // ---------------------------------------------------------------------------------------
    // Ces libellés ne sont pas dans un objet à part : les deux écrans se répondent — l'onglet de
    // l'un est le nom du type de l'autre —, et les séparer ferait chercher « Rappel » dans deux
    // fichiers. Ils sont **employés** par `feature:dailycontents` : un libellé que rien n'affiche
    // n'est pas une constante, c'est un commentaire.

    /** Onglet « rappel » de l'écran de liste. */
    const val TAB_REMINDER: String = "☀ Rappel"

    /** Onglet « invocation » de l'écran de liste. */
    const val TAB_INVOCATION: String = "☾ Invocation"

    /** Pastille qui **retire** le filtre de catégorie. */
    const val ALL_CATEGORIES: String = "Tout"

    /** Bascule de l'onglet des favoris. */
    const val MY_FAVORITES: String = "♡ Mes favoris"

    /** Pied de liste : demander la page suivante. */
    const val LOAD_MORE: String = "Afficher la suite"

    /**
     * Une page est en cours de lecture.
     *
     * **Ce n'est pas la phrase du carrousel, et ce n'est pas une négligence.** L'original écrit
     * `'Chargement…'` dans le pied de liste et `'Chargement du contenu du jour…'` dans le
     * carrousel : deux textes pour deux contextes. Les confondre ferait dire au pied de liste qu'il
     * charge « le contenu du jour » alors qu'il charge une page **filtrée**, et à la première page
     * qu'elle charge une suite qui n'existe pas encore.
     */
    const val LIST_LOADING: String = "Chargement…"

    /** Bouton de retour de l'écran de liste **et** de sa fiche. */
    const val BACK: String = "‹ Retour"

    /** Titre de l'écran de liste, et de sa fiche. */
    const val LIST_TITLE: String = "Rappels & Invocations"

    /** L'écran n'a aucun rappel à montrer — après une lecture **réussie**. */
    const val EMPTY_REMINDER: String = "Aucun rappel disponible."

    /** L'écran n'a aucune invocation à montrer — après une lecture **réussie**. */
    const val EMPTY_INVOCATION: String = "Aucune invocation disponible."

    /** Ligne qui ouvre la fiche d'un contenu, sous sa carte. */
    const val OPEN_SHEET: String = "Ouvrir la fiche ›"

    /** Titre de repli de la fiche, quand le contenu n'en porte pas, et qu'il est un rappel. */
    const val CARD_REMINDER: String = "Rappel"

    /** Titre de repli de la fiche, quand le contenu n'en porte pas, et qu'il est une invocation. */
    const val CARD_INVOCATION: String = "Invocation"

    /** Libellé de la fiche qui n'a pas de titre, pour un contenu de nature inconnue. */
    const val CARD_FALLBACK: String = "ce contenu"

    /**
     * **L'écoute n'a pas ses libellés ici, et c'est délibéré.** La fiche de l'original en porte
     * deux — « ▶ Écouter » et « Ⅱ Pause » —, mais le **geste** n'est pas porté : l'application a
     * déjà deux sources audio, la **séance d'écoute** du moushaf (`core:playback`) et le **lecteur
     * d'une récitation** (`core:audio`, tenu par l'écran des récitations), et la fiche en serait
     * une **troisième**. L'original tranche « une seule à la fois » par une variable **globale de
     * module** (`activeAudio`), et cette décision-là — comment une écoute de fiche s'arrête quand
     * la personne ouvre une séance, et l'inverse — n'appartient pas à l'écran de liste. Les deux
     * phrases arriveront avec elle, et pas avant : un libellé que rien n'affiche est une surface à
     * relire sans preuve.
     */

    /**
     * **Le geste d'enregistrement n'a pas ses libellés ici, et c'est délibéré.** La fiche de
     * l'original en porte deux — « Enregistrer ma voix » et l'avis de sauvegarde —, mais le geste
     * lui-même n'est **pas porté** : la capacité du lecteur (`RecitationRecorderCapability`) exige
     * une **plage de versets**, et une invocation n'en a pas. Écrire les deux phrases maintenant
     * ferait exactement ce que la docstring de ce fichier interdit — une surface à relire sans
     * preuve. Elles arriveront avec la variante d'invocation du lecteur, dans le module qui la
     * porte.
     */

    /** Glyphe de la bascule des favoris, quand le contenu **est** en favori. */
    const val FAVORITE_ON: String = "♥"

    /** Glyphe de la bascule des favoris, quand le contenu **n'est pas** en favori. */
    const val FAVORITE_OFF: String = "♡"

    /** Libellé du lecteur d'écran de la bascule, quand le contenu **n'est pas** en favori. */
    const val FAVORITE_ADD: String = "Ajouter aux favoris"

    /** Libellé du lecteur d'écran de la bascule, quand le contenu **est** en favori. */
    const val FAVORITE_REMOVE: String = "Retirer des favoris"

    /** Bouton de partage de la fiche. */
    const val SHARE: String = "Partager"

    /** Fermeture de la feuille du carrousel. */
    const val CLOSE: String = "Fermer"

    /**
     * Préfixe de la ligne de provenance de la **fiche**.
     *
     * Le carrousel écrit la ligne **nue** ; la fiche la préfixe. Deux rendus pour un seul mot, et
     * le mot est ici pour que les deux écrans ne puissent pas diverger sur son orthographe.
     */
    const val SOURCE_PREFIX: String = "Source : "

    /** Titre d'une carte : celui du contenu, ou le nom de son type. */
    fun cardTitle(title: String?, type: DailyContentType): String =
        title?.takeIf { it.isNotEmpty() } ?: cardTypeName(type)

    /** Nom du type, quand un contenu n'a pas de titre. */
    fun cardTypeName(type: DailyContentType): String =
        if (type == DailyContentType.INVOCATION) CARD_INVOCATION else CARD_REMINDER

    /** Libellé d'un onglet de l'écran de liste. */
    fun tabLabel(type: DailyContentType): String =
        if (type == DailyContentType.INVOCATION) TAB_INVOCATION else TAB_REMINDER

    /**
     * Libellé du lecteur d'écran qui ouvre la fiche d'un contenu.
     *
     * C'est `` `Ouvrir ${item.title ?? 'ce contenu'}` `` de l'original — le repli est le **mot** de
     * l'écran, et non le nom du type : un contenu sans titre s'annonce « Ouvrir ce contenu »,
     * jamais « Ouvrir Rappel », qui nommerait un type là où la personne entend un contenu.
     */
    fun openLabel(title: String?): String =
        "Ouvrir " + (title?.takeIf { it.isNotEmpty() } ?: CARD_FALLBACK)

    /** Libellé du lecteur d'écran de la bascule des favoris. */
    fun favoriteLabel(enabled: Boolean): String = if (enabled) FAVORITE_REMOVE else FAVORITE_ADD

    /** Glyphe de la bascule des favoris. */
    fun favoriteGlyph(enabled: Boolean): String = if (enabled) FAVORITE_ON else FAVORITE_OFF

    /**
     * Phrase de l'état vide d'une liste **filtrée par type**.
     *
     * Elle dit le type filtré — « aucun **rappel** » / « aucune **invocation** » —, et non « aucun
     * contenu » : l'écran a deux onglets, et une phrase qui ne les distingue pas ferait croire que
     * la base est vide alors que l'autre onglet est plein. L'original écrit exactement ce partage.
     */
    fun emptyListText(type: DailyContentType): String =
        if (type == DailyContentType.INVOCATION) EMPTY_INVOCATION else EMPTY_REMINDER

    /**
     * La ligne de provenance **jointe** : `source`, ou `source · référence`.
     *
     * C'est `[item.source, item.reference].filter(Boolean).join(' · ')` de l'original, écrit une
     * seule fois pour les **trois** endroits qui l'affichent — la vignette du carrousel, la fiche,
     * et le texte partagé. Les deux premiers la veulent nue ou préfixée par [SOURCE_PREFIX] ; le
     * troisième, lui, ne la joint **pas** ainsi (voir le renderer de l'écran de liste). Trois
     * copies de cette expression finiraient par diverger sur ce qui se voit : une référence
     * absente qui laisserait « Sahih · » se lirait comme une donnée manquante.
     *
     * Le filtre porte sur **les deux** parties, et non sur la seule référence : une source vide —
     * que la base interdit, mais qu'un jeu d'essai peut porter — ne doit pas produire un
     * séparateur orphelin non plus.
     */
    fun sourceLine(source: String, reference: String?): String =
        listOfNotNull(
            source.takeIf { it.isNotEmpty() },
            reference?.takeIf { it.isNotEmpty() },
        ).joinToString(" · ")
}
