package com.msoumaya.deepseekandroid.feature.recitations

import androidx.compose.runtime.Immutable
import com.msoumaya.deepseekandroid.core.model.RecitationKind

// ---------------------------------------------------------------------------
// État affichable de l'écran « Mes récitations »
// ---------------------------------------------------------------------------
// Portage de `RecitationsScreen` (`src/RecitationsScreen.tsx:49-61`).
//
// Les valeurs sont calculées **une fois**, dans le `ViewModel`, et non dans les composables :
// la fusion des deux listes, le filtre, la mise en forme des dates et le statut de chaque ligne
// parcourent les récitations et les corrections. Les appeler depuis le corps d'un composable les
// rejouerait à chaque recomposition — c'est-à-dire à chaque dixième de seconde, puisque la
// position de lecture en est une des entrées.
//
// `@Immutable` est une promesse tenue : toutes les propriétés sont `val` et de type stable.
//
// **Tout ce qui se lit est déjà résolu.** « 01/01/2026 11:00:00 · 1:00 · Synchronisé » est une
// chaîne, pas une date, une durée et un statut à mettre en forme. C'est ce qui rend les règles
// éprouvables sans appareil : « une récitation sans copie locale se lit Synchronisé » se vérifie
// en quelques millisecondes, alors qu'un libellé écrit dans un composable demanderait un test
// d'interface.
// ---------------------------------------------------------------------------

/** Ce que l'écran « Mes récitations » affiche. */
@Immutable
data class RecitationsUiState(
    /** Vrai tant que le premier état n'a pas été lu. */
    val loading: Boolean = true,

    /**
     * Vrai si un compte est ouvert.
     *
     * Faux veut dire qu'il n'y a rien à lire : la liste reste vide, et [message] dit pourquoi.
     * C'est la seule façon dont l'écran sait qu'il n'a personne devant lui — le dépôt ne publie
     * pas de booléen de connexion, il publie le compte, et un compte absent est la même chose.
     */
    val signedIn: Boolean = false,

    /** Vrai pendant un geste. L'écran désactive alors ce qui pourrait partir deux fois. */
    val busy: Boolean = false,

    /**
     * Le message affiché sous le bouton d'actualisation, ou `null`.
     *
     * Il porte **deux choses** : ce que la personne doit savoir quand elle n'est pas connectée, et
     * ce qu'un geste ou une lecture a laissé derrière lui. L'original n'a qu'un seul état de
     * message, et le portage suit : deux champs — l'un pour l'information, l'autre pour la panne —
     * se masqueraient l'un l'autre selon l'ordre des recompositions.
     */
    val message: String? = null,

    /** Libellé du filtre retenu, tel que le sélecteur segmenté l'affiche. */
    val filterLabel: String = "",

    /** Libellés du sélecteur segmenté, dans l'ordre. */
    val filterLabels: List<String> = emptyList(),

    /** Les récitations visibles, déjà fusionnées, filtrées et mises en forme. */
    val rows: List<RecitationRow> = emptyList(),

    /**
     * Vrai si l'écran doit dire « aucune récitation enregistrée ».
     *
     * **Ce n'est pas `rows.isEmpty()`.** Trois situations vident la liste, et une seule autorise
     * la phrase :
     *
     *  - **la personne n'a rien enregistré** — le vide est *établi* : connecté, aucune panne,
     *    aucune récitation. La phrase est vraie, et elle est utile ;
     *  - **la lecture a échoué** — les fichiers sont peut-être sur l'appareil. [message] porte
     *    alors la panne, et la phrase serait un mensonge de plus ;
     *  - **personne n'est connecté** — on n'a rien pu lire. [message] invite à se connecter.
     *
     * **Le filtre ne compte pas.** Il se juge sur la liste **entière**, avant filtrage : une
     * personne qui a des passages du Coran et regarde le filtre « Invocations » n'a pas « rien
     * enregistré », et l'écran n'a pas à le lui dire. L'original, lui, affiche la phrase dans les
     * quatre cas.
     */
    val empty: Boolean = false,

    // --- La récitation ouverte, s'il y en a une ---

    /** Identifiant de la récitation dépliée, ou `null`. */
    val openId: String? = null,

    /** Vrai si l'audio de la récitation ouverte joue. */
    val playing: Boolean = false,

    /** Libellé du bouton de lecture : « ▶ Réécouter » ou « Pause ». */
    val playLabel: String = "",

    /** La phrase de position, sous la barre de progression. Vide quand rien n'est ouvert. */
    val positionLabel: String = "",

    /** Remplissage de la barre, de 0 à 100. Zéro quand rien n'est ouvert. */
    val progressPercent: Float = 0f,

    /** Les retours généraux de la récitation ouverte. */
    val feedback: List<FeedbackRow> = emptyList(),

    /** Les corrections verset par verset de la récitation ouverte. */
    val corrections: List<CorrectionRow> = emptyList(),
)

/**
 * Une récitation dans la liste.
 *
 * @param title « CORAN · Al-Fâtiha 1–7 » ou « INVOCATION · Ma prononciation ».
 * @param subtitle « 01/01/2026 11:00:00 · 1:00 · Synchronisé ». Les trois morceaux sont déjà
 *   résolus, et le séparateur est le point médian de l'original.
 * @param invocationId l'invocation enregistrée, quand il y en a une : c'est ce que le bouton
 *   « Voir l'invocation » ouvre.
 * @param localOnly vrai quand le serveur ne porte pas cette récitation.
 *
 *   **Ce n'est pas un détail d'affichage, c'est ce qui décide du geste.** L'original supprime par
 *   le serveur ce qu'il y a trouvé, et par l'appareil ce qu'il n'y a pas trouvé — deux gestes
 *   différents, et se tromper laisserait une ligne sur le serveur ou un fichier sur l'appareil.
 *   C'est aussi ce qui désactive le partage : on ne partage que ce qui est arrivé.
 * @param open vrai si cette ligne est celle que la personne a dépliée.
 */
@Immutable
data class RecitationRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: RecitationKind,
    val invocationId: String?,
    val localOnly: Boolean,
    val open: Boolean,
)

/**
 * Un retour général, prêt à afficher.
 *
 * @param comment le commentaire du relecteur, ou le repli « Commentaire vocal du professeur ».
 * @param voicePath l'adresse de la correction vocale, quand elle existe. C'est elle qui décide
 *   si le bouton « Écouter le professeur » apparaît.
 */
@Immutable
data class FeedbackRow(
    val id: String,
    val title: String,
    val comment: String,
    val voicePath: String?,
)

/**
 * Une correction de verset, prête à afficher.
 *
 * @param label « Al-Fâtiha · verset 3 ».
 * @param comment le commentaire du relecteur, ou le repli « À retravailler ».
 * @param date le jour de la correction, ou `null` si l'instant est illisible. L'original ne
 *   montre pas l'heure d'un commentaire, et le portage non plus.
 */
@Immutable
data class CorrectionRow(
    val id: String,
    val label: String,
    val comment: String,
    val voicePath: String?,
    val date: String?,
)
