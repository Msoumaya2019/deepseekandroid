import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// ---------------------------------------------------------------------------
// Application
// ---------------------------------------------------------------------------
// Ce module ne contient que le point d'entree : il assemble le conteneur, applique le theme et
// affiche la coquille de navigation. Toute la logique vit dans les modules qu'il depend.
// ---------------------------------------------------------------------------

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Paramètres de connexion Supabase, lus **hors du dépôt**.
 *
 * Trois sources, dans cet ordre : `local.properties` (non versionné, pour le poste de travail),
 * puis une variable d'environnement (pour l'intégration continue, où les valeurs viennent des
 * secrets du dépôt), puis rien.
 *
 * **La clé `service_role` ne doit jamais apparaître ici.** Elle contourne les politiques RLS :
 * quiconque l'extrait de l'APK obtient un accès total aux données de tous les comptes. Seule la
 * clé publique est lue, et c'est le serveur qui décide des droits.
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun connectionSetting(key: String, environment: String, fallback: String = ""): String =
    localProperties.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(environment)?.takeIf { it.isNotBlank() }
        ?: fallback

val supabaseUrl = connectionSetting(
    key = "supabase.url",
    environment = "SUPABASE_URL",
    fallback = "https://npbwnvrqmajwqtnncuyv.supabase.co",
)
val supabaseAnonKey = connectionSetting(key = "supabase.anonKey", environment = "SUPABASE_ANON_KEY")

android {
    namespace = "com.msoumaya.deepseekandroid"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.msoumaya.deepseekandroid"
        minSdk = 26
        targetSdk = 36

        // Le client React Native en est a la version 0.9.38 ; cette application est un nouveau
        // paquet (`fr.coranmemoire.app` contre `com.msoumaya.deepseekandroid`), donc une nouvelle
        // numerotation. Elle ne remplace pas l'application existante sur un appareil : les deux
        // coexistent, ce qui permet de comparer avant de basculer.
        versionCode = 1
        versionName = "0.1.0"

        // Absente tant que la cle publique n'est pas fournie : l'application fonctionne alors
        // hors ligne et le dit. Voir `SupabaseConfig.isConfigured`.
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            // Sans cle de signature fournie, la compilation de release est signee avec la cle de
            // debogage : elle produit un APK installable pour verifier le resultat, mais **elle
            // n'est pas publiable**. C'est volontaire — bloquer la compilation de release sur une
            // intervention empecherait de verifier tout le reste.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Le catalogue racine le desactive par defaut : il est reactive ici, seul module a
        // exposer des constantes de compilation.
        buildConfig = true
    }

    // Les pages de moushaf sont des PNG deja comprimes ; les polices sont deja compressees par
    // `core:design`. Rien a recompresser ici, mais on evite la double compression des polices
    // si elles remontent un jour dans ce module.
    androidResources {
        noCompress += listOf("ttf", "otf")
    }

    packaging {
        resources {
            // Ces fichiers sont dupliques par plusieurs dependances (Ktor, kotlinx, coroutines)
            // et font echouer l'empaquetage s'ils ne sont pas exclus.
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":navigation"))
    // La porte d'entrée est un frère de la coquille, pas un enfant : `:app` décide de l'ordre,
    // donc il dépend des deux directement.
    implementation(project(":feature:auth"))
    // `navigation` declare ces deux modules en `implementation` : ils ne remontent donc pas.
    // `:app` en a besoin directement — le conteneur est construit ici, le theme applique ici.
    implementation(project(":core:data"))
    implementation(project(":core:design"))
    // Le lecteur natif est construit ici : c'est le seul module qui ait à la fois un `Context`
    // d'application et le droit d'assembler. `core:data` reçoit l'objet, il ne le crée pas.
    implementation(project(":core:audio"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
