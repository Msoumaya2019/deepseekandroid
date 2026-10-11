package com.msoumaya.deepseekandroid.feature.home

import com.msoumaya.deepseekandroid.core.data.repository.DailyContentsState
import com.msoumaya.deepseekandroid.core.domain.DailyContents
import com.msoumaya.deepseekandroid.core.domain.DailyContentsText
import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.Program
import com.msoumaya.deepseekandroid.core.domain.ProgramText
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.QuizText
import com.msoumaya.deepseekandroid.core.domain.Review
import com.msoumaya.deepseekandroid.core.domain.StudyProgressCalculator
import com.msoumaya.deepseekandroid.core.domain.StudySession
import com.msoumaya.deepseekandroid.core.domain.WeeklyProgress
import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.DailyContent
import com.msoumaya.deepseekandroid.core.model.DailyContentType
import com.msoumaya.deepseekandroid.core.model.MushafSource
import com.msoumaya.deepseekandroid.core.model.QuizSnapshot
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.SessionStatus
import com.msoumaya.deepseekandroid.core.model.StudyMode
import com.msoumaya.deepseekandroid.core.model.effectiveStudyProgress

// ---------------------------------------------------------------------------
// Calcul de l'accueil
// ---------------------------------------------------------------------------
// Le calcul est séparé du `ViewModel` à dessein : c'est une fonction pure de `(état, jour)`, donc
// éprouvable sans coroutine, sans horloge et sans appareil. Le `ViewModel` ne fait plus que
// relier deux flux et appeler ce fichier.
//
// Cette séparation a une conséquence pratique : les règles de l'accueil — l'ordre du point de
// reprise, le calcul de la série, la période couverte par chaque bandeau — se vérifient en
// quelques millisecondes, alors qu'un calcul enfoui dans un `init` de `ViewModel` demanderait de
// piloter `Dispatchers.Main` pour être atteint.
//
// **Le référentiel coranique doit être chargé avant d'appeler [render].** Toutes les fonctions
// ci-dessous passent par `Quran`, qui rend un référentiel vide tant qu'il n'est pas initialisé.
// C'est au `ViewModel` de ne pas appeler avant que l'état ne soit `Ready`.
// ---------------------------------------------------------------------------

internal object HomeRenderer {

    /** Libellé de la carte de révision quand un passage est dû. */
    private const val REVISION_DUE = "À revoir aujourd'hui"

    /**
     * Calcule l'état affichable à partir de l'état applicatif.
     *
     * @param state état applicatif persisté.
     * @param at jour courant, au format `AAAA-MM-JJ`. Il sert aussi de **jour du Quiz** : les deux
     *   calendriers sont le même — `Quiz.quizDay()` est `Dates.zone()`, et `Dates.todayLocal()` le
     *   même fuseau —, et le passer en entrée plutôt que de le relire rend les cartes de quiz
     *   éprouvables sans horloge.
     * @param quiz instantané du Quiz, ou `null` tant que le disque n'a pas été lu — et pour
     *   toujours hors configuration Supabase. **Facultatif à dessein** : l'accueil s'affiche sans
     *   le Quiz, et les deux cartes annoncent alors la question du jour, ce qui est exact
     *   puisqu'elles ne promettent rien de plus.
     * @param daily état des contenus du jour. **Facultatif à dessein**, comme [quiz] : la valeur
     *   par défaut est « en attente », donc un appelant qui n'aurait pas de dépôt sous la main
     *   obtient la phrase de chargement, et non un carrousel vide qui affirmerait qu'il n'y a rien.
     */
    fun render(
        state: AppState,
        at: String,
        quiz: QuizSnapshot? = null,
        daily: DailyContentsState = DailyContentsState(),
    ): HomeUiState {
        val stats = Program.stats(state, at)
        val week = WeeklyProgress.weeklyProgress(state, at)
        val active = activity(state, at)
        val monday = Dates.addDays(at, -((Dates.dayOf(at) + 6) % 7))

        val todoToday = state.sessions.firstOrNull {
            WeeklyProgress.scheduledDate(it) == at && it.status == SessionStatus.TODO
        }
        val reviewTask = Review.reviewPlan(state, at).session.firstOrNull()

        val resumeId = state.lastRead?.verseId
            ?: todoToday?.start
            ?: state.goal.ranges.firstOrNull()?.start
            ?: 1
        val verse = Quran.verseAt(resumeId)
        val surah = Quran.surahs[verse.surah - 1]
        // La source **brute**, comme le client d'origine (`studyPage(id, state.reader?.mushaf
        // ?? 'coranTest')`). Replier la source ferait calculer la page annoncée avec le découpage
        // de Médine pour une source qui a le sien — et `coranTest` est le **défaut** de
        // l'application, donc le cas courant et non un cas de bord.
        val source = (state.reader?.mushaf ?: MushafSource.CORAN_TEST).persistedKey
        val page = state.lastRead?.page ?: StudyProgressCalculator.studyPage(resumeId, source)

        return HomeUiState(
            loading = false,
            firstName = state.profile?.firstName,
            resume = Resume(
                surahName = surah.name,
                surahArabic = surah.arabic,
                ayah = verse.ayah,
                page = page,
                ratio = if (surah.count > 0) (verse.ayah - 1).toFloat() / surah.count else 0f,
                verseId = resumeId,
            ),
            learning = todoToday
                ?.let {
                    DailyTask(
                        passage = Quran.reference(it.range),
                        details = countText(it.range),
                        verseId = it.start,
                        study = StudySession.forSession(it),
                    )
                }
                ?: DailyTask.EMPTY_LEARNING,
            revision = reviewTask
                ?.let { DailyTask(Quran.reference(it.range), REVISION_DUE, it.start) }
                ?: DailyTask.EMPTY_REVISION,
            week = WeekSummary(
                verses = stats.week,
                daily = (0 until WeekSummary.DAYS).map {
                    Program.stats(state, Dates.addDays(monday, it)).today
                },
                streak = active.streak,
                activeDays = (0 until WeekSummary.DAYS).map {
                    Dates.addDays(at, it - (WeekSummary.DAYS - 1)) in active.dates
                },
                goalRatio = week.ratio.toFloat(),
            ),
            quiz = quizCards(quiz, at),
            daily = dailyCarousel(daily),
        )
    }

    /**
     * Le carrousel des contenus du jour — ses vignettes, ou l'état vide qui les remplace.
     *
     * ## Deux formes, et non une liste plus un message
     *
     * [DailyCarousel] ne peut pas être « une liste vide avec un message facultatif » : l'appelant
     * oublierait la phrase d'état, et un carrousel vide sans phrase se lirait comme une panne.
     * Voir le KDoc du type.
     *
     * ## Les trois phrases, et les trois états du dépôt qui les produisent
     *
     * L'original écrit `loading ? 'Chargement…' : error ? 'hors connexion' : 'Aucun contenu'`, et
     * le portage garde cet ordre. Les trois états sont ceux que le dépôt **publie** : avant la
     * lecture du disque (`loading`, sans document), après une lecture ratée qui n'a rien laissé
     * (`failure`, document vide), et après une lecture réussie qui n'a rien trouvé (document vide,
     * ni `loading` ni `failure`). Le dépôt écrit le document et retire `loading` dans **la même**
     * publication, donc les deux drapeaux ne se contredisent jamais : l'ordre des branches ne
     * tranche aucun cas réel, il recopie celui de l'original.
     *
     * ## Le titre d'un carrousel vide
     *
     * [DailyContentsText.HOME_REMINDER], et non un titre vide : c'est l'`else` de l'original, qui
     * couvre l'absence d'entrée comme le type rappel. La place du bloc reste annoncée.
     *
     * ## Ce qui n'est pas filtré ici
     *
     * `isActive` : la lecture du dépôt filtre déjà côté serveur, et le refaire donnerait deux
     * vérités pour une seule règle. Voir `DailyContents.entries`.
     */
    internal fun dailyCarousel(state: DailyContentsState): DailyCarousel {
        val entries = DailyContents.entries(state.snapshot?.entries.orEmpty())

        if (entries.isEmpty()) {
            return DailyCarousel.Empty(
                title = DailyContentsText.HOME_REMINDER,
                message = when {
                    state.loading -> DailyContentsText.HOME_LOADING
                    state.failure != null -> DailyContentsText.HOME_OFFLINE
                    else -> DailyContentsText.HOME_EMPTY
                },
            )
        }

        return DailyCarousel.Slides(entries.map { slide(it) })
    }

    /**
     * Une vignette, **entièrement résolue en texte**.
     *
     * Le titre vient de [DailyContentsText.homeTitle] et le libellé du lecteur d'écran de la même
     * règle que l'original — `item.type === 'reminder' ? 'le rappel' : 'l’invocation'` —, ramenée
     * ici à un choix binaire explicite : les deux règles de l'original sont écrites dans les deux
     * sens (l'une teste `invocation`, l'autre `reminder`), et les recopier telles quelles
     * laisserait croire à deux décisions là où il n'y en a qu'une.
     */
    private fun slide(content: DailyContent): DailySlide = DailySlide(
        id = content.id,
        title = DailyContentsText.homeTitle(content.type),
        arabic = content.arabicText,
        phonetic = content.phoneticText,
        french = content.frenchText,
        source = sourceLine(content),
        label = if (content.type == DailyContentType.INVOCATION) {
            DailyContentsText.OPEN_INVOCATION
        } else {
            DailyContentsText.OPEN_REMINDER
        },
    )

    /**
     * La ligne de provenance d'une vignette : `source`, ou `source · référence`.
     *
     * C'est `[item.source, item.reference].filter(Boolean).join(' · ')` de l'original. Le filtre
     * n'est pas décoratif : sans lui, une référence absente laisserait un **séparateur orphelin** —
     * « Sahih · » —, qui se lit comme une donnée manquante plutôt que comme une absence normale.
     * Le séparateur est le point médian entouré d'espaces, celui de la source.
     */
    private fun sourceLine(content: DailyContent): String =
        listOfNotNull(
            content.source.takeIf { it.isNotEmpty() },
            content.reference?.takeIf { it.isNotEmpty() },
        ).joinToString(" · ")

    /**
     * Les deux cartes de quiz de l'accueil.
     *
     * ## Les deux conditions de l'original, recopiées mot pour mot
     *
     * `done` est vrai dès qu'une réponse **quelconque** porte le jour courant
     * (`responses.some(r => r.day === quizDay())`) ; `available` demande en plus que l'instantané
     * soit **celui du jour** et qu'il porte la question publiée. Les deux ne se déduisent pas l'une
     * de l'autre, et les confondre produirait deux erreurs muettes : un instantané d'hier peut
     * porter des réponses d'aujourd'hui — la carte annoncerait « terminée » d'après un instantané
     * qui ne sait rien du jour —, et un instantané du jour peut n'avoir aucune question publiée,
     * auquel cas « disponible » serait un mensonge.
     *
     * ## La pastille
     *
     * `available && !done` : une question publiée et pas encore répondue. C'est la seule chose qui
     * ne soit pas du texte, et l'écran ne la calcule pas — il la reçoit décidée.
     *
     * ## Le repli
     *
     * Un instantané nul rend « Question du jour » et l'invitation, qui ne promettent rien. C'est le
     * cas du tout premier affichage, et celui d'une application sans projet Supabase configuré.
     */
    private fun quizCards(snapshot: QuizSnapshot?, at: String): QuizCards {
        val done = snapshot?.responses?.any { it.day == at } == true
        val available = snapshot != null && snapshot.day == at && snapshot.daily != null

        return QuizCards(
            quizTitle = QuizText.HOME_CARD_QUIZ,
            quizSub = when {
                done -> QuizText.HOME_CARD_QUIZ_DONE
                available -> QuizText.HOME_CARD_QUIZ_AVAILABLE
                else -> QuizText.HOME_CARD_QUIZ_PENDING
            },
            quizDetail = if (done) {
                QuizText.HOME_CARD_QUIZ_DETAIL_DONE
            } else {
                QuizText.HOME_CARD_QUIZ_DETAIL
            },
            quizAlert = available && !done,
            friendsTitle = QuizText.HOME_CARD_FRIENDS,
            friendsSub = QuizText.HOME_CARD_FRIENDS_SUB,
            friendsDetail = QuizText.HOME_CARD_FRIENDS_DETAIL,
        )
    }

    /**
     * Jours d'activité et série en cours.
     *
     * Un jour compte dès qu'une séance y a été validée **ou** qu'une validation fine y a été
     * enregistrée. Les deux sources sont nécessaires : une séance suivie par progression fine
     * n'est pas marquée terminée avant sa dernière validation, donc ne compter que les séances
     * terminées romprait la série au milieu d'un apprentissage en cours.
     *
     * La série part d'aujourd'hui si aujourd'hui compte, sinon d'hier : une journée en cours
     * n'est pas une journée manquée, et afficher « 0 jour d'affilée » à dix heures du matin
     * serait faux pour quelqu'un dont la séance est prévue le soir.
     */
    private fun activity(state: AppState, at: String): Activity {
        val dates = buildSet {
            state.sessions
                .filter { it.status == SessionStatus.DONE }
                .forEach { add(it.completedDate ?: it.completedAt?.take(10) ?: it.date) }
            state.effectiveStudyProgress.values
                .filter { it.mode == StudyMode.LEARNING }
                .flatMap { it.validations }
                .forEach { add(it.date) }
        }

        var streak = 0
        var cursor = if (at in dates) at else Dates.addDays(at, -1)
        while (cursor in dates) {
            streak++
            cursor = Dates.addDays(cursor, -1)
        }
        return Activity(dates, streak)
    }

    private data class Activity(val dates: Set<String>, val streak: Int)

    /**
     * Libellé du nombre de versets d'une plage, au pluriel si nécessaire.
     *
     * Délégué à `ProgramText` : l'accueil et le programme écrivent le même compte, et deux
     * copies de cette phrase finiraient par accorder différemment. Le dépôt d'origine en avait
     * une seule, au niveau du module (`MainScreens.tsx:16`), partagée par les deux écrans.
     */
    internal fun countText(range: Range): String = ProgramText.verseCount(range)
}
