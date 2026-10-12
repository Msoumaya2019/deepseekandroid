// Module `feature:dailycontents` — rappels et invocations : la liste, ses filtres, sa fiche.
//
// **Pourquoi un module, alors que `feature:home` porte déjà le carrousel.** Le carrousel est un
// bloc de l'accueil ; la liste est une **destination** — une route, ouverte par le geste du
// carrousel, par la feuille de la fiche, et demain par une notification. Les poser dans le meme
// module ferait dependre l'accueil de la resolution des adresses signees, des favoris et du
// partage, qui ne le concernent pas : c'est le meme partage que `feature:sources` avec
// `feature:reader`.
//
// Il declare `core:data` (le conteneur fournit les depots) et `core:domain` (le renderer lit les
// libelles et la taille de page). `core:data` expose deja `core:model`, donc il n'est pas
// redeclare.
//
// **Ni `core:audio`, ni `feature:reader`.** La fiche de l'original porte un bouton
// « Enregistrer ma voix », dont la capacite vit dans `feature:reader` et exige une **plage de
// versets** : une invocation n'en a pas. Le geste n'est donc pas porte ici, et une fonctionnalite
// ne depend pas d'une autre fonctionnalite dans ce depot — la variante d'invocation arrivera avec
// le lecteur, la ou la capacite vit.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.msoumaya.deepseekandroid.feature.dailycontents"
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
    implementation(project(":core:domain"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
