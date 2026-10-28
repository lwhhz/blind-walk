import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.blindwalk.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.blindwalk.app"
        minSdk = 33
        targetSdk = 36
        versionCode = 10
        versionName = "1.0.0"
    }

    signingConfigs {
        // 用 debug 签名给 release 用，只为在本机安装验证瘦身效果，不用于分发。
        create("previewRelease") {
            initWith(getByName("debug"))
        }
    }

    buildTypes {
        release {
            // 这是本次瘦身的主刀：debug 包 29MB 里 28MB 是未压缩的 dex。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("previewRelease")
        }
        debug {
            // 调试包保持原样：混淆后的堆栈没法看。
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")

    implementation("androidx.compose.ui:ui:1.12.0")
    implementation("androidx.compose.foundation:foundation:1.12.0")
    implementation("androidx.compose.material3:material3:1.4.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // 液态玻璃（实验性）。Kyant0/AndroidLiquidGlass，Apache-2.0。
    // 2.0.1 会拉入 Compose 1.12.0，而它要求 AGP 9.1+（本工程 8.13.2），故锁 2.0.0：
    // 它是这条线上最后一个「Compose 1.11 + Kotlin 2.3.21」的版本。
    implementation("io.github.kyant0:backdrop:2.0.1")

    testImplementation("junit:junit:4.13.2")
}
