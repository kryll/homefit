plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Version y firma vienen del entorno (CI al crear un tag vX.Y.Z). En local: 0.0.0-dev, sin firma de release.
val keystorePath: String? = System.getenv("HOMEFIT_KEYSTORE_PATH")

val robolectricDeps34 = configurations.create("robolectricDeps34")
val robolectricDeps35 = configurations.create("robolectricDeps35")

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
    testOptions { unitTests { isReturnDefaultValues = true; isIncludeAndroidResources = true } }
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
    testImplementation(libs.robolectric)
    testImplementation("androidx.work:work-testing:2.9.1")
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    "robolectricDeps34"("org.robolectric:android-all-instrumented:14-robolectric-10818077-i7")
    "robolectricDeps35"("org.robolectric:android-all-instrumented:15-robolectric-12650502-i7")
    testImplementation(libs.coroutines.test)
}

// Robolectric descarga su android-all en tiempo de test con su propio cliente HTTP, que falla a veces en CI.
// Lo resolvemos con Gradle (cacheado) y ejecutamos Robolectric sin conexión.
tasks.withType<Test>().configureEach {
    val depsDir = File(layout.buildDirectory.get().asFile, "robolectric-deps")
    doFirst {
        depsDir.mkdirs()
        (robolectricDeps34.files + robolectricDeps35.files).forEach { it.copyTo(File(depsDir, it.name), overwrite = true) }
    }
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", depsDir.absolutePath)
    maxHeapSize = "768m"
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = true
        showStandardStreams = false
    }
}
