plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.chatty.fr"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chatty.fr"
        minSdk = 26
        targetSdk = 35
        // En CI, le numéro de build GitHub fait monter la version à chaque push.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.$build"
    }

    signingConfigs {
        // Clé fixe pour que chaque APK de la CI s'installe par-dessus le précédent.
        // Pour le Play Store, définir CHATTY_KEYSTORE* dans les secrets GitHub.
        create("chatty") {
            val custom = System.getenv("CHATTY_KEYSTORE_PATH")
            storeFile = if (custom != null) file(custom) else rootProject.file("signing/chatty-ci.jks")
            storePassword = System.getenv("CHATTY_KEYSTORE_PASSWORD") ?: "chattyci"
            keyAlias = System.getenv("CHATTY_KEY_ALIAS") ?: "chatty"
            keyPassword = System.getenv("CHATTY_KEY_PASSWORD") ?: "chattyci"
        }
    }

    // Deux APK : un pour Android 10+ et un pour Android 8-9.
    flavorDimensions += "android"
    productFlavors {
        create("android10") {
            dimension = "android"
            minSdk = 29
        }
        create("android8") {
            dimension = "android"
            minSdk = 26
            versionNameSuffix = "-android8"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("chatty")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.07.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.work:work-runtime-ktx:2.10.2")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
}
