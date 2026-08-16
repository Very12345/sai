plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.phoneagent.dsh"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    androidResources.noCompress += listOf("xz", "json")
}

dependencies {
    implementation(project(":core:runtime"))
    implementation(project(":core:harness"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    testImplementation(libs.junit4)
}
