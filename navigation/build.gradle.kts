import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.msoumaya.deepseekandroid.navigation"
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
    // Ce module est le seul a connaitre **toutes** les fonctionnalites : c'est lui qui les
    // relie. Il depend donc de chaque `feature:*`, et aucun `feature:*` ne depend de lui.
    implementation(project(":core:design"))
    implementation(project(":core:data"))

    implementation(project(":feature:home"))
    implementation(project(":feature:reader"))
    implementation(project(":feature:sources"))
    implementation(project(":feature:program"))
    implementation(project(":feature:progress"))
    implementation(project(":feature:social"))
    implementation(project(":feature:quiz"))
    implementation(project(":feature:profile"))

    // `BackHandler` : la sortie du lecteur doit écrire la mémoire de lecture, et le retour
    // système est une sortie comme une autre. Sans lui, quitter au geste n'enregistrerait rien
    // et la position serait perdue exactement quand on quitte vite — le défaut le plus difficile
    // à voir, puisqu'il ne se produit que si l'on ne fait pas ce qu'on fait en essayant.
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // `collectAsStateWithLifecycle` : la route du lecteur observe la source et l'installation,
    // et une collecte qui survivrait à l'écran garderait le téléchargement en vie après la
    // sortie du lecteur.
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    // Le seul test de ce module lit le source de `ReaderRoute.kt` : il est donc placé ici,
    // où le fichier qu'il mesure vit, et non dans un autre module. Un test qui lit le source
    // d'ailleurs ne serait pas rejoué quand ce source change, et resterait vert par oubli.
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
