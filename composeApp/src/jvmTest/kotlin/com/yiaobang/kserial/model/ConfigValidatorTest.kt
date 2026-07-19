package com.yiaobang.kserial.model

import com.yiaobang.kserial.i18n.Language
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigValidatorTest {
    @Test
    fun acceptsValidHexSimulator() = withConfig("""
        simulator {
          endpoint { port = "COM2", baudRate = 9600 }
          encode = "HEX"
          packSize = 2
          rules = [{ request = "01 02", response = "03 04" }]
        }
    """) { file ->
        assertTrue(ConfigValidator.validateSimulator(file, Language.EN).isValid)
    }

    @Test
    fun rejectsFrameLengthMismatch() = withConfig("""
        simulator {
          endpoint { port = "COM2", baudRate = 9600 }
          encode = "HEX"
          packSize = 4
          rules = [{ request = "01 02", response = "03 04" }]
        }
    """) { file ->
        assertFalse(ConfigValidator.validateSimulator(file, Language.EN).isValid)
    }

    @Test
    fun rejectsDuplicateGatewayPortsAndUnknownTargets() = withConfig("""
        gateway {
          endpoints = [
            { id = "a", port = "COM1", baudRate = 9600 },
            { id = "b", port = "COM1", baudRate = 9600 }
          ]
          routes = [{ sources = ["a"], targets = ["missing"] }]
        }
    """) { file ->
        assertFalse(ConfigValidator.validateGateway(file, Language.EN).isValid)
    }

    private fun withConfig(content: String, block: (java.io.File) -> Unit) {
        val directory = createTempDirectory("kserial-config-test")
        try {
            val file = directory.resolve("test.conf")
            Files.writeString(file, content.trimIndent())
            block(file.toFile())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
