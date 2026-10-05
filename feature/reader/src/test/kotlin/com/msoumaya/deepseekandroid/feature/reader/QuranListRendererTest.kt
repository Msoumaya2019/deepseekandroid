package com.msoumaya.deepseekandroid.feature.reader

import com.msoumaya.deepseekandroid.core.domain.Program
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.QuranDataLoader
import com.msoumaya.deepseekandroid.core.domain.QuranText
import com.msoumaya.deepseekandroid.core.domain.StudyProgressCalculator
import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.Division
import com.msoumaya.deepseekandroid.core.model.LastRead
import com.msoumaya.deepseekandroid.core.model.Mastery
import com.msoumaya.deepseekandroid.core.model.MushafSource
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.ReaderPreferences
import org.junit.BeforeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Éprouve le calcul de l'écran « Coran » sur le **vrai corpus** — 114 sourates, 30 juz, 60 hizb,
 * 6 236 versets — et non sur une maquette de trois entrées.
 *
 * ## Pourquoi le vrai corpus, et ce que cela change
 *
 * Les nombres attendus ici ne sont pas des estimations : ils ont été lus dans les ressources
 * livrées. C'est la seule façon de voir un défaut de bornes ou de tri — une maquette de trois
 * sourates laisserait passer les deux, et une recherche qui ne rend rien ressemble à une
 * recherche juste.
 *
 * ## Ce que ces contrôles séparent
 *
 * Trois choses distinctes s'éprouvent ici, et les confondre rendrait l'une des trois fausse :
 *
 *  - **les lignes** : combien, dans quel ordre, avec quel texte ;
 *  - **les deux règles de sélection** : la recherche, qui plie la casse et porte sur des champs
 *    différents selon la vue, et le filtre, qui ne s'applique **qu'aux sourates** ;
 *  - **le découpage de pages**, qui suit la source affichée.
 *
 * ## Le contrôle qui ne pouvait pas se prendre sur les données livrées
 *
 * **Mesuré : aucune** des 30 juz ni des 60 hizb ne change de page entre les trois découpages du
 * projet — moushaf de Médine, composition typographique, et « Coran 1441 » — alors que 56 versets
 * du Mushaf, eux, changent de page. Passer la source au calcul ne peut donc rien changer sur ces
 * données, et un contrôle écrit sur elles passerait même si la source était ignorée. La règle
 * s'éprouve donc sur une **division fabriquée** dont une borne tombe sur le verset 746 — page 121
 * côté moushaf, page 120 côté composition —, et l'inertie des données livrées est mesurée à part.
 * Un contrôle qui ne passe jamais ne prouve rien ; celui-ci passe.
 */
class QuranListRendererTest {

    companion object {
        /**
         * Charge le référentiel une fois pour toute la classe.
         *
         * Le calcul ne peut pas être appelé avant : `Quran.surahs` lève sur un référentiel vide.
         * Le test le charge donc lui-même, exactement comme l'application le fait au démarrage.
         */
        @BeforeClass
        @JvmStatic
        fun chargerLeReferentiel() {
            Quran.initialize(QuranDataLoader.loadFromClasspath())
        }
    }

    // --- outillage ---------------------------------------------------------

    private fun etat(mushaf: MushafSource = MushafSource.MEDINA): AppState =
        Program.defaultState().copy(reader = ReaderPreferences(mushaf = mushaf))

    private fun connu(vararg ids: Int): AppState = etat().copy(
        knowledge = ids.associate { it.toString() to Mastery.PERFECT },
    )

    private fun rendre(
        state: AppState,
        view: QuranText.View = QuranText.View.LIST,
        query: String = "",
        filter: QuranText.Filter = QuranText.Filter.ALL,
    ): QuranUiState = QuranListRenderer.render(state, view, query, filter)

    // --- les lignes de la liste des sourates -------------------------------

    @Test
    fun `la liste porte les cent quatorze sourates, dans l'ordre du Mushaf`() {
        val lignes = rendre(etat()).rows

        assertEquals(114, lignes.size)
        assertEquals(1, lignes.first().number)
        assertEquals("Al Fâtiha", lignes.first().name)
        assertEquals(114, lignes.last().number)
        assertEquals("An Nâs", lignes.last().name)
        // Les identifiants sont ceux que la liste utilise comme clés : deux lignes qui les
        // partageraient feraient disparaître l'une des deux au rendu, sans erreur.
        assertEquals(114, lignes.map { it.key }.toSet().size)
    }

    @Test
    fun `une sourate porte son sens, son nom arabe et sa pastille`() {
        val sourate = rendre(etat()).rows[1]

        assertEquals("Al Baqarah", sourate.name)
        assertEquals("La vache", sourate.meaning)
        assertEquals("البَقَرَة", sourate.arabic)
        assertEquals(Range(8, 293), sourate.range)
        assertEquals("Ouvrir Al Baqarah", sourate.openLabel)
        assertEquals("Médinoise", sourate.badge?.place)
        assertEquals(false, sourate.badge?.isMeccan)
        assertEquals("286 versets", sourate.badge?.count)
    }

    @Test
    fun `une sourate mecquoise porte la pastille mecquoise`() {
        val sourate = rendre(etat()).rows.first()

        assertEquals("Mecquoise", sourate.badge?.place)
        assertEquals(true, sourate.badge?.isMeccan)
        assertEquals("7 versets", sourate.badge?.count)
    }

    @Test
    fun `les divisions n'ont ni pastille ni nom arabe`() {
        // Une division n'a pas de lieu de révélation ni de nom arabe : lui en donner un ferait
        // réserver une colonne à un texte qui n'existe pas, et afficherait une pastille vide.
        for (view in listOf(QuranText.View.JUZ, QuranText.View.HIZB)) {
            for (ligne in rendre(etat(), view = view).rows) {
                assertNull(ligne.badge, "${ligne.name} : pastille")
                assertNull(ligne.arabic, "${ligne.name} : nom arabe")
            }
        }
    }

    // --- les deux vues de division -----------------------------------------

    @Test
    fun `la vue des juz porte trente lignes et celle des hizb en porte soixante`() {
        val juz = rendre(etat(), view = QuranText.View.JUZ).rows
        assertEquals(30, juz.size)
        assertEquals("Juz’ 1", juz.first().name)
        assertEquals("Juz’ 30", juz.last().name)

        val hizb = rendre(etat(), view = QuranText.View.HIZB).rows
        assertEquals(60, hizb.size)
        assertEquals("Hizb 1", hizb.first().name)
        assertEquals("Hizb 60", hizb.last().name)
    }

    @Test
    fun `une division annonce ses pages dans le decoupage du moushaf`() {
        val juz = rendre(etat(), view = QuranText.View.JUZ).rows

        // Mesuré sur les données livrées : le juz 1 va du verset 1 au verset 148, soit les pages
        // 1 à 21 ; le juz 30 va du verset 5673 au verset 6236, soit les pages 582 à 604.
        assertEquals("Pages 1 – 21", juz.first().meaning)
        assertEquals("Pages 582 – 604", juz.last().meaning)
        assertEquals("Ouvrir Juz’ 1", juz.first().openLabel)
        assertEquals(Range(1, 148), juz.first().range)
    }

    // --- la recherche ------------------------------------------------------

    @Test
    fun `une recherche vide ne retire rien`() {
        assertEquals(114, rendre(etat(), query = "").rows.size)
    }

    @Test
    fun `la recherche plie la casse`() {
        // Le client d'origine replie la casse des deux côtés (`toLowerCase()`), et le portage
        // aussi : sans cela, une recherche tapée en majuscules ne trouverait rien.
        val minuscules = rendre(etat(), query = "vache").rows
        val majuscules = rendre(etat(), query = "VACHE").rows

        assertEquals(1, minuscules.size)
        assertEquals(2, minuscules.first().number)
        assertEquals(minuscules, majuscules)
    }

    @Test
    fun `une sourate se cherche par son nom, son sens, son nom arabe et son numero`() {
        // Les quatre champs du client d'origine, éprouvés un par un : si l'un d'eux disparaissait
        // de la chaîne cherchée, seul son contrôle tomberait — un contrôle global ne le dirait pas.
        assertEquals(listOf(2), rendre(etat(), query = "Al Baqarah").rows.map { it.number })
        assertEquals(listOf(2), rendre(etat(), query = "vache").rows.map { it.number })
        assertEquals(listOf(2), rendre(etat(), query = "البَقَرَة").rows.map { it.number })
        // Le numéro est cherché comme du texte : « 114 » ne trouve que la 114ᵉ sourate, et non
        // celles dont le numéro *contiendrait* ces chiffres — il n'y en a pas.
        assertEquals(listOf(114), rendre(etat(), query = "114").rows.map { it.number })
    }

    @Test
    fun `une recherche sans resultat rend une liste vide, et non une erreur`() {
        assertTrue(rendre(etat(), query = "zzzz").rows.isEmpty())
    }

    @Test
    fun `une division se cherche aussi par la sourate ou elle commence`() {
        // Le client d'origine cherche, pour une division, son numéro, le mot de sa vue et le nom
        // de la sourate **où elle commence**. C'est ce qui permet de retrouver « Juz’ 30 » en
        // tapant « An Naba' » : le juz 30 commence au verset 5673, qui est 78:1.
        val juz = rendre(etat(), view = QuranText.View.JUZ, query = "An Naba'").rows
        assertEquals(listOf(30), juz.map { it.number })

        // Le hizb 59 commence au même verset ; le hizb 60, lui, commence dans Al A'lâ.
        val hizb = rendre(etat(), view = QuranText.View.HIZB, query = "An Naba'").rows
        assertEquals(listOf(59), hizb.map { it.number })
        assertEquals(
            listOf(60),
            rendre(etat(), view = QuranText.View.HIZB, query = "A'lâ").rows.map { it.number },
        )
    }

    // --- le filtre ---------------------------------------------------------

    @Test
    fun `le filtre retient le lieu demande, et ses deux parts font le tout`() {
        // Mesuré sur les données livrées : 86 sourates mecquoises et 28 médinoises, soit les 114.
        val toutes = rendre(etat(), filter = QuranText.Filter.ALL).rows
        val mecquoises = rendre(etat(), filter = QuranText.Filter.MECCAN).rows
        val medinoises = rendre(etat(), filter = QuranText.Filter.MEDINAN).rows

        assertEquals(114, toutes.size)
        assertEquals(86, mecquoises.size)
        assertEquals(28, medinoises.size)
        assertEquals(toutes.size, mecquoises.size + medinoises.size)
        assertTrue(mecquoises.all { it.badge?.isMeccan == true })
        assertTrue(medinoises.all { it.badge?.isMeccan != true })
    }

    @Test
    fun `le filtre ne s'applique pas aux divisions`() {
        // Une division n'a pas de lieu de révélation : le client d'origine ne teste le filtre que
        // dans la branche des sourates, et ne rend même pas le bouton ailleurs. Un filtre appliqué
        // aux divisions viderait la liste, ce qui se lirait comme « aucun résultat ».
        for (view in listOf(QuranText.View.JUZ, QuranText.View.HIZB)) {
            assertEquals(
                rendre(etat(), view = view, filter = QuranText.Filter.ALL).rows,
                rendre(etat(), view = view, filter = QuranText.Filter.MECCAN).rows,
                "$view",
            )
        }
    }

    // --- ce que l'écran reçoit, et qui change avec la vue ------------------

    @Test
    fun `le bouton de filtre et les invites suivent la vue`() {
        val liste = rendre(etat())
        assertEquals(true, liste.showFilter)
        assertEquals("Rechercher une sourate", liste.searchPlaceholder)
        assertEquals("Mushaf de Médine • Hafs ‘an ‘Âsim • 604 pages", liste.subtitle)
        assertEquals(false, liste.loading)

        val juz = rendre(etat(), view = QuranText.View.JUZ)
        assertEquals(false, juz.showFilter)
        assertEquals("Rechercher un Juz’", juz.searchPlaceholder)
        assertEquals("Liste des Juz’ • 30 parties", juz.subtitle)

        val hizb = rendre(etat(), view = QuranText.View.HIZB)
        assertEquals(false, hizb.showFilter)
        assertEquals("Rechercher un Hizb", hizb.searchPlaceholder)
        assertEquals("Liste des Hizb • 60 parties", hizb.subtitle)
    }

    // --- la carte des connaissances ----------------------------------------

    @Test
    fun `sans verset connu, la carte le dit`() {
        assertEquals("Aucun verset validé", rendre(etat()).knownValue)
    }

    @Test
    fun `la carte des connaissances nomme le plus grand verset connu`() {
        // Mesuré : le verset 8 est 2:1, et le verset 6236 est 114:6.
        assertEquals("Al Baqarah • verset 1", rendre(connu(8)).knownValue)
        assertEquals("An Nâs • verset 6", rendre(connu(6236)).knownValue)
    }

    @Test
    fun `c'est le plus grand identifiant qui gagne, et non le dernier ajoute`() {
        // La carte dit une **position dans le corpus**, pas une date : `Math.max(...known)` dans
        // le client d'origine. Un état qui porterait les deux dans l'autre ordre doit rendre le
        // même texte — sinon la carte suivrait l'ordre d'insertion de la table.
        assertEquals("An Nâs • verset 6", rendre(connu(6236, 8)).knownValue)
        assertEquals("An Nâs • verset 6", rendre(connu(8, 6236)).knownValue)
    }

    @Test
    fun `un verset en cours d'apprentissage n'est pas connu`() {
        val etat = etat().copy(knowledge = mapOf("8" to Mastery.LEARNING))
        assertEquals("Aucun verset validé", rendre(etat).knownValue)
    }

    @Test
    fun `un verset connu hors du corpus est ecarte sans faire tomber l'ecran`() {
        // `knowledge` est indexé par des clés numériques venues d'un autre appareil : `Quran.verseAt`
        // lève sur un identifiant qu'il ne connaît pas. Le contrôle vise exactement cela — une clé
        // hors corpus doit être écartée, pas propagée jusqu'à l'écran.
        assertEquals("Aucun verset validé", rendre(connu(7000)).knownValue)
        assertEquals("Al Baqarah • verset 1", rendre(connu(8, 7000)).knownValue)
    }

    // --- la dernière lecture -----------------------------------------------

    @Test
    fun `la derniere lecture est portee telle quelle, et son absence vaut null`() {
        assertNull(rendre(etat()).lastReadVerse)

        val lu = etat().copy(lastRead = LastRead(page = 121, verseId = 746, readAt = "2026-01-01T00:00:00.000Z"))
        assertEquals(746, rendre(lu).lastReadVerse)
    }

    // --- le découpage des pages suit la source -----------------------------

    @Test
    fun `les pages d'une division se calculent dans le decoupage de la source affichee`() {
        // Division fabriquée : le verset 746 est le premier de la page 121 dans le moushaf de
        // Médine, et le dernier de la page 120 dans la composition typographique. C'est la
        // première divergence entre les deux découpages, et elle est mesurée.
        val fabriquee = listOf(Division(number = 1, start = 746, end = 746))

        val medine = QuranListRenderer.divisions(fabriquee, QuranText.View.JUZ, "", "traditional")
        val composition = QuranListRenderer.divisions(fabriquee, QuranText.View.JUZ, "", "coranTest")

        assertEquals("Pages 121 – 121", medine.single().meaning)
        assertEquals("Pages 120 – 120", composition.single().meaning)
    }

    @Test
    fun `les deux bouts d'une division sont calcules, et non deduits l'un de l'autre`() {
        // `studyPage` n'est pas linéaire : les deux découpages ne placent pas les mêmes versets au
        // même endroit, donc déduire la fin du début donnerait un compte faux sur les divisions qui
        // chevrochent une page. Ici la même division rend « 120 – 121 » ou « 120 – 120 » selon le
        // découpage : un calcul qui n'aurait traité que le début rendrait le même texte des deux
        // côtés, et ce contrôle tomberait.
        val fabriquee = listOf(Division(number = 1, start = 740, end = 746))

        assertEquals(
            "Pages 120 – 121",
            QuranListRenderer.divisions(fabriquee, QuranText.View.JUZ, "", "traditional").single().meaning,
        )
        assertEquals(
            "Pages 120 – 120",
            QuranListRenderer.divisions(fabriquee, QuranText.View.JUZ, "", "coranTest").single().meaning,
        )
    }

    @Test
    fun `les bornes des divisions livrees tombent sur des versets que les trois sources placent pareil`() {
        // Le fait mesuré, écrit pour qu'on ne croie pas que la règle ci-dessus est couverte par les
        // données : **aucune** des 30 juz ni des 60 hizb ne change de page entre les trois
        // découpages du projet. Le paramètre de source est donc inerte sur le corpus livré — ce qui
        // est précisément la raison pour laquelle la règle s'éprouve sur une division fabriquée.
        val cles = listOf(MushafSource.MEDINA, MushafSource.CORAN_TEST, MushafSource.CORAN_1441)
            .map { StudyProgressCalculator.sourceKey(it) }

        var bornes = 0
        for (division in Quran.juzs + Quran.hizbs) {
            for (verset in listOf(division.start, division.end)) {
                val pages = cles.map { StudyProgressCalculator.studyPage(verset, it) }
                assertEquals(1, pages.toSet().size, "verset $verset : $pages")
                bornes++
            }
        }
        // 30 juz + 60 hizb, deux bornes chacun : le compte est vérifié pour que la boucle ne
        // puisse pas devenir vide sans que rien ne le dise.
        assertEquals(180, bornes)
    }
}
