package com.msoumaya.deepseekandroid.core.data.remote

import com.msoumaya.deepseekandroid.core.model.AppJson
import com.msoumaya.deepseekandroid.core.model.DailyContentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * La forme des lignes lues, mesurée sur de **vrais documents JSON**.
 *
 * ## Pourquoi ce fichier, alors que le dépôt est éprouvé contre une doublure
 *
 * La doublure rend des `DailyContent` déjà construits : elle ne dit donc **rien** de la
 * correspondance entre les colonnes de la table et le modèle. Or c'est là que se cache le défaut
 * muet de ce portage : le client Postgrest est configuré **sans conversion de propriété**, donc un
 * `@SerialName` oublié — ou écrit `frenchText` là où la colonne s'appelle `french_text` — ne lève
 * rien. `AppJson` porte `ignoreUnknownKeys`, la clé inconnue est simplement ignorée, et le champ
 * tombe sur sa valeur par défaut ; pour `frenchText`, qui n'en a pas, la désérialisation lèverait
 * — mais pour `arabicText`, qui en a une, une invocation s'afficherait **sans arabe** et personne
 * ne saurait pourquoi. C'est exactement le défaut que `RecitationSourceTest` a été écrit pour
 * attraper, et la raison est la même ici.
 *
 * ## Ce qui est mesuré
 *
 *  - les sept colonnes en `snake_case` que le modèle renomme (`arabic_text`, `phonetic_text`,
 *    `french_text`, `category_id`, `audio_url`, `image_url`, `is_active`) ;
 *  - les deux de `content_categories` (`display_order`, `is_active`) ;
 *  - les deux colonnes **non demandées** par la requête (`created_at`, `updated_at`) : elles ne
 *    doivent pas casser la lecture, sans quoi une ligne écrite par l'administration web rendrait
 *    l'écran illisible ;
 *  - les valeurs par défaut, qui couvrent une ligne écrite **avant** l'ajout d'une colonne.
 */
class DailyContentSourceShapeTest {

    @Test
    fun `une ligne de daily_contents se lit dans le modele`() {
        // Le document est celui que PostgREST rend pour `select` sur la table : les noms de
        // colonnes, `snake_case` compris, et deux colonnes que la requete ne demande pas.
        val json = """
            {
              "id": "c-1",
              "type": "invocation",
              "title": "Invocation du matin",
              "arabic_text": "سبحان الله",
              "phonetic_text": "Subhan Allah",
              "french_text": "Gloire à Dieu.",
              "explanation": "Une explication.",
              "source": "Recueil",
              "reference": "1:1",
              "category_id": "cat-1",
              "audio_url": "https://exemple.test/a.mp3",
              "image_url": "https://exemple.test/i.png",
              "is_active": true,
              "created_at": "2026-10-01T08:00:00Z",
              "updated_at": "2026-10-02T09:00:00Z",
              "created_by": "00000000-0000-0000-0000-000000000000"
            }
        """.trimIndent()

        val contenu = AppJson.decodeFromString<DailyContentRow>(json).toModel()

        assertEquals("c-1", contenu.id)
        assertEquals(DailyContentType.INVOCATION, contenu.type)
        assertEquals("Invocation du matin", contenu.title)
        assertEquals("سبحان الله", contenu.arabicText)
        assertEquals("Subhan Allah", contenu.phoneticText)
        assertEquals("Gloire à Dieu.", contenu.frenchText)
        assertEquals("Une explication.", contenu.explanation)
        assertEquals("Recueil", contenu.source)
        assertEquals("1:1", contenu.reference)
        assertEquals("cat-1", contenu.categoryId)
        assertEquals("https://exemple.test/a.mp3", contenu.audioUrl)
        assertEquals("https://exemple.test/i.png", contenu.imageUrl)
        assertEquals(true, contenu.isActive)
    }

    @Test
    fun `une ligne ancienne sans les colonnes recentes reste lisible`() {
        // `is_active` et `type` portent une valeur par defaut : une ligne ecrite avant leur ajout
        // ne doit pas faire echouer la lecture, sans quoi elle disparaitrait de la liste sans que
        // rien ne le dise. Les champs nullables tombent a `null`, ce qui est leur sens.
        val json = """
            {
              "id": "c-2",
              "french_text": "Un rappel.",
              "source": "Une source",
              "category_id": "cat-2"
            }
        """.trimIndent()

        val contenu = AppJson.decodeFromString<DailyContentRow>(json).toModel()

        assertEquals(DailyContentType.REMINDER, contenu.type)
        assertEquals(true, contenu.isActive)
        assertNull(contenu.title)
        assertNull(contenu.arabicText)
        assertNull(contenu.phoneticText)
        assertNull(contenu.audioUrl)
        assertNull(contenu.imageUrl)
    }

    @Test
    fun `une ligne de content_categories se lit dans le modele`() {
        val json = """
            {
              "id": "cat-1",
              "name": "Prière",
              "icon": "☾",
              "type": "invocation",
              "display_order": 3,
              "is_active": true,
              "created_at": "2026-10-01T08:00:00Z"
            }
        """.trimIndent()

        val categorie = AppJson.decodeFromString<ContentCategoryRow>(json).toModel()

        assertEquals("cat-1", categorie.id)
        assertEquals("Prière", categorie.name)
        assertEquals("☾", categorie.icon)
        assertEquals(DailyContentType.INVOCATION, categorie.type)
        assertEquals(3, categorie.displayOrder)
        assertEquals(true, categorie.isActive)
    }

    @Test
    fun `une categorie ancienne sans les colonnes recentes reste lisible`() {
        val json = """
            {
              "id": "cat-2",
              "name": "Rappels",
              "icon": "☀",
              "type": "reminder"
            }
        """.trimIndent()

        val categorie = AppJson.decodeFromString<ContentCategoryRow>(json).toModel()

        assertEquals(0, categorie.displayOrder)
        assertEquals(true, categorie.isActive)
    }
}
