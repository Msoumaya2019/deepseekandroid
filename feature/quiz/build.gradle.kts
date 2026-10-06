// Module `feature:quiz` — quiz du jour et defis entre amis.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.msoumaya.deepseekandroid.feature.quiz"
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
    // Ce module ne depend que du design, du modele et du domaine : il ne connait ni le reseau,
    // ni le stockage, ni les autres fonctionnalites. Le domaine est necessaire pour les regles
    // du Quiz (`Quiz.challengeStatus`, `Quiz.quizDay`) et pour ses libelles (`QuizText`), qui
    // doivent rester la seule copie des mots que l'utilisateur voit.
    //
    // `core:data` est la pour l'ecran seul : il lit le depot du Quiz, celui des amis et l'etat du
    // compte. Le **calcul**, lui, n'en depend pas — le `ViewModel` extrait la liste d'amis de
    // `SocialState` pour la passer au renderer en `List<FriendLink>`, ce qui garde le renderer
    // pur, donc eprouvable sans appareil.
    api(project(":core:design"))
    api(project(":core:domain"))
    implementation(project(":core:data"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
