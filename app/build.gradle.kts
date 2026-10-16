import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use(::load)
}

fun Project.gradleOrEnvOrLocal(name: String, default: String = ""): String =
    providers.gradleProperty(name).orNull?.takeIf(String::isNotBlank)
        ?: providers.environmentVariable(name).orNull?.takeIf(String::isNotBlank)
        ?: localProperties.getProperty(name)?.takeIf(String::isNotBlank)
        ?: default

val haloResourceServerUrl = project.gradleOrEnvOrLocal(
    "HALO_RESOURCE_SERVER_URL",
    "https://newformaholdingsdev.haloitsm.com/api/",
)
val haloAuthServerUrl = project.gradleOrEnvOrLocal(
    "HALO_AUTH_SERVER_URL",
    "https://newformaholdingsdev.haloitsm.com/auth/",
)
val haloTenantId = project.gradleOrEnvOrLocal("HALO_TENANT_ID", "newformaholdingsdev")
val haloClientId = project.gradleOrEnvOrLocal("HALO_CLIENT_ID")
val haloRedirectUri = project.gradleOrEnvOrLocal("HALO_REDIRECT_URI", "timekeep://auth/callback")
val haloScopes = (
    localProperties.getProperty("HALO_SCOPES")?.takeIf(String::isNotBlank)
        ?: project.gradleOrEnvOrLocal("HALO_SCOPES", "openid profile offline_access")
)
    .split(Regex("\\s+"))
    .filter(String::isNotBlank)
    .plus("read:assets")
    .plus("edit:tickets")
    .plus("read:users")
    .plus("read:customers")
    .distinct()
    .joinToString(" ")
val haloIncludeTenant = project.gradleOrEnvOrLocal("HALO_INCLUDE_TENANT_ON_TOKEN_REQUESTS", "true")
val useFakeAssetRepository = project.gradleOrEnvOrLocal("USE_FAKE_ASSET_REPOSITORY", "false")
val haloRedirectScheme = URI(haloRedirectUri).scheme
    ?: error("HALO_REDIRECT_URI must include a URI scheme")

android {
    namespace = "com.example.timekeep_work"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.timekeep_work"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appAuthRedirectScheme"] = haloRedirectScheme
        buildConfigField(
            "String",
            "HALO_RESOURCE_SERVER_URL",
            haloResourceServerUrl.trim().trimEnd('/').plus('/').asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "HALO_AUTH_SERVER_URL",
            haloAuthServerUrl.trim().trimEnd('/').plus('/').asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "HALO_TENANT_ID",
            haloTenantId.trim().asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "HALO_CLIENT_ID",
            haloClientId.trim().asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "HALO_REDIRECT_URI",
            haloRedirectUri.trim().asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "HALO_SCOPES",
            haloScopes.trim().asBuildConfigString(),
        )
        buildConfigField(
            "boolean",
            "HALO_INCLUDE_TENANT_ON_TOKEN_REQUESTS",
            haloIncludeTenant,
        )
        buildConfigField(
            "boolean",
            "USE_FAKE_ASSET_REPOSITORY",
            useFakeAssetRepository,
        )
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.appauth)
    implementation(libs.google.code.scanner)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
