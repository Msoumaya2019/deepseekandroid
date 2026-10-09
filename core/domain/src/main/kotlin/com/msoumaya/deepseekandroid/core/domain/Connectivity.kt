package com.msoumaya.deepseekandroid.core.domain

/**
 * Connectivité réseau — les décisions.
 *
 * Porté depuis `src/services/connectivity.ts` (douze lignes) et de l'usage qu'en fait
 * `src/App.tsx`, où le bandeau s'affiche.
 *
 * ## La frontière du portage
 *
 * L'original lit le réseau par `@react-native-community/netinfo`, un module natif qui n'a pas
 * d'équivalent ici. Ce qui est porté, c'est **ce que le client décide à partir d'une lecture** :
 * quand le bandeau s'affiche, quel texte il porte, combien de temps il reste, et quand la
 * synchronisation est demandée. La lecture elle-même — `ConnectivityManager` — vit côté Android,
 * dans le module qui porte le contexte, et n'est pas éprouvable sans appareil.
 *
 * ## La lecture est stricte, et c'est le premier piège
 *
 * L'original écrit `network.isConnected === false || network.isInternetReachable === false` :
 * deux comparaisons **strictes** à `false`. Une mesure **absente** — `null` en Kotlin,
 * `undefined` en JavaScript — compte donc comme **en ligne**. Ce n'est pas un détail : `NetInfo`
 * rend `null` tant qu'il n'a pas encore mesuré, et le client refuse de déclarer hors ligne sur
 * une mesure qu'il n'a pas.
 *
 * Le portage naïf `isConnected != true` inverserait exactement ce cas — il déclarerait hors ligne
 * tout appareil dont la mesure n'est pas encore arrivée. C'est l'erreur que le banc épingle.
 */

/**
 * Hors ligne si **l'une** des deux mesures dit explicitement `false`.
 *
 * `null` ne dit rien : il compte comme en ligne, comme les deux `=== false` de l'original.
 */
fun estHorsLigne(isConnected: Boolean?, isInternetReachable: Boolean?): Boolean =
    isConnected == false || isInternetReachable == false

/**
 * Une lecture de l'état du réseau, telle que le système la rend.
 *
 * Les deux champs sont nullables et le restent jusqu'au bout : les aplatir en `Boolean` au
 * moment de la lecture perdrait précisément l'information qui distingue « déconnecté » de
 * « pas encore mesuré ».
 */
data class ConnectivityReading(
    val isConnected: Boolean?,
    val isInternetReachable: Boolean?,
) {
    /** Voir [estHorsLigne] : la règle est la même, elle est écrite une seule fois. */
    val horsLigne: Boolean get() = estHorsLigne(isConnected, isInternetReachable)
}

/**
 * L'état gardé entre deux lectures.
 *
 * ## Pourquoi une échéance, et non un booléen
 *
 * L'original garde un booléen `restored` qu'un minuteur éteint, et un handle `timer` qu'il
 * **annule et réarme** à chaque retour : `if(timer)clearTimeout(timer); timer=setTimeout(…)`.
 * Deux retours successifs ne doivent donc pas laisser deux minuteurs vivants, dont le premier
 * éteindrait le bandeau du second avant l'heure.
 *
 * Ici, `restored` est remplacé par l'**instant** où il s'éteint. Le réarmement devient une
 * simple réécriture de cette échéance, et le minuteur orphelin devient **structurellement
 * impossible** — il n'y a plus de handle à oublier d'annuler. Le prix est que la visibilité du
 * bandeau dépend du temps, donc qu'elle se demande **avec** l'instant courant ([bandeauVisible]).
 *
 * @param horsLigne la dernière lecture : hors ligne, ou en ligne.
 * @param retourJusquaMs l'instant où le bandeau de retour s'éteint, ou `null` si aucun retour
 *   n'est affiché. **Préservé** par les lectures qui ne sont pas un retour, comme le booléen de
 *   l'original : reperdre le réseau n'efface pas l'annonce de la réparation en cours.
 */
data class ConnectivityState(
    val horsLigne: Boolean = false,
    val retourJusquaMs: Long? = null,
) {
    /**
     * Le bandeau de retour est-il encore affiché ?
     *
     * Strictement avant l'échéance : à l'instant pile où le minuteur de l'original se
     * déclencherait, le bandeau est éteint.
     */
    fun retablieA(maintenantMs: Long): Boolean =
        retourJusquaMs?.let { maintenantMs < it } == true
}

/** Ce qu'une transition demande de faire au monde extérieur. */
sealed interface ConnectivityEffect {
    /**
     * Lancer la synchronisation des modifications en attente.
     *
     * L'original appelle `flushPendingSync()` **dans** le rappel du retour de réseau. C'est le
     * seul moment où il le fait : rien ne synchronise au démarrage, ni à chaque lecture.
     */
    data object DemanderSynchronisation : ConnectivityEffect
}

/** Le nouvel état, et les effets à exécuter. */
data class ConnectivityTransition(
    val etat: ConnectivityState,
    val effets: List<ConnectivityEffect>,
)

/**
 * Applique une lecture.
 *
 * Reproduit l'ordre exact de l'original :
 *
 *  1. l'état hors ligne prend la valeur de la lecture ;
 *  2. si l'on revient **en ligne** alors qu'on était hors ligne, on annonce le retour — pour
 *     [DUREE_RETOUR_MS] — et on demande la synchronisation ;
 *  3. la mémoire du « on était hors ligne » devient la lecture courante.
 *
 * L'étape 3 fait que l'étape 2 ne peut se déclencher qu'**une fois par panne** : deux lectures en
 * ligne d'affilée n'annoncent rien la seconde fois. Un premier démarrage en ligne, en particulier,
 * n'annonce **aucun** retour — c'est le cas que le banc épingle, parce qu'un « rétabli » affiché
 * au lancement ferait croire à une panne qui n'a pas eu lieu.
 */
fun transition(
    etat: ConnectivityState,
    lecture: ConnectivityReading,
    maintenantMs: Long,
): ConnectivityTransition {
    val down = lecture.horsLigne
    val retour = !down && etat.horsLigne
    return ConnectivityTransition(
        etat = ConnectivityState(
            horsLigne = down,
            retourJusquaMs = if (retour) maintenantMs + DUREE_RETOUR_MS else etat.retourJusquaMs,
        ),
        effets = if (retour) listOf(ConnectivityEffect.DemanderSynchronisation) else emptyList(),
    )
}

/**
 * Le bandeau est-il visible ?
 *
 * L'original écrit `(connectivity.offline || connectivity.restored) && <bandeau/>`. Les deux
 * causes sont **distinctes** — être hors ligne, ou venir de l'être — et c'est pour cela que le
 * bandeau s'affiche aussi pendant les trois secondes qui suivent un retour.
 */
fun bandeauVisible(etat: ConnectivityState, maintenantMs: Long): Boolean =
    etat.horsLigne || etat.retablieA(maintenantMs)

/**
 * Le texte du bandeau.
 *
 * Il dépend de **l'état hors ligne seul**, jamais du retour. Les deux messages ne sont pas deux
 * états du même texte : l'un annonce une panne, l'autre une réparation. Un appareil qui reperd le
 * réseau pendant que « Connexion rétablie » est encore affiché doit donc **repasser** au texte de
 * panne — le bandeau ne reste pas à dire que tout va bien pendant que tout va mal.
 */
fun texteDuBandeau(etat: ConnectivityState): String =
    if (etat.horsLigne) TEXTE_HORS_LIGNE else TEXTE_RETABLIE

/**
 * Le texte du bandeau hors ligne.
 *
 * Le tiret est un **cadratin** (U+2014), comme dans l'original — mesuré, pas supposé. Un portage
 * qui le redresse en trait d'union produit un texte visuellement presque identique et faux, que
 * rien d'autre ne signalerait.
 */
const val TEXTE_HORS_LIGNE: String =
    "Mode hors connexion \u2014 les modifications seront synchronisées automatiquement"

/** Le texte du bandeau de retour, tel quel. */
const val TEXTE_RETABLIE: String = "Connexion rétablie"

/** Durée d'affichage du bandeau de retour, en millisecondes. Portée de `3000`. */
const val DUREE_RETOUR_MS: Long = 3_000
