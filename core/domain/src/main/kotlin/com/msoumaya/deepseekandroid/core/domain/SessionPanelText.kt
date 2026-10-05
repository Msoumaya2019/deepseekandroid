package com.msoumaya.deepseekandroid.core.domain

/**
 * Les mots du panneau « Ma séance ».
 *
 * Porté depuis le panneau `sessionPanel === 'session'` de `src/App.tsx` — le titre à la ligne 507,
 * les entrées à la ligne 511. Les chaînes sont reprises **caractère pour caractère** : ce sont des
 * textes d'interface validés par le propriétaire du projet.
 *
 * ## Ce panneau est le carrefour d'une tâche, et c'est pourquoi il décide
 *
 * Il ne fait pas qu'afficher : il **décide de ce qu'une tâche permet**. Une étape de consolidation
 * se valide d'un geste ; une révision se note ; une séance d'apprentissage se clôt ou se reporte.
 * Ces gestes n'ont ni la même conséquence ni le même public, et les proposer ensemble sur le
 * bandeau aurait fait un bandeau à sept boutons.
 *
 * ## Les conditions ne sont pas quatre booléens, c'est la requête
 *
 * Le client d'origine écrit cinq conditions (`reader.consolidation`, `reviewing &&
 * !reader.consolidation`, `reader.revisionId`, `focused && !reader.consolidation`, `learning`) et
 * les répartit sur cinq blocs de rendu. Elles sont toutes des lectures de la **même** requête :
 * [StudySession.Request] porte déjà `learning`, `reviewing`, `consolidation` et `revisionId`, et
 * `focused` s'en déduit. Passer quatre booléens séparés aurait laissé croire qu'on peut n'en
 * passer que trois — et le jour où l'un manquerait, une entrée disparaîtrait en silence, ce qui
 * est exactement le défaut que ce panneau doit rendre impossible.
 *
 * ## Deux règles voisines, et il ne faut pas les confondre
 *
 * `consolidation` **exclut** [Entry.VALIDATE] : une étape de consolidation se valide par son
 * propre bouton, et lui offrir en plus « Valider une partie ou toute la séance » proposerait deux
 * chemins pour un même geste, dont un seul enregistre ce qu'il faut. C'est le
 * `focused && !reader.consolidation` du source, transcrit tel quel.
 *
 * [Entry.RELEARN] dépend de `revisionId`, et **non** de `reviewing`. C'est l'identifiant du modèle
 * de révision « legacy », celui que ce client n'écrit jamais : l'entrée n'existe donc pas
 * aujourd'hui. Elle est gardée parce que la condition est celle du source, et qu'une entrée dont
 * la condition est fausse ne s'affiche pas — il n'y a rien à retirer.
 */
object SessionPanelText {

    /** Le titre du panneau, tel qu'il s'affiche. */
    const val TITLE: String = "Ma séance"

    /**
     * Le libellé du bouton de fermeture, lu par les lecteurs d'écran.
     *
     * ## Écart assumé : le source écrit « Fermer le panneau »
     *
     * Le client d'origine a **un seul** en-tête pour ses cinq panneaux, donc un seul libellé. Le
     * portage a une feuille par panneau, et chacune **nomme ce qu'elle ferme** — c'est la règle
     * posée dans `BookmarksText`, et celle-ci la suit.
     */
    const val CLOSE: String = "Fermer la séance"

    // --- Les entrées, dans l'ordre du client d'origine ----------------------

    /**
     * Valider l'étape de consolidation proposée.
     *
     * Le libellé porte l'**échéance** — « J+3 » — et c'est elle qui dit ce qu'on valide : une
     * étape faite en retard reste l'étape qu'elle était, et le bouton doit annoncer la même chose
     * que le bandeau qui l'a proposée.
     */
    fun consolidationLabel(offset: Int?): String =
        "Valider la consolidation · J+" +
            (offset ?: StudySession.LAST_CONSOLIDATION_OFFSET)

    /** Déclarer qu'on doit réapprendre le passage : la révision repart à un jour. */
    const val RELEARN: String = "À réapprendre"

    /** Ouvrir la feuille qui choisit le point d'arrêt réel. */
    const val VALIDATE: String = "Valider une partie ou toute la séance"

    /** L'intertitre du dernier bloc, qui ne s'affiche que pour un apprentissage. */
    const val AFTER_SESSION: String = "Après ma séance"

    /**
     * Clore la séance en déclarant qu'elle n'est pas finie.
     *
     * Le verset retenu reste appris ; c'est la séance qui n'est pas terminée, et le programme la
     * reproposera. Le mot est celui du source, et il est plus juste que « Partiel » : il dit ce
     * que la personne fera, pas l'état qu'elle laisse.
     */
    const val WORK_AGAIN: String = "Je dois encore le travailler"

    /**
     * Reporter la séance entière.
     *
     * C'est le seul geste du panneau qui **n'enregistre aucun verset** : la séance repart au
     * programme, entière, comme si on ne l'avait pas ouverte. Il est donc secondaire, et il est
     * le dernier — un geste qui ne compte rien ne se place pas avant ceux qui comptent.
     */
    const val POSTPONE: String = "Reporter cette séance"

    /**
     * Les entrées du panneau, **dans l'ordre du client d'origine**.
     *
     * L'ordre n'est pas décoratif : il va du geste le plus spécifique au plus général. Une
     * consolidation d'abord — elle est le seul cas où le panneau s'ouvre du bandeau —, puis la
     * notation, puis les deux gestes de clôture. Un ordre différent ferait lire « Reporter cette
     * séance » avant la note qu'on est venu donner.
     *
     * [AFTER] n'est pas une entrée comme les autres : le rendu la déploie en **trois** éléments —
     * un intertitre et deux boutons. Elle reste une seule valeur ici parce que la décision qui la
     * gouverne (`learning`) est une seule décision, et que la séparer en trois ferait trois fois
     * la même condition.
     */
    enum class Entry {
        /** Valider l'étape de consolidation proposée. */
        CONSOLIDATION,

        /** La barre des trois notes, avec ses deux gestes d'écoute. */
        GRADES,

        /** Déclarer qu'on doit réapprendre le passage. */
        RELEARN,

        /** Ouvrir la feuille du point d'arrêt. */
        VALIDATE,

        /** L'intertitre « Après ma séance » et ses deux boutons. */
        AFTER,
    }

    /**
     * Les entrées à afficher, **dans l'ordre du client d'origine**.
     *
     * @param request la tâche que le lecteur sert. Ses quatre faits suffisent : c'est ce que le
     *   commentaire de tête explique.
     * @param available les destinations réellement branchées par l'appelant. Une entrée absente de
     *   cet ensemble est **retirée**, et non grisée — même règle que `VerseActionsText.rows`, que
     *   `BookmarksText.panelRows` et que la feuille d'options. Le jour où l'appelant ne saura plus
     *   écrire une validation de consolidation, l'entrée disparaîtra au lieu de mener nulle part.
     */
    fun entries(request: StudySession.Request, available: Set<Entry>): List<Entry> = buildList {
        if (request.consolidation && Entry.CONSOLIDATION in available) add(Entry.CONSOLIDATION)
        if (request.reviewing && !request.consolidation && Entry.GRADES in available) {
            add(Entry.GRADES)
        }
        if (request.revisionId != null && Entry.RELEARN in available) add(Entry.RELEARN)
        if (request.focused && !request.consolidation && Entry.VALIDATE in available) {
            add(Entry.VALIDATE)
        }
        if (request.learning && Entry.AFTER in available) add(Entry.AFTER)
    }

    /**
     * `true` si le panneau a au moins une entrée à proposer.
     *
     * Sert à ne pas l'ouvrir du tout quand rien n'y mène : un panneau réduit à son titre et à sa
     * poignée serait une impasse. Une lecture libre est exactement ce cas — `focused` est faux,
     * donc aucune entrée n'est retenue — et c'est pourquoi le bandeau ne s'affiche pas non plus
     * pour elle.
     */
    fun isUseful(request: StudySession.Request, available: Set<Entry>): Boolean =
        entries(request, available).isNotEmpty()
}
