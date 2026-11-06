// 個人記録 v4 — UI v4 モック(feat/ui-v4 の v4/)を、既存アプリ(:app)とは別のアプリとして実装したもの。
// applicationId が別なので、既存アプリと並べてインストールできる。データは端末内(Room)にだけ保存する。
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val debugKeyAlias = "personallog"
val appVersionCode: Int = (System.currentTimeMillis() / 60000L).toInt()
val appVersionName: String = "4.15." + (System.getenv("GITHUB_RUN_NUMBER") ?: "local")

android {
    namespace = "com.amgm.personallog.v4"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.amgm.personallog.v4"
        minSdk = 28
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    // 既存アプリと同じ鍵(CIが Secrets から app/debug.keystore を復元する)。無ければ AGP 標準のデバッグ鍵
    signingConfigs {
        getByName("debug") {
            val ksFile = rootProject.file("app/" + (System.getenv("DEBUG_KEYSTORE_PATH") ?: "debug.keystore"))
            if (ksFile.exists()) {
                val pass = System.getenv("DEBUG_KEYSTORE_PASSWORD")?.takeIf { it.isNotEmpty() }
                    ?: error("署名鍵 ${ksFile.name} があるのに環境変数 DEBUG_KEYSTORE_PASSWORD が未設定です")
                storeFile = ksFile
                storePassword = pass
                keyAlias = debugKeyAlias
                keyPassword = pass
            }
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("debug") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// スクリーンショット(Robolectric)。-Pshots=home,a/l1 -PshotTheme=dark -PshotH=915
tasks.withType<Test>().configureEach {
    System.getenv("ROBO_REPO")?.let { systemProperty("robolectric.dependency.repo.url", it) }
    listOf("shots", "shotTheme", "shotH", "shotOut").forEach { k -> project.findProperty(k)?.let { systemProperty(k, it.toString()) } }
    systemProperty("shotOut", project.findProperty("shotOut")?.toString() ?: "${project.layout.buildDirectory.get().asFile}/shots")
    maxHeapSize = "2g"
}
