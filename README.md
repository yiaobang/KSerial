<p align="center">
  <img src="composeApp/src/jvmMain/composeResources/drawable/app_icon.png" width="128" alt="KSerial 图标" />
</p>

<h1 align="center">KSerial</h1>

<p align="center">集串口终端、设备模拟和串口桥接于一体的跨平台桌面串口工作台。</p>

<p align="center">
  <strong>简体中文</strong> · <a href="README.en.md">English</a> · <a href="README.ja.md">日本語</a>
</p>

<p align="center">
  <a href="https://github.com/yiaobang/KSerial/actions/workflows/release.yml"><img src="https://github.com/yiaobang/KSerial/actions/workflows/release.yml/badge.svg" alt="构建与发布" /></a>
  <a href="https://github.com/yiaobang/KSerial/releases/latest"><img src="https://img.shields.io/github/v/release/yiaobang/KSerial?display_name=tag&amp;sort=semver" alt="最新版本" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/yiaobang/KSerial" alt="Apache-2.0 协议" /></a>
  <img src="https://img.shields.io/badge/platform-Windows%20%7C%20macOS%20%7C%20Linux-4c8bf5" alt="支持 Windows、macOS 和 Linux" />
</p>

KSerial 使用 Kotlin 和 Compose Multiplatform 构建，面向嵌入式开发、硬件联调、协议验证和串口设备测试。它不仅提供日常串口收发能力，还将自动回复、设备行为模拟和多串口转发整合到同一个多标签工作区中。

## 核心能力

- 自动发现串口并显示设备友好名称，支持手动刷新以及配置波特率、数据位、停止位、校验位和流控。
- 支持文本与 HEX 收发、HEX 实时校验与格式化、可选行尾、循环发送、快捷命令，以及 `Ctrl+Enter` / `Enter` 快速发送。
- 接收区支持时间窗口、原始数据块、分隔符和定长四种显示分帧模式，并采用有界缓冲与丢弃计数避免高吞吐数据拖垮界面。
- 提供明确的连接状态、异常断开检测与可选自动重连，同时支持 DTR、RTS、Break 控制和 CTS、DSR、DCD、RI 信号查看。
- 提供发送历史、键盘回溯、CRC16-Modbus/XOR/SUM8/LRC 校验和及通用分帧检查。
- 提供 TX/RX 统计、日志搜索与类型过滤、关键词高亮、复制、TXT/CSV 导出和数据落盘。
- 使用 HOCON `.conf` / `.hocon` 描述设备响应规则，支持定长和分隔符分帧；应答延迟可设固定毫秒数，也可设 `"100~300"` 这类**随机区间**，用来复现真设备的处理抖动。
- 通过配置连接多个串口端点，支持一对一、一对多和多对多转发；端点地址可以是串口、`tcp://`、`tcp-listen://`、`udp://` 或 `udp-listen://`，直接把串口数据接入网络。
- **串口录制**：把商业上位机软件和真实设备分别接在两侧，双向透传的同时录制「指令 → 应答」，一键导出成设备模拟器配置。没有上位机软件、设备只有一个串口时切到**手动模式**，用页面内输入区直接对设备发指令，同样能录成模拟器配置。
- **测试序列**：用 HOCON 描述「发送 → 期望 → 超时」的步骤表，一键跑完给出 PASS/FAIL 结果，适合固件回归。
- **串口绘图**：把设备打印的数值流按行解析成多通道曲线，支持暂停、清空和窗口长度调节。
- **无头模式**：同一个可执行文件带参数启动即可在后台跑桥接、模拟或测试序列，可放进脚本和 CI，用法见[无头模式（CLI）](#无头模式cli)。
- 支持多标签页、标签页独立窗口和合并，同时阻止同一串口被多个工作区重复占用。
- 内置简体中文、英文和日文，以及浅色、深色主题。
- 由 GitHub Actions 自动生成 Windows、macOS 和 Linux 安装包及便携包。

## 六个工作区

| 工作区 | 用途 |
| --- | --- |
| **串口终端** | 日常串口调试：连接设备、收发文本或 HEX、循环发送、快捷发送、过滤日志及保存 TX/RX 数据。 |
| **设备模拟** | 根据配置匹配收到的数据并自动回复，用于模拟串口设备、验证上位机或执行重复性协议测试。 |
| **串口桥接** | 同时管理多个串口端点和转发路由，端点可以是物理串口也可以是 TCP/UDP 地址，用于协议透传、串口中继、远程串口和多设备联调。 |
| **串口录制** | 一对端点做双向透传，同时录制「软件指令 → 设备应答」，停止后直接导出模拟器配置；也支持网络端点和手动模式。 |
| **测试序列** | 按配置逐步发送、等待并判定，输出 PASS/FAIL 报告，用于协议回归和产线自检。 |
| **串口绘图** | 将文本数值流解析为多通道实时曲线，便于观察传感器数据和调参过程。 |

每个工作区都可以创建多个独立标签页。标签页可拆分为单独窗口，并在需要时重新合并回主窗口。

## 下载

从 [GitHub Releases](https://github.com/yiaobang/KSerial/releases/latest) 下载适合当前系统的最新版本：

| 系统 | 安装包 | 便携包（绿色包） |
| --- | --- | --- |
| Windows | `.msi`、`.exe` | `.zip` |
| macOS | `.dmg`、`.pkg` | `.zip` |
| Linux | `.deb`、`.rpm` | `.tar.gz` |

发布包包含应用运行时，普通用户通常不需要额外安装 JDK。当前安装包尚未配置 Windows 代码签名和 macOS 签名/公证，系统首次运行时可能显示安全提示。

## 快速开始

1. 打开 **串口终端**，点击 `+` 新建标签页。
2. 在右侧“连接与通信设置”中选择串口，并设置波特率等通信参数。
3. 打开串口，在底部输入区发送文本或 HEX 数据。文本模式会在发送前完整解析转义序列：`\\`、`\'`、`\"`、`\?`、`\a`、`\b`、`\e`、`\f`、`\n`、`\r`、`\s`、`\t`、`\v`、八进制 `\ooo`、原始字节 `\xHH`、Unicode `\uHHHH` / `\UHHHHHHHH`，以及控制字符 `\cX`。
4. 使用 `Ctrl+Enter` 发送；也可启用 Enter 发送（此时 `Shift+Enter` 换行），并按协议选择 LF、CR 或 CRLF 行尾。
5. 根据需要启用自动重连、接收分帧、循环发送、快捷发送或 TX/RX 数据保存。搜索、关键词高亮和日志过滤都在日志区标题行：左侧搜索框直接过滤（`Ctrl+F` 聚焦），右侧两个图标分别打开高亮与筛选选项。

需要模拟设备时，在 **设备模拟** 中加载规则配置；需要在多个串口之间转发数据时，在 **串口桥接** 中加载端点与路由配置。

### 串口 ⇄ 网络（TCP / UDP）

**串口终端**、**串口绘图**、**串口录制** 三个工作区的地址框，以及串口桥接的端点配置，除了串口名还接受以下网络写法（大小写不敏感）；地址框下方同时提供「检测到的串口」下拉，可以直接挑系统端口：

| 写法 | 行为 |
| --- | --- |
| `COM3` / `/dev/ttyUSB0` | 物理串口（默认） |
| `tcp://192.168.1.20:8080` | TCP 客户端：连接远端串口服务器（ser2net、ESP-Link、USR-TCP232 等） |
| `tcp://:9000` / `tcp-listen://9000` | TCP 服务端：本机监听，等待对端接入（一对一，后接入的顶掉前一个） |
| `udp://192.168.1.20:9000` | UDP：向固定对端收发（串口转 UDP 模块常见） |
| `udp-listen://9000` | 监听 UDP 端口，用首个报文的对端地址作为回发目标 |

**典型场景：在电脑 A 上操作电脑 B 的串口。**

1. 电脑 B（串口所在的机器）：打开 **串口桥接**，加载一份端点配置，一侧是它的本地串口（如 `COM3`，波特率在这里设），另一侧是 `tcp-listen://9000`，两条方向相反的路由都要写；
2. 电脑 A：在 **串口终端** 或 **串口绘图** 的地址框输入 `tcp://电脑B的IP:9000`（录制页同理），打开后收发、HEX、循环发送、录制、绘图与直连串口完全一致；
3. 电脑 B 也可以用无头模式在后台跑同一份桥接配置，适合长期挂着的"串口服务器"。

**注意事项**：

- 网络端点没有波特率概念，串口参数（波特率、校验位等）在持有物理串口的那一侧设置；
- DTR / RTS / BREAK 线路控制是本地电气信号，**不穿透网络**，数据收发不受影响；
- 每个监听端口一次只接受一个客户端连接；
- `udp-listen` 的回发地址取自首个收到的报文，对端地址变化后需要重新收包才会更新。

HOCON 写法与完整示例见 [`config_samples/10-network-endpoints.conf`](config_samples/10-network-endpoints.conf)。

### 运行目录与 `settings.conf`

首次启动时，KSerial 以 `System.getProperty("user.dir")` 作为运行根目录，创建 `conf` 文件夹，并在其中生成：

- `settings.conf`：全局设置；
- `01-simulator-minimal.conf` 至 `10-network-endpoints.conf`：由简到繁的设备模拟、串口桥接、测试序列和网络端点示例。

设备模拟和串口桥接的“加载配置”窗口会优先打开该 `conf` 目录。升级旧版本时，如果新目录尚无 `settings.conf`，程序会从原用户配置目录复制旧设置。示例文件只在缺失时释放，不会覆盖用户修改。

`settings.conf` 由程序生成纯 HOCON，不写入字段注释；即使手动添加注释，程序下次保存时也会移除。若程序安装在 `Program Files` 等受保护目录，请确保当前用户对运行目录具有写权限，否则无法创建或更新 `conf`。

| 字段 | 说明 |
| --- | --- |
| `schemaVersion` | 配置结构版本，请勿手动修改。 |
| `appearance.darkTheme` | 是否使用深色主题。 |
| `appearance.alwaysOnTop` | 窗口是否保持最前。 |
| `appearance.language` | 界面语言：`zh`、`en` 或 `ja`。 |
| `serial.selectedPort` | 最后选择的串口名称；只恢复选择，不自动打开。 |
| `serial.baudRate` | 波特率。 |
| `serial.dataBits` | 数据位，范围为 5 至 8。 |
| `serial.stopBitsIndex` | 停止位索引：`0=1`、`1=1.5`、`2=2`。 |
| `serial.parityIndex` | 校验位索引：`0=None`、`1=Odd`、`2=Even`、`3=Mark`、`4=Space`。 |
| `serial.flowControlIndex` | 流控索引：`0=None`、`1=RTS/CTS`、`2=DSR/DTR`、`3=XON/XOFF`。 |
| `preferences.showRealTime` | 日志是否显示实时时间。 |
| `preferences.hexDisplay` | 接收内容是否以 HEX 显示。 |
| `preferences.hexSend` | 发送输入是否按 HEX 解析。 |
| `preferences.receiveDisplayMode` | 接收显示模式：`TIME_WINDOW`、`RAW_CHUNKS`、`DELIMITER` 或 `FIXED_LENGTH`。 |
| `preferences.receiveWindowMs` | 时间窗口模式的批量刷新间隔，单位为毫秒。 |
| `preferences.receiveDelimiter` | 分隔符模式使用的文本分隔符，支持与文本发送相同的转义规则。 |
| `preferences.receiveFrameSize` | 定长模式的每帧字节数。 |
| `preferences.sendLineEnding` | 文本发送时追加的行尾：`NONE`、`LF`、`CR` 或 `CRLF`；HEX 发送不追加。 |
| `preferences.enterToSend` / `clearAfterSend` | 是否使用 Enter 发送，以及发送成功后是否清空输入框。 |
| `preferences.autoReconnect` / `reconnectIntervalMs` | 异常断开后是否自动重连，以及重试间隔（毫秒）。 |
| `preferences.saveSend` / `saveReceive` | 是否保存发送、接收数据。 |
| `preferences.quickCommandsText` | 快捷发送命令，每行一条；使用与文本发送相同的转义规则。 |
| `preferences.highlightEnabled` / `highlightIgnoreCase` / `highlightKeywords` | 关键词高亮开关、大小写规则和关键词列表。 |
| `preferences.logQuery` / `logOnlyMatches` | 最后的日志搜索内容，以及是否只显示匹配项。 |
| `preferences.logAutoScroll` | 日志是否自动滚动到底部。 |
| `preferences.logShowTx` / `logShowRx` / `logShowInfo` | 是否显示对应类型的日志。 |
| `sendHistory` | 发送历史，最新在前、自动去重，最多 100 条。 |

发送输入框内容、循环发送开关及循环间隔不会保存，串口也不会在启动时自动打开。

## 配置驱动的设备模拟与桥接

仓库提供了从最小配置到多端点路由的渐进式 HOCON 示例。完整学习顺序、字段表和注释语法见 [配置示例说明](config_samples/README.md)：

- [最小设备模拟](config_samples/01-simulator-minimal.conf)
- [文本设备模拟](config_samples/02-simulator-text.conf)
- [固定长度 HEX 设备模拟](config_samples/03-simulator-hex-fixed-length.conf)
- [单向串口桥接](config_samples/04-bridge-one-way.conf)
- [双向串口桥接](config_samples/05-bridge-bidirectional.conf)
- [多端点、多路由串口桥接](config_samples/06-bridge-multi-route.conf)
- [多对多全互联桥接](config_samples/07-bridge-many-to-many.conf)
- [AT 风格设备模拟（含应答延迟）](config_samples/08-simulator-device.conf)
- [自动化测试序列](config_samples/09-test-sequence.conf)
- [串口 ⇄ TCP 网络端点](config_samples/10-network-endpoints.conf)

HOCON `.conf` 支持 `#` 和 `//` 注释，可以直接在配置中记录端口用途、协议说明和调试备注。

设备模拟示例：

```hocon
simulator {
  endpoint {
    port = "COM2"
    baudRate = 9600
    dataBits = 8
    stopBits = 1
    parity = "none"
    flowControl = "none"
  }

  encode = "ASCII"
  delimiter = "\r\n"

  rules = [
    { request = "AT\r\n", response = "OK\r\n" }
  ]
}
```

`encode` 可使用 `HEX` 或文本模式；`packSize` 用于固定长度分帧，`delimiter` 用于分隔符分帧，二者必须且只能配置一个。设备模拟必须提供完整端点，串口桥接必须提供至少两个端点和一条启用路由。配置加载时会检查无效 HEX、空分隔符、缺失端口、重复端口和无效路由引用等常见问题。

### 应答延迟（固定值或随机区间）

模拟器可以给应答加延迟，`300` 是固定 300 ms，`"100~300"` 是每应答在 100–300 ms 内**均匀随机**：

```hocon
simulator {
  responseDelay = "10~80"          # 全局：每次应答在 10–80 ms 内随机

  rules = [
    { request = "AT\r\n",         response = "OK\r\n" },
    { request = "AT+STATUS?\r\n", response = "STATUS:READY\r\n", delay = "200~500" }  # 单条覆盖
    { request = "AT+ID\r\n",      response = "ID:42\r\n",       delay = 20 }           # 固定 20 ms
  ]
}
```

- 单位毫秒，范围 `0`–`600000`；区间分隔符 `~` 和 `-` 都可，两侧空格可省略。
- 优先级：规则级 `delay` > 全局 `responseDelay` > 默认 `0`（立即应答）。
- **随机区间的价值在于测边界**：真设备的处理耗时本来就有抖动，固定值会让上位机的超时重传逻辑永远碰不到阈值；把区间调到略超过上位机超时时间，就能看它是否正确重发、是否会重复处理同一条应答。
- 多条延迟应答按请求到达顺序 FIFO 串行发出，与“真设备一次只处理一条请求”一致。
- 写错不会静默：`responseDelay` 非法按 `0` 处理并警告，规则级 `delay` 非法回退全局值并警告。

## 自动化测试序列

设备模拟解决“设备怎么响应”，测试序列解决“怎么验证设备”。一份 HOCON 描述「发送 → 期望 → 超时」，逐步执行并给出 PASS/FAIL：

```hocon
test {
  connection { port = "COM2", baudRate = 115200 }
  defaults   { timeout = 1000, interval = 100 }

  steps = [
    { name = "握手",   send = "AT\r\n",          expect = "OK" },
    { name = "慢应答", send = "AT+STATUS?\r\n",  expect = "STATUS:READY", timeout = 1500 },
    { name = "静默",   match = "silence",        timeout = 500 },
    { name = "HEX",    encoding = "hex", send = "41 54 0D 0A", expect = "4F 4B" }
  ]
}
```

- **界面**：打开 **测试序列** 工作区 → 加载配置 → 运行。连接端口直接取自配置里的 `test.connection.port`（页面不另做端口选择，换端口就改配置），逐步显示 PASS/FAIL、期望值、实际值与耗时；失败时会区分“超时（一个字节都没收到）”和“内容不符”。
- **命令行**：`KSerial.exe test 09-test-sequence.conf --report result.txt`，退出码非 0 即代表有步骤失败，可直接卡 CI。
- 四种判定：`contains`（默认，子串）、`exact`（去首尾空白后全等）、`regex`（正则包含匹配）、`silence`（窗口内不允许收到任何字节）。
- 收到并匹配成功就立即通过，不会傻等满超时；只有跑满超时才判失败。
- 完整字段表和判定语义见[配置示例说明](config_samples/README.md#测试序列字段)，示例见 [09-test-sequence.conf](config_samples/09-test-sequence.conf)。

## 串口绘图

把设备打印的数值流画成实时曲线：**每行一个采样、每列一个通道**（最多 8 路），逗号、空格、`KEY=VALUE` 前缀都能解析。

```
23.5,61,1013      ← 纯 CSV
TEMP=23.5,HUM=61  ← 带标签也行
```

- **用法**：打开 **串口绘图** 工作区 → 在地址框填串口名（或 `tcp://` / `udp://` 网络地址）、设好波特率 → 打开，曲线立即开始滚动；窗口长度可选 200 / 500 / 1000 / 2000 个采样点。
- 绘图页的地址框和终端页一样接受网络地址，可以直接画远端设备的数值流：填 `tcp://设备IP:端口` 后打开即可。
- **暂停**期间收到的数据直接丢弃（不是攒着后补），**清空**会重置曲线、通道数和字节计数。
- 纵轴按当前窗口自适应，左上角显示量程；顶部图例的配色与曲线一一对应。非数字行（`OK`、错误日志）自动忽略，没有换行的二进制流有 4096 字节保护，不会撑爆内存。
- **最常见的坑**：必须以换行符结尾才算一个采样点。在终端页发送时把「行尾」设成 LF 或 CRLF，否则字节收到了却永远成不了曲线。
- **排错**：图表头部显示「已收 N 字节」，有悬挂数据时额外显示「N 字节待行尾」；一个采样都没有时，空态提示会直接说明缺的是换行符还是数字。
- 只认 UTF-8 文本数值流，HEX 显示的二进制数据解析不出来。

## 无头模式（CLI）

同一个可执行文件，**带参数启动就不开窗口**，直接在终端/脚本/CI 里跑：

```powershell
# Windows（安装目录或 zip 解压目录）
KSerial.exe gateway  07-bridge-many-to-many.conf --duration 60
KSerial.exe simulate 08-simulator-device.conf
KSerial.exe test     09-test-sequence.conf --report result.txt

# Linux（tar.gz 解压目录）
./KSerial gateway 05-bridge-bidirectional.conf --duration 30

# macOS（.app 包内）
/Applications/KSerial.app/Contents/MacOS/KSerial gateway 05-bridge-bidirectional.conf --duration 30
```

| 子命令 | 别名 | 作用 |
| --- | --- | --- |
| `gateway` | `bridge` | 按 `gateway.endpoints` / `gateway.routes` 打开端点并转发 |
| `simulate` | `simulator` | 按 `simulator.*` 打开端点并按规则自动应答 |
| `test` | `run-test` | 按 `test.steps` 执行，输出逐步 PASS/FAIL |
| `help` | `--help`、`-h` | 打印用法 |

| 选项 | 适用子命令 | 说明 |
| --- | --- | --- |
| `--duration <秒>` / `-d` | `gateway`、`simulate` | 运行指定时长后自动退出；省略则一直运行到 Ctrl+C |
| `--report <路径>` | `test` | 把逐步结果写成文本报告 |
| `--lang zh\|en\|ja` | `gateway` | 配置校验信息的语言，默认跟随系统 |

退出码：`0` 成功、`1` 失败（配置不可运行、端口打不开、有测试步骤失败）、`2` 用法错误。

从源码直接跑也可以，不用打包：

```powershell
.\gradlew.bat :composeApp:run --args="--help"
.\gradlew.bat :composeApp:run --args="test 09-test-sequence.conf --report result.txt"
```

几个容易踩的点：

- 无头模式**只认配置文件里的端点**，命令行不接受 `COM3` 这类临时参数——换端口就改 `.conf`。
- `simulate` 要求配置里有 `simulator.endpoint.port`；界面里“沿用当前页面参数”的便利在无头下不存在。
- `gateway` 没写 `routes` 时会退化成“收到的数据转发给其余所有端点”。
- 端点地址同样支持 `tcp://`、`tcp-listen://`、`udp://`、`udp-listen://`，所以可以让无头进程常驻当串口服务器（见上文「串口 ⇄ 网络」）。
- 每行日志带时间戳输出到 stdout，重定向即可留档：`KSerial.exe gateway x.conf > gateway.log 2>&1`。
- 放进 CI 时用 `--duration` 让它自己退出，别依赖 Ctrl+C。

## 从源码运行

要求：

- JDK 25
- 仓库自带的 Gradle Wrapper

Windows：

```powershell
.\gradlew.bat :composeApp:run
.\gradlew.bat :composeApp:jvmTest
.\gradlew.bat :composeApp:packageDistributionForCurrentOS
```

macOS / Linux：

```bash
./gradlew :composeApp:run
./gradlew :composeApp:jvmTest
./gradlew :composeApp:packageDistributionForCurrentOS
```

## 测试

刷新 Gradle 项目后，可以在 IDE 的 `composeApp > Tasks > KSerial Tests` 分组中直接运行下列任务：

| 任务 | 用途 |
| --- | --- |
| `coreTest` | 运行单元测试和 Compose UI 测试，不打开真实串口 |
| `terminalHardwareTest` | 使用第 1 组虚拟串口验证串口终端的真实双向收发、转义解析和循环发送 |
| `simulatorHardwareTest` | 使用第 2、3 组虚拟串口验证 ASCII 分隔符与 HEX 定长分帧自动回复 |
| `gatewayHardwareTest` | 使用前 5 组虚拟串口验证配置路由、一对一、一对多、多对多、二进制透明转发和非目标端口隔离 |
| `captureHardwareTest` | 验证串口录制：双口透传录制与手动模式对单口设备的问答录制 |
| `recorderRoundTripHardwareTest` | 录制 → 导出配置 → 模拟器加载 → 回放复现的完整闭环 |
| `networkSerialBridgeHardwareTest` | 验证串口 ⇄ TCP 混合桥接：TCP 服务端与真实串口之间的双向透传 |
| `testRunnerHardwareTest` | 验证测试序列的真机执行：CONTAINS / HEX 匹配、失败判定与静默等待 |
| `cliGatewayHardwareTest` | 验证无头模式：CLI 网关桥接 TCP 与真实串口 |
| `plotterHardwareTest` | 验证串口绘图：分片粘包下的多通道采样、窗口截断与暂停语义 |
| `allHardwareTest` | 按功能顺序严格串行运行全部真实串口测试（虚拟串口是全局共享资源） |
| `allKSerialTest` | 先运行单元/UI 测试，再运行全部真实串口测试 |

另有各功能对应的 `*HardwareObserve` 观察任务（如 `terminalHardwareObserve`）。它们会放慢每次真实写入，并在控制台输出 `OPEN`、`SEND`、`WRITE RESULT` 和 `RECEIVED` 轨迹，便于配合串口监控工具人工核对流量。

真实串口测试默认使用 10 组已互联的虚拟串口：`COM1↔COM2`、`COM3↔COM4`、…、`COM19↔COM20`。每组前一个端口作为外部测试端，后一个端口由 KSerial 打开。若端口名称不同，可通过 `KSERIAL_PORT_PAIRS` 环境变量覆盖，格式为 `端口A:端口B,端口C:端口D`。

```powershell
$env:KSERIAL_PORT_PAIRS = "COM21:COM22,COM23:COM24"
.\gradlew.bat :composeApp:allKSerialTest
```

普通 `jvmTest`、`coreTest` 和 CI 会主动跳过真实串口测试，避免占用本机串口。生成 JVM 测试覆盖率报告可运行：

```powershell
.\gradlew.bat :composeApp:koverHtmlReportJvm
```

报告入口为 `composeApp/build/reports/kover/htmlJvm/index.html`。

## 自动发布

版本号由 `gradle.properties` 中的 `appVersion` 统一管理。推送与版本一致的 `v*` 标签（例如 `v1.0.0`）时，`Build and Release` 工作流会在 Windows、macOS 和 Linux 上执行测试与打包，随后创建或更新对应的 GitHub Release，并附带 `SHA256SUMS.txt`。

普通 `master` 推送和 Pull Request 只运行不依赖真实串口的 JVM 单元/UI 测试，不打包也不发布。在 GitHub Actions 页面手动运行发布工作流时，默认只验证多平台打包；只有显式启用 `publish` 选项才会创建或更新 Release。

## 技术栈

- Kotlin Multiplatform
- Compose Multiplatform / Material 3
- kotlinx.coroutines / Flow
- jSerialComm
- kotlinx.serialization
- Typesafe Config
- Gradle / jpackage

## 开源协议

KSerial 使用 [Apache License 2.0](LICENSE) 开源。

如果 KSerial 对你有帮助，欢迎在 GitHub 上点一个 ⭐。你的支持会让这个项目继续变得更好。
