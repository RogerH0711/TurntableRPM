import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * 簽章設定。**金鑰與密碼永遠不進版控。**
 *
 * 兩個來源，本機優先：
 * 1. `android/keystore.properties`（已在 .gitignore 裡）
 * 2. 環境變數 `TURNTABLE_KEYSTORE` / `_PASSWORD` / `_ALIAS` / `_ALIAS_PASSWORD`（給 CI 用）
 *
 * **兩個都沒有時不會失敗**，只是產出未簽章的 release APK。這是刻意的：
 * 別人 clone 下來要能建置，不該因為缺一份他不可能有的金鑰而卡住。
 *
 * 產生金鑰的步驟見 docs/android-release.md。
 */
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(key: String, env: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(env)

val keystorePath = signingValue("storeFile", "TURNTABLE_KEYSTORE")

android {
    namespace = "com.roger.turntablerpm"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.roger.turntablerpm"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePath != null && rootProject.file(keystorePath).exists()) {
            create("release") {
                storeFile = rootProject.file(keystorePath)
                storePassword = signingValue("storePassword", "TURNTABLE_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "TURNTABLE_KEYSTORE_ALIAS")
                keyPassword = signingValue("keyPassword", "TURNTABLE_KEYSTORE_ALIAS_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // 有金鑰才簽；沒有就產出未簽章的 APK（見檔頭的說明）。
            signingConfig = signingConfigs.findByName("release")

            // **R8 對這個 app 不是省幾百 KB，是省 20 MB。** 未縮混的 release APK
            // 有 23.1 MB，其中 22 MB 是未使用的 Compose 程式碼（兩個 dex）；
            // 開了之後是 2.7 MB。
            //
            // 這個 app 沒有反射、沒有 JNI、沒有序列化框架，所以額外規則幾乎不需要。
            // **縮混後的版本已經實機驗過**（用 debug 金鑰暫時簽了一版）：所有畫面、
            // 感測器、量測、分析失敗路徑、匯出的 JSON 都正常，logcat 零例外。
            // 改動相依或引入反射之後要重驗。
            optimization {
                enable = true
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // 演算法核心。app 是唯一碰 Android framework 的一層。
    implementation(project(":core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}