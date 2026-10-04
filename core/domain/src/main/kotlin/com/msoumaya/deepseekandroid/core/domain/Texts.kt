package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AccentName
import com.msoumaya.deepseekandroid.core.model.AppTheme
import com.msoumaya.deepseekandroid.core.model.GoalPreset
import com.msoumaya.deepseekandroid.core.model.Pace
import com.msoumaya.deepseekandroid.core.model.PacePreset
import com.msoumaya.deepseekandroid.core.model.QuranPaper
import com.msoumaya.deepseekandroid.core.model.Reciter
import com.msoumaya.deepseekandroid.core.model.ReviewGrade

/**
 * Libellés et catalogues figés.
 *
 * Porté depuis `src/core/program.ts`, `src/core/audio.ts`, `src/core/readerAppearance.ts`
 * et `src/core/quiz.ts`. Les chaînes sont reprises **caractère pour caractère** du client
 * d'origine : ce sont des textes d'interface validés par le propriétaire du projet.
 */
object Texts {

    // --- Rythmes ------------------------------------------------------------

    val paceLabels: Map<Pace, String> = mapOf(
        Pace.VERSE1 to "1 verset",
        Pace.VERSE2 to "2 versets",
        Pace.VERSE3 to "3 versets",
        Pace.VERSE4 to "4 versets",
        Pace.VERSE5 to "5 versets",
        Pace.HALF_PAGE to "½ page",
        Pace.PAGE to "1 page",
        Pace.PAGE2 to "2 pages",
        Pace.TOUMOUN to "1 toumoun",
        Pace.QUARTER to "1 rub‘",
        Pace.HALF_HIZB to "1 nisf",
        Pace.HIZB to "1 hizb",
    )

    data class PacePresetInfo(val label: String, val pace: Pace, val description: String)

    val pacePresets: Map<PacePreset, PacePresetInfo> = mapOf(
        PacePreset.BEGINNER to PacePresetInfo("Débutant", Pace.VERSE1, "1 à 5 versets par séance"),
        PacePreset.INTERMEDIATE to PacePresetInfo("Intermédiaire", Pace.HALF_PAGE, "Une demi-page par séance"),
        PacePreset.INTENSIVE to PacePresetInfo("Intensif", Pace.PAGE, "1 page, 2 pages ou 1 rub‘ par séance"),
    )

    val beginnerPaces: List<Pace> = listOf(Pace.VERSE1, Pace.VERSE2, Pace.VERSE3, Pace.VERSE4, Pace.VERSE5)

    val intensivePaces: List<Pace> = listOf(Pace.PAGE, Pace.PAGE2, Pace.QUARTER)

    /**
     * Rythmes proposés. Le rythme « toumoun » n'apparaît que si ses 480 bornes Hafs sont
     * vérifiées et contiguës — ce qui n'est pas le cas aujourd'hui (voir [Toumoun]).
     */
    val availablePaces: List<Pace>
        get() = paceLabels.keys.filter { it != Pace.TOUMOUN || Toumoun.verifiedToumouns != null }

    val weekdays: List<String> =
        listOf("Dimanche", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi")

    // --- Objectifs ----------------------------------------------------------

    val goalPresetLabels: Map<GoalPreset, String> = mapOf(
        GoalPreset.LAST_TEN to "Les 10 dernières sourates",
        GoalPreset.SABBIH to "Hizb Sabbih",
        GoalPreset.AMMA to "Juz’ ‘Amma",
        GoalPreset.TO_YASIN to "Jusqu’à la sourate Ya-Sîn",
        GoalPreset.HALF to "La moitié du Coran",
        GoalPreset.ALL to "Tout le Coran",
    )

    // --- Récitateurs --------------------------------------------------------

    val reciters: List<Reciter> = listOf(
        Reciter("ar.husary", "Mahmoud Khalil Al-Husary", "Hafs ‘an ‘Âsim", 128),
        Reciter("ar.alafasy", "Mishary Rashid Alafasy", "Hafs ‘an ‘Âsim", 128),
        Reciter("ar.minshawi", "Mohammed Siddiq Al-Minshawi", "Hafs ‘an ‘Âsim", 128),
        Reciter("ar.shaatree", "Abu Bakr Shatri", "Hafs ‘an ‘Âsim", 128),
        Reciter("ar.ghamidi", "Saad Al Ghamidi", "Hafs ‘an ‘Âsim", 40, "Ghamadi_40kbps", "سعد الغامدي"),
        Reciter("ar.dussary", "Yasser Al Dosari", "Hafs ‘an ‘Âsim", 128, "Yasser_Ad-Dussary_128kbps", "ياسر الدوسري"),
        Reciter("ar.qatami", "Nasser Al Qatami", "Hafs ‘an ‘Âsim", 128, "Nasser_Alqatami_128kbps", "ناصر القطامي"),
    )

    /** Récitateur par défaut du client d'origine : le quatrième de la liste. */
    val defaultReciter: Reciter get() = reciters[3]

    /** Pause inter-versets par défaut, en millisecondes. */
    const val DEFAULT_AYAH_GAP_MS = 200

    fun reciter(id: String?): Reciter = reciters.firstOrNull { it.id == id } ?: defaultReciter

    // --- Fond de page -------------------------------------------------------

    data class PaperOption(val key: QuranPaper, val label: String, val color: String)

    val quranPaperOptions: List<PaperOption> = listOf(
        PaperOption(QuranPaper.IVORY, "Ivoire", "#faf7f2"),
        PaperOption(QuranPaper.ROSE, "Rosé", "#f5e1e7"),
        PaperOption(QuranPaper.SAND, "Sable", "#e8dcc8"),
        PaperOption(QuranPaper.SEPIA, "Sépia", "#d7c5ad"),
    )

    fun quranPaperColor(key: QuranPaper?): String =
        quranPaperOptions.firstOrNull { it.key == key }?.color ?: quranPaperOptions[0].color

    // --- Quiz ---------------------------------------------------------------

    val quizCategories: List<String> = listOf(
        "Coran", "Tajwid", "Prophètes", "Sîra", "Vocabulaire coranique", "Connaissances générales",
    )

    // --- Notes --------------------------------------------------------------

    val reviewGradeLabels: Map<ReviewGrade, String> = mapOf(
        ReviewGrade.PERFECT to "Parfait",
        ReviewGrade.HESITANT to "Quelques hésitations",
        ReviewGrade.REWORK to "À retravailler",
    )

    val themeLabels: Map<AppTheme, String> = mapOf(
        AppTheme.WHITE to "Thème blanc",
        AppTheme.CLASSIC to "Thème vert",
        AppTheme.FEMININE to "Thème rose",
        AppTheme.LILAC to "Lilas & Perle",
        AppTheme.NIGHT to "Bleu Nuit & Or",
    )

    val accentLabels: Map<AccentName, String> = mapOf(
        AccentName.PRUNE to "Prune",
        AccentName.ROSE to "Rose",
        AccentName.GREEN to "Vert",
        AccentName.GOLD to "Doré",
    )
}
