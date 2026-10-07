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

    // L'écoute et l'enregistrement. `core:audio` porte le lecteur Media3, l'enchaînement des
    // versets et l'enregistreur natif. Il est **exposé par l'API** depuis que la capacité
    // d'enregistrement est un paramètre public du lecteur : ses deux ports — `AudioRecorder` et
    // `RecitationPlayer` — apparaissent dans la signature de `RecitationRecorderCapability`, et
    // un appelant qui branche le lecteur doit pouvoir les nommer. Ce sont des **ports**, et non
    // des types de Media3 : aucune classe de la bibliothèque de lecture n'entre dans une signature
    // publique de ce module, ce qui était la règle d'origine et le reste.
    //
    // `core:playback` porte la **séance** : le lecteur la reçoit au lieu de la créer, ce qui la
    // fait survivre à l'écran. Exposé par l'API pour la même raison — le paramètre public
    // `playback` porte son type.
    api(project(":core:audio"))
    api(project(":core:playback"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    // La demande de permission du microphone. Elle passe par un **lanceur de résultat**, qui est
    // un objet d'`Activity` : aucun composable ne peut s'en passer, et le lecteur est le seul
    // endroit qui sache quand le micro est nécessaire. C'est aussi ce qui garde la capacité
    // d'enregistrement libre de toute notion d'écran — voir `RecitationRecorderCapability`.
    implementation(libs.androidx.activity.compose)

    // Les 604 pages sont des images embarquées. Coil les décode **à la taille d'affichage**
    // et garde la fenêtre de trois pages en mémoire ; décoder les pages à leur taille
    // d'origine occuperait plusieurs dizaines de mégaoctets par page.
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
