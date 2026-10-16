pluginManagement {
    repositories {
        // Google's repository only serves Android and AndroidX artifacts; filtering it keeps
        // the pure-Kotlin core buildable where Google's hosts are unreachable.
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "Lumen"

include(":core")
// The Android app needs the Android SDK. Build only the core with CORE_ONLY=1
// (for example where Google's download hosts are not reachable).
if (System.getenv("CORE_ONLY") == null) include(":app", ":baselineprofile")
