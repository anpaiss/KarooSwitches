pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// karoo-ext lives on GitHub Packages: credentials from local.properties (gpr.user / gpr.key).
val localProps = java.util.Properties().also { p ->
    rootDir.resolve("local.properties").takeIf { it.exists() }?.inputStream()?.use { p.load(it) }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/hammerheadnav/karoo-ext")
            credentials {
                username = localProps["gpr.user"] as String? ?: System.getenv("GPR_USER") ?: ""
                password = localProps["gpr.key"] as String? ?: System.getenv("GPR_TOKEN") ?: ""
            }
        }
    }
}

rootProject.name = "KarooSwitches"
include(":lib")
