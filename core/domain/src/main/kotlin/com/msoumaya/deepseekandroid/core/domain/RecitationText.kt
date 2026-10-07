package com.msoumaya.deepseekandroid.core.domain

/**
 * Les mots de la récitation.
 *
 * Trois phrases, toutes **recopiées du client d'origine** — `src/services/recitations.ts` —, et
 * aucune n'est inventée ici : une phrase réécrite serait un écart d'interface invisible au
 * portage, et deux clients qui disent la même chose de deux façons finissent par ne plus dire la
 * même chose.
 *
 * Ce fichier ne porte que ce dont la **file de dépôt** a besoin. Les mots des écrans — le titre
 * de la liste, les états vides, les boutons — viendront s'y ajouter avec eux, comme `ReviewText`
 * et `ProgressText` ont grandi avec leurs écrans.
 */
object RecitationText {

    /**
     * Aucun compte ouvert.
     *
     * L'original l'écrit pour les gestes qui n'ont pas de sens sans compte (`Connecte-toi.`) ;
     * c'est la même phrase qui convient à un enregistrement, qui n'appartient à personne tant
     * qu'il n'y a personne.
     */
    const val SIGNED_OUT: String = "Connecte-toi."

    /**
     * Le passage demandé n'existe pas, ou il manque un propriétaire, une source ou une durée.
     *
     * **Une seule phrase pour les quatre causes, comme l'original.** `Recitations.saveProblem`
     * les distingue pour que la règle soit lisible et éprouvable ; l'original, lui, lève la même
     * erreur pour les quatre. Donner ici quatre phrases serait un écart d'interface assumé par
     * personne — et le cas est de toute façon inatteignable depuis l'écran, qui ne propose que
     * des passages du Coran et une durée mesurée.
     */
    const val INVALID_RECORDING: String = "Récitation ou passage invalide."

    /**
     * Le fichier audio a disparu de l'appareil.
     *
     * Le cas se produit réellement : le système efface les fichiers d'un dossier de cache, et une
     * restauration de sauvegarde peut rendre le registre sans les fichiers. La synchronisation
     * marque alors la récitation en échec plutôt que d'écrire une ligne distante vers un fichier
     * que personne ne pourra jamais écouter.
     */
    const val LOCAL_FILE_MISSING: String = "Fichier local introuvable."
}
