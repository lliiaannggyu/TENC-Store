import java.util.Properties

// 机密配置（密码本 / 签名）放在根目录 secrets.properties 中，
// 该文件被 .gitignore 排除，不会提交到仓库。缺失时用占位值，保证开源代码可编译。
val secretProps = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tengwear.tengstore"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tengwear.tengstore"
        // minSdk 25 = Android 7.1 / Wear OS 2.x：官方 Wear Compose 库要求的下限，
        // 从 30（Wear OS 3 专属）降低后可适配更多手表与安卓设备
        minSdk = 25
        targetSdk = 34
        versionCode = 50
        versionName = "1.49"

        // 密码本来自本地 secrets.properties（不入库），编译期写入 BuildConfig
        buildConfigField(
            "String",
            "LOG_CODEBOOK",
            "\"${secretProps.getProperty("LOG_CODEBOOK") ?: "PLACEHOLDER_CODEBOOK"}\"",
        )
    }

    signingConfigs {
        create("release") {
            val ksFile = rootProject.file(
                secretProps.getProperty("KEYSTORE_FILE") ?: "release.keystore",
            )
            // 有签名文件（本地开发）才启用 release 签名，否则回退 debug 签名，保证开源代码可直接构建
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = secretProps.getProperty("KEYSTORE_PASSWORD") ?: ""
                keyAlias = secretProps.getProperty("KEY_ALIAS") ?: ""
                keyPassword = secretProps.getProperty("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 本地有签名配置则用 release 签名，否则回退 debug 签名
            signingConfig = if (signingConfigs["release"].storeFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }

    composeOptions {
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += setOf(
                "META-INF/NOTICE.md",
                "META-INF/NOTICE.txt",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE.txt",
                "META-INF/INDEX.LIST",
            )
        }
    }
}

dependencies {
    val composeVersion = "1.9.0"
    // M3E 1.7+ 强制要求 compileSdk 37 + AGP 9.1（Gradle 9），当前工具链上限即 1.6.0
    val wearComposeVersion = "1.6.0"

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Jetpack Compose (mobile) baseline
    implementation("androidx.compose.ui:ui:$composeVersion")
    implementation("androidx.compose.ui:ui-graphics:$composeVersion")
    implementation("androidx.compose.ui:ui-tooling-preview:$composeVersion")
    implementation("androidx.compose.foundation:foundation:$composeVersion")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    // Wear OS official Material Design (Material Design for Wear OS) + navigation
    implementation("androidx.wear.compose:compose-material3:$wearComposeVersion")
    implementation("androidx.wear.compose:compose-foundation:$wearComposeVersion")
    implementation("androidx.wear.compose:compose-navigation:$wearComposeVersion")
    // On-device voice / keyboard text input (the official Wear OS input pattern)
    implementation("androidx.wear:wear-input:1.2.0")

    // 应用内发邮件（邮箱托管）：JavaMail for Android，直接走 SMTP 发送
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    // 项目介绍图片加载（README 内容 + 图片）
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Networking: GitHub REST API
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling:$composeVersion")
}
