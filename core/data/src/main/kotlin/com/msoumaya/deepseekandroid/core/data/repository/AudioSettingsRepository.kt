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
 * ## Ce que ce dépôt ne fait pas
 *
 * Il n'enregistre rien côté serveur. Le client d'origine range ces deux clés hors de toute notion
 * de compte, et c'est le bon choix : la façon d'écouter tient à l'appareil. Le récitateur, lui,
 * est **aussi** rapporté à l'état synchronisé (`AppState.audioPreferences`) par un autre chemin,
 * qui n'est pas encore branché.
 */
class AudioSettingsRepository(private val store: JsonFileStore<StoredAudioSettings>) {

    private val _settings = MutableStateFlow(AudioSession())
    private val _reciterId = MutableStateFlow<String?>(null)

    /** Les réglages de répétition, tels qu'ils doivent être appliqués. */
    val settings: StateFlow<AudioSession> = _settings.asStateFlow()

    /** Le récitateur choisi, ou `null` si aucun ne l'a jamais été. */
    val reciterId: StateFlow<String?> = _reciterId.asStateFlow()

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
    }

    /**
     * Enregistre les deux morceaux d'un coup, et publie.
     *
     * Une seule écriture pour les deux, parce que la feuille les change au même moment : deux
     * écritures laisseraient entre elles un instant où le document porterait un réglage et pas
     * l'autre, et une coupure à cet instant précis perdrait le second.
     */
    suspend fun save(session: AudioSession, reciterId: String?) {
        store.update { it.copy(repeat = session.stored(), reciterId = reciterId) }
        // Publié après l'écriture, et non avant : la valeur annoncée est celle qui est sur le
        // disque. L'annoncer d'abord ferait dire « enregistré » à un état qui ne l'est pas.
        _settings.value = session
        _reciterId.value = reciterId
    }
}
