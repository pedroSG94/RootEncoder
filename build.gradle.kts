allprojects {
  group = "com.github.pedroSG94"
  version = "2.6.2"
}

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