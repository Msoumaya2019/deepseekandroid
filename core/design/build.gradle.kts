import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.msoumaya.deepseekandroid.core.design"
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

    // Les polices embarquées sont des ressources volumineuses : on ne les compresse pas
    // deux fois, et surtout on n'applique aucune transformation qui aplatirait la police
    // variable (l'axe `wght` 300->700 de Cormorant Garamond doit survivre à la compilation).
    androidResources {
        noCompress += listOf("ttf", "otf")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Le design expose les types de thème (`AppTheme`, `AccentName`, `UiFont`) portés par le
    // modèle : les appelants n'ont pas à redéclarer ces enums pour composer un écran.
    api(project(":core:model"))

    // Compose est exposé par l'API : les modules de fonctionnalité écrivent des écrans, ils ne
    // devraient pas avoir à redéclarer la BOM ni la liste des artefacts Compose.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)

    // Jeu d'icônes complet. Il est volumineux (plusieurs milliers de vecteurs) mais R8 ne
    // conserve en release que celles réellement référencées. C'est la source des icônes de la
    // barre d'onglets et des cartes ; les redessiner à la main serait plus risqué que coûteux.
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
