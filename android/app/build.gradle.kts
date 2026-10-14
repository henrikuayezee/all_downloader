import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Release signing. The key and its passwords never live in this repo: the
// Gradle property `grab.signing` (set in ~/.gradle/gradle.properties) points
// at a keystore.properties file kept outside it. Without that property,
// release builds still work but come out unsigned.
val signingProps: Properties? = providers.gradleProperty("grab.signing").orNull
    ?.let { file(it) }
    ?.takeIf { it.isFile }
    ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }

android {
    namespace = "io.github.henrikuayezee.grab"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.henrikuayezee.grab"
        // Android 10+: saving into Movies/ through MediaStore needs no storage permission there.
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (signingProps != null) {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (signingProps != null) signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// The web app in the repo root is the single source of truth. Copy it into
// a generated (gitignored) assets folder on every build instead of keeping
// a second copy that could drift.
val syncWebAssets by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.dir("..")) {
        include("index.html", "vendor/**", "icon-*.png")
    }
    into(layout.projectDirectory.dir("src/main/assets/www"))
}
tasks.named("preBuild") { dependsOn(syncWebAssets) }

dependencies {
    implementation(libs.webkit)
}
