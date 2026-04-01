<div align="center">

# KSerial

**基于 Kotlin Compose Multiplatform 构建的现代化串口调试工具**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-Desktop-4D8AF0?logo=jetbrains)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-lightgrey)](https://github.com)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

</div>

---

## ✨ 简介

KSerial 是一款基于 **Kotlin Compose Multiplatform** 构建的桌面端串口调试助手。  
它从 JavaFX 版本的串口工具完整移植而来，并在此基础上以 Kotlin 和 Compose 的惯用法进行全面重构：  
更清晰的职责划分、更符合 Kotlin 风格的代码结构、以及基于 **Cupertino 设计语言** 的精致双主题 UI。

---

## 🖥️ 界面预览

| Light Mode | Dark Mode |
|:---:|:---:|
| 极简浅色 Cupertino 风格 | 纯黑深色模式，适合长时间工作 |

> 支持 **500ms 平滑主题切换动画**，明暗切换不突兀。

---

## 🚀 核心功能

### 串口通信
- ✅ 完整的串口参数配置（波特率 / 数据位 / 停止位 / 校验位 / 流控）
- ✅ 系统串口自动扫描与热插拔检测（每 2 秒轮询，变化时自动更新）
- ✅ 检测到串口自动尝试连接（复刻原 JavaFX 行为）
- ✅ HEX / UTF-8 双模式收发
- ✅ 循环定时发送（可动态调整发送周期）
- ✅ TX / RX 字节计数（点击状态栏清零）

### 设备模拟
- ✅ 加载 JSON 配置文件，模拟串口设备自动应答
- ✅ 支持两种协议分帧策略：
  - **定长分帧**（`FixedLengthSimulator`）
  - **分隔符分帧**（`DelimiterBasedSimulator`）
- ✅ 同时支持 HEX 和 UTF-8 编码的指令-应答映射

### 数据持久化
- ✅ 串口连接时自动创建 `.read` / `.write` 日志文件
- ✅ 以 HEX 格式异步追加写入，不阻塞 UI

### 多窗口 & 体验
- ✅ 多窗口并发，每个窗口独立持有串口实例
- ✅ 窗口置顶（双保险：Compose 属性 + AWT 句柄联动）
- ✅ 自定义无边框窗口，顶部 40px 区域原生拖拽
- ✅ 三语言支持：🇨🇳 中文 / 🇺🇸 英文 / 🇯🇵 日文（循环切换）

---

## 🏗️ 项目架构

```
composeApp/src/jvmMain/kotlin/com/yiaobang/kserial/
├── App.kt                    # 主布局编排（~130 行，纯组合）
├── SerialViewModel.kt        # 业务协调层（ViewModel）
├── main.kt                   # 窗口启动入口
│
├── i18n/
│   └── AppStrings.kt         # 多语言：sealed class + Language enum
│
├── model/
│   ├── SerialPortConfig.kt   # Compose 状态持有：串口连接参数
│   ├── AppPreferences.kt     # Compose 状态持有：UI 偏好设置
│   ├── LogState.kt           # 日志数据结构（LogEntry, LogType）
│   ├── CircularArray.kt      # 环形字节缓冲区
│   ├── DataWriteFile.kt      # 异步数据持久化
│   ├── DeviceSimulator.kt    # 模拟器基类（工厂方法模式）
│   ├── DelimiterBasedSimulator.kt
│   └── FixedLengthSimulator.kt
│
├── serial/
│   ├── SerialComm.kt         # 纯串口通信层（单一职责）
│   └── PortMonitor.kt        # 全局串口扫描单例
│
├── theme/
│   ├── AppColors.kt          # Cupertino 色彩令牌
│   └── CupertinoTheme.kt     # 主题容器（含平滑动画）
│
├── ui/
│   ├── TopBar.kt             # 顶部标题栏组件
│   ├── StatusBar.kt          # 底部状态栏组件
│   ├── ControlPanel.kt       # 右侧控制面板（连接参数 / 动作 / 选项）
│   └── SerialChart.kt        # 数据波形图（Canvas 绘制）
│
└── utils/
    └── ByteArrayExt.kt       # ByteArray / String 编码扩展函数
```

### 架构亮点

| 模式 | 应用 |
|------|------|
| **State Holder 模式** | `SerialPortConfig` / `AppPreferences` 将串口参数与 UI 偏好从 ViewModel 中解耦 |
| **单一职责** | `SerialComm` 只做串口通信，通过回调列表通知模拟器和持久化模块 |
| **密封类枚举** | `sealed class AppStrings` + `enum Language`，类型安全的多语言系统 |
| **扩展函数** | `ByteArray.toHexString()` 等，取代 Java 风格的工具类静态方法 |
| **Flow + snapshotFlow** | 串口数据流和参数变化联动均通过 Kotlin Flow 驱动 |

---

## 🔧 设备模拟 JSON 格式

```json
{
  "encode": "UTF8",
  "delimiter": "\n",
  "GET_STATUS": "OK\n",
  "PING": "PONG\n"
}
```

| 字段 | 说明 |
|------|------|
| `encode` | 编码格式：`UTF8`（默认）或 `HEX` |
| `delimiter` | 分帧结束符（与 `packSize` 二选一） |
| `packSize` | 定长分帧字节数（与 `delimiter` 二选一） |
| 其余键值对 | 指令 → 应答 映射表 |

**HEX 模式示例：**
```json
{
  "encode": "HEX",
  "packSize": "4",
  "01 02 03 04": "AA BB CC DD"
}
```

---

## 🛠️ 构建与运行

### 环境要求

- JDK 17+
- Gradle 9.x（Wrapper 已包含，无需手动安装）

### 运行开发版

```bash
# Windows
.\gradlew.bat :composeApp:run

# macOS / Linux
./gradlew :composeApp:run
```

### 编译验证

```bash
.\gradlew composeApp:compileKotlinJvm
```

### 打包发布

```bash
# 生成对应平台的安装包（Windows: MSI, macOS: DMG, Linux: DEB）
.\gradlew composeApp:packageDistributionForCurrentOS
```

---

## 📦 主要依赖

| 库 | 用途 |
|----|------|
| [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) | UI 框架 |
| [jSerialComm](https://github.com/Fazecast/jSerialComm) | 跨平台串口通信 |
| [AndroidX ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel) | 生命周期感知状态管理 |
| [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization) | JSON 解析（模拟器配置） |
| [Kotlinx Coroutines](https://github.com/Kotlin/kotlinx.coroutines) | 异步任务调度 |

---

## 🎨 主题与国际化

### 主题

基于 **Apple Cupertino 设计语言**，提供完整的浅色 / 深色双主题：

- **浅色**：`#007AFF` 蓝 + 纯白表面 + `#F2F2F7` 背景
- **深色**：`#0A84FF` 蓝 + `#1C1C1E` 表面 + 纯黑背景

所有颜色切换均附带 **500ms 补间动画**（基于 `animateColorAsState`）。

### 语言切换

通过顶部工具栏的 🌐 按钮循环切换：  
**中文** → **English** → **日本語** → 中文 …

---

## 📄 许可证

MIT License © 2026 yiaobang