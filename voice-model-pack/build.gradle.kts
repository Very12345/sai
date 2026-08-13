plugins { alias(libs.plugins.android.application) }

android {
    namespace = "com.sai.voice.pack"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.sai.voice.pack"
        minSdk = 29
        targetSdk = 36
        versionCode = 11_026
        versionName = "1.1.26"
    }
    buildTypes { release { isMinifyEnabled = false } }
    androidResources.noCompress += listOf("onnx", "txt")
}
