import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.msoumaya.deepseekandroid.core.audio"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Le domaine est exposé par l'API : l'enchaînement des versets est une règle de
    // `core:domain`, ce module ne fait que l'exécuter. Un appelant qui reçoit un état de
    // lecture reçoit aussi `AudioPosition` et `Range`.
    api(project(":core:domain"))

    // ExoPlayer, et **pas** `media3-session` : une session de média sert à publier des
    // commandes vers l'écran verrouillé et les écouteurs Bluetooth, ce qui suppose un service
    // d'avant-plan et une notification. Cette dépendance appartient à `core:playback`, qui
    // portera ce service — et qui la déclare déjà. L'ajouter ici donnerait une seconde source
    // de vérité pour la même version, et une notification fantôme tant que le service n'existe
    // pas.
    implementation(libs.androidx.media3.exoplayer)

    // `Player` est **exposé par l'API** : `core:playback` doit pouvoir le lire pour le donner
    // à un `MediaSession`, sans dépendre de l'implémentation ExoPlayer ni la reconstruire.
    // Un `api` sur `media3-common` seul laisse `ExoPlayer` en `implementation` : la version
    // reste déclarée une fois, et le lecteur concret reste caché.
    api(libs.androidx.media3.common)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

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
