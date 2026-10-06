package com.msoumaya.deepseekandroid.feature.quiz

import androidx.compose.runtime.Immutable
import com.msoumaya.deepseekandroid.core.domain.QuizText

// ---------------------------------------------------------------------------
// État affichable de l'écran « Quiz »
// ---------------------------------------------------------------------------
// Portage de `QuizScreen` (`src/ui/QuizScreen.tsx`).
//
// **Tout ce qui se lit est déjà résolu.** Le client d'origine calcule ses libellés dans le corps
// du composable — le statut d'une carte, le libellé d'une proposition, la phrase d'un défi
// terminé —, donc rien de tout cela n'est interrogeable sans un appareil. Ici ces décisions sont
// des chaînes et des énumérations, et `QuizRendererTest` les vérifie en quelques millisecondes.
//
// **L'écran est une des six vues, et le renderer dit laquelle.** `view` est publiée avec le
// reste : l'écran ne la recalcule pas, et `backCloses` — qui distingue « Retour » ferme l'écran
// de « Retour » remonte d'une vue — est décidée au même endroit que le titre, pour que les deux
// ne puissent pas se contredire.
//
// **Les propositions portent leur état, pas leur couleur.** Une proposition est CORRECTE,
// FAUSSE, CHOISIE ou NEUTRE ; c'est l'écran qui traduit cet état en teinte et en icône, comme il
// traduit `CounterKind` en icône dans « Progrès ». Une couleur décidée ici lierait le domaine à
// la palette.
//
// **Le chemin d'un avatar distant n'est pas transporté.** L'original affiche l'image quand elle
// existe, ce qui suppose de signer une URL dans le stockage Supabase et de charger une image
// par-dessus le réseau : c'est l'affaire de l'écran de profil, et `feature:social` a déjà tranché
// — aucun de ses états affichables ne porte de chemin, et son médaillon montre toujours
// l'initiale, qui est exactement ce que l'original affiche quand il n'y a pas d'image. Porter ici
// un chemin que personne ne lit aurait été une surface à relire sans preuve.
// ---------------------------------------------------------------------------

/** La vue courante de l'écran. */
enum class QuizView { HOME, DAILY, HISTORY, CHALLENGES, CREATE, CHALLENGE }

/**
 * État affichable de l'écran « Quiz ».
 *
 * Les charges utiles sont **facultatives** : chaque vue ne lit que la sienne. Une vue dont la
 * charge est nulle et qui n'est pas la vue courante ne signifie rien — c'est la vue courante qui
 * décide de ce qui est lu, et le renderer ne remplit que ce qu'elle demande.
 */
@Immutable
data class QuizUiState(
    /** Vrai tant que le premier état n'a pas été publié. */
    val loading: Boolean = true,

    /** Vrai quand un compte est ouvert : sans compte, rien ne peut être attribué. */
    val signedIn: Boolean = false,

    /** Vrai pendant qu'une action est en vol : la garde de double geste de l'original. */
    val busy: Boolean = false,

    /** Avis du dépôt : une action a échoué alors que l'écran avait déjà quelque chose à montrer. */
    val notice: String? = null,

    /** Panne qui laisse l'écran sans rien : c'est elle qui remplace le contenu. */
    val failure: String? = null,

    /** Vue courante. */
    val view: QuizView = QuizView.HOME,

    /** Titre de l'en-tête, qui suit la vue. */
    val title: String = QuizText.TITLE,

    /** Vrai quand « Retour » ferme l'écran, faux quand il remonte d'une vue. */
    val backCloses: Boolean = true,

    /** Accueil. */
    val home: Home? = null,

    /** Question du jour. `null` veut dire « aucune question publiée aujourd'hui ». */
    val daily: Daily? = null,

    /** Historique des réponses, du plus récent au plus ancien. */
    val history: List<HistoryLine> = emptyList(),

    /** Liste complète des défis, pour la vue « défis ». */
    val challenges: List<ChallengeLine> = emptyList(),

    /** Création d'un défi. */
    val create: Create? = null,

    /** Défi ouvert. `null` veut dire « défi absent du cache ». */
    val challenge: Challenge? = null,
)

// ---------------------------------------------------------------------------
// L'accueil
// ---------------------------------------------------------------------------

/**
 * L'accueil de l'écran.
 *
 * @param dailyStatus la phrase d'état de la carte « Question du jour » : terminée, disponible, ou
 *   aucune. Elle est décidée ici parce que trois sources la composent — la réponse du jour, la
 *   question publiée, et le jour que porte l'instantané.
 * @param recent les trois premiers défis, dans l'ordre du serveur.
 * @param noChallenge le texte affiché quand il n'y a aucun défi.
 * @param notifications l'interrupteur « Notifications Quiz » et son état.
 */
@Immutable
data class Home(
    val heading: String,
    val subtitle: String,
    val dailyStatus: String,
    val running: String,
    val recent: List<ChallengeLine>,
    val noChallenge: String,
    val notifications: NotificationLine,
)

/**
 * L'interrupteur de notifications.
 *
 * **L'original l'affiche coché quand la valeur est absente** — `selected={data.notificationsEnabled!==false}`
 * —, et l'envoi inverse ce qui est affiché : `p_enabled: notificationsEnabled===false`. Une
 * valeur absente veut donc dire « activées », et le premier appui **désactive**. C'est
 * contre-intuitif, et c'est reproduit tel quel : un interrupteur qui s'affiche éteint sur une
 * valeur absente ferait croire à un réglage perdu.
 */
@Immutable
data class NotificationLine(val label: String, val enabled: Boolean)

// ---------------------------------------------------------------------------
// La question du jour
// ---------------------------------------------------------------------------

/**
 * La question du jour.
 *
 * @param progress « ✓ 1 / 1 » quand une réponse existe, « 1 / 1 » sinon.
 * @param selectable vrai quand les propositions sont cliquables : il faut un compte, et aucune
 *   réponse encore donnée. L'original écrit `disabled={!userId}` **et** désactive dès qu'une
 *   proposition est choisie.
 * @param pending vrai quand la réponse attend le réseau. La correction n'est alors pas connue, et
 *   l'écran affiche une carte d'attente au lieu de la correction.
 * @param correction `null` tant qu'il n'y a rien à corriger.
 */
@Immutable
data class Daily(
    val progress: String,
    val category: String,
    val question: String,
    val answers: List<AnswerLine>,
    val selectable: Boolean,
    val pending: Boolean,
    val correction: Correction?,
)

/**
 * Une proposition de réponse.
 *
 * @param letter la lettre affichée devant la proposition — A, B, C, D.
 * @param chosen vrai si c'est la proposition choisie. Elle l'est encore quand la correction est
 *   révélée, et c'est ce qui la distingue d'une proposition simplement correcte.
 * @param state l'état, que l'écran traduit en teinte et en icône.
 * @param label le libellé d'accessibilité, construit ici parce qu'il dépend de `chosen`. Il est
 *   lu par `Modifier.semantics`, comme la ligne d'une révision dans le tableau de bord : le
 *   lecteur d'écran annonce « A. Qui a reçu les premières révélations ? · réponse choisie », et
 *   non le seul texte de la proposition.
 */
@Immutable
data class AnswerLine(
    val id: String,
    val letter: String,
    val text: String,
    val chosen: Boolean,
    val state: AnswerState,
    val label: String,
)

/** L'état d'une proposition. */
enum class AnswerState {
    /** Ni choisie, ni concernée par la correction. */
    IDLE,

    /** Choisie, correction pas encore révélée. */
    CHOSEN,

    /** La bonne réponse, révélée. Elle peut ne pas avoir été choisie. */
    CORRECT,

    /** Une mauvaise réponse choisie. */
    WRONG,
}

/**
 * La correction d'une question.
 *
 * @param source « Al-Baqara · 2:255 », ou `null` quand ni le titre ni la référence ne sont là.
 * @param sourceUrl l'adresse de la source, **seulement** si elle est en `https`. L'original écrit
 *   `question.sourceUrl?.startsWith('https://')` : un lien `http` ou `javascript:` n'est pas
 *   proposé. C'est reproduit, et c'est une règle de sûreté, pas une coquetterie.
 */
@Immutable
data class Correction(
    val title: String,
    val explanation: String?,
    val arabic: String?,
    val translation: String?,
    val source: String?,
    val sourceUrl: String?,
)

// ---------------------------------------------------------------------------
// L'historique et les lignes de défi
// ---------------------------------------------------------------------------

/**
 * Une ligne d'historique.
 *
 * @param label « Aujourd'hui », ou la date en toutes lettres.
 * @param tone la teinte : en attente, bonne, mauvaise. L'écran la traduit en couleur.
 */
@Immutable
data class HistoryLine(val day: String, val label: String, val status: String, val tone: Tone)

/** Teinte d'une ligne, que l'écran traduit en couleur. */
enum class Tone { MUTED, GOOD, BAD }

/**
 * Une ligne de défi, à l'accueil comme dans la liste.
 *
 * @param name le nom de **l'autre** joueur : l'adversaire si j'ai créé le défi, le créateur
 *   sinon. C'est la règle de l'original, et elle se voit à l'écran.
 * @param yourTurn vrai quand c'est à moi de jouer : la seule ligne mise en avant.
 * @param detail le compte des scores pour un défi terminé, les heures restantes sinon, et **rien**
 *   pour un défi expiré.
 */
@Immutable
data class ChallengeLine(
    val id: String,
    val name: String,
    val status: String,
    val yourTurn: Boolean,
    val detail: String,
)

// ---------------------------------------------------------------------------
// La création d'un défi
// ---------------------------------------------------------------------------

/**
 * La création d'un défi.
 *
 * @param friends les amis **acceptés** seulement : l'original filtre `status==='accepted'`, et un
 *   lien en attente n'est pas un adversaire possible.
 * @param noFriend le texte affiché quand la liste est vide.
 * @param sets les quiz thématiques, ou vide quand le serveur n'en publie aucun.
 * @param randomSelected vrai quand aucune liste n'est retenue. C'est aussi ce qui décide si le
 *   contrôle de longueur s'affiche : l'original écrit `{!quizSet&&<SegmentedControl/>}`, donc une
 *   liste thématique **cache** le choix, parce qu'elle contient exactement dix questions.
 * @param count la longueur retenue, que [QuizText.countLabel] traduit en libellé.
 * @param counts les deux choix de longueur, dans l'ordre de l'original.
 * @param canLaunch vrai quand un ami est choisi, qu'un compte est ouvert, et que rien n'est en
 *   vol. L'original écrit `disabled={busy||!friendId||!userId}`.
 */
@Immutable
data class Create(
    val friends: List<FriendLine>,
    val noFriend: String,
    val sets: List<SetLine>,
    val randomSelected: Boolean,
    val count: Int,
    val counts: List<String>,
    val rule: String,
    val canLaunch: Boolean,
    val launchLabel: String,
)

/**
 * Un ami proposé.
 *
 * @param name le nom affiché, qui retombe sur « Ami » quand le profil manque — l'original écrit
 *   `f.other?.display_name ?? 'Ami'`, et un lien peut n'avoir que quatre champs.
 * @param selected vrai pour l'ami retenu. L'original le marque en bordure et par une coche ;
 *   `AppChoice` porte le même état, donc il n'est pas résolu en chaîne ici.
 */
@Immutable
data class FriendLine(val id: String, val name: String, val selected: Boolean)

/**
 * Un quiz thématique proposé : « Sciences · Coran ».
 *
 * @param selected vrai pour la liste retenue. Le marqueur « ✓ » n'est pas dans le libellé : il est
 *   ajouté par l'écran avec [QuizText.selectedLabel], comme dans l'original où il suit le titre
 *   sans en faire partie.
 */
@Immutable
data class SetLine(val id: String, val label: String, val selected: Boolean)

// ---------------------------------------------------------------------------
// Le défi ouvert
// ---------------------------------------------------------------------------

/**
 * Le défi ouvert.
 *
 * @param heading « Créateur · Adversaire », dans cet ordre, comme l'original.
 * @param play ce que la vue montre, parmi les quatre états possibles.
 */
@Immutable
data class Challenge(val heading: String, val play: ChallengePlay)

/**
 * Les quatre états d'un défi ouvert.
 *
 * **Une interface scellée, et non quatre champs facultatifs.** Les quatre états s'excluent :
 * un défi terminé n'a pas de question suivante, un défi expiré n'a pas de score. Quatre
 * propriétés nulles laisseraient écrire des combinaisons qui n'existent pas — et l'écran devrait
 * deviner laquelle prime. `QuranState` suit déjà ce modèle.
 */
@Immutable
sealed interface ChallengePlay {

    /** Le défi est terminé : les scores, le verdict, et toutes les questions corrigées. */
    @Immutable
    data class Finished(
        val scores: List<String>,
        val verdict: String,
        val questions: List<AnsweredQuestion>,
    ) : ChallengePlay

    /** Le défi a expiré : aucun vainqueur n'est comptabilisé. */
    data object Expired : ChallengePlay

    /** C'est mon tour : la question suivante, sans correction. */
    @Immutable
    data class Next(
        val progress: String,
        val category: String,
        val question: String,
        val answers: List<AnswerLine>,
        /** Vrai quand les propositions sont cliquables : faux pendant qu'une réponse est en vol. */
        val selectable: Boolean,
        val reveal: String,
    ) : ChallengePlay

    /** J'ai répondu à tout : l'écran attend l'autre joueur. */
    @Immutable
    data class Waiting(val title: String, val waitingFor: String) : ChallengePlay
}

/**
 * Une question d'un défi terminé, avec sa correction.
 *
 * La correction est **toujours** présente ici : `correctAnswerId` n'arrive du serveur que lorsque
 * le défi est terminé, ou pour mes propres réponses. Une question terminée sans correction
 * signalerait un instantané incohérent, et l'écran n'a pas à le deviner.
 */
@Immutable
data class AnsweredQuestion(
    val question: String,
    val answers: List<AnswerLine>,
    val correction: Correction,
)
