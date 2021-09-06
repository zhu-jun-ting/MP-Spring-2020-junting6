import java.util.function.BiConsumer

buildscript {
    repositories {
        mavenCentral()
        google()
    }
}
plugins {
    id("com.android.application")
    id("checkstyle")
    id("edu.illinois.cs.cs125.gradlegrader") version "2020.1.0"
    id("edu.illinois.cs.cs125.empire") version "2020.1.3"
}
repositories {
    google()
    jcenter()
}
dependencies {
    implementation("androidx.appcompat:appcompat:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:1.1.3")
    implementation("androidx.media:media:1.1.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("com.android.volley:volley:1.1.1")
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("com.google.android.gms:play-services-maps:17.0.0")
    implementation("com.google.android.gms:play-services-location:17.0.0")
    implementation("com.google.firebase:firebase-core:17.2.2")
    implementation("com.google.firebase:firebase-auth:19.2.0")
    implementation("com.firebaseui:firebase-ui-auth:6.2.0")
    implementation("com.neovisionaries:nv-websocket-client:2.9")
    implementation("nz.ac.waikato.cms.weka:weka-stable:3.8.4") {
        exclude(module = "java-cup-runtime")
    }
    testImplementation("junit:junit:4.13")
    testImplementation("org.robolectric:robolectric:4.3.1")
    testImplementation("androidx.test:core:1.2.0")
    testImplementation("org.powermock:powermock-module-junit4:2.0.4")
    testImplementation("org.powermock:powermock-module-junit4-rule:2.0.4")
    testImplementation("org.powermock:powermock-api-mockito2:2.0.4")
    testImplementation("org.powermock:powermock-classloading-xstream:2.0.4")
    testImplementation("com.github.cs125-illinois:gradlegrader:2020.1.0")
    testImplementation("com.github.cs125-illinois:robolectricsecurity:1.1.1")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.10.2")
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.10.2")
}
android {
    compileSdkVersion(29)
    buildToolsVersion("29.0.2")
    defaultConfig {
        applicationId = "edu.illinois.cs.cs125.spring2020.mp"
        minSdkVersion(24)
        targetSdkVersion(29)
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    lintOptions {
        disable("InvalidPackage")
    }
    packagingOptions {
        exclude("META-INF/LICENSE.md")
        exclude("META-INF/NOTICE.md")
    }
}
gradlegrader {
    assignment = "Spring2020.MP"
    checkpoint {
        yamlFile = rootProject.file("grade.yaml")
        configureTests(BiConsumer { checkpoint, test ->
            if (checkpoint in setOf("0", "1", "2", "3", "4", "5")) {
                test.setTestNameIncludePatterns(listOf("Checkpoint${checkpoint}Test"))
                test.filter.isFailOnNoMatchingTests = false
            } else {
                error("Cannot grade unknown checkpoint '$checkpoint'")
            }
        })
    }
    checkstyle {
        points = 10
        configFile = rootProject.file("config/checkstyle.xml")
    }
    forceClean = false
    identification {
        txtFile = rootProject.file("email.txt")
        validate = Spec { it.endsWith("@illinois.edu") }
        message = "You must enter your @illinois.edu email address into email.txt."
    }
    reporting {
        post {
            endpoint = "https://cs125-cloud.cs.illinois.edu/gradlegrader"
        }
        printPretty {
            title = "UNOFFICIAL Grade Summary"
            notes = "On checkpoints with an early deadline, the maximum local score is 90/100. " +
                    "10 points will be provided during official grading if you submitted code " +
                    "that earns at least 40 points by the end of the early deadline day."
        }
    }
    vcs {
        git = true
        requireCommit = true
    }
}
eMPire {
    excludedSrcPath = "edu/illinois/cs/cs125/spring2020/mp"
    studentConfig = rootProject.file("grade.yaml")
    studentCompileTasks("compileDebugJavaWithJavac", "compileReleaseJavaWithJavac")
    segments {
        register("tvc") {
            addJars("lib-tvc")
            removeClasses("logic/TargetVisitChecker")
        }
        register("ad") {
            addJars("lib-ad")
            removeClasses("logic/AreaDivider")
        }
        register("ga1") {
            addAars("lib-dt")
            chimera("chimera-ga1.jar", "GameActivity", "addLine")
        }
        register("wg") {
            addAars("lib-wg")
            removeClasses("MainActivity", "LaunchActivity", "logic/GameSummary")
            manifestEditor("manifest-la.jar", "LaunchManifestEditor")
        }
        register("sgc") {
            addAars("lib-sgc")
            removeClasses("NewGameActivity", "logic/Invitee", "logic/GameSetup")
        }
        register("llr") {
            addJars("lib-llr")
            removeClasses("logic/LineCrossDetector", "logic/Target")
        }
        register("gsc") {
            addJars("lib-gsc")
            removeClasses("logic/TargetVisitChecker", "logic/TargetGame", "logic/AreaGame")
        }
        register("gp") {
            chimera("chimera-gp.jar", "logic/Game", "getTeamScore")
        }
        register("ga4") {
            addAars("lib-gi")
            chimera("chimera-ga4.jar", "GameActivity", "updateRandomWalkDetection",
                    "camo-ga4.jar", "CallScraperKt", "createCamo")
        }
        register("rwd") {
            addAars("lib-rwd")
            removeClasses("GameActivity", "logic/RandomWalkDetector", "logic/Game")
        }
    }
    checkpoints {
        register("0") {
            segments()
        }
        register("1") {
            segments("tvc")
        }
        register("2") {
            segments("tvc", "ad", "ga1")
        }
        register("3") {
            segments("tvc", "ad", "ga1", "wg")
        }
        register("4") {
            segments("tvc", "ad", "wg", "sgc", "llr")
        }
        register("5") {
            segments("ad", "wg", "sgc", "llr", "gsc", "gp", "ga4")
        }
        register("demo") {
            segments("ad", "wg", "sgc", "llr", "gsc", "rwd")
        }
    }
}

apply(plugin = "com.google.gms.google-services")
