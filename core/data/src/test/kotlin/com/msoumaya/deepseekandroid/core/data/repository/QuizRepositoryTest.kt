package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.local.JsonFileStore
import com.msoumaya.deepseekandroid.core.data.local.QuizCacheStore
import com.msoumaya.deepseekandroid.core.data.local.QuizOutbox
import com.msoumaya.deepseekandroid.core.data.local.QuizOutboxStore
import com.msoumaya.deepseekandroid.core.data.remote.DailyAnswerPayload
import com.msoumaya.deepseekandroid.core.data.remote.QuizSource
import com.msoumaya.deepseekandroid.core.domain.Quiz
import com.msoumaya.deepseekandroid.core.domain.QuizText
import com.msoumaya.deepseekandroid.core.model.AppJson
import com.msoumaya.deepseekandroid.core.model.DailyResponse
import com.msoumaya.deepseekandroid.core.model.QuizAnswer
import com.msoumaya.deepseekandroid.core.model.QuizQuestion
import com.msoumaya.deepseekandroid.core.model.QuizSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Éprouve le dépôt « Quiz » : ce qu'il lit, dans quel ordre, et ce qu'un échec fait à ce qui est
 * déjà à l'écran.
 *
 * ## Ce qui est mesuré ici, et pourquoi c'est ici
 *
 * Le dépôt ne contient que des règles d'**ordonnancement et de survie** : publier le disque avant
 * le réseau, vider la file avant de lire, ne pas perdre une réponse faite hors ligne, ne pas
 * garder le quiz d'un compte sous le nom du suivant, ne pas remplacer par une panne ce qui est
 * déjà affiché. Aucune ne lève d'exception quand elle est fausse — et c'est précisément pourquoi
 * elles sont figées :
 *
 *  - publier le réseau avant le disque afficherait un écran vide sans connexion, alors que tout
 *    le quiz est sur l'appareil ;
 *  - lire avant de vider ferait lire un instantané **antérieur** à l'envoi, donc sans la réponse
 *    qu'on vient de faire ;
 *  - perdre une réponse hors ligne efface un travail que la personne a fait ;
 *  - garder le quiz du compte précédent affiche les statistiques d'un autre sous son nom ;
 *  - remplacer par une panne un quiz déjà lu fait disparaître ce qu'on avait.
 *
 * ## Ce que ce fichier ne couvre pas, et où la limite passe
 *
 * **La lecture du champ `error` d'une `RestException` n'est pas éprouvée ici.** Elle l'est par
 * lecture de l'artefact installé (`postgrest-kt-android-3.8.0.aar`, classes `RestException` et
 * `PostgrestRestException`, relevées au `javap`), et la raison est dans `RestError.kt`. Construire
 * une telle exception demanderait une `HttpResponse` ktor dont le constructeur **déréférence**
 * `request.url` : il faudrait donc aussi une `HttpClientCall`, donc un vrai `HttpClient` — une
 * doublure de trois classes adossée aux entrailles d'une bibliothèque, qui casserait à sa
 * prochaine montée de version en accusant le produit. Ce qui **est** éprouvé ici, c'est qu'un
 * refus ordinaire traverse le dépôt avec son texte ; la règle de choix entre code, texte et
 * repli, elle, est dans `QuizMessagesTest`, avec le domaine.
 *
 * ## Pourquoi [settle] attend sur une condition, et non sur une horloge
 *
 * Le chargement part d'un collecteur lancé à la construction, donc dans le `backgroundScope` du
 * test — et `advanceUntilIdle()` n'exécute pas ce travail-là, ce qui est déjà mesuré dans ce
 * dépôt (voir `SocialRepositoryTest` et `AudioSessionControllerTest`).
 *
 * **Ici, `advanceTimeBy` ne suffit pas non plus, et c'est mesuré :** les deux magasins lisent et
 * écrivent par `Dispatchers.IO` (`BlobFile`). Le rafraîchissement quitte donc le répartiteur de
 * test et y revient par un **vrai fil**, que l'horloge virtuelle n'attend pas. Avancer l'horloge
 * ne fait que **lancer** le travail : sept épreuves de ce fichier lisaient l'état d'avant le
 * rafraîchissement, et deux d'entre elles passaient quand même — celles qui demandent de
 * constater une absence. C'est la bonne manière d'apprendre que le harnais mentait.
 *
 * On attend donc que le dépôt n'ait plus rien **en vol** ([QuizRepository.enTravail]), avec un
 * délai de garde qui échoue en le disant. Un `Thread.sleep` fixe aurait rendu le harnais instable
 * au lieu de le rendre juste, et une attente sans condition n'aurait pas su dire si le travail
 * avait abouti. **Sonder une accalmie de l'état ne suffit pas**, et c'est la seconde leçon de ce
 * fichier : voir [settle].
 */
class QuizRepositoryTest {

    private val moi = "moi-0000"
    private val autre = "autre-0000"

    private lateinit var root: File

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("quiz-repository-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    /** Le jour du jour, celui que le dépôt demande au serveur. */
    private val jour: String get() = Quiz.quizDay()

    private fun question(id: String = "q1", publicationDate: String? = jour) = QuizQuestion(
        id = id,
        category = "Coran",
        question = "Quelle est la première sourate ?",
        answers = listOf(QuizAnswer("a", "Al Fâtiha"), QuizAnswer("b", "Al Baqarah")),
        publicationDate = publicationDate,
    )

    private fun nouveauCache() = QuizCacheStore(root)

    private fun nouvelleFile() = QuizOutboxStore(
        store = JsonFileStore(
            file = File(root, "quiz_outbox.json"),
            serializer = QuizOutbox.serializer(),
            default = { QuizOutbox() },
        ),
        nowIso = { "2026-10-06T10:00:00Z" },
    )

    private fun TestScope.quiz(
        source: QuizSource?,
        owners: FakeOwners,
        cache: QuizCacheStore = nouveauCache(),
        outbox: QuizOutboxStore = nouvelleFile(),
    ) = QuizRepository(
        source = source,
        session = owners,
        cache = cache,
        outbox = outbox,
        scope = backgroundScope,
    )

    /**
     * Laisse le travail de fond aboutir, en attendant que le dépôt n'ait plus rien **en vol**.
     *
     * **On sonde le dépôt, et non une accalmie de l'état**, et c'est une leçon d'intégration
     * continue. La version précédente guettait trois lectures stables de l'état publié : elle
     * passait sur une machine rapide, et elle est tombée en CI sur « la fusion garde une réponse
     * locale que le serveur ne connaît pas », qui attendait `[jour, 2026-10-05]` et lisait
     * `[jour]`.
     *
     * La raison est dans le dépôt lui-même : il publie l'instantané du **disque** avant d'appeler
     * le serveur — c'est délibéré, un appareil hors connexion doit afficher le quiz quand même —,
     * et pendant l'aller-retour **rien ne bouge plus**. Trois lectures stables pouvaient donc
     * toutes tomber au milieu du travail, et l'attente rendait la main avant la fusion. C'est le
     * même piège que `RecitationRepositoryTest`, résolu de la même façon :
     * [QuizRepository.enTravail].
     *
     * Deux tours sans rien en vol, et non un seul : le rafraîchissement recommence tant que la
     * file n'est pas vide — la purge précède la lecture, et la boucle repart —, donc un tour calme
     * peut être un tour **entre** deux travaux. Le sommeil n'est plus l'attente, il n'en est que
     * le pas d'échantillonnage.
     */
    private fun TestScope.settle(repository: QuizRepository, millis: Long = 1_000) {
        advanceTimeBy(millis)

        val limite = System.nanoTime() + DELAI_DE_GARDE_NANOS
        var tours = 0
        while (tours < 2 && System.nanoTime() < limite) {
            runCurrent()
            tours = if (repository.enTravail) 0 else tours + 1
            Thread.sleep(2)
        }

        if (tours < 2) {
            error(
                "le travail de fond n'a pas abouti dans le delai : le harnais ne peut pas dire " +
                    "si le depot est fautif ou si l'attente est trop courte",
            )
        }
    }

    private fun reponseDuJour(day: String = jour) = DailyAnswerPayload(
        questionId = "q1",
        answerId = "a",
        day = day,
        answeredAt = "2026-10-06T09:00:00Z",
    )

    // ------------------------------------------------------------------
    // Sans compte
    // ------------------------------------------------------------------

    @Test
    fun `sans compte ouvert, la source n'est jamais interrogee`() = runTest {
        val source = FakeQuizSource()
        val repository = quiz(source, FakeOwners(null))
        settle(repository)

        assertTrue(source.calls.isEmpty(), "aucun appel ne doit partir sans compte ouvert")
    }

    @Test
    fun `sans compte, repondre ne range rien et le dit`() = runTest {
        val file = nouvelleFile()
        val repository = quiz(FakeQuizSource(), FakeOwners(null), outbox = file)
        settle(repository)

        assertFalse(repository.answerDaily(question(), "a"))
        settle(repository)

        assertEquals(QuizText.SIGNED_OUT, repository.state.value.notice)
        assertTrue(file.list(moi).isEmpty(), "aucune reponse ne doit etre rangee sans compte")
        assertNull(repository.state.value.snapshot)
    }

    // ------------------------------------------------------------------
    // La chronologie : disque, file, serveur, fusion
    // ------------------------------------------------------------------

    @Test
    fun `la file est videe avant que l'instantane ne soit lu`() = runTest {
        val file = nouvelleFile()
        file.enqueue(moi, "$moi:$jour", AppJson.encodeToString(reponseDuJour()))
        val source = FakeQuizSource()

        val repository = quiz(source, FakeOwners(moi), outbox = file)
        settle(repository)

        assertEquals(
            listOf("reponse du jour", "instantane"),
            source.calls,
            "l'envoi doit preceder la lecture : sinon l'instantane lu est anterieur a la reponse",
        )
        assertTrue(file.list(moi).isEmpty(), "une entree acceptee doit quitter la file")
    }

    @Test
    fun `l'instantane du disque est publie avant le premier appel reseau`() = runTest {
        val source = FakeQuizSource()
        val repository = quiz(source, FakeOwners(moi))
        val vus = mutableListOf<QuizSnapshot?>()
        source.probe = { vus += repository.state.value.snapshot }

        repository.refresh()

        assertTrue(vus.isNotEmpty(), "la doublure doit avoir ete appelee")
        assertTrue(
            vus.all { it != null },
            "l'instantane du disque doit etre publie avant le reseau : c'est ce qui affiche le " +
                "quiz sans connexion",
        )
    }

    @Test
    fun `la fusion garde une reponse locale que le serveur ne connait pas`() = runTest {
        // Le serveur ne connait pas la reponse du jour — elle attend encore dans la file. La
        // fusion doit la garder : sans cela, repondre hors ligne ferait disparaitre la reponse.
        nouveauCache().accountFor(moi).update {
            it.copy(
                responses = listOf(
                    DailyResponse("q1", jour, "a", "2026-10-06T09:00:00Z", question(), pending = true),
                ),
            )
        }
        val source = FakeQuizSource().apply {
            snapshotValue = QuizSnapshot(
                day = jour,
                responses = listOf(
                    DailyResponse("q0", "2026-10-05", "b", "2026-10-05T09:00:00Z", question("q0", "2026-10-05")),
                ),
            )
        }

        val repository = quiz(source, FakeOwners(moi))
        settle(repository)

        val reponses = repository.state.value.snapshot?.responses.orEmpty()
        assertEquals(listOf(jour, "2026-10-05"), reponses.map { it.day })
        assertEquals(true, reponses.first { it.day == jour }.pending, "la reponse locale reste en attente")
    }

    // ------------------------------------------------------------------
    // Hors ligne
    // ------------------------------------------------------------------

    @Test
    fun `une reponse faite hors ligne est rangee puis renvoyee`() = runTest {
        val file = nouvelleFile()
        val source = FakeQuizSource().apply {
            failSnapshot = IOException("reseau injoignable")
            failAnswerDaily = IOException("reseau injoignable")
        }
        val repository = quiz(source, FakeOwners(moi), outbox = file)
        settle(repository)

        assertTrue(repository.answerDaily(question(), "a"))
        settle(repository)

        assertEquals(1, file.list(moi).size, "la reponse doit attendre dans la file")
        val enAttente = repository.state.value.snapshot?.responses?.firstOrNull()
        assertEquals(true, enAttente?.pending, "elle doit etre marquee en attente")
        assertNull(repository.state.value.failure, "la reponse est a l'ecran : ce n'est plus une panne")
        assertNotNull(repository.state.value.notice, "mais son envoi rate doit etre signale")

        // Ce que l'ecran porte est ce qui doit partir : c'est l'instant de la reponse sur
        // l'appareil, et non celui de l'envoi, qui compte pour la fenetre de tolerance.
        val instantEnregistre = enAttente?.answeredAt

        // Le reseau revient.
        source.failSnapshot = null
        source.failAnswerDaily = null
        source.snapshotValue = QuizSnapshot(day = jour, daily = question())
        repository.refresh()

        assertEquals(1, source.sentAnswers.size, "la reponse doit partir au retour du reseau")
        assertEquals("q1", source.sentAnswers.first().questionId)
        assertEquals(
            instantEnregistre,
            source.sentAnswers.first().answeredAt,
            "l'instant transmis est celui de la reponse, et non celui de l'envoi",
        )
        assertTrue(file.list(moi).isEmpty(), "la file doit etre videe")
    }

    @Test
    fun `une seconde reponse le meme jour ne range rien de plus`() = runTest {
        val file = nouvelleFile()
        val source = FakeQuizSource().apply {
            failSnapshot = IOException("hors ligne")
            failAnswerDaily = IOException("hors ligne")
        }
        val repository = quiz(source, FakeOwners(moi), outbox = file)
        settle(repository)

        assertTrue(repository.answerDaily(question(), "a"))
        settle(repository)
        assertEquals(1, file.list(moi).size)

        // Une seconde reponse le meme jour : ce n'est pas un echec, c'est la regle « une
        // participation par jour ».
        assertTrue(repository.answerDaily(question(), "b"))
        settle(repository)

        assertEquals(1, file.list(moi).size, "une seconde reponse ne doit pas enfiler une entree")
        assertEquals(1, repository.state.value.snapshot?.responses?.size, "ni ajouter une reponse")
        assertEquals("a", repository.state.value.snapshot?.responses?.first()?.selectedAnswerId)
    }

    @Test
    fun `un echec de rafraichissement n'efface pas ce qui est deja a l'ecran`() = runTest {
        val source = FakeQuizSource().apply {
            snapshotValue = QuizSnapshot(day = jour, daily = question())
        }
        val repository = quiz(source, FakeOwners(moi))
        settle(repository)
        assertNotNull(repository.state.value.snapshot?.daily)

        source.failSnapshot = IOException("coupure")
        repository.refresh()

        assertNull(repository.state.value.failure, "la panne ne doit pas remplacer ce qui est affiche")
        assertEquals("coupure", repository.state.value.notice, "elle doit seulement etre signalee")
        assertNotNull(repository.state.value.snapshot?.daily, "et le quiz doit rester")
    }

    @Test
    fun `un echec sans rien a montrer est une panne`() = runTest {
        val source = FakeQuizSource().apply { failSnapshot = IOException("reseau injoignable") }
        val repository = quiz(source, FakeOwners(moi))
        settle(repository)

        assertEquals("reseau injoignable", repository.state.value.failure)
        assertNull(
            repository.state.value.notice,
            "un avis se lirait comme la confirmation d'autre chose : il n'y a rien a l'ecran",
        )
    }

    @Test
    fun `un echec d'envoi ne perd pas la reponse en attente`() = runTest {
        val file = nouvelleFile()
        val source = FakeQuizSource().apply { failAnswerDaily = IOException("hors ligne") }
        val repository = quiz(source, FakeOwners(moi), outbox = file)
        settle(repository)

        repository.answerDaily(question(), "a")
        settle(repository)

        // Le refus laisse l'entree en place : c'est ce qui permet de la renvoyer plus tard.
        val restante = file.list(moi)
        assertEquals(1, restante.size)
        assertEquals("q1", AppJson.decodeFromString<DailyAnswerPayload>(restante.first().payload).questionId)
    }

    // ------------------------------------------------------------------
    // Comptes
    // ------------------------------------------------------------------

    @Test
    fun `changer de compte efface le quiz du precedent`() = runTest {
        // Le serveur ne rend **rien** : tout ce qui s'affiche vient donc du disque, et c'est
        // exactement ce qu'il faut pour mesurer une fuite entre comptes. Une doublure qui
        // rendrait le même instantané à tout le monde ne mesurerait rien — ce qui s'afficherait
        // viendrait d'elle, et non du compte précédent.
        nouveauCache().accountFor(moi).update {
            it.copy(
                responses = listOf(
                    DailyResponse("q1", jour, "a", "2026-10-06T09:00:00Z", question(), pending = true),
                ),
            )
        }
        val owners = FakeOwners(moi)
        val repository = quiz(FakeQuizSource(), owners)
        settle(repository)
        assertEquals(
            1,
            repository.state.value.snapshot?.responses?.size,
            "la reponse du compte ouvert doit s'afficher",
        )

        owners.setOwner(autre)
        settle(repository)

        assertTrue(
            repository.state.value.snapshot?.responses.isNullOrEmpty(),
            "la reponse du compte precedent ne doit pas rester sous le nom du suivant",
        )
    }

    @Test
    fun `la deconnexion efface le quiz affiche`() = runTest {
        // Se deconnecter n'est pas changer de compte : c'est le cas ou **rien** ne doit rester.
        // Le serveur ne rend rien non plus, donc ce qui s'affiche vient du disque, et ce qui
        // reste a l'ecran apres la deconnexion serait le quiz d'un compte ferme.
        nouveauCache().accountFor(moi).update {
            it.copy(
                responses = listOf(
                    DailyResponse("q1", jour, "a", "2026-10-06T09:00:00Z", question(), pending = true),
                ),
            )
        }
        val owners = FakeOwners(moi)
        val repository = quiz(FakeQuizSource(), owners)
        settle(repository)
        assertEquals(1, repository.state.value.snapshot?.responses?.size)

        owners.setOwner(null)
        settle(repository)

        assertNull(
            repository.state.value.snapshot,
            "sans compte, rien ne doit rester a l'ecran : c'est le quiz d'un compte ferme",
        )
    }

    @Test
    fun `les reponses d'un compte ne partent pas pour un autre`() = runTest {
        val file = nouvelleFile()
        file.enqueue(moi, "$moi:$jour", AppJson.encodeToString(reponseDuJour()))
        val source = FakeQuizSource()

        val repository = quiz(source, FakeOwners(autre), outbox = file)
        settle(repository)

        assertTrue(source.sentAnswers.isEmpty(), "la file d'un compte ne doit pas partir sous un autre")
        assertEquals(1, file.list(moi).size, "et elle doit rester intacte")
    }

    // ------------------------------------------------------------------
    // Gestes
    // ------------------------------------------------------------------

    @Test
    fun `creer un defi rend son identifiant et relit l'instantane`() = runTest {
        val source = FakeQuizSource()
        val repository = quiz(source, FakeOwners(moi))
        settle(repository)
        source.calls.clear()

        assertEquals("defi-0001", repository.createChallenge("ami-0000", 10))
        assertEquals(listOf("creation de defi", "instantane"), source.calls)
        assertEquals(Triple("ami-0000", 10, null), source.createdChallenges.single())
        assertFalse(repository.state.value.busy)
    }

    @Test
    fun `creer un defi sans reseau dit qu'il faut une connexion`() = runTest {
        // L'original refuse **avant** d'essayer, en interrogeant l'etat du reseau ; ce portage
        // n'a pas de couche de connectivite, donc il refuse **apres**. La phrase est la meme.
        val source = FakeQuizSource().apply { failCreateChallenge = IOException("pas de route") }
        val repository = quiz(source, FakeOwners(moi))
        settle(repository)

        assertNull(repository.createChallenge("ami-0000", 10))
        assertEquals(QuizText.CHALLENGE_OFFLINE, repository.state.value.notice)
        assertFalse(repository.state.value.busy, "un geste qui echoue doit relacher l'ecran")
    }

    @Test
    fun `un refus du serveur est affiche tel quel`() = runTest {
        val source = FakeQuizSource().apply {
            failSetNotifications = IllegalStateException("Fuseau inconnu")
        }
        val repository = quiz(source, FakeOwners(moi))
        settle(repository)

        assertFalse(repository.setNotifications(enabled = true, timezone = "Europe/Paris"))
        assertEquals("Fuseau inconnu", repository.state.value.notice)
    }

    @Test
    fun `le fuseau par defaut est celui de l'appareil`() = runTest {
        val source = FakeQuizSource()
        val repository = quiz(source, FakeOwners(moi))
        settle(repository)

        assertTrue(repository.setNotifications(enabled = true))
        assertEquals(true to ZoneId.systemDefault().id, source.sentNotifications.single())
    }

    @Test
    fun `un second geste pendant le premier est ignore`() = runTest {
        val source = FakeQuizSource()
        val repository = quiz(source, FakeOwners(moi))
        settle(repository)

        val porte = CompletableDeferred<Unit>()
        source.gate = porte

        val premier = launch { repository.setNotifications(enabled = true, timezone = "Europe/Paris") }
        runCurrent()
        assertTrue(repository.state.value.busy, "le premier geste doit tenir l'ecran")

        assertFalse(
            repository.setNotifications(enabled = true, timezone = "Europe/Paris"),
            "le second geste doit etre ignore tant que le premier n'est pas fini",
        )

        porte.complete(Unit)
        premier.join()
        assertFalse(repository.state.value.busy)
        assertEquals(1, source.sentNotifications.size, "un seul appel doit etre parti")
    }

    // ------------------------------------------------------------------
    // L'attente du harnais
    // ------------------------------------------------------------------

    @Test
    fun `l'attente ne rend pas la main avant la fusion, et le depot se dit en vol`() = runTest {
        // Ce test est ne d'un echec d'integration continue, et il tient les deux choses qui le
        // rendent impossible.
        //
        // La version precedente de [settle] guettait une **accalmie de l'etat** — trois lectures
        // stables. Elle passait sur une machine rapide et tombait en CI, parce que l'etat ne bouge
        // plus pendant l'aller-retour reseau : l'instantane du **disque** est deja publie, et
        // c'est delibere — un appareil hors connexion doit afficher le quiz quand meme. L'attente
        // rendait donc la main avant la fusion, et le test lisait `[jour]` au lieu de
        // `[jour, 2026-10-05]`.
        //
        // La porte rend le cas **deterministe** : tant qu'elle n'est pas franchie, le
        // rafraichissement est en vol et l'etat publie est stable — les deux conditions du bug,
        // reunies sans dependre d'une horloge.
        val porte = CompletableDeferred<Unit>()
        val source = FakeQuizSource().apply {
            gate = porte
            snapshotValue = QuizSnapshot(
                day = jour,
                responses = listOf(
                    DailyResponse(
                        "q0",
                        "2026-10-05",
                        "b",
                        "2026-10-05T09:00:00Z",
                        question("q0", "2026-10-05"),
                    ),
                ),
            )
        }
        nouveauCache().accountFor(moi).update {
            it.copy(
                responses = listOf(
                    DailyResponse("q1", jour, "a", "2026-10-06T09:00:00Z", question(), pending = true),
                ),
            )
        }
        val repository = quiz(source, FakeOwners(moi))

        // On sonde le **depot** pour savoir que le serveur a ete appele : la lecture du disque
        // passe par un vrai fil, donc `runCurrent()` seul ne suffit pas a faire avancer le
        // rafraichissement jusqu'a l'appel.
        val limite = System.nanoTime() + DELAI_DE_GARDE_NANOS
        while (source.snapshotCalls == 0 && System.nanoTime() < limite) {
            runCurrent()
            Thread.sleep(2)
        }
        assertEquals(1, source.snapshotCalls, "le serveur doit avoir ete appele")
        assertTrue(
            repository.enTravail,
            "le depot doit se declarer en vol tant que le serveur n'a pas repondu",
        )
        assertEquals(
            listOf(jour),
            repository.state.value.snapshot?.responses.orEmpty().map { it.day },
            "l'instantane du disque est publie avant la fusion, et c'est delibere",
        )

        porte.complete(Unit)
        settle(repository)

        assertFalse(repository.enTravail, "le rafraichissement est fini : plus rien en vol")
        assertEquals(
            listOf(jour, "2026-10-05"),
            repository.state.value.snapshot?.responses.orEmpty().map { it.day },
            "l'attente doit avoir laisse la fusion aboutir",
        )
    }

    private companion object {
        /** Delai de garde de [settle] : au-dela, c'est le harnais qu'il faut regarder. */
        const val DELAI_DE_GARDE_NANOS = 2_000_000_000L
    }
}
