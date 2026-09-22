plugins { id("com.android.library"); kotlin("android") }
android {
    namespace = "dev.hamfist.shared"
    compileSdk = 36
    defaultConfig { minSdk = 30 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
kotlin { jvmToolchain(21) }
dependencies {
    api(project(":core"))
    implementation("androidx.core:core-ktx:1.16.0")
    api("com.google.android.gms:play-services-wearable:19.0.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
