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

rootProject.name = "PhoneAgent"

include(
    ":app",
    ":voice-model-pack",
    ":core:network",
    ":core:provider",
    ":core:runtime",
    ":core:data",
    ":core:extensions",
    ":core:dsh",
    ":core:terminal",
)
