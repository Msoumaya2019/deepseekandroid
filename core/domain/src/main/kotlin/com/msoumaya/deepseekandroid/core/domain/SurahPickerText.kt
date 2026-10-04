package com.msoumaya.deepseekandroid.core.domain

/**
 * Les mots et la règle de saisie du sélecteur de sourate.
 *
 * Porté depuis `src/SurahPicker.tsx`. Le composable ne fait que disposer : ce qui décide, c'est
 * **ce qu'une saisie de page accepte**, et c'est ici que ça s'éprouve.
 *
 * ## Une saisie de page n'est pas un nombre
 *
 * Le client d'origine écrit `Number(pageText)` puis vérifie `Number.isInteger(page)` et les
 * bornes 1..604. La règle est reprise, avec **une différence assumée**, mesurée sur les deux
 * langages :
 *
 *  * `Number("0x10")` vaut **16** en JavaScript — une saisie hexadécimale serait acceptée ;
 *  * `Number("1e2")` vaut **100** — une notation exponentielle aussi.
 *
 * Aucune des deux n'est atteignable depuis un clavier numérique, que les deux clients
 * demandent (`keyboardType="number-pad"` d'un côté, `KeyboardType.Number` de l'autre) : il n'y
 * a pas de touche pour `x` ni pour `e`. `toIntOrNull()` les refuse, et c'est **le bon sens de
 * l'écart** — un `16` qui vient de `0x10` n'est pas ce que la personne a voulu écrire.
 *
 * Ce qui compte est que les deux se rejoignent sur tout ce qui est atteignable : vide, espaces
 * autour, signe, zéros de tête, décimales, hors bornes. Les tests couvrent ces cas-là.
 */
object SurahPickerText {

    const val TITLE = "Choisir une sourate"
    const val SUBTITLE = "Les 114 sourates du Coran"
    const val CLOSE = "Fermer"
    const val PAGE_PLACEHOLDER = "Page 1 à 604"
    const val GO = "Aller à la page"

    /** Titre de l'alerte d'une page refusée. */
    const val INVALID_PAGE_TITLE = "Page invalide"

    /** Son corps. Repris mot pour mot du client d'origine, apostrophe comprise. */
    const val INVALID_PAGE_BODY = "Choisis une page entre 1 et 604."

    /** Le libellé du nombre de versets d'une sourate. */
    fun versesLabel(count: Int): String = "$count versets"

    /**
     * La page demandée par une saisie, ou `null` si la saisie n'en désigne aucune.
     *
     * Rendre `null` plutôt que de lever : une saisie fausse est un **cas normal** — on tape
     * trop vite — et l'appelant doit pouvoir redemander sans traiter une exception. Ce qui
     * compte est qu'aucune valeur hors bornes ne passe pour une page.
     *
     * @param totalPages le nombre de pages de la source affichée. Il est passé plutôt que lu :
     *   les sources n'ont pas toutes le même découpage, et une règle qui lirait une constante
     *   accepterait une page que la source courante ne porte pas.
     */
    fun pageFromInput(text: String, totalPages: Int = QuranSourceTransition.TOTAL_PAGES): Int? {
        val page = text.trim().toIntOrNull() ?: return null
        return if (page in 1..totalPages) page else null
    }
}
