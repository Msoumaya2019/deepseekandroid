package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.local.QuizCacheStore
import com.msoumaya.deepseekandroid.core.data.local.QuizOutboxStore
import com.msoumaya.deepseekandroid.core.data.remote.DailyAnswerPayload
import com.msoumaya.deepseekandroid.core.data.remote.OwnerStore
import com.msoumaya.deepseekandroid.core.data.remote.QuizSource
import com.msoumaya.deepseekandroid.core.data.remote.isOffline
import com.msoumaya.deepseekandroid.core.data.remote.restCode
import com.msoumaya.deepseekandroid.core.data.remote.restMessage
import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.Quiz
import com.msoumaya.deepseekandroid.core.domain.QuizText
import com.msoumaya.deepseekandroid.core.model.AppJson
import com.msoumaya.deepseekandroid.core.model.QuizQuestion
import com.msoumaya.deepseekandroid.core.model.QuizSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// ---------------------------------------------------------------------------
// Dépôt « Quiz »
// ---------------------------------------------------------------------------
// Portage de `src/services/quiz.ts` : ce qui est lu, dans quel ordre, et ce qu'un échec fait à ce
// qui est déjà à l'écran.
//
// **Ce fichier ne décide d'aucun libellé et d'aucune règle du jeu.** Le statut d'un défi, les
// statistiques, la fusion d'un instantané et la règle « une participation par jour » sont dans
// `core:domain/Quiz.kt` ; ici il n'y a que l'ordonnancement, le sort des erreurs et le passage
// hors ligne.
//
// ## La chronologie du rafraîchissement, et pourquoi elle est dans cet ordre
//
//   1. **lire l'instantané local et le publier** — l'écran doit pouvoir s'afficher sans réseau ;
//   2. **vider la file** — envoyer ce qui attend, pour que le serveur le sache **avant** qu'on
//      lui demande son état ;
//   3. **lire l'instantané du serveur** — ce qui vient d'être envoyé y figure alors ;
//   4. **fusionner** avec l'instantané relu — une réponse encore en attente que le serveur ne
//      connaît pas est conservée.
//
// L'original suit le même ordre, et l'inverser ne se verrait pas : on lirait un instantané
// **antérieur** à l'envoi, donc sans la réponse qu'on vient de faire, et la fusion devrait
// ensuite la rajouter à la main. Les deux étapes 2 et 3 se répètent tant qu'une réponse a été
// enfilée pendant qu'on vidait la file — c'est la boucle de l'original, et elle n'est pas une
// précaution : `answerDaily` enfile **sans attendre** le rafraîchissement en cours.
//
// ## Les trois états d'échec, et ce qu'ils évitent
//
// Le client d'origine n'en avait qu'un : le bandeau. Ici, comme pour l'espace social, un échec
// qui laisse l'écran **vide** publie [QuizState.failure], et un échec qui laisse quelque chose à
// lire publie [QuizState.notice]. La raison est la même qu'ailleurs et elle est propre au Quiz :
// un instantané vide s'affiche « Aucune question publiée aujourd'hui », ce qui est faux quand la
// lecture a échoué.
// ---------------------------------------------------------------------------

/**
 * Ce que l'écran « Quiz » a besoin de savoir, et rien de plus.
 *
 * [snapshot] est `null` tant qu'aucune lecture locale n'a eu lieu, et ne l'est plus ensuite : le
 * Quiz est **hors ligne d'abord**, donc l'instantané du disque est publié avant tout appel
 * réseau, et l'écran n'a jamais à distinguer « pas encore lu » de « lu, et vide ».
 */
data class QuizState(
    /** Vrai si un compte est ouvert. Faux aussi hors configuration Supabase. */
    val signedIn: Boolean = false,

    /** Vrai tant que le premier instantané n'a pas été lu. Le défaut est donc « en attente ». */
    val loading: Boolean = true,

    /** Vrai pendant qu'un geste est en cours. C'est ce qui empêche un double envoi. */
    val busy: Boolean = false,

    /** Dernier message d'information, ou `null`. Il est effacé au **début** d'un geste. */
    val notice: String? = null,

    /** Panne quand il n'y a **rien à montrer** à la place. Voir [Quiz.isEmpty]. */
    val failure: String? = null,

    /** L'instantané affichable, ou `null` tant que le disque n'a pas été lu. */
    val snapshot: QuizSnapshot? = null,
)

/**
 * Charge et modifie l'état du Quiz, en gardant l'écran honnête sur ce qu'il sait.
 *
 * @param source accès aux données, ou `null` si aucun projet Supabase n'est configuré. Le `null`
 *   n'est pas une panne : c'est le mode hors ligne, et il doit se lire comme « pas de compte »,
 *   jamais comme une erreur réseau.
 * @param session propriétaire courant. Observé : c'est lui qui déclenche le chargement, et c'est
 *   lui qui **vide** l'état à la déconnexion — sans quoi le quiz du compte précédent resterait
 *   affiché sous le compte suivant, statistiques comprises.
 * @param cache instantané persisté, **un fichier par compte**.
 * @param outbox file des réponses faites hors ligne. Distincte de celle de l'état, pour la raison
 *   mesurée dans `QuizOutboxStore`.
 * @param scope portée des travaux qui vivent aussi longtemps que l'application.
 */
class QuizRepository(
    private val source: QuizSource?,
    private val session: OwnerStore,
    private val cache: QuizCacheStore,
    private val outbox: QuizOutboxStore,
    private val scope: CoroutineScope,
) {

    /**
     * Sérialise les rafraîchissements.
     *
     * Sans lui, deux rafraîchissements concurrents — l'ouverture de l'écran et la fin d'un geste
     * — videraient la file **deux fois** : le premier enverrait les réponses en attente, le
     * second les renverrait avant que la première purge ne soit écrite, et la file ne
     * s'épuiserait pas. La boucle de vidage suppose en effet qu'elle est la seule à écrire.
     */
    private val lock = Mutex()

    private val _state = MutableStateFlow(QuizState())

    /** État affichable. */
    val state: StateFlow<QuizState> = _state.asStateFlow()

    init {
        scope.launch {
            session.ownerId.distinctUntilChanged().collect { owner ->
                if (owner == null) clear() else refresh()
            }
        }
    }

    /**
     * Relit l'instantané : file d'abord, serveur ensuite, fusion en dernier.
     *
     * @return vrai si la lecture a abouti.
     */
    suspend fun refresh(): Boolean = lock.withLock {
        val api = source
        val owner = session.currentOwner()
        if (api == null || owner == null) {
            clear()
            return@withLock false
        }

        val store = cache.accountFor(owner)

        // 1. Hors ligne d'abord. Publier le disque **avant** le réseau n'est pas une optimisation :
        //    c'est ce qui fait qu'un appareil sans connexion affiche quand même le quiz, ses
        //    statistiques et ses défis. L'original fait de même, mais depuis l'écran — ici le
        //    dépôt en est seul maître, donc il le fait lui-même.
        _state.value = _state.value.copy(
            signedIn = true,
            loading = false,
            snapshot = store.current(),
            failure = null,
        )

        val day = Quiz.quizDay()
        try {
            do {
                // 2. Vider la file. L'identifiant de chaque entrée est retiré **après** que le
                //    serveur a accepté l'appel : un envoi qui échoue laisse donc l'entrée en
                //    place, et rien n'est perdu.
                for (entry in outbox.list(owner)) {
                    // Un compte peut changer pendant l'envoi : le vérifier entre deux entrées
                    // évite de pousser les réponses d'un compte sous le jeton d'un autre.
                    if (session.currentOwner() != owner) return@withLock false
                    api.answerDaily(AppJson.decodeFromString<DailyAnswerPayload>(entry.payload))
                    outbox.acknowledge(listOf(entry.id))
                }
                if (session.currentOwner() != owner) return@withLock false

                // 3. Lire.
                val remote = api.snapshot(day)
                if (session.currentOwner() != owner) return@withLock false

                // 4. Fusionner avec l'instantané **relu maintenant**, et non avec celui publié
                //    plus haut : une réponse enregistrée entre-temps est déjà dedans, et partir
                //    de la version d'avant la perdrait.
                val merged = store.update { Quiz.mergeSnapshot(remote, it) }
                _state.value = _state.value.copy(loading = false, failure = null, snapshot = merged)

                // La boucle n'est pas une précaution : une réponse enfilée pendant la purge
                // n'aurait pas été envoyée, et le serveur ne la connaîtrait donc pas encore.
            } while (session.currentOwner() == owner && outbox.hasPending(owner))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            fail(error)
            return@withLock false
        }

        true
    }

    /**
     * Enregistre la réponse à la question du jour.
     *
     * **L'écran n'attend pas le réseau.** L'instantané local est écrit, la réponse est mise en
     * file, l'état est publié, et l'envoi part en arrière-plan : c'est ce qui permet de répondre
     * dans le métro et de voir sa réponse tout de suite. Le serveur acceptera cet envoi plus tard
     * grâce à sa fenêtre de tolérance — l'instant transmis est celui de la réponse **sur
     * l'appareil**, pas celui de l'envoi.
     *
     * @return vrai si la réponse est enregistrée. Une seconde réponse le même jour ne change rien
     *   et rend vrai : ce n'est pas un échec, c'est la règle « une participation par jour ».
     */
    suspend fun answerDaily(question: QuizQuestion, answerId: String): Boolean {
        val owner = session.currentOwner()
        if (owner == null) {
            _state.value = _state.value.copy(notice = QuizText.SIGNED_OUT)
            return false
        }

        val day = Quiz.quizDay()
        val at = Dates.nowIso()
        val store = cache.accountFor(owner)
        val payload = DailyAnswerPayload(
            questionId = question.id,
            answerId = answerId,
            day = day,
            answeredAt = at,
        )

        // `recordDailyAnswer` rend l'instantané **inchangé** quand ce jour a déjà sa réponse :
        // la comparaison d'identité est donc la façon exacte de savoir s'il y a quelque chose à
        // envoyer, et c'est le test que fait l'original (`if (next === old) return`).
        var recorded = false
        try {
            store.update { current ->
                val next = Quiz.recordDailyAnswer(current, question, answerId, day, at)
                recorded = next !== current
                next
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Une réponse hors des propositions, ou une question qui n'est pas celle du jour :
            // `recordDailyAnswer` refuse, et ce refus doit se lire plutôt que se taire.
            _state.value = _state.value.copy(notice = describe(error))
            return false
        }

        if (!recorded) return true

        outbox.enqueue(owner, id = "$owner:$day", payload = AppJson.encodeToString(payload))

        // `failure` tombe, et le bandeau n'est **pas** effacé — les deux à dessein.
        //
        // La panne tombe parce qu'il y a maintenant quelque chose à montrer : la réponse qu'on
        // vient d'écrire. La laisser ferait préférer un message d'erreur à la réponse, ce qui est
        // exactement ce que la distinction panne/avis existe pour éviter.
        //
        // Le bandeau reste, lui, parce que la réponse **n'est pas encore partie** : l'effacer
        // cacherait la seule chose qu'il reste à comprendre. Le rafraîchissement de fond le
        // reposera de toute façon si l'envoi échoue.
        _state.value = _state.value.copy(snapshot = store.current(), failure = null)

        scope.launch { runCatching { refresh() } }
        return true
    }

    /**
     * Crée un défi contre [opponentId] et rend son identifiant, ou `null` si le serveur a refusé.
     *
     * **Pas de garde de connexion ici, et c'est un écart assumé.** L'original interroge `NetInfo`
     * et refuse avant d'essayer ; ce portage n'a **aucune** couche de connectivité — le fait est
     * mesuré, pas supposé : aucune des vingt-et-un modules ne lit l'état du réseau. En inventer
     * une coûterait une permission, une abstraction et, surtout, un risque de **faux négatif** :
     * un appareil connecté mais mal détecté refuserait un défi que le serveur aurait accepté.
     * Le message de l'original est donc prononcé **après** coup, quand la requête est revenue
     * sans avoir atteint le serveur — voir [QuizText.CHALLENGE_OFFLINE].
     */
    suspend fun createChallenge(
        opponentId: String,
        count: Int,
        quizSetId: String? = null,
    ): String? {
        val api = source ?: return null
        if (_state.value.busy) return null
        _state.value = _state.value.copy(busy = true, notice = null)
        return try {
            val id = api.createChallenge(opponentId, count, quizSetId)
            _state.value = _state.value.copy(busy = false)
            refresh()
            id
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.value = _state.value.copy(
                busy = false,
                notice = if (error.isOffline()) QuizText.CHALLENGE_OFFLINE else describe(error),
            )
            null
        }
    }

    /** Répond à une question de défi. */
    suspend fun answerChallenge(challengeId: String, questionId: String, answerId: String): Boolean =
        act { it.answerChallenge(challengeId, questionId, answerId) }

    /**
     * Active ou coupe les notifications du Quiz.
     *
     * [timezone] est celle de l'appareil, au format des noms de fuseaux (`Europe/Paris`). C'est
     * exactement ce que l'original envoie — `Intl.DateTimeFormat().resolvedOptions().timeZone` —,
     * et le serveur la vérifie contre `pg_timezone_names`.
     */
    suspend fun setNotifications(enabled: Boolean, timezone: String = Dates.zone().id): Boolean =
        act { it.setNotifications(enabled, timezone) }

    /**
     * Exécute un geste, puis relit.
     *
     * **`busy` est posé avant l'appel et le bandeau est effacé au même moment**, comme dans le
     * `act` de `QuizScreen.tsx` : un geste qui réussit ne dit donc rien — il a déjà changé
     * l'écran —, et un geste qui échoue écrit le message. Un second geste pendant le premier est
     * ignoré : c'est la règle de l'original, et elle protège d'un double appui que l'écran
     * n'aurait pas désactivé.
     */
    private suspend fun act(block: suspend (QuizSource) -> Unit): Boolean {
        val api = source ?: return false
        if (_state.value.busy) return false
        _state.value = _state.value.copy(busy = true, notice = null)
        return try {
            block(api)
            _state.value = _state.value.copy(busy = false)
            refresh()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.value = _state.value.copy(busy = false, notice = describe(error))
            false
        }
    }

    /** État « personne de connecté » : rien à montrer, et ce n'est pas une panne. */
    private fun clear() {
        _state.value = QuizState(signedIn = false, loading = false)
    }

    /**
     * Publie l'échec d'un rafraîchissement, en choisissant entre panne et avis.
     *
     * Le partage se fait sur ce qui **reste à lire**, et non sur la gravité de l'erreur : quand
     * l'instantané est vide, il n'y a rien à l'écran et l'échec est donc une panne ; quand il
     * porte quelque chose, ce quelque chose n'est pas devenu faux, et l'échec n'est qu'un avis.
     */
    private fun fail(error: Throwable) {
        val text = describe(error)
        val empty = _state.value.snapshot?.let { Quiz.isEmpty(it) } ?: true
        _state.value = _state.value.copy(
            loading = false,
            failure = if (empty) text else null,
            notice = if (empty) null else text,
        )
    }

    /**
     * Message lisible d'une erreur.
     *
     * Le texte vient de [restMessage], qui lit le champ que le serveur a écrit — et non
     * `message`, qui y accole un bloc de débogage et l'adresse du projet. Le code sert au
     * diagnostic d'installation : voir [Quiz.errorText].
     */
    private fun describe(error: Throwable): String =
        Quiz.errorText(message = error.restMessage(), code = error.restCode())
}
