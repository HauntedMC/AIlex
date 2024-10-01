plugins {
    `java-library`
    `maven-publish`
    id("io.papermc.paperweight.userdev") version "1.7.1"
}


group = "nl.hauntedmc.ailex"
version = "1.21-v1"
description = "AIlex"

java.sourceCompatibility = JavaVersion.VERSION_21


repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.citizensnpcs.co/repo")
    maven("https://repo.alessiodp.com/releases/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    paperweight.paperDevBundle("1.20.6-R0.1-SNAPSHOT")
    implementation("net.kyori:adventure-api:4.17.0")
    implementation("net.byteflux:libby-bukkit:1.1.5")
    implementation("net.citizensnpcs:citizensapi:2.0.34-SNAPSHOT")
    implementation("net.citizensnpcs:citizens-main:2.0.34-SNAPSHOT")
    implementation("io.github.classgraph:classgraph:4.8.171")
    compileOnly("com.github.retrooper:packetevents-spigot:2.4.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveFileName.set("AIlex.jar")
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION