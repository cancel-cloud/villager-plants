import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.4.10"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "de.cancelcloud"
version = "1.2.0"

repositories {
    mavenCentral()
    maven("https://repo.purpurmc.org/snapshots")
}

dependencies {
    compileOnly("org.purpurmc.purpur:purpur-api:26.2.build.2620-stable")
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}

kotlin {
    jvmToolchain(25)
}

tasks.processResources {
    filesMatching("plugin.yml") {
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

// Copy the built jar into the test server's plugins folder
tasks.register<Copy>("copyJar") {
    dependsOn(tasks.named<ShadowJar>("shadowJar"))
    from(tasks.named<ShadowJar>("shadowJar").get().archiveFile.get().asFile)
    into(file("/Users/cancelcloud/Developer/Minecraft/purpur26-2/plugins/"))
    rename { "VillagerPlants-${project.version}.jar" }
}

tasks.named("build") {
    finalizedBy("copyJar")
}
