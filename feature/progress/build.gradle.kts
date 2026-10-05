// Module `feature:progress` — progression, revisions, consolidations.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.msoumaya.deepseekandroid.feature.progress"
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
    // Ce module ne depend que du design, du domaine et du depot local : il ne connait ni le
    // reseau, ni les autres fonctionnalites. L'ecran « Progres » lit des donnees, d'ou
    // `core:data` — comme `feature:home` et `feature:program`, et rien d'autre.
    api(project(":core:design"))
    implementation(project(":core:data"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
