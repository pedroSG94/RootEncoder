val libraryGroup = "com.github.pedroSG94"
val vCode = 240
val vName = "2.4.0"
val coroutinesVersion = "1.7.3"
val junitVersion = "4.13.2"
val mockitoVersion = "5.2.1"

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

android {
  namespace = "com.pedro.streamer"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.pedro.streamer"
    minSdk = 16
    targetSdk = 34
    versionCode = vCode
    versionName = vName
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
  implementation("androidx.constraintlayout:constraintlayout:2.1.4")
  implementation("com.google.android.material:material:1.11.0")
  implementation("androidx.multidex:multidex:2.0.1")

  val cameraxVersion = "1.3.2"
  implementation("androidx.camera:camera-core:$cameraxVersion")
  implementation("androidx.camera:camera-camera2:$cameraxVersion")
  implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
}
