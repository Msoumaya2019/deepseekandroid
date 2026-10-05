package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.local.JsonFileStore
import com.msoumaya.deepseekandroid.core.domain.AudioSession
import com.msoumaya.deepseekandroid.core.domain.StoredAudioSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Les réglages d'écoute de l'appareil : lus une fois, publiés, enregistrés à chaque changement.
 *
 * Porté depuis les deux clés d'`AsyncStorage` de `src/PassageAudioPlayer.tsx`
 * (`audio-repeat-preferences` et `audio-reciter-hafs`), réunies dans un seul document — voir
 * [StoredAudioSettings].
 *
 * ## Pourquoi un `StateFlow`, et non un simple `Flow`
 *
 * Le lecteur a besoin de ces valeurs **au moment où il s'ouvre**, pas d'un flux à observer : il
 * les reçoit comme valeur initiale, puis les tient lui-même le temps de la séance. Un `StateFlow`
 * dit exactement cela — il y a toujours une valeur, et c'est la dernière connue. La lecture du
 * disque est faite une seule fois, par [prime], appelée au démarrage de l'application ; ensuite,
 * seule l'écriture touche encore le fichier.
 *
 * ## La relecture est tolérante, champ par champ
 *
 * [AudioSession.fromStored] est appliquée à la lecture : une préférence abîmée retombe sur sa
 * valeur par défaut **sans emporter ses voisines**. C'est la règle du client d'origine, et c'est
 * aussi ce qui garantit qu'un document à moitié écrit ne rend pas l'écoute impossible.
 *
 * ## Ce que ce dépôt ne fait pas, et qui n'est plus un manque
 *
 * Il n'enregistre rien côté serveur. C'est le bon partage : les **répétitions** tiennent à
 * l'appareil, et n'ont jamais suivi la personne d'un appareil à l'autre. Le **récitateur**, lui,
 * est double — l'appareil s'en souvient hors ligne, et le compte s'en souvient partout. La
 * seconde mémoire n'est pas ici : elle vit dans `AppState.audioPreferences`, et c'est la route du
 * lecteur qui la lit et l'écrit, après avoir demandé à `ReciterPreference` laquelle des deux
 * retenir.
 *
 * Ce dépôt ne décide donc **jamais** quel récitateur appliquer : il garde ce qu'on lui donne, et
 * publie ce qu'il a lu. La règle de préférence n'a ainsi qu'un seul endroit, et elle est
 * éprouvable sans disque.
 */
class AudioSettingsRepository(private val store: JsonFileStore<StoredAudioSettings>) {

    private val _settings = MutableStateFlow(AudioSession())
    private val _reciterId = MutableStateFlow<String?>(null)
    private val _reciterOwnerId = MutableStateFlow<String?>(null)

    /** Les réglages de répétition, tels qu'ils doivent être appliqués. */
    val settings: StateFlow<AudioSession> = _settings.asStateFlow()

    /** Le récitateur retenu par l'appareil, ou `null` si aucun ne l'a jamais été. */
    val reciterId: StateFlow<String?> = _reciterId.asStateFlow()

    /**
     * À qui appartient [reciterId], ou `null` s'il n'appartient à personne.
     *
     * `null` n'est pas « personne » au sens d'un défaut : c'est l'état d'un document écrit avant
     * que la portée par utilisateur existe, et il est **adoptable** — voir [ReciterPreference].
     */
    val reciterOwnerId: StateFlow<String?> = _reciterOwnerId.asStateFlow()

    /**
     * Lit le document et publie ce qu'il contient.
     *
     * Lancée une fois par le bloc `init` du conteneur, donc au démarrage de l'application : un
     * lecteur ouvert dans les premières secondes doit pouvoir partir des valeurs enregistrées, et
     * le premier réglage de la personne ne doit pas écraser un choix antérieur — ce qu'un départ
     * sur les valeurs par défaut ferait sans le dire.
     *
     * Rien ne garantit pour autant que la lecture ait abouti quand le lecteur s'ouvre : c'est
     * pourquoi le lecteur, de son côté, adopte la valeur publiée **tant que la personne n'a rien
     * réglé elle-même** — voir `ReaderScreen`. Le comportement ne dépend donc pas d'une course
     * entre une lecture de fichier et un appui.
     *
     * Une lecture impossible — fichier absent, document illisible — publie les valeurs par
     * défaut. Un premier démarrage n'est pas une panne, et `JsonFileStore` met de côté un
     * document illisible au lieu de l'écraser.
     */
    suspend fun prime() {
        val stored = store.current()
        _settings.value = AudioSession.fromStored(stored.repeat)
        _reciterId.value = stored.reciterId
        _reciterOwnerId.value = stored.reciterOwnerId
    }

    /**
     * Enregistre les deux morceaux d'un coup, et publie.
     *
     * Une seule écriture pour les deux, parce que la feuille les change au même moment : deux
     * écritures laisseraient entre elles un instant où le document porterait un réglage et pas
     * l'autre, et une coupure à cet instant précis perdrait le second.
     *
     * [ownerId] est **exigé**, sans valeur par défaut, et c'est volontaire : un appel qui
     * l'omettrait effacerait le propriétaire, et rendrait la valeur adoptable par le compte
     * suivant — soit exactement la fuite que la portée par utilisateur empêche. Un appelant qui
     * écrit vraiment un récitateur sait à qui il appartient ; celui qui ne le sait pas n'a rien à
     * écrire ici.
     */
    suspend fun save(session: AudioSession, reciterId: String?, ownerId: String?) {
        store.update { it.copy(repeat = session.stored(), reciterId = reciterId, reciterOwnerId = ownerId) }
        // Publié après l'écriture, et non avant : la valeur annoncée est celle qui est sur le
        // disque. L'annoncer d'abord ferait dire « enregistré » à un état qui ne l'est pas.
        _settings.value = session
        _reciterId.value = reciterId
        _reciterOwnerId.value = ownerId
    }

    /**
     * Retient à qui appartient le récitateur déjà enregistré, sans toucher aux répétitions.
     *
     * C'est l'écriture d'une **décision**, et non d'un geste : `ReciterPreference` a dit que la
     * valeur retenue était celle du compte, ou qu'une valeur sans propriétaire était adoptée, et
     * l'appareil apprend ainsi ce que le compte sait. Les deux champs s'écrivent ensemble — un
     * propriétaire nommé sans sa valeur laisserait le document porter le choix d'un **autre**
     * compte sous le nom du compte courant.
     *
     * L'écriture est évitée quand rien ne change : la route appelle cette méthode à chaque
     * composition où la décision est recalculée, et un document réécrit pour rien userait le
     * disque sans rien apprendre.
     */
    suspend fun remember(reciterId: String, ownerId: String) {
        if (_reciterId.value == reciterId && _reciterOwnerId.value == ownerId) return
        store.update { it.copy(reciterId = reciterId, reciterOwnerId = ownerId) }
        _reciterId.value = reciterId
        _reciterOwnerId.value = ownerId
    }
}
