plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.zeljkobilbija001.beneluxroadinfo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.zeljkobilbija001.beneluxroadinfo"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Potpis za Google Play: ključ i lozinka iz C:/Users/porod/.gradle/gradle.properties (van projekta, ne ide na GitHub).
    // Bez upisane lozinke release ostaje nepotpisan.
    val potpisKljuc = providers.gradleProperty("POTPIS_BENELUXROADINFO").orNull
    val potpisLozinka = providers.gradleProperty("POTPIS_LOZINKA").orNull
    val potpis = potpisKljuc != null && !potpisLozinka.isNullOrEmpty() && file(potpisKljuc).exists()
    signingConfigs {
        if (potpis) {
            create("release") {
                storeFile = file(potpisKljuc!!)
                storePassword = potpisLozinka
                keyAlias = providers.gradleProperty("POTPIS_BENELUXROADINFO_ALIAS").orNull ?: "key0"
                keyPassword = potpisLozinka
            }
        }
    }

    buildTypes {
        release {
            if (potpis) signingConfig = signingConfigs.getByName("release")
            // R8 (skraćivanje i maskiranje) – Google traži maskiranje; mapping ide u .aab.
            // Pravila u proguard-rules.pro čuvaju WorkDatabase_Impl (WorkManager iz AdMob-a; Room ga traži po imenu).
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.core.splashscreen)
    implementation(libs.recyclerview)
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.play.services.location)
    implementation(libs.work.runtime)
    implementation(libs.billing)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}