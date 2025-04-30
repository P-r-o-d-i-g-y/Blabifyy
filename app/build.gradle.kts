plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.kapt")

    id("com.google.gms.google-services")
}

android {
    namespace = "com.test.blabify"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.test.blabify"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // BOM — набор версий всех библиотек Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))

    // Базовая аналитика (можно удалить, если не нужна)
    implementation("com.google.firebase:firebase-analytics")

    // Добавляй нужные тебе модули:
    // implementation("com.google.firebase:firebase-auth")
    // implementation("com.google.firebase:firebase-firestore")
    // implementation("com.google.firebase:firebase-storage")
}