plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    `maven-publish`
}

// Coordinates match what JitPack would give the public repo
// (com.github.anpaiss:KarooSwitches), so the dependency line never changes
// between a local build (publishToMavenLocal) and the published one.
group = "com.github.anpaiss"
version = "0.1.0"

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

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "KarooSwitches"
            afterEvaluate { from(components["release"]) }
        }
    }
}
