plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Version y firma vienen del entorno (CI al crear un tag vX.Y.Z). En local: 0.0.0-dev, sin firma de release.
val keystorePath: String? = System.getenv("HOMEFIT_KEYSTORE_PATH")

android {
    namespace = "com.kryll.homefit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kryll.homefit"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("HOMEFIT_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("HOMEFIT_VERSION_NAME") ?: "0.0.0-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("HOMEFIT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("HOMEFIT_KEY_ALIAS")
                keyPassword = System.getenv("HOMEFIT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore)
    implementation(libs.work.runtime)
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation(libs.coroutines.test)
}
