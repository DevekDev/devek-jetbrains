plugins {
    id("org.jetbrains.intellij.platform") version "2.2.0"
    // Match these Kotlin versions (JVM + serialization) to avoid mismatches
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
}

group = "com.devek.dev"
version = "0.0.3"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
        localPlatformArtifacts()
    }
    maven {
        url = uri("https://repo1.maven.org/maven2/")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

intellijPlatform {
    pluginConfiguration {
        name.set("Devek")
    }
    pluginVerification {
        ides {
            ide("2024.3")
        }
        verificationReportsDirectory.set(layout.buildDirectory.dir("reports/verification"))
    }
}

dependencies {
    // IntelliJ Platform dependencies
    intellijPlatform {
        // Use the specific IDE version you want to verify against
        intellijIdeaCommunity("2024.3")
        pluginVerifier()
    }

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.0")

    // If you do NOT need Kotlin/Native references, ensure your plugin doesn't
    // accidentally pull them in. Typically no extra steps are needed if you only
    // use plain JVM modules. Just verify you're not referencing org.jetbrains.kotlin.library.
    // If needed, you might do `compileOnly` for any Native libs or remove them.

    // Jakarta WebSocket
    implementation("jakarta.websocket:jakarta.websocket-api:2.2.0")

    // Latest Tyrus for WebSocket support
    implementation("org.glassfish.tyrus:tyrus-client:2.2.0")

    // OSGi core
    implementation("org.osgi:osgi.core:6.0.0")
}

kotlin {
    compilerOptions {
        // Target Java 21 bytecode
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)

        // Align API and language with Kotlin 2.1
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)

        // Example arg to skip prerelease version checks
        freeCompilerArgs.addAll(listOf("-Xskip-prerelease-check"))
    }
}

tasks {
    patchPluginXml {
        // Adjust these as needed for your plugin’s IntelliJ range
        sinceBuild.set("243.0")
        untilBuild.set("243.*")
    }

    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(configurations.runtimeClasspath.get().map { file ->
            if (file.isDirectory) file else zipTree(file)
        })
    }

    // Disable the searchable options task if not needed
    buildSearchableOptions {
        enabled = false
    }
}
