import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.priyatra.guide"
    compileSdk = 35

    val localProps = Properties()
    val lp = rootProject.file("local.properties")
    if (lp.exists()) localProps.load(lp.inputStream())
    // local.properties, then env (GitHub Actions secrets → env in workflow).
    fun firstNonBlank(vararg s: String?): String =
        s.firstOrNull { !it.isNullOrBlank() } ?: ""
    val supabaseUrl = firstNonBlank(
        localProps.getProperty("SUPABASE_URL"),
        localProps.getProperty("NEXT_PUBLIC_SUPABASE_URL"),
        System.getenv("SUPABASE_URL"),
        System.getenv("NEXT_PUBLIC_SUPABASE_URL"),
    ).replace("\"", "'")
    val supabaseKey = firstNonBlank(
        localProps.getProperty("SUPABASE_ANON_KEY"),
        localProps.getProperty("NEXT_PUBLIC_SUPABASE_ANON_KEY"),
        localProps.getProperty("NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY"),
        System.getenv("SUPABASE_ANON_KEY"),
        System.getenv("NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY"),
    ).replace("\"", "'")
    val groqKey = localProps.getProperty("GROQ_API_KEY") ?: ""
    val llmBase = localProps.getProperty("LLM_BASE_URL") ?: ""
    val llmModel = localProps.getProperty("LLM_MODEL") ?: ""
    // Cap completion tokens so input + max_tokens fits model / gateway limits (avoids "request too large for model")
    val llmMaxOutTokens = (localProps.getProperty("LLM_MAX_TOKENS") ?: "8192")
        .toIntOrNull()
        ?.coerceIn(1024, 8192)
        ?: 8192

    defaultConfig {
        applicationId = "com.priyatra.guide"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.1.4-poc"
        buildConfigField("String", "GROQ_API_KEY", "\"$groqKey\"")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseKey\"")
        buildConfigField("String", "LLM_BASE_URL", "\"$llmBase\"")
        buildConfigField("String", "LLM_MODEL", "\"$llmModel\"")
        buildConfigField("int", "LLM_MAX_TOKENS", llmMaxOutTokens.toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.maps.android:maps-compose:6.1.2")
    implementation("com.google.android.gms:play-services-maps:19.0.0")

    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
}
