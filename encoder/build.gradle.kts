plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.jetbrains.dokka)
  `maven-publish`
}

android {
  namespace = "com.pedro.encoder"
  //noinspection GradleDependency
  compileSdk = 35

  defaultConfig {
    minSdk = 16
    lint.targetSdk = 37
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
  implementation(libs.kotlinx.coroutines.android)
  testImplementation(libs.junit)
  api(libs.androidx.annotation)
  api(project(":common"))
}
