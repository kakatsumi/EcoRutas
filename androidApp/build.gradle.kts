import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins { alias(libs.plugins.androidApplication); alias(libs.plugins.composeCompiler) }
kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_11 } }
dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.koin.core)
    implementation(libs.ktor.client.core)
    implementation(libs.koin.compose)
    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}
android {
    namespace = "pe.upeu.ecorutas"
    compileSdk = 36
    defaultConfig {
        applicationId = "pe.upeu.ecorutas"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    buildFeatures { compose = true }
}
