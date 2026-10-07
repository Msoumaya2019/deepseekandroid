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
)

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
     */
    fun render(
        state: RecitationState,
        inputs: RecitationsInputs,
        corrections: List<VerseCorrection> = emptyList(),
        feedback: List<GeneralFeedback> = emptyList(),
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

        val lignes = RecitationsList
            .filtered(toutes, inputs.filter)
            .map { item -> ligne(item, state, ouvertes, reference) }

        // Le message **remplace** celui du dépôt quand personne n'est connecté : l'original
        // s'arrête avant la lecture, et son message est celui de la connexion, pas celui d'une
        // panne qui n'a pas eu lieu.
        val message = when {
            !connecte -> if (configured) {
                RecitationText.LIST_SIGNED_OUT_PROFILE
            } else {
                RecitationText.LIST_SIGNED_OUT
            }

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
            empty = toutes.isEmpty() && connecte && message == null,

            openId = ouverte?.id,
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
            // supprime, et non une préférence d'affichage.
            localOnly = state.remote.none { it.id == item.id },
            open = item.id == ouvertes,
        )
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
