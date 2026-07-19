package com.yiaobang.kserial.model

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppSettingsStoreTest {
    @Test
    fun savesDocumentedHoconAndLoadsItBack() {
        val directory = createTempDirectory("kserial-settings-test")
        try {
            val path = directory.resolve("settings.conf")
            val store = AppSettingsStore(path)
            val expected = PersistedSettings(
                appearance = PersistedAppearance(darkTheme = true, alwaysOnTop = true, language = "ja"),
                serial = PersistedSerialSettings(selectedPort = "COM7", baudRate = 115200),
                preferences = PersistedPreferences(hexDisplay = true, quickCommandsText = "AT\\r\\n\nPING"),
                sendHistory = listOf("01 03", "AT")
            )

            store.save(expected).getOrThrow()
            val text = Files.readString(path)
            val loaded = store.load()

            assertTrue(text.contains("KSerial 全局设置"))
            assertTrue(text.contains("KSerial global settings"))
            assertTrue(text.contains("KSerial グローバル設定"))
            assertTrue(text.contains("停止位索引"))
            assertTrue(text.contains("Stop-bits index"))
            assertTrue(text.contains("ストップビットの索引"))
            assertTrue(text.contains("sendHistory"))
            assertFalse(text.contains("loopInterval"))
            assertFalse(text.contains("inputText"))
            assertEquals(expected, loaded.settings)
            assertEquals(null, loaded.warning)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun hoconCommentsAreAccepted() {
        val directory = createTempDirectory("kserial-settings-comments")
        try {
            val path = directory.resolve("settings.conf")
            Files.writeString(path, """
                # User comment
                appearance.language = "en" // inline comment
                serial.baudRate = 230400
            """.trimIndent())

            val loaded = AppSettingsStore(path).load()

            assertEquals("en", loaded.settings.appearance.language)
            assertEquals(230400, loaded.settings.serial.baudRate)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun savePreservesCustomCommentsAndUnknownFields() {
        val directory = createTempDirectory("kserial-settings-preserve")
        try {
            val path = directory.resolve("settings.conf")
            val store = AppSettingsStore(path)
            store.save(PersistedSettings()).getOrThrow()
            Files.writeString(
                path,
                Files.readString(path) + "\n# Keep this extension setting\nextensionFlag = true\n"
            )

            store.save(PersistedSettings(serial = PersistedSerialSettings(baudRate = 115200))).getOrThrow()

            val rendered = Files.readString(path)
            assertTrue(rendered.contains("# Keep this extension setting"))
            assertTrue(rendered.contains("extensionFlag=true"))
            assertEquals(115200, store.load().settings.serial.baudRate)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun builtInTrilingualCommentsSurviveSubsequentSaves() {
        val directory = createTempDirectory("kserial-settings-built-in-comments")
        try {
            val path = directory.resolve("settings.conf")
            val store = AppSettingsStore(path)
            store.save(PersistedSettings()).getOrThrow()
            store.save(PersistedSettings(serial = PersistedSerialSettings(baudRate = 230400))).getOrThrow()

            val rendered = Files.readString(path)
            val commentLineCount = rendered.lineSequence().count { it.trimStart().startsWith("#") }
            assertTrue(commentLineCount >= 30)
            assertTrue(rendered.contains("支持 # 和 // 注释"))
            assertTrue(rendered.contains("Supports # and // comments"))
            assertTrue(rendered.contains("# と // のコメントに対応"))
            assertTrue(rendered.contains("Flow-control index"))
            assertTrue(rendered.contains("送信履歴"))
            assertEquals(230400, store.load().settings.serial.baudRate)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
