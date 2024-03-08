// Top-level build file where you can add configuration options common to all sub-projects/modules.
val libraryGroup by rootProject.extra { "com.github.pedroSG94" }
val vCode by rootProject.extra { 240 }
val vName by rootProject.extra { "2.4.0" }
val coroutinesVersion by rootProject.extra { "1.7.3" }
val junitVersion by rootProject.extra { "4.13.2" }
val mockitoVersion by rootProject.extra { "5.2.1" }

plugins {
  id("com.android.application") version "8.2.2" apply false
  id("org.jetbrains.kotlin.android") version "1.9.23" apply false
  id("org.jetbrains.dokka") version "1.9.20" apply true
}

tasks.register("clean") {
  delete(rootProject.buildDir)
}

tasks.dokkaHtmlMultiModule.configure {
  outputDirectory.set(File("docs"))
}