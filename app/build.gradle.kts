import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import org.gradle.api.tasks.Exec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
    alias(libs.plugins.firebase.app.distribution)
}

val releaseSigningKeys =
    listOf(
        "RELEASE_STORE_FILE",
        "RELEASE_STORE_PASSWORD",
        "RELEASE_KEY_ALIAS",
        "RELEASE_KEY_PASSWORD",
    )
val releaseSigningValues =
    releaseSigningKeys
        .mapNotNull { key -> System.getenv(key)?.takeIf(String::isNotBlank)?.let { key to it } }
        .toMap()

if (releaseSigningValues.isNotEmpty() && releaseSigningValues.size != releaseSigningKeys.size) {
    throw GradleException(
        "Incomplete release signing configuration. Missing: " +
            (releaseSigningKeys - releaseSigningValues.keys).joinToString(),
    )
}

val releaseSigningConfigured = releaseSigningValues.size == releaseSigningKeys.size

android {
    namespace = "com.example.smsforwarder"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.smsforwarder"
        // S25용 최소 버전
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                val releaseKeystore = file(releaseSigningValues.getValue("RELEASE_STORE_FILE"))
                if (!releaseKeystore.isFile) {
                    throw GradleException("Release keystore does not exist: $releaseKeystore")
                }
                storeFile = releaseKeystore
                storePassword = releaseSigningValues.getValue("RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigningValues.getValue("RELEASE_KEY_ALIAS")
                keyPassword = releaseSigningValues.getValue("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            firebaseAppDistribution {
                appId = System.getenv("FIREBASE_APP_ID").orEmpty()
                serviceCredentialsFile = System.getenv("GOOGLE_APPLICATION_CREDENTIALS").orEmpty()
                groups = System.getenv("FIREBASE_TESTER_GROUPS").orEmpty().ifBlank { "testers" }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        animationsDisabled = true
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        managedDevices {
            localDevices {
                create("pixel2Api30") {
                    device = "Pixel 2"
                    apiLevel = 30
                    systemImageSource = "aosp"
                }
            }
        }
    }
}

val releaseSigningCheckCommand =
    if (releaseSigningConfigured) {
        "exit 0"
    } else {
        "echo 'Release tasks require RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, " +
            "RELEASE_KEY_ALIAS, and RELEASE_KEY_PASSWORD.' >&2; exit 1"
    }
val checkReleaseSigningForRelease =
    tasks.register<Exec>("checkReleaseSigningForRelease") {
        group = "verification"
        description = "Validates release signing credentials before building the release variant."
        commandLine("sh", "-c", releaseSigningCheckCommand)
    }

tasks.configureEach {
    if (name == "preReleaseBuild" || name == "appDistributionUploadRelease") {
        dependsOn(checkReleaseSigningForRelease)
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

ktlint {
    version.set(libs.versions.ktlint.get())
    outputToConsole.set(true)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestUtil(libs.androidx.test.orchestrator)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
