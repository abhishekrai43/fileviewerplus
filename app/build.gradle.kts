import org.gradle.internal.impldep.org.apache.sshd.sftp.common.extensions.VersionsParser

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)

}

android {
    namespace = "com.arapps.fileviewplus"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.arapps.fileviewplus"
        minSdk = 24
        targetSdk = 35
        versionCode = 13
        versionName = "1.1.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "version"


    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        // Compose compiler version is handled by the BOM
    }
    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    // Core & lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.review.ktx)
    implementation(libs.firebase.coroutines)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.google.billing)
    implementation(libs.play.inappupdate)
    implementation(libs.zip4j)
    implementation(libs.gson)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.firebase.messaging)




    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.coil.compose)
    implementation(libs.materialIconsExtended)
    implementation(libs.androidx.documentfile)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.serialization.jackson)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.androidx.appcompat)
    implementation(libs.lottie)
    implementation(libs.lottie.compose)
    implementation(libs.apache.ftpserver)
    implementation(project(":ftpserver"))
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)


    // Unit Testing
    testImplementation(libs.junit)

    // Android Instrumentation Testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Debug Tools
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
