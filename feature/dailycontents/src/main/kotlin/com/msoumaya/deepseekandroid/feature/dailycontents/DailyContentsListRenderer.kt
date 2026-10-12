package com.msoumaya.deepseekandroid.feature.dailycontents

import com.msoumaya.deepseekandroid.core.domain.DailyContentsText
import com.msoumaya.deepseekandroid.core.model.DailyContent
import com.msoumaya.deepseekandroid.core.model.DailyContentType

// ---------------------------------------------------------------------------
// Le renderer de l'écran de liste
// ---------------------------------------------------------------------------
// Portage de `DailyContentsScreen` (`src/DailyContentsScreen.tsx:53`) et de sa fiche
// (`ContentCard`, ligne 15). C'est le seul endroit qui **décide** de ce que l'écran affiche :
// le Compose reçoit des textes résolus et ne compare rien.
//
// **Pourquoi hors de Compose.** Un écran qui reçoit un `DailyContent` décide — quel titre quand
// il n'y en a pas, quelle ligne de provenance, quel libellé d'ouverture, quel texte partagé —, et
// aucune de ces décisions ne s'exécute sans appareil. Les sortir ici les rend mesurables, et c'est
// le même partage que `HomeRenderer`, `QuizRenderer` et `RecitationsRenderer`.
//
// **Ce qui n'est pas décidé ici.** Le nombre de la fenêtre et la règle de suite vivent dans le
// domaine (`DailyContents.PAGE_SIZE`, `DailyContents.pageComplete`) : ils décident de la requête
// **et** de l'écran, donc ils ne peuvent pas être recopiés. Les libellés vivent dans
// `DailyContentsText`, avec les autres : ce fichier les assemble, il ne les invente pas.
//
// **Trois écarts relevés à la source, et reproduits.**
//
//   1. **L'onglet de départ est l'invocation.** `useState<ContentType>('invocation')` : l'écran
//      s'ouvre sur le second onglet. Voir `DailyListState`.
//   2. **Le pied de liste double la phrase de chargement.** L'original rend son
//      `ListEmptyComponent` **et** son `ListFooterComponent`, et la liste de React Native rend le
//      second **même quand le premier s'affiche** : une liste vide qui charge affiche donc
//      « Chargement… » deux fois — une fois dans la plage vide, une fois sous le bouton. C'est
//      pourquoi [footer] ne dépend pas du corps de la liste : le lier ferait perdre le bouton sous
//      une liste vide, que l'original affiche bel et bien. Le reproduire évite de décider à sa
//      place ; le jour où quelqu'un voudra l'éviter, la décision sera visible ici.
//   3. **Le texte partagé ne joint pas la provenance comme la fiche.** La fiche écrit
//      `Source : X · Y` ; le partage écrit `Source : X Y` — une espace, pas de point médian, et la
//      référence absente laisse une **espace de fin**. C'est `` `Source : ${item.source}
//      ${item.reference??''}` `` de l'original, reproduit tel quel : le texte partagé sort de
//      l'application, et le changer changerait ce que reçoivent les destinataires.
// ---------------------------------------------------------------------------

/**
 * Ce que l'écran de liste affiche, à partir de l'état que le ViewModel publie.
 *
 * Toutes les fonctions sont **pures** : ni horloge, ni coroutine, ni appareil. C'est ce qui permet
 * de les éprouver ligne à ligne.
 */
internal object DailyContentsListRenderer {

    /**
     * Les deux onglets, **dans l'ordre de l'original**.
     *
     * C'est la liste écrite en clair à la source — `(['reminder','invocation'] as const)` —, et non
     * `DailyContentType.entries`. La différence n'est pas cosmétique : coupler cet ordre à
     * l'énumération ferait apparaître un troisième onglet le jour où le `check` de la base
     * accepterait une troisième valeur, alors que l'écran d'origine n'en propose que deux. Le
     * carrousel suit la même règle, dans `DailyContents.CAROUSEL_TYPES`.
     */
    private val TYPES = listOf(DailyContentType.REMINDER, DailyContentType.INVOCATION)

    /**
     * L'en-tête : les onglets, les pastilles, la bascule des favoris, l'erreur.
     *
     * **Les pastilles sont filtrées par le type courant.** L'original écrit
     * `cats.filter(c => c.type===type)` : changer d'onglet change donc la rangée de catégories, et
     * une catégorie d'invocation n'apparaît pas sous l'onglet des rappels. Sans ce filtre, un
     * appui poserait un filtre qui ne rendrait **rien** — la liste serait vide, et rien ne dirait
     * que c'est la catégorie qui ne s'applique pas.
     *
     * **La pastille « Tout » porte `id = null`**, et son état est `selected == null` : elle
     * **retire** le filtre au lieu d'en poser un.
     */
    internal fun header(state: DailyListState): DailyListHeader = DailyListHeader(
        tabs = TYPES.map { type ->
            DailyTab(
                type = type,
                label = DailyContentsText.tabLabel(type),
                selected = type == state.type,
            )
        },
        categories = buildList {
            add(
                DailyCategoryChip(
                    id = null,
                    label = DailyContentsText.ALL_CATEGORIES,
                    selected = state.selected == null,
                ),
            )
            state.categories
                .filter { it.type == state.type }
                .forEach { category ->
                    add(
                        DailyCategoryChip(
                            id = category.id,
                            // L'icône de la base est un caractère, pas un nom : la pastille écrit
                            // `☀ Foi`, comme l'original. Un espace les sépare, et il vient d'ici.
                            label = category.icon + " " + category.name,
                            selected = category.id == state.selected,
                        ),
                    )
                }
        },
        favorites = DailyToggle(
            label = DailyContentsText.MY_FAVORITES,
            selected = state.favorites,
        ),
        error = state.error?.takeIf { it.isNotBlank() },
    )

    /**
     * Le corps de la liste : les lignes, ou l'état vide.
     *
     * **La liste vide se décide sur les lignes seules.** Le pied de page, lui, se décide à part
     * ([footer]) : c'est une mesure de l'original, dont la liste rend les deux indépendamment.
     */
    internal fun body(state: DailyListState): DailyListBody =
        if (state.items.isEmpty()) {
            DailyListBody.Empty(emptyMessage(state.busy, state.type))
        } else {
            DailyListBody.Rows(state.items.map { row(it, state.favoriteIds.contains(it.id)) })
        }

    /**
     * Le pied de liste, ou `null` quand il n'y a **pas** de suite.
     *
     * C'est `ListFooterComponent={more ? <Button …>…</Button> : null}` : il disparaît entièrement
     * quand `more` est faux, et porte « Chargement… » pendant une lecture, « Afficher la suite »
     * sinon. Il ne dépend **pas** du corps : voir l'écart n° 2 de l'en-tête du fichier.
     */
    internal fun footer(state: DailyListState): String? = when {
        !state.more -> null
        state.busy -> DailyContentsText.LIST_LOADING
        else -> DailyContentsText.LOAD_MORE
    }

    /** Une ligne : de quoi ouvrir la fiche, et la fiche. */
    internal fun row(content: DailyContent, favorite: Boolean): DailyListRow = DailyListRow(
        id = content.id,
        openLabel = DailyContentsText.openLabel(content.title),
        card = card(content, favorite),
    )

    /**
     * La fiche d'un contenu.
     *
     * **Le titre se replie sur le nom du type** — `Rappel` ou `Invocation` —, et non sur le mot de
     * l'écran : c'est `item.title??(item.type==='invocation'?'Invocation':'Rappel')`. Le repli du
     * **libellé d'ouverture**, lui, est « ce contenu » : les deux replis ne disent pas la même
     * chose, et les confondre ferait annoncer « Ouvrir Rappel », qui nomme un type là où la
     * personne entend un contenu.
     *
     * **La ligne de provenance est facultative ici**, contrairement à la vignette du carrousel :
     * l'original n'affiche celle de la fiche que si la source est non vide. Voir [DailyCard].
     */
    internal fun card(content: DailyContent, favorite: Boolean): DailyCard = DailyCard(
        title = DailyContentsText.cardTitle(content.title, content.type),
        // La **même** condition que le repli du titre, dans l'autre sens : l'original écrit
        // `!!item.title` pour décider d'afficher l'en-tête, et le repli s'affiche à l'intérieur.
        // Voir `DailyCard.hasTitle`.
        hasTitle = !content.title.isNullOrEmpty(),
        arabic = content.arabicText?.takeIf { it.isNotEmpty() },
        phonetic = content.phoneticText?.takeIf { it.isNotEmpty() },
        french = content.frenchText,
        explanation = content.explanation?.takeIf { it.isNotEmpty() },
        source = if (content.source.isNotEmpty()) {
            DailyContentsText.SOURCE_PREFIX + DailyContentsText.sourceLine(content.source, content.reference)
        } else {
            null
        },
        imageUrl = content.imageUrl?.takeIf { it.isNotEmpty() },
        favorite = favorite,
        favoriteGlyph = DailyContentsText.favoriteGlyph(favorite),
        favoriteLabel = DailyContentsText.favoriteLabel(favorite),
        shareLabel = DailyContentsText.SHARE,
        shareText = shareText(content),
    )

    /**
     * La phrase de la liste vide.
     *
     * L'ordre est celui de l'original — `busy ? 'Chargement…' : type==='reminder' ? … : …` —, et il
     * compte : une liste qui charge ne dit **pas** qu'il n'y a rien, elle dit qu'elle lit. Les
     * confondre ferait affirmer « Aucune invocation disponible. » pendant la lecture, c'est-à-dire
     * exactement le défaut que le partage panne/avis du dépôt existe pour éviter.
     *
     * Le message d'erreur, lui, n'est **pas** ici : l'original le pose dans l'en-tête, où il reste
     * visible même quand la liste porte des lignes.
     */
    internal fun emptyMessage(busy: Boolean, type: DailyContentType): String =
        if (busy) DailyContentsText.LIST_LOADING else DailyContentsText.emptyListText(type)

    /**
     * Le texte partagé : six parties, séparées par une **ligne vide**, les vides retirées.
     *
     * C'est `[title, arabic_text, phonetic_text, french_text, explanation, 'Source : …']
     * .filter(Boolean).join('\n\n')` de l'original. Le `filter(Boolean)` porte sur **les six**, et
     * il n'est pas décoratif : un titre absent laisserait sinon une ligne vide en tête du message,
     * et une explication absente un blanc entre le français et la source.
     *
     * La provenance du partage s'écrit **autrement** que celle de la fiche — voir l'en-tête du
     * fichier. Elle est la seule des six à ne jamais être vide, puisqu'elle commence par
     * `Source : `, donc elle survit toujours au filtre.
     */
    internal fun shareText(content: DailyContent): String = listOfNotNull(
        content.title?.takeIf { it.isNotEmpty() },
        content.arabicText?.takeIf { it.isNotEmpty() },
        content.phoneticText?.takeIf { it.isNotEmpty() },
        content.frenchText.takeIf { it.isNotEmpty() },
        content.explanation?.takeIf { it.isNotEmpty() },
        shareSource(content),
    ).joinToString("\n\n")

    /**
     * La provenance **du partage** : `Source : X Y`.
     *
     * C'est `` `Source : ${item.source} ${item.reference??''}` `` — une espace entre les deux, pas
     * le point médian de la fiche, et **une espace de fin** quand il n'y a pas de référence. Les
     * deux formes existent dans l'original, à deux endroits, et elles ne disent pas la même chose :
     * celle-ci sort de l'application, celle de la fiche reste à l'écran.
     */
    private fun shareSource(content: DailyContent): String =
        DailyContentsText.SOURCE_PREFIX + content.source + " " + (content.reference ?: "")

    /**
     * Vrai si le bouton de retour de la **fiche** ferme tout l'écran.
     *
     * L'original écrit `onPress={()=>initialId?onClose():setDetail(null)}` : une fiche ouverte
     * **par son identifiant** — depuis le carrousel de l'accueil, ou depuis une notification — est
     * la raison d'être de l'écran, et « Retour » en ressort donc entièrement. Une fiche ouverte
     * depuis la liste, elle, ramène à la liste. La même image, deux sorties, et rien à l'écran ne
     * les distingue : c'est la valeur d'[initialId] qui décide, et elle est connue du seul écran.
     */
    internal fun detailBackExits(initialId: String?): Boolean = initialId != null
}
