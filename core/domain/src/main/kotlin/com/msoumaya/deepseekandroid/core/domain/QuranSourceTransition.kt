package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppState
import kotlinx.coroutines.sync.Mutex

/**
 * Changement de source coranique.
 *
 * Porté depuis `src/core/quranSourceTransition.ts`. Une seule transition à la fois, et le
 * changement n'est **validé qu'après** préparation des ressources : si le téléchargement
 * échoue ou si l'écran est quitté entre-temps, la source affichée reste l'ancienne et le
 * lecteur ne se retrouve jamais sur une page sans image.
 */
class QuranSourceTransition {

    private val mutex = Mutex()

    @Volatile
    private var disposed = false

    /**
     * Prépare puis valide un changement de source.
     *
     * Générique sur le type de la source, comme l'original TypeScript l'était : la transition
     * ne fait que **transporter** la valeur de `prepare` vers `commit`. La typer en chaîne
     * aurait obligé les appelants à convertir une énumération en texte pour la reconvertir
     * aussitôt — deux endroits de plus où se tromper, pour aucun gain.
     *
     * ## Pourquoi `commit` suspend
     *
     * Dans le client d'origine, `commit` est synchrone : il appelle `update()`, qui réécrit
     * l'état en mémoire et le programme en écriture. Ici, valider la source passe par
     * `UserRepository.mutate`, qui **écrit sur le disque** — donc suspend. Typer `commit` en
     * `(S, Int) -> Unit` obligeait l'appelant à lancer une coroutine depuis un rappel non
     * suspendu, c'est-à-dire à rendre la validation asynchrone par rapport à la transition :
     * `change` aurait rendu `true` avant que l'état soit écrit, et un échec d'écriture serait
     * passé inaperçu.
     *
     * La validation reste donc **dans** le verrou, et c'est voulu : deux transitions ne doivent
     * pas pouvoir s'entrelacer entre la préparation et l'écriture.
     *
     * @return `true` si le changement a été validé, `false` s'il a été abandonné (transition
     *   déjà en cours, ou composant détruit). Une exception de `prepare` ou de `commit` remonte
     *   à l'appelant : rien n'a été validé.
     */
    suspend fun <S> change(
        source: S,
        page: Int,
        prepare: suspend (S, Int) -> Unit,
        commit: suspend (S, Int) -> Unit,
    ): Boolean {
        if (disposed) return false
        require(page in 1..TOTAL_PAGES) { "Page du Coran invalide." }
        // `tryLock` et non `lock` : une seconde demande pendant une transition ne doit pas
        // **attendre** puis s'appliquer, elle doit être refusée. Deux préparations concurrentes
        // se disputeraient les mêmes fichiers, et la seconde écraserait la première.
        if (!mutex.tryLock()) return false
        try {
            prepare(source, page)
            if (disposed) return false
            commit(source, page)
            return true
        } finally {
            mutex.unlock()
        }
    }

    fun dispose() {
        disposed = true
    }

    companion object {
        /** Nombre de pages du moushaf. Identique dans les deux clients. */
        const val TOTAL_PAGES: Int = 604
    }
}

/** Porté depuis `src/core/offlineAccess.ts`. */
object OfflineAccess {
    /**
     * Premier écran à présenter : l'état du compte s'il existe, l'écran de compte sinon.
     *
     * Une session déjà ouverte ne redemande jamais de se connecter, même sans réseau.
     */
    fun initialAccountAccess(state: AppState): String = if (state.userId != null) "done" else "show"
}
