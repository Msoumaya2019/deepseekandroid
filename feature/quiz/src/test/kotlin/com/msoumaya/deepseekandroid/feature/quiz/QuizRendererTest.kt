package com.msoumaya.deepseekandroid.feature.quiz

import com.msoumaya.deepseekandroid.core.domain.QuizText
import com.msoumaya.deepseekandroid.core.model.ChallengeAnswer
import com.msoumaya.deepseekandroid.core.model.ChallengeStatus
import com.msoumaya.deepseekandroid.core.model.DailyResponse
import com.msoumaya.deepseekandroid.core.model.FriendBrief
import com.msoumaya.deepseekandroid.core.model.FriendLink
import com.msoumaya.deepseekandroid.core.model.FriendLinkStatus
import com.msoumaya.deepseekandroid.core.model.QuizAnswer
import com.msoumaya.deepseekandroid.core.model.QuizChallenge
import com.msoumaya.deepseekandroid.core.model.QuizQuestion
import com.msoumaya.deepseekandroid.core.model.QuizSnapshot
import com.msoumaya.deepseekandroid.core.model.ThemedQuiz
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Règles de l'écran « Quiz »
// ---------------------------------------------------------------------------
// Le calcul est une fonction pure de `(instantané, compte, sélection, amis, en vol, jour, now)`.
// Il est donc éprouvé ici sans coroutine, sans appareil et sans horloge : le jour et l'instant
// sont **fixés**, et les dates d'expiration sont dérivées de cet instant.
//
// **Un piège a été mesuré en écrivant ces tests, et il vaut d'être connu.** La question affichée
// ne vient pas de l'instantané quand une réponse existe : elle vient de **la réponse**, qui porte
// sa propre copie. Une réponse en attente porte donc une copie **sans** `correctAnswerId`, alors
// que l'instantané, lui, peut déjà le connaître. Trois tests passaient d'abord une question à
// l'instantané et attendaient le comportement de celle de la réponse : ils auraient échoué, et
// pour une raison qui n'a rien à voir avec la règle visée. Les fabriques reçoivent donc
// maintenant la **même** question des deux côtés.
//
// Cinq familles portent le plus, parce que ce sont celles qui peuvent **mentir** :
//
//   1. **Quelle question s'affiche.** Trois sources se ressemblent — la réponse du jour, la
//      question publiée, et le jour que porte l'instantané. Le test construit un instantané
//      d'HIER pour vérifier qu'une question publiée hier ne passe pas pour celle d'aujourd'hui,
//      et il demande un jour **disparu** pour vérifier qu'on ne retombe pas silencieusement sur
//      aujourd'hui.
//
//   2. **Quand la correction est révélée.** Elle exige une réponse **et** la bonne réponse. Une
//      réponse en attente ne doit colorer **aucune** proposition : la colorer révélerait la bonne
//      réponse avant que l'adversaire ait joué.
//
//   3. **Quel nom s'affiche sur une ligne de défi.** C'est celui de **l'autre** — l'adversaire
//      si j'ai créé le défi, le créateur sinon. Le test éprouve les deux sens, parce qu'un seul
//      sens passerait aussi bien avec la mauvaise règle.
//
//   4. **Ce qui distingue les quatre états d'un défi ouvert.** Terminé, expiré, à moi, en
//      attente : le test construit les quatre, et vérifie pour chacun ce qui **ne doit pas** y
//      être — un défi expiré n'a ni score ni heures, un défi en cours ne montre aucune
//      correction.
//
//   5. **Le verdict d'un défi terminé.** Il se compte en **bonnes réponses**, pas en réponses
//      données : le test pose un joueur qui a répondu à tout mais juste à une seule, et exige
//      qu'il perde.
//
// Le jour est fixé au 6 octobre 2026, et l'instant à une valeur constante : rien ici ne dépend de
// l'horloge de la machine.
// ---------------------------------------------------------------------------

private const val JOUR = "2026-10-06"
private const val HIER = "2026-10-05"
private const val MOI = "moi"
private const val AMI = "ami"
private const val MAINTENANT = 1_790_000_000_000L
private const val HEURE = 3_600_000L

private val DANS_TROIS_HEURES: String = Instant.ofEpochMilli(MAINTENANT + 3 * HEURE).toString()
private val DANS_DEMI_HEURE: String = Instant.ofEpochMilli(MAINTENANT + HEURE / 2).toString()
private val IL_Y_A_UNE_HEURE: String = Instant.ofEpochMilli(MAINTENANT - HEURE).toString()

// --- Fabriques -----------------------------------------------------------------------------

private fun question(
    id: String = "q1",
    categorie: String = "Coran",
    intitule: String = "Quelle est la première sourate ?",
    bonneReponse: String? = "a",
    publication: String? = JOUR,
    url: String? = "https://exemple.test/1",
): QuizQuestion = QuizQuestion(
    id = id,
    category = categorie,
    question = intitule,
    answers = listOf(
        QuizAnswer("a", "Al-Fatiha"),
        QuizAnswer("b", "Al-Baqara"),
        QuizAnswer("c", "An-Nas"),
    ),
    publicationDate = publication,
    explanation = "La première sourate du moushaf.",
    sourceTitle = "Al-Fatiha",
    sourceReference = "1:1",
    sourceUrl = url,
    arabic = "بِسْمِ اللَّهِ",
    translation = "Au nom d’Allah",
    correctAnswerId = bonneReponse,
)

private fun reponse(
    jour: String = JOUR,
    choisie: String = "a",
    correcte: Boolean? = true,
    enAttente: Boolean? = null,
    question: QuizQuestion = question(),
): DailyResponse = DailyResponse(
    questionId = question.id,
    day = jour,
    selectedAnswerId = choisie,
    answeredAt = "${jour}T09:00:00Z",
    question = question,
    isCorrect = correcte,
    pending = enAttente,
)

private fun reponseDeDefi(
    joueur: String,
    questionId: String,
    correcte: Boolean? = true,
    choisie: String? = "a",
): ChallengeAnswer = ChallengeAnswer(
    userId = joueur,
    questionId = questionId,
    selectedAnswerId = choisie,
    answeredAt = "${JOUR}T09:00:00Z",
    isCorrect = correcte,
)

private fun defi(
    id: String = "c1",
    createur: String = MOI,
    adversaire: String = AMI,
    statut: ChallengeStatus = ChallengeStatus.PENDING,
    expire: String = DANS_TROIS_HEURES,
    reponses: List<ChallengeAnswer> = emptyList(),
    questions: List<QuizQuestion> = listOf(
        question("q1", intitule = "Première ?"),
        question("q2", intitule = "Deuxième ?"),
        question("q3", intitule = "Troisième ?"),
    ),
): QuizChallenge = QuizChallenge(
    id = id,
    creatorId = createur,
    opponentId = adversaire,
    creatorName = "Moi",
    opponentName = "Aïcha",
    questionCount = questions.size,
    createdAt = "${JOUR}T08:00:00Z",
    expiresAt = expire,
    status = statut,
    questions = questions,
    answers = reponses,
)

private fun lien(
    id: String = "l1",
    statut: FriendLinkStatus = FriendLinkStatus.ACCEPTED,
    autreId: String? = AMI,
    nom: String = "Aïcha",
): FriendLink = FriendLink(
    id = id,
    requesterId = MOI,
    recipientId = autreId.orEmpty(),
    status = statut,
    createdAt = "${JOUR}T08:00:00Z",
    other = autreId?.let { FriendBrief(id = it, displayName = nom, avatarPath = "avatars/$it.png") },
)

private fun instantane(
    jour: String = JOUR,
    question: QuizQuestion? = question(),
    reponses: List<DailyResponse> = emptyList(),
    defis: List<QuizChallenge> = emptyList(),
    themes: List<ThemedQuiz>? = null,
    notifications: Boolean? = null,
): QuizSnapshot = QuizSnapshot(
    day = jour,
    daily = question,
    responses = reponses,
    challenges = defis,
    quizSets = themes,
    notificationsEnabled = notifications,
)

private fun rendu(
    snapshot: QuizSnapshot,
    userId: String? = MOI,
    selection: QuizSelection = QuizSelection(),
    friends: List<FriendLink> = emptyList(),
    busy: Boolean = false,
): QuizUiState = QuizRenderer.render(
    snapshot = snapshot,
    userId = userId,
    selection = selection,
    friends = friends,
    busy = busy,
    day = JOUR,
    now = MAINTENANT,
)

private fun vue(selection: QuizView) = QuizSelection(view = selection)

class QuizRendererTest {

    // --- La vue et la coquille ---------------------------------------------------------------

    @Test
    fun `la vue d'ouverture suit les deux arguments de la route`() {
        assertEquals(QuizView.HOME, QuizRenderer.initialView(null, null))
        assertEquals(QuizView.CREATE, QuizRenderer.initialView(AMI, null))
        assertEquals(QuizView.CHALLENGE, QuizRenderer.initialView(null, "c1"))
        // L'ami l'emporte sur le défi : c'est l'ordre du ternaire de l'original.
        assertEquals(QuizView.CREATE, QuizRenderer.initialView(AMI, "c1"))
    }

    @Test
    fun `le retour ferme l'ecran depuis l'accueil et remonte ailleurs`() {
        assertTrue(QuizRenderer.back(QuizView.HOME).close)
        assertEquals(QuizView.CHALLENGES, QuizRenderer.back(QuizView.CHALLENGE).view)
        assertFalse(QuizRenderer.back(QuizView.CHALLENGE).forgetDay)
        for (depart in listOf(QuizView.DAILY, QuizView.HISTORY, QuizView.CHALLENGES, QuizView.CREATE)) {
            val retour = QuizRenderer.back(depart)
            assertEquals(QuizView.HOME, retour.view, "retour depuis $depart")
            assertFalse(retour.close, "retour depuis $depart")
            assertTrue(retour.forgetDay, "retour depuis $depart")
        }
    }

    @Test
    fun `le titre suit la vue, et la liste des defis porte celui de l'accueil`() {
        assertEquals(QuizText.TITLE_DAILY, QuizRenderer.title(QuizView.DAILY))
        assertEquals(QuizText.TITLE_HISTORY, QuizRenderer.title(QuizView.HISTORY))
        assertEquals(QuizText.TITLE_CREATE, QuizRenderer.title(QuizView.CREATE))
        assertEquals(QuizText.TITLE_CHALLENGE, QuizRenderer.title(QuizView.CHALLENGE))
        // `view==='challenge'?'Défi entre amis':'Quiz'` : les défis tombent dans « Quiz ».
        assertEquals(QuizText.TITLE, QuizRenderer.title(QuizView.CHALLENGES))
        assertEquals(QuizText.TITLE, QuizRenderer.title(QuizView.HOME))
    }

    @Test
    fun `l'en-tete publie la vue, son titre et le fait que le retour ferme`() {
        val accueil = rendu(instantane())
        assertEquals(QuizView.HOME, accueil.view)
        assertEquals(QuizText.TITLE, accueil.title)
        assertTrue(accueil.backCloses)

        val jour = rendu(instantane(), selection = vue(QuizView.DAILY))
        assertEquals(QuizText.TITLE_DAILY, jour.title)
        assertFalse(jour.backCloses)
    }

    // --- Quelle question s'affiche -----------------------------------------------------------

    @Test
    fun `une question publiee hier ne passe pas pour celle du jour`() {
        // L'instantané est celui d'hier : la question qu'il porte n'est pas celle d'aujourd'hui.
        val etat = rendu(instantane(jour = HIER), selection = vue(QuizView.DAILY))
        assertNull(etat.daily)
    }

    @Test
    fun `le jour consulte donne sa question, et un jour disparu laisse celle du jour`() {
        val snapshot = instantane(reponses = listOf(reponse(jour = HIER, question = question(intitule = "Hier ?"))))

        val hier = rendu(snapshot, selection = QuizSelection(QuizView.DAILY, historyDay = HIER))
        assertEquals("Hier ?", hier.daily?.question)

        // **Le repli porte sur la réponse, pas sur la question.** Un jour demandé qui a disparu
        // de l'instantané ne donne **aucune réponse** — donc pas de correction, et « 1 / 1 » —,
        // mais la question retombe sur celle du jour, parce que l'instantané est celui
        // d'aujourd'hui. C'est l'expression de l'original : `response?.question ?? (data.day===day
        // ? data.daily : null)`. Ce test a d'abord été écrit à l'envers, en supposant qu'un jour
        // disparu laissait un écran vide ; c'est la mesure qui a tranché, et la combinaison
        // surprenante est désormais figée ici plutôt que supposée.
        val disparu = rendu(snapshot, selection = QuizSelection(QuizView.DAILY, historyDay = "2026-01-01"))
        assertEquals("Quelle est la première sourate ?", disparu.daily?.question)
        assertNull(disparu.daily?.correction)
        assertEquals(QuizText.DAILY_PROGRESS, disparu.daily?.progress)
    }

    @Test
    fun `la progression dit si la question du jour est deja repondue`() {
        val sans = rendu(instantane(), selection = vue(QuizView.DAILY))
        assertEquals(QuizText.DAILY_PROGRESS, sans.daily?.progress)

        val avec = rendu(instantane(reponses = listOf(reponse())), selection = vue(QuizView.DAILY))
        assertEquals(QuizText.DAILY_PROGRESS_DONE, avec.daily?.progress)
    }

    // --- La correction ----------------------------------------------------------------------

    @Test
    fun `une bonne reponse colore la bonne proposition et nomme la correction`() {
        val etat = rendu(instantane(reponses = listOf(reponse(choisie = "a", correcte = true))), selection = vue(QuizView.DAILY))
        val daily = assertNotNull(etat.daily)

        assertEquals(QuizText.CORRECT_TITLE, daily.correction?.title)
        assertEquals(AnswerState.CORRECT, daily.answers[0].state)
        assertTrue(daily.answers[0].chosen)
        assertEquals(AnswerState.IDLE, daily.answers[1].state)
        assertEquals(AnswerState.IDLE, daily.answers[2].state)
    }

    @Test
    fun `une mauvaise reponse colore la bonne et la choisie, separement`() {
        val etat = rendu(instantane(reponses = listOf(reponse(choisie = "b", correcte = false))), selection = vue(QuizView.DAILY))
        val daily = assertNotNull(etat.daily)

        assertEquals(QuizText.WRONG_TITLE, daily.correction?.title)
        // La bonne réponse est verte **sans** avoir été choisie ; c'est ce qui les distingue.
        assertEquals(AnswerState.CORRECT, daily.answers[0].state)
        assertFalse(daily.answers[0].chosen)
        assertEquals(AnswerState.WRONG, daily.answers[1].state)
        assertTrue(daily.answers[1].chosen)
        assertEquals(AnswerState.IDLE, daily.answers[2].state)
    }

    @Test
    fun `une reponse en attente ne revele aucune correction`() {
        // Le serveur n'a pas encore envoyé `correctAnswerId` : c'est l'état d'une réponse en
        // attente de synchronisation, et révéler la bonne réponse ici la divulguerait. La
        // question est passée **des deux côtés**, parce que c'est celle de la réponse qui est
        // lue dès qu'une réponse existe.
        val sansCorrection = question(bonneReponse = null)
        val etat = rendu(
            instantane(
                question = sansCorrection,
                reponses = listOf(reponse(choisie = "a", correcte = null, enAttente = true, question = sansCorrection)),
            ),
            selection = vue(QuizView.DAILY),
        )
        val daily = assertNotNull(etat.daily)

        assertTrue(daily.pending)
        assertNull(daily.correction)
        assertEquals(AnswerState.CHOSEN, daily.answers[0].state)
        assertEquals(AnswerState.IDLE, daily.answers[1].state)
    }

    @Test
    fun `sans compte les propositions ne sont pas cliquables, et plus du tout apres avoir repondu`() {
        val sansCompte = rendu(instantane(), userId = null, selection = vue(QuizView.DAILY))
        assertFalse(assertNotNull(sansCompte.daily).selectable)

        val avecCompte = rendu(instantane(), selection = vue(QuizView.DAILY))
        assertTrue(assertNotNull(avecCompte.daily).selectable)

        val repondu = rendu(instantane(reponses = listOf(reponse())), selection = vue(QuizView.DAILY))
        assertFalse(assertNotNull(repondu.daily).selectable)
    }

    @Test
    fun `un lien de source qui n'est pas https n'est pas propose`() {
        val sur = question(url = "https://exemple.test/1")
        val propose = rendu(
            instantane(question = sur, reponses = listOf(reponse(question = sur))),
            selection = vue(QuizView.DAILY),
        )
        assertEquals("https://exemple.test/1", assertNotNull(propose.daily).correction?.sourceUrl)

        val nonSur = question(url = "http://exemple.test/1")
        val refuse = rendu(
            instantane(question = nonSur, reponses = listOf(reponse(question = nonSur))),
            selection = vue(QuizView.DAILY),
        )
        assertNull(assertNotNull(refuse.daily).correction?.sourceUrl)
    }

    @Test
    fun `la source de la correction joint le titre et la reference`() {
        val etat = rendu(instantane(reponses = listOf(reponse())), selection = vue(QuizView.DAILY))
        assertEquals("Al-Fatiha · 1:1", assertNotNull(etat.daily).correction?.source)
    }

    @Test
    fun `le libelle d'une proposition dit si elle a ete choisie`() {
        val etat = rendu(instantane(reponses = listOf(reponse(choisie = "b"))), selection = vue(QuizView.DAILY))
        val answers = assertNotNull(etat.daily).answers
        assertEquals("B. Al-Baqara · réponse choisie", answers[1].label)
        assertEquals("A. Al-Fatiha", answers[0].label)
        assertEquals("C", answers[2].letter)
    }

    // --- L'historique ------------------------------------------------------------------------

    @Test
    fun `l'historique nomme aujourd'hui et date les autres jours`() {
        val etat = rendu(
            instantane(
                reponses = listOf(
                    reponse(jour = JOUR, correcte = true),
                    reponse(jour = HIER, choisie = "b", correcte = false),
                ),
            ),
            selection = vue(QuizView.HISTORY),
        )
        assertEquals(QuizText.HISTORY_TODAY, etat.history[0].label)
        assertEquals(Tone.GOOD, etat.history[0].tone)
        assertEquals(QuizText.HISTORY_CORRECT, etat.history[0].status)
        assertEquals("5 octobre", etat.history[1].label)
        assertEquals(Tone.BAD, etat.history[1].tone)
    }

    @Test
    fun `une reponse en attente est grisee, et une reponse non jugee est annoncee mauvaise`() {
        val etat = rendu(
            instantane(
                reponses = listOf(
                    reponse(enAttente = true, correcte = null),
                    reponse(jour = HIER, correcte = null, enAttente = null),
                ),
            ),
            selection = vue(QuizView.HISTORY),
        )
        assertEquals(Tone.MUTED, etat.history[0].tone)
        assertEquals(QuizText.HISTORY_PENDING, etat.history[0].status)
        // Ni en attente, ni correcte : l'original tombe dans « mauvaise réponse ». Reproduit.
        assertEquals(Tone.BAD, etat.history[1].tone)
        assertEquals(QuizText.HISTORY_WRONG, etat.history[1].status)
    }

    @Test
    fun `une date d'historique illisible affiche le jour brut au lieu d'un texte d'erreur`() {
        val etat = rendu(
            instantane(reponses = listOf(reponse(jour = "2026-13-45"))),
            selection = vue(QuizView.HISTORY),
        )
        assertEquals("2026-13-45", etat.history[0].label)
    }

    // --- Les lignes de défi ------------------------------------------------------------------

    @Test
    fun `une ligne de defi nomme l'autre joueur, dans les deux sens`() {
        val cree = rendu(
            instantane(defis = listOf(defi(createur = MOI, adversaire = AMI))),
            selection = vue(QuizView.CHALLENGES),
        )
        assertEquals("Aïcha", cree.challenges[0].name)

        // Le même défi vu de l'autre côté : le nom affiché doit changer.
        val recu = rendu(
            instantane(defis = listOf(defi(createur = AMI, adversaire = MOI))),
            selection = vue(QuizView.CHALLENGES),
        )
        assertEquals("Moi", recu.challenges[0].name)
    }

    @Test
    fun `c'est a moi de jouer seulement quand il me reste des questions`() {
        val aMoi = rendu(instantane(defis = listOf(defi())), selection = vue(QuizView.CHALLENGES))
        assertEquals(QuizText.CHALLENGE_YOUR_TURN, aMoi.challenges[0].status)
        assertTrue(aMoi.challenges[0].yourTurn)

        val toutRepondu = rendu(
            instantane(
                defis = listOf(
                    defi(
                        reponses = listOf(
                            reponseDeDefi(MOI, "q1"),
                            reponseDeDefi(MOI, "q2"),
                            reponseDeDefi(MOI, "q3"),
                        ),
                    ),
                ),
            ),
            selection = vue(QuizView.CHALLENGES),
        )
        assertEquals(QuizText.CHALLENGE_WAITING, toutRepondu.challenges[0].status)
        assertFalse(toutRepondu.challenges[0].yourTurn)
    }

    @Test
    fun `un defi termine affiche les deux scores, et un defi expire n'affiche rien`() {
        val termine = rendu(
            instantane(
                defis = listOf(
                    defi(
                        statut = ChallengeStatus.COMPLETED,
                        reponses = listOf(
                            reponseDeDefi(MOI, "q1", correcte = true),
                            reponseDeDefi(MOI, "q2", correcte = true),
                            reponseDeDefi(MOI, "q3", correcte = false, choisie = "b"),
                            reponseDeDefi(AMI, "q1", correcte = true),
                            reponseDeDefi(AMI, "q2", correcte = false, choisie = "b"),
                            reponseDeDefi(AMI, "q3", correcte = false, choisie = "b"),
                        ),
                    ),
                ),
            ),
            selection = vue(QuizView.CHALLENGES),
        )
        assertEquals("2/3 contre 1/3", termine.challenges[0].detail)

        val expire = rendu(
            instantane(defis = listOf(defi(statut = ChallengeStatus.EXPIRED, expire = IL_Y_A_UNE_HEURE))),
            selection = vue(QuizView.CHALLENGES),
        )
        assertEquals("", expire.challenges[0].detail)
    }

    @Test
    fun `un defi dont la date est passee est expire meme si le serveur le dit en cours`() {
        // `Dates.parseIsoMillis` rend 0 sur une date abîmée, donc l'expiration est établie par
        // la date, et pas seulement par le statut que le serveur a bien voulu publier.
        val etat = rendu(
            instantane(defis = listOf(defi(expire = IL_Y_A_UNE_HEURE))),
            selection = vue(QuizView.CHALLENGES),
        )
        assertEquals(QuizText.CHALLENGE_EXPIRED, etat.challenges[0].status)
    }

    @Test
    fun `les heures restantes arrondissent au-dessus`() {
        val trois = rendu(instantane(defis = listOf(defi())), selection = vue(QuizView.CHALLENGES))
        assertEquals("3 h", trois.challenges[0].detail)

        // Une demi-heure restante s'affiche « 1 h », jamais « 0 h » : c'est l'arrondi au-dessus
        // de l'original, et c'est ce qui évite d'annoncer zéro pendant la dernière heure.
        //
        // La borne `maxOf(0, …)` du calcul, elle, n'est **pas** atteignable ici : un défi dont la
        // date est passée est classé « Expiré » par la même valeur, donc sa ligne n'affiche pas
        // d'heures du tout. Elle est conservée par fidélité à l'original, et le dire vaut mieux
        // que d'écrire un test qui la simulerait.
        val demi = rendu(
            instantane(defis = listOf(defi(expire = DANS_DEMI_HEURE))),
            selection = vue(QuizView.CHALLENGES),
        )
        assertEquals("1 h", demi.challenges[0].detail)
    }

    // --- L'accueil ---------------------------------------------------------------------------

    @Test
    fun `l'accueil compte les defis en cours, et pas les termines ni les expires`() {
        val etat = rendu(
            instantane(
                defis = listOf(
                    defi(id = "en-cours"),
                    defi(id = "fini", statut = ChallengeStatus.COMPLETED),
                    defi(id = "perime", statut = ChallengeStatus.EXPIRED),
                ),
            ),
        )
        assertEquals("1 défis en cours", etat.home?.running)
        assertEquals(3, etat.home?.recent?.size)
    }

    @Test
    fun `l'accueil ne garde que les trois premiers defis`() {
        val etat = rendu(
            instantane(defis = listOf(defi(id = "a"), defi(id = "b"), defi(id = "c"), defi(id = "d"))),
        )
        assertEquals(listOf("a", "b", "c"), etat.home?.recent?.map { it.id })
    }

    @Test
    fun `l'accueil annonce la question terminee avant la question disponible`() {
        val terminee = rendu(instantane(reponses = listOf(reponse())), selection = vue(QuizView.HOME))
        assertEquals(QuizText.HOME_DAILY_DONE, terminee.home?.dailyStatus)

        val disponible = rendu(instantane(), selection = vue(QuizView.HOME))
        assertEquals(QuizText.HOME_DAILY_AVAILABLE, disponible.home?.dailyStatus)

        // L'instantané est celui d'hier, donc la question qu'il porte n'est pas disponible.
        val aucune = rendu(instantane(jour = HIER), selection = vue(QuizView.HOME))
        assertEquals(QuizText.HOME_DAILY_NONE, aucune.home?.dailyStatus)
    }

    @Test
    fun `les notifications sont activees quand la valeur est absente`() {
        assertTrue(assertNotNull(rendu(instantane()).home).notifications.enabled)
        assertFalse(assertNotNull(rendu(instantane(notifications = false)).home).notifications.enabled)
        assertTrue(assertNotNull(rendu(instantane(notifications = true)).home).notifications.enabled)
    }

    // --- La création d'un défi ---------------------------------------------------------------

    @Test
    fun `la creation ne propose que les amis acceptes`() {
        val etat = rendu(
            instantane(),
            selection = vue(QuizView.CREATE),
            friends = listOf(
                lien(id = "accepte", statut = FriendLinkStatus.ACCEPTED, autreId = "a1", nom = "Aïcha"),
                lien(id = "attente", statut = FriendLinkStatus.PENDING, autreId = "p1", nom = "En attente"),
                lien(id = "bloque", statut = FriendLinkStatus.BLOCKED, autreId = "b1", nom = "Bloqué"),
            ),
        )
        val create = assertNotNull(etat.create)
        assertEquals(1, create.friends.size)
        assertEquals("Aïcha", create.friends[0].name)
        // L'identifiant, et non le chemin de l'avatar : c'est lui que l'écran renvoie au dépôt
        // quand l'ami est choisi, et le chemin n'entre pas dans l'état affichable.
        assertEquals("a1", create.friends[0].id)
    }

    @Test
    fun `un lien sans profil propose quand meme un nom`() {
        val etat = rendu(
            instantane(),
            selection = vue(QuizView.CREATE),
            friends = listOf(lien(autreId = null)),
        )
        assertEquals(QuizText.CREATE_FRIEND_FALLBACK, assertNotNull(etat.create).friends[0].name)
    }

    @Test
    fun `la creation ne peut pas lancer sans ami, ni pendant qu'un envoi est en vol`() {
        val sansAmi = rendu(instantane(), selection = vue(QuizView.CREATE), friends = listOf(lien()))
        assertFalse(assertNotNull(sansAmi.create).canLaunch)

        val avecAmi = rendu(
            instantane(),
            selection = QuizSelection(QuizView.CREATE, friendId = AMI),
            friends = listOf(lien()),
        )
        val create = assertNotNull(avecAmi.create)
        assertTrue(create.canLaunch)
        assertEquals(QuizText.CREATE_LAUNCH, create.launchLabel)
        assertTrue(create.friends[0].selected)

        val enVol = rendu(
            instantane(),
            selection = QuizSelection(QuizView.CREATE, friendId = AMI),
            friends = listOf(lien()),
            busy = true,
        )
        val pendant = assertNotNull(enVol.create)
        assertFalse(pendant.canLaunch)
        assertEquals(QuizText.CREATE_BUSY, pendant.launchLabel)
    }

    @Test
    fun `la creation ne peut pas lancer sans compte`() {
        val etat = rendu(
            instantane(),
            userId = null,
            selection = QuizSelection(QuizView.CREATE, friendId = AMI),
            friends = listOf(lien()),
        )
        assertFalse(assertNotNull(etat.create).canLaunch)
    }

    @Test
    fun `les quiz thematiques portent leur categorie, et l'aleatoire est choisi par defaut`() {
        val themes = listOf(ThemedQuiz(id = "s1", title = "Sciences", category = "Coran"))
        val etat = rendu(instantane(themes = themes), selection = vue(QuizView.CREATE))

        val create = assertNotNull(etat.create)
        assertEquals("Sciences · Coran", create.sets[0].label)
        assertTrue(create.randomSelected)
        assertEquals(QuizRenderer.DEFAULT_COUNT, create.count)
        assertEquals(listOf(QuizText.CREATE_COUNT_5, QuizText.CREATE_COUNT_10), create.counts)

        val choisi = rendu(
            instantane(themes = themes),
            selection = QuizSelection(QuizView.CREATE, quizSetId = "s1"),
        )
        val avecTheme = assertNotNull(choisi.create)
        assertFalse(avecTheme.randomSelected)
        assertTrue(avecTheme.sets[0].selected)
    }

    // --- Le défi ouvert ----------------------------------------------------------------------

    @Test
    fun `un defi absent du cache ne donne rien a montrer`() {
        val etat = rendu(instantane(), selection = QuizSelection(QuizView.CHALLENGE, challengeId = "inconnu"))
        assertNull(etat.challenge)
    }

    @Test
    fun `un defi en cours propose la premiere question sans reponse et sans correction`() {
        val etat = rendu(
            instantane(defis = listOf(defi(reponses = listOf(reponseDeDefi(MOI, "q1"))))),
            selection = QuizSelection(QuizView.CHALLENGE, challengeId = "c1"),
        )
        val play = assertNotNull(assertNotNull(etat.challenge).play as? ChallengePlay.Next)

        assertEquals("2 / 3 · 3 h restantes", play.progress)
        assertEquals("Deuxième ?", play.question)
        assertTrue(play.selectable)
        // Aucune proposition n'est colorée : la correction n'arrive qu'à la fin du défi.
        assertTrue(play.answers.all { it.state == AnswerState.IDLE })
        assertEquals(QuizText.CHALLENGE_REVEAL_LATER, play.reveal)
    }

    @Test
    fun `pendant qu'une reponse est en vol, les propositions du defi ne sont pas cliquables`() {
        val etat = rendu(
            instantane(defis = listOf(defi())),
            selection = QuizSelection(QuizView.CHALLENGE, challengeId = "c1"),
            busy = true,
        )
        val play = assertNotNull(assertNotNull(etat.challenge).play as? ChallengePlay.Next)
        assertFalse(play.selectable)
    }

    @Test
    fun `un defi ou j'ai tout repondu attend l'autre joueur`() {
        val etat = rendu(
            instantane(
                defis = listOf(
                    defi(
                        reponses = listOf(
                            reponseDeDefi(MOI, "q1"),
                            reponseDeDefi(MOI, "q2"),
                            reponseDeDefi(MOI, "q3"),
                        ),
                    ),
                ),
            ),
            selection = QuizSelection(QuizView.CHALLENGE, challengeId = "c1"),
        )
        val attente = assertNotNull(assertNotNull(etat.challenge).play as? ChallengePlay.Waiting)
        assertEquals(QuizText.CHALLENGE_WAITING_TITLE, attente.title)
        assertEquals("En attente de Aïcha", attente.waitingFor)
    }

    @Test
    fun `un defi expire ne propose aucune question`() {
        val etat = rendu(
            instantane(defis = listOf(defi(statut = ChallengeStatus.EXPIRED))),
            selection = QuizSelection(QuizView.CHALLENGE, challengeId = "c1"),
        )
        assertEquals(ChallengePlay.Expired, assertNotNull(etat.challenge).play)
    }

    @Test
    fun `un defi termine affiche les deux scores, le verdict et chaque correction`() {
        val etat = rendu(
            instantane(
                defis = listOf(
                    defi(
                        statut = ChallengeStatus.COMPLETED,
                        reponses = listOf(
                            reponseDeDefi(MOI, "q1", correcte = true),
                            reponseDeDefi(MOI, "q2", correcte = false, choisie = "b"),
                            reponseDeDefi(AMI, "q1", correcte = true),
                            reponseDeDefi(AMI, "q2", correcte = true),
                            reponseDeDefi(AMI, "q3", correcte = true),
                        ),
                    ),
                ),
            ),
            selection = QuizSelection(QuizView.CHALLENGE, challengeId = "c1"),
        )
        val fini = assertNotNull(assertNotNull(etat.challenge).play as? ChallengePlay.Finished)

        assertEquals(listOf("Moi · 1 / 3", "Aïcha · 3 / 3"), fini.scores)
        // Le verdict se compte en **bonnes réponses**, pas en réponses données.
        assertEquals("Aïcha remporte le défi", fini.verdict)
        assertEquals(3, fini.questions.size)
        assertEquals(QuizText.CORRECT_TITLE, fini.questions[0].correction.title)
        assertEquals(QuizText.WRONG_TITLE, fini.questions[1].correction.title)
        // Une question à laquelle je n'ai pas répondu porte quand même la correction.
        assertEquals(QuizText.WRONG_TITLE, fini.questions[2].correction.title)
        // Ma réponse est colorée dans le défi terminé, et la bonne réponse l'est aussi.
        assertEquals(AnswerState.CORRECT, fini.questions[0].answers[0].state)
        assertEquals(AnswerState.WRONG, fini.questions[1].answers[1].state)
        assertEquals(AnswerState.CORRECT, fini.questions[1].answers[0].state)
    }

    @Test
    fun `une egalite ne nomme personne`() {
        val etat = rendu(
            instantane(
                defis = listOf(
                    defi(
                        statut = ChallengeStatus.COMPLETED,
                        reponses = listOf(
                            reponseDeDefi(MOI, "q1", correcte = true),
                            reponseDeDefi(AMI, "q1", correcte = true),
                        ),
                    ),
                ),
            ),
            selection = QuizSelection(QuizView.CHALLENGE, challengeId = "c1"),
        )
        val fini = assertNotNull(assertNotNull(etat.challenge).play as? ChallengePlay.Finished)
        assertEquals(QuizText.CHALLENGE_TIE, fini.verdict)
    }

    // --- Ce que le renderer ne remplit pas ---------------------------------------------------

    @Test
    fun `seule la vue courante porte une charge utile`() {
        val snapshot = instantane(reponses = listOf(reponse()), defis = listOf(defi()))
        val etat = rendu(snapshot, selection = vue(QuizView.HOME))

        assertNotNull(etat.home)
        assertNull(etat.daily)
        assertTrue(etat.history.isEmpty())
        assertTrue(etat.challenges.isEmpty())
        assertNull(etat.create)
        assertNull(etat.challenge)
    }

    @Test
    fun `le compte ouvert est publie, et l'absence de compte aussi`() {
        assertTrue(rendu(instantane()).signedIn)
        assertFalse(rendu(instantane(), userId = null).signedIn)
    }
}
