package com.msoumaya.deepseekandroid.feature.recitations

import androidx.compose.runtime.Immutable
import com.msoumaya.deepseekandroid.core.data.repository.RecitationState
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.RecitationText
import com.msoumaya.deepseekandroid.core.domain.RecitationsList
import com.msoumaya.deepseekandroid.core.model.GeneralFeedback
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.RecitationSyncStatus
import com.msoumaya.deepseekandroid.core.model.RemoteRecitation
import com.msoumaya.deepseekandroid.core.model.VerseCorrection

// ---------------------------------------------------------------------------
// Rendu de l'écran « Mes récitations »
// ---------------------------------------------------------------------------
// Portage de `RecitationsScreen` (`src/RecitationsScreen.tsx:46-61`).
//
// **Pourquoi un objet à part, et pur.** Le client d'origine calculait la liste visible **dans le
// JSX** : la fusion des deux listes, le filtre, la date, la durée, le statut et la barre de
// progression étaient écrits au milieu du rendu. Ici, ce qui **décide** vit dans
// `core:domain/RecitationsList.kt` et s'éprouve là-bas ; ce qui **assemble** — quel titre, quel
// sous-titre, quelle carte — vit ici et s'éprouve de même, sans appareil.
//
// **Ce que ce fichier ne fait pas.** Il ne touche ni au réseau, ni à l'horloge de l'appareil, ni
// au référentiel coranique : la référence d'un passage et le nom d'un verset lui sont **passés**.
// Un test peut donc nommer les sourates comme il l'entend, au lieu de charger les 6236 versets
// pour vérifier une phrase.
//
// ## Deux écarts, tous deux mesurés
//
// **Le titre d'une invocation.** L'original écrit `item.invocation_snapshot?.title ??
// 'Ma prononciation'`. Le portage ne demande **pas** la colonne `invocation_snapshot` : elle
// porte un document entier, et `RemoteRecitation` ne l'a pas. Le repli s'applique donc toujours,
// et une invocation s'intitule « INVOCATION · Ma prononciation ». Ce n'est pas une perte
// silencieuse : l'invocation elle-même reste atteignable par `invocation_id`, que la ligne
// publie et que le bouton « Voir l'invocation » ouvre.
//
// **La phrase du vide.** L'original affiche « Aucune récitation enregistrée » dès que la liste
// est vide — y compris quand la lecture a échoué, ou quand personne n'est connecté. C'est une
// affirmation sur ce que la personne a fait, et elle est alors fausse. Ici la phrase n'apparaît
// que lorsque le vide est **établi** : connecté, aucune panne, aucune ligne.
// ---------------------------------------------------------------------------

/**
 * Ce que la personne a fait, et qui entre dans le calcul.
 *
 * Ces quatre valeurs sont tenues par le `ViewModel` et publiées : ce ne sont pas des états
 * d'interface oubliés dans un composable, mais des **entrées** du rendu. La distinction compte
 * pour la position de lecture : garder la position dans le lecteur et la barre ailleurs ferait
 * deux sources pour la même décision, et l'une des deux finirait par retarder.
 */
@Immutable
internal data class RecitationsInputs(
    val filter: RecitationsList.Filter = RecitationsList.Filter.ALL,
    val openId: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,

    /**
     * La **récitation** dont le choix d'ami est ouvert, ou `null`.
     *
     * Une **saisie**, au même titre que [openId] : elle entre dans le calcul du rendu — le choix
     * ne s'affiche que sous la ligne qui l'a ouvert —, et la garder dans un composable ferait deux
     * sources pour la même décision.
     *
     * Elle est portée par la ligne ouverte et **vidée avec elle** : déplier autre chose ferme le
     * choix, comme l'original remet `sharing` à `null` en ouvrant une ligne.
     */
    val sharingId: String? = null,

    /**
     * Le **lien** de l'ami dont la confirmation de partage est ouverte, ou `null`.
     *
     * Un second cran, et non un doublon de [sharingId] : l'original ouvre une **boîte de dialogue**
     * après le choix de l'ami — « Partager cette récitation ? » —, et n'envoie qu'à la
     * confirmation. Le premier cran dit *sous quelle ligne* la liste s'affiche, le second *à qui*
     * on s'apprête à envoyer. Les confondre enverrait la récitation au premier ami touché, sans
     * confirmation, et rien ne le dirait.
     *
     * C'est le **lien** qui est retenu, et non le nom : c'est lui que le partage enverra, et un nom
     * ne le retrouverait pas si le profil changeait entre le choix et la confirmation.
     */
    val pendingLinkId: String? = null,
)

/**
 * Ce que le `ViewModel` a chargé **pour la ligne ouverte**, et pour elle seule.
 *
 * Le porteur est **étiqueté**, et ce n'est pas une coquetterie : les corrections et les retours
 * généraux arrivent après un aller-retour réseau. Ouvrir une ligne, puis une autre, laisse la
 * première réponse arriver **en retard** — et sans étiquette elle s'afficherait sous la seconde
 * ligne, ce qui attribuerait à une récitation le commentaire d'une autre. Rien ne le signalerait :
 * les cartes seraient simplement fausses, et personne ne saurait laquelle croire.
 */
@Immutable
internal data class RecitationsDetails(
    val id: String? = null,
    val corrections: List<VerseCorrection> = emptyList(),
    val feedback: List<GeneralFeedback> = emptyList(),
)

/**
 * Les détails **de la ligne ouverte**, ou rien.
 *
 * Rend un porteur vide dès que l'étiquette ne correspond plus — c'est-à-dire dès que la personne a
 * déplié autre chose, ou tout replié.
 */
internal fun RecitationsDetails.forOpen(openId: String?): RecitationsDetails =
    if (id != null && id == openId) this else RecitationsDetails()

/** Rendu de la liste : fusion, filtre, mise en forme, et ce qui reste à décider. */
internal object RecitationsRenderer {

    /** Le séparateur des morceaux du sous-titre : le point médian **U+00B7** de l'original. */
    private const val SEPARATEUR = " · "

    /**
     * Traduit l'état du dépôt en état affichable.
     *
     * @param corrections les corrections de la récitation **ouverte**, ou une liste vide. Elles
     *   ne sont pas lues ici : c'est le `ViewModel` qui les demande, parce qu'elles dépendent de
     *   ce que la personne a déplié, et non de ce que le dépôt publie.
     * @param configured vrai si un projet Supabase est configuré. Il ne change qu'une chose : la
     *   phrase d'invitation à se connecter. L'original en a deux — « Connecte-toi dans Profil »
     *   quand il y a un compte à ouvrir, « Connecte-toi » tout court quand il n'y a rien à
     *   configurer —, et les confondre enverrait chercher un écran de connexion qui ne peut pas
     *   aboutir.
     * @param reference ce qu'un intervalle de versets s'appelle. Passé plutôt que lu de [Quran],
     *   comme dans `RecitationsList.title` : la règle reste pure.
     * @param verse ce qu'un verset corrigé s'appelle. Même raison.
     * @param playbackError ce que l'écoute a laissé derrière elle — un fichier qui ne s'ouvre pas,
     *   une adresse signée impossible à obtenir —, ou `null`. Voir la note du message, plus bas.
     * @param canListen vrai si un lecteur audio est disponible. Faux, l'écran n'offre aucun geste
     *   d'écoute : voir `RecitationsUiState.canListen`.
     * @param friends les destinataires possibles, déjà réduits aux amitiés acceptées. La liste est
     *   **vide** quand la couche sociale n'est pas là, et c'est ce qui fait lire « Aucun ami
     *   accepté pour le moment. » à l'original.
     * @param canShare vrai si la couche sociale est disponible. Faux, l'écran n'offre aucun bouton
     *   de partage : voir `RecitationsUiState.canShare`.
     * @param shareMessage ce que le partage vient de dire — « Récitation partagée… », ou la raison
     *   de l'échec —, ou `null`. Voir la note du message, plus bas.
     */
    fun render(
        state: RecitationState,
        inputs: RecitationsInputs,
        corrections: List<VerseCorrection> = emptyList(),
        feedback: List<GeneralFeedback> = emptyList(),
        canListen: Boolean = false,
        playbackError: String? = null,
        friends: List<RecitationFriend> = emptyList(),
        canShare: Boolean = false,
        shareMessage: String? = null,
        configured: Boolean = true,
        reference: (Int, Int) -> String = { debut, fin -> Quran.reference(Range(debut, fin)) },
        verse: (Int) -> String = { id ->
            RecitationsList.verseLabel(Quran.surahAt(id).name, Quran.verseAt(id).ayah)
        },
    ): RecitationsUiState {
        val connecte = state.ownerId != null
        val ouvertes = inputs.openId
        val toutes = RecitationsList.merge(state.items, state.remote)
        val ouverte = ouvertes?.let { id -> toutes.firstOrNull { it.id == id } }

        // Le choix d'ami n'est publié que s'il porte sur la ligne **ouverte**. Un choix ouvert sur
        // une ligne que la personne vient de replier ne doit pas survivre : le bouton qui l'ouvre
        // n'est plus là, et la liste d'amis flotterait sous rien.
        val choixOuvert = inputs.sharingId?.takeIf { it == ouverte?.id }

        // **Le second cran : à qui l'on s'apprête à envoyer.** L'original n'envoie pas au premier
        // ami touché — il ouvre une boîte de dialogue, et c'est la phrase de cette boîte qui nomme
        // l'ami et la référence. Le portage la **calcule** ici, parce que c'est un texte, et que le
        // dépôt n'a pas d'outillage de dialogue.
        //
        // Nulle dès que l'une des pièces manque : aucun ami choisi, la ligne repliée, ou l'ami
        // introuvable dans la liste — une amitié retirée pendant qu'on regardait. Dans ce dernier
        // cas, mieux vaut ne rien demander que de nommer « Ami » quelqu'un qui n'est plus un
        // destinataire.
        val confirmation = if (choixOuvert == null || ouverte == null) {
            null
        } else {
            friends.firstOrNull { it.linkId == inputs.pendingLinkId }?.let { ami ->
                RecitationText.shareBody(ami.name, partageLabel(ouverte, reference))
            }
        }

        val lignes = RecitationsList
            .filtered(toutes, inputs.filter)
            .map { item -> ligne(item, state, ouvertes, reference) }

        // Quatre sources pour un seul message, et l'ordre est celui de l'original :
        //
        //  - **sans compte**, c'est l'invitation à se connecter qui compte : l'original s'arrête
        //    avant la lecture, et son message est celui de la connexion, pas celui d'une panne qui
        //    n'a pas eu lieu ;
        //  - **sinon ce que le partage vient de dire** : l'original écrit
        //    `setMessage('Récitation partagée…')`, ou la raison de l'échec, et c'est le geste le
        //    plus récent — il passe donc devant ce que l'écoute avait laissé ;
        //  - **sinon ce que l'écoute vient de laisser derrière elle** : l'original écrit
        //    `setMessage('Lecture impossible : ...')`, qui écrase le message précédent, et c'est le
        //    seul retour qu'on reçoit quand un fichier ne s'ouvre pas — un appui sans effet et sans
        //    explication est exactement ce que ce dépôt s'interdit ;
        //  - **sinon** le message du dépôt.
        val message = when {
            !connecte -> if (configured) {
                RecitationText.LIST_SIGNED_OUT_PROFILE
            } else {
                RecitationText.LIST_SIGNED_OUT
            }

            shareMessage != null -> shareMessage
            playbackError != null -> playbackError
            else -> state.notice
        }

        return RecitationsUiState(
            loading = state.loading,
            signedIn = connecte,
            busy = state.busy,
            message = message,
            filterLabel = libelle(inputs.filter),
            filterLabels = RecitationsList.Filter.entries.map { libelle(it) },
            rows = lignes,
            // Le vide se juge **avant le filtre**. Une personne qui a des récitations du Coran
            // et regarde le filtre « Invocations » verrait sinon « aucune récitation
            // enregistrée », ce qui est faux sur ce qu'elle a fait. C'est ici que le portage
            // s'écarte de l'original, qui ne distingue pas les deux vides.
            //
            // **Et pas pendant une lecture.** Le registre local est publié avant la liste
            // distante : entre les deux, la liste est vide parce qu'on **attend**. La phrase
            // serait fausse, et elle le serait au pire moment — à l'ouverture de l'écran.
            empty = toutes.isEmpty() && connecte && message == null && !state.loading,

            openId = ouverte?.id,
            canListen = canListen,

            canShare = canShare,
            sharingId = choixOuvert,
            friends = friends,
            shareBody = confirmation,
            playing = inputs.playing,
            playLabel = if (inputs.playing) RecitationText.LIST_PAUSE else RecitationText.LIST_PLAY,
            positionLabel = ouverte?.let { item ->
                RecitationText.positionLabel(
                    position = RecitationsList.clock(inputs.positionMs),
                    duration = RecitationsList.clock(item.durationMs),
                    status = RecitationsList.statusLabel(
                        kind = item.kind,
                        listened = item.listenedAt != null,
                        correctionCount = corrections.size,
                        feedbackCount = feedback.size,
                    ),
                )
            }.orEmpty(),
            progressPercent = ouverte?.let {
                RecitationsList.progressPercent(inputs.positionMs, it.durationMs)
            } ?: 0f,

            // Les cartes ne s'affichent que sous la ligne dépliée : les publier quand rien n'est
            // ouvert ferait porter à l'écran des corrections que personne n'a demandées.
            feedback = if (ouverte == null) {
                emptyList()
            } else {
                feedback.map { retour ->
                    FeedbackRow(
                        id = retour.id,
                        title = RecitationText.FEEDBACK_TITLE,
                        comment = retour.comment ?: RecitationText.FEEDBACK_FALLBACK,
                        voicePath = retour.voicePath,
                    )
                }
            },
            corrections = if (ouverte == null) {
                emptyList()
            } else {
                corrections.map { correction ->
                    CorrectionRow(
                        id = correction.id,
                        label = verse(correction.verseId),
                        comment = correction.comment ?: RecitationText.CORRECTION_FALLBACK,
                        voicePath = correction.voicePath,
                        date = RecitationText.dateOnly(correction.createdAt),
                    )
                }
            },
        )
    }

    /**
     * Met une récitation en forme pour la liste.
     *
     * **La copie locale est retrouvée par identifiant**, et non par position : c'est elle qui
     * porte le statut de dépôt, et une ligne distante arrivée par un autre chemin n'en a pas.
     * L'original fait la même recherche (`local.find(row => row.id === item.id)`).
     */
    private fun ligne(
        item: RemoteRecitation,
        state: RecitationState,
        ouvertes: String?,
        reference: (Int, Int) -> String,
    ): RecitationRow {
        val locale = state.items.firstOrNull { it.id == item.id }
        val surLeServeur = state.remote.any { it.id == item.id }

        return RecitationRow(
            id = item.id,
            title = RecitationsList.title(
                kind = item.kind,
                startVerseId = item.startVerseId,
                endVerseId = item.endVerseId,
                // Voir la note du fichier : `invocation_snapshot` n'est pas demandé, donc le repli
                // s'applique toujours.
                invocationTitle = null,
                reference = reference,
            ),
            subtitle = sousTitre(item, locale?.syncStatus),
            kind = item.kind,
            invocationId = item.invocationId,
            // Le serveur porte-t-il cette récitation ? C'est ce qui décide **par où** elle se
            // supprime, **et si le partage peut aboutir** — un partage écrit l'identifiant d'une
            // ligne distante, qu'un enregistrement encore local n'a pas.
            localOnly = !surLeServeur,
            open = item.id == ouvertes,
            // Deux conditions distinctes, et l'original les traite distinctement : il **retire** le
            // bouton pour une invocation, et le **désactive** pour une récitation qui n'est pas
            // encore arrivée.
            shareOffered = RecitationsList.shareOffered(item.kind),
            shareable = RecitationsList.shareable(item.kind, remote = surLeServeur),
            // **La référence seule**, sans le préfixe « CORAN · » du titre : c'est l'original, qui
            // partage `reference({start, end})`.
            shareLabel = partageLabel(item, reference),
        )
    }

    /**
     * La référence qu'un partage nomme : « Al-Fâtiha 1–7 », ou la chaîne vide.
     *
     * **Une seule règle, deux lecteurs.** La ligne s'en sert pour son libellé, et la confirmation
     * pour nommer ce que l'ami recevra : l'écrire deux fois laisserait dériver la phrase de la
     * confirmation et celle de la liste, et la personne confirmerait l'envoi d'une référence qui
     * n'est pas celle qu'elle a sous les yeux.
     *
     * Vide quand les bornes manquent — une ligne du Coran sans bornes n'a pas de référence à
     * partager —, et non une exception : la ligne est déjà dégénérée, et faire tomber l'écran pour
     * un partage qui n'aura pas lieu serait hors de proportion.
     */
    private fun partageLabel(
        item: RemoteRecitation,
        reference: (Int, Int) -> String,
    ): String {
        val debut = item.startVerseId ?: return ""
        val fin = item.endVerseId ?: return ""
        return reference(debut, fin)
    }

    /**
     * « 01/01/2026 11:00:00 · 1:00 · Synchronisé ».
     *
     * **Un instant illisible est omis, pas écrit.** Le client d'origine afficherait « Invalid
     * Date » à cet endroit ; ici le morceau disparaît, et la phrase reste lisible avec ce qu'elle
     * sait — c'est la même règle que `SocialText.dayStamp`, qui rend `null` plutôt qu'un texte
     * d'erreur.
     *
     * Le statut est celui de la copie **locale**. Sans copie locale, il n'y a pas de dépôt en
     * attente : `syncLabel(null)` lit « Synchronisé », ce qui est vrai — la récitation est
     * arrivée, sinon on ne l'aurait pas reçue du serveur.
     */
    private fun sousTitre(item: RemoteRecitation, statut: RecitationSyncStatus?): String =
        listOfNotNull(
            RecitationText.dateStamp(item.createdAt),
            RecitationsList.clock(item.durationMs),
            RecitationsList.syncLabel(statut),
        ).joinToString(SEPARATEUR)

    /**
     * Le libellé d'un filtre, tel que le sélecteur segmenté l'affiche.
     *
     * La correspondance vit ici plutôt que dans `core:domain` : le filtre est une **règle** — ce
     * qui passe et ce qui ne passe pas —, et son libellé est une **mise en forme**. `Recitations
     * List.Filter` ne porte donc pas de texte, et l'écran décide comment il l'appelle.
     */
    private fun libelle(filtre: RecitationsList.Filter): String = when (filtre) {
        RecitationsList.Filter.ALL -> RecitationText.FILTER_ALL
        RecitationsList.Filter.QURAN -> RecitationText.FILTER_QURAN
        RecitationsList.Filter.INVOCATION -> RecitationText.FILTER_INVOCATION
    }
}
