plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.phoneagent.runtime"
    compileSdk = 37
    defaultConfig {
        minSdk = 29
        ndk.abiFilters += listOf("arm64-v8a", "x86_64")
        externalNativeBuild.cmake.arguments += "-DANDROID_STL=c++_static"
    }
    androidResources.noCompress += listOf("xz", "gz")
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    testImplementation(libs.junit4)
}
