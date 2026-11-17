plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.greenair.universalremote"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.greenair.universalremote"
        minSdk = 23
        targetSdk = 35
        versionCode = 8
        versionName = "0.6.3"
    }
    flavorDimensions += "remote"
    productFlavors {
        create("universal") {
            dimension = "remote"
            resValue("string", "app_name", "Greenair Universal Remote")
        }
        create("philips") {
            dimension = "remote"
            applicationIdSuffix = ".philips"
            versionCode = 1
            versionName = "1.0.0"
            resValue("string", "app_name", "Greenair Philips Remote")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
