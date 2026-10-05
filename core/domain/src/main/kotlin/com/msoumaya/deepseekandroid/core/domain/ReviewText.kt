package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.ReviewGrade
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Les mots du tableau de bord des révisions.
 *
 * Porté depuis `src/ReviewDashboard.tsx` (les cinq cartes, l'en-tête et le résumé) et depuis
 * `src/ui/RevisionBottomActionBar.tsx` (les cinq gestes). Les chaînes sont reprises **caractère
 * pour caractère**, apostrophes typographiques comprises, parce qu'elles ont été validées par le
 * propriétaire du projet.
 *
 * **Pourquoi ces libellés ne sont pas dans un composable.** Trois d'entre eux décident de ce que
 * la personne croit lire, et un composable ne les éprouverait qu'au travers d'un test
 * d'interface :
 *
 *  - « Jour 3 / 7 » : le compteur du cycle. Un décalage d'un jour annonce une échéance qui n'est
 *    pas celle du jour.
 *  - « 43 % réellement révisés » : le pourcentage du corpus déjà revu. Il se calcule à partir
 *    d'un poids par verset, donc il peut être faux d'une manière qu'aucun test d'écran ne
 *    signalerait.
 *  - « À rattraper » / « Aujourd'hui » / « À venir » : l'état d'une étape de consolidation. Le
 *    client d'origine compare deux dates **en texte** (`step.due <= at`), ce qui n'est juste que
 *    parce qu'elles sont au format `AAAA-MM-JJ` ; le portage garde la même comparaison, et c'est
 *    ce test qui la fige.
 *
 * Les autres — les titres de carte, les libellés de bouton — sont des constantes. Ils sont ici
 * malgré tout, et non recopiés à l'écran : c'est le seul moyen qu'une carte et son bouton ne
 * finissent pas par dire deux choses différentes, et le dépôt d'origine les avait lui aussi
 * rassemblés dans un seul fichier.
 *
 * ## Un écart assumé avec la source
 *
 * Le client d'origine écrit « 1 révisions effectuées » et « 1 versets mémorisés » : il ne met
 * jamais ces deux comptes au singulier. Le portage le fait. Ce n'est pas une coquetterie — le cas
 * `n = 1` est atteint **dès la première révision**, et une faute de français visible sur l'écran
 * d'accueil du module se remarque plus qu'un pluriel correct. L'écart est éprouvé par
 * `ReviewTextTest`, et il est le seul de ce fichier : partout ailleurs, la chaîne est celle du
 * client d'origine.
 */
object ReviewText {

    /**
     * Locale des formats, **fixée** et non celle de l'appareil.
     *
     * Même raison que dans [ProgramText] : le client d'origine demande explicitement `fr-FR` à
     * `toLocaleDateString`. Un téléphone réglé en anglais afficherait « March » là où l'autre
     * client affiche « mars », et les deux ne diraient plus la même chose pour le même état.
     */
    private val FR = Locale.FRANCE

    /** « 10 mars ». Le mois abrégé de `toLocaleDateString` en `fr-FR`. */
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", FR)

    /** « 10 mars 2026 ». La date complète d'un apprentissage, sans le jour de la semaine. */
    private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMMM yyyy", FR)

    // -----------------------------------------------------------------------
    // En-tête
    // -----------------------------------------------------------------------

    /** Le titre de l'écran. */
    const val TITLE: String = "Mes révisions"

    /** Le sous-titre, sous le titre. */
    const val SUBTITLE: String = "Un programme fondé sur les versets réellement mémorisés."

    // -----------------------------------------------------------------------
    // Résumé — trois colonnes
    // -----------------------------------------------------------------------

    /**
     * Les trois colonnes du résumé, **dans l'ordre du client d'origine**.
     *
     * L'ordre suit celui des cartes qui suivent : le cycle du jour, puis les consolidations,
     * puis les versets à retravailler. Un ordre différent ferait lire le résumé à l'envers.
     *
     * La colonne porte son libellé, et non sa position. Un entier de rang suffirait à l'affichage,
     * mais il ne dirait pas **ce que** la colonne compte : le jour où l'ordre change, l'écran
     * peindrait l'icône du cycle devant les consolidations sans que rien ne le signale.
     */
    enum class SummaryKind(val label: String) {
        CYCLE("Cycle du jour"),
        CONSOLIDATION("Consolidation"),
        PRIORITY("À retravailler"),
    }

    /**
     * Le pied de la colonne : la référence du premier verset, ou l'absence.
     *
     * Le client d'origine ajoute « … » quand la colonne porte **plusieurs** tâches, parce que la
     * référence n'en montre qu'une. Sans le point de suspension, on croirait que la colonne ne
     * contient qu'une plage.
     */
    fun summaryReference(count: Int, first: String): String =
        if (count == 0) "Rien à revoir" else first + if (count > 1) "…" else ""

    // -----------------------------------------------------------------------
    // Carte « Ma révision du jour »
    // -----------------------------------------------------------------------

    /** Le titre de la carte du jour. */
    const val DAY_HEADING: String = "Ma révision du jour"

    /** Le bouton qui ouvre la séance du jour. */
    const val START: String = "Commencer ma révision"

    /** Le pied de la carte quand une séance est prévue. */
    const val DAY_FOOTER_FULL: String =
        "La séance du jour regroupe automatiquement ton cycle, tes consolidations et tes versets " +
            "prioritaires."

    /** Le pied de la carte quand il n'y a rien à faire. */
    const val DAY_FOOTER_EMPTY: String =
        "Ta révision du jour est terminée, ou aucune échéance n’est prévue aujourd’hui."

    /**
     * Le pied de la carte du jour, selon qu'une séance existe ou non.
     *
     * C'est le **nombre de tâches** qui décide, et non le fait que la carte s'affiche : une carte
     * toujours visible dont le bouton est désactivé doit dire pourquoi.
     */
    fun dayFooter(hasSession: Boolean): String = if (hasSession) DAY_FOOTER_FULL else DAY_FOOTER_EMPTY

    // -----------------------------------------------------------------------
    // Carte « Mon cycle de révision »
    // -----------------------------------------------------------------------

    /** Le titre de la carte du cycle. */
    const val CYCLE_HEADING: String = "Mon cycle de révision"

    /** Le bouton qui déplie les réglages du cycle. */
    const val CYCLE_EDIT: String = "Modifier"

    /** Le même bouton, lu par les lecteurs d'écran — il n'a pas de texte visible suffisant. */
    const val CYCLE_EDIT_LABEL: String = "Modifier la durée du cycle"

    /** Les durées proposées, **dans l'ordre du client d'origine**. */
    val CYCLE_DAY_OPTIONS: List<Int> = listOf(7, 14, 21, 30)

    /** Le libellé d'une durée : « 7 jours ». */
    fun cycleOptionLabel(days: Int): String = "$days jours"

    /** Le mode « quantité » de `reviewSettings.mode`. */
    const val QUANTITY_MODE: String = "quantity"

    /** La quantité retenue quand le réglage n'en porte pas — « hizb » chez le client d'origine. */
    const val DEFAULT_QUANTITY: String = "hizb"

    /**
     * Les quatre quantités quotidiennes, **dans l'ordre du client d'origine**, et leur libellé.
     *
     * L'ordre est celui des boutons du sélecteur : Nisf, Hizb, Juz, deux Juz — du plus petit au
     * plus grand. Un ordre différent ferait choisir « 2 Juz » en croyant choisir « 1 Nisf ».
     */
    private val QUANTITY_LABELS: Map<String, String> = linkedMapOf(
        "nisf" to "1 Nisf / jour",
        "hizb" to "1 Hizb / jour",
        "juz" to "1 Juz / jour",
        "juz2" to "2 Juz / jour",
    )

    /** Les clés des quantités, dans l'ordre d'affichage. */
    val QUANTITY_KEYS: List<String> = QUANTITY_LABELS.keys.toList()

    /**
     * Le libellé d'une quantité.
     *
     * Une clé inconnue — ou absente — retombe sur « 1 Hizb / jour », comme le `??'hizb'` du
     * client d'origine. Le repli est **nommé** et non silencieux : il s'affiche, et un réglage
     * corrompu se voit au lieu de produire un bouton vide.
     */
    fun quantityLabel(key: String?): String =
        QUANTITY_LABELS[key] ?: QUANTITY_LABELS.getValue(DEFAULT_QUANTITY)

    /**
     * Le mode du cycle tel qu'il s'affiche.
     *
     * Deux formes, et **une seule** à l'écran : soit le rythme en quantité (« 1 Hizb / jour »),
     * soit la durée du cycle (« Cycle de 7 jours »). Le client d'origine choisit sur
     * `reviewSettings.mode`, et retombe sur la durée quand le mode n'est pas `quantity` — y
     * compris quand il est absent.
     */
    fun cycleModeLabel(mode: String?, dailyQuantity: String?, lengthDays: Int): String =
        if (mode == QUANTITY_MODE) quantityLabel(dailyQuantity) else "Cycle de $lengthDays jours"

    /** Le compteur du cycle : « Jour 3 / 7 ». */
    fun dayCounter(cycleDay: Int, lengthDays: Int): String = "Jour $cycleDay / $lengthDays"

    /**
     * Le pourcentage du corpus réellement revu : « 43 % réellement révisés ».
     *
     * Le client d'origine arrondit à l'entier (`Math.round`). Le portage arrondit pareil : un
     * dixième de pourcent n'aide personne à décider, et deux arrondis différents feraient
     * afficher « 42 % » ici et « 43 % » là pour le même état.
     */
    fun percentReviewed(ratio: Double): String = "${Math.round(ratio * 100)} % réellement révisés"

    /**
     * Le résumé du corpus mémorisé, partagé par la carte du cycle et la carte du suivi.
     *
     * Le client d'origine l'écrit **deux fois**, à l'identique, dans deux cartes. Il est ici une
     * seule fois : deux copies finiraient par diverger, et le lecteur verrait deux vérités.
     */
    fun corpusSummary(memorized: Int, juz: Int, rub: Int, nisf: Int): String {
        val versets = if (memorized == 1) "verset mémorisé" else "versets mémorisés"
        return "$memorized $versets · $juz Juz’ · $rub Rubu’ · $nisf Nisf"
    }

    /** Les trois statistiques du cycle, dans l'ordre : à revoir, révisés, restants. */
    val CYCLE_STAT_LABELS: List<String> = listOf("à revoir", "révisés", "restants")

    /** Le titre du bandeau de rythme. */
    const val RHYTHM_LABEL: String = "Rythme du cycle · moyenne"

    /** Le rythme quand le cycle est vide. */
    const val RHYTHM_EMPTY: String = "Aucun verset dans ce cycle"

    /**
     * Le rythme affiché, ou l'absence.
     *
     * Un cycle vide n'a pas de rythme : `reviewRhythm` diviserait par sa durée et annoncerait
     * « 0 verset / jour », ce qui se lirait comme un rythme choisi alors que c'est un corpus
     * absent.
     */
    fun rhythmLine(hasCorpus: Boolean, rhythm: String): String =
        if (hasCorpus) rhythm else RHYTHM_EMPTY

    /** Le pied de la carte du cycle. */
    const val CYCLE_FOOTNOTE: String =
        "Tout le corpus du cycle sera proposé. Une journée manquée reste à faire et peut décaler " +
            "la fin du cycle."

    // -----------------------------------------------------------------------
    // Carte « Nouveaux versets à consolider »
    // -----------------------------------------------------------------------

    /** Le titre de la carte des consolidations. */
    const val CONSOLIDATION_HEADING: String = "Nouveaux versets à consolider"

    /** La note qui explique les trois échéances. */
    const val CONSOLIDATION_NOTE: String =
        "Les nouveaux versets appris sont revus à J+1, J+3 puis J+7, avant d’intégrer le prochain " +
            "cycle."

    /** L'absence de consolidation. */
    const val CONSOLIDATION_EMPTY: String = "Aucun nouveau verset à consolider."

    /** Le libellé lu par les lecteurs d'écran sur une ligne de consolidation. */
    fun consolidateLabel(reference: String): String = "Consolider $reference"

    /** Le détail d'une ligne de consolidation : « 5 versets · appris le 10 mars 2026 ». */
    fun consolidationDetail(quantity: String, learnedAt: String): String =
        "$quantity · appris le ${date(learnedAt, DAY_MONTH_YEAR)}"

    /** L'échéance d'une étape : « J+1 ». */
    fun stepOffset(offset: Int): String = "J+$offset"

    /**
     * L'état d'une étape : « Consolidé », « Aujourd'hui », « À rattraper » ou « À venir ».
     *
     * Quatre mots pour trois questions, et l'ordre n'est pas interchangeable. Une étape faite est
     * « Consolidé » **même si** son échéance est dépassée : c'est le fait qui l'emporte. Une
     * étape due aujourd'hui n'est pas « À venir », elle est « Aujourd'hui » — la différence est
     * celle entre une chose à faire maintenant et une chose à faire plus tard.
     *
     * Les deux dates sont comparées **en texte**, comme chez le client d'origine
     * (`step.due <= at`). Ce n'est juste que parce qu'elles sont au format `AAAA-MM-JJ`, où
     * l'ordre alphabétique est l'ordre chronologique — c'est `stepStatus` qui fige cette
     * dépendance, et le commentaire est là pour qu'on ne la casse pas en changeant le format.
     */
    fun stepStatus(completed: String?, due: String, at: String): String = when {
        completed != null -> "Consolidé"
        due == at -> "Aujourd’hui"
        due < at -> "À rattraper"
        else -> "À venir"
    }

    /**
     * La date affichée sous une étape : celle de la validation si elle a eu lieu, sinon celle de
     * l'échéance.
     *
     * C'est ce que fait le `step.completed ?? step.due` du client d'origine. Montrer l'échéance
     * d'une étape déjà validée ferait croire qu'elle est en retard.
     */
    fun stepDate(completed: String?, due: String): String = date(completed ?: due, DAY_MONTH)

    /** Le bouton qui déplie ou replie la liste des consolidations. */
    fun consolidationToggle(expanded: Boolean): String =
        if (expanded) "Réduire" else "Voir toutes les consolidations"

    // -----------------------------------------------------------------------
    // Carte « À retravailler »
    // -----------------------------------------------------------------------

    /** Le titre de la carte des versets prioritaires. */
    const val PRIORITY_HEADING: String = "À retravailler"

    /** Le compte en tête de carte : « 3 versets à retravailler aujourd'hui ». */
    fun priorityHeadline(quantity: String): String = "$quantity à retravailler aujourd’hui"

    /** L'absence de verset prioritaire. */
    const val PRIORITY_EMPTY: String = "Aucun verset prioritaire prévu aujourd’hui."

    /** Le détail d'une ligne prioritaire. */
    fun priorityDetail(quantity: String): String =
        "$quantity · marqué lors d’une révision précédente"

    /** Le bouton qui déplie ou replie la liste des versets prioritaires. */
    fun priorityToggle(expanded: Boolean): String =
        if (expanded) "Réduire" else "Voir tous les versets prioritaires"

    // -----------------------------------------------------------------------
    // Carte « Mon suivi »
    // -----------------------------------------------------------------------

    /** Le titre de la carte du suivi. */
    const val TRACKING_HEADING: String = "Mon suivi"

    /** Le nombre de révisions effectuées, accordé en nombre. */
    fun revisionCount(count: Int): String =
        if (count == 1) "1 révision effectuée" else "$count révisions effectuées"

    /** Le bouton vers les statistiques. */
    const val STATISTICS: String = "Voir mes statistiques"

    /** Le bouton vers les récitations. */
    const val RECITATIONS: String = "Mes récitations"

    // -----------------------------------------------------------------------
    // Barre d'action d'une révision
    // -----------------------------------------------------------------------

    /**
     * Les cinq gestes de la barre d'action, **dans l'ordre du client d'origine**.
     *
     * L'ordre n'est pas décoratif : un trait sépare les trois grades des deux gestes d'écoute, et
     * c'est le troisième rang qui le porte. Le déplacer changerait la barre.
     */
    enum class Action {
        PERFECT,
        HESITANT,
        REWORK,
        LISTEN,
        RECORD,
        ;

        /**
         * Le grade correspondant, ou `null` pour les deux gestes qui n'en sont pas un.
         *
         * « Écouter » et « Ma voix » ne notent pas la récitation : ils l'écoutent et
         * l'enregistrent. Leur donner un grade ferait valider une révision qu'on n'a pas faite.
         */
        val grade: ReviewGrade?
            get() = when (this) {
                PERFECT -> ReviewGrade.PERFECT
                HESITANT -> ReviewGrade.HESITANT
                REWORK -> ReviewGrade.REWORK
                LISTEN, RECORD -> null
            }
    }

    /**
     * Le libellé d'un geste.
     *
     * Deux d'entre eux portent un **saut de ligne** : « Quelques / hésitations » et « À /
     * retravailler ». Ce n'est pas une mise en forme : sur une barre à cinq colonnes, le mot
     * entier ne tiendrait pas, et le client d'origine coupe au même endroit.
     */
    fun actionLabel(action: Action): String = when (action) {
        Action.PERFECT -> "Parfait"
        Action.HESITANT -> "Quelques\nhésitations"
        Action.REWORK -> "À\nretravailler"
        Action.LISTEN -> "Écouter"
        Action.RECORD -> "Ma voix"
    }

    /**
     * Le libellé d'un grade.
     *
     * Dérivé de [actionLabel] plutôt qu'écrit une seconde fois : le bouton de la barre et le mot
     * du grade sont la même chose, et deux copies finiraient par dire deux choses différentes.
     */
    fun gradeLabel(grade: ReviewGrade): String =
        actionLabel(Action.entries.first { it.grade == grade })

    /**
     * Met une clé `AAAA-MM-JJ` en forme, ou la rend **telle quelle** si ce n'en est pas une.
     *
     * Même repli que dans [ProgramText], et pour la même raison : le client d'origine écrirait
     * « Invalid Date » à l'écran, ce qui ne dit rien de ce qui n'allait pas.
     */
    private fun date(key: String, formatter: DateTimeFormatter): String =
        runCatching { Dates.parse(key).format(formatter) }.getOrDefault(key)
}
