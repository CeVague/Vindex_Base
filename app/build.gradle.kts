plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
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

            signingConfig = signingConfigs.getByName("debug")
        }

        getByName("release") {
            // 1. Optimisation et Obfuscation (R8)
            // Indispensable pour la performance et réduire la taille de l'APK
            isMinifyEnabled = true

            // 2. Nettoyage des ressources
            // Supprime les ressources XML/Images inutilisées (nécessite isMinifyEnabled)
            isShrinkResources = false

            // Utilise les règles d'optimisation par défaut d'Android + les tiennes
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // 3. Configuration pour F-Droid / Publication
            // versionName = versionName brut (0.1.0), pas de suffixe en release.

            // Recommandé pour F-Droid : assure la reproductibilité du build
            vcsInfo.include = false

            // Signature : le build F-Droid retire cette signature et resigne avec
            // sa propre clé — la signature debug ici sert uniquement à produire un
            // APK installable en local. Pour une distribution directe (APK/Play),
            // remplacer par un vrai keystore release.
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
    implementation(libs.androidx.room.paging)
    implementation(libs.fragment)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.paging.common)
    implementation(libs.androidx.paging.runtime.ktx)
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

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Hilt pour WorkManager
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
