// Module `feature:recitations` — mes récitations : la liste, la relecture, les corrections.
//
// Il declare `core:data`, comme `feature:home`, `feature:program`, `feature:progress` et
// `feature:social` : l'ecran lit des recitations, et c'est le conteneur qui les fournit. Il n'a
// donc **pas** besoin de declarer `core:domain` ni `core:model` — `core:data` les expose deja en
// `api`, et les redire ici ferait croire a une dependance directe qui n'existe pas.
//
// Ni le reseau, ni une autre fonctionnalite : le lecteur audio viendra avec l'ecran, et il
// appartient a `core:audio`.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.msoumaya.deepseekandroid.feature.recitations"
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
    api(project(":core:design"))
    implementation(project(":core:data"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
