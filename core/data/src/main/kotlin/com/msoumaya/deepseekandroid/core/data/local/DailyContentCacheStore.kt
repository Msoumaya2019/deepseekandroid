package com.msoumaya.deepseekandroid.core.data.local

import com.msoumaya.deepseekandroid.core.domain.DailyContents
import com.msoumaya.deepseekandroid.core.model.DailyContentsSnapshot
import java.io.File

/**
 * Le contenu du jour, persisté sur l'appareil.
 *
 * **Un seul fichier, et non un par compte — contrairement à l'instantané du Quiz.** Ce n'est pas
 * une omission : `daily_contents` et `content_categories` sont lisibles **sans compte**. Les
 * politiques de `supabase/daily-contents.sql` ouvrent la lecture à `anon`, la fonction
 * `daily_content_for_date` est exécutable par `anon`, et le client d'origine range lui aussi ce
 * document sous une clé qui ne porte aucun utilisateur (`daily-content:<date>`). Un fichier par
 * compte recopierait donc le même contenu pour chaque compte de l'appareil, et — pire — le
 * contenu du jour disparaîtrait de l'écran à la déconnexion, alors qu'il n'appartient à personne.
 *
 * **Le jour est porté par le document, et non par le nom du fichier.** C'est la même règle que
 * pour `QuizCacheStore`, et c'est [DailyContents.isFor] qui la fait vivre : le magasin ne
 * connaît pas la date, il rend ce qu'il a. C'est le dépôt qui compare, parce que c'est lui qui
 * décide ce qui est publié — et un magasin qui filtrerait par date cacherait au dépôt la
 * différence entre « rien n'a jamais été lu » et « ce qui a été lu date d'hier », qui sont deux
 * choses à dire différemment.
 *
 * **Un fichier unique, et non un par date.** L'original range une clé par date dans
 * `AsyncStorage`, et les dates s'y accumulent sans que rien ne les retire. Ici le document porte
 * sa date et le suivant la remplace : il n'y a jamais qu'un contenu du jour, et garder la veille
 * ne servirait à rien — elle ne s'afficherait pas, puisque [DailyContents.isFor] la refuserait.
 *
 * Le nom du fichier est écrit en clair, sans passer par `LocalStateStore.fileToken` : il ne
 * contient aucun identifiant de compte, donc rien à assainir.
 */
class DailyContentCacheStore(
    root: File,
    private val today: () -> String = { DailyContents.today() },
) {

    private val store: JsonFileStore<DailyContentsSnapshot> = JsonFileStore(
        file = File(root, "daily-content.json"),
        serializer = DailyContentsSnapshot.serializer(),
        default = { DailyContents.emptySnapshot(today()) },
    )

    /** Le document en cache. Sans fichier, un document **vide et du jour**. */
    suspend fun current(): DailyContentsSnapshot = store.current()

    /** Remplace le document, et rend la valeur écrite. */
    suspend fun update(
        transform: (DailyContentsSnapshot) -> DailyContentsSnapshot,
    ): DailyContentsSnapshot = store.update(transform)
}
