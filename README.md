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

- 自动发现串口，支持配置波特率、数据位、停止位、校验位和流控。
- 支持文本与 HEX 收发、HEX 实时校验与格式化、循环发送和快捷命令。
- 提供发送历史、键盘回溯、CRC16-Modbus/XOR/SUM8/LRC 校验和及通用分帧检查。
- 提供 TX/RX 统计、日志搜索与类型过滤、关键词高亮、复制、TXT/CSV 导出和数据落盘。
- 使用 JSON 或 HOCON `.conf` 描述设备响应规则，支持定长和分隔符分帧。
- 通过配置连接多个串口端点，支持一对一、一对多和多对多转发。
- 支持多标签页、标签页独立窗口和合并，同时阻止同一串口被多个工作区重复占用。
- 内置简体中文、英文和日文，以及浅色、深色主题。
- 由 GitHub Actions 自动生成 Windows、macOS 和 Linux 安装包及便携包。

## 三个工作区

| 工作区 | 用途 |
| --- | --- |
| **串口终端** | 日常串口调试：连接设备、收发文本或 HEX、循环发送、快捷发送、过滤日志及保存 TX/RX 数据。 |
| **设备模拟** | 根据配置匹配收到的数据并自动回复，用于模拟串口设备、验证上位机或执行重复性协议测试。 |
| **串口桥接** | 同时管理多个串口端点和转发路由，用于协议透传、串口中继和多设备联调。 |

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
3. 打开串口，在底部输入区发送文本或 HEX 数据。
4. 根据需要启用循环发送、快捷发送、关键词高亮、日志过滤或 TX/RX 数据保存。

需要模拟设备时，在 **设备模拟** 中加载规则配置；需要在多个串口之间转发数据时，在 **串口桥接** 中加载端点与路由配置。

### 运行目录与 `settings.conf`

首次启动时，KSerial 以 `System.getProperty("user.dir")` 作为运行根目录，创建 `conf` 文件夹，并在其中生成：

- `settings.conf`：全局设置；
- `01-simulator-minimal.conf` 至 `06-bridge-multi-route.conf`：由简到繁的设备模拟和串口桥接示例。

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
| `preferences.saveSend` / `saveReceive` | 是否保存发送、接收数据。 |
| `preferences.quickCommandsText` | 快捷发送命令，每行一条，可使用转义字符。 |
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
    autoOpen = false
  }

  encode = "ASCII"
  delimiter = "\r\n"

  rules = [
    { request = "AT\r\n", response = "OK\r\n" }
  ]
}
```

`encode` 可使用 `HEX` 或文本模式；`packSize` 用于固定长度分帧，`delimiter` 用于分隔符分帧。二者至少需要配置一个。配置加载时会检查无效 HEX、空分隔符、重复端口和无效路由引用等常见问题。

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

## 自动发布

版本号由 `gradle.properties` 中的 `appVersion` 统一管理。推送与版本一致的 `v*` 标签（例如 `v1.0.0`）时，`Build and Release` 工作流会在 Windows、macOS 和 Linux 上执行测试与打包，随后创建或更新对应的 GitHub Release，并附带 `SHA256SUMS.txt`。

普通 `master` 推送和 Pull Request 只运行 JVM 测试，不打包也不发布。在 GitHub Actions 页面手动运行发布工作流时，默认只验证多平台打包；只有显式启用 `publish` 选项才会创建或更新 Release。

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
