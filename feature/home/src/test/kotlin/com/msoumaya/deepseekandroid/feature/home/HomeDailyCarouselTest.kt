package com.msoumaya.deepseekandroid.feature.home

import com.msoumaya.deepseekandroid.core.data.repository.DailyContentsState
import com.msoumaya.deepseekandroid.core.domain.DailyContentsText
import com.msoumaya.deepseekandroid.core.domain.Program
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.QuranDataLoader
import com.msoumaya.deepseekandroid.core.model.DailyContent
import com.msoumaya.deepseekandroid.core.model.DailyContentType
import com.msoumaya.deepseekandroid.core.model.DailyContentsSnapshot
import org.junit.BeforeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// ---------------------------------------------------------------------------
// Le carrousel des contenus du jour
// ---------------------------------------------------------------------------
// Le calcul est une fonction pure d'un `DailyContentsState`. Il est donc éprouvé ici sans
// coroutine, sans réseau, sans disque et sans appareil.
//
// ## Les quatre règles qui ne se voient pas quand elles sont fausses
//
//  - **l'ordre, et le premier de chaque type** : le carrousel affiche au plus un rappel **puis**
//    au plus une invocation. Le serveur ne promet pas cet ordre — `values('reminder'),
//    ('invocation')` est une écriture, pas un contrat —, et rien ne garantit non plus qu'un type
//    n'apparaisse qu'une fois. Deux vignettes pour un seul titre feraient défiler deux fois le
//    même en-tête ;
//  - **le type absent ne laisse pas de trou** : une base qui ne publie qu'un rappel donne **une**
//    vignette, et non deux dont une vide ;
//  - **les trois phrases de l'état vide** disent trois choses différentes — « je n'ai pas encore
//    lu », « je n'ai pas pu lire » et « il n'y a rien ». Les confondre ferait dire « aucun contenu »
//    après une lecture ratée : une affirmation fausse sur ce que la base publie ;
//  - **la ligne de provenance** : `source`, ou `source · référence`. Une référence absente qui
//    laisserait un séparateur orphelin — « Sahih · » — se lirait comme une donnée manquante.
//
// ## Les états sont nommés, jamais fabriqués à la main
//
// Les trois états vides sont écrits par des fabriques ([enAttente], [lu], [rate]) qui portent les
// mêmes combinaisons de drapeaux que celles que le dépôt **publie** : `loading` sans document,
// puis `failure` **ou** document vide avec `loading` retiré. Construire ces états au hasard
// produirait des combinaisons que le dépôt n'écrit jamais — et un test vert sur une combinaison
// impossible ne prouve rien.
//
// ## Ce que ce fichier ne mesure pas
//
// La mise en page — 88 × 98 pour l'illustration, 6 px pour une pastille, 7 px d'écart, la fusion
// pour le lecteur d'écran — n'est atteignable par aucun test sans hôte Compose. Elle est relevée
// à la source et commentée dans `TodayContents.kt`.
// ---------------------------------------------------------------------------

class HomeDailyCarouselTest {

    private val today = "2026-03-10"

    private fun content(
        id: String,
        type: DailyContentType,
        source: String = "Sahih al-Bukhari",
        reference: String? = null,
        arabic: String? = null,
        phonetic: String? = null,
    ) = DailyContent(
        id = id,
        type = type,
        arabicText = arabic,
        phoneticText = phonetic,
        frenchText = "Un texte français.",
        source = source,
        reference = reference,
        categoryId = "cat-1",
    )

    private fun snapshot(vararg entries: DailyContent) =
        DailyContentsSnapshot(day = today, entries = entries.toList())

    /** L'état publié **avant** la première lecture du disque : rien n'est encore arrivé. */
    private val enAttente = DailyContentsState()

    /** L'état publié après une lecture **réussie**, qui a trouvé ce qu'elle a trouvé. */
    private fun lu(vararg entries: DailyContent) =
        DailyContentsState(loading = false, snapshot = snapshot(*entries))

    /** L'état publié après une lecture **ratée** qui n'a rien laissé à l'écran. */
    private fun rate(message: String) =
        DailyContentsState(loading = false, failure = message, snapshot = snapshot())

    private fun carrousel(state: DailyContentsState): DailyCarousel =
        HomeRenderer.dailyCarousel(state)

    private fun vignettes(state: DailyContentsState): List<DailySlide> =
        assertNotNull(
            carrousel(state) as? DailyCarousel.Slides,
            "Le carrousel doit porter des vignettes.",
        ).slides

    private fun vide(state: DailyContentsState): DailyCarousel.Empty =
        assertNotNull(
            carrousel(state) as? DailyCarousel.Empty,
            "Le carrousel doit être vide.",
        )

    // -----------------------------------------------------------------------
    // Les vignettes : ordre, unicité, trous
    // -----------------------------------------------------------------------

    @Test
    fun `l'ordre est rappel puis invocation meme si la base rend l'inverse`() {
        // Le serveur rend ses deux lignes dans l'ordre de son `values(…)`, et rien ne lui interdit
        // de changer. C'est donc la lecture qui fixe l'ordre — et l'inverser mettrait l'invocation
        // en tête de l'accueil, sous un en-tête qui la nommerait bien : rien ne lèverait.
        val slides = vignettes(
            lu(
                content("i1", DailyContentType.INVOCATION),
                content("r1", DailyContentType.REMINDER),
            ),
        )

        assertEquals(listOf("r1", "i1"), slides.map { it.id })
        assertEquals(
            listOf(DailyContentsText.HOME_REMINDER, DailyContentsText.HOME_INVOCATION),
            slides.map { it.title },
        )
    }

    @Test
    fun `le premier de chaque type est garde et les suivants sont ecartes`() {
        // Le serveur rend au plus une ligne par type, mais rien côté client ne le garantit. Garder
        // les deux rappels ferait défiler deux fois « Rappel du jour », et le second remplacerait
        // l'invocation — qui n'apparaîtrait plus du tout.
        val slides = vignettes(
            lu(
                content("r1", DailyContentType.REMINDER),
                content("r2", DailyContentType.REMINDER),
                content("i1", DailyContentType.INVOCATION),
                content("i2", DailyContentType.INVOCATION),
            ),
        )

        assertEquals(listOf("r1", "i1"), slides.map { it.id })
    }

    @Test
    fun `un rappel seul donne une seule vignette`() {
        val slides = vignettes(lu(content("r1", DailyContentType.REMINDER)))

        assertEquals(1, slides.size, "Un type absent ne doit pas laisser de vignette vide.")
        assertEquals(DailyContentsText.HOME_REMINDER, slides.single().title)
    }

    @Test
    fun `une invocation seule donne une seule vignette`() {
        val slides = vignettes(lu(content("i1", DailyContentType.INVOCATION)))

        assertEquals(1, slides.size, "Un type absent ne doit pas laisser de vignette vide.")
        assertEquals(DailyContentsText.HOME_INVOCATION, slides.single().title)
    }

    @Test
    fun `les textes du contenu traversent la vignette`() {
        // Les trois champs sont recopiés un par un, et deux d'entre eux sont nullables : les
        // intervertir donnerait une vignette qui affiche la phonétique à la place de l'arabe, sans
        // qu'aucun contrôle ne s'en aperçoive.
        val slide = vignettes(
            lu(
                content(
                    "i1",
                    DailyContentType.INVOCATION,
                    arabic = "بسم الله",
                    phonetic = "Bismillah",
                ),
            ),
        ).single()

        assertEquals("بسم الله", slide.arabic)
        assertEquals("Bismillah", slide.phonetic)
        assertEquals("Un texte français.", slide.french)
    }

    @Test
    fun `un contenu sans arabe ni phonetique garde ses champs nuls`() {
        // Un rappel n'est pas tenu d'avoir un arabe — la migration ne l'exige que pour une
        // invocation. Rendre une chaîne vide au lieu de `null` ferait afficher un bloc arabe vide,
        // et le repli de la mise en page ne s'appliquerait plus.
        val slide = vignettes(lu(content("r1", DailyContentType.REMINDER))).single()

        assertNull(slide.arabic)
        assertNull(slide.phonetic)
    }

    // -----------------------------------------------------------------------
    // La ligne de provenance
    // -----------------------------------------------------------------------

    @Test
    fun `la ligne de provenance joint la reference quand elle existe`() {
        val slide = vignettes(
            lu(
                content(
                    "r1",
                    DailyContentType.REMINDER,
                    source = "Sahih al-Bukhari",
                    reference = "n° 1",
                ),
            ),
        ).single()

        assertEquals("Sahih al-Bukhari · n° 1", slide.source)
    }

    @Test
    fun `la ligne de provenance ne laisse pas de separateur orphelin`() {
        // Les deux façons dont une référence peut manquer : absente, ou présente et vide. La
        // seconde est celle qu'un `?:` seul laisserait passer — `null` n'est pas `""`.
        val absente = vignettes(
            lu(content("r1", DailyContentType.REMINDER, source = "Sahih al-Bukhari")),
        ).single()
        val vide = vignettes(
            lu(content("r2", DailyContentType.REMINDER, source = "Sahih al-Bukhari", reference = "")),
        ).single()

        assertEquals("Sahih al-Bukhari", absente.source)
        assertEquals("Sahih al-Bukhari", vide.source)
    }

    // -----------------------------------------------------------------------
    // Le libellé du lecteur d'écran
    // -----------------------------------------------------------------------

    @Test
    fun `le libelle du lecteur d'ecran nomme le type de la vignette`() {
        // L'original écrit `Ouvrir ${type === 'reminder' ? 'le rappel' : 'l’invocation'} du jour`.
        // Les deux libellés sont les seuls mots que le lecteur d'écran prononce — le contenu de la
        // carte est fusionné en une phrase —, donc les confondre ferait annoncer une invocation
        // pour un rappel.
        val rappel = vignettes(lu(content("r1", DailyContentType.REMINDER))).single()
        val invocation = vignettes(lu(content("i1", DailyContentType.INVOCATION))).single()

        assertEquals(DailyContentsText.OPEN_REMINDER, rappel.label)
        assertEquals(DailyContentsText.OPEN_INVOCATION, invocation.label)
    }

    // -----------------------------------------------------------------------
    // Les trois états vides
    // -----------------------------------------------------------------------

    @Test
    fun `avant la premiere lecture le carrousel annonce le chargement`() {
        val empty = vide(enAttente)

        assertEquals(DailyContentsText.HOME_LOADING, empty.message)
        assertEquals(DailyContentsText.HOME_REMINDER, empty.title)
    }

    @Test
    fun `une lecture ratee qui ne laisse rien affiche la phrase hors connexion`() {
        val empty = vide(rate("boum"))

        assertEquals(DailyContentsText.HOME_OFFLINE, empty.message)
    }

    @Test
    fun `une lecture reussie sans contenu affiche l'absence de contenu`() {
        val empty = vide(lu())

        assertEquals(DailyContentsText.HOME_EMPTY, empty.message)
    }

    @Test
    fun `l'echec et l'absence de contenu ne disent pas la meme chose`() {
        // C'est le partage que tout le fichier protège : après une lecture **ratée**, l'écran ne
        // sait pas s'il n'y a pas de contenu ou s'il n'a pas pu le lire. Afficher « Aucun contenu
        // disponible aujourd'hui » serait alors une affirmation fausse — sur un contenu religieux,
        // et au pire moment.
        assertEquals(
            DailyContentsText.HOME_EMPTY,
            vide(lu()).message,
            "Une lecture réussie et vide est une information.",
        )
        assertEquals(
            DailyContentsText.HOME_OFFLINE,
            vide(rate("boum")).message,
            "Une lecture ratée est un aveu, et non une information.",
        )
    }

    @Test
    fun `un contenu publie reste affiche meme si la lecture reseau a echoue`() {
        // Le dépôt publie l'avis, et non la panne, dès qu'il reste quelque chose à lire : le
        // carrousel ne doit donc **pas** se vider parce que la relecture a échoué. C'est le cas
        // d'un appareil qui affiche le document du disque et dont le serveur ne répond plus.
        val slides = vignettes(
            DailyContentsState(
                loading = false,
                failure = "boum",
                snapshot = snapshot(content("r1", DailyContentType.REMINDER)),
            ),
        )

        assertEquals(listOf("r1"), slides.map { it.id })
    }

    // -----------------------------------------------------------------------
    // Le branchement dans l'accueil
    // -----------------------------------------------------------------------

    @Test
    fun `l'accueil calcule porte toujours son carrousel`() {
        // `render` a une valeur par défaut pour `daily`, donc oublier de le brancher compilerait
        // et l'accueil afficherait « Chargement » pour toujours — un carrousel qui ne se remplit
        // jamais, sans qu'aucune erreur ne le dise.
        val ui = HomeRenderer.render(
            Program.defaultState(),
            today,
            null,
            lu(content("r1", DailyContentType.REMINDER)),
        )

        val slides = assertNotNull(ui.daily as? DailyCarousel.Slides, "Le carrousel doit arriver.").slides
        assertEquals(listOf("r1"), slides.map { it.id })
    }

    companion object {

        /**
         * Charge le référentiel coranique une fois pour toute la classe.
         *
         * Le dernier test passe par `HomeRenderer.render`, qui lit `Quran.verseAt` et
         * `Quran.surahs` : sans le référentiel, il lèverait avant d'atteindre le carrousel. Les
         * fichiers JSON sont des ressources du module `core:domain`, donc présentes sur le chemin
         * de classes des tests exactement comme à l'exécution.
         */
        @BeforeClass
        @JvmStatic
        fun chargerLeReferentiel() {
            Quran.initialize(QuranDataLoader.loadFromClasspath())
        }
    }
}
