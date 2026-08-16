plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.phoneagent.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.phoneagent.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 13_000
        versionName = "1.3.0"
        // Local/device builds must be able to find optional release modules too.
        // CI may override this for forks with -PsaiGithubRepository=owner/repo.
        val githubRepository = providers.gradleProperty("saiGithubRepository").orNull
            ?.takeIf(String::isNotBlank) ?: "Very12345/sai"
        buildConfigField("String", "GITHUB_REPOSITORY", "\"$githubRepository\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        ndk.abiFilters += listOf("arm64-v8a", "x86_64")
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
        debug {
            // Keep instrumentation/debug installs isolated from the user's signed
            // PhoneAgent package. Pass -PphoneAgentDebugPackageSuffix=false only
            // for an intentional in-place device build.
            if (providers.gradleProperty("phoneAgentDebugPackageSuffix").orNull != "false") {
                applicationIdSuffix = ".debug"
            }
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("saiRelease")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // PRoot must be a real executable file under nativeLibraryDir on Android 10+.
        // Direct mmap-from-APK loading is insufficient because /system/bin/linker64
        // launches it as a child process and PRoot also reads its injection loader.
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(project(":core:provider"))
    implementation(project(":core:runtime"))
    implementation(project(":core:data"))
    implementation(project(":core:extensions"))
    implementation(project(":core:harness"))
    implementation(project(":core:dsh"))
    implementation(project(":core:terminal"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sora.editor)
    implementation(files("libs/sherpa-onnx-1.13.4.aar"))
    implementation(libs.okhttp)
    implementation(libs.commons.compress)
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.latex)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.ext.strikethrough)
    implementation(libs.markwon.ext.tasklist)
    implementation(libs.markwon.inline.parser)
    implementation(libs.markwon.html)
    implementation(libs.zxing.embedded)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
