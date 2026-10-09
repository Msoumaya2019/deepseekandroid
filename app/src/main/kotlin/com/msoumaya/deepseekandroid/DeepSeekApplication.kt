package com.msoumaya.deepseekandroid

import android.app.Application
import com.msoumaya.deepseekandroid.core.audio.ExoAudioOutput
import com.msoumaya.deepseekandroid.core.audio.ExoRecitationPlayer
import com.msoumaya.deepseekandroid.core.audio.MediaAudioRecorder
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.remote.SupabaseConfig
import com.msoumaya.deepseekandroid.core.playback.PlaybackBridge
import java.io.File

/**
 * Point d'entrée de l'application : construit le graphe d'objets une seule fois.
 *
 * Le conteneur est créé ici et non dans l'activité, pour deux raisons :
 *  - une rotation de l'écran, un changement de thème ou un passage en écran partagé ne doivent
 *    pas reconstruire le graphe — et surtout pas relancer la lecture du référentiel coranique ;
 *  - un futur service (synchronisation en arrière-plan, notification de 19 h) doit pouvoir
 *    atteindre le conteneur sans dépendre d'une activité vivante.
 *
 * **Le projet Supabase est le projet existant**, partagé avec le client React Native : mêmes
 * comptes, mêmes identifiants, mêmes données. L'URL et la clé publique viennent de `BuildConfig`,
 * donc de `local.properties` ou des secrets du dépôt — jamais du code versionné.
 */
class DeepSeekApplication : Application() {

    /**
     * Le graphe d'objets de l'application.
     *
     * `lateinit` est sûr ici : il est construit dans [onCreate], qui s'exécute avant toute
     * activité. Un accès depuis un composant système lancé sans passer par l'application
     * échouerait — ce qui est le comportement voulu, un `null` silencieux serait pire.
     */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(
            context = this,
            supabase = SupabaseConfig(
                url = BuildConfig.SUPABASE_URL,
                anonKey = BuildConfig.SUPABASE_ANON_KEY,
            ),
            // Le lecteur natif est construit **ici**, à la vie de l'application : la séance
            // d'écoute n'appartient plus à un écran, et quitter le lecteur ne coupe donc plus
            // la récitation. Le `Context` d'application est passé, et non celui d'une activité,
            // qui survivrait mal à la fermeture de celle-ci.
            audioOutput = ExoAudioOutput(applicationContext),
            // Le lecteur des récitations enregistrées, construit ici pour la même raison : il a
            // besoin d'un `Context`, et c'est le seul endroit qui en ait un d'application. Il est
            // **distinct** du précédent — jouer une récitation ne doit pas remplacer le média de
            // la séance d'enchaînement en cours.
            recitationPlayer = ExoRecitationPlayer(applicationContext),
            // L'enregistreur natif, construit ici comme les deux lecteurs : `MediaRecorder`
            // demande un `Context`, et c'est le seul endroit qui en ait un d'application.
            //
            // Le dossier visé est celui du **cache**, et non celui de l'état : un brouillon qu'on
            // n'a pas gardé n'a aucune raison de survivre à une purge du système, et le registre
            // **copie** le fichier dans le dossier d'état au moment où on le garde. Une capture
            // abandonnée ne laisse donc rien derrière elle — ce que l'original obtient de son
            // fichier temporaire, et ce qu'un enregistrement interrompu doit obtenir ici.
            recorder = MediaAudioRecorder(
                context = applicationContext,
                directory = File(applicationContext.cacheDir, "recitations"),
            ),
            // La version part dans chaque signalement de probleme, et elle ne peut venir que
            // d'ici : `core:data` n'a pas de `BuildConfig`, et le seul module qui connaisse le
            // paquet est celui-ci. C'est `versionName` — « 0.1.0 » en debug comme en release —,
            // et non `versionCode`, parce que c'est la forme que la personne peut reconnaitre
            // dans un magasin ou dans un rapport.
            appVersion = BuildConfig.VERSION_NAME,
        )
        // Le lecteur unique est **depose** pour le service : celui-ci peut etre reveille par
        // Android alors que l'application n'existe plus en memoire, et n'a donc aucun autre
        // moyen de le retrouver. Deposer un lecteur qui ne joue pas encore est sans effet :
        // aucune session n'est publiee tant qu'aucune recreation n'a commence.
        //
        // Les deux `?.` sont distincts, et pas redondants : le conteneur n'a **aucun** detenteur
        // quand aucun lecteur ne lui a ete donne, et le detenteur n'a **aucun** lecteur a publier
        // quand son `AudioOutput` n'est pas un lecteur Media3 — c'est le cas d'une doublure de
        // test. `:app` fournit toujours un `ExoAudioOutput`, donc les deux sont non nuls ici ;
        // mais l'expression dit ce dont elle depend, et ne suppose rien.
        container.playback?.player?.let { PlaybackBridge.publish(it) }

        // L'observation du reseau demarre **ici**, et non dans une activite : le bandeau doit
        // dire la verite des le premier ecran, y compris celui de la porte d'entree, et une
        // observation liee a une activite s'arreterait a chaque rotation pour ne reprendre
        // qu'apres un rendu — c'est-a-dire apres avoir affiche un etat peut-etre faux.
        //
        // L'inscription est silencieuse en cas d'echec (voir `ConnectivityObserver.start`) :
        // un appareil qui refuse l'inscription n'affiche pas de bandeau, et c'est le bon defaut
        // — annoncer une panne qu'on n'a pas mesuree serait pire que de ne rien annoncer.
        container.connectivity.start()

        // La boite d'envoi des signalements demarre ici pour la meme raison : un signalement
        // ecrit hors connexion lors d'une session precedente doit partir des l'ouverture, sans
        // qu'un ecran ait a etre visite. L'original branche cet observateur dans `App.tsx`, a la
        // vie de l'application, et non dans un ecran.
        container.problemReports.start()
    }
}
