plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.jetbrains.kotlin)
  id(libs.plugins.maven.publish.get().pluginId)
  alias(libs.plugins.jetbrains.dokka)
}

android {
  namespace = "com.pedro.encoder"
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
        groupId = libs.versions.libraryGroup.get()
        artifactId = "encoder"
        version = libs.versions.versionName.get()
      }
    }
  }
}

dependencies {
  testImplementation(libs.junit)
  api(libs.androidx.annotation)
  api(project(":common"))
}
