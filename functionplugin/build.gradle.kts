plugins {
    id("java")
}

group = "dev.rbm72"
version = "1.5.1"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "enginehub"
        url = uri("https://maven.enginehub.org/repo/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.92-stable")
    // Optional: only needed to compile the /build .schem paster. Guarded at runtime via
    // isPluginEnabled("WorldEdit") + an isolated SchemPaster class, so the server runs fine without it.
    // Non-transitive: the API classes we touch live in worldedit-core, plus BukkitAdapter in
    // worldedit-bukkit — pulling transitives would drag in relocated libs not in the offline cache.
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.4.4") { isTransitive = false }
    compileOnly("com.sk89q.worldedit:worldedit-core:7.4.4") { isTransitive = false }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.processResources {
    filteringCharset = "UTF-8"
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
