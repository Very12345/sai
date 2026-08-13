plugins {
    alias(libs.plugins.android.library)
}

android { namespace = "com.phoneagent.network"; compileSdk = 37; defaultConfig { minSdk = 29 } }

dependencies {
    api(libs.okhttp)
    testImplementation(libs.junit4)
}
