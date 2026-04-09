import java.util.Properties // 파일 상단에 import 추가

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 1. local.properties 파일 로드 로직 추가
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.example.activity_mainxml"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.activity_mainxml"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // 2. BuildConfig에 API 키 주입
        // local.properties에 설정한 이름을 getProperty 안에 넣습니다.
        val apiKey = localProperties.getProperty("MAPS_API_KEY") ?: ""
        buildConfigField("String", "GOOGLE_TTS_API_KEY", "\"$apiKey\"")

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        // 3. BuildConfig 클래스 생성 활성화 (필수)
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
}

dependencies {
    // BOM(Bill of Materials)을 제거하고 버전을 직접 적어 충돌 소지를 없앱니다.
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // platform(...) 대신 직접 버전 명시
    implementation("androidx.compose.ui:ui:1.6.0")
    implementation("androidx.compose.ui:ui-graphics:1.6.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.0")
    implementation("androidx.compose.material3:material3:1.2.0")

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    // 코루틴 핵심 라이브러리
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    // 안드로이드용 코루틴 라이브러리 (Dispatchers.Main 사용을 위해 필수)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}