import io.papermc.paperweight.userdev.ReobfArtifactConfiguration
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    `java-library`
    `maven-publish`
    checkstyle
    jacoco
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}


group = "nl.hauntedmc.ailex"
version = "1.1.0"
description = "AIlex"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.citizensnpcs.co/repo")
    maven("https://repo.alessiodp.com/releases/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    implementation("net.kyori:adventure-api:5.0.1")
    implementation("net.byteflux:libby-bukkit:1.3.1")
    implementation("net.citizensnpcs:citizensapi:2.0.42-SNAPSHOT")
    implementation("net.citizensnpcs:citizens-main:2.0.42-SNAPSHOT")
    implementation("io.github.classgraph:classgraph:4.8.184")
    compileOnly("com.github.retrooper:packetevents-spigot:2.12.0")

    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("com.github.retrooper:packetevents-spigot:2.12.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

checkstyle {
    toolVersion = "10.26.1"
    configFile = file("config/checkstyle/checkstyle.xml")
    isShowViolations = true
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

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.withType<JacocoReport>().configureEach {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestReport)
}

paperweight.reobfArtifactConfiguration = ReobfArtifactConfiguration.MOJANG_PRODUCTION
