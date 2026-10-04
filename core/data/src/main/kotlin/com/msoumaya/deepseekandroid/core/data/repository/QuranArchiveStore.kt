package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.local.QuranArchiveInstaller
import com.msoumaya.deepseekandroid.core.domain.ArchiveProgress
import com.msoumaya.deepseekandroid.core.domain.PageReadiness
import com.msoumaya.deepseekandroid.core.domain.QuranSourceReady
import com.msoumaya.deepseekandroid.core.domain.QuranSourceTransition
import com.msoumaya.deepseekandroid.core.domain.ZIP_LINES_PER_PAGE
import com.msoumaya.deepseekandroid.core.model.MushafSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Le paquet « Coran 1441 » vu par l'interface : un état observable, une commande pour
 * télécharger, une commande pour s'arrêter, et la réponse à « cette page peut-elle s'afficher ? ».
 *
 * Porté depuis `src/services/quranDownload.ts` (`subscribeQuranDownload`,
 * `ensureQuranDownloaded`, `pauseQuranDownload`) et `quranSourceReady.ts`.
 *
 * Deux décisions valent d'être dites :
 *
 *  1. **La pause est une annulation, pas un échec.** L'installeur travaille dans une coroutine ;
 *     l'interrompre laisse le partiel en place, et la reprise suivante repart de sa longueur.
 *     C'est plus simple que le client d'origine — qui enregistre un `resume.json` parce que son
 *     gestionnaire de fichiers garde les octets ailleurs — et cela ne peut pas se
 *     désynchroniser. Le magasin ne publie donc **pas** d'erreur sur une pause.
 *  2. **L'état d'installation est mis en cache.** `installer.isInstalled()` lit le témoin sur le
 *     disque. Le lecteur demande « cette page peut-elle s'afficher ? » à chaque recomposition :
 *     une lecture de fichier par image affichée serait un défaut invisible en test et bien réel
 *     sur un appareil.
 *
 * @param scope la portée qui porte le travail. Elle doit vivre aussi longtemps que l'écran qui
 *   l'observe : une installation ne survit pas à la fermeture de l'application, ce qui est le
 *   comportement voulu tant qu'aucun service d'avant-plan n'existe.
 */
class QuranArchiveStore(
    private val installer: QuranArchiveInstaller,
    private val scope: CoroutineScope,
) {

    private val _progress = MutableStateFlow(installer.progress())
    private val _downloaded = MutableStateFlow(installer.isInstalled())

    /** Avancement de l'installation, tel que l'écran doit le montrer. */
    val progress: StateFlow<ArchiveProgress> = _progress.asStateFlow()

    /** L'installation est-elle complète ? */
    val downloaded: StateFlow<Boolean> = _downloaded.asStateFlow()

    private var job: Job? = null

    /**
     * Lance l'installation, ou la reprend là où elle s'était arrêtée.
     *
     * Sans effet si une installation est déjà en cours. L'installeur a son propre verrou, mais
     * s'en remettre à lui seul lancerait un second travail qui attendrait pour rien — et
     * l'écran afficherait deux fois la même progression.
     */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                installer.install { _progress.value = it }
                _downloaded.value = true
            } catch (cancelled: CancellationException) {
                // Une pause : l'état est publié par `pause()`, une fois le partiel refermé.
                throw cancelled
            } catch (_: Throwable) {
                // L'installeur a déjà publié l'erreur avant de relancer. On empêche seulement
                // l'exception de remonter à la portée, où elle ferait tomber l'application.
            }
        }
    }

    /**
     * Installe le paquet et **attend la fin**.
     *
     * C'est ce qu'il faut à un changement de source, qui ne doit adopter la source qu'une fois
     * ses images en place. Le travail est le même que celui de [start] : l'installeur a son
     * propre verrou, donc les deux ne peuvent pas télécharger en même temps, et l'appel qui
     * arrive second attend le premier au lieu de le doubler.
     */
    suspend fun ensureInstalled() {
        if (_downloaded.value) return
        installer.install { _progress.value = it }
        _downloaded.value = true
    }

    /**
     * Interrompt le téléchargement en cours.
     *
     * Le partiel reste en place : la reprise repartira de sa longueur. Un arrêt qui effacerait
     * son travail transformerait une connexion capricieuse en impossibilité définitive.
     */
    fun pause() {
        val running = job ?: return
        job = null
        // L'état est recalculé à la fin **effective** du travail, et non au moment de la
        // demande : la longueur du partiel n'est complète qu'une fois la boucle d'écriture
        // sortie, et l'annoncer plus tôt donnerait un pourcentage en retard d'un bloc. Le
        // gestionnaire est posé avant l'annulation, sinon la fin pourrait le devancer.
        running.invokeOnCompletion { _progress.value = installer.progress() }
        running.cancel()
    }

    /** Ce qu'il faut faire pour afficher [page] d'une source, disque compris. */
    fun readiness(
        source: MushafSource,
        page: Int,
        pageImageAvailable: Boolean = true,
    ): PageReadiness {
        // Les lignes ne sont lues que là où elles ont un sens : hors bornes, `missingLines`
        // lèverait une exception au lieu de rendre le refus que le domaine a prévu.
        val lines = if (QuranSourceReady.isZipSource(source) && page in 1..TOTAL_PAGES) {
            installer.missingLines(page)
        } else {
            emptyList()
        }
        return QuranSourceReady.readiness(
            source = source,
            page = page,
            downloaded = _downloaded.value,
            missingLines = lines,
            pageImageAvailable = pageImageAvailable,
        )
    }

    /** Les lignes absentes d'une page du paquet, dans l'ordre. Vide quand la page est complète. */
    fun missingLines(page: Int): List<Int> = installer.missingLines(page)

    /** Le fichier d'une ligne de page. */
    fun lineFile(page: Int, line: Int): File = installer.lineFile(page, line)

    /**
     * L'adresse d'une bande, prête à être affichée.
     *
     * L'adresse est écrite à la main plutôt que demandée à `Uri.fromFile` : cette dernière est
     * une fonction d'Android, et un test unitaire qui l'appelle obtient une coquille vide. Or
     * c'est précisément la forme de l'adresse qu'on veut vérifier — une adresse fausse ne lève
     * rien, elle affiche une page blanche, ce qui est la panne la plus silencieuse du lecteur.
     *
     * Le fichier peut ne pas exister — c'est le cas tant que le paquet n'est pas installé — et
     * l'adresse reste juste : c'est à l'écran de décider s'il a le droit d'afficher cette page,
     * et `readiness` le lui dit.
     */
    fun lineUri(page: Int, line: Int): String = fileUri(lineFile(page, line))

    /** Les adresses des quinze bandes d'une page, dans l'ordre des lignes. */
    fun pageLineUris(page: Int): List<String> =
        (1..ZIP_LINES_PER_PAGE).map { line -> lineUri(page, line) }

    /** L'état à afficher avant toute action, relu sur le disque. */
    fun refresh(): ArchiveProgress = installer.progress().also { _progress.value = it }

    companion object {
        private const val TOTAL_PAGES = QuranSourceTransition.TOTAL_PAGES

        /**
         * L'adresse `file://` d'un fichier du stockage privé.
         *
         * Le chemin est absolu et vient de `filesDir`, où aucun caractère n'a besoin d'être
         * échappé : c'est une contrainte du stockage privé d'Android, pas une supposition.
         */
        internal fun fileUri(file: File): String = "file://" + file.absolutePath
    }
}
