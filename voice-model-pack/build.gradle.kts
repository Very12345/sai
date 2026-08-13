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
    val saiSigningStoreFile = providers.gradleProperty("saiSigningStoreFile").orNull
    val saiSigningStorePassword = providers.environmentVariable("SAI_ANDROID_STORE_PASSWORD").orNull
    val saiSigningKeyAlias = providers.environmentVariable("SAI_ANDROID_KEY_ALIAS").orNull
    val saiSigningKeyPassword = providers.environmentVariable("SAI_ANDROID_KEY_PASSWORD").orNull
    signingConfigs {
        if (!saiSigningStoreFile.isNullOrBlank() && !saiSigningStorePassword.isNullOrBlank() &&
            !saiSigningKeyAlias.isNullOrBlank() && !saiSigningKeyPassword.isNullOrBlank()
        ) {
            create("saiRelease") {
                storeFile = file(saiSigningStoreFile)
                storePassword = saiSigningStorePassword
                keyAlias = saiSigningKeyAlias
                keyPassword = saiSigningKeyPassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("saiRelease")
        }
    }
    androidResources.noCompress += listOf("onnx", "txt")
}
