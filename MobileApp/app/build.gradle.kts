import java.util.Properties

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("com.google.gms.google-services") apply false
  id("org.jetbrains.kotlin.plugin.compose")
  id("com.google.devtools.ksp")
}

// Real Firebase options stay local; a clean debug checkout supports offline guest workflows.
val hasFirebaseConfiguration = file("google-services.json").isFile

if (hasFirebaseConfiguration) apply(plugin = "com.google.gms.google-services")

kotlin {
  // The repository contains one canonical Kotlin tree for each Android source set.
  sourceSets.configureEach { kotlin.setSrcDirs(listOf("src/$name/kotlin")) }
  compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()

if (keystorePropertiesFile.exists()) {
  keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

fun keystoreProperty(name: String): String =
  providers
    .environmentVariable("FIELDFOLIO_" + name.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase())
    .orNull
    ?: keystoreProperties.getProperty(name)
    ?: error("Missing release signing setting: $name")

val hasSigningKey =
  keystorePropertiesFile.exists() ||
    providers.environmentVariable("FIELDFOLIO_STORE_FILE").isPresent

ksp { arg("room.schemaLocation", file("schemas").absolutePath) }

android {
  sourceSets.configureEach { java.setSrcDirs(listOf("src/$name/kotlin")) }
  namespace = "com.karandaev.fieldfolio"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.karandaev.fieldfolio"
    minSdk = 26
    targetSdk = 36
    versionCode = providers.gradleProperty("fieldfolio.versionCode").get().toInt()
    versionName = providers.gradleProperty("fieldfolio.versionName").get()

    if (!hasFirebaseConfiguration) {
      resValue("string", "google_app_id", "1:123456789:android:123456789abcdef")
      resValue("string", "google_api_key", "local-development-placeholder-not-a-real-key")
      resValue("string", "project_id", "fieldfolio-local-development")
      resValue("string", "gcm_defaultSenderId", "123456789")
      resValue("string", "google_storage_bucket", "fieldfolio-local-development.invalid")
      resValue("string", "default_web_client_id", "local-development.invalid")
    }

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    vectorDrawables { useSupportLibrary = true }
  }

  signingConfigs {
    if (hasSigningKey) {
      create("release") {
        storeFile = rootProject.file(keystoreProperty("storeFile"))
        storePassword = keystoreProperty("storePassword")
        keyAlias = keystoreProperty("keyAlias")
        keyPassword = keystoreProperty("keyPassword")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true

      if (hasSigningKey) {
        signingConfig = signingConfigs.getByName("release")
      }

      if (!hasFirebaseConfiguration) {
        // Release credentials are checked by requireReleaseInputs, never replaced with debug ones.
      }
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }

    create("unsignedRelease") {
      initWith(getByName("release"))

      signingConfig = null

      matchingFallbacks += listOf("release")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  testOptions { unitTests.isIncludeAndroidResources = true }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  androidResources { noCompress += "tflite" }

  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
  implementation("androidx.documentfile:documentfile:1.0.1")

  implementation("androidx.palette:palette:1.0.0")

  implementation("androidx.credentials:credentials:1.5.0")
  implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
  implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("com.google.android.material:material:1.12.0")

  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.activity:activity-compose:1.9.2")

  implementation(platform("androidx.compose:compose-bom:2024.09.03"))

  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-graphics")
  implementation("androidx.compose.ui:ui-tooling-preview")

  implementation("androidx.compose.material:material")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")

  debugImplementation("androidx.compose.ui:ui-tooling")
  debugImplementation("androidx.compose.ui:ui-test-manifest")

  implementation("androidx.navigation:navigation-compose:2.8.2")

  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

  implementation(platform("com.google.firebase:firebase-bom:33.5.1"))

  implementation("com.google.firebase:firebase-auth-ktx")
  implementation("com.google.firebase:firebase-analytics-ktx")
  implementation("com.google.firebase:firebase-storage")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

  implementation("com.google.android.gms:play-services-auth:21.2.0")

  // Room local database
  val roomVersion = "2.8.5"
  implementation("androidx.room:room-runtime:$roomVersion")
  implementation("androidx.room:room-ktx:$roomVersion")
  ksp("androidx.room:room-compiler:$roomVersion")

  // osmdroid - OpenStreetMap for Android without an API key
  implementation("org.osmdroid:osmdroid-android:6.1.20")

  // Retrofit + Gson
  implementation("com.squareup.retrofit2:retrofit:2.11.0")
  implementation("com.squareup.retrofit2:converter-gson:2.11.0")

  // Moshi + KSP codegen
  implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
  implementation("com.squareup.moshi:moshi:1.15.1")
  ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")

  // OkHttp
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

  // Coil
  implementation("io.coil-kt:coil-compose:2.7.0")

  implementation("sh.calvin.reorderable:reorderable:3.1.0")
  implementation("androidx.webkit:webkit:1.13.0")

  // TensorFlow Lite / LiteRT for local YOLO inference
  implementation("org.tensorflow:tensorflow-lite:2.16.1")
  implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

  testImplementation("junit:junit:4.13.2")
  testImplementation("org.robolectric:robolectric:4.14.1")
  testImplementation("androidx.test:core:1.6.1")
  testImplementation("androidx.test.ext:junit:1.2.1")
  testImplementation("androidx.compose.ui:ui-test-junit4")

  androidTestImplementation("androidx.test.ext:junit:1.2.1")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

/** Fail release builds rather than silently signing with a development identity. */
val requireReleaseInputs =
  tasks.register("requireReleaseInputs") {
    doLast {
      check(hasFirebaseConfiguration) { "Add app/google-services.json for a release build." }
      check(hasSigningKey) {
        "Configure release signing using FIELDFOLIO_* or keystore.properties."
      }
    }
  }

tasks
  .matching { it.name in setOf("assembleRelease", "bundleRelease", "packageRelease") }
  .configureEach { dependsOn(requireReleaseInputs) }

tasks
  .matching { it.name == "assembleUnsignedRelease" }
  .configureEach {
    doFirst {
      check(hasFirebaseConfiguration) { "Add app/google-services.json for an unsigned release." }
    }
  }

tasks.withType<Test>().configureEach {
  maxHeapSize = "1536m"
  maxParallelForks = 1
  // Compose/native graphics tests must not share process-global dispatchers with plain JVM cases.
  forkEvery = 1
  testLogging {
    events("failed", "skipped")
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}

// Explicit opt-in keeps normal Android Studio tests independent of Python bootstrap tooling.
if (providers.gradleProperty("offlineTestNetwork").map { it.toBoolean() }.getOrElse(false)) {
  val compileNetworkGuard =
    tasks.register<org.gradle.api.tasks.compile.JavaCompile>("compileNetworkGuard") {
      source(rootProject.file("scripts/testing/OfflineNetworkAgent.java"))
      classpath = files()
      destinationDirectory.set(layout.buildDirectory.dir("network-guard/classes"))
      options.release.set(17)
    }
  val networkGuard =
    tasks.register<org.gradle.jvm.tasks.Jar>("networkGuard") {
      dependsOn(compileNetworkGuard)
      from(compileNetworkGuard.flatMap { it.destinationDirectory })
      destinationDirectory.set(layout.buildDirectory.dir("network-guard"))
      archiveFileName.set("offline-network.jar")
      manifest.attributes("Premain-Class" to "OfflineNetworkAgent")
    }
  tasks.withType<Test>().configureEach {
    dependsOn(networkGuard)
    jvmArgs("-javaagent:" + networkGuard.get().archiveFile.get().asFile.absolutePath)
    systemProperty("robolectric.offline", "true")
    systemProperty(
      "robolectric.dependency.dir",
      rootProject.file("build/robolectric-sdks").absolutePath,
    )
  }
}
