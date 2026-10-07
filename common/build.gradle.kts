plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.dokka)
    `maven-publish`
}

android {
    namespace = "com.pedro.common"
    // The selected NDK supports API 21+, while common retains minSdk 16.
    // This is safe because the JNI shim is loaded only on API 29+.
    ndkVersion = "28.2.13676358"
    experimentalProperties["android.ndk.suppressMinSdkVersionError"] = 21
    //noinspection GradleDependency
    compileSdk = 35

    defaultConfig {
        minSdk = 16
        lint.targetSdk = 37
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
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
                groupId = project.group.toString()
                artifactId = project.name
                version = project.version.toString()
            }
        }
    }
}

dependencies {
    implementation(libs.ktor.network)
    implementation(libs.ktor.network.tls)
    implementation(libs.androidx.annotation)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
}