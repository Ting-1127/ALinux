plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.android.alinux"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.android.alinux"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // 添加 Apache Commons Compress 来支持完美解压含有 Symlink 的 Tar 压缩包
    implementation("org.apache.commons:commons-compress:1.28.0")
}