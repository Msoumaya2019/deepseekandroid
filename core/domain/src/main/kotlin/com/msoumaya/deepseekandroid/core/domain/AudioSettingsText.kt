package com.msoumaya.deepseekandroid.core.domain

/**
 * Les libellés des réglages d'écoute, et les trois règles d'affichage qui vont avec.
 *
 * Porté depuis `src/PassageAudioPlayer.tsx`. Le client d'origine présente ces réglages **deux
 * fois** : une fois repliés dans le lecteur de poche (son bloc `advanced`), une fois en plein
 * écran. Les deux versions ne disent pas exactement la même chose — l'une annonce « Pause entre
 * les répétitions », l'autre « Pause entre deux écoutes ». C'est la **seconde** qui fait
 * référence ici : c'est l'écran complet, celui qu'on ouvre exprès pour régler.
 *
 * ## Ce qui est ici, et ce qui n'y est pas
 *
 * Les libellés, la mise en forme des nombres, et l'état affiché de la case d'arrêt automatique.
 * Les bornes et les valeurs admises sont dans [AudioSettings] ; les choix sont dans
 * [AudioCount]. Séparer les deux permet d'éprouver les phrases sans ouvrir un écran.
 */
object AudioSettingsText {

    /**
     * Le titre de la feuille.
     *
     * Le client d'origine n'a pas de titre pour ce bloc : on y arrive par le lecteur de poche,
     * dont l'en-tête dit « Écouter un récitateur ». Ici, on y arrive par la ligne « Réglages
     * audio » de la feuille d'options, et c'est donc **ce nom-là** que l'écran reprend : le
     * titre d'un écran doit être celui du bouton qui l'ouvre, sinon on croit s'être trompé.
     */
    const val TITLE: String = "Réglages audio"

    /** Le libellé du bouton de fermeture, lu par les lecteurs d'écran. */
    const val CLOSE: String = "Fermer les réglages audio"

    const val RECITER_LABEL: String = "Récitateur"

    /** Le libellé du champ libre, tel quel — il porte lui-même ses bornes. */
    const val CUSTOM_PLACEHOLDER: String = "Nombre personnalisé (1 à 999)"

    const val REPEAT_LABEL: String = "Répétitions"

    const val MODE_PASSAGE: String = "Passage complet"

    const val MODE_EACH_VERSE: String = "Chaque verset"

    const val SPEED_LABEL: String = "Vitesse"

    const val GAP_LABEL: String = "Pause entre deux écoutes"

    /** Ce qu'on lit pour un silence nul. Le client d'origine écrit « Aucune ». */
    const val GAP_NONE: String = "Aucune"

    const val AUTO_STOP: String = "Arrêter à la fin des écoutes"

    const val START: String = "Lancer ce passage"

    const val RESTART: String = "Recommencer le passage"

    /**
     * La note sur le silence de sécurité.
     *
     * Elle n'est pas décorative : sans elle, un silence réglé à « Aucune » laisserait croire
     * qu'il n'y a **aucune** attente entre deux versets, alors qu'une marge technique subsiste
     * toujours. Le chiffre est lu dans [Audio.DEFAULT_AYAH_GAP_MS], pour que la phrase ne
     * puisse pas mentir si la marge change.
     */
    fun technicalMarginNote(): String =
        "Une marge technique de ${Audio.DEFAULT_AYAH_GAP_MS} ms reste active entre les versets."

    /**
     * Un silence : « 2 s », « 10 s », ou [GAP_NONE].
     *
     * Zéro s'écrit en mots, comme dans le client d'origine : « 0 s » se lirait comme une durée,
     * alors qu'il s'agit d'une absence.
     */
    fun gapLabel(seconds: Int): String = if (seconds > 0) "$seconds s" else GAP_NONE

    /**
     * Une vitesse : « 0,75× », « 1× », « 1,25× ».
     *
     * La virgule est celle du client d'origine, qui écrit `String(valeur).replace('.', ',')`.
     * Un entier perd sa partie décimale — « 1× » et non « 1,0× » — parce que c'est la vitesse
     * par défaut et qu'elle doit se lire sans bruit.
     */
    fun speedLabel(speed: Float): String = "${formatSpeed(speed)}×"

    /** La ligne du récitateur : « Récitateur : Hafs ‘an ‘Âsim ». */
    fun reciterLine(name: String): String = "$RECITER_LABEL : $name"

    /**
     * L'état affiché de la case « Arrêter à la fin des écoutes ».
     *
     * Le client d'origine écrit `selected={autoStop && countChoice!=='continuous'}` : quand la
     * répétition est illimitée, la case paraît **décochée** même si le réglage vaut vrai. Ce
     * n'est pas une incohérence à corriger mais la vérité de l'écran — arrêter à la fin des
     * écoutes n'a aucun sens s'il n'y a pas de fin — et la personne peut malgré tout cocher la
     * case, ce qui remet le réglage à vrai pour plus tard.
     */
    fun autoStopShown(settings: AudioSession): Boolean =
        settings.autoStop && settings.countChoice != AudioCount.CONTINUOUS

    /**
     * Le nombre tel qu'il s'écrit sur un choix de répétition : `3`, `20`, ou `∞`.
     *
     * Distinct de `countLabelOf` du mini-lecteur, qui lit le **réglage effectif**. Ici, il
     * s'agit du libellé du bouton, qui vient de [AudioCount.label].
     */
    fun countLabel(count: AudioCount): String = count.label

    private fun formatSpeed(speed: Float): String =
        if (speed == speed.toInt().toFloat()) {
            speed.toInt().toString()
        } else {
            speed.toString().replace('.', ',')
        }
}
