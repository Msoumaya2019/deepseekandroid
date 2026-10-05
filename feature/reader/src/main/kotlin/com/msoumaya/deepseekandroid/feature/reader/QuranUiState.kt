package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.runtime.Immutable
import com.msoumaya.deepseekandroid.core.domain.QuranText
import com.msoumaya.deepseekandroid.core.model.Range

// ---------------------------------------------------------------------------
// État affichable de l'écran du Coran
// ---------------------------------------------------------------------------
// Portage de `QuranScreen` (`src/ui/MainScreens.tsx`).
//
// **Tout ce qui se lit est déjà résolu.** Les libellés, les plages de pages, la pastille de lieu
// de révélation et le compte de versets sont des chaînes : le rendu ne calcule rien, il pose.
// C'est ce qui rend la liste éprouvable sans appareil — « Pages 12 – 34 » et « Mecquoise » se
// vérifient en quelques millisecondes.
//
// **Ce qui reste dans le composable.** Trois choses, et trois seulement : la vue choisie
// (`Liste` / `Juz’` / `Hizb`), la recherche, et le filtre. Ce sont des états d'interface : ils ne
// changent rien à ce que l'application croit savoir, et le client d'origine les tenait déjà dans
// son composant. Ils sont donc passés **au** calcul, et non rangés dans l'état affichable — sans
// quoi l'écran et l'état porteraient la même vérité, et l'un des deux finirait par mentir.
// ---------------------------------------------------------------------------

/**
 * Ce que l'écran du Coran affiche.
 *
 * Les trois champs [loading], [failure] et le contenu forment la triade des écrans de ce portage :
 * tant que [loading] est vrai il n'y a rien à montrer, [failure] non nul remplace la liste par sa
 * raison, et sinon les lignes sont là.
 */
@Immutable
data class QuranUiState(
    /** Vrai tant que le référentiel coranique ou l'état du compte ne sont pas arrivés. */
    val loading: Boolean = true,

    /**
     * Message d'échec, ou `null`.
     *
     * Renseigné quand le référentiel coranique n'a pas pu être chargé : `Quran.surahs` lève, donc
     * aucune ligne n'est constructible. Une liste vide laisserait croire à un corpus vide plutôt
     * qu'à une panne.
     */
    val failure: String? = null,

    /** Le titre de la bande d'en-tête. */
    val title: String = QuranText.TITLE,

    /** Le sous-titre de la bande d'en-tête, qui dépend de la vue. */
    val subtitle: String = QuranText.SUBTITLE_LIST,

    /** Le libellé de la carte des connaissances. */
    val knownLabel: String = QuranText.KNOWN_LABEL,

    /** La référence du dernier verset connu, ou le mot qui dit qu'il n'y en a aucun. */
    val knownValue: String = QuranText.NOTHING_KNOWN,

    /** Le libellé vocal du bouton qui mène aux connaissances. */
    val editKnowledgeLabel: String = QuranText.EDIT_KNOWLEDGE,

    /** Le texte d'invite de la recherche, qui dépend de la vue. */
    val searchPlaceholder: String = QuranText.SEARCH_SURAH,

    /** Le libellé vocal du bouton de filtre. */
    val filterLabel: String = QuranText.FILTER_LABEL,

    /**
     * Les trois libellés du sélecteur de vue, dans l'ordre.
     *
     * Ils sont résolus ici plutôt que lus par l'écran à partir de `QuranText` : le sélecteur prend
     * des chaînes, et les lui donner toutes faites évite qu'un écran recompose un libellé qui
     * existe déjà.
     */
    val views: List<String> = QuranText.View.entries.map { it.label },

    /**
     * Vrai quand le filtre par lieu de révélation a un sens.
     *
     * Le client d'origine ne rend le bouton que dans la vue `Liste` : une division n'a pas de lieu
     * de révélation, et le proposer ferait chercher un réglage qui ne s'applique à rien.
     */
    val showFilter: Boolean = true,

    /** Les lignes affichées, dans l'ordre de la liste. */
    val rows: List<QuranRow> = emptyList(),

    /** Ce que la liste affiche quand aucune ligne ne correspond. */
    val empty: String = QuranText.EMPTY,

    /** Le titre de la carte du pied de liste. */
    val tajwidTitle: String = QuranText.TAJWID_TITLE,

    /** Le corps de cette carte. */
    val tajwidBody: String = QuranText.TAJWID_BODY,

    /** Le libellé du bouton flottant. */
    val lastRead: String = QuranText.LAST_READ,

    /**
     * Le verset que le bouton flottant doit ouvrir, ou `null` quand rien n'a jamais été lu.
     *
     * Le client d'origine écrit `state.lastRead?.verseId ?? 1` et ouvre donc **le premier verset**
     * du Coran quand il n'y a pas de dernière lecture. Le repli est posé par l'écran, et non ici :
     * `null` dit la vérité — « on n'a rien lu » —, là où un `1` en dur ferait passer un repli pour
     * une lecture.
     */
    val lastReadVerse: Int? = null,
)

/**
 * Une ligne de la liste : une sourate, ou une division.
 *
 * ## Pourquoi il n'y a pas de champ « genre »
 *
 * Le client d'origine attache `kind: 'surah' | 'division'` à chaque ligne, puis s'en sert pour
 * décider d'afficher la pastille. Ce portage ne reprend pas ce champ, parce que la pastille
 * **est** le genre : une sourate en a une, une division n'en a pas. Garder les deux serait tenir
 * deux vérités en accord pour la même chose, et le jour où l'une des deux bougerait, l'écran
 * peindrait une pastille vide ou cacherait une pastille pleine sans que rien ne le signale.
 *
 * ## Ce qui est nul, et pourquoi
 *
 * [arabic] est nul pour une division — le client d'origine lui donne la chaîne vide, que son
 * rendu traite comme une absence. [badge] est nul pour une division, dont le nom arabe et le lieu
 * de révélation n'existent pas.
 *
 * @param key l'identité de la ligne dans la liste. Elle mêle la vue et le rang — `Liste-18`,
 *   `Juz’-30` — comme le client d'origine : deux lignes de même rang dans deux vues différentes
 *   sont deux lignes différentes, et une clé qui les confondrait ferait sauter une ligne au
 *   changement de vue.
 * @param range la plage de versets que l'ouverture couvre. C'est elle qui décide de ce que le
 *   lecteur affiche.
 * @param openLabel le libellé vocal de la ligne, qui annonce ce que son ouverture fera.
 */
@Immutable
data class QuranRow(
    val key: String,
    val number: Int,
    val name: String,
    val meaning: String,
    val arabic: String?,
    val range: Range,
    val badge: QuranBadge?,
    val openLabel: String,
)

/**
 * La pastille d'une sourate : son lieu de révélation et son nombre de versets.
 *
 * @param place « Mecquoise » ou « Médinoise ».
 * @param isMeccan le fait, dont dépendent les **deux couleurs** de la pastille. Il est porté par
 *   la pastille plutôt que relu du libellé : comparer `place` à une chaîne ferait dépendre une
 *   couleur d'une égalité de texte, et changer un mot changerait un fond.
 * @param count le nombre de versets, déjà mis en forme — « 7 versets ».
 */
@Immutable
data class QuranBadge(val place: String, val isMeccan: Boolean, val count: String)
