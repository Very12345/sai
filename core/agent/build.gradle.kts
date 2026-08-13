plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.phoneagent.agent"; compileSdk = 37; defaultConfig { minSdk = 29 } }

dependencies {
    api(project(":core:provider"))
    api(project(":core:runtime"))
    api(project(":core:extensions"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    testImplementation(libs.junit4)
}
