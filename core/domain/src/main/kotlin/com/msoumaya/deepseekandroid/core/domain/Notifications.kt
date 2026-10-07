package com.msoumaya.deepseekandroid.core.domain

/**
 * Ce qu'une notification **décide**, et rien de ce qu'elle **transporte**.
 *
 * Porté depuis `src/services/notifications.ts` : les littéraux de charge utile (lignes 7 à 12), la
 * porte de présentation (`setNotificationHandler`, lignes 26 à 39), l'identité d'une notification
 * (lignes 28 à 34), les quatre canaux (lignes 48 à 54), la correspondance des préférences (lignes
 * 128 à 133), le rappel d'apprentissage (lignes 135 à 143), la notification de test (lignes 145 à
 * 148) et `notificationDestination` (lignes 159 à 169).
 *
 * ## Ce qui est porté, et ce qui ne peut pas l'être
 *
 * Le fichier d'origine fait **deux** choses que ce portage sépare :
 *
 *  - il **décide** — quelle notification s'affiche, où mène un toucher, quel identifiant la
 *    dédoublonne, quel canal la reçoit, ce que dit le rappel de 19 h ;
 *  - il **transporte** — il enregistre un jeton auprès de Supabase et laisse le système afficher.
 *
 * La première moitié est du raisonnement pur : elle ne connaît ni `expo-notifications`, ni le
 * réseau, ni Compose, et s'éprouve donc **sans appareil**. C'est elle qui vit ici. La seconde
 * moitié n'est pas portable telle quelle, et le document `SUPABASE_COMPATIBILITY.md` dit
 * **pourquoi**, mesure en main : la colonne `push_devices.expo_push_token` porte une contrainte
 * `check` sur la forme du jeton Expo, et le serveur envoie par `https://exp.host/…`, deux choses
 * qu'un client Android natif ne peut pas satisfaire. Ce portage ne feint donc pas d'avoir livré
 * les notifications : il livre **tout ce qui ne dépend pas du propriétaire du projet**, et nomme
 * ce qui reste.
 *
 * ## La charge utile est un dictionnaire de chaînes, et c'est une décision
 *
 * L'original lit `data` — un `Record<string, unknown>` venu d'un JSON — et teste partout
 * `typeof x === 'string'`. Le portage prend donc un `Map<String, String?>` et pose comme
 * **contrat d'appel** que l'appelant n'y met que les valeurs qui sont des chaînes. Une valeur d'un
 * autre type — un nombre, un objet — doit être **omise**, ce qui reproduit exactement le test de
 * type de l'original : absent et mal typé mènent au même `null`.
 *
 * La conséquence est visible et voulue : une chaîne **vide** passe le test de type de l'original,
 * donc elle est conservée ici aussi. C'est [Destination.Conversation] qui porte ce cas, et son
 * commentaire dit qui, ensuite, refuse cette valeur.
 *
 * ## Ce que cette règle ne fait pas
 *
 * Elle n'affiche rien, ne planifie rien, ne demande aucune permission et ne connaît ni Android ni
 * Firebase. Elle ne choisit pas non plus le **texte** d'une notification reçue : le serveur
 * l'écrit. Elle ne connaît que les deux textes que le **client** fabrique lui-même — le rappel
 * d'apprentissage et la notification de test — et les tient pour ce qu'ils sont : des constantes
 * figées par un test, parce qu'un libellé qui dérive ne casse rien.
 */
object Notifications {

    // ---------------------------------------------------------------------------------------
    // 1. Les littéraux de la charge utile
    // ---------------------------------------------------------------------------------------

    /**
     * Les `kind` que le client sait lire, **tels que le serveur les écrit**.
     *
     * Ce sont des chaînes et non une énumération, et c'est mesuré : la charge utile vient d'un JSON
     * dont le serveur choisit le contenu, et les douze fonctions SQL du dossier `supabase` qui en
     * émettent une n'appartiennent pas toutes au même fichier. Une énumération exigerait un membre
     * « inconnu » et une conversion qui peut échouer ; ici, un `kind` que ce fichier ne connaît pas
     * tombe simplement dans le `else` de [destination] et dans l'absence de garde de [present],
     * c'est-à-dire qu'il **s'affiche sans mener nulle part** — exactement ce que fait l'original.
     *
     * Ces valeurs sont **figées par un test** : ce sont elles que le serveur écrit, et les renommer
     * ici ferait disparaître une notification sans qu'aucune compilation ne s'en plaigne.
     */
    const val LEARNING_REMINDER: String = "learning-reminder"
    const val REVISION_REMINDER: String = "revision-reminder"
    const val PRIVATE_MESSAGE: String = "private-message"
    const val FRIEND_PROGRESS: String = "friend-progress"
    const val RECITATION_CORRECTED: String = "recitation-corrected"
    const val ADMIN_REMINDER: String = "admin-reminder"
    const val QUIZ_DAILY: String = "quiz-daily"
    const val QUIZ_CHALLENGE: String = "quiz-challenge"
    const val QUIZ_RESULT: String = "quiz-result"
    const val FRIEND_REQUEST: String = "friend-request"
    const val FRIEND_ACCEPTED: String = "friend-accepted"
    const val NOTIFICATION_TEST: String = "notification-test"

    /** Les douze littéraux, dans l'ordre où la source les déclare. */
    val kinds: List<String> = listOf(
        LEARNING_REMINDER,
        REVISION_REMINDER,
        PRIVATE_MESSAGE,
        FRIEND_PROGRESS,
        RECITATION_CORRECTED,
        ADMIN_REMINDER,
        QUIZ_DAILY,
        QUIZ_CHALLENGE,
        QUIZ_RESULT,
        FRIEND_REQUEST,
        FRIEND_ACCEPTED,
        NOTIFICATION_TEST,
    )

    // ---------------------------------------------------------------------------------------
    // 2. Où mène un toucher
    // ---------------------------------------------------------------------------------------

    /**
     * La destination d'un toucher de notification.
     *
     * Cinq formes, et pas une de plus : l'original en rend cinq, plus `null`. `Program` et
     * `Reviews` n'ont pas de paramètre parce que la source n'en porte pas non plus — l'écran se
     * suffit à lui-même.
     */
    sealed interface Destination {

        /** L'onglet « Programme » : le rappel d'apprentissage et celui du professeur y mènent. */
        data object Program : Destination

        /** L'espace Révisions — **sous la garde** de [revisionsOpen], voir plus bas. */
        data object Reviews : Destination

        /**
         * L'écran de Quiz.
         *
         * @param challengeId le défi à ouvrir, ou `null` pour la question du jour. L'original
         *   écrit `undefined` dans ce cas, et l'écran ouvre alors sans défi : les trois `kind` de
         *   quiz partagent la même destination, mais **seul** `quiz-challenge` et `quiz-result`
         *   portent un identifiant.
         */
        data class Quiz(val challengeId: String?) : Destination

        /** L'écran des récitations, ouvert sur la récitation corrigée. */
        data class Recitation(val recitationId: String) : Destination

        /**
         * Une discussion privée.
         *
         * **Le `linkId` vide passe.** L'original teste `typeof data.linkId === 'string'`, et une
         * chaîne vide satisfait ce test : [destination] rend donc bien `Conversation("")`. Ce qui
         * refuse ensuite cette valeur n'est pas ici — c'est le consommateur, qui écrit
         * `if(account && pendingLinkId)` et se fie à la fausseté de la chaîne vide en JavaScript.
         * Le portage Android doit donc porter cette garde **de son côté** : c'est la raison pour
         * laquelle elle est nommée ici plutôt que corrigée en silence, car la corriger ici ferait
         * diverger [destination] de la source sans que rien ne le dise.
         */
        data class Conversation(val linkId: String) : Destination
    }

    /**
     * La destination d'une charge utile, ou `null` quand elle ne mène nulle part.
     *
     * **L'ordre des cas porte tout le sens**, et il est celui de l'original : les trois `kind` de
     * quiz d'abord, puis les deux rappels, puis la correction, puis les trois familles de
     * discussion. Un `private-message` **sans** `linkId` ne mène nulle part — la notification
     * s'affiche et le toucher ne fait rien, ce qui est le comportement de la source et non un
     * oubli.
     *
     * @param donnees la charge utile ; seules les valeurs **qui sont des chaînes** doivent y
     *   figurer, voir l'en-tête de [Notifications].
     */
    fun destination(donnees: Map<String, String?>): Destination? {
        val kind = donnees["kind"]
        return when (kind) {
            QUIZ_DAILY, QUIZ_CHALLENGE, QUIZ_RESULT -> Destination.Quiz(donnees["challengeId"])

            ADMIN_REMINDER, LEARNING_REMINDER -> Destination.Program

            REVISION_REMINDER -> Destination.Reviews

            RECITATION_CORRECTED -> donnees["recitationId"]?.let { Destination.Recitation(it) }

            PRIVATE_MESSAGE, FRIEND_PROGRESS, FRIEND_REQUEST, FRIEND_ACCEPTED ->
                donnees["linkId"]?.let { Destination.Conversation(it) }

            else -> null
        }
    }

    /**
     * La garde du consommateur sur la destination « révisions ».
     *
     * Transcrit de `App.tsx`, ligne 155 : `destination.kind === 'reviews' && reviewsEnabled(...)`.
     * L'espace Révisions peut être **éteint** par l'élève, et un rappel de révision qui arrive
     * pendant ce temps ne doit pas ouvrir un écran qui n'existe plus : il ne fait rien.
     *
     * Cette règle vit ici, et non dans le consommateur Android, pour une raison précise : le
     * consommateur est du Compose, donc hors de portée d'un test sans appareil, et la garde y
     * serait invisible — un rappel qui ouvrirait un écran éteint ne casserait rien, il serait
     * simplement faux. Le défaut de [Review] est `enabled != false`, c'est-à-dire **ouvert par
     * défaut** : c'est `reviewsEnabled` de `src/core/review.ts`, ligne 9.
     */
    fun revisionsOpen(enabled: Boolean?): Boolean = enabled != false

    // ---------------------------------------------------------------------------------------
    // 3. L'identité, et le dédoublonnage
    // ---------------------------------------------------------------------------------------

    /**
     * Le plafond de l'ensemble des notifications déjà vues.
     *
     * Mesuré : `if(displayedMessages.size>200)displayedMessages.clear()`.
     */
    const val DISPLAYED_CAP: Int = 200

    /**
     * L'identifiant qui dédoublonne une notification, ou `null` quand elle n'en porte pas.
     *
     * Trois sources, et **la précédence compte** : `messageId`, puis la correction, puis le rappel
     * du professeur. Deux conséquences que l'original produit et que ce portage reproduit :
     *
     *  - un `messageId` **présent** gagne, **quel que soit** le `kind` — une correction qui
     *    porterait un `messageId` serait identifiée par lui, et non par son couple ;
     *  - un `messageId` **vide** ne gagne pas : la source écrit `messageId || correctionId`, et
     *    l'opérateur `||` de JavaScript saute la chaîne vide. C'est ce que fait
     *    `ifEmpty { null }` ici.
     *
     * **Le couple d'une correction n'est jamais vide** : il s'écrit `"${recitationId}:${revision}"`
     * et le `revision` absent vaut la chaîne vide, si bien qu'un `recitationId` vide donne `":"` —
     * une chaîne **vraie**, donc retenue. Deux corrections sans identifiant se dédoublonneraient
     * donc l'une l'autre. Le cas est improbable — le serveur envoie un `uuid` — mais il est écrit
     * ici plutôt que découvert plus tard.
     */
    fun identity(donnees: Map<String, String?>): String? {
        val message = donnees["messageId"]?.ifEmpty { null }
        if (message != null) return message

        if (donnees["kind"] == RECITATION_CORRECTED) {
            val recitationId = donnees["recitationId"]
            if (recitationId != null) {
                return "$recitationId:${donnees["revision"] ?: ""}"
            }
        }

        if (donnees["kind"] == ADMIN_REMINDER) {
            val notificationId = donnees["notificationId"]
            if (notificationId != null) return notificationId.ifEmpty { null }
        }

        return null
    }

    // ---------------------------------------------------------------------------------------
    // 4. La porte de présentation
    // ---------------------------------------------------------------------------------------

    /** L'état de l'application, tel que l'original le lit : `active`, ou tout le reste. */
    enum class Visibility { ACTIVE, BACKGROUND }

    /**
     * Ce que le client sait au moment où une notification arrive.
     *
     * Les quatre drapeaux viennent de l'état du compte, et leurs **défauts diffèrent** — c'est
     * [preferencesFrom] qui les porte, et non cette classe, parce que le défaut appartient à la
     * préférence et non à la porte.
     */
    data class Context(
        /** La discussion ouverte, ou `null`. */
        val activeLinkId: String?,
        /** L'écran des récitations est-il à l'écran ? */
        val recitationsVisible: Boolean,
        /** L'application est-elle au premier plan ? */
        val visibility: Visibility,
        val messagesEnabled: Boolean,
        val progressEnabled: Boolean,
        val correctionsEnabled: Boolean,
        val adminEnabled: Boolean,
    )

    /**
     * Ce qu'il faut faire d'une notification qui arrive.
     *
     * @param show vrai si le système doit l'afficher — bandeau, liste **et** son.
     * @param identity l'identifiant retenu, ou `null` : c'est aussi celui qui vient d'être ajouté
     *   à la mémoire, quand il y en a un.
     */
    data class Decision(val show: Boolean, val identity: String?)

    /**
     * Décide si une notification s'affiche, **et retient son identifiant**.
     *
     * Transcrit de `setNotificationHandler`, lignes 26 à 39. Quatre raisons de **ne pas**
     * afficher, et elles sont toutes différentes :
     *
     *  1. [sameChat] — la discussion concernée est **ouverte** et l'application est au premier
     *     plan : la notification serait redondante avec ce qu'on est en train de lire. **La
     *     progression compte comme une discussion** : `isChat` couvre `private-message` **et**
     *     `friend-progress`, si bien qu'une progression partagée arrive en silence quand la
     *     discussion est ouverte, alors qu'elle n'est pas un message.
     *  2. [sameRecitations] — une correction arrive alors que l'écran des récitations est à
     *     l'écran et au premier plan.
     *  3. [duplicate] — cet identifiant a **déjà** été affiché. Le dédoublonnage est un ensemble
     *     qui vit d'un lancement à l'autre, et non une fenêtre glissante.
     *  4. la préférence du `kind` est **éteinte** — et seuls **quatre** `kind` en ont une. Les
     *     trois `kind` de quiz et les deux d'invitation n'en ont **aucune** côté client : c'est le
     *     serveur qui les retient, par `notification_preferences.quiz_enabled` et par le réglage
     *     de la demande d'ami. Un portage qui leur inventerait une garde ici serait plus strict
     *     que la source.
     *
     * ## L'ordre des deux gestes, et pourquoi il est mesuré
     *
     * L'original **calcule** `duplicate` avant d'**ajouter** l'identifiant : la première
     * occurrence s'affiche donc, et la seconde est retenue. Inverser les deux ferait disparaître
     * *toutes* les notifications dédoublonnées, y compris la première — un défaut silencieux, car
     * l'écran resterait parfaitement fonctionnel.
     *
     * ## Le plafond **vide**, il n'évince pas
     *
     * `if(size>200)clear()` : au 201ᵉ identifiant, l'ensemble entier est **vidé** — ce n'est pas
     * un tampon circulaire, et l'identifiant qui vient d'être ajouté part avec les autres. La
     * conséquence est réelle : une notification rejouée juste après le plafond serait affichée
     * une seconde fois. Un portage qui évincerait le plus ancien serait plus économe et **faux**.
     *
     * ## La comparaison de `linkId`, et sa seule divergence
     *
     * L'original écrit `data?.linkId === activeLinkId`, et en JavaScript `undefined === null` est
     * **faux**. Le portage exige donc les deux côtés non nuls avant de comparer : sans quoi
     * l'absence de discussion ouverte **et** l'absence de `linkId` seraient égales en Kotlin
     * (`null == null`) et une notification serait supprimée là où l'original l'affiche. C'est le
     * piège que cette fonction existe pour ne pas retomber dans.
     *
     * La divergence restante est nommée : un `linkId` **explicitement nul** dans la charge utile
     * — que les fonctions serveur n'émettent jamais, elles omettent la clé — serait supprimé ici
     * et pas là-bas. Le contrat d'appel de [destination] rend ce cas impossible : la carte ne porte
     * que des chaînes, donc `null` veut dire « clé absente ».
     *
     * @param dejaVues la mémoire des identifiants déjà affichés, **tenue par l'appelant** : elle
     *   survit aux recompositions et ne survit pas au redémarrage, comme le module de l'original.
     *   Cette fonction l'**écrit**.
     */
    fun present(
        donnees: Map<String, String?>,
        context: Context,
        dejaVues: MutableSet<String>,
    ): Decision {
        val kind = donnees["kind"]
        val identifiant = identity(donnees)

        // Les deux « même endroit » exigent le premier plan : une notification qui arrive alors
        // que l'application est en arrière-plan doit s'afficher, même si l'écran concerné est
        // celui qu'on avait laissé — c'est la lecture qu'on en fait en revenant qui compte.
        val auPremierPlan = context.visibility == Visibility.ACTIVE

        val linkId = donnees["linkId"]
        val isChat = kind == PRIVATE_MESSAGE || kind == FRIEND_PROGRESS
        val memeDiscussion = isChat &&
            linkId != null &&
            context.activeLinkId != null &&
            linkId == context.activeLinkId &&
            auPremierPlan

        val memeRecitations = kind == RECITATION_CORRECTED &&
            context.recitationsVisible &&
            auPremierPlan

        val dejaAffichee = identifiant != null && dejaVues.contains(identifiant)

        val eteinte = (kind == PRIVATE_MESSAGE && !context.messagesEnabled) ||
            (kind == FRIEND_PROGRESS && !context.progressEnabled) ||
            (kind == RECITATION_CORRECTED && !context.correctionsEnabled) ||
            (kind == ADMIN_REMINDER && !context.adminEnabled)

        val show = !(memeDiscussion || memeRecitations || dejaAffichee || eteinte)

        // Le plafond n'est atteint que par une notification **qui porte une identité** : une
        // charge utile sans identifiant n'entre jamais dans la mémoire, donc ne peut pas la vider.
        if (identifiant != null) {
            dejaVues.add(identifiant)
            if (dejaVues.size > DISPLAYED_CAP) dejaVues.clear()
        }

        return Decision(show = show, identity = identifiant)
    }

    // ---------------------------------------------------------------------------------------
    // 5. Les canaux
    // ---------------------------------------------------------------------------------------

    /**
     * Un canal de notification Android.
     *
     * Les quatre de l'original, tous en importance **haute** : `configureNotificationChannels`,
     * lignes 48 à 54. Les libellés portent l'apostrophe **typographique** de la source — un
     * caractère droit casserait l'affichage sans qu'aucune compilation ne s'en plaigne, et c'est
     * le même piège que le `Juz’` de `feature:profile`.
     *
     * L'ordre est celui de la source. Il n'est pas indifférent pour un lecteur humain, et il ne
     * l'est pas pour Android non plus : c'est l'ordre dans lequel les canaux apparaissent dans les
     * réglages du téléphone.
     */
    data class Channel(val id: String, val name: String, val highImportance: Boolean)

    /** Les quatre canaux, dans l'ordre de la source. */
    val channels: List<Channel> = listOf(
        Channel("messages", "Messages privés", highImportance = true),
        Channel("learning", "Rappels d’apprentissage", highImportance = true),
        Channel("corrections", "Corrections des récitations", highImportance = true),
        Channel("admin", "Rappels du professeur", highImportance = true),
    )

    // ---------------------------------------------------------------------------------------
    // 6. Les préférences, et leurs défauts
    // ---------------------------------------------------------------------------------------

    /**
     * Les sept préférences que le client **écrit** au serveur.
     *
     * Sept, alors que `notification_preferences` en porte **neuf** : les deux autres —
     * `quiz_enabled` et `quiz_timezone` — passent par la couche du Quiz (`QuizSource.setNotifications`),
     * et non par ici. C'est mesuré dans `supabase/notifications.sql`, `social-v2.sql`,
     * `admin-notifications.sql`, `notification-corrections.sql` et `quiz-install.sql`.
     *
     * @param revision **toujours faux**, et c'est une divergence de l'original, reproduite et non
     *   corrigée : `App.tsx` ligne 177 écrit `revision: false` en dur, alors que l'écran des
     *   réglages offre bien l'interrupteur « Rappels de révision » et que la colonne
     *   `revision_reminders_enabled` existe. L'interrupteur change donc l'état local **sans
     *   jamais atteindre le serveur**. Le portage garde ce comportement parce qu'il est celui du
     *   client d'origine, et le nomme ici pour qu'il ne soit pas pris pour une décision.
     */
    data class Preferences(
        val messages: Boolean,
        val friendRequests: Boolean,
        val sharedProgress: Boolean,
        val revision: Boolean,
        val corrections: Boolean,
        val adminMessages: Boolean,
        val messagePreview: Boolean,
    )

    /**
     * Les sept préférences et la colonne qui les reçoit, dans l'ordre de `saveNotificationPreferences`.
     *
     * Cette table existe pour être **vérifiée**, pas pour être lue à l'exécution : c'est elle qui
     * permet à un test de tenir l'accord entre les noms du client et ceux du schéma.
     */
    val preferenceColumns: List<Pair<String, String>> = listOf(
        "messages" to "messages_enabled",
        "friendRequests" to "friend_requests_enabled",
        "sharedProgress" to "shared_progress_enabled",
        "revision" to "revision_reminders_enabled",
        "corrections" to "corrections_enabled",
        "adminMessages" to "admin_messages_enabled",
        "messagePreview" to "message_preview_enabled",
    )

    /**
     * Les sept valeurs à écrire, **défauts compris**.
     *
     * Transcrit de `App.tsx` ligne 177, et les défauts **ne sont pas les mêmes** : trois
     * préférences sont **ouvertes** sauf refus explicite (`!== false`), une est **fermée** sauf
     * accord explicite (`=== true`), et une est constante. Confondre les deux formes suffirait à
     * activer la progression partagée pour tout le monde — c'est-à-dire à prévenir les amis d'un
     * élève qui n'a rien demandé.
     *
     * Chaque paramètre est `Boolean?` : `null` veut dire « l'état du compte ne porte pas ce
     * champ », ce que la source écrit `prefs?.messages`.
     */
    fun preferencesFrom(
        messages: Boolean?,
        friendRequests: Boolean?,
        sharedProgress: Boolean?,
        corrections: Boolean?,
        adminMessages: Boolean?,
        messagePreview: Boolean?,
    ): Preferences = Preferences(
        messages = messages != false,
        friendRequests = friendRequests != false,
        sharedProgress = sharedProgress == true,
        // En dur, comme la source : voir la documentation de [Preferences].
        revision = false,
        corrections = corrections != false,
        adminMessages = adminMessages != false,
        messagePreview = messagePreview != false,
    )

    // ---------------------------------------------------------------------------------------
    // 7. Les deux notifications que le client fabrique
    // ---------------------------------------------------------------------------------------

    /**
     * Le rappel d'apprentissage : **19 h 00**, canal `learning`.
     *
     * `syncLearningReminder`, ligne 140. Les deux textes sont figés par un test — ils sont écrits
     * ici et non reçus du serveur, donc un libellé qui dérive ne casserait rien.
     *
     * L'heure est **locale à l'appareil** : l'original demande un déclencheur `DAILY` à 19 h 00,
     * que le système interprète dans le fuseau du téléphone. Un rappel à 19 h 00 UTC serait faux
     * pour tout le monde sauf une partie de l'Europe.
     */
    data class Reminder(
        val hour: Int,
        val minute: Int,
        val channelId: String,
        val title: String,
        val body: String,
    )

    /** Le rappel du soir, tel que l'original le planifie. */
    val learningReminder: Reminder = Reminder(
        hour = 19,
        minute = 0,
        channelId = "learning",
        title = "Ton programme du Coran",
        body = "Retrouve ton passage du jour et prends un moment pour apprendre.",
    )

    /** Le délai de la notification de test, en secondes : `TIME_INTERVAL, seconds:5`. */
    const val TEST_DELAY_SECONDS: Int = 5

    /** Le titre de la notification de test. */
    const val TEST_TITLE: String = "Test des notifications"

    /** Le corps de la notification de test. */
    const val TEST_BODY: String = "Les notifications sont autorisées sur ce téléphone."

    /**
     * Le rappel d'apprentissage s'**annule toujours** avant d'être replanifié.
     *
     * `syncLearningReminder` lit d'abord `getAllScheduledNotificationsAsync`, retire **toutes** les
     * notifications de `kind` `learning-reminder`, puis n'en planifie une que si le réglage est
     * actif **et** la permission accordée. Cet ordre n'est pas une commodité : sans l'annulation,
     * chaque passage de l'effet ajouterait un rappel, et le téléphone sonnerait autant de fois
     * qu'on a ouvert l'écran.
     *
     * @param enabled le réglage `learning` de l'état du compte.
     * @param granted la permission d'afficher des notifications.
     * @return vrai si un rappel doit être planifié **après** l'annulation.
     */
    fun shouldScheduleLearningReminder(enabled: Boolean, granted: Boolean): Boolean =
        enabled && granted
}
