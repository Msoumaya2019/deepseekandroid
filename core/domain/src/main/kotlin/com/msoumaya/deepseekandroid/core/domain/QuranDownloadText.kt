package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.MushafSource

/**
 * Les mots et les règles d'affichage du panneau de téléchargement.
 *
 * Porté depuis `src/ui/QuranDownload.tsx`. Chaque libellé est **repris tel quel** : c'est ce
 * que la personne lit, et une reformulation pendant un portage est une modification qu'on ne
 * remarque qu'après coup, quand quelqu'un compare les deux applications.
 *
 * Le choix d'écrire ces règles ici plutôt que dans le composable tient à ce qu'elles décident :
 * quel bouton apparaît pendant une phase, et ce qu'il fait. Un bouton « Mettre en pause »
 * affiché pendant l'**installation des pages** arrêterait une écriture de 9 060 fichiers au
 * milieu d'un fichier — et c'est exactement ce que le client d'origine évite en ne le
 * proposant que pendant le téléchargement.
 */
object QuranDownloadText {

    const val TITLE = "Coran 1441"

    const val SUBTITLE =
        "Téléchargement initial · environ 98 Mo. Les pages resteront disponibles hors connexion."

    /** Nom de la phase de transfert, repris par l'étiquette d'avancement. */
    const val DOWNLOADING = "Téléchargement"

    /** Nom de la phase d'écriture, repris par l'étiquette d'avancement. */
    const val EXTRACTING = "Installation des pages"

    const val PAUSE = "Mettre en pause"
    const val START = "Télécharger et utiliser"
    const val RESUME = "Reprendre le téléchargement"
    const val BACK = "Retour"

    /**
     * Ce qui est dit quand une source est demandée alors que son paquet n'est pas installé.
     *
     * **Ce libellé est le nôtre, pas celui du client d'origine.** Il faut le dire, parce que
     * c'est une différence de conception et non une traduction : l'original n'a pas d'état
     * « il manque le paquet ». Son `ensureQuranSourcePage` (`quranSourceReady.ts:12`) appelle
     * `ensureQuranDownloaded()` — il **télécharge** au lieu de refuser, sans écran d'avancement
     * ni possibilité d'arrêter, et la seule chose qui l'empêche d'être surpris est que le
     * sélecteur déplie le panneau avant de sélectionner (`QuranDownload.tsx:8`).
     *
     * Le portage distingue les deux cas parce qu'ils ne demandent pas la même chose à la
     * personne : « il y a 102 Mo à télécharger » n'est pas « cette page est cassée ». Le
     * panneau reste le seul à conduire une installation ; ce message est ce qu'une règle dit
     * quand on lui demande d'adopter une source dont les images ne sont pas encore là.
     */
    const val NOT_INSTALLED = "Le paquet « Coran 1441 » n’est pas encore installé."

    /** Sous-titre du choix « Coran 1441 » dans la liste des présentations. */
    const val CHOICE_SUBTITLE =
        "Pages originales · téléchargement à la demande · lecture hors connexion"

    /** Titre du sélecteur de présentation. */
    const val PICKER_TITLE = "Affichage du Coran"

    /**
     * Ce qui est dit pendant qu'une source est préparée.
     *
     * Une préparation peut durer — le paquet fait 102 Mo — et un écran qui ne dit rien pendant
     * ce temps laisse croire que l'appui n'a pas été pris en compte.
     */
    const val LOADING_SOURCE = "Chargement du Coran…"

    /**
     * Ce qui est dit quand une source n'a pas pu être chargée.
     *
     * **Message fixe**, et non celui de l'exception : le client d'origine en a décidé ainsi
     * (`App.tsx:461`, où l'erreur réelle part dans la console). La raison en est qu'un message
     * technique — « les images de cette page sont manquantes » — décrit un défaut de fichiers
     * là où la personne n'a qu'un geste à refaire, et qu'il faut surtout lui dire que sa page
     * n'a pas bougé. Le message est donc le même mot pour mot dans les deux clients.
     */
    const val SWITCH_FAILED =
        "Cette source n’a pas pu être chargée. La source précédente est conservée. Réessaie."

    /**
     * Libellés des présentations, dans l'ordre du sélecteur du client d'origine.
     *
     * Le quatrième choix — « Coran 1441 » — n'est pas ici : il ne se comporte pas comme les
     * autres. Sélectionné sans que son paquet soit installé, il déplie un panneau de
     * téléchargement au lieu de changer la source. C'est `QuranSourceChoice` qui le porte.
     */
    const val MEDINA_LABEL = "Coran de Médine"
    const val TEST_LABEL = "Coran avec règles de Tajwid"
    const val SIMPLIFIED_LABEL = "Lecture simplifiée"

    /** Les présentations à choix simple, dans l'ordre. */
    val SIMPLE_PRESENTATIONS: List<Pair<MushafSource, String>> = listOf(
        MushafSource.MEDINA to MEDINA_LABEL,
        MushafSource.CORAN_TEST to TEST_LABEL,
        MushafSource.SIMPLIFIED to SIMPLIFIED_LABEL,
    )

    /**
     * L'étiquette d'avancement, ou `null` quand il n'y a rien à annoncer.
     *
     * Le pourcentage n'est **pas borné** ici, et c'est volontaire : l'installation publie déjà
     * une valeur bornée entre 0 et 1 (`QuranArchiveInstaller.fraction`), et un bornage
     * supplémentaire à l'affichage masquerait une valeur aberrante au lieu de la rendre
     * visible. La barre, elle, borne de son côté — c'est une contrainte de dessin, pas de
     * mesure.
     */
    fun progressLabel(phase: ArchivePhase, progress: Float): String? {
        if (!ArchiveProgress(phase, progress).isBusy) return null
        val name = if (phase == ArchivePhase.EXTRACTING) EXTRACTING else DOWNLOADING
        return "$name · ${Math.round(progress * 100)} %"
    }

    /** Le libellé du bouton d'action, pour une phase où il est proposé. */
    fun actionLabel(phase: ArchivePhase): String =
        if (phase == ArchivePhase.IDLE) START else RESUME

    /** Vrai si la pause est proposée : **seulement** pendant le transfert, jamais pendant l'écriture. */
    fun showsPause(phase: ArchivePhase): Boolean = phase == ArchivePhase.DOWNLOADING

    /** Vrai si le bouton d'action est proposé : dans toute phase où rien ne travaille. */
    fun showsAction(phase: ArchivePhase): Boolean = !ArchiveProgress(phase, 0f).isBusy
}
