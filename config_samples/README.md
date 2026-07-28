# KSerial 配置示例

**简体中文** · [English](README.en.md) · [日本語](README.ja.md)

这里的 `.conf` 文件按难度从低到高排列。建议先复制最接近需求的示例，再修改串口号、通信参数和规则。加载配置只会进行校验和准备，必须在界面中点击“运行”才会打开串口。

## `.conf` 支持注释吗？

支持。KSerial 使用 Typesafe Config 读取 [HOCON](https://github.com/lightbend/config#using-hocon-the-json-superset)，可以使用 `#` 或 `//` 编写整行注释和行尾注释：

```hocon
# 这是整行注释
port = "COM2" // 这是行尾注释
baudRate = 115200 # 这种写法也可以
```

建议给包含 `#`、`//`、空格或转义字符的实际值加双引号，避免它被误认为注释或被拆分。配置导入仅接受 HOCON `.conf` / `.hocon` 文件。

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
| `simulator.endpoint` | 是 | 本次模拟使用的完整串口参数；不能依赖页面中的旧选择。 |
| `simulator.encode` | 否 | `HEX` 表示十六进制字节；`ASCII`、`TEXT`、`UTF8`、`UTF-8` 均按 UTF-8 文本处理，默认 `UTF8`。 |
| `simulator.delimiter` | 二选一 | 分隔符分帧。文本模式填写普通字符串，HEX 模式填写 HEX 字节。 |
| `simulator.packSize` | 二选一 | 固定长度分帧，范围为 1–4096 字节。不能与 `delimiter` 同时配置。 |
| `simulator.rules` | 是 | 至少一条 `request` / `response` 规则，请求内容应保持唯一。 |
| `simulator.rules[].request` | 是 | 收到并完成分帧后用于匹配的请求内容。 |
| `simulator.rules[].response` | 是 | 匹配成功后写入串口的响应内容。 |
| `simulator.highlight` | 否 | 日志关键词、启用状态和是否忽略大小写。 |

`delimiter` 与 `packSize` 必须且只能选择一个。使用 `delimiter` 时，规则中的 `request` 必须包含结尾分隔符，例如 `request = "AT\r\n"`。使用 `packSize` 时，每条请求解码后的字节数必须正好等于指定长度。

## 串口桥接字段

| 字段 | 是否必需 | 说明 |
| --- | --- | --- |
| `gateway` | 否 | 推荐使用的根节点；省略时可以直接在文件根部写内部字段。 |
| `gateway.endpoints` | 是 | 至少两个串口端点，同一个物理串口不能重复配置。 |
| `gateway.endpoints[].id` | 是 | 路由引用的端点 ID，在当前配置中必须唯一。 |
| `gateway.endpoints[].name` | 否 | 日志中显示的端点名称，默认使用 `id`。 |
| `gateway.routes` | 是 | 非空路由列表，并且至少有一条路由处于启用状态。 |
| `gateway.routes[].name` | 否 | 路由显示名称，默认生成 `route1`、`route2` 等名称。 |
| `gateway.routes[].sources` | 是 | 允许触发该路由的端点 ID 列表，不能为空。 |
| `gateway.routes[].targets` | 是 | 收到数据后要转发到的端点 ID 列表，不能为空。 |
| `gateway.routes[].enabled` | 否 | 是否启用路由，默认值为 `true`。 |
| `gateway.highlight` | 否 | 桥接日志的关键词高亮配置。 |

一个端点可以出现在多条路由中，一个来源也可以同时配置多个目标。`sources` 和 `targets` 必须引用 `endpoints` 中存在的 ID。加载失败会清空上一份桥接配置，防止误运行旧路由。

## 端点通信参数

设备模拟的 `endpoint` 和串口桥接的 `endpoints[]` 使用相同通信字段：

| 字段 | 是否必需 | 默认值或可选值 | 说明 |
| --- | --- | --- | --- |
| `port` | 是 | 无默认值 | 系统串口名，例如 `COM3` 或 `/dev/ttyUSB0`。 |
| `baudRate` | 是 | 大于 0 的整数 | 波特率，例如 `9600`、`115200`。 |
| `dataBits` | 否 | 默认 `8`；范围 `5`–`8` | 数据位。 |
| `stopBits` | 否 | 默认 `1`；可选 `1`、`1.5`、`2` | 停止位。 |
| `parity` | 否 | 默认 `none` | 可选 `none`、`odd`、`even`、`mark`、`space`。 |
| `flowControl` | 否 | 默认 `none` | 可选 `none`、`rts_cts`、`dsr_dtr`、`xon_xoff`，也接受带 `/` 的写法。 |

配置中不要写 `autoOpen`。加载配置只做校验和准备，点击“运行”后才会打开串口。

## 日志高亮字段

设备模拟使用 `simulator.highlight`，串口桥接使用 `gateway.highlight`，两者内部字段相同：

| 字段 | 是否必需 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `highlight.enabled` | 否 | 有关键词时默认 `true` | 是否启用关键词高亮。 |
| `highlight.ignoreCase` | 否 | `true` | 匹配时是否忽略大小写。 |
| `highlight.keywords` | 否 | 空列表 | 要高亮的文本关键词列表。 |
