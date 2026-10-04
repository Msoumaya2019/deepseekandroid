# ---------------------------------------------------------------------------
# Regles ProGuard / R8
# ---------------------------------------------------------------------------
# La minification n'est active qu'en release. Ces regles sont volontairement peu nombreuses :
# la plupart des bibliotheques du projet livrent leurs propres regles de consommation, et
# recopier ici ce qu'elles declarent deja produit des regles fausses au premier changement de
# version.
#
# Les trois points ci-dessous sont ceux qui ne sont couverts par personne.
# ---------------------------------------------------------------------------

# --- kotlinx.serialization -------------------------------------------------
# Les serialiseurs sont generes par le compilateur et atteints par leur nom depuis le
# `Companion`. R8 ne voit pas ce lien et supprime les classes `$$serializer`, ce qui produit une
# `SerializationException` a l'execution — pas a la compilation.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    <1>$$serializer INSTANCE;
}

# --- Ktor / OkHttp ---------------------------------------------------------
# Ktor resout son moteur et ses plugins par leur nom de classe. Sans ces regles, la connexion
# echoue avec une `ClassNotFoundException` que rien ne relie au moteur HTTP.
-keep class io.ktor.client.engine.okhttp.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**

# --- Supabase --------------------------------------------------------------
# Le client encode et decode les lignes PostgREST par reflection sur les serialiseurs generes.
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**

# --- Ressources du referentiel coranique -----------------------------------
# `QuranDataLoader` lit `quran/*.json` par le chargeur de classes
# (`classLoader.getResourceAsStream`). R8 ne touche pas aux ressources, et `shrinkResources`
# ne supprime que les ressources Android : les fichiers embarques dans le JAR de `core:domain`
# sont donc conserves. Aucune regle n'est necessaire ici, et en ajouter une donnerait
# l'illusion d'une protection qui n'existe pas.
