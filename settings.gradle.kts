pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // GitHub Releases hosts the AAR only (no Maven metadata). Ivy maps
        // com.nshiftui.rainbowparser:rainbowparser:<ver>:release@aar →
        // .../releases/download/v<ver>/RainbowParser-Android-release.aar
        exclusiveContent {
            forRepository {
                ivy {
                    url = uri(
                        "https://github.com/NShiftUI-OSS/RainbowParser-Android/releases/download",
                    )
                    patternLayout {
                        artifact("v[revision]/RainbowParser-Android-[classifier].[ext]")
                    }
                    metadataSources {
                        artifact()
                    }
                }
            }
            filter {
                includeGroup("com.nshiftui.rainbowparser")
            }
        }
    }
}

rootProject.name = "NShiftUI-Android"

include(":nshiftui")
include(":nshiftui-ksp")
include(":nshiftui-ksp-test")
