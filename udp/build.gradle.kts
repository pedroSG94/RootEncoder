plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.jetbrains.dokka)
  `maven-publish`
}

android {
  namespace = "com.pedro.udp"
  compileSdk = 36

  defaultConfig {
    minSdk = 16
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

        groupId = project.group.toString()
        artifactId = project.name
        version = project.version.toString()
      }
    }
  }
}

dependencies {
  implementation(libs.kotlinx.coroutines.android)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.junit)
  testImplementation(libs.mockito.kotlin)
  implementation(project(":srt"))
  api(project(":common"))
}
