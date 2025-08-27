allprojects {
  group = "com.github.pedroSG94"
  version = "2.6.4"

  plugins.withType<PublishingPlugin> {
    configure<PublishingExtension> {
      publications.withType<MavenPublication>().all {
        pom {
          name = "RootEncoder"
          description = "A stream encoder to push video/audio to media servers"
          url = "https://github.com/pedroSG94/RootEncoder"
          licenses {
            license {
              name = "Apache-2.0"
              url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
              distribution = "manual"
            }
          }
        }
      }
    }
  }
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