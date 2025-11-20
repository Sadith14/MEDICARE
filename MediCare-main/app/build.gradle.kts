plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "2.0.21-1.0.25"
}

android {
    namespace = "com.example.medicare"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.medicare"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GEMINI_API_KEY", "\"${project.findProperty("GEMINI_API_KEY") ?: ""}\"")

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
        dataBinding = true
        viewBinding = true

    }
}

dependencies {

    // AndroidX - Versiones actualizadas
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // Lifecycle - Versiones actualizadas
    implementation(libs.androidx.lifecycle.viewmodel.ktx.v291)
    implementation(libs.androidx.lifecycle.livedata.ktx.v291)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.androidx.ui.text.android)

    // Testing - Versiones actualizadas
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // NUEVAS DEPENDENCIAS PARA RECONOCIMIENTO DE MEDICAMENTOS:

    // CameraX para captura de imágenes
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // TensorFlow Lite para IA
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)
    implementation(libs.tensorflow.lite.metadata)
    implementation(libs.tensorflow.lite.gpu)

    // Para manejo de permisos
    implementation(libs.easypermissions)

    // Para manejo de imágenes
    implementation(libs.glide)

    // Corrutinas (si no las tienes ya)
    implementation(libs.kotlinx.coroutines.android.v173)

    // ViewModel y LiveData
    implementation(libs.androidx.lifecycle.viewmodel.ktx.v287)
    implementation(libs.lifecycle.livedata.ktx)

    // Opcionales para APIs REST
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.logging.interceptor)
    implementation (libs.text.recognition)
    implementation (libs.retrofit.v290)
    implementation (libs.converter.gson.v290)
    implementation (libs.logging.interceptor.v4110)
    implementation (libs.jetbrains.kotlinx.coroutines.android)
    implementation (libs.androidx.lifecycle.lifecycle.viewmodel.ktx)
    implementation (libs.eventbus)

}