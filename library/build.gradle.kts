val libraryGroup: String by rootProject.extra
val vName: String by rootProject.extra

plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  id("maven-publish")
  id("org.jetbrains.dokka")
}

android {
  namespace = "com.pedro.library"
  compileSdk = 34

  defaultConfig {
    minSdk = 16
    lint.targetSdk = 34
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
        groupId = libraryGroup
        artifactId = "library"
        version = vName
      }
    }
  }
}

dependencies {
  api(project(":encoder"))
  api(project(":rtmp"))
  api(project(":rtsp"))
  api(project(":srt"))
  api(project(":common"))
}
