# KSerial

KSerial 是一个基于 Kotlin 和 Compose Multiplatform 的桌面串口调试工具。项目当前处在从 JavaFX 版本迁移到 Compose Multiplatform 的阶段，现有界面布局和业务能力主要来源于原项目 [SerialPortToolFX](https://github.com/yiaobang/SerialPortToolFX)，本地对照项目路径为 `D:\Code\JavaProjects\SerialPortToolFX`。

这个仓库的目标不是只把 JavaFX UI 机械翻译成 Compose，而是在保留原有串口调试能力的基础上，逐步利用 Kotlin、协程、Flow、Compose 状态模型和跨平台工程结构，把项目整理成更适合长期维护和扩展的串口工具。

## 当前定位

- 当前运行目标是 JVM Desktop。虽然工程使用 Compose Multiplatform，但串口通信依赖 `jSerialComm`，所以当前主要是桌面端应用。
- 当前 UI 与功能形态基本继承 JavaFX 版本：左侧收发区、右侧串口参数与操作面板、底部 TX/RX 统计栏。
- 当前代码已经开始 Kotlin 化：串口扫描使用协程和 `StateFlow`，串口数据使用 `SharedFlow` 分发，循环发送使用协程 `Job`，主题和文案由 Compose 状态驱动。
- 后续适合继续拆分为 UI 状态、串口会话、编解码、设备模拟、日志存储等更清晰的模块。

## 功能现状

| 功能 | 当前状态 | 说明 |
| --- | --- | --- |
| 串口扫描 | 已实现 | `PortMonitor` 每 2 秒扫描一次系统串口，并通过 `StateFlow` 推送到 UI |
| 串口连接参数 | 已实现 | 支持串口号、波特率、数据位、停止位、校验位、流控 |
| 自动同步串口 | 已实现 | 选中串口或参数变化后会尝试同步打开串口 |
| 数据发送 | 已实现 | 支持文本发送与 HEX 发送 |
| 数据接收显示 | 已实现 | 接收数据进入日志列表，支持实时显示开关和 HEX 显示 |
| 循环发送 | 已实现 | 使用协程按指定毫秒间隔循环发送输入区内容 |
| TX/RX 统计 | 已实现 | 底部状态栏显示发送/接收字节数，点击可清零 |
| 工作区导航与标签页 | 已实现 | 左侧按功能导航；基础、自动回复、串口网关各自管理标签页，标签页可剥离为独立窗口再合并 |
| 设备模拟/自动回复 | 已实现 | 支持 JSON 与 HOCON `.conf` 配置、定长分帧、分隔符分帧、HEX/文本映射；`.conf` 可同时声明串口参数 |
| 串口网关 | 初版实现 | 支持从 `.conf` 加载多个串口端点和路由，实现一对一、一对多、多对多转发 |
| 关键词高亮 | 初版实现 | 基础、自动回复、串口网关日志均支持多关键词高亮和忽略大小写 |
| 日志过滤/搜索 | 初版实现 | 支持按关键词、TX/RX/INFO 类型过滤，支持暂停自动滚动 |
| 快捷发送 | 初版实现 | 基础和自动回复页面支持多行快捷命令模板，点击即可发送 |
| 串口占用保护 | 初版实现 | 基础、自动回复、串口网关之间共享进程内占用表，避免同一 COM 被多个页面重复打开 |
| 配置校验提示 | 初版实现 | 自动回复和串口网关加载 `.conf` 后会在日志中提示缺失端点、重复端口、无效路由引用等问题 |
| 串口可靠性 | 初版实现 | 写入在 IO 协程中执行，监听器支持并发增删，未连接写入和非法 HEX 会返回明确错误 |
| 数据落盘 | 初版实现 | `保存发送` / `保存接收` 开关会按需创建 `.write` / `.read` 文件并写入 HEX 数据 |
| 多语言 | 已实现基础版 | 内置中文、英文、日文，通过顶部按钮循环切换；当前默认中文，尚未按系统语言初始化 |
| 主题 | 已实现 | 自定义 Cupertino 风格浅色/深色主题，颜色切换使用 Compose 动画 |
| 波形图 | 代码存在但未接入 | `SerialChart` 已存在，当前主界面还没有实际使用 |

## 与 JavaFX 原项目的对比

| 方向 | SerialPortToolFX | KSerial 当前实现 |
| --- | --- | --- |
| UI 框架 | JavaFX + FXML + CSS + AtlantaFX | Compose Multiplatform + Material3 组件 + 自定义主题 |
| 主界面 | `SerialPortView.fxml` + `SerialPortView.java` | `App.kt` + `ControlPanel.kt` + `TopBar.kt` + `StatusBar.kt` |
| 状态模型 | JavaFX `Property` / Binding | Compose `mutableStateOf` / `mutableStateListOf` / `snapshotFlow` |
| 串口通信 | `SerialComm` 同时承担 ViewModel、串口、统计、存储协调 | `SerialViewModel` 协调业务，`SerialComm` 更偏底层通信 |
| 串口扫描 | 后台线程 + `SimpleStringProperty` | 协程 + `MutableStateFlow<List<String>>` |
| 数据监听 | 独立 `SerialDataListener`，使用 `LISTENING_EVENT_DATA_RECEIVED` | `SerialComm` 实现监听器，使用 `LISTENING_EVENT_DATA_AVAILABLE` 后主动读取 |
| 接收展示 | `ByteBuffer` 保存最多 115200 字节，TextArea 整体刷新 | `LogEntry` 列表展示，最多保留 1000 条日志 |
| 循环发送 | JavaFX `Timeline` | Kotlin 协程 `Job` |
| 设备模拟配置 | Gson + `Map<String, String>` | `kotlinx.serialization.json` 兼容旧 JSON，Typesafe Config 解析 HOCON `.conf` |
| 国际化 | `ResourceBundle`，按系统语言初始化 | sealed class 文案表，按钮循环切换 |
| 主题 | AtlantaFX Cupertino 主题 + CSS | Compose `CompositionLocal` 色板 + `animateColorAsState` |
| 多窗口 | 新窗口会复制部分配置与主题，并偏移位置 | 新窗口独立创建，当前未复制已有窗口配置 |
| 打包 | Gradle jlink / jpackage | Compose Desktop `nativeDistributions` |

## 项目结构

```text
KSerial
├─ build.gradle.kts
├─ settings.gradle.kts
├─ gradle/libs.versions.toml
└─ composeApp
   ├─ build.gradle.kts
   ├─ compose-desktop.pro
   └─ src/jvmMain
      ├─ composeResources
      │  └─ drawable
      └─ kotlin/com/yiaobang/kserial
         ├─ main.kt
         ├─ App.kt
         ├─ SerialViewModel.kt
         ├─ i18n
         │  └─ AppStrings.kt
         ├─ model
         │  ├─ AppPreferences.kt
         │  ├─ SerialPortConfig.kt
         │  ├─ LogState.kt
         │  ├─ DataWriteFile.kt
         │  ├─ DeviceSimulator.kt
         │  ├─ FixedLengthSimulator.kt
         │  ├─ DelimiterBasedSimulator.kt
         │  └─ CircularArray.kt
         ├─ serial
         │  ├─ SerialComm.kt
         │  └─ PortMonitor.kt
         ├─ theme
         │  ├─ AppColors.kt
         │  └─ CupertinoTheme.kt
         ├─ ui
         │  ├─ ControlPanel.kt
         │  ├─ TopBar.kt
         │  ├─ StatusBar.kt
         │  └─ SerialChart.kt
         └─ utils
            └─ ByteArrayExt.kt
```

## 核心数据流

```text
SerialPort
  -> SerialComm
  -> dataListeners: DataWriteFile / DeviceSimulator
  -> SharedFlow<ByteArray>
  -> SerialViewModel
  -> Compose UI
```

发送路径：

```text
Compose input
  -> SerialViewModel.sendData()
  -> HEX or UTF-8 encode
  -> SerialComm.write()
  -> TX counter / log / write file
```

设备模拟路径：

```text
JSON / HOCON config
  -> DeviceSimulator factory
  -> FixedLengthSimulator or DelimiterBasedSimulator
  -> SerialComm data listener
  -> matched reply
  -> SerialComm.write()
```

## 示例配置

示例文件位于 `config_samples/`。自动回复和串口网关建议优先使用 `.conf`，因为它可以把串口连接参数、规则、路由和关键词高亮放在同一个文件里。

### 自动回复

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

  highlight {
    enabled = true
    ignoreCase = true
    keywords = ["ERROR", "WARN", "OK"]
  }

  encode = "ASCII"
  delimiter = "\r\n"

  rules = [
    { request = "AT\r\n", response = "OK\r\n" }
  ]
}
```

### 串口网关

```hocon
gateway {
  endpoints = [
    { id = "devA", name = "Device A", port = "COM1", baudRate = 9600, dataBits = 8, stopBits = 1, parity = "none", flowControl = "none" },
    { id = "devB", name = "Device B", port = "COM2", baudRate = 115200, dataBits = 8, stopBits = 1, parity = "none", flowControl = "none" }
  ]

  highlight {
    enabled = true
    ignoreCase = true
    keywords = ["ERROR", "WARN", "A to B"]
  }

  routes = [
    { name = "A to B", enabled = true, sources = ["devA"], targets = ["devB"] }
  ]
}
```

### 字段说明

| 字段 | 说明 |
| --- | --- |
| `endpoint` / `endpoints` | 自动回复使用单个串口端点；网关使用多个端点 |
| `routes` | 网关路由，`sources` 中任意端点收到数据后转发给 `targets` |
| `highlight` | 日志关键词高亮配置，`keywords` 支持多个词 |
| `encode` | `HEX` 表示十六进制；其他值当前按文本/UTF-8 处理，兼容 `ASCII` 写法 |
| `packSize` | 固定长度分帧，单位为字节 |
| `delimiter` | 分隔符分帧；HEX 模式下填写 HEX 字节，文本模式下填写普通字符串 |
| `rules` | 自动回复规则，`request` 为接收内容，`response` 为回复内容 |

建议 `packSize` 和 `delimiter` 至少提供一个。后续可以把这部分改成 `@Serializable` 配置类，并对空分隔符、非法 HEX、重复映射等情况给出明确错误提示。

## 构建与运行

环境建议：

- JDK 17 或更高版本
- 使用仓库自带 Gradle Wrapper，当前 Wrapper 指向 Gradle 9.4.0

运行开发版：

```powershell
.\gradlew.bat :composeApp:run
```

编译 Kotlin/JVM：

```powershell
.\gradlew.bat :composeApp:compileKotlinJvm
```

打包当前系统安装包：

```powershell
.\gradlew.bat :composeApp:packageDistributionForCurrentOS
```

注意：当前 `composeApp/build.gradle.kts` 中 Windows 图标路径指向 `src/main/resources/icons/icon.ico`，仓库里暂未看到该文件。打包 Windows 安装包前需要补充图标文件，或把配置改到现有资源路径。

## 主要依赖

| 依赖 | 用途 |
| --- | --- |
| Kotlin Multiplatform | 工程与 JVM target 配置 |
| Compose Multiplatform | 桌面 UI |
| Compose Material3 | 基础 UI 组件 |
| jSerialComm | 跨平台串口通信 |
| kotlinx.coroutines | 串口扫描、循环发送、异步任务 |
| kotlinx.serialization-json | 设备模拟 JSON 解析 |
| Typesafe Config | 自动回复和串口网关 `.conf` 解析 |
| AndroidX Lifecycle ViewModel Compose | ViewModel 与 Compose 集成 |

## 后续演进方向

### 先对齐 JavaFX 行为

- 按系统语言初始化默认文案，再允许用户循环切换。
- 清理接收区、暂停实时显示、数据落盘、接收缓存之间的行为需要重新定义边界。

### 再做 Compose/Kotlin 化

- 引入统一 `SerialUiState` 和 `SerialIntent`，减少 UI 直接修改多个 mutable state。
- 把串口会话抽象成 `SerialSession`，隔离连接、配置、统计、日志、生命周期。
- 把编码逻辑抽成 `Codec` 或 `SerialPayload`，统一处理 HEX、UTF-8、非法输入和显示格式。
- 把设备模拟配置改成强类型 `@Serializable` 数据类，返回可展示的解析错误。
- 使用 `StateFlow` 暴露 ViewModel 状态，让 Compose 层更接近单向数据流。
- 给 `CircularArray`、HEX 转换、模拟器分帧、保存开关补单元测试。

### 充分利用 Compose Multiplatform

- 把纯业务逻辑放入 common 层：编解码、日志模型、模拟器配置、过滤规则、显示策略。
- 把桌面串口实现留在 JVM 层：`jSerialComm`、文件选择器、窗口置顶、原生拖拽、打包配置。
- UI 组件逐步做成可复用模块：连接参数面板、发送面板、日志面板、状态栏、模拟器配置面板。
- 如果未来考虑 Android，需要单独实现 Android 串口/USB 后端，不应直接把 `jSerialComm` 放进 commonMain。

## 当前已知问题

- `SerialChart` 未接入主界面。
- 自动回复和网关配置目前仍由 ViewModel 直接读取，后续适合抽成强类型配置模型和独立校验器。
- 当前缺少串口通信、分帧、路由、配置校验的自动化测试。
- 当前仓库暂未包含 `LICENSE` 文件；如果要继承原 JavaFX 项目的 MIT License，需要在本仓库补充。

## 原项目参考

- 本地 JavaFX 项目：`D:\Code\JavaProjects\SerialPortToolFX`
- 远程仓库：[yiaobang/SerialPortToolFX](https://github.com/yiaobang/SerialPortToolFX)
- 原项目主要能力：串口收发、HEX/ASCII、实时显示、保存发送/接收、循环发送、多窗口、设备模拟、三语言、浅色/深色主题、jpackage 打包。
