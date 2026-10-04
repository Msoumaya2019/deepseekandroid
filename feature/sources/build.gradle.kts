// Module `feature:sources` — présentation du Coran et installation du paquet « Coran 1441 ».
//
// Pourquoi un module, et pas une place dans `feature:reader` : le lecteur n'a **ni stockage ni
// réseau**, et c'est une contrainte du cahier des charges — il doit s'ouvrir en avion. Or
// choisir une présentation et télécharger 102 Mo demande les deux. Les mêler au lecteur
// aurait obligé celui-ci à connaître le stockage, donc à pouvoir échouer au démarrage pour une
// raison qui ne le regarde pas.
//
// Ce module est donc celui qui a le droit de parler au dépôt local et au réseau, et il ne
// dessine rien du moushaf : il fournit au lecteur des adresses déjà résolues.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.msoumaya.deepseekandroid.feature.sources"
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
    // Le magasin du paquet : c'est lui qui sait où sont les images et où en est
    // l'installation. Le domaine porte les règles — quel bouton pendant quelle phase, quelle
    // forme de page pour quelle source — et c'est ce qui permet de les éprouver sans écran.
    implementation(project(":core:data"))
    implementation(project(":core:domain"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
