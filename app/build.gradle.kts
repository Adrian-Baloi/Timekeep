import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun Project.gradleOrEnv(name: String, default: String = ""): String {
    return providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
        ?: default
}

fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val defaultResourceServer = "https://newformaholdingsdev.haloitsm.com/api"
val defaultAuthServer = "https://newformaholdingsdev.haloitsm.com/auth"
val defaultTenantId = "newformaholdingsdev"
val defaultEnvironmentLabel = "Newforma DEV Sandbox"
val redirectUriValue = project.gradleOrEnv("HALO_REDIRECT_URI", "timekeep://auth/callback")
val redirectUri = URI(redirectUriValue)
val redirectScheme = redirectUri.scheme ?: "timekeep"
val redirectHost = redirectUri.host ?: "auth"
val redirectPath = redirectUri.path?.ifBlank { "/callback" } ?: "/callback"

android {
    namespace = "com.newforma.timekeep"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.newforma.timekeep"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders += mapOf(
            "haloRedirectScheme" to redirectScheme,
            "haloRedirectHost" to redirectHost,
            "haloRedirectPath" to redirectPath,
        )
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        all {
            buildConfigField("String", "HALO_ENVIRONMENT_LABEL", project.gradleOrEnv("HALO_ENVIRONMENT_LABEL", defaultEnvironmentLabel).asBuildConfigString())
            buildConfigField("String", "HALO_RESOURCE_SERVER_URL", project.gradleOrEnv("HALO_RESOURCE_SERVER_URL", defaultResourceServer).asBuildConfigString())
            buildConfigField("String", "HALO_AUTH_SERVER_URL", project.gradleOrEnv("HALO_AUTH_SERVER_URL", defaultAuthServer).asBuildConfigString())
            buildConfigField("String", "HALO_TENANT_ID", project.gradleOrEnv("HALO_TENANT_ID", defaultTenantId).asBuildConfigString())
            buildConfigField("String", "HALO_CLIENT_ID", project.gradleOrEnv("HALO_CLIENT_ID", "878f661c-9f8d-42a1-bf85-a0b0037c76c0").asBuildConfigString())
            buildConfigField("String", "HALO_CLIENT_SECRET", project.gradleOrEnv("HALO_CLIENT_SECRET").asBuildConfigString())
            buildConfigField("String", "HALO_REDIRECT_URI", redirectUriValue.asBuildConfigString())
            buildConfigField("String", "HALO_REDIRECT_SCHEME", redirectScheme.asBuildConfigString())
            buildConfigField("String", "HALO_REDIRECT_HOST", redirectHost.asBuildConfigString())
            buildConfigField("String", "HALO_REDIRECT_PATH", redirectPath.asBuildConfigString())
            buildConfigField("String", "HALO_SCOPES", project.gradleOrEnv("HALO_SCOPES", "openid profile offline_access").asBuildConfigString())
            buildConfigField("boolean", "HALO_USE_PKCE", project.gradleOrEnv("HALO_USE_PKCE", "true"))
            buildConfigField("boolean", "HALO_INCLUDE_TENANT_ON_TOKEN_REQUESTS", project.gradleOrEnv("HALO_INCLUDE_TENANT_ON_TOKEN_REQUESTS", "true"))
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

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

