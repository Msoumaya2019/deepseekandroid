package com.msoumaya.deepseekandroid

import android.app.Application
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.remote.SupabaseConfig

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
        )
    }
}
