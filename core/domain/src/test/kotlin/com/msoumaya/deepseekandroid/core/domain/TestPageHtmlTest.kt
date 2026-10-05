package com.msoumaya.deepseekandroid.core.domain

import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Éprouve le document produit pour une page de la composition « Coran avec règles de Tajwid ».
 *
 * Le document est la seule pièce que la WebView reçoit : ce qui n'y est pas ne s'affichera pas,
 * et ce qui y est mal formé ne s'affichera pas davantage — sans erreur, le plus souvent, parce
 * qu'un navigateur ignore ce qu'il ne comprend pas. Les contrôles portent donc sur ce que le
 * document **doit** contenir, et sur ce qu'il ne doit **pas**.
 *
 * ## Les polices sont factices ici, et c'est voulu
 *
 * Les 607 polices couleur pèsent 48,9 Mo et ne sont pas encore embarquées : elles partiront avec
 * la WebView, qui est leur seul consommateur. Le générateur, lui, reçoit les URI de polices en
 * paramètre — il n'a donc besoin que de chaînes reconnaissables pour être éprouvé. Un contrôle
 * qui exigerait les vraies polices mesurerait le poids du dépôt, pas la justesse du document.
 */
class TestPageHtmlTest {

    @Before
    fun setUp() = QuranFixture.install()

    private val fonts = TestPageHtml.Fonts(
        page = "data:font/woff2;base64,PAGE",
        title = "data:font/woff2;base64,TITRE",
        basmala = "data:font/woff2;base64,BASMALA",
    )

    private fun document(page: Int): String = TestPageHtml.document(TestPageLoader.load(page), fonts)

    // --- l'ossature ---

    @Test
    fun `le document declare les trois polices, et chacune a sa place`() {
        val html = document(1)
        assertTrue(html.contains("@font-face{font-family:Page;src:url('data:font/woff2;base64,PAGE') format('woff2')}"))
        assertTrue(html.contains("@font-face{font-family:Title;src:url('data:font/woff2;base64,TITRE') format('woff2')}"))
        assertTrue(html.contains("@font-face{font-family:Basmala;src:url('data:font/woff2;base64,BASMALA') format('woff2')}"))
    }

    @Test
    fun `le document interdit tout reseau sauf la police, et n'autorise que son propre script`() {
        val html = document(1)
        assertTrue(html.contains("default-src 'none'"))
        assertTrue(html.contains("font-src data:"))
        assertTrue(html.contains("script-src 'unsafe-inline'"))
    }

    @Test
    fun `le canevas logique est celui de la source`() {
        val html = document(1)
        assertTrue(html.contains("width:${TestPageIndex.PAGE_WIDTH}px"))
        assertTrue(html.contains("height:${TestPageIndex.PAGE_HEIGHT}px"))
    }

    @Test
    fun `le document porte le numero de page en chiffres arabes, et le juz`() {
        assertTrue(document(1).contains("<footer>\u0661</footer>"))
        assertTrue(document(604).contains("<footer>\u0666\u0660\u0664</footer>"))
        assertTrue(document(2).contains("<span>Juz 1</span>"))
    }

    // --- les mots ---

    @Test
    fun `chaque mot porte son identifiant, sa cle de verset et son libelle`() {
        val html = document(1)
        assertTrue(html.contains("class=\"word\" data-id=\"1\" data-verse=\"1:1\""))
        // Le libelle est le texte lisible, pas les glyphes.
        val premier = TestPageLoader.load(1).lines[1].words.first()
        assertTrue(html.contains("aria-label=\"${premier.arabic}\""))
    }

    @Test
    fun `un caractere de balisage est echappe, dans le libelle comme dans les glyphes`() {
        val page = TestPageLoader.parse(
            """{"page":1,"surah":1,"juz":1,"fontSize":70,"lines":[{"line":1,"type":"ayah","centered":false,"surah":null,"words":[[1,1,1,1,"<g>","a & b \" c"]]}]}""",
        )
        val html = TestPageHtml.document(page, fonts)
        assertTrue(html.contains("&lt;g&gt;"), "les glyphes sont echappes")
        assertTrue(html.contains("a &amp; b &quot; c"), "le libelle est echappe")
        assertFalse(html.contains("<g>"), "la balise brute ne doit pas subsister")
    }

    // --- les lignes ---

    @Test
    fun `une ligne de mots porte son numero et son centrage`() {
        val html = document(1)
        assertTrue(html.contains("class=\"line centered\" data-line=\"2\""))
        // La page 3 porte une ligne non centree : les deux formes doivent exister.
        assertTrue(document(3).contains("class=\"line \" data-line=\"1\""))
    }

    @Test
    fun `un bandeau de sourate porte l'ornement et le nom de la sourate`() {
        val html = document(1)
        assertTrue(html.contains("class=\"surah-heading\""))
        assertTrue(html.contains("<svg"), "l'ornement est un SVG en ligne")
        assertTrue(html.contains("class=\"surah\">surah001</span>"))
        // Le bandeau porte le numero de la **sourate**, pas celui de la page : la derniere
        // page porte les bandeaux 112, 113 et 114, et n'en porte aucun qui dise « 604 ».
        assertTrue(document(604).contains("class=\"surah\">surah114</span>"))
    }

    @Test
    fun `la basmala prend la graphie de la sourate qui suit`() {
        // Al-Baqara : sa propre graphie.
        assertTrue(document(2).contains("<span class=\"basmala\">\uFC9A\uFC9B\uFC9E\uFCA4</span>"))
        // Les sourates 95 et 97 : une autre.
        assertTrue(document(597).contains("<span class=\"basmala\">\uFB57\uFCAB\uFCAE\uFCB4</span>"))
        assertTrue(document(598).contains("<span class=\"basmala\">\uFB57\uFCAB\uFCAE\uFCB4</span>"))
        // Toutes les autres : la graphie courante. La premiere de ces basmalas est page 50,
        // sourate 3 -- la page 3, elle, n'en porte aucune.
        assertTrue(document(50).contains("<span class=\"basmala\">\uFCAA\uFCAB\uFCAE\uFCB4</span>"))
    }

    @Test
    fun `une page sans basmala n'en porte aucune`() {
        // Al-Fatiha : sa basmala est son premier verset, donc une ligne de mots.
        assertFalse(document(1).contains("class=\"basmala\""))
    }

    // --- la taille de police, ecrite comme l'original l'ecrit ---

    @Test
    fun `une taille ronde s'ecrit sans decimale, une taille fractionnaire telle quelle`() {
        assertTrue(document(1).contains("font:70px Page;"), "70 et non 70.0")
        assertTrue(
            document(3).contains("font:56.93794974923073px Page;"),
            "la valeur fractionnaire est ecrite en entier",
        )
    }

    // --- le pont, et le script ---

    @Test
    fun `le script renvoie ses mesures par le pont Android, avec les deux replis`() {
        val html = document(1)
        assertTrue(html.contains("window.CoranTest.postMessage"), "le pont Android d'abord")
        assertTrue(html.contains("window.ReactNativeWebView"), "le repli du client d'origine")
        assertTrue(html.contains("window.parent.postMessage"), "le repli de l'apercu navigateur")
    }

    @Test
    fun `le script annonce la page qu'il vient de mesurer`() {
        assertTrue(document(7).contains("send({type:'ready',page:7,words})"))
        assertTrue(document(7).contains("send({type:'error',page:7})"))
    }

    @Test
    fun `le script mesure les mots, fusionne par ligne, et sait ou l'on a touche`() {
        val html = document(1)
        assertTrue(html.contains("getBoundingClientRect"), "la mesure vient du moteur de rendu")
        assertTrue(html.contains("zones.find(z=>z.line===r.line)"), "la fusion se fait par ligne")
        assertTrue(html.contains("function hit(x,y)"), "le toucher resout le verset")
        assertTrue(html.contains("window.applyReaderState"), "les surcouches se pilotent de l'exterieur")
    }

    @Test
    fun `le document porte les reperes de marge, mais pas le bandeau de seance`() {
        // Les repères appartiennent au document, et à lui seul : il est le seul à connaître les
        // rectangles de ses mots, et c'est de là que vient leur place. Le bandeau, lui, est rendu
        // en Compose au-dessus de la page, pour toutes les sources : le porter ici ferait deux
        // bandeaux pour une seule séance.
        val html = document(1)
        assertTrue(html.contains("function marginAnchors("), "les repères se calculent dans le document")
        assertTrue(html.contains("function drawMargin("), "et ils s'y dessinent")
        assertTrue(html.contains("drawMargin();}"), "la surimpression les appelle en dernier")
        assertFalse(html.contains("study-banner"))
    }

    @Test
    fun `le document lit les trois champs de seance, et pas celui qu'on a ecarte`() {
        // L'accord entre ce que l'application envoie et ce que le document lit se mesure des deux
        // côtés : `TestPageOverlayTest` fixe ce qui part, ce test fixe ce qui est lu. Un champ
        // envoyé et jamais lu laisserait croire que quelque chose s'en sert — et, à l'inverse, un
        // champ lu et jamais envoyé resterait `undefined` sans que rien ne le signale.
        val html = document(1)
        assertTrue(html.contains("readerState.session||[]"), "la suite des clés de la séance")
        assertTrue(html.contains("readerState.sessionDone||0"), "le nombre de repères pleins")
        assertTrue(
            html.contains("readerState.sessionColor||readerState.primary"),
            "la teinte, avec son repli sur `primary`",
        )
        assertFalse(html.contains("sessionThrough"), "le quatrième champ est écarté après mesure")
    }
}
