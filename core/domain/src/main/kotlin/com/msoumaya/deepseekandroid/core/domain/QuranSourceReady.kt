package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.MushafSource

/**
 * Ce qu'il faut préparer avant d'afficher une page, selon la source.
 *
 * Porté depuis `src/services/quranSourceReady.ts`. Les sources n'ont ni le même support ni le
 * même coût, et les confondre donne exactement le défaut qu'on veut éviter : **une page blanche
 * ne se distingue pas d'une page en cours de chargement**. La décision est donc prise avant
 * l'affichage, et non découverte à la lecture.
 *
 * Le domaine ne connaît ni le disque ni le réseau : l'appelant lui dit ce qu'il sait —
 * l'installation est-elle en place, quelles lignes manquent, l'image est-elle embarquée — et
 * reçoit en retour ce qu'il doit faire. C'est ce qui rend la règle éprouvable sans appareil.
 */
sealed interface PageReadiness {
    /** Les images sont là : rien à préparer. */
    data object Ready : PageReadiness

    /** La page se peint avec une police, pas avec une image : il n'y a aucun fichier à charger. */
    data object FontRendered : PageReadiness

    /** La page n'existe pas encore : le paquet de cette source doit être téléchargé. */
    data object DownloadNeeded : PageReadiness

    /** La page ne peut pas s'afficher, et voici le message à montrer. */
    data class Refused(val message: String) : PageReadiness
}

object QuranSourceReady {

    /** Les messages du client d'origine, mot pour mot : les deux clients disent la même chose. */
    const val INVALID_PAGE = "Page du Coran invalide."
    const val MISSING_PAGE_IMAGES = "Les images de cette page sont manquantes."
    const val PAGE_UNAVAILABLE = "Page du Coran indisponible."

    /**
     * La source est-elle un paquet de lignes téléchargé ?
     *
     * Une seule source l'est aujourd'hui. La question est posée par une fonction plutôt
     * qu'écrite `source == CORAN_1441` à chaque endroit : le jour où une seconde source
     * téléchargée arrive, il n'y aura qu'un endroit à changer — et un seul à oublier.
     */
    fun isZipSource(source: MushafSource): Boolean = source == MushafSource.CORAN_1441

    /**
     * Faut-il passer par le téléchargement avant d'afficher cette source ?
     *
     * C'est la porte du lecteur : tant que la réponse est vraie, l'écran montre l'installation
     * et non la page.
     */
    fun needsDownload(source: MushafSource, downloaded: Boolean): Boolean =
        isZipSource(source) && !downloaded

    /**
     * Ce qui empêche d'adopter une source, ou `null` si rien ne s'y oppose.
     *
     * C'est la règle qu'applique un changement de présentation : il vérifie la **page
     * affichée**, pas seulement la source. Un paquet peut être installé et une ligne manquer —
     * un disque plein, un fichier effacé — et adopter la source dans ce cas afficherait une
     * page à laquelle il manque une bande, sans rien dire.
     *
     * Les quatre cas sont traités nommément, et non par un `else` : le jour où une cinquième
     * disponibilité apparaîtrait, un `else` la ferait passer pour acceptable.
     */
    fun refusalMessage(readiness: PageReadiness): String? = when (readiness) {
        is PageReadiness.Refused -> readiness.message
        PageReadiness.DownloadNeeded -> QuranDownloadText.NOT_INSTALLED
        PageReadiness.Ready, PageReadiness.FontRendered -> null
    }

    /**
     * Décide de ce qu'il faut faire pour afficher [page] d'une source.
     *
     * L'ordre des cas est celui du client d'origine, et il compte : une page hors bornes est
     * refusée **avant** que la source soit regardée. Sans cela, une page 605 d'une source
     * téléchargée demanderait 102 Mo pour finir sur une erreur de fichier — et l'utilisateur
     * aurait payé le téléchargement pour rien.
     *
     * @param downloaded l'installation de la source est-elle complète ?
     * @param missingLines les lignes absentes de cette page, calculées par l'appelant : le
     *   domaine ne lit pas le disque, et c'est ce qui permet de l'éprouver sans fichier.
     * @param pageImageAvailable l'image de cette page est-elle embarquée dans l'application ?
     *   Les sources héritées et le moushaf Tajweed par images n'ont pas été importés.
     */
    fun readiness(
        source: MushafSource,
        page: Int,
        downloaded: Boolean = false,
        missingLines: List<Int> = emptyList(),
        pageImageAvailable: Boolean = true,
    ): PageReadiness {
        if (page < 1 || page > QuranSourceTransition.TOTAL_PAGES) {
            return PageReadiness.Refused(INVALID_PAGE)
        }
        return when (source) {
            MushafSource.CORAN_1441 -> when {
                // Le paquet passe avant les lignes manquantes : sans lui, « les images de cette
                // page sont manquantes » serait vrai de toutes les pages, et le message
                // enverrait chercher un défaut là où il n'y a qu'un téléchargement à faire.
                !downloaded -> PageReadiness.DownloadNeeded
                missingLines.isNotEmpty() -> PageReadiness.Refused(MISSING_PAGE_IMAGES)
                else -> PageReadiness.Ready
            }

            // Rendu par police : le même écran doit savoir peindre du texte coranique, et il n'y
            // a pas d'image à préparer.
            MushafSource.CORAN_TEST, MushafSource.SIMPLIFIED -> PageReadiness.FontRendered

            else -> if (pageImageAvailable) PageReadiness.Ready
            else PageReadiness.Refused(PAGE_UNAVAILABLE)
        }
    }
}
