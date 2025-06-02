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

    // Ktor for file uploads
    implementation("io.ktor:ktor-server-host-common-jvm:2.3.11")
    implementation("io.ktor:ktor-server-sessions:2.3.11")

    // This module contains many core Ktor I/O utilities and extensions.
    implementation("io.ktor:ktor-utils-jvm:2.3.11")

    // Add this for ByteReadChannel and IO operations
    implementation("io.ktor:ktor-io-jvm:2.3.11")

    // Your existing kotlinx.serialization-json dependency
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // --- JUnit 5 Dependencies (Explicitly managed with BOM) ---
    // Remove testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.10.0")) // Use JUnit Platform BOM for consistent versions
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    // --- End JUnit 5 Dependencies ---
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}