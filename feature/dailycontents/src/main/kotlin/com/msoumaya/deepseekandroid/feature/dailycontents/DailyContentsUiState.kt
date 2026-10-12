package com.msoumaya.deepseekandroid.feature.dailycontents

import com.msoumaya.deepseekandroid.core.model.ContentCategory
import com.msoumaya.deepseekandroid.core.model.DailyContent
import com.msoumaya.deepseekandroid.core.model.DailyContentType

// ---------------------------------------------------------------------------
// L'écran de liste : son état, et ce qu'il devient à l'affichage
// ---------------------------------------------------------------------------
// Deux étages, comme pour l'accueil : [DailyListState] est ce que le ViewModel publie — il porte
// les objets du domaine —, et les types `Daily*` sont ce que l'écran **affiche**, entièrement
// résolus en texte. C'est `DailyContentsListRenderer` qui fait passer de l'un à l'autre.
//
// **Pourquoi séparer.** Un écran qui reçoit un `DailyContent` décide : quel titre quand il n'y en
// a pas, quelle ligne de provenance, quel libellé d'ouverture, quel texte partagé. Autant de
// décisions qui ne s'exécutent pas hors d'un appareil. Résolues en amont, elles se mesurent
// (`DailyContentsListRendererTest`), et le Compose ne fait plus que disposer.
//
// **Ce que le modèle ne porte pas.** L'échec de l'illustration (`imageError`) est un **état
// local** : il appartient à la composition, pas au modèle, et l'y faire entrer obligerait à
// recalculer toute la liste au premier échec d'une seule image. C'est la fiche qui le tient, et
// elle seule en dépend — mais il décide de l'existence de son en-tête, donc il n'est pas
// décoratif. Voir [DailyCard.hasTitle].
// ---------------------------------------------------------------------------

/**
 * L'état de l'écran de liste, tel que le ViewModel le publie.
 *
 * **Public, alors que les autres types `Daily*` ne le sont pas.** Le ViewModel est public — c'est
 * le type de son paramètre d'écran, et `:navigation` le compose depuis un autre module —, donc le
 * type qu'il publie l'est aussi : un état `internal` derrière une propriété publique ne compile
 * pas. Les types de **rendu** ([DailyListHeader], [DailyCard]…), eux, ne franchissent pas la
 * frontière du module et restent `internal`. C'est le partage déjà tenu par `RecitationsUiState`.
 *
 * **Le type de départ est l'invocation, et ce n'est pas un choix arbitraire.** L'original écrit
 * `useState<ContentType>('invocation')` : l'écran s'ouvre sur le second onglet. Le reproduire est
 * la seule façon de ne pas changer ce que voit la personne qui ouvre la liste.
 *
 * **`busy` démarre à `true`, et c'est une divergence assumée.** L'original l'initialise à `false` :
 * sa première image affiche donc « Aucune invocation disponible. » — une **affirmation** — avant
 * que la lecture n'ait commencé, ce que l'effet corrige à l'image suivante. Le portage démarre en
 * attente, comme `DailyContentsState.loading` : la phrase de liste vide est une information, et
 * elle ne se dit pas avant d'avoir lu. L'écart ne change rien d'autre que cette image-là.
 */
data class DailyListState(
    val type: DailyContentType = DailyContentType.INVOCATION,
    val categories: List<ContentCategory> = emptyList(),
    val selected: String? = null,
    val items: List<DailyContent> = emptyList(),
    val favorites: Boolean = false,
    val favoriteIds: Set<String> = emptySet(),
    val busy: Boolean = true,
    val error: String? = null,
    val more: Boolean = true,
    val detail: DailyContent? = null,
)

/**
 * L'en-tête de la liste : les onglets, les pastilles de catégorie, la bascule des favoris, et le
 * message d'erreur s'il y en a un.
 *
 * Les quatre voyagent ensemble parce qu'ils sont **au même endroit** : l'en-tête défile avec la
 * liste, et un état vide n'en prive pas — c'est la même raison que le titre du carrousel, qui
 * reste au-dessus d'une carte vide.
 */
internal data class DailyListHeader(
    val tabs: List<DailyTab>,
    val categories: List<DailyCategoryChip>,
    val favorites: DailyToggle,
    val error: String?,
)

/** Un onglet de type. Le libellé est déjà résolu. */
internal data class DailyTab(
    val type: DailyContentType,
    val label: String,
    val selected: Boolean,
)

/**
 * Une pastille de catégorie.
 *
 * [id] vaut `null` pour la pastille « Tout », qui **retire** le filtre au lieu d'en poser un :
 * c'est la seule dont l'identifiant n'existe pas, et un `String` vide l'aurait confondue avec une
 * catégorie sans identifiant.
 */
internal data class DailyCategoryChip(
    val id: String?,
    val label: String,
    val selected: Boolean,
)

/** Une bascule de l'en-tête, avec son libellé et son état. */
internal data class DailyToggle(
    val label: String,
    val selected: Boolean,
)

/**
 * Ce que la liste a à montrer : des lignes, ou l'état vide qui les remplace.
 *
 * **Deux formes, et non une liste plus un message.** Une liste vide **avec** un message reste une
 * liste vide : l'appelant peut oublier le message et afficher une plage blanche, qui se lit comme
 * une panne. Le carrousel a fait le même choix, pour la même raison.
 *
 * **Le pied de liste n'est pas ici.** Il se décide à part — `DailyContentsListRenderer.footer` —,
 * parce que l'original le rend **que** le corps porte des lignes ou non. Le lier au corps ferait
 * perdre le bouton sous une liste vide, que l'original affiche bel et bien.
 */
internal sealed interface DailyListBody {

    /** Aucune ligne : la phrase qui dit pourquoi. */
    data class Empty(val message: String) : DailyListBody

    /** Des lignes. */
    data class Rows(val rows: List<DailyListRow>) : DailyListBody
}

/** Une ligne : de quoi ouvrir la fiche, et la fiche elle-même. */
internal data class DailyListRow(
    /** L'identifiant du contenu, seul jeton que l'écran renvoie au ViewModel. */
    val id: String,
    /**
     * Ce que le lecteur d'écran annonce sur le geste d'ouverture — « Ouvrir <titre> ».
     *
     * Le libellé **visible**, lui, est constant (« Ouvrir la fiche › ») et vit dans
     * `DailyContentsText.OPEN_SHEET` : une constante n'est pas une décision, elle n'a rien à faire
     * dans un renderer.
     */
    val openLabel: String,
    val card: DailyCard,
)

/**
 * La fiche d'un contenu, entièrement résolue en texte.
 *
 * **L'adresse de l'illustration reste brute.** Elle demande une signature, donc une suspension :
 * la résoudre ici obligerait le renderer à être `suspend`, donc à ne plus être une fonction pure.
 * C'est la fiche qui la résout, et `DailyContentMediaRepository` qui retient le résultat.
 *
 * **L'écoute n'a pas de champ ici, et c'est délibéré.** L'original affiche un bouton de lecture
 * quand le contenu porte une adresse audio ; il n'est pas porté — voir le bloc d'absence de
 * `DailyContentsText` —, et un booléen que rien ne lit serait un commentaire déguisé en donnée.
 *
 * [source] est **facultatif**, contrairement à la vignette du carrousel : l'original n'affiche la
 * ligne de provenance de la fiche que si la source est non vide (`{!!item.source && …}`), là où la
 * vignette l'affiche sans condition. Deux rendus pour un seul mot, et c'est la source qui tranche.
 */
internal data class DailyCard(
    val title: String,
    /**
     * Vrai si le contenu porte un titre **à lui**.
     *
     * Ce n'est pas la même question que « [title] est-il non vide » : [title] porte toujours le nom
     * du type quand le contenu n'a pas de titre, et l'original n'affiche l'en-tête — donc ce nom de
     * repli — que si le contenu a un titre **ou** une illustration qui a bien voulu se charger
     * (`!!item.title || (!!item.image_url && !imageError)`). Un contenu sans titre dont
     * l'illustration échoue n'affiche donc **aucun** en-tête : sa carte commence à l'arabe. C'est
     * une bizarrerie de l'original, reproduite parce qu'elle est visible, et non corrigée :
     * afficher un titre que l'original n'affiche pas serait un écart, pas une amélioration.
     *
     * C'est la raison pour laquelle l'écran **et** la fiche doivent connaître l'échec de
     * l'illustration : la première partie du `||` est ici, la seconde est un état local.
     */
    val hasTitle: Boolean,
    val arabic: String?,
    val phonetic: String?,
    val french: String,
    val explanation: String?,
    val source: String?,
    val imageUrl: String?,
    val favorite: Boolean,
    val favoriteGlyph: String,
    val favoriteLabel: String,
    val shareLabel: String,
    val shareText: String,
)
