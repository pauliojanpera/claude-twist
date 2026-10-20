plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Build identity baked into the APK, so the settings screen can name the commit it came from.
fun gitOutput(vararg args: String): String = runCatching {
    val process = ProcessBuilder(listOf("git", *args))
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    process.inputStream.bufferedReader().use { it.readText() }.trim()
        .takeIf { process.waitFor() == 0 }
}.getOrNull().orEmpty()

val buildCommit = gitOutput("log", "-1", "--format=%h").ifBlank { "dev" }
val buildDate = gitOutput("log", "-1", "--format=%cd", "--date=format:%Y%m%d").ifBlank { "00000000" }

// The release identity, overridable so a tagged build stamps the version it was cut from.
fun buildInput(name: String, environmentName: String): String? =
    (project.findProperty(name) as String?) ?: System.getenv(environmentName)

val releaseVersionName = buildInput("versionName", "VERSION_NAME") ?: "0.1.0"
val releaseVersionCode = buildInput("versionCode", "VERSION_CODE")?.toIntOrNull() ?: 1

// Release signing from a keystore handed in at build time and never committed — it lives outside the
// tree, and in CI it is restored from a secret. Without it the release APK comes out unsigned, which
// installs nowhere: the honest outcome of having no key, and what the release workflow verifies.
val keystoreFile = buildInput("keystoreFile", "KEYSTORE_FILE")

android {
    namespace = "net.miettii.claudetwist"
    compileSdk = 34

    defaultConfig {
        applicationId = "net.miettii.claudetwist"
        minSdk = 29
        targetSdk = 34
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        buildConfigField("String", "BUILD_COMMIT", "\"$buildCommit\"")
        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")
    }

    signingConfigs {
        if (keystoreFile != null && file(keystoreFile).exists()) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = buildInput("keystorePassword", "KEYSTORE_PASSWORD")
                keyAlias = buildInput("keyAlias", "KEY_ALIAS")
                keyPassword = buildInput("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8 is left off: the app is small, and shrinking it would buy little against the risk
            // of trimming something Compose or DataStore reaches reflectively.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // The toolchain is pinned on purpose: these versions are the ones this project is known to
        // build with, against the one platform the build machines carry. Both checks below only
        // report that something newer exists, which is not a defect in this tree.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
