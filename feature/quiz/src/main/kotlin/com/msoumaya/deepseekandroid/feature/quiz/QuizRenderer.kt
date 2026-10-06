package com.msoumaya.deepseekandroid.feature.quiz

import androidx.compose.runtime.Immutable
import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.Quiz
import com.msoumaya.deepseekandroid.core.domain.QuizText
import com.msoumaya.deepseekandroid.core.model.DailyResponse
import com.msoumaya.deepseekandroid.core.model.FriendLink
import com.msoumaya.deepseekandroid.core.model.FriendLinkStatus
import com.msoumaya.deepseekandroid.core.model.QuizChallenge
import com.msoumaya.deepseekandroid.core.model.QuizQuestion
import com.msoumaya.deepseekandroid.core.model.QuizSnapshot
import kotlin.math.ceil

// ---------------------------------------------------------------------------
// Calcul de l'écran « Quiz »
// ---------------------------------------------------------------------------
// Portage de `QuizScreen` (`src/ui/QuizScreen.tsx`). Tout le calcul vit ici parce qu'il est pur :
// il s'éprouve sans coroutine, sans appareil et sans horloge — `now` et `day` sont injectés.
//
// **Ce que le renderer ne fait pas.** Il n'écrit rien et il ne lit rien : l'instantané, le
// compte, la vue choisie et la liste d'amis lui sont **donnés**. Il ne connaît ni le réseau ni le
// stockage, donc il ne dépend pas de `core:data` — c'est le `ViewModel` qui extrait les amis de
// `SocialState` et les lui passe en `List<FriendLink>`.
//
// **La frontière entre ce qui est résolu ici et ce que l'écran lit directement.** Tout ce qui
// dépend des données est résolu : un statut, une phrase de score, un libellé d'accessibilité.
// Les libellés **fixes** — un titre de section, le texte d'un bouton — sont lus par l'écran
// dans [QuizText] : les recopier dans l'état n'ajouterait rien à éprouver.
//
// **Deux règles de l'original sont reproduites bien qu'elles surprennent**, et chacune est
// signalée à l'endroit où elle vit : l'interrupteur de notifications s'affiche **coché** sur une
// valeur absente, et un défi dont la date d'expiration est illisible est « Expiré » — parce que
// `Dates.parseIsoMillis` rend `0` sur une date abîmée.
// ---------------------------------------------------------------------------

/**
 * Ce que l'écran a choisi : la vue courante, et les sélections qui la précisent.
 *
 * Regroupées en un objet plutôt qu'en six paramètres, parce que ces valeurs voyagent ensemble —
 * le `ViewModel` les publie d'un bloc, et `render` les reçoit d'un bloc.
 */
@Immutable
internal data class QuizSelection(
    val view: QuizView = QuizView.HOME,
    /** Jour consulté depuis l'historique, ou `null` pour la question du jour. */
    val historyDay: String? = null,
    /** Défi ouvert, ou `null`. */
    val challengeId: String? = null,
    /** Ami choisi pour un nouveau défi, ou `null`. */
    val friendId: String? = null,
    /** Quiz thématique choisi, ou `null` pour des questions aléatoires. */
    val quizSetId: String? = null,
    /** Longueur choisie pour un nouveau défi. */
    val count: Int = QuizRenderer.DEFAULT_COUNT,
)

/** Calcul de l'écran « Quiz ». */
internal object QuizRenderer {

    /** Longueur par défaut d'un défi : `useState<5|10>(10)` dans l'original. */
    const val DEFAULT_COUNT = 10

    /**
     * Ce que fait « Retour » depuis une vue.
     *
     * Trois issues, et non deux : **fermer** l'écran depuis l'accueil, **remonter** d'un défi
     * vers la liste des défis, et **rentrer** à l'accueil depuis les quatre autres vues — en
     * oubliant le jour consulté, sans quoi revenir à l'historique rouvrirait la question d'un
     * autre jour.
     *
     * @param close vrai quand l'écran se ferme.
     * @param view la vue d'arrivée, quand il ne se ferme pas.
     * @param forgetDay vrai quand le jour consulté doit être oublié.
     */
    @Immutable
    data class Back(val close: Boolean = false, val view: QuizView = QuizView.HOME, val forgetDay: Boolean = false)

    /**
     * La vue d'ouverture, décidée par les deux arguments de la route.
     *
     * L'ami l'emporte sur le défi quand les deux sont fournis : c'est l'ordre du ternaire de
     * l'original, `initialFriend?'create':initialChallenge?'challenge':'home'`.
     */
    fun initialView(friendId: String?, challengeId: String?): QuizView = when {
        friendId != null -> QuizView.CREATE
        challengeId != null -> QuizView.CHALLENGE
        else -> QuizView.HOME
    }

    /** Ce que fait « Retour » depuis [from]. */
    fun back(from: QuizView): Back = when (from) {
        QuizView.HOME -> Back(close = true)
        QuizView.CHALLENGE -> Back(view = QuizView.CHALLENGES)
        else -> Back(view = QuizView.HOME, forgetDay = true)
    }

    /** Le titre de l'en-tête, qui suit la vue. */
    fun title(view: QuizView): String = when (view) {
        QuizView.DAILY -> QuizText.TITLE_DAILY
        QuizView.HISTORY -> QuizText.TITLE_HISTORY
        QuizView.CREATE -> QuizText.TITLE_CREATE
        QuizView.CHALLENGE -> QuizText.TITLE_CHALLENGE
        // L'accueil **et** la liste des défis portent le même titre : l'original écrit
        // `view==='challenge'?'Défi entre amis':'Quiz'`, donc les défis tombent dans « Quiz ».
        else -> QuizText.TITLE
    }

    /**
     * Prépare l'affichage.
     *
     * @param snapshot l'instantané du Quiz : c'est lui qui porte la question du jour, les
     *   réponses et les défis.
     * @param userId le compte ouvert, ou `null`.
     * @param selection la vue et les sélections.
     * @param friends les liens d'amitié **bruts**. Le filtre des liens acceptés est ici, parce
     *   que c'est une règle de l'écran : un lien en attente n'est pas un adversaire possible.
     * @param busy vrai pendant qu'une action est en vol. Il entre dans le calcul parce que deux
     *   décisions en dépendent : le bouton de lancement, et la cliquabilité des propositions.
     * @param day jour courant, injectable pour que les tests ne dépendent pas de l'horloge.
     * @param now instant courant, injectable pour la même raison.
     */
    fun render(
        snapshot: QuizSnapshot,
        userId: String?,
        selection: QuizSelection = QuizSelection(),
        friends: List<FriendLink> = emptyList(),
        busy: Boolean = false,
        day: String = Quiz.quizDay(),
        now: Long = System.currentTimeMillis(),
    ): QuizUiState {
        val id = userId.orEmpty()

        // Deux règles, et elles vivent **une seule fois** : juste en dessous. Le `ViewModel` a
        // besoin des mêmes, parce que c'est le `QuizQuestion` complet — et non son identifiant —
        // que `QuizRepository.answerDaily` exige. Les recopier ici aurait fait deux copies d'une
        // règle qui a déjà deux replis.
        val response = responseFor(snapshot, selection.historyDay, day)
        val question = questionFor(snapshot, selection.historyDay, day)

        return QuizUiState(
            loading = false,
            signedIn = userId != null,
            busy = busy,
            view = selection.view,
            title = title(selection.view),
            backCloses = selection.view == QuizView.HOME,
            home = if (selection.view == QuizView.HOME) home(snapshot, id, day, now) else null,
            daily = if (selection.view == QuizView.DAILY) daily(question, response, userId != null) else null,
            history = if (selection.view == QuizView.HISTORY) {
                snapshot.responses.map { historyLine(it, day) }
            } else {
                emptyList()
            },
            challenges = if (selection.view == QuizView.CHALLENGES) {
                snapshot.challenges.map { challengeLine(it, id, now) }
            } else {
                emptyList()
            },
            create = if (selection.view == QuizView.CREATE) {
                create(snapshot, friends, selection, userId != null, busy)
            } else {
                null
            },
            challenge = if (selection.view == QuizView.CHALLENGE) {
                challenge(snapshot.challenges.firstOrNull { it.id == selection.challengeId }, id, busy, now)
            } else {
                null
            },
        )
    }

    // -----------------------------------------------------------------------
    // L'accueil
    // -----------------------------------------------------------------------

    private fun home(snapshot: QuizSnapshot, id: String, day: String, now: Long): Home {
        val done = snapshot.responses.any { it.day == day }
        val available = snapshot.day == day && snapshot.daily != null
        return Home(
            heading = QuizText.HOME_HEADING,
            subtitle = QuizText.HOME_SUBTITLE,
            dailyStatus = when {
                done -> QuizText.HOME_DAILY_DONE
                available -> QuizText.HOME_DAILY_AVAILABLE
                else -> QuizText.HOME_DAILY_NONE
            },
            running = QuizText.homeRunning(snapshot.challenges.count { running(it, id, now) }),
            recent = snapshot.challenges.take(3).map { challengeLine(it, id, now) },
            noChallenge = QuizText.HOME_NO_CHALLENGE,
            notifications = NotificationLine(
                label = QuizText.NOTIFICATIONS,
                // `!==false` : une valeur absente veut dire « activées ». Voir le KDoc de
                // `NotificationLine` — le premier appui désactive, et c'est voulu.
                enabled = snapshot.notificationsEnabled != false,
            ),
        )
    }

    /** Vrai si le défi n'est ni terminé ni expiré, du point de vue de ce joueur. */
    private fun running(challenge: QuizChallenge, id: String, now: Long): Boolean {
        val status = Quiz.challengeStatus(challenge, id, now)
        return status != QuizText.CHALLENGE_DONE && status != QuizText.CHALLENGE_EXPIRED
    }

    // -----------------------------------------------------------------------
    // La question du jour
    // -----------------------------------------------------------------------

    /**
     * La question du jour, ou `null` quand il n'y en a pas.
     *
     * `null` n'est pas « rien à afficher » : c'est une information — l'écran montre alors
     * « Aucune question du jour disponible ». C'est la même distinction que
     * `QuizRepository` fait entre un instantané vide lu **avec succès** et le même instantané
     * vide après un échec.
     */
    private fun daily(question: QuizQuestion?, response: DailyResponse?, signedIn: Boolean): Daily? {
        if (question == null) return null
        val selected = response?.selectedAnswerId
        val pending = response?.pending == true
        return Daily(
            progress = if (response == null) QuizText.DAILY_PROGRESS else QuizText.DAILY_PROGRESS_DONE,
            category = question.category,
            question = question.question,
            answers = answers(question, selected),
            selectable = signedIn && selected == null,
            pending = pending,
            // Pas de correction tant que la réponse attend : le serveur n'a pas encore révélé
            // la bonne réponse, et `correctAnswerId` vaut alors `null` — la carte dirait
            // « Voici la bonne réponse » sans pouvoir la nommer.
            correction = if (response == null || pending) null else correction(question, response.isCorrect == true),
        )
    }

    // -----------------------------------------------------------------------
    // Les propositions et la correction
    // -----------------------------------------------------------------------

    /**
     * Les propositions d'une question, dans l'ordre du serveur.
     *
     * **La correction n'est révélée que si les deux sont connus** — une proposition choisie *et*
     * la bonne réponse. C'est le `!!selected && !!question.correctAnswerId` de l'original : tant
     * que le serveur n'a pas révélé la bonne réponse, aucune proposition n'est colorée, même si
     * la personne a déjà répondu. Sans cette condition, l'écran d'un défi en cours afficherait
     * la bonne réponse avant que l'adversaire ait joué.
     */
    private fun answers(question: QuizQuestion, selected: String?): List<AnswerLine> {
        val revealed = selected != null && question.correctAnswerId != null
        return question.answers.mapIndexed { index, answer ->
            val letter = ('A' + index).toString()
            val chosen = answer.id == selected
            val state = when {
                revealed && answer.id == question.correctAnswerId -> AnswerState.CORRECT
                revealed && chosen -> AnswerState.WRONG
                chosen -> AnswerState.CHOSEN
                else -> AnswerState.IDLE
            }
            AnswerLine(
                id = answer.id,
                letter = letter,
                text = answer.text,
                chosen = chosen,
                state = state,
                label = QuizText.answerLabel(letter, answer.text, chosen),
            )
        }
    }

    /**
     * La correction d'une question.
     *
     * @param correct vrai quand la réponse choisie était la bonne. Faux **aussi** quand on n'a
     *   pas répondu à cette question — c'est le `!!r?.isCorrect` de l'original, qui affiche
     *   « Voici la bonne réponse » sur une question d'un défi terminé à laquelle je n'ai pas
     *   répondu. Le titre n'est donc pas un jugement sur la personne, seulement sur la réponse.
     */
    private fun correction(question: QuizQuestion, correct: Boolean): Correction = Correction(
        title = if (correct) QuizText.CORRECT_TITLE else QuizText.WRONG_TITLE,
        explanation = question.explanation,
        arabic = question.arabic,
        translation = question.translation,
        source = QuizText.correctionSource(question.sourceTitle, question.sourceReference)
            .ifBlank { null },
        // `startsWith('https://')` de l'original : ni `http`, ni un schéma d'exécution. Un lien
        // non `https` n'est **pas** proposé, et c'est une règle de sûreté.
        sourceUrl = question.sourceUrl?.takeIf { it.startsWith("https://") },
    )

    // -----------------------------------------------------------------------
    // L'historique
    // -----------------------------------------------------------------------

    /**
     * Une ligne d'historique.
     *
     * **La teinte et le texte suivent la même condition, et une réponse non jugée est
     * mauvaise.** L'original écrit `r.pending ? muted : r.isCorrect ? review : red` : une
     * réponse qui n'est ni en attente ni marquée correcte tombe donc dans « ✕ Mauvaise
     * réponse », y compris quand `isCorrect` est `null` — un instantané d'ancien schéma. C'est
     * reproduit tel quel ; l'alternative serait d'inventer un quatrième état que le serveur ne
     * produit pas.
     */
    private fun historyLine(response: DailyResponse, day: String): HistoryLine {
        val pending = response.pending == true
        val good = response.isCorrect == true
        return HistoryLine(
            day = response.day,
            label = if (response.day == day) {
                QuizText.HISTORY_TODAY
            } else {
                // Le jour brut quand il est illisible : voir `QuizText.historyDay`.
                QuizText.historyDay(response.day) ?: response.day
            },
            status = when {
                pending -> QuizText.HISTORY_PENDING
                good -> QuizText.HISTORY_CORRECT
                else -> QuizText.HISTORY_WRONG
            },
            tone = when {
                pending -> Tone.MUTED
                good -> Tone.GOOD
                else -> Tone.BAD
            },
        )
    }

    // -----------------------------------------------------------------------
    // Les défis
    // -----------------------------------------------------------------------

    /**
     * Une ligne de défi.
     *
     * @param id le compte de celui qui regarde : c'est lui qui décide **quel nom s'affiche** —
     *   l'adversaire si j'ai créé le défi, le créateur sinon.
     */
    private fun challengeLine(challenge: QuizChallenge, id: String, now: Long): ChallengeLine {
        val mine = challenge.creatorId == id
        val status = Quiz.challengeStatus(challenge, id, now)
        return ChallengeLine(
            id = challenge.id,
            name = if (mine) challenge.opponentName else challenge.creatorName,
            status = status,
            yourTurn = status == QuizText.CHALLENGE_YOUR_TURN,
            detail = when (status) {
                QuizText.CHALLENGE_DONE -> QuizText.challengeScore(
                    own = challenge.answers.count { it.userId == id && it.isCorrect == true },
                    other = challenge.answers.count { it.userId != id && it.isCorrect == true },
                    count = challenge.questionCount,
                )
                // Un défi expiré n'affiche **rien** à droite : ni score, ni heures. L'original
                // écrit `status==='Expiré'?'':…`.
                QuizText.CHALLENGE_EXPIRED -> ""
                else -> QuizText.challengeHours(hoursLeft(challenge.expiresAt, now))
            },
        )
    }

    /**
     * Heures restantes, arrondies **au-dessus** et jamais négatives.
     *
     * C'est `Math.max(0, Math.ceil((expiresAt - now) / 3600000))` de l'original. L'arrondi
     * au-dessus est ce qui évite d'afficher « 0 h » pendant la dernière heure.
     *
     * Une date illisible rend `0` par `Dates.parseIsoMillis`, donc **zéro heure restante** — et
     * `Quiz.challengeStatus` la classe « Expiré » par la même valeur. Les deux s'accordent : on
     * ne peut pas afficher « 3 h » sur un défi que le statut dit expiré.
     */
    private fun hoursLeft(expiresAt: String, now: Long): Int {
        val millis = Dates.parseIsoMillis(expiresAt) - now
        return maxOf(0, ceil(millis.toDouble() / MILLIS_PER_HOUR).toInt())
    }

    private const val MILLIS_PER_HOUR = 3_600_000.0

    // -----------------------------------------------------------------------
    // La création d'un défi
    // -----------------------------------------------------------------------

    private fun create(
        snapshot: QuizSnapshot,
        friends: List<FriendLink>,
        selection: QuizSelection,
        signedIn: Boolean,
        busy: Boolean,
    ): Create {
        val accepted = friends.filter { it.status == FriendLinkStatus.ACCEPTED }
        return Create(
            friends = accepted.map { link ->
                // **`other` est lié à une variable locale avant d'être testé.** C'est une
                // propriété publique de `core:model`, donc d'un autre module : Kotlin refuse
                // d'en affiner le type, et `link.other?.id != null && link.other.id == …` ne
                // compile pas. Même contrainte que `ProgressRenderer.pagesRead`, et même
                // remède.
                val other = link.other
                FriendLine(
                    // `other` peut être nul : un lien lu depuis la table ne porte que quatre
                    // champs, et le profil de l'autre n'est pas toujours là.
                    id = other?.id.orEmpty(),
                    name = other?.displayName ?: QuizText.CREATE_FRIEND_FALLBACK,
                    selected = other != null && other.id == selection.friendId,
                )
            },
            noFriend = QuizText.CREATE_NO_FRIEND,
            sets = snapshot.quizSets.orEmpty().map { set ->
                SetLine(
                    id = set.id,
                    label = QuizText.createSet(set.title, set.category),
                    selected = set.id == selection.quizSetId,
                )
            },
            randomSelected = selection.quizSetId == null,
            count = selection.count,
            counts = listOf(QuizText.CREATE_COUNT_5, QuizText.CREATE_COUNT_10),
            rule = QuizText.CREATE_RULE,
            // `disabled={busy||!friendId||!userId}` de l'original. Le compte est déjà connu par
            // `signedIn`, donc le bouton ne peut pas être actif sans compte.
            canLaunch = signedIn && selection.friendId != null && !busy,
            launchLabel = if (busy) QuizText.CREATE_BUSY else QuizText.CREATE_LAUNCH,
        )
    }

    // -----------------------------------------------------------------------
    // Le défi ouvert
    // -----------------------------------------------------------------------

    /**
     * Le défi ouvert, ou `null` quand il n'est pas dans l'instantané.
     *
     * `null` mène à la carte « Défi non disponible dans le cache » : l'écran a été ouvert sur un
     * défi précis — par un lien de notification, ou depuis la conversation — et l'instantané
     * publié ne le contient pas encore.
     */
    private fun challenge(challenge: QuizChallenge?, id: String, busy: Boolean, now: Long): Challenge? {
        if (challenge == null) return null
        val status = Quiz.challengeStatus(challenge, id, now)
        val own = challenge.answers.filter { it.userId == id }
        return Challenge(
            heading = "${challenge.creatorName} · ${challenge.opponentName}",
            play = when (status) {
                QuizText.CHALLENGE_DONE -> ChallengePlay.Finished(
                    // Les deux scores, dans l'ordre du serveur : créateur puis adversaire.
                    // L'original boucle sur `[creatorId, opponentId]`.
                    scores = listOf(challenge.creatorId, challenge.opponentId).map { player ->
                        val name = if (player == challenge.creatorId) {
                            challenge.creatorName
                        } else {
                            challenge.opponentName
                        }
                        val correct = challenge.answers.count { it.userId == player && it.isCorrect == true }
                        "$name · $correct / ${challenge.questionCount}"
                    },
                    verdict = verdict(challenge),
                    questions = challenge.questions.map { question ->
                        val mine = own.firstOrNull { it.questionId == question.id }
                        AnsweredQuestion(
                            question = question.question,
                            answers = answers(question, mine?.selectedAnswerId),
                            correction = correction(question, mine?.isCorrect == true),
                        )
                    },
                )

                QuizText.CHALLENGE_EXPIRED -> ChallengePlay.Expired

                else -> {
                    // La question suivante est celle à laquelle **je** n'ai pas répondu. La
                    // règle vit dans `nextQuestion`, parce que le `ViewModel` en a besoin de la
                    // même pour envoyer une réponse : l'identifiant de la question ne figure pas
                    // dans l'état affichable, qui ne porte que des chaînes déjà résolues.
                    val next = nextQuestion(challenge, id)
                    if (next != null) {
                        ChallengePlay.Next(
                            progress = QuizText.challengeProgress(
                                answered = own.size + 1,
                                count = challenge.questionCount,
                                hours = hoursLeft(challenge.expiresAt, now),
                            ),
                            category = next.category,
                            question = next.question,
                            answers = answers(next, selected = null),
                            selectable = !busy,
                            reveal = QuizText.CHALLENGE_REVEAL_LATER,
                        )
                    } else {
                        ChallengePlay.Waiting(
                            title = QuizText.CHALLENGE_WAITING_TITLE,
                            waitingFor = QuizText.challengeWaitingFor(
                                if (challenge.creatorId == id) challenge.opponentName else challenge.creatorName,
                            ),
                        )
                    }
                }
            },
        )
    }

    /**
     * Le verdict d'un défi terminé.
     *
     * À égalité, l'original écrit « Égalité » sans nommer personne ; sinon il nomme le gagnant —
     * et le gagnant est le **créateur** quand le score de l'adversaire n'est pas strictement
     * supérieur, ce qui couvre l'égalité déjà traitée. La comparaison est faite sur le nombre de
     * **bonnes réponses**, pas sur le nombre de réponses données.
     */
    private fun verdict(challenge: QuizChallenge): String {
        val creator = challenge.answers.count { it.userId == challenge.creatorId && it.isCorrect == true }
        val opponent = challenge.answers.count { it.userId == challenge.opponentId && it.isCorrect == true }
        if (creator == opponent) return QuizText.CHALLENGE_TIE
        return QuizText.challengeWinner(
            if (creator > opponent) challenge.creatorName else challenge.opponentName,
        )
    }

    // -----------------------------------------------------------------------
    // Les règles que l'écran partage avec le ViewModel
    // -----------------------------------------------------------------------
    // Elles sont `internal` — visibles dans le module — parce que le `ViewModel` en a besoin
    // pour **agir** : enregistrer une réponse exige le `QuizQuestion` complet, et répondre à un
    // défi exige l'identifiant de la question suivante. Aucune des deux choses ne figure dans
    // l'état affichable, qui ne porte que du texte déjà résolu.
    //
    // Les laisser au `ViewModel` aurait créé une seconde copie de règles qui ont, chacune, deux
    // replis — et une copie diverge au premier changement, sans que rien ne le signale.
    // -----------------------------------------------------------------------

    /**
     * La réponse affichée : celle du jour consulté, ou celle d'aujourd'hui.
     *
     * Un jour demandé qui a **disparu** de l'instantané — une lecture l'a remplacé pendant qu'on
     * lisait l'historique — ne donne **aucune** réponse. Le repli sur le jour courant ne
     * s'applique donc pas : il n'a lieu que si aucun jour n'est demandé.
     */
    private fun responseFor(snapshot: QuizSnapshot, historyDay: String?, day: String): DailyResponse? =
        if (historyDay != null) {
            snapshot.responses.firstOrNull { it.day == historyDay }
        } else {
            snapshot.responses.firstOrNull { it.day == day }
        }

    /**
     * La question affichée pour un jour donné.
     *
     * **Le repli porte sur la réponse, pas sur la question.** Un jour demandé qui a disparu ne
     * donne aucune réponse — donc ni correction, ni progression « 1 / 1 » —, mais la question,
     * elle, retombe sur celle du jour dès que l'instantané est celui d'aujourd'hui : c'est le
     * `response?.question ?? (data.day===day ? data.daily : null)` de l'original. L'écran montre
     * donc la question du jour **sans** réponse. C'est contre-intuitif, et c'est reproduit : un
     * écran vide serait défendable, mais il divergerait du client d'origine sans qu'aucun test ne
     * puisse dire lequel des deux a raison. `QuizRendererTest` fige cette combinaison.
     *
     * La question vient de la **réponse** quand il y en a une : elle porte sa propre copie, et
     * c'est celle que la personne a vue. Sinon elle vient de l'instantané, et **seulement** si
     * l'instantané est celui d'aujourd'hui — une question publiée hier ne doit pas s'afficher
     * comme celle du jour.
     *
     * @param day jour courant, injectable pour que les tests ne dépendent pas de l'horloge.
     */
    internal fun questionFor(
        snapshot: QuizSnapshot,
        historyDay: String?,
        day: String = Quiz.quizDay(),
    ): QuizQuestion? {
        val response = responseFor(snapshot, historyDay, day)
        return response?.question ?: if (snapshot.day == day) snapshot.daily else null
    }

    /**
     * La question suivante d'un défi : la première à laquelle ce joueur n'a pas répondu.
     *
     * `null` quand il a tout répondu — l'écran attend alors l'autre joueur. C'est la même
     * condition qui décide entre « à moi de jouer » et « en attente », puisque
     * `Quiz.challengeStatus` compte les réponses de ce même compte.
     */
    internal fun nextQuestion(challenge: QuizChallenge, userId: String): QuizQuestion? {
        val answered = challenge.answers.filter { it.userId == userId }.map { it.questionId }.toSet()
        return challenge.questions.firstOrNull { it.id !in answered }
    }
}
