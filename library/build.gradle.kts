plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.jetbrains.kotlin)
  alias(libs.plugins.jetbrains.dokka)
  `maven-publish`
}

android {
  namespace = "com.pedro.library"
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
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlin {
    jvmToolchain(17)
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
  implementation(libs.kotlinx.coroutines.android)
  api(project(":encoder"))
  api(project(":rtmp"))
  api(project(":rtsp"))
  api(project(":srt"))
  api(project(":udp"))
  api(project(":common"))
}
