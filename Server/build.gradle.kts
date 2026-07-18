plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    id("com.diffplug.spotless") version "6.25.0"
}

group = "com.autoapi"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.postgresql:r2dbc-postgresql")
    implementation("org.postgresql:postgresql")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    implementation("io.opentelemetry:opentelemetry-api:1.42.1")
    implementation("io.opentelemetry:opentelemetry-context:1.42.1")
    implementation("io.opentelemetry:opentelemetry-sdk:1.42.1")
    implementation("io.opentelemetry:opentelemetry-sdk-common:1.42.1")
    implementation("io.opentelemetry:opentelemetry-sdk-trace:1.42.1")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.42.1")
    implementation("io.opentelemetry:opentelemetry-extension-trace-propagators:1.42.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:r2dbc")
    testImplementation("com.redis:testcontainers-redis:2.2.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
    maxParallelForks = 1
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("1.22.0")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
