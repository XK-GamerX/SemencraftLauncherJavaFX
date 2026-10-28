import org.gradle.api.GradleException
import org.gradle.jvm.tasks.Jar
import java.io.File
import java.util.Locale

plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.semencraft.semencraftlauncherjavafx"
version = "1.2.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml")
}

application {
    mainModule = "com.semencraft.semencraftlauncherjavafx"
    mainClass  = "com.semencraft.semencraftlauncherjavafx.MainApp"
}

val launcherName = "Semencraft Launcher"
val launcherVendor = "Semencraft - Khel Palacios"
val launcherAuthor = "Khel Palacios"
val launcherDescription = "Launcher oficial de Semencraft creado por Khel Palacios"
val launcherCopyright = "Copyright (c) 2026 Khel Palacios"
val launcherMenuGroup = "Semencraft by Khel Palacios"
val launcherModule = "com.semencraft.semencraftlauncherjavafx"
val launcherMainClass = "com.semencraft.semencraftlauncherjavafx.MainApp"
val runtimeExtraModules = listOf(
    "jdk.crypto.ec",
    "jdk.crypto.cryptoki"
)
val windowsUpgradeUuid = "96c730f0-e00e-489e-81d4-3e4df5fcb266"
val windowsPrimaryIcon = layout.projectDirectory.file("src/main/resources/com/semencraft/semencraftlauncherjavafx/assets/circular-blue.ico").asFile
val windowsFallbackIcon = layout.projectDirectory.file("src/main/resources/com/semencraft/semencraftlauncherjavafx/assets/icon.ico").asFile
val windowsLicense = layout.projectDirectory.file("installer/LICENSE.txt").asFile
val windowsWixResources = layout.projectDirectory.dir("installer/wix").asFile
val windowsMsiPatchScript = layout.projectDirectory.file("installer/patch-msi.ps1").asFile

fun normalizeInstallerVersion(raw: String): String {
    val numericParts = Regex("\\d+").findAll(raw).map { it.value }.toList()
    val major = numericParts.getOrNull(0) ?: "1"
    val minor = numericParts.getOrNull(1) ?: "0"
    val patch = numericParts.getOrNull(2) ?: "0"
    return "$major.$minor.$patch"
}

fun sanitizeWindowsPathSegment(raw: String): String {
    val sanitized = raw.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim()
    return if (sanitized.isBlank()) "Semencraft" else sanitized
}

fun resolveWindowsInstallerIcon(): File? {
    return when {
        windowsPrimaryIcon.exists() -> windowsPrimaryIcon
        windowsFallbackIcon.exists() -> windowsFallbackIcon
        else -> null
    }
}

val isWindowsHost = System.getProperty("os.name").lowercase(Locale.ROOT).contains("win")
val jpackageBinary = if (isWindowsHost) "jpackage.exe" else "jpackage"
val installerVersion = providers.gradleProperty("installerVersion")
    .map(::normalizeInstallerVersion)
    .orElse(normalizeInstallerVersion(project.version.toString()))

// Version visible para el launcher (chequeo de actualizaciones contra GitHub Releases).
val generatedVersionDir = layout.buildDirectory.dir("generated/launcher-version")
val generateLauncherVersion by tasks.registering {
    val versionValue = installerVersion
    val outputDir = generatedVersionDir
    inputs.property("version", versionValue)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get()
            .file("com/semencraft/semencraftlauncherjavafx/launcher-version.properties")
            .asFile
        file.parentFile.mkdirs()
        file.writeText("version=${versionValue.get()}\n")
    }
}
sourceSets.main {
    resources.srcDir(generateLauncherVersion)
}

val installerInputDir = layout.buildDirectory.dir("installer/input")
val installerOutputDir = layout.buildDirectory.dir("installer/output")
val appJar = tasks.named<Jar>("jar")

fun isCommandOnPath(command: String): Boolean {
    val pathEnv = System.getenv("PATH") ?: return false
    val entries = pathEnv.split(File.pathSeparator).filter { it.isNotBlank() }
    val hasExtension = command.contains(".")
    val pathext = (System.getenv("PATHEXT") ?: ".EXE;.BAT;.CMD")
        .split(';')
        .filter { it.isNotBlank() }
        .map { it.lowercase(Locale.ROOT) }
    val names = if (hasExtension) {
        listOf(command)
    } else {
        listOf(command) + pathext.map { command + it }
    }
    return entries.any { folder ->
        val dir = File(folder)
        names.any { name -> File(dir, name).exists() }
    }
}

fun assertWindowsInstallerPrerequisites(installerKind: String) {
    if (!isWindowsHost) {
        throw GradleException("MSI/EXE packaging only works on Windows.")
    }
    if (!isCommandOnPath("jpackage.exe")) {
        throw GradleException("jpackage.exe not found in PATH. Install JDK 21+ and ensure PATH includes its bin folder.")
    }
    val wixV3Present = isCommandOnPath("candle.exe") && isCommandOnPath("light.exe")
    if ((installerKind == "msi" || installerKind == "exe") && !wixV3Present) {
        throw GradleException(
            "WiX Toolset v3 is required for $installerKind packaging. " +
                    "Install WiX and ensure candle.exe and light.exe are available in PATH."
        )
    }
}

val prepareInstallerInput by tasks.registering(Copy::class) {
    dependsOn(appJar)
    doFirst {
        delete(installerInputDir)
    }
    from(appJar.flatMap { it.archiveFile })
    from(configurations.runtimeClasspath)
    into(installerInputDir)
}

tasks.register<Exec>("packageLauncherAppImage") {
    group = "distribution"
    description = "Builds a portable app-image with jpackage."
    dependsOn(prepareInstallerInput)
    doFirst {
        delete(installerOutputDir.map { it.dir(launcherName) })
        val args = mutableListOf(
            "--type", "app-image",
            "--name", launcherName,
            "--vendor", launcherVendor,
            "--description", launcherDescription,
            "--copyright", launcherCopyright,
            "--app-version", installerVersion.get(),
            "--dest", installerOutputDir.get().asFile.absolutePath,
            "--module-path", installerInputDir.get().asFile.absolutePath,
            "--module", "$launcherModule/$launcherMainClass",
            "--add-modules", runtimeExtraModules.joinToString(","),
            "--java-options", "-Dfile.encoding=UTF-8"
        )
        val installerIcon = resolveWindowsInstallerIcon()
        if (installerIcon != null) {
            args += listOf("--icon", installerIcon.absolutePath)
        }
        commandLine(jpackageBinary, *args.toTypedArray())
    }
}

tasks.register<Exec>("packageLauncherInstaller") {
    group = "distribution"
    description = "Builds a Windows MSI installer with jpackage."
    dependsOn(prepareInstallerInput)
    doFirst {
        assertWindowsInstallerPrerequisites("msi")
        delete(fileTree(installerOutputDir.get().asFile) { include("*.msi") })
        val safeVendor = sanitizeWindowsPathSegment(launcherVendor)
        val safeMenuGroup = sanitizeWindowsPathSegment(launcherMenuGroup)
        logger.lifecycle("Packaging MSI version {} for {}", installerVersion.get(), safeVendor)

        val args = mutableListOf(
            "--type", "msi",
            "--name", launcherName,
            "--vendor", safeVendor,
            "--description", launcherDescription,
            "--copyright", launcherCopyright,
            "--app-version", installerVersion.get(),
            "--dest", installerOutputDir.get().asFile.absolutePath,
            "--module-path", installerInputDir.get().asFile.absolutePath,
            "--module", "$launcherModule/$launcherMainClass",
            "--add-modules", runtimeExtraModules.joinToString(","),
            "--java-options", "-Dfile.encoding=UTF-8",
            "--win-menu",
            "--win-menu-group", safeMenuGroup,
            "--win-shortcut",
            "--win-shortcut-prompt",
            "--win-dir-chooser",
            "--win-per-user-install",
            "--win-upgrade-uuid", windowsUpgradeUuid
        )
        if (windowsLicense.exists()) {
            args += listOf("--license-file", windowsLicense.absolutePath)
        }
        // main.wxs propio: detecta/desinstala versiones previas, textos en espanol,
        // recuerda la carpeta y permite abrir el launcher al terminar.
        if (windowsWixResources.isDirectory) {
            args += listOf("--resource-dir", windowsWixResources.absolutePath)
            // jpackage solo copia los recursos que conoce: main.wxs encuentra las imagenes
            // del instalador (banner.bmp, dialog.bmp) con esta variable de entorno.
            environment("SEMENCRAFT_WIX_RES", windowsWixResources.absolutePath)
        }
        val installerIcon = resolveWindowsInstallerIcon()
        if (installerIcon != null) {
            args += listOf("--icon", installerIcon.absolutePath)
        }
        commandLine(jpackageBinary, *args.toTypedArray())
    }
    // Oculta los botones "Atras"/"Cancelar" desactivados de las pantallas finales (installer/patch-msi.ps1).
    doLast {
        val msi = installerOutputDir.get().asFile.listFiles { f -> f.extension == "msi" }?.maxByOrNull { it.lastModified() }
            ?: throw GradleException("No se encontro el MSI generado.")
        val patch = ProcessBuilder(
            "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
            "-File", windowsMsiPatchScript.absolutePath, "-MsiPath", msi.absolutePath
        ).inheritIO().start()
        if (patch.waitFor() != 0) {
            throw GradleException("patch-msi.ps1 fallo con codigo ${patch.exitValue()}")
        }
    }
}

tasks.register("printInstallerConfig") {
    group = "help"
    description = "Prints effective MSI packaging config."
    doLast {
        logger.lifecycle("installerVersion={}", installerVersion.get())
        logger.lifecycle("vendor={}", sanitizeWindowsPathSegment(launcherVendor))
        logger.lifecycle("menuGroup={}", sanitizeWindowsPathSegment(launcherMenuGroup))
        logger.lifecycle("runtimeExtraModules={}", runtimeExtraModules.joinToString(","))
    }
}

tasks.register<Zip>("packageLauncherZip") {
    group = "distribution"
    description = "Builds a zip distributable from the jpackage app-image."
    dependsOn("packageLauncherAppImage")
    archiveBaseName.set("semencraft-launcher")
    archiveVersion.set(installerVersion)
    destinationDirectory.set(installerOutputDir)
    from(installerOutputDir.map { it.dir(launcherName) })
}
