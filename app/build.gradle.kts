import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val keystoreFile = rootProject.file("keystore.properties")

android {
    namespace = "com.sheuberger.fitness"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sheuberger.fitnesstracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.6"
    }

    // Signatur für die Release-APK. Android installiert nur signierte Apps, und Updates
    // müssen mit demselben Schlüssel signiert sein. Die Zugangsdaten liegen in keystore.properties
    // (nicht im Git). Ohne diese Datei bricht der Release-Build ab (siehe checkReleaseKey unten).
    signingConfigs {
        if (keystoreFile.exists()) {
            val props = Properties().apply { keystoreFile.inputStream().use(::load) }
            create("release") {
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
                // Alle Signaturverfahren aktivieren, damit jeder Paket-Installer die APK akzeptiert.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // Eigene Paket-ID für Debug-Builds: So können Debug- und Release-Version nebeneinander
            // installiert sein, statt sich gegenseitig zu blockieren ("App wurde nicht installiert").
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }
    testOptions {
        // Robolectric braucht die Android-Ressourcen und Assets für die Datenbank-Tests.
        unitTests.isIncludeAndroidResources = true
    }
    sourceSets {
        // Die exportierten Schemas aller bisherigen Datenbankversionen. Die Migrationstests
        // bauen damit alte Datenbanken nach, wie sie auf dem Handy liegen. Robolectric liest die Assets
        // der Debug-Variante; in der Release-APK landen die Schemas nicht.
        getByName("debug").assets.srcDir("$projectDir/schemas")
    }
}

// Ein Update lässt sich nur installieren, wenn die neue APK mit demselben Schlüssel signiert ist wie die
// installierte. Ist er anders, bleibt nur Deinstallieren, und dabei löscht Android alle Einträge. Deshalb
// bricht der Release-Build ab, wenn keystore.properties fehlt oder auf einen anderen Schlüssel zeigt.
// Der Fingerabdruck gehört zu dem Schlüssel, mit dem die APKs 1.0 bis 1.3 signiert sind.
val releaseKeySha256 = "c87dab8a8021d277e367a8c1b4a63da745fb5e7a32f5a35ed85e84ecab240914"

val checkReleaseKey by tasks.registering {
    doLast {
        if (!keystoreFile.exists()) {
            throw GradleException(
                "keystore.properties fehlt. Ohne den Release-Schlüssel lässt sich die APK nicht als Update " +
                    "installieren. Datei ins Projektverzeichnis kopieren und erneut bauen."
            )
        }
        val props = Properties().apply { keystoreFile.inputStream().use(::load) }
        val keyStore = KeyStore.getInstance("JKS")
        file(props.getProperty("storeFile")).inputStream().use {
            keyStore.load(it, props.getProperty("storePassword").toCharArray())
        }
        val cert = keyStore.getCertificate(props.getProperty("keyAlias"))
            ?: throw GradleException("Schlüssel '${props.getProperty("keyAlias")}' nicht im Keystore gefunden.")
        val sha256 = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString("") { "%02x".format(it) }
        if (sha256 != releaseKeySha256) {
            throw GradleException(
                "Falscher Release-Schlüssel ($sha256). Eine damit signierte APK ließe sich nicht als Update " +
                    "installieren, und eine Neuinstallation würde alle Einträge löschen."
            )
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseKey) }

// Room schreibt die Schema-Datei einer neuen Version erst beim Kompilieren (KSP). Die Tests brauchen sie
// schon als Asset, deshalb Assets erst danach zusammenstellen.
tasks.matching { it.name == "mergeDebugAssets" }.configureEach { dependsOn("kspDebugKotlin") }

ksp {
    // Room speichert hier das Datenbankschema. Das hilft später bei Migrationen.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
}
