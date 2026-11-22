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
        // GeckoView (Firefox engine) is published only on Mozilla's Maven.
        maven {
            url = uri("https://maven.mozilla.org/maven2/")
            content { includeGroupAndSubgroups("org.mozilla") }
        }
    }
}

rootProject.name = "fBrowser"
include(":app")
