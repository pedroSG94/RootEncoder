// Top-level build file where you can add configuration options common to all sub-projects/modules.
val libraryGroup by rootProject.extra { "com.github.pedroSG94" }
val vCode by rootProject.extra { 245 }
val vName by rootProject.extra { "2.4.5" }

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.jetbrains.kotlin) apply false
  alias(libs.plugins.jetbrains.dokka) apply true
}

tasks.register("clean") {
  delete(layout.buildDirectory)
}

tasks.dokkaHtmlMultiModule.configure {
  outputDirectory.set(File("docs"))
}