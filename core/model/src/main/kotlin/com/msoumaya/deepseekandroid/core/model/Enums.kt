package com.msoumaya.deepseekandroid.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Énumérations partagées.
 *
 * Chaque entrée porte un `@SerialName` **identique à la chaîne littérale** utilisée par
 * l'application React Native (`src/core/program.ts`). C'est ce qui garantit qu'un état écrit
 * par le client Expo reste lisible par le client Android, et inversement.
 */

/** Niveau de maîtrise d'un verset. `PERFECT` et `REVIEW` comptent comme « connu ». */
@Serializable
enum class Mastery {
    @SerialName("perfect")
    PERFECT,

    @SerialName("review")
    REVIEW,

    @SerialName("learning")
    LEARNING,
    ;

    /** `true` si le verset est considéré comme connu (par cœur ou en révision). */
    val isKnown: Boolean get() = this == PERFECT || this == REVIEW
}

/** Rythme d'une séance d'apprentissage. */
@Serializable
enum class Pace {
    @SerialName("verse1")
    VERSE1,

    @SerialName("verse2")
    VERSE2,

    @SerialName("verse3")
    VERSE3,

    @SerialName("verse4")
    VERSE4,

    @SerialName("verse5")
    VERSE5,

    @SerialName("halfPage")
    HALF_PAGE,

    @SerialName("page")
    PAGE,

    @SerialName("page2")
    PAGE2,

    @SerialName("toumoun")
    TOUMOUN,

    @SerialName("quarter")
    QUARTER,

    @SerialName("halfHizb")
    HALF_HIZB,

    @SerialName("hizb")
    HIZB,
    ;

    /** Nombre de versets demandé pour les rythmes « N versets ». `null` pour les autres. */
    val verseCount: Int?
        get() = when (this) {
            VERSE1 -> 1
            VERSE2 -> 2
            VERSE3 -> 3
            VERSE4 -> 4
            VERSE5 -> 5
            else -> null
        }
}

/** Préréglage de rythme proposé pendant l'inscription. */
@Serializable
enum class PacePreset {
    @SerialName("beginner")
    BEGINNER,

    @SerialName("intermediate")
    INTERMEDIATE,

    @SerialName("intensive")
    INTENSIVE,
}

/** Statut d'une séance du programme. */
@Serializable
enum class SessionStatus {
    @SerialName("todo")
    TODO,

    @SerialName("done")
    DONE,

    @SerialName("postponed")
    POSTPONED,
}

/** Sens de parcours du programme. */
@Serializable
enum class LearningDirection {
    /** Al-Fâtiha → An-Nâs. */
    @SerialName("fromStart")
    FROM_START,

    /** An-Nâs → Al-Fâtiha, sourate par sourate en remontant. */
    @SerialName("fromNas")
    FROM_NAS,
}

/** Objectif prédéfini proposé à l'inscription. */
@Serializable
enum class GoalPreset {
    @SerialName("lastTen")
    LAST_TEN,

    @SerialName("sabbih")
    SABBIH,

    @SerialName("amma")
    AMMA,

    @SerialName("toYasin")
    TO_YASIN,

    @SerialName("half")
    HALF,

    @SerialName("all")
    ALL,
}

/** Thème visuel. */
@Serializable
enum class AppTheme {
    @SerialName("white")
    WHITE,

    @SerialName("classic")
    CLASSIC,

    @SerialName("feminine")
    FEMININE,

    @SerialName("lilac")
    LILAC,

    @SerialName("night")
    NIGHT,
}

/** Famille de police de l'interface. */
@Serializable
enum class UiFont {
    @SerialName("elegant")
    ELEGANT,

    @SerialName("system")
    SYSTEM,

    @SerialName("classic")
    CLASSIC,
}

/** Couleur d'accent appliquée par-dessus le thème. */
@Serializable
enum class AccentName {
    @SerialName("prune")
    PRUNE,

    @SerialName("rose")
    ROSE,

    @SerialName("green")
    GREEN,

    @SerialName("gold")
    GOLD,
}

/** Auto-évaluation d'une révision. */
@Serializable
enum class ReviewGrade {
    @SerialName("perfect")
    PERFECT,

    @SerialName("hesitant")
    HESITANT,

    @SerialName("rework")
    REWORK,
}

/** Note historique du modèle de révision « legacy » (`revisions`). */
@Serializable
enum class LegacyReviewGrade {
    @SerialName("perfect")
    PERFECT,

    @SerialName("hesitant")
    HESITANT,

    @SerialName("errors")
    ERRORS,

    @SerialName("relearn")
    RELEARN,
}

/** Catégorie d'une tâche de révision. */
@Serializable
enum class ReviewCategory {
    @SerialName("recent")
    RECENT,

    @SerialName("habitual")
    HABITUAL,

    @SerialName("priority")
    PRIORITY,
}

/** Source du moushaf affichée par le lecteur. */
@Serializable
enum class MushafSource {
    /** Coran de Médine : 604 images de la mise en page classique. */
    @SerialName("traditional")
    MEDINA,

    /** Lecture simplifiée : texte + règles de Tajweed en couleur. */
    @SerialName("tajweed")
    SIMPLIFIED,

    /** Ancien « Moushaf Tajweed » par images. Conservé pour la compatibilité d'état. */
    @SerialName("tajweedPages")
    TAJWEED_PAGES,

    /** Moushaf Tajwid QPC, affichage immersif et suivi audio verset par verset. */
    @SerialName("coranTest")
    CORAN_TEST,

    /** Coran 1441 : pages originales téléchargées à la demande. */
    @SerialName("coran_1441")
    CORAN_1441,

    // --- Valeurs héritées ---------------------------------------------------
    // Ces trois identifiants ont existé dans des versions antérieures du client.
    // Ils sont conservés ici uniquement pour qu'un état existant reste lisible ;
    // `migrateReaderState` les convertit en CORAN_1441 et ils ne sont jamais réécrits.
    @SerialName("tawjeed_test_2")
    LEGACY_TAWJEED_TEST_2,

    @SerialName("tajweed_test_2")
    LEGACY_TAJWEED_TEST_2,

    @SerialName("medine_test")
    LEGACY_MEDINE_TEST,
    ;

    /** Vrai si la valeur provient d'une version antérieure et doit être migrée. */
    val isLegacy: Boolean
        get() = this == LEGACY_TAWJEED_TEST_2 || this == LEGACY_TAJWEED_TEST_2 || this == LEGACY_MEDINE_TEST

    /**
     * L'identifiant de cette source **tel qu'il est écrit dans l'état synchronisé**.
     *
     * C'est la valeur de son `@SerialName`, **lue sur le descripteur** au lieu d'être recopiée
     * dans une seconde table. Une table recopiée finirait par diverger du format réellement
     * écrit, et la divergence serait muette : le lecteur chercherait une clé que personne n'a
     * posée, la carte `sourcePages` d'un signet rendrait `null`, et le signet s'ouvrirait
     * simplement à la mauvaise page — sans exception, sans message. `MushafSourceKeyTest` fige
     * les huit valeurs pour qu'un renommage, lui, se voie.
     *
     * À ne pas confondre avec `StudyProgressCalculator.sourceKey`, qui **replie** sur
     * `"traditional"` les sources que ce client **ne rend pas** — le moushaf Tajwid QPC — et les
     * trois sources **héritées** : ce repli sert aux pages d'étude, où une source sans table à
     * elle est remplacée par le découpage canonique en 604 pages. Une page de signet, elle, est
     * enregistrée sous la source **réellement affichée** — replier ferait relire une page d'un
     * autre découpage.
     */
    val persistedKey: String
        get() = MushafSource.serializer().descriptor.getElementName(ordinal)
}

/** Fond de page du moushaf. */
@Serializable
enum class QuranPaper {
    @SerialName("ivory")
    IVORY,

    @SerialName("rose")
    ROSE,

    @SerialName("sand")
    SAND,

    @SerialName("sepia")
    SEPIA,
}

/** Mode d'une tâche de progression fine. */
@Serializable
enum class StudyMode {
    @SerialName("learning")
    LEARNING,

    @SerialName("revision")
    REVISION,
}

/** Statut d'une tâche de progression fine. */
@Serializable
enum class StudyStatus {
    @SerialName("partial")
    PARTIAL,

    @SerialName("completed")
    COMPLETED,
}

/** Mode de répétition de l'audio. */
@Serializable
enum class RepeatMode {
    @SerialName("passage")
    PASSAGE,

    @SerialName("each-verse")
    EACH_VERSE,
}

/** Statut d'un défi de quiz. */
@Serializable
enum class ChallengeStatus {
    @SerialName("pending")
    PENDING,

    @SerialName("completed")
    COMPLETED,

    @SerialName("expired")
    EXPIRED,
}

/** Type de contenu quotidien. */
@Serializable
enum class DailyContentType {
    @SerialName("reminder")
    REMINDER,

    @SerialName("invocation")
    INVOCATION,
}
