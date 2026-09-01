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

/**
 * 拿 iOS 匯出的真實資料跟 Swift 的結果對照。匯出檔不進版控，所以這是手動工具，
 * 不是自動測試 —— 從 repo 根目錄跑 `make android-crosscheck FILE=<檔案>`。
 */
tasks.register<JavaExec>("crossCheck") {
    group = "verification"
    description = "拿 iOS 匯出的逐樣本資料驗證 Kotlin 核心"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.roger.turntablerpm.core.ExportCrossCheck")
    if (project.hasProperty("file")) args(project.property("file") as String)
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed"); showStandardStreams = true }
}
