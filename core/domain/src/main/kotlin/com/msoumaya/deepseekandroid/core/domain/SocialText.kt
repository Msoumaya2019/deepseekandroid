package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.GroupRole
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

    /**
     * Le mot du bouton qui ouvre une conversation, dans la liste d'amis.
     *
     * **Un libellé que la liste n'avait plus.** Le bouton avait été omis tant que la conversation
     * n'existait pas ; il revient avec elle, et avec le mot de l'original.
     */
    const val MESSAGE = "Message"

    fun optionsFor(name: String): String = "Options pour $name"

    /**
     * Libellé d'accessibilité du geste d'ouverture d'une ligne d'ami.
     *
     * **Pourquoi la ligne en a besoin.** Elle ne porte aucun mot qui annonce qu'elle ouvre une
     * conversation : qui écoute l'écran n'entendrait que le nom, le résumé et l'aperçu, et rien ne
     * lui dirait que la ligne est actionnable. L'original porte ce libellé.
     */
    fun openConversationWith(name: String): String = "Ouvrir la conversation avec $name"

    /** Libellé d'accessibilité du bouton « Message » d'une ligne d'ami. */
    fun messageTo(name: String): String = "Message à $name"

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

    // --- Conversation ---

    /** Retour à la liste, depuis l'en-tête d'une conversation. */
    const val BACK_TO_FRIENDS = "← Mes amis"

    /** L'autre est en train d'écrire. C'est une affirmation sur un tiers, donc elle est figée ici. */
    const val TYPING = "Écrit un message…"

    const val REPORT = "Signaler"

    /**
     * Libellé d'accessibilité du geste de signalement.
     *
     * Le texte visible — « Signaler » — ne dit pas **de quelle** conversation il s'agit, et c'est
     * justement ce qu'une personne qui écoute l'écran a besoin d'entendre. L'original porte le
     * même libellé.
     */
    fun reportConversationWith(name: String): String = "Signaler la conversation avec $name"
    const val REPORT_TITLE = "Signaler cette conversation à la modération"
    const val REPORT_PLACEHOLDER = "Motif du signalement"
    const val REPORT_SENT = "Signalement envoyé."
    const val NOTHING_TO_REPORT = "Aucun message reçu à signaler dans cette conversation."

    const val SEND = "Envoyer"
    const val COMPOSER = "Écris un message à tes amis…"
    const val DISCUSSION = "Discussion libre"
    const val LOAD_OLDER = "Charger les messages précédents"
    const val LOADING_OLDER = "Chargement…"

    /**
     * Première lecture des messages.
     *
     * **Un libellé que l'original n'a pas, et pourquoi il en faut un.** Là-bas, la première
     * lecture affichait le titre « Discussion libre » suivi de rien : un écran vide se lit
     * « personne ne t'a jamais écrit », qui est une affirmation fausse sur un tiers tant que la
     * donnée est en vol. C'est exactement le raisonnement de [LOADING] pour la liste d'amis.
     */
    const val LOADING_MESSAGES = "Chargement des messages…"
    const val DELETE = "Supprimer"
    const val SHARE_STEP = "Partager volontairement mon étape"
    const val STEP_SHARED = "Étape partagée avec cet ami."
    const val TOOLS = "Profil et entraide"
    const val TOOLS_CLOSE = "Masquer les options"
    const val CHALLENGE = "🏆 Défier"

    /**
     * Nom du cercle de l'administration.
     *
     * Le serveur, lui, le nomme « Contact · <nom> » : ce libellé-ci est celui que l'écran écrit
     * quand il ouvre ce cercle, et il vient de l'original (`name:'Administration'`).
     */
    const val ADMIN_CIRCLE_NAME = "Administration"

    /**
     * Titre de repli d'un cercle dont on ne retrouve pas le nom.
     *
     * **Un libellé que l'original n'a pas, et pourquoi il en faut un.** Là-bas, le nom du cercle
     * était **capturé au clic** et conservé dans l'état de l'écran : il restait affiché même si
     * le cercle disparaissait de la liste. Ici, la pièce ouverte ne porte que son identifiant, et
     * le nom se relit dans la liste des cercles — qui peut ne plus le contenir, après une
     * relecture partielle. Sans repli, l'en-tête serait **vide**, ce qui se lirait comme un écran
     * cassé. « Cercle » est faux sans être trompeur : c'est le seul mot qui reste vrai.
     */
    const val CIRCLE = "Cercle"

    // --- Aperçu du profil d'un ami ---

    const val PRIVATE_PROGRESS = "Progression privée"

    /** Première ligne de la carte d'aperçu : le nom, puis la présence. */
    fun friendLine(name: String, online: Boolean): String =
        "$name · ${if (online) ONLINE else OFFLINE}"

    /** Deuxième ligne : l'objectif et son taux. Le « % » vient de [ProgressText.percent]. */
    fun goalReached(label: String, percent: Int): String =
        "$label · objectif atteint : ${ProgressText.percent(percent)}"

    fun weekStats(verses: Int, sessions: Int): String =
        "Cette semaine : $verses versets · $sessions séances"

    fun currentPassage(reference: String): String = "Passage actuel : $reference"

    // --- Objectif partagé ---

    const val SHARED_GOAL_TITLE = "Objectif partagé"
    const val SHARED_GOAL_HINT =
        "Fixez ensemble un nombre de séances pour cette semaine. Chacun garde son propre programme."
    const val GOAL_PLACEHOLDER = "Séances cette semaine (1 à 14)"
    const val PROPOSE_GOAL = "Proposer cet objectif"

    fun weekGoal(weekStart: String, sessions: Int): String =
        "Semaine du $weekStart · $sessions séances"

    const val GOAL_ACCEPTED = "Accepté par vous deux"
    const val GOAL_PENDING = "En attente d’acceptation"

    // --- Rendez-vous ---

    const val APPOINTMENT_TITLE = "Rendez-vous de révision"
    const val APPOINTMENT_PLACEHOLDER = "AAAA-MM-JJ HH:mm"
    const val PROPOSE_APPOINTMENT = "Proposer un rendez-vous"

    /** Avis affiché quand la date saisie n'est pas une date future lisible. */
    const val BAD_APPOINTMENT = "Entre une date et une heure futures au format AAAA-MM-JJ HH:mm."

    const val APPOINTMENT_CONFIRMED = "Confirmé"
    const val APPOINTMENT_PENDING = "En attente"

    // --- Membres d'un cercle ---

    fun membersCount(count: Int): String = "Membres ($count/${Social.MAX_GROUP_MEMBERS})"

    const val MEMBER_PENDING = "invitation en attente"
    const val JOIN = "Rejoindre"
    const val NAME_MODERATOR = "Nommer modérateur"
    const val UNNAME_MODERATOR = "Retirer la modération"
    const val REMOVE_FROM_GROUP = "Retirer du cercle"
    const val DELETE_GROUP = "Supprimer le cercle"
    const val DELETE_GROUP_TITLE = "Supprimer le cercle ?"
    const val DELETE_GROUP_BODY = "Les messages de ce cercle seront supprimés définitivement."

    fun inviteMember(name: String): String = "Inviter $name"

    /**
     * Rôle d'un membre, **traduit**.
     *
     * **Écart assumé.** L'original affiche `{m.role}`, c'est-à-dire le littéral de la base —
     * « owner », « moderator », « member ». C'est le vocabulaire du serveur qui fuit dans une
     * interface française, et l'écrire tel quel ferait lire « owner » à la personne qui gère son
     * cercle. Les trois mots sont donc traduits, et l'écart est déclaré ici plutôt que découvert
     * un jour en comparant les deux clients.
     */
    fun role(role: GroupRole): String = when (role) {
        GroupRole.OWNER -> "Propriétaire"
        GroupRole.MODERATOR -> "Modérateur"
        GroupRole.MEMBER -> "Membre"
    }

    /**
     * Une ligne de membre : « Nom · Rôle », et la mention d'invitation en attente.
     *
     * Le rôle arrive **déjà traduit** par [role] : le traduire ici mêlerait deux décisions, et le
     * jour où l'une changerait, l'autre suivrait sans qu'on l'ait voulu.
     */
    fun memberLine(name: String, role: String, pending: Boolean): String =
        if (pending) "$name · $role · $MEMBER_PENDING" else "$name · $role"

    // --- Récitation jointe ---

    const val RECORDING_MISSING = "Enregistrement indisponible"
    const val RECORDING_GONE = "Cet enregistrement n’est plus disponible."
    const val LISTEN = "▶ Écouter la récitation"
    const val PAUSE = "Pause"

    /**
     * Durée d'un enregistrement joint, sous sa référence : « Durée : 1:05 ».
     *
     * Elle réemploie [duration], la même que l'accusé d'un message vocal : deux formats pour la
     * même durée donneraient deux lectures du même enregistrement.
     */
    fun durationLabel(ms: Long): String = "Durée : ${duration(ms / 60_000, (ms / 1_000) % 60)}"

    // --- Partage d'étape ---

    /**
     * Le texte envoyé quand on partage son étape.
     *
     * Le pourcentage est **arrondi** comme dans l'original (`Math.round`), et sa mise en forme
     * vient de [ProgressText.percent] : deux écrans qui annoncent le même objectif ne doivent pas
     * l'écrire de deux façons.
     *
     * Le paramètre est le **ratio** rendu par `Program.progress` — un nombre entre 0 et 1 —, et
     * non un pourcentage déjà calculé : arrondir deux fois ferait perdre un point au passage.
     */
    fun sharedProgress(goalLabel: String, goalRatio: Double, weekVerses: Int): String =
        "Mon objectif $goalLabel est atteint à " +
            "${ProgressText.percent(Math.round(goalRatio * 100).toInt())}. " +
            "Cette semaine, j’ai appris $weekVerses versets."

    // --- Heures ---

    /**
     * Heure d'un message, sous le nom de son auteur : « 18:00 ».
     *
     * C'est `toLocaleTimeString('fr-FR', {hour:'2-digit', minute:'2-digit'})` de l'original, et
     * la locale est **fixée** — voir la note de [FR]. Un instant illisible rend `null` plutôt
     * qu'un texte d'erreur, pour la même raison que [dayStamp] : l'appelant sait alors qu'il n'a
     * rien à écrire, au lieu d'écrire « Invalid Date ».
     */
    fun clock(iso: String): String? =
        runCatching { Instant.parse(iso) }.getOrNull()?.atZone(Dates.zone())?.format(CLOCK)

    /**
     * Instant complet d'un rendez-vous : « 10/03/2026 18:00:00 ».
     *
     * C'est `toLocaleString('fr-FR')` **sans options** de l'original, dont le format par défaut
     * porte les secondes. Les retrancher ferait diverger les deux clients sur la même date : un
     * rendez-vous se lit à la minute, la seconde n'y sert à rien, mais elle est là dans l'autre
     * application.
     */
    fun appointmentStamp(iso: String): String? =
        runCatching { Instant.parse(iso) }.getOrNull()?.atZone(Dates.zone())?.format(FULL_STAMP)

    /**
     * En-tête d'un message : « Moi · 18:00 ».
     *
     * **Écart assumé.** L'original écrit `{auteur} · {new Date(...).toLocaleTimeString(...)}`
     * sans regarder si l'instant est lisible : sur une date corrompue, il affiche donc
     * « Untel · Invalid Date ». Ici l'heure absente disparaît **avec son séparateur** — laisser
     * « Untel · » en suspens afficherait une ponctuation qui n'annonce rien.
     *
     * Le séparateur est celui du reste du fichier — espace, point médian, espace — et il n'est
     * **pas** insécable, pas plus que dans l'original. C'est dit ici pour qu'on ne le « corrige »
     * pas un jour en croyant à un oubli.
     */
    fun messageStamp(sender: String, clock: String?): String =
        if (clock == null) sender else "$sender · $clock"

    private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", FR)

    private val FULL_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", FR)
}
