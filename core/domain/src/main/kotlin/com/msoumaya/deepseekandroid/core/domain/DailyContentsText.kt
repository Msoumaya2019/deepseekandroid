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
 * **Les phrases de l'écran de liste ne sont pas encore là, et c'est délibéré.** Les deux onglets
 * (« ☀ Rappel », « ☾ Invocation »), « ♡ Mes favoris », « Tout », « Afficher la suite »,
 * « Ouvrir la fiche › », « ‹ Retour », « Rappels & Invocations » et les deux phrases de liste
 * vide arrivent **avec** cet écran. Les écrire maintenant les ferait relire sans qu'aucun rendu ne
 * les éprouve.
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
}
