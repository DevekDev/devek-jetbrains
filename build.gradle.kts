plugins {
    id("org.jetbrains.intellij.platform") version "2.2.1"
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
}

group = "com.devek.dev"
version = "0.0.3"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

repositories {
    maven {
        name = "foojay"
        url = uri("https://foojay.io/discoclient/v2/")
        credentials {
            username = "foo"
            password = "foo"
        }
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
}

dependencies {
    intellijPlatform {
        create("IC", "2024.3")
    }

    implementation("jakarta.websocket:jakarta.websocket-api:2.2.0")
    implementation("org.glassfish.tyrus:tyrus-client:2.2.0")
    implementation("org.glassfish.tyrus:tyrus-container-grizzly-client:2.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0-RC")
    implementation("org.osgi:org.osgi.core:6.0.0")
    implementation("org.osgi:org.osgi.framework:1.10.0")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
        freeCompilerArgs.addAll(listOf(
            "-Xskip-prerelease-check"
        ))
    }
}

tasks {
    patchPluginXml {
        sinceBuild.set("243.0")
        untilBuild.set("243.22562.222")
    }

    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    }
}