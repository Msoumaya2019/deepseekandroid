package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.remote.DailyContentFavoriteSource
import kotlinx.coroutines.CompletableDeferred

// ---------------------------------------------------------------------------
// Doublure des tests du dépôt des favoris
// ---------------------------------------------------------------------------
// **Ce qu'elle mesure, et pourquoi elle le mesure.** Elle retient la **liste de ses lectures** —
// leur nombre, et le compte qu'elles visaient —, et c'est la seule façon d'apercevoir les trois
// règles du dépôt : la fenêtre de trente secondes (deux lectures rapprochées n'en font qu'une), le
// dédoublonnage des lectures concurrentes (deux appelants n'en font qu'une), et le partage par
// compte (deux comptes ne se servent pas la même liste). Une doublure qui rendrait des valeurs
// plausibles sans compter ne distinguerait rien de tout cela.
//
// **Le serveur est un état, pas une constante.** [lists] est indexé par compte et **modifié** par
// les deux gestes, comme le serait la table : c'est ce qui permet de mesurer qu'un ajout est suivi
// d'une lecture qui le voit, et qu'un retrait retire vraiment.
//
// **Les trois refus sont séparés** — [failList], [failWrite], et le silence. Un échec de lecture et
// un échec d'écriture n'ont pas les mêmes conséquences : le premier laisse la liste inconnue, le
// second laisse la liste **connue et inchangée**, et le dépôt doit se comporter différemment dans
// les deux cas. Une doublure qui refuserait tout d'un bloc ne les distinguerait pas.
//
// **Deux crochets.** [probe] s'exécute **pendant** une lecture, ce qui permet de tenir une lecture
// en vol et d'en lancer une seconde — c'est la seule façon de mesurer le dédoublonnage autrement
// qu'en l'affirmant. [gate] suspend la lecture, pour la même raison.
// ---------------------------------------------------------------------------

/** Doublure en mémoire de la table `content_favorites`. */
internal class FakeFavoriteSource : DailyContentFavoriteSource {

    /** Ce que le serveur contient, par compte. Les deux gestes le modifient. */
    val lists = mutableMapOf<String, Set<String>>()

    /** Refuse la lecture quand il est posé. */
    var failList: Throwable? = null

    /** Refuse **l'ajout et le retrait**. Séparé de [failList] : les deux n'ont pas le même effet. */
    var failWrite: Throwable? = null

    /** Le nombre de lectures. L'assertion la plus fréquente de ce fichier. */
    var listCalls = 0

    /** Les ajouts reçus, dans l'ordre : `(compte, contenu)`. */
    val added = mutableListOf<Pair<String, String>>()

    /** Les retraits reçus, dans l'ordre : `(compte, contenu)`. */
    val removed = mutableListOf<Pair<String, String>>()

    /** Appelé **pendant** la lecture, avant que [gate] ne soit franchie. */
    var probe: (() -> Unit)? = null

    /** Quand elle est posée, chaque lecture attend qu'elle soit franchie. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun list(userId: String): Set<String> {
        listCalls++
        probe?.invoke()
        gate?.await()
        failList?.let { throw it }
        return lists[userId] ?: emptySet()
    }

    override suspend fun add(userId: String, contentId: String) {
        failWrite?.let { throw it }
        added += userId to contentId
        lists[userId] = (lists[userId] ?: emptySet()) + contentId
    }

    override suspend fun remove(userId: String, contentId: String) {
        failWrite?.let { throw it }
        removed += userId to contentId
        lists[userId] = (lists[userId] ?: emptySet()) - contentId
    }
}
