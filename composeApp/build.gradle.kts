@file:OptIn(ExperimentalKotlinGradlePluginApi::class)


import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlin.serialization)
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
