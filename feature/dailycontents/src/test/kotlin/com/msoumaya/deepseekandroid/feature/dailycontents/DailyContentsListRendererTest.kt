package com.msoumaya.deepseekandroid.feature.dailycontents

import com.msoumaya.deepseekandroid.core.domain.DailyContentsText
import com.msoumaya.deepseekandroid.core.model.ContentCategory
import com.msoumaya.deepseekandroid.core.model.DailyContent
import com.msoumaya.deepseekandroid.core.model.DailyContentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Éprouve le rendu de l'écran de liste, ligne à ligne.
 *
 * ## Pourquoi ici, et pas à l'écran
 *
 * `DailyContentsListRenderer` est un objet pur : ni horloge, ni coroutine, ni appareil. C'est ce
 * qui permet de mesurer les décisions qui, dans le Compose, ne s'exécuteraient que sur un
 * téléphone — quel titre quand il n'y en a pas, quelle ligne de provenance, quel libellé
 * d'ouverture, quel texte partagé.
 *
 * ## Ce que ces contrôles tiennent, et qu'aucune compilation ne dirait
 *
 *  - **l'ordre des onglets** est celui écrit en clair à la source, et non celui de l'énumération ;
 *  - **le pied de liste ne dépend pas du corps** : une liste vide qui charge affiche la phrase de
 *    chargement **deux fois**, ce que produit la liste de l'original ;
 *  - **les deux replis ne disent pas la même chose** — le titre se replie sur le nom du **type**,
 *    le libellé d'ouverture sur le mot « ce contenu » ;
 *  - **la provenance du partage n'est pas celle de la fiche** — une espace au lieu du point médian,
 *    et une espace de fin quand la référence manque. C'est le texte qui **sort** de l'application.
 */
class DailyContentsListRendererTest {

    // ------------------------------------------------------------------ fabriques

    private fun contenu(
        id: String = "c1",
        type: DailyContentType = DailyContentType.INVOCATION,
        titre: String? = "Un titre",
        arabe: String? = "نص",
        phonetique: String? = "nass",
        francais: String = "Un texte.",
        explication: String? = null,
        source: String = "Recueil",
        reference: String? = null,
        image: String? = null,
    ) = DailyContent(
        id = id,
        type = type,
        title = titre,
        arabicText = arabe,
        phoneticText = phonetique,
        frenchText = francais,
        explanation = explication,
        source = source,
        reference = reference,
        categoryId = "cat-1",
        imageUrl = image,
    )

    private fun categorie(
        id: String = "cat-1",
        type: DailyContentType = DailyContentType.INVOCATION,
        nom: String = "Prière",
        icone: String = "☾",
    ) = ContentCategory(id = id, name = nom, icon = icone, type = type)

    // ------------------------------------------------------------------ les onglets

    @Test
    fun `les deux onglets sont ceux de l'original, dans son ordre`() {
        // C'est `(['reminder','invocation'] as const)` écrit en clair, et non l'énumération : le
        // jour où le `check` de la base accepterait un troisième type, un onglet de plus
        // apparaîtrait alors que l'écran d'origine n'en propose que deux.
        val header = DailyContentsListRenderer.header(DailyListState())

        assertEquals(
            listOf(DailyContentsText.TAB_REMINDER, DailyContentsText.TAB_INVOCATION),
            header.tabs.map { it.label },
        )
    }

    @Test
    fun `l'ecran s'ouvre sur l'onglet de l'invocation`() {
        // `useState<ContentType>('invocation')` : le second onglet. Le reproduire est la seule
        // façon de ne pas changer ce que voit la personne qui ouvre la liste.
        val header = DailyContentsListRenderer.header(DailyListState())

        assertEquals(listOf(false, true), header.tabs.map { it.selected })
        assertEquals(DailyContentType.INVOCATION, DailyListState().type)
    }

    @Test
    fun `la bascule des favoris suit l'etat`() {
        assertEquals(DailyContentsText.MY_FAVORITES, DailyContentsListRenderer.header(DailyListState()).favorites.label)
        assertFalse(DailyContentsListRenderer.header(DailyListState()).favorites.selected)
        assertTrue(DailyContentsListRenderer.header(DailyListState(favorites = true)).favorites.selected)
    }

    // ------------------------------------------------------------------ les pastilles

    @Test
    fun `les pastilles sont filtrees par le type courant`() {
        // `cats.filter(c => c.type===type)`. Sans ce filtre, un appui poserait un filtre qui ne
        // rendrait **rien** — la liste serait vide, et rien ne dirait que c'est la catégorie qui
        // ne s'applique pas.
        val state = DailyListState(
            type = DailyContentType.INVOCATION,
            categories = listOf(
                categorie("cat-i", DailyContentType.INVOCATION, "Prière"),
                categorie("cat-r", DailyContentType.REMINDER, "Rappels"),
            ),
        )

        assertEquals(
            listOf(null, "cat-i"),
            DailyContentsListRenderer.header(state).categories.map { it.id },
        )
    }

    @Test
    fun `la pastille Tout est allumee tant qu'aucun filtre n'est pose`() {
        val libre = DailyContentsListRenderer.header(DailyListState(selected = null))
        val filtre = DailyContentsListRenderer.header(DailyListState(selected = "cat-1"))

        assertTrue(libre.categories.first().selected)
        assertFalse(filtre.categories.first().selected)
    }

    @Test
    fun `la pastille porte l'icone de la base, separee par une espace`() {
        // `{c.icon} {c.name}` : l'icône est un **caractère**, pas un nom d'icône. L'espace vient
        // d'ici, et non de la base.
        val state = DailyListState(categories = listOf(categorie(nom = "Foi", icone = "☀")))

        assertEquals("☀ Foi", DailyContentsListRenderer.header(state).categories[1].label)
    }

    // ------------------------------------------------------------------ l'état vide

    @Test
    fun `la liste vide dit qu'elle charge avant de dire qu'il n'y a rien`() {
        // Une liste qui charge ne dit **pas** qu'il n'y a rien, elle dit qu'elle lit. Les confondre
        // ferait affirmer « Aucune invocation disponible. » pendant la lecture.
        assertEquals(
            DailyContentsText.LIST_LOADING,
            DailyContentsListRenderer.emptyMessage(busy = true, type = DailyContentType.REMINDER),
        )
        assertEquals(
            DailyContentsText.EMPTY_REMINDER,
            DailyContentsListRenderer.emptyMessage(busy = false, type = DailyContentType.REMINDER),
        )
        assertEquals(
            DailyContentsText.EMPTY_INVOCATION,
            DailyContentsListRenderer.emptyMessage(busy = false, type = DailyContentType.INVOCATION),
        )
    }

    @Test
    fun `la phrase de liste vide nomme le type filtre`() {
        // Deux onglets, deux phrases : une phrase unique ferait croire que la base est vide alors
        // que l'autre onglet est plein.
        assertTrue(DailyContentsListRenderer.emptyMessage(false, DailyContentType.INVOCATION).contains("invocation"))
        assertTrue(DailyContentsListRenderer.emptyMessage(false, DailyContentType.REMINDER).contains("rappel"))
    }

    // ------------------------------------------------------------------ le pied de liste

    @Test
    fun `le pied de liste disparait quand il n'y a pas de suite`() {
        // `more ? <Button …> : null` : l'original retire entièrement son `ListFooterComponent`.
        assertNull(DailyContentsListRenderer.footer(DailyListState(more = false, busy = false)))
    }

    @Test
    fun `le pied de liste propose la suite quand rien ne charge`() {
        assertEquals(
            DailyContentsText.LOAD_MORE,
            DailyContentsListRenderer.footer(DailyListState(busy = false, more = true)),
        )
    }

    @Test
    fun `le pied de liste double la phrase de chargement d'une liste vide`() {
        // **C'est la mesure de l'original.** Sa liste rend `ListEmptyComponent` **et**
        // `ListFooterComponent`, et la liste de React Native rend le second même quand le premier
        // s'affiche : une liste vide qui charge affiche donc « Chargement… » deux fois — une fois
        // dans la plage vide, une fois sous le bouton. C'est pourquoi `footer` ne dépend pas du
        // corps : le lier ferait perdre le bouton sous une liste vide, que l'original affiche.
        val state = DailyListState(items = emptyList(), busy = true, more = true)

        val corps = assertIs<DailyListBody.Empty>(DailyContentsListRenderer.body(state))

        assertEquals(DailyContentsText.LIST_LOADING, corps.message)
        assertEquals(DailyContentsText.LIST_LOADING, DailyContentsListRenderer.footer(state))
    }

    // ------------------------------------------------------------------ le corps

    @Test
    fun `le corps publie une ligne par contenu, avec son favori`() {
        val state = DailyListState(
            items = listOf(contenu("c1"), contenu("c2")),
            favoriteIds = setOf("c2"),
        )

        val corps = assertIs<DailyListBody.Rows>(DailyContentsListRenderer.body(state))

        assertEquals(listOf("c1", "c2"), corps.rows.map { it.id })
        assertEquals(listOf(false, true), corps.rows.map { it.card.favorite })
    }

    // ------------------------------------------------------------------ la fiche

    @Test
    fun `la fiche replie le titre sur le nom du type`() {
        // `item.title??(item.type==='invocation'?'Invocation':'Rappel')` : le repli est le nom du
        // **type**, et non le mot de l'écran.
        assertEquals("Invocation", DailyContentsListRenderer.card(contenu(titre = null), false).title)
        assertEquals(
            "Rappel",
            DailyContentsListRenderer.card(contenu(type = DailyContentType.REMINDER, titre = null), false).title,
        )
        assertEquals("Un titre", DailyContentsListRenderer.card(contenu(), false).title)
    }

    @Test
    fun `le libelle d'ouverture replie sur le mot « ce contenu »`() {
        // `Ouvrir ${item.title??'ce contenu'}`. Les deux replis ne disent pas la même chose : les
        // confondre ferait annoncer « Ouvrir Rappel », qui nomme un **type** là où la personne
        // entend un **contenu**.
        assertEquals("Ouvrir ce contenu", DailyContentsListRenderer.row(contenu(titre = null), false).openLabel)
        assertEquals("Ouvrir Un titre", DailyContentsListRenderer.row(contenu(), false).openLabel)
    }

    @Test
    fun `l'en-tete de la fiche ne se deduit pas du titre de repli`() {
        // L'original écrit `!!item.title || (!!item.image_url && !imageError)`. `hasTitle` dit donc
        // la présence d'un titre **propre**, et non la visibilité de l'en-tête : un contenu sans
        // titre peut porter un en-tête — celui du repli — si son illustration se charge, et n'en
        // porte aucun si elle échoue. La seconde moitié est un état local, tenu par la fiche.
        assertFalse(DailyContentsListRenderer.card(contenu(titre = null), false).hasTitle)
        assertFalse(DailyContentsListRenderer.card(contenu(titre = ""), false).hasTitle)
        assertTrue(DailyContentsListRenderer.card(contenu(), false).hasTitle)
    }

    @Test
    fun `la ligne de provenance de la fiche est facultative`() {
        // `{!!item.source && …}` : la vignette du carrousel affiche la sienne sans condition, la
        // fiche non. Deux rendus pour un seul mot, et c'est la source qui tranche.
        assertNull(DailyContentsListRenderer.card(contenu(source = ""), false).source)
        assertEquals(
            "Source : Recueil",
            DailyContentsListRenderer.card(contenu(source = "Recueil"), false).source,
        )
    }

    @Test
    fun `la provenance de la fiche joint la reference par un point median`() {
        assertEquals(
            "Source : Recueil · 1:1",
            DailyContentsListRenderer.card(contenu(source = "Recueil", reference = "1:1"), false).source,
        )
    }

    @Test
    fun `les textes facultatifs vides sont retires`() {
        // `{!!item.arabic_text && …}` : une chaîne vide n'est pas un texte, et l'afficher laisserait
        // une ligne blanche au milieu de la carte.
        val card = DailyContentsListRenderer.card(
            contenu(arabe = "", phonetique = "", explication = ""),
            false,
        )

        assertNull(card.arabic)
        assertNull(card.phonetic)
        assertNull(card.explanation)
    }

    @Test
    fun `la fiche porte le glyphe et le libelle de son favori`() {
        val oui = DailyContentsListRenderer.card(contenu(), true)
        val non = DailyContentsListRenderer.card(contenu(), false)

        assertEquals(DailyContentsText.FAVORITE_ON, oui.favoriteGlyph)
        assertEquals(DailyContentsText.FAVORITE_REMOVE, oui.favoriteLabel)
        assertEquals(DailyContentsText.FAVORITE_OFF, non.favoriteGlyph)
        assertEquals(DailyContentsText.FAVORITE_ADD, non.favoriteLabel)
        assertEquals(DailyContentsText.SHARE, non.shareLabel)
    }

    @Test
    fun `l'adresse de l'illustration reste brute`() {
        // Elle demande une signature, donc une suspension : la résoudre ici obligerait le renderer
        // à ne plus être une fonction pure. C'est la fiche qui la résout.
        assertEquals(
            "https://exemple.test/i.png",
            DailyContentsListRenderer.card(contenu(image = "https://exemple.test/i.png"), false).imageUrl,
        )
        assertNull(DailyContentsListRenderer.card(contenu(image = null), false).imageUrl)
    }

    // ------------------------------------------------------------------ le texte partagé

    @Test
    fun `le texte partage joint les parties par une ligne vide`() {
        // `[title, arabic, phonetic, french, explanation, source].filter(Boolean).join('\n\n')`.
        val texte = DailyContentsListRenderer.shareText(
            contenu(
                titre = "T",
                arabe = "A",
                phonetique = "P",
                francais = "F",
                explication = "E",
                source = "S",
                reference = "R",
            ),
        )

        assertEquals("T\n\nA\n\nP\n\nF\n\nE\n\nSource : S R", texte)
    }

    @Test
    fun `le texte partage retire les parties vides`() {
        // Le filtre porte sur **les six** : un titre absent laisserait sinon une ligne vide en tête
        // du message, et une explication absente un blanc entre le français et la source.
        val texte = DailyContentsListRenderer.shareText(
            contenu(
                titre = null,
                arabe = null,
                phonetique = null,
                francais = "F",
                explication = null,
                source = "S",
                reference = null,
            ),
        )

        assertEquals("F\n\nSource : S ", texte)
    }

    @Test
    fun `la provenance du partage ne joint pas comme celle de la fiche`() {
        // `` `Source : ${item.source} ${item.reference??''}` `` : une espace, pas le point médian
        // de la fiche, et une **espace de fin** quand la référence manque. Le texte partagé sort de
        // l'application : le changer changerait ce que reçoivent les destinataires.
        val texte = DailyContentsListRenderer.shareText(
            contenu(titre = null, arabe = null, phonetique = null, francais = "F", source = "S", reference = null),
        )

        assertTrue(texte.endsWith("Source : S "), "La provenance du partage garde son espace de fin : « $texte »")
        assertFalse(texte.contains("·"), "Le partage ne joint pas par le point médian de la fiche.")
    }

    // ------------------------------------------------------------------ le retour de la fiche

    @Test
    fun `le retour de la fiche sort de l'ecran quand la fiche vient d'un identifiant`() {
        // `onPress={()=>initialId?onClose():setDetail(null)}` : une fiche ouverte **par son
        // identifiant** — carrousel, notification — est la raison d'être de l'écran, et « Retour »
        // en ressort entièrement. Une fiche ouverte depuis la liste y ramène.
        assertTrue(DailyContentsListRenderer.detailBackExits("c1"))
        assertFalse(DailyContentsListRenderer.detailBackExits(null))
    }

    // ------------------------------------------------------------------ l'erreur

    @Test
    fun `une erreur blanche n'est pas affichee`() {
        // Le message vit dans l'en-tête, où il reste visible même quand la liste porte des lignes.
        // Une chaîne d'espaces n'est pas un message : l'afficher laisserait un cadre vide.
        assertNull(DailyContentsListRenderer.header(DailyListState(error = "   ")).error)
        assertEquals("Panne", DailyContentsListRenderer.header(DailyListState(error = "Panne")).error)
    }
}
