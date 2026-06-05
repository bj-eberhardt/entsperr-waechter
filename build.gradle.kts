import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    id("com.android.application") version "8.8.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10" apply false
    id("com.diffplug.spotless") version "8.5.1"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.android") {
        extensions.configure<KotlinAndroidProjectExtension>("kotlin") {
            jvmToolchain(17)
        }
    }
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude(
            "**/build/**",
            "**/.gradle/**",
            ".gradle-home/**",
            "**/.gradle-home/**",
            ".gradle-user-home/**",
            "**/.gradle-user-home/**",
        )
        ktlint("1.7.1").editorConfigOverride(
            mapOf("ktlint_function_naming_ignore_when_annotated_with" to "Composable"),
        )
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude(
            "**/build/**",
            "**/.gradle/**",
            ".gradle-home/**",
            "**/.gradle-home/**",
            ".gradle-user-home/**",
            "**/.gradle-user-home/**",
        )
        ktlint("1.7.1")
    }

    format("markdown") {
        target("*.md", "**/*.md")
        targetExclude(
            "**/build/**",
            "**/.gradle/**",
            ".gradle-home/**",
            "**/.gradle-home/**",
            ".gradle-user-home/**",
            "**/.gradle-user-home/**",
            "pages-dist/**",
            "**/pages-dist/**",
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("detekt.yml"))
    baseline = file("detekt-baseline.xml")
    source.setFrom(files("app/src/main/java"))
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}
