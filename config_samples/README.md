# KSerial 配置示例

**简体中文** · [English](README.en.md) · [日本語](README.ja.md)

这里的 `.conf` 文件按难度从低到高排列。建议先复制最接近需求的示例，再修改串口号、通信参数和规则，不要直接连接未知设备后启用 `autoOpen`。

## `.conf` 支持注释吗？

支持。KSerial 使用 Typesafe Config 读取 [HOCON](https://github.com/lightbend/config#using-hocon-the-json-superset)，可以使用 `#` 或 `//` 编写整行注释和行尾注释：

```hocon
# 这是整行注释
port = "COM2" // 这是行尾注释
baudRate = 115200 # 这种写法也可以
```

建议给包含 `#`、`//`、空格或转义字符的实际值加双引号，避免它被误认为注释或被拆分。只有 HOCON `.conf` / `.hocon` 支持这种注释；旧版 JSON 配置不支持注释。

## 学习顺序

| 级别 | 示例 | 内容 |
| --- | --- | --- |
| 1 | [01-simulator-minimal.conf](01-simulator-minimal.conf) | 最小文本设备模拟：一个端点、一条规则、换行符分帧。 |
| 2 | [02-simulator-text.conf](02-simulator-text.conf) | 完整文本设备模拟：串口参数、多条 AT 规则和关键词高亮。 |
| 3 | [03-simulator-hex-fixed-length.conf](03-simulator-hex-fixed-length.conf) | HEX 设备模拟：固定长度分帧和字节响应。 |
| 4 | [04-bridge-one-way.conf](04-bridge-one-way.conf) | 最小单向桥接：两个端点和一条转发路由。 |
| 5 | [05-bridge-bidirectional.conf](05-bridge-bidirectional.conf) | 双向桥接：完整端点参数、两条反向路由和日志高亮。 |
| 6 | [06-bridge-multi-route.conf](06-bridge-multi-route.conf) | 多端点桥接：一对多转发、返回路由和停用路由。 |

## 设备模拟字段

| 字段 | 是否必需 | 说明 |
| --- | --- | --- |
| `simulator` | 否 | 推荐使用的根节点；省略时可以直接在文件根部写内部字段。 |
| `endpoint` | 否 | 串口连接参数；省略时沿用当前页面设置并显示配置提示。 |
| `encode` | 否 | `HEX` 表示十六进制字节；其他值按 UTF-8 文本处理。默认是文本。 |
| `delimiter` | 二选一 | 分隔符分帧。文本模式填写普通字符串，HEX 模式填写 HEX 字节。 |
| `packSize` | 二选一 | 固定长度分帧，范围为 1–4096 字节；有效时优先于 `delimiter`。 |
| `rules` | 是 | `request` / `response` 规则列表。请求内容应保持唯一。 |
| `highlight` | 否 | 日志关键词、启用状态和是否忽略大小写。 |

使用 `delimiter` 时，规则中的 `request` 必须包含结尾分隔符，例如 `request = "AT\r\n"`。使用 `packSize` 时，每条请求解码后的字节数必须正好等于指定长度。

## 串口桥接字段

| 字段 | 是否必需 | 说明 |
| --- | --- | --- |
| `gateway.endpoints` | 是 | 串口端点列表；每个 `id` 必须唯一，同一个物理串口不要重复配置。 |
| `gateway.routes` | 否 | 显式路由列表。存在路由时，转发关系由启用的路由决定。 |
| `route.sources` | 是 | 允许触发该路由的端点 ID 列表。 |
| `route.targets` | 是 | 收到数据后要转发到的端点 ID 列表。 |
| `route.enabled` | 否 | 是否启用路由，默认值为 `true`。 |
| `gateway.highlight` | 否 | 桥接日志的关键词高亮配置。 |

一个端点可以出现在多条路由中，一个来源也可以同时配置多个目标。`sources` 和 `targets` 必须引用 `endpoints` 中存在的 ID。

## 端点通信参数

设备模拟的 `endpoint` 和串口桥接的 `endpoints` 使用相同字段：

| 字段 | 默认值或可选值 |
| --- | --- |
| `port` | 系统串口名，例如 `COM3` 或 `/dev/ttyUSB0` |
| `baudRate` | 默认 `9600` |
| `dataBits` | 默认 `8` |
| `stopBits` | `1`、`1.5`、`2` |
| `parity` | `none`、`odd`、`even`、`mark`、`space` |
| `flowControl` | `none`、`rts_cts`、`dsr_dtr`、`xon_xoff`，也接受带 `/` 的写法 |
| `autoOpen` | 默认 `false`；加载配置后是否立即尝试打开串口 |

首次使用示例时应保持 `autoOpen = false`，确认端口与通信参数无误后再决定是否开启。
