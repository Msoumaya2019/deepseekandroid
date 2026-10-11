package com.msoumaya.deepseekandroid.core.domain

/**
 * Libellés des contenus du jour.
 *
 * **Ce fichier ne porte pour l'instant qu'une phrase, et ce n'est pas un fichier en avance sur son
 * usage.** Elle est écrite **par le dépôt** (`DailyContentRepository`), qui doit publier un texte
 * lisible quand le serveur n'en a pas donné — sans quoi une panne deviendrait un **échec muet**,
 * c'est-à-dire exactement ce que le partage panne/avis existe pour éviter. C'est la même règle que
 * `ProblemReportText.SEND_FAILED`, et la même forme : `error.restMessage() ?: <repli>`.
 *
 * **Les phrases de l'écran n'y sont pas encore, et c'est délibéré.** Les quatre phrases de
 * l'accueil — « Chargement du contenu du jour… », « Contenu du jour indisponible hors
 * connexion. », « Aucun contenu disponible aujourd'hui. » — et les libellés de l'écran de liste
 * (« ☀ Rappel », « ☾ Invocation », « ♡ Mes favoris », « Afficher la suite »…) arrivent **avec**
 * l'écran, comme ceux du Quiz et de l'espace social : une constante que personne n'affiche est une
 * surface à relire sans preuve.
 *
 * **La phrase ci-dessous n'est pas celle de l'accueil, et ce n'est pas une divergence.** L'accueil
 * de l'original affiche une phrase **fixe** (« Contenu du jour indisponible hors connexion. »)
 * quand sa lecture a échoué, et il jette le message reçu ; l'écran de liste, lui, **affiche** ce
 * message. Le portage publie donc le message — le serveur quand il en donne un, ce repli sinon —,
 * et c'est chaque écran qui décide de ce qu'il en montre. Un seul champ, deux rendus fidèles.
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
}
