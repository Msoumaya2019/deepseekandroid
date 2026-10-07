package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.LocalRecitation
import com.msoumaya.deepseekandroid.core.model.RecitationKind
import com.msoumaya.deepseekandroid.core.model.RecitationSyncStatus
import com.msoumaya.deepseekandroid.core.model.RemoteRecitation

/**
 * Les règles de l'écran « Mes récitations » — **pures**, donc éprouvables sans appareil.
 *
 * Porté depuis `src/RecitationsScreen.tsx`. L'écran, lui, ne décide rien : il affiche ce que ces
 * fonctions rendent. La raison est celle de tout le portage — ces décisions se prouvent en
 * quelques secondes, alors qu'un écran, un lecteur audio et un compartiment de stockage ne se
 * prouvent que sur un appareil.
 *
 * Ce fichier ne dit **rien** du dessin : ni couleur, ni marge, ni composant. Il dit ce qu'une
 * ligne porte, dans quel ordre les lignes se rangent, et ce qu'elles annoncent.
 */
object RecitationsList {

    /** Pas du déplacement dans l'audio, en millisecondes. L'original avance de dix secondes. */
    const val SEEK_STEP_MS: Long = 10_000L

    // -----------------------------------------------------------------------
    // La fusion du local et du distant
    // -----------------------------------------------------------------------

    /**
     * Le filtre de la liste.
     *
     * Les trois valeurs portent les trois boutons de l'original, et [matches] dit lequel des trois
     * accepte une ligne.
     */
    enum class Filter { ALL, QURAN, INVOCATION }

    /**
     * La ligne **distante** qui correspond à une récitation locale.
     *
     * L'original fabrique exactement cet objet (`all.push({... storage_path:'' ...})`) : une
     * récitation qui n'est encore que sur l'appareil se présente à la liste sous la forme d'une
     * ligne distante, afin que la liste n'ait qu'une seule forme de ligne à connaître.
     *
     * Deux points méritent d'être dits :
     *
     *  * `storagePath` est **vide**, et ce n'est pas une adresse : rien n'a été déposé. Une ligne
     *    vide ne peut pas être confondue avec une adresse, qui porte toujours un propriétaire et
     *    une extension ([Recitations.storagePath]) ;
     *  * `listenedAt` est **nul**, même si la copie locale n'a pas de champ pour le dire : c'est
     *    un relecteur qui écoute, et il ne peut pas avoir écouté un fichier qui ne lui est pas
     *    encore parvenu.
     */
    fun asRemote(local: LocalRecitation): RemoteRecitation = RemoteRecitation(
        id = local.id,
        userId = local.userId,
        startVerseId = local.start,
        endVerseId = local.end,
        durationMs = local.durationMs,
        storagePath = "",
        createdAt = local.createdAt,
        listenedAt = null,
        kind = local.kind,
        invocationId = local.invocationId,
    )

    /**
     * Les lignes de la liste : le distant d'abord, puis le local qui n'y est pas encore.
     *
     * L'ordre est celui du client d'origine, et il compte : une ligne présente des deux côtés est
     * prise **du distant**, parce que c'est lui qui porte ce que le local ignore — l'écoute par un
     * relecteur, et la nature venue du serveur. Prendre la copie locale ferait disparaître
     * « Écoutée » dès que la récitation existe aussi sur l'appareil, c'est-à-dire toujours.
     *
     * Le tri est **décroissant sur `createdAt`**. L'original compare deux chaînes
     * (`localeCompare`) ; ce portage compare aussi deux chaînes, par ordre naturel. Les deux
     * rendent le même ordre parce que l'horodatage est un ISO-8601 de largeur **fixe**
     * (`2026-10-07T12:34:56.789Z`) : à largeur fixe, l'ordre lexicographique est l'ordre
     * chronologique. Un format de largeur variable ferait diverger les deux comparaisons.
     *
     * Le tri est **stable** de part et d'autre : deux lignes de même horodatage gardent l'ordre
     * d'insertion, donc la ligne distante passe avant la ligne locale de même instant.
     */
    fun merge(local: List<LocalRecitation>, remote: List<RemoteRecitation>): List<RemoteRecitation> {
        val toutes = remote.toMutableList()
        for (item in local) {
            if (toutes.none { it.id == item.id }) toutes.add(asRemote(item))
        }
        return toutes.sortedByDescending { it.createdAt }
    }

    /**
     * `true` si le filtre accepte une ligne de cette nature.
     *
     * La nature **absente** compte comme un passage du Coran, comme l'original
     * (`item.recording_type ?? 'quran'`) : une ligne écrite avant que la colonne existe n'est pas
     * une invocation, et la ranger parmi elles la ferait disparaître du filtre « Coran ».
     */
    fun matches(filter: Filter, kind: RecitationKind?): Boolean = when (filter) {
        Filter.ALL -> true
        Filter.QURAN -> kind != RecitationKind.INVOCATION
        Filter.INVOCATION -> kind == RecitationKind.INVOCATION
    }

    /** Les lignes que le filtre retient, dans leur ordre. */
    fun filtered(rows: List<RemoteRecitation>, filter: Filter): List<RemoteRecitation> =
        rows.filter { matches(filter, it.kind) }

    // -----------------------------------------------------------------------
    // Ce que la ligne annonce
    // -----------------------------------------------------------------------

    /**
     * L'état de dépôt, tel qu'il se lit sous une récitation.
     *
     * Le `null` **n'est pas** un cas d'erreur : c'est une ligne qui n'a pas de copie locale, donc
     * une récitation déjà sur le serveur et venue de lui. Elle se lit « Synchronisé », comme
     * l'original (`own?.syncStatus === 'synced' || !own`) — l'absence de copie locale est la
     * preuve la plus forte que le dépôt a abouti, puisque le dépôt **crée** cette copie.
     */
    fun syncLabel(status: RecitationSyncStatus?): String = when (status) {
        null, RecitationSyncStatus.SYNCED -> RecitationText.SYNC_SYNCED
        RecitationSyncStatus.UPLOADING -> RecitationText.SYNC_UPLOADING
        RecitationSyncStatus.FAILED -> RecitationText.SYNC_FAILED
        RecitationSyncStatus.PENDING -> RecitationText.SYNC_PENDING
    }

    /**
     * L'état d'une récitation ouverte : ce qu'il reste à en faire.
     *
     * Les deux natures ne disent **pas** la même chose, et c'est l'original : une invocation n'a
     * pas de versets, donc rien à corriger — elle n'est qu'écoutée ou pas. Un passage du Coran,
     * lui, se corrige verset par verset, et une correction l'emporte sur l'écoute : une récitation
     * écoutée puis corrigée se lit « Corrigée », pas « Écoutée ».
     *
     * Le retour **général** compte autant qu'une correction de verset (`correctionCount ||
     * feedback.length`) : les deux sont des retours du relecteur, et n'en compter qu'un ferait
     * lire « En attente de correction » sous une récitation que quelqu'un a déjà commentée.
     */
    fun statusLabel(
        kind: RecitationKind,
        listened: Boolean,
        correctionCount: Int,
        feedbackCount: Int,
    ): String = when {
        kind == RecitationKind.INVOCATION ->
            if (listened) RecitationText.STATUS_LISTENED else RecitationText.STATUS_TO_LISTEN

        correctionCount > 0 || feedbackCount > 0 -> RecitationText.STATUS_CORRECTED

        listened -> RecitationText.STATUS_LISTENED

        else -> RecitationText.STATUS_AWAITING_CORRECTION
    }

    /**
     * Le titre d'une ligne : la nature, puis ce qu'elle désigne.
     *
     * Le préfixe est en majuscules dans l'original (`INVOCATION · …`, `CORAN · …`) : c'est lui qui
     * distingue les deux sortes d'enregistrement d'un coup d'oeil, avant même de lire la
     * référence.
     *
     * Le repli de l'invocation est **« Ma prononciation »**, et il diffère de celui de
     * l'enregistreur (« Invocation ») : c'est l'original, et les deux écrans parlent bien de deux
     * moments différents — celui où l'on enregistre, et celui où l'on relit. Le repli ne joue que
     * sur `null`, comme le `??` de l'original : un titre vide rend une ligne vide.
     *
     * `reference` est passé en paramètre plutôt que lu de [Quran] : la règle reste ainsi pure, et
     * l'appelant décide d'où vient le Coran — même raison, et même forme, que
     * [Recitations.correctionProblem] et son `verseExists`.
     *
     * **Divergence assumée.** L'original affirme que les bornes existent (`item.start_verse_id!`)
     * et lèverait une erreur si elles manquaient. Ici, une ligne de Coran sans bornes rend une
     * ligne **sans référence** au lieu de faire tomber l'écran. Le cas est inatteignable par la
     * table, dont la contrainte lie la nature aux bornes ; mais un écran qui tombe sur une ligne
     * inattendue cache toutes les autres, et une ligne sans référence dit déjà ce qu'elle sait.
     */
    fun title(
        kind: RecitationKind,
        startVerseId: Int?,
        endVerseId: Int?,
        invocationTitle: String?,
        reference: (Int, Int) -> String,
    ): String = if (kind == RecitationKind.INVOCATION) {
        "${RecitationText.TITLE_PREFIX_INVOCATION} · " +
            (invocationTitle ?: RecitationText.LIST_INVOCATION_FALLBACK)
    } else {
        val detail = if (startVerseId == null || endVerseId == null) {
            ""
        } else {
            reference(startVerseId, endVerseId)
        }
        "${RecitationText.TITLE_PREFIX_QURAN} · $detail"
    }

    /**
     * La position d'un verset corrigé, telle qu'une carte de correction la nomme.
     *
     * L'original compose `surahs[verseAt(id).surah - 1].name` et le numéro du verset dans sa
     * sourate — et non l'identifiant global, qui irait jusqu'à 6236 et ne dit rien à personne.
     * `surah` et `ayah` sont passés en paramètres pour la même raison que `reference` ci-dessus.
     */
    fun verseLabel(surahName: String, ayah: Int): String =
        "$surahName · ${RecitationText.VERSE_WORD} $ayah"

    // -----------------------------------------------------------------------
    // Le temps
    // -----------------------------------------------------------------------

    /**
     * Le temps écoulé, en `m:ss`.
     *
     * **Les minutes ne sont pas remplies**, contrairement à [RecitationRecorder.clock] : l'écran
     * de liste écrit `Math.floor(ms/60000)` sans `padStart`, là où l'enregistreur écrit
     * `.padStart(2,'0')`. Les deux formats coexistent donc réellement dans le client d'origine
     * (`RecitationsScreen.tsx` ligne 13, `RecitationRecorder.tsx` ligne 15), et les confondre
     * changerait le texte rendu d'un des deux écrans.
     *
     * Les secondes, elles, sont **toujours** remplies à deux chiffres : « 1:05 », jamais « 1:5 ».
     *
     * Les minutes ne sont pas repliées à soixante : `3600000` ms rend `60:00`, et non `00:00`.
     */
    fun clock(durationMs: Long): String {
        val minutes = Math.floorDiv(durationMs, 60_000L)
        val seconds = Math.floorMod(Math.floorDiv(durationMs, 1_000L), 60L)
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }

    /**
     * L'avancement de l'écoute, en pourcentage de la barre.
     *
     * Une durée **nulle ou négative** rend `100`, et ce n'est pas un choix : l'original divise par
     * elle (`position*1000/item.duration_ms*100`), ce qui donne l'infini en JavaScript, que
     * `Math.min(100, …)` ramène à 100. La barre paraît donc pleine. Le cas n'est pas atteignable —
     * `Recitations.saveProblem` refuse une durée non positive —, mais le reproduire coûte une
     * ligne et évite une division par zéro, qui rendrait `NaN` et ferait disparaître la barre.
     *
     * Le plafond est à 100 : une position qui dépasse la durée — le lecteur peut rendre un temps
     * légèrement supérieur en fin de piste — ne déborde pas de la barre.
     */
    fun progressPercent(positionMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 100f
        return minOf(100f, positionMs.toFloat() / durationMs.toFloat() * 100f)
    }

    /**
     * Où revient le lecteur en reculant.
     *
     * Le **plancher à zéro** est celui de l'original (`Math.max(0, position - 10)`). Sans lui, un
     * recul demandé au tout début donnerait une position négative, que le lecteur recevrait comme
     * une adresse invalide.
     */
    fun seekBackward(positionMs: Long, stepMs: Long = SEEK_STEP_MS): Long =
        maxOf(0L, positionMs - stepMs)

    /**
     * Où va le lecteur en avançant.
     *
     * **Aucun plafond**, et c'est l'original (`seekTo(position + 10)`) : c'est au lecteur de
     * décider ce qu'il fait d'une position au-delà de la fin — il s'arrête. Plafonner ici
     * inventerait une règle que l'original n'a pas, et ferait diverger les deux clients sur ce
     * qu'un appui produit quand on est déjà à la fin.
     */
    fun seekForward(positionMs: Long, stepMs: Long = SEEK_STEP_MS): Long = positionMs + stepMs

    // -----------------------------------------------------------------------
    // Ce qu'un appui sur « Réécouter / Pause » produit
    // -----------------------------------------------------------------------

    /**
     * Les quatre choses qu'un appui peut demander au lecteur.
     *
     * **Quatre, et non deux**, parce que le client d'origine distingue *reprendre* de *relancer* —
     * et que les confondre produit un bouton muet. Un lecteur arrivé à la fin de sa piste y reste :
     * reprendre une piste terminée ne rend aucun son. C'est le piège que `ExoAudioOutput.play`
     * documente déjà du côté de l'enchaînement des versets ; il se représente ici, du côté de
     * l'écoute d'une récitation.
     */
    enum class PlaybackAction {
        /** Rien à faire : aucune ligne n'est dépliée, donc aucun bouton n'existe. */
        NOTHING,

        /** Suspendre : la piste joue en ce moment. */
        PAUSE,

        /** Reprendre où l'on s'était arrêté : la piste est chargée, et pas arrivée à sa fin. */
        RESUME,

        /**
         * Charger puis jouer.
         *
         * C'est le cas de la **première** écoute, et aussi celui d'une **réécoute** après la fin :
         * dans les deux, il faut repartir du début, et le lecteur ne le fait pas de lui-même.
         */
        LOAD,
    }

    /**
     * Décide ce qu'un appui sur le bouton de lecture demande au lecteur.
     *
     * @param playing vrai si une piste joue en ce moment.
     * @param ended vrai si la piste chargée est arrivée à sa fin.
     * @param loadedId la récitation dont la piste est **chargée**, ou `null` si rien ne l'est.
     * @param openId la récitation dépliée, ou `null`.
     *
     * **L'ordre des cas porte tout le sens, et chacun a sa raison :**
     *
     *  - `openId == null` **d'abord** : sans ligne dépliée il n'y a pas de bouton, donc la question
     *    ne se pose pas — et répondre autre chose ferait démarrer une lecture que personne n'a
     *    demandée ;
     *  - `playing` **ensuite** : ce qui joue se suspend, et c'est le seul cas où la ligne chargée
     *    peut différer de la ligne ouverte sans que ce soit une erreur ;
     *  - **l'identité avant la fin** : une piste chargée pour une **autre** récitation ne se reprend
     *    pas — elle serait le son d'une autre, sous la ligne ouverte. La recharger est le seul
     *    geste qui joue ce que la personne regarde ;
     *  - **la fin en dernier** : une piste terminée se relance, elle ne se reprend pas.
     */
    fun playbackAction(
        playing: Boolean,
        ended: Boolean,
        loadedId: String?,
        openId: String?,
    ): PlaybackAction = when {
        openId == null -> PlaybackAction.NOTHING
        playing -> PlaybackAction.PAUSE
        loadedId == openId && !ended -> PlaybackAction.RESUME
        else -> PlaybackAction.LOAD
    }

    // -----------------------------------------------------------------------
    // Ce qui peut être partagé
    // -----------------------------------------------------------------------

    /**
     * `true` si l'écran **offre** le partage pour une récitation de cette nature.
     *
     * Une invocation ne se partage pas. Elle porte la prononciation d'une personne sur un texte
     * qu'elle a choisi ; un passage du Coran, lui, est le même pour tout le monde, et le partager
     * a un sens que celui d'une invocation n'a pas. L'original retire le bouton pour cette seule
     * raison (`item.recording_type !== 'invocation'`).
     *
     * **Retirer n'est pas désactiver**, et l'original fait bien les deux : une invocation n'a
     * **aucun** bouton, parce qu'aucun dépôt ne la rendrait partageable ; une récitation du Coran
     * encore sur l'appareil garde le sien, **désactivé** — voir [shareable].
     */
    fun shareOffered(kind: RecitationKind): Boolean = kind != RecitationKind.INVOCATION

    /**
     * `true` si le partage peut **aboutir** — donc si le bouton est actif.
     *
     * **Une récitation absente du serveur ne se partage pas.** Le partage écrit l'identifiant
     * d'une **ligne distante** dans un message ; un enregistrement qui n'est encore que sur
     * l'appareil n'en a pas, et le déclencheur `validate_recitation_message` refuserait le
     * message — il exige que la récitation existe **et** qu'elle appartienne à l'expéditeur.
     * L'original désactive le bouton pour cette raison
     * (`disabled={!remote.some(row => row.id === item.id)}`) : il le laisse **visible**, parce
     * que le dépôt est en cours et que le bouton s'activera de lui-même à la prochaine lecture.
     *
     * La nature **absente** ne se pose pas ici : `RemoteRecitation.kind` vaut `QURAN` par défaut,
     * comme le `recording_type ?? 'quran'` de l'original.
     */
    fun shareable(kind: RecitationKind, remote: Boolean): Boolean =
        shareOffered(kind) && remote

    /**
     * Le texte déposé dans la conversation au moment du partage : « Récitation vocale · Al-Fâtiha
     * 1–7 ».
     *
     * Le préfixe est **nécessaire**, et pas décoratif : le message part sans la nature de la
     * récitation — la conversation n'a pas de colonne pour la dire —, et un corps qui commencerait
     * par la référence se lirait comme une citation du Coran. Le destinataire doit savoir qu'il
     * reçoit un enregistrement.
     *
     * La [reference] est passée en paramètre plutôt que lue de [Quran], comme dans [title] : la
     * règle reste pure, et c'est le rendu qui décide d'où vient le Coran.
     */
    fun shareDescription(reference: String): String =
        "${RecitationText.SHARE_PREFIX} · $reference"
}
