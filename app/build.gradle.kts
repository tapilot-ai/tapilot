import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "lifeos.md.tapilot"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "lifeos.md.tapilot"
        if (providers.gradleProperty("speechVerification").isPresent) applicationIdSuffix = ".speechverify"
        minSdk = 30
        targetSdk = 35
        // An older local build exercises the available-update UI against the real published feed.
        // This verification package is never distributed as a release.
        val updateVerification = providers.gradleProperty("updateVerification").isPresent
        versionCode = if (updateVerification) 1 else 6
        versionName = if (updateVerification) "0.1.0-update-test" else "0.1.5"
    }

    buildTypes {
        release {
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
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    if (providers.gradleProperty("speechVerification").isPresent) {
        sourceSets["debug"].assets.srcDir("../dist/runtime")
        sourceSets["debug"].java.srcDir("src/speechVerification/java")
        sourceSets["debug"].manifest.srcFile("src/speechVerification/AndroidManifest.xml")
    } else {
        // Offline reports still need their export helper and branding assets.
        sourceSets["main"].assets.srcDir("../public")
    }
    sourceSets["main"].jniLibs.srcDir("../.cache/native")

    packaging {
        jniLibs.useLegacyPackaging = true
    }

    bundle {
        language.enableSplit = false
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2025.03.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

val verifyBrandTheme by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("node", "scripts/generate-brand.mjs", "--check")
}

val verifyRuntimeAssets by tasks.registering {
    val runtimeIndex = rootProject.file("dist/runtime/index.html")
    inputs.file(runtimeIndex)
    doLast {
        check(runtimeIndex.isFile) {
            "Missing embedded Tapilot runtime. Run `pnpm --filter tapilot build`."
        }
    }
}

val verifyReportAssets by tasks.registering {
    val reportFiles = listOf("report-export.js", "tapilot-icon.png").map { rootProject.file("public/$it") }
    inputs.files(reportFiles)
    doLast { check(reportFiles.all { it.isFile }) { "Missing offline report assets" } }
}

val verifyNodeRuntimeAssets by tasks.registering {
    val nodeBundle = rootProject.file("app/src/main/assets/node-runtime-bundle.zip")
    val nodeManifest = rootProject.file("app/src/main/assets/node-runtime-manifest.json")
    val nodeBinary = rootProject.file(".cache/native/arm64-v8a/libnodebin.so")
    inputs.files(nodeBundle, nodeManifest, nodeBinary)
    doLast {
        check(nodeBundle.isFile && nodeManifest.isFile && nodeBinary.isFile) {
            "Missing embedded Node runtime. Run `pnpm --filter tapilot build:android`."
        }
    }
}

tasks.named("preBuild") {
    dependsOn(verifyBrandTheme, verifyReportAssets)
    if (providers.gradleProperty("speechVerification").isPresent) dependsOn(verifyRuntimeAssets)
}

tasks.configureEach {
    if (name == "packageDebug" || name == "packageRelease") {
        dependsOn(verifyNodeRuntimeAssets)
    }
    if (name == "assembleDebug" && !providers.gradleProperty("speechVerification").isPresent) {
        doLast {
            ZipFile(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile).use { apk ->
                val names = apk.entries().asSequence().map { it.name }.toSet()
                check("assets/report-export.js" in names && "assets/tapilot-icon.png" in names) {
                    "Native APK is missing offline report assets"
                }
                check("assets/index.html" !in names && names.none { it.startsWith("assets/static/") }) {
                    "Native APK must not include the legacy web UI"
                }
            }
        }
    }
}
