package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.LastRead
import com.msoumaya.deepseekandroid.core.model.MushafSource
import com.msoumaya.deepseekandroid.core.model.ReaderPreferences
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La mémoire du lecteur, éprouvée sur le **vrai** référentiel.
 *
 * Les six mille deux cent trente-six versets et les six cent quatre pages sont chargés : une
 * maquette de trois versets ne dirait rien du point qui compte ici, à savoir **quel verset
 * représente une page**. Le choix se prend sur une table de découpage, et c'est précisément
 * l'endroit où une erreur d'indexation passe inaperçue — le verset retenu serait *un* verset de
 * la page, donc plausible, donc jamais signalé.
 *
 * ## Le cas qui donne son sens à la règle
 *
 * Les deux découpages du projet ne placent pas les mêmes versets au même endroit : cinquante-six
 * versets sur six mille deux cent trente-six changent de page entre le moushaf de Médine et la
 * composition. La première page où les deux divergent est **cherchée** ici plutôt que recopiée,
 * et le test qui l'utilise vérifie d'abord qu'elle existe : sans cela, le jour où les deux
 * découpages s'aligneraient, le contrôle deviendrait vert en ne mesurant plus rien.
 */
class ReaderMemoryTest {

    @BeforeTest
    fun installerLeReferentiel() {
        QuranFixture.install()
    }

    // -----------------------------------------------------------------------
    // Ce que la fermeture écrit
    // -----------------------------------------------------------------------

    @Test
    fun `la page quittee est ajoutee aux pages lues`() {
        val etat = QuranFixture.onboardedState().copy(readPages = listOf(3, 7))
        val apres = ReaderMemory.close(etat, page = 120, at = INSTANT)
        assertEquals(listOf(3, 7, 120), apres.readPages)
    }

    @Test
    fun `une page deja lue ne se deplace pas dans la liste`() {
        // L'ordre d'insertion est celui des lectures, et le client d'origine le conserve :
        // `Array.from(new Set([...lus, page]))` garde la page à sa place si elle y est déjà.
        // La replacer à la fin écrirait un état différent du sien pour la même suite d'actions,
        // et la comparaison des deux états — le contrôle de compatibilité du portage — le dirait.
        val etat = QuranFixture.onboardedState().copy(readPages = listOf(120, 3))
        val apres = ReaderMemory.close(etat, page = 120, at = INSTANT)
        assertEquals(listOf(120, 3), apres.readPages)
    }

    @Test
    fun `les pages lues ne perdent rien quand l'etat n'en portait aucune`() {
        val etat = QuranFixture.onboardedState().copy(readPages = null)
        val apres = ReaderMemory.close(etat, page = 42, at = INSTANT)
        assertEquals(listOf(42), apres.readPages)
    }

    @Test
    fun `la derniere lecture retient la page et l'instant recus`() {
        val etat = QuranFixture.onboardedState()
        val apres = ReaderMemory.close(etat, page = 120, at = INSTANT)
        assertEquals(120, apres.lastRead?.page)
        assertEquals(INSTANT, apres.lastRead?.readAt)
        assertEquals(INSTANT, apres.updatedAt)
    }

    @Test
    fun `la source composee recoit sa page memorisee`() {
        val etat = QuranFixture.onboardedState()
        val apres = ReaderMemory.close(etat, page = 120, at = INSTANT)
        assertEquals(MushafSource.CORAN_TEST, apres.reader?.mushaf)
        assertEquals(120, apres.reader?.testPage)
    }

    @Test
    fun `une source en images ne recoit aucune page memorisee`() {
        // `testPage` est la page de la **composition**. L'écrire pour une autre source ferait
        // ouvrir la composition là où l'on a quitté des images — une page plausible, et fausse.
        val etat = QuranFixture.onboardedState().copy(
            reader = ReaderPreferences(mushaf = MushafSource.MEDINA),
            lastRead = null,
        )
        val apres = ReaderMemory.close(etat, page = 120, at = INSTANT)
        assertNull(apres.reader?.testPage)
        assertEquals(MushafSource.MEDINA, apres.reader?.mushaf)
        // La page de la source affichée, elle, est écrite pour toutes les sources : c'est ce
        // que la carte « Continuer » de l'accueil annonce.
        assertEquals(120, apres.lastRead?.page)
    }

    @Test
    fun `la fermeture adopte la source composee et son suivi audio`() {
        val etat = QuranFixture.onboardedState().copy(reader = null)
        val apres = ReaderMemory.close(etat, page = 5, at = INSTANT)
        assertEquals(MushafSource.CORAN_TEST, apres.reader?.mushaf)
        assertEquals(true, apres.reader?.followAudio)
    }

    @Test
    fun `un etat portant l'ancienne source est migre avant la decision`() {
        // Sans la migration préalable, la règle ne reconnaîtrait pas la source composée,
        // n'écrirait pas `testPage`, et la migration changerait ensuite la source en `coranTest` :
        // la page serait mémorisée pour la source affichée sans jamais avoir été écrite.
        val etat = QuranFixture.onboardedState().copy(
            reader = ReaderPreferences(mushaf = MushafSource.TAJWEED_PAGES),
        )
        val apres = ReaderMemory.close(etat, page = 120, at = INSTANT)
        assertEquals(MushafSource.CORAN_TEST, apres.reader?.mushaf)
        assertEquals(120, apres.reader?.testPage)
    }

    @Test
    fun `une page hors moushaf est refusee`() {
        val etat = QuranFixture.onboardedState()
        assertFailsWith<IllegalArgumentException> { ReaderMemory.close(etat, page = 0, at = INSTANT) }
    }

    // -----------------------------------------------------------------------
    // Le verset qui représente la page
    // -----------------------------------------------------------------------

    @Test
    fun `le verset memorise est conserve s'il tient encore sur la page`() {
        val page = PAGE_DIVERGENTE
        val premier = MushafSourceNavigation.pageRange(MushafSource.CORAN_TEST, page).start
        val etat = QuranFixture.onboardedState().copy(
            lastRead = LastRead(page = page, verseId = premier, readAt = INSTANT),
        )
        val apres = ReaderMemory.close(etat, page = page, at = INSTANT)
        assertEquals(premier, apres.lastRead?.verseId)
    }

    @Test
    fun `le verset memorise est abandonne s'il a change de page`() {
        // Le verset mémorisé appartient à la page 1 et l'on ferme sur une autre page : il n'est
        // plus sous les yeux, il ne peut donc pas représenter la position. Le repli doit prendre
        // la main, et non conserver un verset que personne ne voit.
        val premier = MushafSourceNavigation.pageRange(MushafSource.CORAN_TEST, 1).start
        val etat = QuranFixture.onboardedState().copy(
            lastRead = LastRead(page = 1, verseId = premier, readAt = INSTANT),
        )
        val apres = ReaderMemory.close(etat, page = PAGE_DIVERGENTE, at = INSTANT)
        val attendu = MushafSourceNavigation.pageRange(MushafSource.CORAN_TEST, PAGE_DIVERGENTE).start
        assertEquals(attendu, apres.lastRead?.verseId)
    }

    @Test
    fun `le verset d'ouverture est retenu quand il tient sur la page`() {
        // Le cas de la séance : on ouvre sur le verset 746, on tourne jusqu'à la page 120, et
        // l'on veut retrouver 746 — pas le premier verset de la page 120. Le verset est pris
        // **au milieu** de la page exprès : au début, il ne se distinguerait pas du repli.
        val range = MushafSourceNavigation.pageRange(MushafSource.CORAN_TEST, PAGE_DIVERGENTE)
        val milieu = range.start + 1
        assertTrue(milieu <= range.end, "La page $PAGE_DIVERGENTE porte moins de deux versets.")
        val etat = QuranFixture.onboardedState()
        val apres = ReaderMemory.close(etat, page = PAGE_DIVERGENTE, start = milieu, at = INSTANT)
        assertEquals(milieu, apres.lastRead?.verseId)
    }

    @Test
    fun `le verset memorise passe avant le verset d'ouverture`() {
        // L'ordre des deux premiers candidats compte : un feuilletage ne doit pas décaler la
        // position d'un verset à chaque page tournée. Ici les deux tiennent sur la page, et
        // c'est le mémorisé qui gagne — l'inverse ferait dériver la position à chaque ouverture.
        val range = MushafSourceNavigation.pageRange(MushafSource.CORAN_TEST, PAGE_DIVERGENTE)
        val memorise = range.start
        val ouverture = range.start + 1
        val etat = QuranFixture.onboardedState().copy(
            lastRead = LastRead(page = PAGE_DIVERGENTE, verseId = memorise, readAt = INSTANT),
        )
        val apres = ReaderMemory.close(etat, page = PAGE_DIVERGENTE, start = ouverture, at = INSTANT)
        assertEquals(memorise, apres.lastRead?.verseId)
    }

    @Test
    fun `un verset hors corpus ne fait pas echouer la fermeture`() {
        // Un état synchronisé depuis un autre client peut porter un verset que ce corpus ignore.
        // Fermer le lecteur ne doit pas pouvoir échouer : le candidat est écarté, et le repli
        // prend la main. Sans l'enveloppe, l'exception remonterait jusqu'à la route — qui
        // l'avalerait, donc la position ne serait jamais écrite, et rien ne le dirait.
        val etat = QuranFixture.onboardedState().copy(
            lastRead = LastRead(page = 1, verseId = 99_999, readAt = INSTANT),
        )
        val apres = ReaderMemory.close(etat, page = 1, start = 99_999, at = INSTANT)
        val attendu = MushafSourceNavigation.pageRange(MushafSource.CORAN_TEST, 1).start
        assertEquals(attendu, apres.lastRead?.verseId)
    }

    // -----------------------------------------------------------------------
    // La page d'ouverture
    // -----------------------------------------------------------------------

    @Test
    fun `la source composee s'ouvre sur sa page memorisee`() {
        val etat = QuranFixture.onboardedState().copy(
            reader = ReaderPreferences(mushaf = MushafSource.CORAN_TEST, testPage = 120),
        )
        assertEquals(120, ReaderMemory.openingPage(etat, MushafSource.CORAN_TEST))
    }

    @Test
    fun `sans page memorisee la source composee s'ouvre au debut`() {
        val etat = QuranFixture.onboardedState()
        assertNull(etat.reader?.testPage)
        assertEquals(1, ReaderMemory.openingPage(etat, MushafSource.CORAN_TEST))
    }

    @Test
    fun `une source en images s'ouvre sur le dernier verset lu, reprojete`() {
        // `lastRead.page` a été écrite dans le découpage de la source qui était affichée. La
        // relire telle quelle pour une autre source ouvrirait à côté : c'est exactement le
        // défaut que les deux découpages du projet rendent visible. Le test le **mesure** au
        // lieu de l'affirmer.
        //
        // Le verset est **cherché** dans la page, et non pris à son début. La divergence des
        // deux découpages est un décalage, donc le premier verset d'une page divergente tombe
        // le plus souvent sur la même page dans les deux tables : mesuré, la page 120 commence
        // au verset 740 dans les deux. Prendre le premier verset aurait donc rendu ce test vert
        // en comparant la page brute à elle-même — c'est ce qui est arrivé à sa première
        // version, et le message d'échec l'a dit.
        val range = MushafSourceNavigation.pageRange(MushafSource.CORAN_TEST, PAGE_DIVERGENTE)
        val verseId = (range.start..range.end).firstOrNull { Quran.pageOf(it) != PAGE_DIVERGENTE }
            ?: error(
                "Aucun verset de la page $PAGE_DIVERGENTE ne change de page entre les deux " +
                    "découpages : ce test ne mesure plus la reprojection.",
            )
        assertTrue(
            Quran.pageOf(verseId) != PAGE_DIVERGENTE,
            "Le verset $verseId tombe sur la page $PAGE_DIVERGENTE dans les deux découpages.",
        )
        val etat = QuranFixture.onboardedState().copy(
            reader = ReaderPreferences(mushaf = MushafSource.MEDINA),
            lastRead = LastRead(page = PAGE_DIVERGENTE, verseId = verseId, readAt = INSTANT),
        )
        // La page brute aurait été $PAGE_DIVERGENTE ; la projection rend l'autre.
        assertEquals(Quran.pageOf(verseId), ReaderMemory.openingPage(etat, MushafSource.MEDINA))
    }

    @Test
    fun `sans derniere lecture une source en images s'ouvre au debut`() {
        val etat = QuranFixture.onboardedState().copy(lastRead = null)
        assertEquals(1, ReaderMemory.openingPage(etat, MushafSource.MEDINA))
    }

    @Test
    fun `un dernier verset hors corpus ramene au debut`() {
        val etat = QuranFixture.onboardedState().copy(
            lastRead = LastRead(page = 300, verseId = 99_999, readAt = INSTANT),
        )
        assertEquals(1, ReaderMemory.openingPage(etat, MushafSource.MEDINA))
    }

    private companion object {

        const val INSTANT = "2026-10-05T13:00:00Z"

        /**
         * La première page où les deux découpages du projet divergent.
         *
         * **Cherchée**, et non recopiée : c'est la mesure qui fait vivre les tests ci-dessus.
         * Si les deux découpages s'alignaient, la constante vaudrait `null` et les tests qui
         * l'utilisent échoueraient sur un message explicite, au lieu de devenir verts en ne
         * mesurant plus rien.
         *
         * `by lazy`, et non un `val` du compagnon : l'initialisation du compagnon précède le
         * `@BeforeTest` qui installe le référentiel, et `Quran.pages` serait alors **vide** — la
         * recherche ne trouverait rien, et tous les tests tomberaient sur un message qui
         * accuserait les découpages au lieu de l'ordre d'initialisation.
         */
        val PAGE_DIVERGENTE: Int by lazy {
            (1..Quran.pages.size).firstOrNull { page ->
                MushafSourceNavigation.pageRange(MushafSource.CORAN_TEST, page) != Quran.pageRange(page)
            } ?: error(
                "Les deux découpages ne divergent sur aucune page : ces tests ne mesurent plus rien.",
            )
        }
    }
}
