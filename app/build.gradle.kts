import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    id("androidx.navigation.safeargs.kotlin")
}

// Assinatura de release: lida de keystore.properties, que nunca entra no Git.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// IDs de teste públicos da Google: o build de debug nunca usa anúncios reais.
val testAdmobAppId = "ca-app-pub-3940256099942544~3347511713"
val testAdmobBannerId = "ca-app-pub-3940256099942544/9214589741"
val testAdmobInterstitialId = "ca-app-pub-3940256099942544/1033173712"

// IDs reais, definidos em gradle.properties. Sem os três, o release não mostra anúncios.
fun admobProperty(name: String) = providers.gradleProperty(name).orNull.orEmpty().trim()
val releaseAdmobAppId = admobProperty("RESTOCK_ADMOB_APP_ID")
val releaseAdmobBannerId = admobProperty("RESTOCK_ADMOB_BANNER_ID")
val releaseAdmobInterstitialId = admobProperty("RESTOCK_ADMOB_INTERSTITIAL_ID")
val releaseAdsEnabled = listOf(
    releaseAdmobAppId,
    releaseAdmobBannerId,
    releaseAdmobInterstitialId
).all { it.isNotEmpty() }

android {
    namespace = "com.example.restock"
    compileSdk = 36

    defaultConfig {
        applicationId = "pt.hugomoreira.restock"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = testAdmobAppId
            buildConfigField("boolean", "ADS_ENABLED", "true")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$testAdmobBannerId\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$testAdmobInterstitialId\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            // O SDK de anúncios exige um App ID no manifesto mesmo com os anúncios desligados.
            manifestPlaceholders["admobAppId"] =
                if (releaseAdsEnabled) releaseAdmobAppId else testAdmobAppId
            buildConfigField("boolean", "ADS_ENABLED", releaseAdsEnabled.toString())
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$releaseAdmobBannerId\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$releaseAdmobInterstitialId\"")
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

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Fragment
    implementation("androidx.fragment:fragment-ktx:1.8.2")

    // Navigation Component
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Splash Screen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Anúncios e consentimento (RGPD)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.11.1")

    // UI libs
    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // ML Kit & CameraX
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.guava)

    // Tests
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.mockk:mockk:1.13.10")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
}