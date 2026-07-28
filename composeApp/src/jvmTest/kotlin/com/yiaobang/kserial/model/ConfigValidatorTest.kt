package com.yiaobang.kserial.model

import com.yiaobang.kserial.i18n.Language
import java.io.File
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
    fun rejectsSimulatorWithoutEndpoint() = withConfig("""
        simulator {
          encode = "ASCII"
          delimiter = "\n"
          rules = [{ request = "PING\n", response = "PONG\n" }]
        }
    """) { file ->
        assertFalse(ConfigValidator.validateSimulator(file, Language.EN).isValid)
    }

    @Test
    fun rejectsSimulatorWithoutPort() = withConfig("""
        simulator {
          endpoint { baudRate = 9600 }
          encode = "ASCII"
          delimiter = "\n"
          rules = [{ request = "PING\n", response = "PONG\n" }]
        }
    """) { file ->
        assertFalse(ConfigValidator.validateSimulator(file, Language.EN).isValid)
    }

    @Test
    fun rejectsAmbiguousSimulatorFraming() = withConfig("""
        simulator {
          endpoint { port = "COM2", baudRate = 9600 }
          encode = "ASCII"
          delimiter = "\n"
          packSize = 5
          rules = [{ request = "PING\n", response = "PONG\n" }]
        }
    """) { file ->
        assertFalse(ConfigValidator.validateSimulator(file, Language.EN).isValid)
    }

    @Test
    fun rejectsUnsupportedAutoOpenField() = withConfig("""
        simulator {
          endpoint { port = "COM2", baudRate = 9600, autoOpen = true }
          encode = "ASCII"
          delimiter = "\n"
          rules = [{ request = "PING\n", response = "PONG\n" }]
        }
    """) { file ->
        assertFalse(ConfigValidator.validateSimulator(file, Language.EN).isValid)
    }

    @Test
    fun acceptsCompleteGateway() = withConfig("""
        gateway {
          endpoints = [
            { id = "a", port = "COM1", baudRate = 9600 },
            { id = "b", port = "COM2", baudRate = 9600 }
          ]
          routes = [{ sources = ["a"], targets = ["b"] }]
        }
    """) { file ->
        assertTrue(ConfigValidator.validateGateway(file, Language.EN).isValid)
    }

    @Test
    fun rejectsGatewayWithoutRoutes() = withConfig("""
        gateway {
          endpoints = [
            { id = "a", port = "COM1", baudRate = 9600 },
            { id = "b", port = "COM2", baudRate = 9600 }
          ]
        }
    """) { file ->
        assertFalse(ConfigValidator.validateGateway(file, Language.EN).isValid)
    }

    @Test
    fun rejectsGatewayWithoutEnabledRoute() = withConfig("""
        gateway {
          endpoints = [
            { id = "a", port = "COM1", baudRate = 9600 },
            { id = "b", port = "COM2", baudRate = 9600 }
          ]
          routes = [{ sources = ["a"], targets = ["b"], enabled = false }]
        }
    """) { file ->
        assertFalse(ConfigValidator.validateGateway(file, Language.EN).isValid)
    }

    @Test
    fun rejectsGatewayWithOneEndpoint() = withConfig("""
        gateway {
          endpoints = [{ id = "a", port = "COM1", baudRate = 9600 }]
          routes = [{ sources = ["a"], targets = ["a"] }]
        }
    """) { file ->
        assertFalse(ConfigValidator.validateGateway(file, Language.EN).isValid)
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

    @Test
    fun rejectsJsonConfigurationFiles() = withConfig(
        content = """
            simulator {
              endpoint { port = "COM2", baudRate = 9600 }
              delimiter = "\n"
              rules = [{ request = "PING\n", response = "PONG\n" }]
            }
        """,
        fileName = "test.json"
    ) { file ->
        assertFalse(ConfigValidator.validateSimulator(file, Language.EN).isValid)
    }

    @Test
    fun bundledExamplesFollowStrictSchema() {
        val samples = sequenceOf(File("config_samples"), File("../config_samples"))
            .firstOrNull(File::isDirectory)
            ?: error("config_samples directory not found")

        (1..3).forEach { level ->
            val file = samples.listFiles().orEmpty().single { it.name.startsWith("0$level-simulator-") }
            val result = ConfigValidator.validateSimulator(file, Language.EN)
            assertTrue(result.isValid, "$file: ${result.issues.joinToString { it.message }}")
        }
        (4..6).forEach { level ->
            val file = samples.listFiles().orEmpty().single { it.name.startsWith("0$level-bridge-") }
            val result = ConfigValidator.validateGateway(file, Language.EN)
            assertTrue(result.isValid, "$file: ${result.issues.joinToString { it.message }}")
        }
    }

    private fun withConfig(
        content: String,
        fileName: String = "test.conf",
        block: (File) -> Unit
    ) {
        val directory = createTempDirectory("kserial-config-test")
        try {
            val file = directory.resolve(fileName)
            Files.writeString(file, content.trimIndent())
            block(file.toFile())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
