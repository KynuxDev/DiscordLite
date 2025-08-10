plugins {
    kotlin("jvm") version "2.2.20-Beta2"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "kynux.cloud"
version = "0.1"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
        name = "spigotmc-repo"
    }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    
    // Discord JDA
    implementation("net.dv8tion:JDA:5.0.2")
    
    // Database dependencies
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    implementation("com.mysql:mysql-connector-j:8.2.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    
    // YAML configuration
    implementation("org.yaml:snakeyaml:2.2")
    
    // JSON support
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")
    
    // Security and encryption
    implementation("org.mindrot:jbcrypt:0.4")
    
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.20")
    }
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
