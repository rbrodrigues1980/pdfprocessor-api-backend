import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
}

group = "br.com.verticelabs"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // PDF & Tika
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    implementation("org.apache.tika:tika-core:2.9.2")
    implementation("org.apache.tika:tika-parsers-standard-package:2.9.2")

    // iText 8 - Extração avançada de PDF (tabelas e estruturas complexas)
    implementation("com.itextpdf:itext-core:8.0.1")

    // MongoDB Sync Driver (for Logback)
    implementation("org.mongodb:mongodb-driver-sync")

    // Logging JSON estruturado (Cloud Run / Cloud Logging)
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")

    // Excel (Apache POI)
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    // Security (JWT + Argon2)
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1") // For Argon2

    // MapStruct
    implementation("org.mapstruct:mapstruct:1.6.2")
    // kapt("org.mapstruct:mapstruct-processor:1.6.2")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
    testCompileOnly("org.projectlombok:lombok:1.18.34")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.34")

    // OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.6.0")

    // Spring AI - Google Gemini (Vertex AI)
    implementation("com.google.cloud:google-cloud-vertexai:1.2.0")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mongodb")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    
    // Configurações JVM para reduzir warnings
    jvmArgs = listOf(
        "-XX:+EnableDynamicAgentLoading", // Suporta carregamento dinâmico de agentes (Mockito/ByteBuddy)
        "-Djdk.instrument.traceUsage=false", // Desabilita trace de instrumentação
        "-Xshare:off" // Desabilita Class Data Sharing para evitar warnings
    )
    
    // Suprimir warnings de deprecação do Gradle (opcional)
    systemProperty("org.gradle.warning.mode", "none")
}
