plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "dev.onetools"
    compileSdk = 37

    defaultConfig {
        // Device checks use a separate installation and never replace the user's app.
        applicationId = if (providers.gradleProperty("isolatedAcceptance").orNull == "true") "dev.onetools.acceptance" else "dev.onetools"
        minSdk = 30
        targetSdk = 36
        versionCode = 19
        versionName = "1.10.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testApplicationId = if (providers.gradleProperty("isolatedAcceptance").orNull == "true") "dev.onetools.acceptance.test"
            else if (providers.gradleProperty("testBuildType").orNull == "release") "dev.onetools.release.test" else "dev.onetools.test"
    }
    signingConfigs {
        create("release") {
            storeFile = providers.environmentVariable("ONETOOLS_KEYSTORE").orNull?.takeIf { it.isNotBlank() }?.let { file(it) }
            storePassword = providers.environmentVariable("ONETOOLS_STORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("ONETOOLS_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("ONETOOLS_KEY_PASSWORD").orNull
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }
    testBuildType = providers.gradleProperty("testBuildType").orElse("debug").get()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Export through the public artifact API; keep AGP metadata/install outputs intact.
androidComponents {
    onVariants(selector().all()) { variant ->
        val variantTaskName = variant.name.replaceFirstChar { it.uppercaseChar() }
        val exportApk = tasks.register<Sync>("export${variantTaskName}Apk") {
            from(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK))
            include("*.apk")
            into(layout.buildDirectory.dir("outputs/distribution/${variant.name}"))
            rename { "OneTools-${variant.outputs.single().versionName.get()}.apk" }
        }
        tasks.matching { it.name == "assemble$variantTaskName" }.configureEach {
            dependsOn(exportApk)
        }
    }
}

val validateReleaseCredentials by tasks.registering {
    doLast {
        check(listOf("ONETOOLS_KEYSTORE", "ONETOOLS_STORE_PASSWORD", "ONETOOLS_KEY_ALIAS", "ONETOOLS_KEY_PASSWORD")
            .all { !providers.environmentVariable(it).orNull.isNullOrBlank() }) {
            "Release signing credentials are required. Run scripts/Build-Release.ps1."
        }
    }
}
tasks.matching { it.name == "validateSigningRelease" }.configureEach {
    dependsOn(validateReleaseCredentials)
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.3")
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

