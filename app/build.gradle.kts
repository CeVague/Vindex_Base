plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.cevague.vindex"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cevague.vindex"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    ksp {
        arg("room.schemaLocation", file("$projectDir/schemas").path)
    }

    buildTypes {
        getByName("debug") {
            // Rapidité de build maximale : pas d'obfuscation ni de minification
            isMinifyEnabled = false

            // Permet d'installer la version de test à côté de la version officielle
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"

            // Optionnel : utile pour certains outils de debug réseau
            isDebuggable = true
        }

        getByName("release") {
            // 1. Optimisation et Obfuscation (R8)
            // Indispensable pour la performance et réduire la taille de l'APK
            isMinifyEnabled = true

            // 2. Nettoyage des ressources
            // Supprime les ressources XML/Images inutilisées (nécessite isMinifyEnabled)
            isShrinkResources = true

            // Utilise les règles d'optimisation par défaut d'Android + les tiennes
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // 3. Configuration pour F-Droid / Publication
            // Pour la version finale, on retire généralement les suffixes
            // pour que l'ID corresponde exactement au store
            applicationIdSuffix = ".release" // À retirer pour la prod
            versionNameSuffix = "-release"   // À retirer pour la prod

            // Recommandé pour F-Droid : assure la reproductibilité du build
            vcsInfo.include = false

            // Signature (à configurer quand tu auras ton keystore)
            // signingConfig = signingConfigs.getByName("release")
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.constraintlayout)

    // Material Design 3
    implementation(libs.material)

    // Lifecycle (MVVM)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.runtime)

    // Navigation
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.fragment)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.swiperefreshlayout)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime)

    // Image loading - Glide
    implementation(libs.glide)
    ksp(libs.glide.compiler)

    // Photo viewer (zoom, pan)
    implementation(libs.photoview)

    // EXIF
    implementation(libs.androidx.exifinterface)
    implementation(libs.metadata.extractor)

    // Preferences
    implementation(libs.androidx.preference)

    // RecyclerView
    implementation(libs.androidx.recyclerview)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}