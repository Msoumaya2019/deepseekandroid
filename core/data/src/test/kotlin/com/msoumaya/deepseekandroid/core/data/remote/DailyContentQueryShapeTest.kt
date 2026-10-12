package com.msoumaya.deepseekandroid.core.data.remote

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient, par la **source**, les règles de la requête des favoris qu'aucun test de comportement ne
 * peut voir.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * Les tests du dépôt remplacent la source par une doublure : la requête PostgREST n'est **jamais**
 * construite. Tout ce que ce fichier surveille vit donc dans du code qu'aucun banc n'exécute.
 *
 * ## Ce qui disparaîtrait sans un mot
 *
 * Trois choses, et chacune laisserait l'écran parfaitement fonctionnel en apparence :
 *
 *  - **un `is_active` ajouté au filtre des favoris** — c'est la divergence de l'original, et elle
 *    est **reproduite** : un contenu désactivé après avoir été mis en favori reste dans les
 *    favoris de qui l'avait gardé. L'ajouter ferait disparaître une entrée que la personne a
 *    explicitement gardée, et rien à l'écran ne le dirait — la liste serait simplement plus courte ;
 *  - **une taille de page recopiée** : la requête et la règle « en reste-t-il ? »
 *    ([com.msoumaya.deepseekandroid.core.domain.DailyContents.pageComplete]) divergeraient en
 *    silence, et le bouton « Afficher la suite » apparaîtrait ou disparaîtrait au mauvais moment ;
 *  - **une boucle sur les identifiants** : `isIn` écrit `id=in.(…)`, que PostgREST rend en **une**
 *    requête. Boucler donnerait trente appels pour une page, et l'ordre `created_at` ne serait plus
 *    celui du serveur — donc deux conséquences invisibles, l'une sur le réseau, l'autre sur
 *    l'ordre des cartes.
 *
 * ## La borne, et le témoin qu'elle a mordu
 *
 * Le filtre est lu sur le bloc de `favoriteContents`, **jamais sur le fichier entier** : `contents`
 * porte, lui, un `eq("is_active", true)` parfaitement légitime, et un `contains` global serait
 * satisfait par cette occurrence étrangère. La borne est donc ce qui donne son sens au contrôle, et
 * le second `check` est le témoin qu'elle a bien mordu.
 *
 * Le bloc est en outre **privé de ses commentaires** : la ligne de commentaire qui documente
 * l'absence d'`is_active` écrit le jeton `is_active` en toutes lettres, et un contrôle naïf
 * conclurait l'inverse de ce qu'il mesure. C'est le piège classique de ce genre de banc — l'ancre
 * finit par encoder ce qu'on **attendait lire**, et non ce que l'artefact **dit**.
 */
class DailyContentQueryShapeTest {

    @Test
    fun `la page de favoris ne filtre pas sur is_active`() {
        val bloc = sansCommentaires(blocFavoris(sourceSupabase()))

        // L'ancre d'abord : sans elle, le contrôle qui suit pourrait porter sur un bloc qui ne
        // construit plus la requête du tout, et ne mesurerait rien.
        assertTrue(
            bloc.contains("isIn(\"id\", ids)"),
            "L'ancre a bouge : la page de favoris ne demande plus ses identifiants par `isIn`. Ce " +
                "controle ne mesure donc plus rien, et doit etre repris avant d'etre cru.",
        )
        assertFalse(
            bloc.contains("is_active"),
            "Un `is_active` a ete ajoute au filtre des favoris. C'est la divergence de l'original, " +
                "et elle est reproduite : un contenu desactive apres avoir ete mis en favori reste " +
                "dans les favoris de qui l'avait garde. Le filtrer ferait disparaitre une entree " +
                "que la personne a explicitement gardee, sans que rien ne l'annonce.",
        )
    }

    @Test
    fun `la fenetre d'une page de favoris est lue au domaine`() {
        val bloc = sansCommentaires(blocFavoris(sourceSupabase()))

        assertTrue(
            bloc.contains("range(offset.toLong()"),
            "L'ancre a bouge : la page de favoris ne construit plus de fenetre. Ce controle ne " +
                "mesure donc plus rien, et doit etre repris avant d'etre cru.",
        )
        assertTrue(
            bloc.contains("DailyContents.PAGE_SIZE"),
            "La fenetre de la page de favoris ne lit plus la taille de page au domaine : elle la " +
                "recopie, et la requete et `pageComplete` peuvent alors diverger en silence — une " +
                "liste qui s'arrete au milieu, ou un bouton qui ne ramene jamais rien.",
        )
        assertFalse(
            bloc.contains("offset + 30"),
            "La taille de page est de nouveau ecrite en dur dans la source.",
        )
    }

    @Test
    fun `les favoris partent en une seule requete, dans l'ordre du serveur`() {
        val bloc = sansCommentaires(blocFavoris(sourceSupabase()))

        assertTrue(
            bloc.contains("order(\"created_at\", Order.DESCENDING)"),
            "L'ancre a bouge : la page de favoris n'est plus triee par le serveur. Ce controle ne " +
                "mesure donc plus rien, et doit etre repris avant d'etre cru.",
        )
        assertTrue(
            bloc.contains("isIn(\"id\", ids)"),
            "Les favoris ne sont plus demandes en un seul appel : boucler sur les identifiants " +
                "donnerait trente requetes pour une page, et l'ordre ne serait plus celui du " +
                "serveur.",
        )
        assertFalse(
            bloc.contains("forEach") || bloc.contains("for ("),
            "La page de favoris boucle de nouveau sur ses identifiants au lieu de les demander " +
                "d'un bloc.",
        )
    }

    // ------------------------------------------------------------------ outillage

    /**
     * Le bloc de `favoriteContents`, borné à la déclaration suivante.
     *
     * La borne est `override suspend fun content(` : c'est la méthode qui suit dans le fichier, et
     * c'est aussi celle qui **n'a pas** de filtre `is_active` — s'arrêter plus loin, ou pas du
     * tout, ferait entrer le filtre de `contents` dans le bloc, et le contrôle conclurait
     * l'inverse de ce qu'il mesure.
     */
    private fun blocFavoris(source: String): String {
        val ancre = "override suspend fun favoriteContents("
        val debut = source.indexOf(ancre)
        check(debut >= 0) {
            "l'ancre de `favoriteContents` est introuvable dans SupabaseDailyContentSource.kt"
        }
        val reste = source.substring(debut)
        val bloc = reste.substringBefore("\n    override suspend fun content(")
        check(bloc.length < reste.length) {
            "la borne n'a pas mordu : le bloc va jusqu'a la fin du fichier, donc le controle ne " +
                "distingue plus le filtre de `favoriteContents` de celui de `contents` — qui porte, " +
                "lui, un `is_active` legitime."
        }
        return bloc
    }

    /**
     * [texte] privé de ses commentaires de fin de ligne.
     *
     * **Sans quoi le contrôle se trompe de sens.** Le bloc de `favoriteContents` porte un
     * commentaire qui écrit `eq("is_active", true)` pour expliquer son **absence** ; un `contains`
     * le trouverait, et déclarerait présent ce que le code ne fait pas.
     *
     * Un `//` à l'intérieur d'une chaîne serait retiré à tort — aucune des chaînes de ce fichier
     * n'en porte, et la lecture est bornée à `SupabaseDailyContentSource.kt`.
     */
    private fun sansCommentaires(texte: String): String =
        texte.lines().joinToString("\n") { ligne ->
            val marque = ligne.indexOf("//")
            if (marque < 0) ligne else ligne.substring(0, marque)
        }

    /** Le source de la source Supabase, cherché depuis le module **et** depuis la racine. */
    private fun sourceSupabase(): String {
        val relatif =
            "src/main/kotlin/com/msoumaya/deepseekandroid/core/data/remote/SupabaseDailyContentSource.kt"
        val candidats = listOf(File(relatif), File("core/data/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "SupabaseDailyContentSource.kt introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }
}
