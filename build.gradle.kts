plugins {
    id("org.jetbrains.intellij") version "1.17.4"
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.10"
}

group = "com.devek.dev"
version = "0.0.1"

repositories {
    mavenCentral()
}

intellij {
    version.set("2023.2")
    type.set("IC")
    plugins.set(listOf())
    downloadSources.set(true)
}

tasks {

    patchPluginXml {
        sinceBuild = "232"
        untilBuild = "999.*"
    }

    compileKotlin {
        kotlinOptions.jvmTarget = "17"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "17"
    }
}

dependencies {
    implementation("jakarta.websocket:jakarta.websocket-api:2.2.0")
    implementation("org.glassfish.tyrus:tyrus-client:2.2.0")
    implementation("org.glassfish.tyrus:tyrus-container-grizzly-client:2.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}

tasks.withType<Jar> {
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}