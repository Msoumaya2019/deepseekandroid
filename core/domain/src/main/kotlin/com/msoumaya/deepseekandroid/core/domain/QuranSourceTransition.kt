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
     * @return `true` si le changement a été validé, `false` s'il a été abandonné (transition
     *   déjà en cours, composant détruit, ou page invalide).
     */
    suspend fun change(
        source: String,
        page: Int,
        prepare: suspend (String, Int) -> Unit,
        commit: (String, Int) -> Unit,
    ): Boolean {
        if (disposed) return false
        require(page in 1..TOTAL_PAGES) { "Page du Coran invalide." }
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
