plugins {
    id("org.jetbrains.intellij.platform") version "2.2.0"
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.10"
}

group = "com.devek.dev"
version = "0.0.2"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

intellijPlatform {
    pluginConfiguration {
        name.set("Devek")
    }
}

dependencies {
    intellijPlatform {
        // This replaces the old intellij.version and intellij.type
        create("IC", "2024.3")
    }

    implementation("jakarta.websocket:jakarta.websocket-api:2.2.0")
    implementation("org.glassfish.tyrus:tyrus-client:2.2.0")
    implementation("org.glassfish.tyrus:tyrus-container-grizzly-client:2.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.0")
}

tasks {
    patchPluginXml {
        sinceBuild = "243"  // For 2024.3
        untilBuild = ""
    }

    compileKotlin {
        kotlinOptions.jvmTarget = "17"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "17"
    }
}

tasks.withType<Jar> {
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}