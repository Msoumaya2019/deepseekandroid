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
     * **Ce n'est pas `rows.isEmpty()`.** Quatre situations vident la liste, et une seule autorise
     * la phrase :
     *
     *  - **la personne n'a rien enregistré** — le vide est *établi* : connecté, aucune panne,
     *    aucune récitation. La phrase est vraie, et elle est utile ;
     *  - **la lecture a échoué** — les fichiers sont peut-être sur l'appareil. [message] porte
     *    alors la panne, et la phrase serait un mensonge de plus ;
     *  - **personne n'est connecté** — on n'a rien pu lire. [message] invite à se connecter ;
     *  - **une lecture est en vol** — le registre local est publié **avant** la liste distante,
     *    donc entre les deux la liste est vide parce qu'on attend. La phrase serait fausse, et
     *    elle le serait au pire moment : à l'ouverture de l'écran.
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

    /**
     * Vrai si un lecteur audio est disponible.
     *
     * **C'est ce qui décide si l'écran offre l'écoute.** Faux, les boutons de lecture et d'avance,
     * et la barre de progression, ne sont pas affichés du tout : un bouton de lecture qui ne joue
     * rien est le geste mort que ce dépôt s'interdit, et il le serait ici pour la raison la plus
     * banale — aucun lecteur n'a été fourni au conteneur, ce qui est le cas dans les tests.
     */
    val canListen: Boolean = false,

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

    // --- Le partage, s'il est possible ---

    /**
     * Vrai si un partage est possible **en principe** — c'est-à-dire si la couche sociale est
     * là.
     *
     * Faux, l'écran n'offre **aucun** bouton de partage : il n'y aurait personne à qui envoyer,
     * et l'original ne partage pas non plus sans ami (`friends` y est vide). C'est la même garde
     * que [canListen], et pour la même raison — un bouton qui ne mène nulle part est le geste
     * mort que ce dépôt s'interdit.
     */
    val canShare: Boolean = false,

    /**
     * La récitation dont le **choix d'ami** est ouvert, ou `null`.
     *
     * C'est l'identifiant de la **récitation**, et non d'une amitié : c'est le bouton de la ligne
     * qui ouvre le choix, et c'est sous cette ligne que la liste des destinataires s'affiche. Le
     * partage lui-même, en revanche, part par un **lien** — voir [RecitationFriend.linkId].
     *
     * Le choix disparaît quand la personne déplie autre chose : le rendu ne le publie que s'il
     * porte sur la ligne ouverte.
     */
    val sharingId: String? = null,

    /**
     * Les destinataires possibles, déjà réduits aux amitiés **acceptées**.
     *
     * La réduction vit dans `Social.shareRecipients`, où elle s'éprouve : c'est la règle du
     * serveur, dont la fonction `can_play_shared_recitation` n'ouvre l'enregistrement qu'à un
     * lien accepté. La liste est **complète** — pas de plafond, contrairement à l'écran des amis.
     */
    val friends: List<RecitationFriend> = emptyList(),

    /**
     * La phrase qui demande confirmation avant d'envoyer, ou `null` quand il n'y a rien à
     * confirmer.
     *
     * **Le partage n'est pas immédiat.** L'original ouvre une boîte de dialogue — « Partager cette
     * récitation ? », puis « Seul <ami> pourra écouter <référence> tant que vous restez amis. » —,
     * et n'envoie qu'à la confirmation. Le dépôt n'a pas d'outillage de dialogue : la phrase est
     * donc **calculée** ici, et l'écran la pose dans la ligne, comme la confirmation de
     * suppression.
     *
     * Nulle, il n'y a rien à confirmer : aucun ami n'a été choisi, ou la ligne n'est plus celle
     * dont le choix est ouvert.
     */
    val shareBody: String? = null,
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

    /**
     * Vrai si l'écran **offre** le partage pour cette ligne.
     *
     * Faux pour une invocation : l'original **retire** le bouton, il ne le désactive pas — aucun
     * dépôt ne rendrait une invocation partageable, et un bouton qui ne s'activerait jamais serait
     * un geste mort de plus.
     */
    val shareOffered: Boolean = false,

    /**
     * Vrai si le partage peut **aboutir** — donc si le bouton est actif.
     *
     * Faux tant que la récitation n'est pas arrivée sur le serveur : le partage écrit
     * l'identifiant d'une ligne distante, et un enregistrement encore local n'en a pas. Le bouton
     * reste **visible** et s'active de lui-même à la prochaine lecture — c'est l'original, et le
     * sous-titre dit déjà « En attente ».
     */
    val shareable: Boolean = false,

    /**
     * La référence du passage, telle que le partage la nomme : « Al-Fâtiha 1–7 ».
     *
     * **Sans le préfixe « CORAN · »** du titre : c'est l'original, qui partage `reference(start,
     * end)` et non le titre de la ligne. Le préfixe du message — « Récitation vocale » — dit, lui,
     * qu'il s'agit d'un enregistrement.
     *
     * Vide quand les bornes manquent : une ligne du Coran sans bornes n'a pas de référence à
     * partager.
     */
    val shareLabel: String = "",
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

/**
 * Un destinataire possible d'une récitation partagée, prêt à afficher.
 *
 * @param linkId l'**amitié** qui portera le partage, et non le compte. Le serveur attend un lien :
 *   un partage s'écrit dans une conversation, et une conversation est un lien — le déclencheur
 *   `validate_recitation_message` refuse un partage sans lien, et le refuse aussi dans un cercle.
 * @param name le nom affiché, ou le repli « Ami ». L'original en a un
 *   (`friend.other?.display_name ?? 'Ami'`) : une amitié dont le profil n'est pas lisible reste
 *   proposée, parce qu'on ne peut pas la retirer du choix sans retirer l'ami.
 */
@Immutable
data class RecitationFriend(
    val linkId: String,
    val name: String,
)
