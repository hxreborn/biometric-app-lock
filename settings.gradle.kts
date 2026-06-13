pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // TODO: Remove temp snapshot repo
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
            mavenContent { snapshotsOnly() }
            content { includeGroup("io.github.libxposed") }
        }
    }
}

rootProject.name = "biometricapplock"
include(":app")

include(":test-notif-app")
project(":test-notif-app").projectDir = file("tools/notification-test")
