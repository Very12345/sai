plugins {
    alias(libs.plugins.android.library)
}

android { namespace = "com.phoneagent.terminal"; compileSdk = 37; defaultConfig { minSdk = 29 } }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
