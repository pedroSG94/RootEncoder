plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin)
    id(libs.plugins.maven.publish.get().pluginId)
    alias(libs.plugins.jetbrains.dokka)
}

android {
    namespace = "com.pedro.extrasources"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        lint.targetSdk = 35
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release")
    }
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                // Applies the component for the release build variant.
                from(components["release"])

                // You can then customize attributes of the publication as shown below.
                groupId = libs.versions.libraryGroup.get()
                artifactId = "extra-sources"
                version = libs.versions.versionName.get()
            }
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.appcompat)
    implementation(libs.bundles.androidx.camera)
    implementation(libs.uvcandroid)
    implementation(libs.androidx.media3.exoplayer)
    testImplementation(libs.junit)
    api(project(":encoder"))
}