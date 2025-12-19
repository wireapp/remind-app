import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.allopen") version "2.2.20"
    kotlin("plugin.noarg") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("io.quarkus")
    id("com.gradleup.shadow") version "9.3.0"
}

repositories {
    mavenCentral()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

/*
* Forcing protobuf versions to avoid conflicts with Quarkus dependencies.
* Make it same as in Wire SDK!
 */
configurations.all {
    resolutionStrategy {
        force("com.google.protobuf:protobuf-java:4.33.0")
        force("com.google.protobuf:protobuf-kotlin:4.33.0")
    }
}

dependencies {
    implementation(
        enforcedPlatform(
            "$quarkusPlatformGroupId:$quarkusPlatformArtifactId:$quarkusPlatformVersion"
        )
    )

    // Core Quarkus modules
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-flyway")
    implementation("io.quarkus:quarkus-quartz")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-logging-json")

    // Kotlin support
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-hibernate-orm-panache-kotlin")

    // Other project dependencies
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.rubiconproject.oss:jchronic:0.2.8")
    implementation("io.arrow-kt:arrow-core:2.1.2")
    implementation("com.wire:wire-apps-jvm-sdk:0.0.18")

    // Test dependencies
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
}

group = "com.wire"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

ktlint {
    verbose.set(true)
    outputToConsole.set(true)
    coloredOutput.set(true)
    reporters {
        reporter(ReporterType.CHECKSTYLE)
        reporter(ReporterType.JSON)
        reporter(ReporterType.HTML)
    }
}

detekt {
    toolVersion = "1.23.8"
    config.setFrom(file("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
    parallel = true
    buildUponDefaultConfig = true
    source.setFrom("src/main/kotlin")
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
allOpen {
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.persistence.Entity")
    annotation("io.quarkus.test.junit.QuarkusTest")
}

noArg {
    annotation("jakarta.persistence.Entity")
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        javaParameters.set(true)
    }
}

tasks {
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        mergeServiceFiles()
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    build {
        dependsOn(shadowJar)
    }
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
}
