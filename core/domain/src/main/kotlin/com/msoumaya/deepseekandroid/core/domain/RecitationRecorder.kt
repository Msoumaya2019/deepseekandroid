package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.Range

/**
 * Les décisions de l'enregistreur de récitation.
 *
 * Porté depuis `src/RecitationRecorder.tsx`. Le fichier d'origine fait deux choses à la fois : il
 * **décide** (quelle phase suit quelle autre, quels gestes sont offerts, ce qui empêche de
 * commencer, quel format produire) et il **exécute** (microphone, lecteur, navigation). Seule la
 * première moitié est portée ici, et c'est la seule qui puisse l'être : un `MediaRecorder`, une
 * permission système et un écran ne se prouvent que sur un appareil, alors que ces décisions-ci
 * se prouvent en quelques secondes, sans rien d'autre que la machine qui compile.
 *
 * ## Ce que l'enregistreur natif produit, et pourquoi le format est une décision
 *
 * L'original enregistre avec `RecordingPresets.HIGH_QUALITY` d'`expo-audio`, puis force
 * `numberOfChannels: 1` et `bitRate: 64000`, et rabaisse la qualité audio côté iOS. Le portage
 * n'a pas d'`expo-audio` : il a `MediaRecorder`, et c'est **lui** qui produit le fichier. Le
 * format n'est donc pas un réglage hérité, c'est un choix à faire — et il est fait ici, en un
 * seul endroit, parce que trois autres décisions en dépendent :
 *
 *  - l'**extension** du fichier, dont `Recitations.extensionFor` déduit le type MIME du dépôt ;
 *  - le **type MIME** envoyé au compartiment de stockage, qui doit correspondre aux octets ;
 *  - les **bornes** écrites dans la ligne, qui distinguent un passage d'une invocation.
 *
 * AAC dans un conteneur MPEG-4, mono, 64 kbit/s : c'est ce que le client d'origine demandait
 * (mono, 64 kbit/s), et c'est le format que son `RecordingPresets.HIGH_QUALITY` produisait de
 * toute façon sur Android. Le portage ne choisit donc pas autre chose — il **nomme** ce qui était
 * implicite, et le rend éprouvable.
 *
 * ## Les phases, et ce qui les sépare
 *
 * ```
 *   IDLE ──begin──▶ RECORDING ⇄ PAUSED
 *                      │            │
 *                      └──finish────┴──▶ PREVIEW ──save──▶ SAVED
 *                                           │                 │
 *                                           └────restart──────┴──▶ IDLE
 * ```
 *
 * `PREVIEW` n'existe que pour les enregistrements qu'on ne sauvegarde pas d'emblée : une
 * invocation, ou l'enregistreur réduit, où la personne réécoute avant d'engager quoi que ce soit.
 * Un passage du Coran en pleine page part directement en `SAVED` — c'est ce que fait l'original,
 * et c'est ce qui a été conservé.
 */
object RecitationRecorder {

    /**
     * Débit de l'enregistrement, en bits par seconde.
     *
     * 64 kbit/s, comme l'original. Une récitation corrigée par un professeur se juge à
     * l'articulation et à la prononciation : au-delà, on paie des mégaoctets pour un timbre que
     * personne n'écoute, et le dépôt se fait sur un forfait mobile.
     */
    const val BIT_RATE: Int = 64_000

    /**
     * Nombre de canaux.
     *
     * Un seul. Une voix seule ne porte pas d'information stéréo, et deux canaux doubleraient le
     * poids du fichier pour rien — c'est exactement ce que l'original corrigeait après coup en
     * écrasant le préréglage par `numberOfChannels: 1`.
     */
    const val CHANNELS: Int = 1

    /**
     * Fréquence d'échantillonnage, en hertz.
     *
     * 44 100 Hz est la fréquence que `MediaRecorder` retient par défaut pour l'AAC, et celle que
     * tout décodeur lit. La baisser pour gagner quelques kilo-octets rendrait la prononciation
     * des lettres emphatiques difficile à juger — précisément ce qu'on demande à corriger.
     */
    const val SAMPLE_RATE: Int = 44_100

    /**
     * L'extension du fichier que l'enregistreur produit.
     *
     * Elle est **déduite** de [Recitations.EXTENSION_MP4] et non recopiée : `extensionFor` la
     * reconnaît, `contentType` en tire le type MIME, et les deux doivent parler du même fichier.
     * Une constante écrite deux fois finirait par diverger.
     */
    val RECORDED_EXTENSION: String get() = Recitations.EXTENSION_MP4

    /** Le type MIME des octets produits, tel qu'il part au compartiment de stockage. */
    val RECORDED_MIME_TYPE: String get() = Recitations.contentType(RECORDED_EXTENSION)

    /** La valeur écrite dans la mémoire de l'appareil quand la notice a été acceptée. */
    const val NOTICE_ACCEPTED: String = "yes"

    /**
     * La clé sous laquelle l'acceptation de la notice est retenue, **par utilisateur**.
     *
     * Par utilisateur, comme l'original : la notice parle des récitations d'une personne, et un
     * second compte sur le même appareil ne l'a pas lue. Une clé globale ferait taire la notice
     * pour quelqu'un qui ne l'a jamais vue.
     */
    fun noticeKey(userId: String): String = "recitation-info-$userId"

    /**
     * `true` si la notice a déjà été acceptée.
     *
     * La comparaison est exacte, comme celle de l'original (`informed !== 'yes'`) : toute autre
     * valeur — y compris `"oui"`, ou un document tronqué — vaut « pas encore acceptée ». Le doute
     * penche du côté de la personne à informer, jamais de celui de l'économie d'un écran.
     */
    fun noticeAccepted(stored: String?): Boolean = stored == NOTICE_ACCEPTED

    /**
     * Ce qui empêche de commencer un enregistrement, ou `null` si rien ne l'empêche.
     *
     * **L'ordre est celui de l'original**, et il compte : les trois causes sont contrôlées dans
     * cet ordre-là, et quand plusieurs se présentent ensemble, c'est la première qui est dite.
     * L'inverser changerait le message affiché à quelqu'un qui n'est pas connecté, ce qui n'est
     * pas un détail d'implémentation mais ce que la personne lit.
     *
     *  - le **compte** d'abord : sans lui, l'enregistrement n'appartiendrait à personne et ne
     *    pourrait pas être déposé ;
     *  - la **notice** ensuite : l'original la montre avant de demander la permission du
     *    microphone, pour ne pas réclamer un accès avant d'avoir dit à quoi il sert ;
     *  - le **microphone** en dernier, parce que c'est la seule des trois que le système peut
     *    refuser sans que l'application y puisse rien.
     *
     * La présence du compte est testée par `isNullOrEmpty`, comme la véracité de l'original
     * (`if(!userId)`) : une chaîne vide y passait déjà pour une absence.
     */
    fun startProblem(
        userId: String?,
        noticeAccepted: Boolean,
        microphoneGranted: Boolean,
    ): RecitationStartProblem? {
        if (userId.isNullOrEmpty()) return RecitationStartProblem.OWNER_MISSING
        if (!noticeAccepted) return RecitationStartProblem.NOTICE_PENDING
        if (!microphoneGranted) return RecitationStartProblem.MICROPHONE_DENIED
        return null
    }

    /**
     * `true` si « Terminer » a un effet dans cette phase.
     *
     * L'original l'écrit en garde d'entrée (`if(phase!=='recording'&&phase!=='paused')return`), et
     * cette garde n'est pas décorative : un second appui sur « Terminer » — une main qui tremble,
     * un écran qui n'a pas encore réagi — ne doit pas tenter d'arrêter un enregistreur déjà
     * arrêté, ce que `MediaRecorder` refuse en levant.
     */
    fun canFinish(phase: RecitationPhase): Boolean =
        phase == RecitationPhase.RECORDING || phase == RecitationPhase.PAUSED

    /**
     * La phase qui suit un arrêt **sauvegardé**.
     *
     * Deux raisons de passer par un aperçu : une invocation, dont le texte doit rester sous les
     * yeux pendant qu'on réécoute, et l'enregistreur réduit, où l'aperçu tient lieu de
     * confirmation. Un passage du Coran en pleine page, lui, part directement en `SAVED` — sa
     * référence est déjà affichée, et un écran d'aperçu n'y ajouterait rien.
     */
    fun phaseAfterFinish(isInvocation: Boolean, compact: Boolean): RecitationPhase =
        if (isInvocation || compact) RecitationPhase.PREVIEW else RecitationPhase.SAVED

    /**
     * Les gestes offerts dans une phase, **dans l'ordre où ils sont affichés**.
     *
     * L'ordre est celui de l'original, et il est conservé : c'est une barre d'actions, et
     * permuter deux boutons change le geste que fait le pouce.
     *
     * [canShare] couvre deux conditions que l'original écrit ensemble (`item && onShare`) : il y a
     * une récitation à partager **et** un écran qui sait quoi en faire. Les séparer ici n'aurait
     * pas de sens — dans les deux cas, le bouton n'apparaît pas.
     */
    fun actionsFor(phase: RecitationPhase, canShare: Boolean): List<RecitationAction> = when (phase) {
        RecitationPhase.IDLE -> listOf(RecitationAction.BEGIN)
        RecitationPhase.RECORDING -> listOf(
            RecitationAction.PAUSE,
            RecitationAction.FINISH,
            RecitationAction.CANCEL,
        )

        RecitationPhase.PAUSED -> listOf(
            RecitationAction.RESUME,
            RecitationAction.FINISH,
            RecitationAction.CANCEL,
        )

        RecitationPhase.PREVIEW -> listOf(
            RecitationAction.LISTEN,
            RecitationAction.RESTART,
            RecitationAction.SAVE,
        )

        RecitationPhase.SAVED -> if (canShare) {
            listOf(RecitationAction.LISTEN, RecitationAction.RESTART, RecitationAction.SHARE)
        } else {
            listOf(RecitationAction.LISTEN, RecitationAction.RESTART)
        }
    }

    /**
     * `true` si le geste est mis en avant.
     *
     * Un seul par phase, et c'est celui qu'on attend : commencer, terminer, enregistrer. Les
     * autres sont des issues de secours — pause, annulation, recommencement —, et les mettre au
     * même rang inviterait à annuler un enregistrement qu'on voulait garder.
     */
    fun isPrimary(action: RecitationAction): Boolean = when (action) {
        RecitationAction.BEGIN, RecitationAction.FINISH, RecitationAction.SAVE -> true
        RecitationAction.PAUSE,
        RecitationAction.RESUME,
        RecitationAction.CANCEL,
        RecitationAction.LISTEN,
        RecitationAction.RESTART,
        RecitationAction.SHARE,
        -> false
    }

    /**
     * Le libellé d'un geste **dans la barre réduite**.
     *
     * ## Pourquoi une fonction, et non un champ de l'énumération
     *
     * Le libellé dépend de la **mise en page**, et c'est l'original : la barre réduite dit
     * « Terminer » là où la pleine page dit « Terminer et sauvegarder », et « Partager » là où
     * elle dit « Partager avec un ami ». Un geste qui porterait un seul libellé ne serait plus le
     * même geste selon l'écran qui l'affiche, et l'énumération ne peut pas porter les deux.
     *
     * C'est aussi la forme du dépôt : `ReviewText.actionLabel` est la même correspondance pour la
     * barre de révision, et elle vit dans `core:domain` — les mots sont dans `RecitationText`, et
     * **quelle mise en page dit lequel** est une règle, donc elle s'éprouve sans appareil.
     *
     * ## Les libellés de la pleine page ne sont pas ici, et c'est une mesure
     *
     * Trois d'entre eux dépendent de la **phase**, et pas seulement du geste : en phase d'aperçu
     * la pleine page dit « Réécouter avant sauvegarde », et « Réécouter » une fois l'enregistrement
     * gardé. `RecitationAction.LISTEN` porte donc deux libellés selon la phase où il apparaît, et
     * choisir entre les deux demande la phase — que la barre réduite, elle, n'utilise jamais : son
     * unique libellé est `LISTEN`. Les mots de la pleine page existent déjà
     * (`RecitationText.PAGE_*`) ; la règle qui les choisit arrivera avec la surface qui les
     * affiche, comme `fullLabel` a attendu la sienne.
     *
     * La fonction est **exhaustive et sans `else`** : ajouter un geste à [RecitationAction] sans
     * lui donner de libellé ne compile pas, ce qui vaut mieux qu'un bouton sans mot.
     */
    fun compactActionLabel(action: RecitationAction): String = when (action) {
        RecitationAction.BEGIN -> RecitationText.BAR_BEGIN
        RecitationAction.PAUSE -> RecitationText.PAUSE
        RecitationAction.RESUME -> RecitationText.RESUME
        RecitationAction.FINISH -> RecitationText.BAR_FINISH
        RecitationAction.CANCEL -> RecitationText.CANCEL
        RecitationAction.LISTEN -> RecitationText.LISTEN
        RecitationAction.RESTART -> RecitationText.RESTART
        RecitationAction.SAVE -> RecitationText.SAVE
        RecitationAction.SHARE -> RecitationText.BAR_SHARE
    }

    /**
     * Le temps écoulé, en `MM:SS`.
     *
     * L'original écrit `Math.floor(ms/60000)` et `Math.floor(ms/1000%60)`. Les minutes **ne sont
     * pas repliées** à soixante : une heure d'enregistrement s'affiche `60:00`, et non `00:00`.
     * C'est voulu — un enregistrement d'une heure n'existe pas ici, mais un compteur qui
     * repartirait à zéro au bout d'une heure ferait croire à un redémarrage.
     *
     * **Divergence assumée sur les durées négatives.** L'expression d'origine divise en nombres à
     * virgule : pour `-1` ms elle rend `-1:-1`, ce que l'arithmétique entière de ce portage ne
     * reproduit pas (`-1:59`). Aucun appelant ne peut produire une durée négative — elle vient du
     * compteur du système —, et reproduire une bizarrerie de la division flottante de JavaScript
     * ne dirait rien de la règle. Ce qui compte est reproduit : les deux chiffres, le zéro de
     * tête, et l'absence de repli des minutes.
     */
    fun clock(durationMs: Long): String {
        val minutes = Math.floorDiv(durationMs, 60_000L)
        val seconds = Math.floorMod(Math.floorDiv(durationMs, 1_000L), 60L)
        return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }

    /**
     * `true` tant que l'enregistreur occupe la personne.
     *
     * L'original s'en sert pour prévenir l'écran qui l'abrite (`onRecordingChange`), lequel s'en
     * sert pour empêcher de quitter : partir en pleine capture perdrait l'enregistrement, et
     * `busy` en fait partie — un enregistrement en cours de sauvegarde n'est pas terminé non
     * plus.
     */
    fun isActive(phase: RecitationPhase, busy: Boolean): Boolean =
        busy || phase == RecitationPhase.RECORDING || phase == RecitationPhase.PAUSED

    /** Le titre de l'enregistreur, selon ce qu'il enregistre. */
    fun title(isInvocation: Boolean): String =
        if (isInvocation) RecitationText.RECORDER_TITLE_INVOCATION else RecitationText.RECORDER_TITLE_QURAN

    /**
     * La ligne qui suit le titre : la référence du passage, ou le titre de l'invocation.
     *
     * Le repli de l'original (`invocation.title ?? 'Invocation'`) porte sur `null` **seulement** :
     * un titre vide rend une ligne vide, il ne devient pas « Invocation ». C'est reproduit tel
     * quel — une chaîne vide est un choix du serveur, et le portage ne le corrige pas.
     *
     * Sans passage ni invocation, la ligne est vide plutôt qu'absente : l'original écrit
     * `range ? reference(range) : ''`, et une chaîne vide occupe la même place qu'une ligne
     * présente, ce qui évite que le panneau change de hauteur d'un enregistrement à l'autre.
     */
    fun subtitle(isInvocation: Boolean, invocationTitle: String?, range: Range?): String = when {
        isInvocation -> invocationTitle ?: RecitationText.INVOCATION_FALLBACK
        range != null -> Quran.reference(range)
        else -> ""
    }

    /**
     * Le libellé d'état de la barre réduite.
     *
     * Quatre phases s'y nomment, et la cinquième — `IDLE` — a le libellé de l'invitation à
     * enregistrer. L'original écrit cette chaîne en dernier recours d'un ternaire en cascade :
     * c'est bien un défaut, pas une phase oubliée.
     */
    fun compactLabel(phase: RecitationPhase): String = when (phase) {
        RecitationPhase.RECORDING -> RecitationText.RECORDING
        RecitationPhase.PAUSED -> RecitationText.PAUSED_COMPACT
        RecitationPhase.PREVIEW -> RecitationText.PHASE_PREVIEW
        RecitationPhase.SAVED -> RecitationText.PHASE_SAVED
        RecitationPhase.IDLE -> RecitationText.PHASE_IDLE
    }

    /**
     * Le libellé d'état de la pleine page, ou `null` quand rien n'est en cours.
     *
     * Deux mots diffèrent de la barre réduite, et ce n'est pas une coquille de l'original :
     * « Ⅱ En pause » y porte le symbole, « En pause » non, parce que la pleine page affiche
     * l'état en grand à côté d'un chronomètre, et la barre réduite dans une ligne de texte.
     */
    fun fullLabel(phase: RecitationPhase): String? = when (phase) {
        RecitationPhase.RECORDING -> RecitationText.RECORDING
        RecitationPhase.PAUSED -> RecitationText.PAUSED_FULL
        else -> null
    }

    /**
     * La durée affichée par la barre réduite, ou `null` en `IDLE`.
     *
     * Trois sources, dans cet ordre de priorité, comme l'original : le brouillon en attente
     * d'aperçu, la récitation enregistrée, puis le compteur **vivant** du système. L'ordre
     * compte : pendant une pause, le compteur vivant continue de tourner sur certaines versions
     * d'Android, et afficher sa valeur ferait croire que l'enregistrement n'a pas été suspendu —
     * alors que le brouillon, lui, porte la durée réellement captée.
     *
     * En `IDLE`, il n'y a rien à montrer : l'original n'ajoute la durée que si la phase n'est pas
     * `idle`, et un `00:00` figé à côté de « Enregistrement personnel » se lirait comme un
     * enregistrement vide.
     */
    fun shownDurationMs(
        phase: RecitationPhase,
        draftMs: Long?,
        itemMs: Long?,
        liveMs: Long,
    ): Long? = if (phase == RecitationPhase.IDLE) null else draftMs ?: itemMs ?: liveMs

    /**
     * La durée affichée par la pleine page, ou `null` quand rien n'est en cours.
     *
     * Une seule source ici, et c'est **le compteur vivant** : la pleine page ne montre la durée
     * que pendant la capture, où il n'y a ni brouillon ni récitation enregistrée à lire. Les deux
     * écrans ne lisent donc pas les mêmes sources — c'est l'original, et le confondre
     * afficherait, après un arrêt, la durée du dernier enregistrement à côté du bouton
     * « Enregistrer ma voix ».
     */
    fun fullDurationMs(phase: RecitationPhase, liveMs: Long): Long? =
        if (phase == RecitationPhase.RECORDING || phase == RecitationPhase.PAUSED) liveMs else null
}

/**
 * Les phases d'un enregistrement, dans l'ordre de sa vie.
 *
 * Les noms sont ceux de l'original, à une exception : `IDLE` y est écrit `'idle'` dans un type
 * d'union de chaînes, et devient ici un cas d'énumération — ce qui fait qu'une phase oubliée ne
 * compile pas au lieu de tomber dans le `else` d'un ternaire.
 */
enum class RecitationPhase {
    /** Rien en cours. */
    IDLE,

    /** Le microphone capte. */
    RECORDING,

    /** La capture est suspendue ; le fichier n'est pas fermé. */
    PAUSED,

    /** L'enregistrement est arrêté, la personne peut le réécouter avant de le garder. */
    PREVIEW,

    /** L'enregistrement est rangé sur l'appareil. */
    SAVED,
}

/**
 * Un geste de l'enregistreur.
 *
 * Les libellés ne sont pas portés par l'énumération : ils dépendent de la mise en page — la barre
 * réduite dit « Terminer » là où la pleine page dit « Terminer et sauvegarder » —, et un geste qui
 * porterait deux libellés ne serait plus un geste. Ils vivent dans une **fonction** de
 * [RecitationRecorder] : `compactActionLabel` pour la barre réduite, et la pleine page la sienne
 * quand sa surface arrivera — voir la note de cette fonction, qui dit pourquoi l'un de ses
 * libellés dépend de la phase.
 */
enum class RecitationAction {
    /** Demander le microphone et commencer. */
    BEGIN,

    /** Suspendre la capture sans fermer le fichier. */
    PAUSE,

    /** Reprendre la capture. */
    RESUME,

    /** Arrêter **et** garder. */
    FINISH,

    /** Arrêter et jeter. */
    CANCEL,

    /** Réécouter ce qui vient d'être capté. */
    LISTEN,

    /** Tout reprendre à zéro. */
    RESTART,

    /** Ranger l'enregistrement sur l'appareil. */
    SAVE,

    /** Proposer l'enregistrement à un ami. */
    SHARE,
}

/** Ce qui empêche de commencer un enregistrement. */
enum class RecitationStartProblem {
    /** Personne n'est connecté : l'enregistrement n'appartiendrait à personne. */
    OWNER_MISSING,

    /** La notice qui dit ce que deviennent les récitations n'a pas encore été lue. */
    NOTICE_PENDING,

    /** Le microphone a été refusé : l'application ne peut rien y faire seule. */
    MICROPHONE_DENIED,
}
