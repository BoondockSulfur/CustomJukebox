plugins {
    java
    id("com.gradleup.shadow") version "9.2.2" // oder jeweils aktuelle Version
}

group = "de.boondocksulfur"
version = "2.1.6"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/") // WorldGuard
    maven("https://jitpack.io") // GriefPrevention
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") // PlaceholderAPI
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.9")
    compileOnly("com.github.TechFortress:GriefPrevention:16.18.2")
    compileOnly("me.clip:placeholderapi:2.11.6")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.bstats:bstats-bukkit:3.1.0")
    // Note: Adventure API is already included in Paper API 1.21.1+
    // No need for separate dependency as Paper bundles it
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    withType<JavaCompile> {
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }

    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("CustomJukebox-${version}.jar")

        // Only include bStats in the shadow jar
        configurations = listOf(project.configurations.runtimeClasspath.get())
        dependencies {
            exclude { it.moduleGroup != "org.bstats" }
        }

        // Relocate bStats to avoid conflicts with other plugins
        relocate("org.bstats", "${project.group}")
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        inputs.property("version", project.version)
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
}
