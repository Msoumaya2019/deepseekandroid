package com.msoumaya.deepseekandroid.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modèle des récitations.
 *
 * Porté depuis `src/services/recitations.ts`. Une récitation est un enregistrement vocal d'un
 * passage du Coran — ou d'une invocation — que la personne dépose pour se le faire corriger.
 *
 * Deux vies coexistent, exactement comme dans le client d'origine :
 *  - **locale**, dans la base de l'appareil, avec l'adresse du fichier et l'état de dépôt ;
 *  - **distante**, dans la table `recitations`, avec l'adresse dans le compartiment de stockage.
 *
 * Le fichier lui-même n'entre jamais dans la base : seule son adresse y figure. Un enregistrement
 * de dix minutes pèse plusieurs mégaoctets, et le mettre dans une ligne rendrait chaque lecture de
 * liste coûteuse.
 */

/** Où en est une récitation locale vis-à-vis du dépôt distant. */
@Serializable
enum class RecitationSyncStatus {
    @SerialName("pending")
    PENDING,

    @SerialName("uploading")
    UPLOADING,

    @SerialName("synced")
    SYNCED,

    @SerialName("failed")
    FAILED,
    ;

    /**
     * `true` tant que le dépôt n'a pas abouti.
     *
     * `UPLOADING` en fait partie, et ce n'est pas un oubli : une application tuée en pleine
     * transmission laisse la ligne dans cet état, et la considérer comme « en cours » ferait
     * qu'elle ne repartirait **jamais**. Le client d'origine filtre de la même façon — il ne
     * garde que ce qui n'est pas `synced`.
     */
    val awaitsUpload: Boolean get() = this != SYNCED
}

/**
 * Nature d'un enregistrement.
 *
 * Une invocation n'est pas un passage du Coran : elle n'a donc **pas** de bornes de versets, et
 * c'est la seule différence qui compte pour les règles. La charge utile de l'invocation
 * elle-même n'est pas portée ici — elle dépend de `DailyContent`, qui ne l'est pas encore.
 */
@Serializable
enum class RecitationKind {
    @SerialName("quran")
    QURAN,

    @SerialName("invocation")
    INVOCATION,
}

/** Une récitation enregistrée sur l'appareil, déposée ou non. */
@Serializable
data class LocalRecitation(
    val id: String,
    val userId: String,
    /** Premier verset enregistré. Sans objet pour une invocation. */
    val start: Int,
    /** Dernier verset enregistré. Sans objet pour une invocation. */
    val end: Int,
    val durationMs: Long,
    /** Adresse du fichier sur l'appareil. */
    val uri: String,
    val createdAt: String,
    val syncStatus: RecitationSyncStatus,
    val kind: RecitationKind = RecitationKind.QURAN,
    /** Identifiant de l'invocation, quand l'enregistrement en est une. */
    val invocationId: String? = null,
)

/**
 * Une récitation telle que la table `recitations` la porte.
 *
 * Les bornes sont **nullables**, et c'est le modèle qui le dit : une invocation n'en a pas. Le
 * client d'origine les met à `null` dans ce cas, plutôt que d'écrire un intervalle qui ne veut
 * rien dire.
 */
@Serializable
data class RemoteRecitation(
    val id: String,
    val userId: String,
    val startVerseId: Int? = null,
    val endVerseId: Int? = null,
    val durationMs: Long,
    /** Adresse dans le compartiment de stockage, de la forme `userId/id.m4a`. */
    val storagePath: String,
    val createdAt: String,
    /** Renseigné quand un relecteur a écouté la récitation. */
    val listenedAt: String? = null,
    val displayName: String? = null,
    val kind: RecitationKind = RecitationKind.QURAN,
    val invocationId: String? = null,
)

/** Une correction d'un verset précis, déposée par un relecteur. */
@Serializable
data class VerseCorrection(
    val id: String,
    val recitationId: String,
    val verseId: Int,
    val comment: String? = null,
    /** Adresse d'une correction vocale, quand elle existe. */
    val voicePath: String? = null,
    val createdAt: String,
    /** Renseigné quand la personne a traité la correction. */
    val resolvedAt: String? = null,
)

/** Un retour général, portant sur la récitation entière. */
@Serializable
data class GeneralFeedback(
    val id: String,
    val recitationId: String,
    val comment: String? = null,
    val voicePath: String? = null,
    val createdAt: String,
)
