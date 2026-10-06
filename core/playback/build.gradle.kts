import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.msoumaya.deepseekandroid.core.playback"
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
    // La lecture est exposée par l'API : ce module ne fait que **tenir** une séance, et un
    // appelant qui la reçoit reçoit aussi son état, ses réglages et sa position.
    api(project(":core:audio"))

    // Media3 Session arrive **ici**, et non dans `core:audio` : c'est ce module qui publiera la
    // séance vers l'écran verrouillé et les écouteurs Bluetooth, une fois le service d'avant-plan
    // écrit. Tant qu'il ne l'est pas, la dépendance est déclarée mais aucun `MediaSession` n'est
    // construit — une session sans service donnerait une notification fantôme.
    implementation(libs.androidx.media3.session)

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

    // Les controles de forme de ce module lisent des fichiers **hors du module** : la
    // declaration du service dans `app/AndroidManifest.xml`, et le depot du lecteur dans
    // `DeepseekApplication`. Sans ces declarees en entrees, Gradle voit la tache « up-to-date »
    // des que le code du module n'a pas bouge — et le test **passe sans etre rejoue**, en
    // lisant un fichier dont le contenu a change. Le controle serait vert pour la mauvaise
    // raison, ce qui est pire que rouge : le falsificateur l'a montre.
    inputs.files(
        rootProject.file("app/src/main/AndroidManifest.xml"),
        rootProject.file("app/src/main/kotlin/com/msoumaya/deepseekandroid/DeepSeekApplication.kt"),
    ).withPropertyName("fichiersDePublicationLusParLesControles")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
