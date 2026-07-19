@file:OptIn(ExperimentalKotlinGradlePluginApi::class)


import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(25)
    jvm {
        mainRun {
            mainClass.set("com.yiaobang.kserial.MainKt")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.bundles.compose.common)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain {
            resources.srcDir(rootProject.file("config_samples"))
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutinesSwing)
                //串口
                implementation(libs.fazecast.jSerialComm)
                //配置
                implementation(libs.typesafe.config)
            }
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.testJunit)
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.compose.ui.test)
            implementation(compose.desktop.currentOs)
        }
    }
}
compose {
    resources {
        // 是否生成公开的 Res 类。如果你的资源只在当前模块使用，建议设为 false
        publicResClass = false

        // 生成的 Res 类的包名（建议与你的应用包名一致）
        packageOfResClass = "com.yiaobang.kserial.resources"

        // 默认的 auto 模式会在存在 resources 依赖时生成 Res 类
    }
    desktop {
        application {
            mainClass = "com.yiaobang.kserial.MainKt"
            jvmArgs(
                "-Xms64m",
                "-Xmx512m",
                "--enable-native-access=ALL-UNNAMED"
            )
            nativeDistributions {
                targetFormats(
                    TargetFormat.Msi,
                    TargetFormat.Exe,
                    TargetFormat.Pkg,
                    TargetFormat.Dmg,
                    TargetFormat.Deb,
                    TargetFormat.Rpm
                )
                packageName = "KSerial"
                packageVersion = providers.gradleProperty("appVersion").get()
                buildTypes.release.proguard.isEnabled.set(false)
                buildTypes{
                    release{
                        proguard {
                            // 确保项目根目录或 composeApp 目录下确实有这个 .pro 文件
                            configurationFiles.from(project.file("compose-desktop.pro"))
                        }
                    }
                }

                windows {
                    iconFile.set(project.file("src/main/resources/icons/icon.ico"))
                     //如果需要设置开始菜单/桌面快捷方式，可以在这里增加
                    menu = true
                    shortcut = true
                    // 某些驱动会通过这个参数识别是否需要开启高性能 GPU
                    jvmArgs(
                        "-Dsun.java2d.d3d=true",
                        "-Dskiko.renderApi=DIRECT3D",
                        "-Xms64m",
                        "-Xmx512m",
                        "--enable-native-access=ALL-UNNAMED"
                    )
                }
//                macOS {
//                    iconFile.set(project.file("src/main/resources/icons/icon.icns"))
//                }
//                linux {
//                    iconFile.set(project.file("src/main/resources/icons/icon.png"))
//                }
            }
        }
    }
}

val jvmTestTask = tasks.named<Test>("jvmTest")
val kserialTestGroup = "KSerial Tests"
jvmTestTask.configure {
    group = kserialTestGroup
    description = "Runs all unit and Compose UI tests; real serial-port tests are skipped."
    maxParallelForks = 1
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty(
        "kserial.hardwareTests",
        providers.gradleProperty("kserialHardwareTests").orElse("false").get()
    )
}

val hardwareTestClasses = mapOf(
    "terminalHardwareTest" to "com.yiaobang.kserial.hardware.TerminalHardwareTest",
    "simulatorHardwareTest" to "com.yiaobang.kserial.hardware.SimulatorHardwareTest",
    "gatewayHardwareTest" to "com.yiaobang.kserial.hardware.GatewayHardwareTest"
)

// 每个功能独占一个 Test 任务，IDE 可单独点击运行，避免不同功能同时争用虚拟串口。
fun registerHardwareTestTask(
    taskName: String,
    testClass: String,
    observationDelayMillis: String
) = tasks.register<Test>(taskName) {
        group = kserialTestGroup
        description = "Runs $testClass against configured virtual COM-port pairs."
        dependsOn("jvmTestClasses")
        testClassesDirs = jvmTestTask.get().testClassesDirs
        classpath = jvmTestTask.get().classpath
        filter { includeTestsMatching(testClass) }
        systemProperty("kserial.hardwareTests", "true")
        systemProperty(
            "kserial.hardwareObservationDelayMs",
            providers.gradleProperty("kserialHardwareObservationDelayMs").orElse(observationDelayMillis).get()
        )
        maxParallelForks = 1
        jvmArgs("--enable-native-access=ALL-UNNAMED")
        testLogging {
            events("passed", "failed", "skipped")
            showStandardStreams = true
        }
        outputs.upToDateWhen { false }
}

val hardwareTestTasks = hardwareTestClasses.mapValues { (taskName, testClass) ->
    registerHardwareTestTask(taskName, testClass, observationDelayMillis = "0")
}

tasks.register("coreTest") {
    group = kserialTestGroup
    description = "Runs unit and Compose UI tests without opening real serial ports."
    dependsOn(jvmTestTask)
}

hardwareTestClasses.forEach { (taskName, testClass) ->
    val featureName = taskName.removeSuffix("HardwareTest")
    // Observe 任务在每次写入前后暂停 750ms，便于人工核对控制台或驱动级串口监控。
    registerHardwareTestTask(
        taskName = "${featureName}HardwareObserve",
        testClass = testClass,
        observationDelayMillis = "750"
    ).configure {
        description = "Runs the $featureName hardware test slowly with visible port-level traffic."
    }
}

hardwareTestTasks.getValue("simulatorHardwareTest").configure {
    // 即使调用方启用了 Gradle 并行执行，聚合任务仍按终端→模拟器→桥接的顺序运行。
    mustRunAfter(hardwareTestTasks.getValue("terminalHardwareTest"))
}
hardwareTestTasks.getValue("terminalHardwareTest").configure {
    mustRunAfter(jvmTestTask)
}
hardwareTestTasks.getValue("gatewayHardwareTest").configure {
    mustRunAfter(hardwareTestTasks.getValue("simulatorHardwareTest"))
}

tasks.register("allHardwareTest") {
    group = kserialTestGroup
    description = "Runs terminal, simulator, and gateway hardware tests sequentially."
    dependsOn(hardwareTestTasks.values)
}

tasks.register("allKSerialTest") {
    group = kserialTestGroup
    description = "Runs unit/UI tests followed by all real serial-port hardware tests."
    dependsOn(jvmTestTask, "allHardwareTest")
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "com.yiaobang.kserial.resources.*",
                    "com.yiaobang.kserial.ComposableSingletons*",
                    "com.yiaobang.kserial.MainKt*"
                )
            }
        }
    }
}
