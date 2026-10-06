package com.msoumaya.deepseekandroid.core.playback

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient la **déclaration** qui rend la lecture audible écran éteint.
 *
 * ## Le défaut que ce fichier empêche de revenir
 *
 * Un `MediaSessionService` ne fonctionne que si **quatre** choses sont vraies en même temps, et
 * aucune ne dépend des trois autres :
 *
 *  1. le service est déclaré dans le manifeste de `:app` ;
 *  2. il porte `foregroundServiceType="mediaPlayback"`, faute de quoi Android 14 refuse
 *     l'avant-plan — l'application plante à la première lecture ;
 *  3. il est `exported="true"` avec l'action `MediaSessionService`, faute de quoi le système ne
 *     peut pas le lier pour l'écran verrouillé ;
 *  4. les deux permissions `FOREGROUND_SERVICE*` sont demandées.
 *
 * Oublier l'une des quatre ne casse **aucun** test de comportement : le code compile, les tests
 * passent, et le défaut n'apparaît qu'écran éteint, sur un appareil. C'est exactement la classe
 * de défaut qu'un contrôle de forme doit tenir.
 *
 * ## Ce que ce fichier ne peut pas vérifier
 *
 * Que la notification **apparaisse** réellement, et que la lecture **survive** à l'extinction de
 * l'écran. Ces deux-là demandent un appareil : elles sont déclarées comme telles dans
 * `ANDROID_MIGRATION.md`, et non pas affirmées ici.
 */
class PlaybackPublicationWiringTest {

    @Test
    fun `le service est declare avec le type d'avant-plan de lecture`() {
        // Ancre sur le **bloc du service**, et non sur le fichier. C'est ce que le
        // falsificateur a montre : la chaine `foregroundServiceType="mediaPlayback"` figure
        // aussi dans le **commentaire** qui precede le service. Une assertion sur le fichier
        // entier restait donc verte alors que l'attribut avait disparu du service lui-meme —
        // elle etait satisfaite par de la prose.
        val bloc = blocDuService()
        assertTrue(
            bloc.contains("android:foregroundServiceType=\"mediaPlayback\""),
            "Le service ne declare pas `android:foregroundServiceType=\"mediaPlayback\"` : " +
                "Android 14 refusera l'avant-plan, et l'application s'arretera des la premiere " +
                "lecture ecran eteint.",
        )
    }

    @Test
    fun `le service est declare exporte avec l'action de media3`() {
        // Le systeme lie ce service pour l'ecran verrouille ; il n'appartient pas a
        // l'application. Sans `exported="true"` et sans l'action, rien ne le trouve.
        //
        // Le controle est ancre sur le **bloc du service**, et non sur le fichier entier :
        // `android:exported="true"` est aussi porte par `MainActivity`, donc une assertion sur
        // la chaine seule resterait verte meme si le service, lui, etait prive.
        val bloc = blocDuService()
        assertTrue(
            bloc.contains("android:exported=\"true\""),
            "Le service n'est pas exporte : le systeme ne peut pas le lier pour l'ecran " +
                "verrouille.",
        )
        assertTrue(
            bloc.contains("androidx.media3.session.MediaSessionService"),
            "Le service ne porte pas l'action `androidx.media3.session.MediaSessionService` : " +
                "le systeme ne le liera pas, et l'ecran verrouille restera muet.",
        )
    }

    @Test
    fun `les deux permissions d'avant-plan sont demandees`() {
        val manifeste = sourceDuManifeste()
        for (permission in listOf(
            "android.permission.FOREGROUND_SERVICE\"",
            "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK\"",
        )) {
            assertTrue(
                manifeste.contains(permission),
                "La permission `$permission` n'est pas demandee : l'avant-plan sera refuse " +
                    "sur les versions recentes.",
            )
        }
    }

    @Test
    fun `le service reprend le lecteur au lieu d'en construire un`() {
        // C'est la faute la plus couteuse a diagnostiquer : deux ExoPlayer joueraient la meme
        // recitation, decales de quelques millisecondes, et le son doublerait.
        val source = sourceDuService()
        assertFalse(
            source.contains("ExoPlayer.Builder") || source.contains("ExoAudioOutput("),
            "Le service construit son propre lecteur : deux lecteurs joueraient la meme " +
                "recitation, l'un par-dessus l'autre.",
        )
        assertTrue(
            source.contains("PlaybackBridge.player"),
            "Le service ne reprend pas le lecteur de l'application via `PlaybackBridge` : il " +
                "ne peut pas publier la seance qui joue reellement.",
        )
    }

    @Test
    fun `le service ne conduit aucune decision de seance`() {
        // Un service qui deciderait du verset suivant donnerait deux conducteurs pour un seul
        // lecteur, donc deux seances concurrentes.
        val source = sourceDuService()
        for (interdit in listOf(
            "AudioSessionController(",
            "AudioQueue.",
        )) {
            assertFalse(
                source.contains(interdit),
                "Le service contient `$interdit` : la conduite de la seance doit rester dans " +
                    "`AudioSessionHolder`, sans quoi deux conducteurs se disputeraient le lecteur.",
            )
        }
    }

    @Test
    fun `le pont est depose par l'application et non par un ecran`() {
        val application = sourceDeLApplication()
        assertTrue(
            application.contains("PlaybackBridge.publish("),
            "L'application ne depose pas le lecteur dans `PlaybackBridge` : le service, " +
                "reveille sans l'application, ne trouverait aucun lecteur a publier.",
        )
    }

    /**
     * Lit un fichier du dépôt depuis le répertoire du module.
     *
     * Un test de `:core:playback` s'exécute avec le répertoire du module pour origine
     * (`deepseekandroid/core/playback`), et non la racine du dépôt : c'est ce que Gradle donne à
     * `Test` par défaut. Les fichiers visés vivent donc un ou deux crans plus haut —
     * `core/playback/src/...` pour ce module, `app/src/...` pour l'application. Les chemins sont
     * essayés dans l'ordre, et l'erreur nomme **tous** les candidats, pour qu'un déplacement
     * futur se diagnostique en une lecture plutôt qu'en une chasse.
     */
    private fun sourceDe(cheminRelatif: String, prefixe: String): String {
        val candidats = listOf(
            File(cheminRelatif),
            File("$prefixe/$cheminRelatif"),
            File("../$prefixe/$cheminRelatif"),
            File("../../$prefixe/$cheminRelatif"),
        )
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "$cheminRelatif introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }

    private fun sourceDuManifeste(): String =
        sourceDe("src/main/AndroidManifest.xml", "app")

    /**
     * Le bloc `<service ...>...</service>` du manifeste, et **rien d'autre**.
     *
     * Découper plutôt que chercher dans tout le fichier : sans ce découpage, une assertion sur
     * `exported="true"` serait satisfaite par `MainActivity`, et le contrôle ne dirait rien du
     * service. Une balise absente est une **erreur**, pas une chaîne vide — sinon les assertions
     * qui suivent échoueraient en accusant l'attribut manquant au lieu de la balise manquante.
     */
    private fun blocDuService(): String {
        val manifeste = sourceDuManifeste()
        val debut = manifeste.indexOf("<service")
        return if (debut < 0) {
            error("Aucune balise `<service>` dans le manifeste : le service de lecture n'est pas declare.")
        } else {
            val fin = manifeste.indexOf("</service>", debut)
            if (fin < 0) manifeste.substring(debut) else manifeste.substring(debut, fin)
        }
    }

    private fun sourceDuService(): String =
        "PlaybackService.kt".let { nom ->
            val relatif = "src/main/kotlin/com/msoumaya/deepseekandroid/core/playback/$nom"
            sourceDe(relatif, "core/playback")
        }

    private fun sourceDeLApplication(): String =
        sourceDe("src/main/kotlin/com/msoumaya/deepseekandroid/DeepSeekApplication.kt", "app")
}
