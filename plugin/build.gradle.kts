plugins {
    id("java")
}

group = "dev.rbm72"
version = "0.1.0"

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
    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    // Both optional integrations — guarded at runtime via isPluginEnabled(), server runs fine without them.
    // Transitive libs WorldGuard bundles its own (older) copies of are excluded: paper-api pins newer
    // versions of the same jars (guava/gson/fastutil/etc are all "Mojang provides X" on the server),
    // and WorldGuard's strict version constraints on them otherwise deadlock dependency resolution.
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.12") {
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "com.google.code.gson", module = "gson")
        exclude(group = "it.unimi.dsi", module = "fastutil")
        exclude(group = "org.yaml", module = "snakeyaml")
        exclude(group = "io.netty")
        exclude(group = "org.apache.logging.log4j")
        exclude(group = "org.joml", module = "joml")
    }
    compileOnly("com.github.decentsoftware-eu:decentholograms:2.10.1")
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

tasks.named<Wrapper>("wrapper") {
    gradleVersion = "9.1.0"
    validateDistributionUrl = false
}
