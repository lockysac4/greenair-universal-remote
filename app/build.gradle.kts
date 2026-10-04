plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.greenair.universalremote"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.greenair.universalremote"
        minSdk = 23
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
