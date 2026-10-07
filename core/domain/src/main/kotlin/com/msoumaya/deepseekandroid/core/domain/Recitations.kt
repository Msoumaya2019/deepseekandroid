package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.RecitationKind

/**
 * Les règles de la récitation — **pures**, donc éprouvables sans appareil.
 *
 * Porté depuis `src/services/recitations.ts`. Ce qui décide vit ici ; ce qui enregistre, dépose
 * ou affiche vit ailleurs. La raison est celle de tout le portage : la décision se prouve par des
 * tests qui tournent en quelques secondes, alors qu'un enregistreur, un compartiment de stockage
 * et un écran ne se prouvent que sur un appareil.
 *
 * Les **bornes** des versets sont celles du Coran entier : les identifiants sont globaux et
 * contigus de 1 à 6236, jamais « sourate:verset ». C'est ce qui permet de comparer deux passages
 * sans savoir de quelle sourate ils viennent.
 */
object Recitations {

    /** Nombre de versets du Coran. Les identifiants vont de 1 à cette valeur. */
    const val VERSE_COUNT: Int = 6236

    /**
     * Durée de validité d'une adresse d'audio signée, en secondes.
     *
     * Dix minutes : assez pour écouter une récitation longue, assez court pour qu'une adresse
     * qui fuite ne serve plus à grand-chose.
     */
    const val SIGNED_URL_SECONDS: Int = 600

    const val EXTENSION_MP4: String = ".m4a"
    const val EXTENSION_3GP: String = ".3gp"

    /**
     * `true` si `start` et `end` désignent un passage réel du Coran.
     *
     * L'ordre est celui du client d'origine : d'abord les bornes du référentiel, puis l'ordre des
     * deux. Un intervalle inversé (`start > end`) est refusé même si les deux bornes sont valides
     * prises séparément.
     *
     * L'« entier » du client d'origine (`Number.isInteger`) n'a pas d'équivalent ici : le type est
     * `Int`, donc la question ne se pose plus. C'est une règle que le portage fait **disparaître**,
     * pas une règle qu'il oublie.
     */
    fun validRange(start: Int, end: Int): Boolean = start >= 1 && end <= VERSE_COUNT && start <= end

    /**
     * L'extension du fichier, déduite de l'adresse de la source.
     *
     * Le client d'origine enregistre tantôt en `.3gp`, tantôt en `.m4a`, selon ce que le moteur
     * de l'appareil a bien voulu produire ; il regarde l'adresse pour le savoir. La comparaison
     * ignore la casse, car une adresse peut porter `.3GP`.
     */
    fun extensionFor(sourceUri: String): String =
        if (sourceUri.lowercase().contains(EXTENSION_3GP)) EXTENSION_3GP else EXTENSION_MP4

    /** Le type MIME qui correspond à l'extension, pour le dépôt. */
    fun contentType(extension: String): String =
        if (extension == EXTENSION_3GP) "audio/3gpp" else "audio/mp4"

    /**
     * L'adresse d'une récitation dans le compartiment de stockage.
     *
     * Le premier segment est l'identifiant du propriétaire : c'est lui qui porte les politiques
     * d'accès du compartiment. Un fichier rangé à la racine ne serait protégeable par personne.
     */
    fun storagePath(userId: String, id: String, extension: String): String = "$userId/$id$extension"

    /**
     * Les bornes à écrire dans la ligne distante.
     *
     * Une invocation n'a **pas** de bornes de versets : le client d'origine écrit alors `null`,
     * plutôt qu'un intervalle qui ne voudrait rien dire. Rendre `null` ici plutôt que de laisser
     * l'appelant décider, c'est s'assurer que la même décision est prise des deux côtés.
     */
    fun remoteBounds(kind: RecitationKind, start: Int, end: Int): Pair<Int?, Int?> =
        if (kind == RecitationKind.INVOCATION) null to null else start to end

    /**
     * Les bornes à écrire dans la ligne **locale**, pour la nature donnée.
     *
     * Le miroir de [remoteBounds], et il ne dit **pas** la même chose : une invocation part au
     * serveur avec des bornes **nulles** (`null`), et se range sur l'appareil avec des bornes à
     * **zéro**. L'original écrit exactement cela (`draft.invocation ? 0 : range.start`), et l'écart
     * n'est pas une inattention : la table distante accepte `null` parce que sa contrainte
     * distingue les deux natures, alors que le modèle local porte deux `Int` que le reste du code
     * additionne et compare sans se demander s'ils existent.
     *
     * Les confondre écrirait `null` dans un champ qui n'en veut pas, ou ferait passer une
     * invocation pour un passage couvrant le premier verset du Coran.
     *
     * C'est la règle qu'applique `RecitationStore.add` : elle est appliquée là où la ligne se
     * forme, pour que le registre ne puisse pas contenir une invocation portant les bornes d'un
     * passage.
     */
    fun localBounds(kind: RecitationKind, start: Int, end: Int): Pair<Int, Int> =
        if (kind == RecitationKind.INVOCATION) 0 to 0 else start to end

    /**
     * `true` si l'échec d'un dépôt de fichier ne doit pas arrêter la synchronisation.
     *
     * Le compartiment refuse d'écraser un fichier existant — le client d'origine dépose avec
     * `upsert: false` —, et ce refus-là est **toléré** : il signifie que les octets sont déjà
     * arrivés, donc que l'étape est faite. Le cas se produit réellement quand une synchronisation
     * précédente a déposé l'audio puis s'est arrêtée avant d'écrire la ligne, ou avant
     * d'enregistrer le statut : le fichier est là, la ligne manque, et le renvoi du fichier est
     * refusé. Traiter ce refus comme une panne laisserait la récitation en échec **pour toujours**,
     * en renvoyant à chaque tentative des octets que le serveur a déjà.
     *
     * La comparaison est **insensible à la casse** et cherche le mot **au milieu** du message,
     * comme l'expression régulière du client d'origine (`/already exists|duplicate/i`) : le texte
     * est composé par le serveur, et sa forme exacte n'est pas un contrat.
     *
     * Un message absent ne rend pas `true`. Sans texte, rien ne prouve que l'échec est bénin, et
     * le tenir pour tel ferait passer pour déposée une récitation qui ne l'est pas — une ligne
     * écrite vers un fichier qui n'existe pas.
     */
    fun uploadFailureIsBenign(message: String?): Boolean {
        if (message.isNullOrEmpty()) return false
        val text = message.lowercase()
        return text.contains("already exists") || text.contains("duplicate")
    }

    /**
     * Ce qui empêche d'enregistrer une récitation locale, ou `null` si rien ne l'empêche.
     *
     * L'ordre des contrôles est **celui du client d'origine**, et il est conservé : quand
     * plusieurs manques coexistent, c'est le premier de cette liste que la personne verra. Le
     * changer changerait les messages, pas seulement leur ordre.
     *
     * Les présences sont testées par `isEmpty`, comme la véracité du client d'origine (`!userId`) :
     * une chaîne d'espaces y passait déjà, et la refuser ici serait un écart silencieux.
     */
    fun saveProblem(
        kind: RecitationKind,
        start: Int,
        end: Int,
        userId: String,
        sourceUri: String,
        durationMs: Long,
    ): RecitationSaveProblem? {
        if (kind == RecitationKind.QURAN && !validRange(start, end)) {
            return RecitationSaveProblem.RANGE_INVALID
        }
        if (userId.isEmpty()) return RecitationSaveProblem.OWNER_MISSING
        if (sourceUri.isEmpty()) return RecitationSaveProblem.SOURCE_MISSING
        if (durationMs <= 0L) return RecitationSaveProblem.DURATION_NOT_POSITIVE
        return null
    }

    /**
     * Ce qui empêche de déposer une correction, ou `null` si rien ne l'empêche.
     *
     * [verseExists] est passé en paramètre plutôt que lu d'un référentiel : la règle reste ainsi
     * pure, et l'appelant décide d'où vient la vérité. Le client d'origine lit `verseAt`, qui
     * consulte le Coran chargé en mémoire.
     */
    fun correctionProblem(
        startVerseId: Int?,
        endVerseId: Int?,
        verseIds: List<Int>,
        verseExists: (Int) -> Boolean,
    ): CorrectionProblem? {
        if (verseIds.isEmpty()) return CorrectionProblem.NOTHING_SELECTED
        if (startVerseId == null || endVerseId == null) return CorrectionProblem.NO_VERSE_RANGE
        for (verseId in verseIds) {
            if (verseId < startVerseId || verseId > endVerseId) return CorrectionProblem.VERSE_OUTSIDE
            if (!verseExists(verseId)) return CorrectionProblem.VERSE_UNKNOWN
        }
        return null
    }

    /**
     * `true` si un retour général ne dit rien.
     *
     * Le commentaire est comparé **rogné**, la correction vocale non : le client d'origine écrit
     * `!comment.trim() && !voicePath`, donc une adresse faite d'espaces compte comme présente.
     * C'est une incohérence du client d'origine, et elle est reproduite telle quelle — la corriger
     * ici ferait diverger les deux clients sur ce qui est acceptable.
     */
    fun feedbackEmpty(comment: String, voicePath: String?): Boolean =
        comment.trim().isEmpty() && voicePath.isNullOrEmpty()

    /** `true` si [userId] peut supprimer la récitation de [ownerId]. Seul le propriétaire peut. */
    fun mayDelete(ownerId: String, userId: String): Boolean = ownerId == userId
}

/** Ce qui empêche d'enregistrer une récitation locale. */
enum class RecitationSaveProblem {
    /** Le passage demandé n'existe pas : bornes hors du Coran, ou intervalle inversé. */
    RANGE_INVALID,

    /** Aucun propriétaire : personne n'est connecté. */
    OWNER_MISSING,

    /** Aucune source : il n'y a pas de fichier à copier. */
    SOURCE_MISSING,

    /** Durée nulle ou négative : l'enregistrement n'a rien capté. */
    DURATION_NOT_POSITIVE,
}

/** Ce qui empêche de déposer une correction. */
enum class CorrectionProblem {
    /** Aucun verset sélectionné. */
    NOTHING_SELECTED,

    /** La récitation est une invocation : elle ne porte pas de versets à corriger. */
    NO_VERSE_RANGE,

    /** Un verset demandé tombe hors des bornes de la récitation. */
    VERSE_OUTSIDE,

    /** Un verset demandé n'existe pas dans le référentiel. */
    VERSE_UNKNOWN,
}
