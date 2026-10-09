package com.msoumaya.deepseekandroid.core.domain

/**
 * Les règles du signalement de problème — **pures**, donc éprouvables sans appareil.
 *
 * Porté depuis `src/services/problemReports.ts`. Ce qui décide vit ici ; ce qui copie un fichier,
 * range une file ou parle au serveur vit ailleurs. La raison est celle de tout le portage : la
 * décision se prouve en quelques secondes, alors qu'un sélecteur d'image, un compartiment de
 * stockage et une file sur disque ne se prouvent que sur un appareil.
 *
 * ## Ce que ce fichier porte, et ce qu'il ne porte pas
 *
 * Il porte les **bornes** — 500 caractères, 5 Mo, 32 caractères de version —, la **forme** de
 * l'adresse d'une capture, et les deux tolérances du dépôt : celle d'un fichier déjà déposé, et
 * celle d'un signalement dont la confirmation manque.
 *
 * Il ne porte **aucun geste** : il ne copie rien, ne dépose rien, ne relit rien. Il ne connaît ni
 * Android, ni Supabase, ni Compose.
 *
 * ## Deux règles de l'original qui disparaissent ici, et pourquoi
 *
 *  - `problemTypes.includes(type)` — la nature est une **énumération** ([com.msoumaya.deepseekandroid.core.model.ProblemReportType]),
 *    donc un type inconnu n'est pas une valeur à refuser mais une valeur qu'on ne peut pas
 *    écrire. La colonne garde son `check` côté serveur.
 *  - le test `String(error.statusCode) === 'Duplicate'` — voir [uploadFailureIsBenign].
 *
 * Ce ne sont pas des règles oubliées, et chacune est nommée là où elle s'applique.
 */
object ProblemReports {

    // -------------------------------------------------------------------------------------------
    // 1. Les bornes
    // -------------------------------------------------------------------------------------------

    /**
     * Longueur maximale de la description, **après rognage**.
     *
     * C'est le `length(btrim(description)) between 1 and 500` de `supabase/problem-reports.sql`
     * (ligne 6). La borne est la même des deux côtés, et c'est nécessaire : le client compte sur
     * le texte rogné, le serveur aussi, et compter sur le texte brut refuserait ici une
     * description de 500 caractères suivie d'un espace — que le serveur, lui, accepterait.
     */
    const val DESCRIPTION_MAX: Int = 500

    /**
     * Taille maximale d'une capture, en octets.
     *
     * `5*1024*1024` dans l'original, soit **5 242 880** — exactement le `file_size_limit` du
     * compartiment déclaré par `supabase/problem-reports.sql` (ligne 22). Le nombre est le même
     * des deux côtés, et un test le fige : si l'un des deux changeait, une capture acceptée par
     * l'écran serait refusée par le compartiment, et la personne ne l'apprendrait qu'après avoir
     * attendu un dépôt.
     *
     * La comparaison de l'original est **stricte** (`>`) : une capture de très exactement
     * 5 242 880 octets passe. C'est aussi ce que fait le compartiment, qui refuse ce qui dépasse.
     */
    const val SCREENSHOT_MAX_BYTES: Long = 5L * 1024L * 1024L

    /**
     * Longueur maximale de la version de l'application.
     *
     * `length(app_version)<=32` dans le schéma (ligne 8). Le client ne la contrôle pas — il
     * écrit la version de son paquet —, et cette borne est donc **déclarative** : elle existe
     * pour qu'un test tienne l'accord avec la colonne, comme `Notifications.preferenceColumns`
     * tient l'accord des préférences.
     */
    const val APP_VERSION_MAX: Int = 32

    // -------------------------------------------------------------------------------------------
    // 2. Les formes acceptées
    // -------------------------------------------------------------------------------------------

    const val MIME_JPEG: String = "image/jpeg"
    const val MIME_PNG: String = "image/png"

    const val EXTENSION_JPG: String = "jpg"
    const val EXTENSION_PNG: String = "png"

    /** Le suffixe reconnu dans une adresse, quand le sélecteur ne dit pas le type MIME. */
    private const val PNG_SUFFIX: String = ".png"

    /**
     * Les plateformes que la colonne `platform` accepte.
     *
     * `check(platform in('ios','android','web'))` (ligne 8). Ce client n'en écrit qu'**une** —
     * [PLATFORM_ANDROID] —, et les deux autres sont déclarées pour que le test d'accord avec le
     * schéma puisse les nommer : une liste qui ne porterait que la sienne ne prouverait rien de
     * l'accord.
     */
    const val PLATFORM_IOS: String = "ios"
    const val PLATFORM_ANDROID: String = "android"
    const val PLATFORM_WEB: String = "web"

    /**
     * Le statut d'un signalement qui vient d'être envoyé.
     *
     * C'est le seul que ce client écrit, et il est **obligatoire** : la politique d'insertion du
     * schéma exige `with check(user_id=auth.uid() and status='open')` (ligne 18). Un signalement
     * rejoué par la file doit donc encore porter cette valeur — c'est la raison pour laquelle
     * elle voyage dans la ligne au lieu d'être laissée à la valeur par défaut de la colonne, que
     * PostgREST appliquerait aussi mais que rien, côté client, ne rappellerait.
     *
     * `resolved` n'est pas déclaré : c'est l'administrateur qui l'écrit, depuis le web, et ce
     * client ne le lit ni ne l'écrit. Une énumération à deux membres dont un seul est atteignable
     * serait une fiction, et non une règle.
     */
    const val STATUS_OPEN: String = "open"

    // -------------------------------------------------------------------------------------------
    // 3. La description
    // -------------------------------------------------------------------------------------------

    /**
     * Ce qui empêche d'envoyer une description, ou `null` si rien ne l'empêche.
     *
     * L'original écrit `if(!problemTypes.includes(type)||!text||text.length>500)`. Il ne reste ici
     * que les deux contrôles de texte : le premier a disparu avec l'énumération (voir la note de
     * tête de [ProblemReports]).
     *
     * **Le texte est rogné avant d'être mesuré**, et l'ordre des deux contrôles est celui de
     * l'original : une description faite d'espaces est **vide** (et non « trop courte »), et c'est
     * `!text` qui la refuse là-bas comme ici. Une description de 501 caractères dont les deux
     * derniers sont des espaces est donc **acceptée** : c'est le comportement de l'original, et le
     * rogner ici serait un écart que rien à l'écran ne montrerait.
     */
    fun descriptionProblem(description: String): ProblemReportProblem? {
        val texte = description.trim()
        if (texte.isEmpty()) return ProblemReportProblem.DESCRIPTION_EMPTY
        if (texte.length > DESCRIPTION_MAX) return ProblemReportProblem.DESCRIPTION_TOO_LONG
        return null
    }

    // -------------------------------------------------------------------------------------------
    // 4. La capture
    // -------------------------------------------------------------------------------------------

    /**
     * Ce qui empêche d'envoyer une capture, ou `null` si rien ne l'empêche.
     *
     * **Deux contrôles, deux phrases**, et c'est l'original : le format est refusé par
     * `chooseProblemScreenshot` (« Choisis une capture au format JPEG ou PNG. »), la taille par
     * les deux (« La capture doit faire moins de 5 Mo. »). Les réunir sous une seule phrase ferait
     * dire à l'écran « capture invalide » là où l'original dit laquelle des deux choses ne va pas.
     *
     * **Le format est contrôlé avant la taille**, comme dans l'original : une image de 6 Mo au
     * format GIF reçoit le message de format. Inverser les deux changerait le message, et pas
     * seulement son ordre.
     */
    fun attachmentProblem(mime: String, sizeBytes: Long): ProblemReportAttachmentProblem? {
        if (!isAcceptedMime(mime)) return ProblemReportAttachmentProblem.FORMAT_UNSUPPORTED
        if (sizeBytes > SCREENSHOT_MAX_BYTES) return ProblemReportAttachmentProblem.TOO_LARGE
        return null
    }

    /** Vrai si le type MIME est l'un des deux que le compartiment accepte. */
    fun isAcceptedMime(mime: String): Boolean = mime == MIME_PNG || mime == MIME_JPEG

    /**
     * Le type MIME d'une capture, celui que le sélecteur a donné ou celui que l'adresse dit.
     *
     * Transcrit de `asset.mimeType ?? (/\.png$/i.test(asset.uri) ? 'image/png' : 'image/jpeg')`.
     * Le repli sur l'extension n'est pas une politesse : le sélecteur d'images d'Android rend un
     * type MIME pour la plupart des fournisseurs, mais pas pour tous, et une capture dont le type
     * serait refusé à cause d'un `null` serait une capture qu'on ne peut pas envoyer.
     *
     * **Le repli est `.png` ou rien.** Toute autre extension — `.jpg`, `.gif`, `.webp` — donne
     * `image/jpeg`. C'est exactement ce que fait l'original, et c'est une imprécision assumée :
     * une image `.gif` sans type MIME serait **déclarée** JPEG et déposée comme telle. La
     * corriger ici ferait diverger les deux clients sur ce qui est acceptable, et le serveur, lui,
     * ne lit que le type déclaré.
     *
     * La comparaison d'extension **ignore la casse** (`/i`) et porte sur la fin de l'adresse
     * (`$`). `endsWith(…, ignoreCase = true)` est la traduction exacte : en JavaScript, `$` sans
     * l'option `m` ne reconnaît que la fin de la chaîne.
     */
    fun resolveMime(declared: String?, uri: String): String =
        declared ?: if (uri.endsWith(PNG_SUFFIX, ignoreCase = true)) MIME_PNG else MIME_JPEG

    /**
     * L'extension du fichier, déduite du type MIME.
     *
     * `mime === 'image/png' ? 'png' : 'jpg'` — une comparaison **exacte**, et non une recherche :
     * un type MIME qui porterait « png » ailleurs qu'à la fin (`image/png;charset=utf-8`) donne
     * `jpg`. C'est le comportement de l'original, et il est sans conséquence visible, parce que
     * les deux seules valeurs possibles sont celles de [resolveMime].
     *
     * Les deux extensions sont les seules que le `check` de `screenshot_path` accepte.
     */
    fun extensionFor(mime: String): String = if (mime == MIME_PNG) EXTENSION_PNG else EXTENSION_JPG

    /**
     * L'adresse d'une capture dans le compartiment : `userId/id.extension`.
     *
     * **Le premier segment est l'identifiant du compte**, comme pour les récitations, et la
     * contrainte `screenshot_path` du schéma (ligne 7) l'exige : la colonne n'accepte que
     * `user_id::text||'/'||id::text||'.jpg'` ou la même avec `.png`. Un fichier rangé à la racine
     * ne serait protégeable par aucune politique.
     *
     * La forme est donc écrite **deux fois** — ici, et dans le `check` —, et c'est ce qui rend le
     * désaccord coûteux : une adresse que le client compose différemment ferait refuser
     * l'insertion, et le signalement resterait dans la file pour toujours. Un test fige les deux
     * formes.
     */
    fun screenshotPath(userId: String, id: String, extension: String): String =
        "$userId/$id.$extension"

    // -------------------------------------------------------------------------------------------
    // 5. Les deux tolérances du dépôt
    // -------------------------------------------------------------------------------------------

    /** Le code d'état HTTP d'un fichier déjà présent. */
    const val CONFLICT_STATUS: String = "409"

    /**
     * Le nom que le client d'origine lit dans `error.statusCode` quand le fichier existe déjà.
     *
     * **Cette valeur n'est pas atteignable depuis ce portage**, et c'est mesuré : dans
     * `supabase-kt-3.8.0`, `RestException.statusCode` est un `Int` (relevé par `javap` sur
     * l'artefact livré), là où le client JavaScript de stockage y range une **chaîne**, qui vaut
     * `'Duplicate'` dans ce cas. Le littéral est conservé pour que la tolérance soit portée telle
     * qu'elle est écrite, et la comparaison de message ci-dessous couvre le cas réel.
     */
    const val DUPLICATE_STATUS: String = "Duplicate"

    /**
     * `true` si l'échec d'un dépôt de capture ne doit pas arrêter la file.
     *
     * Transcrit de
     * `if(error && !['409','Duplicate'].includes(String(error.statusCode)) && !/already exists|duplicate/i.test(error.message)) throw error`.
     *
     * Le refus d'un fichier déjà présent signifie que **les octets sont arrivés** : l'étape est
     * faite, et il ne manque que la ligne. Le cas se produit réellement quand un envoi précédent a
     * déposé la capture puis s'est arrêté avant d'écrire la ligne — le renvoi du fichier est alors
     * refusé, et traiter ce refus comme une panne laisserait le signalement dans la file **pour
     * toujours**, en renvoyant à chaque tentative des octets que le serveur a déjà.
     *
     * La comparaison de message est **insensible à la casse** et cherche le mot **au milieu** du
     * texte, comme l'expression régulière de l'original : ce texte est composé par le serveur, et
     * sa forme exacte n'est pas un contrat.
     *
     * Un message absent ne rend pas `true`. Sans texte, rien ne prouve que l'échec est bénin, et
     * le tenir pour tel ferait écrire une ligne vers une capture qui n'existe pas — la même règle
     * que [Recitations.uploadFailureIsBenign].
     */
    fun uploadFailureIsBenign(statusCode: String?, message: String?): Boolean {
        if (statusCode == CONFLICT_STATUS || statusCode == DUPLICATE_STATUS) return true
        if (message.isNullOrEmpty()) return false
        val texte = message.lowercase()
        return texte.contains("already exists") || texte.contains("duplicate")
    }

    // -------------------------------------------------------------------------------------------
    // 6. L'issue d'un envoi
    // -------------------------------------------------------------------------------------------

    /**
     * Faut-il tenter le dépôt maintenant ?
     *
     * L'original interroge le réseau avant d'essayer
     * (`if(!network.isConnected||network.isInternetReachable===false)return 'queued'`) et
     * n'essaie donc rien hors connexion. La condition est **exactement** [estHorsLigne] : la même
     * règle stricte que celle du bandeau, réutilisée au lieu d'être réécrite — deux écritures de
     * la même condition finiraient par diverger, et le bandeau dirait « hors ligne » pendant que
     * la file essaierait quand même.
     *
     * **Ce que la réponse ne décide pas.** Ne pas tenter n'est pas échouer : l'entrée reste dans
     * la file, et c'est [outcome] qui dit ce qu'on annonce à la personne. L'original sépare les
     * deux de la même façon — il rend `'queued'` sans rien écrire, et la file garde l'entrée.
     */
    fun shouldAttempt(horsLigne: Boolean): Boolean = !horsLigne

    /**
     * Ce qu'on annonce après avoir tenté : envoyé, ou en attente.
     *
     * **L'issue se lit sur la file, et non sur la tentative.** C'est la dernière ligne de
     * `sendProblemReport` : `return db.getFirstSync('SELECT id FROM problem_report_queue WHERE id=?',id) ? 'queued' : 'sent'`.
     * Un dépôt qui lève, un dépôt hors connexion et un dépôt refusé mènent tous au même état
     * observable — l'entrée est encore là —, et c'est celui-là qu'il faut annoncer.
     *
     * Un booléen rendu par la tentative aurait été une **seconde** source de la même vérité, et
     * deux sources finissent par se contredire : un dépôt qui réussit en laissant l'entrée — parce
     * qu'une confirmation a manqué — aurait annoncé « envoyé » sur un signalement encore en
     * attente.
     */
    fun outcome(stillQueued: Boolean): ProblemReportOutcome =
        if (stillQueued) ProblemReportOutcome.QUEUED else ProblemReportOutcome.SENT
}

/** Ce qui empêche d'envoyer la description d'un signalement. */
enum class ProblemReportProblem {
    /** La description est vide, une fois rognée. */
    DESCRIPTION_EMPTY,

    /** La description dépasse [ProblemReports.DESCRIPTION_MAX] caractères, une fois rognée. */
    DESCRIPTION_TOO_LONG,
}

/** Ce qui empêche de joindre une capture. */
enum class ProblemReportAttachmentProblem {
    /** Le type MIME n'est ni JPEG ni PNG. */
    FORMAT_UNSUPPORTED,

    /** Le fichier dépasse [ProblemReports.SCREENSHOT_MAX_BYTES] octets. */
    TOO_LARGE,
}

/**
 * Ce qu'un envoi annonce à la personne.
 *
 * Deux valeurs, et elles ne disent pas la même chose : [SENT] affirme que le signalement est
 * arrivé, [QUEUED] qu'il est **gardé** et partira seul. L'original les distingue par deux phrases
 * différentes, et les confondre ferait dire « envoyé » sur quelque chose qui n'a pas bougé — ou
 * l'inverse, ce qui ferait renvoyer un signalement déjà arrivé.
 */
enum class ProblemReportOutcome {
    /** Le signalement est arrivé sur le serveur, et la file ne le porte plus. */
    SENT,

    /** Le signalement est gardé sur l'appareil et partira à la prochaine connexion. */
    QUEUED,
}
