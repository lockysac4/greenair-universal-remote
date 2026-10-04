plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.greenair.universalremote"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.greenair.universalremote"
        minSdk = 23
        targetSdk = 35
        versionCode = 6
        versionName = "0.6.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
