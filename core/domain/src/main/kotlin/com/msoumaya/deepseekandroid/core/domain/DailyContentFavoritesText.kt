package com.msoumaya.deepseekandroid.core.domain

/**
 * Libellés des favoris d'un contenu du jour.
 *
 * **Une seule constante, et elle n'est pas un libellé d'écran.** [NO_PROJECT] est écrite par le
 * **dépôt** (`DailyContentFavoritesRepository`), qui doit lever un texte lisible quand la voie du
 * compte est demandée alors qu'aucun projet n'est configuré — c'est la même règle que
 * `DailyContentsText.GENERIC_FAILURE` et `ProblemReportText.SEND_FAILED`, et la même forme : un
 * dépôt qui lève un texte vide produirait un **échec muet**.
 *
 * **Les phrases de la fiche et de la liste ne sont pas ici, et c'est délibéré.** Les deux libellés
 * du cœur (« Ajouter aux favoris », « Retirer des favoris »), l'onglet « ♡ Mes favoris », la
 * pastille « Tout » et les phrases de liste vide arrivent **avec** les écrans qui les affichent,
 * comme celles du Quiz, de l'espace social et du carrousel sont venues avec les leurs. Les écrire
 * maintenant les ferait relire sans qu'aucun rendu ne les éprouve.
 */
object DailyContentFavoritesText {

    /**
     * Levé quand un compte est ouvert mais qu'aucun projet Supabase n'est configuré.
     *
     * Le texte est celui de l'original, dont le `db()` de `services/dailyContents.ts` lève
     * exactement ces mots quand `supabase` est absent.
     *
     * **Ce cas est aujourd'hui inatteignable, et il est écrit quand même.** Un propriétaire
     * n'existe que si une session Supabase a été ouverte, donc si un projet est configuré ; la
     * branche ne se produit pas. Rendre une liste **vide** à sa place serait pourtant le mauvais
     * choix : une liste vide se lit « ce compte n'a aucun favori », ce qui est une affirmation —
     * et une affirmation fausse le jour où la configuration changerait, sur un écran qui
     * n'afficherait plus le cœur rempli d'un contenu pourtant mis en favori. Un échec nommé vaut
     * mieux qu'un silence qui ment, même sur une branche qu'on croit morte.
     */
    const val NO_PROJECT: String = "Service de contenus non configuré."
}
