import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "dev.icebear.yac"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        phpstorm("2025.3.3")
        bundledPlugin("com.jetbrains.php")
        pluginVerifier("1.410")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

intellijPlatform {
    pluginConfiguration {
        val pluginVersion = project.version.toString()
        changeNotes = providers.fileContents(layout.projectDirectory.file("CHANGELOG.md")).asText.map { changelog ->
            fun inlineHtml(text: String): String =
                text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace(Regex("`([^`]+)`"), "<code>$1</code>")

            val lines = changelog.lines()
            val start = lines.indexOfFirst { it.startsWith("## [$pluginVersion]") }
            check(start >= 0) { "CHANGELOG.md has no section for $pluginVersion." }

            val html = StringBuilder()
            var isListOpen = false
            fun closeList() {
                if (isListOpen) {
                    html.append("</li></ul>")
                    isListOpen = false
                }
            }

            lines.drop(start + 1).takeWhile { !it.startsWith("## [") }.map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                when {
                    line.startsWith("### ") -> {
                        closeList()
                        html.append("<h3>").append(inlineHtml(line.removePrefix("### "))).append("</h3>")
                    }
                    line.startsWith("• ") -> {
                        html.append(if (isListOpen) "</li><li>" else "<ul><li>")
                        isListOpen = true
                        html.append(inlineHtml(line.removePrefix("• ").replace(Regex("^(MAJOR|MINOR|PATCH) "), "")))
                    }
                    line.startsWith("- ") -> html.append("<br>").append(inlineHtml(line.removePrefix("- ")))
                    else -> {
                        closeList()
                        html.append("<p>").append(inlineHtml(line)).append("</p>")
                    }
                }
            }
            closeList()

            html.toString()
        }
        ideaVersion {
            sinceBuild = "253"
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            create(IntelliJPlatformType.PhpStorm, "2025.3.3")
            create(IntelliJPlatformType.PhpStorm, "2026.2")
        }
    }
}

