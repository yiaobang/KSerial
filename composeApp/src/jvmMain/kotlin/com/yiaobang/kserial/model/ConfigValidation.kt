package com.yiaobang.kserial.model

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.yiaobang.kserial.i18n.Language
import com.yiaobang.kserial.serial.EndpointAddress
import com.yiaobang.kserial.utils.hexToByteArray
import java.io.File

/** 配置问题分级：警告允许继续加载，错误会阻止配置生效。 */
enum class ConfigIssueSeverity { WARNING, ERROR }

data class ConfigIssue(
    val severity: ConfigIssueSeverity,
    val message: String
)

/** 解析后的 HOCON 节点及所有可一次性展示给用户的问题。 */
data class ConfigValidationResult(
    val config: Config?,
    val issues: List<ConfigIssue>
) {
    val isValid: Boolean get() = config != null && issues.none { it.severity == ConfigIssueSeverity.ERROR }
}

/**
 * 模拟器和桥接配置的结构/语义校验器。
 * 校验阶段只读取文件，不打开串口；这样 UI 可以先完整展示问题，再决定是否应用配置。
 */
object ConfigValidator {
    fun validateSimulator(file: File, language: Language): ConfigValidationResult {
        unsupportedFileResult(file, language)?.let { return it }
        return runCatching {
            val parsed = ConfigFactory.parseFile(file).resolve()
            val config = if (parsed.hasPath("simulator")) parsed.getConfig("simulator") else parsed
            ConfigValidationResult(config, validateSimulatorConfig(config, language))
        }.getOrElse { error ->
            ConfigValidationResult(
                null,
                listOf(error(language, "配置解析失败", "Configuration parsing failed", "設定の解析に失敗しました", error))
            )
        }
    }

    fun validateGateway(file: File, language: Language): ConfigValidationResult {
        unsupportedFileResult(file, language)?.let { return it }
        return runCatching {
            val parsed = ConfigFactory.parseFile(file).resolve()
            val config = if (parsed.hasPath("gateway")) parsed.getConfig("gateway") else parsed
            ConfigValidationResult(config, validateGatewayConfig(config, language))
        }.getOrElse { error ->
            ConfigValidationResult(
                null,
                listOf(error(language, "配置解析失败", "Configuration parsing failed", "設定の解析に失敗しました", error))
            )
        }
    }

    private fun validateSimulatorConfig(config: Config, language: Language): List<ConfigIssue> {
        val issues = mutableListOf<ConfigIssue>()
        validateEndpoint(config.optionalConfig("endpoint"), language, "simulator.endpoint", issues)
        config.optionalConfig("highlight")?.let {
            validateHighlight(it, language, "simulator.highlight", issues)
        }

        val encode = config.optionalString("encode")?.uppercase() ?: "UTF8"
        if (encode !in setOf("HEX", "UTF8", "UTF-8", "ASCII", "TEXT")) {
            issues += warning(
                language,
                "未知 encode=$encode，将按 UTF-8 文本处理",
                "Unknown encode=$encode; UTF-8 text mode will be used",
                "不明な encode=$encode は UTF-8 テキストとして扱われます"
            )
        }

        // 控制中心不再提供分帧模式选择，因此配置必须明确且只能选择一种分帧方式。
        val packSizeRaw = config.optionalString("packSize")
        val packSize = packSizeRaw?.toIntOrNull()
        val delimiterRaw = config.optionalString("delimiter")
        if (packSizeRaw == null && delimiterRaw == null) {
            issues += error(language, "必须配置 packSize 或 delimiter", "Either packSize or delimiter is required", "packSize または delimiter が必要です")
        }
        if (packSizeRaw != null && (packSize == null || packSize !in 1..4096)) {
            issues += error(language, "packSize 必须为 1～4096", "packSize must be between 1 and 4096", "packSize は 1～4096 で指定してください")
        }
        if (packSizeRaw != null && delimiterRaw != null) {
            issues += error(language, "packSize 和 delimiter 只能配置一个", "Configure exactly one of packSize and delimiter", "packSize と delimiter はどちらか一方だけを指定してください")
        }

        val delimiter = when {
            delimiterRaw == null -> null
            encode == "HEX" -> delimiterRaw.hexToByteArray()
            else -> delimiterRaw.encodeToByteArray()
        }
        if (delimiterRaw != null && (delimiter == null || delimiter.isEmpty())) {
            issues += error(language, "delimiter 为空或格式无效", "delimiter is empty or invalid", "delimiter が空、または形式が無効です")
        }

        val rules = if (config.hasPath("rules")) {
            config.getConfigList("rules").map { rule ->
                rule.getString("request") to rule.getString("response")
            }
        } else emptyList()
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

        // 应答延迟：全局 responseDelay 与规则级 delay，支持固定毫秒数或 "min~max" 随机区间，0～600000ms。
        config.optionalString("responseDelay")?.let { raw ->
            if (ReplyDelay.parse(raw) == null) {
                issues += warning(
                    language,
                    "responseDelay 无效：$raw（支持固定毫秒数或 min~max 区间，0～600000，已按 0 处理）",
                    "Invalid responseDelay: $raw (expected a fixed value or min~max range, 0-600000; 0 will be used)",
                    "responseDelay が無効です：$raw（固定値または min~max の範囲、0～600000 で指定してください、0 として処理します）"
                )
            }
        }
        if (config.hasPath("rules")) {
            config.getConfigList("rules").forEachIndexed { index, rule ->
                val raw = rule.optionalString("delay") ?: return@forEachIndexed
                if (ReplyDelay.parse(raw) == null) {
                    issues += warning(
                        language,
                        "规则 ${index + 1} 的 delay 无效：$raw（支持固定毫秒数或 min~max 区间，0～600000，已按全局值处理）",
                        "Rule ${index + 1} delay is invalid: $raw (expected a fixed value or min~max range, 0-600000; the global value will be used)",
                        "ルール ${index + 1} の delay が無効です：$raw（固定値または min~max の範囲、0～600000 で指定してください、グローバル値を適用します）"
                    )
                }
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
        if (endpoints.size < 2) {
            issues += error(language, "串口桥接至少需要两个端点", "A serial bridge requires at least two endpoints", "シリアルブリッジには 2 つ以上の端点が必要です")
        }
        endpoints.forEachIndexed { index, endpoint ->
            validateEndpoint(endpoint, language, "endpoints[$index]", issues)
            if (endpoint.optionalString("id").isNullOrBlank()) {
                issues += error(language, "endpoints[$index].id 缺失", "endpoints[$index].id is required", "endpoints[$index].id が必要です")
            }
        }

        // 路由通过逻辑 id 关联端点，因此 id 与物理串口都必须在同一配置内唯一。
        val ids = endpoints.mapIndexed { index, endpoint -> endpoint.optionalString("id") ?: "endpoint$index" }
        ids.groupBy { it }.filterValues { it.size > 1 }.keys.forEach { id ->
            issues += error(language, "端点 id 重复：$id", "Duplicate endpoint id: $id", "端点 ID が重複しています：$id")
        }
        val ports = endpoints.mapNotNull { it.optionalString("port")?.takeIf(String::isNotBlank) }
        ports.groupBy { it }.filterValues { it.size > 1 }.keys.forEach { port ->
            issues += error(language, "串口重复：$port", "Duplicate serial port: $port", "シリアルポートが重複しています：$port")
        }
        config.optionalConfig("highlight")?.let {
            validateHighlight(it, language, "gateway.highlight", issues)
        }
        config.optionalString("logFormat")?.let { raw ->
            val value = raw.trim().lowercase()
            if (value !in setOf("auto", "text", "hex")) {
                issues += warning(
                    language,
                    "gateway.logFormat 无效：$raw（可用 auto / text / hex，已按 auto 处理）",
                    "Invalid gateway.logFormat: $raw (expected auto / text / hex, falling back to auto)",
                    "gateway.logFormat が無効です：$raw（auto / text / hex を指定できます、auto として処理します）"
                )
            }
        }
        config.optionalString("backpressure")?.let { raw ->
            val value = raw.trim().lowercase()
            if (value !in setOf("drop", "block")) {
                issues += warning(
                    language,
                    "gateway.backpressure 无效：$raw（可用 drop / block，已按 drop 处理）",
                    "Invalid gateway.backpressure: $raw (expected drop / block, falling back to drop)",
                    "gateway.backpressure が無効です：$raw（drop / block を指定できます、drop として処理します）"
                )
            }
        }

        if (!config.hasPath("routes")) {
            issues += error(language, "缺少 gateway.routes", "gateway.routes is required", "gateway.routes が必要です")
        } else {
            // 路由引用在加载前解析，避免运行中才发现目标不存在或形成无意义的自转发。
            val idSet = ids.toSet()
            val routeConfigs = config.getConfigList("routes")
            if (routeConfigs.isEmpty()) {
                issues += error(language, "routes 不能为空", "routes cannot be empty", "routes を空にすることはできません")
            }
            var hasEnabledRoute = false
            routeConfigs.forEachIndexed { index, route ->
                val name = route.optionalString("name") ?: "route${index + 1}"
                val sources = route.optionalStringList("sources")
                val targets = route.optionalStringList("targets")
                val enabled = route.optionalBoolean("enabled")
                if (route.hasPath("enabled") && enabled == null) {
                    issues += error(language, "$name 的 enabled 必须是布尔值", "$name enabled must be a boolean", "$name の enabled は真偽値で指定してください")
                }
                if (enabled != false) hasEnabledRoute = true
                if (sources.isEmpty()) issues += error(language, "$name 的 sources 不能为空", "$name sources cannot be empty", "$name の sources を空にすることはできません")
                if (targets.isEmpty()) issues += error(language, "$name 的 targets 不能为空", "$name targets cannot be empty", "$name の targets を空にすることはできません")
                (sources - idSet).forEach { id -> issues += error(language, "$name 引用了不存在的来源：$id", "$name references unknown source: $id", "$name が存在しない source を参照しています：$id") }
                (targets - idSet).forEach { id -> issues += error(language, "$name 引用了不存在的目标：$id", "$name references unknown target: $id", "$name が存在しない target を参照しています：$id") }
                sources.intersect(targets.toSet()).forEach { id ->
                    issues += warning(language, "$name 会把 $id 转发给自身，该目标会被忽略", "$name routes $id to itself; that target will be ignored", "$name は $id 自身へ転送します。この対象は無視されます")
                }
            }
            if (routeConfigs.isNotEmpty() && !hasEnabledRoute) {
                issues += error(language, "至少需要一条启用的路由", "At least one route must be enabled", "有効なルートを 1 件以上指定してください")
            }
        }
        return issues
    }

    private fun validateEndpoint(
        endpoint: Config?,
        language: Language,
        path: String,
        issues: MutableList<ConfigIssue>
    ) {
        if (endpoint == null) {
            issues += error(language, "$path 缺失", "$path is missing", "$path がありません")
            return
        }
        if (endpoint.optionalString("port").isNullOrBlank()) {
            issues += error(language, "$path.port 缺失", "$path.port is required", "$path.port が必要です")
        }
        if (endpoint.hasPath("autoOpen")) {
            issues += error(
                language,
                "$path.autoOpen 已不受支持，请删除并在加载后点击运行",
                "$path.autoOpen is not supported; remove it and click Run after loading",
                "$path.autoOpen はサポートされていません。削除し、読み込み後に実行をクリックしてください"
            )
        }
        // 网络端点（tcp:// / udp://）没有波特率概念，只校验串口端点。
        val isNetworkEndpoint = EndpointAddress.looksLikeNetwork(endpoint.optionalString("port").orEmpty())
        if (!isNetworkEndpoint) {
            val baudRate = endpoint.optionalInt("baudRate")
            if (baudRate == null || baudRate <= 0) {
                issues += error(
                    language,
                    "$path.baudRate 缺失或无效",
                    "$path.baudRate is missing or invalid",
                    "$path.baudRate がないか無効です"
                )
            }
        }
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

    private fun validateHighlight(
        highlight: Config,
        language: Language,
        path: String,
        issues: MutableList<ConfigIssue>
    ) {
        listOf("enabled", "ignoreCase").forEach { field ->
            if (highlight.hasPath(field) && highlight.optionalBoolean(field) == null) {
                issues += error(
                    language,
                    "$path.$field 必须是布尔值",
                    "$path.$field must be a boolean",
                    "$path.$field は真偽値で指定してください"
                )
            }
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

    private fun unsupportedFileResult(file: File, language: Language): ConfigValidationResult? {
        if (file.extension.equals("conf", ignoreCase = true) || file.extension.equals("hocon", ignoreCase = true)) {
            return null
        }
        return ConfigValidationResult(
            null,
            listOf(
                error(
                    language,
                    "仅支持 .conf 或 .hocon 配置文件",
                    "Only .conf and .hocon configuration files are supported",
                    ".conf または .hocon 設定ファイルのみ使用できます"
                )
            )
        )
    }

    private fun Config.optionalConfig(path: String): Config? = if (hasPath(path)) getConfig(path) else null
    private fun Config.optionalString(path: String): String? = if (hasPath(path)) getValue(path).unwrapped().toString() else null
    private fun Config.optionalInt(path: String): Int? = optionalString(path)?.toIntOrNull()
    private fun Config.optionalBoolean(path: String): Boolean? {
        if (!hasPath(path)) return null
        return runCatching { getBoolean(path) }.getOrNull()
    }
    private fun Config.optionalStringList(path: String): List<String> {
        if (!hasPath(path)) return emptyList()
        // HOCON 标准数组和逗号/分号分隔的简写都接受，便于手工编辑配置。
        return runCatching { getStringList(path) }.getOrElse {
            optionalString(path)?.split(',', ';', '\n', '\r')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
        }
    }

    private fun ByteArray.endsWith(suffix: ByteArray): Boolean {
        if (suffix.size > size) return false
        return suffix.indices.all { index -> this[size - suffix.size + index] == suffix[index] }
    }
}
