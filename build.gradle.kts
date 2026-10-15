plugins {
    java
}

group = "dev.ryvrook"
version = "1.0.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

// Which Hytale server artifact to compile against. Defaults live in gradle.properties
// and can be overridden with -Phytale_channel=release -Phytale_version=0.6.8
val hytaleChannel = (project.findProperty("hytale_channel") as String?)?.trim() ?: "release"
val hytaleVersion = (project.findProperty("hytale_version") as String?)?.trim() ?: "0.7.1"
require(hytaleChannel == "release" || hytaleChannel == "pre-release") {
    "hytale_channel must be \"release\" or \"pre-release\", got \"$hytaleChannel\""
}

repositories {
    mavenCentral()
    maven {
        name = "hytale-$hytaleChannel"
        url = uri("https://maven.hytale.com/$hytaleChannel")
    }
}

dependencies {
    // Provided by the Hytale server at runtime; never bundled into the mod jar.
    compileOnly("com.hypixel.hytale:Server:$hytaleVersion")

    // Tests run without a server. The server jar is only on the test classpath for the codecs, bson and the logger.
    testImplementation("com.hypixel.hytale:Server:$hytaleVersion")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
    }
    // The engine logger insists on owning java.util.logging before anything logs.
    systemProperty("java.util.logging.manager", "com.hypixel.hytale.logger.backend.HytaleLogManager")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.jar {
    archiveBaseName.set("Lullaby")
    archiveVersion.set(project.version.toString())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

/**
 * Copies the built jar into the local Hytale UserData/Mods folder so it loads in
 * singleplayer worlds. Override the target with -PmodsDir=/path/to/Mods.
 */
val installMod by tasks.registering(Copy::class) {
    group = "hytale"
    description = "Builds the mod and copies it into the local Hytale UserData/Mods folder."
    dependsOn(tasks.jar)

    val home = System.getProperty("user.home")
    val os = System.getProperty("os.name").lowercase()
    val defaultMods = when {
        os.contains("mac") -> "$home/Library/Application Support/Hytale/UserData/Mods"
        os.contains("win") -> (System.getenv("APPDATA") ?: "$home/AppData/Roaming") + "/Hytale/UserData/Mods"
        else -> "$home/.local/share/Hytale/UserData/Mods"
    }
    val target = (project.findProperty("modsDir") as String?) ?: defaultMods

    from(tasks.jar.flatMap { it.archiveFile })
    into(target)
    doLast { println("Installed ${tasks.jar.get().archiveFileName.get()} -> $target") }
}
