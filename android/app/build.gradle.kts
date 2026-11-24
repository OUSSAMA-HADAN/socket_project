plugins {
    id("com.android.application")
}

android {
    // Android needs a dotted id; our Java packages stay short (mobile.*), the R class lives in chatfx.mobile.
    namespace = "chatfx.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "chatfx.mobile"
        minSdk = 26 // Android 8.0: java.time, and everything common/ needs
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            // The SAME protocol and network code as the server and the desktop app:
            // compiled straight from the Maven "common" module, so they never drift apart.
            java.directories.add("../../common/src/main/java")
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    // Google's emoji keyboard panel (categories, recents, skin tones); emoji themselves are native on Android
    implementation("androidx.emoji2:emoji2-emojipicker:1.7.0")
}
