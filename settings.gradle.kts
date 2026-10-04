pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "deepseekandroid"

// ---------------------------------------------------------------------------
// Structure des modules
// ---------------------------------------------------------------------------
//  core:model     -> types de données purs, sérialisables, sans dépendance Android
//  core:domain    -> logique métier pure (portée depuis src/core/ de coran-memoire)
//  core:data      -> persistance locale (SQLite, DataStore, Keystore) + Supabase
//  core:design    -> jetons de design et composants Compose partagés
//  core:audio     -> lecture audio (Media3) et enchaînement des versets
//  feature:*      -> un module par domaine fonctionnel
//  navigation     -> graphe de navigation unique
//  app            -> point d'entrée, assemblage, DI manuelle
// ---------------------------------------------------------------------------

include(":app")

include(":core:model")
include(":core:domain")
include(":core:data")
include(":core:design")
include(":core:audio")

include(":navigation")

include(":feature:home")
include(":feature:auth")
include(":feature:reader")
include(":feature:sources")
include(":feature:program")
include(":feature:progress")
include(":feature:social")
include(":feature:quiz")
include(":feature:profile")
