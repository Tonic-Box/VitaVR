import java.nio.file.Paths

plugins {
    id("java")
}

group = "com.tonic.vitavr"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.runelite.net")
        content {
            includeGroupByRegex("net\\.runelite.*")
        }
    }
    mavenCentral()
}

val runeliteVersion = "1.13.6"
val vitaVersion = "1.13.6_1"
val lwjglVersion = "3.3.2"
val sideloadedPluginsDir: File = Paths.get(System.getProperty("user.home"), ".runelite", "sideloaded-plugins").toFile()

// Bundled into the plugin jar: RuneLite ships LWJGL core and OpenGL, but not OpenXR
val shade: Configuration by configurations.creating {
    isTransitive = false
}

dependencies {
    compileOnly("net.runelite:client:$runeliteVersion")
    compileOnly("com.tonic:base-api:$vitaVersion")
    compileOnly("com.tonic:api:$vitaVersion")
    compileOnly("org.projectlombok:lombok:1.18.24")
    annotationProcessor("org.projectlombok:lombok:1.18.24")

    compileOnly("org.lwjgl:lwjgl:$lwjglVersion")
    compileOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-openxr:$lwjglVersion")
    shade("org.lwjgl:lwjgl-openxr:$lwjglVersion")
    shade("org.lwjgl:lwjgl-openxr:$lwjglVersion:natives-windows")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Jar>("buildPlugin") {
    group = "build"
    description = "Builds the plugin JAR with OpenXR bundled and moves it to sideloaded-plugins for easy refreshing"
    dependsOn(tasks.named("classes"))

    from(sourceSets.main.get().output)
    // LWJGL keeps its natives under META-INF/windows, so only the manifest, signatures and module descriptors are left out
    from({ shade.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "META-INF/versions/**", "**/module-info.class")
    }

    archiveBaseName.set(project.name)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    destinationDirectory.set(file(sideloadedPluginsDir))
    doFirst {
        sideloadedPluginsDir.mkdirs()
    }
}
