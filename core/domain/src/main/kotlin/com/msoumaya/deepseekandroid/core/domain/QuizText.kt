package com.msoumaya.deepseekandroid.core.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Libellés de l'écran « Quiz ».
 *
 * Porté de `src/ui/QuizScreen.tsx`. Les chaînes sont reprises **caractère pour caractère** du
 * client d'origine, comme [SocialText] et [ProgramText] : une reformulation ferait diverger les
 * deux clients sur des mots que l'utilisateur voit, et personne ne saurait plus laquelle des
 * deux formulations est la bonne.
 *
 * **Ce que le dépôt prononce est venu le premier**, et l'écran ensuite. Une constante que
 * personne n'affiche est une surface à relire sans preuve : les libellés de l'écran sont donc
 * arrivés **avec** l'écran, et pas avant lui.
 *
 * **Les apostrophes suivent le source, et il n'est pas homogène.** `Aujourd’hui`,
 * `l’administrateur` et `n’est` portent une apostrophe typographique ; `d’espace` en porterait
 * une aussi, mais `En attente de` n'en a pas. Ce n'est pas une négligence de transcription :
 * c'est le relevé du fichier d'origine, et un test le fige — sans quoi une relecture
 * « uniformiserait » ces chaînes sans que rien ne le signale.
 */
object QuizText {

    /**
     * Message affiché quand une panne ne dit rien de lisible.
     *
     * **Il diffère de [SocialText.GENERIC_ERROR], et c'est délibéré.** L'original du Quiz écrit
     * `Connexion nécessaire. Réessaie.` là où l'espace social écrit `Une erreur est survenue.` :
     * toutes les actions du Quiz passent par le réseau — lire l'instantané, répondre, défier —,
     * donc la cause est presque toujours l'absence de connexion, et annoncer « une erreur est
     * survenue » ferait chercher au mauvais endroit.
     */
    const val GENERIC_ERROR = "Connexion nécessaire. Réessaie."

    /**
     * La fonction serveur n'existe pas : la migration du Quiz n'est pas déployée.
     *
     * C'est le message que `quizRpc` de `services/quiz.ts` prononce sur le code PostgREST
     * `PGRST202`. Il dit à qui exploite le projet ce qu'aucun autre message ne dirait : le client
     * est correct, c'est le schéma qui manque. Le confondre avec une panne de réseau enverrait
     * chercher la connexion au lieu de la migration.
     */
    const val SERVICE_MISSING = "Le service Quiz doit être activé sur le serveur."

    /** Aucun compte ouvert : il n'y a personne à qui attribuer la réponse. */
    const val SIGNED_OUT = "Connecte-toi pour enregistrer ta réponse."

    /**
     * L'appareil n'a pas joint le serveur, donc le défi n'a pas pu être créé.
     *
     * L'original prononce ces mots **avant** d'essayer, en interrogeant `NetInfo` ; ici ils sont
     * prononcés **après**, quand la requête est revenue sans avoir atteint le serveur. La phrase
     * est la même, et le fait est mieux établi : on ne devine plus l'absence de réseau, on la
     * constate. Le raisonnement complet est dans `QuizRepository.createChallenge`.
     */
    const val CHALLENGE_OFFLINE = "Connexion nécessaire pour lancer ce défi"

    // -----------------------------------------------------------------------
    // Les quatre statuts d'un défi
    // -----------------------------------------------------------------------
    // Ils vivaient **en clair** dans [Quiz.challengeStatus], et l'écran devait les reconnaître
    // pour colorer la ligne — « À toi de jouer » en vert, le reste en gris. Comparer une phrase
    // pour décider d'une couleur est le défaut que l'écran de conversation a déjà évité pour le
    // geste de modération : le jour où l'un des deux libellés change, la couleur ne suit pas et
    // rien ne le dit. Nommés ici, ils se comparent sans se recopier.
    // -----------------------------------------------------------------------

    /** Le défi est clos et le serveur l'a basculé. */
    const val CHALLENGE_DONE = "Terminé"

    /** Le défi a expiré, ou la date d'expiration est passée. */
    const val CHALLENGE_EXPIRED = "Expiré"

    /** J'ai répondu à toutes les questions, l'autre non. */
    const val CHALLENGE_WAITING = "En attente de l’ami"

    /** C'est à moi de jouer. Le seul des quatre que l'écran met en avant. */
    const val CHALLENGE_YOUR_TURN = "À toi de jouer"

    // -----------------------------------------------------------------------
    // Les titres des vues
    // -----------------------------------------------------------------------

    const val TITLE = "Quiz"
    const val TITLE_DAILY = "Question du jour"
    const val TITLE_HISTORY = "Historique"
    const val TITLE_CREATE = "Nouveau défi"
    const val TITLE_CHALLENGE = "Défi entre amis"

    // -----------------------------------------------------------------------
    // La coquille
    // -----------------------------------------------------------------------

    const val BACK = "Retour"
    const val HISTORY_LABEL = "Historique Quiz"
    const val REFRESH = "Actualiser"
    const val SIGNED_OUT_SCREEN = "Connecte-toi pour participer au Quiz."

    // -----------------------------------------------------------------------
    // Les deux cartes de l'accueil (`QuizHomeCards`)
    // -----------------------------------------------------------------------

    const val HOME_CARD_QUIZ = "Quiz"
    const val HOME_CARD_QUIZ_DONE = "Question du jour terminée"
    const val HOME_CARD_QUIZ_AVAILABLE = "Question du jour disponible"
    const val HOME_CARD_QUIZ_PENDING = "Question du jour"
    const val HOME_CARD_QUIZ_DETAIL_DONE = "1/1 aujourd’hui"
    const val HOME_CARD_QUIZ_DETAIL = "Teste tes connaissances"
    const val HOME_CARD_FRIENDS = "Amis"
    const val HOME_CARD_FRIENDS_SUB = "Défie tes amis"
    const val HOME_CARD_FRIENDS_DETAIL = "Quiz entre amis"

    // -----------------------------------------------------------------------
    // Les statistiques (`QuizStats`)
    // -----------------------------------------------------------------------

    /**
     * Titre du bloc de statistiques.
     *
     * Il vaut [TITLE] et [HOME_CARD_QUIZ], et il est pourtant distinct : l'original écrit **trois
     * fois** le mot « Quiz » — comme titre d'écran, comme titre de la carte d'accueil et comme
     * titre de ce bloc. Les replier ferait qu'une retouche de l'un renommerait les trois, et c'est
     * exactement le raisonnement de [HOME_DAILY_TITLE].
     */
    const val STATS_TITLE = "Quiz"

    const val STATS_RATE_LABEL = "Taux de réussite"

    /** « Questions du jour · 7 bonnes réponses / 9 ». */
    fun statsDaily(correct: Int, total: Int): String =
        "Questions du jour · $correct bonnes réponses / $total"

    /** « Taux de réussite · 78 % ». */
    fun statsRate(rate: Int): String = "$STATS_RATE_LABEL · $rate %"

    /** « Défis · 4 joués · 2 victoires · 1 égalités ». Le pluriel est celui de l'original. */
    fun statsChallenges(played: Int, wins: Int, ties: Int): String =
        "Défis · $played joués · $wins victoires · $ties égalités"

    // -----------------------------------------------------------------------
    // L'accueil de l'écran
    // -----------------------------------------------------------------------

    const val HOME_HEADING = "Teste tes connaissances"
    const val HOME_SUBTITLE = "Coran, Tajwid, Prophètes, Sîra et plus encore"

    /**
     * Titre de la carte « Question du jour ».
     *
     * Il vaut [TITLE_DAILY], et il est pourtant distinct : l'original écrit **deux fois** la même
     * chaîne — une fois comme titre de la vue, une fois comme titre de la carte —, et replier
     * l'une sur l'autre ferait qu'une retouche du titre de la vue changerait la carte sans que
     * personne ne l'ait demandé.
     *
     * Même raison pour [HOME_CHALLENGES_TITLE], et là la nuance se voit : la carte dit
     * « Défis entre amis » au **pluriel** quand [TITLE_CHALLENGE] dit « Défi entre amis » au
     * singulier. Deux littéraux indépendants à la source, donc deux constantes ici.
     */
    const val HOME_DAILY_TITLE = "Question du jour"
    const val HOME_CHALLENGES_TITLE = "Défis entre amis"

    /**
     * Le bouton « Historique » de l'accueil.
     *
     * Vaut [TITLE_HISTORY], et distinct pour la même raison que [HOME_DAILY_TITLE] : l'un est le
     * titre d'une vue, l'autre le libellé d'un bouton, et rien ne dit que les deux changeront
     * ensemble.
     */
    const val HOME_HISTORY_BUTTON = "Historique"

    const val HOME_DAILY_SUB = "Une nouvelle question chaque jour"
    const val HOME_DAILY_DONE = "✓ Terminée aujourd’hui"
    const val HOME_DAILY_AVAILABLE = "● Disponible aujourd’hui"
    const val HOME_DAILY_NONE = "Aucune question publiée aujourd’hui"
    const val HOME_CHALLENGES_SUB = "Affronte tes amis sur 10 questions"
    const val HOME_SECTION = "Mes défis"
    const val HOME_SEE_ALL = "Voir tout"
    const val HOME_NO_CHALLENGE = "Invite un ami à tester ses connaissances."
    const val NOTIFICATIONS = "Notifications Quiz"

    /** « 3 défis en cours ». Le singulier de l'original ne s'accorde pas : « 1 défis en cours ». */
    fun homeRunning(count: Int): String = "$count défis en cours"

    // -----------------------------------------------------------------------
    // La question du jour
    // -----------------------------------------------------------------------

    const val DAILY_PROGRESS = "1 / 1"
    const val DAILY_PROGRESS_DONE = "✓ 1 / 1"
    const val DAILY_PENDING_TITLE = "Tes réponses ont été enregistrées."
    const val DAILY_PENDING_SUB = "La correction sera disponible après synchronisation."
    const val DAILY_NONE_TITLE = "Aucune question du jour disponible."
    const val DAILY_NONE_SUB = "Les questions sont publiées par l’administrateur."
    const val BACK_TO_QUIZ = "Retour au Quiz"

    // -----------------------------------------------------------------------
    // La correction d'une question
    // -----------------------------------------------------------------------

    const val CORRECT_TITLE = "Bonne réponse !"
    const val WRONG_TITLE = "Voici la bonne réponse"
    const val SEE_SOURCE = "Voir la source"

    // -----------------------------------------------------------------------
    // L'historique
    // -----------------------------------------------------------------------

    const val HISTORY_TODAY = "Aujourd’hui"
    const val HISTORY_PENDING = "Synchronisation en attente"
    const val HISTORY_CORRECT = "✓ Bonne réponse"
    const val HISTORY_WRONG = "✕ Mauvaise réponse"
    const val HISTORY_EMPTY = "Aucune réponse enregistrée."

    // -----------------------------------------------------------------------
    // La liste des défis
    // -----------------------------------------------------------------------

    const val CHALLENGES_NEW = "Nouveau défi"
    const val CHALLENGES_RULE = "5 ou 10 questions · 48 heures"
    const val CHALLENGES_EMPTY = "Aucun défi pour le moment."

    // -----------------------------------------------------------------------
    // La création d'un défi
    // -----------------------------------------------------------------------

    const val CREATE_CHOOSE = "Choisir un ami"
    const val CREATE_FRIEND_FALLBACK = "Ami"
    const val CREATE_NO_FRIEND = "Ajoute un ami dans l’espace Amis pour lancer un défi."
    const val CREATE_RANDOM = "Questions aléatoires"
    const val CREATE_COUNT_5 = "5 questions"
    const val CREATE_COUNT_10 = "10 questions"
    const val CREATE_RULE = "Les mêmes questions pour vous deux · durée 48 heures"
    const val CREATE_BUSY = "Création…"
    const val CREATE_LAUNCH = "Lancer le défi"

    /** Le libellé d'un quiz thématique : « Sciences · Coran ». */
    fun createSet(title: String, category: String): String = "$title · $category"

    // --- Les deux longueurs d'un défi ----------------------------------------------------

    /** Longueur courte d'un défi. */
    const val COUNT_FIVE = 5

    /** Longueur longue d'un défi, et celle que l'original choisit par défaut. */
    const val COUNT_TEN = 10

    /**
     * Le libellé du contrôle de longueur pour une longueur donnée.
     *
     * **Tout ce qui n'est pas cinq s'affiche « 10 questions »**, et c'est le ternaire de
     * l'original — `count===5?'5 questions':'10 questions'` —, qui ne connaît que ces deux
     * valeurs. Le reproduire vaut mieux qu'inventer un troisième libellé : la longueur ne peut
     * venir que de ce contrôle, ou d'une liste thématique qui force dix.
     */
    fun countLabel(count: Int): String =
        if (count == COUNT_FIVE) CREATE_COUNT_5 else CREATE_COUNT_10

    /**
     * La longueur que désigne un libellé du contrôle, ou `null` s'il n'en désigne aucune.
     *
     * L'original écrit cette correspondance **dans le geste** — `v==='5 questions'?5:10` —, à
     * l'endroit où le contrôle rend son choix. Elle vit ici, et le `ViewModel` la lit au même
     * endroit que le renderer : deux copies d'un même barème finiraient par diverger, et
     * l'écran annoncerait alors une longueur que le défi n'a pas.
     */
    fun countOf(label: String): Int? = when (label) {
        CREATE_COUNT_5 -> COUNT_FIVE
        CREATE_COUNT_10 -> COUNT_TEN
        else -> null
    }

    /**
     * Un libellé coché, quand le choix est retenu.
     *
     * L'original écrit `{quizSet===q.id?' ✓':''}` **deux fois** — sur le bouton des questions
     * aléatoires et sur celui de chaque quiz thématique. Le marqueur vit donc ici, une fois : il
     * suit le libellé sans en faire partie, ce qui est pourquoi il n'est pas replié dans
     * [createSet].
     */
    fun selectedLabel(label: String, selected: Boolean): String =
        if (selected) "$label ✓" else label

    // -----------------------------------------------------------------------
    // Le défi en cours
    // -----------------------------------------------------------------------

    const val CHALLENGE_FINISHED = "Défi terminé"
    const val CHALLENGE_TIE = "Égalité"
    const val CHALLENGE_EXPIRED_TITLE = "Défi expiré"
    const val CHALLENGE_NO_WINNER = "Aucun vainqueur n’est comptabilisé."
    const val CHALLENGE_REVEAL_LATER =
        "La correction sera révélée quand vous aurez tous les deux terminé."
    const val CHALLENGE_WAITING_TITLE = "Tes réponses ont été enregistrées"
    const val CHALLENGE_MISSING = "Défi non disponible dans le cache."

    /** « Aïcha remporte le défi ». */
    fun challengeWinner(name: String): String = "$name remporte le défi"

    /** « En attente de Youssef ». */
    fun challengeWaitingFor(name: String): String = "En attente de $name"

    /** « 3 / 5 contre 2 / 5 ». */
    fun challengeScore(own: Int, other: Int, count: Int): String = "$own/$count contre $other/$count"

    /** « 12 h ». Le vide de l'original pour un défi expiré est décidé par l'écran. */
    fun challengeHours(hours: Int): String = "$hours h"

    /** « 3 / 10 · 12 h restantes ». */
    fun challengeProgress(answered: Int, count: Int, hours: Int): String =
        "$answered / $count · $hours h restantes"

    /** « A. Qui a reçu les premières révélations ? · réponse choisie ». */
    fun answerLabel(letter: String, text: String, chosen: Boolean): String =
        if (chosen) "$letter. $text · réponse choisie" else "$letter. $text"

    /** « Al-Baqara · 2:255 ». Un titre ou une référence absente ne laisse pas de séparateur. */
    fun correctionSource(title: String?, reference: String?): String =
        listOfNotNull(title, reference).filter { it.isNotBlank() }.joinToString(" · ")

    // -----------------------------------------------------------------------
    // La date d'une ligne d'historique
    // -----------------------------------------------------------------------

    /**
     * Locale des formats, **fixée** et non celle de l'appareil.
     *
     * L'original demande explicitement `fr-FR` à `toLocaleDateString` : il ne prend pas la locale
     * du téléphone. Un appareil réglé en anglais afficherait « October 5 » là où l'autre affiche
     * « 5 octobre », et les deux clients montreraient alors le même historique de deux façons.
     * Même règle que [ProgramText], [ReviewText] et [SocialText].
     */
    private val FR = Locale.FRANCE

    /** « 5 octobre » : le motif `day:'numeric', month:'long'` de l'original. */
    private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", FR)

    /**
     * Le libellé d'un jour de l'historique.
     *
     * **`null` plutôt qu'une chaîne d'erreur**, comme [SocialText.dayStamp] : l'original écrit
     * « Invalid Date » sur un jour illisible, ce qui n'apprend rien à personne. Ici le jour est
     * traité comme illisible, et l'appelant montre alors le jour **brut** — « 2026-13-45 » —,
     * qui ne prétend rien mais dit au moins de quoi il parle.
     *
     * Le jour est celui de la réponse, au format `AAAA-MM-JJ`, et **sans passage par un
     * instant** : `LocalDate.parse` ne peut pas décaler d'un jour, là où un
     * `Instant.parse(day + "T12:00:00")` le ferait selon le fuseau — l'original ajoute bien ce
     * `T12:00:00`, et c'est exactement pour cette raison.
     */
    fun historyDay(day: String): String? =
        runCatching { LocalDate.parse(day) }.getOrNull()?.format(DAY_MONTH)
}
