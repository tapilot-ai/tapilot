plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "lifeos.md.tapilot"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "lifeos.md.tapilot"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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

    sourceSets["main"].assets.srcDir("../dist/runtime")
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
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.webkit:webkit:1.12.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
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
    dependsOn(verifyRuntimeAssets)
}

tasks.configureEach {
    if (name == "packageDebug" || name == "packageRelease") {
        dependsOn(verifyNodeRuntimeAssets)
    }
}
