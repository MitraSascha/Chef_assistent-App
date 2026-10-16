plugins {
    // Kotlin-Unterstuetzung bringt AGP ab 9.0 selbst mit - das Plugin
    // 'org.jetbrains.kotlin.android' darf hier nicht mehr stehen.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Firebase (Push) nur fuer die Monteur-App (Auftrag 1.24). Ohne src/monteur/google-services.json
// wird das Plugin gar nicht angewendet - dann baut die App ohne Push und holt Nachrichten selbst ab.
// Die Buero-App hat keine Firebase-Datei: ihre google-services-Tasks werden abgeschaltet.
if (file("src/monteur/google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    tasks.matching { it.name.contains("GoogleServices") && it.name.contains("Buero") }.configureEach { enabled = false }
}

android {
    namespace = "de.chefassistent.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.chefassistent.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    // Zwei Apps aus einem Projekt (Auftrag 1.22): die Buero-App (Chef-Assistent)
    // und die schlanke Monteur-App. Datenschicht, Farben und Bausteine liegen in
    // src/main, die Oberflaechen in src/buero bzw. src/monteur.
    flavorDimensions += "app"
    productFlavors {
        create("buero") {
            dimension = "app"
            applicationId = "de.chefassistent.app"
        }
        create("monteur") {
            dimension = "app"
            applicationId = "de.mitra.monteur"
            versionName = "0.1.0"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work)

    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    implementation(libs.media3.common)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // QR-Code der Einrichtung, ueber Google Play-Dienste: braucht keine Kamera-Berechtigung.
    "monteurImplementation"(libs.play.code.scanner)
    "monteurImplementation"(platform(libs.firebase.bom))
    "monteurImplementation"(libs.firebase.messaging)

    implementation(libs.markwon.core)
    implementation(libs.markwon.tables)
    implementation(libs.markwon.strikethrough)
    implementation(libs.markwon.linkify)

    testImplementation(libs.junit)
}
