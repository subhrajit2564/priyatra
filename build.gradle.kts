import org.gradle.api.JavaVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}

// Keeps `gradle/gradle-daemon-jvm.properties` aligned with Java 17 when you run `./gradlew updateDaemonJvm`.
tasks.updateDaemonJvm {
    jvmVersion.set(JavaLanguageVersion.of(17))
}
