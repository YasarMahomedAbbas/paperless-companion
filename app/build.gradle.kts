plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
}

// Release signing comes from env vars set by CI, never from the repo.
val keystorePath: String? = System.getenv("RELEASE_KEYSTORE")

android {
    namespace = "io.github.yasarmahomedabbas.paperlesscompanion"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.yasarmahomedabbas.paperlesscompanion"
        minSdk = 26
        targetSdk = 35
        // CI passes these from the git tag and run number; local builds get a dev version.
        versionCode = providers.gradleProperty("versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("versionName").orNull ?: "0.0.0-dev"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "release"
                keyPassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        warningsAsErrors = false
        abortOnError = true
    }
    // Robolectric needs the merged resources and manifest to run Compose and Material 3.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// The Android SDK jar Robolectric runs the screen tests against, kept apart from the app's own
// dependencies.
val robolectricSdk by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

// Robolectric would download its SDK jar into ~/.m2, which the Docker build loses after
// every run. Gradle resolves it into GRADLE_USER_HOME instead (cached on the host by
// build.sh and on CI by setup-gradle), and Robolectric runs offline against it.
tasks.withType<Test>().configureEach {
    inputs.files(robolectricSdk).withPropertyName("robolectricSdk").withNormalizer(ClasspathNormalizer::class.java)
    jvmArgumentProviders += CommandLineArgumentProvider {
        listOf(
            "-Drobolectric.offline=true",
            "-Drobolectric.dependency.dir=${robolectricSdk.singleFile.parentFile}",
        )
    }
}

// Coverage is measured on the domain and data layers and the ViewModels. Compose screens, the
// activity and the Keystore-backed classes need a device, so they are left out.
kover {
    reports {
        filters {
            excludes {
                annotatedBy("androidx.compose.runtime.Composable")
                packages("io.github.yasarmahomedabbas.paperlesscompanion.ui.theme")
                classes(
                    "*.MainActivity*",
                    "*.PaperlessApp",
                    "*.AppContainer*",
                    "*.ComposableSingletons*",
                    "*Screen*",
                    "*.data.KeystoreTokenVault*",
                    "*.data.PrefsStore*",
                    "*.data.UriDocuments*",
                    "*.data.update.PackageInstallerUpdates*",
                    "*.data.update.InstallResultReceiver*",
                    "*.data.update.PrefsUpdateStore*",
                    "*.data.update.UpdateWorker*",
                    "*.data.update.UpdateSchedule*",
                    "*.data.update.UpdateNotifier*",
                )
            }
        }
        verify {
            rule {
                minBound(80)
            }
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.biometric)
    implementation(libs.fragment.ktx)
    // For its theme: the biometric prompt's fingerprint dialog before API 28 is an AppCompat dialog.
    implementation(libs.appcompat)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.work.runtime)
    implementation(libs.mlkit.document.scanner)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    // Puts the ComponentActivity that createComposeRule() launches into the debug manifest.
    debugImplementation(libs.compose.ui.test.manifest)
    robolectricSdk(libs.robolectric.android.all)
}
