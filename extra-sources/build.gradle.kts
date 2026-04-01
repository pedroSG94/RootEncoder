plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.dokka)
    `maven-publish`
}

android {
    namespace = "com.pedro.extrasources"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        lint.targetSdk = 36
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    publishing {
        singleVariant("release")
    }
}

kotlin {
    jvmToolchain(17)
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                // Applies the component for the release build variant.
                from(components["release"])

                // You can then customize attributes of the publication as shown below.
                groupId = project.group.toString()
                artifactId = project.name
                version = project.version.toString()
            }
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.appcompat)
    implementation(libs.bundles.androidx.camera)
    implementation(libs.uvcandroid)
    implementation(libs.androidx.media3.inspector)
    testImplementation(libs.junit)
    api(project(":encoder"))
}