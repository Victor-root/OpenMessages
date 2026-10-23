dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.chrisbanes")
                includeGroup("com.github.realm")
            }
        }
    }
}

include(":presentation", ":android-smsmms", ":domain", ":data", ":common")
