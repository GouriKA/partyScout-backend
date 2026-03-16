plugins {
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    kotlin("plugin.jpa") version "2.0.21"
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

group = "com.birthdayplanner"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // HTTP Client (WebClient)
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Configuration Properties
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Kotlin DSL
    runtimeOnly("io.netty:netty-resolver-dns-native-macos:4.1.111.Final:osx-x86_64")
// or
    runtimeOnly("io.netty:netty-resolver-dns-native-macos:4.1.111.Final:osx-aarch_64")

    // Kotlin support
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // Database
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("com.google.cloud.sql:postgres-socket-factory")

    // ShedLock
    implementation("net.javacrumbs.shedlock:shedlock-spring:5.10.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:5.10.0")

    // Google Cloud Pub/Sub
    implementation(platform("com.google.cloud:spring-cloud-gcp-dependencies:5.0.0"))
    implementation("com.google.cloud:spring-cloud-gcp-starter-pubsub")

    // Spring Security
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Firebase Admin SDK
    implementation("com.google.firebase:firebase-admin:9.2.0")

    // Rate limiting
    implementation("com.bucket4j:bucket4j-core:8.10.1")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Disable plain jar - only produce the Spring Boot executable jar
tasks.named<Jar>("jar") {
    enabled = false
}
