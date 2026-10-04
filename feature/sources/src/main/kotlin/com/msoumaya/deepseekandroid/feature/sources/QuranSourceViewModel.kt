package com.msoumaya.deepseekandroid.feature.sources

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.repository.QuranArchiveStore
import com.msoumaya.deepseekandroid.core.data.repository.UserRepository
import com.msoumaya.deepseekandroid.core.domain.ArchiveProgress
import com.msoumaya.deepseekandroid.core.domain.PageReadiness
import com.msoumaya.deepseekandroid.core.domain.QuranDownloadText
import com.msoumaya.deepseekandroid.core.domain.QuranSourceReady
import com.msoumaya.deepseekandroid.core.domain.QuranSourceTransition
import com.msoumaya.deepseekandroid.core.model.MushafSource
import com.msoumaya.deepseekandroid.core.model.ReaderPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * L'état de la présentation du Coran, et les deux actions qui la changent : télécharger le
 * paquet, ou adopter une autre source.
 *
 * ## La règle qui compte
 *
 * **Une source n'est adoptée qu'une fois ses images en place.** C'est `QuranSourceTransition`
 * qui la tient : elle prépare, puis valide. Adopter d'abord et télécharger ensuite afficherait
 * une page blanche — et personne ne saurait dire s'il s'agit d'un défaut d'affichage ou d'un
 * téléchargement en cours. C'est aussi la raison pour laquelle la source retenue n'est pas
 * écrite dans l'état tant que la préparation n'a pas abouti : un état qui porterait une source
 * inutilisable serait repris au prochain démarrage.
 *
 * ## Ce que l'écran voit
 *
 * L'avancement et l'état d'installation viennent du magasin, qui les publie ; ce modèle ne les
 * recopie pas. Il ajoute la source courante — lue dans l'état applicatif, donc la même que
 * celle des autres écrans et du serveur — et le message d'un refus.
 */
class QuranSourceViewModel(
    private val repository: UserRepository,
    private val archive: QuranArchiveStore,
) : ViewModel() {

    private val transition = QuranSourceTransition()

    private val _source = MutableStateFlow(MushafSource.CORAN_TEST)
    private val _failure = MutableStateFlow<String?>(null)
    private val _switching = MutableStateFlow(false)

    /** Avancement de l'installation du paquet, tel que le panneau doit le montrer. */
    val progress: StateFlow<ArchiveProgress> = archive.progress

    /** L'installation est-elle complète ? */
    val downloaded: StateFlow<Boolean> = archive.downloaded

    /** La source actuellement retenue. */
    val source: StateFlow<MushafSource> = _source.asStateFlow()

    /** Pourquoi le dernier changement de source a été refusé, ou `null`. */
    val failure: StateFlow<String?> = _failure.asStateFlow()

    /**
     * Un changement de source est-il en cours ?
     *
     * Une préparation peut durer — installer le paquet fait 102 Mo — et un écran qui ne dit
     * rien pendant ce temps laisse croire que l'appui n'a pas été pris en compte. Le client
     * d'origine annonce « Chargement du Coran… » pendant toute la transition (`App.tsx:496`).
     */
    val switching: StateFlow<Boolean> = _switching.asStateFlow()

    init {
        // La source est **lue** de l'état applicatif et non tenue ici : c'est le même réglage
        // que celui du programme, de la progression et du serveur, et deux copies finiraient
        // par diverger — l'écran affichant une source que l'avancement ne connaîtrait pas.
        viewModelScope.launch {
            repository.state.collect { state ->
                _source.value = state.reader?.mushaf ?: MushafSource.CORAN_TEST
            }
        }
    }

    /** Commence ou reprend l'installation. */
    fun startDownload() {
        _failure.value = null
        archive.start()
    }

    /** Interrompt l'installation ; le partiel reste en place et la reprise repartira de là. */
    fun pauseDownload() = archive.pause()

    /** Efface le dernier refus affiché. */
    fun clearFailure() {
        _failure.value = null
    }

    /** Ce qu'il faut faire pour afficher [page] d'une source. */
    fun readiness(source: MushafSource = _source.value, page: Int): PageReadiness =
        archive.readiness(source, page)

    /**
     * Adopte [source], après avoir vérifié que sa page [page] peut s'afficher.
     *
     * Le paquet est installé **ici et attendu** s'il ne l'est pas : la transition ne valide
     * qu'ensuite. C'est le même travail que celui du panneau — l'installeur a son verrou, donc
     * les deux ne se doublent pas — mais il est visible dans le panneau, qui montre l'avancement
     * et permet de s'arrêter.
     *
     * Sans effet si la source est déjà celle demandée : réécrire le même état ferait avancer
     * `updatedAt` et déclencherait une synchronisation pour rien.
     */
    fun select(source: MushafSource, page: Int) {
        if (source == _source.value) return
        // Une seule transition à la fois, comme le client d'origine (`App.tsx:449`) : deux
        // appuis rapprochés ne doivent pas lancer deux préparations concurrentes. Le verrou de
        // la transition refuserait la seconde de toute façon, mais elle aurait déjà été annoncée
        // à l'écran — et le second appui est presque toujours un doublon du premier.
        if (_switching.value) return
        _failure.value = null
        _switching.value = true
        viewModelScope.launch {
            try {
                // Un `false` signifie que la transition a été abandonnée — écran détruit, ou
                // verrou tenu par une autre. Il n'y a alors personne à qui expliquer un échec,
                // et le remettre en cause n'aurait pas de sens : on ne fait rien.
                transition.change(
                    source = source,
                    page = page,
                    prepare = { chosen, at -> ensurePage(chosen, at) },
                    commit = { chosen, _ -> write(chosen) },
                )
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                // Le message affiché est **fixe**, celui du client d'origine ; la cause réelle
                // part dans le journal. Un message technique — « les images de cette page sont
                // manquantes » — décrirait un défaut de fichiers là où il n'y a qu'un geste à
                // refaire. Voir `QuranDownloadText.SWITCH_FAILED`.
                Log.w(TAG, "changement de source refusé : $source, page $page", cause)
                _failure.value = QuranDownloadText.SWITCH_FAILED
            } finally {
                _switching.value = false
            }
        }
    }

    override fun onCleared() {
        // La transition est abandonnée **avant** la pause : une préparation en cours doit
        // cesser de préparer, et l'installation doit s'arrêter sur un partiel propre plutôt
        // que d'être tuée au milieu d'un fichier.
        transition.dispose()
        archive.pause()
    }

    /**
     * Vérifie que la page demandée pourra s'afficher.
     *
     * L'installation est attendue pour une source en paquet ; ensuite, ce qui reste à vérifier
     * est la **page** : le paquet peut être installé et une ligne manquer, ce qu'un contrôle de
     * l'installation seule ne verrait pas.
     */
    private suspend fun ensurePage(source: MushafSource, page: Int) {
        if (QuranSourceReady.isZipSource(source)) archive.ensureInstalled()

        // Le message de refus est celui de la règle, pas une copie : deux `when` qui décident la
        // même chose finissent par diverger, et c'est celui-ci qu'on relirait en dernier.
        val refusal = QuranSourceReady.refusalMessage(archive.readiness(source, page))
        if (refusal != null) throw IllegalStateException(refusal)
    }

    private suspend fun write(source: MushafSource) {
        repository.mutate { state ->
            val reader = state.reader ?: ReaderPreferences()
            // `followAudio` est conservé tel quel : changer de présentation ne doit pas
            // changer le suivi de la récitation, qui est un autre réglage.
            state.copy(reader = reader.copy(mushaf = source))
        }
    }

    companion object {
        /** Nom du journal, celui du client d'origine (`[QuranSwitch]`). */
        private const val TAG = "QuranSwitch"

        /** Fabrique rattachée au conteneur, pour que l'écran n'ait pas à le connaître. */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return QuranSourceViewModel(container.userState, container.archive) as T
                }
            }
    }
}
