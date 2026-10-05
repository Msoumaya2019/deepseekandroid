// Module `feature:profile` — profil, apparence, reglages.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.msoumaya.deepseekandroid.feature.profile"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Ce module ne depend que du design et du modele : il ne connait ni le reseau, ni le
    // stockage, ni les autres fonctionnalites. Quand il aura besoin de lire des donnees, il
    // declarera `core:data` — comme `feature:home` — et rien d'autre.
    api(project(":core:design"))

    // L'ecran d'objectif est le premier de ce module a lire des donnees : il declare donc
    // `core:data` (le conteneur applicatif, comme `feature:home`) et `core:domain` (le
    // programme, le Coran et les libelles, comme `feature:program`). Le profil, les reglages
    // et l'apparence continuent de n'en avoir besoin d'aucun.
    implementation(project(":core:data"))
    implementation(project(":core:domain"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
