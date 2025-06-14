plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
}

group = "org.example"
version = "1.0-SNAPSHOT"

application {
    // Ensure this matches your main class if you're building a runnable JAR
    mainClass = "io.ktor.server.netty.EngineMain" // Assuming your main is similar to Ktor generator
}

repositories {
    mavenCentral()
}

dependencies {
    // Import Kotlin BOM to ensure consistent versions of all Kotlin-related artifacts
    implementation(platform(libs.kotlin.bom))

    // Ktor Core
    implementation("io.ktor:ktor-server-core-jvm:2.3.11")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.11")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.11")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.11")
    implementation("io.ktor:ktor-server-cors-jvm:2.3.11")

    // Ktor for file uploads
    implementation("io.ktor:ktor-server-host-common-jvm:2.3.11")
    implementation("io.ktor:ktor-server-sessions:2.3.11")

    // This module contains many core Ktor I/O utilities and extensions.
    implementation("io.ktor:ktor-utils-jvm:2.3.11")

    // Add this for ByteReadChannel and IO operations
    implementation("io.ktor:ktor-io-jvm:2.3.11")

    // Redis Implementation
    implementation("redis.clients:jedis:5.1.0")

    // existing kotlinx.serialization-json dependency
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Ktor Test Dependencies
    testImplementation("io.ktor:ktor-server-test-host:2.3.11") // For withTestApplication/testApplication
    testImplementation("io.ktor:ktor-client-content-negotiation:2.3.11") // If you need client-side JSON in tests
    testImplementation("io.ktor:ktor-client-core:2.3.11")
    testImplementation("io.ktor:ktor-client-cio:2.3.11") // Or another client engine for tests
    testImplementation("io.ktor:ktor-client-apache:2.3.11") // Example: Apache client for tests
    testImplementation("io.ktor:ktor-serialization-kotlinx-json:2.3.11") // For client-side JSON serialization

    // JUnit 5 Dependencies
    // Use kotlin-test-junit for Kotlin's JUnit 5 integration.
    // It pulls in junit-jupiter-api and platform-engine dependencies.
    testImplementation(kotlin("test-junit5")) // Use 'test-junit5' for explicit JUnit 5 integration

    // Explicitly add junit-jupiter-params if needed for @ParameterizedTest
    // (sometimes not pulled by test-junit5 by default)
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.0") // Ensure version matches your BOM if you use one for JUnit

    // The JUnit Platform Engine is needed at runtime to execute tests.
    // It's often pulled transitively by 'kotlin-test-junit5' but explicitly defining it as runtimeOnly is robust.
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0") // Ensure version matches your BOM if you use one for JUnit
    // End JUnit 5 Dependencies
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}