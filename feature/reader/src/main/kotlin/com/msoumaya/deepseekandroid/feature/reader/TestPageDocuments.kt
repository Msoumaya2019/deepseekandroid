package com.msoumaya.deepseekandroid.feature.reader

import android.content.Context
import com.msoumaya.deepseekandroid.core.domain.BoundedCache
import com.msoumaya.deepseekandroid.core.domain.TestPageHtml
import com.msoumaya.deepseekandroid.core.domain.TestPageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Les documents de la composition « Coran avec règles de Tajwid », construits et conservés.
 *
 * Porté depuis `src/coranTest/loadPage.ts`. Un document, c'est la page **plus ses trois polices
 * encodées en base64** : le construire coûte une lecture d'actifs et un encodage, et c'est
 * pourquoi l'original en garde cinq.
 *
 * ## La borne, et ce qu'elle protège
 *
 * Cinq documents, jamais plus. La règle d'éviction est celle de [BoundedCache], éprouvée là-bas.
 * L'écran, lui, n'en garde que trois montés ; le chargeur en garde cinq **parce que la
 * quatrième et la cinquième sont celles qu'on vient de quitter** — et revenir sur ses pas est le
 * geste même de la mémorisation. Sans cette marge, chaque retour relirait trois polices.
 *
 * ## Une seule construction à la fois
 *
 * L'original déduplique les chargements en cours par une `Map<number, Promise<string>>`. Ici,
 * tout le corps passe par un verrou : une seconde demande de la même page **attend** la
 * première puis la trouve dans le cache. Le résultat est le même, et il a un second mérite sur
 * un appareil modeste : trois encodages base64 concurrents de 300 Ko ne se disputent pas la
 * mémoire et le processeur au moment précis où l'écran change de page.
 *
 * ## Ce que le chargeur ne fait pas
 *
 * Il ne connaît ni l'écran ni la navigation : il rend un document pour un numéro de page, et
 * rien d'autre. C'est ce qui permet à l'écran de rester une machine à états lisible.
 *
 * Le `Context` est reçu à chaque appel plutôt que conservé : un objet qui garde un `Context`
 * d'activité survit à l'écran qui l'a créé, et c'est une fuite.
 */
internal object TestPageDocuments {

    /**
     * La borne du chargeur d'origine : `while(cache.size>5) cache.delete(cache.keys().next())`.
     */
    const val CACHE_LIMIT = 5

    private val cache = BoundedCache<Int, String>(CACHE_LIMIT)

    /** Le verrou qui sérialise les constructions et rend la déduplication gratuite. */
    private val lock = Mutex()

    /**
     * Le document d'une page, prêt à être chargé dans une WebView.
     *
     * Lève si la page n'existe pas — le refus vient de [TestPageLoader], et il nomme la page.
     */
    suspend fun document(context: Context, page: Int): String = lock.withLock {
        cache.get(page)?.let { return@withLock it }
        // La lecture d'actifs et l'encodage base64 ne sont pas du travail d'affichage : les
        // laisser sur le fil principal figerait l'écran pendant qu'on tourne une page.
        val html = withContext(Dispatchers.IO) {
            TestPageHtml.document(TestPageLoader.load(page), TestPageFonts.fontsOf(context, page))
        }
        cache.put(page, html)
        html
    }
}
