import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
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
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(
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
        ktlint(libs.versions.ktlint.get())
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
