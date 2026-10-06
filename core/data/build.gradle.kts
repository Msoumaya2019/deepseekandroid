import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.msoumaya.deepseekandroid.core.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Les tests unitaires du module tournent sur la JVM : le stockage et la mise en file
    // sont conçus pour ne dépendre que d'un `File`, pas d'un `Context` Android.
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
    // Le domaine est exposé par l'API du module : les dépôts rendent des `AppState` et
    // utilisent les règles de fusion, les appelants n'ont pas à redéclarer ces types.
    api(project(":core:model"))
    api(project(":core:domain"))

    // La séance d'écoute vit dans le conteneur, à l'échelle de l'application : elle doit donc
    // être atteignable depuis les fonctionnalités, qui ne dépendent pas de `:app`. Exposée par
    // l'API, comme `core:domain` : un appelant qui reçoit la séance reçoit aussi son état.
    api(project(":core:playback"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Stockage local : un magasin JSON maison pour l'état et la file d'attente (il archive
    // les fichiers illisibles au lieu de les écraser), DataStore Preferences pour les
    // réglages simples, et des fichiers bruts pour les ressources lourdes (pages de moushaf,
    // audio). Aucun SQL, aucune génération de code.
    //
    // `androidx.datastore` (le magasin typé) n'est volontairement pas déclaré : son
    // gestionnaire de corruption est `internal` côté Kotlin et la seule implémentation
    // fournie est `final`, donc on ne peut pas empêcher l'écrasement d'un fichier fautif.
    implementation(libs.androidx.datastore.preferences)

    // Réseau et authentification.
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.androidx.core.ktx)

    // `Compose Runtime` seul, sans interface. Ce module publie son conteneur d'injection par un
    // `CompositionLocal` : c'est le point par lequel les fonctionnalites atteignent les depots,
    // et il doit donc etre visible depuis un `@Composable`. L'alternative — faire dependre
    // `core:design` de `core:data` — inversait le sens de la dependance pour la seule commodite
    // de placer la constante ailleurs.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    maxHeapSize = "1g"
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
