import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val sshHosts: String = providers.gradleProperty("AX_SSH_HOSTS").orNull
    ?: rootProject.file("local.properties").takeIf { it.exists() }?.let { f ->
        Properties().apply { f.reader(Charsets.UTF_8).use { load(it) } }.getProperty("AX_SSH_HOSTS")
    }
    ?: ""

// §32 앱 업데이트: `ax-kit phone apk` on the Mac sets these, so every build it hands out has one key and a growing number
// (the commit count) and installs over the last one. Without them: the debug key and the numbers below, as before.
fun env(name: String): String? = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
val releaseKeystore = env("AXKIT_KEYSTORE")?.let { file(it) }?.takeIf { it.isFile }
val versionCodeFromEnv = env("AXKIT_VERSION_CODE")?.toIntOrNull()?.takeIf { it > 0 }
val versionNameFromEnv = env("AXKIT_VERSION_NAME")

android {
    namespace = "dev.axkit.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.axkit.app"
        minSdk = 29
        targetSdk = 36
        versionCode = versionCodeFromEnv ?: 1
        versionName = versionNameFromEnv ?: "1.0.0"
        // Macs the 터미널 lists from the start: name=user@address[:port], comma separated (iOS AX_SSH_HOSTS).
        // Empty here; set AX_SSH_HOSTS in local.properties or ~/.gradle/gradle.properties.
        buildConfigField("String", "AX_SSH_HOSTS", "\"" + sshHosts.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
    }

    signingConfigs {
        if (releaseKeystore != null) create("axkit") {
            storeFile = releaseKeystore
            storeType = "pkcs12"
            storePassword = env("AXKIT_KEYSTORE_PASSWORD")
            keyAlias = env("AXKIT_KEY_ALIAS")
            // PKCS12 keeps one password for the store and the key.
            keyPassword = env("AXKIT_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        debug {
            // Debug builds show "개발용 가짜 맥에 연결" (same as the iOS Debug build).
            buildConfigField("boolean", "SHOW_MOCK", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro", "proguard-terminal.pro")
            signingConfig = signingConfigs.findByName("axkit") ?: signingConfigs.getByName("debug")
            buildConfigField("boolean", "SHOW_MOCK", "false")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)
    // 터미널: SSH (sshj, Ed25519 through BouncyCastle) and the terminal drawn in Compose (ConnectBot termlib, libvterm).
    implementation(libs.sshj)
    implementation(libs.bouncycastle.prov)
    implementation(libs.termlib)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
