// 演算法核心。**不依賴任何 Android framework** —— 跟 iOS 那邊的 TurntableCore 同樣的分層理由：
// 感測器要實機才測得到，演算法不該被綁在裝置上。這個模組的測試在純 JVM 上跑。
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    // 正式程式碼刻意零依賴，只有 Kotlin 標準函式庫。
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.serialization.json)
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed"); showStandardStreams = true }
}
