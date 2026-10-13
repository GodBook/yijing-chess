plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.yijing.chess"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yijing.chess"
        minSdk = 30
        targetSdk = 35
        versionCode = 5
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("shared") {
            storeFile = file("signing/app.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.register<JavaExec>("verifyChessRules") {
    description = "Runs every regression test and writes JUnit XML reports"
    group = "verification"
    dependsOn("compileDebugKotlin", "compileDebugUnitTestKotlin")
    mainClass.set("com.yijing.chess.RegressionRunner")
    val testClasses = layout.buildDirectory.dir("intermediates/built_in_kotlinc/debugUnitTest/compileDebugUnitTestKotlin/classes")
    val appClasses = layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")
    classpath = files(testClasses, appClasses, configurations.getByName("debugUnitTestRuntimeClasspath"))
    jvmArgs("-Dfile.encoding=UTF-8")
    doFirst {
        val root = testClasses.get().asFile
        val classes = fileTree(root).matching { include("**/*Test.class") }.files
            .filter { !it.name.contains('$') }
            .map { it.relativeTo(root).path.removeSuffix(".class").replace(File.separatorChar, '.') }.sorted()
        require(classes.isNotEmpty()) { "No regression tests discovered" }
        args = listOf(layout.buildDirectory.dir("test-results/verifyChessRules").get().asFile.path) + classes
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.code.gson:gson:2.13.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.02.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
