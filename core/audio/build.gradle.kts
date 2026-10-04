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
    // d'avant-plan et une notification. Ce sera le travail des notifications (phase D). Tant
    // que ce service n'existe pas, déclarer la session donnerait une notification fantôme.
    implementation(libs.androidx.media3.exoplayer)

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
