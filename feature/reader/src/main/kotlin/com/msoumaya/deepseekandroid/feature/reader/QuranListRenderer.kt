package com.msoumaya.deepseekandroid.feature.reader

import com.msoumaya.deepseekandroid.core.domain.Program
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.QuranText
import com.msoumaya.deepseekandroid.core.domain.StudyProgressCalculator
import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.Division
import com.msoumaya.deepseekandroid.core.model.Surah
import com.msoumaya.deepseekandroid.core.model.effectiveReader

// ---------------------------------------------------------------------------
// La liste des sourates, des juz’ et des hizb
// ---------------------------------------------------------------------------
// Portage de la partie calcul de `QuranScreen` (`src/ui/MainScreens.tsx`).
//
// **Pourquoi ce calcul est séparé.** Il ne lit que l'état du compte et le référentiel, et il ne
// produit que du texte. Il n'a donc besoin ni d'un appareil, ni d'une coroutine, ni d'une horloge :
// « Pages 12 – 34 », « Mecquoise » et « 7 versets » se vérifient en quelques millisecondes, sur le
// vrai corpus. C'est la même raison qui a fait sortir `ReviewDashboardRenderer` du tableau de bord.
//
// **Ce que le rendu ne fait pas.** Il ne garde aucun état : la vue, la recherche et le filtre lui
// sont **passés**. Le client d'origine les tenait dans son composant, et les y laisser évite que
// l'écran et l'état affichable portent la même vérité.
//
// **Le filtre ne s'applique qu'aux sourates.** Une division n'a pas de lieu de révélation : le
// client d'origine ne teste le filtre que dans la branche des sourates, et ne rend même pas le
// bouton ailleurs. Le filtre est donc ignoré pour les deux autres vues — ce n'est pas un oubli,
// c'est la règle de l'original, et l'écran la rend visible en cachant le bouton.
// ---------------------------------------------------------------------------

/**
 * Calcule ce que l'écran du Coran affiche.
 *
 * **Précondition : le référentiel coranique est chargé.** `Quran.surahs` lève sur un référentiel
 * vide, et c'est voulu : cet objet ne rend pas un état vide à la place d'une panne, il refuse de
 * calculer. C'est à l'appelant de ne l'invoquer que lorsque le référentiel est prêt — ce que fait
 * `QuranScreen`, qui affiche une attente tant qu'il ne l'est pas.
 *
 * @param state l'état du compte : connaissances, source de moushaf, thème, dernière lecture.
 * @param view la présentation demandée — les sourates, les juz’, ou les hizb.
 * @param query la recherche saisie. La comparaison est **insensible à la casse** et porte sur des
 *   champs différents selon la vue, comme dans l'original : une sourate se cherche par son numéro,
 *   son nom, son sens et son nom arabe ; une division par son numéro, le mot de sa vue, et le nom
 *   de la sourate où elle commence.
 * @param filter le lieu de révélation retenu. Il ne s'applique qu'à la vue
 *   [QuranText.View.LIST] — voir la note du fichier.
 */
internal object QuranListRenderer {

    fun render(
        state: AppState,
        view: QuranText.View,
        query: String,
        filter: QuranText.Filter,
    ): QuranUiState {
        // La source affichée décide du **découpage** : les deux exemplaires du projet ne placent
        // pas les mêmes versets au même endroit — 56 versets sur 6 236 changent de page — donc une
        // plage de pages calculée dans l'autre découpage annoncerait des pages fausses.
        val studySource = StudyProgressCalculator.sourceKey(state.effectiveReader.mushaf)

        return QuranUiState(
            loading = false,
            subtitle = QuranText.subtitle(view),
            knownValue = knownValue(state),
            searchPlaceholder = QuranText.search(view),
            showFilter = view == QuranText.View.LIST,
            rows = rows(view, query, filter, studySource),
            lastReadVerse = state.lastRead?.verseId,
        )
    }

    // -----------------------------------------------------------------------
    // La carte des connaissances
    // -----------------------------------------------------------------------

    /**
     * La référence du dernier verset connu, ou le mot qui dit qu'il n'y en a aucun.
     *
     * Le dernier connu est le **plus grand identifiant** de verset validé, comme dans l'original
     * (`Math.max(...known)`), et non le plus récemment validé : c'est une position dans le corpus,
     * pas une date.
     *
     * Un identifiant hors corpus est **écarté** plutôt que de faire tomber l'écran. L'état peut en
     * porter : `knowledge` est indexé par des clés numériques venues d'un autre appareil ou d'une
     * autre source, et `Quran.verseAt` lève sur un identifiant qu'il ne connaît pas. Le repli est
     * le mot de l'absence — « Aucun verset validé » —, ce qui est la vérité affichable : aucun
     * verset **du corpus affiché** n'est connu.
     */
    private fun knownValue(state: AppState): String {
        val dernier = Program.memorizedIds(state)
            .filter { it in 1..Quran.verses.size }
            .maxOrNull()
            ?: return QuranText.NOTHING_KNOWN
        return QuranText.knownVerse(
            surah = Quran.surahAt(dernier).name,
            ayah = Quran.verseAt(dernier).ayah,
        )
    }

    // -----------------------------------------------------------------------
    // Les lignes
    // -----------------------------------------------------------------------

    private fun rows(
        view: QuranText.View,
        query: String,
        filter: QuranText.Filter,
        studySource: String,
    ): List<QuranRow> {
        // La casse est repliée **une fois** de chaque côté, comme le `toLowerCase()` de
        // l'original : replier dans la boucle recalculerait la même chaîne pour chaque ligne.
        val aiguille = query.lowercase()

        return when (view) {
            QuranText.View.LIST -> Quran.surahs
                .filter { surahRetenue(it, aiguille, filter) }
                .map(::surahRow)

            QuranText.View.JUZ -> divisions(Quran.juzs, view, aiguille, studySource)
            QuranText.View.HIZB -> divisions(Quran.hizbs, view, aiguille, studySource)
        }
    }

    /**
     * Une sourate passe-t-elle la recherche **et** le filtre ?
     *
     * Les deux conditions sont celles de l'original, dans le même ordre : la recherche d'abord,
     * le filtre ensuite. L'ordre ne change pas le résultat, mais il dit ce qui est cherché — le
     * filtre ne s'applique qu'à ce qui a déjà été trouvé.
     */
    private fun surahRetenue(surah: Surah, aiguille: String, filter: QuranText.Filter): Boolean {
        val champs = "${surah.number} ${surah.name} ${surah.meaning} ${surah.arabic}".lowercase()
        if (!champs.contains(aiguille)) return false
        return when (filter) {
            QuranText.Filter.ALL -> true
            // `isMeccan` est **nullable** dans le corpus, alors que le client d'origine le traite
            // comme un booléen. Un `null` y est donc faux : la sourate est médinoise, et c'est
            // exactement ce que dit `== true` — un `null` ne peut pas être mecquois.
            QuranText.Filter.MECCAN -> surah.isMeccan == true
            QuranText.Filter.MEDINAN -> surah.isMeccan != true
        }
    }

    private fun surahRow(surah: Surah): QuranRow = QuranRow(
        key = "${QuranText.View.LIST.label}-${surah.number}",
        number = surah.number,
        name = surah.name,
        meaning = surah.meaning,
        // Le nom arabe vide est une **absence**, pas un mot vide : le client d'origine teste sa
        // vérité (`item.arabic && …`) et n'affiche donc rien. Le porter tel quel ferait réserver
        // 24 % de la largeur à une colonne sans texte.
        arabic = surah.arabic.ifEmpty { null },
        range = surah.range,
        badge = QuranBadge(
            place = QuranText.place(surah.isMeccan == true),
            isMeccan = surah.isMeccan == true,
            count = QuranText.verseCount(surah.count),
        ),
        openLabel = QuranText.open(surah.name),
    )

    /**
     * Les lignes d'une vue de division.
     *
     * Le filtre n'y intervient pas : une division n'a pas de lieu de révélation.
     *
     * ## Pourquoi elle est visible du test, et non privée
     *
     * **Mesuré sur les données livrées : aucune** des 30 juz ni des 60 hizb ne change de page
     * entre les trois découpages du projet — moushaf de Médine, composition typographique, et
     * « Coran 1441 ». Toutes leurs bornes tombent sur des versets que les trois placent pareil,
     * alors que 56 versets du Mushaf, eux, changent de page. Le paramètre [studySource] est donc
     * **inexerçable par `render`** sur ces données : une division fabriquée dont une borne tombe
     * sur le verset 746 — page 121 côté moushaf, page 120 côté composition — est la seule façon
     * d'éprouver que le découpage est bien suivi. C'est la même raison qui a fait sortir
     * `TestPageIndex.pageFor` : un contrôle qui ne passe jamais ne prouve rien.
     *
     * @param source les divisions de la vue — `Quran.juzs` ou `Quran.hizbs`.
     * @param view la vue, qui donne à la fois le mot du nom (« Juz’ 30 ») et celui de la recherche.
     */
    internal fun divisions(
        source: List<Division>,
        view: QuranText.View,
        aiguille: String,
        studySource: String,
    ): List<QuranRow> = source
        .filter { division ->
            // La sourate **où la division commence** fait partie des champs cherchés : c'est ce qui
            // permet de retrouver « Juz’ 30 » en tapant « An-Naba », comme dans l'original.
            val champs = "${division.number} ${view.label} ${Quran.surahAt(division.start).name}"
            champs.lowercase().contains(aiguille)
        }
        .map { division ->
            val nom = QuranText.divisionName(view, division.number)
            QuranRow(
                key = "${view.label}-${division.number}",
                number = division.number,
                name = nom,
                // Les pages sont calculées dans le découpage de la **source affichée**, et aux deux
                // bouts : `studyPage` n'est pas linéaire, donc déduire la fin du début donnerait un
                // compte faux sur les divisions qui chevrochent une page.
                meaning = QuranText.divisionPages(
                    first = StudyProgressCalculator.studyPage(division.start, studySource),
                    last = StudyProgressCalculator.studyPage(division.end, studySource),
                ),
                arabic = null,
                range = division.range,
                badge = null,
                openLabel = QuranText.open(nom),
            )
        }
}
