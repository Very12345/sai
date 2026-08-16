plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.phoneagent.harness"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
}

dependencies {
    api(project(":core:provider"))
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit4)
}
