plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// aliasは鍵のラベルで秘密ではない。キーストア生成時の -alias と一致させる
val debugKeyAlias = "personallog"

// versionCode: エポックからの経過「分」。CIでもローカルでも同じ式なので単調増加し、ダウングレードしない。
val appVersionCode: Int = (System.currentTimeMillis() / 60000L).toInt()
// versionName: CIでは GITHUB_RUN_NUMBER、ローカルでは "local"
val appVersionName: String = "0.1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "local")

android {
    namespace = "com.amgm.personallog"
    compileSdk = 35

    defaultConfig {
        // debug/release で同一(applicationIdSuffix は付けない)
        applicationId = "com.amgm.personallog"
        minSdk = 28
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 更新エラー対策: debug 署名鍵を固定する。
    // CIは毎回新しいランナーで ~/.android/debug.keystore を作り直すため、固定しないと
    // ビルドごとに署名が変わり「上書き更新できない」(INSTALL_FAILED_UPDATE_INCOMPATIBLE)になる。
    // 公開リポジトリのため鍵はコミットしない。CIが GitHub Secrets から app/debug.keystore を復元し、
    // 下記の環境変数でパスワード等を渡す。app/debug.keystore が無い(ローカルビルド)ときは
    // AGP標準のデバッグ鍵のまま。
    signingConfigs {
        getByName("debug") {
            val ksFile = file(System.getenv("DEBUG_KEYSTORE_PATH") ?: "debug.keystore")
            if (ksFile.exists()) {
                fun env(name: String): String =
                    System.getenv(name)?.takeIf { it.isNotEmpty() }
                        ?: error("署名鍵 ${ksFile.name} があるのに環境変数 $name が未設定です")
                storeFile = ksFile
                // PKCS12のためキーパスワードはストアパスワードと同一でなければならない
                val pass = env("DEBUG_KEYSTORE_PASSWORD")
                storePassword = pass
                keyAlias = debugKeyAlias
                keyPassword = pass
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // Robolectric(本物の SQLite で移行を試すテスト)がリソース・マニフェストを使う
        unitTests.isIncludeAndroidResources = true
        // 失敗の理由(期待値と実際の値)を CI のログにそのまま出す
        unitTests.all { test ->
            test.testLogging {
                events("failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }
}

ksp {
    // DBスキーマJSONを app/schemas に出力(コミット対象)。version を上げたら Migration を必ず追加する。
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    // Robolectric 上の Compose UI テスト(シートの位置・操作の確認)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// DB の版を上げた直後、kspDebugKotlin と kspReleaseKotlin が同時に app/schemas へ同じ版の JSON を書き、
// KSP が "Empty schema file" で落ちることがある(2026-10-18、v10 の CI で発生)。release の KSP は debug の後に動かす。
tasks.configureEach {
    if (name == "kspReleaseKotlin") mustRunAfter("kspDebugKotlin")
}
