allprojects {
  group = "com.github.rubik-crypto"
  version = "2.6.7"

  plugins.withType<PublishingPlugin> {
    configure<PublishingExtension> {
      publications.withType<MavenPublication>().all {
        pom {
          name = "RootEncoder"
          description = "A stream encoder to push video/audio to media servers"
          url = "https://github.com/rubik-crypto/RootEncoder"
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

tasks.dokkaHtmlMultiModule.configure {
  outputDirectory.set(File("docs"))
}