import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.4.20"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "de.cancelcloud"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.purpurmc.org/snapshots")
}

dependencies {
    compileOnly("org.purpurmc.purpur:purpur-api:26.3.build.2656-stable")
    // Provided at runtime by MCKotlin-Paper 1.5.1-k2.4.20. Do not shade these.
    compileOnly(kotlin("stdlib"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}

kotlin {
    jvmToolchain(25)
}

tasks.processResources {
    filesMatching("paper-plugin.yml") {
        expand("version" to project.version)
    }
}

tasks {
    shadowJar {
        archiveBaseName.set("VillagerPlants")
        archiveVersion.set(version.toString())
        archiveClassifier.set("")
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}

// Local test server only. Skipped when that folder is not on this machine.
val testPluginsDir = file("/Users/cancelcloud/Developer/Minecraft/purpur26-3/plugins/")
tasks.register<Copy>("copyJar") {
    onlyIf { testPluginsDir.isDirectory }
    dependsOn(tasks.named<ShadowJar>("shadowJar"))
    from(tasks.named<ShadowJar>("shadowJar").get().archiveFile.get().asFile)
    into(testPluginsDir)
    rename { "VillagerPlants-${project.version}.jar" }
}

tasks.named("build") {
    finalizedBy("copyJar")
}
