package com.yiaobang.kserial.utils

import java.io.ByteArrayInputStream
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppPathsTest {
    @Test
    fun createsConfigDirectoryMigratesSettingsAndCopiesSamples() {
        val directory = createTempDirectory("kserial-app-paths")
        try {
            val legacy = directory.resolve("legacy-settings.conf")
            val conf = directory.resolve("run").resolve("conf")
            Files.writeString(legacy, "appearance.language=en")

            AppPaths.initializeConfigDirectoryAt(
                confDirectory = conf,
                legacySettingsFile = legacy,
                resourceLoader = { name -> ByteArrayInputStream("sample=$name".toByteArray()) }
            ).getOrThrow()

            assertEquals("appearance.language=en", Files.readString(conf.resolve("settings.conf")))
            AppPaths.CONFIG_SAMPLE_NAMES.forEach { name ->
                assertEquals("sample=$name", Files.readString(conf.resolve(name)))
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun doesNotOverwriteExistingSettingsOrSamples() {
        val directory = createTempDirectory("kserial-app-paths-existing")
        try {
            val conf = directory.resolve("conf")
            Files.createDirectories(conf)
            Files.writeString(conf.resolve("settings.conf"), "keep=settings")
            Files.writeString(conf.resolve(AppPaths.CONFIG_SAMPLE_NAMES.first()), "keep=sample")

            AppPaths.initializeConfigDirectoryAt(
                confDirectory = conf,
                resourceLoader = { name -> ByteArrayInputStream("new=$name".toByteArray()) }
            ).getOrThrow()

            assertEquals("keep=settings", Files.readString(conf.resolve("settings.conf")))
            assertEquals("keep=sample", Files.readString(conf.resolve(AppPaths.CONFIG_SAMPLE_NAMES.first())))
            assertTrue(Files.exists(conf.resolve(AppPaths.CONFIG_SAMPLE_NAMES.last())))
            assertFalse(Files.exists(directory.resolve("settings.conf")))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun packagedSamplesAndApplicationIconAreAvailable() {
        val directory = createTempDirectory("kserial-bundled-resources")
        try {
            val conf = directory.resolve("conf")

            AppPaths.initializeConfigDirectoryAt(confDirectory = conf).getOrThrow()

            AppPaths.CONFIG_SAMPLE_NAMES.forEach { name ->
                assertTrue(Files.size(conf.resolve(name)) > 0L)
            }
            assertTrue(DesktopActions.appIconAvailable())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
