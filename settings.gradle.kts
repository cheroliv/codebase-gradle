pluginManagement.repositories {
    mavenLocal()
    gradlePluginPortal()
    mavenCentral()
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "codebase-gradle"

include(":codebase-plugin")
