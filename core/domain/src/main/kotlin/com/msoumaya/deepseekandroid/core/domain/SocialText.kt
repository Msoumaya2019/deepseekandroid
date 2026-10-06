package com.msoumaya.deepseekandroid.core.domain

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Libellés de l'espace « Amis ».
 *
 * Porté depuis `src/SocialScreens.tsx`. Comme [ProgressText], ces chaînes vivent dans le
 * domaine : « En ligne », « Lu », « En attente d'acceptation » disent à la personne ce que
 * l'autre fait ou n'a pas fait, et un mot faux y est une affirmation fausse sur un tiers.
 *
 * **Une coïncidence à ne pas confondre.** L'original écrit `'En ligne'` à deux endroits qui
 * n'ont rien à voir : le **filtre** de la liste d'amis et la **présence** d'un ami. Les deux
 * constantes portent donc la même valeur, et elles sont déclarées séparément — les fusionner
 * ferait qu'un jour l'une des deux changerait seule, et l'autre suivrait sans qu'on l'ait
 * voulu.
 */
object SocialText {

    // --- Filtre de la liste d'amis ---

    const val FILTER_ALL = "Tous"

    /** Libellé du filtre « En ligne ». Voir la note de classe : ce n'est pas [ONLINE]. */
    const val FILTER_ONLINE = "En ligne"

    const val FILTER_REQUESTS = "Demandes"

    // --- Présence ---

    /** Présence d'un ami. Voir la note de classe : ce n'est pas [FILTER_ONLINE]. */
    const val ONLINE = "En ligne"
    const val OFFLINE = "Hors ligne"

    // --- Identités ---

    /** Nom de l'auteur d'un message quand c'est soi-même. */
    const val ME = "Moi"

    /** Dernier repli du nom d'un auteur : ni soi-même, ni un membre, ni un ami connu. */
    const val MEMBER = "Membre"

    /** Repli du nom d'un ami dans la liste, quand le lien ne porte pas son profil. */
    const val FRIEND = "Ami"

    /** Repli du nom dans une phrase : « Invitation de **un membre** ». */
    const val A_MEMBER = "un membre"

    /** Repli du nom dans un libellé d'action : « Message à **cet ami** ». */
    const val THIS_FRIEND = "cet ami"

    // --- Accusés de lecture ---

    const val READ = "Lu"
    const val SENT = "Envoyé"

    // --- Confirmations ---

    /** Confirmation d'un geste sans message propre : accepter, refuser, débloquer, créer. */
    const val SAVED = "Enregistré."

    /** Confirmation d'une demande d'ami envoyée. */
    const val INVITATION_SENT = "Invitation envoyée."

    /** Confirmation d'une copie dans le presse-papiers. */
    const val CODE_COPIED = "Code d'invitation copié."

    /**
     * Corps d'un aperçu que le serveur rend **sans texte**.
     *
     * La fonction `friend_inbox` ne rend un corps vide que pour un lien dont le dernier message
     * a été supprimé, et elle écrit alors elle-même « Message supprimé ». Ce repli n'est donc
     * atteint que par un serveur plus ancien ; il est conservé pour ne pas inventer un
     * troisième comportement entre les deux clients.
     */
    const val NEW_MESSAGE = "Nouveau message"

    // --- Écran ---

    const val TITLE = "Mes amis"
    const val SUBTITLE = "Apprenez et progressez ensemble"

    /** Affiché tant que le profil n'est pas lu, quand un compte est ouvert. */
    const val LOADING = "Chargement de tes amis…"

    /** Affiché quand aucun compte n'est ouvert. Ce n'est pas une erreur, c'est une invitation. */
    const val SIGN_IN = "Connecte-toi à ton compte pour utiliser les amis."

    const val SEARCH_PLACEHOLDER = "Rechercher un ami…"

    /** Repli du sous-titre d'une conversation qui n'a jamais commencé. */
    const val START_CHAT = "Commencer une discussion"

    /** Affiché quand aucun ami n'est accepté. */
    const val NO_FRIENDS = "Invite un ami pour commencer une conversation."

    const val SEE_ALL = "Voir tout"
    const val COLLAPSE = "Réduire"

    /** Titre de la section, avec le nombre d'amis acceptés. */
    fun friendsCount(count: Int): String = "Mes amis ($count)"

    // --- Invitation ---

    const val INVITE_TITLE = "Invitez vos amis"
    const val INVITE_SUBTITLE = "Partagez votre code d'invitation et apprenez le Coran ensemble."
    const val COPY = "Copier"
    const val SHARE_LINK = "Partager mon lien d'invitation"
    const val WHY_TITLE = "Pourquoi inviter des amis ?"
    const val WHY_TEXT = "Restez motivé, progressez ensemble et partagez cette belle aventure."
    const val INVITE_SECTION = "Inviter un ami"
    const val CODE_PLACEHOLDER = "Code d'invitation"
    const val SEND_INVITATION = "Envoyer l'invitation"

    /**
     * Le lien profond partagé.
     *
     * Le schéma `coranmemoire://` est celui du client d'origine, et il est **conservé tel quel**
     * : c'est le même qui a été publié dans les partages déjà envoyés, et le changer ici ferait
     * pointer les anciens liens vers rien.
     */
    fun shareLink(code: String): String = "Rejoins-moi sur Apprendre le Coran : coranmemoire://friend/$code"

    // --- Invitations et blocages ---

    const val RECEIVED = "Invitations reçues"
    const val ACCEPT = "Accepter"
    const val DECLINE = "Refuser"
    const val UNBLOCK = "Débloquer"

    fun invitationFrom(name: String): String = "Invitation de $name"

    fun invitationTo(name: String): String = "Invitation envoyée à $name"

    fun blocked(name: String): String = "$name bloqué"

    // --- Gestion d'une amitié ---

    const val MANAGE = "Gérer cette amitié"
    const val REMOVE = "Retirer"
    const val BLOCK = "Bloquer"
    const val CANCEL = "Annuler"

    fun optionsFor(name: String): String = "Options pour $name"

    // --- Cercles ---

    const val TOGGLE_OPTIONS = "Invitations et cercles privés"
    const val TOGGLE_OPTIONS_CLOSE = "Réduire les options"
    const val CIRCLES = "Cercles privés · 3 à 5 personnes"
    const val GROUP_PLACEHOLDER = "Nom du cercle"
    const val CREATE_GROUP = "Créer un cercle"
    const val OPEN = "Ouvrir"

    // --- Contact administrateur ---

    const val ADMIN_CONTACT = "Contacter l'admin"
    const val ADMIN_OPENING = "Ouverture…"
    const val ADMIN_NOTE = "Une conversation privée, sans invitation d'amitié."

    /**
     * Nature du cercle de l'administration.
     *
     * L'original écrit ce libellé sous le titre de la conversation ouverte avec l'administration,
     * et pour cause : le nom du cercle ne le dit pas — le serveur le nomme « Contact · <nom> ».
     * Le portage l'emploie au même usage, mais dans la liste des cercles, puisque la
     * conversation n'existe pas encore.
     */
    const val ADMIN_CONTACT_LABEL = "Contact administrateur"

    // --- Modération ---

    /** Bandeau de suspension. Le motif est celui que la modération a écrit. */
    fun suspended(reason: String): String = "Messagerie suspendue : $reason"

    // --- Erreurs ---

    /** Repli quand l'erreur ne porte ni message, ni détail, ni code. */
    const val GENERIC_ERROR = "Une erreur est survenue. Réessaie dans un instant."

    /** Repli du client d'origine quand l'erreur n'est pas un objet. */
    const val CONNECTION_NEEDED = "Connexion nécessaire. Réessaie."

    /** Repli quand l'erreur ne porte qu'un code de service. */
    fun serviceError(code: String): String = "Erreur de service ($code)."

    /**
     * Durée d'un enregistrement, en `m:ss`.
     *
     * Les secondes sont complétées à deux chiffres — « 1:05 », jamais « 1:5 » —, et les
     * minutes ne le sont pas : c'est le format du client d'origine.
     */
    fun duration(minutes: Long, seconds: Long): String =
        "$minutes:${seconds.toString().padStart(2, '0')}"

    // --- Dates ---

    /**
     * Locale des formats, **fixée** et non celle de l'appareil.
     *
     * Le client d'origine demande explicitement `fr-FR` à `toLocaleDateString` : il ne prend pas
     * la locale du téléphone. Un appareil réglé en anglais afficherait « Jan 2, 10:00 » là où
     * l'autre affiche « 2 janv. 10:00 », et les deux clients montreraient alors la même
     * conversation de deux façons différentes. Même règle que [ProgramText] et [ReviewText].
     */
    private val FR = Locale.FRANCE

    /**
     * Instant d'un message, tel qu'il est écrit sous le nom d'un ami.
     *
     * Le motif est celui demandé par l'original — `day:'numeric', month:'short', hour:'2-digit',
     * minute:'2-digit'` —, soit « 2 janv. 10:00 ».
     *
     * **`null` plutôt qu'une chaîne vide ou un texte d'erreur.** Le client d'origine écrit
     * « Invalid Date » pour un instant illisible, ce qui n'apprend rien à personne. Ici, un
     * instant illisible est traité comme **absent** : l'appelant retombe alors sur
     * [START_CHAT], qui est vrai — on ne sait pas quand la conversation a commencé, mais on
     * sait qu'aucun message lisible ne l'a ouverte.
     */
    fun dayStamp(iso: String): String? =
        runCatching { Instant.parse(iso) }
            .getOrNull()
            ?.atZone(Dates.zone())
            ?.format(STAMP)

    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm", FR)
}
