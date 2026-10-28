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
| 7 | [07-bridge-many-to-many.conf](07-bridge-many-to-many.conf) | 多对多桥接：5 组端点互相转发，验证复杂路由拓扑。 |
| 8 | [08-simulator-device.conf](08-simulator-device.conf) | 真实设备复刻模拟：来自录制问答对的完整设备行为，含固定与随机区间应答延迟。 |
| 9 | [09-test-sequence.conf](09-test-sequence.conf) | 测试序列：声明式步骤脚本（发送/期望/匹配模式/超时）。 |
| 10 | [10-network-endpoints.conf](10-network-endpoints.conf) | 网络端点：TCP 客户端/服务端与 UDP 端点写法，可与物理串口混用。 |

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
| `simulator.rules[].delay` | 否 | 单条规则的应答延迟，覆盖全局 `responseDelay`。 |
| `simulator.responseDelay` | 否 | 全局应答延迟，默认 `0`（立即应答）。 |
| `simulator.highlight` | 否 | 日志关键词、启用状态和是否忽略大小写。 |

`delimiter` 与 `packSize` 必须且只能选择一个。使用 `delimiter` 时，规则中的 `request` 必须包含结尾分隔符，例如 `request = "AT\r\n"`。使用 `packSize` 时，每条请求解码后的字节数必须正好等于指定长度。

## 应答延迟写法

`responseDelay`（全局）和 `rules[].delay`（单条覆盖）都接受两种写法：

| 写法 | 行为 |
| --- | --- |
| `delay = 300` 或 `delay = "300"` | 固定 300 ms 后应答。 |
| `delay = "100~300"` 或 `delay = "100-300"` | 每次应答在 100–300 ms 之间**均匀随机**，模拟真设备处理耗时的抖动。 |

- 单位毫秒，范围 `0`–`600000`；`~` 和 `-` 都能作区间分隔符，两侧空格可省略。
- 优先级：规则级 `delay` > 全局 `responseDelay` > 默认 `0`。没写延迟的规则沿用全局值。
- 随机区间的用途不是“更真实”这么简单：把区间调到略超过上位机的超时阈值，就能验证上位机是否正确重发、是否会重复处理同一条应答；固定值永远测不到这个边界。
- 多条延迟应答按请求到达顺序 **FIFO 串行**发出，和“真设备一次只处理一条请求”的行为一致。
- 写错不会静默失败：`responseDelay` 非法时按 `0` 处理并给出警告；规则级 `delay` 非法时回退到全局值并给出警告。加载配置时就能在问题列表里看到。

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

## 测试序列字段

| 字段 | 是否必需 | 默认值或可选值 | 说明 |
| --- | --- | --- | --- |
| `test` | 否 | 推荐使用的根节点 | 省略时可以直接在文件根部写内部字段。 |
| `test.connection` | 运行必需 | 无默认值 | 连接参数，字段与「端点通信参数」相同。 |
| `test.connection.port` | 是 | 无默认值 | 串口名或网络端点地址。 |
| `test.connection.baudRate` | 否 | 默认 `115200` | 波特率。 |
| `test.defaults.timeout` | 否 | 默认 `1000`；范围 `1`–`600000` | 单步未写 `timeout` 时的等待上限，单位毫秒。 |
| `test.defaults.interval` | 否 | 默认 `100`；范围 `0`–`10000` | 单步未写 `delay` 时，发送前的等待，单位毫秒。 |
| `test.steps` | 是 | `1`–`500` 步 | 按顺序执行的步骤列表。 |
| `test.steps[].name` | 否 | 默认 `Step N` | 报告里显示的步骤名。 |
| `test.steps[].send` | 否 | 无默认值 | 发送内容；为空表示本步只等待、不发送。 |
| `test.steps[].expect` | 否 | 无默认值 | 期望内容；为空表示只发送、不校验（除非 `match = "silence"`）。 |
| `test.steps[].match` | 否 | 默认 `contains` | `contains`、`exact`、`regex`、`silence`。 |
| `test.steps[].timeout` | 否 | 取 `defaults.timeout` | 本步等待上限。 |
| `test.steps[].delay` | 否 | 取 `defaults.interval` | 本步发送前的等待。 |
| `test.steps[].encoding` | 否 | 默认 `ascii` | `ascii` 或 `hex`；同时决定 `send` 的解析口径和 `expect` 的比较口径。 |

判定语义：

- `contains` 子串匹配、`exact` 去首尾空白后全等、`regex` 正则包含匹配、`silence` 要求整个超时窗口内**零字节**。
- 匹配是在超时窗口内**轮询**进行的：一旦收到内容并匹配成功就立即通过，不必等满 `timeout`；只有跑满超时才判失败。
- `encoding = "hex"` 时 `send` 按 `"41 54 0D 0A"` 这类字节串解析，`expect` 也按去掉空格、统一大写后的 HEX 串比较；`ascii` 模式按 UTF-8 文本比较，并忽略内容首尾空白。
- 配置错误在加载阶段直接报出，不会跑到一半才发现：`send` 为空且没有 `expect`/`silence`、正则非法、HEX 非法、步骤为空或超过 500 步都会拒绝加载。

## 端点通信参数

设备模拟的 `endpoint` 和串口桥接的 `endpoints[]` 使用相同通信字段：

| 字段 | 是否必需 | 默认值或可选值 | 说明 |
| --- | --- | --- | --- |
| `port` | 是 | 无默认值 | 串口名（`COM3`、`/dev/ttyUSB0`）或网络端点地址，见下方「网络端点写法」。 |
| `baudRate` | 串口必需 | 大于 0 的整数 | 波特率，例如 `9600`、`115200`；网络端点可省略。 |
| `dataBits` | 否 | 默认 `8`；范围 `5`–`8` | 数据位。 |
| `stopBits` | 否 | 默认 `1`；可选 `1`、`1.5`、`2` | 停止位。 |
| `parity` | 否 | 默认 `none` | 可选 `none`、`odd`、`even`、`mark`、`space`。 |
| `flowControl` | 否 | 默认 `none` | 可选 `none`、`rts_cts`、`dsr_dtr`、`xon_xoff`，也接受带 `/` 的写法。 |

配置中不要写 `autoOpen`。加载配置只做校验和准备，点击“运行”后才会打开串口。

## 网络端点写法

`port` 字段除了串口名，还可以写成以下网络地址（大小写不敏感）：

| 写法 | 行为 |
| --- | --- |
| `tcp://192.168.1.20:8080` | TCP 客户端：连接远端串口服务器（ser2net、ESP-Link、USR-TCP232 等） |
| `tcp://:9000` / `tcp-listen://9000` | TCP 服务端：本机监听，等待对端接入（一对一，后接入的顶掉前一个） |
| `udp://192.168.1.20:9000` | UDP：向固定对端收发（串口转 UDP 模块常见） |
| `udp-listen://9000` | 监听 UDP 端口，用首个报文的对端地址作为回发目标 |

网络端点与物理串口可以在同一份配置中混用，路由按端点 ID 正常引用即可。注意事项：

- 网络端点没有波特率概念，`baudRate`、`parity` 等串口参数可全部省略；物理串口一侧的参数在它自己的端点里配置。
- DTR / RTS / BREAK 线路控制是本地电气信号，不穿透网络，数据收发不受影响。
- 每个监听端口一次只接受一个客户端连接；`udp-listen` 的回发地址取自首个收到的报文。

完整的双向透传示例见 [10-network-endpoints.conf](10-network-endpoints.conf)。

## 日志高亮字段

设备模拟使用 `simulator.highlight`，串口桥接使用 `gateway.highlight`，两者内部字段相同：

| 字段 | 是否必需 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `highlight.enabled` | 否 | 有关键词时默认 `true` | 是否启用关键词高亮。 |
| `highlight.ignoreCase` | 否 | `true` | 匹配时是否忽略大小写。 |
| `highlight.keywords` | 否 | 空列表 | 要高亮的文本关键词列表。 |
