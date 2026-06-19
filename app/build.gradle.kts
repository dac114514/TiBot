plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.faster.tibot"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.faster.tibot"
        minSdk = 24
        targetSdk = 36
        versionCode = 23
        versionName = "1.0.22"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        val keystoreFile = file(rootProject.file("tibot.keystore"))
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = keystoreFile
                storePassword = "fasterjike"
                keyAlias = "fasterjike"
                keyPassword = "fasterjike"
            }
        }
    }

    buildTypes {
        val releaseSigning = signingConfigs.findByName("release")
        release {
            if (releaseSigning != null) this.signingConfig = releaseSigning
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            if (releaseSigning != null) this.signingConfig = releaseSigning
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Only force the ARM64 AAPT2 classifier when actually building on Linux ARM64
// (Termux/Proot). On x86_64 hosts (incl. GitHub Actions Ubuntu runners) and
// Windows/macOS, leave the default classifier in place.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.android.tools.build" && requested.name == "aapt2") {
            val osArch = System.getProperty("os.arch")
            val osName = System.getProperty("os.name").orEmpty().lowercase()
            if (osArch == "aarch64" && osName.contains("linux")) {
                useTarget("com.android.tools.build:aapt2:${'$'}{requested.version}:linux-aarch64")
            }
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.paho.mqtt.client)
    implementation("org.apache.commons:commons-compress:1.26.2")
    debugImplementation(libs.androidx.ui.tooling)
}
