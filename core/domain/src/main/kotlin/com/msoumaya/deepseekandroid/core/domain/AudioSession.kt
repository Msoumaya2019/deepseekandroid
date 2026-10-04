package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.RepeatMode
import kotlinx.serialization.Serializable

/**
 * Réglages d'écoute d'un passage, et les bornes qui les rendent valides.
 *
 * Porté depuis `src/PassageAudioPlayer.tsx`. Les valeurs admises ne sont pas décoratives :
 * le client d'origine **refuse** tout ce qui n'y figure pas, et une préférence enregistrée
 * hors bornes est **ignorée au rechargement** — champ par champ — plutôt que de faire échouer
 * l'écran. C'est cette seconde règle qui compte : un réglage corrompu ne doit pas empêcher
 * d'écouter, et il ne doit pas non plus contaminer les réglages voisins.
 */
object AudioSettings {

    /** Nombre d'écoutes proposé au premier lancement. */
    val DEFAULT_COUNT_CHOICE: AudioCount = AudioCount.THREE

    /** Contenu initial du champ « Autre ». */
    const val DEFAULT_CUSTOM_COUNT: String = "20"

    const val MIN_CUSTOM_COUNT: Int = 1
    const val MAX_CUSTOM_COUNT: Int = 999

    /** Le message exact du client d'origine, hors bornes. */
    const val CUSTOM_COUNT_MESSAGE: String = "Choisis entre 1 et 999 écoutes."

    /** Les silences proposés entre deux écoutes, en secondes. */
    val GAP_CHOICES: List<Int> = listOf(0, 2, 5, 10)

    const val DEFAULT_GAP_SECONDS: Int = 0

    /** Les vitesses de lecture proposées. */
    val SPEED_CHOICES: List<Float> = listOf(0.75f, 1f, 1.25f)

    const val DEFAULT_SPEED: Float = 1f

    /**
     * Valide une saisie de nombre d'écoutes.
     *
     * Le client d'origine passe par `Number(...)`, qui accepte `" 20 "` et rend `0` pour une
     * chaîne vide. On garde le même résultat sur ces entrées, mais **pas** sur `"1e2"` ni
     * `"0x10"`, que `Number` convertirait en 100 et 16 : ces écritures n'ont pas de sens dans
     * un champ numérique et les accepter ferait diverger les deux clients sur un nombre
     * d'écoutes. Elles sont refusées, avec le même message.
     */
    fun requireCustomCount(text: String): Int {
        val value = text.trim().toIntOrNull()
        if (value == null || value !in MIN_CUSTOM_COUNT..MAX_CUSTOM_COUNT) {
            error(CUSTOM_COUNT_MESSAGE)
        }
        return value
    }
}

/**
 * Un choix de répétition tel qu'il est présenté.
 *
 * `wire` est la forme enregistrée par le client d'origine (`"1"`, `"custom"`, `"continuous"`).
 * Elle est conservée telle quelle pour que les préférences écrites par l'application React
 * Native restent lisibles ici, et réciproquement.
 */
enum class AudioCount(val label: String, val wire: String) {
    ONE("1", "1"),
    TWO("2", "2"),
    THREE("3", "3"),
    FIVE("5", "5"),
    TEN("10", "10"),
    CUSTOM("Autre", "custom"),
    CONTINUOUS("∞", "continuous"),
    ;

    /** Le nombre fixe, ou `null` pour les deux choix qui n'en portent pas. */
    val fixed: Int?
        get() = when (this) {
            ONE -> 1
            TWO -> 2
            THREE -> 3
            FIVE -> 5
            TEN -> 10
            CUSTOM, CONTINUOUS -> null
        }

    /**
     * Le nombre d'écoutes, ou `null` pour une répétition illimitée.
     *
     * Échoue avec le message du client d'origine si le choix est « Autre » et que la saisie
     * n'est pas un entier entre 1 et 999.
     */
    fun resolve(customText: String): Int? = when (this) {
        CONTINUOUS -> null
        CUSTOM -> AudioSettings.requireCustomCount(customText)
        else -> fixed
    }

    companion object {
        /** Les choix dans l'ordre d'affichage. */
        val ALL: List<AudioCount> = listOf(ONE, TWO, THREE, FIVE, TEN, CUSTOM, CONTINUOUS)

        fun fromWire(wire: String?): AudioCount? = ALL.firstOrNull { it.wire == wire }
    }
}

/**
 * Les préférences telles qu'elles sont enregistrées.
 *
 * Tous les champs sont facultatifs : un enregistrement écrit par une version antérieure peut
 * n'en porter qu'une partie, et un champ illisible ne doit pas faire perdre les autres.
 */
@Serializable
data class StoredAudioPreferences(
    val countChoice: String? = null,
    val customCount: String? = null,
    val repeatMode: String? = null,
    val gap: Int? = null,
    val speed: Float? = null,
    val autoStop: Boolean? = null,
)

/**
 * Les réglages effectifs d'une séance d'écoute.
 *
 * Volontairement **non sérialisable** : ce qui s'enregistre est [StoredAudioPreferences], dont
 * tous les champs sont des chaînes et des nombres. La relecture passe par [fromStored], qui
 * applique la règle « champ hors bornes ignoré, les autres conservés ».
 */
data class AudioSession(
    val countChoice: AudioCount = AudioSettings.DEFAULT_COUNT_CHOICE,
    val customCount: String = AudioSettings.DEFAULT_CUSTOM_COUNT,
    val mode: RepeatMode = RepeatMode.PASSAGE,
    val gapSeconds: Int = AudioSettings.DEFAULT_GAP_SECONDS,
    val speed: Float = AudioSettings.DEFAULT_SPEED,
    val autoStop: Boolean = true,
) {

    /** Le nombre d'écoutes, ou `null` si la répétition est illimitée. */
    fun resolveCount(): Int? = countChoice.resolve(customCount)

    /**
     * `true` quand la répétition est illimitée.
     *
     * Deux chemins mènent au même résultat, exactement comme dans le client d'origine :
     * le choix « ∞ », ou le fait d'avoir décoché « Arrêter à la fin des écoutes ».
     */
    val isUnlimited: Boolean get() = resolveCount() == null || !autoStop

    /** La forme enregistrée, pour l'écriture locale. */
    fun stored(): StoredAudioPreferences = StoredAudioPreferences(
        countChoice = countChoice.wire,
        customCount = customCount,
        repeatMode = mode.wire,
        gap = gapSeconds,
        speed = speed,
        autoStop = autoStop,
    )

    companion object {

        /**
         * Relit des préférences enregistrées.
         *
         * Chaque champ est validé **séparément** : un silence de 7 s (qui n'existe pas) est
         * remplacé par le silence par défaut, mais le récitateur, la vitesse et le mode
         * survivent. C'est le comportement du client d'origine, et c'est le bon : une
         * préférence illisible ne doit pas en emporter d'autres avec elle.
         */
        fun fromStored(stored: StoredAudioPreferences?): AudioSession {
            if (stored == null) return AudioSession()
            val defaults = AudioSession()
            return AudioSession(
                countChoice = AudioCount.fromWire(stored.countChoice)
                    ?: defaults.countChoice,
                customCount = stored.customCount ?: defaults.customCount,
                mode = repeatModeFromWire(stored.repeatMode) ?: defaults.mode,
                gapSeconds = stored.gap?.takeIf { it in AudioSettings.GAP_CHOICES }
                    ?: defaults.gapSeconds,
                speed = stored.speed?.takeIf { speed ->
                    AudioSettings.SPEED_CHOICES.any { it == speed }
                } ?: defaults.speed,
                autoStop = stored.autoStop ?: defaults.autoStop,
            )
        }

        /** La forme enregistrée d'un mode de répétition, telle que l'écrit le client d'origine. */
        fun repeatModeFromWire(wire: String?): RepeatMode? = when (wire) {
            "passage" -> RepeatMode.PASSAGE
            "each-verse" -> RepeatMode.EACH_VERSE
            else -> null
        }
    }
}

/** La forme enregistrée d'un mode de répétition. */
val RepeatMode.wire: String
    get() = when (this) {
        RepeatMode.PASSAGE -> "passage"
        RepeatMode.EACH_VERSE -> "each-verse"
    }
