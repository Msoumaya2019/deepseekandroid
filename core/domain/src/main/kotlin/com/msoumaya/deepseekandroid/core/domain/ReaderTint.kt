package com.msoumaya.deepseekandroid.core.domain

/**
 * Ce qui colore un verset, et dans quel ordre.
 *
 * ## Pourquoi cette règle n'est pas restée dans la vue
 *
 * Elle y était écrite sous forme de `when` sur des couleurs — donc **inatteignable** : une
 * fonction privée, dans un composable, ne se déclenche pas sans hôte Compose, et le projet n'a
 * aucun outillage de test d'interface. Or c'est bien une règle, et une règle mesurable : un
 * verset peut porter deux marques à la fois, et une seule couleur peut l'emporter. La sortir du
 * dessin la rend éprouvable, et laisse à la vue ce qui lui revient — traduire un cas en
 * couleur.
 *
 * ## L'ordre retenu
 *
 * `DIFFICULT`, puis `BOOKMARK`, puis `PLAYING` : c'est l'ordre de `MushafPage.tsx`, le rendu
 * dont ce portage descend.
 *
 * Il faut savoir que **la source se contredit** : son second rendu, celui de l'écran immersif
 * (`coranTest/html.ts`), ordonne `DIFFICULT`, puis `PLAYING`, puis `BOOKMARK`. Un verset à la
 * fois signet et en cours d'écoute n'y a donc pas la même couleur que dans le rendu principal.
 * Le portage suit le rendu principal, et le dit ici plutôt que de laisser croire à un ordre
 * unique : le jour où l'écran immersif sera porté, son ordre devra être décidé sciemment.
 */
enum class TintKind { DIFFICULT, BOOKMARK, PLAYING }

object ReaderTint {

    /**
     * La marque qui colore [verseId], ou `null` si le verset n'est ni difficile, ni en signet,
     * ni en cours d'écoute.
     *
     * Un verset qui cumule plusieurs marques reçoit **la première** dans l'ordre de
     * [TintKind] : c'est la règle de priorité, et c'est tout l'objet de cette fonction.
     *
     * Aucune borne n'est vérifiée, et aucun identifiant n'est validé : la fonction répond pour
     * n'importe quel entier, y compris hors du Coran. Un verset qui n'existe pas n'est
     * simplement jamais égal à [playingVerse] ni présent dans les deux ensembles, et rend donc
     * `null` — il n'y a rien à refuser ici.
     */
    fun kindOf(
        verseId: Int,
        playingVerse: Int?,
        bookmarkIds: Set<Int>,
        difficultIds: Set<Int>,
    ): TintKind? = when {
        verseId in difficultIds -> TintKind.DIFFICULT
        verseId in bookmarkIds -> TintKind.BOOKMARK
        verseId == playingVerse -> TintKind.PLAYING
        else -> null
    }
}
