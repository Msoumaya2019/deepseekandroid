package com.msoumaya.deepseekandroid.core.playback

import android.content.Context
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Publier la séance : la rendre audible **écran éteint**, et pilotable depuis l'écran
 * verrouillé, les écouteurs Bluetooth ou le volant.
 *
 * Sans ce service, `ExoPlayer` s'arrête avec l'application : Android ne laisse pas un lecteur
 * continuer sans qu'un composant déclaré en réponde. C'est une règle de plateforme, pas un
 * choix — et c'est la raison d'être de ce fichier.
 *
 * **Ce que ce service ne fait pas**, et qui mérite d'être écrit : il ne conduit aucune décision.
 * Quel verset suit, après quel silence, quand s'arrêter — tout cela reste dans
 * [AudioSessionHolder] et [com.msoumaya.deepseekandroid.core.audio.AudioSessionController]. Le
 * service ne fait que **prêter un `MediaSession`** au lecteur qui existe déjà. Deux
 * conducteurs donneraient deux séances concurrentes sur un seul `ExoPlayer`.
 *
 * **Un seul lecteur, donc une seule session.** Le `Player` vient de
 * [com.msoumaya.deepseekandroid.core.audio.AudioOutputWithPlayer] : c'est celui qui joue déjà.
 * Construire ici un second `ExoPlayer` ferait jouer la même récitation deux fois, décalée.
 *
 * Le service est déclaré dans le manifeste de `:app` avec
 * `android:foregroundServiceType="mediaPlayback"`, et `MediaSessionService` se charge lui-même
 * de **démarrer l'avant-plan** quand la lecture commence et de le **quitter** quand elle
 * s'arrête : c'est la raison pour laquelle on étend cette classe plutôt que `Service`.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        session = buildSession()
    }

    /**
     * Rend la session au système, qui vient la chercher pour l'écran verrouillé.
     *
     * [ControllerInfo] dit **qui** demande — l'application, l'écran verrouillé, un écouteur
     * Bluetooth. Le client d'origine ne restreint personne : on ne filtre donc pas ici, mais le
     * paramètre est nommé pour que l'endroit où l'on filtrerait, si besoin, soit visible.
     */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }

    /**
     * La session, ou `null` si aucun lecteur n'est publiable.
     *
     * Rendre `null` est un état **légitime**, et non un échec : le système peut réveiller ce
     * service sans qu'aucune lecture n'ait commencé. Une session sans lecteur produirait une
     * notification vide sur l'écran verrouillé, ce qui est pire que pas de notification du tout.
     */
    private fun buildSession(): MediaSession? {
        val player = PlaybackBridge.player ?: return null
        return MediaSession.Builder(this, player).build()
    }

    companion object {
        /** L'intention explicite qui désigne ce service, vue par l'application. */
        fun intent(context: Context): Intent = Intent(context, PlaybackService::class.java)
    }
}
