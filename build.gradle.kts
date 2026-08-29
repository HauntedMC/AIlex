import io.papermc.paperweight.userdev.ReobfArtifactConfiguration
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    `java-library`
    `maven-publish`
    checkstyle
    jacoco
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}


group = "nl.hauntedmc.ailex"
version = "1.9.2"
description = "AIlex"

val citizensVersion = "2.0.43-b4239"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    // Citizens' Maven snapshot endpoint can reject GitHub-hosted runners with HTTP 403. Resolve the exact official
    // Jenkins distribution used for compilation instead; Citizens itself remains server-provided and is never shaded.
    ivy {
        name = "CitizensJenkins"
        url = uri("https://ci.citizensnpcs.co/job/Citizens2/4239/artifact/dist/target/")
        patternLayout {
            artifact("[artifact]-[revision].[ext]")
        }
        metadataSources {
            artifact()
        }
        content {
            includeModule("net.citizensnpcs", "Citizens")
        }
    }
    maven("https://repo.alessiodp.com/releases/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

// Only dependencies in this configuration are embedded in AIlex.jar. Paper/Citizens remain server-provided.
val bundled by configurations.creating

dependencies {
    paperweight.paperDevBundle("26.2.build.118-stable")
    implementation("net.kyori:adventure-api:5.2.0")
    compileOnly("net.citizensnpcs:Citizens:$citizensVersion") {
        isTransitive = false
    }
    compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")

    // Local durable memory uses SQLite/WAL. Shared network memory can use MySQL; bundle both JDBC drivers only.
    compileOnly("org.xerial:sqlite-jdbc:3.53.2.1")
    bundled("org.xerial:sqlite-jdbc:3.53.2.1") {
        isTransitive = false
    }
    compileOnly("com.mysql:mysql-connector-j:26.7.0")
    bundled("com.mysql:mysql-connector-j:26.7.0") {
        isTransitive = false
    }

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("net.citizensnpcs:Citizens:$citizensVersion") {
        isTransitive = false
    }
    testImplementation("com.github.retrooper:packetevents-spigot:2.13.0")
    testImplementation("org.xerial:sqlite-jdbc:3.53.2.1")
    testImplementation("com.mysql:mysql-connector-j:26.7.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

// Live/model benchmarks are a separate local-only source set. They are not attached to test, check, build or CI.
val benchmarkSourceSet = sourceSets.create("benchmark") {
    java.srcDir("src/benchmark/java")
    resources.srcDir("src/benchmark/resources")
    compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}
configurations[benchmarkSourceSet.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[benchmarkSourceSet.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

val benchmarkRequest = providers.gradleProperty("benchmarkRequest")
tasks.register<JavaExec>("benchmarkRun") {
    group = "verification"
    description = "Runs the local paid/model AIlex benchmark harness. Never used by CI."
    classpath = benchmarkSourceSet.runtimeClasspath
    mainClass.set("nl.hauntedmc.ailex.benchmark.BenchmarkMain")
    doFirst {
        val requestFile = benchmarkRequest.orNull
            ?: throw GradleException("Pass -PbenchmarkRequest=/absolute/path/to/request.json")
        args(requestFile)
    }
}

val benchmarkBridgeWorkspace = providers.gradleProperty("benchmarkBridgeWorkspace")
tasks.register<JavaExec>("benchmarkV2Bridge") {
    group = "verification"
    description = "Runs the local AIlex text-memory bridge used by LongMemEval-V2."
    classpath = benchmarkSourceSet.runtimeClasspath
    mainClass.set("nl.hauntedmc.ailex.benchmark.V2MemoryBridgeMain")
    standardInput = System.`in`
    doFirst {
        val workspace = benchmarkBridgeWorkspace.orNull
            ?: throw GradleException("Pass -PbenchmarkBridgeWorkspace=/absolute/path")
        args(project.projectDir.absolutePath, workspace)
    }
}

tasks.register("benchmarkCheck") {
    group = "verification"
    description = "Compiles the local benchmark harness without making model calls."
    dependsOn(tasks.named(benchmarkSourceSet.classesTaskName))
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
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveFileName.set("AIlex.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(bundled.map { dependency -> if (dependency.isDirectory) dependency else zipTree(dependency) })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
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

tasks.withType<JacocoCoverageVerification>().configureEach {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.55".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.40".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestReport)
    dependsOn(tasks.jacocoTestCoverageVerification)
}

paperweight.reobfArtifactConfiguration = ReobfArtifactConfiguration.MOJANG_PRODUCTION
