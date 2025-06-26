plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin)
}

android {
  namespace = "com.pedro.streamer"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.pedro.streamer"
    minSdk = 16
    targetSdk = 35
    versionCode = libs.versions.versionCode.get().toInt()
    versionName = libs.versions.versionName.get()
    multiDexEnabled = true
  }
  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
    }
  }
  buildFeatures {
    buildConfig = true
  }
}

dependencies {
  implementation(project(":library"))
  implementation(project(":extra-sources"))
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.multidex)
}
