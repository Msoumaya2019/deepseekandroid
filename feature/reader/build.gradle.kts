// Module `feature:reader` — lecteur de moushaf.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.msoumaya.deepseekandroid.feature.reader"
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
    // `core:design` porte les jetons et les composants, et expose Compose (BOM, `ui`,
    // `foundation`, `material3`) par l'API — les gestes du lecteur viennent de là.
    // `core:domain` porte la géométrie du lecteur, les données de page et les règles de geste.
    //
    // Ni réseau, ni stockage : le lecteur doit s'ouvrir en avion, et il n'a donc rien à
    // demander à `core:data`. C'est une contrainte, pas une économie. `core:audio` n'y
    // contredit rien : il ne se connecte pas, il lit des URL qu'on lui donne.
    api(project(":core:design"))
    implementation(project(":core:domain"))

    // L'écoute. `core:audio` porte le lecteur Media3 et l'enchaînement des versets ; il est
    // exposé par l'implémentation seule, car aucun type de Media3 ne doit apparaître dans la
    // signature publique de ce module.
    //
    // `core:playback` porte la **séance** : le lecteur la reçoit au lieu de la créer, ce qui la
    // fait survivre à l'écran. Exposé par l'API, car le paramètre public `playback` porte son
    // type — un appelant qui branche le lecteur doit pouvoir le nommer.
    implementation(project(":core:audio"))
    api(project(":core:playback"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    // Les 604 pages sont des images embarquées. Coil les décode **à la taille d'affichage**
    // et garde la fenêtre de trois pages en mémoire ; décoder les pages à leur taille
    // d'origine occuperait plusieurs dizaines de mégaoctets par page.
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
