import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.Duration
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    kotlin("jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.anyplayer"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        val localIdePath = providers.gradleProperty("localIdePath").orNull
        if (localIdePath != null) local(localIdePath) else intellijIdea("2026.1")
        pluginVerifier()
        testFramework(TestFrameworkType.Platform)
    }
    implementation("uk.co.caprica:vlcj:4.12.1") {
        // IDEA already loads its JNA dispatcher; a plugin copy cannot load it again.
        exclude(group = "net.java.dev.jna")
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("stdlib"))
    testRuntimeOnly("com.google.code.gson:gson:2.13.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3
    }
}

intellijPlatform {
    // This plugin has no Settings configurable to index.
    buildSearchableOptions = false
    autoReload = false
    pluginConfiguration {
        name = "Any Player"
        ideaVersion {
            sinceBuild = "261"
            untilBuild = "261.*"
        }
    }
    nativeVariants {
        enabled = true
        windows.x86_64.from(layout.projectDirectory.dir("native/windows-x86_64"))
        windows.arm64.from(layout.projectDirectory.dir("native/windows-arm64"))
        linux.x86_64.from(layout.projectDirectory.dir("native/linux-x86_64"))
        linux.arm64.from(layout.projectDirectory.dir("native/linux-arm64"))
        mac.x86_64.from(layout.projectDirectory.dir("native/mac-x86_64"))
        mac.arm64.from(layout.projectDirectory.dir("native/mac-arm64"))
    }
    pluginVerification {
        ides {
            val product = providers.gradleProperty("verificationProduct").orNull
            val localPath = providers.gradleProperty("localIdePath").orNull
            if (product != null) {
                create(IntelliJPlatformType.entries.first { it.code == product }, providers.gradleProperty("verificationVersion").getOrElse("2026.1"))
            } else if (localPath != null) local(file(localPath)) else current()
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnit()
    // Match the installed IDE: its JNA dispatcher is loaded before plugin classes.
    systemProperty("jna.nosys", "true")
    systemProperty("jna.noclasspath", "true")
    doFirst {
        val jnaRoot = intellijPlatform.platformPath.resolve("lib/jna").toFile()
        val arch = System.getProperty("os.arch")
        val aliases = if (arch in setOf("aarch64", "arm64")) listOf(arch, "arm64", "aarch64") else listOf(arch, "amd64", "x86_64")
        val directory = aliases.map(jnaRoot::resolve).firstOrNull { it.isDirectory }
        check(directory != null) { "No $arch JNA runtime in $jnaRoot; available: ${jnaRoot.list()?.joinToString()}" }
        logger.lifecycle("Test JNA runtime: $directory ($arch)")
        systemProperty("jna.boot.library.path", directory.toString())
    }
}
tasks.test {
    timeout = Duration.ofMinutes(2)
    exclude("**/NativeMediaTest*")
    providers.gradleProperty("mediaFixture").orNull?.let { systemProperty("anyplayer.mediaFixture", it) }
}

tasks.register<Test>("nativeTest") {
    description = "Exercise the prepared native media runtime with generated fixtures."
    group = "verification"
    dependsOn("prepareTestSandbox")
    timeout = Duration.ofMinutes(2)
    testLogging { events("started", "passed", "failed", "skipped"); showStandardStreams = true }
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = files(provider { tasks.test.get().classpath })
    include("**/NativeMediaTest*")
    val os = when {
        System.getProperty("os.name").startsWith("Windows") -> "windows"
        System.getProperty("os.name").startsWith("Mac") -> "mac"
        else -> "linux"
    }
    val arch = if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "arm64" else "x86_64"
    val nativeRoot = providers.gradleProperty("nativeRoot").orNull?.let(::file)
        ?: layout.projectDirectory.dir("native/$os-$arch/native").asFile
    systemProperty("anyplayer.nativeRoot", nativeRoot.absolutePath)
    systemProperty("java.awt.headless", "false")
    jvmArgs("--enable-native-access=ALL-UNNAMED", "--add-modules=jdk.httpserver")
    doFirst { check(nativeRoot.isDirectory) { "Prepare the $os-$arch native runtime first." } }
}

tasks.matching { it.name.startsWith("buildPluginVariants_") }.configureEach {
    doFirst {
        val target = name.removePrefix("buildPluginVariants_").replace("_x86_64", "-x86_64").replace("_arm64", "-arm64")
        val directory = layout.projectDirectory.dir("native/$target/native").asFile
        val windows = target.startsWith("windows-")
        val extension = if (windows) ".exe" else ""
        val library = when {
            windows -> "vlc/libvlc.dll"
            target.startsWith("mac-") -> "vlc/lib/libvlc.dylib"
            else -> "vlc/lib/libvlc.so"
        }
        val required = listOf("bin/ffmpeg$extension", "bin/ffprobe$extension", "bin/mediainfo$extension", library, "dependencies.json")
        val missing = required.filterNot { directory.resolve(it).isFile }
        check(missing.isEmpty()) {
            "Native runtime for $target is incomplete: ${missing.joinToString()}. Prepare and verify it before building a platform archive."
        }
    }
}

tasks.withType<Jar>().configureEach { from("LICENSE") { into("META-INF") } }

tasks.withType<Zip>().configureEach {
    filesMatching("**/native/bin/*") { permissions { unix("rwxr-xr-x") } }
}
