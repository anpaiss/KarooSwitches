import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    `maven-publish`
}

// Coordinates com.github.anpaiss:karooswitches, the same for publishToMavenLocal
// and for GitHub Packages (which rejects uppercase artifact ids).
group = "com.github.anpaiss"
version = "0.2.2"

android {
    namespace  = "com.anpaiss.karooswitches"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }

    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

dependencies {
    // Provided by the host extension: every one of ours ships karoo-ext already.
    compileOnly(libs.hammerhead.karoo.ext)
    compileOnly(libs.kotlinx.coroutines.android)
}

// Credentials for GitHub Packages: gradle property, local.properties, or the env
// of the CI (GPR_USER / GPR_TOKEN), in this order. The token needs write:packages
// to publish and read:packages (any token of the owner) to consume.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun gpr(name: String, env: String): String =
    providers.gradleProperty(name).orNull ?: localProps.getProperty(name) ?: System.getenv(env) ?: ""

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "karooswitches"   // GitHub Packages rejects uppercase coordinates (422)
            afterEvaluate { from(components["release"]) }
        }
    }
    repositories {
        // ./gradlew :lib:publish → GitHub Packages of the private repo anpaiss/KarooSwitches;
        // the hosts add the same URL to their settings. publishToMavenLocal still works for
        // a quick local round trip.
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/anpaiss/KarooSwitches")
            credentials {
                username = gpr("gpr.user", "GPR_USER")
                password = gpr("gpr.key", "GPR_TOKEN")
            }
        }
    }
}
