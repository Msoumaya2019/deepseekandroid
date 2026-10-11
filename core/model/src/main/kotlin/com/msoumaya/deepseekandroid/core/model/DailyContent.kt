package com.msoumaya.deepseekandroid.core.model

import kotlinx.serialization.Serializable

/**
 * Modèle des contenus du jour.
 *
 * Porté depuis `src/services/dailyContents.ts`. Les rappels et les invocations vivent côté
 * serveur (`content_categories`, `daily_contents`, `daily_content_schedule`) ; ce modèle ne
 * décrit que ce que le client lit, met en cache et affiche.
 *
 * **Aucun contenu religieux n'est embarqué, et ce n'est pas un oubli.** Le client d'origine le
 * dit dans `DAILY_CONTENTS.md` : « Aucun contenu religieux n'a été prérempli : la maquette n'est
 * pas une source religieuse. » Le portage suit la même règle — les entrées arrivent du serveur,
 * ou il n'y en a pas —, et c'est aussi ce qui rend le repli hors ligne honnête : un contenu que
 * l'appareil n'a jamais lu ne peut pas être inventé.
 *
 * **Une seule forme est écrite ici.** La table rend ses colonnes en `snake_case`, et c'est la
 * couche de données qui porte cette correspondance (`DailyContentRow`, dans `core:data`) ; le
 * modèle, lui, n'a **aucun** `@SerialName` — même partage que `RemoteRecitation` et
 * `QuizSnapshot`, et pour la même raison : deux tables de noms qui ne peuvent pas se vérifier
 * l'une l'autre finiraient par diverger en silence, et c'est la lecture qui casserait.
 *
 * **Ce que ce modèle ne porte pas.** `created_at` et `updated_at` existent en base et la requête
 * les reçoit (`select *`), mais aucun écran ne les affiche : `AppJson` ignore les clés inconnues,
 * donc les omettre ne coûte rien et laisse moins de surface à relire. `category_name` est absent
 * pour une raison plus forte : la colonne **n'existe pas** dans `daily_contents` — elle est
 * composée par l'administration, qui reste sur le web.
 */

/**
 * Une catégorie de contenus.
 *
 * Le serveur garantit qu'une catégorie et les contenus qu'elle porte ont **le même type**
 * (`unique(id,type)` et une clé étrangère sur le couple), donc filtrer les catégories par type
 * suffit à filtrer les contenus.
 */
@Serializable
data class ContentCategory(
    val id: String,
    val name: String,
    /** Pictogramme de la pastille. La colonne vaut `☾` par défaut. */
    val icon: String,
    val type: DailyContentType,
    /**
     * Ordre d'affichage voulu par l'administration.
     *
     * Il est lu et non recalculé : c'est le serveur qui trie (`order('display_order')` puis
     * `order('name')`), et retrier ici donnerait deux vérités pour une seule règle. Le champ est
     * donc porté pour que l'écran puisse s'y référer, pas pour décider à la place du serveur.
     */
    val displayOrder: Int = 0,
    val isActive: Boolean = true,
)

/**
 * Un contenu du jour : un rappel, ou une invocation.
 *
 * **La nullabilité suit les contraintes de la migration, elle ne les rejoue pas.** `french_text`
 * et `source` sont non nuls parce que `daily_contents` exige `length(btrim(…))>0` ; `arabic_text`
 * et `phonetic_text` sont nullables parce que la contrainte ne les exige que pour une
 * **invocation** (`check(type='reminder' or …)`). Un rappel sans arabe est donc valide en base,
 * et le modèle le dit au lieu de mentir sur une ligne que le serveur accepterait.
 */
@Serializable
data class DailyContent(
    val id: String,
    val type: DailyContentType,
    /** Titre affiché, ou `null` — l'écran se replie alors sur le nom du type. */
    val title: String? = null,
    val arabicText: String? = null,
    val phoneticText: String? = null,
    /** Toujours renseigné, et jamais vide : la migration l'impose. */
    val frenchText: String,
    val explanation: String? = null,
    /** Provenance éditoriale, toujours renseignée et toujours affichée. */
    val source: String,
    /** Référence précise — sourate, verset, recueil —, ou `null`. */
    val reference: String? = null,
    val categoryId: String,
    /** Adresse `https://` d'une écoute, ou `null`. La migration interdit tout autre schéma. */
    val audioUrl: String? = null,
    /** Adresse `https://` d'une vignette, ou `null`. */
    val imageUrl: String? = null,
    val isActive: Boolean = true,
)

/**
 * Les contenus du jour, tels que l'appareil les a lus.
 *
 * **Le jour est porté par le document, et non par le nom du fichier.** C'est le même choix que
 * celui de l'instantané du Quiz, et il a la même raison d'être : le serveur choisit **une entrée
 * par type et par date** (`daily_content_for_date`), donc un document qui ne dit pas sa date ne
 * permettrait pas de distinguer « le contenu du jour » de « le contenu d'hier resté sur le
 * disque ». Un fichier par date aurait fait disparaître cette comparaison, et avec elle la règle
 * « un contenu d'hier ne vaut pas pour aujourd'hui » — celle qui évite d'afficher un rappel périmé
 * comme s'il était celui du jour.
 *
 * [entries] en porte donc **au plus deux**, et l'ordre n'est pas garanti par le serveur : la
 * fonction les compose par un `values('reminder'),('invocation')`, mais rien dans son contrat ne
 * promet cet ordre au client. C'est la règle de lecture qui l'établit.
 */
@Serializable
data class DailyContentsSnapshot(
    val day: String,
    val entries: List<DailyContent> = emptyList(),
)
