plugins {
    alias(libs.plugins.android.application)
}

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

    buildTypes {
        release {
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
