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
    implementation(project(":feature:program"))
    implementation(project(":feature:progress"))
    implementation(project(":feature:social"))
    implementation(project(":feature:quiz"))
    implementation(project(":feature:profile"))

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)
}
