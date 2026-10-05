package com.msoumaya.deepseekandroid.core.domain

/**
 * Un cache borné, dans l'ordre de récence.
 *
 * Porté depuis `src/coranTest/loadPage.ts`, dont l'élagage tient en une ligne :
 *
 * ```
 * const hit=cache.get(page); if(hit){cache.delete(page); cache.set(page,hit); return hit;}
 * …
 * cache.set(page,html); while(cache.size>5) cache.delete(cache.keys().next().value!);
 * ```
 *
 * ## Ce n'est pas un détail d'implémentation, c'est une règle
 *
 * Un document de l'écran immersif porte **trois polices** encodées en base64 et le balisage
 * d'une page. Le reconstruire coûte une lecture d'actifs et un encodage ; le garder coûte de la
 * mémoire. La borne est ce qui empêche la seconde de croître avec le nombre de pages visitées —
 * et la **récence** est ce qui empêche la première de se répéter quand on revient sur ses pas,
 * ce que fait toute personne qui mémorise.
 *
 * C'est aussi la raison pour laquelle [get] écrit : l'original retire puis réinsère l'entrée
 * lue, donc la lecture **protège** de l'éviction suivante. Un cache qui ne ferait que lire
 * évincerait exactement la page qu'on vient de quitter pour y revenir.
 *
 * ## Pourquoi elle vit ici
 *
 * Parce qu'un ordre d'éviction est une décision, et qu'une décision se prouve. Une
 * `LinkedHashMap` posée dans un chargeur Android ne s'éprouverait qu'avec un appareil ; ici,
 * elle s'éprouve sur trois entrées.
 */
class BoundedCache<K, V>(val limit: Int) {

    init {
        require(limit > 0) { "Un cache de taille $limit ne retient rien" }
    }

    // `LinkedHashMap` conserve l'ordre d'insertion : la première clé est la plus ancienne.
    private val entries = LinkedHashMap<K, V>()

    val size: Int get() = entries.size

    /** Les clés retenues, de la plus ancienne à la plus récente. */
    fun keys(): List<K> = entries.keys.toList()

    /** La valeur retenue pour [key], ou `null`. Une lecture remonte l'entrée en récence. */
    fun get(key: K): V? {
        val hit = entries.remove(key) ?: return null
        entries[key] = hit
        return hit
    }

    /**
     * Retient [value] pour [key], puis évince les plus anciennes entrées tant que la borne est
     * dépassée.
     *
     * Réécrire une clé déjà présente ne consomme pas de place : elle est retirée avant d'être
     * reposée, donc elle compte pour une seule entrée et remonte en récence.
     */
    fun put(key: K, value: V) {
        entries.remove(key)
        entries[key] = value
        while (entries.size > limit) {
            entries.remove(entries.keys.first())
        }
    }
}
