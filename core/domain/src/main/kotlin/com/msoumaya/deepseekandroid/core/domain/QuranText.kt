package com.msoumaya.deepseekandroid.core.domain

/**
 * Les mots de l'écran du Coran.
 *
 * Porté depuis `src/ui/MainScreens.tsx` (la fonction `QuranScreen`). Les chaînes sont reprises
 * **caractère pour caractère** : ce sont des textes d'interface validés par le propriétaire du
 * projet. Les apostrophes typographiques (`’`), les guillemets simples ouvrants (`‘`) de
 * « Hafs ‘an ‘Âsim », les puces (`•`) et le tiret demi-cadratin (`–`) des plages de pages en
 * font partie — les remplacer serait une faute de portage, pas une simplification.
 *
 * ## Ce que cet objet porte, et ce qu'il ne porte pas
 *
 * Il porte les **mots** et les **libellés composés** : « Juz’ 30 », « Pages 12 – 34 »,
 * « Al-Fâtiha • verset 3 ». Il ne porte pas les **règles de recherche** : la chaîne sur laquelle
 * une ligne est trouvée — « numéro, nom, sens, nom arabe » pour une sourate — n'est pas un mot
 * affiché mais une règle de filtrage, et elle vit dans `QuranListRenderer`, avec les autres.
 *
 * ## Les deux choix de l'écran
 *
 * [View] et [Filter] sont déclarés **ici** plutôt que dans l'écran, comme `ReviewText.SummaryKind`
 * ou `SessionPanelText.Entry` : un choix qui porte son libellé appartient aux mots de l'écran, et
 * l'ordre de déclaration **est** l'ordre d'affichage. Un test le fige, pour qu'une énumération
 * réordonnée ne réordonne pas silencieusement les trois segments du sélecteur.
 */
object QuranText {

    // -----------------------------------------------------------------------
    // Les trois vues
    // -----------------------------------------------------------------------

    /**
     * La façon dont la liste est présentée : les sourates, les juz’, ou les hizb.
     *
     * Le libellé sert aussi d'**identité** : le client d'origine cherche « Juz’ » ou « Hizb »
     * dans le texte d'une division, et son sélecteur affiche les mêmes trois mots. C'est
     * pourquoi la valeur porte son libellé plutôt qu'un rang ou une clé distincte — une clé
     * séparée pourrait diverger du mot affiché, et la recherche ne trouverait plus la ligne.
     */
    enum class View(val label: String) {
        /** Les 114 sourates. Le seul mode où le filtre par lieu de révélation s'applique. */
        LIST("Liste"),

        /** Les 30 juz’. */
        JUZ("Juz’"),

        /** Les 60 hizb. */
        HIZB("Hizb"),
    }

    // -----------------------------------------------------------------------
    // Le filtre par lieu de révélation
    // -----------------------------------------------------------------------

    /**
     * Le lieu de révélation retenu.
     *
     * Ces trois valeurs ne s'appliquent qu'à la vue [View.LIST] : une division n'a pas de lieu
     * de révélation, et le client d'origine ne rend même pas le bouton de filtre ailleurs.
     */
    enum class Filter(val label: String) {
        /** Toutes les sourates. */
        ALL("Toutes"),

        /** Les sourates mecquoises. */
        MECCAN("Mecquoises"),

        /** Les sourates médinoises. */
        MEDINAN("Médinoises"),
    }

    // -----------------------------------------------------------------------
    // La bande d'en-tête
    // -----------------------------------------------------------------------

    /** Le titre de la bande d'en-tête. */
    const val TITLE: String = "Le Coran"

    /** Le sous-titre en vue [View.LIST]. */
    const val SUBTITLE_LIST: String = "Mushaf de Médine • Hafs ‘an ‘Âsim • 604 pages"

    /** Le sous-titre en vue [View.JUZ]. */
    const val SUBTITLE_JUZ: String = "Liste des Juz’ • 30 parties"

    /** Le sous-titre en vue [View.HIZB]. */
    const val SUBTITLE_HIZB: String = "Liste des Hizb • 60 parties"

    /**
     * Le sous-titre de la bande d'en-tête, selon la vue.
     *
     * Les trois phrases portent le compte de ce qu'elles listent — 604 pages, 30 parties,
     * 60 parties. C'est la source : le sous-titre est aussi l'annonce de la taille du corpus.
     */
    fun subtitle(view: View): String = when (view) {
        View.LIST -> SUBTITLE_LIST
        View.JUZ -> SUBTITLE_JUZ
        View.HIZB -> SUBTITLE_HIZB
    }

    // -----------------------------------------------------------------------
    // Carte « J’ai appris jusqu’à »
    // -----------------------------------------------------------------------

    /** Le libellé de la carte des connaissances. */
    const val KNOWN_LABEL: String = "J’ai appris jusqu’à :"

    /** Ce que la carte affiche quand aucun verset n'est validé. */
    const val NOTHING_KNOWN: String = "Aucun verset validé"

    /**
     * La référence du dernier verset connu.
     *
     * @param surah le nom de la sourate, tel que le référentiel le donne.
     * @param ayah le numéro du verset dans cette sourate.
     */
    fun knownVerse(surah: String, ayah: Int): String = "$surah • verset $ayah"

    /** Le libellé vocal du bouton qui mène aux connaissances. */
    const val EDIT_KNOWLEDGE: String = "Modifier mes connaissances"

    /** Le titre du message affiché quand aucun écran de connaissances n'est branché. */
    const val KNOWLEDGE_TITLE: String = "Mes connaissances"

    /** Le corps de ce message : où les connaissances se modifient réellement. */
    const val KNOWLEDGE_BODY: String =
        "Modifie tes connaissances depuis ton objectif dans Programme."

    // -----------------------------------------------------------------------
    // Recherche
    // -----------------------------------------------------------------------

    /** Le texte d'invite de la recherche en vue [View.LIST]. */
    const val SEARCH_SURAH: String = "Rechercher une sourate"

    /**
     * Le texte d'invite de la recherche pour une division.
     *
     * La source écrit « Rechercher un Juz’ » et « Rechercher un Hizb » : le mot de la vue est
     * repris **tel qu'il s'affiche**, article compris, et non recomposé à partir d'une clé.
     *
     * @param view la vue courante. La vue [View.LIST] a sa propre phrase, [SEARCH_SURAH].
     */
    fun search(view: View): String = when (view) {
        View.LIST -> SEARCH_SURAH
        View.JUZ -> "Rechercher un ${view.label}"
        View.HIZB -> "Rechercher un ${view.label}"
    }

    // -----------------------------------------------------------------------
    // Le filtre : le bouton et le message de choix
    // -----------------------------------------------------------------------

    /** Le libellé vocal du bouton de filtre. */
    const val FILTER_LABEL: String = "Filtrer les sourates"

    /** Le titre du message de choix du filtre. */
    const val FILTER_TITLE: String = "Lieu de révélation"

    /** Le choix qui referme le message sans rien changer. */
    const val FILTER_CANCEL: String = "Annuler"

    // -----------------------------------------------------------------------
    // Une ligne de la liste
    // -----------------------------------------------------------------------

    /** Le lieu de révélation d'une sourate mecquoise. */
    const val MECCAN: String = "Mecquoise"

    /** Le lieu de révélation d'une sourate médinoise. */
    const val MEDINAN: String = "Médinoise"

    /**
     * Le lieu de révélation, tel qu'il s'affiche dans la pastille d'une sourate.
     *
     * @param isMeccan le fait, lu au référentiel. La pastille change aussi de couleur selon lui.
     */
    fun place(isMeccan: Boolean): String = if (isMeccan) MECCAN else MEDINAN

    /**
     * Le nombre de versets d'une ligne.
     *
     * La source écrit toujours « versets », au pluriel, sans traiter le singulier : la plus
     * courte sourate du Coran en compte trois, donc le cas ne se présente pas.
     */
    fun verseCount(count: Int): String = "$count versets"

    /** Le libellé vocal d'une ligne, qui annonce ce que son ouverture fera. */
    fun open(name: String): String = "Ouvrir $name"

    // -----------------------------------------------------------------------
    // Une division
    // -----------------------------------------------------------------------

    /**
     * Le nom d'une division, tel qu'il s'affiche : « Juz’ 30 », « Hizb 12 ».
     *
     * @param view la vue courante, dont le libellé ouvre le nom.
     * @param number le rang de la division.
     */
    fun divisionName(view: View, number: Int): String = "${view.label} $number"

    /**
     * Les pages couvertes par une division.
     *
     * Le tiret est un **demi-cadratin** (`–`), pas un trait d'union : c'est celui de la source.
     *
     * @param first la première page, calculée par `StudyProgressCalculator.studyPage`.
     * @param last la dernière page, calculée de la même façon.
     */
    fun divisionPages(first: Int, last: Int): String = "Pages $first – $last"

    // -----------------------------------------------------------------------
    // La liste vide
    // -----------------------------------------------------------------------

    /** Ce que la liste affiche quand aucune ligne ne correspond. */
    const val EMPTY: String = "Aucun résultat."

    // -----------------------------------------------------------------------
    // Le pied de liste : le Coran avec règles de Tajwid
    // -----------------------------------------------------------------------

    /** Le titre de la carte du pied de liste. */
    const val TAJWID_TITLE: String = "Coran avec règles de Tajwid"

    /** Le corps de cette carte. */
    const val TAJWID_BODY: String =
        "Organisation par couleurs pour faciliter votre lecture et votre apprentissage."

    // -----------------------------------------------------------------------
    // Le bouton flottant
    // -----------------------------------------------------------------------

    /** Le libellé du bouton flottant, affiché et annoncé. */
    const val LAST_READ: String = "Dernière lecture"
}
