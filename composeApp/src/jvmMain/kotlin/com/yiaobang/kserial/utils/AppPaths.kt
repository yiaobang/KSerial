package com.yiaobang.kserial.utils

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** 应用目录约定及首次启动时的配置样例部署逻辑。 */
object AppPaths {
    /** 所有便携式配置都优先相对于启动工作目录解析。 */
    @JvmField
    val ROOT_FILE_PATH: File = File(System.getProperty("user.dir", "."))
        .toPath()
        .toAbsolutePath()
        .normalize()
        .toFile()

    @JvmField
    val CONF_FILE_PATH: File = File(ROOT_FILE_PATH, "conf")

    @JvmField
    val SETTINGS_FILE: File = File(CONF_FILE_PATH, "settings.conf")

    val CONFIG_SAMPLE_NAMES = listOf(
        "01-simulator-minimal.conf",
        "02-simulator-text.conf",
        "03-simulator-hex-fixed-length.conf",
        "04-bridge-one-way.conf",
        "05-bridge-bidirectional.conf",
        "06-bridge-multi-route.conf"
    )

    fun initializeConfigDirectory(): Result<Path> = initializeConfigDirectoryAt(
        confDirectory = CONF_FILE_PATH.toPath(),
        legacySettingsFile = legacySettingsPath(),
        resourceLoader = ::openBundledResource
    )

    internal fun initializeConfigDirectoryAt(
        confDirectory: Path,
        legacySettingsFile: Path? = null,
        resourceLoader: (String) -> InputStream? = ::openBundledResource
    ): Result<Path> = runCatching {
        val normalizedDirectory = confDirectory.toAbsolutePath().normalize()
        Files.createDirectories(normalizedDirectory)

        val settingsFile = normalizedDirectory.resolve("settings.conf")
        // 只在新目录尚未生成 settings.conf 时迁移旧版本设置，绝不覆盖用户的新配置。
        if (!Files.exists(settingsFile) && legacySettingsFile != null && Files.isRegularFile(legacySettingsFile)) {
            Files.copy(legacySettingsFile, settingsFile, StandardCopyOption.COPY_ATTRIBUTES)
        }

        // 样例同样只补缺失文件，用户修改过的内容会被完整保留。
        CONFIG_SAMPLE_NAMES.forEach { sampleName ->
            val target = normalizedDirectory.resolve(sampleName)
            if (!Files.exists(target)) {
                val source = resourceLoader(sampleName)
                    ?: error("Bundled configuration sample is missing: $sampleName")
                source.use { Files.copy(it, target) }
            }
        }
        normalizedDirectory
    }

    fun preferredConfigDirectory(): File = CONF_FILE_PATH.takeIf { it.isDirectory } ?: ROOT_FILE_PATH

    private fun openBundledResource(name: String): InputStream? =
        AppPaths::class.java.classLoader.getResourceAsStream(name)

    private fun legacySettingsPath(): Path {
        val os = System.getProperty("os.name").lowercase()
        val userHome = Path.of(System.getProperty("user.home"))
        return when {
            os.contains("win") -> {
                val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                (appData?.let(Path::of) ?: userHome.resolve("AppData/Roaming"))
                    .resolve("KSerial/settings.conf")
            }
            os.contains("mac") -> userHome.resolve("Library/Application Support/KSerial/settings.conf")
            else -> {
                val xdg = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
                (xdg?.let(Path::of) ?: userHome.resolve(".config"))
                    .resolve("kserial/settings.conf")
            }
        }
    }
}
