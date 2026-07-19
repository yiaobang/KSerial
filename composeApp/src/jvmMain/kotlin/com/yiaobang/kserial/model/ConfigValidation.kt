package com.yiaobang.kserial.model

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.yiaobang.kserial.i18n.Language
import com.yiaobang.kserial.utils.hexToByteArray
import java.io.File

enum class ConfigIssueSeverity { WARNING, ERROR }

data class ConfigIssue(
    val severity: ConfigIssueSeverity,
    val message: String
)

data class ConfigValidationResult(
    val config: Config?,
    val issues: List<ConfigIssue>
) {
    val isValid: Boolean get() = config != null && issues.none { it.severity == ConfigIssueSeverity.ERROR }
}

object ConfigValidator {
    fun validateSimulator(file: File, language: Language): ConfigValidationResult = runCatching {
        val parsed = ConfigFactory.parseFile(file).resolve()
        val config = if (parsed.hasPath("simulator")) parsed.getConfig("simulator") else parsed
        ConfigValidationResult(config, validateSimulatorConfig(config, language))
    }.getOrElse { error ->
        ConfigValidationResult(
            null,
            listOf(error(language, "配置解析失败", "Configuration parsing failed", "設定の解析に失敗しました", error))
        )
    }

    fun validateGateway(file: File, language: Language): ConfigValidationResult = runCatching {
        val parsed = ConfigFactory.parseFile(file).resolve()
        val config = if (parsed.hasPath("gateway")) parsed.getConfig("gateway") else parsed
        ConfigValidationResult(config, validateGatewayConfig(config, language))
    }.getOrElse { error ->
        ConfigValidationResult(
            null,
            listOf(error(language, "配置解析失败", "Configuration parsing failed", "設定の解析に失敗しました", error))
        )
    }

    private fun validateSimulatorConfig(config: Config, language: Language): List<ConfigIssue> {
        val issues = mutableListOf<ConfigIssue>()
        validateEndpoint(config.optionalConfig("endpoint"), language, "simulator.endpoint", required = false, issues)

        val encode = config.optionalString("encode")?.uppercase() ?: "UTF8"
        if (encode !in setOf("HEX", "UTF8", "UTF-8", "ASCII", "TEXT")) {
            issues += warning(
                language,
                "未知 encode=$encode，将按 UTF-8 文本处理",
                "Unknown encode=$encode; UTF-8 text mode will be used",
                "不明な encode=$encode は UTF-8 テキストとして扱われます"
            )
        }

        val packSizeRaw = config.optionalString("packSize")
        val packSize = packSizeRaw?.toIntOrNull()
        val delimiterRaw = config.optionalString("delimiter")
        if (packSizeRaw == null && delimiterRaw == null) {
            issues += error(language, "必须配置 packSize 或 delimiter", "Either packSize or delimiter is required", "packSize または delimiter が必要です")
        }
        if (packSizeRaw != null && (packSize == null || packSize !in 1..4096)) {
            issues += error(language, "packSize 必须为 1～4096", "packSize must be between 1 and 4096", "packSize は 1～4096 で指定してください")
        }
        if (packSize != null && delimiterRaw != null) {
            issues += warning(language, "同时配置时 packSize 优先", "packSize takes precedence when both framing options are set", "両方を指定した場合は packSize が優先されます")
        }

        val delimiter = when {
            delimiterRaw == null -> null
            encode == "HEX" -> delimiterRaw.hexToByteArray()
            else -> delimiterRaw.encodeToByteArray()
        }
        if (delimiterRaw != null && (delimiter == null || delimiter.isEmpty())) {
            issues += error(language, "delimiter 为空或格式无效", "delimiter is empty or invalid", "delimiter が空、または形式が無効です")
        }

        val rules = when {
            config.hasPath("rules") -> config.getConfigList("rules").map { rule ->
                rule.getString("request") to rule.getString("response")
            }
            config.hasPath("replies") -> config.getConfig("replies").entrySet().map { entry ->
                entry.key to entry.value.unwrapped().toString()
            }
            else -> emptyList()
        }
        if (rules.isEmpty()) {
            issues += error(language, "至少需要一条响应规则", "At least one response rule is required", "応答ルールを 1 件以上設定してください")
            return issues
        }
        rules.groupBy { it.first }.filterValues { it.size > 1 }.keys.forEach { request ->
            issues += warning(
                language,
                "请求规则重复，后一个值会覆盖前一个：$request",
                "Duplicate request; the later response overrides the earlier one: $request",
                "リクエストが重複しています。後の応答で上書きされます：$request"
            )
        }

        rules.forEachIndexed { index, (request, response) ->
            val requestBytes = if (encode == "HEX") request.hexToByteArray() else request.encodeToByteArray()
            val responseBytes = if (encode == "HEX") response.hexToByteArray() else response.encodeToByteArray()
            if (requestBytes == null) {
                issues += error(language, "规则 ${index + 1} 的 request 不是有效 HEX", "Rule ${index + 1} request is not valid HEX", "ルール ${index + 1} の request は有効な HEX ではありません")
            }
            if (responseBytes == null) {
                issues += error(language, "规则 ${index + 1} 的 response 不是有效 HEX", "Rule ${index + 1} response is not valid HEX", "ルール ${index + 1} の response は有効な HEX ではありません")
            }
            if (requestBytes != null && packSize != null && packSize in 1..4096 && requestBytes.size != packSize) {
                issues += error(
                    language,
                    "规则 ${index + 1} 请求长度为 ${requestBytes.size}，应为 $packSize 字节",
                    "Rule ${index + 1} request is ${requestBytes.size} bytes; expected $packSize",
                    "ルール ${index + 1} の request は ${requestBytes.size} バイトです。$packSize バイト必要です"
                )
            }
            if (requestBytes != null && packSize == null && delimiter != null && !requestBytes.endsWith(delimiter)) {
                issues += warning(
                    language,
                    "规则 ${index + 1} 的 request 未包含结尾 delimiter",
                    "Rule ${index + 1} request does not end with delimiter",
                    "ルール ${index + 1} の request が delimiter で終わっていません"
                )
            }
        }
        return issues
    }

    private fun validateGatewayConfig(config: Config, language: Language): List<ConfigIssue> {
        val issues = mutableListOf<ConfigIssue>()
        if (!config.hasPath("endpoints")) {
            return listOf(error(language, "缺少 gateway.endpoints", "gateway.endpoints is required", "gateway.endpoints が必要です"))
        }
        val endpoints = config.getConfigList("endpoints")
        if (endpoints.isEmpty()) {
            issues += error(language, "endpoints 不能为空", "endpoints cannot be empty", "endpoints を空にすることはできません")
        }
        endpoints.forEachIndexed { index, endpoint ->
            validateEndpoint(endpoint, language, "endpoints[$index]", required = true, issues)
        }

        val ids = endpoints.mapIndexed { index, endpoint -> endpoint.optionalString("id") ?: "endpoint$index" }
        ids.groupBy { it }.filterValues { it.size > 1 }.keys.forEach { id ->
            issues += error(language, "端点 id 重复：$id", "Duplicate endpoint id: $id", "端点 ID が重複しています：$id")
        }
        val ports = endpoints.mapNotNull { it.optionalString("port")?.takeIf(String::isNotBlank) }
        ports.groupBy { it }.filterValues { it.size > 1 }.keys.forEach { port ->
            issues += error(language, "串口重复：$port", "Duplicate serial port: $port", "シリアルポートが重複しています：$port")
        }

        if (config.hasPath("routes")) {
            val idSet = ids.toSet()
            config.getConfigList("routes").forEachIndexed { index, route ->
                val name = route.optionalString("name") ?: "route${index + 1}"
                val sources = route.optionalStringList("sources")
                val targets = route.optionalStringList("targets")
                if (sources.isEmpty()) issues += error(language, "$name 的 sources 不能为空", "$name sources cannot be empty", "$name の sources を空にすることはできません")
                if (targets.isEmpty()) issues += error(language, "$name 的 targets 不能为空", "$name targets cannot be empty", "$name の targets を空にすることはできません")
                (sources - idSet).forEach { id -> issues += error(language, "$name 引用了不存在的来源：$id", "$name references unknown source: $id", "$name が存在しない source を参照しています：$id") }
                (targets - idSet).forEach { id -> issues += error(language, "$name 引用了不存在的目标：$id", "$name references unknown target: $id", "$name が存在しない target を参照しています：$id") }
                sources.intersect(targets.toSet()).forEach { id ->
                    issues += warning(language, "$name 会把 $id 转发给自身，该目标会被忽略", "$name routes $id to itself; that target will be ignored", "$name は $id 自身へ転送します。この対象は無視されます")
                }
            }
        }
        return issues
    }

    private fun validateEndpoint(
        endpoint: Config?,
        language: Language,
        path: String,
        required: Boolean,
        issues: MutableList<ConfigIssue>
    ) {
        if (endpoint == null) {
            if (required) issues += error(language, "$path 缺失", "$path is missing", "$path がありません")
            else issues += warning(language, "$path 未声明，将沿用页面参数", "$path is not declared; current page settings will be used", "$path が未定義のため、現在のページ設定を使用します")
            return
        }
        if (endpoint.optionalString("port").isNullOrBlank()) issues += warning(language, "$path.port 为空", "$path.port is empty", "$path.port が空です")
        val baudRate = endpoint.optionalInt("baudRate")
        if (baudRate == null || baudRate <= 0) issues += error(language, "$path.baudRate 缺失或无效", "$path.baudRate is missing or invalid", "$path.baudRate がないか無効です")
        endpoint.optionalInt("dataBits")?.let { value ->
            if (value !in 5..8) issues += error(language, "$path.dataBits 必须为 5～8", "$path.dataBits must be between 5 and 8", "$path.dataBits は 5～8 で指定してください")
        }
        endpoint.optionalString("stopBits")?.lowercase()?.let { value ->
            if (value !in setOf("1", "1.0", "1.5", "2", "one_point_five", "two")) issues += error(language, "$path.stopBits 无效：$value", "$path.stopBits is invalid: $value", "$path.stopBits が無効です：$value")
        }
        endpoint.optionalString("parity")?.lowercase()?.let { value ->
            if (value !in setOf("none", "odd", "even", "mark", "space")) issues += error(language, "$path.parity 无效：$value", "$path.parity is invalid: $value", "$path.parity が無効です：$value")
        }
        endpoint.optionalString("flowControl")?.lowercase()?.let { value ->
            if (value !in setOf("none", "rts/cts", "rts_cts", "dsr/dtr", "dsr_dtr", "xon/xoff", "xon_xoff")) issues += error(language, "$path.flowControl 无效：$value", "$path.flowControl is invalid: $value", "$path.flowControl が無効です：$value")
        }
    }

    private fun warning(language: Language, zh: String, en: String, ja: String) =
        ConfigIssue(ConfigIssueSeverity.WARNING, localized(language, zh, en, ja))

    private fun error(language: Language, zh: String, en: String, ja: String) =
        ConfigIssue(ConfigIssueSeverity.ERROR, localized(language, zh, en, ja))

    private fun error(language: Language, zh: String, en: String, ja: String, cause: Throwable): ConfigIssue {
        val detail = cause.message?.takeIf { it.isNotBlank() } ?: cause::class.simpleName.orEmpty()
        return ConfigIssue(ConfigIssueSeverity.ERROR, "${localized(language, zh, en, ja)}: $detail")
    }

    private fun localized(language: Language, zh: String, en: String, ja: String): String = when (language) {
        Language.ZH -> zh
        Language.EN -> en
        Language.JA -> ja
    }

    private fun Config.optionalConfig(path: String): Config? = if (hasPath(path)) getConfig(path) else null
    private fun Config.optionalString(path: String): String? = if (hasPath(path)) getValue(path).unwrapped().toString() else null
    private fun Config.optionalInt(path: String): Int? = optionalString(path)?.toIntOrNull()
    private fun Config.optionalStringList(path: String): List<String> {
        if (!hasPath(path)) return emptyList()
        return runCatching { getStringList(path) }.getOrElse {
            optionalString(path)?.split(',', ';', '\n', '\r')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
        }
    }

    private fun ByteArray.endsWith(suffix: ByteArray): Boolean {
        if (suffix.size > size) return false
        return suffix.indices.all { index -> this[size - suffix.size + index] == suffix[index] }
    }
}
