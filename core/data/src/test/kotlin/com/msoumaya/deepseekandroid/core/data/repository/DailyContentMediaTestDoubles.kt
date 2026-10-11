package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.remote.DailyContentMediaSource

// ---------------------------------------------------------------------------
// Doublure des tests du dépôt des médias
// ---------------------------------------------------------------------------
// **Ce qu'elle mesure, et pourquoi elle le mesure.** Elle retient les chemins qu'on lui a demandé
// de signer, et leur **nombre** : c'est ce qui rend visible la règle que le dépôt porte — une
// adresse signée est réutilisée pendant cinquante-cinq minutes, donc deux résolutions rapprochées
// de la même adresse ne produisent **qu'une** signature. Une doublure qui rendrait une valeur
// plausible sans compter ses appels ne pourrait pas l'apercevoir, et un dépôt qui resignerait à
// chaque affichage passerait pour juste.
//
// **La racine est un paramètre de construction, et non une constante.** C'est ce qui permet
// d'éprouver le cas que le conteneur doit éviter : une adresse qui appartient à un **autre**
// projet, ou à un autre compartiment, doit être rendue telle quelle — sans qu'aucune signature ne
// parte. Une doublure qui porterait la racine du projet partagé en dur ne pourrait pas le
// mesurer.
//
// **L'échec est séparé de la valeur.** [failSignedUrl] permet de refuser une signature, ce qui est
// le seul moyen de mesurer qu'un échec **remonte** et n'est pas retenu par le cache : une doublure
// qui ne saurait qu'échouer ne dirait rien de ce qui reste en mémoire après l'échec.
// ---------------------------------------------------------------------------

/** Doublure en mémoire du compartiment des médias éditoriaux. */
internal class FakeMediaSource(
    override val root: String,
) : DailyContentMediaSource {

    /** Les chemins demandés, dans l'ordre, **avec leurs répétitions**. */
    val pathsAsked = mutableListOf<String>()

    /** Ce que la signature rendra. Le chemin demandé y est ajouté, pour que la valeur soit traçable. */
    var signedPrefix: String = "https://signe.test/"

    /** Refuse la signature quand elle est posée. */
    var failSignedUrl: Throwable? = null

    /** Appelé au début de la signature : permet de suspendre ou de photographier. */
    var probe: (() -> Unit)? = null

    /** Le nombre de signatures demandées — l'assertion la plus fréquente de ce fichier. */
    val signatures: Int get() = pathsAsked.size

    override suspend fun signedUrl(path: String): String {
        pathsAsked += path
        probe?.invoke()
        failSignedUrl?.let { throw it }
        return signedPrefix + path
    }
}
