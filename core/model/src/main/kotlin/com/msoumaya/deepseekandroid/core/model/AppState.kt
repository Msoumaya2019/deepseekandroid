package com.msoumaya.deepseekandroid.core.model

import kotlinx.serialization.Serializable

/**
 * État applicatif persisté.
 *
 * **Contrat de compatibilité.** Cette classe est la contrepartie Kotlin exacte du type
 * `AppState` de `src/core/program.ts`. Elle est stockée telle quelle :
 *  - localement, dans la table SQLite `app_state` / `account_state` ;
 *  - à distance, dans la colonne `public.user_state.data` (jsonb) du projet Supabase.
 *
 * Conséquences à respecter impérativement :
 *  - les noms de champs sont ceux du client React Native, en camelCase ;
 *  - les valeurs d'énumération sont les chaînes littérales du client React Native ;
 *  - tout champ absent doit rester absent (pas de `null` écrit), d'où `explicitNulls = false`
 *    dans [AppJson] ;
 *  - `schema` doit valoir 1, sinon le client d'origine refuse l'état.
 *
 * **Pourquoi tant de champs nullables.** Le client d'origine distingue « champ absent » de
 * « champ présent avec une valeur ». Cette distinction porte une décision de synchronisation :
 * quand le serveur ne connaît pas encore `uiFont` ou `accent` et que l'appareil en a un,
 * `reconcileState` doit recopier la valeur locale et pousser l'état fusionné. Un défaut
 * non nul à la désérialisation effacerait cette information et ramènerait silencieusement
 * l'apparence de l'utilisateur au thème par défaut. Les accesseurs `effective*` ci-dessous
 * donnent la valeur à utiliser à l'affichage, sans jamais modifier l'état stocké.
 */
@Serializable
data class AppState(
    val schema: Int = SCHEMA,
    val onboardingDone: Boolean = false,
    val updatedAt: String = EPOCH,

    // --- Apprentissage ------------------------------------------------------
    val knowledge: Map<String, Mastery> = emptyMap(),
    val goal: Goal = Goal(label = "Juz’ ‘Amma", ranges = listOf(Range(5673, 6236))),
    val pace: Pace = Pace.VERSE3,
    val learningDays: List<Int> = listOf(1, 2, 3, 4, 5),
    val sessions: List<Session> = emptyList(),
    val revisions: List<Revision> = emptyList(),

    // --- Champs optionnels (absents restent absents) ------------------------
    val onboardingStep: Int? = null,
    val userId: String? = null,
    val profile: PersonalProfile? = null,
    val theme: AppTheme? = null,
    val uiFont: UiFont? = null,
    val accent: AccentName? = null,
    val notifications: NotificationPreferences? = null,
    val reader: ReaderPreferences? = null,
    val bookmarks: Map<String, VerseBookmark>? = null,
    val readPages: List<Int>? = null,
    val lastRead: LastRead? = null,
    val audioPreferences: AudioPreferences? = null,
    val memorizedAt: Map<String, String>? = null,
    val reviewSettings: ReviewSettings? = null,
    val reviewHistory: List<ReviewEvent>? = null,
    val reviewDue: Map<String, String>? = null,
    val difficultyMarkers: Map<String, DifficultyMarker>? = null,
    val difficultyHistory: List<DifficultyEvent>? = null,
    val reviewModelStartedAt: String? = null,
    val reviewCycle: ReviewCycle? = null,
    val reviewConsolidations: Map<String, Consolidation>? = null,
    val reviewPriorityDue: Map<String, String>? = null,
    val reviewCycleHistory: List<ReviewCycle>? = null,
    val consolidationHistory: List<ConsolidationEvent>? = null,
    val studyProgress: Map<String, StudyProgress>? = null,
) {
    companion object {
        const val SCHEMA = 1
        const val EPOCH = "1970-01-01T00:00:00.000Z"
    }
}

// ---------------------------------------------------------------------------
// Valeurs effectives
//
// Ces accesseurs ne modifient jamais l'état : ils résolvent l'absence de valeur pour
// l'affichage et le calcul, en laissant le champ absent dans l'état sérialisé.
// ---------------------------------------------------------------------------

val AppState.effectiveTheme: AppTheme get() = theme ?: AppTheme.WHITE
val AppState.effectiveUiFont: UiFont get() = uiFont ?: UiFont.ELEGANT
val AppState.effectiveAccent: AccentName get() = accent ?: AccentName.PRUNE
val AppState.effectiveNotifications: NotificationPreferences get() = notifications ?: NotificationPreferences()
val AppState.effectiveReader: ReaderPreferences get() = reader ?: ReaderPreferences()
val AppState.effectiveBookmarks: Map<String, VerseBookmark> get() = bookmarks ?: emptyMap()
val AppState.effectiveReadPages: List<Int> get() = readPages ?: emptyList()
val AppState.effectiveMemorizedAt: Map<String, String> get() = memorizedAt ?: emptyMap()
val AppState.effectiveReviewSettings: ReviewSettings get() = reviewSettings ?: ReviewSettings()
val AppState.effectiveReviewHistory: List<ReviewEvent> get() = reviewHistory ?: emptyList()
val AppState.effectiveReviewDue: Map<String, String> get() = reviewDue ?: emptyMap()
val AppState.effectiveDifficultyMarkers: Map<String, DifficultyMarker> get() = difficultyMarkers ?: emptyMap()
val AppState.effectiveDifficultyHistory: List<DifficultyEvent> get() = difficultyHistory ?: emptyList()
val AppState.effectiveReviewConsolidations: Map<String, Consolidation> get() = reviewConsolidations ?: emptyMap()
val AppState.effectiveReviewPriorityDue: Map<String, String> get() = reviewPriorityDue ?: emptyMap()
val AppState.effectiveReviewCycleHistory: List<ReviewCycle> get() = reviewCycleHistory ?: emptyList()
val AppState.effectiveConsolidationHistory: List<ConsolidationEvent> get() = consolidationHistory ?: emptyList()
val AppState.effectiveStudyProgress: Map<String, StudyProgress> get() = studyProgress ?: emptyMap()

/** Objectif d'apprentissage : un libellé et une ou plusieurs plages de versets. */
@Serializable
data class Goal(
    val label: String,
    val ranges: List<Range>,
    val deadline: String? = null,
    val direction: LearningDirection? = null,
)

/** Une séance du programme d'apprentissage. */
@Serializable
data class Session(
    val id: String,
    val date: String,
    val start: Int,
    val end: Int,
    val unit: Pace,
    val status: SessionStatus,
    val scheduledDate: String? = null,
    /** Instant réel de validation, au format ISO 8601 complet. */
    val completedAt: String? = null,
    /** Jour réel de validation, au format `AAAA-MM-JJ`. */
    val completedDate: String? = null,
) {
    /**
     * Jour prévu. `date` et `scheduledDate` ne sont **jamais** déplacés après création :
     * une séance prévue mardi et faite lundi garde `scheduledDate = mardi` et
     * `completedDate = lundi`.
     */
    val dueDate: String get() = scheduledDate ?: date

    val range: Range get() = Range(start, end)
}

/** Une révision du modèle historique (cycles 7/14/21/30 jours par plage). */
@Serializable
data class Revision(
    val id: String,
    val start: Int,
    val end: Int,
    val due: String,
    val interval: Int,
    val streak: Int,
    val completedCount: Int,
    val lastGrade: LegacyReviewGrade? = null,
) {
    val range: Range get() = Range(start, end)
}

/** Prénom et civilité, utilisés pour l'accueil et le profil social. */
@Serializable
data class PersonalProfile(
    /** `"Homme"` ou `"Femme"` — conservé en chaîne pour rester tolérant. */
    val sex: String,
    val firstName: String,
)

/** Préférences de notification locales (miroir partiel de `notification_preferences`). */
@Serializable
data class NotificationPreferences(
    val messages: Boolean = true,
    val learning: Boolean = false,
    val friendRequests: Boolean? = null,
    val sharedProgress: Boolean? = null,
    val revision: Boolean? = null,
    val corrections: Boolean? = null,
    val adminMessages: Boolean? = null,
    val messagePreview: Boolean? = null,
    val permissionExplained: Boolean? = null,
)

/** Préférences du lecteur. */
@Serializable
data class ReaderPreferences(
    val mushaf: MushafSource = MushafSource.CORAN_TEST,
    val followAudio: Boolean = true,
    val testPage: Int? = null,
    val paper: QuranPaper? = null,
)

/** Récitateur retenu pour l'audio. */
@Serializable
data class AudioPreferences(val reciterId: String)

/** Dernière position de lecture. */
@Serializable
data class LastRead(val page: Int, val verseId: Int, val readAt: String)

/** Réglages du module de révisions. */
@Serializable
data class ReviewSettings(
    val enabled: Boolean = true,
    val cycleDays: Int = 7,
    /** `"cycle"` ou `"quantity"`. */
    val mode: String? = null,
    /** `"nisf"`, `"hizb"`, `"juz"` ou `"juz2"`. */
    val dailyQuantity: String? = null,
    val resumedAt: String? = null,
)

/** Une révision effectuée. */
@Serializable
data class ReviewEvent(
    val id: String,
    val date: String,
    val start: Int,
    val end: Int,
    val category: ReviewCategory,
    val grade: ReviewGrade,
    val scheduledDate: String? = null,
    val completedAt: String? = null,
)

/** Marqueur de difficulté sur un verset, posé par l'élève ou par le professeur. */
@Serializable
data class DifficultyMarker(
    val user: DifficultyStamp? = null,
    val admin: AdminDifficultyStamp? = null,
)

@Serializable
data class DifficultyStamp(val createdAt: String)

@Serializable
data class AdminDifficultyStamp(val createdAt: String, val comment: String? = null)

/** Événement d'historique de difficulté. */
@Serializable
data class DifficultyEvent(
    val verseId: Int,
    val date: String,
    /** `"user"` ou `"admin"`. */
    val origin: String,
    /** `"marked"` ou `"resolved"`. */
    val action: String,
    val comment: String? = null,
)

/**
 * Un cycle de révision : un instantané figé du corpus réparti sur plusieurs jours.
 *
 * `corpus` est un instantané immuable : un verset nouvellement mémorisé n'entre dans un
 * cycle qu'au cycle suivant, jamais au milieu.
 */
@Serializable
data class ReviewCycle(
    val index: Int,
    val startDate: String,
    val lengthDays: Int,
    val corpus: List<Int>,
    val days: List<List<Int>>,
    val completed: List<Int> = emptyList(),
    /** Date `AAAA-MM-JJ` → index de la part du jour dans [days]. */
    val assignments: Map<String, Int> = emptyMap(),
)

/**
 * Consolidation d'un verset à J+1, J+3 et J+7.
 *
 * Les échéances sont **ancrées sur la date d'apprentissage** et ne sont jamais déplacées,
 * même si une étape est validée en avance ou en retard.
 */
@Serializable
data class Consolidation(
    val learnedAt: String,
    val completed: Map<Int, String> = emptyMap(),
    val scheduledDates: Map<Int, String>? = null,
    val completedAt: Map<Int, String>? = null,
)

/** Événement d'historique de consolidation. */
@Serializable
data class ConsolidationEvent(
    val id: String,
    val verseId: Int,
    val offset: Int,
    val learnedAt: String,
    val scheduledDate: String,
    val completedAt: String,
)

/** Une validation unitaire dans une tâche de progression fine. */
@Serializable
data class StudyValidation(
    val start: Int,
    val end: Int,
    val date: String,
    val validatedAt: String? = null,
)

/**
 * Progression fine d'une tâche, clé `"<mode>:<id>"` dans `studyProgress`.
 *
 * Permet de reprendre une séance interrompue exactement où elle s'est arrêtée :
 * `through` est le dernier verset validé.
 */
@Serializable
data class StudyProgress(
    val id: String,
    val mode: StudyMode,
    val start: Int,
    val end: Int,
    val through: Int,
    val page: Int,
    val source: String,
    val updatedAt: String,
    val status: StudyStatus,
    val validations: List<StudyValidation> = emptyList(),
    val category: ReviewCategory? = null,
) {
    val range: Range get() = Range(start, end)

    /** Portion restante, ou `null` si la tâche est terminée. */
    val remaining: Range? get() = if (through >= end) null else Range(through + 1, end)
}

/** Un signet. Suppression logique via [deletedAt] pour que la suppression se propage. */
@Serializable
data class VerseBookmark(
    val verseId: Int,
    val surah: Int,
    val ayah: Int,
    val page: Int,
    val createdAt: String,
    val updatedAt: String,
    val sourcePages: Map<String, Int>? = null,
    val lastUsedAt: String? = null,
    val deletedAt: String? = null,
)
