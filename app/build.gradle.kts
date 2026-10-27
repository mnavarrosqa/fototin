plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.fototin"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.fototin"
        minSdk = 28
        targetSdk = 35
        // En CI cada build tiene un número mayor, así Android acepta instalarlo encima.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.2.${versionCode}"
    }

    // La clave de firma no está en el repo: GitHub Actions la arma desde los secrets
    // FOTOTIN_KEYSTORE_BASE64 y FOTOTIN_KEYSTORE_PASSWORD (ver .github/workflows/build.yml).
    // Sin esas variables (por ejemplo, al compilar en otra computadora) se usa la clave de debug,
    // y ese APK no se puede instalar encima del oficial.
    val releaseKeystore = System.getenv("FOTOTIN_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("fototin") {
                storeFile = releaseKeystore
                storeType = "PKCS12"
                storePassword = System.getenv("FOTOTIN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FOTOTIN_KEY_ALIAS") ?: "fototin"
                keyPassword = System.getenv("FOTOTIN_KEYSTORE_PASSWORD")
            }
        }
    }
    val signing = signingConfigs.findByName("fototin") ?: signingConfigs.getByName("debug")

    buildTypes {
        debug {
            signingConfig = signing
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signing
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
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.window)
    implementation(libs.play.services.auth)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}
