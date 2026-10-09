package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.local.ProblemReportDraft
import com.msoumaya.deepseekandroid.core.data.local.ProblemReportStore
import com.msoumaya.deepseekandroid.core.data.local.QueuedProblemReport
import com.msoumaya.deepseekandroid.core.data.remote.OwnerStore
import com.msoumaya.deepseekandroid.core.data.remote.ProblemReportSender
import com.msoumaya.deepseekandroid.core.data.remote.restMessage
import com.msoumaya.deepseekandroid.core.data.remote.restStatusCode
import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.ProblemReportAttachmentProblem
import com.msoumaya.deepseekandroid.core.domain.ProblemReportOutcome
import com.msoumaya.deepseekandroid.core.domain.ProblemReportText
import com.msoumaya.deepseekandroid.core.domain.ProblemReports
import com.msoumaya.deepseekandroid.core.model.ProblemReportType
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// ---------------------------------------------------------------------------
// Dépôt « Signalements »
// ---------------------------------------------------------------------------
// Portage de `src/services/problemReports.ts` : écrire un signalement dans la boîte d'envoi,
// **tenter** de le déposer, et vider la boîte dès qu'une occasion se présente.
//
// **Ce fichier ne décide d'aucune règle.** Les bornes, la forme de l'adresse d'une capture, les
// deux tolérances et la façon de lire l'issue sont dans `core:domain/ProblemReports.kt` ; ici il
// n'y a que l'ordonnancement, le sort des erreurs et le passage hors ligne.
//
// ## L'issue se lit sur la file, et non sur la tentative
//
// C'est la règle centrale de l'original, et elle est portée telle quelle : après avoir tenté,
// [send] **relit la file** et annonce [ProblemReportOutcome.SENT] si l'entrée a disparu,
// [ProblemReportOutcome.QUEUED] sinon. Un dépôt qui échoue, un dépôt hors connexion et un dépôt
// qui réussit mais dont la confirmation manque mènent tous au même état observable — l'entrée est
// encore là —, et c'est celui-là qu'il faut annoncer.
//
// **Une divergence nommée, et elle ne fait que rendre le message plus vrai.** L'original écrit
// `catch{return 'queued'}` autour de sa tentative : si la file contient **deux** signalements et
// que le second échoue, le premier a pourtant été déposé, et la personne lit « enregistré, il
// sera envoyé » à propos d'un signalement déjà arrivé. Ici, l'échec de la passe est absorbé et
// l'issue est lue sur la file : le premier est annoncé envoyé. Le résultat est identique dans tous
// les cas où l'original dit la vérité, et meilleur dans le seul où il se trompe.
//
// ## La passe s'arrête au premier échec, comme dans l'original
//
// C'est une **différence assumée** avec la file des récitations, qui marque l'élément en échec et
// passe au suivant. L'original des signalements, lui, n'a pas de `try` dans sa boucle : une erreur
// en sort, et l'entrée reste en place. Reproduire ce comportement est acceptable ici pour trois
// raisons mesurables :
//
//   1. la boîte est **minuscule** — un signalement est un geste rare, et elle est vidée depuis la
//      tête à chaque occasion ;
//   2. une capture vit dans `filesDir`, que le système **ne récupère pas** — contrairement à un
//      dossier de cache —, donc une entrée qui échoue pour toujours n'est pas atteignable par
//      l'usage normal ;
//   3. une panne réseau est **transitoire**, et la tête de la file est précisément ce qu'il faut
//      réessayer en premier.
//
// La file des récitations a dû diverger pour la raison inverse : elle peut porter dix
// enregistrements, et un échec du premier ne doit pas retenir les neuf autres.
//
// ## Ce qui protège d'un double envoi
//
// La garde [guard] est un `Mutex`, et un second appel **attend** le premier au lieu de rendre la
// main. C'est le comportement de l'original, qui partage une promesse en vol (`if(flight)return
// flight`) : deux envois simultanés — un geste et le retour du réseau — sérialisent, et le second
// ne repart pas de la même tête. C'est l'inverse du choix fait pour les récitations, dont la garde
// **rend la main** : là-bas, l'appelant est un déclencheur de fond pour qui attendre n'a pas de
// sens ; ici, l'appelant est une personne qui attend une réponse, et l'issue qu'on lui annonce
// doit être lue après la passe, pas pendant.
// ---------------------------------------------------------------------------

/**
 * Une capture choisie par la personne, **avant** d'être copiée.
 *
 * Deux champs, et pas trois : l'original porte aussi l'extension, qu'il calcule du type MIME par
 * la même expression que [ProblemReports.extensionFor]. La porter ici serait une seconde source
 * d'un même fait — une extension qui contredirait le type MIME produirait un fichier que le
 * compartiment refuserait, et le refus n'arriverait qu'au dépôt.
 *
 * @param path chemin du fichier rendu par le sélecteur d'images.
 * @param mime type MIME décidé par [ProblemReports.resolveMime] au moment du choix.
 */
data class ProblemReportAttachment(
    val path: String,
    val mime: String,
)

/**
 * Ce que l'écran de signalement a besoin de savoir, et rien de plus.
 *
 * @param busy vrai pendant un envoi. C'est ce qui empêche un double appui, et ce qui fait dire
 *   « Envoi… » au bouton.
 * @param done vrai quand le dernier envoi a **abouti à une issue** — envoyé ou gardé. C'est le
 *   `done` de l'original, et il ne dit pas *laquelle* : c'est [notice] qui la porte, et un booléen
 *   de plus ici serait une seconde façon de dire la même chose.
 * @param notice le message à afficher, ou `null`. Il est effacé au **début** d'un geste.
 */
data class ProblemReportState(
    val busy: Boolean = false,
    val done: Boolean = false,
    val notice: String? = null,
)

/**
 * Écrit les signalements de l'appareil, les dépose, et vide la boîte d'envoi.
 *
 * @param store la boîte d'envoi locale et ses captures.
 * @param sender accès au serveur, ou `null` si aucun projet Supabase n'est configuré. Le `null`
 *   n'est pas une panne : c'est le mode hors ligne, et il doit se lire comme « pas de compte »,
 *   jamais comme une erreur réseau. L'original lève dans ce cas (« Connexion au serveur
 *   indisponible. ») ; ici, la file garde le signalement et le dit.
 * @param session propriétaire courant. Il est lu à chaque passe : un signalement écrit sous un
 *   compte ne doit pas partir sous le jeton d'un autre.
 * @param scope portée du vidage périodique. Elle vit aussi longtemps que l'application.
 * @param appVersion la version de l'application, telle qu'elle part au serveur. Elle vient de
 *   `:app`, qui seul connaît son paquet.
 * @param platform la plateforme déclarée. Elle vaut `android` pour ce client, et elle est reçue
 *   plutôt qu'écrite en dur pour qu'un test puisse la confronter au schéma.
 * @param horsLigne la lecture du réseau, injectée. C'est **la même** que celle du bandeau — voir
 *   [ProblemReports.shouldAttempt] —, et la passer garde le dépôt éprouvable sans appareil.
 * @param nowIso horloge, injectable pour que l'instant du signalement soit éprouvable.
 */
class ProblemReportRepository(
    private val store: ProblemReportStore,
    private val sender: ProblemReportSender?,
    private val session: OwnerStore,
    private val scope: CoroutineScope,
    private val appVersion: String,
    private val platform: String = ProblemReports.PLATFORM_ANDROID,
    private val horsLigne: () -> Boolean = { false },
    private val nowIso: () -> String = { Dates.nowIso() },
) {

    /**
     * Sérialise les passes.
     *
     * Sans elle, deux passes concurrentes — le retour du réseau et la fin d'un envoi — liraient
     * la même tête de file et déposeraient deux fois les mêmes octets. La boucle suppose en effet
     * qu'elle est seule à retirer les entrées.
     */
    private val guard = Mutex()

    private val _state = MutableStateFlow(ProblemReportState())

    /** État affichable. */
    val state: StateFlow<ProblemReportState> = _state.asStateFlow()

    /**
     * Efface ce que le dernier geste a laissé à l'écran.
     *
     * L'écran de signalement est **transitoire** : il est monté à l'ouverture et démonté à la
     * fermeture. L'original porte son `done` et sa `notice` dans un `useState` de la feuille, qui
     * renaît donc vide à chaque ouverture. Ici, ces deux valeurs vivent dans le dépôt — parce que
     * c'est lui qui les produit —, et sans cet effacement une feuille rouverte montrerait l'écran
     * de confirmation du geste **précédent** : la personne croirait son nouveau signalement déjà
     * envoyé, et n'écrirait rien.
     *
     * **Un effacement pendant un envoi est refusé.** `busy` est ce qui empêche un second appui :
     * le remettre à zéro en pleine passe rouvrirait le bouton, et deux envois partiraient. La
     * feuille, elle, ne se referme pas pendant un envoi — les deux gardes se répondent.
     */
    fun reset() {
        if (_state.value.busy) return
        _state.value = ProblemReportState()
    }

    /**
     * Lance le vidage périodique, et en tente un tout de suite.
     *
     * C'est le `setInterval(sync,30000)` de l'original, suivi de son `sync()` immédiat. Le
     * premier vidage est utile au démarrage : un signalement écrit hors connexion lors de la
     * session précédente part dès que l'application s'ouvre avec du réseau.
     *
     * **Ce que ce minuteur ne remplace pas, et qui est nommé pour ne pas être cru fait.**
     * L'original branche **quatre** déclencheurs : l'ouverture, le retour du réseau, le retour au
     * premier plan, et le minuteur. Les deux premiers sont ici — l'ouverture par cet appel, le
     * réseau par le conteneur ; le **retour au premier plan** ne l'est pas encore, parce qu'il
     * demande d'observer le cycle de vie du processus, ce que ce module ne fait pas. La
     * conséquence est bornée et connue : un signalement mis en attente pendant que l'application
     * était en arrière-plan part au plus tard à la fin du délai ci-dessous.
     */
    fun start() {
        scope.launch {
            passeDeFond()
            while (true) {
                delay(PERIOD_MS)
                passeDeFond()
            }
        }
    }

    /**
     * Tente un vidage sans attendre le résultat.
     *
     * C'est ce qu'appellent le minuteur et le retour du réseau : dans les deux cas, personne
     * n'attend, et un échec n'a rien à dire — la boîte garde l'entrée, et la prochaine occasion la
     * reprendra.
     */
    fun flushInBackground() {
        scope.launch { passeDeFond() }
    }

    /** Corps de [flushInBackground] : une passe dont l'échec est absorbé. */
    private suspend fun passeDeFond() {
        try {
            flush()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Rien à dire : l'entrée est restée dans la file, et c'est tout ce qui compte.
        }
    }

    /**
     * Écrit un signalement, tente de le déposer, et annonce ce qu'il en est.
     *
     * **L'écriture locale est le premier geste, et elle est inconditionnelle.** Elle précède toute
     * lecture du réseau et tout appel : c'est ce qui rend le geste possible sans connexion, et
     * c'est la raison d'être de la file. Un signalement perdu parce qu'il a été écrit après
     * l'appel serait le pire des défauts — la personne a fait un geste, et rien ne le garde.
     *
     * @return l'issue, ou `null` si rien n'a été écrit — compte absent, geste déjà en cours, ou
     *   signalement refusé par une règle. Voir la note de `RecitationRepository.save` : ce n'est
     *   pas un booléen, parce que l'appelant doit distinguer « envoyé » de « gardé ».
     */
    suspend fun send(
        type: ProblemReportType,
        description: String,
        attachment: ProblemReportAttachment? = null,
    ): ProblemReportOutcome? {
        val owner = session.currentOwner()
        if (owner == null) {
            _state.value = _state.value.copy(notice = ProblemReportText.NOT_SIGNED_IN)
            return null
        }
        if (_state.value.busy) return null
        _state.value = _state.value.copy(busy = true, done = false, notice = null)

        ProblemReports.descriptionProblem(description)?.let {
            _state.value = _state.value.copy(
                busy = false,
                notice = ProblemReportText.DESCRIPTION_INVALID,
            )
            return null
        }

        if (attachment != null) {
            // La taille est **relue du fichier**, et non reçue : c'est ce que fait l'original
            // (`new File(attachment.uri).size`), et une taille annoncée par l'appelant pourrait
            // contredire le fichier — le compartiment, lui, lit le fichier.
            val taille = with(File(attachment.path)) { if (isFile) length() else 0L }
            when (ProblemReports.attachmentProblem(attachment.mime, taille)) {
                ProblemReportAttachmentProblem.FORMAT_UNSUPPORTED -> {
                    _state.value = _state.value.copy(
                        busy = false,
                        notice = ProblemReportText.ATTACHMENT_FORMAT,
                    )
                    return null
                }

                ProblemReportAttachmentProblem.TOO_LARGE -> {
                    _state.value = _state.value.copy(
                        busy = false,
                        notice = ProblemReportText.ATTACHMENT_TOO_LARGE,
                    )
                    return null
                }

                null -> Unit
            }
        }

        return try {
            val entry = store.enqueue(
                ProblemReportDraft(
                    userId = owner,
                    type = type,
                    description = description,
                    appVersion = appVersion,
                    platform = platform,
                    createdAt = nowIso(),
                    attachmentPath = attachment?.path,
                    attachmentExtension = attachment?.let {
                        ProblemReports.extensionFor(it.mime)
                    },
                    attachmentMime = attachment?.mime,
                ),
            )

            if (ProblemReports.shouldAttempt(horsLigne())) {
                try {
                    flush()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // L'échec est **absorbé**, et c'est l'original : il rend `'queued'` sans rien
                    // dire. Ici, l'issue est lue sur la file juste après, et elle dit la même
                    // chose — sauf dans le cas où l'entrée a bel et bien été déposée.
                }
            }

            val issue = ProblemReports.outcome(stillQueued(owner, entry.id))
            _state.value = _state.value.copy(
                busy = false,
                done = true,
                notice = ProblemReportText.outcomeMessage(issue),
            )
            issue
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.value = _state.value.copy(
                busy = false,
                done = false,
                // `restMessage()` peut rendre `null` — une exception dont le serveur n'a pas
                // rempli le texte —, et un `notice` nul serait un **échec muet**. Le repli est la
                // phrase de l'écran de l'original.
                notice = error.restMessage() ?: ProblemReportText.SEND_FAILED,
            )
            null
        }
    }

    /** Vrai si le signalement [id] attend encore, pour le compte [owner]. */
    private suspend fun stillQueued(owner: String, id: String): Boolean =
        store.list(owner).any { it.id == id }

    /**
     * Vide la boîte d'envoi du compte ouvert, de la tête vers la queue.
     *
     * La passe **s'arrête au premier échec** : l'entrée reste en place, et les suivantes
     * attendront la prochaine occasion. Voir la note de tête pour ce que ce choix coûte et
     * pourquoi il est tenable ici.
     *
     * @return vrai si au moins une entrée a été déposée, faux si la boîte était vide ou si aucun
     *   expéditeur n'existe.
     */
    suspend fun flush(): Boolean {
        val api = sender ?: return false
        val owner = session.currentOwner() ?: return false
        return guard.withLock {
            var depose = false
            while (true) {
                // Un compte peut changer pendant une passe : le vérifier entre deux entrées évite
                // de pousser les signalements d'un compte sous le jeton d'un autre. L'original le
                // vérifie deux fois par entrée — avant le dépôt du fichier, et avant l'écriture de
                // la ligne —, et les deux sont ici : celle-ci, et celle de [push].
                if (session.currentOwner() != owner) break
                val entry = store.first(owner) ?: break
                push(api, owner, entry)
                depose = true
            }
            depose
        }
    }

    /**
     * Dépose **un** signalement : la capture d'abord, la ligne ensuite, la confirmation après.
     *
     * L'ordre est celui de l'original, et chacun des trois gestes a sa raison :
     *
     *   - la capture **avant** la ligne, sans quoi une ligne désignerait un fichier que le
     *     compartiment ne contient pas — et rien ne le vérifierait, la colonne n'étant liée à
     *     aucune clé étrangère ;
     *   - la ligne **avant** la confirmation, qui n'a de sens qu'après ;
     *   - la confirmation **avant** le retrait de l'entrée, et c'est le geste qui protège d'une
     *     perte : sans elle, un serveur qui accepterait la ligne sans la rendre — ou un cache de
     *     lecture en retard — ferait disparaître un signalement que personne ne pourrait plus
     *     renvoyer.
     *
     * @throws Exception tout échec non toléré. L'entrée reste alors dans la file, et la passe
     *   s'arrête : c'est le comportement de l'original.
     */
    private suspend fun push(api: ProblemReportSender, owner: String, entry: QueuedProblemReport) {
        val ligne = entry.report
        val adresse = ligne.screenshotPath

        if (entry.localPath != null && adresse != null) {
            val octets = store.bytesOf(entry)
                ?: throw IllegalStateException(ProblemReportText.SCREENSHOT_MISSING)

            try {
                api.uploadScreenshot(adresse, octets, entry.mime ?: ProblemReports.MIME_JPEG)
            } catch (cancelled: CancellationException) {
                // L'annulation passe **avant** l'examen du message : `CancellationException` est
                // une `Exception`, et une interruption dont le texte contiendrait par hasard
                // « already exists » serait avalée — la passe continuerait alors qu'on vient de
                // demander de l'arrêter.
                throw cancelled
            } catch (error: Exception) {
                // Une capture déjà déposée n'est pas une panne : les octets sont arrivés, et c'est
                // la ligne qui manquait. Voir `ProblemReports.uploadFailureIsBenign`.
                if (!ProblemReports.uploadFailureIsBenign(
                        error.restStatusCode(),
                        error.restMessage(),
                    )
                ) {
                    throw error
                }
            }
        }

        if (session.currentOwner() != owner) return

        api.insert(ligne)

        if (!api.confirm(ligne.id, owner)) {
            throw IllegalStateException(ProblemReportText.CONFIRMATION_PENDING)
        }

        store.remove(entry)
    }

    companion object {
        /**
         * Le délai du vidage périodique, en millisecondes.
         *
         * `setInterval(sync,30000)` dans l'original. Il est écrit ici, et non dans le conteneur,
         * parce que c'est une propriété de la file et non de l'assemblage : la changer sans la
         * file n'aurait pas de sens.
         */
        const val PERIOD_MS: Long = 30_000L
    }
}
