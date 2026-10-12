package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.local.DailyContentCacheStore
import com.msoumaya.deepseekandroid.core.data.remote.DailyContentSource
import com.msoumaya.deepseekandroid.core.domain.DailyContentsText
import com.msoumaya.deepseekandroid.core.model.ContentCategory
import com.msoumaya.deepseekandroid.core.model.DailyContent
import com.msoumaya.deepseekandroid.core.model.DailyContentType
import com.msoumaya.deepseekandroid.core.model.DailyContentsSnapshot
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Éprouve le dépôt « contenus du jour » : ce qu'il publie, dans quel ordre, et ce qu'un échec fait
 * à ce qui est déjà à l'écran.
 *
 * ## Ce qui est mesuré ici, et pourquoi c'est ici
 *
 * Le dépôt ne contient que des règles d'**ordonnancement et de survie**, et aucune ne lève
 * d'exception quand elle est fausse — c'est précisément pourquoi elles sont figées :
 *
 *  - publier le réseau avant le disque afficherait un accueil vide sans connexion, alors que le
 *    contenu du jour est sur l'appareil ;
 *  - publier un document **d'une autre date** afficherait le rappel de la veille sous le titre
 *    « Rappel du jour » : une affirmation fausse, sur un contenu religieux, et que rien ne lève ;
 *  - remplacer par une panne un document déjà lu fait disparaître ce qu'on avait ;
 *  - publier un document vide après une lecture **ratée** ferait dire à l'écran « Aucun contenu
 *    disponible aujourd'hui », qui est faux — et personne n'ira vérifier ;
 *  - lire les catégories **avant** le contenu du jour ferait dépendre l'affichage du jour d'un
 *    échec des pastilles de filtre, qui ne servent qu'à l'écran de liste ;
 *  - laisser un message d'échec nul produirait un échec **muet**.
 *
 * ## Pourquoi [settle] sonde le dépôt, et non l'état
 *
 * Même leçon que `QuizRepositoryTest` et `RecitationRepositoryTest`, et elle vient d'une chute en
 * intégration continue : le dépôt publie le document du **disque** avant d'appeler le serveur — ce
 * qui est délibéré, un appareil sans connexion doit afficher le contenu du jour quand même —, donc
 * pendant l'aller-retour **rien ne bouge plus** dans l'état. Une attente fondée sur « l'état ne
 * bouge plus » rendait donc la main **au milieu** du travail, et le test lisait le document local
 * seul. On attend ici que le dépôt n'ait plus rien **en vol**
 * ([DailyContentRepository.enTravail]), avec un délai de garde qui échoue en le disant.
 */
class DailyContentRepositoryTest {

    /** Le jour courant, figé : les tests ne doivent pas dépendre de l'horloge. */
    private val jour = "2026-10-11"

    /** La veille, pour éprouver qu'un document périmé n'est pas publié. */
    private val veille = "2026-10-10"

    private lateinit var root: File

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("daily-content-repository-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    // ------------------------------------------------------------------ fabriques

    private fun contenu(
        id: String = "c1",
        type: DailyContentType = DailyContentType.REMINDER,
        titre: String? = "Un titre",
    ): DailyContent = DailyContent(
        id = id,
        type = type,
        title = titre,
        arabicText = "نص",
        phoneticText = "nass",
        frenchText = "Un texte.",
        source = "Une source",
        reference = "1:1",
        categoryId = "cat-1",
    )

    private fun categorie(id: String = "cat-1") = ContentCategory(
        id = id,
        name = "Prière",
        icon = "☾",
        type = DailyContentType.INVOCATION,
    )

    private fun nouveauCache(): DailyContentCacheStore = DailyContentCacheStore(root) { jour }

    private fun TestScope.nouveauDepot(
        source: DailyContentSource?,
        cache: DailyContentCacheStore = nouveauCache(),
    ): DailyContentRepository = DailyContentRepository(
        source = source,
        cache = cache,
        scope = backgroundScope,
        today = { jour },
    )

    /**
     * Laisse le travail de fond aboutir, en attendant que le dépôt n'ait plus rien **en vol**.
     *
     * Deux tours sans rien en vol, et non un seul : un tour calme peut être un tour **entre** deux
     * travaux. Le sommeil n'est pas l'attente, il n'en est que le pas d'échantillonnage — et il est
     * nécessaire parce que les magasins lisent et écrivent par `Dispatchers.IO`, donc par un vrai
     * fil que l'horloge virtuelle n'attend pas.
     */
    private fun TestScope.settle(repository: DailyContentRepository, millis: Long = 1_000) {
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

    // ------------------------------------------------------------------ le disque d'abord

    @Test
    fun `le document du disque est publie avant l'appel reseau`() = runTest {
        // C'est la règle qui rend le repli hors ligne honnête : ce qui s'affiche a été lu un jour,
        // et le document porte **sa** date. Publier le réseau d'abord afficherait un accueil vide
        // sur un appareil sans connexion, alors que tout est sur l'appareil.
        val cache = nouveauCache()
        cache.update {
            DailyContentsSnapshot(day = jour, entries = listOf(contenu("cache-1")))
        }

        val source = FakeDailyContentSource()
        source.dayEntries = listOf(contenu("serveur-1"))

        var vu: DailyContentsSnapshot? = null
        var depot: DailyContentRepository? = null
        source.probe = { vu = depot?.state?.value?.snapshot }

        depot = nouveauDepot(source, cache)
        settle(depot)

        assertEquals(
            listOf("cache-1"),
            vu?.entries?.map { it.id },
            "Le document du disque doit etre publie avant le premier appel reseau : sinon un " +
                "appareil sans connexion n'affiche rien alors que le contenu est sur l'appareil.",
        )
    }

    @Test
    fun `le depot s'amorce lui-meme`() = runTest {
        // Sans cet amorcage, l'accueil devrait demander la lecture a l'ouverture, et le contenu
        // n'apparaitrait qu'apres un aller-retour — alors que le document du disque est pret.
        val source = FakeDailyContentSource()

        val depot = nouveauDepot(source)
        settle(depot)

        assertEquals(
            listOf("contenu du jour", "categories"),
            source.calls,
            "Le depot doit lire le contenu du jour puis les categories, de lui-meme, sans qu'un " +
                "ecran le lui demande.",
        )
    }

    // ------------------------------------------------------------------ le document périmé

    @Test
    fun `un document d'une autre date n'est pas publie`() = runTest {
        // Le serveur choisit une entree **par type et par date** : un document lu la veille decrit
        // un autre jour, et rien dans sa forme ne l'annonce sinon son champ `day`.
        val cache = nouveauCache()
        cache.update {
            DailyContentsSnapshot(day = veille, entries = listOf(contenu("hier-1")))
        }

        val source = FakeDailyContentSource()
        source.failDay = RuntimeException("reseau indisponible")

        val depot = nouveauDepot(source, cache)
        settle(depot)

        val snapshot = assertNotNull(depot.state.value.snapshot, "Le disque doit etre publie.")
        assertEquals(
            jour,
            snapshot.day,
            "Le document publie doit porter le jour courant, et non celui qu'il remplace : sans " +
                "cela l'accueil annoncerait le rappel d'hier comme « Rappel du jour ».",
        )
        assertTrue(
            snapshot.entries.isEmpty(),
            "Les entrees d'hier ne doivent pas etre publiees : elles decrivent un autre jour.",
        )
    }

    @Test
    fun `un document du jour est publie tel quel quand le reseau echoue`() = runTest {
        // Un document non vide n'est pas devenu faux parce que la relecture a echoue : le retirer
        // ferait disparaitre de l'ecran ce que la personne etait en train de lire.
        val cache = nouveauCache()
        cache.update {
            DailyContentsSnapshot(day = jour, entries = listOf(contenu("cache-1")))
        }

        val source = FakeDailyContentSource()
        source.failDay = RuntimeException("reseau indisponible")

        val depot = nouveauDepot(source, cache)
        settle(depot)

        val etat = depot.state.value
        assertEquals(listOf("cache-1"), etat.snapshot?.entries?.map { it.id })
        assertNull(
            etat.failure,
            "Un echec qui laisse quelque chose a lire ne doit pas publier une panne : l'ecran " +
                "prefererait le message d'erreur au contenu.",
        )
        assertEquals("reseau indisponible", etat.notice)
    }

    // ------------------------------------------------------------------ les deux états d'échec

    @Test
    fun `un echec avec un document vide publie une panne et pas un avis`() = runTest {
        // « Aucun contenu disponible aujourd'hui » est une affirmation : elle ne doit pas etre
        // faite quand la lecture a echoue, et personne n'ira verifier.
        val source = FakeDailyContentSource()
        source.failDay = RuntimeException("reseau indisponible")

        val depot = nouveauDepot(source)
        settle(depot)

        val etat = depot.state.value
        assertEquals("reseau indisponible", etat.failure)
        assertNull(etat.notice, "Une panne et un avis ne se publient pas ensemble.")
        assertTrue(etat.snapshot?.entries?.isEmpty() == true)
    }

    @Test
    fun `un echec sans texte lisible publie le repli du domaine`() = runTest {
        // `restMessage()` peut rendre `null` : une exception dont le serveur n'a rien rempli. Un
        // message nul serait un **echec muet**, et l'ecran afficherait un cadre vide.
        val source = FakeDailyContentSource()
        source.failDay = RuntimeException()

        val depot = nouveauDepot(source)
        settle(depot)

        assertEquals(DailyContentsText.GENERIC_FAILURE, depot.state.value.failure)
    }

    @Test
    fun `un echec dont le texte est vide publie le repli du domaine`() = runTest {
        // Un message fait d'espaces ne compte pas, pour la meme raison qu'au Quiz : l'ecran
        // afficherait un cadre vide la ou il y a une erreur a comprendre.
        val source = FakeDailyContentSource()
        source.failDay = RuntimeException("   ")

        val depot = nouveauDepot(source)
        settle(depot)

        assertEquals(DailyContentsText.GENERIC_FAILURE, depot.state.value.failure)
    }

    // ------------------------------------------------------------------ l'ordre des deux lectures

    @Test
    fun `le contenu du jour se lit avant les categories`() = runTest {
        // L'ordre est une regle, et non un hasard de lecture : les categories ne servent qu'aux
        // pastilles de filtre de l'ecran de liste, donc leur echec ne doit pas empecher le contenu
        // du jour d'etre relu — ni, s'il l'etait, de rester a l'ecran.
        val source = FakeDailyContentSource()

        val depot = nouveauDepot(source)
        settle(depot)

        assertEquals(listOf("contenu du jour", "categories"), source.calls)
    }

    @Test
    fun `un echec des categories ne retire pas le contenu du jour`() = runTest {
        val source = FakeDailyContentSource()
        source.dayEntries = listOf(contenu("serveur-1"))
        source.failCategories = RuntimeException("categories indisponibles")

        val depot = nouveauDepot(source)
        settle(depot)

        val etat = depot.state.value
        assertEquals(
            listOf("serveur-1"),
            etat.snapshot?.entries?.map { it.id },
            "Un echec des pastilles de filtre ne doit pas retirer de l'ecran le contenu du jour.",
        )
        assertEquals("categories indisponibles", etat.notice)
        assertNull(etat.failure)
    }

    @Test
    fun `les categories lues sont publiees`() = runTest {
        val source = FakeDailyContentSource()
        source.categoryList = listOf(categorie("cat-1"), categorie("cat-2"))

        val depot = nouveauDepot(source)
        settle(depot)

        assertEquals(listOf("cat-1", "cat-2"), depot.state.value.categories.map { it.id })
    }

    // ------------------------------------------------------------------ l'écriture

    @Test
    fun `une lecture reussie ecrit le disque et publie les entrees`() = runTest {
        val source = FakeDailyContentSource()
        source.dayEntries = listOf(
            contenu("serveur-1", DailyContentType.REMINDER),
            contenu("serveur-2", DailyContentType.INVOCATION),
        )

        val depot = nouveauDepot(source)
        settle(depot)

        assertEquals(
            listOf("serveur-1", "serveur-2"),
            depot.state.value.snapshot?.entries?.map { it.id },
        )

        // Le disque est relu **par un second magasin**, et non par celui du depot : un magasin qui
        // garde son document en memoire aurait rendu les entrees lues sans rien avoir ecrit.
        val relu = nouveauCache().current()
        assertEquals(jour, relu.day)
        assertEquals(listOf("serveur-1", "serveur-2"), relu.entries.map { it.id })
    }

    @Test
    fun `le jour demande au serveur est celui du jour courant`() = runTest {
        // Le serveur choisit l'entree du jour a partir de la date qu'on lui envoie : une date
        // calculee en UTC ferait basculer le contenu a une heure fausse pour tout fuseau en avance
        // sur Greenwich, et personne ne le verrait — le contenu resterait coherent, seulement
        // decale.
        val source = FakeDailyContentSource()

        val depot = nouveauDepot(source)
        settle(depot)

        assertEquals(listOf(jour), source.daysAsked)
    }

    // ------------------------------------------------------------------ sans projet configuré

    @Test
    fun `sans projet configure le disque est publie et rien n'est demande`() = runTest {
        // Le `null` n'est pas une panne : c'est le mode hors ligne. Il doit se lire comme « rien a
        // demander », et **jamais** comme une erreur reseau — un message d'erreur enverrait
        // chercher la connexion alors qu'il n'y a pas de projet a joindre.
        val cache = nouveauCache()
        cache.update {
            DailyContentsSnapshot(day = jour, entries = listOf(contenu("cache-1")))
        }

        val depot = nouveauDepot(source = null, cache = cache)
        settle(depot)

        val etat = depot.state.value
        assertEquals(listOf("cache-1"), etat.snapshot?.entries?.map { it.id })
        assertNull(etat.failure)
        assertNull(etat.notice)
        assertEquals(false, etat.loading)
    }

    @Test
    fun `sans projet configure la page est vide et la fiche est nulle`() = runTest {
        val depot = nouveauDepot(source = null)
        settle(depot)

        assertTrue(depot.page(DailyContentType.INVOCATION).isEmpty())
        assertNull(depot.content("c1"))
    }

    // ------------------------------------------------------------------ les deux lectures d'écran

    @Test
    fun `la page transmet le type la categorie et le decalage`() = runTest {
        val source = FakeDailyContentSource()
        source.page = listOf(contenu("p-1", DailyContentType.INVOCATION))

        val depot = nouveauDepot(source)
        settle(depot)
        source.calls.clear()

        val page = depot.page(DailyContentType.INVOCATION, categoryId = "cat-9", offset = 30)

        assertEquals(listOf("p-1"), page.map { it.id })
        // Les deux cotes sont ecrits avec **le meme** type : la doublure retient
        // `Triple<DailyContentType, String?, Int>` — la categorie y est nullable, parce qu'un appel
        // sans filtre en passe `null` —, et `Triple(DailyContentType.INVOCATION, "cat-9", 30)` en
        // rend un `Triple<DailyContentType, String, Int>`. L'inférence du parametre de
        // `assertEquals` ne remonte pas seule de l'un a l'autre.
        assertEquals(
            listOf<Triple<DailyContentType, String?, Int>>(
                Triple(DailyContentType.INVOCATION, "cat-9", 30),
            ),
            source.pagesAsked.toList(),
            "Le type, la categorie et le decalage doivent parvenir au serveur tels quels : c'est " +
                "lui qui decide de la fenetre et du filtre.",
        )
    }

    @Test
    fun `la page ne touche pas l'etat publie`() = runTest {
        // Une page est propre a un filtre et a un decalage : la publier ecraserait le contenu du
        // jour pour tout le monde alors qu'un seul ecran l'a demandee.
        val source = FakeDailyContentSource()
        source.dayEntries = listOf(contenu("serveur-1"))
        source.page = listOf(contenu("p-1", DailyContentType.INVOCATION))

        val depot = nouveauDepot(source)
        settle(depot)
        val avant = depot.state.value.snapshot

        depot.page(DailyContentType.INVOCATION)

        assertEquals(avant, depot.state.value.snapshot)
    }

    @Test
    fun `la fiche rend le contenu demande`() = runTest {
        val source = FakeDailyContentSource()
        source.single = contenu("c-42", DailyContentType.INVOCATION)

        val depot = nouveauDepot(source)
        settle(depot)

        assertEquals("c-42", depot.content("c-42")?.id)
        assertEquals(listOf("c-42"), source.idsAsked)
    }

    @Test
    fun `une page en echec leve, et l'etat publie reste intact`() = runTest {
        // C'est l'ecran de liste qui porte son propre message d'erreur : lui seul sait ce qu'il
        // avait a l'ecran. Le depot ne publie donc rien pour une page.
        val source = FakeDailyContentSource()
        source.dayEntries = listOf(contenu("serveur-1"))
        source.failPage = RuntimeException("page indisponible")

        val depot = nouveauDepot(source)
        settle(depot)
        val avant = depot.state.value

        val leve = runCatching<List<DailyContent>> { depot.page(DailyContentType.REMINDER) }

        assertTrue(leve.isFailure, "Une page en echec doit lever : l'ecran decide de son message.")
        assertEquals(avant, depot.state.value)
    }

    // ------------------------------------------------------------------ la page de favoris

    @Test
    fun `une liste d'identifiants vide ne demande rien`() = runTest {
        // La garde compte plus qu'elle n'en a l'air : `favoritePage` rend `[]` **avant** d'appeler
        // la source, donc l'onglet « Mes favoris » d'un appareil sans projet — ou d'une personne
        // qui n'a rien mis en favori — affiche son etat vide au lieu d'une erreur. Sans elle, la
        // requete partirait avec un `in.()` vide, que PostgREST refuse.
        val source = FakeDailyContentSource()
        source.favoritePage = listOf(contenu("f-1", DailyContentType.INVOCATION))

        val depot = nouveauDepot(source)
        settle(depot)

        val vide = depot.favoritePage(emptySet(), DailyContentType.INVOCATION)

        assertTrue(vide.isEmpty(), "Une liste d'identifiants vide doit rendre une liste vide.")
        assertTrue(
            source.favoritesAsked.isEmpty(),
            "La source a ete interrogee avec une liste d'identifiants vide : PostgREST refuse un " +
                "`in.()` vide, et l'onglet « Mes favoris » annoncerait une panne la ou il n'y a " +
                "rien a montrer.",
        )

        // **Le temoin.** Sans lui, le controle precedent serait vrai meme si `favoritePage`
        // n'appelait jamais la source — il serait vert pour la mauvaise raison. Le meme appel avec
        // un identifiant **doit** l'atteindre.
        depot.favoritePage(setOf("f-1"), DailyContentType.INVOCATION)
        assertEquals(
            listOf("f-1"),
            source.favoritesAsked.map { it.ids.single() },
            "Le temoin a echoue : ce banc ne mesure pas ce qu'il croit mesurer.",
        )
    }

    @Test
    fun `la page de favoris transmet les identifiants et le filtre`() = runTest {
        val source = FakeDailyContentSource()
        source.favoritePage = listOf(contenu("f-1", DailyContentType.INVOCATION))

        val depot = nouveauDepot(source)
        settle(depot)
        source.favoritesAsked.clear()

        val page = depot.favoritePage(
            ids = setOf("f-1", "f-2"),
            type = DailyContentType.INVOCATION,
            categoryId = "cat-9",
            offset = 30,
        )

        assertEquals(listOf("f-1"), page.map { it.id })

        // Les identifiants sont compares en **ensemble** : l'ordre d'un `in.()` est sans effet sur
        // la reponse, et dependre ici de l'ordre d'iteration d'un `setOf` ferait echouer ce banc
        // sur une implementation voisine mais correcte. Le reste est compare tel quel — c'est lui
        // qui decide de la fenetre et du filtre.
        val appel = source.favoritesAsked.single()
        assertEquals(setOf("f-1", "f-2"), appel.ids.toSet())
        assertEquals(DailyContentType.INVOCATION, appel.type)
        assertEquals("cat-9", appel.categoryId)
        assertEquals(30, appel.offset)
    }

    @Test
    fun `sans projet configure la page de favoris est vide`() = runTest {
        // Meme regle que pour la page publique : hors configuration, l'ecran doit pouvoir afficher
        // son etat vide plutot que d'echouer une lecture qu'il n'a pas les moyens de faire.
        val depot = nouveauDepot(source = null)
        settle(depot)

        assertTrue(
            depot.favoritePage(setOf("f-1"), DailyContentType.REMINDER).isEmpty(),
            "Sans projet configure, une page de favoris doit etre vide, et non lever.",
        )
    }

    @Test
    fun `une page de favoris en echec leve, et l'etat publie reste intact`() = runTest {
        // Meme partage que pour la page publique : l'ecran de liste porte son propre message
        // d'erreur, parce que lui seul sait ce qu'il avait a l'ecran. Le depot ne publie donc rien.
        val source = FakeDailyContentSource()
        source.dayEntries = listOf(contenu("serveur-1"))
        source.failFavoritePage = RuntimeException("favoris indisponibles")

        val depot = nouveauDepot(source)
        settle(depot)
        val avant = depot.state.value

        val leve = runCatching<List<DailyContent>> {
            depot.favoritePage(setOf("f-1"), DailyContentType.REMINDER)
        }

        assertTrue(
            leve.isFailure,
            "Une page de favoris en echec doit lever : l'ecran decide de son message.",
        )
        assertEquals(avant, depot.state.value)
    }

    // ------------------------------------------------------------------ le texte lisible

    @Test
    fun `le texte lisible d'une erreur est celui que le depot publie`() = runTest {
        // `readable` existe pour que l'ecran de liste n'invente pas sa propre conversion : deux
        // ecrans pourraient sinon montrer deux textes pour la **meme** panne — l'un le message du
        // serveur, l'autre un `toString()` d'exception.
        //
        // Le controle passe par le chemin **reel** : la meme instance d'erreur, publiee par une
        // lecture du jour en echec, est ensuite relue par `readable`. Comparer `readable` avec
        // elle-meme serait vrai quoi qu'il arrive.
        //
        // **Le cas decisif est le message BLANC, et pas le message ordinaire.** Pour une exception
        // ordinaire, `restMessage()` retombe sur `message` — donc `describe` et une lecture
        // directe de `message` rendent la **meme** chose, et un controle qui s'y limiterait serait
        // vert meme si `readable` cessait de passer par le depot. C'est le piege que
        // `SocialSourceShapeTest` decrit deja pour `restMessage`. Ce qui separe les deux chemins
        // est le **filtre du blanc**, et il ne s'observe que sur un message qui n'en porte pas.
        // La campagne de falsification a rendu ce test FAUX avant qu'il ne soit ecrit ainsi.

        // --- le message ordinaire : les deux chemins se confondent, et c'est normal ---
        val ordinaire = RuntimeException("jour indisponible")
        val sourceOrdinaire = FakeDailyContentSource()
        sourceOrdinaire.failDay = ordinaire

        val premier = nouveauDepot(sourceOrdinaire)
        settle(premier)

        assertNotNull(
            premier.state.value.failure,
            "Une lecture du jour en echec doit publier un message.",
        )
        assertEquals(
            premier.state.value.failure,
            premier.readable(ordinaire),
            "`readable` ne dit pas la meme chose que ce que le depot publie : deux ecrans " +
                "montreraient deux textes pour la meme panne.",
        )

        // --- le message blanc : le seul cas ou les deux chemins divergent ---
        val blanc = RuntimeException("   ")
        val sourceBlanche = FakeDailyContentSource()
        sourceBlanche.failDay = blanc

        val second = nouveauDepot(sourceBlanche)
        settle(second)

        assertEquals(
            DailyContentsText.GENERIC_FAILURE,
            second.state.value.failure,
            "Un message fait d'espaces doit etre remplace par le repli du domaine.",
        )
        assertEquals(
            second.state.value.failure,
            second.readable(blanc),
            "`readable` doit retomber sur le repli du domaine pour un message blanc : une lecture " +
                "directe de `message` rendrait les espaces tels quels, et l'ecran afficherait un " +
                "cadre vide la ou le depot publie le repli.",
        )
    }

    private companion object {
        /** Délai de garde de [settle], en nanosecondes. Le meme que les autres bancs du depot. */
        const val DELAI_DE_GARDE_NANOS = 5_000_000_000L
    }
}
