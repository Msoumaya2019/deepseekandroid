package com.msoumaya.deepseekandroid.feature.profile

import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.GoalText
import com.msoumaya.deepseekandroid.core.domain.Program
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.Texts
import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.Division
import com.msoumaya.deepseekandroid.core.model.Goal
import com.msoumaya.deepseekandroid.core.model.Mastery
import com.msoumaya.deepseekandroid.core.model.Pace
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.SessionStatus
import com.msoumaya.deepseekandroid.core.model.Surah

// ---------------------------------------------------------------------------
// Calcul de l'écran d'objectif
// ---------------------------------------------------------------------------
// Portage de `GoalScreen` (`src/ui/GoalScreen.tsx`).
//
// Le calcul est séparé de l'écran à dessein : c'est une fonction pure de
// `(état, champs de saisie, jour)`, donc éprouvable sans Compose, sans coroutine et sans
// horloge. L'écran ne fait que disposer ce que ce fichier décide.
//
// **Ce que ce fichier décide, et qui ne se voit pas.** Quatre règles portent à conséquence :
//
//   1. **Ce qu'« enregistrer » écrit.** Le brouillon porte le rythme, l'échéance, les
//      connaissances déclarées **et** le nouvel objectif — puis la sauvegarde ajoute
//      `seedInitialRevisions` et `generateProgram`. Un brouillon qui oublierait une seule de ces
//      quatre pièces produirait un programme plausible, calculé sur autre chose que ce que
//      l'écran affichait : l'aperçu annoncerait un passage, et l'enregistrement en écrirait un
//      autre.
//
//   2. **L'ordre des écritures du brouillon, et ce que l'objectif remplace.** Le client d'origine
//      pose le rythme et l'échéance, **puis** marque les connaissances, **puis** remplace
//      l'objectif — en dernier, et **partiellement** : il écrit `{ ...next.goal, label, ranges }`.
//      L'échéance posée à la première étape survit donc au changement d'objectif. Remplacer
//      l'objet entier la perdrait, et l'écran n'en dirait rien : la date resterait affichée, et
//      le repère enregistré serait vide. C'est un test qui l'a montré, pas une relecture.
//
//   3. **L'unité de rythme se déduit des options, pas d'une condition recopiée.** Le client
//      d'origine écrit `pace==='quarter' ? 'Par rubu‘' : …` — et **oublie `halfHizb` et `hizb`**,
//      qui appartiennent pourtant à la même liste d'options. Un rythme enregistré en hizb
//      s'affiche donc « Par page » là-bas, et le bouton « + » le fait **silencieusement**
//      basculer à une demi-page : la liste affichée ne contient pas le rythme courant, `indexOf`
//      rend `-1`, et le premier pas retombe sur le premier élément. Écart assumé, mesuré et
//      atteignable : voir [GoalRenderer.paceUnitOf].
//
//   4. **L'échéance ne change rien au programme.** Mesuré : `Program.generateProgram` ne lit
//      jamais `goal.deadline`. La note de l'écran le dit déjà — « la date est un repère » —, et
//      c'est exactement ce que le domaine fait. La validation de la date n'existe donc que pour
//      empêcher d'enregistrer un repère illisible.
//
// **Les types que l'état expose ne vivent pas ici.** `GoalUnit`, `GoalPaceUnit` et `GoalChoice`
// sont déclarés dans `GoalUiState.kt`, publics, parce que l'état de l'écran les publie : un
// membre d'un objet `internal` ne peut pas remonter dans un état public, et le dépôt garde ses
// renderers `internal`. Le calcul, lui, reste interne au module.
//
// **Le référentiel coranique doit être chargé avant d'appeler ce fichier.** Tout passe par
// `Quran`, qui rend un référentiel vide tant qu'il n'est pas initialisé — et `verseAt` lève alors
// sur un index. C'est au `ViewModel` de ne pas appeler avant que l'état ne soit prêt.
// ---------------------------------------------------------------------------

internal object GoalRenderer {

    /**
     * Une option de sélecteur, construite par ce fichier et affichée par l'écran.
     *
     * @param number la valeur choisie — un numéro de sourate, de hizb, de juz’ ou de verset.
     * @param label ce qui s'affiche.
     * @param end le **dernier verset** de la division, qui est ce que l'objectif retient.
     */
    private fun choice(number: Int, label: String, end: Int): GoalChoice =
        GoalChoice(number = number, label = label, end = end)

    // --- L'objectif enregistré ----------------------------------------------

    /**
     * L'unité de l'objectif enregistré.
     *
     * L'objectif n'est reconnu que s'il porte **exactement une** plage, et que cette plage est
     * celle d'une division entière. L'ordre d'essai est celui du client d'origine — juz’ d'abord,
     * puis hizb, puis sourate —, et il compte : les trois listes se recouvrent, un juz’ étant un
     * ensemble de deux hizbs, et une sourate courte pouvant tenir dans un hizb. Essayer la sourate
     * en premier ferait afficher « Sourate » là où l'objectif enregistré était un juz’.
     *
     * Un objectif qui ne correspond à rien retombe sur [GoalUnit.HIZB], comme dans le client
     * d'origine.
     */
    fun unitOf(goal: Goal): GoalUnit {
        val range = goal.ranges.singleOrNull() ?: return GoalUnit.HIZB
        return when {
            Quran.juzs.any { it.start == range.start && it.end == range.end } -> GoalUnit.JUZ
            Quran.hizbs.any { it.start == range.start && it.end == range.end } -> GoalUnit.HIZB
            Quran.surahs.any { it.start == range.start && it.end == range.end } -> GoalUnit.SURAH
            else -> GoalUnit.HIZB
        }
    }

    /**
     * Le numéro de la division choisie dans l'objectif enregistré.
     *
     * Le client d'origine y arrive par trois chemins, dans cet ordre : la division reconnue, puis
     * le hizb qui **contient** la fin du dernier objectif, puis soixante. Les deux bornes du
     * repli — 6 236 versets et 60 hizbs — y sont écrites en dur ; ici elles sont **lues** au
     * référentiel, qui porte ces deux nombres. Un Coran d'une autre composition donnerait donc
     * encore un index valide au lieu d'un 60 hors bornes.
     */
    fun goalIndex(goal: Goal): Int {
        val range = goal.ranges.singleOrNull()
        if (range != null) {
            divisionNumber(Quran.juzs, range)?.let { return it }
            divisionNumber(Quran.hizbs, range)?.let { return it }
            surahNumber(Quran.surahs, range)?.let { return it }
        }
        val end = goal.ranges.lastOrNull()?.end ?: Quran.verses.size
        val containing = Quran.hizbs.firstOrNull { end <= it.end }?.number ?: Quran.hizbs.size
        return maxOf(1, containing)
    }

    /**
     * Le numéro de la division dont la plage est exactement [range], s'il en existe une.
     *
     * Une sourate a sa propre fonction, et ce n'est pas une coquetterie : `Surah` et [Division]
     * portent les mêmes trois champs — `number`, `start`, `end` — mais sont deux types sans
     * parenté, et `Quran.surahs` n'est **pas** une `List<Division>`. Le compilateur l'a dit. Une
     * fonction générique sur `List<*>` perdrait le typage pour trois lignes gagnées.
     */
    private fun divisionNumber(divisions: List<Division>, range: Range): Int? =
        divisions.firstOrNull { it.start == range.start && it.end == range.end }?.number

    /** Le numéro de la sourate dont la plage est exactement [range], s'il en existe une. */
    private fun surahNumber(surahs: List<Surah>, range: Range): Int? =
        surahs.firstOrNull { it.start == range.start && it.end == range.end }?.number

    // --- Les options --------------------------------------------------------

    /**
     * Les options d'objectif d'une unité : « Finir <nom> » pour une sourate, « Finir le Hizb 12 »
     * pour les deux autres.
     *
     * Le libellé rendu est **aussi** celui qui sera enregistré comme libellé de l'objectif. C'est
     * la mesure du client d'origine, qui écrit la même expression aux deux endroits : les séparer
     * ferait diverger ce qu'on choisit de ce qu'on relit.
     */
    fun goalChoices(unit: GoalUnit): List<GoalChoice> = when (unit) {
        GoalUnit.SURAH -> Quran.surahs.map {
            choice(it.number, GoalText.finishSurah(it.name), it.end)
        }

        GoalUnit.HIZB -> Quran.hizbs.map {
            choice(it.number, GoalText.finishDivision(GoalText.HIZB, it.number), it.end)
        }

        GoalUnit.JUZ -> Quran.juzs.map {
            choice(it.number, GoalText.finishDivision(GoalText.JUZ, it.number), it.end)
        }
    }

    /**
     * Les options « Dernier Hizb appris » / « Dernier Juz’ appris ».
     *
     * Le libellé est ici le nom nu de la division — « Hizb 12 » —, et non la phrase de l'objectif.
     * C'est ce que le client d'origine écrit, et la distinction est utile : la carte des
     * connaissances déclare ce qu'on **sait**, celle de l'objectif ce qu'on **vise**.
     */
    fun knownDivisionChoices(unit: GoalUnit): List<GoalChoice> = when (unit) {
        GoalUnit.HIZB -> Quran.hizbs.map { choice(it.number, "${GoalText.HIZB} ${it.number}", it.end) }
        GoalUnit.JUZ -> Quran.juzs.map { choice(it.number, "${GoalText.JUZ} ${it.number}", it.end) }
        // Une sourate ne se déclare pas par ce chemin : elle a ses deux champs, la sourate et le
        // verset. Rendre la liste des sourates ici ferait choisir une sourate « connue » sans
        // dire jusqu'où, ce qui est précisément ce que le second champ existe pour éviter.
        GoalUnit.SURAH -> emptyList()
    }

    /** Les options « Dernière sourate apprise », nommées par le nom de la sourate. */
    fun knownSurahChoices(): List<GoalChoice> =
        Quran.surahs.map { choice(it.number, it.name, it.end) }

    /**
     * Les options « Dernier verset appris », pour une sourate donnée.
     *
     * Le nombre de versets est celui de la **sourate choisie**, jamais un maximum global : offrir
     * 286 versets après avoir choisi Al-Fâtiha laisserait déclarer un verset qui n'existe pas.
     */
    fun verseChoices(surah: Int): List<GoalChoice> {
        val count = Quran.surahs.getOrNull(surah - 1)?.count ?: return emptyList()
        return (1..count).map { choice(it, it.toString(), 0) }
    }

    /**
     * Le dernier verset connu d'après ce qui est déclaré.
     *
     * Pour une sourate, c'est le **début** de la sourate plus le verset, moins un : le référentiel
     * donne la plage de la sourate, pas la position du verset. Les deux autres unités se déclarent
     * par la division entière, donc leur fin est celle de la division.
     */
    fun knownEnd(unit: GoalUnit, surah: Int, ayah: Int, division: Int): Int = when (unit) {
        GoalUnit.SURAH -> (Quran.surahs.getOrNull(surah - 1)?.start ?: 1) + ayah - 1
        GoalUnit.HIZB -> Quran.hizbs.getOrNull(division - 1)?.end ?: 0
        GoalUnit.JUZ -> Quran.juzs.getOrNull(division - 1)?.end ?: 0
    }

    // --- Le rythme ----------------------------------------------------------

    /**
     * Les rythmes offerts pour une unité, dans l'ordre du client d'origine.
     *
     * Ces trois listes sont la **définition** de l'unité, et non un détail d'affichage : c'est
     * d'elles que [paceUnitOf] déduit l'unité d'un rythme enregistré.
     */
    fun paceOptions(unit: GoalPaceUnit): List<Pace> = when (unit) {
        GoalPaceUnit.PER_PAGE -> listOf(Pace.HALF_PAGE, Pace.PAGE, Pace.PAGE2)
        GoalPaceUnit.PER_VERSE -> listOf(Pace.VERSE1, Pace.VERSE2, Pace.VERSE3, Pace.VERSE4, Pace.VERSE5)
        GoalPaceUnit.PER_RUBU -> listOf(Pace.QUARTER, Pace.HALF_HIZB, Pace.HIZB)
    }

    /**
     * L'unité d'un rythme, **déduite de l'appartenance aux options**.
     *
     * ## Écart assumé : le client d'origine oublie deux rythmes
     *
     * Il écrit `pace.startsWith('verse') ? 'Par verset' : pace === 'quarter' ? 'Par rubu‘' :
     * 'Par page'`. Les rythmes `halfHizb` et `hizb` ne sont donc **pas** reconnus comme des rub‘,
     * alors qu'ils figurent dans la liste des rub‘. Le défaut est atteignable : l'écran permet de
     * monter jusqu'à « 1 hizb / jour » — « Par rubu‘ » puis deux appuis sur « + » —, et
     * l'enregistrement écrit ce rythme. À la réouverture, l'unité affichée est « Par page », la
     * liste ne contient plus le rythme courant, et un appui sur « + » le remplace
     * **silencieusement** par une demi-page.
     *
     * Le portage déduit donc l'unité de la liste qui **contient** le rythme. Sur les valeurs que
     * la source traite correctement, le résultat est identique — un verset donne « Par verset »,
     * `quarter` donne « Par rubu‘ », une page donne « Par page ». Sur `halfHizb` et `hizb`, il
     * donne « Par rubu‘ », qui est la seule unité dont la liste les contienne.
     *
     * Un rythme qui n'appartiendrait à aucune liste retombe sur [GoalPaceUnit.PER_PAGE] : c'est le
     * repli de la source, et il reste le moins surprenant — la page est l'unité la plus générale.
     */
    fun paceUnitOf(pace: Pace): GoalPaceUnit =
        GoalPaceUnit.entries.firstOrNull { pace in paceOptions(it) } ?: GoalPaceUnit.PER_PAGE

    /**
     * Le rythme qu'ouvre une unité quand on la choisit.
     *
     * Le client d'origine écrit ces trois valeurs en clair dans son gestionnaire de changement
     * d'unité — `'page'`, `'verse1'`, `'quarter'`. Elles ne sont pas les premières de chaque
     * liste : une demi-page et un quart de rub‘ sont des valeurs de **réglage fin**, et ouvrir sur
     * elles ferait croire que le rythme a été réduit. Le rythme d'ouverture est donc celui qui
     * **représente** l'unité — une page, un verset, un rub‘.
     *
     * Ce n'est pas la même chose que le rythme **enregistré** : changer d'unité le remplace, et
     * c'est ce que fait la source. Revenir à l'unité d'origine ne restitue pas le rythme d'avant.
     */
    fun defaultPace(unit: GoalPaceUnit): Pace = when (unit) {
        GoalPaceUnit.PER_PAGE -> Pace.PAGE
        GoalPaceUnit.PER_VERSE -> Pace.VERSE1
        GoalPaceUnit.PER_RUBU -> Pace.QUARTER
    }

    /**
     * Le rythme voisin, dans la même unité.
     *
     * Le pas est **borné** à la liste de l'unité : arrivé au bout, « + » ne fait rien plutôt que
     * de sortir de l'unité. Le client d'origine borne par `Math.max(0, Math.min(len-1, i+delta))`,
     * ce qui donne la même chose — mais seulement parce qu'il borne aussi l'index d'entrée ; ici
     * un rythme absent de la liste est ramené à son premier élément, faute de voisin à désigner.
     */
    fun shiftPace(current: Pace, unit: GoalPaceUnit, delta: Int): Pace {
        val options = paceOptions(unit)
        if (options.isEmpty()) return current
        val index = options.indexOf(current)
        val depart = if (index >= 0) index else 0
        val cible = (depart + delta).coerceIn(0, options.size - 1)
        return options[cible]
    }

    // --- Le brouillon et l'aperçu -------------------------------------------

    /**
     * L'état qu'on enregistrerait si l'on appuyait maintenant.
     *
     * @param state l'état persisté.
     * @param pace le rythme affiché.
     * @param deadline l'échéance affichée, ou `null` si « Sans date » est choisi.
     * @param markKnown la plage à déclarer connue, ou `null` si rien n'a été déclaré.
     * @param goal le nouvel objectif, ou `null` si rien n'a été changé.
     */
    fun draft(
        state: AppState,
        pace: Pace,
        deadline: String?,
        markKnown: Range?,
        goal: Goal?,
    ): AppState {
        var next = state.copy(pace = pace, goal = state.goal.copy(deadline = deadline))
        if (markKnown != null) {
            next = Program.markKnowledge(next, markKnown, Mastery.PERFECT)
        }
        if (goal != null) {
            // L'objectif ne remplace que son **libellé** et ses **plages** : l'échéance posée
            // juste au-dessus doit survivre. C'est la mesure de la source, qui écrit
            // `{ ...next.goal, label, ranges }` — et non `goal`. Remplacer l'objet entier ferait
            // perdre la date saisie dès qu'on change d'objectif, sans que rien ne le signale.
            next = next.copy(goal = next.goal.copy(label = goal.label, ranges = goal.ranges))
        }
        return next
    }

    /**
     * L'objectif à écrire quand on choisit une option.
     *
     * Le libellé est celui de l'option : le client d'origine écrit la même expression pour les
     * deux, et les séparer ferait diverger ce qu'on choisit de ce qu'on relit. La plage part
     * **toujours de 1** — l'objectif du client d'origine va du début du Coran à la fin de la
     * division visée, il ne désigne pas un passage.
     *
     * [draft] n'en retient que le **libellé** et les **plages** : le reste de l'objectif —
     * l'échéance, le sens de parcours — vient de l'état courant du brouillon. Le `previous` reçu
     * ici n'est donc qu'un porteur, et le passer depuis l'objectif enregistré plutôt que depuis
     * le brouillon ne change rien à ce qui est écrit.
     */
    fun goalFor(choice: GoalChoice, previous: Goal): Goal =
        previous.copy(label = choice.label, ranges = listOf(Range(1, choice.end)))

    /**
     * Ce que la carte « Programme généré » annonce.
     *
     * La première séance à faire du programme **que le brouillon produirait** — et non de celui
     * qui est enregistré : c'est ce qui fait de cet aperçu une vérification plutôt qu'un rappel.
     * Quand il n'y a plus de séance, le client d'origine annonce « Objectif atteint ».
     *
     * Une référence qui ne se résout pas — une plage vide, un référentiel incomplet — donne le
     * même mot plutôt qu'une exception : l'aperçu est un ornement, il ne doit pas emporter
     * l'écran.
     */
    fun preview(draft: AppState, at: String): String {
        val session = Program.generateProgram(draft, at)
            .sessions
            .firstOrNull { it.status == SessionStatus.TODO }
            ?: return GoalText.GOAL_REACHED
        return runCatching { Quran.reference(Range(session.start, session.end)) }
            .getOrDefault(GoalText.GOAL_REACHED)
    }

    /**
     * L'état à enregistrer : le brouillon, daté, avec ses révisions initiales et son programme.
     *
     * C'est l'enchaînement du client d'origine, dans son ordre : `touch` pose la date de mise à
     * jour, `seedInitialRevisions` ouvre les révisions des versets déclarés connus,
     * `generateProgram` remplit le calendrier. Omettre `seedInitialRevisions` ferait apprendre des
     * versets que l'application croirait neufs ; omettre `generateProgram` laisserait l'objectif
     * sans aucune séance.
     */
    fun saved(draft: AppState, at: String): AppState =
        Program.generateProgram(Program.seedInitialRevisions(Program.touch(draft), at), at)

    // --- L'amorçage, le rendu et l'enregistrement ---------------------------

    /**
     * Les champs au premier affichage, déduits de l'état enregistré.
     *
     * Trois choses viennent de l'état, et une seule est une devinette :
     *
     *  - le **dernier verset connu** ouvre les champs de connaissance. Un état sans aucune
     *    connaissance ouvre sur le premier verset, comme le `?? 1` du client d'origine ;
     *  - l'**unité et le numéro de l'objectif** sont relus de l'objectif enregistré ;
     *  - le **rythme** et son unité viennent de `state.pace` ;
     *  - l'**échéance** est reprise si elle existe, et le mode « Choisir une date » avec elle.
     *
     * Un identifiant connu hors du corpus — un état abîmé, ou écrit par un client qui connaissait
     * un Coran plus long — ne fait pas tomber l'écran : le verset est lu **borné**, et le champ
     * retombe sur le premier. C'est le seul endroit où l'écran lit une donnée qu'il n'a pas
     * produite.
     */
    fun seed(state: AppState): GoalFields {
        val last = Program.memorizedIds(state).maxOrNull() ?: 1
        val verse = Quran.verses.getOrNull((last - 1).coerceIn(0, Quran.verses.size - 1))
        return GoalFields(
            goalUnit = unitOf(state.goal),
            surah = verse?.surah ?: 1,
            ayah = verse?.ayah ?: 1,
            goalIndex = goalIndex(state.goal),
            pace = state.pace,
            paceUnit = paceUnitOf(state.pace),
            deadlineOn = state.goal.deadline != null,
            deadline = state.goal.deadline ?: "",
        )
    }

    /**
     * L'état qu'on enregistrerait si l'on appuyait maintenant.
     *
     * **Le rendu et l'enregistrement passent par ici, et c'est le point de ce fichier.** L'aperçu
     * affiché est calculé sur ce brouillon ; si l'enregistrement construisait le sien, les deux
     * pourraient différer d'un champ — et l'écran annoncerait un passage pour en écrire un autre.
     */
    fun brouillon(state: AppState, fields: GoalFields): AppState {
        val choisi = goalChoices(fields.goalUnit).getOrNull(fields.goalIndex - 1)
        return draft(
            state = state,
            pace = fields.pace,
            deadline = if (fields.deadlineOn) fields.deadline else null,
            markKnown = if (fields.knownEdited) {
                Range(1, knownEnd(fields.knownUnit, fields.surah, fields.ayah, fields.knownDivision))
            } else {
                null
            },
            goal = if (fields.goalEdited && choisi != null) goalFor(choisi, state.goal) else null,
        )
    }

    /**
     * L'état à enregistrer, daté et complet.
     *
     * La date est supposée déjà validée par l'appelant : ce fichier ne refuse rien, il calcule.
     * Le refus vit dans [validDate], et c'est l'écran qui l'oppose — de même que le client
     * d'origine laisse saisir une date fausse et ne la refuse qu'à l'appui.
     */
    fun save(state: AppState, fields: GoalFields, at: String): AppState =
        saved(brouillon(state, fields), at)

    /**
     * Ce que l'écran affiche.
     *
     * @param state l'état persisté.
     * @param fields les champs de saisie.
     * @param at le jour courant, injectable pour que les tests ne dépendent pas de l'horloge.
     */
    fun render(state: AppState, fields: GoalFields, at: String): GoalUiState = GoalUiState(
        loading = false,
        fields = fields,
        knownChoices = if (fields.knownUnit == GoalUnit.SURAH) {
            knownSurahChoices()
        } else {
            knownDivisionChoices(fields.knownUnit)
        },
        // Le champ du verset n'existe que pour une sourate : offrir des versets après avoir choisi
        // un hizb laisserait déclarer « hizb 12, verset 200 », qui ne veut rien dire.
        verseChoices = if (fields.knownUnit == GoalUnit.SURAH) {
            verseChoices(fields.surah)
        } else {
            emptyList()
        },
        goalChoices = goalChoices(fields.goalUnit),
        savedGoalLabel = state.goal.label,
        // La phrase décrit l'état **enregistré** : une déclaration en cours la contredirait.
        nothingKnown = Program.memorizedIds(state).isEmpty() && !fields.knownEdited,
        // `getValue` et non `?: ""` : un rythme sans libellé afficherait « / jour », et rien ne
        // dirait qu'il manque un mot. Le dépôt a déjà tranché ce cas pour le programme.
        paceLabel = GoalText.paceLine(Texts.paceLabels.getValue(fields.pace)),
        preview = preview(brouillon(state, fields), at),
    )

    // --- La date ------------------------------------------------------------

    /**
     * Une date d'échéance lisible et réelle.
     *
     * Les trois contrôles du client d'origine, et ils ne sont pas redondants :
     *
     *  - la **forme** `AAAA-MM-JJ`, pour écarter une saisie qui se lirait autrement ;
     *  - l'**existence** de la date, parce que le 31 février n'existe pas ;
     *  - l'**aller-retour**, parce qu'un analyseur permissif accepte `2026-2-3` et le rend
     *    `2026-02-03` — deux écritures, une seule date, et l'échéance enregistrée ne serait pas
     *    celle qu'on a tapée.
     */
    fun validDate(text: String): Boolean {
        if (!DATE_SHAPE.matches(text)) return false
        val date = runCatching { Dates.parse(text) }.getOrNull() ?: return false
        return Dates.dateKey(date) == text
    }

    /** `AAAA-MM-JJ`, ancré aux deux bouts : `find` accepterait `2026-1-1` en son milieu. */
    private val DATE_SHAPE = Regex("""^\d{4}-\d{2}-\d{2}$""")
}
