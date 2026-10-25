plugins {
    `java`
    `maven-publish`
    `idea`

    alias(libs.plugins.moddevgradle)
}

version = providers.gradleProperty("mod_version").get()
group = "com.itcmd.anotherweathermod"

val mcVersion = libs.versions.minecraft.asProvider().get()
val mcVersionRange = libs.versions.minecraft.range.get()
val neoLoaderRange = libs.versions.neoforge.loader.range.get()
val neoVersionRange = libs.versions.neoforge.range.get()

base {
    archivesName = "anotherweathermod-neoforge-${mcVersion}"
}

// In-world GameTests live in their own source set so they never ship in the mod jar.
val gametest: SourceSet = sourceSets.create("gametest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[gametest.compileClasspathConfigurationName].extendsFrom(configurations.compileClasspath.get())
configurations[gametest.runtimeClasspathConfigurationName].extendsFrom(configurations.runtimeClasspath.get())

neoForge {
    version = libs.versions.neoforge.asProvider().get()

    parchment {
        minecraftVersion = libs.versions.parchment.minecraft.get()
        mappingsVersion = libs.versions.parchment.asProvider().get()
    }

    mods.create("anotherweathermod") {
        sourceSet(project.sourceSets.getByName("main"))
        sourceSet(gametest)
    }

    // Puts Minecraft/NeoForge on the unit test classpath (plain JUnit, no game bootstrap).
    addModdingDependenciesTo(sourceSets.test.get())

    runs {
        configureEach {
            logLevel = org.slf4j.event.Level.DEBUG
        }

        create("client") {
            client()
            gameDirectory = file("runs/client")
        }

        create("server") {
            server()
            gameDirectory = file("runs/server")
            programArgument("--nogui")
        }

        // Headless server that runs every @GameTest in the "anotherweathermod" namespace, then exits
        // with a non-zero code if a required test failed. Use: ./gradlew runGameTestServer
        create("gameTestServer") {
            type = "gameTestServer"
            gameDirectory = file("runs/gametest")
            systemProperty("neoforge.enabledGameTestNamespaces", "anotherweathermod")
            logLevel = org.slf4j.event.Level.INFO
        }
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // Loading Minecraft classes initializes log4j, which writes logs/ into the working directory.
    val testRunDir = layout.buildDirectory.dir("test-run").get().asFile
    workingDir = testRunDir
    doFirst { testRunDir.mkdirs() }
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
    }
}

tasks.withType<ProcessResources>().configureEach {
    val expandProps = mapOf(
        "version" to version,
        "minecraft_version" to mcVersion,
        "neo_version_range" to neoVersionRange,
        "loader_version_range" to neoLoaderRange,
        "minecraft_version_range" to mcVersionRange
    )

    filesMatching(listOf("pack.mcmeta", "META-INF/neoforge.mods.toml", "*.mixins.json")) {
        expand(expandProps)
    }

    inputs.properties(expandProps)
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) {
        rename { "${it}_AnotherWeatherMod" }
    }

    manifest {
        attributes(mapOf(
            "Specification-Title"     to "Another Weather Mod",
            "Specification-Version"   to version,
            "Implementation-Title"    to "Another Weather Mod",
            "Implementation-Version"  to version,
            "Built-On-Minecraft"      to mcVersion
        ))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.add("-Xlint:all")
}

tasks.withType<Javadoc>().configureEach {
    include("**/api/**/*.java")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)

    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("anotherweathermod") {
            from(components["java"])
            artifactId = base.archivesName.get()
        }
    }
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}
