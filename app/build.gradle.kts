plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin)
}

android {
  namespace = "com.pedro.streamer"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.pedro.streamer"
    minSdk = 19
    targetSdk = 34
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
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }
  buildFeatures {
    buildConfig = true
  }
}

dependencies {
  implementation(project(":library"))

//  implementation(project(":extra-sources"))

  implementation(libs.androidx.media3.extractor)
  implementation(libs.androidx.media3.common)
  implementation(libs.androidx.media3.datasource)

  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.multidex)
}
